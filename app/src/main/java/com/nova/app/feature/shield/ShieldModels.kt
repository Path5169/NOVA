package com.nova.app.feature.shield

/**
 * Everything NOVA Shield tracks and shows is derived from real DNS queries NOVA itself
 * observed while the local filtering VPN was active — never estimated or simulated.
 */
data class ShieldStats(
    val adsBlocked: Long = 0,
    val trackersBlocked: Long = 0,
    val domainsBlocked: Long = 0,
    val queriesTotal: Long = 0,
    val sinceEpochMs: Long = 0
) {
    val totalBlocked: Long get() = adsBlocked + trackersBlocked + domainsBlocked
}

enum class ShieldCategory { AD, TRACKER, CUSTOM }

data class BlockedEntry(
    val domain: String,
    val category: ShieldCategory,
    val timestampMs: Long
)

enum class ShieldState { ACTIVE, INACTIVE, STARTING, ERROR }

data class ShieldUiState(
    val state: ShieldState = ShieldState.INACTIVE,
    val stats: ShieldStats = ShieldStats(),
    val recentBlocks: List<BlockedEntry> = emptyList(),
    val errorMessage: String? = null
)

/**
 * NOVA Shield is DNS-level filtering only. It intercepts DNS resolution (the "what
 * domain does this app want to talk to" step) and refuses to resolve known ad/tracker
 * domains. It does NOT decrypt, inspect, or MITM any traffic — it never sees page
 * content, and it cannot block ads that are served from a domain the host app also
 * needs for legitimate functionality (those share a hostname NOVA can't tell apart).
 * This limitation is stated in the Shield screen itself, not just here.
 */
const val SHIELD_HONEST_LIMITATION =
    "DNS-level filtering blocks known ad/tracker domains before they resolve. It can't " +
        "filter ads served from a domain an app also needs to function, and it can't see " +
        "or alter encrypted traffic content."
