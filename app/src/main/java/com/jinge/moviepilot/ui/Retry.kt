package com.jinge.moviepilot.ui

/**
 * 失败自动重试的退避间隔：第 [attempt] 次（从 0 开始）失败后等多久再试。
 *
 * 为什么必须自己重试：`MpApi.netEpoch` 只在**默认网络变化**时自增一次，而那一刻新网络常常
 * 还没真正可用（DHCP 拿地址、DNS 就绪、路由切换要几秒）。这一次重载必然失败，而 netEpoch
 * 不会再变第二遍 —— 错误就被写死在界面状态里，用户看到的是「切网后报错、切回来也不自动刷新，
 * 得切到别的榜再切回来」。退避重试把这段空窗补上，网络一通就自动恢复。
 *
 * 2s → 4s → 8s → 16s → 30s 封顶：既能在网络恢复后几秒内自愈，服务端长时间不可用时也不会猛打。
 */
internal fun retryDelayMs(attempt: Int): Long =
    (2_000L shl attempt.coerceIn(0, 4)).coerceAtMost(30_000L)
