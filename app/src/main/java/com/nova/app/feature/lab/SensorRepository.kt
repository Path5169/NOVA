package com.nova.app.feature.lab

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Thin, honest wrapper over SensorManager. Every value emitted is a real reading
 * from a real SensorEvent — nothing here is synthesized. If a sensor type is
 * absent on the device, callers get an empty/absent result and must say so in the UI.
 */
class SensorRepository(context: Context) {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun hasSensor(type: Int): Boolean = sensorManager.getDefaultSensor(type) != null

    fun allSensors(): List<Sensor> = sensorManager.getSensorList(Sensor.TYPE_ALL)

    /** Emits raw values for a single sensor type at the requested sampling rate. */
    fun readings(type: Int, samplingPeriodUs: Int = SensorManager.SENSOR_DELAY_UI): Flow<FloatArray> = callbackFlow {
        val sensor = sensorManager.getDefaultSensor(type)
        if (sensor == null) {
            close()
            return@callbackFlow
        }
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                trySend(event.values.copyOf())
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, samplingPeriodUs)
        awaitClose { sensorManager.unregisterListener(listener) }
    }
}
