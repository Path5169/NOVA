package com.nova.app.feature.vision

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaListRow
import com.nova.app.ui.navigation.NovaDestination
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary

@Composable
fun VisionScreen(onNavigate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Vision", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "On-device camera intelligence — everything below runs locally, nothing leaves your phone.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }
        item {
            NovaCard(onClick = { onNavigate(NovaDestination.VisionBarcode.route) }) {
                NovaListRow(
                    title = "QR / Barcode Scanner",
                    subtitle = "Live camera scan, on-device ML Kit decoding",
                    icon = { Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = NovaAccent) },
                    onClick = { onNavigate(NovaDestination.VisionBarcode.route) }
                )
            }
        }
        item {
            NovaCard(onClick = { onNavigate(NovaDestination.VisionOcr.route) }) {
                NovaListRow(
                    title = "Text Scanner (OCR)",
                    subtitle = "Point the camera at text to extract it",
                    icon = { Icon(Icons.Filled.TextFields, contentDescription = null, tint = NovaAccent) },
                    onClick = { onNavigate(NovaDestination.VisionOcr.route) }
                )
            }
        }
    }
}
