package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject

/**
 * 下载页：正在下载（进度条/速度/剩余时间）+ 最近下载记录，
 * 每条记录关联媒体库入库结果（成功=绿 / 失败=红 / 等待=灰）。5 秒自动刷新。
 */
@Composable
fun DownloadsScreen(api: MpApi, key: Int) {
    var downloading by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    var history by remember(key) { mutableStateOf<List<JsonObject>>(emptyList()) }
    // 下载哈希 → 入库记录（status: 成功/失败；没有记录 = 等待入库）
    var transfers by remember(key) { mutableStateOf<Map<String, JsonObject>>(emptyMap()) }
    var loading by remember(key) { mutableStateOf(true) }
    var error by remember(key) { mutableStateOf<String?>(null) }
    var msg by remember(key) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        try {
            val dl = api.listDownloading()
            val h = api.listDownloadHistory()
            val t = if (h.isNotEmpty()) api.transferMapByDownloadHash() else emptyMap()
            downloading = dl; history = h; transfers = t; error = null
        } catch (e: Exception) {
            error = e.message ?: "加载失败"
        } finally {
            loading = false
        }
    }

    LaunchedEffect(key) {
        refresh()
        while (true) { delay(5000); refresh() }
    }

    Column(Modifier.fillMaxSize()) {
        msg?.let {
            Text(
                it,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary, fontSize = 13.sp
            )
        }
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            error != null && history.isEmpty() && downloading.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(error!!, color = Color(0xFF8A8FA3), fontSize = 13.sp)
                }
            else -> LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (downloading.isNotEmpty()) {
                    item {
                        SectionTitle("正在下载 (${downloading.size})")
                    }
                    items(downloading) { t ->
                        DownloadingCard(t, api) { result ->
                            // result=null 成功（刷新列表）；非 null 为失败信息
                            msg = result
                            if (result == null) scope.launch { refresh() }
                        }
                    }
                }
                item { SectionTitle("最近记录") }
                if (history.isEmpty()) {
                    item {
                        Text(
                            "暂无下载记录",
                            Modifier.fillMaxWidth().padding(vertical = 24.dp),
                            color = Color(0xFF8A8FA3), fontSize = 13.sp
                        )
                    }
                } else {
                    items(history) { h -> HistoryCard(h, transfers[h.str("download_hash")]) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(top = 4.dp, bottom = 2.dp),
        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF8A8FA3)
    )
}

/** 正在下载的任务卡片：进度条 + 速度 + 剩余时间 + 暂停/继续/删除操作 */
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
        state.contains("downloading", true) -> "下载中"
        state.contains("seed", true) -> "做种中"
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
        Column(Modifier.padding(14.dp)) {
            Text(
                t.str("title") ?: t.str("name") ?: "未知任务",
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = Color(0xFF1A1C2B)
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress / 100f },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = Color(0xFFE3E5F0),
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "%.1f%%".format(progress),
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "  ·  $stateText",
                    fontSize = 12.sp, color = Color(0xFF8A8FA3)
                )
                Spacer(Modifier.weight(1f))
                t.str("dlspeed")?.takeIf { it.isNotBlank() }?.let {
                    Text("$it  ", fontSize = 12.sp, color = Color(0xFF5C5F70))
                }
                t.str("left_time")?.takeIf { it.isNotBlank() }?.let {
                    Text("剩 $it", fontSize = 12.sp, color = Color(0xFF8A8FA3))
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                t.long("size")?.takeIf { it > 0 }?.let {
                    Text("体积 ${humanSize(it)}", fontSize = 11.sp, color = Color(0xFF8A8FA3))
                }
                Spacer(Modifier.weight(1f))
                if (busy) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    // 暂停 / 继续（按任务状态切换）
                    TextButton(
                        onClick = {
                            t.str("hash")?.takeIf { it.isNotBlank() }?.let { hash ->
                                act {
                                    api.controlDownload(hash, if (paused) "start" else "stop")
                                    if (paused) "已继续" else "已暂停"
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        enabled = !t.str("hash").isNullOrBlank()
                    ) {
                        Text(if (paused) "继续" else "暂停", fontSize = 13.sp)
                    }
                    // 删除（红色，弹确认框）
                    TextButton(
                        onClick = { showDelete = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("删除", fontSize = 13.sp, color = Color(0xFFD24444))
                    }
                }
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { if (!busy) showDelete = false },
            title = { Text("删除下载任务？", color = Color(0xFFD24444), fontWeight = FontWeight.SemiBold) },
            text = {
                Column {
                    Text(
                        t.str("title") ?: t.str("name") ?: "未知任务",
                        fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("已下载的文件会保留，仅从下载器移除任务。", fontSize = 12.sp, color = Color(0xFF8A8FA3))
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val hash = t.str("hash")
                        showDelete = false
                        if (!hash.isNullOrBlank()) {
                            act {
                                api.deleteDownload(hash)
                                "已删除"
                            }
                        }
                    }
                ) { Text("删除", color = Color(0xFFD24444)) }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { showDelete = false }) { Text("取消") }
            }
        )
    }
}

/** 下载记录标题：标题 + 季/集（seasons=Sxx、episodes=Exx） */
private fun historyTitle(h: JsonObject): String {
    val base = h.str("title") ?: h.str("torrent_name") ?: "未知记录"
    val seasonNum = h.str("seasons")?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }?.toIntOrNull()
    val eps = h.str("episodes")?.filter { it.isDigit() }?.takeIf { it.isNotBlank() }
    val parts = buildList {
        if (seasonNum != null && seasonNum > 0) add("第${seasonNum}季")
        if (!eps.isNullOrBlank()) {
            // episodes 可能是 E05 或 E01-E05 / E01+E02
            val nums = Regex("\\d+").findAll(eps).map { it.value.toInt() }.toList()
            when {
                nums.isEmpty() -> {}
                nums.size == 1 -> add("第${nums[0]}集")
                else -> add("第${nums.first()}-${nums.last()}集")
            }
        }
    }
    return if (parts.isEmpty()) base else "$base ${parts.joinToString(" ")}"
}

/**
 * 下载记录卡片，附带媒体库入库结果标签：
 * 成功=绿「已入库」/ 失败=红「入库失败」/ 无记录=灰「等待入库」
 */
@Composable
private fun HistoryCard(h: JsonObject, transfer: JsonObject?) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                historyTitle(h),
                fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = Color(0xFF1A1C2B)
            )
            val sub = listOfNotNull(
                h.str("torrent_site")?.let { "站点：$it" },
                h.str("date")?.takeIf { it.isNotBlank() }?.take(16)?.replace('T', ' '),
            ).joinToString("  ·  ")
            if (sub.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(sub, fontSize = 12.sp, color = Color(0xFF8A8FA3), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ok = transfer?.str("status")?.toBooleanStrictOrNull() ?: false
                val errmsg = transfer?.str("errmsg")
                when {
                    transfer == null -> InboundTag("等待入库", Color(0xFFF0F1F5), Color(0xFF8A8FA3))
                    ok -> InboundTag("媒体库已入库", Color(0xFFE4F5EC), Color(0xFF2E9E5B))
                    else -> InboundTag("入库失败${errmsg?.let { "：$it" } ?: ""}", Color(0xFFFBE9E9), Color(0xFFD24444))
                }
                Spacer(Modifier.weight(1f))
                transfer?.str("dest")?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it.substringAfterLast('/'),
                        fontSize = 11.sp, color = Color(0xFF8A8FA3),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun InboundTag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        fontSize = 11.sp,
        color = fg,
        maxLines = 1,
        modifier = Modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
