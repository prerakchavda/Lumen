package com.seerflix.tv.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.ContentRatingHelper
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.ExternalPlayerHelper
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.core.player.YouTubeTrailerResolver
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class DetailsUiState(
    val item: UnifiedMediaItem? = null,
    val recommendations: List<UnifiedMediaItem> = emptyList(),
    val collectionSaga: CollectionSummary? = null,
    val selectedSeasonNumber: Int = 1,
    val episodes: List<EpisodeSummary> = emptyList(),
    val episodesLoading: Boolean = false,
    val previewStream: ResolvedPreviewStream? = null,
    val has4kServer: Boolean = true,
    val selectedMediaSourceId: String? = null,
    val arrServers: List<ArrServerOption> = emptyList(),
    val arrServersLoading: Boolean = false,
    val showRequestModal: Boolean = false,
    val isSubmittingRequest: Boolean = false,
    val showReportIssueModal: Boolean = false,
    val isReportingIssue: Boolean = false,
    val isSeerrConfigured: Boolean = true,
    val canSubmitRequests: Boolean = true,
    val feedbackBanner: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    /** The active profile's parental limit disallows this title (search/cast/recs/deep link). */
    val isBlockedByParentalControls: Boolean = false
)

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver,
    private val playbackManager: JellyfinPlaybackManager,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {

    private val tmdbIdArg: Int? = savedStateHandle.get<String>("tmdbId")?.toIntOrNull()?.takeIf { it > 0 }
    private val jellyfinIdArg: String? = savedStateHandle.get<String>("jellyfinId")?.takeIf { it.isNotBlank() && it != "none" }
    private val mediaTypeArg: MediaType = runCatching {
        MediaType.valueOf(savedStateHandle.get<String>("mediaType") ?: "MOVIE")
    }.getOrDefault(MediaType.MOVIE)

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null
    private var seasonJob: Job? = null
    private var loadJob: Job? = null
    private var activeUserId: String = ""
    private var activeProfile: UserProfile? = null
    @Volatile private var isScreenVisible = true
    @Volatile private var hasBeenHidden = false

    init {
        loadAll()
    }

    /** Called by the screen when it becomes visible again (lifecycle STARTED). */
    fun onScreenVisible() {
        isScreenVisible = true
        val item = _uiState.value.item ?: return
        if (hasBeenHidden) {
            hasBeenHidden = false
            // Returning from the player: pick up new resume position / watched state.
            viewModelScope.launch(Dispatchers.IO) { refreshStatusSilently() }
        } else {
            startLivePollingIfNeeded(item)
        }
    }

    /**
     * Called when the screen is covered (player, another Details page) or the app is backgrounded:
     * stop the 15s Seerr/Jellyfin polling instead of letting it run under the player.
     */
    fun onScreenHidden() {
        isScreenVisible = false
        hasBeenHidden = true
        pollingJob?.cancel()
        pollingJob = null
    }

    fun loadAll() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, isBlockedByParentalControls = false) }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active profile") }
                return@launch
            }
            activeUserId = profile.jellyfinUserId
            activeProfile = profile
            val seerrConfigured = sessionPreferences.isSeerrConfigured

            val result = catalogRepository.loadFullDetails(
                userId = activeUserId,
                tmdbId = tmdbIdArg,
                jellyfinId = jellyfinIdArg,
                mediaType = mediaTypeArg
            )
            // loadFullDetails() runCatching-wraps CancellationException; don't show it as an error.
            ensureActive()

            result.onSuccess { item ->
                // Parental gate: Details can be reached from search, cast, recommendations, a saga
                // row or a deep link, none of which guarantee the rating was filtered upstream.
                if (!ContentRatingHelper.isAllowed(item, profile)) {
                    _uiState.update {
                        it.copy(
                            item = null,
                            isLoading = false,
                            errorMessage = "This title isn't available on this profile.",
                            isBlockedByParentalControls = true
                        )
                    }
                    return@onSuccess
                }
                val defaultSeason = item.seasons.firstOrNull()?.seasonNumber ?: 1
                _uiState.update {
                    it.copy(
                        item = item,
                        selectedSeasonNumber = defaultSeason,
                        selectedMediaSourceId = it.selectedMediaSourceId ?: item.mediaVersions.firstOrNull()?.id,
                        isSeerrConfigured = seerrConfigured,
                        canSubmitRequests = profile.canSubmitRequests,
                        isLoading = false
                    )
                }

                if (seerrConfigured) {
                    launch(Dispatchers.IO) {
                        val has4k = catalogRepository.has4kServerConfigured(item.mediaType)
                        _uiState.update { it.copy(has4kServer = has4k) }
                    }
                }
                launch(Dispatchers.IO) {
                    val recs = catalogRepository.loadRecommendations(
                        userId = activeUserId,
                        tmdbId = item.tmdbId,
                        jellyfinId = item.jellyfinId,
                        mediaType = item.mediaType,
                        activeProfile = profile
                    )
                    // Row keys are item ids; a duplicate (TMDB recs do repeat) would crash the LazyRow.
                    _uiState.update { it.copy(recommendations = recs.distinctBy { r -> r.id }) }
                }
                item.collectionId?.let { colId ->
                    launch(Dispatchers.IO) {
                        val saga = catalogRepository.loadCollectionSaga(activeUserId, colId, activeProfile = profile)
                        _uiState.update {
                            it.copy(collectionSaga = saga?.let { s -> s.copy(items = s.items.distinctBy { p -> p.id }) })
                        }
                    }
                }
                if (profile.autoplayPreviews) {
                    launch(Dispatchers.IO) {
                        val preview = trailerResolver.resolvePreview(
                            localTrailerId = item.localTrailerItemId,
                            youtubeKey = item.youtubeTrailerKey
                        )
                        _uiState.update { it.copy(previewStream = preview) }
                    }
                }
                if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                    selectSeason(defaultSeason)
                }

                if (isScreenVisible) startLivePollingIfNeeded(item)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = UnifiedCatalogRepository.formatHttpError(err, "Failed to load details")
                    )
                }
            }
        }
    }

    fun selectMediaSource(mediaSourceId: String) {
        _uiState.update { it.copy(selectedMediaSourceId = mediaSourceId) }
    }

    fun selectSeason(seasonNumber: Int) {
        val item = _uiState.value.item ?: return
        val season = item.seasons.firstOrNull { it.seasonNumber == seasonNumber }
        // Flipping quickly through seasons used to race: a slow earlier season could land last
        // and show Season 1's episodes under the Season 3 tab. Cancel the previous load and
        // only accept results that still match the selected season.
        seasonJob?.cancel()
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber, episodesLoading = true) }
        seasonJob = viewModelScope.launch(Dispatchers.IO) {
            val eps = catalogRepository.loadSeasonEpisodes(
                userId = activeUserId,
                tmdbId = item.tmdbId,
                jellyfinSeriesId = item.jellyfinId,
                jellyfinSeasonId = season?.jellyfinSeasonId,
                seasonNumber = seasonNumber
            )
            ensureActive()
            _uiState.update {
                if (it.selectedSeasonNumber != seasonNumber) it
                else it.copy(episodes = eps.distinctBy { e -> e.id }, episodesLoading = false)
            }
        }
    }

    fun openRequestModal() {
        if (!_uiState.value.isSeerrConfigured) {
            _uiState.update { it.copy(feedbackBanner = "Connect Seerr in Server Settings to submit requests.") }
            return
        }
        if (!_uiState.value.canSubmitRequests) {
            _uiState.update { it.copy(feedbackBanner = "Requests are locked in Kids Mode for this profile.") }
            return
        }
        val item = _uiState.value.item ?: return
        _uiState.update { it.copy(showRequestModal = true, feedbackBanner = null) }
        if (_uiState.value.arrServers.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                _uiState.update { it.copy(arrServersLoading = true) }
                val servers = catalogRepository.loadArrServers(item.mediaType)
                _uiState.update { it.copy(arrServers = servers, arrServersLoading = false) }
            }
        }
    }

    fun closeRequestModal() {
        _uiState.update { it.copy(showRequestModal = false) }
    }

    fun submitRequest(
        requestHd: Boolean,
        request4k: Boolean,
        seasons: List<Int>?,
        hdServerId: Int? = null,
        hdProfileId: Int? = null,
        hdRootFolder: String? = null,
        uhdServerId: Int? = null,
        uhdProfileId: Int? = null,
        uhdRootFolder: String? = null
    ) {
        val item = _uiState.value.item ?: return
        val tmdbId = item.tmdbId ?: return
        if (!_uiState.value.canSubmitRequests) {
            _uiState.update { it.copy(showRequestModal = false, feedbackBanner = "Requests locked in Kids Mode") }
            return
        }
        // Double OK-press on "Send Request" must not fire duplicate requests (Seerr answers 409).
        if (_uiState.value.isSubmittingRequest || (!requestHd && !request4k)) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmittingRequest = true, feedbackBanner = null) }
            var anySuccess = false
            var lastError: String? = null

            if (requestHd) {
                catalogRepository.submitRequest(
                    tmdbId = tmdbId,
                    mediaType = item.mediaType,
                    is4k = false,
                    seasons = seasons,
                    serverId = hdServerId,
                    profileId = hdProfileId,
                    rootFolder = hdRootFolder
                )
                    .onSuccess { anySuccess = true }
                    .onFailure { lastError = it.message }
            }
            if (request4k) {
                catalogRepository.submitRequest(
                    tmdbId = tmdbId,
                    mediaType = item.mediaType,
                    is4k = true,
                    seasons = seasons,
                    serverId = uhdServerId,
                    profileId = uhdProfileId,
                    rootFolder = uhdRootFolder
                )
                    .onSuccess { anySuccess = true }
                    .onFailure { lastError = it.message }
            }

            _uiState.update {
                it.copy(
                    isSubmittingRequest = false,
                    showRequestModal = false,
                    feedbackBanner = if (anySuccess) "Request sent to Seerr!" else "Request failed: ${lastError ?: "Unknown error"}"
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
                    // Reflect the cancellation locally instead of re-fetching: the repository's
                    // details cache isn't invalidated by cancelRequest(), so a refresh (or the live
                    // poll) would put the stale "Requested" item back for up to two minutes.
                    _uiState.update { state ->
                        val cur = state.item
                        val updated = when {
                            cur == null -> null
                            cur.uhdRequestId == requestId -> cur.copy(
                                uhdRequestId = null,
                                uhdStatus = AvailabilityTier.UNKNOWN,
                                uhdDownload = null
                            )
                            cur.hdRequestId == requestId -> cur.copy(
                                hdRequestId = null,
                                hdStatus = if (cur.jellyfinId != null) cur.hdStatus else AvailabilityTier.UNKNOWN,
                                hdDownload = null
                            )
                            else -> cur
                        }
                        state.copy(item = updated, feedbackBanner = "Request cancelled")
                    }
                    pollingJob?.cancel()
                    pollingJob = null
                }
                .onFailure { err ->
                    _uiState.update { it.copy(feedbackBanner = "Cancel failed: ${err.message}") }
                }
        }
    }

    fun openReportIssueModal() {
        if (!_uiState.value.isSeerrConfigured) {
            _uiState.update { it.copy(feedbackBanner = "Issue reporting requires Seerr.") }
            return
        }
        _uiState.update { it.copy(showReportIssueModal = true, feedbackBanner = null) }
    }

    fun closeReportIssueModal() {
        _uiState.update { it.copy(showReportIssueModal = false) }
    }

    fun submitIssue(issueType: SeerrIssueType, message: String) {
        val item = _uiState.value.item ?: return
        val tmdbId = item.tmdbId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isReportingIssue = true) }
            catalogRepository.reportIssue(
                tmdbId = tmdbId,
                mediaType = item.mediaType,
                issueType = issueType,
                message = message
            )
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isReportingIssue = false,
                            showReportIssueModal = false,
                            feedbackBanner = "Issue reported to Seerr"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isReportingIssue = false,
                            showReportIssueModal = false,
                            feedbackBanner = "Failed to report issue: ${err.message}"
                        )
                    }
                }
        }
    }

    fun toggleWatched() {
        val item = _uiState.value.item ?: return
        val jfId = item.jellyfinId ?: return
        if (activeUserId.isBlank()) return
        val nextWatched = !item.isWatched
        viewModelScope.launch {
            catalogRepository.markWatched(activeUserId, jfId, nextWatched)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            item = it.item?.copy(
                                isWatched = nextWatched,
                                resumePositionTicks = if (nextWatched) 0L else it.item.resumePositionTicks
                            ),
                            // Marking a series (un)watched applies to all of its episodes on the
                            // server; mirror that in the visible episode list instead of leaving
                            // stale watched/progress indicators until the next reload.
                            episodes = if (item.mediaType == MediaType.SERIES) {
                                it.episodes.map { ep ->
                                    if (ep.isAvailableInJellyfin) {
                                        ep.copy(
                                            isWatched = nextWatched,
                                            resumePositionTicks = if (nextWatched) 0L else ep.resumePositionTicks
                                        )
                                    } else ep
                                }
                            } else {
                                it.episodes
                            },
                            feedbackBanner = if (nextWatched) "Marked as watched" else "Marked as unwatched"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(feedbackBanner = "Update failed: ${err.message}") }
                }
        }
    }

    fun openExternalPlayer(context: Context) {
        val item = _uiState.value.item ?: return
        // For a series, hand the series id to the playback manager: it resolves the in-progress /
        // next-up episode. Picking the first episode of the currently selected season restarted
        // S1E1 for someone halfway through season 3.
        val playableId = if (item.mediaType == MediaType.SERIES) {
            item.jellyfinId ?: _uiState.value.episodes.firstOrNull { it.isAvailableInJellyfin }?.id
        } else {
            item.jellyfinId
        } ?: return
        if (activeUserId.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(feedbackBanner = "Opening external player...") }
            playbackManager.preparePlayback(
                userId = activeUserId,
                itemId = playableId,
                mediaSourceId = _uiState.value.selectedMediaSourceId
            )
                .onSuccess { session ->
                    val launched = ExternalPlayerHelper.launchExternalPlayer(
                        context = context,
                        streamUrl = session.directPlayUrl ?: session.streamUrl,
                        title = session.title,
                        positionMs = session.resumeTicks / 10_000L,
                        externalSubtitles = session.externalSubtitleFiles
                    )
                    _uiState.update {
                        it.copy(
                            feedbackBanner = if (launched) null else "No compatible external video player installed"
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(feedbackBanner = "External player failed: ${err.message}") }
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

    private fun hasActiveRequestOrDownload(item: UnifiedMediaItem): Boolean =
        item.hdStatus.isRequestedOrDownloading ||
            item.uhdStatus.isRequestedOrDownloading ||
            item.hdDownload != null ||
            item.uhdDownload != null

    private fun startLivePollingIfNeeded(initial: UnifiedMediaItem) {
        pollingJob?.cancel()
        // Only poll while something is actually requested/downloading. Previously any title not in
        // the library (`!isPlayable`) polled forever — rebuilding the Jellyfin library index and
        // re-fetching details every 15s just for viewing an unrequested movie.
        if (!isScreenVisible || !hasActiveRequestOrDownload(initial)) return

        pollingJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(15_000L)
                refreshStatusSilently()
                val cur = _uiState.value.item ?: break
                if (!hasActiveRequestOrDownload(cur)) break
            }
        }
    }

    private suspend fun refreshStatusSilently() {
        if (activeUserId.isBlank()) return
        // Pass the profile: without it the shared library index was rebuilt with no rating cap,
        // leaking unrestricted titles into a Kids profile's Home/Search lookups.
        catalogRepository.refreshLibraryIndex(activeUserId, activeProfile = activeProfile)
        val refreshed = catalogRepository.loadFullDetails(
            userId = activeUserId,
            tmdbId = tmdbIdArg,
            jellyfinId = jellyfinIdArg,
            mediaType = mediaTypeArg
        ).getOrNull() ?: return
        if (!ContentRatingHelper.isAllowed(refreshed, activeProfile)) {
            pollingJob?.cancel()
            _uiState.update {
                it.copy(
                    item = null,
                    errorMessage = "This title isn't available on this profile.",
                    isBlockedByParentalControls = true
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                item = refreshed,
                // Keep the user's version choice if it still exists after the refresh.
                selectedMediaSourceId = it.selectedMediaSourceId
                    ?.takeIf { id -> refreshed.mediaVersions.isEmpty() || refreshed.mediaVersions.any { v -> v.id == id } }
                    ?: refreshed.mediaVersions.firstOrNull()?.id
            )
        }
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
            // The user may have switched seasons while this was loading.
            _uiState.update {
                if (it.selectedSeasonNumber != seasonNum || it.episodesLoading) it
                else it.copy(episodes = eps.distinctBy { e -> e.id })
            }
        }
        if (pollingJob?.isActive != true) {
            startLivePollingIfNeeded(refreshed)
        }
    }
}
