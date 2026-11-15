package com.seerflix.tv.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AppUpdateChecker
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.HomeContentCache
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.AppUpdateInfo
import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.HomeTabId
import com.seerflix.tv.core.model.JellyfinLibraryView
import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ReadyRequestAlert
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.ServerConfig
import com.seerflix.tv.core.model.SyncPlayGroup
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.MediaClient
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.core.player.SyncPlayManager
import com.seerflix.tv.core.player.YouTubeTrailerResolver
import com.seerflix.tv.core.tv.TvChannelSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject

enum class HomeTab(val label: String, val filter: MediaType?) {
    HOME("Home", null),
    SHOWS("TV Shows", MediaType.SERIES),
    MOVIES("Movies", MediaType.MOVIE),
    COLLECTIONS("Collections", null),
    LIVE_TV("Live TV", null),
    NEW_AND_POPULAR("New & Popular", null),
    MY_LIST("My List", null)
}

fun HomeTabId.toHomeTab(): HomeTab = when (this) {
    HomeTabId.HOME -> HomeTab.HOME
    HomeTabId.SHOWS -> HomeTab.SHOWS
    HomeTabId.MOVIES -> HomeTab.MOVIES
    HomeTabId.COLLECTIONS -> HomeTab.COLLECTIONS
    HomeTabId.LIVE_TV -> HomeTab.LIVE_TV
    HomeTabId.NEW_AND_POPULAR -> HomeTab.NEW_AND_POPULAR
    HomeTabId.MY_LIST -> HomeTab.MY_LIST
}

fun HomeTab.toHomeTabId(): HomeTabId = when (this) {
    HomeTab.HOME -> HomeTabId.HOME
    HomeTab.SHOWS -> HomeTabId.SHOWS
    HomeTab.MOVIES -> HomeTabId.MOVIES
    HomeTab.COLLECTIONS -> HomeTabId.COLLECTIONS
    HomeTab.LIVE_TV -> HomeTabId.LIVE_TV
    HomeTab.NEW_AND_POPULAR -> HomeTabId.NEW_AND_POPULAR
    HomeTab.MY_LIST -> HomeTabId.MY_LIST
}

@Immutable
data class HomeUiState(
    val activeTab: HomeTab = HomeTab.HOME,
    val selectedGenre: GenreFilterOption = GenreFilterOption.ALL_GENRES.first(),
    val availabilityFilter: AvailabilityFilter = AvailabilityFilter.ALL,
    val profile: UserProfile? = null,
    val isSeerrConfigured: Boolean = true,
    val rows: List<CatalogRow> = emptyList(),
    val collections: List<CollectionSummary> = emptyList(),
    val liveTvChannels: List<LiveTvChannel> = emptyList(),
    val liveTvRecordings: List<UnifiedMediaItem> = emptyList(),
    val libraryViews: List<JellyfinLibraryView> = emptyList(),
    val readyAlerts: List<ReadyRequestAlert> = emptyList(),
    val syncPlayGroups: List<SyncPlayGroup> = emptyList(),
    val activeSyncPlayGroup: SyncPlayGroup? = null,
    val updateInfo: AppUpdateInfo? = null,
    val activeCollectionDetails: CollectionSummary? = null,
    val collectionDetailsLoading: Boolean = false,
    val featuredItem: UnifiedMediaItem? = null,
    val previewStream: ResolvedPreviewStream? = null,
    val isPreviewMuted: Boolean = true,
    val activeDownloadCount: Int = 0,
    val has4kMovieServer: Boolean = true,
    val has4kTvServer: Boolean = true,
    val continueWatchingMenuTarget: UnifiedMediaItem? = null,
    val posterMenuTarget: UnifiedMediaItem? = null,
    val showAppearanceDialog: Boolean = false,
    val showCustomizeHomeDialog: Boolean = false,
    val showSyncPlayDialog: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val actionFeedbackMessage: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver,
    private val tvChannelSyncManager: TvChannelSyncManager,
    private val syncPlayManager: SyncPlayManager,
    private val appUpdateChecker: AppUpdateChecker,
    private val prefs: SessionPreferences,
    /** Honours the self-signed-certificate toggle; used by the hero trailer preview player. */
    @MediaClient val previewHttpClient: OkHttpClient,
    /** Last good rows per (profile, tab, genre, filter): painted instantly, then revalidated. */
    private val homeContentCache: HomeContentCache
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var focusPreviewJob: Job? = null
    private var feedbackJob: Job? = null
    private var catalogJob: Job? = null
    private var collectionJob: Job? = null
    private var auxiliaryJob: Job? = null

    /**
     * Cache key of the row content currently in [HomeUiState.rows] plus the rows as they were
     * loaded. In-place edits (My List, watched, Continue Watching) produce a different list, which
     * is written back when the user leaves that view so its cached copy doesn't resurrect them.
     */
    @Volatile
    private var displayedContent: Pair<HomeContentCache.Key, List<CatalogRow>>? = null

    /** Monotonic token so a superseded catalog load can never overwrite a newer one. */
    @Volatile
    private var catalogGeneration = 0

    /** Tab whose content is currently held in [HomeUiState.rows]/collections/live TV. */
    @Volatile
    private var lastLoadedTab: HomeTab? = null

    init {
        viewModelScope.launch {
            authRepository.activeProfileFlow.collect { active ->
                if (active != null) {
                    _uiState.update { it.copy(profile = active) }
                }
            }
        }
        viewModelScope.launch {
            prefs.serverConfigFlow.collect { cfg ->
                _uiState.update { it.copy(isSeerrConfigured = cfg.isSeerrConfigured) }
            }
        }
        viewModelScope.launch {
            prefs.readyAlertsFlow.collect { alerts ->
                _uiState.update { it.copy(readyAlerts = alerts) }
            }
        }
        viewModelScope.launch {
            syncPlayManager.availableGroups.collect { groups ->
                _uiState.update { it.copy(syncPlayGroups = groups) }
            }
        }
        viewModelScope.launch {
            syncPlayManager.activeGroup.collect { active ->
                _uiState.update { it.copy(activeSyncPlayGroup = active) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val initialProfile = authRepository.activeProfileFlow.first()
            val startTab = initialProfile?.defaultStartupTab?.toHomeTab() ?: HomeTab.HOME
            loadCatalog(startTab, GenreFilterOption.ALL_GENRES.first(), AvailabilityFilter.ALL)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val upd = runCatching { appUpdateChecker.checkForUpdate() }.getOrNull()
            if (upd != null) {
                _uiState.update { it.copy(updateInfo = upd) }
            }
        }
    }

    fun openAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = true) }
    }

    fun dismissAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = false) }
    }

    fun selectUiPreset(preset: UiPreset) {
        _uiState.update { it.copy(showAppearanceDialog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.updateActiveUiPreset(preset)
            showActionFeedback("Switched UI preset to ${preset.displayName}")
        }
    }

    fun openCustomizeHomeDialog() {
        _uiState.update { it.copy(showCustomizeHomeDialog = true) }
        val userId = _uiState.value.profile?.jellyfinUserId ?: return
        if (_uiState.value.libraryViews.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val views = catalogRepository.loadUserLibraryViews(userId)
                _uiState.update { it.copy(libraryViews = views) }
            }
        }
    }

    fun dismissCustomizeHomeDialog() {
        _uiState.update { it.copy(showCustomizeHomeDialog = false) }
    }

    fun saveHomeCustomization(
        pinnedLibraryIds: List<String>,
        hiddenRowIds: List<String>,
        rowOrder: List<String>,
        defaultStartupTab: HomeTabId
    ) {
        val current = _uiState.value.profile ?: return
        _uiState.update { it.copy(showCustomizeHomeDialog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            val updated = current.copy(
                pinnedLibraryIds = pinnedLibraryIds,
                hiddenHomeRowIds = hiddenRowIds,
                homeRowOrder = rowOrder,
                defaultStartupTab = defaultStartupTab
            )
            authRepository.updateProfilePreferences(updated)
            showActionFeedback("Saved Home layout customization")
            loadCatalog(_uiState.value.activeTab, _uiState.value.selectedGenre, _uiState.value.availabilityFilter)
        }
    }

    fun openSyncPlayDialog() {
        _uiState.update { it.copy(showSyncPlayDialog = true) }
        refreshSyncPlayGroups()
    }

    fun dismissSyncPlayDialog() {
        _uiState.update { it.copy(showSyncPlayDialog = false) }
    }

    fun refreshSyncPlayGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.refreshGroups()
        }
    }

    fun createSyncPlayGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.createGroup(name)
                .onSuccess { grp ->
                    showActionFeedback("Started SyncPlay room: ${grp?.groupName ?: name}")
                }
                .onFailure { err ->
                    showActionFeedback("SyncPlay unavailable: ${err.message ?: "Check Jellyfin"}")
                }
        }
    }

    fun joinSyncPlayGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.joinGroup(groupId)
                .onSuccess { grp ->
                    showActionFeedback("Joined SyncPlay: ${grp?.groupName ?: "Room"}")
                }
                .onFailure { err ->
                    showActionFeedback("Could not join SyncPlay: ${err.message ?: "Error"}")
                }
        }
    }

    fun leaveSyncPlayGroup() {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.leaveGroup()
                .onSuccess { showActionFeedback("Left SyncPlay room") }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    showActionFeedback("Could not leave SyncPlay: ${err.message ?: "Error"}")
                }
        }
    }

    fun openUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = true) }
    }

    fun dismissUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = false) }
    }

    fun dismissReadyAlert(requestId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.dismissReadyAlert(requestId)
        }
    }

    fun resolveReadyAlertPlayId(alert: ReadyRequestAlert): String? {
        return alert.jellyfinId ?: catalogRepository.findJellyfinIdForTmdb(alert.tmdbId, alert.mediaType)
    }

    fun selectTab(tab: HomeTab) {
        val state = _uiState.value
        if (state.activeTab == tab) {
            // Re-selecting the tab that is already loading: don't restart the same request.
            if (state.isLoading && catalogJob?.isActive == true) return
            val alreadyLoaded = lastLoadedTab == tab && when (tab) {
                HomeTab.COLLECTIONS -> state.collections.isNotEmpty()
                HomeTab.LIVE_TV -> state.liveTvChannels.isNotEmpty() || state.liveTvRecordings.isNotEmpty()
                else -> state.rows.isNotEmpty()
            }
            if (alreadyLoaded) return
        }
        loadCatalog(tab, GenreFilterOption.ALL_GENRES.first(), state.availabilityFilter)
    }

    fun selectGenre(genre: GenreFilterOption) {
        val currentTab = _uiState.value.activeTab
        if (currentTab != HomeTab.SHOWS && currentTab != HomeTab.MOVIES) return
        if (_uiState.value.selectedGenre.id == genre.id) return
        loadCatalog(currentTab, genre, _uiState.value.availabilityFilter)
    }

    fun selectAvailabilityFilter(filter: AvailabilityFilter) {
        if (_uiState.value.availabilityFilter == filter) return
        loadCatalog(_uiState.value.activeTab, _uiState.value.selectedGenre, filter)
    }

    fun refresh() {
        loadCatalog(
            tab = _uiState.value.activeTab,
            genre = _uiState.value.selectedGenre,
            availabilityFilter = _uiState.value.availabilityFilter,
            forceRefresh = true
        )
    }

    private var continueWatchingJob: Job? = null

    /**
     * Called whenever Home becomes visible again (e.g. back from the player). Only swaps the
     * Continue Watching row in place so focus and the rest of the page stay put.
     */
    fun refreshContinueWatching() {
        val state = _uiState.value
        if (state.activeTab != HomeTab.HOME || state.availabilityFilter != AvailabilityFilter.ALL) return
        if (lastLoadedTab != HomeTab.HOME || state.isLoading || catalogJob?.isActive == true) return
        if (continueWatchingJob?.isActive == true) return
        val profile = state.profile ?: return
        continueWatchingJob = viewModelScope.launch(Dispatchers.IO) {
            val items = try {
                catalogRepository.loadContinueWatchingItems(profile.jellyfinUserId, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            _uiState.update { s ->
                if (s.activeTab != HomeTab.HOME || s.isLoading) return@update s
                val idx = s.rows.indexOfFirst { it.id == "continue_watching" }
                val newRows = when {
                    idx >= 0 && items.isEmpty() -> s.rows.filterIndexed { i, _ -> i != idx }
                    idx >= 0 -> s.rows.toMutableList().also { it[idx] = it[idx].copy(items = items) }
                    items.isNotEmpty() -> {
                        val insertAt = if (s.rows.firstOrNull()?.id == "ready_to_watch") 1 else 0
                        s.rows.toMutableList().also {
                            it.add(
                                insertAt,
                                CatalogRow(
                                    id = "continue_watching",
                                    title = "Continue Watching",
                                    style = RowStyle.CONTINUE_WATCHING,
                                    items = items
                                )
                            )
                        }
                    }
                    else -> s.rows
                }
                if (newRows == s.rows) s else s.copy(rows = newRows)
            }
        }
    }

    fun togglePreviewMute() {
        _uiState.update { it.copy(isPreviewMuted = !it.isPreviewMuted) }
    }

    fun toggleFeaturedMyList() {
        val featured = _uiState.value.featuredItem ?: return
        toggleMyListForItem(featured)
    }

    fun togglePosterMyList(item: UnifiedMediaItem) {
        _uiState.update { it.copy(posterMenuTarget = null) }
        toggleMyListForItem(item)
    }

    fun togglePosterWatched(item: UnifiedMediaItem) {
        val profile = _uiState.value.profile ?: return
        val jfId = item.jellyfinId ?: return
        val nextWatched = !item.isWatched
        _uiState.update { it.copy(posterMenuTarget = null) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.markWatched(profile.jellyfinUserId, jfId, nextWatched)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedFeatured = if (state.featuredItem?.id == item.id) {
                            state.featuredItem.copy(isWatched = nextWatched)
                        } else state.featuredItem
                        val updatedRows = state.rows.map { row ->
                            row.copy(
                                items = row.items.map {
                                    if (it.id == item.id) it.copy(isWatched = nextWatched) else it
                                }
                            )
                        }
                        state.copy(featuredItem = updatedFeatured, rows = updatedRows)
                    }
                    showActionFeedback(
                        if (nextWatched) "Marked \"${item.title}\" as Watched"
                        else "Marked \"${item.title}\" as Unwatched"
                    )
                }
                .onFailure { err ->
                    showActionFeedback("Failed to update watched state: ${err.message ?: "Error"}")
                }
        }
    }

    private fun toggleMyListForItem(target: UnifiedMediaItem) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.toggleMyList(profile.jellyfinUserId, target)
                .onSuccess { inList ->
                    _uiState.update { state ->
                        val updatedFeatured = if (state.featuredItem?.id == target.id) {
                            state.featuredItem.copy(isInMyList = inList)
                        } else state.featuredItem

                        val updatedRows = state.rows.map { row ->
                            if (row.id == "my_list" || row.id == "my_list_page") {
                                val nextItems = if (inList) {
                                    (listOf(target.copy(isInMyList = true)) + row.items).distinctBy { it.id }
                                } else {
                                    row.items.filterNot { it.id == target.id }
                                }
                                row.copy(items = nextItems)
                            } else if (!inList && (row.id == "my_list_shows" || row.id == "my_list_movies")) {
                                // My List tab split rows must also drop the removed title.
                                row.copy(items = row.items.filterNot { it.id == target.id })
                            } else {
                                row.copy(
                                    items = row.items.map {
                                        if (it.id == target.id) it.copy(isInMyList = inList) else it
                                    }
                                )
                            }
                        }.filter { it.items.isNotEmpty() }

                        state.copy(featuredItem = updatedFeatured, rows = updatedRows)
                    }
                    showActionFeedback(
                        if (inList) "Added \"${target.title}\" to My List"
                        else "Removed \"${target.title}\" from My List"
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    showActionFeedback("Couldn't update My List: ${err.message ?: "Error"}")
                }
        }
    }

    fun openContinueWatchingOptions(item: UnifiedMediaItem) {
        _uiState.update { it.copy(continueWatchingMenuTarget = item) }
    }

    fun dismissContinueWatchingOptions() {
        _uiState.update { it.copy(continueWatchingMenuTarget = null) }
    }

    fun openPosterOptions(item: UnifiedMediaItem) {
        _uiState.update { it.copy(posterMenuTarget = item) }
    }

    fun dismissPosterOptions() {
        _uiState.update { it.copy(posterMenuTarget = null) }
    }

    fun dismissActionFeedback() {
        feedbackJob?.cancel()
        _uiState.update { it.copy(actionFeedbackMessage = null) }
    }

    fun quickRequestItem(item: UnifiedMediaItem, is4k: Boolean) {
        val tmdbId = item.tmdbId ?: return
        _uiState.update { it.copy(posterMenuTarget = null) }
        val profile = _uiState.value.profile
        if (!_uiState.value.isSeerrConfigured) {
            showActionFeedback("Connect Seerr in Servers to submit requests")
            return
        }
        if (profile != null && !profile.canSubmitRequests) {
            showActionFeedback("Requests are locked for Kids profiles")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val seasons = if (item.mediaType == MediaType.SERIES) {
                item.seasons.map { it.seasonNumber }.filter { it > 0 }.ifEmpty { null }
            } else null

            catalogRepository.submitRequest(
                tmdbId = tmdbId,
                mediaType = item.mediaType,
                is4k = is4k,
                seasons = seasons
            ).onSuccess {
                _uiState.update { state ->
                    val applyStatus: (UnifiedMediaItem) -> UnifiedMediaItem = { current ->
                        if (current.id == item.id || (current.tmdbId == tmdbId && current.mediaType == item.mediaType)) {
                            if (is4k) current.copy(uhdStatus = AvailabilityTier.PENDING)
                            else current.copy(hdStatus = AvailabilityTier.PENDING)
                        } else current
                    }
                    state.copy(
                        featuredItem = state.featuredItem?.let(applyStatus),
                        rows = state.rows.map { row -> row.copy(items = row.items.map(applyStatus)) },
                        activeDownloadCount = state.activeDownloadCount + 1
                    )
                }
                showActionFeedback("Requested \"${item.title}\" (${if (is4k) "4K" else "HD"})")
            }.onFailure { err ->
                showActionFeedback("Request failed: ${err.message ?: "Unknown error"}")
            }
        }
    }

    private fun showActionFeedback(message: String) {
        feedbackJob?.cancel()
        _uiState.update { it.copy(actionFeedbackMessage = message) }
        feedbackJob = viewModelScope.launch {
            delay(3500L)
            _uiState.update { it.copy(actionFeedbackMessage = null) }
        }
    }

    fun removeFromContinueWatching(item: UnifiedMediaItem) {
        val profile = _uiState.value.profile ?: return
        val jfId = item.jellyfinId ?: return
        _uiState.update { it.copy(continueWatchingMenuTarget = null) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.removeFromContinueWatching(profile.jellyfinUserId, jfId)
                .onSuccess {
                    _uiState.update { state ->
                        val nextRows = state.rows.mapNotNull { row ->
                            if (row.id == "continue_watching") {
                                val filtered = row.items.filterNot { it.id == item.id }
                                if (filtered.isEmpty()) null else row.copy(items = filtered)
                            } else row
                        }
                        state.copy(rows = nextRows)
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    showActionFeedback("Couldn't remove from Continue Watching: ${err.message ?: "Error"}")
                }
        }
    }

    fun openCollection(boxSetId: String, title: String, tmdbCollectionId: Int? = null) {
        val profile = _uiState.value.profile ?: return
        _uiState.update {
            it.copy(
                activeCollectionDetails = CollectionSummary(
                    id = boxSetId,
                    tmdbCollectionId = tmdbCollectionId,
                    name = title
                ),
                collectionDetailsLoading = true
            )
        }
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch(Dispatchers.IO) {
            val details = try {
                catalogRepository.loadBoxSetDetails(
                    userId = profile.jellyfinUserId,
                    boxSetId = boxSetId,
                    fallbackTitle = title,
                    tmdbCollectionIdHint = tmdbCollectionId
                )
            } catch (e: CancellationException) {
                ensureActive() // rethrow if the dialog was dismissed / superseded
                null
            } catch (e: Exception) {
                // Never leave the dialog spinning forever (or crash the app) on a network error.
                null
            }
            _uiState.update { state ->
                if (state.activeCollectionDetails?.id != boxSetId) state
                else state.copy(
                    activeCollectionDetails = details ?: state.activeCollectionDetails,
                    collectionDetailsLoading = false
                )
            }
        }
    }

    fun dismissCollectionDetails() {
        collectionJob?.cancel()
        _uiState.update { it.copy(activeCollectionDetails = null, collectionDetailsLoading = false) }
    }

    fun requestCollectionItem(item: UnifiedMediaItem, is4k: Boolean = false) {
        val tmdbId = item.tmdbId ?: return
        val profile = _uiState.value.profile
        if (!_uiState.value.isSeerrConfigured) {
            showActionFeedback("Connect Seerr in Servers to submit requests")
            return
        }
        if (profile != null && !profile.canSubmitRequests) {
            showActionFeedback("Requests are locked for Kids profiles")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.submitRequest(tmdbId = tmdbId, mediaType = item.mediaType, is4k = is4k)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedCol = state.activeCollectionDetails?.let { col ->
                            col.copy(
                                items = col.items.map { part ->
                                    if (part.tmdbId == tmdbId) {
                                        if (is4k) part.copy(uhdStatus = AvailabilityTier.PENDING)
                                        else part.copy(hdStatus = AvailabilityTier.PENDING)
                                    } else part
                                }
                            )
                        }
                        state.copy(
                            activeCollectionDetails = updatedCol,
                            activeDownloadCount = state.activeDownloadCount + 1
                        )
                    }
                    showActionFeedback("Requested \"${item.title}\"")
                }
                .onFailure { err ->
                    showActionFeedback("Request failed: ${err.message ?: "Unknown error"}")
                }
        }
    }

    fun requestAllMissingInCollection(is4k: Boolean = false) {
        val profile = _uiState.value.profile
        if (!_uiState.value.isSeerrConfigured) {
            showActionFeedback("Connect Seerr in Servers to submit requests")
            return
        }
        if (profile != null && !profile.canSubmitRequests) {
            showActionFeedback("Requests are locked for Kids profiles")
            return
        }
        val col = _uiState.value.activeCollectionDetails ?: return
        val missing = col.items.filter { !it.isPlayable && it.canRequestHd && it.tmdbId != null }
        if (missing.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            val requestedTmdbIds = mutableSetOf<Int>()
            missing.forEach { part ->
                val tmdb = part.tmdbId ?: return@forEach
                catalogRepository.submitRequest(tmdbId = tmdb, mediaType = part.mediaType, is4k = is4k)
                    .onSuccess {
                        count++
                        requestedTmdbIds += tmdb
                    }
            }
            if (requestedTmdbIds.isNotEmpty()) {
                _uiState.update { state ->
                    val updatedCol = state.activeCollectionDetails?.let { cur ->
                        cur.copy(
                            items = cur.items.map { part ->
                                if (part.tmdbId in requestedTmdbIds) {
                                    if (is4k) part.copy(uhdStatus = AvailabilityTier.PENDING)
                                    else part.copy(hdStatus = AvailabilityTier.PENDING)
                                } else part
                            }
                        )
                    }
                    state.copy(
                        activeCollectionDetails = updatedCol,
                        activeDownloadCount = state.activeDownloadCount + count
                    )
                }
            }
            showActionFeedback(
                if (count > 0) "Requested $count missing title${if (count == 1) "" else "s"} in ${col.name}"
                else "Unable to submit collection requests"
            )
        }
    }

    fun onItemFocused(item: UnifiedMediaItem, force: Boolean = false) {
        if (!force && _uiState.value.featuredItem?.id == item.id) return
        focusPreviewJob?.cancel()
        focusPreviewJob = viewModelScope.launch(Dispatchers.IO) {
            if (!force) {
                delay(550L)
            }
            _uiState.update { it.copy(featuredItem = item, previewStream = null) }

            if (item.boxSetId != null) return@launch
            val profile = _uiState.value.profile ?: return@launch

            val needsMetadataEnrichment = item.overview.isBlank() ||
                (item.backdropUrl == null && item.posterUrl == null)
            val needsTrailerEnrichment = profile.autoplayPreviews &&
                item.youtubeTrailerKey == null &&
                item.localTrailerItemId == null

            val mergedFeatured: UnifiedMediaItem? = if (needsMetadataEnrichment || needsTrailerEnrichment) {
                val enriched = catalogRepository.loadFullDetails(
                    userId = profile.jellyfinUserId,
                    tmdbId = item.tmdbId,
                    jellyfinId = item.jellyfinId,
                    mediaType = item.mediaType
                ).getOrNull()

                enriched?.copy(
                    id = item.id,
                    jellyfinId = if (item.mediaType == MediaType.EPISODE && !item.jellyfinId.isNullOrBlank()) {
                        item.jellyfinId
                    } else {
                        enriched.jellyfinId ?: item.jellyfinId
                    },
                    episodeLabel = item.episodeLabel ?: enriched.episodeLabel,
                    resumePositionTicks = if (item.resumePositionTicks > 0L) item.resumePositionTicks else enriched.resumePositionTicks
                )?.also { merged ->
                    _uiState.update { state ->
                        if (state.featuredItem?.id == item.id) {
                            state.copy(featuredItem = merged)
                        } else state
                    }
                }
            } else {
                item
            }

            if (!profile.autoplayPreviews) return@launch

            delay(1400L)
            val target = mergedFeatured ?: item
            val stream = trailerResolver.resolvePreview(
                localTrailerId = target.localTrailerItemId,
                youtubeKey = target.youtubeTrailerKey
            )
            if (stream != null) {
                _uiState.update { state ->
                    if (state.featuredItem?.id == item.id) {
                        state.copy(previewStream = stream)
                    } else state
                }
            }
        }
    }

    private fun loadCatalog(
        tab: HomeTab,
        genre: GenreFilterOption,
        availabilityFilter: AvailabilityFilter,
        forceRefresh: Boolean = false
    ) {
        // Cancel any in-flight load so rapid tab/filter switching can't race and let a stale
        // (slower) response overwrite the rows of the tab the user actually selected.
        catalogJob?.cancel()
        val generation = ++catalogGeneration
        catalogJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    activeTab = tab,
                    selectedGenre = genre,
                    availabilityFilter = availabilityFilter,
                    isLoading = true,
                    errorMessage = null,
                    previewStream = null
                )
            }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active profile selected.") }
                return@launch
            }
            val serverConfig = prefs.serverConfigFlow.first()
            _uiState.update { it.copy(profile = profile, isSeerrConfigured = prefs.isSeerrConfigured) }

            // Download badge / 4K capability / library views: never block the rows on them.
            refreshAuxiliaryState(profile)

            // Stale-while-revalidate: paint the last good content for this exact view right away
            // (no spinner), then swap in the fresh rows below. A user-initiated Refresh skips it.
            val cacheKey = homeCacheKeyFor(profile, tab, genre, availabilityFilter, serverConfig)
            saveLocallyEditedRows()
            var showingCachedContent = false
            if (cacheKey != null && !forceRefresh) {
                val cachedRows = try {
                    homeContentCache.read(cacheKey, currentJellyfinBase())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                ensureActive()
                if (!cachedRows.isNullOrEmpty() && generation == catalogGeneration) {
                    val cachedFeatured = pickInitialFeatured(cachedRows)
                    lastLoadedTab = tab
                    displayedContent = cacheKey to cachedRows
                    _uiState.update {
                        it.copy(rows = cachedRows, featuredItem = cachedFeatured, isLoading = false)
                    }
                    showingCachedContent = true
                    if (cachedFeatured != null) onItemFocused(cachedFeatured, force = true)
                }
            }

            val result: Result<CatalogLoadBundle> = try {
                Result.success(coroutineScope {
                    val collectionsDeferred = async {
                        if (tab == HomeTab.COLLECTIONS) {
                            catalogRepository.loadBoxSetCollections(profile.jellyfinUserId)
                        } else emptyList()
                    }
                    val liveTvChannelsDeferred = async {
                        if (tab == HomeTab.LIVE_TV) {
                            catalogRepository.loadLiveTvChannelsAndPrograms(profile.jellyfinUserId)
                        } else emptyList()
                    }
                    val liveTvRecordingsDeferred = async {
                        if (tab == HomeTab.LIVE_TV) {
                            catalogRepository.loadLiveTvRecordings(profile.jellyfinUserId)
                        } else emptyList()
                    }
                    val rowsDeferred = async {
                        when (tab) {
                            HomeTab.COLLECTIONS, HomeTab.LIVE_TV -> emptyList()
                            HomeTab.NEW_AND_POPULAR -> catalogRepository.loadNewAndPopular(
                                userId = profile.jellyfinUserId,
                                availabilityFilter = availabilityFilter,
                                activeProfile = profile,
                                forceRefresh = forceRefresh
                            )
                            HomeTab.MY_LIST -> {
                                val listItems = catalogRepository.loadMyList(profile.jellyfinUserId)
                                if (listItems.isEmpty()) {
                                    emptyList()
                                } else {
                                    val movies = listItems.filter { it.mediaType == MediaType.MOVIE }
                                    val shows = listItems.filter { it.mediaType != MediaType.MOVIE }
                                    buildList {
                                        add(
                                            CatalogRow(
                                                id = "my_list_page",
                                                title = "My List",
                                                style = RowStyle.POSTER,
                                                items = listItems
                                            )
                                        )
                                        if (shows.isNotEmpty() && movies.isNotEmpty()) {
                                            add(CatalogRow("my_list_shows", "Saved TV Shows", RowStyle.POSTER, shows))
                                            add(CatalogRow("my_list_movies", "Saved Movies", RowStyle.POSTER, movies))
                                        }
                                    }
                                }
                            }
                            HomeTab.HOME, HomeTab.SHOWS, HomeTab.MOVIES -> {
                                val activeGenre = if (genre.movieGenreId == null && genre.tvGenreId == null) null else genre
                                catalogRepository.loadHomeRows(
                                    userId = profile.jellyfinUserId,
                                    filterType = tab.filter,
                                    genreFilter = activeGenre,
                                    availabilityFilter = availabilityFilter,
                                    activeProfile = profile,
                                    forceRefresh = forceRefresh
                                )
                            }
                        }
                    }

                    val rows = rowsDeferred.await()
                    CatalogLoadBundle(
                        rows = rows,
                        collections = collectionsDeferred.await(),
                        liveChannels = liveTvChannelsDeferred.await(),
                        liveRecordings = liveTvRecordingsDeferred.await(),
                        featured = pickInitialFeatured(rows)
                    )
                })
            } catch (e: CancellationException) {
                // Superseded by a newer load (or ViewModel cleared): rethrow, never surface as an
                // error. A stray CancellationException from a child (e.g. a timeout) while this job
                // is still active is a real failure and must not leave isLoading stuck at true.
                ensureActive()
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
            ensureActive()
            if (generation != catalogGeneration) return@launch

            result.onSuccess { bundle ->
                lastLoadedTab = tab
                // Background swap over cached content: keep the hero (and its enrichment / preview)
                // when the title the user is looking at is still on the page, so nothing jumps.
                val currentFeaturedId = _uiState.value.featuredItem?.id
                val keepFeatured = showingCachedContent && currentFeaturedId != null &&
                    bundle.rows.any { row -> row.items.any { it.id == currentFeaturedId } }
                _uiState.update {
                    it.copy(
                        rows = bundle.rows,
                        collections = bundle.collections,
                        liveTvChannels = bundle.liveChannels,
                        liveTvRecordings = bundle.liveRecordings,
                        featuredItem = if (keepFeatured) it.featuredItem else bundle.featured,
                        isLoading = false
                    )
                }
                displayedContent = cacheKey?.let { it to bundle.rows }
                if (!keepFeatured && bundle.featured != null) {
                    onItemFocused(bundle.featured, force = true)
                }
                if (cacheKey != null && bundle.rows.isNotEmpty()) {
                    launch(Dispatchers.IO) {
                        runCatching { homeContentCache.write(cacheKey, bundle.rows, currentJellyfinBase()) }
                    }
                }
                if (tab == HomeTab.HOME && availabilityFilter == AvailabilityFilter.ALL) {
                    launch(Dispatchers.IO) {
                        val continueItems = bundle.rows.firstOrNull { it.id == "continue_watching" }?.items.orEmpty()
                        val channelItems = bundle.rows.firstOrNull { it.id == "trending_now" }?.items
                            ?: bundle.rows.firstOrNull { it.id != "continue_watching" }?.items.orEmpty()
                        tvChannelSyncManager.syncHomeChannels(continueItems, channelItems)
                    }
                }
            }.onFailure { err ->
                val message = UnifiedCatalogRepository.formatHttpError(err, "Failed to load catalog")
                val keepsCurrentContent = lastLoadedTab == tab
                _uiState.update {
                    if (keepsCurrentContent) {
                        // Refresh of the visible tab failed: keep what's on screen.
                        it.copy(isLoading = false, errorMessage = message)
                    } else {
                        // The content on screen belongs to another tab; clear it so the
                        // error + Retry state is shown instead of mislabelled stale rows.
                        displayedContent = null
                        it.copy(
                            isLoading = false,
                            errorMessage = message,
                            rows = emptyList(),
                            collections = emptyList(),
                            liveTvChannels = emptyList(),
                            liveTvRecordings = emptyList(),
                            featuredItem = null,
                            previewStream = null
                        )
                    }
                }
                if (keepsCurrentContent) {
                    // The full-screen error UI only appears when nothing is loaded, so tell the
                    // user a background refresh failed instead of failing silently.
                    showActionFeedback(message)
                }
            }
        }
    }

    /**
     * Nav-badge download count, 4K-server capability and library views. Loaded beside the rows
     * instead of gating them (the old request-hub load did up to 50 Seerr detail calls first).
     */
    private fun refreshAuxiliaryState(profile: UserProfile) {
        if (auxiliaryJob?.isActive == true) return
        auxiliaryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    val downloadsDeferred = async {
                        if (prefs.isSeerrConfigured) catalogRepository.loadActiveDownloadCount() else 0
                    }
                    val has4kMovieDeferred = async { catalogRepository.has4kServerConfigured(MediaType.MOVIE) }
                    val has4kTvDeferred = async { catalogRepository.has4kServerConfigured(MediaType.SERIES) }
                    val libraryViewsDeferred = async {
                        if (_uiState.value.libraryViews.isEmpty()) {
                            catalogRepository.loadUserLibraryViews(profile.jellyfinUserId)
                        } else _uiState.value.libraryViews
                    }
                    val downloads = downloadsDeferred.await()
                    val has4kMovie = has4kMovieDeferred.await()
                    val has4kTv = has4kTvDeferred.await()
                    val views = libraryViewsDeferred.await()
                    _uiState.update {
                        it.copy(
                            activeDownloadCount = downloads,
                            has4kMovieServer = has4kMovie,
                            has4kTvServer = has4kTv,
                            libraryViews = views
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the previous values in place.
            }
        }
    }

    private fun saveLocallyEditedRows() {
        val shown = displayedContent ?: return
        val current = _uiState.value.rows
        if (current === shown.second || current.isEmpty()) return
        displayedContent = shown.first to current
        try {
            homeContentCache.write(shown.first, current, currentJellyfinBase())
        } catch (_: Exception) {
            // Best effort.
        }
    }

    private fun pickInitialFeatured(rows: List<CatalogRow>): UnifiedMediaItem? =
        rows.firstOrNull { it.id != "continue_watching" }?.items?.firstOrNull()
            ?: rows.firstOrNull()?.items?.firstOrNull()

    private fun currentJellyfinBase(): String =
        prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }

    /** Only the row-based tabs are cached; Collections / Live TV load as before. */
    private fun homeCacheKeyFor(
        profile: UserProfile,
        tab: HomeTab,
        genre: GenreFilterOption,
        availabilityFilter: AvailabilityFilter,
        serverConfig: ServerConfig
    ): HomeContentCache.Key? {
        if (tab == HomeTab.COLLECTIONS || tab == HomeTab.LIVE_TV) return null
        return HomeContentCache.keyFor(
            profile = profile,
            tab = tab.name,
            genreId = genre.id,
            availability = availabilityFilter.name,
            seerrConfigured = serverConfig.isSeerrConfigured,
            jellyfinServer = serverConfig.jellyfinUrl.ifBlank { serverConfig.jellyfinLocalUrl },
            seerrServer = serverConfig.seerrUrl.ifBlank { serverConfig.seerrLocalUrl },
            metadataLanguage = profile.metadataLanguage.ifBlank { "en" }
        )
    }

    private data class CatalogLoadBundle(
        val rows: List<CatalogRow>,
        val collections: List<CollectionSummary>,
        val liveChannels: List<LiveTvChannel>,
        val liveRecordings: List<UnifiedMediaItem>,
        val featured: UnifiedMediaItem?
    )
}
