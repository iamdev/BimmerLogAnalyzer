package com.bimmerloganalyzer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val BluePrimary = Color(0xFF1E90FF)
val BlueContainer = Color(0xFF003A70)
val GreenAccent = Color(0xFF00E676)
val OrangeAccent = Color(0xFFFF6D00)
val RedAccent = Color(0xFFFF1744)
val SurfaceDark = Color(0xFF121212)
val SurfaceVariant = Color(0xFF1E1E1E)
val OnSurface = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = BluePrimary,
    primaryContainer = BlueContainer,
    secondary = GreenAccent,
    tertiary = OrangeAccent,
    background = SurfaceDark,
    surface = SurfaceVariant,
    onPrimary = Color.White,
    onBackground = OnSurface,
    onSurface = OnSurface,
)

@Composable
fun BimmerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content,
    )
}
