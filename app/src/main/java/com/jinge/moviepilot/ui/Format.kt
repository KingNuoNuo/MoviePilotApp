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

/**
 * 从种子名 / 规格串里认出分辨率，归一成 `2160P` / `1080P` / `720P`，认不出返回 null。
 *
 * ⚠️ 两处必须用词边界 `(?<![0-9A-Z])`：否则 `H264` 里的 `264`、`X264 1080` 里的数字串，
 * 以及 `4K` 出现在 `4Kings` 这类片名里都会误判。
 * 下载页的「规格」标签（`parseSpecs`）与资源列表的「画质偏好」排序都走这一个实现，别再各写一套。
 */
fun resolutionLabel(raw: String?): String? {
    val u = raw?.uppercase() ?: return null
    fun hit(vararg patterns: String) = patterns.any { Regex(it).containsMatchIn(u) }
    return when {
        hit("(?<![0-9A-Z])2160[PI](?![0-9A-Z])", "(?<![0-9A-Z])4K(?![0-9A-Z])") -> "2160P"
        hit("(?<![0-9A-Z])1080[PI](?![0-9A-Z])") -> "1080P"
        hit("(?<![0-9A-Z])720[PI](?![0-9A-Z])") -> "720P"
        else -> null
    }
}

/**
 * 分辨率档位（越小越清晰，供「画质偏好」把所选画质置顶）：
 * 0 = 4K/2160P，1 = 1080P，2 = 720P，3 = 其它 / 认不出。
 *
 * 认不出归 3 而不是归 0：宁可把没写分辨率的排后面，也别把来源不明的资源顶到 4K 前面。
 */
fun resolutionTier(raw: String?): Int = when (resolutionLabel(raw)) {
    "2160P" -> 0
    "1080P" -> 1
    "720P" -> 2
    else -> 3
}
