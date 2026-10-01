package com.cloakdroid.ui.browser

import com.cloakdroid.ui.theme.parseTagColor
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cloakdroid.engine.GeckoSessionManager
import com.cloakdroid.ui.profiles.ProfileViewModel
import kotlinx.coroutines.launch
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoView

private const val DEFAULT_START_URL = "https://www.google.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    profileId: String,
    viewModel: ProfileViewModel,
    sessionManager: GeckoSessionManager,
    onBack: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var session by remember { mutableStateOf<GeckoSession?>(null) }
    var urlInput by remember { mutableStateOf(DEFAULT_START_URL) }
    var showProfileSheet by remember { mutableStateOf(false) }

    val currentUrl by sessionManager.currentUrl.collectAsStateWithLifecycle(initialValue = null)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val profile = profiles.find { it.id == profileId }

    val proxyType = profile?.proxyType ?: "Direct"
    val tagColor: androidx.compose.ui.graphics.Color = if (profile?.tagColor != null) {
        parseTagColor(profile.tagColor, fallback = MaterialTheme.colorScheme.primary)
    } else MaterialTheme.colorScheme.primary
    val profileName = profile?.name ?: "Profile"

    // Keep the URL field in sync with whatever the engine is currently displaying.
    LaunchedEffect(currentUrl) {
        val url = currentUrl
        if (!url.isNullOrBlank()) {
            urlInput = url
        }
    }

    DisposableEffect(profileId) {
        val created = sessionManager.launch(profileId, DEFAULT_START_URL)
        session = created
        onDispose {
            sessionManager.destroyCurrent()
            session = null
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ---- Top bar -------------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { session?.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = { session?.goForward() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = { session?.reload() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Reload")
            }

            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                singleLine = true,
                placeholder = { Text("Search or enter address") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(
                    onGo = {
                        focusManager.clearFocus()
                        urlInput.trim().takeIf { it.isNotBlank() }?.let { session?.loadUri(it) }
                    }
                ),
                trailingIcon = {
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            urlInput.trim().takeIf { it.isNotBlank() }?.let { session?.loadUri(it) }
                        }
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Go")
                    }
                }
            )

            IconButton(
                onClick = {
                    sessionManager.destroyCurrent()
                    session = null
                    onBack()
                }
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close tab")
            }
        }

        HorizontalDivider()

        // ---- Privacy HUD ---------------------------------------------------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.material3.Surface(
                onClick = { showProfileSheet = true },
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp, MaterialTheme.colorScheme.outline
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color = tagColor, shape = CircleShape)
                    )
                    Text("$profileName • $proxyType", style = MaterialTheme.typography.labelMedium)
                }
            }
            Text(
                text = if (currentUrl.isNullOrBlank()) "Idle" else "Protected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // ---- Content -------------------------------------------------------
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                GeckoView(context).apply {
                    session?.let { setSession(it) }
                }
            },
            update = { geckoView ->
                session?.let { geckoView.setSession(it) }
            }
        )
    }

    if (showProfileSheet) {
        ModalBottomSheet(
            onDismissRequest = { showProfileSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(color = tagColor, shape = CircleShape)
                    )
                    Text(
                        text = profileName,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Profile ID",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = profileId, style = MaterialTheme.typography.bodyMedium)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Proxy",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(text = proxyType, style = MaterialTheme.typography.bodyMedium)
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Current URL",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = currentUrl ?: DEFAULT_START_URL,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                HorizontalDivider()

                OutlinedButton(
                    onClick = {
                        viewModel.clearCache(profileId)
                        scope.launch { showProfileSheet = false }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Wipe Session Data")
                }
            }
        }
    }
}
