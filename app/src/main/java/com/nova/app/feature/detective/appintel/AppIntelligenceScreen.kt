package com.nova.app.feature.detective.appintel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.feature.privacy.PrivacyUiState
import com.nova.app.feature.privacy.PrivacyViewModel
import com.nova.app.feature.privacy.SensitiveAppEntry
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.*

private val filterCategories = listOf(
    "All", "Camera", "Microphone", "Precise location", "Contacts", "SMS", "Storage"
)

@Composable
fun AppIntelligenceScreen(viewModel: PrivacyViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var filter by remember { mutableStateOf("All") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text("App Intelligence", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
                Text(
                    "What apps NOVA can legitimately see, and what they're allowed to access.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTextTertiary
                )
            }
        }

        item {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filterCategories) { category ->
                    FilterChip(
                        selected = filter == category,
                        onClick = { filter = category },
                        label = { Text(category) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NovaAccent,
                            selectedLabelColor = NovaBackground
                        )
                    )
                }
            }
        }

        when (val s = state) {
            is PrivacyUiState.Loading -> item { NovaCard { NovaLoadingState("Scanning visible apps…") } }
            is PrivacyUiState.Loaded -> {
                val apps = s.snapshot.sensitiveApps
                    .filter { app -> filter == "All" || filter in app.permissionLabels }
                    .sortedByDescending { it.permissionLabels.size }
                if (apps.isEmpty()) {
                    item { NovaCard { NovaUnavailableState("No apps match this filter among what's currently visible to NOVA.") } }
                } else {
                    itemsIndexed(apps) { index, app ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(tween(260, delayMillis = index * 40)) +
                                slideInVertically(tween(260, delayMillis = index * 40)) { it / 5 }
                        ) {
                            AppIntelCard(app)
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

/** Exposure is a simple count-based read of the same permission set — never a spying accusation, just "more surfaces than most." */
private fun exposureFor(count: Int): Triple<androidx.compose.ui.graphics.Color, String, String> = when {
    count >= 4 -> Triple(NovaBad, "HIGH EXPOSURE", "Worth reviewing — this app can reach several sensitive permissions.")
    count >= 2 -> Triple(NovaWarn, "MODERATE", "A couple of sensitive permissions — check they match what the app does.")
    else -> Triple(NovaGood, "LOW", "Limited sensitive access on this device.")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppIntelCard(app: SensitiveAppEntry) {
    val (color, badge, note) = exposureFor(app.permissionLabels.size)

    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(color))
        androidx.compose.foundation.layout.Column(
            Modifier
                .weight(1f)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .background(NovaSurface)
                .border(1.dp, NovaSurfaceOutline, androidx.compose.foundation.shape.RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                .padding(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    Text(app.label, style = MaterialTheme.typography.titleMedium, color = NovaTextPrimary, maxLines = 1)
                    Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary, maxLines = 1)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(6.dp))
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = color)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(note, style = MaterialTheme.typography.labelSmall, color = NovaTextTertiary)
            Spacer(Modifier.height(12.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                app.permissionLabels.forEach { NovaChip(it, status = NovaStatus.WARN) }
            }
        }
    }
}
