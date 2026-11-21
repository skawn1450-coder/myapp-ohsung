package com.ohsung.marketbrief.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ohsung.marketbrief.data.Trend

/** Korean market convention: rising = red, falling = blue. */
@Immutable
data class MarketColors(
    val up: Color,
    val down: Color,
    val flat: Color,
    val upContainer: Color,
    val downContainer: Color,
)

val LocalMarketColors = staticCompositionLocalOf {
    MarketColors(Color.Red, Color.Blue, Color.Gray, Color.White, Color.White)
}

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E4BE0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E8FF),
    onPrimaryContainer = Color(0xFF0E1A5C),
    secondaryContainer = Color(0xFFE3E8FF),
    onSecondaryContainer = Color(0xFF0E1A5C),
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFF0F2F7),
    onSurfaceVariant = Color(0xFF6B7280),
    surfaceContainer = Color(0xFFFFFFFF),
    outline = Color(0xFFD5D9E2),
    outlineVariant = Color(0xFFE6E9F0),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9AACFF),
    onPrimary = Color(0xFF0E1A5C),
    primaryContainer = Color(0xFF26346F),
    onPrimaryContainer = Color(0xFFDDE3FF),
    secondaryContainer = Color(0xFF26346F),
    onSecondaryContainer = Color(0xFFDDE3FF),
    background = Color(0xFF0E1117),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF161B22),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1F2630),
    onSurfaceVariant = Color(0xFF9AA3AF),
    surfaceContainer = Color(0xFF161B22),
    outline = Color(0xFF39414D),
    outlineVariant = Color(0xFF2A313C),
)

private val LightMarket = MarketColors(
    up = Color(0xFFE03131),
    down = Color(0xFF1C64F2),
    flat = Color(0xFF6B7280),
    upContainer = Color(0xFFFFF0F0),
    downContainer = Color(0xFFEEF4FF),
)

private val DarkMarket = MarketColors(
    up = Color(0xFFFF6B6B),
    down = Color(0xFF6EA8FE),
    flat = Color(0xFF9AA3AF),
    upContainer = Color(0xFF3A1F24),
    downContainer = Color(0xFF1B2A45),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 28.sp),
    titleMedium = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 24.sp),
    titleSmall = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 21.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun MarketTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalMarketColors provides if (dark) DarkMarket else LightMarket) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}

@Composable
fun trendColor(trend: Trend): Color {
    val c = LocalMarketColors.current
    return when (trend) {
        Trend.UP -> c.up
        Trend.DOWN -> c.down
        Trend.FLAT -> c.flat
    }
}

@Composable
fun signColor(value: Double): Color {
    val c = LocalMarketColors.current
    return when {
        value > 0 -> c.up
        value < 0 -> c.down
        else -> c.flat
    }
}
