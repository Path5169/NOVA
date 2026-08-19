package com.nova.app.feature.lab

import android.hardware.Sensor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nova.app.ui.components.NovaCard
import com.nova.app.ui.components.NovaMetric
import com.nova.app.ui.components.NovaSectionHeader
import com.nova.app.ui.components.NovaUnavailableState
import com.nova.app.ui.theme.NovaTextPrimary
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.sqrt

private const val HISTORY_SIZE = 120

@Composable
fun MotionGraphScreen() {
    val context = LocalContext.current
    val repository = remember { SensorRepository(context) }
    val available = remember { repository.hasSensor(Sensor.TYPE_ACCELEROMETER) }

    var latest by remember { mutableStateOf(FloatArray(3)) }
    val historyX = remember { mutableStateListOf<Float>() }
    val historyY = remember { mutableStateListOf<Float>() }
    val historyZ = remember { mutableStateListOf<Float>() }

    if (!available) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Text("Motion Graph", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)
            Spacer(Modifier.height(16.dp))
            NovaUnavailableState("This device does not report an accelerometer.")
        }
        return
    }

    LaunchedEffect(Unit) {
        repository.readings(Sensor.TYPE_ACCELEROMETER).collectLatest { values ->
            latest = values
            fun push(list: MutableList<Float>, v: Float) {
                list.add(v)
                if (list.size > HISTORY_SIZE) list.removeAt(0)
            }
            push(historyX, values.getOrElse(0) { 0f })
            push(historyY, values.getOrElse(1) { 0f })
            push(historyZ, values.getOrElse(2) { 0f })
        }
    }

    val magnitude = sqrt(latest[0] * latest[0] + latest[1] * latest[1] + latest[2] * latest[2])

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Motion Graph", style = MaterialTheme.typography.titleLarge, color = NovaTextPrimary)

        NovaCard {
            NovaSectionHeader("Live axes (m/s²)")
            Spacer(Modifier.height(12.dp))
            NovaMetric("X", "%.2f".format(latest.getOrElse(0) { 0f }))
            Spacer(Modifier.height(8.dp))
            NovaMetric("Y", "%.2f".format(latest.getOrElse(1) { 0f }))
            Spacer(Modifier.height(8.dp))
            NovaMetric("Z", "%.2f".format(latest.getOrElse(2) { 0f }))
            Spacer(Modifier.height(8.dp))
            NovaMetric("Magnitude", "%.2f".format(magnitude))
        }

        NovaCard {
            NovaSectionHeader("Waveform")
            Spacer(Modifier.height(12.dp))
            TriAxisGraph(
                x = historyX, y = historyY, z = historyZ,
                modifier = Modifier.fillMaxWidth().height(180.dp)
            )
        }
    }
}

@Composable
fun TriAxisGraph(x: List<Float>, y: List<Float>, z: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.background(Color(0xFF0D1117))) {
        val range = 20f // ±20 m/s^2 covers accel+gravity comfortably
        fun plot(values: List<Float>, color: Color) {
            if (values.size < 2) return
            val stepX = size.width / (HISTORY_SIZE - 1)
            var prev: Offset? = null
            values.forEachIndexed { i, v ->
                val xPos = i * stepX
                val norm = (v / range).coerceIn(-1f, 1f)
                val yPos = size.height / 2 - norm * (size.height / 2)
                val point = Offset(xPos, yPos)
                if (prev != null) drawLine(color, prev!!, point, strokeWidth = 3f)
                prev = point
            }
        }
        drawLine(Color(0xFF232A36), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 1f)
        plot(x, Color(0xFFE05252))
        plot(y, Color(0xFF3DDC97))
        plot(z, Color(0xFF4C9FE8))
    }
}
