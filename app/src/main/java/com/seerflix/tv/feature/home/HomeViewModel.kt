package com.seerflix.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.core.player.YouTubeTrailerResolver
import com.seerflix.tv.core.tv.TvChannelSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab(val label: String, val filter: MediaType?) {
    HOME("Home", null),
    SHOWS("TV Shows", MediaType.SERIES),
    MOVIES("Movies", MediaType.MOVIE),
    COLLECTIONS("Collections", null),
    NEW_AND_POPULAR("New & Popular", null),
    MY_LIST("My List", null)
}

data class HomeUiState(
    val activeTab: HomeTab = HomeTab.HOME,
    val selectedGenre: GenreFilterOption = GenreFilterOption.ALL_GENRES.first(),
    val availabilityFilter: AvailabilityFilter = AvailabilityFilter.ALL,
    val profile: UserProfile? = null,
    val rows: List<CatalogRow> = emptyList(),
    val collections: List<CollectionSummary> = emptyList(),
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
    val actionFeedbackMessage: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver,
    private val tvChannelSyncManager: TvChannelSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var focusPreviewJob: Job? = null
    private var feedbackJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.activeProfileFlow.collect { active ->
                if (active != null) {
                    _uiState.update { it.copy(profile = active) }
                }
            }
        }
        loadCatalog(HomeTab.HOME, GenreFilterOption.ALL_GENRES.first(), AvailabilityFilter.ALL)
    }

    fun openAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = true) }
    }

    fun dismissAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = false) }
    }

    fun selectUiPreset(preset: UiPreset) {
        _uiState.update { it.copy(showAppearanceDialog = false) }
        viewModelScope.launch {
            authRepository.updateActiveUiPreset(preset)
            showActionFeedback("Switched UI preset to ${preset.displayName}")
        }
    }

    fun selectTab(tab: HomeTab) {
        if (_uiState.value.activeTab == tab &&
            (_uiState.value.rows.isNotEmpty() || _uiState.value.collections.isNotEmpty())
        ) return
        loadCatalog(tab, GenreFilterOption.ALL_GENRES.first(), _uiState.value.availabilityFilter)
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
        loadCatalog(_uiState.value.activeTab, _uiState.value.selectedGenre, _uiState.value.availabilityFilter)
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

    private fun toggleMyListForItem(target: UnifiedMediaItem) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
            val details = catalogRepository.loadBoxSetDetails(
                userId = profile.jellyfinUserId,
                boxSetId = boxSetId,
                fallbackTitle = title,
                tmdbCollectionIdHint = tmdbCollectionId
            )
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
        _uiState.update { it.copy(activeCollectionDetails = null, collectionDetailsLoading = false) }
    }

    fun requestCollectionItem(item: UnifiedMediaItem, is4k: Boolean = false) {
        val tmdbId = item.tmdbId ?: return
        viewModelScope.launch {
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
        val col = _uiState.value.activeCollectionDetails ?: return
        val missing = col.items.filter { !it.isPlayable && it.canRequestHd && it.tmdbId != null }
        if (missing.isEmpty()) return
        viewModelScope.launch {
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

    fun onItemFocused(item: UnifiedMediaItem) {
        if (_uiState.value.featuredItem?.id == item.id) return
        focusPreviewJob?.cancel()
        focusPreviewJob = viewModelScope.launch {
            delay(380L)
            _uiState.update { it.copy(featuredItem = item, previewStream = null) }

            if (item.boxSetId != null) return@launch
            val profile = _uiState.value.profile ?: return@launch
            // Enrich focused item with trailer key / local trailer / live download status / My List state
            val enriched = catalogRepository.loadFullDetails(
                userId = profile.jellyfinUserId,
                tmdbId = item.tmdbId,
                jellyfinId = item.jellyfinId,
                mediaType = item.mediaType
            ).getOrNull()

            if (enriched != null) {
                _uiState.update { state ->
                    if (state.featuredItem?.id == item.id) {
                        state.copy(featuredItem = enriched)
                    } else state
                }
            }

            if (!profile.autoplayPreviews) return@launch

            // Dwell 1.2s more before resolving and auto-playing trailer preview
            delay(1200L)
            val target = enriched ?: item
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
        availabilityFilter: AvailabilityFilter
    ) {
        viewModelScope.launch {
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
            _uiState.update { it.copy(profile = profile) }

            runCatching {
                coroutineScope {
                    val collectionsDeferred = async {
                        if (tab == HomeTab.COLLECTIONS) {
                            catalogRepository.loadBoxSetCollections(profile.jellyfinUserId)
                        } else emptyList()
                    }
                    val rowsDeferred = async {
                        when (tab) {
                            HomeTab.COLLECTIONS -> emptyList()
                            HomeTab.NEW_AND_POPULAR -> catalogRepository.loadNewAndPopular(
                                userId = profile.jellyfinUserId,
                                availabilityFilter = availabilityFilter
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
                                    availabilityFilter = availabilityFilter
                                )
                            }
                        }
                    }
                    val requestsDeferred = async {
                        catalogRepository.loadRequestsHub().getOrDefault(emptyList())
                    }
                    val has4kMovieDeferred = async {
                        catalogRepository.has4kServerConfigured(MediaType.MOVIE)
                    }
                    val has4kTvDeferred = async {
                        catalogRepository.has4kServerConfigured(MediaType.SERIES)
                    }

                    val rows = rowsDeferred.await()
                    val collections = collectionsDeferred.await()
                    val requests = requestsDeferred.await()
                    val activeDownloads = requests.count {
                        it.downloadProgress != null || it.mediaStatus.isRequestedOrDownloading
                    }
                    val initialFeatured = rows.firstOrNull { it.id != "continue_watching" }?.items?.firstOrNull()
                        ?: rows.firstOrNull()?.items?.firstOrNull()
                    CatalogLoadBundle(
                        rows = rows,
                        collections = collections,
                        featured = initialFeatured,
                        activeDownloads = activeDownloads,
                        has4kMovie = has4kMovieDeferred.await(),
                        has4kTv = has4kTvDeferred.await()
                    )
                }
            }.onSuccess { bundle ->
                _uiState.update {
                    it.copy(
                        rows = bundle.rows,
                        collections = bundle.collections,
                        featuredItem = bundle.featured,
                        activeDownloadCount = bundle.activeDownloads,
                        has4kMovieServer = bundle.has4kMovie,
                        has4kTvServer = bundle.has4kTv,
                        isLoading = false
                    )
                }
                if (bundle.featured != null) {
                    onItemFocused(bundle.featured)
                }
                if (tab == HomeTab.HOME && availabilityFilter == AvailabilityFilter.ALL) {
                    launch {
                        val continueItems = bundle.rows.firstOrNull { it.id == "continue_watching" }?.items.orEmpty()
                        val channelItems = bundle.rows.firstOrNull { it.id == "trending_now" }?.items
                            ?: bundle.rows.firstOrNull { it.id != "continue_watching" }?.items.orEmpty()
                        tvChannelSyncManager.syncHomeChannels(continueItems, channelItems)
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = err.message ?: "Failed to load catalog"
                    )
                }
            }
        }
    }

    private data class CatalogLoadBundle(
        val rows: List<CatalogRow>,
        val collections: List<CollectionSummary>,
        val featured: UnifiedMediaItem?,
        val activeDownloads: Int,
        val has4kMovie: Boolean,
        val has4kTv: Boolean
    )
}

