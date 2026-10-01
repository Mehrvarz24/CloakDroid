package com.cloakdroid.ui.profiles

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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
    modifier: Modifier = Modifier
) {
    val profiles by viewModel.profiles.collectAsState()

    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(ProfileFilter.ALL) }
    var menuForId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "CloakDroid",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
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
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                onClick = {
                    val fresh = viewModel.newRandomProfile()
                    viewModel.save(fresh)
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("New Profile", color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (query.isBlank()) "No profiles yet. Tap New Profile to create one."
                        else "No profiles match \"$query\".",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                        val isDirect =
                            profile.proxyType.toString().equals("DIRECT", ignoreCase = true)
                        val badgeLabel = profile.proxyType.toString().uppercase()
                        val badgeContainerColor =
                            if (isDirect) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        val badgeContentColor =
                            if (isDirect) MaterialTheme.colorScheme.onTertiaryContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer

                        ElevatedCard(
                            onClick = { onEdit(profile.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            elevation = CardDefaults.elevatedCardElevation(
                                defaultElevation = 2.dp
                            )
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
                                        .background(Color(android.graphics.Color.parseColor(profile.tagColor)))
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
                                        shape = MaterialTheme.shapes.small,
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
                                    onClick = { onLaunch(profile.id) },
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                ) {
                                    Text("Launch")
                                }

                                Spacer(modifier = Modifier.width(4.dp))

                                IconButton(onClick = {
                                    menuForId =
                                        if (menuForId == profile.id) null else profile.id
                                }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More actions",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                DropdownMenu(
                                    expanded = menuForId == profile.id,
                                    onDismissRequest = { menuForId = null }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Clone",
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            viewModel.clone(profile.id)
                                            menuForId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Clear Cache",
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            viewModel.clearCache(profile.id)
                                            menuForId = null
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "Delete",
                                                color = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            pendingDeleteId = profile.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
