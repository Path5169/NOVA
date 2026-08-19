package com.nova.app.feature.vision

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nova.app.ui.components.NovaPermissionExplainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.nova.app.ui.theme.NovaTextPrimary

/**
 * Gates camera-dependent content behind an explicit, explained runtime permission request.
 * Nothing in Vision touches the camera before the user has granted this.
 */
@Composable
fun CameraPermissionGate(screenTitle: String, explanation: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val granted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted.value = isGranted }

    if (!granted.value) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text(screenTitle, style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(16.dp))
            NovaPermissionExplainer(
                icon = "📷",
                title = "CAMERA",
                explanation = explanation,
                actionLabel = "Grant camera access",
                onRequest = { launcher.launch(Manifest.permission.CAMERA) }
            )
        }
        return
    }

    content()
}
