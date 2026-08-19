package com.nova.app.feature.network

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Router
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nova.app.ui.components.*
import com.nova.app.ui.navigation.NovaDestination
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary

@Composable
fun NetworkScreen(onNavigate: (String) -> Unit) {
    val context = LocalContext.current
    val repository = remember { NetworkRepository(context) }

    val hasLocationPermission = remember {
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    var connection by remember { mutableStateOf(repository.connectionInfo()) }
    var wifi by remember { mutableStateOf(repository.wifiDetails(hasLocationPermission)) }
    var addressing by remember { mutableStateOf(repository.addressingInfo()) }

    // Cheap, local, non-network reads — safe to refresh whenever the screen resumes composition.
    LaunchedEffect(Unit) {
        connection = repository.connectionInfo()
        wifi = repository.wifiDetails(hasLocationPermission)
        addressing = repository.addressingInfo()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Network", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Live from Android's connectivity APIs — no speed or signal claims without a real measurement.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }

        item {
            NovaCard {
                NovaSectionHeader("Connection")
                Spacer(Modifier.height(12.dp))
                NovaMetric(
                    "Type",
                    connection.type.name.lowercase().replaceFirstChar { it.uppercase() },
                    status = if (connection.isConnected) NovaStatus.GOOD else NovaStatus.BAD
                )
                Spacer(Modifier.height(10.dp))
                NovaMetric(
                    "Internet capability",
                    if (connection.isConnected) "Reported" else "Not reported",
                    status = if (connection.isConnected) NovaStatus.GOOD else NovaStatus.WARN
                )
                Spacer(Modifier.height(10.dp))
                NovaMetric(
                    "Validated by system",
                    if (connection.isValidated) "Yes" else "No",
                    status = if (connection.isValidated) NovaStatus.GOOD else NovaStatus.NEUTRAL
                )
                Spacer(Modifier.height(10.dp))
                NovaMetric("Metered", if (connection.isMetered) "Yes" else "No")
            }
        }

        if (connection.type == ConnectionType.WIFI) {
            item {
                NovaCard {
                    NovaSectionHeader("Wi-Fi")
                    Spacer(Modifier.height(12.dp))
                    val w = wifi
                    if (w == null) {
                        NovaUnavailableState("Wi-Fi details not reported by Android on this connection.")
                    } else {
                        NovaMetric("SSID", w.ssid ?: if (w.ssidHidden) "Hidden — grant location" else "—")
                        Spacer(Modifier.height(10.dp))
                        NovaMetric(
                            "Signal",
                            w.rssiDbm?.let { "$it" } ?: "—",
                            unit = "dBm" + (w.signalBars?.let { " · $it/4 bars" } ?: ""),
                            status = when {
                                w.rssiDbm == null -> NovaStatus.UNAVAILABLE
                                w.rssiDbm > -60 -> NovaStatus.GOOD
                                w.rssiDbm > -75 -> NovaStatus.WARN
                                else -> NovaStatus.BAD
                            }
                        )
                        Spacer(Modifier.height(10.dp))
                        NovaMetric("Link speed", w.linkSpeedMbps?.let { "$it" } ?: "—", unit = "Mbps (negotiated, not throughput)")
                        Spacer(Modifier.height(10.dp))
                        NovaMetric("Frequency", w.frequencyMhz?.let { "$it" } ?: "—", unit = "MHz")
                        if (w.ssidHidden) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Android withholds the SSID from apps without location permission — this isn't a NOVA limitation.",
                                style = MaterialTheme.typography.labelSmall,
                                color = NovaTextPrimary.copy(alpha = 0.4f)
                            )
                        }
                    }
                }
            }
        }

        item {
            NovaCard {
                NovaSectionHeader("Addressing")
                Spacer(Modifier.height(12.dp))
                NovaMetric("Local IPv4", addressing.localIpv4 ?: "—")
                Spacer(Modifier.height(10.dp))
                NovaMetric("Subnet", addressing.subnetPrefixLength?.let { "/$it" } ?: "—")
                Spacer(Modifier.height(10.dp))
                NovaMetric("Gateway", addressing.gateway ?: "—")
                Spacer(Modifier.height(10.dp))
                if (addressing.dnsServers.isEmpty()) {
                    NovaUnavailableState("No DNS servers reported for the active network.")
                } else {
                    addressing.dnsServers.forEachIndexed { i, dns ->
                        NovaMetric("DNS ${i + 1}", dns)
                        if (i != addressing.dnsServers.lastIndex) Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }

        item {
            NovaSectionHeader("Diagnostics")
        }
        item {
            NovaCard(onClick = { onNavigate(NovaDestination.NetworkDiagnostics.route) }) {
                NovaListRow(
                    title = "Run Diagnostics",
                    subtitle = "Ping, jitter, DNS timing, connectivity, real throughput",
                    icon = { Icon(Icons.Filled.NetworkCheck, contentDescription = null, tint = NovaAccent) },
                    onClick = { onNavigate(NovaDestination.NetworkDiagnostics.route) }
                )
            }
        }
        item {
            NovaCard(onClick = { onNavigate(NovaDestination.NetworkLanScan.route) }) {
                NovaListRow(
                    title = "LAN Scan",
                    subtitle = "Find devices responding on your own network",
                    icon = { Icon(Icons.Filled.Router, contentDescription = null, tint = NovaAccent) },
                    onClick = { onNavigate(NovaDestination.NetworkLanScan.route) }
                )
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}
