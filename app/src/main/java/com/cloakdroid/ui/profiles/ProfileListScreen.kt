package com.cloakdroid.ui.profiles

import com.cloakdroid.data.local.ProfileEntity
import com.cloakdroid.ui.theme.CloakColors
import com.cloakdroid.ui.theme.parseTagColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import kotlinx.coroutines.launch

private enum class ProfileFilter(val label: String) {
    ALL("All"),
    PROXY("Proxy"),
    DIRECT("Direct")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileListScreen(
    viewModel: ProfileViewModel,
    onLaunch: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsState()

    // Ambient glow: 20s cycle, 0 -> 1 -> 0.
    val ambientTransition = rememberInfiniteTransition(label = "ambientGlow")
    val ambientPhase by ambientTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambientPhase"
    )

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ProfileFilter.ALL) }
    var menuForId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    var showBatchDialog by remember { mutableStateOf(false) }
    val bulkTesting by viewModel.bulkTesting.collectAsState()
    val bulkResults by viewModel.bulkResults.collectAsState()
    val bulkProgress by viewModel.bulkProgress.collectAsState()
    val bulkTotal by viewModel.bulkTotal.collectAsState()
    var showBulkSheet by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val visibleProfiles = remember(profiles, query, filter) {
        val trimmed = query.trim()
        profiles.filter { profile ->
            val matchesQuery = trimmed.isEmpty() || profile.name.contains(trimmed, ignoreCase = true)
            val isDirectProfile = profile.proxyType.toString().equals("DIRECT", ignoreCase = true)
            val matchesFilter = when (filter) {
                ProfileFilter.ALL -> true
                ProfileFilter.PROXY -> !isDirectProfile
                ProfileFilter.DIRECT -> isDirectProfile
            }
            matchesQuery && matchesFilter
        }
    }

    pendingDeleteId?.let { deleteId ->
        val target = profiles.firstOrNull { it.id == deleteId }
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Delete profile") },
            text = {
                Text(
                    "Delete \"${target?.name ?: deleteId}\"? This removes its settings and cannot be undone."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(deleteId)
                        pendingDeleteId = null
                        menuForId = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
    }

    if (showBatchDialog) {
        var batchCount by remember { mutableStateOf("5") }
        AlertDialog(
            onDismissRequest = { showBatchDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("Generate profiles") },
            text = {
                Column {
                    Text("How many unique profiles should be generated? Each gets its own unique fingerprint.")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = batchCount,
                        onValueChange = { batchCount = it.filter { c -> c.isDigit() } },
                        label = { Text("Count (1-50)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val n = batchCount.toIntOrNull()?.coerceIn(1, 50) ?: 0
                        if (n > 0) viewModel.generateBatch(n)
                        showBatchDialog = false
                    }
                ) {
                    Text("Generate")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val bgBitmap = androidx.compose.runtime.remember { com.cloakdroid.ui.settings.ThemeController.backgroundBitmap() }
    Scaffold(
        modifier = modifier
            .background(com.cloakdroid.ui.theme.CloakBrushes.background)
            .then(
                if (bgBitmap != null) {
                    Modifier.drawBehind {
                        // Custom background image, drawn dimmed behind content.
                        val iw = bgBitmap.width.toFloat()
                        val ih = bgBitmap.height.toFloat()
                        val scale = maxOf(size.width / iw, size.height / ih)
                        val dw = iw * scale
                        val dh = ih * scale
                        val left = (size.width - dw) / 2f
                        val top = (size.height - dh) / 2f
                        drawImage(
                            image = bgBitmap,
                            dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                            dstSize = androidx.compose.ui.unit.IntSize(dw.toInt(), dh.toInt()),
                            alpha = 0.22f
                        )
                    }
                } else Modifier
            )
            .drawBehind {
                // Ambient: very slow-moving large radial indigo glow behind content.
                val phase = ambientPhase
                val cx = size.width * (0.7f - 0.4f * phase)
                val cy = size.height * (0.65f - 0.25f * phase)
                val radius = size.maxDimension * 0.9f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CloakColors.Primary.copy(alpha = 0.10f),
                            CloakColors.Secondary.copy(alpha = 0.04f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = radius
                    ),
                    radius = radius,
                    center = Offset(cx, cy)
                )
            },
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            Surface(
                color = androidx.compose.ui.graphics.Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    com.cloakdroid.ui.theme.CloakUI.MetallicHeadline(text = "CloakDroid")
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "Search profiles",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Overflow menu: import from clipboard + batch generate.
                        var topMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { topMenuExpanded = true }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            DropdownMenu(
                                expanded = topMenuExpanded,
                                onDismissRequest = { topMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Import from clipboard",
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        topMenuExpanded = false
                                        val raw = clipboard.getText()?.text.orEmpty()
                                        if (raw.isNotBlank()) {
                                            viewModel.importJson(raw)
                                            android.widget.Toast.makeText(
                                                context,
                                                "Profile imported from clipboard",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        } else {
                                            android.widget.Toast.makeText(
                                                context,
                                                "Clipboard is empty",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "Generate N profiles…",
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        topMenuExpanded = false
                                        showBatchDialog = true
                                    }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                com.cloakdroid.ui.theme.CloakButtons.GhostButton(
                    text = if (bulkTesting) "Testing $bulkProgress/$bulkTotal…" else "⚡ Test All",
                    onClick = {
                        viewModel.testAllProfiles()
                        showBulkSheet = true
                    },
                    height = 44.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                com.cloakdroid.ui.theme.CloakButtons.GradientButton(
                    text = "+  New Profile",
                    onClick = {
                        val fresh = viewModel.newRandomProfile()
                        viewModel.save(fresh)
                    },
                    height = 52.dp
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProfileFilter.entries.forEach { entry ->
                    val selected = filter == entry
                    FilterChip(
                        selected = selected,
                        onClick = { filter = entry },
                        label = {
                            Text(
                                text = entry.label,
                                color = if (selected) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    )
                }
            }

            if (visibleProfiles.isEmpty()) {
                EmptyState(queryText = query)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 96.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visibleProfiles, key = { it.id }) { profile ->
                        val index = visibleProfiles.indexOf(profile)
                        ProfileCard(
                            profile = profile,
                            index = index,
                            badgeLabel = profile.proxyType.toString().uppercase(),
                            badgeContainerColor = if (profile.proxyType.toString()
                                    .equals("DIRECT", ignoreCase = true)
                            ) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                            badgeContentColor = if (profile.proxyType.toString()
                                    .equals("DIRECT", ignoreCase = true)
                            ) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                            onEdit = { onEdit(profile.id) },
                            onLaunch = { onLaunch(profile.id) },
                            onMenuToggle = {
                                menuForId =
                                    if (menuForId == profile.id) null else profile.id
                            },
                            menuExpanded = menuForId == profile.id,
                            onMenuDismiss = { menuForId = null },
                            onClone = {
                                viewModel.clone(profile.id)
                                menuForId = null
                            },
                            onClearCache = {
                                viewModel.clearCache(profile.id)
                                menuForId = null
                            },
                            onExport = {
                                menuForId = null
                                scope.launch {
                                    val json = viewModel.exportJson(profile.id)
                                    if (json != null) {
                                        clipboard.setText(AnnotatedString(json))
                                        android.widget.Toast.makeText(
                                            context,
                                            "Profile JSON copied to clipboard",
                                            android.widget.Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            },
                            onShare = {
                                menuForId = null
                                scope.launch {
                                    val json = viewModel.exportJson(profile.id)
                                        ?: return@launch
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_SEND
                                    ).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, json)
                                    }
                                    context.startActivity(
                                        android.content.Intent.createChooser(intent, "Share profile")
                                    )
                                }
                            },
                            onDelete = { pendingDeleteId = profile.id }
                        )
                    }
                }
            }
        }
    }

        // ---- Bulk test results sheet ----------------------------------------
        if (showBulkSheet) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { showBulkSheet = false },
                containerColor = com.cloakdroid.ui.theme.CloakColors.Surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 28.dp)
                ) {
                    Text(
                        "Proxy Test Results",
                        style = MaterialTheme.typography.titleLarge,
                        color = com.cloakdroid.ui.theme.CloakColors.TextHigh
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (bulkTesting) "Testing $bulkProgress of $bulkTotal…"
                        else "Sorted by latency — fastest first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = com.cloakdroid.ui.theme.CloakColors.TextMuted
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (bulkResults.isEmpty()) {
                        Text(
                            "No proxy profiles to test.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = com.cloakdroid.ui.theme.CloakColors.TextMuted
                        )
                    } else {
                        bulkResults.forEachIndexed { i, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${i + 1}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = com.cloakdroid.ui.theme.CloakColors.TextFaint,
                                    modifier = Modifier.width(24.dp)
                                )
                                Text(
                                    entry.name,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = com.cloakdroid.ui.theme.CloakColors.TextHigh
                                )
                                if (entry.ok) {
                                    Text(
                                        "${entry.latencyMs} ms",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = com.cloakdroid.ui.theme.CloakColors.Success
                                    )
                                } else {
                                    Text(
                                        "Failed",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = com.cloakdroid.ui.theme.CloakColors.Error
                                    )
                                }
                            }
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = com.cloakdroid.ui.theme.CloakColors.GlassBorder
                            )
                        }
                    }
                }
            }
        }
}

/**
 * Single profile card: staggered slide-up + fade entrance on first composition
 * and a spring press-scale (0.97) while the user holds it down.
 */
@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    index: Int,
    badgeLabel: String,
    badgeContainerColor: Color,
    badgeContentColor: Color,
    onEdit: () -> Unit,
    onLaunch: () -> Unit,
    onMenuToggle: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onClone: () -> Unit,
    onExport: () -> Unit,
    onShare: () -> Unit,
    onClearCache: () -> Unit,
    onDelete: () -> Unit
) {
    // Staggered entrance: slide up from 48dp + fade in, 40ms delay per index.
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 40L)
        appeared = true
    }
    val entranceProgress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "cardEntrance"
    )

    // Press scale, driven by the card's real interaction source.
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardPress"
    )

    ElevatedCard(
        onClick = {
            onEdit()
        },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = entranceProgress
                translationY = (1f - entranceProgress) * 48.dp.toPx()
                scaleX = pressScale
                scaleY = pressScale
            },
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = 0.dp
        ),
        interactionSource = interactionSource
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(parseTagColor(profile.tagColor))
            )
            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = profile.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                    color = badgeContainerColor,
                    contentColor = badgeContentColor
                ) {
                    Text(
                        text = badgeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = badgeContentColor,
                        modifier = Modifier.padding(
                            horizontal = 8.dp,
                            vertical = 3.dp
                        )
                    )
                }
            }

            FilledTonalButton(
                onClick = onLaunch,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            ) {
                Text("Launch")
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(onClick = onMenuToggle) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More actions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss
            ) {
                DropdownMenuItem(
                    text = { Text("Clone", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = onClone
                )
                DropdownMenuItem(
                    text = {
                        Text("Export (copy JSON)", color = MaterialTheme.colorScheme.onSurface)
                    },
                    onClick = onExport
                )
                DropdownMenuItem(
                    text = { Text("Share…", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = onShare
                )
                DropdownMenuItem(
                    text = { Text("Clear Cache", color = MaterialTheme.colorScheme.onSurface) },
                    onClick = onClearCache
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = onDelete
                )
            }
        }
    }

}

@Composable
private fun EmptyState(queryText: String) {
    val isFiltering = queryText.isNotBlank()
    val transition = rememberInfiniteTransition(label = "emptyPulse")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emptyPulseScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    CloakColors.GlassBorder,
                    MaterialTheme.shapes.medium
                ),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(56.dp)
                        .graphicsLayer {
                            scaleX = pulse
                            scaleY = pulse
                        }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (isFiltering) "No profiles match \"$queryText\"."
                    else "No profiles yet — create your first identity",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                if (!isFiltering) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap \"+ New Profile\" to get started.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

    }
}
