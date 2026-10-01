package com.cloakdroid.ui.browser

import com.cloakdroid.ui.theme.parseTagColor
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Star
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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
    var showBookmarkSheet by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }

    val bookmarks by viewModel.observeBookmarks(profileId)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val history by viewModel.observeHistory(profileId)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    // Prefer the live engine URL for page-scoped actions (star, sheets).
    val currentUrl by sessionManager.currentUrl.collectAsStateWithLifecycle(initialValue = null)
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val profile = profiles.find { it.id == profileId }

    // Prefer the live engine URL for page-scoped actions.
    val pageUrl = currentUrl ?: DEFAULT_START_URL

    val proxyType = profile?.proxyType ?: "Direct"
    val tagColor: androidx.compose.ui.graphics.Color = if (profile?.tagColor != null) {
        parseTagColor(profile.tagColor, fallback = MaterialTheme.colorScheme.primary)
    } else MaterialTheme.colorScheme.primary
    val profileName = profile?.name ?: "Profile"

    // Keep the URL field in sync with the engine's live location, but never
    // while the user is typing in it (focus check).
    val urlFieldFocused = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(currentUrl) {
        val url = currentUrl
        if (!url.isNullOrBlank() && !urlFieldFocused.value) {
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
                    .height(56.dp)
                    .onFocusChanged { urlFieldFocused.value = it.isFocused },
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

            // Bookmark star: filled when the current page is saved.
            val bookmarked = bookmarks.any { it.url == pageUrl }
            IconButton(
                onClick = {
                    val url = pageUrl.takeIf { it.isNotBlank() } ?: return@IconButton
                    viewModel.toggleBookmark(
                        profileId = profileId,
                        url = url,
                        title = null
                    )
                },
                enabled = !pageUrl.isBlank() && pageUrl != "about:blank"
            ) {
                Icon(
                    imageVector = if (bookmarked) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = if (bookmarked) "Remove bookmark" else "Add bookmark",
                    tint = if (bookmarked) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = { showBookmarkSheet = true }) {
                Icon(Icons.Outlined.List, contentDescription = "Bookmarks")
            }

            IconButton(onClick = { showHistorySheet = true }) {
                Icon(Icons.Default.Refresh, contentDescription = "History")
            }

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

    if (showBookmarkSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBookmarkSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Bookmarks", style = MaterialTheme.typography.titleLarge)
                HorizontalDivider()

                if (bookmarks.isEmpty()) {
                    Text(
                        "No bookmarks yet. Tap the star to save the current page.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(bookmarks, key = { it.id }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showBookmarkSheet = false
                                        sessionManager.loadUrl(bookmark.url)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bookmark.title,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bookmark.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { viewModel.deleteBookmark(bookmark) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete bookmark",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false },
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "History",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { viewModel.clearHistory(profileId) }) {
                        Text(
                            "Clear history",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                HorizontalDivider()

                if (history.isEmpty()) {
                    Text(
                        "No browsing history for this profile.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(history, key = { it.id }) { entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        showHistorySheet = false
                                        sessionManager.loadUrl(entry.url)
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = entry.title ?: entry.url,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = entry.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = java.text.DateFormat.getDateTimeInstance(
                                        java.text.DateFormat.SHORT,
                                        java.text.DateFormat.SHORT
                                    ).format(java.util.Date(entry.visitedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
