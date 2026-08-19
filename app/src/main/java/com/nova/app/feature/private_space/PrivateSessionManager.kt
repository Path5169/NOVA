package com.nova.app.feature.private_space

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Options shown in the "Auto-lock after" setting inside NOVA Private. */
enum class AutoLockDuration(val seconds: Int, val label: String) {
    THIRTY_SECONDS(30, "30 seconds"),
    ONE_MINUTE(60, "1 minute"),
    FIVE_MINUTES(300, "5 minutes"),
    FIFTEEN_MINUTES(900, "15 minutes")
}

/**
 * Process-lifetime session state for NOVA // PRIVATE.
 *
 * This is intentionally a plain singleton, not persisted anywhere: an unlocked session never
 * survives a process death, so there's nothing for anything to read back even in memory-dump
 * scenarios beyond what's already true of any unlocked app.
 *
 * The inactivity timer is a simple restartable countdown — any call to [notifyActivity] resets
 * it, and expiry (or [lockNow]) flips the session back to locked, which the nav layer treats as
 * "show the auth gate again."
 */
object PrivateSessionManager {

    private val scope = CoroutineScope(Dispatchers.Main.immediate)

    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    private val _autoLockDuration = MutableStateFlow(AutoLockDuration.ONE_MINUTE)
    val autoLockDuration: StateFlow<AutoLockDuration> = _autoLockDuration

    private var timeoutJob: Job? = null

    fun setAutoLockDuration(duration: AutoLockDuration) {
        _autoLockDuration.value = duration
        if (_unlocked.value) restartTimer()
    }

    fun unlock() {
        _unlocked.value = true
        restartTimer()
    }

    fun lockNow() {
        timeoutJob?.cancel()
        _unlocked.value = false
    }

    /** Call on any meaningful user interaction while inside NOVA Private to reset the auto-lock clock. */
    fun notifyActivity() {
        if (_unlocked.value) restartTimer()
    }

    private fun restartTimer() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            delay(_autoLockDuration.value.seconds * 1000L)
            _unlocked.value = false
        }
    }
}
