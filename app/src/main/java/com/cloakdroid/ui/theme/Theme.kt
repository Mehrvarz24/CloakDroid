package com.cloakdroid.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloakdroid.R
import com.cloakdroid.ui.settings.Accent
import com.cloakdroid.ui.settings.ThemeController
import com.cloakdroid.ui.settings.ThemeMode

/**
 * CloakDroid design language — "soft warm dark":
 *  - Warm charcoal surfaces (#1A1815 / #232019), cream/caramel accent (#D9A05B)
 *  - Inter font family (Regular / Medium / SemiBold)
 *  - Soft, low-saturation buttons and glass cards
 *  - Runtime-switchable accent palette + light/dark mode via ThemeController
 */

/** One soft, muted accent palette. */
data class AccentPalette(
    val primary: Color,
    val primaryDeep: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val secondary: Color,
    val glow: Color
)

private val Caramel = AccentPalette(
    primary = Color(0xFFD9A05B),
    primaryDeep = Color(0xFFC08A47),
    primaryContainer = Color(0xFF3A2F22),
    onPrimaryContainer = Color(0xFFF2D9BC),
    secondary = Color(0xFF8FA98F),
    glow = Color(0xFFE8C39E)
)

private val Sage = AccentPalette(
    primary = Color(0xFF9DB58E),
    primaryDeep = Color(0xFF86A077),
    primaryContainer = Color(0xFF2A3326),
    onPrimaryContainer = Color(0xFFDCE8D3),
    secondary = Color(0xFFC9A87C),
    glow = Color(0xFFB9CFA9)
)

private val Sky = AccentPalette(
    primary = Color(0xFF8FB0C9),
    primaryDeep = Color(0xFF7A9DB9),
    primaryContainer = Color(0xFF243240),
    onPrimaryContainer = Color(0xFFD6E5F0),
    secondary = Color(0xFFC9B08A),
    glow = Color(0xFFB3CEDF)
)

private val Rose = AccentPalette(
    primary = Color(0xFFC99AA4),
    primaryDeep = Color(0xFFB6848F),
    primaryContainer = Color(0xFF3A2A2E),
    onPrimaryContainer = Color(0xFFF2D8DD),
    secondary = Color(0xFFA9A08F),
    glow = Color(0xFFE3C0C8)
)

fun accentPalette(a: Accent): AccentPalette = when (a) {
    Accent.CARAMEL -> Caramel
    Accent.SAGE -> Sage
    Accent.SKY -> Sky
    Accent.ROSE -> Rose
}

/** Inter — soft geometric humanist sans (OFL). */
val InterFontFamily = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_regular, FontWeight.Bold),
    Font(R.font.inter_regular, FontWeight.ExtraBold)
)

object CloakColors {
    // Dark-mode base surfaces (warm charcoal)
    private val DarkBackground = Color(0xFF1A1815)
    private val DarkBackgroundDeep = Color(0xFF12100D)
    private val DarkSurface = Color(0xFF232019)
    private val DarkElevated = Color(0xFF2C2820)

    // Light-mode base surfaces (warm paper)
    private val LightBackground = Color(0xFFF5F1EA)
    private val LightBackgroundDeep = Color(0xFFEDE7DC)
    private val LightSurface = Color(0xFFFDFBF7)
    private val LightElevated = Color(0xFFEFE9DF)

    /** Whether the dark palette is active. Set by CloakDroidTheme. */
    var isDark by mutableStateOf(true)

    /** Active accent palette. Set by ThemeController / CloakDroidTheme. */
    var accent by mutableStateOf(Caramel)

    val Background get() = if (isDark) DarkBackground else LightBackground
    val BackgroundDeep get() = if (isDark) DarkBackgroundDeep else LightBackgroundDeep
    val Surface get() = if (isDark) DarkSurface else LightSurface
    val Elevated get() = if (isDark) DarkElevated else LightElevated

    val Primary get() = accent.primary
    val PrimaryDeep get() = accent.primaryDeep
    val Secondary get() = accent.secondary

    val TextHigh get() = if (isDark) Color(0xFFF5EFE6) else Color(0xFF232019)
    val TextMuted get() = if (isDark) Color(0xFFA79E90) else Color(0xFF6B6357)
    val TextFaint get() = if (isDark) Color(0xFF7A7264) else Color(0xFF9A9184)

    val Border get() = if (isDark) Color(0xFF353026) else Color(0xFFE0D8CB)
    val GlassFill get() = if (isDark) Color(0x14FFFFFF) else Color(0x0F232019)
    val GlassBorder get() = if (isDark) Color(0x1AFFFFFF) else Color(0x14232019)

    // Status
    val Success = Color(0xFF7FA97A)
    val Error = Color(0xFFD97A6C)
    val Warning = Color(0xFFD9B25B)
    val Connecting = Color(0xFF8FB0C9)

    // Headline gradient — warm white → cream on dark, charcoal on light
    val Metallic
        get() = if (isDark) {
            listOf(Color(0xFFF5EFE6), Color(0xFFE8C39E), Color(0xFFC9A87C))
        } else {
            listOf(Color(0xFF232019), Color(0xFF4A4336), Color(0xFF7A7264))
        }

    // Soft brand gradient: caramel → cream
    val BrandGradient
        get() = if (isDark) {
            listOf(accent.primary, accent.glow)
        } else {
            listOf(accent.primaryDeep, accent.primary)
        }
}

object CloakBrushes {
    /** Soft glowing headline gradient. */
    val headline get() = Brush.linearGradient(CloakColors.Metallic)
    val brand get() = Brush.linearGradient(CloakColors.BrandGradient)
    /** Subtle top-to-bottom background wash. */
    val background get() = Brush.verticalGradient(
        listOf(CloakColors.BackgroundDeep, CloakColors.Background, CloakColors.Background)
    )
}

private fun darkScheme(p: AccentPalette) = darkColorScheme(
    primary = p.primary,
    onPrimary = Color(0xFF231A0E),
    secondary = p.secondary,
    onSecondary = Color(0xFF1A1815),
    background = CloakColors.Background,
    onBackground = CloakColors.TextHigh,
    surface = CloakColors.Surface,
    onSurface = CloakColors.TextHigh,
    surfaceVariant = CloakColors.Elevated,
    onSurfaceVariant = CloakColors.TextMuted,
    surfaceContainer = CloakColors.Elevated,
    surfaceContainerHigh = Color(0xFF353026),
    outline = CloakColors.Border,
    outlineVariant = CloakColors.GlassBorder,
    error = CloakColors.Error,
    onError = Color(0xFF2B120D),
    errorContainer = Color(0xFF3B1A14),
    onErrorContainer = Color(0xFFF2B8A5),
    primaryContainer = p.primaryContainer,
    onPrimaryContainer = p.onPrimaryContainer,
    secondaryContainer = Color(0xFF2E332B),
    onSecondaryContainer = Color(0xFFD9E4D2),
    tertiary = CloakColors.Success
)

private fun lightScheme(p: AccentPalette) = lightColorScheme(
    primary = p.primaryDeep,
    onPrimary = Color(0xFFFFFFFF),
    secondary = p.secondary,
    background = CloakColors.Background,
    onBackground = CloakColors.TextHigh,
    surface = CloakColors.Surface,
    onSurface = CloakColors.TextHigh,
    surfaceVariant = CloakColors.Elevated,
    onSurfaceVariant = CloakColors.TextMuted,
    surfaceContainer = CloakColors.Elevated,
    surfaceContainerHigh = Color(0xFFE5DDCF),
    outline = CloakColors.Border,
    outlineVariant = CloakColors.GlassBorder,
    error = CloakColors.Error,
    primaryContainer = Color(0xFFF2D9BC),
    onPrimaryContainer = Color(0xFF3A2F22),
    secondaryContainer = Color(0xFFDCE8D3),
    onSecondaryContainer = Color(0xFF2A3326),
    tertiary = CloakColors.Success
)

/** Inter typography: gentle weights, generous line heights. */
private val CloakTypography = Typography(
    displaySmall = TextStyle(fontFamily = InterFontFamily, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = InterFontFamily, fontSize = 26.sp, lineHeight = 33.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = InterFontFamily, fontSize = 22.sp, lineHeight = 29.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontFamily = InterFontFamily, fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = InterFontFamily, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontFamily = InterFontFamily, fontSize = 16.sp, lineHeight = 26.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = InterFontFamily, fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = InterFontFamily, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = InterFontFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelMedium = TextStyle(fontFamily = InterFontFamily, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = InterFontFamily, fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp)
)

/** 16dp rounded cards, pill badges. */
private val CloakShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun CloakDroidTheme(
    darkTheme: Boolean = when (ThemeController.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    },
    accent: Accent = ThemeController.accent,
    content: @Composable () -> Unit
) {
    CloakColors.isDark = darkTheme
    CloakColors.accent = accentPalette(accent)
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme(CloakColors.accent) else lightScheme(CloakColors.accent),
        typography = CloakTypography,
        shapes = CloakShapes,
        content = content
    )
}
