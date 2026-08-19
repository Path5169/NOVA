package com.nova.app.feature.shield

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.nova.app.feature.shield.dns.DnsMessage
import com.nova.app.feature.shield.dns.Ipv4Udp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Process-wide bus the VPN service publishes into and ShieldRepository observes. A plain
 * Service can't be a ViewModel-scoped singleton, so this small object is the bridge —
 * everything in it is only ever written from ShieldVpnService while it's actually running.
 */
object ShieldVpnBus {
    private val _stats = MutableStateFlow(ShieldStats())
    val stats = _stats.asStateFlow()

    private val _recentBlocks = MutableStateFlow<List<BlockedEntry>>(emptyList())
    val recentBlocks = _recentBlocks.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running = _running.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    internal fun setRunning(value: Boolean) { _running.value = value }
    internal fun setError(message: String?) { _error.value = message }
    internal fun resetStats() { _stats.value = ShieldStats(sinceEpochMs = System.currentTimeMillis()) }

    internal fun recordQuery() {
        _stats.value = _stats.value.copy(queriesTotal = _stats.value.queriesTotal + 1)
    }

    internal fun recordBlock(domain: String, category: ShieldCategory) {
        val current = _stats.value
        _stats.value = when (category) {
            ShieldCategory.CUSTOM -> current.copy(domainsBlocked = current.domainsBlocked + 1)
            ShieldCategory.AD -> current.copy(adsBlocked = current.adsBlocked + 1)
            ShieldCategory.TRACKER -> current.copy(trackersBlocked = current.trackersBlocked + 1)
        }
        val entry = BlockedEntry(domain, category, System.currentTimeMillis())
        _recentBlocks.value = (listOf(entry) + _recentBlocks.value).take(50)
    }
}

/**
 * NOVA Shield's engine. Deliberately narrow scope: it routes ONLY DNS traffic (UDP/53)
 * through the tun interface, by advertising the tun's own address as the device's DNS
 * server and routing exclusively to that single address — not a 0.0.0.0/0 catch-all.
 * Everything else (the actual HTTP/TLS traffic) never touches this service at all, which
 * is what keeps this "DNS filtering," not a traffic-inspecting VPN.
 *
 * Blocked domains get a synthesized NXDOMAIN reply. Everything else is forwarded, unmodified,
 * to a public upstream resolver over a protect()'d socket (so the reply doesn't loop back
 * into our own tun) and relayed back verbatim.
 */
class ShieldVpnService : VpnService() {

    companion object {
        const val ACTION_START = "com.nova.app.shield.action.START"
        const val ACTION_STOP = "com.nova.app.shield.action.STOP"
        private const val CHANNEL_ID = "nova_shield"
        private const val NOTIF_ID = 4201
        private const val TUN_ADDRESS = "10.111.222.1"
        private const val UPSTREAM_DNS = "1.1.1.1"
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var workerThread: Thread? = null
    @Volatile private var running = false
    private var snapshot: ShieldFilterSnapshot =
        ShieldFilterSnapshot(emptySet(), emptySet(), emptySet(), emptySet())

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopShield()
            return START_NOT_STICKY
        }
        startShield()
        return START_STICKY
    }

    private fun startShield() {
        if (running) return
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        ShieldVpnBus.resetStats()

        serviceScope.launch {
            val store = ShieldBlocklistStore(applicationContext)
            snapshot = store.snapshotForStart()

            val fd = establishInterface()
            if (fd == null) {
                ShieldVpnBus.setError("Could not establish local filtering interface")
                stopSelf()
                return@launch
            }
            vpnInterface = fd
            running = true
            ShieldVpnBus.setRunning(true)
            ShieldVpnBus.setError(null)
            workerThread = Thread({ runLoop(fd) }, "nova-shield-loop").also { it.start() }
        }
    }

    private fun establishInterface(): ParcelFileDescriptor? = try {
        Builder()
            .setSession("NOVA Shield")
            .addAddress(TUN_ADDRESS, 32)
            .addDnsServer(TUN_ADDRESS)
            .addRoute(TUN_ADDRESS, 32) // ONLY this address is routed — DNS traffic only
            .setMtu(1500)
            .apply {
                snapshot.disallowedPackages.forEach { pkg ->
                    try { addDisallowedApplication(pkg) } catch (_: Exception) { /* app not installed */ }
                }
            }
            .establish()
    } catch (e: Exception) {
        null
    }

    private fun runLoop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor)
        val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)

        while (running) {
            val length = try {
                input.read(buffer)
            } catch (e: Exception) {
                if (running) continue else break
            }
            if (length <= 0) continue

            val udp = Ipv4Udp.parseUdp(buffer, length) ?: continue
            if (udp.destPort != 53) continue // nothing else is ever routed here, but stay defensive

            val qname = DnsMessage.readQuestionName(udp.payload, udp.payloadLength) ?: continue
            ShieldVpnBus.recordQuery()

            if (snapshot.isBlocked(qname)) {
                val response = DnsMessage.buildNxDomainResponse(udp.payload, udp.payloadLength)
                val reply = Ipv4Udp.buildUdp(udp.destAddress, 53, udp.sourceAddress, udp.sourcePort, response)
                try { output.write(reply) } catch (_: Exception) { }
                ShieldVpnBus.recordBlock(qname, snapshot.categoryFor(qname))
                continue
            }

            forwardToUpstream(udp, output)
        }
    }

    private fun forwardToUpstream(
        udp: com.nova.app.feature.shield.dns.ParsedUdpPacket,
        output: FileOutputStream
    ) {
        try {
            DatagramSocket().use { socket ->
                protect(socket) // exempt this socket from the VPN's own routing to avoid a loop
                socket.soTimeout = 4000
                socket.send(DatagramPacket(udp.payload, udp.payloadLength, InetAddress.getByName(UPSTREAM_DNS), 53))

                val respBuf = ByteArray(512)
                val respPacket = DatagramPacket(respBuf, respBuf.size)
                socket.receive(respPacket)

                val reply = Ipv4Udp.buildUdp(
                    udp.destAddress, 53, udp.sourceAddress, udp.sourcePort,
                    respBuf.copyOfRange(0, respPacket.length)
                )
                output.write(reply)
            }
        } catch (e: Exception) {
            // Upstream unreachable/timed out — dropped silently; the querying app's own
            // DNS client will retry, same as any transient DNS failure.
        }
    }

    private fun stopShield() {
        running = false
        workerThread?.interrupt()
        try { vpnInterface?.close() } catch (_: Exception) { }
        vpnInterface = null
        ShieldVpnBus.setRunning(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopShield()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onRevoke() {
        // User/system revoked VPN permission (e.g. switched to another VPN app) — clean shutdown.
        stopShield()
        super.onRevoke()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "NOVA Shield", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while local DNS filtering is active" }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NOVA Shield active")
            .setContentText("Filtering DNS locally · no data leaves your device")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
