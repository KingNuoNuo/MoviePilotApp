package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.humanSize
import com.jinge.moviepilot.api.long
import com.jinge.moviepilot.api.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 资源列表：流式搜索（SSE，各站点增量返回，显示进度条），
 * 失败时降级为一次性搜索。每条资源可「下载」或「订阅」。
 */
@Composable
fun TorrentListScreen(
    api: MpApi,
    media: JsonObject?,
    keyword: String?,
    season: Int?,
    episode: Int? = null,
    /** 该季**最新已播集号**（未播完时才有值）——用于把「含最新集」的资源置顶与推荐，见 sortTorrents */
    latestAired: Int? = null,
    /** 结果推荐策略（设置页「搜索」），见 [SortStrategy] */
    strategy: Int = SortStrategy.SMART,
    /** 全局画质偏好（设置页「搜索」），见 [QualityPref]：只置顶不过滤 */
    qualityPref: Int = QualityPref.ANY,
    onSelectTab: (Int) -> Unit = {},
    onBack: () -> Unit,
    key: Int,
) {
    var results by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var message by remember(key) { mutableStateOf<String?>(null) }
    var seasonDialogFor by remember(key) { mutableStateOf<JsonObject?>(null) }
    // 进度：已完成站点数 / 总站点数 + 最近返回结果的站点名
    var doneSites by remember(key) { mutableStateOf(0) }
    var totalSites by remember(key) { mutableStateOf(0) }
    var currentSite by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        loading = true; error = null; results = emptyList(); doneSites = 0
        val onResults: (List<JsonObject>) -> Unit = { results = it }
        val onProgress: (Int, Int, String?) -> Unit = { d, t, s ->
            doneSites = d; if (t > 0) totalSites = t; s?.takeIf { it.isNotBlank() }?.let { currentSite = it }
        }
        try {
            results = when {
                media != null -> api.searchTorrentsByMediaStream(media, season, onResults, onProgress)
                else -> api.searchTorrentsByTitleStream(keyword ?: "", onResults, onProgress)
            }
            if (results.isEmpty()) error = "没有搜索到资源"
        } catch (e: Exception) {
            // 流式失败 → 降级一次性搜索
            try {
                results = if (media != null) api.searchTorrentsByMedia(media, season)
                else api.searchTorrentsByTitle(keyword ?: "")
                if (results.isEmpty()) error = "没有搜索到资源"
            } catch (e2: Exception) {
                error = e2.message ?: e.message ?: "搜索失败"
            }
        } finally {
            loading = false
        }
    }

    // key 里带上 api.netEpoch：切换网络后自动重新搜索（见 MpApi.onNetworkChanged）
    LaunchedEffect(key, media, keyword, season, api.netEpoch) { load() }

    fun doDownload(ctx: JsonObject) {
        scope.launch {
            message = null
            try {
                message = api.download(ctx)
            } catch (e: Exception) {
                message = "下载失败：${e.message}"
            }
        }
    }

    fun doSubscribe(m: JsonObject, s: Int?) {
        scope.launch {
            message = null
            try {
                message = api.subscribe(m, s)
            } catch (e: Exception) {
                message = "订阅失败：${e.message}"
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 标题行：返回箭头 + 媒体名 + 季/集 + 结果数（顶栏由主 Scaffold 统一提供，不在此重复）
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
            }
            Text(
                listOfNotNull(
                    media?.str("title") ?: keyword ?: "资源",
                    season?.let { "第${it}季" },
                    episode?.let { "第${it}集" }
                ).joinToString(" ") + if (loading) " · 已找到 ${results.size} 条…" else " · 共 ${results.size} 条",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = Color(0xFF1A1C2B)
            )
        }

        message?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }

        when {
            loading && results.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        if (totalSites > 0) {
                            Text(
                                "正在搜索站点 $doneSites/$totalSites",
                                color = Color(0xFF1A1C2B), fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            currentSite?.let {
                                Spacer(Modifier.height(4.dp))
                                Text("最近返回：$it", color = Color(0xFF8A8FA3), fontSize = 12.sp)
                            }
                        } else {
                            Text("正在搜索各站点资源…", color = Color(0xFF8A8FA3), fontSize = 13.sp)
                        }
                    }
                }
            loading -> Column(Modifier.fillMaxSize()) {
                SearchProgressBar(doneSites, totalSites, currentSite, results.size)
                TorrentList(results, media, season, episode, latestAired, strategy, qualityPref, ::doDownload, ::doSubscribe) { seasonDialogFor = it }
            }
            error != null && results.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = Color(0xFF8A8FA3))
                }
            else -> TorrentList(results, media, season, episode, latestAired, strategy, qualityPref, ::doDownload, ::doSubscribe) { seasonDialogFor = it }
        }
    }

    // 订阅选季
    seasonDialogFor?.let { m ->
        var seasonText by remember(m) { mutableStateOf("1") }
        AlertDialog(
            onDismissRequest = { seasonDialogFor = null },
            title = { Text("订阅第几季？") },
            text = {
                OutlinedTextField(
                    value = seasonText,
                    onValueChange = { seasonText = it.filter(Char::isDigit).take(2) },
                    label = { Text("季号") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    doSubscribe(m, seasonText.toIntOrNull())
                    seasonDialogFor = null
                }) { Text("订阅") }
            },
            dismissButton = {
                TextButton(onClick = { seasonDialogFor = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun SearchProgressBar(done: Int, total: Int, currentSite: String?, found: Int) {
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "正在搜索",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = {
                    if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
                },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = Color(0xFFE3E5F0),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (total > 0) "正在搜索站点 $done/$total" else "正在搜索各站点…",
                    fontSize = 12.sp, color = Color(0xFF1A1C2B),
                    fontWeight = FontWeight.SemiBold
                )
                currentSite?.let {
                    Text(
                        "  ·  $it",
                        fontSize = 12.sp, color = Color(0xFF8A8FA3),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                Text("已找到 $found 条", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/** 速度分：做种数 / √体积(GB)，做种越多越快、同做种下小文件更快；无做种返回 null（排最后） */
private fun speedScore(torrent: JsonObject?): Double? {
    torrent ?: return null
    val seeders = torrent.long("seeders")?.takeIf { it > 0 } ?: return null
    val sizeGb = ((torrent.long("size") ?: 0L) / 1024.0 / 1024 / 1024).coerceAtLeast(0.5)
    return seeders / kotlin.math.sqrt(sizeGb)
}

// ---- 集号范围解析（分层 / 过滤 / 推荐三处共用）----

/**
 * 数字写法兼容：站点命名里「第一季」「第十二集」极其常见，而下面各正则原先只认阿拉伯数字
 * —— 后果是整季包被判成单集（排最底）、选集过滤漏条，推荐策略对这批资源形同虚设。
 * 统一放行两种写法：`\d{1,3}` 或中文数字（[cnNum] 只支持 1-99，季/集号够用）。
 */
private const val NUM_ALT = "(?:\\d{1,3}|[一二两三四五六七八九十]{1,3})"

/** "12" / "十二" → 12；认不出返回 null */
private fun numOf(s: String): Int? = s.toIntOrNull() ?: cnNum(s)

/** 中文数字 → Int（一/十/十一/二十/二十四…，支持 1-99） */
private fun cnNum(s: String): Int? {
    if (s.isEmpty()) return null
    val digits = mapOf('一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9)
    if (s.any { it != '十' && it !in digits }) return null
    var total = 0
    var pending = 0
    for (ch in s) {
        if (ch == '十') {
            total += (if (pending == 0) 1 else pending) * 10
            pending = 0
        } else {
            pending = digits[ch] ?: return null
        }
    }
    total += pending
    return total.takeIf { it in 1..99 }
}

/** Int → 中文数字（1→一，12→十二，20→二十，21→二十一）；超出 1-99 返回 null */
private fun cnNumOf(n: Int): String? {
    if (n !in 1..99) return null
    val d = "一二三四五六七八九"
    val tens = n / 10
    val units = n % 10
    val t = when (tens) {
        0 -> ""
        1 -> "十"
        else -> d[tens - 1] + "十"
    }
    val u = if (units == 0) "" else d[units - 1].toString()
    return t + u
}

/** `E01-E12` / `E01~12` / `EP01+12` */
private val RE_EP_RANGE_EN = Regex(
    "E\\s*P?\\s*(\\d{1,3})\\s*[-~～+至]{1,2}\\s*E?\\s*P?\\s*(\\d{1,3})",
    RegexOption.IGNORE_CASE
)

/** `第05-06集` / `第1~12集` / `第五-六集`（集号认中文数字） */
private val RE_EP_RANGE_CN = Regex("第\\s*($NUM_ALT)\\s*[-~～+至]{1,2}\\s*($NUM_ALT)\\s*集")

/** `S01E05` / `S01.E05` / 裸 `E05` / `EP05`（两个分支分别取组 1 / 组 2） */
private val RE_EP_SINGLE_EN = Regex(
    "(?<![A-Za-z0-9])S\\d{1,2}\\s*[._\\- ]?\\s*E\\s*P?\\s*(\\d{1,3})" +
            "|(?<![A-Za-z0-9])E\\s*P?\\s*(\\d{1,3})(?![0-9])",
    RegexOption.IGNORE_CASE
)

/** `第5集` / `第05集` / `第十二集`（集号认中文数字） */
private val RE_EP_SINGLE_CN = Regex("第\\s*($NUM_ALT)\\s*集")

/**
 * 从标题 / 描述里解析出集号区间（合并文本里出现的所有单集与范围写法）。
 *
 * 未播完的剧，资源几乎全是「第05-06集」「E01-E12」这类**分段包**（实测某剧 63 条资源，
 * 前排清一色如此），所以「这条资源包含哪些集」必须这样解析，光认 `E6 / 第6集` 的单集写法
 * 会全部漏掉。注意集号经常只写在**描述**里（标题是 `...S01 2026 1080p WEB-DL...`）。
 *
 * @return 覆盖到的集号区间（1..N，取所有匹配的最小/最大）；**null = 文本里完全没有集号信息**
 *         （整季包 / 命名不规范），调用方自行决定按「可能包含」还是「排除」处理。
 */
fun parseEpisodeRange(vararg texts: String?): IntRange? {
    var lo = Int.MAX_VALUE
    var hi = Int.MIN_VALUE
    fun take(a: Int?, b: Int?) {
        val x = a?.takeIf { it > 0 } ?: return
        val y = b?.takeIf { it > 0 } ?: x
        lo = minOf(lo, x, y); hi = maxOf(hi, x, y)
    }
    texts.forEach { raw ->
        if (raw.isNullOrBlank()) return@forEach
        val t = Regex("<[^>]+>").replace(raw, " ")   // 描述里常带 HTML 标签
        RE_EP_RANGE_EN.findAll(t).forEach { take(numOf(it.groupValues[1]), numOf(it.groupValues[2])) }
        RE_EP_RANGE_CN.findAll(t).forEach { take(numOf(it.groupValues[1]), numOf(it.groupValues[2])) }
        RE_EP_SINGLE_EN.findAll(t).forEach { m ->
            take(numOf(m.groupValues[1].ifEmpty { m.groupValues[2] }), null)
        }
        RE_EP_SINGLE_CN.findAll(t).forEach { take(numOf(it.groupValues[1]), null) }
    }
    return if (hi >= lo) lo..hi else null
}

/** 种子是否覆盖到最新已播集（未播完的季里，这最该被推荐/置顶） */
private fun coversLatest(torrent: JsonObject?, latest: Int?): Boolean {
    latest ?: return false
    val r = parseEpisodeRange(torrent?.str("title"), torrent?.str("description")) ?: return false
    return r.last >= latest
}

/** 标题是否带明确单集/集号标记（S01E05 / S01 E05 / S01.E05 / 第1季第5集 / 第一季第五集 等，容忍任意分隔符） */
private fun hasEpisodeMark(t: String): Boolean =
    Regex("S\\d{1,2}\\s*[.\\-_ ]?\\s*E\\s*P?\\s*\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            Regex("第\\s*$NUM_ALT\\s*季\\s*第?\\s*$NUM_ALT\\s*集").containsMatchIn(t)

/**
 * 资源类型分层（从标题解析）：
 * 0 = 整季包（S01 不带集号 / 第N季 / Season N / 全集合集）——一次下载全齐，优先
 * 1 = 多集包（E01-E08 这类范围）
 * 2 = 单集（要下 N 次才齐，最后）
 */
fun torrentTier(title: String?, subtitle: String? = null): Int {
    val t = title ?: return 2
    // 副标题里明确写「第N集」/ S01E05 的，肯定不是整季包（集号认中文数字）
    val sub = subtitle?.let { Regex("<[^>]+>").replace(it, " ") } ?: ""   // 去掉描述里的 HTML 标签
    val subEpisode = Regex("(?<![-~至\\d])第\\s*(?:0*\\d{1,3}|[一二两三四五六七八九十]{1,3})\\s*集(?![0-9-~至])").containsMatchIn(sub) ||
            Regex("S\\d{1,2}\\s*[.\\-_ ]?\\s*E\\s*P?\\s*\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(sub)
    // 先判断多集包（范围），再排除带单集标记的，最后才是整季包 —— 顺序不能反
    if (Regex("E[P]?\\d{1,3}\\s*[-~～+至]{1,2}\\s*E?P?\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 1
    if (Regex("第$NUM_ALT\\s*[-~～+至]\\s*$NUM_ALT\\s*集").containsMatchIn(t)) return 1
    if (hasEpisodeMark(t)) return 2
    if (subEpisode) return 2
    // 副标题里带集数范围（第25-26集 / E01-E08 / 第二十五-二十六集）→ 多集包
    if (Regex("第\\s*$NUM_ALT\\s*[-~至]\\s*$NUM_ALT\\s*集").containsMatchIn(sub)) return 1
    if (Regex("E[P]?\\d{1,3}\\s*[-~至]\\s*E?P?\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(sub)) return 1
    // 整季包：S01 后面不跟集号、第N季（中文数字也行：第一季）后面不带集、Season N、S01-S03 多季合集
    if (Regex("S\\d{1,2}(?![0-9])", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    if (Regex("S\\d{1,2}\\s*[-~～]\\s*S\\d{1,2}", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    if (Regex("第\\s*$NUM_ALT\\s*季(?![\\d集])").containsMatchIn(t)) return 0
    if (Regex("Season\\s*\\d{1,2}(?![0-9])", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    if (Regex("全集|合集|Complete", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    return 2
}

private fun ctxTorrent(ctx: JsonObject): JsonObject? = ctx["torrent_info"] as? JsonObject

/**
 * 结果推荐策略（设置页「搜索 › 结果推荐策略」→ TokenStore.searchSortStrategy）。
 * 常量顺序 = 下拉里的展示顺序，[labels] 与常量必须一一对应。
 */
object SortStrategy {
    /** 智能推荐（默认）：跟着剧集状态走 —— 未播完 → 含最新集优先；已完结/未知 → 整季包优先。即改动前的老行为 */
    const val SMART = 0

    /** 最新集优先：只认「覆盖到最新已播集」的包，不管整季不整季 */
    const val LATEST = 1

    /** 整季包优先：整季 → 多集 → 单集 */
    const val FULL_SEASON = 2

    /** 做种速度优先：纯按做种数/体积的评分 */
    const val SPEED = 3

    /** 小体积优先：体积升序（省硬盘/带宽） */
    const val SMALL = 4

    val labels = listOf("智能推荐", "最新集优先", "整季包优先", "做种速度优先", "小体积优先")

    fun labelOf(v: Int): String = labels.getOrElse(v) { labels[SMART] }
}

/** 全局画质偏好（设置页「搜索 › 画质偏好」→ TokenStore.qualityPref） */
object QualityPref {
    const val ANY = 0
    const val UHD = 1
    const val FHD = 2

    val labels = listOf("不限", "4K", "1080P")

    fun labelOf(v: Int): String = labels.getOrElse(v) { labels[ANY] }
}

/**
 * 推荐排序。所有策略共用一个前提：**画质偏好只置顶、不过滤** ——
 * 命中所选画质档的记 0、其余记 1；选「不限」时全是 0，等于这一维不存在（老行为）。
 *
 * 之后按 [strategy] 追加比较键：
 * - **智能推荐**（默认）：指定了某一集 → 纯速度分（下整季浪费体积）；
 *   未播完的季 → 先「含最新已播集」再分层再速度（实测追更剧资源多为「第05-06集」这类分段包，
 *   只看速度分会因 1080p 体积小、分数虚高，把中间那段旧集顶到第一，含最新集的包反被压在后面）；
 *   已完结 / 未知 → 整季包 → 多集包 → 单集，组内按速度分。
 * - **最新集优先**：按「覆盖到的最大集号」**降序**——E34 → E33 → E28 …（同集号再按速度），
 *   整季包（解析不出集号但被认成整季的）视为覆盖全集排最前；解析不出集号的杂项记为最旧。
 *   早期实现只按「是否覆盖到最新已播集」的二值分组、组内按速度，于是 E33(做种96) 压过 E34(94)，
 *   最新集反而不在最前（实测余红旧事 S01 219 条就是这个现象），故改为真正的集号序。
 *   拿不到最新已播集（已完结/未知）也照样按集号降序，自然退化为「集号大的在前、同集号看速度」。
 * - **整季包优先**：始终按分层（即使选了某一集）。
 * - **做种速度优先** / **小体积优先**：只用速度 / 体积，不管分层。
 *
 * ★ 推荐 = 排序后第一个有做种分的，所以它自然落到排头那一类里最快的那条。
 */
fun sortTorrents(
    results: List<JsonObject>,
    episode: Int?,
    latestAired: Int? = null,
    strategy: Int = SortStrategy.SMART,
    qualityPref: Int = QualityPref.ANY,
): List<JsonObject> {
    // 命中画质偏好的档位值；「不限」为 null → 完全不参与比较
    val wantTier = when (qualityPref) {
        QualityPref.UHD -> 0
        QualityPref.FHD -> 1
        else -> null
    }

    // 名字带 Sort 前缀：别和 Compose 的 Row 撞名（本文件里到处都在用那个）
    /** 一条资源的预计算排序键（避免比较器里反复解析字符串） */
    data class SortRow(
        val ctx: JsonObject,
        val speed: Double,
        val quality: Int,
        val size: Long,
        val tier: Int,
        val latest: Int,
        /** 覆盖到的最大集号；整季包记 Int.MAX_VALUE（覆盖全集），解析不出集号的杂项记 0（最旧） */
        val epKey: Int,
    )

    val rows = results.map { ctx ->
        val tr = ctxTorrent(ctx)
        val title = tr?.str("title")
        val desc = tr?.str("description")
        val tier = torrentTier(title, desc)
        val maxEp = parseEpisodeRange(title, desc)?.last
        SortRow(
            ctx = ctx,
            speed = speedScore(tr) ?: Double.NEGATIVE_INFINITY,
            // 分辨率可能只写在描述里（标题是 `...S01 2026 WEB-DL...`），两处一起看
            quality = if (wantTier == null) 0
            else if (resolutionTier(listOfNotNull(title, desc).joinToString(" ")) == wantTier) 0 else 1,
            size = tr?.long("size") ?: Long.MAX_VALUE,
            tier = tier,
            latest = if (coversLatest(tr, latestAired)) 0 else 1,
            // 「最新集优先」的排序依据：有集号就用最大集号；没有集号的，整季包当覆盖全集排最前，
            // 认不出类型的当最旧（否则命名不规范的杂项会被误顶到第一位）
            epKey = maxEp ?: if (tier == 0) Int.MAX_VALUE else 0,
        )
    }

    val comparator: Comparator<SortRow> = when (strategy) {
        SortStrategy.SPEED ->
            compareBy<SortRow> { it.quality }.thenByDescending { it.speed }

        SortStrategy.SMALL ->
            compareBy<SortRow> { it.quality }.thenBy { it.size }

        SortStrategy.FULL_SEASON ->
            compareBy<SortRow> { it.quality }.thenBy { it.tier }.thenByDescending { it.speed }

        SortStrategy.LATEST ->
            // 真正的「集号优先」：覆盖到的最新集号越大越前（整季包 = 覆盖全集排最前），
            // 同集号再比做种速度。旧实现只分「含最新 / 不含最新」两档、组内纯速度，
            // 会让 E33 压过 E34，见上面的策略说明。
            compareBy<SortRow> { it.quality }.thenByDescending { it.epKey }.thenByDescending { it.speed }

        else ->
            if (episode != null) compareBy<SortRow> { it.quality }.thenByDescending { it.speed }
            else if (latestAired != null) compareBy<SortRow> { it.quality }.thenBy { it.latest }.thenBy { it.tier }
                .thenByDescending { it.speed }
            else compareBy<SortRow> { it.quality }.thenBy { it.tier }.thenByDescending { it.speed }
    }
    return rows.sortedWith(comparator).map { it.ctx }
}

fun pickRecommended(sorted: List<JsonObject>): JsonObject? =
    sorted.firstOrNull { speedScore(ctxTorrent(it)) != null }

/** 种子标题是否属于指定季：S01 / 第1季 / 第一季 / Season 1 等（季号两种数字写法都认） */
private fun matchesSeason(title: String?, season: Int): Boolean {
    val t = title ?: return false
    val cn = cnNumOf(season)
    val patterns = listOfNotNull(
        Regex("S%02d(?![0-9])".format(season), RegexOption.IGNORE_CASE),
        Regex("第\\s*0*${season}\\s*季"),
        cn?.let { Regex("第\\s*${it}\\s*季") },
        Regex("Season\\s*0*${season}(?![0-9])", RegexOption.IGNORE_CASE),
    )
    return patterns.any { it.containsMatchIn(t) }
}

/**
 * 种子是否包含指定集。**标题和描述一起看**，并把范围写法展开：
 * 集号在很多站点只写在描述里（标题是 `...S01 2026 1080p WEB-DL...`，描述才是「第05-06集」），
 * 而旧实现只拿标题匹配 `E6 / 第6集` 单集写法，于是**选了某一集一条都筛不出来**，
 * 退化成「已显示全部」——过滤形同虚设。
 * 完全解析不出集号的（整季包 / 命名不规范）按「可能包含」返回 true，宁可多列不可漏。
 */
private fun matchesEpisode(title: String?, subtitle: String?, episode: Int): Boolean {
    val range = parseEpisodeRange(title, subtitle) ?: return true
    return episode in range
}

@Composable
private fun TorrentList(
    results: List<JsonObject>,
    media: JsonObject?,
    season: Int?,
    episode: Int?,
    latestAired: Int?,
    strategy: Int,
    qualityPref: Int,
    doDownload: (JsonObject) -> Unit,
    doSubscribe: (JsonObject, Int?) -> Unit,
    onNeedSeason: (JsonObject) -> Unit,
) {
    // 选中季/集时按标题+描述过滤（集号常只写在描述里，且多为「第05-06集」范围写法）；
    // 一个都没命中时显示全部并提示
    val (shown, filterNotice) = remember(results, season, episode) {
        val label = listOfNotNull(
            season?.let { "第${it}季" },
            episode?.let { "第${it}集" }
        ).joinToString("·")
        if (season == null && episode == null) {
            results to null
        } else {
            val matched = results.filter { ctx ->
                val tr = ctx["torrent_info"] as? JsonObject
                val title = tr?.str("title")
                val desc = tr?.str("description")
                (season == null || matchesSeason(title, season)) &&
                    (episode == null || matchesEpisode(title, desc, episode))
            }
            if (matched.isNotEmpty()) {
                matched to "已按$label 过滤：${matched.size}/${results.size} 条"
            } else {
                results to "没有包含「$label」的种子（可能命名不规范），已显示全部 ${results.size} 条"
            }
        }
    }
    // 「含最新」绿标的基准集号：未播完用真实的最新已播集；拿不到时（已完结 / 总集数未知），
    // 「最新集优先」会用本批资源的最大集号兜底排序 —— 标注也跟着用同一基准，否则会被标得莫名其妙。
    val latestBasis = remember(shown, latestAired, strategy) {
        latestAired ?: if (strategy == SortStrategy.LATEST) {
            shown.mapNotNull { ctx ->
                val tr = ctx["torrent_info"] as? JsonObject
                parseEpisodeRange(tr?.str("title"), tr?.str("description"))?.last
            }.maxOrNull()
        } else null
    }
    // 排序：先按全局画质偏好置顶，再按设置页选的推荐策略（见 sortTorrents）
    val sorted = remember(shown, episode, latestAired, strategy, qualityPref) {
        sortTorrents(shown, episode, latestAired, strategy, qualityPref)
    }
    val recommended = remember(sorted) { pickRecommended(sorted) }
    Column(Modifier.fillMaxSize()) {
        filterNotice?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                fontSize = 12.sp,
                color = if (it.startsWith("已按")) MaterialTheme.colorScheme.primary else Color(0xFFB0621F)
            )
        }
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        items(sorted) { ctx ->
            TorrentCard(
                context = ctx,
                isRecommended = ctx == recommended,
                // 只有「覆盖到最新集」的资源才标「含最新」，解释它为什么被推荐
                showLatest = episode == null && coversLatest(ctx["torrent_info"] as? JsonObject, latestBasis),
                onDownload = { doDownload(ctx) },
                onSubscribe = {
                    val m = ctx["media_info"] as? JsonObject ?: media
                    if (m != null && m.str("type") in listOf("tv", "电视剧")) {
                        onNeedSeason(m)
                    } else if (m != null) {
                        doSubscribe(m, null)
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun TorrentCard(
    context: JsonObject,
    isRecommended: Boolean,
    showLatest: Boolean = false,
    onDownload: () -> Unit,
    onSubscribe: () -> Unit,
) {
    val torrent = context["torrent_info"] as? JsonObject
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                torrent?.str("title") ?: "未知资源",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = Color(0xFF1A1C2B)
            )
            torrent?.str("description")?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(2.dp))
                Text(
                    it, fontSize = 12.sp, color = Color(0xFF8A8FA3),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRecommended) {
                    Tag("★推", SuccessGreen, Color.White)
                    Spacer(Modifier.width(6.dp))
                }
                // 追更中的季：标出「含最新」，解释它为什么被置顶/推荐
                if (showLatest) {
                    Tag("含最新", TagDoneBg, SuccessGreen)
                    Spacer(Modifier.width(6.dp))
                }
                // 资源类型标签：整季=靛蓝 / 多集=橙 / 单集不标
                when (torrentTier(torrent?.str("title"), torrent?.str("description"))) {
                    0 -> { Tag("整季", TagDownloadBg, SelectedPillText); Spacer(Modifier.width(6.dp)) }
                    1 -> { Tag("多集", Color(0xFFFCEEDB), Color(0xFFB0621F)); Spacer(Modifier.width(6.dp)) }
                }
                val siteName = torrent?.str("site_name")
                siteName?.let {
                    Tag(it, TagDownloadBg, SelectedPillText)
                    Spacer(Modifier.width(6.dp))
                }
                torrent?.long("size")?.let {
                    Tag(humanSize(it), TagWaitBg, TextSecondary)
                    Spacer(Modifier.width(6.dp))
                }
                torrent?.long("seeders")?.let {
                    if (it > 0) Tag("做种$it", TagDoneBg, SuccessGreen)
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onSubscribe, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("订阅", fontSize = 13.sp)
                }
                // 箭头（下载）图标按钮：占位小，推荐行不会挤爆
                IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "下载",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontSize = 11.sp,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}
