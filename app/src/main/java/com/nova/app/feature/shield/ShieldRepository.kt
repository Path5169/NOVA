package com.nova.app.feature.shield

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

private val Context.shieldStatsStore by preferencesDataStore(name = "nova_shield_stats")

private val KEY_ADS = longPreferencesKey("cumulative_ads")
private val KEY_TRACKERS = longPreferencesKey("cumulative_trackers")
private val KEY_DOMAINS = longPreferencesKey("cumulative_domains")
private val KEY_QUERIES = longPreferencesKey("cumulative_queries")

/**
 * Bridges the UI layer to [ShieldVpnService]. NOVA Shield's counters are cumulative
 * (all-time, on this device, never uploaded) — this repository merges whatever the running
 * service has counted this session on top of the persisted all-time baseline, and folds the
 * session into the baseline whenever the service stops, so nothing is lost between runs.
 */
class ShieldRepository(private val context: Context) {

    val blocklistStore = ShieldBlocklistStore(context)

    private fun persistedFlow(): Flow<ShieldStats> = context.shieldStatsStore.data.map { prefs ->
        ShieldStats(
            adsBlocked = prefs[KEY_ADS] ?: 0,
            trackersBlocked = prefs[KEY_TRACKERS] ?: 0,
            domainsBlocked = prefs[KEY_DOMAINS] ?: 0,
            queriesTotal = prefs[KEY_QUERIES] ?: 0
        )
    }

    /** All-time totals = persisted baseline + whatever the current session has counted so far. */
    val stats: Flow<ShieldStats> = combine(persistedFlow(), ShieldVpnBus.stats) { persisted, session ->
        ShieldStats(
            adsBlocked = persisted.adsBlocked + session.adsBlocked,
            trackersBlocked = persisted.trackersBlocked + session.trackersBlocked,
            domainsBlocked = persisted.domainsBlocked + session.domainsBlocked,
            queriesTotal = persisted.queriesTotal + session.queriesTotal,
            sinceEpochMs = session.sinceEpochMs
        )
    }

    val running: Flow<Boolean> = ShieldVpnBus.running
    val error: Flow<String?> = ShieldVpnBus.error
    val recentBlocks: Flow<List<BlockedEntry>> = ShieldVpnBus.recentBlocks

    /** Returns an Intent to launch for the system VPN consent dialog, or null if already granted. */
    fun prepareIntent(): Intent? = VpnService.prepare(context)

    fun start() {
        val intent = Intent(context, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_START)
        context.startService(intent)
    }

    suspend fun stop() {
        // Fold this session's counts into the persisted baseline before the session flow resets.
        val session = ShieldVpnBus.stats.value
        if (session.queriesTotal > 0 || session.totalBlocked > 0) {
            context.shieldStatsStore.edit { prefs ->
                prefs[KEY_ADS] = (prefs[KEY_ADS] ?: 0) + session.adsBlocked
                prefs[KEY_TRACKERS] = (prefs[KEY_TRACKERS] ?: 0) + session.trackersBlocked
                prefs[KEY_DOMAINS] = (prefs[KEY_DOMAINS] ?: 0) + session.domainsBlocked
                prefs[KEY_QUERIES] = (prefs[KEY_QUERIES] ?: 0) + session.queriesTotal
            }
        }
        val intent = Intent(context, ShieldVpnService::class.java).setAction(ShieldVpnService.ACTION_STOP)
        context.startService(intent)
    }
}
