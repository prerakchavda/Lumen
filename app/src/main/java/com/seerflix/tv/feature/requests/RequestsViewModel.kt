package com.seerflix.tv.feature.requests

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.MediaRequestSummary
import com.seerflix.tv.core.network.SessionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

@Immutable
data class RequestsUiState(
    val filter: RequestFilter = RequestFilter.ALL,
    val allRequests: List<MediaRequestSummary> = emptyList(),
    val isAdmin: Boolean = false,
    val isSeerrConfigured: Boolean = true,
    val busyRequestId: Int? = null,
    val actionMessage: String? = null,
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
    private val catalogRepository: UnifiedCatalogRepository,
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(RequestsUiState())
    val uiState: StateFlow<RequestsUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch(Dispatchers.IO) {
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
        viewModelScope.launch(Dispatchers.IO) {
            val isAdmin = authRepository.activeProfileFlow.first()?.isAdmin == true
            val seerrConfigured = sessionPreferences.isSeerrConfigured
            if (!seerrConfigured) {
                _uiState.update {
                    it.copy(
                        isAdmin = isAdmin,
                        isSeerrConfigured = false,
                        allRequests = emptyList(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
                return@launch
            }
            if (!silent) {
                _uiState.update { it.copy(isLoading = true, errorMessage = null, isAdmin = isAdmin, isSeerrConfigured = true) }
            }
            catalogRepository.loadRequestsHub()
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            allRequests = list,
                            isAdmin = isAdmin,
                            isSeerrConfigured = true,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    if (!silent) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = UnifiedCatalogRepository.formatHttpError(err, "Failed to load requests")
                            )
                        }
                    }
                }
        }
    }

    fun approveRequest(requestId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busyRequestId = requestId, actionMessage = null) }
            catalogRepository.approveRequest(requestId)
                .onSuccess {
                    _uiState.update { it.copy(busyRequestId = null, actionMessage = "Request #$requestId approved") }
                    refresh(silent = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            busyRequestId = null,
                            actionMessage = UnifiedCatalogRepository.formatHttpError(err, "Approve failed")
                        )
                    }
                }
        }
    }

    fun declineRequest(requestId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(busyRequestId = requestId, actionMessage = null) }
            catalogRepository.declineRequest(requestId)
                .onSuccess {
                    _uiState.update { it.copy(busyRequestId = null, actionMessage = "Request #$requestId declined") }
                    refresh(silent = true)
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            busyRequestId = null,
                            actionMessage = UnifiedCatalogRepository.formatHttpError(err, "Decline failed")
                        )
                    }
                }
        }
    }
}

