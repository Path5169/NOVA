package com.nova.app.feature.appspace

/** One row in the App Space list — cheap to load for every visible app. */
data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val enabled: Boolean
)

/** A single granted or requested permission, resolved against this specific app. */
data class AppPermissionEntry(
    val name: String,
    val label: String,
    val granted: Boolean,
    val isDangerous: Boolean
)

/** Real, per-app "usage access" figures — only populated when the special access is granted. */
data class AppUsageStats(
    val lastUsedMillis: Long?,
    val foregroundTimeTodayMillis: Long
)

data class AppWarning(val label: String, val detail: String)

/**
 * Everything NOVA shows on an App Profile screen. Every field traces back to a real
 * PackageManager/ApplicationInfo/StorageStats read for this exact package — nothing here is
 * estimated or invented. Fields that Android does not expose to a normal app for other
 * packages (live battery/network attribution) are deliberately absent rather than faked.
 */
data class AppProfile(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val firstInstallMillis: Long,
    val lastUpdateMillis: Long,
    val targetSdk: Int,
    val minSdk: Int,
    val isSystemApp: Boolean,
    val enabled: Boolean,
    val apkSizeBytes: Long?,
    val permissions: List<AppPermissionEntry>,
    val usageStats: AppUsageStats?,
    /** Null when the user hasn't granted NOVA the Usage Access special permission. */
    val usageAccessGranted: Boolean,
    /** 0..100 composite index — see [AppSpaceRepository.computeIndex] for exactly what feeds it. */
    val index: Int,
    val warnings: List<AppWarning>
)

enum class NovaCheckStatus { OK, WARN, UNAVAILABLE }

data class AppDiagnosticCheck(val label: String, val status: NovaCheckStatus, val detail: String)

data class AppDiagnosticReport(val checks: List<AppDiagnosticCheck>, val warningCount: Int)
