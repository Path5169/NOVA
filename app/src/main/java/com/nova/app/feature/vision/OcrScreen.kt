package com.nova.app.feature.vision

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
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
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaLoadingState
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import java.io.ByteArrayOutputStream

private enum class OcrMode { CAMERA, RESULT }

@Composable
fun OcrScreen() {
    CameraPermissionGate(
        screenTitle = "Text Scanner (OCR)",
        explanation = "NOVA reads a single camera frame and runs on-device ML Kit text recognition to extract any text it contains. Nothing is uploaded."
    ) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        var mode by remember { mutableStateOf(OcrMode.CAMERA) }
        var extracted by remember { mutableStateOf<String?>(null) }
        var processing by remember { mutableStateOf(false) }
        var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

        if (mode == OcrMode.CAMERA) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx)
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val provider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()
                            try {
                                provider.unbindAll()
                                provider.bindToLifecycle(
                                    lifecycleOwner,
                                    CameraSelector.DEFAULT_BACK_CAMERA,
                                    preview,
                                    capture
                                )
                                imageCapture = capture
                            } catch (e: Exception) {
                                // Bind failures on rapid lifecycle changes are safe to ignore here.
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                Text(
                    "Frame the text, then capture",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .background(Color(0x990A0D12))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .background(if (processing) Color(0x552A9C6B) else Color(0xFF3DDC97), shape = androidx.compose.foundation.shape.CircleShape)
                        .clickable(enabled = !processing) {
                            val capture = imageCapture ?: return@clickable
                            processing = true
                            capture.takePicture(
                                ContextCompat.getMainExecutor(context),
                                object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bitmap = imageProxyToBitmap(image)
                                        image.close()
                                        if (bitmap == null) {
                                            processing = false
                                            Toast.makeText(context, "Couldn't read frame", Toast.LENGTH_SHORT).show()
                                            return
                                        }
                                        val input = InputImage.fromBitmap(bitmap, 0)
                                        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                                        recognizer.process(input)
                                            .addOnSuccessListener { result ->
                                                extracted = result.text
                                                mode = OcrMode.RESULT
                                                processing = false
                                            }
                                            .addOnFailureListener {
                                                processing = false
                                                Toast.makeText(context, "Recognition failed", Toast.LENGTH_SHORT).show()
                                            }
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        processing = false
                                        Toast.makeText(context, "Capture failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }
                        .padding(22.dp)
                ) {
                    if (processing) {
                        NovaLoadingState("Reading text…")
                    } else {
                        Text("CAPTURE", style = MaterialTheme.typography.labelLarge, color = Color(0xFF0A0D12))
                    }
                }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Extracted Text", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
                NovaCard {
                    SelectionContainer {
                        Text(
                            extracted?.takeIf { it.isNotBlank() } ?: "No text detected in that frame.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NovaTextPrimary,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        "COPY",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaAccent,
                        modifier = Modifier.clickable {
                            copyText(context, "Extracted text", extracted ?: "")
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                    Text(
                        "SCAN AGAIN",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaAccent,
                        modifier = Modifier.clickable {
                            extracted = null
                            mode = OcrMode.CAMERA
                        }
                    )
                }
            }
        }
    }
}

private typealias ImageCaptureException = androidx.camera.core.ImageCaptureException

/** Converts a captured YUV_420_888/JPEG ImageProxy frame to a Bitmap for ML Kit, entirely on-device. */
private fun imageProxyToBitmap(image: ImageProxy): android.graphics.Bitmap? {
    return try {
        if (image.format == ImageFormat.JPEG) {
            val buffer = image.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } else {
            val yBuffer = image.planes[0].buffer
            val uBuffer = image.planes[1].buffer
            val vBuffer = image.planes[2].buffer
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 90, out)
            val jpegBytes = out.toByteArray()
            val bitmap = android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
            val matrix = android.graphics.Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
            android.graphics.Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (e: Exception) {
        null
    }
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
