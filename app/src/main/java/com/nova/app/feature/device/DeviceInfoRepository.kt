package com.nova.app.feature.device

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.util.DisplayMetrics
import androidx.core.content.ContextCompat

data class MemoryInfo(val totalMb: Long, val availableMb: Long, val lowMemory: Boolean)
data class StorageInfo(val totalGb: Double, val freeGb: Double)
data class BatteryInfo(val percent: Int?, val isCharging: Boolean?, val temperatureC: Float?)
data class DisplayInfo(val widthPx: Int, val heightPx: Int, val density: Float, val refreshRateHz: Float?)
data class CpuInfo(val abis: List<String>, val cores: Int)
data class CameraInfo(val hasCamera: Boolean, val hasFrontCamera: Boolean, val hasFlash: Boolean)

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val sdkInt: Int,
    val memory: MemoryInfo,
    val storage: StorageInfo,
    val battery: BatteryInfo,
    val display: DisplayInfo,
    val cpu: CpuInfo,
    val camera: CameraInfo,
    val sensorCount: Int,
    val totalKnownSensorTypes: Int
)

/**
 * Reads only device information Android legitimately exposes to a normal,
 * non-privileged app. Nothing here is estimated, guessed, or hard-coded.
 * If a value genuinely isn't available on this device/API level, the field is null
 * and the UI is responsible for saying so explicitly rather than hiding it.
 */
class DeviceInfoRepository(private val context: Context) {

    fun snapshot(): DeviceSnapshot {
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            androidVersion = Build.VERSION.RELEASE ?: "Unknown",
            sdkInt = Build.VERSION.SDK_INT,
            memory = readMemory(),
            storage = readStorage(),
            battery = readBattery(),
            display = readDisplay(),
            cpu = readCpu(),
            camera = readCamera(),
            sensorCount = readSensorList().size,
            totalKnownSensorTypes = KNOWN_SENSOR_TYPES.size
        )
    }

    private fun readMemory(): MemoryInfo {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return MemoryInfo(
            totalMb = info.totalMem / (1024 * 1024),
            availableMb = info.availMem / (1024 * 1024),
            lowMemory = info.lowMemory
        )
    }

    private fun readStorage(): StorageInfo {
        val stat = StatFs(context.filesDir.absolutePath)
        val total = stat.blockCountLong * stat.blockSizeLong
        val free = stat.availableBlocksLong * stat.blockSizeLong
        val gb = 1024.0 * 1024.0 * 1024.0
        return StorageInfo(totalGb = total / gb, freeGb = free / gb)
    }

    private fun readBattery(): BatteryInfo {
        // Registering a null receiver against ACTION_BATTERY_CHANGED is the standard
        // sticky-intent trick to read the last broadcast without a running receiver.
        val intent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = if (status >= 0) {
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        } else null
        // Android does NOT expose battery "health" (degradation %) to normal apps — only
        // a coarse BATTERY_HEALTH_* enum (e.g. GOOD/OVERHEAT/DEAD) with no percentage.
        // NOVA deliberately does not surface a fake health percentage.
        val tempTenthsC = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        val tempC = if (tempTenthsC != Int.MIN_VALUE) tempTenthsC / 10f else null
        return BatteryInfo(percent = percent, isCharging = charging, temperatureC = tempC)
    }

    private fun readDisplay(): DisplayInfo {
        val dm: DisplayMetrics = context.resources.displayMetrics
        val refreshRate: Float? = try {
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                context.display
            } else {
                @Suppress("DEPRECATION")
                (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay
            }
            display?.refreshRate
        } catch (e: Exception) {
            null
        }
        return DisplayInfo(
            widthPx = dm.widthPixels,
            heightPx = dm.heightPixels,
            density = dm.density,
            refreshRateHz = refreshRate
        )
    }

    private fun readCpu(): CpuInfo {
        return CpuInfo(
            abis = Build.SUPPORTED_ABIS?.toList() ?: emptyList(),
            cores = Runtime.getRuntime().availableProcessors()
        )
    }

    private fun readCamera(): CameraInfo {
        val pm = context.packageManager
        return CameraInfo(
            hasCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY),
            hasFrontCamera = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FRONT),
            hasFlash = pm.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)
        )
    }

    fun readSensorList(): List<Sensor> {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        return sm.getSensorList(Sensor.TYPE_ALL)
    }

    fun hasFlashlight(): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH)

    companion object {
        // The set of sensor types NOVA Lab actively surfaces as named instruments.
        // Used only to compute "N of M" availability — not a claim every phone has all of them.
        val KNOWN_SENSOR_TYPES = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD,
            Sensor.TYPE_LIGHT,
            Sensor.TYPE_PROXIMITY,
            Sensor.TYPE_PRESSURE,
            Sensor.TYPE_GRAVITY,
            Sensor.TYPE_LINEAR_ACCELERATION,
            Sensor.TYPE_ROTATION_VECTOR,
            Sensor.TYPE_STEP_COUNTER,
            Sensor.TYPE_STEP_DETECTOR,
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_RELATIVE_HUMIDITY,
            Sensor.TYPE_ORIENTATION,
            Sensor.TYPE_GAME_ROTATION_VECTOR
        )
    }
}
