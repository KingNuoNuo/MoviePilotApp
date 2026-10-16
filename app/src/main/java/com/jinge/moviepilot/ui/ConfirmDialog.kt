package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

// 颜色语义已收敛到 ui/Colors.kt 的全局 token（DangerRed / TextPrimary / TextTertiary /
// FieldFill / DividerColor）。这里保留的 SoftPrimary 是弹窗专用的浅主色底，一次性使用。
private val SoftPrimary = Color(0xFFF4F5FE)

/**
 * 弹窗骨架（App 统一弹窗样式）：白卡 20dp 圆角 + 6dp 阴影 + 内边距 22dp，
 * 顶部为「圆形徽标 + 标题」，可选灰底对象名框，中部为调用方内容，右下角为操作按钮行。
 * 所有确认类弹窗（删除 / 重新整理…）都基于它，保证风格一致。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DialogScaffold(
    title: String,
    badgeIcon: ImageVector,
    badgeTint: Color,
    badgeBg: Color,
    onDismiss: () -> Unit,
    busy: Boolean,
    target: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = !busy,
            dismissOnClickOutside = !busy,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 26.dp)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(20.dp),
            shadowElevation = 6.dp
        ) {
            Column(Modifier.fillMaxWidth().padding(22.dp)) {
                // 徽标 + 标题
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(42.dp)
                            .background(badgeBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            badgeIcon,
                            contentDescription = null,
                            tint = badgeTint,
                            modifier = Modifier.size(23.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        title,
                        fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 操作对象名：灰底圆角框，一眼看清处理的是哪一个
                if (!target.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        target,
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FieldFill, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))
                content?.invoke(this)

                Spacer(Modifier.height(20.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

/**
 * 统一风格的删除确认弹窗：白卡 20dp 圆角 + 红色警示图标徽标 + 标题，
 * 待删对象名放灰底圆角框里，下方灰色说明，右下「取消 / 删除」实心按钮。
 * 与 App 内卡片（白底圆角 + 灰字说明）同源，破坏性动作用红色强调。
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    target: String?,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "删除",
    busy: Boolean = false,
) {
    DialogScaffold(
        title = title,
        badgeIcon = Icons.Rounded.WarningAmber,
        badgeTint = DangerRed,
        badgeBg = Color(0xFFFBE9E9),
        onDismiss = onDismiss,
        busy = busy,
        target = target,
        content = {
            Text(
                message,
                fontSize = 12.sp, color = TextTertiary,
                lineHeight = 18.sp
            )
        }
    ) {
        DialogButton(
            text = "取消",
            container = FieldFill,
            content = TextSecondary,
            enabled = !busy,
            onClick = onDismiss
        )
        Spacer(Modifier.width(10.dp))
        DialogButton(
            text = confirmText,
            container = DangerRed,
            content = Color.White,
            enabled = true,
            busy = busy,
            onClick = onConfirm
        )
    }
}

/**
 * 批量重新整理里可选的一天：[key] 为 `yyyy-MM-dd`，[label] 形如「10-15 周四」，
 * [ids] 是当天整理失败的整理记录 id（**按整理记录粒度**，一条记录 = 一集 / 一个媒体）。
 */
data class RedoDay(val key: String, val label: String, val ids: List<Long>) {
    val count: Int get() = ids.size
}

/**
 * 「重新整理」弹窗：先选整理范围（批量时）与目标路径来源，再开始整理。
 *
 * - **整理范围**（批量，[days] 非空时出现）：最近三天有整理失败的日期，可多选；[selectedDays] 为
 *   当前勾选的日期 key 集合，[onSelectAll] 为右上角「全选」回调；
 * - **原有路径**：沿用该整理记录里已识别的媒体信息与原目录匹配规则，不改变目标目录；
 * - **自动匹配路径**：按当前目录规则重新匹配目的目录（[autoPath] 由 /transfer/manual/target-path 预检得到）。
 *
 * [onSubmit] 参数 true 表示选了「自动匹配路径」。自动匹配尚未拿到结果（[matching]）或匹配不到
 * （[autoPath] 为空）时，该选项不可选。批量时按钮文案带上本次提交的条数 [count]（0 条时禁用）。
 */
@Composable
fun RedoTransferDialog(
    autoPath: String?,
    matching: Boolean,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (autoMatch: Boolean) -> Unit,
    target: String? = null,
    days: List<RedoDay> = emptyList(),
    selectedDays: Set<String> = emptySet(),
    onToggleDay: (String) -> Unit = {},
    onSelectAll: (() -> Unit)? = null,
    count: Int? = null,
) {
    val autoAvailable = !matching && !autoPath.isNullOrBlank()
    var autoMatch by remember { mutableStateOf(false) }
    // 匹配失败 / 还在匹配时，回落到「原有路径」
    val chosenAuto = autoMatch && autoAvailable
    val batch = days.isNotEmpty()

    DialogScaffold(
        title = "重新整理",
        badgeIcon = Icons.Rounded.Refresh,
        badgeTint = MaterialTheme.colorScheme.primary,
        badgeBg = Color(0xFFE8EAFB),
        onDismiss = onDismiss,
        busy = busy,
        target = target,
        content = {
            if (batch) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("整理范围", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
                    Spacer(Modifier.weight(1f))
                    if (onSelectAll != null) {
                        Text(
                            "全选",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable(enabled = !busy) { onSelectAll() }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                days.forEachIndexed { i, d ->
                    if (i > 0) Spacer(Modifier.height(8.dp))
                    DayOptionRow(
                        label = d.label,
                        count = d.count,
                        selected = d.key in selectedDays,
                        enabled = !busy,
                        onClick = { onToggleDay(d.key) }
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            Text("目标路径", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
            Spacer(Modifier.height(8.dp))
            PathOptionRow(
                title = "原有路径",
                subtitle = if (batch) "沿用各记录已识别的目录规则" else "沿用记录中的目标存储与目录规则",
                selected = !chosenAuto,
                onClick = { if (!busy) autoMatch = false }
            )
            Spacer(Modifier.height(8.dp))
            PathOptionRow(
                title = "自动匹配路径",
                subtitle = when {
                    matching -> "正在匹配目的目录…"
                    autoAvailable -> "匹配到 $autoPath"
                    else -> "未匹配到可用目录"
                },
                selected = chosenAuto,
                enabled = autoAvailable && !busy,
                onClick = { if (!busy) autoMatch = true }
            )
        }
    ) {
        DialogButton(
            text = "取消",
            container = FieldFill,
            content = TextSecondary,
            enabled = !busy,
            onClick = onDismiss
        )
        Spacer(Modifier.width(10.dp))
        DialogButton(
            text = if (batch) "开始整理 ${count ?: 0} 条" else "开始整理",
            container = MaterialTheme.colorScheme.primary,
            content = Color.White,
            enabled = !batch || (count ?: 0) > 0,
            busy = busy,
            onClick = { onSubmit(chosenAuto) }
        )
    }
}

/**
 * 待人工复核里的一个源文件（一条复核任务）：[taskId] + [operationId] 用于「沿用既定目录」重试，
 * [storage] / [path] 是该文件的来源，用于「自动匹配目录」重新提交整理。
 */
data class ReviewItem(
    val taskId: String,
    val operationId: String,
    val storage: String,
    val path: String,
)

/** 待人工复核里的一组文件：[key] 为宿主计划的目标目录，[label] 为可读的剧名，[items] 为该组文件 */
data class ReviewGroup(val key: String, val label: String, val items: List<ReviewItem>)

/**
 * 「待人工复核」弹窗：这些文件卡在宿主的整理队列里（没有整理历史记录，下载页卡片看不到），
 * 由用户选定分组与目标目录后重新整理。
 *
 * 交互与 [RedoTransferDialog] 完全一致：整理范围（分组多选，方形勾选框，右侧条数）+ 全选，
 * 下面是对目标目录的单选块：
 * - **原有目录**：沿用复核条目里宿主已计划的目标目录（可能仍是连不上的存储）；
 * - **自动匹配目录**：按当前目录规则重新匹配媒体库目录（[autoPath] 由
 *   `/transfer/manual/target-path` 预检得到，形如 `/DSM/media/ · local`）。
 *   这是实测能真正入库的那条路，所以**默认选中**；未匹配到时该项不可选，自动回落到「原有目录」。
 *
 * [onSubmit] 参数 true 表示选了「自动匹配目录」。按钮文案带本次提交的条数 [count]（0 条禁用）。
 */
@Composable
fun ManualReviewDialog(
    target: String?,
    autoPath: String?,
    matching: Boolean,
    busy: Boolean,
    groups: List<ReviewGroup>,
    selectedGroups: Set<String>,
    onToggleGroup: (String) -> Unit,
    onSelectAll: (() -> Unit)? = null,
    count: Int,
    onDismiss: () -> Unit,
    onSubmit: (autoMatch: Boolean) -> Unit,
) {
    val autoAvailable = !matching && !autoPath.isNullOrBlank()
    // 默认走「自动匹配目录」：实测只有这条路能把文件真正整理进媒体库
    // （原有目录沿用宿主计划里的 smb 存储，存储没修好时必然再失败）
    var autoMatch by remember { mutableStateOf(true) }
    val chosenAuto = autoMatch && autoAvailable

    DialogScaffold(
        title = "待人工复核",
        badgeIcon = Icons.Rounded.Refresh,
        badgeTint = MaterialTheme.colorScheme.primary,
        badgeBg = Color(0xFFE8EAFB),
        onDismiss = onDismiss,
        busy = busy,
        target = target,
        content = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("整理范围", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
                Spacer(Modifier.weight(1f))
                if (onSelectAll != null) {
                    Text(
                        "全选",
                        fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(enabled = !busy) { onSelectAll() }
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            groups.forEachIndexed { i, g ->
                if (i > 0) Spacer(Modifier.height(8.dp))
                DayOptionRow(
                    label = g.label,
                    count = g.items.size,
                    selected = g.key in selectedGroups,
                    enabled = !busy,
                    onClick = { onToggleGroup(g.key) }
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("目标目录", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextTertiary)
            Spacer(Modifier.height(8.dp))
            PathOptionRow(
                title = "原有目录",
                subtitle = "沿用复核条目里宿主计划的目标目录",
                selected = !chosenAuto,
                onClick = { if (!busy) autoMatch = false }
            )
            Spacer(Modifier.height(8.dp))
            PathOptionRow(
                title = "自动匹配目录",
                subtitle = when {
                    matching -> "正在匹配目的目录…"
                    autoAvailable -> "匹配到 $autoPath"
                    else -> "未匹配到可用目录"
                },
                selected = chosenAuto,
                enabled = autoAvailable && !busy,
                onClick = { if (!busy) autoMatch = true }
            )
        }
    ) {
        DialogButton(
            text = "取消",
            container = FieldFill,
            content = TextSecondary,
            enabled = !busy,
            onClick = onDismiss
        )
        Spacer(Modifier.width(10.dp))
        DialogButton(
            text = "开始整理 $count 条",
            container = MaterialTheme.colorScheme.primary,
            content = Color.White,
            enabled = count > 0,
            busy = busy,
            onClick = { onSubmit(chosenAuto) }
        )
    }
}

/** 日期选项行：圆角卡片 + 左侧方形勾选框（多选），选中＝靛蓝描边 + 浅靛蓝底，右侧为该天失败条数 */
@Composable
private fun DayOptionRow(
    label: String,
    count: Int,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) SoftPrimary else Color.White)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) primary else DividerColor,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 方形勾选框：选中＝靛蓝实底 + 白色对勾，未选＝灰描边空心
        val boxShape = RoundedCornerShape(5.dp)
        Box(
            Modifier
                .size(18.dp)
                .clip(boxShape)
                .background(if (selected) primary else Color.White)
                .border(
                    width = if (selected) 0.dp else 1.5.dp,
                    color = if (selected) primary else Color(0xFFC6CADD),
                    shape = boxShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            color = if (enabled) TextPrimary else TextTertiary,
            modifier = Modifier.weight(1f)
        )
        Text(
            "$count 条",
            fontSize = 12.sp,
            color = if (selected) primary else TextTertiary
        )
    }
}

/** 路径来源选项：圆角卡片 + 左侧单选圆点，选中＝靛蓝描边 + 浅靛蓝底 */
@Composable
private fun PathOptionRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) SoftPrimary else Color.White)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else DividerColor,
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 单选圆点：选中＝靛蓝实心 + 白心，未选＝灰描边空心
        Box(
            Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(if (selected) MaterialTheme.colorScheme.primary else Color.White)
                .border(
                    width = if (selected) 0.dp else 1.5.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFC6CADD),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = if (enabled) TextPrimary else TextTertiary
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = TextTertiary,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

/** 弹窗按钮：圆角实心，加载态显示转圈 */
@Composable
private fun DialogButton(
    text: String,
    container: Color,
    content: Color,
    enabled: Boolean,
    busy: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        color = if (enabled && !busy) container else container.copy(alpha = 0.45f),
        shape = shape,
        modifier = Modifier
            .height(40.dp)
            .clip(shape)
            .clickable(enabled = enabled && !busy, onClick = onClick)
    ) {
        Box(
            Modifier.fillMaxHeight().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = content
                )
            } else {
                Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = content)
            }
        }
    }
}
