package com.nova.app.feature.lab

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
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaListRow
import com.nova.app.ui.navigation.NovaDestination
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary

private data class Instrument(val title: String, val subtitle: String, val route: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val instruments = listOf(
    Instrument("Motion Graph", "Accelerometer, live 3-axis plot", NovaDestination.LabMotion.route, Icons.Filled.ShowChart),
    Instrument("Rotation", "Gyroscope orientation visualizer", NovaDestination.LabRotation.route, Icons.Filled.RotateRight),
    Instrument("Magnetic Field", "Magnetometer strength meter", NovaDestination.LabMagnetic.route, Icons.Filled.Explore),
    Instrument("Light Meter", "Ambient light sensor readout", NovaDestination.LabLight.route, Icons.Filled.LightMode),
    Instrument("Sound Level", "Microphone amplitude meter", NovaDestination.LabSound.route, Icons.Filled.GraphicEq),
    Instrument("Proximity", "Near/far distance sensor", NovaDestination.LabProximity.route, Icons.Filled.SocialDistance),
    Instrument("Barometer", "Pressure + estimated altitude", NovaDestination.LabBarometer.route, Icons.Filled.Speed),
    Instrument("GPS", "Live position, accuracy, satellites", NovaDestination.LabGps.route, Icons.Filled.MyLocation),
    Instrument("Sensor Availability", "Every sensor this device reports", NovaDestination.LabSensorList.route, Icons.Filled.Sensors)
)

@Composable
fun LabScreen(onNavigate: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Lab", style = MaterialTheme.typography.headlineMedium, color = NovaTextPrimary)
            Text(
                "Live instruments built on your phone's real sensors.",
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.5f)
            )
        }
        items(instruments) { instrument ->
            NovaCard {
                NovaListRow(
                    title = instrument.title,
                    subtitle = instrument.subtitle,
                    icon = { Icon(instrument.icon, contentDescription = null, tint = NovaAccent) },
                    onClick = { onNavigate(instrument.route) }
                )
            }
        }
    }
}
