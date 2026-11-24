package io.kunalmno.zefer.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.kunalmno.zefer.data.Catalog
import io.kunalmno.zefer.data.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Error(val message: String) : CatalogUiState
    data class Ready(val catalog: Catalog) : CatalogUiState
}

class CatalogViewModel : ViewModel() {

    private val repository = CatalogRepository()
    private val _state = MutableStateFlow<CatalogUiState>(CatalogUiState.Loading)
    val state: StateFlow<CatalogUiState> = _state.asStateFlow()

    private var job: Job? = null

    init { load() }

    /** Fetches products + deals. No-op while a request is already running. */
    fun load() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            try {
                _state.value = CatalogUiState.Ready(repository.fetch())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // keep showing the old data if we already have some (e.g. refresh on resume failed)
                if (_state.value !is CatalogUiState.Ready) {
                    _state.value = CatalogUiState.Error(e.message ?: "Couldn't load the catalog.")
                }
            }
        }
    }

    /** Retry button on the error screen. */
    fun retry() {
        _state.value = CatalogUiState.Loading
        load()
    }
}
