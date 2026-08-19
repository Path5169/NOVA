package com.nova.app.feature.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.net.InetAddress

@Composable
fun LanScanScreen() {
    val context = LocalContext.current
    val repository = remember { NetworkRepository(context) }
    val scope = rememberCoroutineScope()

    var scanning by remember { mutableStateOf(false) }
    var scanned by remember { mutableStateOf(0) }
    var total by remember { mutableStateOf(0) }
    var found by remember { mutableStateOf(listOf<Pair<String, String?>>()) } // ip, hostname if resolved

    val candidates = remember { repository.localSubnetCandidates() }

    fun startScan() {
        val hosts = candidates ?: return
        scope.launch {
            scanning = true
            scanned = 0
            total = hosts.size
            found = emptyList()
            val results = mutableListOf<Pair<String, String?>>()

            // Bounded concurrency so this stays responsive on mid-range phones — real probes, just parallelized.
            val chunkSize = 24
            for (chunk in hosts.chunked(chunkSize)) {
                val deferred = chunk.map { ip ->
                    scope.async(Dispatchers.IO) {
                        val alive = repository.probeHost(ip)
                        scanned++
                        if (alive) {
                            val hostname = try {
                                InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
                            } catch (e: Exception) {
                                null
                            }
                            ip to hostname
                        } else null
                    }
                }
                val chunkResults = deferred.awaitAll().filterNotNull()
                results += chunkResults
                found = results.sortedBy { it.first.split(".").last().toIntOrNull() ?: 0 }
            }
            scanning = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("LAN Scan", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Probes your own subnet only — real TCP connect attempts, nothing invented.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }

        if (candidates == null) {
            item { NovaUnavailableState("No local IPv4 address available — connect to Wi-Fi or a LAN to scan.") }
        } else {
            item {
                NovaCard(onClick = { if (!scanning) startScan() }) {
                    if (!scanning) {
                        Text("SCAN ${candidates.size} ADDRESSES", style = MaterialTheme.typography.labelLarge, color = NovaAccent)
                    } else {
                        NovaLoadingState("Scanning… $scanned / $total")
                        Spacer(Modifier.height(10.dp))
                        NovaProgressBar(if (total > 0) scanned.toFloat() / total else 0f)
                    }
                }
            }

            if (found.isNotEmpty()) {
                item { NovaSectionHeader("Responding hosts", trailing = "${found.size} found") }
                items(found) { (ip, hostname) ->
                    NovaCard {
                        NovaMetric(hostname ?: ip, if (hostname != null) ip else "", status = NovaStatus.GOOD)
                    }
                }
            } else if (!scanning && scanned > 0) {
                item { NovaUnavailableState("No hosts responded on common ports — some devices don't answer any probe.") }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}
