package com.nova.app.feature.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class DeviceUiState {
    data object Loading : DeviceUiState()
    data class Ready(val snapshot: DeviceSnapshot) : DeviceUiState()
}

class DeviceViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DeviceInfoRepository(application.applicationContext)

    private val _uiState = MutableStateFlow<DeviceUiState>(DeviceUiState.Loading)
    val uiState: StateFlow<DeviceUiState> = _uiState

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = DeviceUiState.Loading
            val snapshot = repository.snapshot()
            _uiState.value = DeviceUiState.Ready(snapshot)
        }
    }
}
