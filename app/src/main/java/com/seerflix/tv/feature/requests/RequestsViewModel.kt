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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
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
    /** Kids profile that isn't allowed to request: hide the household's request list entirely. */
    val isKidsLocked: Boolean = false,
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

    private var refreshJob: Job? = null
    private var hasBeenVisible = false

    init {
        refresh()
    }

    /**
     * Polls every 15s for as long as the caller's coroutine is active. The screen runs this inside
     * repeatOnLifecycle(STARTED), so polling stops while Details/Player is on top of Requests (the
     * old init-loop kept hitting Seerr every 15s for as long as the entry sat in the back stack).
     */
    suspend fun autoRefreshWhileVisible() {
        if (hasBeenVisible) {
            // Coming back (e.g. after requesting from Details): refresh right away.
            refresh(silent = true)
        }
        hasBeenVisible = true
        while (true) {
            delay(15_000L)
            refresh(silent = true)
        }
    }

    fun selectFilter(filter: RequestFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    /**
     * @param force replace an in-flight refresh. Background polls never interrupt a running load
     * (cancelling a user-triggered load would otherwise leave the spinner up).
     */
    fun refresh(silent: Boolean = false, force: Boolean = !silent) {
        if (!force && refreshJob?.isActive == true) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch(Dispatchers.IO) {
            val profile = authRepository.activeProfileFlow.first()
            val kidsMode = profile?.kidsMode == true
            // Approve/Decline are admin actions; never expose them on a kids profile even if the
            // underlying Jellyfin account is an admin.
            val isAdmin = profile?.isAdmin == true && !kidsMode
            val kidsLocked = profile != null && !profile.canSubmitRequests
            val seerrConfigured = sessionPreferences.isSeerrConfigured
            if (!seerrConfigured || kidsLocked) {
                _uiState.update {
                    it.copy(
                        isAdmin = isAdmin,
                        isSeerrConfigured = seerrConfigured,
                        isKidsLocked = kidsLocked,
                        allRequests = emptyList(),
                        isLoading = false,
                        errorMessage = null
                    )
                }
                return@launch
            }
            if (!silent) {
                _uiState.update {
                    it.copy(
                        isLoading = true,
                        errorMessage = null,
                        isAdmin = isAdmin,
                        isSeerrConfigured = true,
                        isKidsLocked = false
                    )
                }
            }
            val result = catalogRepository.loadRequestsHub()
            // loadRequestsHub() wraps everything (CancellationException included) in a Result, so
            // bail out here rather than writing a superseded or cancelled result into the state.
            ensureActive()
            result
                .onSuccess { list ->
                    _uiState.update {
                        it.copy(
                            allRequests = list.distinctBy { req -> req.requestId },
                            isAdmin = isAdmin,
                            isSeerrConfigured = true,
                            isKidsLocked = false,
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
                .onFailure { err ->
                    // Background polls stay quiet, unless they replaced a load that was showing the
                    // spinner (otherwise it would spin forever).
                    if (!silent || _uiState.value.isLoading) {
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
        // Claim the busy slot synchronously: setting it inside the IO coroutine let a quick
        // double OK start two requests before either had marked itself busy.
        if (_uiState.value.busyRequestId != null) return
        _uiState.update { it.copy(busyRequestId = requestId, actionMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.approveRequest(requestId)
                .onSuccess {
                    // Reflect the new status immediately (Seerr: 2 = approved) so the admin buttons
                    // go away together with the busy state, then reconcile with the server.
                    _uiState.update {
                        it.copy(
                            busyRequestId = null,
                            actionMessage = "Request #$requestId approved",
                            allRequests = it.allRequests.withRequestStatus(requestId, 2)
                        )
                    }
                    refresh(silent = true, force = true)
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
        // Claim the busy slot synchronously: setting it inside the IO coroutine let a quick
        // double OK start two requests before either had marked itself busy.
        if (_uiState.value.busyRequestId != null) return
        _uiState.update { it.copy(busyRequestId = requestId, actionMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.declineRequest(requestId)
                .onSuccess {
                    // Seerr: 3 = declined.
                    _uiState.update {
                        it.copy(
                            busyRequestId = null,
                            actionMessage = "Request #$requestId declined",
                            allRequests = it.allRequests.withRequestStatus(requestId, 3)
                        )
                    }
                    refresh(silent = true, force = true)
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

    private fun List<MediaRequestSummary>.withRequestStatus(requestId: Int, status: Int) =
        map { if (it.requestId == requestId) it.copy(requestStatus = status) else it }
}

