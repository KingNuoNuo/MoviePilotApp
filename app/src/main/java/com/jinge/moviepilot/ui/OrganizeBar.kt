package com.jinge.moviepilot.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.jinge.moviepilot.api.MpApi
import com.jinge.moviepilot.api.OrganizeQueue
import kotlinx.coroutines.delay

/** 有任务在整理时的轮询间隔（进度条要跟上「还剩几个」的变化） */
private const val ORGANIZE_POLL_BUSY_MS = 4_000L

/** 队列空时的轮询间隔：只为发现「新任务开始了」，可以慢一些（接口很轻，但要省电） */
private const val ORGANIZE_POLL_IDLE_MS = 30_000L

/**
 * 全局整理进度（跨板块常驻）。
 *
 * [queue] 为 null 表示「还没查过 / 这次没查到」——**不能**当成「没有任务」，否则进度条会在
 * 接口偶发失败时闪一下。只有 `queue.remaining > 0` 才显示进度条。
 */
class OrganizeProgressState {
    var queue by mutableStateOf<OrganizeQueue?>(null)
        private set

    /**
     * 本轮整理的「总数」（取过程中的峰值）。
     *
     * ⚠️ 不能直接用接口返回的 total 当分母：宿主在作业里所有任务都进入终态时，
     * `JobManager.__pop_job` 会把整个作业从 `_job_view` 移除，`total_hint` 取的是
     * `_task_state_changed_at` 的长度（完成即 pop），所以 `total` 实际等于「还剩」。
     * 拿它当分母会得到「正在整理 4 个文件 ⋯ 还剩 4」且进度条恒为 0%。
     */
    var peak by mutableIntStateOf(0)
        private set

    /** 立即重查一次的信号：提交整理后调用，进度条不必等到下一次轮询才出现 */
    internal var poke by mutableIntStateOf(0)

    fun refreshNow() { poke++ }

    /** 收到一份新的队列快照：维护本轮峰值，队列清空即开始新一轮计数 */
    internal fun update(next: OrganizeQueue) {
        if (next.remaining <= 0) {
            peak = 0
        } else if (next.remaining > peak) {
            // 中途又进来新的文件（下载完自动整理 / 再次提交重整理）
            peak = next.remaining
        }
        queue = next
    }
}

/**
 * 轮询宿主整理队列，驱动顶部全局进度条。
 *
 * 只在前台轮询（`repeatOnLifecycle(STARTED)`），切到后台自动停，回来立刻补一轮。
 * 队列里还有任务时 4 秒一轮，空队列 30 秒一轮 —— 这样在任何一个页面待着都能及时看到
 * 「下载完自动整理」「重新整理 N 条」开始与结束。
 */
@Composable
fun rememberOrganizeProgress(api: MpApi): OrganizeProgressState {
    val state = androidx.compose.runtime.remember { OrganizeProgressState() }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(api.netEpoch, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                // 查不到时保留上一次的值（不把「一次失败」显示成「整理完了」）
                runCatching { api.transferQueue() }.getOrNull()?.let { q -> state.update(q) }
                val busy = (state.queue?.remaining ?: 0) > 0
                delay(if (busy) ORGANIZE_POLL_BUSY_MS else ORGANIZE_POLL_IDLE_MS)
            }
        }
    }
    // 提交整理后立刻补一轮
    LaunchedEffect(state.poke) {
        if (state.poke == 0) return@LaunchedEffect
        runCatching { api.transferQueue() }.getOrNull()?.let { q -> state.update(q) }
    }
    return state
}

/**
 * 顶部全局整理进度条（顶栏正下方，四个板块都显示）：
 * 「⟳ 正在整理 6 个文件 ⋯ 还剩 4」+ 底部 3dp 进度条；队列里没有未完成任务时整条收起。
 *
 * 点击跳到「下载」板块看明细（[onClick] 由 MainActivity 切 tab）。
 */
@Composable
fun OrganizeBar(state: OrganizeProgressState, onClick: () -> Unit) {
    val q = state.queue
    val visible = q != null && q.remaining > 0
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        // 收起的动画过程中 q 可能已变成 null，这里兜一层空值
        val info = q ?: OrganizeQueue(0, 0, 0, 0)
        // 分母取本轮峰值（见 OrganizeProgressState.peak 的说明：接口的 total 约等于「还剩」）
        val remaining = info.remaining
        val total = maxOf(state.peak, remaining)
        val fraction = if (total <= 0) 1f else (total - remaining).toFloat() / total
        Column(
            Modifier
                .fillMaxWidth()
                .background(OrganizeBarBg)
                .clickable(onClick = onClick)
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SpinningRefresh()
                Spacer(Modifier.width(7.dp))
                Text("正在整理 $total 个文件", fontSize = 12.sp, color = TextPrimary)
                Spacer(Modifier.weight(1f))
                Text(
                    "还剩 $remaining",
                    fontSize = 12.sp, fontWeight = FontWeight.Medium,
                    color = OrganizeBarText
                )
            }
            // 底部细进度条：完成占比（含失败的），给一个平滑过渡，不要一跳一跳
            val target by animateFloatAsState(
                targetValue = fraction,
                animationSpec = tween(600, easing = FastOutSlowInEasing),
                label = "organizeProgress"
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(OrganizeBarTrack)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(target)
                        .height(3.dp)
                        .background(Color(0xFF5A63D8))
                )
            }
        }
    }
}

/** 转圈的刷新图标：有任务在整理时一直在动，静态图标容易看成「卡住了」 */
@Composable
private fun SpinningRefresh() {
    val transition = rememberInfiniteTransition(label = "organizeSpin")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_400, easing = LinearEasing),
        ),
        label = "organizeAngle"
    )
    Icon(
        Icons.Rounded.Refresh,
        contentDescription = null,
        tint = OrganizeBarText,
        modifier = Modifier
            .size(14.dp)
            .graphicsLayer { rotationZ = angle }
    )
}

/** 进度条浅靛蓝底（比卡片白底醒目，又不抢内容） */
private val OrganizeBarBg = Color(0xFFEDEFFC)

/** 进度条上的文字/图标：主色深一档，12sp 就够看清 */
private val OrganizeBarText = Color(0xFF414BB2)

/** 进度条轨道 */
private val OrganizeBarTrack = Color(0xFFD8DCF6)
