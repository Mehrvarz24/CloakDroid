package com.cloakdroid.ui.profiles

import com.cloakdroid.ui.theme.parseTagColor
import com.cloakdroid.data.network.ProxyInputParser
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.UUID

private const val TAB_GENERAL = 0
private const val TAB_NETWORK = 1
private const val TAB_SPOOFING = 2

private const val PROXY_SOCKS5 = "SOCKS5"
private const val PROXY_HTTP = "HTTP"
private const val PROXY_HTTPS = "HTTPS"
private const val PROXY_DIRECT = "DIRECT"

private val ProxyTypeOptions = listOf(PROXY_SOCKS5, PROXY_HTTP, PROXY_HTTPS, PROXY_DIRECT)

/** 3-state WebRTC policy shown as radio buttons in the Spoofing tab. */
private enum class WebRtcPolicyOption(
    val label: String,
    val description: String
) {
    DISABLED(
        "Disabled",
        "WebRTC is removed entirely - strongest protection"
    ),
    PROXY_ONLY(
        "Proxy-only",
        "WebRTC stays usable but host candidates are stripped, so the real IP never leaks"
    ),
    FULL(
        "Full",
        "WebRTC untouched - not recommended behind a proxy"
    );

    companion object {
        fun from(stored: String?, legacyEnabled: Boolean): WebRtcPolicyOption {
            entries.firstOrNull { it.name.equals(stored, ignoreCase = true) }
                ?.let { return it }
            // Fall back to the old boolean toggle for profiles saved before
            // the policy field existed.
            return if (legacyEnabled) FULL else DISABLED
        }
    }
}

private val TagColorOptions = listOf(
    "0xFFE53935", // red
    "0xFFFB8C00", // orange
    "0xFFFDD835", // amber
    "0xFF43A047", // green
    "0xFF00ACC1", // cyan
    "0xFF1E88E5", // blue
    "0xFF8E24AA", // purple
    "0xFFD81B60", // pink
    "0xFF6D4C41", // brown
    "0xFF546E7A"  // blue grey
)

private val CommonUserAgents = listOf(
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Firefox/121.0 Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Safari/605.1.15",
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1"
)

private const val DefaultTagColor = "0xFF1E88E5"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditorScreen(
    profileId: String?,
    viewModel: ProfileViewModel,
    onDone: () -> Unit
) {
    val profiles by viewModel.profiles.collectAsState()
    val testResult by viewModel.testResult.collectAsState()
    val testing by viewModel.testing.collectAsState()

    var selectedTab by rememberSaveable { mutableStateOf(TAB_GENERAL) }

    // General
    var name by rememberSaveable { mutableStateOf("") }
    var tagColor by rememberSaveable { mutableStateOf(DefaultTagColor) }
    var userAgent by rememberSaveable { mutableStateOf(CommonUserAgents.first()) }
    var userAgentMenuExpanded by remember { mutableStateOf(false) }

    // Network
    var proxyType by rememberSaveable { mutableStateOf(PROXY_SOCKS5) }
    var proxyHost by rememberSaveable { mutableStateOf("") }
    var proxyPort by rememberSaveable { mutableStateOf("") }
    var proxyUsername by rememberSaveable { mutableStateOf("") }
    var proxyPassword by rememberSaveable { mutableStateOf("") }

    // Spoofing
    var autoSync by rememberSaveable { mutableStateOf(true) }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var webRtcPolicy by rememberSaveable { mutableStateOf(WebRtcPolicyOption.DISABLED.name) }
    var canvasNoise by rememberSaveable { mutableStateOf(true) }
    var audioNoise by rememberSaveable { mutableStateOf(true) }
    var timezone by rememberSaveable { mutableStateOf("") }
    var locale by rememberSaveable { mutableStateOf("") }
    var screenWText by rememberSaveable { mutableStateOf("") }
    var screenHText by rememberSaveable { mutableStateOf("") }
    var dprText by rememberSaveable { mutableStateOf("") }
    var deviceName by rememberSaveable { mutableStateOf("") }
    var pendingFingerprintHash by rememberSaveable { mutableStateOf("") }
    var generating by remember { mutableStateOf(false) }
    var regenerateTick by remember { mutableStateOf(0) }
    val editorScope = rememberCoroutineScope()

    var loaded by remember(profileId) { mutableStateOf(profileId == null) }

    LaunchedEffect(profileId, profiles) {
        if (loaded) return@LaunchedEffect
        if (profileId == null) return@LaunchedEffect
        val existing = profiles.firstOrNull { it.id == profileId } ?: return@LaunchedEffect
        name = existing.name
        tagColor = existing.tagColor
        userAgent = existing.userAgent
        proxyType = existing.proxyType
        proxyHost = existing.proxyHost ?: ""
        proxyPort = existing.proxyPort?.toString() ?: ""
        proxyUsername = existing.proxyUsername ?: ""
        proxyPassword = existing.proxyPassword ?: ""
        autoSync = existing.autoSyncGeolocation
        latitude = existing.spoofLat?.toString() ?: ""
        longitude = existing.spoofLon?.toString() ?: ""
        webRtcPolicy = WebRtcPolicyOption.from(existing.webrtcPolicy, existing.webrtcEnabled).name
        screenWText = existing.screenW?.toString() ?: ""
        screenHText = existing.screenH?.toString() ?: ""
        dprText = existing.devicePixelRatio?.toString() ?: ""
        deviceName = existing.deviceName ?: ""
        pendingFingerprintHash = existing.fingerprintHash ?: ""
        canvasNoise = existing.canvasNoise
        audioNoise = existing.audioNoise
        timezone = existing.timezoneId
        locale = existing.localeTag
        loaded = true
    }

    val isDirect = proxyType == PROXY_DIRECT

    fun saveAndFinish() {
        val profile = com.cloakdroid.data.local.ProfileEntity(
            id = profileId ?: UUID.randomUUID().toString(),
            name = name.trim().ifEmpty { "Untitled profile" },
            tagColor = tagColor,
            userAgent = userAgent,
            proxyType = proxyType,
            proxyHost = if (isDirect) null else proxyHost.trim().ifEmpty { null },
            proxyPort = if (isDirect) null else proxyPort.toIntOrNull(),
            proxyUsername = if (isDirect) null else proxyUsername.trim().ifEmpty { null },
            proxyPassword = if (isDirect) null else proxyPassword,
            autoSyncGeolocation = autoSync,
            spoofLat = latitude.toDoubleOrNull(),
            spoofLon = longitude.toDoubleOrNull(),
            webrtcEnabled = webRtcPolicy != WebRtcPolicyOption.DISABLED.name,
            webrtcPolicy = webRtcPolicy,
            screenW = screenWText.toIntOrNull(),
            screenH = screenHText.toIntOrNull(),
            devicePixelRatio = dprText.toFloatOrNull(),
            deviceName = deviceName.trim().ifEmpty { null },
            fingerprintHash = pendingFingerprintHash.ifEmpty { null },
            canvasNoise = canvasNoise,
            audioNoise = audioNoise,
            timezoneId = timezone.trim().ifEmpty { "UTC" },
            localeTag = locale.trim().ifEmpty { "en-US" }
        )
        viewModel.save(profile)
        onDone()
    }

    Scaffold(
        modifier = Modifier.background(com.cloakdroid.ui.theme.CloakBrushes.background),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = {
                    Text(if (profileId == null) "New profile" else "Edit profile")
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
                com.cloakdroid.ui.theme.CloakButtons.GradientButton(
                    text = "Save profile",
                    onClick = { saveAndFinish() },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == TAB_GENERAL,
                    onClick = { selectedTab = TAB_GENERAL },
                    text = { Text("General") }
                )
                Tab(
                    selected = selectedTab == TAB_NETWORK,
                    onClick = { selectedTab = TAB_NETWORK },
                    text = { Text("Network") }
                )
                Tab(
                    selected = selectedTab == TAB_SPOOFING,
                    onClick = { selectedTab = TAB_SPOOFING },
                    text = { Text("Spoofing") }
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                when (selectedTab) {
                    TAB_GENERAL -> {
                        SectionTitle(text = "Identity")

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Profile name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        SectionTitle(text = "Tag color")
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TagColorOptions.take(5).forEach { color ->
                                ColorDot(
                                    color = color,
                                    selected = color == tagColor,
                                    onClick = { tagColor = color }
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            TagColorOptions.drop(5).forEach { color ->
                                ColorDot(
                                    color = color,
                                    selected = color == tagColor,
                                    onClick = { tagColor = color }
                                )
                            }
                        }

                        SectionTitle(text = "User-Agent")

                        ExposedDropdownMenuBox(
                            expanded = userAgentMenuExpanded,
                            onExpandedChange = { userAgentMenuExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = userAgent,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Browser fingerprint") },
                                maxLines = 3,
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = userAgentMenuExpanded
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = userAgentMenuExpanded,
                                onDismissRequest = { userAgentMenuExpanded = false }
                            ) {
                                CommonUserAgents.forEach { ua ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = ua,
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        },
                                        onClick = {
                                            userAgent = ua
                                            userAgentMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    TAB_NETWORK -> {
                        SectionTitle(text = "Proxy type")

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ProxyTypeOptions.forEach { type ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = proxyType == type,
                                            onClick = { proxyType = type }
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = proxyType == type,
                                        onClick = { proxyType = type }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = type,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }

                        if (!isDirect) {
                            SectionTitle(text = "Proxy server")

                            OutlinedTextField(
                                value = proxyHost,
                                onValueChange = { value ->
                                    val pasted = ProxyInputParser.parse(value)
                                    if (pasted != null) {
                                        proxyHost = pasted.host
                                        proxyPort = pasted.port.toString()
                                        proxyType = pasted.type.name
                                        proxyUsername = pasted.username.orEmpty()
                                        proxyPassword = pasted.password.orEmpty()
                                    } else {
                                        proxyHost = value
                                    }
                                },
                                label = { Text("Host or proxy URL") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = proxyPort,
                                onValueChange = { proxyPort = it.filter { c -> c.isDigit() } },
                                label = { Text("Port") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = proxyUsername,
                                onValueChange = { proxyUsername = it },
                                label = { Text("Username (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = proxyPassword,
                                onValueChange = { proxyPassword = it },
                                label = { Text("Password (optional)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                modifier = Modifier.fillMaxWidth()
                            )

                            com.cloakdroid.ui.theme.CloakButtons.GradientButton(
                                text = "Test Proxy",
                                onClick = {
                                    viewModel.testProxy(
                                        proxyType,
                                        proxyHost.trim(),
                                        proxyPort.toIntOrNull() ?: 0,
                                        proxyUsername.trim(),
                                        proxyPassword
                                    )
                                },
                                enabled = !testing && proxyHost.isNotBlank() &&
                                    (proxyPort.toIntOrNull() ?: 0) in 1..65535,
                                modifier = Modifier.fillMaxWidth(),
                                height = 48.dp
                            )

                            val result = testResult
                            when {
                                testing -> {
                                    Text(
                                        text = "Testing proxy… this can take a few seconds.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                result == null -> {
                                    Text(
                                        text = "No test run yet.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }

                                result is com.cloakdroid.data.network.ProxyTestResult.Timeout ->
                                    ResultCard(ok = false, message = "The proxy did not respond in time (timeout).")
                                result is com.cloakdroid.data.network.ProxyTestResult.AuthFailure ->
                                    ResultCard(ok = false, message = result.msg)
                                result is com.cloakdroid.data.network.ProxyTestResult.Unsupported ->
                                    ResultCard(ok = false, message = "Unsupported: ${result.msg}")
                                result is com.cloakdroid.data.network.ProxyTestResult.NetworkError ->
                                    ResultCard(ok = false, message = result.msg)
                                result is com.cloakdroid.data.network.ProxyTestResult.Success -> {
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = "Proxy test succeeded",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "Latency: ${result.latencyMs} ms",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "IP: ${result.publicIp}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "Country: ${result.countryCode} • ${result.city}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            Text(
                                                text = "ISP: ${result.isp}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                            result.metadataWarning?.let { warning ->
                                                Text(
                                                    text = "Warning: $warning",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            com.cloakdroid.ui.theme.CloakButtons.GhostButton(
                                                text = "Apply matching timezone, locale & location",
                                                onClick = {
                                                    timezone = result.suggestedTimezoneId
                                                    locale = result.suggestedLocale
                                                    latitude = String.format(
                                                        java.util.Locale.US, "%.4f", result.lat
                                                    )
                                                    longitude = String.format(
                                                        java.util.Locale.US, "%.4f", result.lon
                                                    )
                                                    autoSync = true
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Text(
                                                text = "Fills the Spoofing tab from the proxy's location (${result.suggestedTimezoneId}, ${result.suggestedLocale}).",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "DIRECT: traffic goes out through the default connection, no proxy settings required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    else -> {
                        SectionTitle(text = "Location")

                        ToggleRow(
                            title = "Auto-sync location",
                            subtitle = "Derive latitude and longitude from the current IP",
                            checked = autoSync,
                            onCheckedChange = { autoSync = it }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = latitude,
                                onValueChange = { latitude = it },
                                label = { Text("Latitude") },
                                enabled = !autoSync,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = longitude,
                                onValueChange = { longitude = it },
                                label = { Text("Longitude") },
                                enabled = !autoSync,
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        HorizontalDivider()

                        SectionTitle(text = "Fingerprint")

                        com.cloakdroid.ui.theme.CloakButtons.GradientButton(
                            text = if (generating) "Generating…" else "Generate fingerprint",
                            onClick = {
                                generating = true
                                editorScope.launch {
                                    val identity = viewModel.generateUniqueIdentity()
                                    if (identity != null) {
                                        userAgent = identity.userAgent
                                        screenWText = identity.screenW.toString()
                                        screenHText = identity.screenH.toString()
                                        dprText = identity.devicePixelRatio.toString()
                                        deviceName = identity.deviceName
                                        pendingFingerprintHash = identity.fingerprintHash
                                    }
                                    regenerateTick++
                                    generating = false
                                }
                            },
                            enabled = !generating,
                            modifier = Modifier.fillMaxWidth(),
                            height = 48.dp
                        )
                        if (pendingFingerprintHash.isNotEmpty()) {
                            Text(
                                text = "Fingerprint generated" +
                                    " • unique hash ${pendingFingerprintHash.take(8)}…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Generates a consistent identity (UA, screen, " +
                                    "device, cores, memory, noise seed) that no other " +
                                    "profile uses.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        val pulseAlpha by animateFloatAsState(
                            targetValue = if (regenerateTick % 2 == 0) 1f else 0.55f,
                            animationSpec = tween(durationMillis = 320),
                            label = "regeneratePulse"
                        )

                        OutlinedTextField(
                            value = userAgent,
                            onValueChange = { userAgent = it },
                            label = { Text("User-Agent") },
                            singleLine = false,
                            maxLines = 3,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = pulseAlpha }
                        )

                        OutlinedTextField(
                            value = deviceName,
                            onValueChange = { deviceName = it },
                            label = { Text("Device name (e.g. Samsung Galaxy S23)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer { alpha = pulseAlpha }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = screenWText,
                                onValueChange = { screenWText = it.filter { c -> c.isDigit() } },
                                label = { Text("Screen width") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { alpha = pulseAlpha }
                            )
                            OutlinedTextField(
                                value = screenHText,
                                onValueChange = { screenHText = it.filter { c -> c.isDigit() } },
                                label = { Text("Screen height") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { alpha = pulseAlpha }
                            )
                            OutlinedTextField(
                                value = dprText,
                                onValueChange = { dprText = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Pixel ratio") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .graphicsLayer { alpha = pulseAlpha }
                            )
                        }

                        HorizontalDivider()

                        SectionTitle(text = "WebRTC policy")

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            WebRtcPolicyOption.entries.forEach { option ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .selectable(
                                            selected = webRtcPolicy == option.name,
                                            onClick = { webRtcPolicy = option.name }
                                        )
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = webRtcPolicy == option.name,
                                        onClick = { webRtcPolicy = option.name }
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = option.label,
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = option.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider()

                        SectionTitle(text = "Noise")

                        ToggleRow(
                            title = "Canvas noise",
                            subtitle = "Add random noise to canvas fingerprints",
                            checked = canvasNoise,
                            onCheckedChange = { canvasNoise = it }
                        )
                        ToggleRow(
                            title = "Audio noise",
                            subtitle = "Perturb the audio context fingerprint",
                            checked = audioNoise,
                            onCheckedChange = { audioNoise = it }
                        )

                        HorizontalDivider()

                        SectionTitle(text = "Locale")

                        OutlinedTextField(
                            value = timezone,
                            onValueChange = { timezone = it },
                            label = { Text("Timezone (e.g. Europe/Berlin)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = locale,
                            onValueChange = { locale = it },
                            label = { Text("Locale (e.g. en-US)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun ColorDot(
    color: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(parseTagColor(color))
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ResultCard(ok: Boolean, message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (ok) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.errorContainer
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (ok) "Proxy test succeeded" else "Proxy test failed",
                style = MaterialTheme.typography.titleSmall,
                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (ok) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
