package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.long
import com.jinge.moviepilot.api.str
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * 订阅页五个页签的顺序（电影 / 电视 / 音乐 / 工作流 / 日历）。
 *
 * [label] 是**页签文案**，[type] 是**宿主订阅数据里的 type 字段**，两者不能混用：
 * 页签要短（写「电视」），而宿主返回的是「电视剧」，拿文案当过滤条件会一条都筛不出来。
 */
private enum class SubscribeTab(val label: String, val type: String? = null) {
    Movie("电影", "电影"), Tv("电视", "电视剧"), Music("音乐", "音乐"),
    Workflow("工作流"), Calendar("日历")
}

/** 我的订阅：电影/电视剧/音乐（订阅查看+删除）、工作流（执行/启停）、日历（订阅未来一周播出） */
@Composable
fun SubscribesScreen(api: MpApi, key: Int) {
    var tab by remember(key) { mutableStateOf(SubscribeTab.Movie) }

    // 各页签数据各自加载，切页签不重新请求（remember 缓存在组合内）
    var subs by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var subsLoading by remember(key) { mutableStateOf(true) }
    var subsError by remember(key) { mutableStateOf<String?>(null) }
    var workflows by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var wfLoading by remember(key) { mutableStateOf(true) }
    var wfError by remember(key) { mutableStateOf<String?>(null) }
    // 日历 = 订阅未来一周的播出计划，按日期分组（每组 {date, label, items[]}）
    var calendar by remember(key) { mutableStateOf<List<CalendarGroup>>(emptyList()) }
    var calLoading by remember(key) { mutableStateOf(true) }
    var calError by remember(key) { mutableStateOf<String?>(null) }
    var message by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // key 里带上 api.netEpoch：切换网络后自动重新加载（见 MpApi.onNetworkChanged）
    LaunchedEffect(key, api.netEpoch) {
        // 重载前先清掉上一次的错误，否则重新加载成功后错误文案还挂在界面上
        subsError = null; wfError = null; calError = null
        // 订阅（电影/电视剧/音乐共用一份）
        try { subs = api.listSubscribes() } catch (e: Exception) { subsError = e.message } finally { subsLoading = false }
        // 工作流
        try { workflows = api.listWorkflows() } catch (e: Exception) { wfError = e.message } finally { wfLoading = false }
        // 日历：遍历电影/电视剧订阅，算未来 7 天播出（音乐不参与）
        try {
            calendar = buildSubscribeCalendar(api, subs)
        } catch (e: Exception) { calError = e.message } finally { calLoading = false }
    }

    Column(Modifier.fillMaxSize()) {
        message?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp
            )
        }

        // 页签行：5 个胶囊压缩内边距 + 间距，保证 360dp 屏一屏放下（合计 320dp）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SubscribeTab.entries.forEach { t ->
                PillTab(t.label, tab == t, horizontalPadding = 12.dp) { tab = t }
            }
        }

        when (tab) {
            SubscribeTab.Movie, SubscribeTab.Tv -> SubList(
                loading = subsLoading,
                error = subsError,
                emptyHint = "还没有${tab.label}订阅，去搜索页添加吧",
                // 按宿主 type 过滤（不是页签文案：页签写「电视」，宿主字段是「电视剧」）
                items = subs.filter { subType(it) == tab.type },
                content = { list ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list) { sub ->
                            var confirmDelete by remember { mutableStateOf(false) }
                            var deleting by remember { mutableStateOf(false) }
                            SubscribeCard(api, sub, onDelete = { confirmDelete = true })
                            if (confirmDelete) {
                                ConfirmDeleteDialog(
                                    title = "删除订阅",
                                    target = sub.str("name") ?: "该订阅",
                                    message = "删除后服务端将不再自动搜索和下载该片。",
                                    busy = deleting,
                                    onDismiss = { if (!deleting) confirmDelete = false },
                                    onConfirm = {
                                        deleting = true
                                        scope.launch {
                                            message = null
                                            try {
                                                api.deleteSubscribe(sub.long("id") ?: 0L)
                                                subs = subs.filter { it.long("id") != sub.long("id") }
                                                message = "已删除订阅"
                                            } catch (e: Exception) {
                                                message = "删除失败：${e.message}"
                                            } finally {
                                                deleting = false
                                                confirmDelete = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )

            SubscribeTab.Music -> SubList(
                loading = subsLoading,
                error = subsError,
                emptyHint = "还没有音乐订阅，去搜索页添加吧",
                items = subs.filter { it.str("type") == "音乐" },
                content = { list ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list) { sub ->
                            var confirmDelete by remember { mutableStateOf(false) }
                            var deleting by remember { mutableStateOf(false) }
                            MusicSubscribeCard(api, sub, onDelete = { confirmDelete = true })
                            if (confirmDelete) {
                                ConfirmDeleteDialog(
                                    title = "删除订阅",
                                    target = sub.str("name") ?: "该订阅",
                                    message = "删除后服务端将不再自动搜索和下载该音乐。",
                                    busy = deleting,
                                    onDismiss = { if (!deleting) confirmDelete = false },
                                    onConfirm = {
                                        deleting = true
                                        scope.launch {
                                            message = null
                                            try {
                                                api.deleteSubscribe(sub.long("id") ?: 0L)
                                                subs = subs.filter { it.long("id") != sub.long("id") }
                                                message = "已删除订阅"
                                            } catch (e: Exception) {
                                                message = "删除失败：${e.message}"
                                            } finally {
                                                deleting = false
                                                confirmDelete = false
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            )

            SubscribeTab.Workflow -> SubList(
                loading = wfLoading,
                error = wfError,
                emptyHint = "暂无工作流",
                items = workflows,
                content = { list ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(list) { w ->
                            var busy by remember(w) { mutableStateOf(false) }
                            WorkflowCard(
                                wf = w,
                                busy = busy,
                                onRun = {
                                    busy = true
                                    scope.launch {
                                        message = null
                                        try {
                                            api.runWorkflow(w.long("id") ?: 0L)
                                            message = "已执行"
                                        } catch (e: Exception) {
                                            message = "执行失败：${e.message}"
                                        } finally { busy = false }
                                    }
                                },
                                onToggle = { enabled ->
                                    busy = true
                                    scope.launch {
                                        message = null
                                        try {
                                            if (enabled) api.startWorkflow(w.long("id") ?: 0L)
                                            else api.pauseWorkflow(w.long("id") ?: 0L)
                                            workflows = workflows.map { x ->
                                                if (x.long("id") == w.long("id")) rebuild(x, "state", if (enabled) "A" else "P") else x
                                            }
                                            message = if (enabled) "已启用" else "已停用"
                                        } catch (e: Exception) {
                                            message = "操作失败：${e.message}"
                                        } finally { busy = false }
                                    }
                                }
                            )
                        }
                    }
                }
            )

            SubscribeTab.Calendar -> SubList(
                loading = calLoading,
                error = calError,
                emptyHint = "未来一周暂无订阅更新",
                items = calendar.map { it.toJson() },
                content = { list ->
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(calendar) { group -> CalendarDayCard(api, group) }
                    }
                }
            )
        }
    }
}

/** 判断订阅类型：type 字段为「电视剧」时归电视剧，否则归电影 */
private fun subType(sub: JsonObject): String =
    if (sub.str("type") == "电视剧") "电视剧" else "电影"

/** 把 JsonObject 里某个字段替换为新值（工作流状态 A/P 本地回写，避免再拉一次列表） */
private fun rebuild(src: JsonObject, key: String, value: String): JsonObject =
    buildJsonObject {
        src.forEach { (k, v) -> put(k, if (k == key) JsonPrimitive(value) else v) }
    }

/** 通用列表骨架：loading / error / empty / 内容，四态统一 */
@Composable
private fun SubList(
    loading: Boolean,
    error: String?,
    emptyHint: String,
    items: List<JsonObject>,
    content: @Composable (List<JsonObject>) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(error!!, color = TextTertiary)
        }
        items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyHint, color = TextTertiary)
        }
        else -> content(items)
    }
}

@Composable
private fun SubscribeCard(api: MpApi, sub: JsonObject, onDelete: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MpPosterImage(
                api = api,
                raw = sub.str("poster") ?: sub.str("poster_path") ?: sub.str("backdrop"),
                contentDescription = null,
                modifier = Modifier
                    .width(44.dp)
                    .aspectRatio(0.7f)
                    .background(Color(0xFFE3E5F0), RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    listOfNotNull(
                        sub.str("name"),
                        sub.str("season")?.let { s -> if (s != "0" && s != "null") "第${s}季" else null }
                    ).joinToString(" "),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    listOfNotNull(sub.str("year"), sub.str("type")).joinToString(" · "),
                    fontSize = 12.sp, color = TextTertiary
                )
            }
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 13.sp, color = DangerRed)
            }
        }
    }
}

@Composable
private fun MusicSubscribeCard(api: MpApi, sub: JsonObject, onDelete: () -> Unit) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            MpPosterImage(
                api = api,
                raw = sub.str("poster") ?: sub.str("poster_path") ?: sub.str("backdrop"),
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .background(FieldFill, RoundedCornerShape(6.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    sub.str("name") ?: "未知音乐",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                val sub2 = listOfNotNull(
                    sub.str("music_type")?.let {
                        when (it) { "album" -> "专辑"; "recording" -> "单曲"; else -> it }
                    },
                    sub.str("type"),
                ).joinToString(" · ")
                Text(sub2, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDelete) {
                Text("删除", fontSize = 13.sp, color = DangerRed)
            }
        }
    }
}

@Composable
private fun WorkflowCard(wf: JsonObject, busy: Boolean, onRun: () -> Unit, onToggle: (Boolean) -> Unit) {
    val enabled = wf.str("state") == "A"
    val trigger = wf.str("trigger_type")
    val triggerLabel = when (trigger) {
        "event" -> "事件触发${wf.str("event_type")?.let { " · $it" } ?: ""}"
        "timer" -> "定时触发"
        "manual" -> "手动触发"
        else -> trigger ?: ""
    }
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    wf.str("name") ?: "未命名工作流",
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                InboundTag(
                    if (enabled) "已启用" else "已停用",
                    if (enabled) TagDoneBg else TagWaitBg,
                    if (enabled) SuccessGreen else TextTertiary
                )
            }
            wf.str("description")?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, fontSize = 12.sp, color = TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (triggerLabel.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(triggerLabel, fontSize = 11.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (!busy) {
                    TextButton(onClick = onRun) { Text("执行", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary) }
                    TextButton(onClick = { onToggle(!enabled) }) {
                        Text(if (enabled) "停用" else "启用", fontSize = 13.sp, color = TextSecondary)
                    }
                } else {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

/** 订阅日历里某天的单条播出：title=剧名/影名、subtitle=集数说明、poster=海报 */
private data class CalendarItem(
    val title: String,
    val subtitle: String,
    val poster: String?,
)

/** 订阅日历里某一天：date=日期（yyyy-MM-dd）、label=今天/明天/后天/周X、items=当天所有播出条目 */
private data class CalendarGroup(
    val date: String,
    val label: String,
    val items: List<CalendarItem>,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("date", date); put("label", label)
        put("count", items.size)
    }
}

/** 计算订阅未来 7 天的播出计划（对齐 web 前端 FullCalendarView，但只取未来一周），按日期合并同天条目 */
private suspend fun buildSubscribeCalendar(api: MpApi, subs: List<JsonObject>): List<CalendarGroup> {
    val today = java.time.LocalDate.now()
    val end = today.plusDays(6) // 今天起 7 天
    val byDate = linkedMapOf<String, MutableList<CalendarItem>>()

    for (sub in subs) {
        val type = sub.str("type") ?: continue
        val mediaId = sub.str("media_id") ?: continue
        val name = sub.str("name") ?: continue
        val poster = sub.str("poster") ?: sub.str("poster_path") ?: sub.str("backdrop")
        when (type) {
            "电影" -> {
                val detail = runCatching { api.mediaDetail(mediaId, type) }.getOrNull() ?: continue
                val date = detail.str("release_date") ?: continue
                val d = parseIsoDate(date) ?: continue
                if (d in today..end) {
                    byDate.getOrPut(date) { mutableListOf() }
                        .add(CalendarItem(name, "上映", poster))
                }
            }
            "电视剧" -> {
                val tmdbId = mediaId.toLongOrNull() ?: continue
                val season = sub.str("season")?.toIntOrNull() ?: 1
                val eps = runCatching { api.tmdbSeasonEpisodes(tmdbId, season) }.getOrNull() ?: continue
                // 未来 7 天内播出的集，按日期合并集数
                val epsByDate = linkedMapOf<String, MutableList<String>>()
                for (ep in eps) {
                    val air = ep.str("air_date") ?: continue
                    val d = parseIsoDate(air) ?: continue
                    if (d in today..end) {
                        epsByDate.getOrPut(air) { mutableListOf() }
                            .add(ep.str("episode_number") ?: "")
                    }
                }
                for ((date, nums) in epsByDate) {
                    val numsStr = formatEpisodeNumbers(nums.mapNotNull { it.toIntOrNull() })
                    val subtitle = if (numsStr.isNotBlank()) "$numsStr 集" else ""
                    byDate.getOrPut(date) { mutableListOf() }
                        .add(CalendarItem(name, subtitle, poster))
                }
            }
        }
    }

    return byDate.entries.sortedBy { it.key }.map { (date, items) ->
        CalendarGroup(date, dateLabel(parseIsoDate(date)!!), items)
    }
}

/** ISO 日期字符串（yyyy-MM-dd）转 LocalDate；解析失败返回 null */
private fun parseIsoDate(s: String): java.time.LocalDate? =
    runCatching { java.time.LocalDate.parse(s.take(10)) }.getOrNull()

/**
 * 集号压缩显示：连续集用 `-` 连接、断档用 `,` 分隔，节省横向空间。
 * 例：[15, 16] → "15-16"；[15, 16, 18] → "15-16,18"；[196] → "196"。
 */
private fun formatEpisodeNumbers(nums: List<Int>): String {
    if (nums.isEmpty()) return ""
    val sorted = nums.distinct().sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var prev = start
    for (i in 1 until sorted.size) {
        val n = sorted[i]
        if (n == prev + 1) { prev = n; continue }
        parts += if (start == prev) "$start" else "$start-$prev"
        start = n; prev = n
    }
    parts += if (start == prev) "$start" else "$start-$prev"
    return parts.joinToString(",")
}

/** 日期标签：今天/明天/后天 或 星期几 */
private fun dateLabel(d: java.time.LocalDate): String {
    val today = java.time.LocalDate.now()
    return when (d) {
        today -> "今天"
        today.plusDays(1) -> "明天"
        today.plusDays(2) -> "后天"
        else -> "周" + "一二三四五六日"[d.dayOfWeek.value - 1]
    }
}

/** 订阅日历：一天一个卡片，当天所有播出条目横向排列，超出可滑动，两端有指示箭头 */
@Composable
private fun CalendarDayCard(api: MpApi, group: CalendarGroup) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(group.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(group.date, fontSize = 11.sp, color = TextTertiary)
                Spacer(Modifier.weight(1f))
                Text("共 ${group.items.size} 部", fontSize = 11.sp, color = TextTertiary)
            }
            Spacer(Modifier.height(10.dp))

            val listState = rememberLazyListState()
            val scope = rememberCoroutineScope()
            val step = with(LocalDensity.current) { 172.dp.toPx() } // 约两个卡片宽，滚一屏

            Box(Modifier.fillMaxWidth()) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(group.items) { item -> CalendarPosterItem(api, item) }
                }

                // 右侧：未滑到末尾时显示白色渐隐 + 右箭头，提示可继续右滑
                if (listState.canScrollForward) {
                    Box(
                        Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(56.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Transparent, Color.White)
                                )
                            )
                    )
                    ScrollArrow(Icons.Rounded.ChevronRight, Alignment.CenterEnd) {
                        scope.launch { listState.animateScrollBy(step) }
                    }
                }

                // 左侧：已滑动过时显示白色渐隐 + 左箭头，提示可回滑
                if (listState.canScrollBackward) {
                    Box(
                        Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxHeight()
                            .width(56.dp)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.White, Color.Transparent)
                                )
                            )
                    )
                    ScrollArrow(Icons.Rounded.ChevronLeft, Alignment.CenterStart) {
                        scope.launch { listState.animateScrollBy(-step) }
                    }
                }
            }
        }
    }
}

/** 横向滑动指示箭头：白色圆形 + 1dp 阴影 + 主色尖角，点按滚动一屏 */
@Composable
private fun BoxScope.ScrollArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    align: Alignment,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .align(align)
            .size(28.dp)
            .shadow(2.dp, CircleShape)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = "滑动", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

/** 日历里单个播出条目：竖版海报 + 剧名 + 集数说明（海报走 [MpPosterImage]，与下载/搜索共用缓存） */
@Composable
private fun CalendarPosterItem(api: MpApi, item: CalendarItem) {
    Column(Modifier.width(76.dp)) {
        MpPosterImage(
            api = api,
            raw = item.poster,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .background(FieldFill, RoundedCornerShape(6.dp))
        )
        Spacer(Modifier.height(4.dp))
        Text(
            item.title,
            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (item.subtitle.isNotBlank()) {
            Text(
                item.subtitle,
                fontSize = 11.sp, color = TextTertiary,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}
