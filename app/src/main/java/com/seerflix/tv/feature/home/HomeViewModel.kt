package com.seerflix.tv.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
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
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab(val label: String, val filter: MediaType?) {
    HOME("Home", null),
    SHOWS("TV Shows", MediaType.SERIES),
    MOVIES("Movies", MediaType.MOVIE)
}

data class HomeUiState(
    val activeTab: HomeTab = HomeTab.HOME,
    val profile: UserProfile? = null,
    val rows: List<CatalogRow> = emptyList(),
    val featuredItem: UnifiedMediaItem? = null,
    val previewStream: ResolvedPreviewStream? = null,
    val isPreviewMuted: Boolean = true,
    val activeDownloadCount: Int = 0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var focusPreviewJob: Job? = null

    init {
        loadCatalog(HomeTab.HOME)
    }

    fun selectTab(tab: HomeTab) {
        if (_uiState.value.activeTab == tab && _uiState.value.rows.isNotEmpty()) return
        loadCatalog(tab)
    }

    fun refresh() {
        loadCatalog(_uiState.value.activeTab)
    }

    fun togglePreviewMute() {
        _uiState.update { it.copy(isPreviewMuted = !it.isPreviewMuted) }
    }

    fun onItemFocused(item: UnifiedMediaItem) {
        if (_uiState.value.featuredItem?.id == item.id) return
        focusPreviewJob?.cancel()
        focusPreviewJob = viewModelScope.launch {
            delay(380L)
            _uiState.update { it.copy(featuredItem = item, previewStream = null) }

            val profile = _uiState.value.profile ?: return@launch
            // Enrich focused item with trailer key / local trailer / live download status
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

    private fun loadCatalog(tab: HomeTab) {
        viewModelScope.launch {
            _uiState.update { it.copy(activeTab = tab, isLoading = true, errorMessage = null) }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active profile selected.") }
                return@launch
            }
            _uiState.update { it.copy(profile = profile) }

            runCatching {
                val rows = catalogRepository.loadHomeRows(profile.jellyfinUserId, tab.filter)
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
