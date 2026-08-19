package com.nova.app.feature.shield

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.core.rememberNovaHaptics
import com.nova.app.ui.components.*
import com.nova.app.ui.navigation.NovaDestination
import com.nova.app.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun ShieldScreen(
    onNavigate: (String) -> Unit,
    viewModel: ShieldViewModel = viewModel()
) {
    val ui by viewModel.uiState.collectAsState()
    val haptics = rememberNovaHaptics()

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.start()
    }

    fun toggle() {
        if (ui.state == ShieldState.ACTIVE) {
            haptics.tick()
            viewModel.stop()
        } else {
            haptics.tap()
            val consent = viewModel.prepareIntentIfNeeded()
            if (consent != null) vpnLauncher.launch(consent) else viewModel.start()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Shield", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Local DNS filtering. Nothing leaves your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }

        item {
            NovaEntrance(index = 0) { ShieldStatusCard(state = ui.state, onToggle = ::toggle) }
        }

        item {
            // Slides open/shut with the error itself, rather than the layout jumping when it
            // appears or clears.
            AnimatedVisibility(
                visible = ui.state == ShieldState.ERROR && ui.errorMessage != null,
                enter = fadeIn(tween(220)) + expandVertically(tween(220, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(150)) + shrinkVertically(tween(150))
            ) {
                NovaCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = NovaBad)
                        Spacer(Modifier.width(10.dp))
                        Text(ui.errorMessage.orEmpty(), style = MaterialTheme.typography.bodyMedium, color = NovaTextSecondary)
                    }
                }
            }
        }

        item {
            NovaEntrance(index = 1) {
                NovaCard {
                    NovaSectionHeader("Today's totals")
                    Spacer(Modifier.height(12.dp))
                    NovaAnimatedMetric("Ads blocked", ui.stats.adsBlocked, status = NovaStatus.GOOD)
                    Spacer(Modifier.height(10.dp))
                    NovaAnimatedMetric("Trackers blocked", ui.stats.trackersBlocked, status = NovaStatus.GOOD)
                    Spacer(Modifier.height(10.dp))
                    NovaAnimatedMetric("Custom-list blocks", ui.stats.domainsBlocked, status = NovaStatus.NEUTRAL)
                    Spacer(Modifier.height(10.dp))
                    NovaAnimatedMetric("DNS queries seen", ui.stats.queriesTotal, status = NovaStatus.NEUTRAL)
                }
            }
        }

        item {
            NovaEntrance(index = 2) {
                NovaCard {
                    NovaListRow(
                        title = "Blocklist",
                        subtitle = "Starter list + your custom domains",
                        icon = { Icon(Icons.Filled.Block, contentDescription = null, tint = NovaAccent) },
                        onClick = { onNavigate(NovaDestination.ShieldBlocklist.route) }
                    )
                }
            }
        }
        item {
            NovaEntrance(index = 3) {
                NovaCard {
                    NovaListRow(
                        title = "Allowlist",
                        subtitle = "Domains exempted from filtering",
                        icon = { Icon(Icons.Filled.CheckCircleOutline, contentDescription = null, tint = NovaAccent) },
                        onClick = { onNavigate(NovaDestination.ShieldAllowlist.route) }
                    )
                }
            }
        }

        if (ui.recentBlocks.isNotEmpty()) {
            item { NovaSectionHeader("Recently blocked") }
            items(
                items = ui.recentBlocks.take(10),
                key = { "${it.domain}-${it.timestampMs}" }
            ) { entry ->
                NovaCard(modifier = Modifier.animateItemPlacement(tween(280, easing = FastOutSlowInEasing))) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(entry.domain, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary)
                            Text(
                                categoryLabel(entry.category),
                                style = MaterialTheme.typography.labelSmall,
                                color = NovaTextTertiary
                            )
                        }
                        Text(
                            formatTime(entry.timestampMs),
                            style = MaterialTheme.typography.labelSmall,
                            color = NovaTextTertiary
                        )
                    }
                }
            }
        }

        item {
            NovaEntrance(index = 4) {
                NovaCard {
                    Text("How this works", style = MaterialTheme.typography.titleSmall, color = NovaTextPrimary)
                    Spacer(Modifier.height(8.dp))
                    Text(SHIELD_HONEST_LIMITATION, style = MaterialTheme.typography.bodySmall, color = NovaTextTertiary)
                }
            }
        }
    }
}

@Composable
private fun ShieldStatusCard(state: ShieldState, onToggle: () -> Unit) {
    val (label, color) = when (state) {
        ShieldState.ACTIVE -> "ACTIVE" to NovaGood
        ShieldState.STARTING -> "STARTING…" to NovaWarn
        ShieldState.ERROR -> "ERROR" to NovaBad
        ShieldState.INACTIVE -> "INACTIVE" to NovaTextTertiary
    }

    // A faint ambient wash behind the whole card while Shield is genuinely filtering —
    // ties the card's own state to the same accent language as the rest of NOVA, without
    // adding a decorative glow that isn't backed by a real state.
    val infinite = rememberInfiniteTransition(label = "shieldCardGlow")
    val glowPulse by infinite.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Reverse),
        label = "shieldCardGlowPulse"
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (state == ShieldState.ACTIVE) 0.10f * glowPulse else 0f,
        animationSpec = tween(400),
        label = "shieldCardGlowAlpha"
    )

    NovaCard(
        modifier = Modifier.background(
            Brush.radialGradient(
                colors = listOf(NovaAccent.copy(alpha = glowAlpha), Color.Transparent),
                radius = 340f
            )
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PROTECTION", style = MaterialTheme.typography.labelMedium, color = NovaTextTertiary)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NovaPulsingDot(color = color, pulsing = state == ShieldState.ACTIVE, size = 8.dp)
                    Spacer(Modifier.width(2.dp))
                    NovaStatusLabel(text = label, color = color, style = MaterialTheme.typography.titleMedium)
                }
            }
            val switchScale by animateFloatAsState(
                targetValue = if (state == ShieldState.STARTING) 1.04f else 1f,
                animationSpec = tween(220, easing = FastOutSlowInEasing),
                label = "shieldSwitchScale"
            )
            Switch(
                checked = state == ShieldState.ACTIVE || state == ShieldState.STARTING,
                onCheckedChange = { onToggle() },
                modifier = Modifier.scale(switchScale),
                colors = SwitchDefaults.colors(checkedThumbColor = NovaAccent, checkedTrackColor = NovaAccentDim)
            )
        }
    }
}

private fun categoryLabel(category: ShieldCategory): String = when (category) {
    ShieldCategory.AD -> "Ad / tracker network"
    ShieldCategory.TRACKER -> "Tracker"
    ShieldCategory.CUSTOM -> "Custom blocklist"
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(epochMs)
