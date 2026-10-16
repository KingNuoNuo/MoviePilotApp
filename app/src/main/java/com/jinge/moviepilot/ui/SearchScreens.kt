package com.jinge.moviepilot.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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
    onBack: () -> Unit,
    key: Int,
) {
    var season by remember(selectedMedia) { mutableStateOf<Int?>(null) }
    var episode by remember(selectedMedia) { mutableStateOf<Int?>(null) }
    var showSeasonDialog by remember(selectedMedia) {
        mutableStateOf(selectedMedia?.isTv() == true)
    }

    if (selectedMedia != null) {
        if (showSeasonDialog) {
            SeasonEpisodeDialog(
                api = api,
                media = selectedMedia,
                onConfirm = { s, e ->
                    season = s
                    episode = e
                    showSeasonDialog = false
                },
                onCancel = onBack,
            )
        } else {
            TorrentListScreen(api, media = selectedMedia, keyword = null, season = season, episode = episode, onSelectTab = onSelectTab, onBack = onBack, key = key)
        }
    } else if (directKeyword != null) {
        TorrentListScreen(api, media = null, keyword = directKeyword, season = null, episode = null, onSelectTab = onSelectTab, onBack = onBack, key = key)
    } else {
        MediaSearchScreen(api, onSelectMedia, onDirectSearch, key, showRanking, showMaoyanRanking)
    }
}

/**
 * 选季/选集弹窗（底部弹出的白色圆角卡片风）：
 * 上半部选季（chip，带集数徽标），下半部选集（数字网格，可选「整季」）。
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SeasonEpisodeDialog(
    api: MpApi,
    media: JsonObject,
    onConfirm: (Int?, Int?) -> Unit,
    onCancel: () -> Unit,
) {
    // 媒体搜索结果不带 seasons 字段，必须从服务器拉取
    var seasons by remember { mutableStateOf(mediaSeasons(media)) }
    var seasonsLoading by remember { mutableStateOf(false) }
    var seasonsFailed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    var seasonEpisodes by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    var loading by remember { mutableStateOf(false) }
    var selectedSeason by remember { mutableStateOf(seasons.firstOrNull()) }
    var selectedEpisode by remember { mutableStateOf<Int?>(null) }
    var manualSeason by remember { mutableStateOf("1") }

    // 拉取季列表（服务器实时调 TMDB，偶发失败返回空 → 自动重试一次，仍失败给手动重试按钮）
    suspend fun fetchSeasons() {
        seasonsLoading = true
        seasonsFailed = false
        try {
            var eps = api.mediaSeasonEpisodes(media)
            if (eps.isEmpty()) {
                delay(1500)
                eps = api.mediaSeasonEpisodes(media)   // 重试一次（首次实时调 TMDB 可能抖动）
            }
            if (eps.isNotEmpty()) {
                seasonEpisodes = eps
                seasons = eps.keys.sorted()
                selectedSeason = selectedSeason ?: seasons.firstOrNull()
            } else {
                seasonsFailed = true
            }
        } catch (_: Exception) {
            seasonsFailed = true
        } finally {
            seasonsLoading = false
        }
    }

    LaunchedEffect(retryTick) {
        if (seasons.isEmpty()) fetchSeasons()
    }

    // 选中季后查该季集数（拿不到就只按整季搜）
    LaunchedEffect(selectedSeason) {
        selectedEpisode = null
        val s = selectedSeason ?: return@LaunchedEffect
        if (seasonEpisodes.containsKey(s)) return@LaunchedEffect
        loading = true
        val eps = runCatching { api.mediaSeasonEpisodes(media) }.getOrDefault(emptyMap())
        if (eps.isNotEmpty()) seasonEpisodes = seasonEpisodes + eps
        loading = false
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
                    if (selectedSeason == null) "搜索全部季的资源"
                    else "第 $selectedSeason 季" + (selectedEpisode?.let { " · 第 $it 集" } ?: " · 整季"),
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column {
                // ---- 选季 ----
                Text("选择季", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
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
                            onClick = { selectedSeason = null }
                        )
                        seasons.forEach { s ->
                            SelectChip(
                                text = if (s == 0) "特别篇" else "第 $s 季",
                                badge = seasonEpisodes[s]?.let { "${it}集" },
                                selected = selectedSeason == s,
                                onClick = { selectedSeason = s }
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
                val epCount = selectedSeason?.let { seasonEpisodes[it] }
                if (selectedSeason != null && (loading || epCount != null)) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "选择集" + if (loading) "（查询中…）" else "",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary
                    )
                    Spacer(Modifier.height(8.dp))
                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            trackColor = DividerColor,
                        )
                    } else if (epCount != null) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(52.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                        ) {
                            item {
                                SelectChip(
                                    text = "整季",
                                    selected = selectedEpisode == null,
                                    onClick = { selectedEpisode = null }
                                )
                            }
                            items((1..epCount).toList()) { e ->
                                SelectChip(
                                    text = "E$e",
                                    selected = selectedEpisode == e,
                                    onClick = { selectedEpisode = e }
                                )
                            }
                        }
                    } else {
                        Text("暂无集数信息，按整季搜索", fontSize = 12.sp, color = TextTertiary)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val s = if (seasons.isNotEmpty()) selectedSeason else manualSeason.toIntOrNull()
                    onConfirm(s, selectedEpisode)
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
) {
    var query by remember(key) { mutableStateOf("") }
    var results by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var searchedText by remember(key) { mutableStateOf("") }
    var searchJob by remember(key) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // 两个榜单都开启时的胶囊页签：0 = 猫眼榜（居左，默认选中），1 = 豆瓣榜
    var rankingTab by remember(key) { mutableStateOf(0) }
    // 两个榜各自的「更新 10-15 19:07」，由榜单加载完成后回传，供页签行 / 标题行显示
    var maoyanUpdated by remember(key) { mutableStateOf<String?>(null) }
    var doubanUpdated by remember(key) { mutableStateOf<String?>(null) }
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

    val searchBar: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = modifier,
            placeholder = { Text("搜索电影、剧集...", color = Color(0xFFA6AABE)) },
            leadingIcon = {
                // 首页：放大镜；结果态：返回箭头（点它清空搜索回首页）
                if (isHome) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFFA6AABE))
                } else {
                    IconButton(onClick = { backToHome() }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回首页",
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                unfocusedTextColor = TextPrimary,
                focusedTextColor = TextPrimary,
                cursorColor = primary,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = primary,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onSearch = { if (query.isNotBlank()) doMediaSearch(query.trim()) }
            ),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Search
            )
        )
    }

    // 搜索结果态：物理返回键先回首页，而不是直接退出 app
    if (!isHome) {
        androidx.activity.compose.BackHandler { backToHome() }
    }

    Column(Modifier.fillMaxSize()) {
        if (isHome) {
            // ---- 首页：搜索框 hero 布局；开启榜单时下方接榜单区 ----
            // 任一榜单开启（豆瓣或猫眼）就不再显示居中的 hero 徽标区，整屏高度让给榜单
            val anyRanking = showRanking || showMaoyanRanking
            // 两个榜都开 → 胶囊页签切换，一次只显示一个，榜单区拿满剩余高度
            val bothRanking = showRanking && showMaoyanRanking
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
                // 搜索框 + 右侧「直接搜种子资源」直达按钮
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    searchBar(
                        Modifier
                            .weight(1f)
                            .shadow(8.dp, RoundedCornerShape(28.dp), spotColor = Color(0x265A63D8))
                    )
                    Spacer(Modifier.width(10.dp))
                    Box(
                        Modifier
                            .size(52.dp)
                            .shadow(8.dp, RoundedCornerShape(16.dp), spotColor = Color(0x265A63D8))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .clickable(enabled = query.isNotBlank()) {
                                if (query.isNotBlank()) onDirectSearch(query.trim())
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.FileDownload,
                            contentDescription = "直接搜种子资源",
                            tint = if (query.isNotBlank()) primary else Color(0xFFC3C6D4),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (anyRanking) {
                    Spacer(Modifier.height(16.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 16.dp)
                    ) {
                        if (bothRanking) {
                            // 页签行：猫眼榜居左（默认）、豆瓣榜居右，右侧显示当前榜的更新时间
                            RankingTabRow(
                                selected = rankingTab,
                                onSelect = { rankingTab = it },
                                updatedAt = if (rankingTab == 0) maoyanUpdated else doubanUpdated,
                            )
                        } else {
                            // 只开一个榜：没有页签，改用榜单自己的标题行（同样带更新时间）
                            RankingBoardTitle(
                                title = if (showMaoyanRanking) "猫眼榜单" else "豆瓣电视榜单",
                                hint = if (showMaoyanRanking) "剧集热度 · 综艺热度" else "国内 · 国外 每周更新",
                                updatedAt = if (showMaoyanRanking) maoyanUpdated else doubanUpdated,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        // 两个榜都常驻组合（数据各拉一次），只把选中那个撑开、另一个压成 0 高：
                        // 切页签不重新请求接口，也不会闪加载动画。
                        if (showMaoyanRanking) {
                            MaoyanRankingBoard(
                                api = api,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!bothRanking || rankingTab == 0) Modifier.weight(1f)
                                        else Modifier.height(0.dp)
                                    ),
                                onPickTitle = { doMediaSearch(it) },
                                onUpdated = { maoyanUpdated = it },
                            )
                        }
                        if (showRanking) {
                            TvRankingBoard(
                                api = api,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (!bothRanking || rankingTab == 1) Modifier.weight(1f)
                                        else Modifier.height(0.dp)
                                    ),
                                onPickTitle = { doMediaSearch(it) },
                                onUpdated = { doubanUpdated = it },
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
                        MediaCard(api, media, tileCache) { onSelectMedia(media) }
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
    LaunchedEffect(cacheKey) {
        if (cached == null && !mid.isNullOrBlank()) {
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
            val premiere = cached?.seasonDate ?: fallbackPremiere
            val metaLine = listOfNotNull(
                media.str("type")?.takeIf { it.isNotBlank() },
                premiere?.let { "首播 $it" },
            ).joinToString(" · ")
            val actors = cached?.actors?.takeIf { it.isNotEmpty() }
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

/**
 * 双榜并存时的胶囊页签行：猫眼榜居左（默认选中）、豆瓣榜居右，右侧显示当前选中榜的更新时间。
 * 只渲染页签本身，选中项决定下方哪个榜被撑开。
 */
@Composable
private fun RankingTabRow(
    selected: Int,
    onSelect: (Int) -> Unit,
    updatedAt: String?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PillTab("猫眼榜", selected == 0) { onSelect(0) }
        Spacer(Modifier.width(8.dp))
        PillTab("豆瓣榜", selected == 1) { onSelect(1) }
        Spacer(Modifier.weight(1f))
        shortStamp(updatedAt)?.let {
            Text("更新 $it", fontSize = 10.sp, color = Color(0xFFA6AABE))
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

    // key 用 api.netEpoch：切换网络后自动重新加载（见 MpApi.onNetworkChanged）
    LaunchedEffect(api.netEpoch) {
        loading = true; failed = false
        try {
            coroutineScope {
                val c = async { runCatching { api.doubanTvWeeklyChinese(count = 10) }.getOrDefault(emptyList()) }
                val g = async { runCatching { api.doubanTvWeeklyGlobal(count = 10) }.getOrDefault(emptyList()) }
                // 重新加载（切网途中会短暂连不上）没拿到数据时保留上一次的榜单，
                // 不要把首页闪成「暂无数据」；首次加载本来就空，行为和以前一致
                c.await().takeIf { it.isNotEmpty() }?.let { chinese = it }
                g.await().takeIf { it.isNotEmpty() }?.let { global = it }
            }
            failed = chinese.isEmpty() && global.isEmpty()
        } catch (_: Exception) {
            failed = true
        } finally {
            loading = false
            // 豆瓣榜是实时拉的，没有服务端时间戳，「更新」就用本次拉取完成的时间
            onUpdated(nowStamp())
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
                    Text("榜单暂不可用（服务端豆瓣接口异常）", fontSize = 12.sp, color = TextTertiary)
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
    onClick: () -> Unit,
) {
    // 显示用标题做「季号压成 S+数字」处理并摘出季号；点击回搜仍用原始 title（见 [rankTitleParts] 说明）
    val (title, season) = rankTitleParts(item.str("title") ?: "未知")
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
 * 首页猫眼榜单：左右两列（左 = 剧集热度：电视剧 + 网络剧；右 = 综艺热度）。
 *
 * 数据来源是服务端自建插件「猫眼榜单」（MaoyanPro）：猫眼热度接口有美团 H5guard 反爬，
 * App 直连一律 403，只能复用服务端无头浏览器抓到的榜单快照。
 * 插件对外接口 `GET /plugin/MaoyanPro/rank?board_type=web-all|zongyi` 走宿主默认的
 * API 令牌鉴权（X-API-KEY），因此 App 的令牌模式即可读取，不再需要账号密码登录态。
 * 标题行由调用方渲染（单榜=榜单标题 / 双榜=胶囊页签），这里只出列头 + 两列列表。
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
    // media_id -> 首播日期（yyyy-MM-dd）；猫眼榜单接口不返回日期，异步按条补全
    var dates by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    // 拿不到数据的原因提示（插件没装/未刷新），空串表示无错误
    var errMsg by remember { mutableStateOf("") }

    // key 用 api.netEpoch：切换网络后自动重新加载（见 MpApi.onNetworkChanged）
    LaunchedEffect(api.netEpoch) {
        loading = true; errMsg = ""
        try {
            coroutineScope {
                // web-all = 猫眼的「电视剧 + 网络剧」综合热度榜；zongyi = 综艺热度单榜
                val a = async { runCatching { api.maoyanRank("web-all", 10) }.getOrDefault(emptyList()) }
                val b = async { runCatching { api.maoyanRank("zongyi", 10) }.getOrDefault(emptyList()) }
                val c = async { runCatching { api.maoyanRankUpdatedAt() }.getOrNull() }
                // 重新加载没拿到数据时保留上一次的榜单（同豆瓣榜，见 TvRankingBoard）
                a.await().takeIf { it.isNotEmpty() }?.let { drama = it }
                b.await().takeIf { it.isNotEmpty() }?.let { variety = it }
                updatedAt = c.await()
            }
            if (drama.isEmpty() && variety.isEmpty()) {
                errMsg = "猫眼榜单暂无数据（需服务端启用「猫眼榜单」插件并刷新一次）"
            }
        } catch (e: Exception) {
            errMsg = e.message ?: "猫眼榜单加载失败"
        } finally {
            loading = false
            // 猫眼榜用的是插件快照时间，回传给父级显示在页签行 / 标题行
            onUpdated(updatedAt)
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
