package com.sentinel.app.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 哨兵主题色 - 青色+深蓝
private val SentinelPrimary = Color(0xFF00BCD4)
private val SentinelOnPrimary = Color.White
private val SentinelPrimaryContainer = Color(0xFFB2EBF2)
private val SentinelSecondary = Color(0xFF1A237E)
private val SentinelOnSecondary = Color.White
private val SentinelBackground = Color(0xFF0D1B2A)
private val SentinelOnBackground = Color.White
private val SentinelSurface = Color(0xFF1B2838)
private val SentinelOnSurface = Color.White
private val SentinelError = Color(0xFFF44336)

private val DarkColorScheme = darkColorScheme(
    primary = SentinelPrimary,
    onPrimary = SentinelOnPrimary,
    primaryContainer = SentinelPrimaryContainer,
    secondary = SentinelSecondary,
    onSecondary = SentinelOnSecondary,
    background = SentinelBackground,
    onBackground = SentinelOnBackground,
    surface = SentinelSurface,
    onSurface = SentinelOnSurface,
    error = SentinelError,
    onError = Color.White,
    surfaceVariant = Color(0xFF2A3A4A),
    onSurfaceVariant = Color(0xFFB0BEC5)
)

@Composable
fun SentinelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography(),
        content = content
    )
}