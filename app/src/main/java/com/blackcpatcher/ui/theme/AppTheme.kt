package com.blackcpatcher.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val SurfaceDark = Color(0xFF1E1E1E)
val BackgroundDark = Color(0xFF121212)
val SurfaceVariantDark = Color(0xFF2D2D2D)
val AccentBlue = Color(0xFF3B82F6)
val AccentEmerald = Color(0xFF10B981)
val ErrorRed = Color(0xFFEF4444)
val TextPrimary = Color(0xFFF9FAFB)
val TextSecondary = Color(0xFF9CA3AF)

private val AppColorScheme = darkColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlue.copy(alpha = 0.2f),
    onPrimaryContainer = AccentBlue,

    secondary = AccentEmerald,
    onSecondary = Color.White,
    secondaryContainer = AccentEmerald.copy(alpha = 0.2f),
    onSecondaryContainer = AccentEmerald,

    background = BackgroundDark,
    onBackground = TextPrimary,

    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = TextSecondary,

    error = ErrorRed,
    onError = Color.White
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        content = content
    )
}
