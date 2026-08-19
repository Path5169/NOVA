package com.nova.app.feature.detective.file

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.feature.tools.copyToClipboard
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun FileInvestigatorScreen() {
    val context = LocalContext.current
    val haptics = rememberNovaHaptics()
    val scope = rememberCoroutineScope()

    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<FileInspectionResult?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            haptics.tap()
            loading = true
            result = null
            scope.launch {
                result = FileInvestigator.inspect(context, uri)
                loading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text("File Investigator", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text("Select a file to inspect its hashes and metadata.", style = MaterialTheme.typography.bodyMedium, color = NovaTextTertiary)
        }

        Button(
            onClick = { picker.launch(arrayOf("*/*")) },
            colors = ButtonDefaults.buttonColors(containerColor = NovaAccent),
            modifier = Modifier.fillMaxWidth()
        ) { Text("SELECT FILE", color = NovaBackground) }

        if (loading) NovaCard { NovaLoadingState("Hashing and reading metadata…") }

        result?.let { r ->
            NovaCard {
                NovaSectionHeader("File")
                Spacer(Modifier.height(10.dp))
                InfoRow("Name", r.name)
                InfoRow("Extension", r.extension)
                InfoRow("MIME type", r.mimeType)
                InfoRow("Size", formatSize(r.sizeBytes))
                InfoRow("Last modified", r.lastModified ?: "Not exposed by this source")
            }

            NovaCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    NovaSectionHeader("SHA-256")
                    Text(
                        "COPY HASH", style = MaterialTheme.typography.labelMedium, color = NovaAccent,
                        modifier = Modifier.clickable {
                            copyToClipboard(context, "SHA-256", r.sha256)
                            Toast.makeText(context, "SHA-256 copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(r.sha256, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = NovaTextPrimary)
                Spacer(Modifier.height(14.dp))
                NovaSectionHeader("MD5")
                Spacer(Modifier.height(8.dp))
                Text(r.md5, style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = NovaTextSecondary)
            }

            if (r.exif.isNotEmpty()) {
                val hasGps = r.exif.containsKey("GPS latitude")
                NovaCard {
                    NovaSectionHeader("Image metadata")
                    Spacer(Modifier.height(10.dp))
                    if (hasGps) {
                        NovaChip("⚠ LOCATION DATA FOUND", status = NovaStatus.WARN)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "This image's metadata includes GPS coordinates. Sharing it as-is may reveal where it was taken.",
                            style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    r.exif.forEach { (label, value) -> InfoRow(label, value) }
                }
            } else if (r.mimeType.startsWith("image/")) {
                NovaCard { NovaUnavailableState("No readable EXIF metadata in this image (stripped, re-encoded, or unsupported format).") }
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
