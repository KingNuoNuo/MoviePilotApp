package com.jinge.moviepilot.ui

/**
 * 时间戳精简："2026-10-15 19:07:09" → "10-15 19:07"（去掉年份、精确到分钟）。
 *
 * 榜单的「更新 MM-dd HH:mm」与整理记录的「整理于 MM-dd HH:mm」共用：这两种场景都是短时间内
 * 的流水，年份占宽度又没有信息量。格式不符时返回 null，由调用方决定是否显示这一段。
 */
fun shortStamp(raw: String?): String? {
    val s = raw?.trim().orEmpty()
    if (s.length < 16) return null
    return "${s.substring(5, 10)} ${s.substring(11, 16)}"
}
