package com.nova.app.ui.navigation

sealed class NovaDestination(val route: String) {
    data object Home : NovaDestination("home")

    // NOVA DEVICE
    data object Device : NovaDestination("device")
    data object DeviceTouchTest : NovaDestination("device/touch")
    data object DeviceDisplayTest : NovaDestination("device/display")
    data object DeviceVibrationTest : NovaDestination("device/vibration")
    data object DeviceFlashlightTest : NovaDestination("device/flashlight")
    data object DeviceCameraTest : NovaDestination("device/camera")

    // NOVA LAB
    data object Lab : NovaDestination("lab")
    data object LabMotion : NovaDestination("lab/motion")
    data object LabRotation : NovaDestination("lab/rotation")
    data object LabMagnetic : NovaDestination("lab/magnetic")
    data object LabLight : NovaDestination("lab/light")
    data object LabSound : NovaDestination("lab/sound")
    data object LabProximity : NovaDestination("lab/proximity")
    data object LabBarometer : NovaDestination("lab/barometer")
    data object LabGps : NovaDestination("lab/gps")
    data object LabSensorList : NovaDestination("lab/sensors")

    // NOVA TOOLS
    data object Tools : NovaDestination("tools")
    data object ToolCalculator : NovaDestination("tools/calculator")
    data object ToolBase64 : NovaDestination("tools/base64")
    data object ToolJson : NovaDestination("tools/json")
    data object ToolUuid : NovaDestination("tools/uuid")
    data object ToolHash : NovaDestination("tools/hash")
    data object ToolTimestamp : NovaDestination("tools/timestamp")
    data object ToolUnitConverter : NovaDestination("tools/units")
    data object ToolUrlEncode : NovaDestination("tools/urlencode")

    // NOVA NETWORK
    data object Network : NovaDestination("network")
    data object NetworkDiagnostics : NovaDestination("network/diagnostics")
    data object NetworkLanScan : NovaDestination("network/lanscan")

    // NOVA VISION
    data object Vision : NovaDestination("vision")
    data object VisionBarcode : NovaDestination("vision/barcode")
    data object VisionOcr : NovaDestination("vision/ocr")

    // Phase 3 module — App Intelligence, upgraded from the original Privacy dashboard.
    data object Privacy : NovaDestination("privacy")

    // NOVA APP SPACE
    data object AppSpace : NovaDestination("appspace")
    data object AppProfile : NovaDestination("appspace/profile/{packageName}") {
        fun routeFor(packageName: String) = "appspace/profile/$packageName"
    }

    // NOVA DETECTIVE
    data object Detective : NovaDestination("detective")
    data object DetectiveUrl : NovaDestination("detective/url")
    data object DetectiveFile : NovaDestination("detective/file")
    data object DetectiveImage : NovaDestination("detective/image")
    data object DetectivePermissionMatrix : NovaDestination("detective/matrix")

    // NOVA SHIELD
    data object Shield : NovaDestination("shield")
    data object ShieldBlocklist : NovaDestination("shield/blocklist")
    data object ShieldAllowlist : NovaDestination("shield/allowlist")

    // NOVA PRIVATE
    data object PrivateHome : NovaDestination("private")
    data object PrivateVault : NovaDestination("private/vault")
    data object PrivateNotes : NovaDestination("private/notes")
}
