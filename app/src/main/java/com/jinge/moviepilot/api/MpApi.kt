package com.jinge.moviepilot.api

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * MoviePilot v3 API 客户端（OkHttp 手写请求，JSON 宽松解析）。
 *
 * v3 的 API 前缀由服务端 API_V1_STR 决定（默认 /api/v1，很多部署用 /api/v2），
 * 登录时按候选顺序探测，成功的那个前缀后续一直用。
 */
class MpApi(private val store: TokenStore) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val cookieJar = object : okhttp3.CookieJar {
        private val cache = HashMap<String, List<okhttp3.Cookie>>()
        override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
            if (cookies.isNotEmpty()) cache[url.host] = cookies
        }
        override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> =
            cache[url.host] ?: emptyList()
    }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        // 轻量接口一律 60s 上限：以前全局 300s，一个挂死的请求能把整条轮询串拖停 5 分钟
        .readTimeout(60, TimeUnit.SECONDS)
        // 遇到失效连接（典型是切换网络后连接池里那条旧连接）时换新连接重试。
        // OkHttp 默认就是 true，这里显式写出来，避免以后被误改成 false。
        .retryOnConnectionFailure(true)
        .cookieJar(cookieJar)
        .build()

    /** 流式搜索（SSE）专用：站点搜索可能长时间没有新数据，只有它需要放宽读超时 */
    private val streamClient = client.newBuilder()
        .readTimeout(300, TimeUnit.SECONDS)
        .build()

    // 重启后从本地恢复前缀，避免请求打到不存在的 /api/v2 导致 404
    var apiPrefix: String = store.savedPrefix ?: "/api/v1"
        private set

    /** 服务器连接状态：null=未知 / true=最近请求成功 / false=失败（供顶栏状态点） */
    var serverOnline: Boolean? by mutableStateOf(null)
        private set

    /**
     * 网络环境代号：默认网络切换时自增。页面把它当成 `LaunchedEffect` 的 key，
     * 一变化就自动重新加载 —— 不用重启 App，也不用去设置页手动「保存并连接」。
     */
    var netEpoch: Int by mutableStateOf(0)
        private set

    /**
     * 默认网络发生变化（切 WiFi / 开关数据 / 从蜂窝回到 WiFi）时由 [MainActivity] 的网络回调调用。
     *
     * 这里必须**清空连接池**：旧网络上的那条 TCP 连接在池子里看着还是「健康」的，下一个请求会先
     * 写进这条死连接、再一路等到 `readTimeout(60s)` 才抛 SocketTimeoutException；而这属于
     * 「请求已发出」的超时，OkHttp 的 `retryOnConnectionFailure` 不会重试它。
     * 用户看到的就是「切网后一直转圈、然后提示连接失败」。
     *
     * 自增 [netEpoch] 则让各页面立刻重新加载：原来页面只在「进页面时」加载一次，
     * 失败后错误就被写死在界面状态里，只能切 tab 或重启 App 才会恢复。
     */
    fun onNetworkChanged() {
        // streamClient 是 client.newBuilder() 出来的，共用同一个连接池，这里清一次两个都生效
        client.connectionPool.evictAll()
        netEpoch++
    }

    companion object {
        /** 候选前缀顺序：v3.1.5 实测默认就是 /api/v1，放首位省掉每次首登的两次无谓 404 */
        private val CANDIDATE_PREFIXES = listOf("/api/v1", "/api/v2", "/api/v3")

        /** 重登去重窗口：这段时间内的第二次 401 直接复用刚刷新的 token，不再打登录接口 */
        private const val RELOGIN_DEDUPE_MS = 3_000L
    }

    class ApiException(message: String) : Exception(message)

    /**
     * 账号已开启二步验证：密码校验通过、但还需要一次动态验证码。
     *
     * 宿主的 `POST /login/access-token` 在缺少 `otp_password` 时返回
     * 401 + `{success:false, message:"需要二次验证", data:{mfa_methods:["otp"]}}`，
     * 与「密码错误」同样是 401，所以这里单独抛出来，让调用方能把验证码行显出来，
     * 而不是把它当成密码错。
     *
     * @param methods 服务端给出的可用二次验证方式（宿主当前只有 `otp`）
     */
    class MfaRequiredException(val methods: List<String>) : Exception("需要二次验证")

    // ---------- 基础请求 ----------

    private fun url(base: String, path: String, params: Map<String, String?> = emptyMap()): String {
        val qs = params.filterValues { !it.isNullOrBlank() }
            .entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
        return base.trimEnd('/') + path + (if (qs.isEmpty()) "" else "?$qs")
    }

    /**
     * 统一用账号密码登录换来的 JWT（`Authorization: Bearer`）。
     *
     * v3.1.5 实测：App 用到的全部接口最终都走 `verify_token`，它同时接受 JWT、API Key
     * 与 API Token，三种凭据等效；插件侧 MaoyanPro 也声明了 `auth="bear"`。
     * 所以已不再需要 API 令牌，全部收敛到账号密码 + （可选）二步验证。
     */
    private fun authHeader(): Pair<String, String>? =
        store.token?.let { "Authorization" to "Bearer $it" }

    /**
     * 阻塞式执行请求并读回响应体。**必须**已经在 IO 线程上调用
     * （get/post/delete 与本类其它网络方法都统一用 withContext(Dispatchers.IO) 包住整段，
     * 这样连 JSON 解析也留在 IO 上，不再占主线程）。
     */
    private fun execute(request: Request): Pair<Int, String> =
        try {
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                // 能拿到 HTTP 响应 = 服务器可达。401/404/429/500 都是「连上了」，
                // 只是凭据/资源/限流问题，不能算离线；否则各接口交替返回非 2xx 时，
                // 顶栏会在「未连接 ↔ 已连接」之间反复横跳（日历页签连发多个 tmdb 请求时尤其明显）。
                serverOnline = true
                resp.code to body
            }
        } catch (e: Exception) {
            // 只有网络层异常（超时/连接被拒/断网/DNS 失败）才算离线
            serverOnline = false
            throw e
        }

    /** 401 静默重登的互斥锁 + 上次成功时间（时间只在锁内读写） */
    private val reloginMutex = kotlinx.coroutines.sync.Mutex()
    private var lastReloginAt = 0L

    /**
     * 认证失效自动恢复：JWT 过期时用记住的账号密码静默重登。
     * 成功返回 true；没有凭据或重登失败返回 false（调用方走 401 报错）。
     *
     * 并发安全：整段用 [reloginMutex] 串行化，并在成功后的 [RELOGIN_DEDUPE_MS] 窗口内直接返回 true。
     * 否则批量预检 6 并发、榜单 20 并发这类场景下，一旦 JWT 过期，这些请求会**同时**发
     * `/login/access-token`，用户看到的会是「登录尝试过于频繁」（宿主 429）而不是「过期了自动重登」。
     *
     * 注意：账号开了二步验证时静默重登必然失败（拿不到动态码），
     * 这时只能回到设置页让用户手输一次验证码，报错文案已说明这一点。
     */
    private suspend fun relogin(): Boolean = reloginMutex.withLock {
        val user = store.savedUsername ?: return@withLock false
        val pass = store.savedPassword ?: return@withLock false
        if (System.currentTimeMillis() - lastReloginAt < RELOGIN_DEDUPE_MS) return@withLock true
        try {
            login(store.baseUrl ?: return@withLock false, user, pass)
            lastReloginAt = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun get(base: String, path: String, params: Map<String, String?> = emptyMap()): Any =
        withContext(Dispatchers.IO) {
            fun request(): Request {
                val b = Request.Builder().url(url(base, path, params))
                authHeader()?.let { (k, v) -> b.header(k, v) } ?: throw ApiException("未登录")
                return b.get().build()
            }
            var (code, body) = execute(request())
            if (code == 401 && relogin()) {
                val retry = execute(request())
                code = retry.first; body = retry.second
            }
            if (code == 401) throw ApiException("登录已过期，请到「设置」重新登录（开了二步验证需再输一次动态码）")
            if (code != 200) throw ApiException("请求失败 (HTTP $code)")
            json.parseToJsonElement(body.ifEmpty { "null" })
        }

    private suspend fun post(
        base: String,
        path: String,
        body: JsonObject,
        params: Map<String, String?> = emptyMap(),
    ): JsonObject = withContext(Dispatchers.IO) {
        val payload = body.toString()
        fun request(): Request {
            val b = Request.Builder().url(url(base, path, params))
            authHeader()?.let { (k, v) -> b.header(k, v) }
            return b.post(payload.toRequestBody("application/json".toMediaType())).build()
        }
        var (code, respBody) = execute(request())
        if (code == 401 && relogin()) {
            val retry = execute(request())
            code = retry.first; respBody = retry.second
        }
        if (code != 200) throw ApiException("请求失败 (HTTP $code)")
        json.parseToJsonElement(respBody.ifEmpty { "{}" }).jsonObject
    }

    private suspend fun delete(base: String, path: String, params: Map<String, String?> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            fun request(): Request {
                val b = Request.Builder().url(url(base, path, params))
                authHeader()?.let { (k, v) -> b.header(k, v) } ?: throw ApiException("未登录")
                return b.delete().build()
            }
            var (code, body) = execute(request())
            if (code == 401 && relogin()) {
                val retry = execute(request())
                code = retry.first; body = retry.second
            }
            if (code != 200) throw ApiException("请求失败 (HTTP $code)")
            json.parseToJsonElement(body.ifEmpty { "{}" }).jsonObject
        }

    /** 解包统一响应包络：{success, message, data} 或裸数组/对象 */
    private fun unwrap(el: Any?): Any? {
        val obj = (el as? JsonObject) ?: return el
        return if (obj.containsKey("success") && obj.containsKey("data")) obj["data"] else obj
    }

    /** 解析 {success:false, message} 包络里的失败信息，无失败返回 null */
    private fun failMessage(obj: JsonObject): String? {
        val ok = (obj["success"] as? kotlinx.serialization.json.JsonPrimitive)
            ?.content?.toBooleanStrictOrNull() ?: true
        if (ok) return null
        return obj["message"]?.let { jsonPrimitiveSafe(it) }?.takeIf { it.isNotBlank() }
    }

    private fun jsonPrimitiveSafe(el: kotlinx.serialization.json.JsonElement): String =
        try { el.jsonPrimitive.content } catch (_: Exception) { el.toString() }

    // ---------- 登录 ----------

    /**
     * 启动探针：App 启动/升级后静默发一次轻量请求。
     * 作用：1) 顶栏连接状态立即生效（不再是「连接中」）
     *      2) 若 JWT 已失效，get() 内部会用记住的账号密码自动重登。
     */
    suspend fun probe() {
        // 用 runCatching 兜住「未登录」：切网回调可能在退出登录后触发，probe 不该把 App 带崩
        val base = runCatching { baseUrl() }.getOrNull() ?: return
        runCatching { get(base, "$apiPrefix/download/") }
    }

    /**
     * 账号密码登录并探测 API 前缀。成功后保存 JWT 与前缀。
     *
     * @param otpCode 二步验证动态码；账号开了二步验证时（见 [MfaRequiredException]）必须带上，
     *                没开则传 null 即可 —— 宿主允许一直是 null
     * @throws MfaRequiredException 密码正确但还缺动态码（调用方据此把验证码行显出来）
     */
    suspend fun login(
        serverBase: String,
        username: String,
        password: String,
        otpCode: String? = null,
    ) = withContext(Dispatchers.IO) {
        val base = serverBase.trim().trimEnd('/')
        val errors = mutableListOf<String>()
        val otp = otpCode?.takeIf { it.isNotBlank() }

        for (prefix in CANDIDATE_PREFIXES) {
            val form = buildString {
                append("username=").append(URLEncoder.encode(username, "UTF-8"))
                append("&password=").append(URLEncoder.encode(password, "UTF-8"))
                // 已开启二步验证时宿主要求带上 otp_password；空着不传，避免被当成「输了个错的码」
                if (otp != null) append("&otp_password=").append(URLEncoder.encode(otp, "UTF-8"))
            }
            val req = Request.Builder()
                .url(url(base, "$prefix/login/access-token"))
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            try {
                val (code, body) = execute(req)
                // 连接状态已由 execute() 统一判定（拿到响应即「已连接」），这里不再单独处理
                if (code != 200) {
                    // 401 有两种：需要二次验证（success=false + data.mfa_methods）与密码错误（detail）
                    if (code == 401) mfaMethodsOf(body)?.let { throw MfaRequiredException(it) }
                    if (code == 400 || code == 401) throw ApiException("用户名或密码错误")
                    if (code == 429) throw ApiException("登录尝试过于频繁，请稍后再试")
                    errors.add("$prefix: HTTP $code") // 前缀不对，试下一个
                    continue
                }
                val obj = json.parseToJsonElement(body).jsonObject
                val token = obj["access_token"]?.let { jsonPrimitiveSafe(it) }
                    ?: (unwrap(obj) as? JsonObject)?.get("access_token")?.let { jsonPrimitiveSafe(it) }
                    ?: throw ApiException("响应中没有 access_token")
                apiPrefix = prefix
                store.save(base, token, prefix)
                store.saveCredentials(username, password)
                return@withContext
            } catch (e: MfaRequiredException) {
                throw e
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                errors.add("$prefix: ${e.message}")
            }
        }
        throw ApiException("无法连接服务器（试过 ${CANDIDATE_PREFIXES.joinToString("/")}）\n${errors.firstOrNull() ?: ""}")
    }

    /**
     * 从 401 响应体里取二次验证方式；不是「需要二次验证」时返回 null。
     *
     * 宿主返回：`{"success":false,"message":"需要二次验证","data":{"mfa_methods":["otp"]}}`
     * 密码错误则是 FastAPI 默认的 `{"detail":"用户名或密码错误"}`，两者都靠这段区分。
     */
    private fun mfaMethodsOf(body: String): List<String>? = runCatching {
        val obj = json.parseToJsonElement(body).jsonObject
        if (failMessage(obj) != "需要二次验证") return@runCatching null
        val data = obj["data"] as? JsonObject ?: return@runCatching null
        data["mfa_methods"]?.jsonArray?.mapNotNull { jsonPrimitiveSafe(it) }?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun baseUrl(): String = store.baseUrl ?: throw ApiException("未登录")

    // ---------- 业务 ----------

    /** 搜索媒体（电影/电视剧），返回 MediaInfo 字典列表 */
    suspend fun searchMedia(title: String, page: Int = 1, count: Int = 30): List<JsonObject> {
        val data = unwrap(
            get(baseUrl(), "$apiPrefix/media/search", mapOf("title" to title, "page" to "$page", "count" to "$count"))
        )
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray
            ?: (data as? JsonObject)?.get("items")?.jsonArray
            ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    // ---------- 豆瓣榜单（服务端 DoubanChain 抓取，App 不直连豆瓣） ----------

    /**
     * 豆瓣国产剧集周榜（中国每周剧集口碑榜）。
     * 数据由服务端 DoubanChain 抓取，返回 MediaInfo 列表（与搜索媒体同结构）。
     */
    suspend fun doubanTvWeeklyChinese(page: Int = 1, count: Int = 10): List<JsonObject> =
        mediaList(
            unwrap(
                get(
                    baseUrl(),
                    "$apiPrefix/recommend/douban_tv_weekly_chinese",
                    mapOf("page" to "$page", "count" to "$count")
                )
            )
        )

    /** 豆瓣全球剧集周榜（国外剧集口碑榜） */
    suspend fun doubanTvWeeklyGlobal(page: Int = 1, count: Int = 10): List<JsonObject> =
        mediaList(
            unwrap(
                get(
                    baseUrl(),
                    "$apiPrefix/recommend/douban_tv_weekly_global",
                    mapOf("page" to "$page", "count" to "$count")
                )
            )
        )

    /**
     * 查询「该条目对应季」的首播日期。
     *
     * 豆瓣周榜列表接口不返回任何日期字段（只有 year 和 season），必须单独补全。
     * 注意不能直接用媒体详情的 release_date：那是**整剧第一季**的首播日
     * （如「百年孤独 第二季」详情 release_date=2026-08-05 恰好等于第 2 季首播日，
     * 但「流人 第六季」若走 TMDB 就是 2011 年第一季的日期），
     * 所以统一走 /media/seasons 取目标季的 air_date。
     *
     * @param season 榜单条目的季号（null 表示未分季，此时取最新一季的 air_date）
     * 返回 yyyy-MM-dd，查不到返回 null。
     */
    suspend fun mediaPremiereDate(
        mediaSource: String,
        mediaId: String,
        title: String?,
        year: String?,
        typeName: String,
        season: Int?,
    ): String? {
        // 1) 主路径：/media/seasons 里取目标季的 air_date（season 为空则取最大季号）
        val seasonDate = runCatching {
            val data = unwrap(
                get(
                    baseUrl(), "$apiPrefix/media/seasons",
                    mapOf(
                        "media_source" to mediaSource,
                        "media_id" to mediaId,
                        "title" to title,
                        "year" to year,
                    )
                )
            )
            val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray
                ?: (data as? JsonObject)?.get("items")?.jsonArray
                ?: return@runCatching null
            val rows = arr.mapNotNull { it as? JsonObject }
            val pick = season?.let { s -> rows.firstOrNull { it.long("season_number")?.toInt() == s } }
                ?: rows.maxByOrNull { it.long("season_number")?.toInt() ?: -1 }
            pick?.str("air_date")?.takeIf { it.isNotBlank() }
        }.getOrNull()
        if (!seasonDate.isNullOrBlank()) return seasonDate

        // 2) 兜底：媒体详情的 release_date / first_air_date（整剧维度）
        return runCatching {
            val data = unwrap(
                get(
                    baseUrl(),
                    "$apiPrefix/media/$mediaId",
                    mapOf("media_source" to mediaSource, "type_name" to typeName)
                )
            )
            val obj = data as? JsonObject ?: return@runCatching null
            obj.str("release_date")?.takeIf { it.isNotBlank() }
                ?: obj.str("first_air_date")?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    /**
     * 查媒体详情，一次取回搜索结果磁贴要补的两样东西：前 [actorLimit] 位主演 + 「当季」首播日。
     *
     * 合并成一次请求的原因：这两样都只有详情接口（/media/{id}）才有 —— 搜索接口的 `actors`
     * 恒为空数组、`first_air_date` 是整剧第一季的日期，而每张磁贴都要显示主演与该季首播日，
     * 分开查会让列表滚动时请求数翻倍。
     *
     * @param typeName 宿主媒体类型（电影/电视剧/音乐/系列；综艺走「电视剧」）
     * 拿不到（媒体不存在 / 网络失败）返回空的 [MediaTileInfo]，调用方自行降级。
     */
    suspend fun mediaTileInfo(
        mediaSource: String,
        mediaId: String,
        title: String?,
        year: String?,
        typeName: String,
        actorLimit: Int = 3,
    ): MediaTileInfo = runCatching {
        val data = unwrap(
            get(
                baseUrl(), "$apiPrefix/media/$mediaId",
                mapOf(
                    "media_source" to mediaSource,
                    "type_name" to typeName,
                    "title" to title,
                    "year" to year,
                )
            )
        )
        val obj = data as? JsonObject ?: return@runCatching MediaTileInfo()
        val actors = obj["actors"]?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.str("name")?.takeIf { n -> n.isNotBlank() } }
            ?.distinct()
            ?.take(actorLimit)
            .orEmpty()
        MediaTileInfo(actors = actors, seasonDate = latestSeasonAirDate(obj))
    }.getOrDefault(MediaTileInfo())

    // ---------- 猫眼榜单（数据取自服务端自建插件 MaoyanPro「猫眼榜单」） ----------

    /**
     * 读取服务端 MaoyanPro 插件抓取的猫眼榜单快照。
     *
     * 说明：猫眼热度接口（/i/api/encrypt/dashboard/webHeatData）有美团 H5guard 反爬，
     * App 直连一律 403，只能复用服务端插件用无头浏览器抓到的结果。
     * 插件对外暴露 `GET /plugin/MaoyanPro/rank`，默认走宿主 API 令牌鉴权（X-API-KEY），
     * 因此无需 superuser 或账号密码登录态。
     *
     * @param boardType 榜单类型：`web-all` 剧集热度（电视剧+网络剧）、`zongyi` 综艺热度、
     *                  `movie` 电影票房、`web-movie` 网络电影
     * @param limit 每个榜单最多返回的条目数
     * @param platform 猫眼 platformType 平台细分：空串 = 只取全网榜（快照里平台细分榜与全网榜
     *                 同 type，不传会一起返回导致解析错位）；`3`=腾讯视频 `2`=爱奇艺
     *                 `7`=芒果TV `1`=优酷。null = 不筛选（返回快照里该 type 的全部榜单）
     * @return 榜单条目（含 rank/title/platform/release_info/poster/media_source/media_id）
     */
    suspend fun maoyanRank(boardType: String, limit: Int = 10, platform: String? = null): List<JsonObject> {
        val params = linkedMapOf<String, String?>(
            "board_type" to boardType,
            "limit" to limit.toString(),
        )
        if (platform != null) params["platform"] = platform
        val data = unwrap(
            get(baseUrl(), "$apiPrefix/plugin/MaoyanPro/rank", params)
        )
        val obj = data as? JsonObject ?: return emptyList()
        val boards = obj["boards"]?.jsonArray ?: return emptyList()
        val board = boards.mapNotNull { it as? JsonObject }
            .firstOrNull { it.str("type") == boardType }
            ?: return emptyList()
        return (board["items"]?.jsonArray ?: return emptyList()).mapNotNull { it as? JsonObject }
    }

    /** 榜单快照的更新时间（yyyy-MM-dd HH:mm:ss），用于界面上标注「更新」时间 */
    suspend fun maoyanRankUpdatedAt(): String? {
        val data = unwrap(get(baseUrl(), "$apiPrefix/plugin/MaoyanPro/rank", emptyMap()))
        val obj = data as? JsonObject ?: return null
        return obj.str("updated_at")?.takeIf { it.isNotBlank() }
    }

    /** 解析 MediaInfo 列表（裸数组或 {items:[...]} 包装） */
    private fun mediaList(data: Any?): List<JsonObject> {
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray
            ?: (data as? JsonObject)?.get("items")?.jsonArray
            ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    /** 按关键词直接搜站点资源，返回 Context 字典列表 */
    suspend fun searchTorrentsByTitle(keyword: String): List<JsonObject> =
        contexts(
            unwrap(get(baseUrl(), "$apiPrefix/search/title", mapOf("keyword" to keyword)))
        )

    /** 按媒体精确搜站点资源 */
    suspend fun searchTorrentsByMedia(media: JsonObject, season: Int? = null): List<JsonObject> {
        val mediaId = media.str("media_id") ?: throw ApiException("缺少 media_id")
        val params = linkedMapOf<String, String?>(
            "media_source" to media.str("media_source"),
            "mtype" to media.str("type"),
            "season" to season?.toString(),
        )
        return contexts(
            unwrap(get(baseUrl(), "$apiPrefix/search/media/$mediaId", params))
        )
    }

    // ---------- 流式搜索（SSE，各站点增量返回） ----------

    /**
     * 服务端启用的站点列表（用于计算搜索进度总数）。失败返回空列表。
     */
    suspend fun listSearchSites(): List<Pair<Int, String>> = try {
        val data = unwrap(get(baseUrl(), "$apiPrefix/site/agent", mapOf("status" to "active")))
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray
            ?: return emptyList()
        arr.mapNotNull { el ->
            (el as? JsonObject)?.let { o ->
                o.long("id")?.toInt()?.let { id -> id to (o.str("name") ?: "站点$id") }
            }
        }
    } catch (_: Exception) {
        emptyList()
    }

    /**
     * SSE 流式搜索。不传 sites 参数 → 默认用服务端勾选的搜索站点。
     * onResults 每批增量结果回调一次；onProgress(已完成站点数, 总站点数, 最近站点名)。
     */
    private suspend fun searchTorrentsSse(
        path: String,
        params: Map<String, String?>,
        onResults: (List<JsonObject>) -> Unit,
        onProgress: (Int, Int, String?) -> Unit,
    ): List<JsonObject> = withContext(Dispatchers.IO) {
        val totalSites = listSearchSites().size
        if (totalSites > 0) onProgress(0, totalSites, null)
        val completed = LinkedHashSet<String>()
        val results = LinkedHashMap<String, JsonObject>()
        var finished = false
        var lastError: String? = null

        fun siteKey(ctx: JsonObject): String? {
            val t = ctx["torrent_info"] as? JsonObject ?: return null
            return t.str("site_id") ?: t.str("site") ?: t.str("site_name")
        }

        fun handleEvent(data: String) {
            val obj = try { json.parseToJsonElement(data).jsonObject } catch (_: Exception) { return }
            when (obj["type"]?.let { jsonPrimitiveSafe(it) }) {
                "replace", "append" -> {
                    val arr = (obj["items"] as? kotlinx.serialization.json.JsonArray)?.jsonArray
                    arr?.mapNotNull { it as? JsonObject }?.forEach { ctx ->
                        val key = ctx["torrent_info"]?.let { t ->
                            (t as? JsonObject)?.str("enclosure") ?: siteKey(ctx)
                        } ?: ctx.hashCode().toString()
                        results[key] = ctx
                        siteKey(ctx)?.let { completed.add(it) }
                    }
                    if (arr != null) {
                        val last = arr.lastOrNull()?.let { e ->
                            (e as? JsonObject)?.let { c ->
                                (c["torrent_info"] as? JsonObject)?.str("site")?.takeIf { it.isNotBlank() }
                            }
                        }
                        onProgress(completed.size, totalSites, last)
                        onResults(results.values.toList())
                    }
                }
                "done" -> finished = true
                "error" -> {
                    lastError = obj["message"]?.let { jsonPrimitiveSafe(it) } ?: "搜索失败"
                    finished = true
                }
                // heartbeat / 其他：忽略
            }
        }

        val req = Request.Builder()
            .url(url(baseUrl(), path, params))
            .apply { authHeader()?.let { (k, v) -> header(k, v) } }
            .header("Accept", "text/event-stream")
            .get()
            .build()

        streamClient.newCall(req).execute().use { resp ->
            if (resp.code == 401) throw ApiException("登录已过期，请重新登录")
            if (resp.code != 200) throw ApiException("流式搜索失败 (HTTP ${resp.code})")
            val source = resp.body?.source() ?: throw ApiException("空响应")
            val sb = StringBuilder()
            while (!finished) {
                val line = source.readUtf8Line() ?: break
                when {
                    line.startsWith("data:") -> sb.append(line.removePrefix("data:").trim())
                    line.isEmpty() && sb.isNotEmpty() -> {
                        handleEvent(sb.toString())
                        sb.clear()
                    }
                }
            }
            if (sb.isNotEmpty()) handleEvent(sb.toString())
        }
        lastError?.let { throw ApiException(it) }
        results.values.toList()
    }

    /** 流式精确搜索（SSE） */
    suspend fun searchTorrentsByMediaStream(
        media: JsonObject,
        season: Int?,
        onResults: (List<JsonObject>) -> Unit,
        onProgress: (Int, Int, String?) -> Unit,
    ): List<JsonObject> {
        val mediaId = media.str("media_id") ?: throw ApiException("缺少 media_id")
        val params = linkedMapOf<String, String?>(
            "media_source" to media.str("media_source"),
            "mtype" to media.str("type"),
            "season" to season?.toString(),
        )
        return searchTorrentsSse("$apiPrefix/search/media/$mediaId/stream", params, onResults, onProgress)
    }

    /** 流式按标题搜索（SSE） */
    suspend fun searchTorrentsByTitleStream(
        keyword: String,
        onResults: (List<JsonObject>) -> Unit,
        onProgress: (Int, Int, String?) -> Unit,
    ): List<JsonObject> =
        searchTorrentsSse(
            "$apiPrefix/search/title/stream",
            mapOf("keyword" to keyword), onResults, onProgress
        )

    private fun contexts(el: Any?): List<JsonObject> {
        val arr = (el as? kotlinx.serialization.json.JsonArray)?.jsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    /** 下载：把搜索结果的 media_info / torrent_info 原样回传 */
    suspend fun download(context: JsonObject): String {
        val media = context["media_info"] as? JsonObject
        val torrent = context["torrent_info"] as? JsonObject
            ?: throw ApiException("该结果缺少种子信息，无法下载")
        if (media == null) {
            // 无媒体信息时走 /download/add
            val body = buildJsonObject {
                put("torrent_in", torrent)
                put("allow_unrecognized", true)
            }
            val resp = post(baseUrl(), "$apiPrefix/download/add", body)
            failMessage(resp)?.let { throw ApiException(it) }
            return resp["message"]?.let { jsonPrimitiveSafe(it) }?.takeIf { it.isNotBlank() } ?: "已加入下载"
        }
        val body = buildJsonObject {
            put("media_in", media)
            put("torrent_in", torrent)
        }
        val resp = post(baseUrl(), "$apiPrefix/download/", body)
        failMessage(resp)?.let { throw ApiException(it) }
        return "已加入下载"
    }

    /**
     * 控制下载任务（v3 接口）：
     * op="stop" → GET /download/stop/{hash}，op="start" → GET /download/start/{hash}
     */
    suspend fun controlDownload(hash: String, op: String) {
        val resp = get(baseUrl(), "$apiPrefix/download/$op/$hash")
        if (resp is JsonObject) failMessage(resp)?.let { throw ApiException(it) }
    }

    /** 删除下载任务：DELETE /download/{hash}（v3 不支持同时删文件，只删任务） */
    suspend fun deleteDownload(hash: String) {
        val resp = delete(baseUrl(), "$apiPrefix/download/$hash")
        if (resp is JsonObject) failMessage(resp)?.let { throw ApiException(it) }
    }

    /** 新增订阅。season 为空表示整部/电影 */
    suspend fun subscribe(media: JsonObject, season: Int?): String {
        val type = when (media.str("type")) {
            "tv", "电视剧" -> "电视剧"
            else -> "电影"
        }
        val body = buildJsonObject {
            put("name", media.str("title") ?: "")
            put("type", type)
            media.str("media_source")?.let { put("media_source", it) }
            media.str("media_id")?.let { put("media_id", it) }
            media.str("year")?.let { put("year", it) }
            media.str("tmdb_id")?.toIntOrNull()?.let { put("tmdbid", it) }
            season?.let { put("season", it) }
        }
        val resp = post(baseUrl(), "$apiPrefix/subscribe/", body)
        failMessage(resp)?.let { throw ApiException(it) }
        return "订阅成功"
    }

    /**
     * 我的订阅列表，按订阅时间由新到旧排序（date 格式 "yyyy-MM-dd HH:mm:ss"，可字典序比较；
     * 无 date 的老数据排最后）
     */
    suspend fun listSubscribes(): List<JsonObject> {
        val data = unwrap(get(baseUrl(), "$apiPrefix/subscribe/"))
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
            .sortedByDescending { it.str("date") ?: "" }
    }

    suspend fun deleteSubscribe(id: Long) {
        val resp = delete(baseUrl(), "$apiPrefix/subscribe/$id")
        failMessage(resp)?.let { throw ApiException(it) }
    }

    // ---------- 订阅页：工作流 / 音乐 / 日历 ----------

    /** 工作流列表（GET /workflow/，包络 data 为数组） */
    suspend fun listWorkflows(): List<JsonObject> {
        val data = unwrap(get(baseUrl(), "$apiPrefix/workflow/"))
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    /** 执行工作流：POST /workflow/{id}/run */
    suspend fun runWorkflow(id: Long) {
        val resp = post(baseUrl(), "$apiPrefix/workflow/$id/run", buildJsonObject {})
        failMessage(resp)?.let { throw ApiException(it) }
    }

    /** 启用工作流：POST /workflow/{id}/start */
    suspend fun startWorkflow(id: Long) {
        val resp = post(baseUrl(), "$apiPrefix/workflow/$id/start", buildJsonObject {})
        failMessage(resp)?.let { throw ApiException(it) }
    }

    /** 停用工作流：POST /workflow/{id}/pause */
    suspend fun pauseWorkflow(id: Long) {
        val resp = post(baseUrl(), "$apiPrefix/workflow/$id/pause", buildJsonObject {})
        failMessage(resp)?.let { throw ApiException(it) }
    }

    /** 查询媒体详情（GET /media/{id}，用于订阅日历里电影的上映日 release_date） */
    suspend fun mediaDetail(mediaId: String, typeName: String): JsonObject? {
        val data = unwrap(
            get(
                baseUrl(), "$apiPrefix/media/$mediaId",
                mapOf("type_name" to typeName)
            )
        )
        return data as? JsonObject
    }

    /** 某季所有集（GET /tmdb/{tmdbid}/{season}，含每集 air_date/episode_number/name，用于订阅日历） */
    suspend fun tmdbSeasonEpisodes(tmdbId: Long, season: Int): List<JsonObject> {
        val data = unwrap(get(baseUrl(), "$apiPrefix/tmdb/$tmdbId/$season"))
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    /**
     * 某季**已播出**的集号列表（用每集 air_date 与今天比较，升序）。
     *
     * 为什么要自己算：TMDB 的 `episode_count`（/media/seasons 返回的那个）是**整季集数**，
     * 把还没播的集也算进去了 —— 正在追的剧会列出十几个没有资源的「死选项」。
     *
     * 每集 air_date 有三种状态，实测 TMDB（以「玉簟秋」S1 为例，今天 2026-10-16）：
     * - 有日期且 ≤ 今天 → **已播出**，列入（E1–E12）；
     * - 有日期但在将来 → **未播出**，排除（E13/E14 是 10-17 / 10-18）；
     * - **日期为空**（尚未定档）→ **未播出**，排除（E15–E40 全是空日期）。
     *
     * ⚠️ 早期版本把「空日期」当已播出（怕藏掉能下的），结果「玉簟秋」26 个未定档集
     * 被算成已播 → 徽标显示 38/40，而实际只播了 12 集。
     *
     * @return 已播出的集号；**查不到时返回 null**（接口异常 / 整季一集都没带 air_date，
     *         此时无从判断，调用方必须退化为「显示全部集」，绝不能把能下的集藏起来）。
     *         确实一集都没播（全在将来）时返回空列表。
     */
    suspend fun tmdbSeasonAiredEpisodes(tmdbId: Long, season: Int): List<Int>? {
        val rows = runCatching { tmdbSeasonEpisodes(tmdbId, season) }.getOrNull() ?: return null
        if (rows.isEmpty()) return null
        val today = java.time.LocalDate.now()
        val dated = rows.mapNotNull { o ->
            val n = o.long("episode_number")?.toInt() ?: return@mapNotNull null
            val d = o.str("air_date")?.take(10)
                ?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() }
            n to d
        }
        if (dated.isEmpty()) return null
        // 整季一集日期都没给 → 数据源不提供日期，无从判断 → 交给调用方退化（宁可全列，不可藏）
        if (dated.all { it.second == null }) return null
        // 只有「有日期且不晚于今天」才算已播出；空日期 = 未定档 = 未播出
        return dated.filter { (_, d) -> d != null && !d.isAfter(today) }
            .map { it.first }.filter { it > 0 }.distinct().sorted()
    }

    /** 剧集各季集数 {季号: 集数}；解析失败返回空 Map（接口 GET /media/seasons） */
    suspend fun mediaSeasonEpisodes(media: JsonObject): Map<Int, Int> {
        val mediaId = media.str("media_id") ?: media.str("tmdb_id") ?: return emptyMap()
        return try {
            val data = unwrap(
                get(
                    baseUrl(), "$apiPrefix/media/seasons",
                    mapOf(
                        "media_source" to media.str("media_source"),
                        "media_id" to mediaId,
                        "title" to media.str("title"),
                        "year" to media.str("year"),
                    )
                )
            )
            val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray
                ?: (data as? JsonObject)?.get("items")?.jsonArray
                ?: return emptyMap()
            arr.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val s = o.long("season_number")?.toInt() ?: return@mapNotNull null
                val c = o.long("episode_count")?.toInt() ?: return@mapNotNull null
                if (s >= 0 && c > 0) s to c else null
            }.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ---------- 下载 / 媒体库入库 ----------

    /** 正在下载的任务列表（progress/state/dlspeed/left_time/title/name/size） */
    suspend fun listDownloading(): List<JsonObject> {
        val data = unwrap(get(baseUrl(), "$apiPrefix/download/"))
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray
            ?: (data as? JsonObject)?.get("items")?.jsonArray
            ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    /** 下载历史（title/year/torrent_name/torrent_site/download_hash/date） */
    suspend fun listDownloadHistory(page: Int = 1, count: Int = 30): List<JsonObject> {
        val data = unwrap(
            get(baseUrl(), "$apiPrefix/history/download", mapOf("page" to "$page", "count" to "$count"))
        )
        return historyArray(data)
    }

    /**
     * 最近 [days] 天（含今天）的下载历史，按下载时间倒序。
     *
     * 宿主的 `/history/download` 只支持 `page`/`count`、**不能按日期过滤**，所以从第一页开始往后翻，
     * 直到出现早于窗口起点的记录（说明更早的都不在窗口内）或取不满一页为止，再在本地按日期截断。
     *
     * 下载页列表用它取数：整理失败记录的 `date` 是**整理时间**，其对应的下载记录可能排在最近
     * 几十条之外（实测「征途」排在第 40 位），用固定条数（30 条）会取不到卡片、看不到整理进度。
     */
    suspend fun listDownloadHistorySince(
        days: Int = 3,
        pageSize: Int = 100,
        maxPages: Int = 5,
        minKeep: Int = 30,
        mustInclude: Set<String> = emptySet(),
    ): List<JsonObject> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1).coerceAtLeast(0))
        val start = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val out = ArrayList<JsonObject>()
        var page = 1
        while (page <= maxPages) {
            val batch = listDownloadHistory(page = page, count = pageSize)
            if (batch.isEmpty()) break
            out += batch.filter { (it.str("date")?.take(10) ?: "") >= start }
            if (batch.size < pageSize) break
            // 已经翻出窗口（出现更早的记录）→ 后面的只会更早，停止
            if (batch.any { (it.str("date")?.take(10) ?: "") < start }) break
            // 提前停止：列表要的条数已经够了（[minKeep]），且「整理失败要补进列表」的那些
            // 下载哈希（[mustInclude]）也都在里面 —— 就不必为了确认窗口边界再多拉一页（约 125KB）。
            if (out.size >= minKeep &&
                mustInclude.all { h -> out.any { it.str("download_hash")?.lowercase() == h } }
            ) break
            page++
        }
        return out
    }

    /**
     * 下载哈希 → 媒体库入库记录（status=true 成功 / false 失败+errmsg，dest 目标路径）。
     * TransferHistory 里有 download_hash 字段，按它关联下载记录。
     */
    suspend fun transferMapByDownloadHash(): Map<String, JsonObject> {
        val data = unwrap(
            get(baseUrl(), "$apiPrefix/history/transfer", mapOf("page" to "1", "count" to "100"))
        )
        val map = LinkedHashMap<String, JsonObject>()
        historyArray(data).forEach { rec ->
            val hash = rec.str("download_hash")?.takeIf { it.isNotBlank() } ?: return@forEach
            // 后端按时间倒序返回，只保留最新一条
            if (!map.containsKey(hash)) map[hash] = rec
        }
        return map
    }

    /**
     * 最近 [days] 天的整理记录（时间倒序），**按整理记录粒度**：一集 / 一个媒体就是一条，
     * 不做下载哈希去重。
     *
     * 与 [transferMapByDownloadHash] 的区别：后者按 download_hash 只留最新一条（卡片状态用），
     * 会把「整季包每集各一条记录」压成一条。下载页统计「每天整理失败的条数」必须用本方法，
     * 否则 26 条失败记录会被算成 3 条（按种子只算一条）。
     *
     * 载荷自适应：一条整理记录约 2.6KB（含完整 fileitem 与结算回执），固定拉 500 条要 1.3MB/次。
     * 这里改成按窗口翻页 —— 宿主按时间倒序返回，一旦翻到早于窗口起点的记录就停。实测三天窗口
     * 283 条，一次 300 条的请求就能覆盖（约 0.8MB）；[maxPages] 只作兜底，防止某天整理量暴增时取不全。
     */
    suspend fun transferRecords(
        days: Int = 3,
        pageSize: Int = 300,
        maxPages: Int = 3,
    ): List<JsonObject> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(days - 1).coerceAtLeast(0))
        val start = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
        val out = ArrayList<JsonObject>()
        var page = 1
        while (page <= maxPages) {
            val batch = historyArray(
                unwrap(
                    get(
                        baseUrl(), "$apiPrefix/history/transfer",
                        mapOf("page" to "$page", "count" to "$pageSize")
                    )
                )
            )
            if (batch.isEmpty()) break
            out += batch.filter { (it.str("date")?.take(10) ?: "") >= start }
            if (batch.size < pageSize) break
            // 已经翻出窗口（出现更早的记录）→ 后面的只会更早，停止
            if (batch.any { (it.str("date")?.take(10) ?: "") < start }) break
            page++
        }
        return out
    }

    /**
     * 重新整理单条整理记录：POST /history/transfer/{id}/ai-redo。
     *
     * v3 里失败的整理记录带 transfer_task_id 时走「持久任务重试」（不需要智能助手），
     * 否则才回落到 AI 重新整理；两种情况都返回 progress_key。返回后端提示语。
     */
    suspend fun redoTransfer(historyId: Long): String {
        val resp = post(
            baseUrl(), "$apiPrefix/history/transfer/$historyId/ai-redo",
            buildJsonObject { }
        )
        failMessage(resp)?.let { throw ApiException(it) }
        return resp.str("message")?.takeIf { it.isNotBlank() } ?: "已提交重新整理"
    }

    /**
     * 「自动匹配路径」预检：POST /transfer/manual/target-path，body {"logid":...}。
     *
     * 只读接口（不执行整理），让服务端按当前目录规则重新匹配该记录的目的目录。
     * 返回含 `target_storage` / `target_path` / `transfer_type` / `scrape` 的对象，匹配不到返回 null。
     */
    suspend fun matchTransferTargetPath(historyId: Long): JsonObject? {
        val resp = post(
            baseUrl(), "$apiPrefix/transfer/manual/target-path",
            buildJsonObject { put("logid", historyId) }
        )
        failMessage(resp)?.let { throw ApiException(it) }
        return (resp["data"] as? JsonObject)?.takeIf {
            it.str("target_path")?.isNotBlank() == true
        }
    }

    /**
     * 重新整理（手动整理）一条记录，后台执行：POST /transfer/manual?background=true。
     *
     * - [target] 为 null → **原有路径**：沿用记录中的识别信息（`from_history`），
     *   不指定目的目录，由服务端按源目录匹配原目录规则。
     * - [target] 为 [matchTransferTargetPath] 的结果 → **自动匹配路径**：
     *   显式提交重新匹配到的媒体库目录、目标存储与整理方式。
     *
     * 返回后端提示语（成功/失败都可能带具体原因）。
     */
    suspend fun redoTransferManual(historyId: Long, target: JsonObject? = null): String {
        val body = buildJsonObject {
            put("logid", historyId)
            // 复用记录里已识别的媒体/季集信息，避免重新识别出错
            put("from_history", true)
            target?.let { putTransferTarget(it) }
        }
        val resp = post(
            baseUrl(), "$apiPrefix/transfer/manual", body,
            mapOf("background" to "true")
        )
        failMessage(resp)?.let { throw ApiException(it) }
        return resp.str("message")?.takeIf { it.isNotBlank() } ?: "已提交整理，后台处理中"
    }

    /**
     * ⚠️ 不要用 `logids` 批量提交 manual 整理（故不提供批量方法）：
     * 宿主 `_route_manual_transfer` 的批量分支只还原源文件项（`restore_manual_transfer_history_batch`），
     * **不会**像单条 `logid` 分支那样 `restore_manual_transfer_history_metadata` 恢复媒体身份，
     * 整批会失败在「手动整理需要同时提供 media_source 和 media_id」（实测 65 条全失败）。
     * 批量整理请逐条调 [redoTransferManual]。
     */

    /** 把「自动匹配路径」的预检结果写进整理请求体（只写服务端认识的字段） */
    private fun JsonObjectBuilder.putTransferTarget(target: JsonObject) {
        target.str("target_storage")?.takeIf { it.isNotBlank() }?.let { put("target_storage", it) }
        target.str("target_path")?.takeIf { it.isNotBlank() }?.let { put("target_path", it) }
        target.str("transfer_type")?.takeIf { it.isNotBlank() }?.let { put("transfer_type", it) }
        target.str("scrape")?.toBooleanStrictOrNull()?.let { put("scrape", it) }
    }

    /**
     * 批量重新整理：POST /history/transfer/ai-redo，body {"history_ids":[...]}。
     * 用于「一键重新整理当日失败的媒体」。返回后端提示语。
     */
    suspend fun redoTransferBatch(historyIds: List<Long>): String {
        if (historyIds.isEmpty()) return "没有需要重新整理的记录"
        val body = buildJsonObject {
            put("history_ids", buildJsonArray { historyIds.forEach { add(it) } })
        }
        val resp = post(baseUrl(), "$apiPrefix/history/transfer/ai-redo", body)
        failMessage(resp)?.let { throw ApiException(it) }
        return resp.str("message")?.takeIf { it.isNotBlank() } ?: "已提交重新整理"
    }

    /**
     * 待人工复核的整理任务：GET /transfer/tasks/manual-reviews?state=manual_review。
     *
     * v3 把「整理步骤结果不确定」的文件停在这里等人确认（本机的都是 `ensure_target_directory`
     * 创建目标目录失败，计划里用的是连不上的 smb 存储）。这些文件**没有整理历史记录**
     * （重试前旧记录被摘掉、也没写回），所以下载页的卡片上看不到它们，只能从本接口取。
     *
     * 每条含 `task_id` / `source{storage,path}` / `step{operation_id,kind,intent{storage,path}}`。
     *
     * 返回体带 `total`，所以这里按 total 翻页取全（原先只取第一页 100 条，超过 100 条时
     * 列表和入口胶囊上的数字都不全）；[pageSize] 上限 200（宿主限制），[maxPages] 兜底。
     */
    suspend fun manualReviews(pageSize: Int = 100, maxPages: Int = 5): List<JsonObject> {
        val out = ArrayList<JsonObject>()
        var page = 1
        while (page <= maxPages) {
            val data = unwrap(
                get(
                    baseUrl(), "$apiPrefix/transfer/tasks/manual-reviews",
                    mapOf("state" to "manual_review", "page" to "$page", "page_size" to "$pageSize")
                )
            )
            val items = historyArray(data)
            out += items
            // 宿主不回 total 时按「取不满一页」判定结束
            val total = (data as? JsonObject)?.long("total")?.toInt() ?: items.size
            if (items.isEmpty() || items.size < pageSize || out.size >= total) break
            page++
        }
        return out
    }

    /**
     * 按**源文件**预检目的目录：「自动匹配目录」用。
     * POST /transfer/manual/target-path，body `{fileitem:{storage,path,type}}`。
     *
     * 与 [matchTransferTargetPath] 是同一个接口，只是待复核的文件没有 logid，只能传源文件项。
     * 只读（不执行整理），返回 `target_storage` / `target_path` 等；匹配不到返回 null。
     */
    suspend fun matchTransferTargetPathForFile(storage: String, path: String): JsonObject? {
        val resp = post(
            baseUrl(), "$apiPrefix/transfer/manual/target-path",
            buildJsonObject { put("fileitem", fileItemOf(storage, path)) }
        )
        failMessage(resp)?.let { throw ApiException(it) }
        return (resp["data"] as? JsonObject)?.takeIf {
            it.str("target_path")?.isNotBlank() == true
        }
    }

    /**
     * 让宿主**按当前目录规则**重新整理一个待复核的源文件：POST /transfer/manual?background=true，
     * body `{fileitem:{storage,path,type}, ...target}`。
     *
     * 为什么用 fileitem 而不是 logid：待复核的文件没有整理历史记录（拿不到 logid）。
     * [target] 传 [matchTransferTargetPathForFile] 的预检结果时，会显式带上重新匹配到的媒体库目录
     * 与存储，**绕开宿主计划里那个连不上的存储**（实测：这样提交的文件能真正入库，
     * 且宿主的复核队列会同步少掉对应条目）。返回后端提示语。
     */
    suspend fun manualTransferFile(storage: String, path: String, target: JsonObject? = null): String {
        val body = buildJsonObject {
            put("fileitem", fileItemOf(storage, path))
            target?.let { putTransferTarget(it) }
        }
        val resp = post(
            baseUrl(), "$apiPrefix/transfer/manual", body,
            mapOf("background" to "true")
        )
        failMessage(resp)?.let { throw ApiException(it) }
        return resp.str("message")?.takeIf { it.isNotBlank() } ?: "已提交整理，后台处理中"
    }

    /**
     * 让宿主重试一条待人工复核的任务（**沿用该任务既定的目标目录**）：
     * POST /transfer/tasks/{taskId}/manual-review，body `{operation_id, decision:"not_applied"}`。
     *
     * 人工复核接口只接受 `not_applied`（这类步骤判 `applied` 会被宿主拒绝），它把这一步标记为
     * 「确实没执行」，任务回到 `retry_wait` 由宿主按既定规划重新整理。返回后端提示语。
     */
    suspend fun manualReviewRetry(taskId: String, operationId: String): String {
        val resp = post(
            baseUrl(), "$apiPrefix/transfer/tasks/$taskId/manual-review",
            buildJsonObject {
                put("operation_id", operationId)
                put("decision", "not_applied")
                put("reason", "MoviePilot App：重新整理")
            }
        )
        failMessage(resp)?.let { throw ApiException(it) }
        return resp.str("message")?.takeIf { it.isNotBlank() } ?: "已提交重新整理"
    }

    /** 手动整理/预检共用的源文件项（宿主 FileItem 只认 storage/path/type） */
    private fun fileItemOf(storage: String, path: String) = buildJsonObject {
        put("storage", storage)
        put("path", path)
        put("type", "file")
    }

    private fun historyArray(data: Any?): List<JsonObject> {
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray
            ?: (data as? JsonObject)?.get("list")?.jsonArray
            ?: (data as? JsonObject)?.get("items")?.jsonArray
            ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    /** resource cookie（登录后服务器下发，图片代理等资源接口用）；令牌模式下没有，返回 null */
    fun resourceCookie(): String? {
        val base = baseUrl() ?: return null
        val url = base.toHttpUrlOrNull() ?: return null
        val cookies = cookieJar.loadForRequest(url)
        return if (cookies.isEmpty()) null else cookies.joinToString("; ") { "${it.name}=${it.value}" }
    }

    /**
     * 海报走服务器代理（/system/cache/image，服务器端带翻墙抓图并缓存）。
     * 实测需 resource cookie（无 cookie 401），缓存后 42ms。img/proxy 端点是 422 参数错误，不可用。
     */
    fun imgProxyUrl(raw: String?): String? {
        val p = posterUrlOf(raw) ?: return null
        if (resourceCookie() == null) return null
        val base = baseUrl() ?: return null
        return "$base$apiPrefix/system/cache/image?url=${android.net.Uri.encode(p)}"
    }

    /** 刷新登录（resource cookie 30 分钟过期后重取）。与 401 重登共用同一把锁，防止多图并发重复登录 */
    suspend fun refreshSession(): Boolean = relogin()
}

/**
 * 搜索结果磁贴需要的补充信息（由 [MpApi.mediaTileInfo] 一次详情请求取回）。
 *
 * @param actors 前几位主演名字（搜索结果接口不带演员表，只能查详情）
 * @param seasonDate 「当季」首播日 yyyy-MM-dd：多季剧集取最新一季的 air_date，
 *                   单季剧集与整剧第一季一致；取不到时为 null，调用方回落 first_air_date。
 */
data class MediaTileInfo(
    val actors: List<String> = emptyList(),
    val seasonDate: String? = null,
)

/**
 * 从媒体详情里取「当季」（季号最大的那一季）的首播日。
 *
 * 为什么不能用详情/搜索结果里的 `first_air_date`：那是**整剧第一季**的首播日。
 * 例：「现在就出发」first_air_date = 2023-08-13（第 1 季），而榜单上的第 4 季是 2026-10-10。
 * 季信息在详情的 `season_info` 数组里（每项含 season_number / air_date）；
 * `season_number = 0` 是「特别篇」，不参与比较；air_date 为空的季跳过。
 * 取不到返回 null，调用方自行回落。
 */
private fun latestSeasonAirDate(detail: JsonObject): String? {
    val arr = detail["season_info"]?.jsonArray ?: return null
    return arr.mapNotNull { it as? JsonObject }
        .filter { (it.long("season_number")?.toInt() ?: 0) > 0 }
        .filter { !it.str("air_date").isNullOrBlank() }
        .maxByOrNull { it.long("season_number")?.toInt() ?: 0 }
        ?.str("air_date")
}

/** JsonObject 字段宽松取值扩展 */
fun JsonObject.str(key: String): String? {
    val el = this[key] ?: return null
    return try {
        when {
            el is kotlinx.serialization.json.JsonNull -> null
            el is kotlinx.serialization.json.JsonPrimitive -> el.content.takeIf { it != "null" }
            else -> null
        }
    } catch (_: Exception) { null }
}

fun JsonObject.long(key: String): Long? = str(key)?.toDoubleOrNull()?.toLong()

/** 是否剧集（搜索资源前需要选季） */
fun JsonObject.isTv(): Boolean = str("type") in listOf("tv", "电视剧")

/** 解析媒体的季列表；解析不出返回空列表。过滤掉未播出（episode_count=0）的季 */
fun mediaSeasons(m: JsonObject): List<Int> {
    val arr = m["seasons"] as? kotlinx.serialization.json.JsonArray
    if (arr != null) {
        val entries = arr.mapNotNull { e ->
            (e as? JsonObject)?.let { o ->
                val n = (o.long("season_number") ?: o.long("value") ?: o.long("season"))?.toInt() ?: return@mapNotNull null
                n to (o.long("episode_count") ?: -1L)
            }
        }
        // 优先只保留已播出的季（episode_count > 0）；解析不出集数就全保留
        val aired = entries.filter { it.second != 0L }.map { it.first }
        val picked = if (entries.any { it.second > 0L }) entries.filter { it.second > 0L }.map { it.first } else aired
        val nums = (if (picked.isNotEmpty()) picked else aired).filter { it >= 0 }.distinct().sorted()
        if (nums.isNotEmpty()) return nums
    }
    m.long("number_of_seasons")?.takeIf { it in 1..50 }?.let { n ->
        return (1..n.toInt()).toList()
    }
    return emptyList()
}

/** 海报地址：相对路径补 TMDB 前缀（顶层工具函数） */
fun posterUrlOf(raw: String?): String? {
    val p = raw?.takeIf { it.isNotBlank() } ?: return null
    return if (p.startsWith("http")) p else "https://image.tmdb.org/t/p/w500$p"
}

/** 字节数转人类可读大小（不带小数） */
fun humanSize(bytes: Long): String = when {
    bytes <= 0 -> "未知"
    bytes < 1024L * 1024 -> "${bytes / 1024}KB"
    bytes < 1024L * 1024 * 1024 -> "${bytes / 1024 / 1024}MB"
    else -> "${bytes / 1024 / 1024 / 1024}GB"
}

/** TokenStore：SharedPreferences 持久化 */
class TokenStore(private val prefs: android.content.SharedPreferences) {
    val baseUrl: String? get() = prefs.getString("base_url", null)
    val token: String? get() = prefs.getString("token", null)
    val savedPrefix: String? get() = prefs.getString("api_prefix", null)
    val savedUsername: String? get() = prefs.getString("username", null)
    val savedPassword: String? get() = prefs.getString("password", null)

    /** 首页豆瓣榜单开关（默认开）。属 UI 偏好，退出登录时保留 */
    var homeTvRanking: Boolean
        get() = prefs.getBoolean("home_tv_ranking", true)
        set(v) { prefs.edit().putBoolean("home_tv_ranking", v).apply() }

    /** 首页猫眼榜单开关（默认关：需要服务端装好猫眼榜单订阅插件）。属 UI 偏好，退出登录时保留 */
    var homeMaoyanRanking: Boolean
        get() = prefs.getBoolean("home_maoyan_ranking", false)
        set(v) { prefs.edit().putBoolean("home_maoyan_ranking", v).apply() }

    /** 首页电影票房榜开关（默认关：同猫眼，数据也来自 MaoyanPro 插件）。属 UI 偏好，退出登录时保留 */
    var homeMovieRanking: Boolean
        get() = prefs.getBoolean("home_movie_ranking", false)
        set(v) { prefs.edit().putBoolean("home_movie_ranking", v).apply() }

    /**
     * 首页榜单的页签顺序（设置页「榜单」卡行序，行尾 ↑ 可上移调整）。
     * 取值为逗号分隔的榜单 id（`maoyan` 猫眼 / `tv` 豆瓣 / `movie` 电影）；未存储或存缺时
     * 按「猫眼、豆瓣、电影」兜底补齐（保持历史顺序）。属 UI 偏好，退出登录时保留。
     */
    var homeRankingOrder: List<String>
        get() {
            val all = listOf("maoyan", "tv", "movie")
            val saved = prefs.getString("home_ranking_order", null)
                ?.split(',')?.map { it.trim() }?.filter { it in all }?.distinct().orEmpty()
            return saved + all.filter { it !in saved }
        }
        set(v) { prefs.edit().putString("home_ranking_order", v.joinToString(",")).apply() }

    /**
     * 资源列表的推荐策略（设置页「搜索 › 结果推荐策略」），取值为 `SortStrategy.*`。
     * 默认 0 = 智能推荐（= 改动前的老行为），老用户升级后无感。属 UI 偏好，退出登录时保留。
     */
    var searchSortStrategy: Int
        get() = prefs.getInt("search_sort_strategy", 0)
        set(v) { prefs.edit().putInt("search_sort_strategy", v).apply() }

    /**
     * 画质偏好（设置页「搜索 › 画质偏好」），取值为 `QualityPref.*`：0 不限 / 1 4K / 2 1080P。
     *
     * 这是与排序策略**正交的全局项**：策略回答「怎么排」，它回答「要哪种」。
     * 只把所选画质档的资源置顶、**不过滤**——某部剧没有 4K 资源时列表不能变空。
     * 默认 0 = 不限（不参与比较，等于老行为）。属 UI 偏好，退出登录时保留。
     */
    var qualityPref: Int
        get() = prefs.getInt("quality_pref", 0)
        set(v) { prefs.edit().putInt("quality_pref", v).apply() }

    /** 搜索框右侧「直接搜种子资源」直达按钮是否显示（默认显示）。属 UI 偏好，退出登录时保留 */
    var showDirectSearch: Boolean
        get() = prefs.getBoolean("show_direct_search", true)
        set(v) { prefs.edit().putBoolean("show_direct_search", v).apply() }

    /** 搜索结果卡是否异步补「主演 + 当季首播日」（默认补）。关掉可省下逐条的详情请求。属 UI 偏好，退出登录时保留 */
    var showMediaMeta: Boolean
        get() = prefs.getBoolean("show_media_meta", true)
        set(v) { prefs.edit().putBoolean("show_media_meta", v).apply() }

    /** 进入设置页时自动检测一次软件更新（默认开）。关掉后只剩手动「检查更新」。属 UI 偏好，退出登录时保留 */
    var autoCheckUpdate: Boolean
        get() = prefs.getBoolean("auto_check_update", true)
        set(v) { prefs.edit().putBoolean("auto_check_update", v).apply() }

    /** 账号密码登录成功。api_token 是旧版 API 令牌模式的残留，顺手清掉 */
    fun save(base: String, token: String, prefix: String) {
        prefs.edit()
            .putString("base_url", base)
            .putString("token", token)
            .putString("api_prefix", prefix)
            .remove("api_token")
            .apply()
    }

    /** 记住账号密码（用于设置页改地址后重连） */
    fun saveCredentials(username: String, password: String) {
        prefs.edit().putString("username", username).putString("password", password).apply()
    }

    fun clear() {
        // UI 偏好（榜单开关、搜索推荐策略、画质偏好…）退出登录不清，重登后保持用户选择
        val keepTv = homeTvRanking
        val keepMaoyan = homeMaoyanRanking
        val keepMovie = homeMovieRanking
        val keepOrder = homeRankingOrder
        val keepStrategy = searchSortStrategy
        val keepQuality = qualityPref
        val keepDirect = showDirectSearch
        val keepMeta = showMediaMeta
        val keepAutoUpdate = autoCheckUpdate
        prefs.edit().clear().apply()
        prefs.edit()
            .putBoolean("home_tv_ranking", keepTv)
            .putBoolean("home_maoyan_ranking", keepMaoyan)
            .putBoolean("home_movie_ranking", keepMovie)
            .putString("home_ranking_order", keepOrder.joinToString(","))
            .putInt("search_sort_strategy", keepStrategy)
            .putInt("quality_pref", keepQuality)
            .putBoolean("show_direct_search", keepDirect)
            .putBoolean("show_media_meta", keepMeta)
            .putBoolean("auto_check_update", keepAutoUpdate)
            .apply()
    }
}
