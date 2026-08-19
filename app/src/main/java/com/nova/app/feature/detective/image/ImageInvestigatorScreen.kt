package com.nova.app.feature.detective.image

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.feature.detective.file.FileInspectionResult
import com.nova.app.feature.detective.file.FileInvestigator
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Image-focused inspector. Reuses [FileInvestigator]'s hashing/EXIF logic (no duplicated
 * infrastructure) but leads with resolution/format and a prominent GPS warning, per NOVA's
 * Digital Detective spec. The image is never uploaded — only decoded locally for preview.
 */
@Composable
fun ImageInvestigatorScreen() {
    val context = LocalContext.current
    val haptics = rememberNovaHaptics()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FileInspectionResult?>(null) }
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var resolution by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            haptics.tap()
            loading = true
            result = null
            scope.launch {
                result = FileInvestigator.inspect(context, uri)
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeStream(stream, null, options)
                        resolution = options.outWidth to options.outHeight
                    }
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        bitmap = BitmapFactory.decodeStream(stream)
                    }
                } catch (e: Exception) {
                    resolution = null
                }
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("Image Investigator", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text("Check an image's format, resolution, and hidden metadata before you share it.", style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
        }

        Button(
            onClick = { picker.launch(arrayOf("image/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = NovaAccent),
            modifier = Modifier.fillMaxWidth()
        ) { Text("SELECT IMAGE", color = NovaBackground) }

        if (loading) NovaCard { NovaLoadingState("Reading image metadata…") }

        bitmap?.let {
            Image(it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(220.dp))
        }

        result?.let { r ->
            val hasGps = r.exif.containsKey("GPS latitude")

            if (hasGps) {
                NovaCard {
                    NovaChip("⚠ LOCATION DATA FOUND", status = NovaStatus.WARN)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This image contains GPS coordinates showing where it was taken (${r.exif["GPS latitude"]}, ${r.exif["GPS longitude"]}). " +
                            "Sharing the original file may expose this location to whoever receives it.",
                        style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary
                    )
                }
            }

            NovaCard {
                NovaSectionHeader("Image")
                Spacer(Modifier.height(10.dp))
                InfoRow("Resolution", resolution?.let { "${it.first} × ${it.second}" } ?: "Unavailable")
                InfoRow("Format", r.mimeType)
                InfoRow("File size", formatSize(r.sizeBytes))
                InfoRow("Camera make", r.exif["Make"] ?: "Not present")
                InfoRow("Camera model", r.exif["Model"] ?: "Not present")
                InfoRow("Date taken", r.exif["Date taken"] ?: "Not present")
                InfoRow("Software", r.exif["Software"] ?: "Not present")
            }

            if (r.exif.isEmpty()) {
                NovaCard { NovaUnavailableState("No EXIF metadata found — likely stripped, screenshot, or re-encoded.") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary, maxLines = 1)
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}
