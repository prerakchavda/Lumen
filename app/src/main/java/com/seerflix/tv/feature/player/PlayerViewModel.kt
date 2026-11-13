package com.seerflix.tv.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlayerUiState(
    val session: PlaybackSessionData? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val playbackManager: JellyfinPlaybackManager
) : ViewModel() {

    private val initialItemId: String = savedStateHandle.get<String>("itemId").orEmpty()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadItem(initialItemId)
    }

    fun loadItem(itemId: String) {
        if (itemId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, errorMessage = "Invalid media item ID") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val userId = authRepository.activeProfileFlow.first()?.jellyfinUserId
            if (userId.isNullOrBlank()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "No active Jellyfin profile") }
                return@launch
            }
            playbackManager.preparePlayback(userId, itemId)
                .onSuccess { session ->
                    _uiState.update { it.copy(session = session, isLoading = false) }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isLoading = false, errorMessage = err.message ?: "Playback initialization failed")
                    }
                }
        }
    }

    fun reportStart(positionMs: Long) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            playbackManager.reportStart(session, positionMs * 10_000L)
        }
    }

    fun reportProgress(positionMs: Long, isPaused: Boolean) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            playbackManager.reportProgress(session, positionMs * 10_000L, isPaused)
        }
    }

    fun reportStopped(positionMs: Long) {
        val session = _uiState.value.session ?: return
        viewModelScope.launch {
            playbackManager.reportStopped(session, positionMs * 10_000L)
        }
    }
}
