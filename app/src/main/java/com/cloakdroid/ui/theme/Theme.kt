package com.cloakdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * CloakDroid design language:
 *  - "Soft Dark / Spotify" base palette
 *  - Material 3 Expressive typography (large display headlines)
 *  - Metallic gradient headline brush + glass card + pill badge helpers
 *  - ProxyCloud-inspired status colors & 16dp rounded cards
 */
object CloakColors {
    // Base surfaces
    val Background = Color(0xFF121316)
    val BackgroundDeep = Color(0xFF0A0A0C)
    val Surface = Color(0xFF1E1F25)
    val Elevated = Color(0xFF2A2B32)

    // Brand
    val Primary = Color(0xFF6366F1)
    val PrimaryDeep = Color(0xFF4F52E0)

    // Text
    val TextHigh = Color(0xFFF3F4F6)
    val TextMuted = Color(0xFF9CA3AF)
    val TextFaint = Color(0xFF6B7280)

    // Lines & glass
    val Border = Color(0xFF2D2F39)
    val GlassFill = Color(0x14FFFFFF)      // white ~8%
    val GlassBorder = Color(0x1AFFFFFF)    // white ~10%

    // Status (ProxyCloud-inspired)
    val Success = Color(0xFF22C55E)
    val Error = Color(0xFFEF4444)
    val Warning = Color(0xFFF59E0B)
    val Connecting = Color(0xFF4285F4)

    // Metallic headline gradient (white -> silver)
    val Metallic = listOf(Color(0xFFFFFFFF), Color(0xFFE5E7EB), Color(0xFF9CA3AF))
    // Brand gradient
    val BrandGradient = listOf(Color(0xFF6366F1), Color(0xFF8B5CF6))
}

object CloakBrushes {
    /** Metallic glowing headline gradient. */
    val headline = Brush.linearGradient(CloakColors.Metallic)
    val brand = Brush.linearGradient(CloakColors.BrandGradient)
    /** Subtle top-to-bottom background wash (ProxyCloud style). */
    val background = Brush.verticalGradient(listOf(CloakColors.BackgroundDeep, CloakColors.Background, CloakColors.Surface))
}

private val DarkScheme = darkColorScheme(
    primary = CloakColors.Primary,
    onPrimary = Color.White,
    secondary = Color(0xFF8B5CF6),
    onSecondary = Color.White,
    background = CloakColors.Background,
    onBackground = CloakColors.TextHigh,
    surface = CloakColors.Surface,
    onSurface = CloakColors.TextHigh,
    surfaceVariant = CloakColors.Elevated,
    onSurfaceVariant = CloakColors.TextMuted,
    surfaceContainer = CloakColors.Elevated,
    surfaceContainerHigh = Color(0xFF32343D),
    outline = CloakColors.Border,
    outlineVariant = CloakColors.GlassBorder,
    error = CloakColors.Error,
    onError = Color.White,
    errorContainer = Color(0xFF3B1214),
    onErrorContainer = Color(0xFFFCA5A5),
    primaryContainer = Color(0xFF2A2B60),
    onPrimaryContainer = Color(0xFFD8DAFF),
    tertiary = CloakColors.Success
)

private val LightScheme = lightColorScheme(
    primary = CloakColors.Primary,
    background = Color(0xFFF7F7FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFEEF0F4),
    onBackground = Color(0xFF111318),
    onSurface = Color(0xFF111318),
    onSurfaceVariant = Color(0xFF4B5563),
    outline = Color(0xFFD1D5DB),
    error = CloakColors.Error
)

/** Expressive typography: big display headlines, relaxed body. */
private val CloakTypography = Typography(
    displaySmall = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),   // leading-relaxed
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
)

/** 16dp rounded cards, pill badges (50% corners) — ProxyCloud-style shapes. */
private val CloakShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CloakDroidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = CloakTypography,
        shapes = CloakShapes,
        content = content
    )
}
