package com.nova.app.feature.shield

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.shieldDataStore by preferencesDataStore(name = "nova_shield_prefs")

private val KEY_CUSTOM_BLOCKED = stringSetPreferencesKey("shield_custom_blocked")
private val KEY_ALLOWED = stringSetPreferencesKey("shield_allowed")
private val KEY_PER_APP_DISALLOWED = stringSetPreferencesKey("shield_disallowed_packages")

/**
 * Owns three lists, all local (DataStore, on-device only, never synced anywhere):
 *  - the bundled starter blocklist (read-only asset, shipped with the app)
 *  - a user-editable custom blocklist (domains the user adds themselves)
 *  - an allowlist (domains exempted even if they'd otherwise match a blocklist)
 * Also tracks which installed apps are excluded from Shield's DNS routing entirely
 * (VpnService.Builder#addDisallowedApplication), NOVA Shield's "per-app control."
 */
class ShieldBlocklistStore(private val context: Context) {

    private var bundledCache: Set<String>? = null

    /** Loads the bundled starter list from assets once and caches it in memory. */
    suspend fun bundledBlocklist(): Set<String> {
        bundledCache?.let { return it }
        val set = try {
            context.assets.open("shield_blocklist.txt").bufferedReader().useLines { lines ->
                lines
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
                    .map { it.lowercase() }
                    .toSet()
            }
        } catch (e: Exception) {
            emptySet()
        }
        bundledCache = set
        return set
    }

    fun customBlockedFlow(): Flow<Set<String>> =
        context.shieldDataStore.data.map { it[KEY_CUSTOM_BLOCKED] ?: emptySet() }

    fun allowedFlow(): Flow<Set<String>> =
        context.shieldDataStore.data.map { it[KEY_ALLOWED] ?: emptySet() }

    fun disallowedPackagesFlow(): Flow<Set<String>> =
        context.shieldDataStore.data.map { it[KEY_PER_APP_DISALLOWED] ?: emptySet() }

    suspend fun addCustomBlocked(domain: String) {
        val cleaned = domain.trim().lowercase()
        if (cleaned.isEmpty()) return
        context.shieldDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_BLOCKED] ?: emptySet()
            prefs[KEY_CUSTOM_BLOCKED] = current + cleaned
        }
    }

    suspend fun removeCustomBlocked(domain: String) {
        context.shieldDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_BLOCKED] ?: emptySet()
            prefs[KEY_CUSTOM_BLOCKED] = current - domain
        }
    }

    suspend fun addAllowed(domain: String) {
        val cleaned = domain.trim().lowercase()
        if (cleaned.isEmpty()) return
        context.shieldDataStore.edit { prefs ->
            val current = prefs[KEY_ALLOWED] ?: emptySet()
            prefs[KEY_ALLOWED] = current + cleaned
        }
    }

    suspend fun removeAllowed(domain: String) {
        context.shieldDataStore.edit { prefs ->
            val current = prefs[KEY_ALLOWED] ?: emptySet()
            prefs[KEY_ALLOWED] = current - domain
        }
    }

    suspend fun setPackageDisallowed(packageName: String, disallowed: Boolean) {
        context.shieldDataStore.edit { prefs ->
            val current = prefs[KEY_PER_APP_DISALLOWED] ?: emptySet()
            prefs[KEY_PER_APP_DISALLOWED] = if (disallowed) current + packageName else current - packageName
        }
    }

    /** Snapshot of everything needed to start the VPN service, read once at start time. */
    suspend fun snapshotForStart(): ShieldFilterSnapshot {
        return ShieldFilterSnapshot(
            bundled = bundledBlocklist(),
            custom = customBlockedFlow().first(),
            allowed = allowedFlow().first(),
            disallowedPackages = disallowedPackagesFlow().first()
        )
    }
}

data class ShieldFilterSnapshot(
    val bundled: Set<String>,
    val custom: Set<String>,
    val allowed: Set<String>,
    val disallowedPackages: Set<String>
) {
    /** True if [domain] (or a parent domain of it) should be blocked. */
    fun isBlocked(domain: String): Boolean {
        if (isAllowed(domain)) return false
        return matchesAnySuffix(domain, bundled) || matchesAnySuffix(domain, custom)
    }

    fun isAllowed(domain: String): Boolean = matchesAnySuffix(domain, allowed)

    /** True if [domain] equals or is a subdomain of anything in [set] (e.g. "x.doubleclick.net"
     * matches a "doubleclick.net" entry). */
    private fun matchesAnySuffix(domain: String, set: Set<String>): Boolean {
        if (set.isEmpty()) return false
        var d = domain
        while (true) {
            if (d in set) return true
            val dot = d.indexOf('.')
            if (dot < 0) return false
            d = d.substring(dot + 1)
        }
    }

    /** Bundled-list hits are counted as "ads/trackers"; custom-list hits as "domains blocked"
     * in the dashboard, matching the three counters in the Shield UI spec. */
    fun categoryFor(domain: String): ShieldCategory = when {
        matchesAnySuffix(domain, custom) -> ShieldCategory.CUSTOM
        else -> ShieldCategory.AD
    }
}
