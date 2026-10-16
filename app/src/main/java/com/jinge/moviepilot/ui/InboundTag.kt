package com.jinge.moviepilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 小号状态标签（11sp / 圆角 6dp）：下载状态、工作流启用态等通用胶囊。
 * 供下载页与订阅页共用。
 */
@Composable
fun InboundTag(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 11.sp,
        color = fg,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(bg, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
