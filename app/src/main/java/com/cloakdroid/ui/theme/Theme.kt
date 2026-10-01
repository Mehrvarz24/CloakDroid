package com.cloakdroid.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CloakColorScheme = darkColorScheme(
    background = CloakColors.Background,
    surface = CloakColors.Surface,
    surfaceVariant = CloakColors.Elevated,
    primary = CloakColors.Primary,
    onBackground = CloakColors.TextHigh,
    onSurface = CloakColors.TextHigh,
    onSurfaceVariant = CloakColors.TextMuted,
    outline = CloakColors.Border,
    error = CloakColors.Error,
    secondary = CloakColors.Primary,
    onPrimary = Color.White
)

@Composable
fun CloakDroidTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = CloakColorScheme, content = content)
}
