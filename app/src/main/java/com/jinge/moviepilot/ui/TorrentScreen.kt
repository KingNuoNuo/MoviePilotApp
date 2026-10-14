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

    LaunchedEffect(key, media, keyword, season) { load() }

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
                TorrentList(results, media, season, episode, ::doDownload, ::doSubscribe, { seasonDialogFor = it })
            }
            error != null && results.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = Color(0xFF8A8FA3))
                }
            else -> TorrentList(results, media, season, episode, ::doDownload, ::doSubscribe, { seasonDialogFor = it })
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

/**
 * 资源类型分层（从标题解析）：
 * 0 = 整季包（S01 不带集号 / 第N季 / Season N / 全集合集）——一次下载全齐，优先
 * 1 = 多集包（E01-E08 这类范围）
 * 2 = 单集（要下 N 次才齐，最后）
 */
fun torrentTier(title: String?): Int {
    val t = title ?: return 2
    // 整季包：S01 后面不跟集号（S01E05 不算）、第N季后面不带集、Season N、S01-S03 多季合集
    if (Regex("S\\d{1,2}(?![0-9E])", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    if (Regex("S\\d{1,2}\\s*[-~～]\\s*S\\d{1,2}", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    if (Regex("第\\s*\\d{1,2}\\s*季(?![\\d集])").containsMatchIn(t)) return 0
    if (Regex("Season\\s*\\d{1,2}(?![0-9])", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    if (Regex("全集|合集|Complete", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 0
    // 多集包：E01-E08 / EP01~08 / E01+E02 / 第1-8集
    if (Regex("E[P]?\\d{1,3}\\s*[-~～+至]{1,2}\\s*E?P?\\d{1,3}", RegexOption.IGNORE_CASE).containsMatchIn(t)) return 1
    if (Regex("第\\d{1,3}\\s*[-~～+至]\\s*\\d{1,3}\\s*集").containsMatchIn(t)) return 1
    return 2
}

private fun ctxTorrent(ctx: JsonObject): JsonObject? = ctx["torrent_info"] as? JsonObject

/**
 * 推荐排序（分层）：
 * 整季包组置顶（组内按速度分降序）→ 多集包 → 单集；
 * 「★ 推荐」= 最优组里速度分最高的（无做种的资源不参与推荐）。
 * 指定了具体某一集时不再强制整季包优先（下整季浪费体积），直接按速度分排。
 */
fun sortTorrents(results: List<JsonObject>, episode: Int?): List<JsonObject> {
    val scored = results.map { ctx -> ctx to speedScore(ctxTorrent(ctx)) }
    return if (episode != null) {
        scored.sortedByDescending { it.second ?: Double.NEGATIVE_INFINITY }.map { it.first }
    } else {
        scored.sortedWith(
            compareBy(
                { (ctx, _) -> torrentTier(ctxTorrent(ctx)?.str("title")) },
                { (_, s) -> -(s ?: Double.NEGATIVE_INFINITY) }
            )
        ).map { it.first }
    }
}

fun pickRecommended(sorted: List<JsonObject>): JsonObject? =
    sorted.firstOrNull { speedScore(ctxTorrent(it)) != null }

/** 种子标题是否属于指定季：S01 / 第1季 / Season 1 等 */
private fun matchesSeason(title: String?, season: Int): Boolean {
    val t = title ?: return false
    val patterns = listOf(
        Regex("S%02d(?![0-9])".format(season), RegexOption.IGNORE_CASE),
        Regex("第\\s*0*${season}\\s*季"),
        Regex("Season\\s*0*${season}(?![0-9])", RegexOption.IGNORE_CASE),
    )
    return patterns.any { it.containsMatchIn(t) }
}

/** 种子标题是否属于指定集：S01E05 / E05 / 第5集 / EP5 等 */
private fun matchesEpisode(title: String?, episode: Int): Boolean {
    val t = title ?: return false
    val patterns = listOf(
        Regex("S\\d{1,2}E0*${episode}(?![0-9])", RegexOption.IGNORE_CASE),
        Regex("(?<![0-9A-Za-z])E0*${episode}(?![0-9])", RegexOption.IGNORE_CASE),
        Regex("EP0*${episode}(?![0-9])", RegexOption.IGNORE_CASE),
        Regex("第\\s*0*${episode}\\s*集"),
    )
    return patterns.any { it.containsMatchIn(t) }
}

@Composable
private fun TorrentList(
    results: List<JsonObject>,
    media: JsonObject?,
    season: Int?,
    episode: Int?,
    doDownload: (JsonObject) -> Unit,
    doSubscribe: (JsonObject, Int?) -> Unit,
    onNeedSeason: (JsonObject) -> Unit,
) {
    // 选中季/集时按标题过滤：命中单集的排在前面；一个都没命中时显示全部并提示
    val (shown, filterNotice) = remember(results, season, episode) {
        val label = listOfNotNull(
            season?.let { "第${it}季" },
            episode?.let { "第${it}集" }
        ).joinToString("·")
        if (season == null && episode == null) {
            results to null
        } else {
            val matched = results.filter { ctx ->
                val title = (ctx["torrent_info"] as? JsonObject)?.str("title")
                (season == null || matchesSeason(title, season)) &&
                    (episode == null || matchesEpisode(title, episode))
            }
            if (matched.isNotEmpty()) {
                matched to "已按$label 过滤：${matched.size}/${results.size} 条"
            } else {
                results to "没有标题含「$label」的种子（可能命名不规范），已显示全部 ${results.size} 条"
            }
        }
    }
    // 分层排序：整季包 → 多集包 → 单集，组内按速度分；指定具体集时不强制整季包优先
    val sorted = remember(shown, episode) { sortTorrents(shown, episode) }
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
                    Tag("★推", Color(0xFF2E9E5B), Color.White)
                    Spacer(Modifier.width(6.dp))
                }
                // 资源类型标签：整季=靛蓝 / 多集=橙 / 单集不标
                when (torrentTier(torrent?.str("title"))) {
                    0 -> { Tag("整季", Color(0xFFE3E5FB), Color(0xFF414BB2)); Spacer(Modifier.width(6.dp)) }
                    1 -> { Tag("多集", Color(0xFFFCEEDB), Color(0xFFB0621F)); Spacer(Modifier.width(6.dp)) }
                }
                val siteName = torrent?.str("site_name")
                siteName?.let {
                    Tag(it, Color(0xFFE3E5FB), Color(0xFF414BB2))
                    Spacer(Modifier.width(6.dp))
                }
                torrent?.long("size")?.let {
                    Tag(humanSize(it), Color(0xFFF0F1F5), Color(0xFF5C5F70))
                    Spacer(Modifier.width(6.dp))
                }
                torrent?.long("seeders")?.let {
                    if (it > 0) Tag("做种$it", Color(0xFFE4F5EC), Color(0xFF2E9E5B))
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
