package com.jinge.moviepilot.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// MoviePilot v3 Web UI 配色：靛蓝主色 + 浅灰背景 + 白卡片
val IndigoPrimary = Color(0xFF5A63D8)
val IndigoDark = Color(0xFF414BB2)
val BgGray = Color(0xFFF2F4F7)
val CardWhite = Color(0xFFFFFFFF)

private val LightScheme = lightColorScheme(
    primary = IndigoPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E5FB),
    onPrimaryContainer = IndigoDark,
    secondary = Color(0xFF3C4257),
    background = BgGray,
    onBackground = Color(0xFF1A1C2B),
    surface = CardWhite,
    onSurface = Color(0xFF1A1C2B),
    surfaceVariant = Color(0xFFE8EAF2),
    onSurfaceVariant = Color(0xFF5C5F70),
    error = Color(0xFFD84343),
)

@Composable
fun MoviePilotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightScheme,
        content = content
    )
}
