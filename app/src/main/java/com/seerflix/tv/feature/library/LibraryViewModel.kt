package com.seerflix.tv.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.ContentRatingHelper
import com.seerflix.tv.core.model.UnifiedMediaItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class LibrarySort(val sortBy: String, val defaultOrder: String) {
    NAME("SortName", "Ascending"),
    DATE_ADDED("DateCreated,SortName", "Descending"),
    RELEASED("PremiereDate,ProductionYear,SortName", "Descending"),
    RATING("CommunityRating,SortName", "Descending"),
    RANDOM("Random", "Ascending")
}

enum class LibraryFilter(val jellyfinFilter: String?) {
    ALL(null),
    UNWATCHED("IsUnplayed"),
    WATCHED("IsPlayed"),
    FAVORITES("IsFavorite")
}

data class LibraryUiState(
    val title: String = "",
    val items: List<UnifiedMediaItem> = emptyList(),
    val totalCount: Int = 0,
    val sort: LibrarySort = LibrarySort.NAME,
    val filter: LibraryFilter = LibraryFilter.ALL,
    val genres: List<String> = emptyList(),
    val genre: String? = null,
    /** "A".."Z", "#" for titles starting with a digit or symbol, null for everything. */
    val letter: String? = null,
    val folderMode: Boolean = false,
    val canToggleFolderMode: Boolean = false,
    val folderBreadcrumbs: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    /** A "load more" page failed; the grid stays and shows a retry at the end. */
    val loadMoreFailed: Boolean = false,
    /** Server-side offset of the next page (items can be dropped by the on-device profile filter). */
    val nextServerIndex: Int = 0,
    /** Bumped on every full reload (folder change, sort, filter): the grid scrolls to the top and refocuses. */
    val reloadGeneration: Int = 0,
    /** After going back up a folder level: the folder card to land on (if it is on the first page). */
    val focusTargetId: String? = null,
    /** Folder navigation: move focus into the new grid (sort / filter / A–Z keep focus where it is). */
    val refocusGrid: Boolean = false
) {
    val canLoadMore: Boolean get() = nextServerIndex < totalCount
    val displayTitle: String
        get() = if (folderBreadcrumbs.isEmpty()) title else (listOf(title) + folderBreadcrumbs).joinToString(" › ")
}

/**
 * The full A-Z grid for a library (Movies, TV Shows or one Jellyfin library), paged from the
 * server with its own sort and filters, so even a few thousand titles stay quick on a stick.
 * Supports optional Folder View (`Recursive=false`) with subfolder drill-down inside any library.
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository
) : ViewModel() {

    private val rootParentId: String? = savedStateHandle.get<String>("parentId")?.takeIf { it.isNotBlank() }
    private val collectionType: String? = savedStateHandle.get<String>("collectionType")?.takeIf { it.isNotBlank() }
    private val baseIncludeTypes: String = savedStateHandle.get<String>("types")?.takeIf { it.isNotBlank() }
        ?: UnifiedCatalogRepository.includeTypesForCollectionType(collectionType, folderMode = false)
    private val initialFolderMode: Boolean = savedStateHandle.get<Boolean>("folderMode") == true

    private val folderStack = mutableListOf<Pair<String, String>>()

    private val _uiState = MutableStateFlow(
        LibraryUiState(
            title = savedStateHandle.get<String>("title").orEmpty(),
            folderMode = initialFolderMode && rootParentId != null,
            canToggleFolderMode = rootParentId != null
        )
    )
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    private fun currentParentId(): String? = folderStack.lastOrNull()?.first ?: rootParentId

    private fun currentIncludeTypes(): String {
        return if (_uiState.value.folderMode) {
            UnifiedCatalogRepository.includeTypesForCollectionType(collectionType, folderMode = true)
        } else {
            baseIncludeTypes
        }
    }

    init {
        // A title deleted from its Details page leaves the grid right away (no 404 card).
        viewModelScope.launch {
            catalogRepository.deletedItemIds.collect { deleted ->
                if (deleted.isEmpty()) return@collect
                _uiState.update { state ->
                    val kept = state.items.filterNot { it.jellyfinId in deleted }
                    if (kept.size == state.items.size) state
                    else state.copy(items = kept, totalCount = (state.totalCount - (state.items.size - kept.size)).coerceAtLeast(kept.size))
                }
            }
        }
        viewModelScope.launch {
            val profile = authRepository.activeProfileFlow.first() ?: return@launch
            val genres = catalogRepository.loadGenres(profile.jellyfinUserId, rootParentId, baseIncludeTypes)
            _uiState.update { it.copy(genres = genres) }
        }
        reload()
    }

    fun toggleFolderMode() {
        if (!_uiState.value.canToggleFolderMode) return
        val next = !_uiState.value.folderMode
        folderStack.clear()
        _uiState.update {
            it.copy(
                folderMode = next,
                folderBreadcrumbs = emptyList(),
                genre = if (next) null else it.genre,
                letter = if (next) null else it.letter
            )
        }
        reload()
    }

    fun enterFolder(folderId: String, folderName: String) {
        folderStack += folderId to folderName
        _uiState.update {
            it.copy(
                folderMode = true,
                folderBreadcrumbs = folderStack.map { f -> f.second }
            )
        }
        reload(refocusGrid = true)
    }

    /** Navigates up one subfolder level; returns true if handled, false if already at library root. */
    fun navigateUpFolder(): Boolean {
        if (folderStack.isEmpty()) return false
        val leftFolderId = folderStack.removeAt(folderStack.lastIndex).first
        _uiState.update {
            it.copy(folderBreadcrumbs = folderStack.map { f -> f.second })
        }
        reload(focusTargetId = leftFolderId, refocusGrid = true)
        return true
    }

    fun setSort(sort: LibrarySort) {
        if (sort == _uiState.value.sort && sort != LibrarySort.RANDOM) return
        _uiState.update { it.copy(sort = sort) }
        reload()
    }

    fun setFilter(filter: LibraryFilter) {
        if (filter == _uiState.value.filter) return
        _uiState.update { it.copy(filter = filter) }
        reload()
    }

    fun setGenre(genre: String?) {
        if (genre == _uiState.value.genre) return
        _uiState.update { it.copy(genre = genre) }
        reload()
    }

    fun setLetter(letter: String?) {
        if (letter == _uiState.value.letter) return
        _uiState.update { it.copy(letter = letter) }
        reload()
    }

    private fun reload(focusTargetId: String? = null, refocusGrid: Boolean = false) {
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = true, errorMessage = null, loadMoreFailed = false, items = emptyList(), totalCount = 0, nextServerIndex = 0,
                reloadGeneration = it.reloadGeneration + 1,
                focusTargetId = focusTargetId,
                refocusGrid = refocusGrid
            )
        }
        loadJob = viewModelScope.launch { loadPage(startIndex = 0) }
    }

    /** Retry after a failed first load or a failed "load more". */
    fun retry() {
        val state = _uiState.value
        if (state.items.isEmpty()) {
            reload()
        } else if (state.loadMoreFailed) {
            _uiState.update { it.copy(loadMoreFailed = false) }
            loadMore()
        }
    }

    /** Called when focus gets close to the end of what is loaded. */
    fun loadMore() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || state.loadMoreFailed || !state.canLoadMore) return
        _uiState.update { it.copy(isLoadingMore = true) }
        loadJob = viewModelScope.launch { loadPage(startIndex = state.nextServerIndex) }
    }

    private suspend fun loadPage(startIndex: Int) {
        val state = _uiState.value
        val profile = authRepository.activeProfileFlow.first()
        if (profile == null) {
            _uiState.update { it.copy(isLoading = false, isLoadingMore = false, errorMessage = "No active profile") }
            return
        }
        val (startsWith, lessThan) = letterQuery(state.letter)
        try {
            val page = catalogRepository.browseLibrary(
                userId = profile.jellyfinUserId,
                parentId = currentParentId(),
                includeItemTypes = currentIncludeTypes(),
                sortBy = state.sort.sortBy,
                sortOrder = state.sort.defaultOrder,
                filters = state.filter.jellyfinFilter,
                genre = state.genre,
                nameStartsWith = startsWith,
                nameLessThan = lessThan,
                maxOfficialRating = profile.effectiveRatingLimit.jellyfinMaxOfficialRating,
                startIndex = startIndex,
                limit = PAGE_SIZE,
                recursive = !state.folderMode
            )
            // The server's MaxOfficialRating lets unrated titles through and knows nothing about
            // the Kids-Mode genre exclusions, so apply the same profile filter as every other row.
            // Folders themselves have no content rating so keep them navigable in folder view.
            val allowed = page.items.filter { it.isFolder || ContentRatingHelper.isAllowed(it, profile) }
            _uiState.update {
                val merged = if (startIndex == 0) allowed else (it.items + allowed).distinctBy { item -> item.id }
                it.copy(
                    items = merged,
                    totalCount = page.totalCount,
                    nextServerIndex = startIndex + page.items.size,
                    isLoading = false,
                    isLoadingMore = false,
                    loadMoreFailed = false
                )
            }
            // A page the filter emptied entirely would leave nothing to focus to trigger the next one.
            if (allowed.isEmpty() && page.items.isNotEmpty() && _uiState.value.canLoadMore) {
                loadPage(_uiState.value.nextServerIndex)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val message = UnifiedCatalogRepository.formatHttpError(e, "Couldn't load the library")
            _uiState.update {
                if (startIndex > 0 && it.items.isNotEmpty()) {
                    // Keep what is already on screen; offer a retry for the next page.
                    it.copy(isLoadingMore = false, loadMoreFailed = true)
                } else {
                    it.copy(isLoading = false, isLoadingMore = false, errorMessage = message)
                }
            }
        }
    }

    companion object {
        const val PAGE_SIZE = 60
        val LETTERS: List<String> = listOf("#") + ('A'..'Z').map { it.toString() }

        /** "#" means "before A" (digits and symbols); a letter is a prefix match. */
        fun letterQuery(letter: String?): Pair<String?, String?> = when (letter) {
            null -> null to null
            "#" -> null to "A"
            else -> letter to null
        }
    }
}
