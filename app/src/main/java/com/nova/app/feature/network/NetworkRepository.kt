package com.nova.app.feature.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URL
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class ConnectionType { WIFI, CELLULAR, ETHERNET, VPN, NONE, OTHER }

data class ConnectionInfo(
    val type: ConnectionType,
    val isConnected: Boolean,
    val isMetered: Boolean,
    val isValidated: Boolean
)

data class WifiDetails(
    val ssid: String?,
    val ssidHidden: Boolean, // true if SSID exists but is withheld pending location permission
    val linkSpeedMbps: Int?,
    val frequencyMhz: Int?,
    val rssiDbm: Int?,
    val signalBars: Int? // 0..4, from Android's own WifiManager.calculateSignalLevel
)

data class AddressingInfo(
    val localIpv4: String?,
    val localIpv6: List<String>,
    val gateway: String?,
    val dnsServers: List<String>,
    val subnetPrefixLength: Int?
)

data class PingResult(val target: String, val samples: List<Long?>) {
    val successful get() = samples.filterNotNull()
    val avgMs: Double? get() = successful.takeIf { it.isNotEmpty() }?.average()
    val minMs: Long? get() = successful.minOrNull()
    val maxMs: Long? get() = successful.maxOrNull()
    /** Jitter = average absolute difference between consecutive successful samples — a standard, honest definition. */
    val jitterMs: Double? get() {
        val s = successful
        if (s.size < 2) return null
        val diffs = s.zipWithNext { a, b -> Math.abs(a - b) }
        return diffs.average()
    }
    val packetLossPercent: Int get() = ((samples.size - successful.size) * 100 / samples.size.coerceAtLeast(1))
}

data class ThroughputResult(val mbps: Double, val bytesTransferred: Long, val durationMs: Long)

/**
 * Every value here is either read directly from an Android system API, or the result
 * of a real network operation this device performed just now. Nothing is estimated,
 * cached from elsewhere, or presented as more precise than it actually is.
 */
class NetworkRepository(private val context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    fun connectionInfo(): ConnectionInfo {
        val network = connectivityManager.activeNetwork
            ?: return ConnectionInfo(ConnectionType.NONE, false, isMetered = false, isValidated = false)
        val caps = connectivityManager.getNetworkCapabilities(network)
            ?: return ConnectionInfo(ConnectionType.NONE, false, isMetered = false, isValidated = false)

        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectionType.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectionType.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> ConnectionType.ETHERNET
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> ConnectionType.VPN
            else -> ConnectionType.OTHER
        }
        return ConnectionInfo(
            type = type,
            isConnected = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET),
            isMetered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED),
            isValidated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        )
    }

    /** hasLocationPermission gates whether Android will actually hand back a real SSID (OS-level restriction, not ours). */
    fun wifiDetails(hasLocationPermission: Boolean): WifiDetails? {
        val info = connectionInfo()
        if (info.type != ConnectionType.WIFI) return null

        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo ?: return null

        @Suppress("DEPRECATION")
        val rawSsid = wifiInfo.ssid
        val ssidWithheld = rawSsid == null || rawSsid == "<unknown ssid>" || rawSsid == "0x"
        val cleanSsid = rawSsid?.removeSurrounding("\"")

        @Suppress("DEPRECATION")
        val rssi = wifiInfo.rssi
        @Suppress("DEPRECATION")
        val bars = try { WifiManager.calculateSignalLevel(rssi, 5) } catch (e: Exception) { null }
        @Suppress("DEPRECATION")
        val linkSpeed = wifiInfo.linkSpeed.takeIf { it > 0 }
        @Suppress("DEPRECATION")
        val frequency = if (Build.VERSION.SDK_INT >= 21) wifiInfo.frequency else null

        return WifiDetails(
            ssid = if (ssidWithheld) null else cleanSsid,
            ssidHidden = ssidWithheld && !hasLocationPermission,
            linkSpeedMbps = linkSpeed,
            frequencyMhz = frequency,
            rssiDbm = rssi,
            signalBars = bars
        )
    }

    fun addressingInfo(): AddressingInfo {
        val network = connectivityManager.activeNetwork
        val linkProps: LinkProperties? = network?.let { connectivityManager.getLinkProperties(it) }

        val v4 = linkProps?.linkAddresses?.firstOrNull { it.address is Inet4Address }
        val v6 = linkProps?.linkAddresses?.filter { it.address is Inet6Address }?.map { it.address.hostAddress ?: "" }
            ?: emptyList()

        val gateway = linkProps?.routes
            ?.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress

        val dns = linkProps?.dnsServers?.map { it.hostAddress ?: "" } ?: emptyList()

        return AddressingInfo(
            localIpv4 = v4?.address?.hostAddress ?: localIpv4Fallback(),
            localIpv6 = v6.filter { it.isNotBlank() },
            gateway = gateway,
            dnsServers = dns.filter { it.isNotBlank() },
            subnetPrefixLength = v4?.prefixLength
        )
    }

    /** Fallback when LinkProperties isn't available (older API levels) — enumerates real interfaces, no guessing. */
    private fun localIpv4Fallback(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces().asSequence()
                .flatMap { it.inetAddresses.asSequence() }
                .firstOrNull { !it.isLoopbackAddress && it is Inet4Address }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    /** One real ICMP-equivalent reachability probe, precisely timed. Null = no response within timeout. */
    suspend fun pingOnce(host: String, timeoutMs: Int = 1500): Long? = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(host)
            val start = System.nanoTime()
            val reached = addr.isReachable(timeoutMs)
            val elapsedMs = (System.nanoTime() - start) / 1_000_000
            if (reached) elapsedMs else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pingSeries(host: String, count: Int = 6, timeoutMs: Int = 1500): PingResult {
        val samples = mutableListOf<Long?>()
        repeat(count) { samples += pingOnce(host, timeoutMs) }
        return PingResult(host, samples)
    }

    /** Real DNS resolution timing through the device's configured resolver — not a canned number. */
    suspend fun dnsLookupTimeMs(host: String): Long? = withContext(Dispatchers.IO) {
        try {
            val start = System.nanoTime()
            InetAddress.getByName(host)
            (System.nanoTime() - start) / 1_000_000
        } catch (e: Exception) {
            null
        }
    }

    /** Genuine HTTP reachability check — a 204 from a real server, not an assumption. */
    suspend fun checkInternetConnectivity(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://www.gstatic.com/generate_204")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            val code = conn.responseCode
            conn.disconnect()
            code == 204
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Real download throughput: streams actual bytes from Cloudflare's public speed-test
     * endpoint and times it. A live measurement, reported as a live measurement — not
     * a canonical/ISP-verified speed test, and the UI must say so.
     */
    suspend fun measureDownloadThroughput(bytes: Int = 3_000_000): ThroughputResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://speed.cloudflare.com/__down?bytes=$bytes")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 8000
            val start = System.nanoTime()
            var total = 0L
            conn.inputStream.use { stream ->
                val buffer = ByteArray(16 * 1024)
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                }
            }
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            conn.disconnect()
            val mbps = (total * 8.0) / (elapsedMs / 1000.0) / 1_000_000.0
            ThroughputResult(mbps, total, elapsedMs)
        } catch (e: Exception) {
            null
        }
    }

    /** Real upload throughput: actually writes bytes to Cloudflare's public speed-test endpoint. */
    suspend fun measureUploadThroughput(bytes: Int = 1_500_000): ThroughputResult? = withContext(Dispatchers.IO) {
        try {
            val url = URL("https://speed.cloudflare.com/__up")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 8000
            conn.doOutput = true
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/octet-stream")
            conn.setFixedLengthStreamingMode(bytes)
            val payload = ByteArray(16 * 1024)
            val start = System.nanoTime()
            var total = 0L
            conn.outputStream.use { out ->
                while (total < bytes) {
                    val chunk = minOf(payload.size.toLong(), bytes - total).toInt()
                    out.write(payload, 0, chunk)
                    total += chunk
                }
            }
            conn.responseCode // forces the request to complete
            val elapsedMs = ((System.nanoTime() - start) / 1_000_000).coerceAtLeast(1)
            conn.disconnect()
            val mbps = (total * 8.0) / (elapsedMs / 1000.0) / 1_000_000.0
            ThroughputResult(mbps, total, elapsedMs)
        } catch (e: Exception) {
            null
        }
    }

    /** Derives the /24 the phone is actually on from its own IP — scans the user's own LAN only. */
    fun localSubnetCandidates(): List<String>? {
        val ip = addressingInfo().localIpv4 ?: return null
        val parts = ip.split(".")
        if (parts.size != 4) return null
        val base = "${parts[0]}.${parts[1]}.${parts[2]}"
        return (1..254).map { "$base.$it" }
    }

    /** A fast, real TCP-connect probe (common service ports) used for the LAN scan — no OS ping binary required. */
    suspend fun probeHost(ip: String, timeoutMs: Int = 250): Boolean = withContext(Dispatchers.IO) {
        val ports = intArrayOf(80, 443, 22, 445, 8080)
        for (port in ports) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), timeoutMs)
                    return@withContext true
                }
            } catch (e: Exception) {
                // try next port
            }
        }
        // Fall back to ICMP-style reachability for hosts with no open probed ports.
        try {
            InetAddress.getByName(ip).isReachable(timeoutMs)
        } catch (e: Exception) {
            false
        }
    }
}

fun Double.roundTo1(): String = "%.1f".format(this)
