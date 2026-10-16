package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 页签胶囊：选中为靛蓝实底白字，未选中白底灰字 + 浅描边。
 * 首页榜单页签与订阅页五个页签共用，样式与设计 token 收敛在这一处。
 * horizontalPadding 供页签多的场景压缩（订阅页 5 个页签需一屏放下）。
 */
@Composable
fun PillTab(
    text: String,
    selected: Boolean,
    horizontalPadding: Dp = 16.dp,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.White)
            .border(
                1.dp,
                if (selected) Color.Transparent else DividerColor,
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) Color.White else TextSecondary
        )
    }
}
