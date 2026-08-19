package com.nova.app.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * NOVA's haptic vocabulary. VIBRATE is a normal (not dangerous) permission, already declared
 * in the manifest, so this never triggers a runtime prompt. Every call is defensively wrapped —
 * a missing/disabled vibrator (common on tablets, some emulators) degrades to a silent no-op,
 * never a crash. Feedback is deliberately restrained: short, instrument-panel ticks rather than
 * buzzy game-controller rumble, matching NOVA's visual language.
 */
class NovaHaptics(context: Context) {

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (e: Exception) {
        null
    }

    private fun fire(effect: VibrationEffect) {
        try {
            if (vibrator?.hasVibrator() == true) vibrator.vibrate(effect)
        } catch (e: Exception) {
            // Never let feedback fail the interaction it's decorating.
        }
    }

    private fun oneShot(durationMs: Long, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fire(VibrationEffect.createOneShot(durationMs, amplitude))
        }
    }

    /** A tiny, near-silent tick for low-emphasis UI moments — tile taps, tab switches, toggles. */
    fun tick() = oneShot(10, 40)

    /** A slightly firmer tap for confirmed actions — running a test, starting a scan. */
    fun tap() = oneShot(18, 90)

    /** Two quick pulses — a positive/complete result (scan finished clean, test passed). */
    fun success() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fire(VibrationEffect.createWaveform(longArrayOf(0, 16, 60, 16), -1))
        }
    }

    /** A firmer double pulse — a finding worth the user's attention (not an error, a flag). */
    fun warning() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            fire(VibrationEffect.createWaveform(longArrayOf(0, 30, 50, 30), -1))
        }
    }
}

@Composable
fun rememberNovaHaptics(): NovaHaptics {
    val context = LocalContext.current
    return remember { NovaHaptics(context) }
}
