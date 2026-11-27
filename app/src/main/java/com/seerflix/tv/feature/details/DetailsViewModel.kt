package com.seerflix.tv.feature.details

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.DetailsSeedCache
import com.seerflix.tv.core.data.PlaylistServerMismatchException
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.ContentRatingHelper
import com.seerflix.tv.core.model.ContentRatingLimit
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.ExternalPlayerHelper
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    /**
     * Which season is selected ([seasonSelectionKey]). Season numbers aren't unique: Jellyfin-only
     * seasons without an index number (e.g. "Specials") all map to season 1.
     */
    val selectedSeasonKey: String? = null,
    val episodes: List<EpisodeSummary> = emptyList(),
    val episodesLoading: Boolean = false,
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
    /** Kids / rating-limited profiles never get destructive actions, whatever the Jellyfin account may do. */
    val isRestrictedProfile: Boolean = false,
    val feedbackBanner: String? = null,
    val isLoading: Boolean = true,
    /**
     * [item] is still the clicked card's data (seed from [DetailsSeedCache]) while the full load
     * runs. The screen renders it immediately but keeps state-dependent actions (request, My List,
     * watched, reminder, ...) as placeholders until the real availability data arrives.
     */
    val isRefining: Boolean = false,
    val errorMessage: String? = null,
    /** The active profile's parental limit disallows this title (search/cast/recs/deep link). */
    val isBlockedByParentalControls: Boolean = false,
    val playlists: List<com.seerflix.tv.core.model.PlaylistSummary> = emptyList(),
    val showPlaylistDialog: Boolean = false,
    val showMediaInfoDialog: Boolean = false,
    val showTrackPickerDialog: Boolean = false,
    val showDeleteConfirmDialog: Boolean = false,
    val episodeMenuTarget: EpisodeSummary? = null,
    val itemDeleted: Boolean = false,
    val themeSongUrl: String? = null
)

/** Identifies a season uniquely (season numbers can repeat, see [DetailsUiState.selectedSeasonKey]). */
fun seasonSelectionKey(season: SeasonSummary): String =
    season.jellyfinSeasonId ?: "n${season.seasonNumber}"

/** The selected season among [seasons]: by key, falling back to the season number. */
fun DetailsUiState.selectedSeasonIn(seasons: List<SeasonSummary>): SeasonSummary? =
    selectedSeasonKey?.let { k -> seasons.firstOrNull { seasonSelectionKey(it) == k } }
        ?: seasons.firstOrNull { it.seasonNumber == selectedSeasonNumber }

@HiltViewModel
class DetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val playbackManager: JellyfinPlaybackManager,
    private val sessionPreferences: SessionPreferences,
    @com.seerflix.tv.core.network.MediaClient val mediaHttpClient: okhttp3.OkHttpClient
) : ViewModel() {

    private val tmdbIdArg: Int? = savedStateHandle.get<String>("tmdbId")?.toIntOrNull()?.takeIf { it > 0 }
    private val jellyfinIdArg: String? = savedStateHandle.get<String>("jellyfinId")?.takeIf { it.isNotBlank() && it != "none" }
    private val mediaTypeArg: MediaType = runCatching {
        MediaType.valueOf(savedStateHandle.get<String>("mediaType") ?: "MOVIE")
    }.getOrDefault(MediaType.MOVIE)

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState.asStateFlow()

    /** UI strings for the active profile's language, read when a message is produced. */
    private val strings: UiStrings
        get() = stringsForLanguage(sessionPreferences.cachedUiLanguage)

    private var pollingJob: Job? = null
    /** The status refresh started on return from the player; cancelled when hidden again. */
    private var visibleRefreshJob: Job? = null
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
        // While the seed is showing, loadAll() is still running and starts polling itself.
        if (_uiState.value.isRefining) return
        val item = _uiState.value.item ?: return
        if (hasBeenHidden) {
            hasBeenHidden = false
            // Returning from the player: pick up new resume position / watched state.
            // Tracked: hidden/visible twice in a row must not run two refreshes that each start
            // a polling loop (one would be orphaned and poll under the player).
            visibleRefreshJob?.cancel()
            visibleRefreshJob = viewModelScope.launch(Dispatchers.IO) { refreshStatusSilently() }
        } else {
            startLivePollingIfNeeded(item)
        }
    }

    /**
     * Called when the screen is covered (player, another Details page) or the app is backgrounded:
     * stop the 15s Seerr/Jellyfin polling instead of letting it run under the player.
     */
    @Synchronized
    fun onScreenHidden() {
        isScreenVisible = false
        hasBeenHidden = true
        visibleRefreshJob?.cancel()
        pollingJob?.cancel()
        pollingJob = null
    }

    fun loadAll() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                // Keep whatever is already on screen (e.g. a seed) instead of blanking it.
                it.copy(isLoading = it.item == null, errorMessage = null, isBlockedByParentalControls = false)
            }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null) {
                _uiState.update { it.copy(item = null, isLoading = false, isRefining = false, errorMessage = strings.commonNoActiveProfile) }
                return@launch
            }
            activeUserId = profile.jellyfinUserId
            activeProfile = profile
            val seerrConfigured = sessionPreferences.isSeerrConfigured
            val resolvedType = if (mediaTypeArg == MediaType.EPISODE) MediaType.SERIES else mediaTypeArg

            // Instant render: paint the clicked card's data now; the full load merges in below.
            if (_uiState.value.item == null) {
                DetailsSeedCache.get(tmdbIdArg, jellyfinIdArg, mediaTypeArg)
                    ?.let(::seedForDisplay)
                    ?.takeIf { isSeedAllowed(it, profile) }
                    ?.let { seed ->
                        _uiState.update {
                            it.copy(
                                item = seed,
                                isLoading = false,
                                isRefining = true,
                                isSeerrConfigured = seerrConfigured,
                                canSubmitRequests = profile.canSubmitRequests,
                                isRestrictedProfile = profile.isRestricted
                            )
                        }
                    }
            }

            // Work that only needs the route args runs in parallel with loadFullDetails() instead
            // of waiting for it. (Exceptions are contained so they can't cancel the main load.)
            if (seerrConfigured) {
                launch {
                    val has4k = runCatching { catalogRepository.has4kServerConfigured(resolvedType) }.getOrNull()
                    if (has4k != null) _uiState.update { it.copy(has4kServer = has4k) }
                }
            }
            val earlyRecs: Deferred<List<UnifiedMediaItem>>? =
                if (seerrConfigured && tmdbIdArg != null && mediaTypeArg != MediaType.EPISODE) {
                    async {
                        runCatching {
                            catalogRepository.loadRecommendations(
                                userId = activeUserId,
                                tmdbId = tmdbIdArg,
                                jellyfinId = jellyfinIdArg,
                                mediaType = mediaTypeArg,
                                activeProfile = profile
                            )
                        }.getOrDefault(emptyList())
                    }
                } else null

            val result = catalogRepository.loadFullDetails(
                userId = activeUserId,
                tmdbId = tmdbIdArg,
                jellyfinId = jellyfinIdArg,
                mediaType = mediaTypeArg
            )
            // loadFullDetails() runCatching-wraps CancellationException; don't show it as an error.
            ensureActive()

            result.onSuccess { loaded ->
                // Parental gate: Details can be reached from search, cast, recommendations, a saga
                // row or a deep link, none of which guarantee the rating was filtered upstream.
                if (!ContentRatingHelper.isAllowed(loaded, profile)) {
                    earlyRecs?.cancel()
                    _uiState.update {
                        it.copy(
                            item = null,
                            isLoading = false,
                            isRefining = false,
                            errorMessage = strings.detailsBlockedForProfile,
                            isBlockedByParentalControls = true
                        )
                    }
                    return@onSuccess
                }
                val item = loaded.sharingWith(_uiState.value.item)
                val defaultSeason = item.seasons.firstOrNull()?.seasonNumber ?: 1
                _uiState.update {
                    it.copy(
                        item = item,
                        selectedSeasonNumber = defaultSeason,
                        selectedSeasonKey = item.seasons.firstOrNull()?.let(::seasonSelectionKey),
                        selectedMediaSourceId = it.selectedMediaSourceId ?: item.mediaVersions.firstOrNull()?.id,
                        isSeerrConfigured = seerrConfigured,
                        canSubmitRequests = profile.canSubmitRequests,
                        isRestrictedProfile = profile.isRestricted,
                        isLoading = false,
                        isRefining = false
                    )
                }

                launch(Dispatchers.IO) {
                    val early = earlyRecs?.await().orEmpty()
                    val recs = early.ifEmpty {
                        // Seerr had nothing (or the route lacked a TMDB id): fall back to the
                        // resolved ids, which can include a Jellyfin id for "similar items".
                        if (earlyRecs != null && item.jellyfinId == null) emptyList()
                        else catalogRepository.loadRecommendations(
                            userId = activeUserId,
                            tmdbId = item.tmdbId,
                            jellyfinId = item.jellyfinId,
                            mediaType = item.mediaType,
                            activeProfile = profile
                        )
                    }
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
                if (profile.themeSongsEnabled && !item.jellyfinId.isNullOrBlank()) {
                    launch(Dispatchers.IO) {
                        val themeUrl = catalogRepository.loadThemeSongUrl(activeUserId, item.jellyfinId)
                        if (!themeUrl.isNullOrBlank()) {
                            _uiState.update { it.copy(themeSongUrl = themeUrl) }
                        }
                    }
                }
                if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                    selectSeason(defaultSeason)
                }

                if (isScreenVisible) startLivePollingIfNeeded(item)
            }.onFailure { err ->
                earlyRecs?.cancel()
                _uiState.update {
                    it.copy(
                        // A seed alone can't back the state-dependent actions; show the Retry screen.
                        item = null,
                        isLoading = false,
                        isRefining = false,
                        errorMessage = UnifiedCatalogRepository.formatHttpError(err, strings.detailsLoadFailed)
                    )
                }
            }
        }
    }

    /**
     * Shapes a clicked card for display while the full load runs. A Continue Watching episode card
     * opens its parent series, so present it as the series (series title; the episode's own
     * overview / TMDB id / runtime would be wrong for the series page).
     */
    private fun seedForDisplay(seed: UnifiedMediaItem): UnifiedMediaItem =
        if (seed.mediaType == MediaType.EPISODE) {
            seed.copy(
                mediaType = MediaType.SERIES,
                title = seed.seriesName?.takeIf { it.isNotBlank() } ?: seed.title.substringBefore(" • "),
                tmdbId = null,
                overview = "",
                tagline = null,
                episodeLabel = null,
                runtimeMinutes = null,
                runtimeTicks = 0L,
                relatedVideos = emptyList(),
                youtubeTrailerKey = null,
                localTrailerItemId = null
            )
        } else {
            seed
        }

    /**
     * The seed must never flash a title the profile can't see. With a rating limit in force, only
     * trust a seed whose rating (and, for Kids Mode, genres) is actually known; an unrated card
     * would otherwise count as PG. Unknown seeds wait for the authoritative full load.
     */
    private fun isSeedAllowed(seed: UnifiedMediaItem, profile: UserProfile): Boolean {
        if (profile.effectiveRatingLimit == ContentRatingLimit.ALL) return true
        if (seed.isAdult || seed.maturityRating.isNullOrBlank()) return false
        if (profile.kidsMode && seed.genres.isEmpty()) return false
        return ContentRatingHelper.isAllowed(seed, profile)
    }

    /**
     * Keeps the artwork that is already on screen (seed -> full, poll -> poll) so the backdrop
     * doesn't visibly swap to a different-but-equivalent URL, and reuses unchanged list instances
     * so the sections that take them can skip recomposition on a poll tick.
     */
    private fun UnifiedMediaItem.sharingWith(prev: UnifiedMediaItem?): UnifiedMediaItem {
        if (prev == null) return this
        val merged = copy(
            backdropUrl = prev.backdropUrl ?: backdropUrl,
            genres = genres.reuse(prev.genres),
            mediaBadges = mediaBadges.reuse(prev.mediaBadges),
            relatedVideos = relatedVideos.reuse(prev.relatedVideos),
            seasons = seasons.reuse(prev.seasons),
            cast = cast.reuse(prev.cast),
            mediaVersions = mediaVersions.reuse(prev.mediaVersions),
            extras = extras.reuse(prev.extras)
        )
        return if (merged == prev) prev else merged
    }

    private fun <T> List<T>.reuse(old: List<T>): List<T> = if (this == old) old else this

    fun selectMediaSource(mediaSourceId: String) {
        _uiState.update { it.copy(selectedMediaSourceId = mediaSourceId) }
    }

    fun selectSeason(season: SeasonSummary) = selectSeason(season.seasonNumber, seasonSelectionKey(season))

    fun selectSeason(seasonNumber: Int, seasonKey: String? = null) {
        val item = _uiState.value.item ?: return
        val season = seasonKey?.let { k -> item.seasons.firstOrNull { seasonSelectionKey(it) == k } }
            ?: item.seasons.firstOrNull { it.seasonNumber == seasonNumber }
        val key = season?.let(::seasonSelectionKey)
        // Flipping quickly through seasons used to race: a slow earlier season could land last
        // and show Season 1's episodes under the Season 3 tab. Cancel the previous load and
        // only accept results that still match the selected season.
        seasonJob?.cancel()
        _uiState.update { it.copy(selectedSeasonNumber = seasonNumber, selectedSeasonKey = key, episodesLoading = true) }
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
                if (it.selectedSeasonNumber != seasonNumber || it.selectedSeasonKey != key) it
                else it.copy(episodes = eps.distinctBy { e -> e.id }, episodesLoading = false)
            }
        }
    }

    fun openRequestModal() {
        // Request eligibility depends on real availability data, which a seed doesn't have.
        if (_uiState.value.isRefining) return
        if (!_uiState.value.isSeerrConfigured) {
            _uiState.update { it.copy(feedbackBanner = strings.requestNeedsSeerr) }
            return
        }
        if (!_uiState.value.canSubmitRequests) {
            _uiState.update { it.copy(feedbackBanner = strings.requestsLockedKids) }
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
            _uiState.update { it.copy(showRequestModal = false, feedbackBanner = strings.requestsLockedKids) }
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

            val s = strings
            _uiState.update {
                it.copy(
                    isSubmittingRequest = false,
                    showRequestModal = false,
                    feedbackBanner = if (anySuccess) s.detailsRequestSent else s.requestFailed.format(lastError ?: s.commonUnknownError)
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
                        state.copy(item = updated, feedbackBanner = strings.detailsRequestCancelled)
                    }
                    pollingJob?.cancel()
                    pollingJob = null
                }
                .onFailure { err ->
                    val s = strings
                    _uiState.update { it.copy(feedbackBanner = s.detailsCancelFailed.format(err.message ?: s.commonUnknownError)) }
                }
        }
    }

    fun openReportIssueModal() {
        if (_uiState.value.isRefining) return
        if (!_uiState.value.isSeerrConfigured) {
            _uiState.update { it.copy(feedbackBanner = strings.detailsIssueNeedsSeerr) }
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
                            feedbackBanner = strings.detailsIssueReported
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isReportingIssue = false,
                            showReportIssueModal = false,
                            feedbackBanner = strings.detailsIssueFailed.format(err.message ?: strings.commonUnknownError)
                        )
                    }
                }
        }
    }

    fun toggleWatched() {
        if (_uiState.value.isRefining) return
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
                            feedbackBanner = if (nextWatched) strings.detailsMarkedWatched else strings.detailsMarkedUnwatched
                        )
                    }
                }
                .onFailure { err ->
                    val s = strings
                    _uiState.update { it.copy(feedbackBanner = s.watchedUpdateFailed.format(err.message ?: s.commonUnknownError)) }
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
            _uiState.update { it.copy(feedbackBanner = strings.detailsOpeningExternalPlayer) }
            val sourceId = _uiState.value.selectedMediaSourceId
            // A version that lives on another server is played as that server's copy.
            val versionItemId = sourceId?.let { id -> item.mediaVersions.firstOrNull { it.id == id }?.itemId }
            playbackManager.preparePlayback(
                userId = activeUserId,
                itemId = versionItemId ?: playableId,
                mediaSourceId = sourceId
            )
                .onSuccess { session ->
                    val launched = ExternalPlayerHelper.launchExternalPlayer(
                        context = context,
                        streamUrl = session.directPlayUrl ?: session.streamUrl,
                        title = session.title,
                        positionMs = session.resumeTicks / 10_000L,
                        externalSubtitles = session.externalSubtitleFiles,
                        chooserTitle = strings.playerOpenWithExternal
                    )
                    _uiState.update {
                        it.copy(
                            feedbackBanner = if (launched) null else strings.detailsNoExternalPlayer
                        )
                    }
                }
                .onFailure { err ->
                    val s = strings
                    _uiState.update { it.copy(feedbackBanner = s.detailsExternalPlayerFailed.format(err.message ?: s.commonUnknownError)) }
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
                            feedbackBanner = strings.requestedTitleFeedback.format(part.title)
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(feedbackBanner = strings.requestFailed.format(err.message ?: strings.commonUnknownError))
                    }
                }
        }
    }

    private var requestAllJob: kotlinx.coroutines.Job? = null

    fun requestAllMissingInCollection(is4k: Boolean = false) {
        val saga = _uiState.value.collectionSaga ?: return
        val missing = saga.items.filter { !it.isPlayable && it.canRequestHd && it.tmdbId != null }
        if (missing.isEmpty()) return
        // A second OK while the batch runs would request every title again.
        if (requestAllJob?.isActive == true) return
        requestAllJob = viewModelScope.launch {
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
                    feedbackBanner = when {
                        count == 1 -> strings.requestedMissingOne
                        count > 1 -> strings.requestedMissingMany.format(count)
                        else -> strings.requestsSubmitFailed
                    }
                )
            }
        }
    }

    fun toggleMyList() {
        if (_uiState.value.isRefining) return
        val item = _uiState.value.item ?: return
        if (activeUserId.isBlank()) return
        viewModelScope.launch {
            catalogRepository.toggleMyList(activeUserId, item)
                .onSuccess { inList ->
                    _uiState.update {
                        it.copy(
                            item = it.item?.copy(isInMyList = inList),
                            feedbackBanner = if (inList) strings.detailsAddedToMyList else strings.detailsRemovedFromMyList
                        )
                    }
                }
                .onFailure { err ->
                    val s = strings
                    _uiState.update { it.copy(feedbackBanner = s.myListUpdateFailed.format(err.message ?: s.commonUnknownError)) }
                }
        }
    }

    fun toggleReminder() {
        if (_uiState.value.isRefining) return
        val item = _uiState.value.item ?: return
        if (activeUserId.isBlank()) return
        viewModelScope.launch {
            val set = catalogRepository.toggleReminder(activeUserId, item)
            _uiState.update {
                it.copy(
                    item = it.item?.copy(isReminderSet = set),
                    feedbackBanner = if (set) strings.detailsReminderAdded else strings.detailsReminderRemoved
                )
            }
        }
    }

    fun removeFromContinueWatching() {
        if (_uiState.value.isRefining) return
        val item = _uiState.value.item ?: return
        val jfId = item.jellyfinId ?: return
        if (activeUserId.isBlank()) return
        viewModelScope.launch {
            catalogRepository.removeFromContinueWatching(activeUserId, jfId)
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            item = it.item?.copy(resumePositionTicks = 0L),
                            feedbackBanner = strings.detailsRemovedFromContinue
                        )
                    }
                }
        }
    }

    // ---- Phase 2: Playlists, Media Info, Pre-Playback Tracks, Delete & Season/Episode Watched ----

    fun openPlaylistDialog() {
        _uiState.update { it.copy(showPlaylistDialog = true) }
        if (activeUserId.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val playlists = catalogRepository.loadPlaylists(activeUserId)
            _uiState.update { it.copy(playlists = playlists) }
        }
    }

    fun dismissPlaylistDialog() {
        _uiState.update { it.copy(showPlaylistDialog = false) }
    }

    fun addToExistingPlaylist(playlistId: String, playlistName: String) {
        val jfId = _uiState.value.item?.jellyfinId ?: return
        if (activeUserId.isBlank()) return
        _uiState.update { it.copy(showPlaylistDialog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.addToPlaylist(activeUserId, playlistId, listOf(jfId))
                .onSuccess {
                    _uiState.update { it.copy(feedbackBanner = strings.playlistAddedFeedback.format(playlistName)) }
                }
                .onFailure { err ->
                    val s = strings
                    val msg = if (err is PlaylistServerMismatchException) s.playlistWrongServer else err.message ?: s.playlistAddFailed
                    _uiState.update { it.copy(feedbackBanner = msg) }
                }
        }
    }

    fun createAndAddToPlaylist(name: String) {
        val clean = name.trim()
        if (clean.isBlank() || activeUserId.isBlank()) return
        val jfId = _uiState.value.item?.jellyfinId
        _uiState.update { it.copy(showPlaylistDialog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.createPlaylist(activeUserId, clean, listOfNotNull(jfId))
                .onSuccess {
                    _uiState.update { it.copy(feedbackBanner = strings.playlistCreatedFeedback.format(clean)) }
                }
                .onFailure { err ->
                    val s = strings
                    val msg = if (err is PlaylistServerMismatchException) s.playlistWrongServer else err.message ?: s.playlistCreateFailed
                    _uiState.update { it.copy(feedbackBanner = msg) }
                }
        }
    }

    fun openMediaInfoDialog() {
        _uiState.update { it.copy(showMediaInfoDialog = true) }
    }

    fun dismissMediaInfoDialog() {
        _uiState.update { it.copy(showMediaInfoDialog = false) }
    }

    fun openTrackPickerDialog() {
        _uiState.update { it.copy(showTrackPickerDialog = true) }
    }

    fun dismissTrackPickerDialog() {
        _uiState.update { it.copy(showTrackPickerDialog = false) }
    }

    fun openDeleteConfirmDialog() {
        if (_uiState.value.item?.canDelete != true || activeProfile?.isRestricted == true) return
        _uiState.update { it.copy(showDeleteConfirmDialog = true) }
    }

    fun dismissDeleteConfirmDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
    }

    fun confirmDeleteItem() {
        val item = _uiState.value.item ?: return
        val jfId = item.jellyfinId ?: return
        if (!item.canDelete || activeUserId.isBlank() || activeProfile?.isRestricted == true) return
        _uiState.update { it.copy(showDeleteConfirmDialog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.deleteItem(activeUserId, jfId)
                .onSuccess {
                    _uiState.update { it.copy(itemDeleted = true, feedbackBanner = strings.deleteSuccessFeedback) }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(feedbackBanner = err.message ?: strings.deleteFailed) }
                }
        }
    }

    fun openEpisodeMenu(episode: EpisodeSummary) {
        if (!episode.isAvailableInJellyfin) return
        _uiState.update { it.copy(episodeMenuTarget = episode) }
    }

    fun dismissEpisodeMenu() {
        _uiState.update { it.copy(episodeMenuTarget = null) }
    }

    fun toggleEpisodeWatched(episode: EpisodeSummary) {
        if (!episode.isAvailableInJellyfin || activeUserId.isBlank()) return
        val nextWatched = !episode.isWatched
        _uiState.update { it.copy(episodeMenuTarget = null) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.markWatched(activeUserId, episode.id, nextWatched)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(
                            episodes = state.episodes.map { ep ->
                                if (ep.id == episode.id) {
                                    ep.copy(
                                        isWatched = nextWatched,
                                        resumePositionTicks = if (nextWatched) 0L else ep.resumePositionTicks
                                    )
                                } else ep
                            }
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(feedbackBanner = strings.watchedUpdateFailed.format(err.message.orEmpty())) }
                }
        }
    }

    fun toggleCurrentSeasonWatched() {
        val state = _uiState.value
        val item = state.item ?: return
        if (activeUserId.isBlank()) return
        val playableEps = state.episodes.filter { it.isAvailableInJellyfin }
        if (playableEps.isEmpty()) return
        val nextWatched = !playableEps.all { it.isWatched }
        val seasonNumber = state.selectedSeasonNumber
        val seasonKey = state.selectedSeasonKey
        val season = state.selectedSeasonIn(item.seasons)
        val episodeIds = playableEps.map { it.id }
        val episodeIdSet = episodeIds.toSet()
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.markSeasonWatched(
                userId = activeUserId,
                jellyfinSeasonId = season?.jellyfinSeasonId,
                episodeIds = episodeIds,
                watched = nextWatched
            ).onSuccess {
                _uiState.update { cur ->
                    // The user may have switched seasons meanwhile: only touch the episodes marked.
                    if (cur.selectedSeasonNumber != seasonNumber || cur.selectedSeasonKey != seasonKey) return@update cur
                    cur.copy(
                        episodes = cur.episodes.map { ep ->
                            if (ep.id in episodeIdSet) {
                                ep.copy(
                                    isWatched = nextWatched,
                                    resumePositionTicks = if (nextWatched) 0L else ep.resumePositionTicks
                                )
                            } else ep
                        }
                    )
                }
                // Resume / Mark Watched on the header follow the series' new state.
                runCatching { refreshStatusSilently() }
            }.onFailure { err ->
                _uiState.update { it.copy(feedbackBanner = strings.watchedUpdateFailed.format(err.message.orEmpty())) }
            }
        }
    }

    private fun hasActiveRequestOrDownload(item: UnifiedMediaItem): Boolean =
        item.hdStatus.isRequestedOrDownloading ||
            item.uhdStatus.isRequestedOrDownloading ||
            item.hdDownload != null ||
            item.uhdDownload != null

    // Called from Main and from IO (after a refresh): one polling loop at a time.
    @Synchronized
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
            mediaType = mediaTypeArg,
            // Live download/request status: bypass the 120 s details cache.
            forceRefresh = true
        ).getOrNull() ?: return
        if (!ContentRatingHelper.isAllowed(refreshed, activeProfile)) {
            pollingJob?.cancel()
            _uiState.update {
                it.copy(
                    item = null,
                    errorMessage = strings.detailsBlockedForProfile,
                    isBlockedByParentalControls = true
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                item = refreshed.sharingWith(it.item),
                // Keep the user's version choice if it still exists after the refresh.
                selectedMediaSourceId = it.selectedMediaSourceId
                    ?.takeIf { id -> refreshed.mediaVersions.isEmpty() || refreshed.mediaVersions.any { v -> v.id == id } }
                    ?: refreshed.mediaVersions.firstOrNull()?.id
            )
        }
        if (refreshed.mediaType == MediaType.SERIES) {
            val seasonNum = _uiState.value.selectedSeasonNumber
            val seasonKey = _uiState.value.selectedSeasonKey
            val season = _uiState.value.selectedSeasonIn(refreshed.seasons)
            val eps = catalogRepository.loadSeasonEpisodes(
                userId = activeUserId,
                tmdbId = refreshed.tmdbId,
                jellyfinSeriesId = refreshed.jellyfinId,
                jellyfinSeasonId = season?.jellyfinSeasonId,
                seasonNumber = seasonNum
            )
            // The user may have switched seasons while this was loading.
            _uiState.update {
                if (it.selectedSeasonNumber != seasonNum || it.selectedSeasonKey != seasonKey || it.episodesLoading) it
                else {
                    val fresh = eps.distinctBy { e -> e.id }
                    // Unchanged list -> keep the old instance so the episode rows skip recomposition.
                    if (fresh == it.episodes) it else it.copy(episodes = fresh)
                }
            }
        }
        if (pollingJob?.isActive != true) {
            startLivePollingIfNeeded(refreshed)
        }
    }
}
