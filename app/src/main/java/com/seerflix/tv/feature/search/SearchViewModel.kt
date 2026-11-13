package com.seerflix.tv.feature.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.UnifiedMediaItem
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

data class SearchUiState(
    val query: String = "",
    val results: List<UnifiedMediaItem> = emptyList(),
    val personResults: List<PersonSearchResult> = emptyList(),
    val trendingItems: List<UnifiedMediaItem> = emptyList(),
    val selectedGenre: GenreFilterOption? = null,
    val activePersonName: String? = null,
    val isSearching: Boolean = false,
    val isLoadingSuggestions: Boolean = false
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository
) : ViewModel() {

    private val initialQuery: String = savedStateHandle.get<String>("query").orEmpty()
    private val initialPersonId: Int? = savedStateHandle.get<String>("personId")?.toIntOrNull()?.takeIf { it > 0 }
    private val initialPersonName: String? = savedStateHandle.get<String>("personName")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(SearchUiState(query = initialQuery))
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadTrendingSuggestions()
        when {
            initialPersonId != null -> selectPerson(initialPersonId, initialPersonName ?: initialQuery.ifBlank { "Filmography" })
            initialQuery.trim().length >= 2 -> onQueryChange(initialQuery)
        }
    }

    private fun loadTrendingSuggestions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingSuggestions = true) }
            val userId = authRepository.activeProfileFlow.first()?.jellyfinUserId.orEmpty()
            val trending = catalogRepository.loadTrendingTitles(userId)
            _uiState.update { it.copy(trendingItems = trending, isLoadingSuggestions = false) }
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
        searchJob = viewModelScope.launch {
            delay(350L)
            _uiState.update { it.copy(isSearching = true) }
            val userId = authRepository.activeProfileFlow.first()?.jellyfinUserId.orEmpty()
            val (mediaResults, people) = catalogRepository.searchUnifiedWithPeople(userId, newQuery.trim())
            _uiState.update {
                it.copy(
                    results = mediaResults,
                    personResults = people,
                    isSearching = false
                )
            }
        }
    }

    fun selectGenre(genre: GenreFilterOption) {
        if (_uiState.value.selectedGenre?.id == genre.id || genre.id == "all") {
            clearFilter()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    query = "",
                    selectedGenre = genre,
                    activePersonName = null,
                    personResults = emptyList(),
                    isSearching = true
                )
            }
            val userId = authRepository.activeProfileFlow.first()?.jellyfinUserId.orEmpty()
            val items = catalogRepository.loadByGenre(userId, genre)
            _uiState.update { it.copy(results = items, isSearching = false) }
        }
    }

    fun selectPerson(person: PersonSearchResult) {
        selectPerson(person.id, person.name)
    }

    fun selectPerson(personId: Int, name: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    activePersonName = name,
                    selectedGenre = null,
                    personResults = emptyList(),
                    isSearching = true
                )
            }
            val userId = authRepository.activeProfileFlow.first()?.jellyfinUserId.orEmpty()
            val items = catalogRepository.loadPersonCredits(userId, personId)
            _uiState.update { it.copy(results = items, isSearching = false) }
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

