package com.seerflix.tv.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.UnifiedMediaItem
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

@Immutable
data class SearchUiState(
    val query: String = "",
    val results: List<UnifiedMediaItem> = emptyList(),
    val personResults: List<PersonSearchResult> = emptyList(),
    val trendingItems: List<UnifiedMediaItem> = emptyList(),
    val selectedGenre: GenreFilterOption? = null,
    val activePersonName: String? = null,
    val isSeerrConfigured: Boolean = true,
    val isSearching: Boolean = false,
    val isLoadingSuggestions: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {

    private val initialQuery: String = savedStateHandle.get<String>("query").orEmpty()
    private val initialPersonId: Int? = savedStateHandle.get<String>("personId")?.toIntOrNull()?.takeIf { it > 0 }
    private val initialJfPersonId: String? = savedStateHandle.get<String>("jfPersonId")?.takeIf { it.isNotBlank() }
    private val initialPersonName: String? = savedStateHandle.get<String>("personName")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(
        SearchUiState(
            query = initialQuery,
            isSeerrConfigured = sessionPreferences.isSeerrConfigured
        )
    )
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTrendingSuggestions()
        when {
            initialPersonId != null || initialJfPersonId != null -> selectPerson(
                personId = initialPersonId,
                jellyfinPersonId = initialJfPersonId,
                name = initialPersonName ?: initialQuery.ifBlank { "Filmography" }
            )
            initialQuery.trim().length >= 2 -> onQueryChange(initialQuery)
        }
    }

    private fun loadTrendingSuggestions() {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    isLoadingSuggestions = true,
                    isSeerrConfigured = sessionPreferences.isSeerrConfigured
                )
            }
            val profile = authRepository.activeProfileFlow.first()
            val userId = profile?.jellyfinUserId.orEmpty()
            val trending = catalogRepository.loadTrendingTitles(userId, activeProfile = profile)
            // Grid keys are derived from item ids; duplicates would crash the LazyVerticalGrid.
            _uiState.update { it.copy(trendingItems = trending.distinctBy { t -> t.id }, isLoadingSuggestions = false) }
        }
    }

    fun onQueryChange(newQuery: String) {
        _uiState.update {
            it.copy(
                query = newQuery,
                selectedGenre = null,
                activePersonName = null
            )
        }
        searchJob?.cancel()
        if (newQuery.trim().length < 2) {
            _uiState.update { it.copy(results = emptyList(), personResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350L)
            _uiState.update { it.copy(isSearching = true) }
            val profile = authRepository.activeProfileFlow.first()
            val userId = profile?.jellyfinUserId.orEmpty()
            val (mediaResults, people) = catalogRepository.searchUnifiedWithPeople(
                userId = userId,
                query = newQuery.trim(),
                activeProfile = profile
            )
            // A newer keystroke may have superseded this search while it was in flight; never let
            // stale results land on top of the current query.
            ensureActive()
            _uiState.update {
                if (it.query != newQuery) {
                    it
                } else {
                    it.copy(
                        results = mediaResults.distinctBy { r -> r.id },
                        personResults = people.distinctBy { p -> p.id },
                        isSearching = false
                    )
                }
            }
        }
    }

    fun selectGenre(genre: GenreFilterOption) {
        if (_uiState.value.selectedGenre?.id == genre.id || genre.id == "all") {
            clearFilter()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    query = "",
                    selectedGenre = genre,
                    activePersonName = null,
                    personResults = emptyList(),
                    isSearching = true
                )
            }
            val profile = authRepository.activeProfileFlow.first()
            val userId = profile?.jellyfinUserId.orEmpty()
            val items = catalogRepository.loadByGenre(userId, genre, activeProfile = profile)
            ensureActive()
            _uiState.update { it.copy(results = items.distinctBy { r -> r.id }, isSearching = false) }
        }
    }

    fun selectPerson(person: PersonSearchResult) {
        selectPerson(
            personId = person.id.takeIf { it > 0 && person.jellyfinPersonId == null },
            jellyfinPersonId = person.jellyfinPersonId,
            name = person.name
        )
    }

    fun selectPerson(
        personId: Int? = null,
        jellyfinPersonId: String? = null,
        name: String
    ) {
        searchJob?.cancel()
        // Set synchronously (not inside the IO coroutine) so the very first frame already knows a
        // filmography is shown — the screen uses that to decide where initial focus goes.
        _uiState.update {
            it.copy(
                activePersonName = name,
                selectedGenre = null,
                personResults = emptyList(),
                isSearching = true
            )
        }
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            val profile = authRepository.activeProfileFlow.first()
            val userId = profile?.jellyfinUserId.orEmpty()
            val items = catalogRepository.loadPersonCredits(
                userId = userId,
                personId = personId,
                jellyfinPersonId = jellyfinPersonId,
                personName = name,
                activeProfile = profile
            )
            ensureActive()
            _uiState.update { it.copy(results = items.distinctBy { r -> r.id }, isSearching = false) }
        }
    }

    fun clearFilter() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(
                query = "",
                selectedGenre = null,
                activePersonName = null,
                results = emptyList(),
                personResults = emptyList(),
                isSearching = false
            )
        }
    }
}


