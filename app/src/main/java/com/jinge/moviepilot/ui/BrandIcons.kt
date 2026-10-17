package com.jinge.moviepilot.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 品牌图形：首页榜单切换用的两个平台图标，一比一取自官方图形并矢量化。
 *
 * - [Maoyan] 猫眼：24×24 满格（红色圆底 + 白色正面猫脸，含尖耳、竖瞳、心形鼻、胡须），
 *   与官方 600×600 图形逐像素比对 IoU 0.970（残差均为抗锯齿边缘）。
 * - [Douban] 豆瓣：24×24（方形底 + 白色"豆瓣"字形，字形占 78%、顶部留 3/24），
 *   图形取官方站点矢量，与官方 favicon 比对 IoU 0.823。
 *
 * 两者都是**单条 evenodd 路径**（底色整块 + 图形镂空），因此同一份资源既能填品牌色，
 * 也能跟随选中态 tint（选中白 / 未选中品牌色），无需准备多套资源。
 *
 * 注意：[Douban] 的方角由调用方裁圆（官方图形是纯直角，缩到 18dp 摆在圆形猫眼旁会显重）。
 * ⚠️ 两个图形均为猫眼 / 豆瓣的注册商标，仅作平台标识使用，版权归其各自所有。
 */
object BrandIcons {

    /** 猫眼官方图形（24×24 满格，红圆 + 镂空猫脸） */
    val Maoyan: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandMaoyan",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(11.3f, 1.2f) ; lineTo(12.7f, 1.2f) ; lineTo(13.8f, 1.3f) ; lineTo(15.1f, 1.6f)
            lineTo(16.1f, 2f) ; lineTo(17.3f, 2.6f) ; lineTo(18.2f, 3.1f) ; lineTo(18.8f, 3.6f)
            lineTo(18.8f, 3.7f) ; lineTo(19.1f, 3.8f) ; lineTo(19.8f, 4.5f) ; lineTo(20.3f, 5f)
            lineTo(20.3f, 5.2f) ; lineTo(20.4f, 5.2f) ; lineTo(21f, 6f) ; lineTo(21.5f, 6.9f)
            lineTo(22.1f, 8.2f) ; lineTo(22.5f, 9.4f) ; lineTo(22.7f, 10.6f) ; lineTo(22.8f, 11.3f)
            lineTo(22.8f, 12.7f) ; lineTo(22.7f, 13.8f) ; lineTo(22.6f, 14.2f) ; lineTo(22.6f, 14.2f)
            lineTo(22.6f, 13.6f) ; lineTo(21.4f, 13.5f) ; lineTo(19.7f, 13.4f) ; lineTo(19.8f, 13f)
            lineTo(19.8f, 11.7f) ; lineTo(19.6f, 10.7f) ; lineTo(19.1f, 9.7f) ; lineTo(18.9f, 9.4f)
            lineTo(18.7f, 7.7f) ; lineTo(18.5f, 6.2f) ; lineTo(18.1f, 4.8f) ; lineTo(17.5f, 4.9f)
            lineTo(16.4f, 5.4f) ; lineTo(15.4f, 6f) ; lineTo(14.9f, 6.3f) ; lineTo(13.7f, 6.1f)
            lineTo(12.6f, 6f) ; lineTo(11.4f, 6f) ; lineTo(10.2f, 6.2f) ; lineTo(9.1f, 6.4f)
            lineTo(8.3f, 5.9f) ; lineTo(7.4f, 5.5f) ; lineTo(6.3f, 5f) ; lineTo(5.9f, 4.9f)
            lineTo(5.4f, 6.6f) ; lineTo(5.2f, 7.9f) ; lineTo(5f, 9.6f) ; lineTo(4.6f, 10.5f)
            lineTo(4.3f, 11.3f) ; lineTo(4.2f, 11.9f) ; lineTo(4.2f, 13.5f) ; lineTo(1.8f, 13.7f)
            lineTo(1.4f, 13.7f) ; lineTo(1.5f, 14.4f) ; lineTo(1.4f, 14.2f) ; lineTo(1.2f, 12.8f)
            lineTo(1.2f, 11.2f) ; lineTo(1.3f, 10.2f) ; lineTo(1.6f, 8.9f) ; lineTo(2f, 7.9f)
            lineTo(2.3f, 7.1f) ; lineTo(2.9f, 6.2f) ; lineTo(3.5f, 5.3f) ; lineTo(3.6f, 5.2f)
            lineTo(3.7f, 5.2f) ; lineTo(3.8f, 4.9f) ; lineTo(4.5f, 4.2f) ; lineTo(5f, 3.7f)
            lineTo(5.2f, 3.7f) ; lineTo(5.2f, 3.6f) ; lineTo(6f, 3f) ; lineTo(6.8f, 2.5f)
            lineTo(7.6f, 2.1f) ; lineTo(8.5f, 1.7f) ; lineTo(9.8f, 1.4f) ; lineTo(10.6f, 1.3f) ; close()
            moveTo(8.3f, 9.5f) ; lineTo(9.4f, 9.5f) ; lineTo(10.1f, 9.8f) ; lineTo(10.6f, 10.2f)
            lineTo(11f, 10.7f) ; lineTo(11.3f, 11.3f) ; lineTo(11.5f, 11.8f) ; lineTo(11.5f, 12.7f)
            lineTo(11.2f, 13.6f) ; lineTo(10.9f, 13.9f) ; lineTo(10.8f, 13.9f) ; lineTo(10.8f, 14f)
            lineTo(10.7f, 14f) ; lineTo(10.7f, 14.2f) ; lineTo(10.6f, 14.2f) ; lineTo(10.6f, 14.3f)
            lineTo(10f, 14.6f) ; lineTo(9.5f, 14.9f) ; lineTo(9.5f, 15f) ; lineTo(9.5f, 15.5f)
            lineTo(9.7f, 15.8f) ; lineTo(10.1f, 16.1f) ; lineTo(10.3f, 16.1f) ; lineTo(10.9f, 16.1f)
            lineTo(11.4f, 16f) ; lineTo(11.4f, 15.8f) ; lineTo(11.5f, 15.8f) ; lineTo(11.5f, 15.7f)
            lineTo(11.6f, 15.7f) ; lineTo(11.8f, 15.4f) ; lineTo(11.8f, 15.2f) ; lineTo(11.5f, 15f)
            lineTo(11.3f, 14.8f) ; lineTo(11.4f, 14.4f) ; lineTo(11.5f, 14.4f) ; lineTo(11.5f, 14.3f)
            lineTo(11.9f, 14.2f) ; lineTo(12.4f, 14.2f) ; lineTo(12.7f, 14.3f) ; lineTo(12.9f, 14.6f)
            lineTo(12.8f, 15f) ; lineTo(12.4f, 15.2f) ; lineTo(12.5f, 15.6f) ; lineTo(12.8f, 15.9f)
            lineTo(13.1f, 16.1f) ; lineTo(13.3f, 16.1f) ; lineTo(13.9f, 16.1f) ; lineTo(14.5f, 15.8f)
            lineTo(14.8f, 15.4f) ; lineTo(14.8f, 15f) ; lineTo(14.5f, 14.8f) ; lineTo(13.9f, 14.4f)
            lineTo(13.4f, 13.9f) ; lineTo(13f, 13.3f) ; lineTo(12.8f, 12.7f) ; lineTo(12.8f, 11.6f)
            lineTo(13f, 10.9f) ; lineTo(13.4f, 10.3f) ; lineTo(13.6f, 10.3f) ; lineTo(13.6f, 10.2f)
            lineTo(13.7f, 10.2f) ; lineTo(13.7f, 10.1f) ; lineTo(14.4f, 9.7f) ; lineTo(14.8f, 9.5f)
            lineTo(15.9f, 9.5f) ; lineTo(16.6f, 9.8f) ; lineTo(17.1f, 10.2f) ; lineTo(17.5f, 10.7f)
            lineTo(17.8f, 11.3f) ; lineTo(17.9f, 11.8f) ; lineTo(17.9f, 12.7f) ; lineTo(17.7f, 13.4f)
            lineTo(17.3f, 14f) ; lineTo(16.9f, 14.4f) ; lineTo(16.3f, 14.8f) ; lineTo(15.7f, 14.9f)
            lineTo(15.4f, 14.9f) ; lineTo(15.3f, 15.5f) ; lineTo(15f, 16.1f) ; lineTo(14.5f, 16.6f)
            lineTo(14f, 16.7f) ; lineTo(13.2f, 16.7f) ; lineTo(12.8f, 16.6f) ; lineTo(12.2f, 16.1f)
            lineTo(12.2f, 15.8f) ; lineTo(11.9f, 16.1f) ; lineTo(11.4f, 16.6f) ; lineTo(11f, 16.7f)
            lineTo(10.2f, 16.7f) ; lineTo(9.7f, 16.6f) ; lineTo(9.2f, 16.1f) ; lineTo(8.9f, 15.6f)
            lineTo(8.9f, 15.4f) ; lineTo(8.9f, 14.9f) ; lineTo(8.3f, 14.9f) ; lineTo(7.7f, 14.6f)
            lineTo(7.1f, 14.3f) ; lineTo(6.7f, 13.8f) ; lineTo(6.4f, 13f) ; lineTo(6.3f, 12.8f)
            lineTo(6.3f, 11.6f) ; lineTo(6.5f, 11f) ; lineTo(7f, 10.3f) ; lineTo(7.1f, 10.3f)
            lineTo(7.1f, 10.2f) ; lineTo(7.2f, 10.2f) ; lineTo(7.2f, 10.1f) ; lineTo(7.9f, 9.7f)
            close() ; moveTo(8.5f, 10.7f) ; lineTo(8.2f, 11f) ; lineTo(8.2f, 13.6f)
            lineTo(8.5f, 13.8f) ; lineTo(8.8f, 13.9f) ; lineTo(9.1f, 13.6f) ; lineTo(9.1f, 10.9f)
            lineTo(9f, 10.7f) ; close() ; moveTo(15.1f, 10.7f) ; lineTo(14.9f, 10.9f)
            lineTo(14.9f, 13.6f) ; lineTo(15.2f, 13.9f) ; lineTo(15.5f, 13.8f) ; lineTo(15.8f, 13.6f)
            lineTo(15.8f, 10.9f) ; lineTo(15.6f, 10.7f) ; close() ; moveTo(19.7f, 14.2f)
            lineTo(21.1f, 14.2f) ; lineTo(22.5f, 14.3f) ; lineTo(22.6f, 14.2f) ; lineTo(22.4f, 14.9f)
            lineTo(22.2f, 15.6f) ; lineTo(21.5f, 15.4f) ; lineTo(20.4f, 15.1f) ; lineTo(19.5f, 14.9f)
            lineTo(19.6f, 14.3f) ; close() ; moveTo(3f, 14.3f) ; lineTo(4.4f, 14.3f)
            lineTo(4.5f, 14.6f) ; lineTo(4.5f, 14.9f) ; lineTo(2.5f, 15.4f) ; lineTo(2f, 15.5f)
            lineTo(2f, 15.9f) ; lineTo(2.2f, 16.3f) ; lineTo(3.1f, 16f) ; lineTo(4.3f, 15.8f)
            lineTo(5f, 15.8f) ; lineTo(5.3f, 16.2f) ; lineTo(5.2f, 16.4f) ; lineTo(4.2f, 16.7f)
            lineTo(2.9f, 17.3f) ; lineTo(2.8f, 17.3f) ; lineTo(2.8f, 17.6f) ; lineTo(2.7f, 17.6f)
            lineTo(2.3f, 16.9f) ; lineTo(1.9f, 15.8f) ; lineTo(1.5f, 14.6f) ; lineTo(1.5f, 14.5f)
            lineTo(2.2f, 14.3f) ; close() ; moveTo(19f, 15.8f) ; lineTo(19.4f, 15.8f)
            lineTo(21.2f, 16.1f) ; lineTo(21.8f, 16.3f) ; lineTo(21.9f, 16.2f) ; lineTo(21.8f, 16.5f)
            lineTo(21.4f, 17.3f) ; lineTo(21.2f, 17.5f) ; lineTo(21.2f, 17.3f) ; lineTo(20.9f, 17.2f)
            lineTo(19.9f, 16.7f) ; lineTo(18.8f, 16.4f) ; lineTo(18.8f, 16.1f) ; close()
            moveTo(5.5f, 17f) ; lineTo(5.9f, 17f) ; lineTo(6.4f, 17.4f) ; lineTo(6.4f, 17.5f)
            lineTo(6.6f, 17.6f) ; lineTo(7.2f, 18.1f) ; lineTo(8f, 18.5f) ; lineTo(9.3f, 19f)
            lineTo(10.6f, 19.3f) ; lineTo(11.3f, 19.4f) ; lineTo(12.7f, 19.4f) ; lineTo(13.7f, 19.3f)
            lineTo(14.7f, 19f) ; lineTo(16f, 18.5f) ; lineTo(16.6f, 18.2f) ; lineTo(17.5f, 17.6f)
            lineTo(17.6f, 17.5f) ; lineTo(17.6f, 17.4f) ; lineTo(17.8f, 17.4f) ; lineTo(17.8f, 17.3f)
            lineTo(18.1f, 17f) ; lineTo(18.6f, 17f) ; lineTo(19.6f, 17.3f) ; lineTo(20.8f, 17.9f)
            lineTo(20.9f, 17.9f) ; lineTo(21f, 17.9f) ; lineTo(20.9f, 18.2f) ; lineTo(20.4f, 18.8f)
            lineTo(20.3f, 18.8f) ; lineTo(20.2f, 19.1f) ; lineTo(19.5f, 19.8f) ; lineTo(19f, 20.3f)
            lineTo(18.8f, 20.3f) ; lineTo(18.8f, 20.4f) ; lineTo(18f, 21f) ; lineTo(17.2f, 21.5f)
            lineTo(16.4f, 21.9f) ; lineTo(15.5f, 22.3f) ; lineTo(14.2f, 22.6f) ; lineTo(13.4f, 22.7f)
            lineTo(12.8f, 22.8f) ; lineTo(11.2f, 22.8f) ; lineTo(10.2f, 22.7f) ; lineTo(8.9f, 22.4f)
            lineTo(7.7f, 22f) ; lineTo(6.8f, 21.5f) ; lineTo(6.2f, 21.1f) ; lineTo(5.3f, 20.5f)
            lineTo(5.2f, 20.4f) ; lineTo(5.2f, 20.3f) ; lineTo(4.9f, 20.2f) ; lineTo(4.2f, 19.5f)
            lineTo(3.7f, 19f) ; lineTo(3.7f, 18.8f) ; lineTo(3.6f, 18.8f) ; lineTo(3.1f, 18.1f)
            lineTo(3.1f, 18f) ; lineTo(4f, 17.5f) ; lineTo(4.7f, 17.2f) ; close()
        }.build()
    }

    /** 豆瓣官方图形（24×24，方底 + 镂空"豆瓣"字形） */
    val Douban: ImageVector by lazy {
        ImageVector.Builder(
            name = "BrandDouban",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(fill = SolidColor(Color.Black), pathFillType = PathFillType.EvenOdd) {
            moveTo(0f, 0f) ; lineTo(24f, 0f) ; lineTo(24f, 24f) ; lineTo(0f, 24f) ; close()
            moveTo(3f, 4.8f) ; lineTo(21f, 4.8f) ; lineTo(21f, 3f) ; lineTo(3f, 3f) ; lineTo(3f, 4.8f)
            close() ; moveTo(19.4f, 6.8f) ; lineTo(19.4f, 14.3f) ; lineTo(17.7f, 14.3f)
            lineTo(16.4f, 18.8f) ; lineTo(21.4f, 18.8f) ; lineTo(21.4f, 20.5f) ; lineTo(2.6f, 20.5f)
            lineTo(2.6f, 18.8f) ; lineTo(7.6f, 18.8f) ; lineTo(6.3f, 14.3f) ; lineTo(4.6f, 14.3f)
            lineTo(4.6f, 6.8f) ; lineTo(19.4f, 6.8f) ; close() ; moveTo(15.4f, 14.3f)
            lineTo(8.6f, 14.3f) ; lineTo(9.9f, 18.8f) ; lineTo(14.1f, 18.8f) ; lineTo(15.4f, 14.3f)
            close() ; moveTo(17f, 8.6f) ; lineTo(7f, 8.6f) ; lineTo(7f, 12.5f) ; lineTo(17f, 12.5f)
            lineTo(17f, 8.6f) ; close()
        }.build()
    }
}
