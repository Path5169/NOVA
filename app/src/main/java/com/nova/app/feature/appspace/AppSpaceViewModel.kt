package com.nova.app.feature.appspace

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class AppListUiState {
    data object Loading : AppListUiState()
    data class Loaded(val apps: List<AppEntry>) : AppListUiState()
}

sealed class AppProfileUiState {
    data object Loading : AppProfileUiState()
    data class Loaded(val profile: AppProfile) : AppProfileUiState()
    data object NotFound : AppProfileUiState()
}

sealed class AppDiagnosticUiState {
    data object Idle : AppDiagnosticUiState()
    data object Running : AppDiagnosticUiState()
    data class Done(val report: AppDiagnosticReport) : AppDiagnosticUiState()
}

class AppSpaceViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppSpaceRepository(application.applicationContext)

    private val _listState = MutableStateFlow<AppListUiState>(AppListUiState.Loading)
    val listState: StateFlow<AppListUiState> = _listState

    private val _profileState = MutableStateFlow<AppProfileUiState>(AppProfileUiState.Loading)
    val profileState: StateFlow<AppProfileUiState> = _profileState

    private val _diagnosticState = MutableStateFlow<AppDiagnosticUiState>(AppDiagnosticUiState.Idle)
    val diagnosticState: StateFlow<AppDiagnosticUiState> = _diagnosticState

    fun loadApps() {
        viewModelScope.launch {
            _listState.value = AppListUiState.Loading
            _listState.value = AppListUiState.Loaded(repo.listVisibleApps())
        }
    }

    fun loadProfile(packageName: String) {
        viewModelScope.launch {
            _profileState.value = AppProfileUiState.Loading
            _diagnosticState.value = AppDiagnosticUiState.Idle
            val profile = repo.loadProfile(packageName)
            _profileState.value = if (profile != null) AppProfileUiState.Loaded(profile) else AppProfileUiState.NotFound
        }
    }

    fun runDiagnostic(packageName: String) {
        viewModelScope.launch {
            _diagnosticState.value = AppDiagnosticUiState.Running
            val report = repo.runDiagnostic(packageName)
            _diagnosticState.value = AppDiagnosticUiState.Done(report)
        }
    }

    fun hasUsageAccess(): Boolean = repo.hasUsageAccess()
    fun requestUsageAccessIntent() = repo.requestUsageAccessIntent()
    fun appInfoSettingsIntent(packageName: String) = repo.appInfoSettingsIntent(packageName)
    fun launchIntentFor(packageName: String) = repo.launchIntentFor(packageName)
}
