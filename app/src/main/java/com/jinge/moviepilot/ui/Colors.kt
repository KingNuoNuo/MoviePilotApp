package com.jinge.moviepilot.ui

import androidx.compose.ui.graphics.Color

/**
 * 全局设计 token（颜色）：与 .workbuddy/memory/MEMORY.md 里的设计 token 一一对应。
 * 统一收敛原本散落在 6 个文件里的几十处 `Color(0xFF…)` 字面量，改主题只动这一处。
 *
 * 主色 / 深主色 / 页面底 / 卡片白已经在 [Theme.kt] 定义（IndigoPrimary / IndigoDark /
 * BgGray / CardWhite），这里补齐其余语义色，避免两处重复定义。
 */

// 文字层级
val TextPrimary = Color(0xFF1A1C2B)      // 主文字（标题、正文强调）
val TextSecondary = Color(0xFF5C5F70)    // 次要文字
val TextTertiary = Color(0xFF8A8FA3)     // 灰字（辅助信息、时间、文件名）

// 分隔 / 填充
val DividerColor = Color(0xFFE3E5F0)     // 分隔线、进度条轨道
val FieldFill = Color(0xFFF3F4F8)        // 浅灰填充（输入框底、卡片内浅底）

// 选中 / 强调
val SelectedPill = Color(0xFFE8EAFB)     // 选中胶囊浅靛蓝底（tab 选中、标题徽标）
val SelectedPillText = Color(0xFF414BB2) // 选中胶囊深靛蓝文字（= IndigoDark，语义别名）

// 语义色
val DangerRed = Color(0xFFD24444)        // 危险红（失败、删除、错误）
val SuccessGreen = Color(0xFF2E9E5B)     // 成功绿（已连接、已入库）
val InfoCyan = Color(0xFF2C7A99)         // 信息青（等待整理标签文字）

// InboundTag（下载页状态标签）四组前景/背景
val TagDownloadBg = Color(0xFFE3E5FB)    // 下载中底
val TagWaitBg = Color(0xFFF0F1F5)        // 等待下载底
val TagDoneBg = Color(0xFFE4F5EC)        // 已入库底
val TagFailBg = Color(0xFFFBE9E9)        // 整理失败底
val TagWaitArrangeBg = Color(0xFFE1F1F6) // 等待整理底
