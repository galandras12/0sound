package com.zerosound.anc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.zerosound.anc.ThemeMode

val WarnYellow = Color(0xFFFFC107)
val DangerRed = Color(0xFFE53935)
val OnGreen = Color(0xFF00C896)

private val Dark = darkColorScheme(
    primary = Color(0xFF00E5A8),
    onPrimary = Color(0xFF00382A),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF171B22),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF2A303A),
    onSurfaceVariant = Color(0xFFB4BCC8),
)

private val Light = lightColorScheme(
    primary = Color(0xFF00A67E),
    onPrimary = Color.White,
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF12161C),
    surface = Color.White,
    onSurface = Color(0xFF12161C),
    surfaceVariant = Color(0xFFD9DEE5),
    onSurfaceVariant = Color(0xFF4A5260),
)

@Composable
fun ZeroSoundTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
