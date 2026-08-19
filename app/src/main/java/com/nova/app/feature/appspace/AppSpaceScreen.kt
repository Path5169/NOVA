package com.nova.app.feature.appspace

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*

@Composable
fun AppSpaceScreen(
    onOpenProfile: (String) -> Unit,
    viewModel: AppSpaceViewModel = viewModel()
) {
    val state by viewModel.listState.collectAsState()
    var query by remember { mutableStateOf("") }
    val haptics = rememberNovaHaptics()

    LaunchedEffect(Unit) { viewModel.loadApps() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Column {
                Text("App Space", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
                Text(
                    "Apps NOVA can currently see, and what NOVA can honestly tell you about each one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTextTertiary
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps", color = NovaTextTertiary) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NovaTextTertiary) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NovaAccent,
                    unfocusedBorderColor = NovaSurfaceOutline,
                    focusedTextColor = NovaTextPrimary,
                    unfocusedTextColor = NovaTextPrimary,
                    cursorColor = NovaAccent
                )
            )
        }

        when (val s = state) {
            is AppListUiState.Loading -> item { NovaCard { NovaLoadingState("Reading visible apps…") } }
            is AppListUiState.Loaded -> {
                val filtered = s.apps.filter {
                    query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
                }
                if (filtered.isEmpty()) {
                    item { NovaCard { NovaUnavailableState("No apps match \"$query\" among what's currently visible to NOVA.") } }
                } else {
                    item {
                        Text(
                            "${filtered.size} app${if (filtered.size == 1) "" else "s"} visible",
                            style = MaterialTheme.typography.labelMedium,
                            color = NovaTextTertiary
                        )
                    }
                    items(filtered) { app ->
                        AppRow(app = app, onClick = {
                            haptics.tick()
                            onOpenProfile(app.packageName)
                        })
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun AppRow(app: AppEntry, onClick: () -> Unit) {
    NovaCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (app.enabled) NovaAccent.copy(alpha = 0.14f) else NovaTextTertiary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Apps,
                        contentDescription = null,
                        tint = if (app.enabled) NovaAccent else NovaTextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary, maxLines = 1, fontWeight = FontWeight.Medium)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary, maxLines = 1)
                }
            }
            if (!app.enabled) {
                NovaChip("DISABLED", status = NovaStatus.WARN)
            } else if (app.isSystemApp) {
                NovaChip("SYSTEM", status = NovaStatus.NEUTRAL)
            }
        }
    }
}
