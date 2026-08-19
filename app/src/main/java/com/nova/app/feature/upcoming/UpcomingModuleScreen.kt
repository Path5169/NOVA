package com.nova.app.feature.upcoming

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nova.app.ui.theme.NovaTextPrimary
import com.nova.app.ui.theme.NovaTextTertiary

/**
 * Used for modules described in the NOVA spec that are not part of Phase 1.
 * Per the "no fake features" rule, this never simulates data — it says plainly
 * what's missing and when it's planned, instead of shipping a hollow screen.
 */
@Composable
fun UpcomingModuleScreen(moduleName: String, phaseLabel: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Filled.Construction, contentDescription = null, tint = NovaTextTertiary, modifier = Modifier.size(36.dp))
        Spacer(Modifier.height(16.dp))
        Text(moduleName, style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Planned for $phaseLabel — not built yet in this version.",
            style = MaterialTheme.typography.bodyMedium,
            color = NovaTextTertiary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = NovaTextTertiary
        )
    }
}
