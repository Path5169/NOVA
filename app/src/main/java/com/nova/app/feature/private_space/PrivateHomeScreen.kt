package com.nova.app.feature.private_space

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.navigation.NovaDestination
import com.nova.app.ui.theme.*

private data class PrivateShortcut(val title: String, val subtitle: String, val route: String, val icon: ImageVector)

private val shortcuts = listOf(
    PrivateShortcut("Vault", "Files, images, secure bookmarks", NovaDestination.PrivateVault.route, Icons.Filled.Bookmarks),
    PrivateShortcut("Notes", "Private local notes", NovaDestination.PrivateNotes.route, Icons.Filled.Notes)
)

@Composable
fun PrivateHomeScreen(onNavigate: (String) -> Unit) {
    val haptics = rememberNovaHaptics()
    val autoLock by PrivateSessionManager.autoLockDuration.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("NOVA // PRIVATE", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
                }
                Spacer(Modifier.height(4.dp))
                NovaChip("SECURE SESSION ACTIVE", status = NovaStatus.GOOD)
            }
        }

        item {
            NovaCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Auto-lock", style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary)
                        Text("After ${autoLock.label} of inactivity", style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
                    }
                    Text(
                        "CHANGE",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaAccent,
                        modifier = Modifier.clickable { showSettings = true }
                    )
                }
            }
        }

        item {
            NovaCard(onClick = {
                haptics.tap()
                PrivateSessionManager.lockNow()
            }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LockOpen, contentDescription = null, tint = NovaBad, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("LOCK NOW", style = MaterialTheme.typography.labelLarge, color = NovaBad)
                }
            }
        }

        item { NovaSectionHeader("Protected spaces") }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(shortcuts) { shortcut ->                    NovaCard(onClick = {
                        haptics.tick()
                        PrivateSessionManager.notifyActivity()
                        onNavigate(shortcut.route)
                    }, modifier = Modifier.height(104.dp)) {
                        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(shortcut.icon, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(22.dp))
                            Column {
                                Text(shortcut.title, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary)
                                Text(shortcut.subtitle, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary, maxLines = 2)
                            }
                        }
                    }
                }
            }
        }

        item {
            NovaCard {
                Text(
                    "Content in NOVA Private stays on this device. It's encrypted with keys stored in " +
                        "the Android Keystore and is never uploaded or synced anywhere.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTextTertiary
                )
            }
        }
    }

    if (showSettings) {
        AutoLockPickerSheet(current = autoLock, onDismiss = { showSettings = false }, onSelect = {
            PrivateSessionManager.setAutoLockDuration(it)
            showSettings = false
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AutoLockPickerSheet(current: AutoLockDuration, onDismiss: () -> Unit, onSelect: (AutoLockDuration) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NovaSurface) {
        Column(Modifier.padding(20.dp)) {
            Text("Auto-lock after", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(12.dp))
            AutoLockDuration.entries.forEach { option ->
                NovaListRow(
                    title = option.label,
                    subtitle = if (option == current) "Current" else null,
                    onClick = { onSelect(option) }
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
