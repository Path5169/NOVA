package com.nova.app.feature.tools

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

private data class Tool(val title: String, val subtitle: String, val route: String, val icon: ImageVector)

private val tools = listOf(
    Tool("Calculator", "Standard arithmetic, no ads, no network", NovaDestination.ToolCalculator.route, Icons.Filled.Calculate),
    Tool("Unit Converter", "Length, mass, temperature, data", NovaDestination.ToolUnitConverter.route, Icons.Filled.SwapHoriz),
    Tool("Timestamp Converter", "Unix epoch ↔ human-readable", NovaDestination.ToolTimestamp.route, Icons.Filled.Schedule),
    Tool("Base64 Encode/Decode", "Text ↔ Base64, both directions", NovaDestination.ToolBase64.route, Icons.Filled.Code),
    Tool("URL Encode/Decode", "Percent-encoding, both directions", NovaDestination.ToolUrlEncode.route, Icons.Filled.Link),
    Tool("JSON Formatter", "Pretty-print and validate JSON", NovaDestination.ToolJson.route, Icons.Filled.DataObject),
    Tool("UUID Generator", "RFC 4122 v4 UUIDs, batch generate", NovaDestination.ToolUuid.route, Icons.Filled.Fingerprint),
    Tool("Hash Generator", "MD5 / SHA-1 / SHA-256 / SHA-512", NovaDestination.ToolHash.route, Icons.Filled.Tag)
)

@Composable
fun ToolsScreen(onNavigate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Tools", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Offline developer & everyday utilities.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }
        items(tools) { tool ->
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
