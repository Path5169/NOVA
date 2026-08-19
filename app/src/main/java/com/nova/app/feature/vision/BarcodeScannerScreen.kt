package com.nova.app.feature.vision

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.nova.app.feature.detective.DetectiveAction
import com.nova.app.feature.detective.DetectiveReport
import com.nova.app.feature.detective.DetectiveReportCard
import com.nova.app.feature.detective.url.UrlInspectionResult
import com.nova.app.feature.detective.url.UrlInspector
import com.nova.app.ui.components.NovaLoadingState
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaSurface
import com.nova.app.ui.theme.NovaTextPrimary
import com.nova.app.ui.theme.NovaTextTertiary
import com.nova.app.ui.theme.NovaWarn
import java.util.concurrent.Executors

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun BarcodeScannerScreen() {
    CameraPermissionGate(
        screenTitle = "QR / Barcode Scanner",
        explanation = "NOVA analyzes the live camera feed on-device with ML Kit to decode QR codes and barcodes. Frames are never saved or sent anywhere."
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        var lastResult by remember { mutableStateOf<Barcode?>(null) }
        var scanCount by remember { mutableStateOf(0) }

        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    val scanner = BarcodeScanning.getClient(
                        BarcodeScannerOptions.Builder()
                            .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                            .build()
                    )
                    val executor = Executors.newSingleThreadExecutor()

                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                        analysis.setAnalyzer(executor) { imageProxy: ImageProxy ->
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val input = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scanner.process(input)
                                    .addOnSuccessListener { barcodes ->
                                        val hit = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                                        if (hit != null) {
                                            lastResult = hit
                                            scanCount++
                                        }
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis
                            )
                        } catch (e: Exception) {
                            // Camera bind can fail on config changes mid-transition — safe to ignore, view stays blank.
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    previewView
                }
            )

            val result = lastResult
            if (result != null) {
                val value = result.rawValue ?: ""
                val looksLikeUrl = value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)
                var showInspection by remember(value) { mutableStateOf(false) }

                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color(0xCC0A0D12))
                        .padding(20.dp)
                ) {
                    Text(
                        result.format.let { formatName(it) },
                        style = MaterialTheme.typography.labelMedium,
                        color = NovaAccent
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge, color = NovaTextPrimary, maxLines = 3)

                    if (looksLikeUrl) {
                        Spacer(Modifier.height(8.dp))
                        QrUrlQuickFacts(value)
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Text(
                            "COPY", style = MaterialTheme.typography.labelLarge, color = NovaAccent,
                            modifier = Modifier.clickable {
                                copyText(context, "Scanned value", value)
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                        if (looksLikeUrl) {
                            Text(
                                "INSPECT", style = MaterialTheme.typography.labelLarge, color = NovaAccent,
                                modifier = Modifier.clickable { showInspection = true }
                            )
                            Text(
                                "OPEN", style = MaterialTheme.typography.labelLarge, color = NovaWarn,
                                modifier = Modifier.clickable {
                                    try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(value)))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Couldn't open this link", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                    }
                }

                if (showInspection) {
                    QrUrlInspectionSheet(url = value, onDismiss = { showInspection = false })
                }
            } else {
                Text(
                    "Point the camera at a QR code or barcode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .background(Color(0x990A0D12))
                        .padding(16.dp)
                )
            }
        }
    }
}

private fun formatName(format: Int): String = when (format) {
    Barcode.FORMAT_QR_CODE -> "QR CODE"
    Barcode.FORMAT_CODE_128 -> "CODE 128"
    Barcode.FORMAT_CODE_39 -> "CODE 39"
    Barcode.FORMAT_EAN_13 -> "EAN-13"
    Barcode.FORMAT_EAN_8 -> "EAN-8"
    Barcode.FORMAT_UPC_A -> "UPC-A"
    Barcode.FORMAT_UPC_E -> "UPC-E"
    Barcode.FORMAT_PDF417 -> "PDF417"
    Barcode.FORMAT_AZTEC -> "AZTEC"
    Barcode.FORMAT_DATA_MATRIX -> "DATA MATRIX"
    else -> "BARCODE"
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Quick, offline structural facts shown right under a scanned URL — before the user does anything with it. */
@Composable
private fun QrUrlQuickFacts(url: String) {
    var quick by remember(url) { mutableStateOf<UrlInspectionResult?>(null) }
    LaunchedEffect(url) { quick = UrlInspector.inspect(url, checkRedirects = false) }

    quick?.let { r ->
        Column {
            Text(
                (if (r.isHttps) "✓ HTTPS · " else "⚠ Not HTTPS · ") + "domain: ${r.registrableDomain}",
                style = MaterialTheme.typography.labelMedium,
                color = if (r.isHttps) NovaAccent else NovaWarn
            )
            if (r.findings.any { !it.positive && it != r.findings.firstOrNull { f -> f.label.contains("HTTPS") } }) {
                Text(
                    "⚠ Structural pattern worth reviewing — tap INSPECT",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaWarn
                )
            }
        }
    }
}

/** Full Detective Report for a URL decoded from a QR code, opened on demand — never automatically. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QrUrlInspectionSheet(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var result by remember(url) { mutableStateOf<UrlInspectionResult?>(null) }

    LaunchedEffect(url) {
        result = UrlInspector.inspect(url, checkRedirects = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NovaSurface) {
        Column(Modifier.padding(20.dp)) {
            Text("QR Detective", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(url, style = MaterialTheme.typography.bodySmall, color = NovaTextTertiary, maxLines = 2)
            Spacer(Modifier.height(16.dp))

            val r = result
            if (r == null) {
                NovaLoadingState("Analyzing…")
            } else {
                DetectiveReportCard(
                    DetectiveReport(
                        verdict = r.verdict,
                        subjectLabel = "${r.scheme}://${r.host}${r.path}",
                        findings = r.findings,
                        explanation = r.explanation,
                        actions = listOf(
                            DetectiveAction("Copy") {
                                copyText(context, "Scanned value", url)
                                Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                            },
                            DetectiveAction("Open") {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Couldn't open this link", Toast.LENGTH_SHORT).show()
                                }
                                onDismiss()
                            }
                        )
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
