package com.nova.app.feature.detective.appintel

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.feature.privacy.PrivacyUiState
import com.nova.app.feature.privacy.PrivacyViewModel
import com.nova.app.feature.privacy.SensitiveAppEntry
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*

private val trackedPermissionOrder = listOf(
    "Camera", "Microphone", "Precise location", "Approximate location",
    "Contacts", "SMS", "Call log", "Storage"
)

private val permissionExplainers: Map<String, Triple<String, String, String>> = mapOf(
    "Camera" to Triple("Camera", "Lets an app take photos or record video.", "An app with unused camera access is worth reviewing, especially if it has no obvious reason to need one."),
    "Microphone" to Triple("Microphone", "Lets an app record audio.", "Apps with background microphone access could record without an obvious visual cue — worth understanding why an app needs this."),
    "Precise location" to Triple("Precise location", "Lets an app read your exact GPS coordinates.", "Precise location shared with more apps than necessary increases how easily your movement patterns could be reconstructed."),
    "Approximate location" to Triple("Approximate location", "Lets an app read your general area.", "Lower risk than precise location, but still worth knowing which apps have it."),
    "Contacts" to Triple("Contacts", "Lets an app read your saved contacts.", "Contact access is commonly used for \"find friends\" features — worth checking it matches the app's purpose."),
    "SMS" to Triple("SMS", "Lets an app read your text messages.", "SMS access is powerful (e.g. it can read verification codes) — worth reviewing closely."),
    "Call log" to Triple("Call log", "Lets an app read your call history.", "Call log access reveals who you talk to and when."),
    "Storage" to Triple("Storage", "Lets an app read files saved on your device.", "Broad storage access can expose photos, downloads, and documents beyond what the app needs.")
)

@Composable
fun PermissionMatrixScreen(viewModel: PrivacyViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var selectedPermission by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("Permission Matrix", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
                Text(
                    "Apps × permissions, from what Android legitimately exposes. Tap a permission to learn more.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTextTertiary
                )
            }
        }

        when (val s = state) {
            is PrivacyUiState.Loading -> item { NovaCard { NovaLoadingState("Building matrix…") } }
            is PrivacyUiState.Loaded -> {
                val apps = s.snapshot.sensitiveApps
                if (apps.isEmpty()) {
                    item { NovaCard { NovaUnavailableState("No visible apps currently hold NOVA's tracked sensitive permissions.") } }
                } else {
                    item {
                        NovaCard {
                            MatrixHeader(onPermissionClick = { selectedPermission = it })
                            Spacer(Modifier.height(10.dp))
                            apps.forEach { app ->
                                MatrixRow(app)
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }
        }

        item {
            NovaCard {
                Text(
                    "Coverage is limited to apps Android exposes to a normal, non-privileged app on this " +
                        "OS version. NOVA does not request QUERY_ALL_PACKAGES.",
                    style = MaterialTheme.typography.bodySmall,
                    color = NovaTextTertiary
                )
            }
        }
    }

    selectedPermission?.let { perm ->
        PermissionExplainerSheet(perm, onDismiss = { selectedPermission = null })
    }
}

@Composable
private fun MatrixHeader(onPermissionClick: (String) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Spacer(Modifier.weight(1.4f))
        trackedPermissionOrder.forEach { perm ->
            Box(
                Modifier.weight(1f).clickable { onPermissionClick(perm) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    permissionAbbreviation(perm),
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaAccent
                )
            }
        }
    }
}

@Composable
private fun MatrixRow(app: SensitiveAppEntry) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            app.label, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary,
            maxLines = 1, modifier = Modifier.weight(1.4f)
        )
        trackedPermissionOrder.forEach { perm ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                val granted = perm in app.permissionLabels
                val scale by animateFloatAsState(
                    targetValue = if (granted) 1f else 0.6f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                    label = "dotScale"
                )
                Box(
                    Modifier
                        .size(10.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(if (granted) NovaGood else NovaSurfaceOutline)
                )
            }
        }
    }
}

private fun permissionAbbreviation(perm: String): String = when (perm) {
    "Camera" -> "CAM"
    "Microphone" -> "MIC"
    "Precise location" -> "GPS"
    "Approximate location" -> "LOC"
    "Contacts" -> "CTC"
    "SMS" -> "SMS"
    "Call log" -> "CALL"
    "Storage" -> "FILE"
    else -> perm.take(3).uppercase()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionExplainerSheet(permission: String, onDismiss: () -> Unit) {
    val info = permissionExplainers[permission]
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = NovaSurface) {
        Column(Modifier.padding(20.dp)) {
            Text(permission, style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(12.dp))
            Text("What it means", style = MaterialTheme.typography.labelMedium, color = NovaTextTertiary)
            Spacer(Modifier.height(4.dp))
            Text(info?.second ?: "—", style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
            Spacer(Modifier.height(14.dp))
            Text("Worth reviewing", style = MaterialTheme.typography.labelMedium, color = NovaTextTertiary)
            Spacer(Modifier.height(4.dp))
            Text(info?.third ?: "—", style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
            Spacer(Modifier.height(12.dp))
        }
    }
}
