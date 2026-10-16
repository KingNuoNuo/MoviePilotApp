package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.humanSize
import com.jinge.moviepilot.api.long
import com.jinge.moviepilot.api.str
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 下载页三个页签（正在下载 / 下载记录 / 整理记录），样式与订阅页统一用 [PillTab] */
private enum class DownloadTab(val label: String) {
    Downloading("正在下载"), History("下载记录"), Transfer("整理记录")
}

/**
 * 下载页：三个页签 ——
 * ① 正在下载（紧凑卡片：标题+进度+状态/速度/体积一行）；
 * ② 下载记录（最近三天下载，每条关联媒体库整理结果：正在下载=靛蓝 / 等待开始=灰 / 成功=绿 / 失败=红 / 等待整理=青蓝）；
 * ③ 整理记录（最近三天每一条整理回执，**按整理时间由近到远**）。
 * 5 秒自动刷新。
 */
@Composable
fun DownloadsScreen(api: MpApi, appScope: CoroutineScope, key: Int) {
    var tab by remember(key) { mutableStateOf(DownloadTab.Downloading) }
    // 首次拿到数据后是否已经选过起始页签（避免加载完成后又跳一次）
    var tabInit by remember(key) { mutableStateOf(false) }
    var downloading by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var history by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    // 下载哈希 → 整理记录（status: 成功/失败；没有记录 = 等待整理）
    var transfers by remember(key) { mutableStateOf<Map<String, JsonObject>>(emptyMap()) }
    // 全量整理记录（按整理记录粒度，未按哈希去重）：用于统计「最近三天每天整理失败的条数」
    var records by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    // 下载哈希 → 近三天该下载失败的整理记录条数（整季包一集一条），供失败卡片右侧显示「失败 N 次」
    var failCounts by remember(key) { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var msg by remember(key) { mutableStateOf<String?>(null) }
    // 操作结果提示的「保鲜期」：批量整理的结果（提交了几条、哪几条失败）刚出来时，
    // 先不让后台整理结束的逐条提示把它顶掉，否则用户看不到失败原因
    var msgHoldUntil by remember(key) { mutableStateOf(0L) }
    // 正在整理的下载哈希 → 提交时的记录签名：后台整理结束后签名会变，用它停掉卡片上的转圈
    var redoPending by remember(key) { mutableStateOf<Map<String, RedoPending>>(emptyMap()) }
    // 待人工复核（宿主整理队列里被停下等人确认的文件，没有整理历史记录 → 卡片上看不到）
    var reviews by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var showReviews by remember(key) { mutableStateOf(false) }
    var reviewsBusy by remember(key) { mutableStateOf(false) }
    var reviewSelected by remember(key) { mutableStateOf<Set<String>>(emptySet()) }
    // 「自动匹配目录」的预检结果，按分组（reviewGroups 的 key）各取一条代表 —— 不同剧可能落在
    // 不同的媒体库目录（电影/剧集分开），不能拿第一条的目录套给所有分组。key=分组目录路径。
    var reviewProbe by remember(key) { mutableStateOf<Map<String, JsonObject?>>(emptyMap()) }
    var reviewMatching by remember(key) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            val dl = api.listDownloading()
            // 候选池 = 最近三天的下载记录（宿主 /history/download 不能按日期过滤，内部翻页 + 本地截断）。
            // 为什么不能只按固定条数拉：失败整理记录的 date 是**整理时间**，其下载记录可能排在几十位
            // 之外（实测「征途」第 40 位），只拉 30 条会取不到卡片、看不到整理进度。
            val window = api.listDownloadHistorySince()
            val recs = if (window.isNotEmpty()) api.transferRecords() else emptyList()
            // 待人工复核：宿主不把它写进整理历史，只能单独拉（宿主没有该接口时静默忽略）
            val revs = runCatching { api.manualReviews() }.getOrDefault(emptyList())
            // 本地聚合：过滤/建映射/统计都是纯 CPU 计算（134+500 条过 5 遍），移出主线程，
            // 避免每 5 秒一次的刷新在滚动时掉帧。网络请求已在 MpApi 内部走 IO，这里只包计算段。
            val (transferMap, hist, failCountMap) = withContext(Dispatchers.Default) {
                // 卡片状态映射：同一批整理记录按 download_hash 只留最新一条（服务端按时间倒序返回）
                val map = mutableMapOf<String, JsonObject>()
                recs.forEach { rec ->
                    val hash = rec.str("download_hash")?.takeIf { it.isNotBlank() } ?: return@forEach
                    if (!map.containsKey(hash)) map[hash] = rec
                }
                // 列表显示 = 最近 HISTORY_KEEP 条 ∪「最近三天整理失败」的那几条（从候选池里补进来），
                // 仍按下载时间倒序；其余更早的条目不显示，避免列表被三天的记录整段撑长。
                val failed = failedHashSet(recs)
                val histFiltered = window.filterIndexed { i, item ->
                    i < HISTORY_KEEP || item.str("download_hash")?.lowercase() in failed
                }
                Triple(map, histFiltered, failedCountByHash(recs))
            }
            downloading = dl; history = hist; transfers = transferMap; records = recs
            failCounts = failCountMap; reviews = revs; error = null
            // 整理中的条目：服务端开始整理时会先把旧记录摘掉（transferMap 里暂时查不到），
            // 所以「记录消失」不代表结束；只有写出新记录（成功 / 再次失败）或超时才停转圈。
            if (redoPending.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val finished = mutableMapOf<String, JsonObject?>()
                redoPending.forEach { (hashKey, pending) ->
                    val rec = transferMap[hashKey]
                    val updated = rec != null && transferSignature(rec) != pending.signature
                    when {
                        updated -> finished[hashKey] = rec
                        now - pending.startedAt > REDO_TIMEOUT_MS -> finished[hashKey] = rec
                    }
                }
                if (finished.isNotEmpty()) {
                    redoPending = redoPending - finished.keys
                    // 批量提交结果还在保鲜期内时，不被逐条结束提示覆盖
                    if (System.currentTimeMillis() >= msgHoldUntil) {
                        val rec = finished[finished.keys.first()]
                        msg = when {
                            rec == null -> "整理已提交，后台仍在处理"
                            rec.str("status")?.toBooleanStrictOrNull() == true -> "整理完成，已入库"
                            else -> "整理失败：${rec.str("errmsg") ?: "请查看服务端日志"}"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    // 自适应轮询 + 前后台感知：有「正在下载的任务 / 整理中 / 待人工复核」任一活跃状态时 5 秒一轮，
    // 否则退到 30 秒一轮。配合 repeatOnLifecycle(STARTED)，切到后台（或切去别的 tab 不再可见）
    // 就停掉定时器，回来再恢复 —— 否则光这一页轮询实测要拉约 1.58MB/5s，后台空转白白吃流量。
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(key, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            refresh()
            while (true) {
                val busy = downloading.isNotEmpty() || redoPending.isNotEmpty() || reviews.isNotEmpty()
                delay(if (busy) FAST_POLL_MS else IDLE_POLL_MS)
                refresh()
            }
        }
    }

    // 起始页签：有下载任务就停在「正在下载」，一条都没有就直接看「下载记录」。
    // 加载中不渲染页签行（走上面的转圈分支），所以这次切换不会有可见跳变。
    LaunchedEffect(key, loading, downloading.isNotEmpty()) {
        if (!loading && !tabInit) {
            if (downloading.isEmpty()) tab = DownloadTab.History
            tabInit = true
        }
    }

    // 正在下载的任务哈希 → 任务（统一小写比较，规避下载器与服务端大小写差异）：
    // 最近记录里对应条目按进度显示「正在下载」/「等待开始下载」，而不是误显示「等待整理」
    val downloadingByHash = downloading.mapNotNull { t ->
        t.str("hash")?.lowercase()?.let { it to t }
    }.toMap()

    // 最近三天（含今天）整理失败的记录，按日期倒序。**按整理记录粒度**统计（一条记录 = 一集/一个媒体，
    // 整季包会按集各建一条），供「最近记录」标题行的「重新整理 N」和弹窗里的日期范围选择使用
    val failedDays = recentFailedDays(records)
    var selectedDays by remember(key) { mutableStateOf<Set<String>>(emptySet()) }
    // 数据首次到达 / 日期滚动时，默认只勾最近一天（与旧的「一键整理当日失败」一致，避免一进弹窗就误提交大批量）
    LaunchedEffect(failedDays.map { it.key }) {
        val keys = failedDays.map { it.key }.toSet()
        if (selectedDays.none { it in keys }) {
            failedDays.firstOrNull()?.let { selectedDays = setOf(it.key) }
        }
    }
    var redoingAll by remember(key) { mutableStateOf(false) }
    // 批量重新整理弹窗：打开时并发预检（限流 6）最近三天各条匹配到的目的目录，供「自动匹配路径」使用
    var showBatchRedo by remember(key) { mutableStateOf(false) }
    var batchMatching by remember(key) { mutableStateOf(false) }
    var probeById by remember(key) { mutableStateOf<Map<Long, JsonObject?>>(emptyMap()) }
    // 弹窗里勾选的记录（按整理记录 id 去重）
    val selectedIds = failedDays.filter { it.key in selectedDays }.flatMap { it.ids }.distinct()
    // 「自动匹配路径」的说明文案：按勾选记录里匹配到的目录分组统计
    val autoGroups = selectedIds
        .mapNotNull { id -> probeById[id]?.let { id to it } }
        .groupBy { (_, t) -> t.str("target_path") to t.str("target_storage") }
    val batchAutoLabel = when {
        batchMatching || autoGroups.isEmpty() -> null
        autoGroups.size == 1 -> autoGroups.values.first().first().second.let { t ->
            listOfNotNull(
                t.str("target_path")?.takeIf { it.isNotBlank() },
                t.str("target_storage")?.takeIf { it.isNotBlank() },
            ).joinToString(" · ") + "（${autoGroups.values.first().size} 条）"
        }
        else -> "匹配到 ${autoGroups.size} 个目录，将按目录分别提交"
    }
    // 待人工复核：按宿主计划的目标目录（中文剧名）分组，条数多的在前
    val reviewGroups = reviewGroupsOf(reviews)
    // 整理记录页签的列表：最近三天每一条整理回执，**按整理时间由近到远**。
    // 服务端本就按时间倒序返回，这里再兜底排一次（翻页拼接后若顺序有偏差也能保证口径）。
    val transferList = remember(records) { records.sortedByDescending { it.str("date") ?: "" } }
    // 复核入口的点击处理：打开弹窗并按分组预检目的目录（每组取第一条代表，限流 6）。
    // 不同剧可能落到不同媒体库目录（电影/剧集分开），所以按组各预检一次，提交时各用各的。
    val openReviews: () -> Unit = {
        showReviews = true
        // 默认全不勾：避免一进弹窗就误提交几十条
        reviewSelected = emptySet()
        reviewProbe = emptyMap()
        reviewMatching = true
        scope.launch {
            val probed = mutableMapOf<String, JsonObject?>()
            reviewGroups.chunked(6).forEach { chunk ->
                coroutineScope {
                    chunk.map { g ->
                        async {
                            // 每组取第一条代表预检；不同剧可能落到不同媒体库目录
                            val first = g.items.firstOrNull()
                            g.key to first?.let {
                                runCatching { api.matchTransferTargetPathForFile(it.storage, it.path) }
                                    .getOrNull()
                            }
                        }
                    }.awaitAll()
                }.forEach { (key, target) -> probed[key] = target }
            }
            reviewProbe = probed
            reviewMatching = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        msg?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp,
                // 服务端失败信息可能很长（含源文件路径等），最多显示 3 行避免顶掉列表
                maxLines = 3, overflow = TextOverflow.Ellipsis
            )
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && history.isEmpty() && downloading.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = TextTertiary, fontSize = 13.sp)
                }
            else -> Column(Modifier.fillMaxSize()) {
                // 页签行：与订阅页同款胶囊（12dp 内边距 + 6dp 间距）
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DownloadTab.entries.forEach { t ->
                        PillTab(t.label, tab == t, horizontalPadding = 12.dp) { tab = t }
                    }
                }

                when (tab) {
                    DownloadTab.Downloading -> LazyColumn(
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (downloading.isEmpty()) {
                            item { EmptyHint("当前没有正在下载的任务") }
                        } else {
                            items(downloading) { t ->
                                DownloadingCard(t, api) { result ->
                                    // result=null 成功（刷新列表）；非 null 为失败信息
                                    msg = result
                                    if (result == null) scope.launch { refresh() }
                                }
                            }
                        }
                    }

                    DownloadTab.History -> LazyColumn(
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (history.isEmpty()) {
                            item { EmptyHint("暂无下载记录") }
                        } else {
                            items(history) { h ->
                                // 该条记录的种子还在下载器里跑 → 按下载进度显示状态
                                val hash = h.str("download_hash")?.lowercase()
                                // 整理中的键用原始 download_hash（与 transfers 的键一致）
                                val hashKey = h.str("download_hash")
                                HistoryCard(
                                    h,
                                    transfers[hashKey],
                                    downloadingTask = hash?.let { downloadingByHash[it] },
                                    api = api,
                                    appScope = appScope,
                                    failCount = hashKey?.let { failCounts[it] } ?: 0,
                                    pendingRedo = hashKey != null && redoPending.containsKey(hashKey),
                                    onRedoSubmitted = {
                                        if (!hashKey.isNullOrBlank()) {
                                            redoPending = redoPending + (hashKey to RedoPending(
                                                signature = transferSignature(transfers[hashKey]),
                                                startedAt = System.currentTimeMillis(),
                                            ))
                                        }
                                    },
                                    onMessage = {
                                        msg = it
                                        // 提交结果先留住 5 秒，别马上被后台结束提示挤掉
                                        msgHoldUntil = System.currentTimeMillis() + 5_000
                                    },
                                    onRefresh = { scope.launch { refresh() } },
                                )
                            }
                        }
                    }

                    DownloadTab.Transfer -> LazyColumn(
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 待人工复核 / 一键重新整理都只和「整理」有关，收在整理记录页签顶部
                        if (reviewGroups.isNotEmpty() || failedDays.isNotEmpty()) {
                            item {
                                // 汇总卡：与下面的整理记录卡片同款白卡，把统计和两个操作收成一块「工具区」。
                                // 之前它们散成一排裸元素，且两颗胶囊一个描边一个实底 —— 描边那颗看着像没选中的
                                // 页签、实底那颗像选中的页签，跟上面的页签行撞了视觉语言。
                                Surface(
                                    color = Color.White,
                                    shape = RoundedCornerShape(12.dp),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 统计单行不换行：右侧两颗胶囊宽度固定，剩余空间不够时由省略号收尾
                                        Text(
                                            failedDays.firstOrNull()
                                                ?.let { "共 ${transferList.size} 条 · 失败 ${it.count}" }
                                                ?: "共 ${transferList.size} 条",
                                            fontSize = 12.sp, color = TextSecondary,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        // 待人工复核：整理卡在「创建目标目录」被停下等人确认的那批文件没有
                                        // 整理历史记录，不会出现在下面的列表里，只能从这个入口进弹窗单独整理
                                        if (reviewGroups.isNotEmpty()) {
                                            ActionPill(
                                                text = "待人工复核 ${reviews.size}",
                                                // 人形图标：这件事得你自己确认，与右侧「重新整理」的 ↻（交给机器再跑一遍）相对
                                                icon = Icons.Rounded.PersonOutline,
                                                busy = reviewsBusy,
                                                onClick = openReviews,
                                            )
                                            if (failedDays.isNotEmpty()) Spacer(Modifier.width(6.dp))
                                        }
                                        // 最近三天有整理失败的记录时才出现：一键重新整理（弹窗里可选日期范围与目标路径）
                                        failedDays.firstOrNull()?.let { latest ->
                                            ActionPill(
                                                text = "重新整理 ${latest.count}",
                                                icon = Icons.Rounded.Refresh,
                                                busy = redoingAll,
                                            ) {
                                                showBatchRedo = true
                                                batchMatching = true
                                                probeById = emptyMap()
                                                val ids = failedDays.flatMap { it.ids }.distinct()
                                                scope.launch {
                                                    // 预检各条匹配到的目的目录：限流 6 并发，避免几十条同时压服务端
                                                    val probed = mutableMapOf<Long, JsonObject?>()
                                                    ids.chunked(6).forEach { chunk ->
                                                        coroutineScope {
                                                            chunk.map { id ->
                                                                async {
                                                                    id to runCatching { api.matchTransferTargetPath(id) }.getOrNull()
                                                                }
                                                            }.awaitAll()
                                                        }.forEach { (id, target) -> probed[id] = target }
                                                    }
                                                    probeById = probed
                                                    batchMatching = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (transferList.isEmpty()) {
                            item { EmptyHint("暂无整理记录") }
                        } else {
                            items(transferList) { rec ->
                                val hashKey = rec.str("download_hash")
                                // 整理记录卡片复用下载记录卡片：标题（剧名 + 季集）+ 状态标签 + 目标文件名，
                                // 副标题换成「整理于 MM-dd HH:mm · 分类」（下载记录那条是「站点 · 下载时间」）
                                HistoryCard(
                                    rec,
                                    rec,
                                    api = api,
                                    appScope = appScope,
                                    failCount = hashKey?.let { failCounts[it] } ?: 0,
                                    pendingRedo = hashKey != null && redoPending.containsKey(hashKey),
                                    subOverride = listOfNotNull(
                                        shortStamp(rec.str("date"))?.let { "整理于 $it" },
                                        rec.str("category")?.takeIf { it.isNotBlank() },
                                    ).joinToString("  ·  "),
                                    onRedoSubmitted = {
                                        if (!hashKey.isNullOrBlank()) {
                                            redoPending = redoPending + (hashKey to RedoPending(
                                                signature = transferSignature(rec),
                                                startedAt = System.currentTimeMillis(),
                                            ))
                                        }
                                    },
                                    onMessage = {
                                        msg = it
                                        msgHoldUntil = System.currentTimeMillis() + 5_000
                                    },
                                    onRefresh = { scope.launch { refresh() } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 批量重新整理弹窗：与单条整理同一个弹窗，多一个「整理范围」（最近三天按日期多选）
    if (showBatchRedo && failedDays.isNotEmpty()) {
        RedoTransferDialog(
            autoPath = batchAutoLabel,
            matching = batchMatching,
            busy = redoingAll,
            onDismiss = { showBatchRedo = false },
            days = failedDays,
            selectedDays = selectedDays,
            onToggleDay = { k ->
                if (!redoingAll) {
                    selectedDays = if (k in selectedDays) selectedDays - k else selectedDays + k
                }
            },
            onSelectAll = { if (!redoingAll) selectedDays = failedDays.map { it.key }.toSet() },
            count = selectedIds.size,
            onSubmit = { autoMatch ->
                // 立即回到上一层：几十条要按限流逐条提交，耗时可观，没必要让用户盯着弹窗等；
                // 「正在处理」改由「最近记录」行上的胶囊转圈来呈现
                showBatchRedo = false
                redoingAll = true
                // 跑在应用级作用域（不是页面作用域）：切 tab / 退出下载页也不中断，否则
                // 几十条整理会「提交一半就消失」；提交段再包一层 NonCancellable，App 退出前把
                // 正在执行的那批也尽量提交完。
                appScope.launch {
                    withContext(NonCancellable) {
                        try {
                            val ids = selectedIds
                            // 每条各自的自动匹配结果（预检没命中的条目回落「原有路径」）
                            val targetById = ids.mapNotNull { id -> probeById[id]?.let { id to it } }.toMap()
                            val submittedIds = mutableSetOf<Long>()
                            val errors = mutableListOf<String>()
                            // 逐条提交：宿主的 `logids` 批量分支不会从历史恢复媒体身份，整批会失败在
                            // 「手动整理需要同时提供 media_source 和 media_id」；只有单条 `logid` 分支
                            // 才会走 restore_manual_transfer_history_metadata。限流 4 并发。
                            ids.chunked(4).forEach { chunk ->
                                val results = coroutineScope {
                                    chunk.map { id ->
                                        async {
                                            runCatching {
                                                api.redoTransferManual(
                                                    id,
                                                    if (autoMatch) targetById[id] else null
                                                )
                                            }
                                        }
                                    }.awaitAll()
                                }
                                results.forEachIndexed { i, r ->
                                    if (r.isSuccess) submittedIds += chunk[i]
                                    else errors += (r.exceptionOrNull()?.message ?: "未知错误")
                                }
                            }
                            val notMatched = if (autoMatch) ids.size - targetById.size else 0
                            msg = buildString {
                                append("已提交 ${submittedIds.size} 条整理，后台处理中")
                                if (notMatched > 0) append("；$notMatched 条未匹配到目录，已按原有路径提交")
                                if (errors.isNotEmpty()) {
                                    append("；${errors.size} 条提交失败：${errors.first().take(70)}（再点一次可重试）")
                                }
                            }
                            // 失败原因先留住，别被逐条「整理完成」提示挤掉
                            msgHoldUntil = System.currentTimeMillis() + 10_000
                            // 逐条登记「整理中」：卡片以下载哈希为键，各卡片自己转圈直到后台写出新记录
                            val hashById = records
                                .mapNotNull { r -> r.long("id")?.let { it to r.str("download_hash") } }
                                .toMap()
                            val startedAt = System.currentTimeMillis()
                            redoPending = redoPending + submittedIds
                                .mapNotNull { id -> hashById[id]?.takeIf { it.isNotBlank() } }
                                .distinct()
                                .associateWith { RedoPending(transferSignature(transfers[it]), startedAt) }
                            refresh()
                        } catch (e: Exception) {
                            msg = "操作失败：${e.message}"
                        } finally {
                            redoingAll = false
                        }
                    }
                }
            }
        )
    }

    // 待人工复核弹窗：与重新整理弹窗同一套版式，多一组「目标目录」选择
    if (showReviews && reviewGroups.isNotEmpty()) {
        // 预检结果里匹配到的目录（去重），用于「自动匹配目录」的说明文案
        val matchedDirs = reviewProbe.values.mapNotNull { it }
            .map { it.str("target_path") to it.str("target_storage") }
            .distinct()
        val autoLabel = when {
            reviewMatching || matchedDirs.isEmpty() -> null
            matchedDirs.size == 1 -> matchedDirs.first().let { (p, s) ->
                listOfNotNull(p?.takeIf { it.isNotBlank() }, s?.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
            }
            else -> "匹配到 ${matchedDirs.size} 个目录，将按目录分别整理"
        }
        val picked = reviewGroups.filter { it.key in reviewSelected }.flatMap { it.items }
        ManualReviewDialog(
            target = "${reviewGroups.size} 部剧 · ${reviews.size} 个文件",
            autoPath = autoLabel,
            matching = reviewMatching,
            busy = reviewsBusy,
            groups = reviewGroups,
            selectedGroups = reviewSelected,
            onToggleGroup = { k ->
                if (!reviewsBusy) {
                    reviewSelected = if (k in reviewSelected) reviewSelected - k else reviewSelected + k
                }
            },
            onSelectAll = { if (!reviewsBusy) reviewSelected = reviewGroups.map { it.key }.toSet() },
            count = picked.size,
            onDismiss = { showReviews = false },
            onSubmit = { autoMatch ->
                // 同上：立即回到上一层，几十条按限流逐条提交耗时可观；
                // 「正在处理」由「待人工复核」胶囊上的转圈承接
                showReviews = false
                reviewsBusy = true
                // 应用级作用域 + NonCancellable：切 tab 不中断，提交段尽量跑到底
                appScope.launch {
                    withContext(NonCancellable) {
                        try {
                            val submitted = mutableSetOf<String>()
                            val errors = mutableListOf<String>()
                            // taskId → 所属分组 key（预建，避免内层逐条遍历 reviewGroups 做 O(n²) 查找）
                            val groupKeyByTaskId = reviewGroups
                                .flatMap { g -> g.items.map { it.taskId to g.key } }
                                .toMap()
                            // 逐条提交（限流 4）：宿主的复核判定与手动整理都是按单个文件处理的
                            picked.chunked(4).forEach { chunk ->
                                val results = coroutineScope {
                                    chunk.map { item ->
                                        async {
                                            runCatching {
                                                if (autoMatch) {
                                                    // 自动匹配目录：按源文件重新提交整理，显式带上
                                                    // 该条目所属分组预检到的媒体库目录（不是全局第一条的）
                                                    val target = groupKeyByTaskId[item.taskId]?.let { reviewProbe[it] }
                                                    api.manualTransferFile(item.storage, item.path, target)
                                                } else {
                                                    // 原有目录：让宿主按既定规划重试这一条
                                                    api.manualReviewRetry(item.taskId, item.operationId)
                                                }
                                            }
                                        }
                                    }.awaitAll()
                                }
                                results.forEachIndexed { i, r ->
                                    if (r.isSuccess) submitted += chunk[i].taskId
                                    else errors += (r.exceptionOrNull()?.message ?: "未知错误")
                                }
                            }
                            msg = buildString {
                                append("已提交 ${submitted.size} 条整理，后台处理中")
                                if (errors.isNotEmpty()) {
                                    append("；${errors.size} 条提交失败：${errors.first().take(70)}（再点一次可重试）")
                                }
                            }
                            // 失败原因先留住，别被逐条「整理完成」提示挤掉
                            msgHoldUntil = System.currentTimeMillis() + 10_000
                            refresh()
                        } catch (e: Exception) {
                            msg = "操作失败：${e.message}"
                        } finally {
                            reviewsBusy = false
                        }
                    }
                }
            }
        )
    }
}

/** 页签空态：居中灰字，上下留白（三个页签各自无数据时使用；版式与订阅页空态一致） */
@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        color = TextTertiary, fontSize = 13.sp, textAlign = TextAlign.Center
    )
}

/**
 * 把待人工复核任务按**宿主计划的目标目录**分组：取 `step.intent.path` 的父目录
 * （`/DSM/media/国产剧/喜剧之王 (2026)/Season 1` → `/DSM/media/国产剧/喜剧之王 (2026)`），
 * 组名用末段「喜剧之王 (2026)」，与弹窗里展示的分组一致；条数多的排在前面。
 */
private fun reviewGroupsOf(reviews: List<JsonObject>): List<ReviewGroup> {
    val byKey = LinkedHashMap<String, MutableList<ReviewItem>>()
    reviews.forEach { r ->
        val taskId = r.str("task_id")?.takeIf { it.isNotBlank() } ?: return@forEach
        val step = r["step"] as? JsonObject ?: return@forEach
        val opId = step.str("operation_id")?.takeIf { it.isNotBlank() } ?: return@forEach
        val src = r["source"] as? JsonObject ?: return@forEach
        val path = src.str("path")?.takeIf { it.isNotBlank() } ?: return@forEach
        val planPath = (step["intent"] as? JsonObject)?.str("path").orEmpty()
        val dir = planPath.trimEnd('/').substringBeforeLast('/')
        val key = dir.ifBlank { planPath.ifBlank { "其他" } }
        byKey.getOrPut(key) { mutableListOf() } += ReviewItem(
            taskId = taskId,
            operationId = opId,
            storage = src.str("storage").orEmpty(),
            path = path,
        )
    }
    return byKey.entries
        .map { (key, items) -> ReviewGroup(key, key.substringAfterLast('/'), items) }
        .sortedByDescending { it.items.size }
}

/**
 * 正在下载的任务卡片（紧凑版）：标题（中文名 + 季集 同行 + 规格副行）+暂停/删除 一行，
 * 进度条一行，百分比·状态·速度·体积·剩余时间 合并一行。短标题约 86dp，长标题最多约 104dp。
 * 标题怎么拆见 [splitTorrentTitle]。
 */
@Composable
private fun DownloadingCard(
    t: JsonObject,
    api: MpApi,
    onResult: (String?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val progress = (t.str("progress")?.toFloatOrNull() ?: 0f).coerceIn(0f, 100f)
    val state = t.str("state") ?: "downloading"
    val paused = state.contains("paused", true) || state.contains("stop", true)
    val stateText = when {
        paused -> "已暂停"
        state.contains("seed", true) -> "做种中"
        // 卡在 0% 且没有下载速度：种子还没真正开始下载
        progress <= 0f && t.noDownloadSpeed() -> "等待开始下载"
        state.contains("downloading", true) -> "下载中"
        else -> state
    }
    var busy by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    fun act(action: suspend () -> String) {
        scope.launch {
            busy = true
            try {
                action()
                onResult(null)
            } catch (e: Exception) {
                onResult("操作失败：${e.message}")
            } finally {
                busy = false
            }
        }
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            // 第一行：剧名 + 季集（同一行，追剧时第一眼要找的集号跟剧名绑一起）；
            // 第二行：规格 + 站点（灰字 1 行省略）。右侧暂停/删除图标顶部对齐首行。
            val rawTitle = t.str("title") ?: t.str("name") ?: "未知任务"
            // 宿主 MetaInfo 已解析出的干净剧名与季集（`Thunder Order` / `S01 E12`），拆标题时优先用
            val hostName = t.str("name")
            val hostSeasonEpisode = t.str("season_episode")
            val title = remember(rawTitle, hostName, hostSeasonEpisode) {
                splitTorrentTitle(rawTitle, hostName, hostSeasonEpisode)
            }
            // 站点名由宿主从下载历史（torrent_site）注入，只有经 MoviePilot 加过的任务才有
            val site = t.str("site_name")?.trim()?.takeIf { it.isNotBlank() }
            val spec = title.spec
            val hasSub = title.episode != null || spec != null || site != null
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            title.name,
                            fontSize = if (hasSub) 14.sp else 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            // 拆不出信息时才需要 2 行兜底（这条路径已补过零宽断点）
                            maxLines = if (hasSub) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = if (hasSub) 18.sp else 17.sp,
                            color = TextPrimary,
                            // fill = false：短剧名只占自身宽度，季集**紧跟**在剧名右侧（贴合 mockup）；
                            // 剧名过长时仍会被压缩到可用宽度内并省略，不会把季集挤出屏幕。
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (title.episode != null) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                title.episode,
                                fontSize = 12.sp, fontWeight = FontWeight.Medium,
                                color = TextTertiary,
                                maxLines = 1,
                            )
                        }
                    }
                    if (spec != null || site != null) {
                        Spacer(Modifier.height(2.dp))
                        // 站点在最前、带浅靛蓝底（复用公共 [InboundTag]，和下载状态标签同一套形态），
                        // 「这条来自哪个站」一眼可见；规格跟在后面吃剩余宽度，长了省略。
                        // chip 定宽上限 96dp：超长站点名内部省略，不会把规格整段挤没。
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (site != null) {
                                InboundTag(
                                    text = site,
                                    bg = SelectedPill,
                                    fg = SelectedPillText,
                                    modifier = Modifier.widthIn(max = 96.dp),
                                )
                                if (spec != null) Spacer(Modifier.width(5.dp))
                            }
                            if (spec != null) {
                                Text(
                                    spec,
                                    fontSize = 11.sp, color = TextTertiary,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    // 暂停 / 继续（图标按钮，24dp）
                    IconAction(
                        icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                        tint = MaterialTheme.colorScheme.primary,
                        enabled = !t.str("hash").isNullOrBlank(),
                        onClick = {
                            t.str("hash")?.takeIf { it.isNotBlank() }?.let { hash ->
                                act {
                                    api.controlDownload(hash, if (paused) "start" else "stop")
                                    if (paused) "已继续" else "已暂停"
                                }
                            }
                        }
                    )
                    // 删除（红色图标，弹确认框）
                    IconAction(
                        icon = Icons.Rounded.DeleteOutline,
                        tint = DangerRed,
                        onClick = { showDelete = true }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(5.dp),
                trackColor = DividerColor,
            )
            Spacer(Modifier.height(5.dp))
            // 第三行：百分比 · 状态 · 速度（左） + 体积 · 剩余（右），单行合并
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.1f%%".format(progress),
                    fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                val dlspeed = t.str("dlspeed")?.takeIf { it.isNotBlank() }
                Text(
                    buildString {
                        append("  ·  ")
                        append(stateText)
                        if (dlspeed != null) append(" · $dlspeed")
                    },
                    fontSize = 11.sp, color = TextTertiary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                val size = t.long("size")?.takeIf { it > 0 }
                val left = t.str("left_time")?.takeIf { it.isNotBlank() }
                val tail = listOfNotNull(
                    size?.let { "体积 ${humanSize(it)}" },
                    left?.let { "剩 $it" },
                ).joinToString(" · ")
                if (tail.isNotBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(tail, fontSize = 11.sp, color = TextTertiary, maxLines = 1)
                }
            }
        }
    }

    if (showDelete) {
        ConfirmDeleteDialog(
            title = "删除下载任务",
            target = t.str("title") ?: t.str("name") ?: "未知任务",
            message = "已下载的文件会保留，仅从下载器移除任务。",
            busy = busy,
            onDismiss = { showDelete = false },
            onConfirm = {
                val hash = t.str("hash")
                if (!hash.isNullOrBlank()) {
                    act {
                        api.deleteDownload(hash)
                        "已删除"
                    }
                }
                showDelete = false
            }
        )
    }
}

/** 下载记录标题：标题 + 季/集。seasons 形如 "S01"/"S01-S05"，episodes 形如 "E05"/"E43-E47"/"E01+E02" */
private fun historyTitle(h: JsonObject): String {
    val base = h.str("title") ?: h.str("torrent_name") ?: "未知记录"

    // 从 "S01-S05" / "E43-E47" / "E01+E02" 里提取数字，保留范围语义
    fun label(field: String, unit: String): String? {
        val raw = h.str(field)?.takeIf { it.isNotBlank() } ?: return null
        val nums = Regex("\\d+").findAll(raw.uppercase()).map { it.value.toInt() }.toList()
        return when {
            nums.isEmpty() -> null
            nums.size == 1 -> "第${nums[0]}$unit"
            else -> "第${nums.first()}-${nums.last()}$unit"
        }
    }

    val parts = listOfNotNull(label("seasons", "季"), label("episodes", "集"))
    return if (parts.isEmpty()) base else "$base ${parts.joinToString(" ")}"
}

/**
 * 长英文串补断点：Compose 默认断行策略里 `.` **不是**断行点，`King.of.Comedy.2026.S01…` 整串会
 * 变成一个不可断的长块，逼着前面的中文在字与字之间提前断行（真机实测「[喜剧之 / 王].King.of…」）。
 * 在 `.` `_` `+` 后插一个零宽空格（不显示、不占宽）即可让排版引擎在点号处断行。
 */
private fun String.breakable(): String =
    replace(".", ".\u200B").replace("_", "_\u200B").replace("+", "+\u200B")

/**
 * 正在下载卡片的标题三段（见 [splitTorrentTitle]）。
 * [name] 是中文剧名，[episode] 贴在剧名同一行右侧，[spec] 是规格副行。
 */
private data class TorrentTitle(
    val name: String,
    /** 「第1季 第12集」/「第1季」/「全集」；无季集信息为 null */
    val episode: String?,
    /** 「2160P · 杜比视界 · HDR · WEB-DL」；认不出规格为 null */
    val spec: String?,
)

/**
 * 把原始种子名拆成「剧名 + 季集 + 规格」，例：
 * `Thunder Order S01E12 2026 1080p TX WEB-DL H265 AAC-ADWeb` →
 * `Thunder Order` / `第1季 第12集` / `1080P · WEB-DL`
 *
 * 为什么拆：种子名普遍是「剧名 + 年份 + 季集 + 画质 + 编码-发布组」这种形态，整串显示时中文名会被
 * 劈成两半、第一行只占约四分之一宽（见 [breakable] 注释里的真机实测）。拆开后剧名完整，季集提到
 * 同行右侧（追剧时第一眼要看的就是集号），画质规格收成 11sp 灰字副行。
 *
 * 剧名优先取中文（[chineseNameFrom]）：宿主的命名规则会把中文名塞进方括号尾部
 * （`Thunder Chaser S01 2028 1080p WEB-DL H264 AAC-CMCTV[雷霆令 | 第32集 | 主演…]`），
 * 中文名比罗马音更好认；没有中文时用宿主 MetaInfo 解析出的 [hostName]（`Thunder Chaser`）。
 *
 * 季集优先用宿主给的 [hostSeasonEpisode]（`S01 E12`/`S01 E05-E06`/`S01`），它连方括号里的
 * 「第32集」也能认；宿主没给才在标题里自己找（[parseSeasonEpisodeFromTitle]）。
 *
 * 连名字都认不出时（宿主也没解析出来）整串显示、不画副行，避免和原始串重复一遍信息。
 */
private fun splitTorrentTitle(raw: String, hostName: String?, hostSeasonEpisode: String?): TorrentTitle {
    val name = chineseNameFrom(raw)
        ?: hostName?.takeIf { it.isNotBlank() && it != raw }
        ?: return TorrentTitle(raw.breakable(), null, null)
    return TorrentTitle(
        name = name,
        episode = formatSeasonEpisode(hostSeasonEpisode) ?: parseSeasonEpisodeFromTitle(raw),
        spec = parseSpecs(raw),
    )
}

/**
 * 从种子名里挑出中文剧名。三种常见形态都要认：
 * - `[喜剧之王].King.of.Comedy.2026.S01…` —— 中文在开头方括号里
 * - `[电影天堂]喜剧之王.2026…` —— 开头方括号是站点名，剧名在括号外
 * - `Thunder Chaser S01 … CMCTV[雷霆令 | 第32集 | 主演…]` —— 中文在尾部方括号里（宿主命名规则）
 *
 * 所以按「括号深度」把候选段分两类：优先取**括号外**的（开头方括号多半是站点名），
 * 全在括号里时取**最早**的一段（尾部 `[中文名 | 集 | 主演]` 的名排在第一段）。
 */
private fun chineseNameFrom(raw: String): String? {
    // 连续中文段（允许中间的普通空格与全角空格，如「庆余年 第二季」）；单字不算，避免「之」这类噪声
    val runs = Regex("[\\u4e00-\\u9fa5][\\u4e00-\\u9fa5\\u3000 ]*")
        .findAll(raw)
        .filter { it.value.trim().length >= 2 }
        .toList()
    if (runs.isEmpty()) return null
    // 常见「非剧名」中文词（画质/字幕/压制说明）：整串中文只由这类词构成时说明压根没有中文剧名
    val noise = setOf(
        "国语", "粤语", "中字", "国语中字", "双语", "简繁", "简体", "繁体", "高清", "超清",
        "蓝光", "修复", "无删减", "全集", "完结", "预告", "花絮", "首发", "转载", "内封", "内嵌", "硬字幕",
    )
    val candidates = runs.filter { it.value.trim() !in noise }
    if (candidates.isEmpty()) return null
    val inBracket = bracketMask(raw)
    val bare = candidates.filter { !inBracket[it.range.first] }
    val pick = bare.firstOrNull() ?: candidates.first()
    // 尾部方括号里「蒸蒸日上 第11-12集」这种会被整段抓下来，末尾的「第」要削掉
    val cn = pick.value.trim()
        .trim('【', '】', '「', '」', '『', '』', '·', '|', '/', '-', '_', '.', ' ')
        .trimEnd('第')
        .trim()
    // 中文段过长说明它本身是描述（不是剧名）；全在括号里且很短说明只截到「修复」之类
    if (cn.length > 20) return null
    if (bare.isEmpty() && cn.length < 3) return null
    return cn.takeIf { it.isNotEmpty() }
}

/** 标记每个字符是否落在 `[]` `【】` `()` `（）` 里（按嵌套深度算，括号内的斜杠不会误判成括号外） */
private fun bracketMask(s: String): BooleanArray {
    val open = "[(（【"
    val close = "])）】"
    val mask = BooleanArray(s.length)
    var depth = 0
    for (i in s.indices) {
        val c = s[i]
        when {
            c in open -> { depth++; mask[i] = true }
            c in close -> { mask[i] = true; depth = (depth - 1).coerceAtLeast(0) }
            else -> mask[i] = depth > 0
        }
    }
    return mask
}

/**
 * 把宿主给的 `season_episode` 转成中文，形如 `S01 E12` / `S01 E05-E06` / `S01` → 「第1季 第12集」。
 * 空值或解析不出内容时返回 null（调用方回退到自己从标题里找）。
 */
private fun formatSeasonEpisode(value: String?): String? {
    val v = value?.uppercase()?.takeIf { it.isNotBlank() } ?: return null

    fun num(s: String): String = s.trimStart('0').ifEmpty { "0" }

    val parts = mutableListOf<String>()
    Regex("S(\\d{1,2})").find(v)?.let { parts += "第${num(it.groupValues[1])}季" }
    Regex("E(\\d{1,4})(?:\\s*[-~]\\s*E?(\\d{1,4}))?").find(v)?.let { m ->
        val a = num(m.groupValues[1])
        val b = m.groupValues[2].takeIf { it.isNotBlank() }?.let(::num)
        parts += if (b != null && b != a) "第$a-${b}集" else "第${a}集"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
}

/**
 * 宿主没给季集时的兜底：直接在标题里找。依次匹配
 * `S01E12`（可带范围 E01-E12）→ 中文「第1季/第12集」→ 只有季号 `S01` → 只有集号 `E12`/`EP12` →
 * 整季包（Complete/全集）；认不出返回 null（电影没有集号，这里就该是空）。
 */
private fun parseSeasonEpisodeFromTitle(raw: String): String? {
    val upper = raw.uppercase()

    fun num(v: String): String = v.trimStart('0').ifEmpty { "0" }

    fun episode(from: String, to: String?): String {
        val a = num(from)
        val b = to?.takeIf { it.isNotBlank() && it != "0" }?.let(::num)
        return if (b != null && b != a) "第$a-${b}集" else "第${a}集"
    }

    // S01E12 / s1e12 / S01.E12 / S01.EP12（可带范围 E01-E12）
    Regex("S(\\d{1,2})[\\s._-]*(?:EP|E)[\\s._-]*(\\d{1,4})(?:\\s*[-~]\\s*(?:EP|E)?(\\d{1,4}))?")
        .find(upper)?.let { m ->
            return "第${num(m.groupValues[1])}季 " + episode(m.groupValues[2], m.groupValues[3])
        }

    // 中文写法：第1季 / 第1季第12集 / 第12集 / 第12-13集
    val cnEpisode = Regex("第\\s*(\\d{1,4})\\s*(?:[-~至]\\s*(\\d{1,4})\\s*)?[集话]").find(raw)
    Regex("第\\s*(\\d{1,2})\\s*季").find(raw)?.let { m ->
        val season = "第${num(m.groupValues[1])}季"
        return if (cnEpisode == null) season
        else "$season " + episode(cnEpisode.groupValues[1], cnEpisode.groupValues[2])
    }
    cnEpisode?.let { return episode(it.groupValues[1], it.groupValues[2]) }

    // 只有季号：S01（前后不接字母数字，避开「1080S」这类）
    Regex("(?<![A-Z0-9])S(\\d{1,2})(?![A-Z0-9])").find(upper)?.let {
        return "第${num(it.groupValues[1])}季"
    }

    // 只有集号：E12 / EP12（可带范围）
    Regex("(?<![A-Z0-9])(?:EP|E)[\\s._-]*(\\d{1,4})(?:\\s*[-~]\\s*(?:EP|E)?(\\d{1,4}))?")
        .find(upper)?.let { m ->
            return episode(m.groupValues[1], m.groupValues[2])
        }

    // 整季包（放最后，否则「第1季」也会被说成全集）
    if (upper.contains("COMPLETE") || raw.contains("全集")) return "全集"
    return null
}

/**
 * 从种子名挑出画质规格并归一成标签，按「分辨率 · 杜比 · HDR · 来源」拼成一行；都没有返回 null。
 * 例：`2160P · 杜比视界 · HDR · WEB-DL`。
 *
 * 杜比分两类：`DV`/`DoVi`/`Dolby Vision` → 杜比视界（画质）；`Atmos` → 杜比全景声（音效）。
 * ⚠️ `DV` 必须带词边界（避开 `DVDRip`）、`HDR` 同理（避开 `HDRip`）；`HDR10`/`HDR10+` 一律显示 HDR。
 */
private fun parseSpecs(raw: String): String? {
    val upper = raw.uppercase()

    fun hit(vararg patterns: String) = patterns.any { Regex(it).containsMatchIn(upper) }

    val parts = mutableListOf<String>()
    when {
        hit("(?<![0-9A-Z])2160[PI](?![0-9A-Z])", "(?<![0-9A-Z])4K(?![0-9A-Z])") -> parts += "2160P"
        hit("(?<![0-9A-Z])1080[PI](?![0-9A-Z])") -> parts += "1080P"
        hit("(?<![0-9A-Z])720[PI](?![0-9A-Z])") -> parts += "720P"
    }
    if (hit("(?<![A-Z])(?:DOVI|DV)(?![A-Z])", "DOLBY[ ._-]?VISION", "杜比视界")) parts += "杜比视界"
    if (hit("(?<![A-Z])ATMOS", "杜比全景声")) parts += "杜比全景声"
    if (hit("(?<![A-Z])HDR(?:10\\+?)?(?![A-Z0-9])")) parts += "HDR"
    when {
        hit("(?<![A-Z])WEB[\\s._-]?DL(?![A-Z])") -> parts += "WEB-DL"
        hit("(?<![A-Z])WEB[\\s._-]?RIP(?![A-Z])") -> parts += "WEBRip"
        hit("(?<![A-Z])REMUX") -> parts += "REMUX"
        hit("BLU[\\s._-]?RAY") -> parts += "BluRay"
        hit("(?<![A-Z])BDRIP") -> parts += "BDRip"
        hit("(?<![A-Z])HDTV") -> parts += "HDTV"
        hit("(?<![A-Z])DVDRIP") -> parts += "DVDRip"
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * 下载记录卡片，附带媒体库整理结果标签：
 * 成功=绿「已整理到媒体库」/ 失败=红「整理失败：原因」+ 右侧「失败 N 次」/ 还在下载=靛蓝「正在下载」/
 * 卡在0%=灰「等待开始下载」/ 已下完未整理=青蓝「等待整理」
 */
@Composable
private fun HistoryCard(
    h: JsonObject,
    transfer: JsonObject?,
    downloadingTask: JsonObject? = null,
    api: MpApi,
    appScope: CoroutineScope,
    failCount: Int = 0,
    pendingRedo: Boolean = false,
    // 副标题覆盖：整理记录页签传「整理于 MM-dd HH:mm · 分类」；为空时按 `h` 自动拼「站点 chip + 下载时间」
    subOverride: String? = null,
    onRedoSubmitted: () -> Unit = {},
    onMessage: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    // 重新整理弹窗：自动匹配到的目的目录（null = 还没匹配到 / 匹配失败）
    var showRedo by remember { mutableStateOf(false) }
    var autoPath by remember { mutableStateOf<JsonObject?>(null) }
    var matchingAuto by remember { mutableStateOf(false) }
    val redoId = transfer?.long("id")
    val ok = transfer?.str("status")?.toBooleanStrictOrNull() ?: false
    // 整理失败：标题行右侧给一个「重新整理」圆形图标按钮（与下载卡片的暂停/删除同款）
    val failed = transfer != null && !ok
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    historyTitle(h),
                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                // 失败时可点（弹出选目标路径的窗口）；提交后服务端会摘掉旧记录、卡片回到「等待整理」，
                // 这期间仍占住同一个位置显示转圈，直到后台写出整理结果
                if (failed || pendingRedo) {
                    Spacer(Modifier.width(6.dp))
                    IconAction(
                        icon = Icons.Rounded.Refresh,
                        tint = MaterialTheme.colorScheme.primary,
                        enabled = redoId != null && !pendingRedo,
                        busy = busy || pendingRedo,
                        onClick = {
                            if (redoId != null) {
                                showRedo = true
                                autoPath = null
                                matchingAuto = true
                                scope.launch {
                                    autoPath = runCatching { api.matchTransferTargetPath(redoId) }.getOrNull()
                                    matchingAuto = false
                                }
                            }
                        }
                    )
                }
            }
            // 「整理记录」页签用 subOverride 传「整理于 … · 分类」的纯文本；「下载记录」页签的副行是
            // 「站点 + 下载时间」——站点沿用「正在下载」卡片那套浅靛蓝 chip（公共 [InboundTag]），
            // 不再写成「站点：观众 · 时间」的灰字串，两页签的站点呈现于是完全一致。
            val site = h.str("torrent_site")?.takeIf { it.isNotBlank() }
            val dlDate = h.str("date")?.takeIf { it.isNotBlank() }?.take(16)?.replace('T', ' ')
            if (subOverride != null) {
                Spacer(Modifier.height(2.dp))
                Text(subOverride, fontSize = 12.sp, color = TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else if (site != null || dlDate != null) {
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (site != null) {
                        InboundTag(
                            text = site,
                            bg = SelectedPill,
                            fg = SelectedPillText,
                            // chip 定宽上限 96dp（与正在下载一致）：超长站点名内部省略，不会挤掉时间
                            modifier = Modifier.widthIn(max = 96.dp),
                        )
                        if (dlDate != null) Spacer(Modifier.width(5.dp))
                    }
                    if (dlDate != null) {
                        Text(
                            dlDate,
                            fontSize = 12.sp, color = TextTertiary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val errmsg = transfer?.str("errmsg")
                // 种子还在下载器里：按进度区分「等待开始下载」（卡在 0%）和「正在下载」
                val dlProgress = downloadingTask?.str("progress")?.toFloatOrNull()
                val dlStarted = dlProgress != null && dlProgress > 0f
                val dlFinished = dlProgress != null && dlProgress >= 100f
                // 目标文件名（带路径时只取文件名）
                val destName = transfer?.str("dest")?.takeIf { it.isNotBlank() }?.substringAfterLast('/')
                if (transfer != null && ok) {
                    // 已整理到媒体库：标签后面空一格紧跟目标文件名，其余留白
                    InboundTag("已整理到媒体库", TagDoneBg, SuccessGreen)
                    destName?.let {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            it,
                            fontSize = 11.sp, color = TextTertiary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                } else {
                    // 标签占满左侧剩余宽度，右侧让给「失败 N 次」；原因过长时在标签内省略，
                    // 不会把右侧的次数挤掉
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        when {
                            failed ->
                                InboundTag("整理失败${errmsg?.let { "：$it" } ?: ""}", TagFailBg, DangerRed)
                            // 已下载完成但还没有整理记录：确实是等待整理，不要误显示「正在下载」
                            dlFinished -> InboundTag("等待整理", TagWaitArrangeBg, InfoCyan)
                            // 卡在 0% 且没有下载速度：还没真正开始下载
                            downloadingTask != null && !dlStarted && downloadingTask.noDownloadSpeed() ->
                                InboundTag("等待开始下载", TagWaitBg, TextTertiary)
                            // 下载器中该种子有进度：正在下载
                            downloadingTask != null ->
                                InboundTag("正在下载", TagDownloadBg, SelectedPillText)
                            // 下载器里没有、也没有整理记录
                            else -> InboundTag("等待整理", TagWaitArrangeBg, InfoCyan)
                        }
                    }
                    // 右侧：失败时「失败 N 次」固定在**最右**（与行尾对齐，和有没有目标文件名无关），
                    // 目标文件名统一收窄到 88dp 并靠左，让有次数/无次数的失败卡片文件名起点一致，
                    // 不会因右侧标签有无而左右乱跳。
                    if (failed && failCount > 0) {
                        destName?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it,
                                fontSize = 11.sp, color = TextTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 88.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "失败 $failCount 次",
                            fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = DangerRed, maxLines = 1
                        )
                    } else {
                        destName?.let {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                it,
                                fontSize = 11.sp, color = TextTertiary,
                                maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 88.dp)
                            )
                        }
                    }
                }
            }
            // 整理失败：状态行下方追加一行宿主的恢复建议（recovery_action，宿主已按失败类型分好类）。
            // 11sp 灰字、最多 2 行（同名文件那句 27 字会折行），无建议时不渲染。
            val recovery = transfer?.str("recovery_action")?.takeIf { it.isNotBlank() }
            if (failed && recovery != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "建议：$recovery",
                    fontSize = 11.sp, color = TextTertiary, lineHeight = 16.sp,
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (showRedo && redoId != null) {
        // 自动匹配到目的目录时带上存储名，如「/DSM/media/ · smb」
        val autoLabel = autoPath?.let { p ->
            listOfNotNull(
                p.str("target_path")?.takeIf { it.isNotBlank() },
                p.str("target_storage")?.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
        }
        RedoTransferDialog(
            target = historyTitle(h),
            autoPath = autoLabel,
            matching = matchingAuto,
            busy = busy,
            onDismiss = { showRedo = false },
            onSubmit = { autoMatch ->
                // 立即回到上一层：卡片标题行右侧的重新整理图标会原地转圈，不必等提交结果。
                // 用 appScope 提交，即使此时切走本页，这一发整理请求也全程不中断。
                showRedo = false
                busy = true
                appScope.launch {
                    try {
                        onMessage(api.redoTransferManual(redoId, if (autoMatch) autoPath else null))
                        // 提交成功：图标继续转圈（pendingRedo），直到后台整理结束写出新记录
                        onRedoSubmitted()
                    } catch (e: Exception) {
                        onMessage("操作失败：${e.message}")
                    } finally {
                        busy = false
                        onRefresh()
                    }
                }
            }
        )
    }
}

/**
 * 下载速度是否为 0：MoviePilot 把 dlspeed 格式化为 "0.0B"/"0B"（或留空）。
 * 配合 0% 进度判断种子是否「还没开始下载」（卡在 0% 无速度）。
 */
private fun JsonObject.noDownloadSpeed(): Boolean {
    val speed = str("dlspeed")?.trim().orEmpty()
    return speed.isEmpty() || speed.startsWith("0")
}

/** 后台整理超过该时长就不再转圈（只提示「仍在处理」，列表照旧刷新） */
private const val REDO_TIMEOUT_MS = 90_000L

/** 有下载任务 / 整理中 / 待复核时的轮询间隔 */
private const val FAST_POLL_MS = 5_000L

/** 空闲（无任何活跃状态）时的轮询间隔：只用来兜底检测新下载任务 */
private const val IDLE_POLL_MS = 30_000L

/** 「最近记录」列表至少保留的最近下载条数；再往后的条目只补「整理失败」的那些 */
private const val HISTORY_KEEP = 30

/** 最近三天的窗口起点（"yyyy-MM-dd"，含今天往前推 2 天） */
private fun recentWindowStart(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .format(Date(System.currentTimeMillis() - 2 * 24 * 3600_000L))

/**
 * 最近三天内整理失败的下载哈希集合（统一小写比较）。
 *
 * 用途：失败整理记录的 `date` 是**整理时间**，对应的下载记录可能排在「最近 30 条」之外
 * （实测「征途」第 40 位），把这些条目补进列表才能看到它的整理状态与转圈。
 */
private fun failedHashSet(records: List<JsonObject>): Set<String> {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val earliest = recentWindowStart()
    val out = mutableSetOf<String>()
    records.forEach { rec ->
        if (rec.str("status")?.toBooleanStrictOrNull() != false) return@forEach
        val day = rec.str("date")?.take(10) ?: return@forEach
        if (day < earliest || day > today) return@forEach
        rec.str("download_hash")?.takeIf { it.isNotBlank() }?.let { out += it.lowercase() }
    }
    return out
}

/**
 * 最近三天内每个下载（download_hash）的失败整理记录**条数**，用于失败卡片右侧的「失败 N 次」。
 *
 * 为什么按记录条数而不是按 `retry_count`：宿主这两个字段不可用 —— 实测 `retry_count` 恒为 0/1，
 * 且同一个 `download_hash` + 同一个 `src` 从不会重复出现（0 组），真正反映「失败了多少次」的
 * 是同一个下载包下失败的记录数（整季包一集一条，如「喜剧之王」一个 hash 挂了 21 条）。
 * 口径与「重新整理 N」一致（同为整理记录粒度、同为最近三天窗口），超过窗口的旧失败不给次数。
 */
private fun failedCountByHash(records: List<JsonObject>): Map<String, Int> {
    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val earliest = recentWindowStart()
    val byHash = mutableMapOf<String, MutableSet<Long>>()
    records.forEach { rec ->
        if (rec.str("status")?.toBooleanStrictOrNull() != false) return@forEach
        val day = rec.str("date")?.take(10) ?: return@forEach
        if (day < earliest || day > today) return@forEach
        val hash = rec.str("download_hash")?.takeIf { it.isNotBlank() } ?: return@forEach
        val id = rec.long("id") ?: return@forEach
        byHash.getOrPut(hash) { mutableSetOf() }.add(id)
    }
    return byHash.mapValues { it.value.size }
}

/**
 * 正在整理的一条下载记录：提交时刻的整理记录签名 + 提交时间。
 * 之后每次刷新比对签名，签名变了就说明后台已经处理过（成功 / 再次失败 / 新增记录）。
 */
private data class RedoPending(val signature: String, val startedAt: Long)

/**
 * 最近三天（含今天）整理失败的记录，按日期倒序（最多 3 天，当天没失败就少一天）。
 *
 * **按整理记录粒度**统计：一条整理记录算一条（整季包会按集各建一条记录），
 * 不做 download_hash 去重 —— 否则 26 条失败记录会被算成 3 条（按种子只算一条）。
 * 记录自身的 `date`（"yyyy-MM-dd HH:mm:ss"）前 10 位即所属日期。
 */
private fun recentFailedDays(records: List<JsonObject>): List<RedoDay> {
    val dayFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    val labelFmt = SimpleDateFormat("MM-dd", Locale.US)
    val weekFmt = SimpleDateFormat("EEE", Locale.CHINA)
    val today = dayFmt.format(Date())
    val earliest = recentWindowStart()
    val byDay = LinkedHashMap<String, MutableSet<Long>>()
    records.forEach { rec ->
        if (rec.str("status")?.toBooleanStrictOrNull() != false) return@forEach
        val day = rec.str("date")?.take(10) ?: return@forEach
        if (day < earliest || day > today) return@forEach
        val id = rec.long("id") ?: return@forEach
        byDay.getOrPut(day) { mutableSetOf() }.add(id)
    }
    return byDay.entries
        .sortedByDescending { it.key }
        .take(3)
        .map { (day, ids) ->
            val d = runCatching { dayFmt.parse(day) }.getOrNull()
            RedoDay(
                key = day,
                label = if (d == null) day else "${labelFmt.format(d)} ${weekFmt.format(d)}",
                ids = ids.toList(),
            )
        }
}

/** 整理记录签名：任一字段变化都视为后台已处理过这条记录 */
private fun transferSignature(rec: JsonObject?): String {
    if (rec == null) return ""
    return listOf("id", "status", "retry_count", "date", "errmsg", "dest")
        .joinToString("|") { rec.str(it) ?: "-" }
}

/**
 * 整理记录汇总卡右侧的操作胶囊：「待人工复核」与「重新整理」共用同一套外观
 * （主色实底 + 白字、10dp 圆角、28dp 高、12sp SemiBold），只有文案与图标不同。
 *
 * 之前两者靠「描边 / 实底」区分主次，但它们就排在页签行下面：描边那颗看着像「未选中的页签」、
 * 实底那颗像「选中的页签」，把操作区读成了第二行页签，所以统一成同款实底胶囊，
 * 再用 10dp 圆角矩形（页签是全圆胶囊）和外面的白色汇总卡把两者分开。
 *
 * 图标一律在文字**左侧**（「待人工复核」的人形、「重新整理」的 ↻），两颗胶囊的骨架就完全一致；
 * 早先「待人工复核」把 › 挂在右侧，既和左边那颗不对称，又像「点进去下一页」的导航箭头，已去掉。
 *
 * [busy] 时左侧内容换成同尺寸转圈并禁止再点，避免重复提交；尺寸一致，忙碌切换时宽度不跳。
 */
@Composable
private fun ActionPill(
    text: String,
    icon: ImageVector? = null,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .height(28.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(enabled = !busy, onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when {
            busy -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(13.dp),
                    strokeWidth = 1.5.dp,
                    color = Color.White
                )
                Spacer(Modifier.width(4.dp))
            }
            icon != null -> {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
        }
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** 紧凑图标按钮（28dp 圆形点击区），用于卡片右侧的暂停/继续、删除、重新整理操作 */
@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    enabled: Boolean = true,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 1.8.dp,
                color = tint
            )
        } else {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
    }
}
