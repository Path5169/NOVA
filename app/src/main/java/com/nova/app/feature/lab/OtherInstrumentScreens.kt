package com.nova.app.feature.lab

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.nova.app.ui.components.*
import com.nova.app.ui.theme.NovaAccent
import com.nova.app.ui.theme.NovaTextPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.log10
import kotlin.math.roundToInt
import kotlin.math.sqrt

// ---------- Rotation (gyroscope + rotation vector) ----------

@Composable
fun RotationScreen() {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val hasRotationVector = remember { repository.hasSensor(Sensor.TYPE_ROTATION_VECTOR) }
    val hasGyro = remember { repository.hasSensor(Sensor.TYPE_GYROSCOPE) }

    var azimuth by remember { mutableStateOf(0f) }
    var pitch by remember { mutableStateOf(0f) }
    var roll by remember { mutableStateOf(0f) }
    var gyroValues by remember { mutableStateOf(FloatArray(3)) }

    LaunchedEffect(hasRotationVector) {
        if (hasRotationVector) {
            repository.readings(Sensor.TYPE_ROTATION_VECTOR).collectLatest { values ->
                val rotationMatrix = FloatArray(9)
                android.hardware.SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                val orientation = FloatArray(3)
                android.hardware.SensorManager.getOrientation(rotationMatrix, orientation)
                azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
                roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
            }
        }
    }

    LaunchedEffect(hasGyro) {
        if (hasGyro) {
            repository.readings(Sensor.TYPE_GYROSCOPE).collectLatest { gyroValues = it }
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Rotation", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        if (hasRotationVector) {
            NovaCard {
                NovaSectionHeader("Orientation (°)")
                Spacer(Modifier.height(12.dp))
                NovaMetric("Azimuth", "%.1f".format(azimuth))
                Spacer(Modifier.height(8.dp))
                NovaMetric("Pitch", "%.1f".format(pitch))
                Spacer(Modifier.height(8.dp))
                NovaMetric("Roll", "%.1f".format(roll))
            }
            NovaCard {
                NovaSectionHeader("Horizon")
                Spacer(Modifier.height(12.dp))
                HorizonIndicator(pitch = pitch, roll = roll, modifier = Modifier.fillMaxWidth().height(160.dp))
            }
        } else {
            NovaUnavailableState("No rotation-vector sensor reported by this device.")
        }

        if (hasGyro) {
            NovaCard {
                NovaSectionHeader("Angular velocity (rad/s)")
                Spacer(Modifier.height(12.dp))
                NovaMetric("X", "%.2f".format(gyroValues.getOrElse(0) { 0f }))
                Spacer(Modifier.height(8.dp))
                NovaMetric("Y", "%.2f".format(gyroValues.getOrElse(1) { 0f }))
                Spacer(Modifier.height(8.dp))
                NovaMetric("Z", "%.2f".format(gyroValues.getOrElse(2) { 0f }))
            }
        } else {
            NovaUnavailableState("No gyroscope reported by this device.")
        }
    }
}

@Composable
private fun HorizonIndicator(pitch: Float, roll: Float, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = minOf(size.width, size.height) / 2 - 8f

        drawCircle(Color(0xFF232A36), radius = radius, center = Offset(cx, cy), style = Stroke(width = 2f))

        val rollRad = Math.toRadians(roll.toDouble())
        val pitchOffset = (pitch / 90f).coerceIn(-1f, 1f) * radius

        val dx = kotlin.math.cos(rollRad).toFloat()
        val dy = kotlin.math.sin(rollRad).toFloat()
        val lineLen = radius * 0.9f
        val center = Offset(cx, cy + pitchOffset)

        drawLine(
            NovaAccent,
            Offset(center.x - dx * lineLen, center.y - dy * lineLen),
            Offset(center.x + dx * lineLen, center.y + dy * lineLen),
            strokeWidth = 4f
        )
        drawCircle(NovaAccent, radius = 5f, center = Offset(cx, cy))
    }
}

// ---------- Magnetic field ----------

@Composable
fun MagneticFieldScreen() {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val available = remember { repository.hasSensor(Sensor.TYPE_MAGNETIC_FIELD) }
    var values by remember { mutableStateOf(FloatArray(3)) }

    LaunchedEffect(available) {
        if (available) repository.readings(Sensor.TYPE_MAGNETIC_FIELD).collectLatest { values = it }
    }

    val magnitude = sqrt(values[0] * values[0] + values[1] * values[1] + values[2] * values[2])
    // Earth's field is roughly 25-65 µT; well above ~150 µT commonly indicates nearby magnetic interference.
    val status = when {
        !available -> NovaStatus.UNAVAILABLE
        magnitude > 150f -> NovaStatus.WARN
        else -> NovaStatus.GOOD
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Magnetic Field", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        if (!available) {
            NovaUnavailableState("No magnetometer reported by this device.")
            return@Column
        }
        NovaCard {
            NovaBigReadout(value = "%.1f".format(magnitude), unit = "µT", label = "Field strength")
        }
        NovaCard {
            NovaSectionHeader("Axes (µT)")
            Spacer(Modifier.height(12.dp))
            NovaMetric("X", "%.1f".format(values.getOrElse(0) { 0f }))
            Spacer(Modifier.height(8.dp))
            NovaMetric("Y", "%.1f".format(values.getOrElse(1) { 0f }))
            Spacer(Modifier.height(8.dp))
            NovaMetric("Z", "%.1f".format(values.getOrElse(2) { 0f }))
        }
        if (status == NovaStatus.WARN) {
            NovaCard {
                Text(
                    "⚠ Elevated reading — likely a nearby magnet or metal object, not a sensor fault.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = NovaTextPrimary.copy(alpha = 0.8f)
                )
            }
        }
    }
}

// ---------- Light meter ----------

@Composable
fun LightMeterScreen() {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val available = remember { repository.hasSensor(Sensor.TYPE_LIGHT) }
    var lux by remember { mutableStateOf(0f) }

    LaunchedEffect(available) {
        if (available) repository.readings(Sensor.TYPE_LIGHT).collectLatest { lux = it.getOrElse(0) { 0f } }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Light Meter", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        if (!available) {
            NovaUnavailableState("No ambient light sensor reported by this device.")
            return@Column
        }
        NovaCard {
            NovaBigReadout(value = "%.0f".format(lux), unit = "lux", label = "Ambient illuminance")
        }
        NovaCard {
            NovaSectionHeader("Reference")
            Spacer(Modifier.height(10.dp))
            Text("Moonlight ≈ 0.1–1 lux · Living room ≈ 100–300 lux · Overcast day ≈ 1,000 lux · Direct sun ≈ 30,000+ lux",
                style = MaterialTheme.typography.bodyMedium, color = NovaTextPrimary.copy(alpha = 0.6f))
        }
    }
}

// ---------- Sound level (microphone) ----------

@Composable
fun SoundLevelScreen() {
    val context = LocalContext.current
    val granted = remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted -> granted.value = isGranted }

    var db by remember { mutableStateOf(0f) }

    if (!granted.value) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Sound Level", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(16.dp))
            NovaPermissionExplainer(
                icon = "🎙",
                title = "MICROPHONE",
                explanation = "NOVA reads live microphone amplitude on-device to show a sound-pressure meter. Audio is never recorded to a file or sent anywhere.",
                actionLabel = "Grant microphone access",
                onRequest = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }
        return
    }

    DisposableEffect(Unit) {
        val sampleRate = 44100
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        var recorder: AudioRecord? = null
        var running = true
        val thread = Thread {
            try {
                recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBuf.coerceAtLeast(2048))
                recorder?.startRecording()
                val buffer = ShortArray(minBuf.coerceAtLeast(2048))
                while (running) {
                    val read = recorder?.read(buffer, 0, buffer.size) ?: 0
                    if (read > 0) {
                        var sum = 0.0
                        for (i in 0 until read) sum += buffer[i] * buffer[i]
                        val rms = sqrt(sum / read)
                        val level = if (rms > 1) (20 * log10(rms)).toFloat() else 0f
                        db = level.coerceIn(0f, 120f)
                    }
                }
            } catch (e: SecurityException) {
                // Permission revoked mid-session — stop quietly, UI will just stop updating.
            } finally {
                recorder?.stop()
                recorder?.release()
            }
        }
        thread.start()
        onDispose {
            running = false
        }
    }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Sound Level", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        NovaCard {
            NovaBigReadout(value = db.roundToInt().toString(), unit = "dB (relative)", label = "Live amplitude")
        }
        NovaCard {
            NovaProgressBar(progress = db / 120f, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Text(
                "This is a relative amplitude reading from the built-in mic, not a calibrated SPL meter — useful for comparison, not certification-grade measurement.",
                style = MaterialTheme.typography.labelSmall,
                color = NovaTextPrimary.copy(alpha = 0.4f)
            )
        }
    }
}

// ---------- Sensor availability list ----------

@Composable
fun SensorListScreen() {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val sensors = remember { repository.allSensors().sortedBy { it.name } }

    Column(Modifier.fillMaxSize().padding(top = 20.dp, start = 20.dp, end = 20.dp)) {
        Text("Sensor Availability", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
        Text(
            "${sensors.size} sensors reported by this device",
            style = MaterialTheme.typography.bodyMedium,
            color = NovaTextPrimary.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(sensors) { sensor ->
                NovaCard {
                    NovaMetric(sensor.name, sensor.vendor, status = NovaStatus.GOOD)
                }
            }
        }
    }
}
