package com.nova.app.feature.privacy

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class PrivacyUiState {
    data object Loading : PrivacyUiState()
    data class Loaded(val snapshot: PrivacySnapshot) : PrivacyUiState()
}

class PrivacyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PrivacyRepository(application.applicationContext)

    private val _state = MutableStateFlow<PrivacyUiState>(PrivacyUiState.Loading)
    val state: StateFlow<PrivacyUiState> = _state

    private var loaded = false

    init {
        refresh()
    }

    /** Cheap guard so returning to the screen doesn't re-run the app scan every time. */
    fun refresh(force: Boolean = false) {
        if (loaded && !force) return
        viewModelScope.launch {
            _state.value = PrivacyUiState.Loading
            val snapshot = repository.snapshot()
            _state.value = PrivacyUiState.Loaded(snapshot)
            loaded = true
        }
    }
}
