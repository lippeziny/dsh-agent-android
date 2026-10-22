package com.lippezin.dshagent.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Paleta "midnight indigo": fundo quase preto azulado, superfícies em camadas e acento índigo/violeta. */
object C {
    val Bg = Color(0xFF0A0B10)
    val Sidebar = Color(0xFF0E1016)
    val Surface = Color(0xFF12141B)
    val Surface2 = Color(0xFF171A22)
    val Surface3 = Color(0xFF1E222C)
    val Border = Color(0xFF232733)
    val BorderStrong = Color(0xFF30364A)
    val Text = Color(0xFFF4F5FA)
    val Text2 = Color(0xFFD2D7E4)
    val Dim = Color(0xFF8A92A6)
    val Accent = Color(0xFF8FA8FF)
    val Accent2 = Color(0xFFB6C4FF)
    val AccentStrong = Color(0xFF6B7FF0)
    val AccentBg = Color(0xFF181F3A)
    val Violet = Color(0xFFA78BFA)
    val Cyan = Color(0xFF67E8F9)
    val Danger = Color(0xFFFF8F85)
    val Ok = Color(0xFF7EE0A8)
    val Code = Color(0xFF090A0F)
    val CodeHeader = Color(0xFF10121A)

    /** Gradiente da marca (índigo → violeta), usado no logo, botão de envio e títulos de destaque. */
    val Brand: Brush = Brush.linearGradient(listOf(Color(0xFF6B7FF0), Color(0xFFA78BFA)))
}

private val DshTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.5.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp)
)

private val DshShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun DshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = Color(0xFF0B1030),
            primaryContainer = Color(0xFF26315F),
            onPrimaryContainer = Color(0xFFDCE3FF),
            secondary = C.Accent2,
            secondaryContainer = C.AccentBg,
            onSecondaryContainer = Color(0xFFDCE3FF),
            tertiary = C.Violet,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Surface,
            onSurface = C.Text,
            surfaceVariant = C.Surface3,
            onSurfaceVariant = C.Dim,
            outline = C.BorderStrong,
            outlineVariant = C.Border,
            error = C.Danger,
            surfaceContainerLowest = C.Bg,
            surfaceContainerLow = C.Surface,
            surfaceContainer = C.Surface2,
            surfaceContainerHigh = Color(0xFF1B1F29),
            surfaceContainerHighest = C.Surface3
        ),
        typography = DshTypography,
        shapes = DshShapes,
        content = content
    )
}
