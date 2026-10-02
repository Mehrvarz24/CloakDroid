package com.cloakdroid.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cloakdroid.ui.theme.CloakColors

/**
 * Settings screen: theme mode (Dark / Light / System), soft accent palette
 * picker, and an optional custom background image shown dimmed behind content.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ThemeController.themeMode) }
    var accent by remember { mutableStateOf(ThemeController.accent) }
    var hasBgImage by remember { mutableStateOf(ThemeController.backgroundImagePath != null) }
    var killSwitch by remember { mutableStateOf(ThemeController.killSwitchEnabled) }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && ThemeController.setBackgroundImage(context, uri)) {
            hasBgImage = true
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Surface(color = Color.Transparent, contentColor = MaterialTheme.colorScheme.onSurface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCard(title = "Security") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Kill switch",
                            style = MaterialTheme.typography.bodyLarge,
                            color = CloakColors.TextHigh
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "If the proxy connection fails, all browsing stops so your real IP can never leak.",
                            style = MaterialTheme.typography.bodySmall,
                            color = CloakColors.TextMuted
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = killSwitch,
                        onCheckedChange = {
                            killSwitch = it
                            ThemeController.setKillSwitch(it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = CloakColors.Primary,
                            checkedThumbColor = Color(0xFF231A0E)
                        )
                    )
                }
            }

            SettingsCard(title = "Theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { m ->
                        val selected = mode == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) CloakColors.Primary.copy(alpha = 0.22f)
                                    else CloakColors.GlassFill
                                )
                                .border(
                                    1.dp,
                                    if (selected) CloakColors.Primary else CloakColors.GlassBorder,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    mode = m
                                    ThemeController.setThemeMode(m)
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = m.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) CloakColors.Primary else CloakColors.TextMuted
                            )
                        }
                    }
                }
            }

            SettingsCard(title = "Accent color") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Accent.entries.forEach { a ->
                        val selected = accent == a
                        val swatch = when (a) {
                            Accent.CARAMEL -> Color(0xFFD9A05B)
                            Accent.SAGE -> Color(0xFF9DB58E)
                            Accent.SKY -> Color(0xFF8FB0C9)
                            Accent.ROSE -> Color(0xFFC99AA4)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = if (selected) 3.dp else 1.dp,
                                        color = if (selected) CloakColors.TextHigh else CloakColors.GlassBorder,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        accent = a
                                        ThemeController.setAccent(a)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF231A0E)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = a.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.labelSmall,
                                color = CloakColors.TextMuted
                            )
                        }
                    }
                }
            }

            SettingsCard(title = "Background image") {
                Text(
                    text = "Pick an image from your gallery. It is stored app-private and shown dimmed behind content.",
                    style = MaterialTheme.typography.bodySmall,
                    color = CloakColors.TextMuted
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(CloakColors.GlassFill)
                            .border(1.dp, CloakColors.GlassBorder, RoundedCornerShape(12.dp))
                            .clickable { pickImage.launch("image/*") }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            "Choose image…",
                            style = MaterialTheme.typography.labelLarge,
                            color = CloakColors.Primary
                        )
                    }
                    if (hasBgImage) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(CloakColors.GlassFill)
                                .border(1.dp, CloakColors.GlassBorder, RoundedCornerShape(12.dp))
                                .clickable {
                                    ThemeController.clearBackgroundImage()
                                    hasBgImage = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                "Remove",
                                style = MaterialTheme.typography.labelLarge,
                                color = CloakColors.TextMuted
                            )
                        }
                    }
                }
                if (hasBgImage) {
                    Spacer(Modifier.height(12.dp))
                    ThemeController.backgroundBitmap()?.let { bmp ->
                        Image(
                            bitmap = bmp,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .border(1.dp, CloakColors.GlassBorder, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = CloakColors.TextHigh
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}
