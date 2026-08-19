package com.nova.app.feature.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nova.app.ui.components.NovaPermissionExplainer
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary

// ---------- Touch test ----------

@Composable
fun TouchTestScreen() {
    var points by remember { mutableStateOf(listOf<Offset>()) }
    var activePointerCount by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Touch Test", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        Text(
            "Drag anywhere below. Active touches: $activePointerCount",
            style = MaterialTheme.typography.bodyMedium,
            color = NovaTextPrimary.copy(alpha = 0.6f)
        )
        Spacer(Modifier.height(12.dp))
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF11151C))
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            activePointerCount = event.changes.count { it.pressed }
                            points = event.changes.filter { it.pressed }.map { it.position }
                        }
                    }
                }
        ) {
            points.forEach { p ->
                drawCircle(color = NovaAccent, radius = 42f, center = p, style = Stroke(width = 4f))
                drawCircle(color = NovaAccent.copy(alpha = 0.25f), radius = 42f, center = p)
            }
        }
    }
}

// ---------- Display / color test ----------

private val displayTestColors = listOf(
    Color.White, Color.Black, Color.Red, Color.Green, Color.Blue,
    Color(0xFF808080)
)

@Composable
fun DisplayTestScreen() {
    var index by remember { mutableStateOf(0) }
    val current = displayTestColors[index]
    val isDark = current == Color.Black

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(current)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { index = (index + 1) % displayTestColors.size })
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            "Tap to cycle · ${index + 1}/${displayTestColors.size} · look for dead pixels or tint",
            color = if (isDark) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(24.dp)
        )
    }
}

// ---------- Vibration test ----------

private data class VibrationPattern(val name: String, val timings: LongArray, val amplitudes: IntArray?)

private val vibrationPatterns = listOf(
    VibrationPattern("Single pulse", longArrayOf(0, 150), null),
    VibrationPattern("Double tap", longArrayOf(0, 80, 100, 80), null),
    VibrationPattern("Long buzz", longArrayOf(0, 500), null),
    VibrationPattern("Heartbeat", longArrayOf(0, 100, 100, 100, 300), null)
)

@Composable
fun VibrationTestScreen() {
    val context = LocalContext.current
    val vibrator = remember { getVibrator(context) }
    val hasVibrator = vibrator?.hasVibrator() == true

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Vibration Test", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        if (!hasVibrator) {
            Text(
                "This device does not report a vibration motor.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.6f)
            )
            return@Column
        }
        vibrationPatterns.forEach { pattern ->
            com.nova.app.ui.components.NovaCard(onClick = {
                runVibration(vibrator, pattern)
            }) {
                Text(pattern.name, style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary)
            }
        }
    }
}

private fun getVibrator(context: Context): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}

private fun runVibration(vibrator: Vibrator?, pattern: VibrationPattern) {
    vibrator ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = VibrationEffect.createWaveform(pattern.timings, pattern.amplitudes, -1)
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(pattern.timings, -1)
    }
}

// ---------- Flashlight test ----------

@Composable
fun FlashlightTestScreen() {
    val context = LocalContext.current
    val cameraManager = remember { context.getSystemService(Context.CAMERA_SERVICE) as CameraManager }
    val torchCameraId = remember { findTorchCameraId(cameraManager) }
    var isOn by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            if (isOn && torchCameraId != null) {
                runCatching { cameraManager.setTorchMode(torchCameraId, false) }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Flashlight Test", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        Spacer(Modifier.height(16.dp))
        if (torchCameraId == null) {
            Text(
                "No flash unit reported on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.6f)
            )
        } else {
            Text(
                if (isOn) "Torch is ON" else "Torch is OFF",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isOn) NovaAccent else NovaTextPrimary.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(16.dp))
            com.nova.app.ui.components.NovaCard(onClick = {
                val next = !isOn
                runCatching { cameraManager.setTorchMode(torchCameraId, next) }
                    .onSuccess { isOn = next }
            }) {
                Text(if (isOn) "Turn off" else "Turn on", color = NovaTextPrimary)
            }
        }
    }
}

private fun findTorchCameraId(manager: CameraManager): String? {
    return runCatching {
        manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id)
                .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }.getOrNull()
}

// ---------- Camera test ----------

@Composable
fun CameraTestScreen() {
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
            Text("Camera Test", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(16.dp))
            NovaPermissionExplainer(
                icon = "📷",
                title = "CAMERA",
                explanation = "NOVA opens a live preview to confirm the lens, focus, and image pipeline work — nothing is recorded or saved.",
                actionLabel = "Grant camera access",
                onRequest = { launcher.launch(Manifest.permission.CAMERA) }
            )
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var useFront by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx -> PreviewView(ctx) },
            update = { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val provider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val selector = if (useFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        provider.unbindAll()
                        provider.bindToLifecycle(lifecycleOwner, selector, preview)
                    } catch (e: Exception) {
                        // No camera matching this selector — leave preview blank rather than crash.
                    }
                }, ContextCompat.getMainExecutor(context))
            }
        )

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp)
                .background(Color(0xCC11151C), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable { useFront = !useFront }
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                if (useFront) "SWITCH TO REAR" else "SWITCH TO FRONT",
                style = MaterialTheme.typography.labelLarge,
                color = NovaAccent
            )
        }
    }
}
