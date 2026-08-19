package com.nova.app.feature.detective

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaListRow
import com.nova.app.ui.navigation.NovaDestination
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import com.nova.app.ui.theme.NovaTextTertiary

private data class DetectiveTool(val title: String, val subtitle: String, val route: String, val icon: ImageVector)

private val investigations = listOf(
    DetectiveTool("URL Inspector", "Structure, HTTPS, look-alike patterns, redirects", NovaDestination.DetectiveUrl.route, Icons.Filled.Link),
    DetectiveTool("QR Detective", "Decode before you open — see the URL first", NovaDestination.VisionBarcode.route, Icons.Filled.QrCodeScanner),
    DetectiveTool("File Investigator", "Hashes, MIME type, size, metadata", NovaDestination.DetectiveFile.route, Icons.Filled.FindInPage),
    DetectiveTool("Image Investigator", "EXIF, camera info, GPS location warning", NovaDestination.DetectiveImage.route, Icons.Filled.Image),
    DetectiveTool("App Intelligence", "What apps can legitimately access on this device", NovaDestination.Privacy.route, Icons.Filled.Apps),
    DetectiveTool("Permission Matrix", "Apps × permissions, visually", NovaDestination.DetectivePermissionMatrix.route, Icons.Filled.GridView)
)

@Composable
fun DetectiveScreen(onNavigate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Detective", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Don't blindly trust it. Inspect it.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextTertiary
            )
        }
        itemsIndexed(investigations) { index, tool ->
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(280, delayMillis = index * 45)) +
                    slideInVertically(tween(280, delayMillis = index * 45)) { it / 5 }
            ) {
                NovaCard {
                    NovaListRow(
                        title = tool.title,
                        subtitle = tool.subtitle,
                        icon = { Icon(tool.icon, contentDescription = null, tint = NovaAccent) },
                        onClick = { onNavigate(tool.route) }
                    )
                }
            }
        }
    }
}
