package com.nova.app.feature.home

import android.content.Context
import com.nova.app.feature.device.DeviceInfoRepository
import com.nova.app.feature.lab.SensorRepository
import com.nova.app.feature.network.ConnectionType
import com.nova.app.feature.network.NetworkRepository
import com.nova.app.feature.privacy.PrivacyRepository
import kotlinx.coroutines.delay

data class ScanFinding(val label: String, val detail: String, val severity: Severity) {
    enum class Severity { GOOD, WARN }
}

data class ScanReport(
    val deviceStatus: String,
    val storageStatus: String,
    val batteryStatus: String,
    val networkStatus: String,
    val privacyStatus: String,
    val sensorsAvailable: Int,
    val sensorsKnown: Int,
    val findings: List<ScanFinding>
)

/**
 * Runs the diagnostics that are safe and available right now, without asking for any new
 * permission and without meaningful background activity. As of Phase 3 this covers Device,
 * Lab (sensor availability), Network (connection state only — no live ping/throughput test,
 * which stays a deliberate, user-initiated action in NOVA Network), and Privacy (a same
 * app-visibility-limited summary as the Privacy dashboard — see PrivacyRepository).
 * Every status here reflects something actually read from an Android API this run.
 */
class DeepScanRepository(context: Context) {

    private val deviceRepo = DeviceInfoRepository(context)
    private val sensorRepo = SensorRepository(context)
    private val networkRepo = NetworkRepository(context)
    private val privacyRepo = PrivacyRepository(context)

    suspend fun runScan(onProgress: (Float) -> Unit): ScanReport {
        onProgress(0.08f)
        delay(120)
        val snapshot = deviceRepo.snapshot()
        onProgress(0.3f)
        delay(120)
        val sensors = sensorRepo.allSensors()
        onProgress(0.5f)
        delay(120)
        val connection = networkRepo.connectionInfo()
        onProgress(0.65f)
        delay(120)
        val privacySummary = privacyRepo.quickSummary()
        onProgress(0.9f)
        delay(120)

        val findings = mutableListOf<ScanFinding>()

        val storageStatus: String
        if (snapshot.storage.freeGb < 1.0) {
            findings.add(ScanFinding("Storage low", "%.1f GB free — consider clearing space.".format(snapshot.storage.freeGb), ScanFinding.Severity.WARN))
            storageStatus = "Low"
        } else {
            storageStatus = "Normal"
        }

        val batteryStatus: String
        val pct = snapshot.battery.percent
        if (pct != null && pct < 15 && snapshot.battery.isCharging != true) {
            findings.add(ScanFinding("Battery low", "$pct% remaining and not charging.", ScanFinding.Severity.WARN))
            batteryStatus = "Low"
        } else {
            batteryStatus = "Normal"
        }

        if (snapshot.memory.lowMemory) {
            findings.add(ScanFinding("RAM pressure", "System reports low available memory right now.", ScanFinding.Severity.WARN))
        }

        val networkStatus: String
        if (!connection.isConnected || connection.type == ConnectionType.NONE) {
            findings.add(ScanFinding("No network connection", "The device isn't currently connected. Some modules need connectivity to work.", ScanFinding.Severity.WARN))
            networkStatus = "None"
        } else if (!connection.isValidated) {
            findings.add(ScanFinding("Connection unvalidated", "Connected, but Android hasn't confirmed real internet reachability yet.", ScanFinding.Severity.WARN))
            networkStatus = "Limited"
        } else {
            networkStatus = "Good"
        }

        val privacyStatus: String
        if (privacySummary.notificationListenerCount + privacySummary.accessibilityServiceCount > 0) {
            findings.add(
                ScanFinding(
                    "Special access granted",
                    "${privacySummary.notificationListenerCount} app(s) with notification access, " +
                        "${privacySummary.accessibilityServiceCount} accessibility service(s) enabled. Review in NOVA Privacy.",
                    ScanFinding.Severity.WARN
                )
            )
            privacyStatus = "Review"
        } else {
            privacyStatus = "Normal"
        }

        val deviceStatus = if (findings.any { it.severity == ScanFinding.Severity.WARN }) "Attention" else "Healthy"

        onProgress(1f)
        return ScanReport(
            deviceStatus = deviceStatus,
            storageStatus = storageStatus,
            batteryStatus = batteryStatus,
            networkStatus = networkStatus,
            privacyStatus = privacyStatus,
            sensorsAvailable = sensors.size,
            sensorsKnown = DeviceInfoRepository.KNOWN_SENSOR_TYPES.size,
            findings = findings
        )
    }
}
