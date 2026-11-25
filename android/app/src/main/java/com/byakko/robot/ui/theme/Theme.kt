package com.byakko.robot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Palette {
    val Bg = Color(0xFF0B0D10)
    val Surface = Color(0xFF14171C)
    val SurfaceHi = Color(0xFF1C2027)
    val Line = Color(0xFF262B33)
    val Ink = Color(0xFFE8EEF6)
    val Muted = Color(0xFF7D8794)
    val Accent = Color(0xFF7DD3FC)     // hielo
    val Ok = Color(0xFF4ADE80)
    val Warn = Color(0xFFFBBF24)
    val Danger = Color(0xFFFF5C5C)
}

private val scheme = darkColorScheme(
    primary = Palette.Accent,
    onPrimary = Palette.Bg,
    background = Palette.Bg,
    onBackground = Palette.Ink,
    surface = Palette.Surface,
    onSurface = Palette.Ink,
    surfaceVariant = Palette.SurfaceHi,
    onSurfaceVariant = Palette.Muted,
    outline = Palette.Line,
    error = Palette.Danger,
)

private val type = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 6.sp),
    titleSmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.6.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.6.sp),
)

@Composable
fun ByakkoTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
