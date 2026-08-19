package com.nova.app.feature.private_space.notes

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    val notes: List<PrivateNote> = emptyList(),
    val query: String = "",
    val loading: Boolean = true
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = NotesRepository(application)
    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val notes = repository.list(_state.value.query)
            _state.update { it.copy(notes = notes, loading = false) }
        }
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
        viewModelScope.launch {
            val notes = repository.list(query)
            _state.update { it.copy(notes = notes) }
        }
    }

    fun save(id: String?, title: String, text: String, tags: List<String>, onDone: () -> Unit) {
        viewModelScope.launch {
            repository.upsert(id, title, text, tags)
            refresh()
            onDone()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.delete(id)
            refresh()
        }
    }
}
