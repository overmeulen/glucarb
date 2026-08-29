package com.glucarb.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val DarkScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentDark,
    secondary = Accent,
    onSecondary = AccentDark,
    tertiary = Warn,
    background = Bg,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextDim,
    outline = Line,
    outlineVariant = Line,
    error = Color(0xFFE5706B),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E8B57),
    onPrimary = Color.White,
    secondary = Color(0xFF2E8B57),
    background = Color(0xFFF7F8FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEDEFF3),
    outline = Color(0xFFD5D9E0),
)

private val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(14.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

@Composable
fun GlucarbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        shapes = AppShapes,
        content = content,
    )
}
