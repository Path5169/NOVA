package com.nova.app.feature.lab

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.NovaTextPrimary
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.pow

// ---------- Proximity ----------

@Composable
fun ProximityScreen() {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val sensor = remember {
        (context.getSystemService(android.content.Context.SENSOR_SERVICE) as android.hardware.SensorManager)
            .getDefaultSensor(Sensor.TYPE_PROXIMITY)
    }
    val available = sensor != null
    var distanceCm by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(available) {
        if (available) repository.readings(Sensor.TYPE_PROXIMITY).collectLatest { distanceCm = it.getOrNull(0) }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Proximity", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        if (!available) {
            NovaUnavailableState("No proximity sensor reported by this device.")
            return@Column
        }
        val maxRange = sensor?.maximumRange ?: 5f
        val isNear = (distanceCm ?: maxRange) < maxRange
        NovaCard {
            NovaBigReadout(
                value = distanceCm?.let { "%.1f".format(it) } ?: "—",
                unit = "cm",
                label = if (isNear) "NEAR" else "FAR"
            )
        }
        NovaCard {
            NovaSectionHeader("Sensor")
            Spacer(Modifier.height(10.dp))
            Text(
                "Maximum reported range: %.1f cm. Many phones report a binary near/far value rather than a continuous distance — that's the hardware, not this app.".format(maxRange),
                style = MaterialTheme.typography.bodyMedium,
                color = NovaTextPrimary.copy(alpha = 0.55f)
            )
        }
    }
}

// ---------- Barometer ----------

@Composable
fun BarometerScreen() {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val available = remember { repository.hasSensor(Sensor.TYPE_PRESSURE) }
    var hPa by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(available) {
        if (available) repository.readings(Sensor.TYPE_PRESSURE).collectLatest { hPa = it.getOrNull(0) }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Barometer", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        if (!available) {
            NovaUnavailableState("No barometer reported by this device.")
            return@Column
        }
        NovaCard {
            NovaBigReadout(value = hPa?.let { "%.1f".format(it) } ?: "—", unit = "hPa", label = "Atmospheric pressure")
        }
        hPa?.let { p ->
            // Standard barometric formula against sea-level reference pressure — a real computation
            // from a real reading, explicitly labeled as an estimate since local sea-level pressure varies.
            val estimatedAltitudeM = 44330.0 * (1.0 - (p / 1013.25).toDouble().pow(1.0 / 5.255))
            NovaCard {
                NovaSectionHeader("Estimated altitude")
                Spacer(Modifier.height(10.dp))
                NovaMetric("Above sea level (est.)", "%.0f".format(estimatedAltitudeM), unit = "m")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Estimated from pressure using a standard sea-level reference (1013.25 hPa) — actual local sea-level pressure varies with weather, so this can be off by tens of meters.",
                    style = MaterialTheme.typography.labelSmall,
                    color = NovaTextPrimary.copy(alpha = 0.4f)
                )
            }
        }
    }
}

// ---------- GPS ----------

private data class GpsState(
    val location: Location? = null,
    val satellitesUsed: Int? = null,
    val satellitesInView: Int? = null
)

@Composable
fun GpsScreen() {
    val context = LocalContext.current
    val granted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted.value = isGranted }

    if (!granted.value) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("GPS", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(16.dp))
            NovaPermissionExplainer(
                icon = "📍",
                title = "PRECISE LOCATION",
                explanation = "NOVA reads live GPS fix data (lat/lon, accuracy, altitude, satellite count) directly from the location provider, on-device only. Nothing is uploaded or logged.",
                actionLabel = "Grant location access",
                onRequest = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
            )
        }
        return
    }

    var state by remember { mutableStateOf(GpsState()) }
    var providerAvailable by remember { mutableStateOf(true) }

    DisposableEffect(Unit) {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        providerAvailable = lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                state = state.copy(location = location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        val gnssCallback: GnssStatus.Callback? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            object : GnssStatus.Callback() {
                override fun onSatelliteStatusChanged(status: GnssStatus) {
                    var used = 0
                    for (i in 0 until status.satelliteCount) {
                        if (status.usedInFix(i)) used++
                    }
                    state = state.copy(satellitesUsed = used, satellitesInView = status.satelliteCount)
                }
            }
        } else null

        try {
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
            } else if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, listener)
            }
            state = state.copy(location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))
            if (gnssCallback != null) lm.registerGnssStatusCallback(gnssCallback)
        } catch (e: SecurityException) {
            // Permission revoked mid-session.
        }

        onDispose {
            try {
                lm.removeUpdates(listener)
                if (gnssCallback != null) lm.unregisterGnssStatusCallback(gnssCallback)
            } catch (e: Exception) { /* provider already gone */ }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("GPS", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        if (!providerAvailable) {
            NovaUnavailableState("Location services are off. Enable GPS or network location to get a fix.")
        }

        val loc = state.location
        NovaCard {
            NovaSectionHeader("Position")
            Spacer(Modifier.height(12.dp))
            if (loc == null) {
                NovaLoadingState("Waiting for a fix…")
            } else {
                NovaMetric("Latitude", "%.6f".format(loc.latitude))
                Spacer(Modifier.height(8.dp))
                NovaMetric("Longitude", "%.6f".format(loc.longitude))
                Spacer(Modifier.height(8.dp))
                NovaMetric(
                    "Accuracy",
                    if (loc.hasAccuracy()) "±%.0f".format(loc.accuracy) else "—",
                    unit = "m",
                    status = when {
                        !loc.hasAccuracy() -> NovaStatus.UNAVAILABLE
                        loc.accuracy < 15 -> NovaStatus.GOOD
                        loc.accuracy < 50 -> NovaStatus.WARN
                        else -> NovaStatus.BAD
                    }
                )
                Spacer(Modifier.height(8.dp))
                NovaMetric("Altitude", if (loc.hasAltitude()) "%.0f".format(loc.altitude) else "—", unit = "m")
                Spacer(Modifier.height(8.dp))
                NovaMetric("Speed", if (loc.hasSpeed()) "%.1f".format(loc.speed) else "—", unit = "m/s")
                Spacer(Modifier.height(8.dp))
                NovaMetric("Provider", loc.provider ?: "—")
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            NovaCard {
                NovaSectionHeader("Satellites")
                Spacer(Modifier.height(12.dp))
                NovaMetric("Used in fix", state.satellitesUsed?.toString() ?: "—")
                Spacer(Modifier.height(8.dp))
                NovaMetric("In view", state.satellitesInView?.toString() ?: "—")
            }
        }
    }
}
