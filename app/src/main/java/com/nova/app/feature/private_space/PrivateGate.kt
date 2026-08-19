package com.nova.app.feature.private_space

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

/**
 * Wraps every screen inside NOVA Private. Renders the auth gate whenever the session is locked
 * — on cold entry, after manual LOCK NOW, or after the inactivity timeout fires — and only ever
 * composes [content] (which is what actually reads Vault/Notes data) once [PrivateSessionManager]
 * reports the session unlocked.
 */
@Composable
fun PrivateGated(content: @Composable () -> Unit) {
    val unlocked by PrivateSessionManager.unlocked.collectAsState()

    if (unlocked) {
        LaunchedEffect(Unit) { PrivateSessionManager.notifyActivity() }
        content()
    } else {
        PrivateGateScreen(onUnlocked = { /* state flip already triggers recomposition */ })
    }
}
