package com.seerflix.tv.feature.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.core.player.YouTubeTrailerResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DetailsUiState(
    val item: UnifiedMediaItem? = null,
    val recommendations: List<UnifiedMediaItem> = emptyList(),
    val collectionSaga: CollectionSummary? = null,
    val selectedSeasonNumber: Int = 1,
    val episodes: List<EpisodeSummary> = emptyList(),
    val episodesLoading: Boolean = false,
    val previewStream: ResolvedPreviewStream? = null,
    val has4kServer: Boolean = true,
    val showRequestModal: Boolean = false,
    val isSubmittingRequest: Boolean = false,
    val feedbackBanner: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver
) : ViewModel() {

    private val tmdbIdArg: Int? = savedStateHandle.get<String>("tmdbId")?.toIntOrNull()?.takeIf { it > 0 }
    private val jellyfinIdArg: String? = savedStateHandle.get<String>("jellyfinId")?.takeIf { it.isNotBlank() && it != "none" }
    private val mediaTypeArg: MediaType = runCatching {
        MediaType.valueOf(savedStateHandle.get<String>("mediaType") ?: "MOVIE")
    }.getOrDefault(MediaType.MOVIE)

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var activeUserId: String = ""

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active profile") }
                return@launch
            }
            activeUserId = profile.jellyfinUserId

            val result = catalogRepository.loadFullDetails(
                userId = activeUserId,
                tmdbId = tmdbIdArg,
                jellyfinId = jellyfinIdArg,
                mediaType = mediaTypeArg
            )

            result.onSuccess { item ->
                val defaultSeason = item.seasons.firstOrNull()?.seasonNumber ?: 1
                _uiState.update {
                    it.copy(
                        item = item,
                        selectedSeasonNumber = defaultSeason,
                        isLoading = false
                    )
                }

                launch {
                    val has4k = catalogRepository.has4kServerConfigured(item.mediaType)
                    _uiState.update { it.copy(has4kServer = has4k) }
                }
                launch {
                    val recs = catalogRepository.loadRecommendations(
                        userId = activeUserId,
                        tmdbId = item.tmdbId,
                        jellyfinId = item.jellyfinId,
                        mediaType = item.mediaType
                    )
                    _uiState.update { it.copy(recommendations = recs) }
                }
                item.collectionId?.let { colId ->
                    launch {
                        val saga = catalogRepository.loadCollectionSaga(activeUserId, colId)
                        _uiState.update { it.copy(collectionSaga = saga) }
                    }
                }
                launch {
                    val preview = trailerResolver.resolvePreview(
                        localTrailerId = item.localTrailerItemId,
                        youtubeKey = item.youtubeTrailerKey
                    )
                    _uiState.update { it.copy(previewStream = preview) }
                }
                if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                    selectSeason(defaultSeason)
                }

                startLivePollingIfNeeded(item)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = err.message ?: "Failed to load details")
                }
            }
        }
    }

    fun selectSeason(seasonNumber: Int) {
        val item = _uiState.value.item ?: return
        val season = item.seasons.firstOrNull { it.seasonNumber == seasonNumber }
        viewModelScope.launch {
            _uiState.update { it.copy(selectedSeasonNumber = seasonNumber, episodesLoading = true) }
            val eps = catalogRepository.loadSeasonEpisodes(
                userId = activeUserId,
                tmdbId = item.tmdbId,
                jellyfinSeriesId = item.jellyfinId,
                jellyfinSeasonId = season?.jellyfinSeasonId,
                seasonNumber = seasonNumber
            )
            _uiState.update { it.copy(episodes = eps, episodesLoading = false) }
        }
    }

    fun openRequestModal() {
        _uiState.update { it.copy(showRequestModal = true, feedbackBanner = null) }
    }

    fun closeRequestModal() {
        _uiState.update { it.copy(showRequestModal = false) }
    }

    fun submitRequest(requestHd: Boolean, request4k: Boolean, seasons: List<Int>?) {
        val item = _uiState.value.item ?: return
        val tmdbId = item.tmdbId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingRequest = true, feedbackBanner = null) }
            var anySuccess = false
            var lastError: String? = null

            if (requestHd) {
                catalogRepository.submitRequest(tmdbId, item.mediaType, is4k = false, seasons = seasons)
                    .onSuccess { anySuccess = true }
                    .onFailure { lastError = it.message }
            }
            if (request4k) {
                catalogRepository.submitRequest(tmdbId, item.mediaType, is4k = true, seasons = seasons)
                    .onSuccess { anySuccess = true }
                    .onFailure { lastError = it.message }
            }

            _uiState.update {
                it.copy(
                    isSubmittingRequest = false,
                    showRequestModal = false,
                    feedbackBanner = if (anySuccess) "Request sent to Seerr!" else "Request failed: $lastError"
                )
            }
            if (anySuccess) {
                refreshStatusSilently()
            }
        }
    }

    fun cancelRequest(requestId: Int) {
        viewModelScope.launch {
            catalogRepository.cancelRequest(requestId)
                .onSuccess {
                    _uiState.update { it.copy(feedbackBanner = "Request cancelled") }
                    refreshStatusSilently()
                }
                .onFailure { err ->
                    _uiState.update { it.copy(feedbackBanner = "Cancel failed: ${err.message}") }
                }
        }
    }

    fun requestCollectionItem(part: UnifiedMediaItem, is4k: Boolean = false) {
        val tmdbId = part.tmdbId ?: return
        viewModelScope.launch {
            catalogRepository.submitRequest(tmdbId = tmdbId, mediaType = part.mediaType, is4k = is4k)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedSaga = state.collectionSaga?.let { saga ->
                            saga.copy(
                                items = saga.items.map { p ->
                                    if (p.tmdbId == tmdbId) {
                                        if (is4k) p.copy(uhdStatus = AvailabilityTier.PENDING)
                                        else p.copy(hdStatus = AvailabilityTier.PENDING)
                                    } else p
                                }
                            )
                        }
                        state.copy(
                            collectionSaga = updatedSaga,
                            feedbackBanner = "Requested \"${part.title}\""
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(feedbackBanner = "Request failed: ${err.message ?: "Unknown error"}")
                    }
                }
        }
    }

    fun requestAllMissingInCollection(is4k: Boolean = false) {
        val saga = _uiState.value.collectionSaga ?: return
        val missing = saga.items.filter { !it.isPlayable && it.canRequestHd && it.tmdbId != null }
        if (missing.isEmpty()) return
        viewModelScope.launch {
            var count = 0
            val requestedIds = mutableSetOf<Int>()
            missing.forEach { part ->
                val tmdb = part.tmdbId ?: return@forEach
                catalogRepository.submitRequest(tmdbId = tmdb, mediaType = part.mediaType, is4k = is4k)
                    .onSuccess {
                        count++
                        requestedIds += tmdb
                    }
            }
            _uiState.update { state ->
                val updatedSaga = if (requestedIds.isNotEmpty()) {
                    state.collectionSaga?.let { cur ->
                        cur.copy(
                            items = cur.items.map { p ->
                                if (p.tmdbId in requestedIds) {
                                    if (is4k) p.copy(uhdStatus = AvailabilityTier.PENDING)
                                    else p.copy(hdStatus = AvailabilityTier.PENDING)
                                } else p
                            }
                        )
                    }
                } else state.collectionSaga
                state.copy(
                    collectionSaga = updatedSaga,
                    feedbackBanner = if (count > 0) "Requested $count missing title${if (count == 1) "" else "s"}" else "Unable to submit requests"
                )
            }
        }
    }

    fun toggleMyList() {
        val item = _uiState.value.item ?: return
        if (activeUserId.isBlank()) return
        viewModelScope.launch {
            catalogRepository.toggleMyList(activeUserId, item)
                .onSuccess { inList ->
                    _uiState.update {
                        it.copy(
                            item = it.item?.copy(isInMyList = inList),
                            feedbackBanner = if (inList) "Added to My List" else "Removed from My List"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(feedbackBanner = "My List update failed: ${err.message}") }
                }
        }
    }

    fun toggleReminder() {
        val item = _uiState.value.item ?: return
        if (activeUserId.isBlank()) return
        viewModelScope.launch {
            val set = catalogRepository.toggleReminder(activeUserId, item)
            _uiState.update {
                it.copy(
                    item = it.item?.copy(isReminderSet = set),
                    feedbackBanner = if (set) "Reminder set for release" else "Reminder removed"
                )
            }
        }
    }

    fun removeFromContinueWatching() {
        val item = _uiState.value.item ?: return
        val jfId = item.jellyfinId ?: return
        if (activeUserId.isBlank()) return
        viewModelScope.launch {
            catalogRepository.removeFromContinueWatching(activeUserId, jfId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            item = it.item?.copy(resumePositionTicks = 0L),
                            feedbackBanner = "Removed from Continue Watching"
                        )
                    }
                }
        }
    }

    private fun startLivePollingIfNeeded(initial: UnifiedMediaItem) {
        pollingJob?.cancel()
        val needsPolling = !initial.isPlayable ||
            initial.hdStatus.isRequestedOrDownloading ||
            initial.uhdStatus.isRequestedOrDownloading ||
            initial.hdDownload != null ||
            initial.uhdDownload != null

        if (!needsPolling) return

        pollingJob = viewModelScope.launch {
            while (isActive) {
                delay(15_000L)
                refreshStatusSilently()
                val cur = _uiState.value.item ?: break
                val stillActive = cur.hdStatus.isRequestedOrDownloading ||
                    cur.uhdStatus.isRequestedOrDownloading ||
                    cur.hdDownload != null ||
                    cur.uhdDownload != null
                if (!stillActive && cur.isPlayable) break
            }
        }
    }

    private suspend fun refreshStatusSilently() {
        if (activeUserId.isBlank()) return
        catalogRepository.refreshLibraryIndex(activeUserId)
        val refreshed = catalogRepository.loadFullDetails(
            userId = activeUserId,
            tmdbId = tmdbIdArg,
            jellyfinId = jellyfinIdArg,
            mediaType = mediaTypeArg
        ).getOrNull() ?: return

        _uiState.update { it.copy(item = refreshed) }
        if (refreshed.mediaType == MediaType.SERIES) {
            val seasonNum = _uiState.value.selectedSeasonNumber
            val season = refreshed.seasons.firstOrNull { it.seasonNumber == seasonNum }
            val eps = catalogRepository.loadSeasonEpisodes(
                userId = activeUserId,
                tmdbId = refreshed.tmdbId,
                jellyfinSeriesId = refreshed.jellyfinId,
                jellyfinSeasonId = season?.jellyfinSeasonId,
                seasonNumber = seasonNum
            )
            _uiState.update { it.copy(episodes = eps) }
        }
        if (pollingJob?.isActive != true) {
            startLivePollingIfNeeded(refreshed)
        }
    }
}
