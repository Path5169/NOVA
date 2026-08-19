package com.nova.app.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nova.app.feature.device.DeviceInfoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class ScanUiState {
    data object Idle : ScanUiState()
    data class Scanning(val progress: Float) : ScanUiState()
    data class Done(val report: ScanReport) : ScanUiState()
}

data class HomeHeaderState(
    val batteryPercent: Int?,
    val isCharging: Boolean?,
    val sensorsAvailable: Int
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val deviceRepo = DeviceInfoRepository(application.applicationContext)
    private val scanRepo = DeepScanRepository(application.applicationContext)

    private val _header = MutableStateFlow(HomeHeaderState(null, null, 0))
    val header: StateFlow<HomeHeaderState> = _header

    private val _scanState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)
    val scanState: StateFlow<ScanUiState> = _scanState

    init {
        refreshHeader()
    }

    fun refreshHeader() {
        viewModelScope.launch {
            val snap = deviceRepo.snapshot()
            _header.value = HomeHeaderState(
                batteryPercent = snap.battery.percent,
                isCharging = snap.battery.isCharging,
                sensorsAvailable = snap.sensorCount
            )
        }
    }

    fun runDeepScan() {
        if (_scanState.value is ScanUiState.Scanning) return
        viewModelScope.launch {
            _scanState.value = ScanUiState.Scanning(0f)
            val report = scanRepo.runScan { progress ->
                _scanState.value = ScanUiState.Scanning(progress)
            }
            _scanState.value = ScanUiState.Done(report)
            refreshHeader()
        }
    }

    fun dismissScan() {
        _scanState.value = ScanUiState.Idle
    }
}
