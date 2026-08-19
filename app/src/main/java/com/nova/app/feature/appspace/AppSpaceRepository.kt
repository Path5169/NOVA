package com.nova.app.feature.appspace

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.io.File
import java.util.Calendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * NOVA App Space reads only what a normal, non-privileged app can read about other apps:
 *
 * - Basic package metadata (name, version, install/update time, target/min SDK, enabled state)
 *   via [PackageManager] — always available for any visible package.
 * - Whether a specific permission is *granted* to another app, via [PackageManager.checkPermission].
 *   This is a public read — it does not require holding that permission yourself.
 * - APK size on disk, read directly from the installed APK file — normally world-readable.
 * - Per-app usage stats (last used, foreground time), but ONLY if the user has separately
 *   granted NOVA the "Usage access" special permission — the same category of opt-in grant
 *   NOVA's Privacy module already uses for notification listeners and accessibility services.
 *   Without that grant, [AppProfile.usageStats] is simply null; NOVA never estimates it.
 *
 * Same package-visibility rule as Privacy: since Android 11, only packages NOVA has interacted
 * with, or that are visible via `<queries>`, show up without QUERY_ALL_PACKAGES — which NOVA
 * deliberately does not request. The App Space list is built from the same visible set Privacy
 * already uses, not a claim of "every app on this phone."
 */
class AppSpaceRepository(private val context: Context) {

    private val pm = context.packageManager

    private val dangerousPermissions: Map<String, String> = mapOf(
        android.Manifest.permission.CAMERA to "Camera",
        android.Manifest.permission.RECORD_AUDIO to "Microphone",
        android.Manifest.permission.ACCESS_FINE_LOCATION to "Precise location",
        android.Manifest.permission.ACCESS_COARSE_LOCATION to "Approximate location",
        android.Manifest.permission.READ_CONTACTS to "Contacts",
        android.Manifest.permission.READ_CALL_LOG to "Call log",
        android.Manifest.permission.READ_SMS to "SMS",
        android.Manifest.permission.READ_CALENDAR to "Calendar",
        android.Manifest.permission.BODY_SENSORS to "Body sensors",
        android.Manifest.permission.READ_EXTERNAL_STORAGE to "Storage"
    )

    suspend fun listVisibleApps(): List<AppEntry> = withContext(Dispatchers.IO) {
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.packageName != context.packageName }
            .map { info ->
                AppEntry(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    enabled = info.enabled
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun hasUsageAccess(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun requestUsageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun appInfoSettingsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

    fun launchIntentFor(packageName: String): Intent? = pm.getLaunchIntentForPackage(packageName)

    suspend fun loadProfile(packageName: String): AppProfile? = withContext(Dispatchers.IO) {
        val packageInfo: PackageInfo = try {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            return@withContext null
        }
        val appInfo = packageInfo.applicationInfo ?: return@withContext null

        val apkSize: Long? = try {
            appInfo.sourceDir?.let { File(it).length() }
        } catch (e: Exception) {
            null
        }

        val requested = packageInfo.requestedPermissions.orEmpty()
        val permissions = requested.map { permName ->
            val granted = pm.checkPermission(permName, packageName) == PackageManager.PERMISSION_GRANTED
            val friendly = dangerousPermissions[permName]
            AppPermissionEntry(
                name = permName,
                label = friendly ?: permName.substringAfterLast('.'),
                granted = granted,
                isDangerous = friendly != null
            )
        }.sortedByDescending { it.isDangerous }

        val usageGranted = hasUsageAccess()
        val usageStats = if (usageGranted) readUsageStats(packageName) else null

        val warnings = mutableListOf<AppWarning>()
        val dangerousGranted = permissions.count { it.isDangerous && it.granted }
        if (dangerousGranted >= 4) {
            warnings += AppWarning("Broad permission access", "Holds $dangerousGranted sensitive permissions.")
        }
        if (!appInfo.enabled) {
            warnings += AppWarning("App disabled", "This app is currently disabled by the system or user.")
        }
        val daysSinceUpdate = (System.currentTimeMillis() - packageInfo.lastUpdateTime) / 86_400_000L
        if (daysSinceUpdate > 365) {
            warnings += AppWarning("Not updated in a while", "Last updated over a year ago.")
        }
        if (apkSize != null && apkSize > 300L * 1024 * 1024) {
            warnings += AppWarning("Large install size", "Takes up ${apkSize / (1024 * 1024)} MB on disk.")
        }

        val index = computeIndex(
            dangerousGranted = dangerousGranted,
            enabled = appInfo.enabled,
            daysSinceUpdate = daysSinceUpdate,
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        )

        AppProfile(
            packageName = packageName,
            label = appInfo.loadLabel(pm).toString(),
            versionName = packageInfo.versionName,
            versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) packageInfo.longVersionCode else @Suppress("DEPRECATION") packageInfo.versionCode.toLong(),
            firstInstallMillis = packageInfo.firstInstallTime,
            lastUpdateMillis = packageInfo.lastUpdateTime,
            targetSdk = appInfo.targetSdkVersion,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) appInfo.minSdkVersion else 0,
            isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            enabled = appInfo.enabled,
            apkSizeBytes = apkSize,
            permissions = permissions,
            usageStats = usageStats,
            usageAccessGranted = usageGranted,
            index = index,
            warnings = warnings
        )
    }

    /**
     * A transparent composite index — NOT a claim about real performance Android does not
     * expose. Built entirely from the fields above: fewer sensitive permissions, an enabled
     * state, and recent updates score higher. Documented in-UI so it never reads as a
     * mysterious "health %".
     */
    private fun computeIndex(dangerousGranted: Int, enabled: Boolean, daysSinceUpdate: Long, isSystemApp: Boolean): Int {
        var score = 100
        score -= (dangerousGranted * 8).coerceAtMost(48)
        if (!enabled) score -= 30
        if (daysSinceUpdate > 365) score -= 10
        if (daysSinceUpdate > 730) score -= 10
        return score.coerceIn(0, 100)
    }

    private fun readUsageStats(packageName: String): AppUsageStats {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val startOfDay = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val statsToday = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startOfDay, now)
        val todayStat = statsToday?.firstOrNull { it.packageName == packageName }

        // Look back 30 days for a genuine "last used" instead of just today's window.
        val statsLong = usm.queryUsageStats(UsageStatsManager.INTERVAL_MONTHLY, now - 30L * 86_400_000L, now)
        val longStat = statsLong?.filter { it.packageName == packageName }?.maxByOrNull { it.lastTimeUsed }

        return AppUsageStats(
            lastUsedMillis = longStat?.lastTimeUsed?.takeIf { it > 0 },
            foregroundTimeTodayMillis = todayStat?.totalTimeInForeground ?: 0L
        )
    }

    suspend fun runDiagnostic(packageName: String): AppDiagnosticReport = withContext(Dispatchers.IO) {
        val checks = mutableListOf<AppDiagnosticCheck>()

        val packageInfo = try {
            pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        if (packageInfo == null) {
            checks += AppDiagnosticCheck("Package readable", NovaCheckStatus.WARN, "NOVA can no longer read this package — it may have been uninstalled.")
            return@withContext AppDiagnosticReport(checks, checks.count { it.status == NovaCheckStatus.WARN })
        }

        checks += AppDiagnosticCheck("Package integrity", NovaCheckStatus.OK, "Package metadata read successfully.")
        checks += AppDiagnosticCheck(
            "Version",
            NovaCheckStatus.OK,
            packageInfo.versionName?.let { "Version $it installed." } ?: "Version name not declared by this app."
        )

        val appInfo = packageInfo.applicationInfo
        if (appInfo != null) {
            checks += AppDiagnosticCheck(
                "App status",
                if (appInfo.enabled) NovaCheckStatus.OK else NovaCheckStatus.WARN,
                if (appInfo.enabled) "Enabled and launchable." else "Currently disabled."
            )

            val apkReadable = try {
                appInfo.sourceDir?.let { File(it).canRead() } == true
            } catch (e: Exception) {
                false
            }
            checks += AppDiagnosticCheck(
                "Storage",
                if (apkReadable) NovaCheckStatus.OK else NovaCheckStatus.UNAVAILABLE,
                if (apkReadable) "Install files present and readable." else "Install size unavailable for this package."
            )
        }

        val requested = packageInfo.requestedPermissions.orEmpty()
        val grantedDangerous = requested.count { p ->
            dangerousPermissions.containsKey(p) && pm.checkPermission(p, packageName) == PackageManager.PERMISSION_GRANTED
        }
        checks += AppDiagnosticCheck(
            "Permissions",
            if (grantedDangerous >= 4) NovaCheckStatus.WARN else NovaCheckStatus.OK,
            if (grantedDangerous == 0) "No sensitive permissions granted." else "$grantedDangerous sensitive permission(s) granted."
        )

        checks += AppDiagnosticCheck(
            "Usage data",
            if (hasUsageAccess()) NovaCheckStatus.OK else NovaCheckStatus.UNAVAILABLE,
            if (hasUsageAccess()) "Usage access granted — last-used data available." else "Usage access not granted — enable it in App Space settings for last-used data."
        )

        AppDiagnosticReport(checks, checks.count { it.status == NovaCheckStatus.WARN })
    }
}
