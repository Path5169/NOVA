package com.nova.app.feature.network

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import kotlinx.coroutines.launch

private enum class Stage { IDLE, PING, DNS, CONNECTIVITY, DOWNLOAD, UPLOAD, DONE }

@Composable
fun NetworkDiagnosticsScreen() {
    val context = LocalContext.current
    val repository = remember { NetworkRepository(context) }
    val scope = rememberCoroutineScope()

    var stage by remember { mutableStateOf(Stage.IDLE) }
    var pingResult by remember { mutableStateOf<PingResult?>(null) }
    var dnsMs by remember { mutableStateOf<Long?>(null) }
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var download by remember { mutableStateOf<ThroughputResult?>(null) }
    var upload by remember { mutableStateOf<ThroughputResult?>(null) }

    fun runAll() {
        scope.launch {
            pingResult = null; dnsMs = null; connected = null; download = null; upload = null

            stage = Stage.PING
            pingResult = repository.pingSeries("1.1.1.1", count = 6)

            stage = Stage.DNS
            dnsMs = repository.dnsLookupTimeMs("cloudflare.com")

            stage = Stage.CONNECTIVITY
            connected = repository.checkInternetConnectivity()

            if (connected == true) {
                stage = Stage.DOWNLOAD
                download = repository.measureDownloadThroughput()

                stage = Stage.UPLOAD
                upload = repository.measureUploadThroughput()
            }

            stage = Stage.DONE
        }
    }

    val progress = when (stage) {
        Stage.IDLE -> 0f
        Stage.PING -> 0.15f
        Stage.DNS -> 0.35f
        Stage.CONNECTIVITY -> 0.5f
        Stage.DOWNLOAD -> 0.7f
        Stage.UPLOAD -> 0.9f
        Stage.DONE -> 1f
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Network Diagnostics", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Every number below is a live measurement made just now, not a lookup or an estimate.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }

        item {
            NovaCard(onClick = { if (stage == Stage.IDLE || stage == Stage.DONE) runAll() }) {
                if (stage == Stage.IDLE) {
                    Text("RUN DIAGNOSTICS", style = MaterialTheme.typography.labelLarge, color = NovaAccent)
                } else if (stage != Stage.DONE) {
                    NovaLoadingState(
                        when (stage) {
                            Stage.PING -> "Pinging 1.1.1.1 (6 samples)…"
                            Stage.DNS -> "Timing DNS resolution…"
                            Stage.CONNECTIVITY -> "Checking internet reachability…"
                            Stage.DOWNLOAD -> "Measuring download throughput…"
                            Stage.UPLOAD -> "Measuring upload throughput…"
                            else -> "Running…"
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    NovaProgressBar(progress)
                } else {
                    Text("RUN AGAIN", style = MaterialTheme.typography.labelLarge, color = NovaAccent)
                }
            }
        }

        pingResult?.let { r ->
            item {
                NovaCard {
                    NovaSectionHeader("Ping — ${r.target}")
                    Spacer(Modifier.height(12.dp))
                    if (r.successful.isEmpty()) {
                        NovaUnavailableState("No response from ${r.target} within the timeout.")
                    } else {
                        NovaMetric(
                            "Latency (avg)",
                            "%.0f".format(r.avgMs ?: 0.0),
                            unit = "ms",
                            status = when {
                                (r.avgMs ?: 999.0) < 60 -> NovaStatus.GOOD
                                (r.avgMs ?: 999.0) < 150 -> NovaStatus.WARN
                                else -> NovaStatus.BAD
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        NovaMetric("Min / Max", "${r.minMs ?: "—"} / ${r.maxMs ?: "—"}", unit = "ms")
                        Spacer(Modifier.height(8.dp))
                        NovaMetric(
                            "Jitter",
                            r.jitterMs?.let { "%.1f".format(it) } ?: "—",
                            unit = "ms",
                            status = when {
                                r.jitterMs == null -> NovaStatus.UNAVAILABLE
                                else -> when (val jitter = r.jitterMs!!) {
                                    in 0.0..10.0 -> NovaStatus.GOOD
                                    in 10.0..30.0 -> NovaStatus.WARN
                                    else -> NovaStatus.BAD
                                }
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        NovaMetric(
                            "Packet loss",
                            "${r.packetLossPercent}",
                            unit = "% (${r.samples.size} sent)",
                            status = if (r.packetLossPercent == 0) NovaStatus.GOOD else NovaStatus.WARN
                        )
                    }
                }
            }
        }

        dnsMs?.let { ms ->
            item {
                NovaCard {
                    NovaSectionHeader("DNS resolution")
                    Spacer(Modifier.height(12.dp))
                    NovaMetric(
                        "cloudflare.com lookup",
                        "$ms",
                        unit = "ms",
                        status = if (ms < 200) NovaStatus.GOOD else NovaStatus.WARN
                    )
                }
            }
        }

        connected?.let { ok ->
            item {
                NovaCard {
                    NovaSectionHeader("Internet reachability")
                    Spacer(Modifier.height(12.dp))
                    NovaMetric(
                        "204 check",
                        if (ok) "Reachable" else "Unreachable",
                        status = if (ok) NovaStatus.GOOD else NovaStatus.BAD
                    )
                    if (!ok) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Connected to a network but no route to the open internet — throughput tests skipped.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovaTextPrimary.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        if (download != null || upload != null) {
            item {
                NovaCard {
                    NovaSectionHeader("Throughput")
                    Spacer(Modifier.height(12.dp))
                    download?.let {
                        NovaMetric("Download", it.mbps.roundTo1(), unit = "Mbps")
                        Spacer(Modifier.height(8.dp))
                    }
                    upload?.let {
                        NovaMetric("Upload", it.mbps.roundTo1(), unit = "Mbps")
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        "Single-connection test against a public speed-test endpoint over a few seconds — a real reading, not a certified ISP-grade result.",
                        style = MaterialTheme.typography.labelSmall,
                        color = NovaTextPrimary.copy(alpha = 0.4f)
                    )
                }
            }
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}
