package com.nova.app.feature.privacy

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** NOVA's own status for one of the runtime permissions it actually declares. */
data class SelfPermissionStatus(
    val label: String,
    val explanation: String,
    val granted: Boolean
)

/** An app (visible to NOVA) holding at least one of the sensitive permissions NOVA tracks. */
data class SensitiveAppEntry(
    val packageName: String,
    val label: String,
    val permissionLabels: List<String>
)

/** An app/component the OS reports as holding a special, user-granted access grant. */
data class SpecialAccessEntry(val packageName: String, val label: String)

data class PrivacySnapshot(
    val selfPermissions: List<SelfPermissionStatus>,
    val notificationListeners: List<SpecialAccessEntry>,
    val accessibilityServices: List<SpecialAccessEntry>,
    val sensitiveApps: List<SensitiveAppEntry>,
    val visibleAppCount: Int
)

/**
 * NOVA Privacy shows only what Android legitimately exposes to a normal, non-privileged app:
 *
 * - NOVA's own runtime permission grants (always fully readable — it's this app).
 * - System settings that list *other* apps by package name without needing any special
 *   permission: enabled notification listeners and enabled accessibility services. These are
 *   genuine, live, per-app grants — not guesses.
 * - A scan of installed apps' *declared and granted* dangerous permissions. Since Android 11
 *   (API 30), package visibility rules mean a normal app — NOVA included — can only see a
 *   subset of installed apps (itself, apps it has interacted with, and some system packages)
 *   unless it requests the QUERY_ALL_PACKAGES permission. NOVA deliberately does not request
 *   that permission: requiring a broad "see every app on the phone" grant just to power a
 *   privacy dashboard would be the opposite of privacy-first. The UI says so plainly rather
 *   than pretending the list is complete.
 *
 * Nothing here bypasses Android security or reads data unavailable to a normal app.
 */
class PrivacyRepository(private val context: Context) {

    private val pm = context.packageManager

    // Curated dangerous permissions NOVA explains in plain language and groups apps by.
    // Only permissions NOVA itself declares are checked for "self" status; all of them are
    // checked against other visible apps for the sensitive-app scan.
    private val trackedPermissions: LinkedHashMap<String, String> = linkedMapOf(
        Manifest.permission.CAMERA to "Camera",
        Manifest.permission.RECORD_AUDIO to "Microphone",
        Manifest.permission.ACCESS_FINE_LOCATION to "Precise location",
        Manifest.permission.ACCESS_COARSE_LOCATION to "Approximate location",
        Manifest.permission.READ_CONTACTS to "Contacts",
        Manifest.permission.READ_CALL_LOG to "Call log",
        Manifest.permission.READ_SMS to "SMS",
        Manifest.permission.READ_CALENDAR to "Calendar",
        Manifest.permission.BODY_SENSORS to "Body sensors",
        Manifest.permission.READ_EXTERNAL_STORAGE to "Storage"
    )

    private val explanations: Map<String, String> = mapOf(
        "Camera" to "Lets an app take photos or record video using the device camera.",
        "Microphone" to "Lets an app record audio through the device microphone.",
        "Precise location" to "Lets an app determine your exact GPS location.",
        "Approximate location" to "Lets an app determine your general location, such as your neighborhood.",
        "Contacts" to "Lets an app read the people saved in your contacts.",
        "Call log" to "Lets an app read your call history.",
        "SMS" to "Lets an app read your text messages.",
        "Calendar" to "Lets an app read events on your calendar.",
        "Body sensors" to "Lets an app read data from sensors that monitor your body, like a heart-rate sensor.",
        "Storage" to "Lets an app read files saved on your device."
    )

    /** NOVA's own permission status — always accurate, since this is NOVA's own package. */
    fun selfPermissions(): List<SelfPermissionStatus> {
        val ownRequested = try {
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions
        } catch (e: Exception) {
            null
        } ?: emptyArray()

        return trackedPermissions.entries
            .filter { (perm, _) -> ownRequested.contains(perm) }
            .map { (perm, label) ->
                SelfPermissionStatus(
                    label = label,
                    explanation = explanations[label].orEmpty(),
                    granted = ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
                )
            }
    }

    /** Apps with an active notification-listener grant — a real, system-wide, per-app list. */
    fun notificationListeners(): List<SpecialAccessEntry> =
        readComponentListSetting("enabled_notification_listeners")

    /** Apps with an active accessibility-service grant — a real, system-wide, per-app list. */
    fun accessibilityServices(): List<SpecialAccessEntry> =
        readComponentListSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    private fun readComponentListSetting(key: String): List<SpecialAccessEntry> {
        val flat = try {
            Settings.Secure.getString(context.contentResolver, key)
        } catch (e: Exception) {
            null
        } ?: return emptyList()

        return flat.split(":")
            .mapNotNull { component ->
                val pkg = component.substringBefore("/").trim()
                if (pkg.isEmpty()) return@mapNotNull null
                SpecialAccessEntry(pkg, resolveLabel(pkg) ?: pkg)
            }
            .distinctBy { it.packageName }
    }

    private fun resolveLabel(pkg: String): String? = try {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
        null
    }

    /**
     * Scans the apps actually visible to NOVA (see class doc for why that's a subset on modern
     * Android) and reports which of them hold granted, tracked dangerous permissions.
     * Returns the matching apps plus how many apps were visible to scan in total.
     */
    private fun sensitiveAppScan(): Pair<List<SensitiveAppEntry>, Int> {
        val apps = try {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (e: Exception) {
            emptyList()
        }

        val entries = mutableListOf<SensitiveAppEntry>()
        for (app in apps) {
            if (app.packageName == context.packageName) continue // NOVA reports itself separately, above
            try {
                val info = pm.getPackageInfo(app.packageName, PackageManager.GET_PERMISSIONS)
                val requested = info.requestedPermissions ?: continue
                @Suppress("DEPRECATION")
                val flags = info.requestedPermissionsFlags
                val granted = mutableListOf<String>()
                requested.forEachIndexed { i, permName ->
                    val label = trackedPermissions[permName] ?: return@forEachIndexed
                    val isGranted = flags != null && i < flags.size &&
                        (flags[i] and PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0
                    if (isGranted) granted.add(label)
                }
                if (granted.isNotEmpty()) {
                    entries.add(SensitiveAppEntry(app.packageName, pm.getApplicationLabel(app).toString(), granted.distinct()))
                }
            } catch (e: Exception) {
                // Package became invisible/uninstalled mid-scan, or details aren't readable — skip it.
            }
        }
        return entries.sortedBy { it.label.lowercase() } to apps.size
    }

    /** Runs the full scan off the main thread — installed-app enumeration can be non-trivial. */
    suspend fun snapshot(): PrivacySnapshot = withContext(Dispatchers.Default) {
        val (sensitiveApps, visibleCount) = sensitiveAppScan()
        PrivacySnapshot(
            selfPermissions = selfPermissions(),
            notificationListeners = notificationListeners(),
            accessibilityServices = accessibilityServices(),
            sensitiveApps = sensitiveApps,
            visibleAppCount = visibleCount
        )
    }

    /**
     * A cheap subset used by Deep Scan: just the counts, not the full per-app breakdown.
     * Still does the same underlying installed-apps enumeration, so it's run off-thread by the
     * caller and cached by ViewModels rather than re-run on every recomposition.
     */
    suspend fun quickSummary(): PrivacyQuickSummary = withContext(Dispatchers.Default) {
        val (sensitiveApps, _) = sensitiveAppScan()
        PrivacyQuickSummary(
            cameraOrMicAppCount = sensitiveApps.count { "Camera" in it.permissionLabels || "Microphone" in it.permissionLabels },
            notificationListenerCount = notificationListeners().size,
            accessibilityServiceCount = accessibilityServices().size
        )
    }
}

data class PrivacyQuickSummary(
    val cameraOrMicAppCount: Int,
    val notificationListenerCount: Int,
    val accessibilityServiceCount: Int
)
