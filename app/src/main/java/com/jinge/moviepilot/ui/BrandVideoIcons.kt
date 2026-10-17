package com.jinge.moviepilot.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 视频平台品牌图形：爱奇艺 / 优酷 / 腾讯视频 / 芒果TV，均按官方图形一比一矢量化（24x24）。
 *
 * - [Iqiyi] 爱奇艺：官方 App 图标（squircle 圆角方块 + 白色 iQIYI/爱奇艺 字形镂空），
 *   对角渐变 #2CEA7D -> #B15AE8，外轮廓 IoU 0.999 / 字形镂空 IoU 0.933。
 * - [Youku] 优酷：官方图形符号（蓝橙双折角 V 形，无底），双色 IoU 均 0.96。
 * - [TencentVideo] 腾讯视频：官方图形符号（橙/绿/蓝三色播放键，无底），三色 IoU 均 0.97。
 * - [Mgtv] 芒果TV：官方图形（squircle 圆角方块 + 白色圆角框内嵌 M 镂空），
 *   垂直渐变 #FF6703 -> #FF3C00，外轮廓 IoU 0.998 / 镂空 IoU 0.917。
 * - [AllNet] 全网（自设计）：主色细线地球（外圆 + 竖经线椭圆 + 三条纬线），单色、跟随 Icon tint 换色，
 *   选中态可做主色实底 + 白描线，与猫眼/豆瓣页签的选中语言一致。
 * - [Movie] 电影（自设计）：紫蓝对角渐变圆角方 + 白色场记板镂空（顶部打板条 + 斜条纹 +
 *   播放三角），代表「电影票房榜」页签；多色渐变不可 tint，页签选中态用 #E8EAFB 浅紫底。
 *
 * 镂空均为**单条 evenodd 路径**；渐变底用 [Brush.linearGradient] 还原。
 * ⚠️ 四个品牌图形均为各平台的注册商标，仅作平台标识使用，版权归其各自所有。
 */
object BrandVideoIcons {

    /**
     * 全网（自设计图形）：24 格细线地球。描边线宽 1.6（约等于 18dp 下 1.2dp 的视觉线宽），
     * 圆角端点；纬线在 ±3.8 处，半弦 = sqrt(9.2² - 3.8²) ≈ 8.38。
     */
    val AllNet: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandAllNet",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = SolidColor(Color(0xFF5A63D8)),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
        ) {
            // 外圆（r = 9.2，圆心 12,12）：右 -> 下 -> 左 -> 上 -> 右，顺时针四段圆弧
            moveTo(21.2f, 12f)
            arcTo(9.2f, 9.2f, 0f, false, true, 12f, 21.2f)
            arcTo(9.2f, 9.2f, 0f, false, true, 2.8f, 12f)
            arcTo(9.2f, 9.2f, 0f, false, true, 12f, 2.8f)
            arcTo(9.2f, 9.2f, 0f, false, true, 21.2f, 12f)
            close()
            // 竖经线椭圆（rx = 4.2）：上 -> 下 -> 上，两段半椭圆
            moveTo(12f, 2.8f)
            arcTo(4.2f, 9.2f, 0f, true, true, 12f, 21.2f)
            arcTo(4.2f, 9.2f, 0f, true, true, 12f, 2.8f)
            close()
            // 赤道 + 南北纬线
            moveTo(2.8f, 12f) ; lineTo(21.2f, 12f)
            moveTo(3.62f, 8.2f) ; lineTo(20.38f, 8.2f)
            moveTo(3.62f, 15.8f) ; lineTo(20.38f, 15.8f)
        }.build()
    }

    /** 电影（自设计图形）：渐变圆角方 + 白色场记板镂空（斜条纹用 evenodd 嵌套子路径回填渐变） */
    val Movie: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandMovie",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = Brush.linearGradient(
                colorStops = arrayOf(0f to Color(0xFF7A5CFF), 1f to Color(0xFF4C6FFF)),
                start = Offset(0f, 0f), end = Offset(24f, 24f),
            ),
            pathFillType = PathFillType.EvenOdd
        ) {
            // 外轮廓：圆角方（r = 5.4），四角圆弧顺时针
            moveTo(5.4f, 0f) ; lineTo(18.6f, 0f)
            arcTo(5.4f, 5.4f, 0f, false, true, 24f, 5.4f) ; lineTo(24f, 18.6f)
            arcTo(5.4f, 5.4f, 0f, false, true, 18.6f, 24f) ; lineTo(5.4f, 24f)
            arcTo(5.4f, 5.4f, 0f, false, true, 0f, 18.6f) ; lineTo(0f, 5.4f)
            arcTo(5.4f, 5.4f, 0f, false, true, 5.4f, 0f)
            close()
            // 场记板顶部打板条（白色镂空区）
            moveTo(3.5f, 4.2f) ; lineTo(20.5f, 4.2f) ; lineTo(20.5f, 8.8f) ; lineTo(3.5f, 8.8f)
            close()
            // 板身（白色镂空区，圆角 1.4）
            moveTo(5.6f, 10.8f) ; lineTo(18.4f, 10.8f)
            arcTo(1.4f, 1.4f, 0f, false, true, 19.8f, 12.2f) ; lineTo(19.8f, 18.2f)
            arcTo(1.4f, 1.4f, 0f, false, true, 18.4f, 19.6f) ; lineTo(5.6f, 19.6f)
            arcTo(1.4f, 1.4f, 0f, false, true, 4.2f, 18.2f) ; lineTo(4.2f, 12.2f)
            arcTo(1.4f, 1.4f, 0f, false, true, 5.6f, 10.8f)
            close()
            // 打板条上的斜条纹 × 3（嵌套在镂空区里 → evenodd 奇偶回填为渐变色）
            moveTo(6.8f, 4.2f) ; lineTo(9.0f, 4.2f) ; lineTo(7.4f, 8.8f) ; lineTo(5.2f, 8.8f) ; close()
            moveTo(11.2f, 4.2f) ; lineTo(13.4f, 4.2f) ; lineTo(11.8f, 8.8f) ; lineTo(9.6f, 8.8f) ; close()
            moveTo(15.6f, 4.2f) ; lineTo(17.8f, 4.2f) ; lineTo(16.2f, 8.8f) ; lineTo(14.0f, 8.8f) ; close()
            // 板身中央的播放三角（同样回填为渐变色）
            moveTo(10.6f, 13.1f) ; lineTo(15.2f, 15.2f) ; lineTo(10.6f, 17.3f) ; close()
        }.build()
    }

    /** 爱奇艺官方图形（对角渐变 squircle + 白色字形镂空） */
    val Iqiyi: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandIqiyi",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = Brush.linearGradient(
                colorStops = arrayOf(0f to Color(0xFF2CEA7D), 0.2f to Color(0xFF16E06B), 0.4f to Color(0xFF11CA76), 0.6f to Color(0xFF2AA79D), 0.8f to Color(0xFF7475CF), 1f to Color(0xFFB15AE8)),
                start = Offset(0f, 0f), end = Offset(24f, 24f),
            ),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(4.8f, 0f) ; lineTo(19.2f, 0f) ; lineTo(20.7f, 0.3f) ; lineTo(22f, 0.9f)
            lineTo(22.6f, 1.4f) ; lineTo(23.2f, 2.2f) ; lineTo(23.7f, 3.3f) ; lineTo(24f, 4.8f)
            lineTo(24f, 19.2f) ; lineTo(23.7f, 20.7f) ; lineTo(23.1f, 22f) ; lineTo(22.6f, 22.6f)
            lineTo(21.8f, 23.2f) ; lineTo(20.7f, 23.7f) ; lineTo(19.2f, 24f) ; lineTo(4.8f, 24f)
            lineTo(3.3f, 23.7f) ; lineTo(2f, 23.1f) ; lineTo(1.4f, 22.6f) ; lineTo(0.8f, 21.8f)
            lineTo(0.3f, 20.7f) ; lineTo(0f, 19.2f) ; lineTo(0f, 4.8f) ; lineTo(0.3f, 3.3f)
            lineTo(0.9f, 2f) ; lineTo(1.4f, 1.4f) ; lineTo(2.2f, 0.8f) ; lineTo(3.3f, 0.3f)
            close() ; moveTo(3.9f, 5.8f) ; lineTo(4.4f, 5.9f) ; lineTo(4.7f, 6.2f)
            lineTo(4.8f, 6.8f) ; lineTo(4.3f, 7.2f) ; lineTo(3.7f, 7.1f) ; lineTo(3.4f, 6.8f)
            lineTo(3.4f, 6.3f) ; lineTo(3.7f, 5.9f) ; close() ; moveTo(8f, 5.8f)
            lineTo(8.9f, 5.9f) ; lineTo(9.8f, 6.4f) ; lineTo(10.3f, 6.8f) ; lineTo(10.7f, 7.4f)
            lineTo(10.9f, 8.1f) ; lineTo(10.9f, 8.9f) ; lineTo(10.7f, 9.5f) ; lineTo(10.8f, 9.9f)
            lineTo(11.2f, 10.3f) ; lineTo(10.6f, 11.1f) ; lineTo(10.2f, 10.9f) ; lineTo(9.9f, 10.7f)
            lineTo(9.5f, 10.8f) ; lineTo(8.7f, 11.1f) ; lineTo(7.7f, 11.1f) ; lineTo(6.8f, 10.7f)
            lineTo(6.1f, 10.1f) ; lineTo(5.6f, 9.3f) ; lineTo(5.5f, 8.8f) ; lineTo(5.5f, 8.1f)
            lineTo(5.8f, 7.2f) ; lineTo(6.7f, 6.3f) ; lineTo(7.5f, 5.9f) ; close()
            moveTo(8.1f, 6.8f) ; lineTo(7.4f, 7f) ; lineTo(6.9f, 7.5f) ; lineTo(6.7f, 8f)
            lineTo(6.7f, 8.9f) ; lineTo(7f, 9.6f) ; lineTo(7.5f, 9.9f) ; lineTo(7.8f, 10f)
            lineTo(8.6f, 10f) ; lineTo(9.2f, 9.8f) ; lineTo(9.7f, 9.2f) ; lineTo(9.8f, 9f)
            lineTo(9.8f, 8f) ; lineTo(9.4f, 7.3f) ; lineTo(8.7f, 6.9f) ; close() ; moveTo(12f, 5.9f)
            lineTo(13.1f, 5.9f) ; lineTo(13.1f, 11.1f) ; lineTo(12f, 11.1f) ; close()
            moveTo(14f, 5.9f) ; lineTo(15.1f, 5.9f) ; lineTo(15.7f, 6.8f) ; lineTo(16.3f, 7.9f)
            lineTo(16.7f, 7.4f) ; lineTo(17.4f, 6.1f) ; lineTo(17.6f, 5.9f) ; lineTo(18.8f, 5.9f)
            lineTo(18.7f, 6.4f) ; lineTo(17.7f, 7.9f) ; lineTo(17.1f, 9f) ; lineTo(17f, 11.1f)
            lineTo(15.8f, 11.1f) ; lineTo(15.7f, 9f) ; lineTo(14.4f, 6.9f) ; lineTo(14f, 6.2f)
            close() ; moveTo(19.5f, 5.9f) ; lineTo(20.6f, 5.9f) ; lineTo(20.6f, 11.1f)
            lineTo(19.4f, 11.1f) ; lineTo(19.4f, 6.1f) ; close() ; moveTo(3.5f, 7.9f)
            lineTo(4.7f, 7.9f) ; lineTo(4.7f, 11.1f) ; lineTo(3.5f, 11.1f) ; close()
            moveTo(11.7f, 12.5f) ; lineTo(12.7f, 12.5f) ; lineTo(12.7f, 12.9f)
            lineTo(14.7f, 12.9f) ; lineTo(14.7f, 13.5f) ; lineTo(13f, 13.5f) ; lineTo(13.3f, 13.8f)
            lineTo(14f, 14.1f) ; lineTo(14.6f, 14.2f) ; lineTo(14.6f, 14.8f) ; lineTo(13.5f, 14.6f)
            lineTo(12.8f, 14.2f) ; lineTo(12.2f, 13.8f) ; lineTo(11.5f, 14.3f)
            lineTo(10.5f, 14.7f) ; lineTo(9.8f, 14.8f) ; lineTo(9.8f, 14.2f) ; lineTo(11f, 13.9f)
            lineTo(11.3f, 13.5f) ; lineTo(9.8f, 13.5f) ; lineTo(9.8f, 12.9f) ; lineTo(11.7f, 12.9f)
            close() ; moveTo(16.3f, 12.6f) ; lineTo(17.2f, 12.6f) ; lineTo(17.2f, 13f)
            lineTo(19.3f, 13f) ; lineTo(19.3f, 12.6f) ; lineTo(20.2f, 12.6f) ; lineTo(20.2f, 13f)
            lineTo(20.9f, 13f) ; lineTo(20.9f, 13.6f) ; lineTo(20.2f, 13.6f) ; lineTo(20.2f, 14.2f)
            lineTo(19.3f, 14.2f) ; lineTo(19.3f, 13.6f) ; lineTo(17.2f, 13.6f)
            lineTo(17.2f, 14.2f) ; lineTo(16.3f, 14.2f) ; lineTo(16.3f, 13.6f)
            lineTo(15.6f, 13.6f) ; lineTo(15.6f, 13f) ; lineTo(16.2f, 12.9f) ; close()
            moveTo(8.1f, 12.6f) ; lineTo(8.4f, 12.6f) ; lineTo(8.5f, 13.1f) ; lineTo(8.3f, 13.2f)
            lineTo(5.2f, 13.3f) ; lineTo(3.8f, 13.3f) ; lineTo(3.8f, 12.7f) ; close()
            moveTo(4.1f, 13.6f) ; lineTo(4.9f, 13.6f) ; lineTo(5f, 13.9f) ; lineTo(5.6f, 13.9f)
            lineTo(5.7f, 13.6f) ; lineTo(6.5f, 13.6f) ; lineTo(6.5f, 14f) ; lineTo(7.2f, 14f)
            lineTo(7.2f, 13.6f) ; lineTo(8f, 13.6f) ; lineTo(8.1f, 13.9f) ; lineTo(8.7f, 14f)
            lineTo(8.8f, 14.1f) ; lineTo(8.8f, 14.8f) ; lineTo(8f, 14.8f) ; lineTo(8f, 14.5f)
            lineTo(4.2f, 14.5f) ; lineTo(4.2f, 14.7f) ; lineTo(3.4f, 14.7f) ; lineTo(3.4f, 14f)
            lineTo(4.1f, 14f) ; close() ; moveTo(15.9f, 14.6f) ; lineTo(20.2f, 14.6f)
            lineTo(20.5f, 14.9f) ; lineTo(20.5f, 15.6f) ; lineTo(20.1f, 15.9f)
            lineTo(17.1f, 17.4f) ; lineTo(16.9f, 17.4f) ; lineTo(16.9f, 17.7f)
            lineTo(20.6f, 17.7f) ; lineTo(20.6f, 18.4f) ; lineTo(16.2f, 18.4f) ; lineTo(15.8f, 18f)
            lineTo(15.8f, 17.3f) ; lineTo(16.3f, 17f) ; lineTo(19.5f, 15.4f) ; lineTo(19.4f, 15.3f)
            lineTo(15.9f, 15.2f) ; close() ; moveTo(4.5f, 14.8f) ; lineTo(5.3f, 14.8f)
            lineTo(5.3f, 15f) ; lineTo(8.5f, 15f) ; lineTo(8.5f, 15.6f) ; lineTo(5.2f, 15.7f)
            lineTo(5.2f, 15.9f) ; lineTo(8.2f, 15.9f) ; lineTo(8.2f, 16f) ; lineTo(8.2f, 16.6f)
            lineTo(7.7f, 17.4f) ; lineTo(7.6f, 17.5f) ; lineTo(8.5f, 17.7f) ; lineTo(8.5f, 18.4f)
            lineTo(7.8f, 18.4f) ; lineTo(6.8f, 18.2f) ; lineTo(6.2f, 18.2f) ; lineTo(5.2f, 18.4f)
            lineTo(4.4f, 18.4f) ; lineTo(4.4f, 17.7f) ; lineTo(5.5f, 17.5f) ; lineTo(5f, 16.9f)
            lineTo(5f, 16.8f) ; lineTo(5.9f, 16.8f) ; lineTo(6.5f, 17.2f) ; lineTo(6.9f, 17.1f)
            lineTo(7.2f, 16.7f) ; lineTo(7.3f, 16.4f) ; lineTo(5f, 16.5f) ; lineTo(4.2f, 17.8f)
            lineTo(3.8f, 18.3f) ; lineTo(3.2f, 17.9f) ; lineTo(3.8f, 16.9f) ; lineTo(4.2f, 15.9f)
            lineTo(4.3f, 15.6f) ; lineTo(3.5f, 15.6f) ; lineTo(3.5f, 15f) ; lineTo(4.5f, 15f)
            close() ; moveTo(9.5f, 15f) ; lineTo(14.9f, 15f) ; lineTo(14.9f, 15.6f)
            lineTo(14.5f, 15.6f) ; lineTo(14.5f, 17.6f) ; lineTo(14.2f, 18.2f)
            lineTo(13.8f, 18.4f) ; lineTo(13.4f, 17.9f) ; lineTo(13.7f, 17.2f)
            lineTo(13.7f, 15.7f) ; lineTo(9.5f, 15.6f) ; close() ; moveTo(10f, 16f)
            lineTo(12.9f, 16f) ; lineTo(13.1f, 16.2f) ; lineTo(13.1f, 17.8f) ; lineTo(12.8f, 18.1f)
            lineTo(10.2f, 18.1f) ; lineTo(9.8f, 17.8f) ; lineTo(9.8f, 16.3f) ; close()
            moveTo(10.6f, 16.5f) ; lineTo(10.6f, 17.5f) ; lineTo(12.4f, 17.5f)
            lineTo(12.4f, 16.5f) ; close()
        }.build()
    }

    /** 优酷官方图形（蓝橙双折角） */
    val Youku: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandYouku",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color(0xFF00A8F0))) {
            moveTo(5.6f, 0f) ; lineTo(7.4f, 0f) ; lineTo(9.1f, 0.7f) ; lineTo(14f, 3.5f)
            lineTo(18.6f, 6.1f) ; lineTo(20.3f, 7.4f) ; lineTo(21.4f, 8.6f) ; lineTo(22.1f, 10.2f)
            lineTo(22.2f, 11f) ; lineTo(22.2f, 13f) ; lineTo(21.7f, 14.7f) ; lineTo(20.7f, 16.3f)
            lineTo(19.4f, 17.3f) ; lineTo(18.4f, 17.2f) ; lineTo(13.8f, 14.5f) ; lineTo(8.9f, 11.7f)
            lineTo(6f, 10f) ; lineTo(3.3f, 8.4f) ; lineTo(2.3f, 7f) ; lineTo(1.8f, 5.6f)
            lineTo(1.8f, 3.9f) ; lineTo(2.5f, 2.1f) ; lineTo(3.3f, 1.2f) ; lineTo(4.2f, 0.5f)
            close()
        }.path(fill = SolidColor(Color(0xFFF07800))) {
            moveTo(9.1f, 11.7f) ; lineTo(11.7f, 13.1f) ; lineTo(14.7f, 14.9f) ; lineTo(18.9f, 17.3f)
            lineTo(18.9f, 17.7f) ; lineTo(15.8f, 19.4f) ; lineTo(11.6f, 21.9f) ; lineTo(8.4f, 23.6f)
            lineTo(7.4f, 24f) ; lineTo(5.6f, 24f) ; lineTo(3.9f, 23.3f) ; lineTo(3f, 22.6f)
            lineTo(1.9f, 20.8f) ; lineTo(1.8f, 20.1f) ; lineTo(1.8f, 18.4f) ; lineTo(2.5f, 16.6f)
            lineTo(3.3f, 15.6f) ; lineTo(6.3f, 13.8f) ; lineTo(8.9f, 12.3f) ; close()
        }.build()
    }

    /** 腾讯视频官方图形（三色播放键） */
    val TencentVideo: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandTencentVideo",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color(0xFF009BFE))) {
            moveTo(6.6f, 1.2f) ; lineTo(8.5f, 1.2f) ; lineTo(11.2f, 2.1f) ; lineTo(15f, 3.9f)
            lineTo(18f, 5.7f) ; lineTo(20.8f, 7.8f) ; lineTo(22.6f, 9.5f) ; lineTo(23.7f, 10.7f)
            lineTo(24f, 11.2f) ; lineTo(24f, 12.6f) ; lineTo(23.4f, 13.8f) ; lineTo(21.8f, 15.5f)
            lineTo(19.2f, 17.4f) ; lineTo(16.9f, 18.9f) ; lineTo(13.8f, 20.7f)
            lineTo(11.1f, 21.9f) ; lineTo(9f, 22.6f) ; lineTo(8.2f, 22.8f) ; lineTo(6.8f, 22.8f)
            lineTo(5.8f, 22.4f) ; lineTo(4.8f, 21.1f) ; lineTo(4.6f, 20.2f) ; lineTo(9.2f, 18.8f)
            lineTo(13.2f, 16.8f) ; lineTo(15.9f, 15.2f) ; lineTo(17.7f, 13.8f)
            lineTo(18.6f, 12.9f) ; lineTo(18.8f, 12.5f) ; lineTo(18.8f, 11.4f)
            lineTo(17.8f, 10.2f) ; lineTo(15.8f, 8.7f) ; lineTo(13.2f, 7.2f) ; lineTo(10.9f, 6f)
            lineTo(7.2f, 4.5f) ; lineTo(4.6f, 3.8f) ; lineTo(4.8f, 2.9f) ; lineTo(5.7f, 1.7f)
            close()
        }.path(fill = SolidColor(Color(0xFF43E603))) {
            moveTo(4.6f, 3.8f) ; lineTo(5.8f, 3.9f) ; lineTo(8.8f, 5f) ; lineTo(12.4f, 6.6f)
            lineTo(15.3f, 8.2f) ; lineTo(17.8f, 10.1f) ; lineTo(18.8f, 11.1f) ; lineTo(18.9f, 11.4f)
            lineTo(18.9f, 12.5f) ; lineTo(18.4f, 13.2f) ; lineTo(17.2f, 14.4f) ; lineTo(15f, 15.9f)
            lineTo(12.3f, 17.4f) ; lineTo(9.2f, 18.9f) ; lineTo(6.1f, 20f) ; lineTo(4.6f, 20.2f)
            lineTo(4.3f, 19.5f) ; lineTo(3.9f, 16.6f) ; lineTo(3.8f, 14.7f) ; lineTo(3.8f, 9.2f)
            lineTo(4f, 6f) ; lineTo(4.5f, 3.9f) ; close() ; moveTo(7f, 8.1f) ; lineTo(6.8f, 8.6f)
            lineTo(6.6f, 9.6f) ; lineTo(6.6f, 14.2f) ; lineTo(6.9f, 15.8f) ; lineTo(8.2f, 15.6f)
            lineTo(12f, 13.8f) ; lineTo(14.1f, 12.5f) ; lineTo(14.2f, 11.7f) ; lineTo(12.9f, 10.7f)
            lineTo(8.8f, 8.6f) ; lineTo(7.5f, 8.1f) ; close()
        }.path(fill = SolidColor(Color(0xFFFE8801))) {
            moveTo(2.7f, 3.6f) ; lineTo(4.5f, 3.6f) ; lineTo(4.3f, 4.8f) ; lineTo(3.9f, 8.1f)
            lineTo(3.8f, 12.6f) ; lineTo(4f, 17.4f) ; lineTo(4.5f, 19.8f) ; lineTo(4.5f, 20.2f)
            lineTo(4.2f, 20.4f) ; lineTo(2.9f, 20.4f) ; lineTo(1.8f, 20f) ; lineTo(1.1f, 19f)
            lineTo(0.4f, 17.4f) ; lineTo(0.1f, 15.6f) ; lineTo(0f, 14f) ; lineTo(0f, 10.1f)
            lineTo(0.3f, 7.4f) ; lineTo(0.9f, 5.1f) ; lineTo(1.9f, 3.9f) ; close()
        }.build()
    }

    /** 芒果TV官方图形（垂直渐变 squircle + 白色 M 镂空） */
    val Mgtv: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandMgtv",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = Brush.linearGradient(
                colorStops = arrayOf(0f to Color(0xFFFF6703), 0.2f to Color(0xFFFF6209), 0.4f to Color(0xFFFF5D0F), 0.6f to Color(0xFFFF520A), 0.8f to Color(0xFFFF4503), 1f to Color(0xFFFF3C00)),
                start = Offset(0f, 0f), end = Offset(0f, 24f),
            ),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(4.6f, 0f) ; lineTo(19.2f, 0f) ; lineTo(20.8f, 0.5f) ; lineTo(22.4f, 1.6f)
            lineTo(23.5f, 3.2f) ; lineTo(24f, 4.6f) ; lineTo(24f, 19.2f) ; lineTo(23.5f, 20.8f)
            lineTo(22.4f, 22.4f) ; lineTo(20.8f, 23.5f) ; lineTo(19.4f, 24f) ; lineTo(4.8f, 24f)
            lineTo(3.2f, 23.5f) ; lineTo(1.6f, 22.4f) ; lineTo(0.5f, 20.8f) ; lineTo(0f, 19.4f)
            lineTo(0f, 4.8f) ; lineTo(0.5f, 3.2f) ; lineTo(1.6f, 1.6f) ; lineTo(3.2f, 0.5f)
            close() ; moveTo(7.8f, 5.1f) ; lineTo(9.7f, 5.1f) ; lineTo(17.6f, 7.4f)
            lineTo(17.6f, 8.1f) ; lineTo(14.8f, 7.4f) ; lineTo(10.4f, 6.2f) ; lineTo(8.8f, 6.2f)
            lineTo(7.8f, 7.1f) ; lineTo(7.6f, 7.6f) ; lineTo(7.6f, 15.7f) ; lineTo(8.1f, 16.8f)
            lineTo(8.8f, 17.1f) ; lineTo(10.4f, 17.1f) ; lineTo(15.9f, 16.6f) ; lineTo(16.9f, 16.1f)
            lineTo(17.6f, 15.2f) ; lineTo(17.8f, 14.5f) ; lineTo(17.8f, 10.6f)
            lineTo(15.5f, 15.4f) ; lineTo(13.6f, 15.5f) ; lineTo(11.3f, 9.5f) ; lineTo(11.1f, 15.7f)
            lineTo(10.9f, 15.9f) ; lineTo(9.7f, 15.9f) ; lineTo(9.2f, 15.4f) ; lineTo(9.2f, 7.9f)
            lineTo(9.7f, 7.4f) ; lineTo(11.3f, 7.6f) ; lineTo(12.7f, 8.1f) ; lineTo(14.6f, 13.2f)
            lineTo(15.2f, 12.5f) ; lineTo(16.8f, 9.2f) ; lineTo(18.5f, 9.7f) ; lineTo(18.7f, 10.1f)
            lineTo(18.7f, 15.7f) ; lineTo(18f, 17.1f) ; lineTo(16.6f, 18.2f) ; lineTo(12.2f, 18.7f)
            lineTo(8.1f, 19.1f) ; lineTo(6.7f, 18.5f) ; lineTo(5.8f, 17.8f) ; lineTo(5.3f, 16.6f)
            lineTo(5.3f, 7.8f) ; lineTo(5.8f, 6.5f) ; lineTo(6.7f, 5.6f) ; close()
        }.build()
    }
}
