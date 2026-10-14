package com.jinge.moviepilot.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val TabNames = listOf("搜索", "下载", "订阅", "设置")

/**
 * 统一顶栏：左上「MoviePilot v3 + 服务器连接状态」，右上四个文字入口（搜索/下载/订阅/设置）。
 * 资源列表等子页显示返回箭头。
 */
@Composable
fun MpTopBar(
    online: Boolean?,
    selected: Int,
    onSelect: (Int) -> Unit,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
) {
    val (dotColor, dotText) = when (online) {
        true -> Color(0xFF2E9E5B) to "已连接"
        false -> Color(0xFFD24444) to "未连接"
        null -> Color(0xFFC3C6D4) to "连接中"
    }
    Surface(color = Color.White, shadowElevation = 1.dp) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", tint = Color(0xFF1A1C2B))
                }
                Spacer(Modifier.width(2.dp))
            }
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
            Text(dotText, fontSize = 11.sp, color = Color(0xFF8A8FA3))
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                TabNames.forEachIndexed { idx, name ->
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = if (selected == idx) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected == idx) MaterialTheme.colorScheme.primary else Color(0xFF8A8FA3),
                        modifier = Modifier
                            .clickable { onSelect(idx) }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }
    }
}

/** 底部导航栏：选中项图标带淡靛蓝胶囊高亮，图标/标签随选中变色，带过渡动画 */
@Composable
fun BottomNavBar(selected: Int, onSelect: (Int) -> Unit) {
    val icons = listOf(
        Icons.Default.Search,
        Icons.Default.Download,
        Icons.Default.Subscriptions,
        Icons.Default.Settings,
    )
    val primary = MaterialTheme.colorScheme.primary
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
                val sel = selected == idx
                val pillColor by animateColorAsState(
                    if (sel) Color(0xFFE8EAFB) else Color.Transparent,
                    label = "tabPill"
                )
                val tint by animateColorAsState(
                    if (sel) primary else Color(0xFF9296AB),
                    label = "tabTint"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                ) {
                    Box(
                        Modifier
                            .size(30.dp)
                            .background(pillColor, RoundedCornerShape(15.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icons[idx], null, tint = tint, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        name,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                        color = tint
                    )
                }
            }
        }
    }
}
