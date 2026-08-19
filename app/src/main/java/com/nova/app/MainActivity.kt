package com.nova.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.nova.app.ui.navigation.NovaNavHost
import com.nova.app.ui.theme.NovaTheme

/**
 * Single-activity host. NOVA has no other activities — every module is a Compose
 * destination inside NovaNavHost, navigated to in-process with no new Activities,
 * no WebViews, and no external intents except the ones the user explicitly triggers
 * (e.g. opening system settings from a permission explainer, if ever needed).
 *
 * Extends FragmentActivity (a superset of ComponentActivity) rather than ComponentActivity
 * directly, since androidx.biometric.BiometricPrompt — used to unlock NOVA Private — requires
 * a FragmentActivity host.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovaApp()
        }
    }
}

@Composable
private fun NovaApp() {
    NovaTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            NovaNavHost()
        }
    }
}
