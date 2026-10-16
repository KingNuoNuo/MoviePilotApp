package com.jinge.moviepilot.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TabNames = listOf("搜索", "下载", "订阅", "设置")

/** 四个主 tab 的图标；顶栏与底部导航共用同一组，保证两处入口看起来是同一个东西 */
private val TabIcons = listOf(
    Icons.Default.Search,
    Icons.Default.Download,
    Icons.Default.Subscriptions,
    Icons.Default.Settings,
)

/**
 * 统一顶栏：左上「MoviePilot v3 + 服务器连接状态」，右上四个图标入口（搜索/下载/订阅/设置）。
 * 图标沿用底部导航的同一套视觉（见 [TabButton]），只是不带文字标签。
 */
@Composable
fun MpTopBar(
    online: Boolean?,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val (dotColor, dotText) = when (online) {
        true -> SuccessGreen to "已连接"
        false -> DangerRed to "未连接"
        null -> Color(0xFFC3C6D4) to "连接中"
    }
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "MoviePilot",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 17.sp, fontWeight = FontWeight.Bold
            )
            Text(
                " v3",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 11.sp, fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(7.dp).background(dotColor, CircleShape))
            Spacer(Modifier.width(4.dp))
            Text(dotText, fontSize = 11.sp, color = TextTertiary)
            Spacer(Modifier.weight(1f))
            // 与底部导航同一套 tab 胶囊；顶栏位置窄，左右留白收到 3dp（底部导航是 12dp）
            Row(verticalAlignment = Alignment.CenterVertically) {
                TabNames.forEachIndexed { idx, name ->
                    TabButton(
                        icon = TabIcons[idx],
                        selected = selected == idx,
                        onClick = { onSelect(idx) },
                        contentDescription = name,
                        hPad = 3.dp,
                    )
                }
            }
        }
    }
}

/** 底部导航栏：四个 tab 与顶栏用同一个 [TabButton]，只是这里带文字标签 */
@Composable
fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = Color.White, shadowElevation = 10.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TabNames.forEachIndexed { idx, name ->
                TabButton(
                    icon = TabIcons[idx],
                    selected = selected == idx,
                    onClick = { onSelect(idx) },
                    label = name,
                    contentDescription = name,
                    hPad = 12.dp,
                )
            }
        }
    }
}

/**
 * 主 tab 按钮（顶栏与底部导航共用，保证两处图标完全同一套视觉）：
 * 30dp 椭圆胶囊 + 20dp 图标，选中＝浅靛蓝底 `#E8EAFB` + 主色图标，未选＝透明底 + 灰图标 `#9296AB`，
 * 颜色带过渡动画；按下时整颗缩到 0.88 由 spring 回弹（轻微过冲），配合水波纹给出即时反馈。
 *
 * @param label 非空时在图标下方显示 11sp 文字（底部导航用）；顶栏只要图标，不传即无文字。
 * @param hPad 左右内边距，决定点击区宽度（顶栏窄用 3dp，底部导航宽松用 12dp）。
 */
@Composable
private fun TabButton(
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    label: String? = null,
    contentDescription: String? = null,
    hPad: Dp = 4.dp,
) {
    val pillColor by animateColorAsState(
        if (selected) SelectedPill else Color.Transparent,
        label = "tabPill"
    )
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else Color(0xFF9296AB),
        label = "tabTint"
    )
    // 按下反馈：缩到 0.88 再弹回（MediumBouncy 会有极轻微的过冲，手感更「活」）
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "tabScale"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .padding(horizontal = hPad, vertical = 5.dp)
    ) {
        Box(
            Modifier
                .size(30.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .background(pillColor, RoundedCornerShape(15.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(20.dp))
        }
        if (label != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = tint
            )
        }
    }
}
