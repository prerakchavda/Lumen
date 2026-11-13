package com.seerflix.tv.feature.requests

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.MediaRequestSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class RequestFilter(val label: String) {
    ALL("All"),
    DOWNLOADING("Downloading"),
    PENDING("Requested"),
    AVAILABLE("Available")
}

data class RequestsUiState(
    val filter: RequestFilter = RequestFilter.ALL,
    val allRequests: List<MediaRequestSummary> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null
) {
    val visibleRequests: List<MediaRequestSummary>
        get() = when (filter) {
            RequestFilter.ALL -> allRequests
            RequestFilter.DOWNLOADING -> allRequests.filter {
                it.downloadProgress != null || it.mediaStatus == AvailabilityTier.PROCESSING
            }
            RequestFilter.PENDING -> allRequests.filter {
                it.mediaStatus == AvailabilityTier.PENDING || it.requestStatus == 1
            }
            RequestFilter.AVAILABLE -> allRequests.filter {
                it.mediaStatus.isInLibrary
            }
        }
}

@HiltViewModel
class RequestsViewModel @Inject constructor(
    private val catalogRepository: UnifiedCatalogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                refresh(silent = true)
            }
        }
    }

    fun selectFilter(filter: RequestFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun refresh(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            }
            catalogRepository.loadRequestsHub()
                .onSuccess { list ->
                    _uiState.update { it.copy(allRequests = list, isLoading = false, errorMessage = null) }
                }
                .onFailure { err ->
                    if (!silent) {
                        _uiState.update {
                            it.copy(isLoading = false, errorMessage = err.message ?: "Failed to load requests")
                        }
                    }
                }
        }
    }
}
