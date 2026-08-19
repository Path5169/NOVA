package com.nova.app.feature.appspace

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*
import java.text.DateFormat
import java.util.Date

@Composable
fun AppProfileScreen(
    packageName: String,
    viewModel: AppSpaceViewModel = viewModel()
) {
    val profileState by viewModel.profileState.collectAsState()
    val diagnosticState by viewModel.diagnosticState.collectAsState()
    val context = LocalContext.current
    val haptics = rememberNovaHaptics()

    LaunchedEffect(packageName) { viewModel.loadProfile(packageName) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        when (val s = profileState) {
            is AppProfileUiState.Loading -> item { NovaCard { NovaLoadingState("Reading app profile…") } }
            is AppProfileUiState.NotFound -> item {
                NovaCard { NovaUnavailableState("NOVA can no longer read this package. It may have been uninstalled.") }
            }
            is AppProfileUiState.Loaded -> {
                val profile = s.profile

                item { ProfileHeader(profile) }
                item { IndexCard(profile) }
                item {
                    UsageCard(
                        profile = profile,
                        onRequestUsageAccess = {
                            haptics.tick()
                            context.startActivity(viewModel.requestUsageAccessIntent())
                        }
                    )
                }
                if (profile.warnings.isNotEmpty()) {
                    item { WarningsCard(profile.warnings) }
                }
                item { PermissionsCard(profile.permissions) }
                item {
                    DiagnosticCard(
                        state = diagnosticState,
                        onRun = {
                            haptics.tap()
                            viewModel.runDiagnostic(packageName)
                        }
                    )
                }
                item {
                    ActionsRow(
                        onOpen = { openApp(context, viewModel, packageName, haptics) },
                        onDetails = {
                            haptics.tick()
                            context.startActivity(viewModel.appInfoSettingsIntent(packageName))
                        },
                        onDiagnostics = {
                            haptics.tap()
                            viewModel.runDiagnostic(packageName)
                        }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

private fun openApp(context: Context, viewModel: AppSpaceViewModel, packageName: String, haptics: com.nova.app.core.NovaHaptics) {
    val intent = viewModel.launchIntentFor(packageName)
    if (intent != null) {
        haptics.tick()
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "This app has no launchable entry point.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun ProfileHeader(profile: AppProfile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background((if (profile.enabled) NovaAccent else NovaTextTertiary).copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Apps,
                contentDescription = null,
                tint = if (profile.enabled) NovaAccent else NovaTextTertiary,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(profile.label, style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Text(profile.packageName, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary)
            Row(Modifier.padding(top = 4.dp)) {
                if (profile.isSystemApp) NovaChip("SYSTEM", status = NovaStatus.NEUTRAL)
                if (!profile.enabled) {
                    Spacer(Modifier.width(6.dp))
                    NovaChip("DISABLED", status = NovaStatus.WARN)
                }
            }
        }
    }
}

@Composable
private fun IndexCard(profile: AppProfile) {
    val status = when {
        profile.index >= 75 -> NovaStatus.GOOD
        profile.index >= 45 -> NovaStatus.WARN
        else -> NovaStatus.BAD
    }
    NovaCard {
        NovaSectionHeader("NOVA Index", trailing = "${profile.index}%")
        Spacer(Modifier.height(4.dp))
        Text(
            "A composite read of permission exposure, enabled state, and update recency — not a performance measurement Android does not expose.",
            style = MaterialTheme.typography.labelSmall,
            color = NovaTextTertiary
        )
        Spacer(Modifier.height(12.dp))
        NovaProgressBar(progress = profile.index / 100f)
        Spacer(Modifier.height(14.dp))
        NovaMetric(
            "Version",
            profile.versionName ?: "—",
            unit = "(${profile.versionCode})",
            status = NovaStatus.NEUTRAL
        )
        Spacer(Modifier.height(8.dp))
        NovaMetric(
            "Storage (install)",
            profile.apkSizeBytes?.let { formatBytes(it) } ?: "—",
            status = if (profile.apkSizeBytes != null) NovaStatus.GOOD else NovaStatus.UNAVAILABLE
        )
        Spacer(Modifier.height(8.dp))
        NovaMetric(
            "Updated",
            DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(profile.lastUpdateMillis)),
            status = NovaStatus.NEUTRAL
        )
        Spacer(Modifier.height(8.dp))
        NovaMetric(
            "Target SDK",
            "${profile.targetSdk}",
            status = status
        )
    }
}

@Composable
private fun UsageCard(profile: AppProfile, onRequestUsageAccess: () -> Unit) {
    NovaCard {
        NovaSectionHeader("Usage")
        Spacer(Modifier.height(12.dp))
        if (profile.usageAccessGranted && profile.usageStats != null) {
            NovaMetric(
                "Foreground today",
                formatDuration(profile.usageStats.foregroundTimeTodayMillis),
                status = NovaStatus.GOOD
            )
            Spacer(Modifier.height(8.dp))
            NovaMetric(
                "Last used",
                profile.usageStats.lastUsedMillis?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "Not recently",
                status = NovaStatus.NEUTRAL
            )
        } else {
            NovaPermissionExplainer(
                icon = "📊",
                title = "Usage access needed",
                explanation = "NOVA needs the Usage Access special permission to show real foreground time and last-used data for other apps. This is an Android system setting, not a runtime prompt — NOVA never sees this without you granting it explicitly.",
                actionLabel = "Grant usage access",
                onRequest = onRequestUsageAccess
            )
        }
    }
}

@Composable
private fun WarningsCard(warnings: List<AppWarning>) {
    NovaCard {
        NovaSectionHeader("Warnings", trailing = "${warnings.size}")
        Spacer(Modifier.height(12.dp))
        warnings.forEachIndexed { index, warning ->
            if (index > 0) Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = NovaWarn, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(warning.label, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary)
                    Text(warning.detail, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary)
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PermissionsCard(permissions: List<AppPermissionEntry>) {
    val dangerous = permissions.filter { it.isDangerous }
    NovaCard {
        NovaSectionHeader("Permissions", trailing = "${dangerous.count { it.granted }}/${dangerous.size} sensitive granted")
        Spacer(Modifier.height(12.dp))
        if (dangerous.isEmpty()) {
            NovaUnavailableState("No sensitive permissions requested by this app.")
        } else {
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dangerous.forEach { perm ->
                    NovaChip(perm.label, status = if (perm.granted) NovaStatus.WARN else NovaStatus.NEUTRAL)
                }
            }
        }
    }
}

@Composable
private fun DiagnosticCard(state: AppDiagnosticUiState, onRun: () -> Unit) {
    NovaCard {
        when (state) {
            is AppDiagnosticUiState.Idle -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Diagnostics", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                        Text("Run a focused scan on this app", style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
                    }
                    FilledIconButton(onClick = onRun, colors = IconButtonDefaults.filledIconButtonColors(containerColor = NovaAccent)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Run diagnostics", tint = NovaBackground)
                    }
                }
            }
            is AppDiagnosticUiState.Running -> {
                NovaLoadingState("Running diagnostic scan…")
            }
            is AppDiagnosticUiState.Done -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NOVA App Diagnostic", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                    Text("RE-RUN", style = MaterialTheme.typography.labelLarge, color = NovaAccent, modifier = Modifier.clickable(onClick = onRun))
                }
                Spacer(Modifier.height(14.dp))
                state.report.checks.forEachIndexed { index, check ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(tween(220, delayMillis = index * 60)) + slideInVertically(tween(220, delayMillis = index * 60)) { it / 4 }
                    ) {
                        Column(Modifier.padding(vertical = 4.dp)) {
                            NovaMetric(
                                check.label,
                                when (check.status) {
                                    NovaCheckStatus.OK -> "OK"
                                    NovaCheckStatus.WARN -> "WARN"
                                    NovaCheckStatus.UNAVAILABLE -> "N/A"
                                },
                                status = when (check.status) {
                                    NovaCheckStatus.OK -> NovaStatus.GOOD
                                    NovaCheckStatus.WARN -> NovaStatus.WARN
                                    NovaCheckStatus.UNAVAILABLE -> NovaStatus.UNAVAILABLE
                                }
                            )
                            Text(check.detail, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary, modifier = Modifier.padding(start = 17.dp, top = 2.dp))
                        }
                    }
                }
                if (state.report.warningCount > 0) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "${state.report.warningCount} warning${if (state.report.warningCount == 1) "" else "s"} found",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaWarn
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionsRow(onOpen: () -> Unit, onDetails: () -> Unit, onDiagnostics: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ActionButton("OPEN", Icons.Filled.OpenInNew, Modifier.weight(1f), onOpen)
        ActionButton("DETAILS", Icons.Filled.Info, Modifier.weight(1f), onDetails)
        ActionButton("DIAGNOSE", Icons.Filled.Troubleshoot, Modifier.weight(1f), onDiagnostics)
    }
}

@Composable
private fun ActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    NovaCard(modifier = modifier, onClick = onClick) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(icon, contentDescription = label, tint = NovaAccent, modifier = Modifier.size(18.dp))
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = NovaTextPrimary)
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024f * 1024f))
    bytes >= 1024 -> "%.0f KB".format(bytes / 1024f)
    else -> "$bytes B"
}

private fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
