package com.nova.app.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.navigation.NovaDestination
import com.nova.app.ui.theme.*

private data class ModuleShortcut(val title: String, val route: String, val icon: ImageVector, val available: Boolean)

private val modules = listOf(
    ModuleShortcut("App Space", NovaDestination.AppSpace.route, Icons.Filled.Apps, true),
    ModuleShortcut("Lab", NovaDestination.Lab.route, Icons.Filled.Science, true),
    ModuleShortcut("Device", NovaDestination.Device.route, Icons.Filled.PhoneAndroid, true),
    ModuleShortcut("Tools", NovaDestination.Tools.route, Icons.Filled.Build, true),
    ModuleShortcut("Network", NovaDestination.Network.route, Icons.Filled.Wifi, true),
    ModuleShortcut("Vision", NovaDestination.Vision.route, Icons.Filled.CameraAlt, true),
    ModuleShortcut("Detective", NovaDestination.Detective.route, Icons.Filled.Search, true),
    ModuleShortcut("Shield", NovaDestination.Shield.route, Icons.Filled.Shield, true)
)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val header by viewModel.header.collectAsState()
    val scanState by viewModel.scanState.collectAsState()
    val haptics = rememberNovaHaptics()

    LaunchedEffect(scanState) {
        if (scanState is ScanUiState.Done) {
            val report = (scanState as ScanUiState.Done).report
            if (report.findings.isEmpty()) haptics.success() else haptics.warning()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                val orbState = when (val scan = scanState) {
                    is ScanUiState.Scanning -> NovaOrbState.WORKING
                    is ScanUiState.Done -> if (scan.report.findings.isEmpty()) NovaOrbState.GOOD else NovaOrbState.WARN
                    is ScanUiState.Idle -> NovaOrbState.IDLE
                }
                val orbLabel = when (val scan = scanState) {
                    is ScanUiState.Scanning -> "Scanning"
                    is ScanUiState.Done -> if (scan.report.findings.isEmpty()) "All normal" else "${scan.report.findings.size} to review"
                    is ScanUiState.Idle -> "NOVA"
                }
                NovaOrb(
                    state = orbState,
                    label = orbLabel,
                    sublabel = "TAP TO SCAN",
                    onClick = {
                        haptics.tap()
                        viewModel.runDeepScan()
                    }
                )
                Spacer(Modifier.height(16.dp))
                Text("NOVA", style = MaterialTheme.typography.headlineLarge, color = NovaTextPrimary)
                Text(
                    "Your phone, instrumented.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTextSecondary
                )
            }
        }

        item {
            val batteryPercent = header.batteryPercent
            NovaCard {
                NovaSectionHeader("Status")
                Spacer(Modifier.height(14.dp))
                NovaMetric(
                    "Battery",
                    batteryPercent?.let { "$it" } ?: "—",
                    unit = "%",
                    status = when {
                        batteryPercent == null -> NovaStatus.UNAVAILABLE
                        batteryPercent < 15 -> NovaStatus.WARN
                        else -> NovaStatus.GOOD
                    }
                )
                Spacer(Modifier.height(10.dp))
                NovaMetric(
                    "Sensors detected",
                    "${header.sensorsAvailable}",
                    status = NovaStatus.GOOD
                )
            }
        }

        item {
            DeepScanCard(
                scanState = scanState,
                onRun = {
                    haptics.tap()
                    viewModel.runDeepScan()
                },
                onDismiss = { viewModel.dismissScan() }
            )
        }

        item {
            NovaCard(onClick = {
                haptics.tick()
                onNavigate(NovaDestination.PrivateHome.route)
            }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Private", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                            Text("Vault, notes, secure files — local only", style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
                        }
                    }
                    Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = NovaTextTertiary)
                }
            }
        }

        item { NovaSectionHeader("Modules") }

        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                userScrollEnabled = false
            ) {
                items(modules) { module ->
                    ModuleTile(module = module, onClick = {
                        haptics.tick()
                        onNavigate(module.route)
                    })
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun ModuleTile(module: ModuleShortcut, onClick: () -> Unit) {
    NovaCard(onClick = onClick, modifier = Modifier.height(96.dp)) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                module.icon,
                contentDescription = null,
                tint = if (module.available) NovaAccent else NovaTextTertiary,
                modifier = Modifier.size(22.dp)
            )
            Column {
                Text(module.title, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary)
                if (!module.available) {
                    Text("Coming soon", style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary)
                }
            }
        }
    }
}

@Composable
private fun DeepScanCard(
    scanState: ScanUiState,
    onRun: () -> Unit,
    onDismiss: () -> Unit
) {
    NovaCard {
        when (scanState) {
            is ScanUiState.Idle -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Deep Scan", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                        Text(
                            "Runs the checks safely available right now",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovaTextTertiary
                        )
                    }
                    FilledIconButton(onClick = onRun, colors = IconButtonDefaults.filledIconButtonColors(containerColor = NovaAccent)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Run Deep Scan", tint = NovaBackground)
                    }
                }
            }
            is ScanUiState.Scanning -> {
                Text("Scanning…", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                Spacer(Modifier.height(10.dp))
                NovaProgressBar(progress = scanState.progress)
            }
            is ScanUiState.Done -> {
                val report = scanState.report
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("NOVA System Report", style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary)
                    Text(
                        "RE-RUN",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaAccent,
                        modifier = Modifier.clickable(onClick = onRun)
                    )
                }
                Spacer(Modifier.height(14.dp))
                NovaMetric("Device", report.deviceStatus, status = if (report.deviceStatus == "Healthy") NovaStatus.GOOD else NovaStatus.WARN)
                Spacer(Modifier.height(8.dp))
                NovaMetric("Storage", report.storageStatus, status = if (report.storageStatus == "Normal") NovaStatus.GOOD else NovaStatus.WARN)
                Spacer(Modifier.height(8.dp))
                NovaMetric("Battery", report.batteryStatus, status = if (report.batteryStatus == "Normal") NovaStatus.GOOD else NovaStatus.WARN)
                Spacer(Modifier.height(8.dp))
                NovaMetric("Network", report.networkStatus, status = if (report.networkStatus == "Good") NovaStatus.GOOD else NovaStatus.WARN)
                Spacer(Modifier.height(8.dp))
                NovaMetric("Privacy", report.privacyStatus, status = if (report.privacyStatus == "Normal") NovaStatus.GOOD else NovaStatus.WARN)
                Spacer(Modifier.height(8.dp))
                NovaMetric("Sensors", "${report.sensorsAvailable}", status = NovaStatus.GOOD)

                if (report.findings.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "${report.findings.size} thing${if (report.findings.size == 1) "" else "s"} worth checking",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaWarn
                    )
                    Spacer(Modifier.height(8.dp))
                    report.findings.forEachIndexed { index, finding ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(220, delayMillis = index * 90)) +
                                slideInVertically(tween(220, delayMillis = index * 90)) { it / 4 }
                        ) {
                            Text(
                                "⚠ ${finding.label} — ${finding.detail}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NovaTextSecondary,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(14.dp))
                    Text("Nothing to flag right now.", style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
                }
            }
        }
    }
}
