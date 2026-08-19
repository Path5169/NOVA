package com.nova.app.feature.private_space.vault

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val items: List<VaultItem> = emptyList(),
    val query: String = "",
    val loading: Boolean = true,
    val error: String? = null
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VaultRepository(application)
    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val items = repository.listItems(_state.value.query)
            _state.update { it.copy(items = items, loading = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        viewModelScope.launch {
            val items = repository.listItems(query)
            _state.update { it.copy(items = items) }
        }
    }

    fun importFile(uri: Uri, type: VaultItemType) {
        viewModelScope.launch {
            val result = repository.importFromUri(uri, type)
            result.onFailure { e -> _state.update { it.copy(error = e.message ?: "Import failed") } }
            refresh()
        }
    }

    fun addNoteOrBookmark(title: String, text: String, type: VaultItemType, bookmarkUrl: String? = null) {
        viewModelScope.launch {
            repository.addTextItem(title, text, type, bookmarkUrl)
            refresh()
        }
    }

    fun delete(item: VaultItem) {
        viewModelScope.launch {
            repository.delete(item)
            refresh()
        }
    }

    fun exportTo(item: VaultItem, destination: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = repository.exportTo(item, destination)
            onDone(result.isSuccess)
        }
    }

    suspend fun readContent(item: VaultItem): ByteArray? = repository.readContent(item)

    fun clearError() = _state.update { it.copy(error = null) }
}
