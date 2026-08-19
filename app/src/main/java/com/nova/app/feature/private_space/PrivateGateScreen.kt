package com.nova.app.feature.private_space

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.feature.private_space.security.BiometricAvailability
import com.nova.app.feature.private_space.security.NovaBiometricAuth
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaStatus
import com.nova.app.ui.components.NovaUnavailableState
import com.nova.app.ui.theme.*

/**
 * Gate shown whenever NOVA Private is locked. Requires real Android authentication
 * (BiometricPrompt, allowing biometric OR device PIN/pattern/password) before any Private
 * content — Vault, Notes — is ever composed or read from disk.
 */
@Composable
fun PrivateGateScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val haptics = rememberNovaHaptics()
    var errorText by remember { mutableStateOf<String?>(null) }

    val auth = remember(activity) { activity?.let { NovaBiometricAuth(it) } }
    val availability = remember(auth) { auth?.availability() }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BreathingLockIcon(active = availability == BiometricAvailability.Ready)
            Spacer(Modifier.height(24.dp))
            Text("NOVA // PRIVATE", style = MaterialTheme.typography.headlineSmall, color = NovaTextPrimary)
            Spacer(Modifier.height(8.dp))
            Text(
                "Locked. Authenticate with your device to continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextTertiary
            )
            Spacer(Modifier.height(28.dp))

            when (availability) {
                null -> NovaCard { NovaUnavailableState("NOVA Private needs a device authentication host that isn't available right now.") }
                BiometricAvailability.NoHardware -> NovaCard {
                    NovaUnavailableState("This device has no biometric or secure lock-screen hardware NOVA can use to protect this space.")
                }
                BiometricAvailability.NoneEnrolled -> NovaCard {
                    NovaUnavailableState(
                        "No fingerprint, face, or screen lock is set up on this device yet. Add one in " +
                            "Android Settings → Security to unlock NOVA Private."
                    )
                }
                BiometricAvailability.TemporarilyUnavailable -> NovaCard {
                    NovaUnavailableState("Device authentication is temporarily unavailable. Try again in a moment.")
                }
                BiometricAvailability.Ready -> {
                    NovaUnlockButton(
                        icon = Icons.Filled.Fingerprint,
                        label = "UNLOCK",
                        onClick = {
                            haptics.tap()
                            errorText = null
                            auth?.authenticate(
                                onSuccess = {
                                    haptics.success()
                                    PrivateSessionManager.unlock()
                                    onUnlocked()
                                },
                                onError = { message -> errorText = message },
                                onFailed = {
                                    haptics.warning()
                                    errorText = "Not recognized. Try again."
                                }
                            )
                        }
                    )
                }
            }

            if (errorText != null) {
                Spacer(Modifier.height(16.dp))
                Text(errorText!!, style = MaterialTheme.typography.bodyMedium, color = NovaBad)
            }
        }
    }
}

/** A slow breathing ring around the lock glyph — reads as "standing watch," not a static padlock graphic. */
@Composable
private fun BreathingLockIcon(active: Boolean) {
    val infinite = rememberInfiniteTransition(label = "lockBreathe")
    val ringScale by infinite.animateFloat(
        initialValue = 1f,
        targetValue = if (active) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringScale"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.5f,
        targetValue = if (active) 0.05f else 0.3f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Reverse),
        label = "ringAlpha"
    )

    Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .scale(ringScale)
                .clip(CircleShape)
                .border(1.5.dp, NovaAccent.copy(alpha = ringAlpha), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(NovaAccent.copy(alpha = 0.10f))
                .border(1.dp, NovaAccent.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
private fun NovaUnlockButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    NovaCard(onClick = onClick, modifier = Modifier.padding(horizontal = 24.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = NovaAccent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = NovaAccent)
        }
    }
}
