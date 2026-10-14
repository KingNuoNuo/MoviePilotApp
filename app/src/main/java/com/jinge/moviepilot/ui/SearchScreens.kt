package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.isTv
import com.jinge.moviepilot.api.mediaSeasons
import com.jinge.moviepilot.api.posterUrlOf
import com.jinge.moviepilot.api.str
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
        MediaSearchScreen(api, onSelectMedia, onDirectSearch, key)
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
                    fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1C2B)
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
                Text("选择季", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A8FA3))
                Spacer(Modifier.height(8.dp))
                when {
                    seasonsLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("正在获取季信息…", fontSize = 13.sp, color = Color(0xFF8A8FA3))
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
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A8FA3)
                    )
                    Spacer(Modifier.height(8.dp))
                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(3.dp),
                            trackColor = Color(0xFFE3E5F0),
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
                        Text("暂无集数信息，按整季搜索", fontSize = 12.sp, color = Color(0xFF8A8FA3))
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
        color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFF3F4F8),
        shape = RoundedCornerShape(10.dp),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE3E5F0))
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
                color = if (selected) Color.White else Color(0xFF1A1C2B)
            )
            badge?.let {
                Spacer(Modifier.width(4.dp))
                Text(
                    it,
                    fontSize = 10.sp,
                    color = if (selected) Color(0xFFDDE0F7) else Color(0xFF8A8FA3)
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
) {
    var query by remember(key) { mutableStateOf("") }
    var results by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var loading by remember(key) { mutableStateOf(false) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var searchedText by remember(key) { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary

    fun doMediaSearch(text: String) {
        scope.launch {
            loading = true; error = null
            try {
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

    val searchBar: @Composable (Modifier) -> Unit = { modifier ->
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = modifier,
            placeholder = { Text("搜索电影、剧集...", color = Color(0xFFA6AABE)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color(0xFFA6AABE)) },
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
                unfocusedTextColor = Color(0xFF1A1C2B),
                focusedTextColor = Color(0xFF1A1C2B),
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

    val isHome = searchedText.isEmpty() && !loading && results.isEmpty() && error == null

    Column(Modifier.fillMaxSize()) {
        if (isHome) {
            // ---- 首页：搜索框居中的 hero 布局 ----
            Column(
                Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.85f))
                // Logo 徽标
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
                    color = Color(0xFF1A1C2B)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "发现电影 · 剧集 · 一键订阅下载",
                    fontSize = 13.sp,
                    color = Color(0xFF8A8FA3)
                )
                Spacer(Modifier.height(28.dp))
                searchBar(
                    Modifier
                        .fillMaxWidth(0.86f)
                        .shadow(8.dp, RoundedCornerShape(28.dp), spotColor = Color(0x265A63D8))
                )
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "输入片名搜索媒体",
                        fontSize = 12.sp,
                        color = Color(0xFFA6AABE)
                    )
                    Text(
                        "  ·  ",
                        fontSize = 12.sp,
                        color = Color(0xFFD5D7E4)
                    )
                    Text(
                        "直接搜种子资源",
                        fontSize = 12.sp,
                        color = Color(0xFFA6AABE),
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            if (query.isNotBlank()) onDirectSearch(query.trim())
                        }
                    )
                }
                Spacer(Modifier.weight(1.15f))
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
                        Text(error!!, color = Color(0xFF8A8FA3), fontSize = 14.sp)
                    }
                }
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(108.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize().weight(1f)
                ) {
                    items(results) { media ->
                        MediaCard(api, media) { onSelectMedia(media) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaCard(api: MpApi, media: JsonObject, onClick: () -> Unit) {
    Column(
        Modifier
            .shadow(4.dp, RoundedCornerShape(14.dp), spotColor = Color(0x1E1A1C2B))
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(6.dp)
    ) {
        MpPosterImage(
            api = api,
            raw = media.str("poster_path"),
            contentDescription = media.str("title"),
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFE3E5F0))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            media.str("title") ?: "未知",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = Color(0xFF1A1C2B)
        )
        Row(Modifier.padding(bottom = 2.dp)) {
            Text(
                listOfNotNull(media.str("year")?.takeIf { it.isNotBlank() }, media.str("type"))
                    .joinToString(" · "),
                fontSize = 11.sp,
                color = Color(0xFF8A8FA3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            media.str("vote_average")?.takeIf { it.isNotBlank() && it != "0" && it != "0.0" }?.let {
                Spacer(Modifier.width(4.dp))
                Text("★ $it", fontSize = 11.sp, color = Color(0xFFE8A33D))
            }
        }
    }
}
