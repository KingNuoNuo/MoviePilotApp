package com.jinge.moviepilot.api

import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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
        .readTimeout(300, TimeUnit.SECONDS) // 资源搜索可能很慢
        .cookieJar(cookieJar)
        .build()

    // 重启后从本地恢复前缀，避免请求打到不存在的 /api/v2 导致 404
    var apiPrefix: String = store.savedPrefix ?: "/api/v1"
        private set

    /** 服务器连接状态：null=未知 / true=最近请求成功 / false=失败（供顶栏状态点） */
    var serverOnline: Boolean? by mutableStateOf(null)
        private set

    companion object {
        private val CANDIDATE_PREFIXES = listOf("/api/v2", "/api/v3", "/api/v1")
    }

    class ApiException(message: String) : Exception(message)

    // ---------- 基础请求 ----------

    private fun url(base: String, path: String, params: Map<String, String?> = emptyMap()): String {
        val qs = params.filterValues { !it.isNullOrBlank() }
            .entries.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
        return base.trimEnd('/') + path + (if (qs.isEmpty()) "" else "?$qs")
    }

    /** API 令牌模式用 X-API-KEY 头，账号密码模式用 Bearer JWT */
    private fun authHeader(): Pair<String, String>? = when {
        store.apiToken != null -> "X-API-KEY" to store.apiToken!!
        store.token != null -> "Authorization" to "Bearer ${store.token}"
        else -> null
    }

    private suspend fun httpCall(request: Request): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { resp ->
                    val body = resp.body?.string() ?: ""
                    serverOnline = resp.isSuccessful
                    resp.code to body
                }
            } catch (e: Exception) {
                serverOnline = false
                throw e
            }
        }

    /**
     * 认证失效自动恢复：JWT 过期时用记住的账号密码静默重登。
     * 成功返回 true；没有凭据或重登失败返回 false（调用方走 401 报错）。
     */
    private suspend fun relogin(): Boolean {
        val user = store.savedUsername ?: return false
        val pass = store.savedPassword ?: return false
        return try {
            login(store.baseUrl ?: return false, user, pass)
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun get(base: String, path: String, params: Map<String, String?> = emptyMap()): Any {
        suspend fun call(): Pair<Int, String> {
            val builder = Request.Builder().url(url(base, path, params))
            authHeader()?.let { (k, v) -> builder.header(k, v) }
                ?: throw ApiException("未登录")
            return httpCall(builder.get().build())
        }
        var (code, body) = call()
        if (code == 401 && relogin()) {
            val retry = call()
            code = retry.first; body = retry.second
        }
        if (code == 401) throw ApiException("认证失效，请到「设置」重新登录")
        if (code != 200) throw ApiException("请求失败 (HTTP $code)")
        return json.parseToJsonElement(body.ifEmpty { "null" })
    }

    private suspend fun post(base: String, path: String, body: JsonObject): JsonObject {
        suspend fun call(): Pair<Int, String> {
            val builder = Request.Builder().url(url(base, path))
            authHeader()?.let { (k, v) -> builder.header(k, v) }
            builder.post(body.toString().toRequestBody("application/json".toMediaType()))
            return httpCall(builder.build())
        }
        var (code, respBody) = call()
        if (code == 401 && relogin()) {
            val retry = call()
            code = retry.first; respBody = retry.second
        }
        if (code != 200) throw ApiException("请求失败 (HTTP $code)")
        return json.parseToJsonElement(respBody.ifEmpty { "{}" }).jsonObject
    }

    private suspend fun delete(base: String, path: String, params: Map<String, String?> = emptyMap()): JsonObject {
        suspend fun call(): Pair<Int, String> {
            val builder = Request.Builder().url(url(base, path, params))
            authHeader()?.let { (k, v) -> builder.header(k, v) }
                ?: throw ApiException("未登录")
            return httpCall(builder.delete().build())
        }
        var (code, body) = call()
        if (code == 401 && relogin()) {
            val retry = call()
            code = retry.first; body = retry.second
        }
        if (code != 200) throw ApiException("请求失败 (HTTP $code)")
        return json.parseToJsonElement(body.ifEmpty { "{}" }).jsonObject
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
        val base = baseUrl() ?: return
        runCatching { get(base, "$apiPrefix/download/") }
    }

    /**
     * 登录并探测 API 前缀。成功后保存 token 与前缀。
     */
    suspend fun login(serverBase: String, username: String, password: String) {
        val base = serverBase.trim().trimEnd('/')
        val errors = mutableListOf<String>()

        for (prefix in CANDIDATE_PREFIXES) {
            val form = "username=${URLEncoder.encode(username, "UTF-8")}" +
                    "&password=${URLEncoder.encode(password, "UTF-8")}"
            val req = Request.Builder()
                .url(url(base, "$prefix/login/access-token"))
                .post(form.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            try {
                val (code, body) = httpCall(req)
                if (code != 200) {
                    if (code == 400 || code == 401) throw ApiException("用户名或密码错误")
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
                return
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                errors.add("$prefix: ${e.message}")
            }
        }
        throw ApiException("无法连接服务器（试过 ${CANDIDATE_PREFIXES.joinToString("/")}）\n${errors.firstOrNull() ?: ""}")
    }

    /**
     * 使用服务器地址 + API 令牌登录：用 X-API-KEY 探测前缀，
     * 以 /site/agent（需认证）验证令牌有效性。
     */
    suspend fun loginWithToken(serverBase: String, token: String) {
        val base = serverBase.trim().trimEnd('/')
        if (token.isBlank()) throw ApiException("API 令牌不能为空")
        val errors = mutableListOf<String>()
        for (prefix in CANDIDATE_PREFIXES) {
            val req = Request.Builder()
                .url(url(base, "$prefix/site/agent", mapOf("status" to "active")))
                .header("X-API-KEY", token)
                .get()
                .build()
            try {
                val (code, body) = httpCall(req)
                when {
                    code == 200 -> {
                        apiPrefix = prefix
                        store.saveTokenMode(base, token, prefix)
                        return
                    }
                    code == 401 || code == 403 -> throw ApiException("API 令牌校验失败（HTTP $code）")
                    else -> errors.add("$prefix: HTTP $code")
                }
            } catch (e: ApiException) {
                throw e
            } catch (e: Exception) {
                errors.add("$prefix: ${e.message}")
            }
        }
        throw ApiException("无法连接服务器或令牌无效（试过 ${CANDIDATE_PREFIXES.joinToString("/")}）\n${errors.firstOrNull() ?: ""}")
    }

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

        client.newCall(req).execute().use { resp ->
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

    /** 我的订阅列表 */
    suspend fun listSubscribes(): List<JsonObject> {
        val data = unwrap(get(baseUrl(), "$apiPrefix/subscribe/"))
        val arr = (data as? kotlinx.serialization.json.JsonArray)?.jsonArray ?: return emptyList()
        return arr.mapNotNull { it as? JsonObject }
    }

    suspend fun deleteSubscribe(id: Long) {
        val resp = delete(baseUrl(), "$apiPrefix/subscribe/$id")
        failMessage(resp)?.let { throw ApiException(it) }
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

    /** 刷新登录（resource cookie 30 分钟过期后重取）。供图片代理重试用；Mutex 防止多图并发重复登录 */
    private val refreshMutex = kotlinx.coroutines.sync.Mutex()
    suspend fun refreshSession(): Boolean = refreshMutex.withLock { relogin() }
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
    val apiToken: String? get() = prefs.getString("api_token", null)
    val savedUsername: String? get() = prefs.getString("username", null)
    val savedPassword: String? get() = prefs.getString("password", null)

    /** 账号密码登录成功 */
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

    /** API 令牌登录成功 */
    fun saveTokenMode(base: String, apiToken: String, prefix: String) {
        prefs.edit()
            .putString("base_url", base)
            .putString("api_token", apiToken)
            .putString("api_prefix", prefix)
            .remove("token")
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
