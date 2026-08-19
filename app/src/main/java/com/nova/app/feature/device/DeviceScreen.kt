package com.nova.app.feature.device

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import kotlin.math.roundToInt

private data class DeviceTest(val title: String, val subtitle: String, val route: String)

private val deviceTests = listOf(
    DeviceTest("Touch Test", "Multi-touch tracking across the panel", "device/touch"),
    DeviceTest("Display / Color Test", "Full-bleed color + dead-pixel sweep", "device/display"),
    DeviceTest("Vibration Test", "Haptic motor pattern check", "device/vibration"),
    DeviceTest("Flashlight Test", "Torch on/off via camera flash unit", "device/flashlight"),
    DeviceTest("Camera Test", "Live preview, front/rear switch", "device/camera")
)

@Composable
fun DeviceScreen(
    onNavigateToTest: (String) -> Unit,
    viewModel: DeviceViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text("Device", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Everything below is read live from Android — nothing estimated.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }

        when (val s = state) {
            is DeviceUiState.Loading -> item { NovaLoadingState("Reading device state…") }
            is DeviceUiState.Ready -> {
                val snap = s.snapshot
                item {
                    NovaCard {
                        NovaSectionHeader("Identity")
                        Spacer(Modifier.height(12.dp))
                        NovaMetric("Model", "${snap.manufacturer} ${snap.model}")
                        Spacer(Modifier.height(10.dp))
                        NovaMetric("Android", "${snap.androidVersion} (API ${snap.sdkInt})")
                    }
                }
                item {
                    NovaCard {
                        NovaSectionHeader("Memory & Storage")
                        Spacer(Modifier.height(12.dp))
                        NovaMetric(
                            "RAM available",
                            "${snap.memory.availableMb}",
                            unit = "/ ${snap.memory.totalMb} MB",
                            status = if (snap.memory.lowMemory) NovaStatus.WARN else NovaStatus.GOOD
                        )
                        Spacer(Modifier.height(10.dp))
                        NovaMetric(
                            "Storage free",
                            "%.1f".format(snap.storage.freeGb),
                            unit = "/ %.1f GB".format(snap.storage.totalGb),
                            status = if (snap.storage.freeGb < 1.0) NovaStatus.WARN else NovaStatus.GOOD
                        )
                    }
                }
                item {
                    NovaCard {
                        NovaSectionHeader("Battery")
                        Spacer(Modifier.height(12.dp))
                        NovaMetric(
                            "Charge",
                            snap.battery.percent?.let { "$it" } ?: "—",
                            unit = "%",
                            status = when {
                                snap.battery.percent == null -> NovaStatus.UNAVAILABLE
                                snap.battery.percent < 15 -> NovaStatus.WARN
                                else -> NovaStatus.GOOD
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        NovaMetric(
                            "State",
                            when (snap.battery.isCharging) {
                                true -> "Charging"
                                false -> "Discharging"
                                null -> "Unknown"
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        if (snap.battery.temperatureC != null) {
                            NovaMetric("Temperature", "%.1f".format(snap.battery.temperatureC), unit = "°C")
                        } else {
                            NovaUnavailableState("Battery temperature not reported by this device.")
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Note: Android does not expose a battery-health percentage to normal apps — NOVA won't invent one.",
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTextPrimary.copy(alpha = 0.35f)
                        )
                    }
                }
                item {
                    NovaCard {
                        NovaSectionHeader("Display")
                        Spacer(Modifier.height(12.dp))
                        NovaMetric("Resolution", "${snap.display.widthPx} × ${snap.display.heightPx}", unit = "px")
                        Spacer(Modifier.height(10.dp))
                        NovaMetric("Density", "%.1f".format(snap.display.density), unit = "x")
                        Spacer(Modifier.height(10.dp))
                        if (snap.display.refreshRateHz != null) {
                            NovaMetric("Refresh rate", "${snap.display.refreshRateHz.roundToInt()}", unit = "Hz")
                        } else {
                            NovaUnavailableState("Refresh rate not reported by this device.")
                        }
                    }
                }
                item {
                    NovaCard {
                        NovaSectionHeader("CPU & Sensors")
                        Spacer(Modifier.height(12.dp))
                        NovaMetric("CPU cores", "${snap.cpu.cores}")
                        Spacer(Modifier.height(10.dp))
                        NovaMetric("Primary ABI", snap.cpu.abis.firstOrNull() ?: "—")
                        Spacer(Modifier.height(10.dp))
                        NovaMetric(
                            "Sensors detected",
                            "${snap.sensorCount}",
                            status = NovaStatus.GOOD
                        )
                    }
                }
                item {
                    NovaCard {
                        NovaSectionHeader("Camera")
                        Spacer(Modifier.height(12.dp))
                        NovaMetric("Rear/any camera", if (snap.camera.hasCamera) "Present" else "Not detected",
                            status = if (snap.camera.hasCamera) NovaStatus.GOOD else NovaStatus.UNAVAILABLE)
                        Spacer(Modifier.height(10.dp))
                        NovaMetric("Front camera", if (snap.camera.hasFrontCamera) "Present" else "Not detected",
                            status = if (snap.camera.hasFrontCamera) NovaStatus.GOOD else NovaStatus.UNAVAILABLE)
                        Spacer(Modifier.height(10.dp))
                        NovaMetric("Flash unit", if (snap.camera.hasFlash) "Present" else "Not detected",
                            status = if (snap.camera.hasFlash) NovaStatus.GOOD else NovaStatus.UNAVAILABLE)
                    }
                }
            }
        }

        item {
            NovaSectionHeader("Interactive tests")
        }
        items(deviceTests) { test ->
            NovaCard(onClick = { onNavigateToTest(test.route) }) {
                NovaListRow(
                    title = test.title,
                    subtitle = test.subtitle,
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = NovaAccent) },
                    onClick = { onNavigateToTest(test.route) }
                )
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}
