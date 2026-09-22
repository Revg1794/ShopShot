package com.fullstackit.shopshot.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Accent = Color(0xFFB68CFF)
private val AccentDark = Color(0xFF7C4DFF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF241148),
    primaryContainer = Color(0xFF3C2A66),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFF9FD8C0),
    background = Color(0xFF101014),
    onBackground = Color(0xFFECE8F2),
    surface = Color(0xFF17171D),
    onSurface = Color(0xFFECE8F2),
    surfaceVariant = Color(0xFF26262F),
    onSurfaceVariant = Color(0xFFC6C2D0),
    outline = Color(0xFF4A4856),
    error = Color(0xFFFFB4A9),
)

private val LightColors = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF23005C),
    secondary = Color(0xFF3F6B59),
    background = Color(0xFFFAF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color.White,
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE8E0F0),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF7A757F),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun ShopShotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        content = content,
    )
}
