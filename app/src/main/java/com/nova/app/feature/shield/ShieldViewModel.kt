package com.nova.app.feature.shield

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ShieldViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShieldRepository(application.applicationContext)

    val uiState: StateFlow<ShieldUiState> = combine(
        repository.running, repository.stats, repository.recentBlocks, repository.error
    ) { running, stats, recent, error ->
        ShieldUiState(
            state = when {
                error != null -> ShieldState.ERROR
                running -> ShieldState.ACTIVE
                else -> ShieldState.INACTIVE
            },
            stats = stats,
            recentBlocks = recent,
            errorMessage = error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ShieldUiState())

    /** Domains from both the bundled starter list and the user's custom list, for the
     * Blocklist screen. Bundled entries are shown read-only; custom entries can be removed. */
    val customBlocked: StateFlow<Set<String>> = repository.blocklistStore.customBlockedFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allowed: StateFlow<Set<String>> = repository.blocklistStore.allowedFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Returns a consent Intent to launch if the system VPN permission dialog is needed,
     * or null if NOVA can start filtering immediately. */
    fun prepareIntentIfNeeded(): Intent? = repository.prepareIntent()

    fun start() = repository.start()

    fun stop() {
        viewModelScope.launch { repository.stop() }
    }

    fun addCustomBlocked(domain: String) {
        viewModelScope.launch { repository.blocklistStore.addCustomBlocked(domain) }
    }

    fun removeCustomBlocked(domain: String) {
        viewModelScope.launch { repository.blocklistStore.removeCustomBlocked(domain) }
    }

    /** One-shot read of the bundled starter list, for the read-only section of the Blocklist screen. */
    suspend fun bundledBlocklistSnapshot(): Set<String> = repository.blocklistStore.bundledBlocklist()

    fun addAllowed(domain: String) {
        viewModelScope.launch { repository.blocklistStore.addAllowed(domain) }
    }

    fun removeAllowed(domain: String) {
        viewModelScope.launch { repository.blocklistStore.removeAllowed(domain) }
    }
}
