package com.nova.app.feature.shield

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaEntrance
import com.nova.app.ui.components.NovaSectionHeader
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import com.nova.app.ui.theme.NovaTextTertiary
import kotlinx.coroutines.launch

@Composable
fun ShieldBlocklistScreen(viewModel: ShieldViewModel = viewModel()) {
    val bundled by produceState<Set<String>>(initialValue = emptySet(), viewModel) {
        value = viewModel.bundledBlocklistSnapshot()
    }
    val custom by viewModel.customBlocked.collectAsState()
    var input by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Blocklist", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Domains NOVA refuses to resolve while Shield is active.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }

        item {
            NovaEntrance(index = 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("example-tracker.com") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    ShieldAddButton(enabled = input.isNotBlank()) {
                        if (input.isNotBlank()) {
                            viewModel.addCustomBlocked(input)
                            input = ""
                        }
                    }
                }
            }
        }

        item { NovaSectionHeader("Your custom list", trailing = "${custom.size}") }
        items(custom.sorted(), key = { it }) { domain ->
            NovaCard(modifier = Modifier.animateItemPlacement(tween(260, easing = FastOutSlowInEasing))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(domain, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary)
                    IconButton(onClick = { viewModel.removeCustomBlocked(domain) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = NovaTextTertiary)
                    }
                }
            }
        }

        item { NovaSectionHeader("Starter list (bundled)", trailing = "${bundled.size}") }
        item {
            NovaEntrance(index = 1) {
                NovaCard {
                    Text(
                        "Read-only. A small, hand-curated set of well-known ad/tracker domains " +
                            "shipped with NOVA — not exhaustive. See Shield's \"How this works\" note.",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaTextTertiary
                    )
                }
            }
        }
        items(bundled.sorted(), key = { it }) { domain ->
            Text(
                domain,
                style = MaterialTheme.typography.bodySmall,
                color = NovaTextTertiary,
                modifier = Modifier
                    .animateItemPlacement(tween(260, easing = FastOutSlowInEasing))
                    .padding(vertical = 4.dp, horizontal = 4.dp)
            )
        }
    }
}

/** The small circular "+" affordance shared by both list screens — settles with a physical
 * bounce on press and dims when there's nothing valid to add yet. */
@Composable
private fun ShieldAddButton(enabled: Boolean, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.85f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "shieldAddScale"
    )
    val alpha by animateFloatAsState(targetValue = if (enabled) 1f else 0.4f, label = "shieldAddAlpha")

    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.scale(scale)
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "Add",
            tint = NovaAccent.copy(alpha = alpha)
        )
    }
}

@Composable
fun ShieldAllowlistScreen(viewModel: ShieldViewModel = viewModel()) {
    val allowed by viewModel.allowed.collectAsState()
    var input by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Allowlist", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Domains exempted from filtering, even if they match a blocklist entry.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }

        item {
            NovaEntrance(index = 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("trusted-domain.com") },
                        singleLine = true
                    )
                    Spacer(Modifier.width(8.dp))
                    ShieldAddButton(enabled = input.isNotBlank()) {
                        if (input.isNotBlank()) {
                            viewModel.addAllowed(input)
                            input = ""
                        }
                    }
                }
            }
        }

        item { NovaSectionHeader("Allowed", trailing = "${allowed.size}") }
        items(allowed.sorted(), key = { it }) { domain ->
            NovaCard(modifier = Modifier.animateItemPlacement(tween(260, easing = FastOutSlowInEasing))) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(domain, style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary)
                    IconButton(onClick = { viewModel.removeAllowed(domain) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove", tint = NovaTextTertiary)
                    }
                }
            }
        }
    }
}
