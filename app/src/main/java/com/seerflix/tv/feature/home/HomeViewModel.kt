package com.seerflix.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.core.player.YouTubeTrailerResolver
import com.seerflix.tv.core.tv.TvChannelSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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
    NEW_AND_POPULAR("New & Popular", null),
    MY_LIST("My List", null)
}

data class HomeUiState(
    val activeTab: HomeTab = HomeTab.HOME,
    val selectedGenre: GenreFilterOption = GenreFilterOption.ALL_GENRES.first(),
    val profile: UserProfile? = null,
    val rows: List<CatalogRow> = emptyList(),
    val featuredItem: UnifiedMediaItem? = null,
    val previewStream: ResolvedPreviewStream? = null,
    val isPreviewMuted: Boolean = true,
    val activeDownloadCount: Int = 0,
    val continueWatchingMenuTarget: UnifiedMediaItem? = null,
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

    init {
        loadCatalog(HomeTab.HOME, GenreFilterOption.ALL_GENRES.first())
    }

    fun selectTab(tab: HomeTab) {
        if (_uiState.value.activeTab == tab && _uiState.value.rows.isNotEmpty()) return
        loadCatalog(tab, GenreFilterOption.ALL_GENRES.first())
    }

    fun selectGenre(genre: GenreFilterOption) {
        val currentTab = _uiState.value.activeTab
        if (currentTab != HomeTab.SHOWS && currentTab != HomeTab.MOVIES) return
        if (_uiState.value.selectedGenre.id == genre.id) return
        loadCatalog(currentTab, genre)
    }

    fun refresh() {
        loadCatalog(_uiState.value.activeTab, _uiState.value.selectedGenre)
    }

    fun togglePreviewMute() {
        _uiState.update { it.copy(isPreviewMuted = !it.isPreviewMuted) }
    }

    fun toggleFeaturedMyList() {
        val profile = _uiState.value.profile ?: return
        val featured = _uiState.value.featuredItem ?: return
        viewModelScope.launch {
            catalogRepository.toggleMyList(profile.jellyfinUserId, featured)
                .onSuccess { inList ->
                    _uiState.update { state ->
                        val updatedFeatured = if (state.featuredItem?.id == featured.id) {
                            state.featuredItem.copy(isInMyList = inList)
                        } else state.featuredItem

                        val updatedRows = state.rows.map { row ->
                            if (row.id == "my_list" || row.id == "my_list_page") {
                                val nextItems = if (inList) {
                                    (listOf(featured.copy(isInMyList = true)) + row.items).distinctBy { it.id }
                                } else {
                                    row.items.filterNot { it.id == featured.id }
                                }
                                row.copy(items = nextItems)
                            } else {
                                row.copy(
                                    items = row.items.map {
                                        if (it.id == featured.id) it.copy(isInMyList = inList) else it
                                    }
                                )
                            }
                        }.filter { it.items.isNotEmpty() }

                        state.copy(featuredItem = updatedFeatured, rows = updatedRows)
                    }
                }
        }
    }

    fun openContinueWatchingOptions(item: UnifiedMediaItem) {
        _uiState.update { it.copy(continueWatchingMenuTarget = item) }
    }

    fun dismissContinueWatchingOptions() {
        _uiState.update { it.copy(continueWatchingMenuTarget = null) }
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

    fun onItemFocused(item: UnifiedMediaItem) {
        if (_uiState.value.featuredItem?.id == item.id) return
        focusPreviewJob?.cancel()
        focusPreviewJob = viewModelScope.launch {
            delay(380L)
            _uiState.update { it.copy(featuredItem = item, previewStream = null) }

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

    private fun loadCatalog(tab: HomeTab, genre: GenreFilterOption) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activeTab = tab,
                    selectedGenre = genre,
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
                val rows = when (tab) {
                    HomeTab.NEW_AND_POPULAR -> catalogRepository.loadNewAndPopular(profile.jellyfinUserId)
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
                            genreFilter = activeGenre
                        )
                    }
                }
                val requests = catalogRepository.loadRequestsHub().getOrDefault(emptyList())
                val activeDownloads = requests.count {
                    it.downloadProgress != null || it.mediaStatus.isRequestedOrDownloading
                }
                val initialFeatured = rows.firstOrNull { it.id != "continue_watching" }?.items?.firstOrNull()
                    ?: rows.firstOrNull()?.items?.firstOrNull()
                Triple(rows, initialFeatured, activeDownloads)
            }.onSuccess { (rows, featured, dlCount) ->
                _uiState.update {
                    it.copy(
                        rows = rows,
                        featuredItem = featured,
                        activeDownloadCount = dlCount,
                        isLoading = false
                    )
                }
                if (featured != null) {
                    onItemFocused(featured)
                }
                if (tab == HomeTab.HOME) {
                    launch {
                        val continueItems = rows.firstOrNull { it.id == "continue_watching" }?.items.orEmpty()
                        val channelItems = rows.firstOrNull { it.id == "trending_now" }?.items
                            ?: rows.firstOrNull { it.id != "continue_watching" }?.items.orEmpty()
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
}

