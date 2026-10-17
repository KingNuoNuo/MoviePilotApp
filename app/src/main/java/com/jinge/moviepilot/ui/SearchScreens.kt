package com.jinge.moviepilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jinge.moviepilot.api.MediaTileInfo
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.isTv
import com.jinge.moviepilot.api.long
import com.jinge.moviepilot.api.mediaSeasons
import com.jinge.moviepilot.api.posterUrlOf
import com.jinge.moviepilot.api.str
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonObject

/**
 * 搜索流：无选中媒体时显示搜索页（媒体结果 + 直接搜资源入口），
 * 选中媒体（剧集先弹选季）后显示资源列表。
 */
@Composable
fun SearchFlow(
    api: MpApi,
    selectedMedia: JsonObject?,
    directKeyword: String?,
    onSelectMedia: (JsonObject) -> Unit,
    onDirectSearch: (String) -> Unit,
    onSelectTab: (Int) -> Unit = {},
    showRanking: Boolean = true,
    showMaoyanRanking: Boolean = false,
    showMovieRanking: Boolean = false,
    /** 当前是否显示在屏幕上（MainActivity 常驻组合保状态，切走板块时折叠隐藏）；隐藏时返回键不归它管 */
    visible: Boolean = true,
    /** 榜单页签顺序（设置页「榜单」卡行序）：`maoyan` 猫眼 / `tv` 豆瓣 / `movie` 电影 */
    rankingOrder: List<String> = listOf("maoyan", "tv", "movie"),
    // 以下四项来自设置页「搜索」分组（TokenStore 持久化）
    strategy: Int = SortStrategy.SMART,
    qualityPref: Int = QualityPref.ANY,
    showDirectSearch: Boolean = true,
    showMediaMeta: Boolean = true,
    onBack: () -> Unit,
    key: Int,
) {
    var season by remember(selectedMedia) { mutableStateOf<Int?>(null) }
    var episode by remember(selectedMedia) { mutableStateOf<Int?>(null) }
    // 未播完的季才有值：该季最新已播集号（资源列表据此把「含最新集」的包置顶 + 推荐）
    var latestAired by remember(selectedMedia) { mutableStateOf<Int?>(null) }
    var showSeasonDialog by remember(selectedMedia) {
        mutableStateOf(selectedMedia?.isTv() == true)
    }

    if (selectedMedia != null) {
        if (showSeasonDialog) {
            SeasonEpisodeDialog(
                api = api,
                media = selectedMedia,
                onConfirm = { s, e, latest ->
                    season = s
                    episode = e
                    latestAired = latest
                    showSeasonDialog = false
                },
                onCancel = onBack,
            )
        } else {
            TorrentListScreen(api, media = selectedMedia, keyword = null, season = season, episode = episode, latestAired = latestAired, strategy = strategy, qualityPref = qualityPref, onSelectTab = onSelectTab, onBack = onBack, key = key)
        }
    } else if (directKeyword != null) {
        TorrentListScreen(api, media = null, keyword = directKeyword, season = null, episode = null, strategy = strategy, qualityPref = qualityPref, onSelectTab = onSelectTab, onBack = onBack, key = key)
    } else {
        MediaSearchScreen(api, onSelectMedia, onDirectSearch, key, showRanking, showMaoyanRanking, showMovieRanking, visible, rankingOrder, showDirectSearch, showMediaMeta)
    }
}

/**
 * 选季/选集弹窗（底部弹出的白色圆角卡片风）：
 * 上半部选季（chip，徽标 = 已播集数/总集数），下半部选集（数字网格，**只列已播出的集**）。
 *
 * 选集是**可选项**：没有「整季」这种选项，默认一个都不勾选，不选就代表按整季搜
 * （服务端与资源列表本来就按季过滤，选集只是进一步缩范围）。正在追的剧只列已播出的集，
 * 免得列出一堆还没播、搜也搜不到资源的日子。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SeasonEpisodeDialog(
    api: MpApi,
    media: JsonObject,
    /** (季号, 集号, 该季最新已播集号或 null) —— 第三个参数只在「未播完」时有值 */
    onConfirm: (Int?, Int?, Int?) -> Unit,
    onCancel: () -> Unit,
) {
    // 媒体搜索结果不带 seasons 字段，必须从服务器拉取
    var seasons by remember { mutableStateOf(mediaSeasons(media)) }
    var seasonsLoading by remember { mutableStateOf(false) }
    var seasonsFailed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    // 季 → 总集数（GET /media/seasons 的 episode_count，含未播集）
    var totalsBySeason by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    // 季 → **可选**的集号：已播出优先，查不到时退化为该季全部集（见 loadEpisodes）
    var episodesBySeason by remember { mutableStateOf<Map<Int, List<Int>>>(emptyMap()) }
    var epsFetching by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf(seasons.firstOrNull()) }
    var selectedEpisode by remember { mutableStateOf<Int?>(null) }
    var manualSeason by remember { mutableStateOf("1") }

    // 拉取季列表（服务器实时调 TMDB，偶发失败返回空 → 自动重试一次，仍失败给手动重试按钮）
    suspend fun fetchSeasons(): List<Int> {
        seasonsLoading = true
        seasonsFailed = false
        var result: List<Int> = emptyList()
        try {
            var eps = api.mediaSeasonEpisodes(media)
            if (eps.isEmpty()) {
                delay(1500)
                eps = api.mediaSeasonEpisodes(media)   // 重试一次（首次实时调 TMDB 可能抖动）
            }
            if (eps.isNotEmpty()) {
                totalsBySeason = eps
                result = eps.keys.sorted()
                seasons = result
                selectedSeason = selectedSeason ?: result.firstOrNull()
            } else {
                seasonsFailed = true
            }
        } catch (_: Exception) {
            seasonsFailed = true
        } finally {
            seasonsLoading = false
        }
        return result
    }

    /**
     * 预取各季的可选集号（限流 3 并发，别把宿主打满）。
     *
     * 只列**已播出**的集：正在追的剧 TMDB 会把未播集一并返回，列出来全是没资源的死选项。
     * 单季查询失败时**退化为该季全部集**（宁可多列，也别漏掉能下的），所以这里存的是
     * 「可直接渲染的集号」而不是「纯已播出集号」——调用方不用再区分两种情况。
     */
    suspend fun loadEpisodes(targets: List<Int>) {
        val tmdbId = (media.str("media_id") ?: media.str("tmdb_id"))?.toLongOrNull() ?: return
        val pending = targets.filter { episodesBySeason[it] == null }
        if (pending.isEmpty()) return
        epsFetching = true
        try {
            val gate = Semaphore(3)
            coroutineScope {
                pending.forEach { s ->
                    launch {
                        gate.withPermit {
                            val aired = runCatching { api.tmdbSeasonAiredEpisodes(tmdbId, s) }.getOrNull()
                            val total = totalsBySeason[s] ?: 0
                            val list = aired ?: if (total > 0) (1..total).toList() else null
                            if (list != null) episodesBySeason = episodesBySeason + (s to list)
                        }
                    }
                }
            }
        } finally {
            epsFetching = false
        }
    }

    LaunchedEffect(retryTick) {
        // 季列表 / 总集数任一缺失就重拉（失败时 seasons 可能还留着媒体自带的解析结果）
        val list = if (seasons.isEmpty() || totalsBySeason.isEmpty()) {
            fetchSeasons().ifEmpty { seasons }
        } else {
            seasons
        }
        loadEpisodes(list)
    }

    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color.White,
        shape = RoundedCornerShape(20.dp),
        title = {
            Column {
                Text(
                    media.str("title") ?: "选择季和集",
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = TextPrimary
                )
                Text(
                    // 「整季」已从选集里去掉，副标题不再写它 —— 不选集就代表按整季搜
                    if (selectedSeason == null) "搜索全部季的资源"
                    else "第 ${selectedSeason} 季" + (selectedEpisode?.let { " · 第 ${it} 集" } ?: ""),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column {
                // ---- 选季 ----
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("选择季", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
                    // 总季数（不含特别篇）贴右显示，不占额外一行
                    val seasonCount = seasons.count { it >= 1 }
                    if (seasonCount > 0) {
                        Spacer(Modifier.weight(1f))
                        Text("共 ${seasonCount} 季", fontSize = 11.sp, color = TextTertiary)
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    seasonsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在获取季信息…", fontSize = 13.sp, color = TextTertiary)
                    }
                    seasonsFailed -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "季信息获取失败（服务器访问 TMDB 暂时不通）",
                            fontSize = 13.sp, color = Color(0xFFB0621F), modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { retryTick++ }) {
                            Text("重试", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    seasons.isNotEmpty() -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SelectChip(
                            text = "全部季",
                            selected = selectedSeason == null,
                            onClick = { selectedSeason = null; selectedEpisode = null }
                        )
                        seasons.forEach { s ->
                            SelectChip(
                                text = if (s == 0) "特别篇" else "第 $s 季",
                                badge = seasonBadge(totalsBySeason[s], episodesBySeason[s]),
                                selected = selectedSeason == s,
                                onClick = { selectedSeason = s; selectedEpisode = null }
                            )
                        }
                    }
                    else -> OutlinedTextField(
                        value = manualSeason,
                        onValueChange = { manualSeason = it.filter(Char::isDigit).take(2) },
                        label = { Text("季号（留空 = 全部季）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    )
                }

                // ---- 选集 ----
                // 只列已播出的集，且**默认不勾选**：不选集 = 按整季搜，所以它是个可选项。
                // 再点一次已选中的集即可取消选择（否则「不选」就成了单向操作，取消不掉）。
                val epList = selectedSeason?.let { episodesBySeason[it] }
                if (selectedSeason != null && (epsFetching || epList != null)) {
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("选择集", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
                        Spacer(Modifier.weight(1f))
                        Text("可选 · 不选则按整季搜", fontSize = 11.sp, color = TextTertiary)
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        epList == null -> LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            trackColor = DividerColor,
                        )
                        epList.isEmpty() ->
                            Text("该季暂无已播出剧集", fontSize = 12.sp, color = TextTertiary)
                        else -> LazyVerticalGrid(
                            columns = GridCells.Adaptive(52.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            items(epList) { e ->
                                SelectChip(
                                    text = "E$e",
                                    selected = selectedEpisode == e,
                                    onClick = { selectedEpisode = if (selectedEpisode == e) null else e }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = if (seasons.isNotEmpty()) selectedSeason else manualSeason.toIntOrNull()
                    // 未播完才有值：最大可选集号 < 该季总集数 → 交给资源列表把「含最新集」的包置顶。
                    // 已播完 / 查不到总集数时给 null，排序退回原行为（整季包优先）。
                    val eps = s?.let { episodesBySeason[it] }
                    val total = s?.let { totalsBySeason[it] } ?: 0
                    val latest = eps?.maxOrNull()?.takeIf { total > 0 && it < total }
                    onConfirm(s, selectedEpisode, latest)
                },
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)
            ) { Text("搜索资源") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}

/**
 * 季徽标：已播出集数 / 总集数。
 *
 * 只有「没播完」才带总集数（`8/12集`）——全播完时写「12/12集」是废话；
 * 而且已播集数要异步查（见 [SeasonEpisodeDialog] 的 loadEpisodes），
 * 让满播季的徽标从头到尾都是「12集」，不会先显示总数再跳成别的样子。
 *
 * 传进来的是「可渲染集号」（可能是查不到时退化的全部集），全播完/退化两种情况
 * 大小都等于总集数，天然得到正确的「12集」。全都没有（还没查到）则不渲染徽标。
 */
private fun seasonBadge(total: Int?, episodes: List<Int>?): String? = when {
    episodes == null -> total?.let { "${it}集" }
    total == null || episodes.size >= total -> "${episodes.size}集"
    else -> "${episodes.size}/${total}集"
}

/** 弹窗用的选中 chip（选中的填主题色，未选中浅灰底） */
@Composable
private fun SelectChip(text: String, selected: Boolean, onClick: () -> Unit, badge: String? = null) {
    Surface(
        color = if (selected) MaterialTheme.colorScheme.primary else FieldFill,
        shape = RoundedCornerShape(10.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, DividerColor)
    ) {
        Row(
            Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text,
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) Color.White else TextPrimary
            )
            badge?.let {
                Spacer(Modifier.width(4.dp))
                Text(
                    it,
                    fontSize = 10.sp,
                    color = if (selected) Color(0xFFDDE0F7) else TextTertiary
                )
            }
        }
    }
}

@Composable
fun MediaSearchScreen(
    api: MpApi,
    onSelectMedia: (JsonObject) -> Unit,
    onDirectSearch: (String) -> Unit,
    key: Int,
    showRanking: Boolean = true,
    showMaoyanRanking: Boolean = false,
    /** 是否显示「电影票房榜」页签（国内 / 全球年度票房，同样依赖 MaoyanPro 插件快照） */
    showMovieRanking: Boolean = false,
    /** 当前是否显示在屏幕上（常驻组合保状态用）；隐藏时返回键拦截必须失效 */
    visible: Boolean = true,
    /** 榜单页签顺序（设置页「榜单」卡行序）：`maoyan` 猫眼 / `tv` 豆瓣 / `movie` 电影 */
    rankingOrder: List<String> = listOf("maoyan", "tv", "movie"),
    /** 是否显示搜索框右侧「直接搜种子资源」直达按钮（设置页「搜索」可关） */
    showDirectSearch: Boolean = true,
    /** 是否按需查媒体详情补「主演 + 当季首播日」（设置页「搜索」可关，关掉省掉逐条详情请求） */
    showMediaMeta: Boolean = true,
) {
    var query by remember(key) { mutableStateOf("") }
    var results by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var searchedText by remember(key) { mutableStateOf("") }
    var searchJob by remember(key) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 按设置里的顺序排「开启的榜」（id: maoyan 猫眼 / tv 豆瓣 / movie 电影）
    val enabledBoards = rankingOrder.filter {
        (it == "maoyan" && showMaoyanRanking) ||
            (it == "tv" && showRanking) ||
            (it == "movie" && showMovieRanking)
    }
    // 当前选中的榜 id；被关掉/不在序里时回退到第一个开启的
    var selectedBoard by remember(key) {
        mutableStateOf(
            rankingOrder.firstOrNull {
                (it == "maoyan" && showMaoyanRanking) ||
                    (it == "tv" && showRanking) ||
                    (it == "movie" && showMovieRanking)
            } ?: "maoyan"
        )
    }
    val selectedTab = if (selectedBoard in enabledBoards) selectedBoard else enabledBoards.firstOrNull() ?: "maoyan"
    // 各榜各自的「更新 10-15 19:07」，由榜单加载完成后回传，供页签行 / 标题行显示
    var maoyanUpdated by remember(key) { mutableStateOf<String?>(null) }
    var doubanUpdated by remember(key) { mutableStateOf<String?>(null) }
    var movieUpdated by remember(key) { mutableStateOf<String?>(null) }
    // 搜索结果磁贴的补充信息缓存：媒体键 -> 主演 +「当季」首播日
    // （跨滚动保留，来回滚动不重复请求详情）
    val tileCache = remember(key) { mutableStateMapOf<String, MediaTileInfo>() }
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary

    fun doMediaSearch(text: String) {
        searchJob?.cancel()   // 新搜索取消旧搜索，避免旧结果覆盖新结果
        searchJob = scope.launch {
            loading = true; error = null
            try {
                query = text   // 同步搜索框（榜单点击进入时 query 为空）
                results = api.searchMedia(text)
                searchedText = text
                if (results.isEmpty()) error = "没有找到相关媒体"
            } catch (e: Exception) {
                error = e.message ?: "搜索失败"
            } finally {
                loading = false
            }
        }
    }

    /**
     * 网络恢复（切网后回到能连上服务器的网络）时，把上一次失败的搜索自动重跑一遍 ——
     * 否则搜索结果区会一直停在「搜索失败」那行红字上，要用户手动再搜一次才恢复。
     */
    LaunchedEffect(api.netEpoch) {
        if (searchedText.isNotBlank() && error != null) doMediaSearch(searchedText)
    }

    /** 退出搜索结果回到首页（取消进行中的搜索，清空状态） */
    fun backToHome() {
        searchJob?.cancel(); searchJob = null
        query = ""; results = emptyList(); searchedText = ""; error = null; loading = false
    }

    val isHome = searchedText.isEmpty() && !loading && results.isEmpty() && error == null

    /**
     * 搜索框：白卡 52dp / 14dp 圆角（与首页榜单卡同族），聚焦时 1.5dp 主色描边。
     *
     * 为什么不用 `OutlinedTextField`：它的最小高度 56dp、内边距不可控，字压不住 15sp 的节奏，
     * 而且没法把「直接搜种子」这颗按钮并进框内（以前是框外独立的一颗白方块，两颗各自带阴影、
     * 视觉上完全脱节）。这里自绘：左侧放大镜 / 返回箭头 + 中间 BasicTextField + 右侧种子按钮，
     * 点卡片任意空白处都会聚焦输入框（放大镜是最常被点的位置）。
     */
    val searchBar: @Composable (Modifier) -> Unit = { modifier ->
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val focusRequester = remember { FocusRequester() }
        val shape = RoundedCornerShape(14.dp)
        Row(
            modifier
                .height(52.dp)
                .then(
                    // 描边已经足够说明「正在输入」，同时保留阴影会让卡片发糊：二选一
                    if (focused) Modifier
                    else Modifier.shadow(
                        elevation = 1.dp,
                        shape = shape,
                        spotColor = Color(0x1A1A1C2B),
                        ambientColor = Color(0x1A1A1C2B),
                    )
                )
                .clip(shape)
                .background(Color.White)
                .then(if (focused) Modifier.border(1.5.dp, primary, shape) else Modifier)
                // 整卡可点 → 聚焦输入框；indication 置空，免得点一下整张卡泛涟漪
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusRequester.requestFocus() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isHome) {
                Icon(
                    Icons.Default.Search, null,
                    tint = if (focused) primary else TextTertiary,
                    modifier = Modifier.padding(start = 14.dp).size(18.dp)
                )
            } else {
                // 结果态：返回箭头（点它清空搜索回首页）
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { backToHome() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回首页",
                        tint = TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (isHome) 8.dp else 4.dp)
                    .focusRequester(focusRequester),
                textStyle = TextStyle(fontSize = 15.sp, color = TextPrimary),
                singleLine = true,
                cursorBrush = SolidColor(primary),
                interactionSource = interaction,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.isNotBlank()) doMediaSearch(query.trim()) }
                ),
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            Text(
                                "搜索电影、剧集…",
                                fontSize = 15.sp,
                                color = Color(0xFFA6AABE),
                                maxLines = 1,
                                overflow = TextOverflow.Clip
                            )
                        }
                        inner()
                    }
                }
            )
            // 直达按钮可关掉（有人只用媒体搜索/榜单，不需要这颗按钮占宽）
            if (showDirectSearch) {
                val directEnabled = query.isNotBlank()
                Box(
                    Modifier
                        .padding(vertical = 6.dp, horizontal = 6.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (directEnabled) SelectedPill else FieldFill)
                        .clickable(enabled = directEnabled) { onDirectSearch(query.trim()) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.FileDownload,
                        contentDescription = "直接搜种子资源",
                        tint = if (directEnabled) SelectedPillText else Color(0xFFC3C6D4),
                        modifier = Modifier.size(19.dp)
                    )
                }
            } else {
                Spacer(Modifier.width(14.dp))
            }
        }
    }

    // 搜索结果态：物理返回键先回首页，而不是直接退出 app（隐藏态下不拦截，让位给其他板块）
    BackHandler(enabled = visible && !isHome) { backToHome() }

    Column(Modifier.fillMaxSize()) {
        if (isHome) {
            // ---- 首页：搜索框 hero 布局；开启榜单时下方接榜单区 ----
            // 任一榜单开启（豆瓣或猫眼或电影）就不再显示居中的 hero 徽标区，整屏高度让给榜单
            val anyRanking = enabledBoards.isNotEmpty()
            // 两个及以上榜开启 → 胶囊页签切换，一次只显示一个，榜单区拿满剩余高度
            val multiRanking = enabledBoards.size >= 2
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (anyRanking) Modifier else Modifier.weight(1f)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (anyRanking) Spacer(Modifier.height(20.dp))
                else {
                    Spacer(Modifier.weight(0.85f))
                    // Logo 徽标（仅无榜单的居中 hero 布局显示）
                    Box(
                        Modifier
                            .size(84.dp)
                            .background(
                                Brush.linearGradient(listOf(primary, Color(0xFF7A82E8))),
                                RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Search, null,
                            tint = Color.White, modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "MoviePilot",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "发现电影 · 剧集 · 一键订阅下载",
                        fontSize = 13.sp,
                        color = TextTertiary
                    )
                    Spacer(Modifier.height(28.dp))
                }
                // 搜索框（种子入口已并进卡片右侧，不再单独占一颗悬浮方块）
                searchBar(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                if (anyRanking) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        if (multiRanking) {
                            // 切换行：「榜单」标签 + 开启的官方图形图标段（顺序跟设置里的行序），
                            // 右侧当前榜更新时间
                            RankingTabRow(
                                boards = enabledBoards,
                                selected = selectedTab,
                                onSelect = { selectedBoard = it },
                                updatedAt = when (selectedTab) {
                                    "maoyan" -> maoyanUpdated
                                    "tv" -> doubanUpdated
                                    else -> movieUpdated
                                },
                            )
                        } else {
                            // 只开一个榜：没有页签，改用榜单自己的标题行（同样带更新时间）
                            RankingBoardTitle(
                                title = when (selectedTab) {
                                    "maoyan" -> "猫眼电视榜单"
                                    "tv" -> "豆瓣电视榜单"
                                    else -> "电影票房榜"
                                },
                                hint = when (selectedTab) {
                                    "maoyan" -> "剧集热度 · 综艺热度"
                                    "tv" -> "国内 · 国外 每周更新"
                                    else -> "国内 · 全球 年度票房"
                                },
                                updatedAt = when (selectedTab) {
                                    "maoyan" -> maoyanUpdated
                                    "tv" -> doubanUpdated
                                    else -> movieUpdated
                                },
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // 开启的榜都常驻组合（数据各拉一次），只把选中那个撑开、其余压成 0 高：
                        // 切页签不重新请求接口，也不会闪加载动画。
                        if ("maoyan" in enabledBoards) {
                            MaoyanRankingBoard(
                                api = api,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!multiRanking || selectedTab == "maoyan") Modifier.weight(1f)
                                        else Modifier.height(0.dp)
                                    ),
                                onPickTitle = { doMediaSearch(it) },
                                onUpdated = { maoyanUpdated = it },
                            )
                        }
                        if ("tv" in enabledBoards) {
                            TvRankingBoard(
                                api = api,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!multiRanking || selectedTab == "tv") Modifier.weight(1f)
                                        else Modifier.height(0.dp)
                                    ),
                                onPickTitle = { doMediaSearch(it) },
                                onUpdated = { doubanUpdated = it },
                            )
                        }
                        if ("movie" in enabledBoards) {
                            MovieRankingBoard(
                                api = api,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!multiRanking || selectedTab == "movie") Modifier.weight(1f)
                                        else Modifier.height(0.dp)
                                    ),
                                onPickTitle = { doMediaSearch(it) },
                                onUpdated = { movieUpdated = it },
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1.15f))
                }
            }
        } else {
            // ---- 搜索后：搜索框置顶 ----
            searchBar(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            )
            when {
                loading -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                error != null -> Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🔍", fontSize = 36.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(error!!, color = TextTertiary, fontSize = 14.sp)
                    }
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    items(results) { media ->
                        MediaCard(api, media, tileCache, enableMeta = showMediaMeta) { onSelectMedia(media) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCard(
    api: MpApi,
    media: JsonObject,
    tileCache: MutableMap<String, MediaTileInfo>,
    /** 关掉后不查详情、不显示主演与首播日（只剩类型行），省掉逐条详情请求 */
    enableMeta: Boolean = true,
    onClick: () -> Unit,
) {
    // 首播时间：搜索结果自带的 first_air_date 是**整剧第一季**的日期，多季剧集（如「现在就出发」
    // 已有第 4 季）会显示成几年前，所以优先用媒体详情里「当季」（最新一季）的首播日；
    // 季信息随下面这次取主演的详情请求一起回来（零新增请求），没拿到时回落搜索结果自带的日期。
    val fallbackPremiere = listOfNotNull(media.str("first_air_date"), media.str("release_date"))
        .firstOrNull { it.isNotBlank() }
    // 主演 +「当季」首播日：搜索接口都不返回，需按需查一次媒体详情（磁贴进入可见区域时才请求）
    val mid = media.str("media_id")
    val cacheKey = "${media.str("media_source") ?: "themoviedb"}:$mid"
    val cached = tileCache[cacheKey]
    LaunchedEffect(cacheKey, enableMeta) {
        if (enableMeta && cached == null && !mid.isNullOrBlank()) {
            // 失败/无数据也写入空结果缓存，避免每次滚回来都重发请求
            tileCache[cacheKey] = api.mediaTileInfo(
                mediaSource = media.str("media_source") ?: "themoviedb",
                mediaId = mid,
                title = media.str("title"),
                year = media.str("year"),
                typeName = media.str("type")?.takeIf { it.isNotBlank() } ?: "电视剧",
            )
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = Color(0x1E1A1C2B))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        MpPosterImage(
            api = api,
            raw = media.str("poster_path"),
            contentDescription = media.str("title"),
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(DividerColor)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            // 标题行：评分跟在标题右侧占位，不再单独占一行
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    media.str("title") ?: "未知",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                media.str("vote_average")?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let {
                    Spacer(Modifier.width(6.dp))
                    Text("★ $it", fontSize = 11.sp, color = Color(0xFFE8A33D))
                }
            }
            // 剧情介绍（内容信息，放在上半区）
            media.str("overview")?.takeIf { it.isNotBlank() }?.let { ov ->
                Spacer(Modifier.height(3.dp))
                Text(
                    ov,
                    fontSize = 12.sp,
                    color = TextSecondary,
                    lineHeight = 16.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 下半区：1dp 分隔线把「编排信息（类型/首播/主演）」与剧情简介分开
            // 「类型 · 首播」合成一行（不带年份，用户要求去掉）
            val premiere = if (enableMeta) cached?.seasonDate ?: fallbackPremiere else null
            val metaLine = listOfNotNull(
                media.str("type")?.takeIf { it.isNotBlank() },
                premiere?.let { "首播 $it" },
            ).joinToString(" · ")
            val actors = if (enableMeta) cached?.actors?.takeIf { it.isNotEmpty() } else null
            if (metaLine.isNotBlank() || actors != null) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(DividerColor))
                Spacer(Modifier.height(6.dp))
            }
            if (metaLine.isNotBlank()) {
                Text(
                    metaLine,
                    fontSize = 11.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 前三位主演（详情接口异步补全；未取到时该行不占位）
            actors?.let { list ->
                if (metaLine.isNotBlank()) Spacer(Modifier.height(2.dp))
                Text(
                    "主演  " + list.joinToString(" / "),
                    fontSize = 11.sp,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 当前时间（yyyy-MM-dd HH:mm:ss）：豆瓣榜是实时拉的，没有服务端时间戳，用它当「更新」时间 */
private fun nowStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date())

/** 榜单切换图标尺寸：猫眼图形自带的圆盘外径 = 21.56/24 格，即这个尺寸就是圆盘直径 */
private val BrandIconSize = 18.dp
/** 豆瓣满格方图形相对猫眼圆盘的内缩比（配平用，见 [RankingBrandTab]） */
private const val SquareIconFit = 0.9f

/**
 * 双榜（或多榜）并存时的切换行：白胶囊容器 =「榜单」标签 + 1dp 竖分隔线 + 各榜品牌图标段，
 * 右侧是当前榜的更新时间。
 *
 * - 「榜单」是**纯标签**（11sp 灰字、不加点击反馈），并入胶囊后与图标共享同一容器边界，不再孤立；
 * - 猫眼/豆瓣为官方单色图形：选中段主色实底 + 白图标，未选中填各自品牌色（红圆 / 绿方）；
 * - 电影榜是自设计多色渐变图形（[BrandVideoIcons.Movie]，无法反白/tint 染色）：
 *   选中 = #E8EAFB 浅紫底 + 原色图标，未选中 = 透明底 + 原色（与猫眼平台行的多色段同语言）；
 * - 段与段之间只换实底，切换动效天然（与 [PillTab] 的语言同源）。
 *
 * 只渲染切换行本身，选中项决定下方哪个榜被撑开；只画开启了的段，段序 = [boards] 列表序
 * （设置页「榜单」卡行序）。
 */
@Composable
private fun RankingTabRow(
    boards: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    updatedAt: String?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "榜单",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = TextTertiary,
                modifier = Modifier.padding(start = 6.dp, end = 6.dp)
            )
            Box(Modifier.width(1.dp).height(16.dp).background(DividerColor))
            Spacer(Modifier.width(6.dp))
            boards.forEachIndexed { index, id ->
                if (index > 0) Spacer(Modifier.width(3.dp))
                when (id) {
                    "maoyan" -> RankingBrandTab(
                        label = "猫眼榜",
                        icon = BrandIcons.Maoyan,
                        brandColor = MaoyanRed,
                        selected = selected == "maoyan",
                    ) { onSelect("maoyan") }
                    "tv" -> RankingBrandTab(
                        label = "豆瓣榜",
                        icon = BrandIcons.Douban,
                        brandColor = DoubanGreen,
                        selected = selected == "tv",
                        squareIcon = true,
                    ) { onSelect("tv") }
                    "movie" -> RankingBrandTab(
                        label = "电影榜",
                        icon = BrandVideoIcons.Movie,
                        brandColor = Color.Unspecified,
                        selected = selected == "movie",
                        squareIcon = true,
                        colorful = true,
                    ) { onSelect("movie") }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        shortStamp(updatedAt)?.let {
            Text("更新 $it", fontSize = 10.sp, color = Color(0xFFA6AABE))
        }
    }
}

/**
 * 榜单切换的单个品牌段（42×28dp）。
 *
 * - 单色图形（默认）：选中主色实底 + 白图标，未选中透明底 + 品牌色图标；
 * - [colorful] 多色渐变图形（电影榜）：无法反白也不能被 tint 染色，选中 = #E8EAFB 浅紫底 +
 *   原色图标（tint = Unspecified 保留原色），未选中 = 透明底 + 原色 —— 与猫眼平台行同语言。
 *
 * [squareIcon] 给「满格方形」图形（豆瓣/电影）用：猫眼图形自带圆形留白、外径只占 21.56/24 格，
 * 而满格方是铺满整格的方，两者同尺寸并排会让方块明显大于圆盘；缩到
 * [SquareIconFit] 后外接尺寸一致（配平）。豆瓣官方是纯直角方，缩到 18dp 会显沉，
 * 按图形自身比例（5/24）裁圆角。
 */
@Composable
private fun RankingBrandTab(
    label: String,
    icon: ImageVector,
    brandColor: Color,
    selected: Boolean,
    squareIcon: Boolean = false,
    colorful: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(width = 42.dp, height = 28.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(
                when {
                    !selected -> Color.Transparent
                    colorful -> Color(0xFFE8EAFB)
                    else -> MaterialTheme.colorScheme.primary
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val tint = when {
            colorful -> Color.Unspecified
            selected -> Color.White
            else -> brandColor
        }
        if (squareIcon) {
            val side = BrandIconSize * SquareIconFit
            Box(Modifier.size(side).clip(RoundedCornerShape(side * (5f / 24f)))) {
                Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.fillMaxSize())
            }
        } else {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(BrandIconSize))
        }
    }
}

/** 只开一个榜时的标题行（沿用原榜单标题版式：标题 + hint + 右侧更新时间） */
@Composable
private fun RankingBoardTitle(title: String, hint: String, updatedAt: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )
        Spacer(Modifier.width(6.dp))
        Text(hint, fontSize = 10.sp, color = Color(0xFFA6AABE))
        Spacer(Modifier.weight(1f))
        shortStamp(updatedAt)?.let {
            Text("更新 $it", fontSize = 10.sp, color = Color(0xFFA6AABE))
        }
    }
}

/**
 * 首页电视榜单：左右两列（国内榜 / 国外榜），数据走服务端豆瓣周榜接口
 * （/recommend/douban_tv_weekly_chinese|global，服务端 DoubanChain 抓取，App 不直连豆瓣）。
 * 两个榜并发拉取，单个失败不拖累另一个；加载中/失败/空均有降级显示。
 * 标题行由调用方渲染（单榜=榜单标题 / 双榜=胶囊页签），这里只出列头 + 两列列表。
 */
@Composable
private fun TvRankingBoard(
    api: MpApi,
    modifier: Modifier = Modifier,
    onPickTitle: (String) -> Unit,
    onUpdated: (String?) -> Unit = {},
) {
    var chinese by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var global by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    // media_id -> 首播日期（yyyy-MM-dd）；榜单接口不返回日期，异步按条补全
    var dates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    // 失败原因是「连不上服务器」（抖动，会自愈）还是「服务端没数据」（要用户处理）——决定提示文案
    var offline by remember { mutableStateOf(false) }

    // key 用 api.netEpoch：切换网络后自动重新加载（见 MpApi.onNetworkChanged）。
    // ⚠️ 只重载一次不够：切网回调到达时新网络往往还没真正通（DHCP / DNS / 路由就绪要几秒），
    // 这一轮必然失败、netEpoch 又不会再变 → 错误被写死，用户只能切到别的榜再切回来。
    // 所以失败后按 retryDelayMs 退避自愈，拿到数据即止（协程随页面离开自动取消）。
    LaunchedEffect(api.netEpoch) {
        var attempt = 0
        while (true) {
            if (attempt > 0) delay(retryDelayMs(attempt - 1))
            // 首次才显示「加载中」，静默重试不闪屏
            loading = attempt == 0
            failed = false
            offline = false
            try {
                coroutineScope {
                    val c = async { runCatching { api.doubanTvWeeklyChinese(count = 10) } }
                    val g = async { runCatching { api.doubanTvWeeklyGlobal(count = 10) } }
                    val cr = c.await()
                    val gr = g.await()
                    // 重新加载（切网途中会短暂连不上）没拿到数据时保留上一次的榜单，
                    // 不要把首页闪成「暂无数据」；首次加载本来就空，行为和以前一致
                    cr.getOrNull()?.takeIf { it.isNotEmpty() }?.let { chinese = it }
                    gr.getOrNull()?.takeIf { it.isNotEmpty() }?.let { global = it }
                    offline = cr.exceptionOrNull() != null || gr.exceptionOrNull() != null
                }
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
                // 豆瓣榜是实时拉的，没有服务端时间戳，「更新」就用本次拉取完成的时间
                onUpdated(nowStamp())
            }
            failed = chinese.isEmpty() && global.isEmpty()
            if (!failed) break
            attempt++
            // 不是网络问题（服务端确实没数据）就别无限重试：试满 3 次就停在提示上
            if (!offline && attempt >= 3) break
        }
        // 榜单主体先显示，日期逐条补全（并发，失败的条目自动跳过）
        val all = (chinese + global)
        val ids = all.mapNotNull { it.str("media_id")?.takeIf { v -> v.isNotBlank() } }.distinct()
        if (ids.isNotEmpty()) {
            coroutineScope {
                val jobs = ids.map { id ->
                    async {
                        val item = all.firstOrNull { it.str("media_id") == id } ?: return@async null
                        val src = item.str("media_source") ?: "douban"
                        val type = item.str("type") ?: "电视剧"
                        // 关键：按「榜单条目的季号」取该季首播日，不能用整剧第一季的日期
                        val se = item.long("season")?.toInt()
                        val d = runCatching {
                            api.mediaPremiereDate(
                                mediaSource = src,
                                mediaId = id,
                                title = item.str("title"),
                                year = item.str("year"),
                                typeName = type,
                                season = se,
                            )
                        }.getOrNull()
                        if (d.isNullOrBlank()) null else id to d
                    }
                }
                dates = jobs.mapNotNull { it.await() }.toMap()
            }
        }
    }

    Column(modifier.fillMaxSize()) {
        // 列头行：两列等宽，与下方列表严格对齐（榜单标题由调用方渲染）
        Row(Modifier.fillMaxWidth()) {
            Text(
                "国内榜",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "国外榜",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("榜单加载中…", fontSize = 12.sp, color = TextTertiary)
            }
            failed && chinese.isEmpty() && global.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (offline) "连接失败，正在自动重试…" else "榜单暂不可用（服务端豆瓣接口异常）",
                        fontSize = 12.sp, color = TextTertiary
                    )
                }
            else -> Row(Modifier.fillMaxSize()) {
                RankingColumn(
                    api = api,
                    items = chinese,
                    dates = dates,
                    modifier = Modifier.weight(1f),
                    onPickTitle = onPickTitle,
                )
                Spacer(Modifier.width(12.dp))
                RankingColumn(
                    api = api,
                    items = global,
                    dates = dates,
                    modifier = Modifier.weight(1f),
                    onPickTitle = onPickTitle,
                )
            }
        }
    }
}

/** 榜单单列：纵向排名条目（半宽小卡：排名 + 小海报 + 标题/首播时间/评分）；列头由榜单统一渲染 */
@Composable
private fun RankingColumn(
    api: MpApi,
    items: List<JsonObject>,
    dates: Map<String, String>,
    modifier: Modifier = Modifier,
    onPickTitle: (String) -> Unit,
) {
    Column(modifier) {
        if (items.isEmpty()) {
            Text("暂无数据", fontSize = 11.sp, color = Color(0xFFA6AABE))
        } else {
            // 列表状态提到外面：底部「⌄ 更多」提示靠 canScrollForward 判断还有没有内容可滑
            val listState = rememberLazyListState()
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // 底部留出「⌄ 更多」胶囊的高度：不然滑到最后一屏时整条提示会压住末条卡片的日期/评分行
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items.take(10)) { index, item ->
                        RankItemCard(
                            api = api,
                            rank = index + 1,
                            item = item,
                            firstAirDate = item.str("media_id")?.let { dates[it] },
                            onClick = {
                                val t = item.str("title")
                                if (!t.isNullOrBlank()) onPickTitle(t)
                            },
                        )
                    }
                }
                ScrollMoreHint(
                    visible = listState.canScrollForward,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * 榜单列底部的滚动提示：这列还有内容能往下滑时，底部居中出现一枚「⌄ 更多」胶囊，
 * 轻微上下呼吸提示「可以滑动」；滑到底或内容不足一屏时自动淡出（淡入淡出，不占位）。
 *
 * ⚠️ 榜单两列是各自独立滚动的 LazyColumn，提示必须每列各挂一枚 ——
 * 整块居中放一个会让人误以为两列一起滚（实际只滑了手指下那一列）。
 *
 * @param visible 由该列自己的 `LazyListState.canScrollForward` 驱动，不要写死 true
 */
@Composable
private fun ScrollMoreHint(visible: Boolean, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        // 呼吸动效：整体上下浮动 3dp，来回各 700ms
        val breathe = rememberInfiniteTransition(label = "scrollHint")
        val dy by breathe.animateFloat(
            initialValue = 0f,
            targetValue = 3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dy",
        )
        Row(
            Modifier
                .offset(y = dy.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .border(1.dp, DividerColor, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "还有更多",
                tint = TextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(Modifier.width(3.dp))
            Text("更多", fontSize = 11.sp, color = TextSecondary)
        }
    }
}

/**
 * 榜单标题里的季号统一压成「S+数字」，并从剧名里**摘出来单独返回**。
 *
 * 榜单数据源给的名字本身带中文季 —— 猫眼榜尤其常见（「现在就出发 第4季」「心动的信号 第九季」
 * 「喜剧之王单口季 第3季」）。中文季号占字形宽，在 158dp 的半宽列里常被挤到第二行；
 * 现在把季号画到海报右下角的角标上（与左上角的名次角标对角呼应），标题行只剩剧名，最省宽度。
 *
 * 只匹配「第…季」这个固定格式，不会误伤剧名里自带「季」的词（如「喜剧之王单口季」）。
 * 认不出数字（如「第十一季」以外的怪写法）时原样保留在剧名里，季号返回 null。
 *
 * @return `first` = 去掉季号的剧名；`second` = 季号角标文案（如 `S4`），无则 null
 */
private val SEASON_RE = Regex("\\s*第\\s*([0-9]+|[零一二两三四五六七八九十]+)\\s*季")

private fun rankTitleParts(raw: String): Pair<String, String?> {
    val m = SEASON_RE.find(raw) ?: return raw to null
    val n = chineseNumberToInt(m.groupValues[1]) ?: return raw to null
    return raw.removeRange(m.range).trim() to "S$n"
}

/** 「4 / 十 / 十二 / 二十」→ 整数；认不出返回 null（此时季号保留原文，不硬转） */
private fun chineseNumberToInt(s: String): Int? {
    s.toIntOrNull()?.let { return it }
    val digits = mapOf(
        '零' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
    )
    val tenAt = s.indexOf('十')
    if (tenAt < 0) return if (s.length == 1) digits[s[0]] else null
    val tens = if (tenAt == 0) 1 else (digits[s[0]] ?: return null)
    val ones = if (tenAt == s.lastIndex) 0 else (digits[s[tenAt + 1]] ?: return null)
    return tens * 10 + ones
}

/**
 * 首播日显示成 `2026.8.5` 这种紧凑写法（月 / 日去掉前导零），比 `2026-08-05` 短两三个字宽，
 * 正好和后面的「★ 评分」并在同一行还不挤。不是完整日期的（只有年份、播放平台名等）原样返回。
 */
private val AIR_DATE_RE = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})$")

private fun formatAirDate(raw: String): String {
    val m = AIR_DATE_RE.matchEntire(raw.trim()) ?: return raw
    return "${m.groupValues[1]}.${m.groupValues[2].toInt()}.${m.groupValues[3].toInt()}"
}

/** 榜单条目海报尺寸：44dp 宽、0.7 比例 → 高约 63dp。文字区高度与它对齐，首行贴顶、信息行贴底。 */
private val RANK_POSTER_WIDTH = 44.dp
private val RANK_POSTER_HEIGHT = 44.dp / 0.7f

/**
 * 猫眼榜条目 `platform` 里的属性后缀（实况值形如「腾讯视频独播」「爱奇艺独播」）。
 * 渲染时从平台名里摘掉、单独做成一颗微徽标，平台名本体才能加重一档形成层级。
 */
private const val PLATFORM_EXCLUSIVE_SUFFIX = "独播"

/** 榜单条目小卡：点击后用标题回搜媒体（豆瓣来源的数据不能直接搜资源，需先匹配 TMDB 媒体） */
@Composable
private fun RankItemCard(
    api: MpApi,
    rank: Int,
    item: JsonObject,
    firstAirDate: String?,
    premiereDate: String? = null,
    platform: String? = null,
    /** 渲染用标题（全球票房榜的英文原名优先显示识别出的中文名）；点击回搜仍用原始 title */
    displayTitle: String? = null,
    onClick: () -> Unit,
) {
    // 显示用标题做「季号压成 S+数字」处理并摘出季号；点击回搜仍用原始 title（见 [rankTitleParts] 说明）
    val (title, season) = rankTitleParts(displayTitle ?: item.str("title") ?: "未知")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(6.dp),
        // 顶对齐：文字区被固定成海报高度，靠 SpaceBetween 把首行顶到海报上沿、信息行压到海报下沿
        verticalAlignment = Alignment.Top
    ) {
        // 名次做成海报左上角的角标：原来它单独占「16dp 宽 + 4dp 间距」，把这 20dp 还给标题后，
        // 标题从 74dp 涨到 94dp（12sp 下约 6 字 → 15 字），「此处通往繁星」这类长名才放得下。
        Box {
            MpPosterImage(
                api = api,
                // 猫眼榜单流水用的是 poster，宿主榜单/媒体是 poster_path，两个都兼容
                raw = item.str("poster") ?: item.str("poster_path"),
                contentDescription = title,
                modifier = Modifier
                    .width(RANK_POSTER_WIDTH)
                    .height(RANK_POSTER_HEIGHT)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DividerColor)
            )
            // 角标外角跟海报左上角同曲率，内角收一点，贴在海报上不显得是块浮贴的补丁
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(topStart = 6.dp, bottomEnd = 5.dp))
                    .background(
                        if (rank <= 3) MaterialTheme.colorScheme.primary
                        else Color.Black.copy(alpha = 0.55f)
                    )
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(
                    "$rank",
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
            // 季号（S4 这类）画在海报右下角：与左上角名次角标对角呼应，只占两个字宽，
            // 剧名因此能整行留给汉字。没有季号的条目（电影等）整块不渲染。
            season?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .clip(RoundedCornerShape(topStart = 5.dp, bottomEnd = 6.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        it,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        // 文字区固定成海报高度：SpaceBetween 把标题顶到海报上沿、信息行压到海报下沿，
        // 于是不管几张信息行，卡片高度都等于海报高度 —— 两列的行与行严格对齐。
        Column(
            Modifier
                .weight(1f)
                .height(RANK_POSTER_HEIGHT),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                title,
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp,
                color = TextPrimary
            )
            // 信息行归一到统一语义：`platform`（播放平台）只有猫眼榜有，豆瓣传 null；日期两榜都从
            // `premiereDate`（该条目所在季首播日）/ `firstAirDate` 取，都没有时回退年份。
            // 平台做成独立入参而不是复用 firstAirDate —— 原来靠「premiereDate 是否为空」来分派，
            // 猫眼的首播日是异步补的，没补到时平台名会落到日期行、绕过平台样式（实测出现过一行旧样式）。
            Column {
                val platformLine = platform?.takeIf { it.isNotBlank() }
                val airDate = (premiereDate ?: firstAirDate)
                    ?.takeIf { it.isNotBlank() }
                    ?: item.str("year")?.takeIf { it.isNotBlank() }
                val rating = item.str("vote_average")
                    ?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }
                // 播放平台独占一行（只有猫眼榜有）。原来整串「腾讯视频独播」是裸灰字，跟下面同色同重的
                // 日期行叠在一起没有主次；这里把「独播」这个属性后缀拆出来做一颗微徽标，平台名本体加重一档
                // （TextSecondary + Medium），于是「谁家平台」和「什么属性」分成两段，属性弱化成装饰。
                // 不是独播的（「全网」、空值）只渲染平台名，不会留一颗空徽标。
                platformLine?.let { raw ->
                    val exclusive = raw.endsWith(PLATFORM_EXCLUSIVE_SUFFIX)
                    val name = if (exclusive) raw.dropLast(PLATFORM_EXCLUSIVE_SUFFIX.length).trim() else raw
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            name.ifBlank { raw },
                            fontSize = 10.sp, lineHeight = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            // 名字先让位：宽度不够时省略平台名，别把徽标挤出卡片
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (exclusive) {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                PLATFORM_EXCLUSIVE_SUFFIX,
                                fontSize = 9.sp, lineHeight = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = SelectedPillText,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(SelectedPill)
                                    .padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                // 首播日贴左、评分贴右：评分在右边缘排出一条整齐的竖线
                if (airDate != null || rating != null) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        airDate?.let {
                            Text(
                                formatAirDate(it),
                                fontSize = 10.sp, lineHeight = 12.sp, color = TextTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                // 占满剩余宽度把评分顶到右边缘；宽度不够时先省略日期
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (airDate != null && rating != null) Spacer(Modifier.width(6.dp))
                        rating?.let {
                            Text(
                                "★ $it",
                                fontSize = 10.sp, lineHeight = 12.sp,
                                color = Color(0xFFE8A33D), maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 猫眼平台细分榜的 platformType（猫眼接口参数，下标与平台切换行一致）：
 * 空串 = 全网；`3` 腾讯视频、`2` 爱奇艺、`7` 芒果TV、`1` 优酷。
 */
private val MAOYAN_PLATFORM_TYPES = listOf("", "3", "2", "7", "1")

/**
 * 猫眼榜单平台切换行：白胶囊容器与上方「榜单｜猫眼｜豆瓣」切换行完全同构 ——
 * 「平台」标签（11sp 灰字、纯标签无点击反馈）+ 1dp 竖分隔线 + 5 个 42×28 图标段。
 *
 * - 全网是自设计单色地球（[BrandVideoIcons.AllNet]）：选中 = 主色实底 + 白描线，
 *   未选中 = 透明底 + 主色描线 —— 与猫眼/豆瓣页签的选中语言完全一致；
 * - 四家品牌图形是多色（渐变/多 path），无法反白也不能被 tint 染色：
 *   选中 = #E8EAFB 浅紫底 + 原色图标（tint = Unspecified 保留原色），未选中透明底 + 原色。
 *   整行只有一段带底色，一眼可辨当前平台。
 */
@Composable
private fun MaoyanPlatformRow(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .border(1.dp, DividerColor, RoundedCornerShape(14.dp))
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "平台",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = TextTertiary,
            modifier = Modifier.padding(start = 6.dp, end = 6.dp)
        )
        Box(Modifier.width(1.dp).height(16.dp).background(DividerColor))
        Spacer(Modifier.width(6.dp))
        // Triple: (无障碍描述, 图标, 是否单色图形)
        val platforms = listOf(
            Triple("全网", BrandVideoIcons.AllNet, true),
            Triple("腾讯视频", BrandVideoIcons.TencentVideo, false),
            Triple("爱奇艺", BrandVideoIcons.Iqiyi, false),
            Triple("芒果TV", BrandVideoIcons.Mgtv, false),
            Triple("优酷", BrandVideoIcons.Youku, false),
        )
        platforms.forEachIndexed { index, (label, icon, mono) ->
            if (index > 0) Spacer(Modifier.width(3.dp))
            Box(
                Modifier
                    .size(width = 42.dp, height = 28.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        when {
                            selected != index -> Color.Transparent
                            mono -> MaterialTheme.colorScheme.primary
                            else -> Color(0xFFE8EAFB)
                        }
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center
            ) {
                if (mono) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (selected == index) Color.White else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * 首页猫眼榜单：平台切换行（全网｜腾讯视频｜爱奇艺｜芒果TV｜优酷）+ 左右两列
 * （左 = 剧集热度：电视剧 + 网络剧；右 = 综艺热度）。
 *
 * 数据来源是服务端自建插件「猫眼榜单」（MaoyanPro）：猫眼热度接口有美团 H5guard 反爬，
 * App 直连一律 403，只能复用服务端无头浏览器抓到的榜单快照。
 * 插件对外接口 `GET /plugin/MaoyanPro/rank?board_type=web-all|zongyi&platform=<platformType>`
 * 走宿主默认的 API 令牌鉴权（X-API-KEY），因此 App 的令牌模式即可读取，不再需要账号密码登录态。
 * 标题行由调用方渲染（单榜=榜单标题 / 双榜=胶囊页签），这里出平台切换行 + 列头 + 两列列表。
 */
@Composable
private fun MaoyanRankingBoard(
    api: MpApi,
    modifier: Modifier = Modifier,
    onPickTitle: (String) -> Unit,
    onUpdated: (String?) -> Unit = {},
) {
    var drama by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var variety by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var updatedAt by remember { mutableStateOf<String?>(null) }
    // 当前选中的平台段（0=全网 1=腾讯视频 2=爱奇艺 3=芒果TV 4=优酷），默认全网
    var platform by remember { mutableStateOf(0) }
    // media_id -> 首播日期（yyyy-MM-dd）；猫眼榜单接口不返回日期，异步按条补全
    var dates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    // 拿不到数据的原因提示（插件没装/未刷新/平台未开启），空串表示无错误
    var errMsg by remember { mutableStateOf("") }
    // 上次成功加载的平台段：只有真的「换了平台」才清空旧榜，
    // 切网重载（平台没变）保留已显示内容 —— 否则断网期间整页会被清成一条错误提示
    var loadedPlatform by remember { mutableStateOf(-1) }

    // key 用 api.netEpoch + platform：切换网络或切换平台后自动重新加载（见 MpApi.onNetworkChanged）。
    // 失败按 retryDelayMs 退避自愈：切网回调到达时新网络常还没真正通，只重载一次会留下一条
    // 「暂无数据/连接失败」再不自恢复（用户要切到别的榜再切回来）。
    LaunchedEffect(api.netEpoch, platform) {
        val platformType = MAOYAN_PLATFORM_TYPES[platform]
        var attempt = 0
        while (true) {
            if (attempt > 0) delay(retryDelayMs(attempt - 1))
            // 首次才显示「加载中」，静默重试不闪屏
            loading = attempt == 0
            errMsg = ""
            // 切换平台先清空旧榜：平台榜可能没数据，保留上一家的榜单会误导
            if (loadedPlatform != platform) {
                drama = emptyList()
                variety = emptyList()
            }
            // 本轮是不是「连不上服务器」（与「插件没数据」区分开，提示文案和重试节奏都不同）
            var offline = false
            try {
                coroutineScope {
                    // web-all = 剧集热度（电视剧 + 网络剧）；zongyi = 综艺热度；platform 空串 = 全网榜
                    val a = async { runCatching { api.maoyanRank("web-all", 10, platformType) } }
                    val b = async { runCatching { api.maoyanRank("zongyi", 10, platformType) } }
                    val c = async { runCatching { api.maoyanRankUpdatedAt() } }
                    val ar = a.await()
                    val br = b.await()
                    ar.getOrNull()?.takeIf { it.isNotEmpty() }?.let { drama = it }
                    br.getOrNull()?.takeIf { it.isNotEmpty() }?.let { variety = it }
                    updatedAt = c.await().getOrNull()
                    offline = ar.exceptionOrNull() != null || br.exceptionOrNull() != null
                }
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
                // 猫眼榜用的是插件快照时间，回传给父级显示在页签行 / 标题行
                onUpdated(updatedAt)
            }
            if (drama.isNotEmpty() || variety.isNotEmpty()) {
                loadedPlatform = platform
                break
            }
            errMsg = when {
                offline -> "连接失败，正在自动重试…"
                platformType.isEmpty() ->
                    "猫眼榜单暂无数据（需服务端启用「猫眼榜单」插件并刷新一次）"
                else ->
                    "该平台暂无榜单数据（需在服务端插件配置中开启该平台的推荐开关并刷新一次）"
            }
            attempt++
            // 不是网络问题（插件确实没数据）就别无限重试：试满 3 次就停在提示上
            if (!offline && attempt >= 3) break
        }
        // 榜单主体先显示，首播时间逐条补全（并发，取不到的条目自动跳过、不占位）
        val all = drama + variety
        val ids = all.mapNotNull { it.str("media_id")?.takeIf { v -> v.isNotBlank() } }.distinct()
        if (ids.isNotEmpty()) {
            coroutineScope {
                val jobs = ids.map { id ->
                    async {
                        val item = all.firstOrNull { it.str("media_id") == id } ?: return@async null
                        val d = runCatching {
                            api.mediaPremiereDate(
                                mediaSource = item.str("media_source") ?: "themoviedb",
                                mediaId = id,
                                title = item.str("title"),
                                year = item.str("year"),
                                // 猫眼条目的 type 是榜单类型（web-all/zongyi），宿主详情只认电影/电视剧/音乐/系列；
                                // 主路径 /media/seasons 不需要类型名，这里统一传「电视剧」用于兜底
                                typeName = "电视剧",
                                // 条目没有季号时取最大季 —— 正好对上「现在就出发 第4季」这类标题
                                season = item.long("season")?.toInt(),
                            )
                        }.getOrNull()
                        if (d.isNullOrBlank()) null else id to d
                    }
                }
                dates = jobs.mapNotNull { it.await() }.toMap()
            }
        }
    }

    Column(modifier) {
        // 平台切换行：全网（原有榜单）+ 四家平台细分榜
        MaoyanPlatformRow(
            selected = platform,
            onSelect = { platform = it },
        )
        Spacer(Modifier.height(10.dp))
        // 列头行：两列等宽，与下方列表严格对齐（榜单标题由调用方渲染）
        Row(Modifier.fillMaxWidth()) {
            Text(
                "剧集热度",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "综艺热度",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("榜单加载中…", fontSize = 12.sp, color = TextTertiary)
            }
            errMsg.isNotEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    errMsg,
                    fontSize = 12.sp, color = TextTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            else -> Row(Modifier.fillMaxSize()) {
                MaoyanColumn(
                    api = api,
                    items = drama,
                    dates = dates,
                    modifier = Modifier.weight(1f),
                    onPickTitle = onPickTitle,
                )
                Spacer(Modifier.width(12.dp))
                MaoyanColumn(
                    api = api,
                    items = variety,
                    dates = dates,
                    modifier = Modifier.weight(1f),
                    onPickTitle = onPickTitle,
                )
            }
        }
    }
}

/** 猫眼榜单单列：纵向条目（复用豆瓣榜同款小卡：第二行播放平台、第三行该条目首播时间）；列头由榜单统一渲染 */
@Composable
private fun MaoyanColumn(
    api: MpApi,
    items: List<JsonObject>,
    dates: Map<String, String>,
    modifier: Modifier = Modifier,
    onPickTitle: (String) -> Unit,
) {
    Column(modifier) {
        if (items.isEmpty()) {
            Text("暂无数据", fontSize = 11.sp, color = Color(0xFFA6AABE))
        } else {
            val listState = rememberLazyListState()
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // 同 RankingColumn：给底部滚动提示留出高度，避免压住末条卡片
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items) { index, item ->
                        // 播放平台走独立入参（第二行），首播日走 premiereDate（第三行）；平台缺失时用
                        // 「上线7天」这类上线信息顶上，日期行则回退年份 —— 不再把平台名塞进日期位。
                        val platform = item.str("platform")?.takeIf { it.isNotBlank() }
                        val relInfo = item.str("release_info")?.takeIf { it.isNotBlank() }
                        RankItemCard(
                            api = api,
                            // 名次用插件给的 rank，缺失时按顺序回退
                            rank = item.long("rank")?.toInt() ?: (index + 1),
                            item = item,
                            firstAirDate = null,
                            premiereDate = item.str("media_id")?.let { dates[it] },
                            platform = platform ?: relInfo,
                            onClick = {
                                val t = item.str("title")
                                if (!t.isNullOrBlank()) onPickTitle(t)
                            },
                        )
                    }
                }
                ScrollMoreHint(
                    visible = listState.canScrollForward,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

/**
 * 首页电影票房榜：左右两列（左 = 国内年度票房 / `board_type=year`；右 = 全球年度票房 / `global`）。
 *
 * 数据同样来自 MaoyanPro 插件快照（猫眼年度票房榜 + boxofficemojo 全球榜），接口条目自带
 * `box_office`（如「44.21亿」/「25.14亿美元」）与 `releaseInfo`（如「2026-02-17 上映」），
 * 无需逐条异步补日期。全球榜原名是英文，渲染与回搜都优先用插件识别出的中文名 `title_cn`。
 * 列头行由本组件渲染（与猫眼/豆瓣榜同款列头），页签行 / 标题行由调用方渲染。
 */
@Composable
private fun MovieRankingBoard(
    api: MpApi,
    modifier: Modifier = Modifier,
    onPickTitle: (String) -> Unit,
    onUpdated: (String?) -> Unit = {},
) {
    var domestic by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var global by remember { mutableStateOf<List<JsonObject>>(emptyList()) }
    var updatedAt by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    // 拿不到数据的原因提示（插件没装/未刷新），空串表示无错误
    var errMsg by remember { mutableStateOf("") }

    // key 用 api.netEpoch：切换网络后自动重新加载（见 MpApi.onNetworkChanged）。
    // 失败后按 retryDelayMs 退避自愈 —— 切网回调到达时新网络常还没真正通，只重载一次会留下错误。
    LaunchedEffect(api.netEpoch) {
        var attempt = 0
        while (true) {
            if (attempt > 0) delay(retryDelayMs(attempt - 1))
            // 首次才显示「加载中」，静默重试不闪屏
            loading = attempt == 0
            errMsg = ""
            // 本轮是不是「连不上服务器」：与「插件没数据」区分开提示
            var offline = false
            // 重新加载没拿到数据时保留上一次的榜单（同猫眼/豆瓣榜：网络抖动不清空已显示内容）
            try {
                coroutineScope {
                    val a = async { runCatching { api.maoyanRank("year", 10) } }
                    val b = async { runCatching { api.maoyanRank("global", 10) } }
                    val c = async { runCatching { api.maoyanRankUpdatedAt() } }
                    val ar = a.await()
                    val br = b.await()
                    ar.getOrNull()?.takeIf { it.isNotEmpty() }?.let { domestic = it }
                    br.getOrNull()?.takeIf { it.isNotEmpty() }?.let { global = it }
                    updatedAt = c.await().getOrNull()
                    offline = ar.exceptionOrNull() != null || br.exceptionOrNull() != null
                }
            } catch (_: Exception) {
                offline = true
            } finally {
                loading = false
                onUpdated(updatedAt)
            }
            if (domestic.isNotEmpty() || global.isNotEmpty()) break
            errMsg = if (offline) {
                "连接失败，正在自动重试…"
            } else {
                "电影票房榜暂无数据（需服务端启用「猫眼榜单」插件并刷新一次）"
            }
            attempt++
            // 不是网络问题（插件确实没数据）就别无限重试：试满 3 次就停在提示上
            if (!offline && attempt >= 3) break
        }
    }

    Column(modifier) {
        // 列头行：两列等宽，与下方列表严格对齐
        Row(Modifier.fillMaxWidth()) {
            Text(
                "国内年度票房",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "全球年度票房",
                fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(6.dp))
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("榜单加载中…", fontSize = 12.sp, color = TextTertiary)
            }
            errMsg.isNotEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    errMsg,
                    fontSize = 12.sp, color = TextTertiary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
            else -> Row(Modifier.fillMaxSize()) {
                MovieColumn(
                    api = api,
                    items = domestic,
                    modifier = Modifier.weight(1f),
                    onPickTitle = onPickTitle,
                )
                Spacer(Modifier.width(12.dp))
                MovieColumn(
                    api = api,
                    items = global,
                    modifier = Modifier.weight(1f),
                    onPickTitle = onPickTitle,
                )
            }
        }
    }
}

/**
 * 电影票房榜单列：纵向条目，复用榜单同款小卡 —— 第二行票房（`box_office`，如「44.21亿」），
 * 第三行上映信息（`releaseInfo`，日期压成紧凑写法）；全球榜条目优先用识别出的中文名显示与回搜。
 */
@Composable
private fun MovieColumn(
    api: MpApi,
    items: List<JsonObject>,
    modifier: Modifier = Modifier,
    onPickTitle: (String) -> Unit,
) {
    Column(modifier) {
        if (items.isEmpty()) {
            Text("暂无数据", fontSize = 11.sp, color = Color(0xFFA6AABE))
        } else {
            val listState = rememberLazyListState()
            Box(Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    // 同 MaoyanColumn：给底部滚动提示留出高度，避免压住末条卡片
                    contentPadding = PaddingValues(bottom = 28.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(items) { index, item ->
                        // 上映信息压成「2026.2.17 上映」这类紧凑写法（日期前缀套 formatAirDate）；
                        // 全球榜只有年份（「2026」）时原样显示
                        val relInfo = item.str("release_info")?.takeIf { it.isNotBlank() }
                        val airDate = relInfo?.let { rel ->
                            val m = Regex("^(\\d{4}-\\d{1,2}-\\d{1,2})").find(rel)
                            if (m != null) formatAirDate(m.groupValues[1]) + rel.substring(m.groupValues[1].length) else rel
                        }
                        // 全球榜原名是英文：显示与回搜都优先用插件识别出的中文名
                        val titleCn = item.str("title_cn")?.takeIf { it.isNotBlank() }
                        RankItemCard(
                            api = api,
                            rank = item.long("rank")?.toInt() ?: (index + 1),
                            item = item,
                            firstAirDate = airDate,
                            // 票房走平台位（第二行）：复用平台行的加重灰字样式，语义都是「这条的关键属性」
                            platform = item.str("box_office")?.takeIf { it.isNotBlank() },
                            displayTitle = titleCn,
                            onClick = {
                                val t = titleCn ?: item.str("title")
                                if (!t.isNullOrBlank()) onPickTitle(t)
                            },
                        )
                    }
                }
                ScrollMoreHint(
                    visible = listState.canScrollForward,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
