package com.nova.app.feature.privacy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PrivacyScreen(viewModel: PrivacyViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val haptics = rememberNovaHaptics()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text("Privacy", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
                    Text(
                        "Only what Android legitimately exposes — nothing bypassed, nothing guessed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = NovaTextPrimary.copy(alpha = 0.5f)
                    )
                }
                if (state is PrivacyUiState.Loaded) {
                    Text(
                        "RESCAN",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaAccent,
                        modifier = Modifier.clickable {
                            haptics.tap()
                            viewModel.refresh(force = true)
                        }
                    )
                }
            }
        }

        when (val s = state) {
            is PrivacyUiState.Loading -> {
                item { NovaCard { NovaLoadingState("Reading permission grants…") } }
            }
            is PrivacyUiState.Loaded -> {
                val snap = s.snapshot

                item {
                    NovaCard {
                        NovaSectionHeader("NOVA's own permissions")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "What NOVA itself has asked for, and whether you've granted it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovaTextTertiary
                        )
                        Spacer(Modifier.height(14.dp))
                        if (snap.selfPermissions.isEmpty()) {
                            NovaUnavailableState("NOVA hasn't requested any tracked permissions yet.")
                        } else {
                            snap.selfPermissions.forEachIndexed { index, perm ->
                                SelfPermissionRow(perm)
                                if (index != snap.selfPermissions.lastIndex) Spacer(Modifier.height(14.dp))
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 6 }
                    ) {
                        NovaCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                NovaSectionHeader("Apps with notification access", trailing = "${snap.notificationListeners.size}")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "These apps can read notifications shown across your whole device.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovaTextTertiary
                            )
                            Spacer(Modifier.height(12.dp))
                            if (snap.notificationListeners.isEmpty()) {
                                NovaUnavailableState("No apps currently have notification access on this device.")
                            } else {
                                snap.notificationListeners.forEachIndexed { i, entry ->
                                    NovaMetric(entry.label, entry.packageName.substringAfterLast('.'), status = NovaStatus.WARN)
                                    if (i != snap.notificationListeners.lastIndex) Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 6 }
                    ) {
                        NovaCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Accessibility, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                NovaSectionHeader("Accessibility services enabled", trailing = "${snap.accessibilityServices.size}")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Accessibility services can observe and act on nearly everything on screen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovaTextTertiary
                            )
                            Spacer(Modifier.height(12.dp))
                            if (snap.accessibilityServices.isEmpty()) {
                                NovaUnavailableState("No accessibility services are currently enabled.")
                            } else {
                                snap.accessibilityServices.forEachIndexed { i, entry ->
                                    NovaMetric(entry.label, entry.packageName.substringAfterLast('.'), status = NovaStatus.WARN)
                                    if (i != snap.accessibilityServices.lastIndex) Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 6 }
                    ) {
                        NovaCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Apps, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                NovaSectionHeader("Apps with sensitive permissions", trailing = "${snap.sensitiveApps.size}")
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Camera, microphone, location, contacts, SMS, calendar, body sensors, and storage — " +
                                    "among the ${snap.visibleAppCount} app(s) currently visible to NOVA.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovaTextTertiary
                            )
                            Spacer(Modifier.height(12.dp))
                            if (snap.sensitiveApps.isEmpty()) {
                                NovaUnavailableState("No other visible apps currently hold NOVA's tracked sensitive permissions.")
                            } else {
                                snap.sensitiveApps.forEachIndexed { i, app ->
                                    Column {
                                        Text(app.label, style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary)
                                        Spacer(Modifier.height(6.dp))
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            app.permissionLabels.forEach { label ->
                                                NovaChip(label, status = NovaStatus.WARN)
                                            }
                                        }
                                    }
                                    if (i != snap.sensitiveApps.lastIndex) Spacer(Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }

                item {
                    NovaCard {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Shield, contentDescription = null, tint = NovaTextTertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text("Why this list might be short", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Since Android 11, apps can normally only see a small subset of what's " +
                                        "installed on your phone — themselves, apps they've interacted with, and " +
                                        "some system packages. Seeing every installed app requires the " +
                                        "QUERY_ALL_PACKAGES permission. NOVA doesn't request it: requiring a " +
                                        "\"see everything on this phone\" grant just to power a privacy dashboard " +
                                        "would work against the privacy it's trying to protect.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = NovaTextTertiary
                                )
                            }
                        }
                    }
                }

                item { Spacer(Modifier.height(8.dp)) }
            }
        }
    }
}

@Composable
private fun SelfPermissionRow(perm: SelfPermissionStatus) {
    Column {
        NovaMetric(
            perm.label,
            if (perm.granted) "Granted" else "Not granted",
            status = if (perm.granted) NovaStatus.GOOD else NovaStatus.NEUTRAL
        )
        Spacer(Modifier.height(4.dp))
        Text(perm.explanation, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary)
    }
}
