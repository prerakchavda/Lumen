package com.seerflix.tv.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val jellyfinUrl: String = "http://192.168.1.100:8096",
    val seerrUrl: String = "http://192.168.1.100:5055",
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val cfg = authRepository.serverConfigFlow.first()
            if (cfg.jellyfinUrl.isNotBlank() || cfg.seerrUrl.isNotBlank()) {
                _uiState.update {
                    it.copy(
                        jellyfinUrl = cfg.jellyfinUrl.ifBlank { it.jellyfinUrl },
                        seerrUrl = cfg.seerrUrl.ifBlank { it.seerrUrl }
                    )
                }
            }
        }
    }

    fun updateJellyfinUrl(value: String) {
        _uiState.update { it.copy(jellyfinUrl = value, errorMessage = null) }
    }

    fun updateSeerrUrl(value: String) {
        _uiState.update { it.copy(seerrUrl = value, errorMessage = null) }
    }

    fun connectAndContinue(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.jellyfinUrl.isBlank() || state.seerrUrl.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Please enter both Jellyfin and Seerr URLs.") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, errorMessage = null, statusMessage = "Testing server connections...") }
            val result = authRepository.testAndSaveServers(state.jellyfinUrl, state.seerrUrl)
            result.onSuccess { (jfName, seerrVer) ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        statusMessage = "Connected to $jfName & Seerr ($seerrVer)"
                    )
                }
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        statusMessage = null,
                        errorMessage = "Connection failed: ${err.message ?: "Check URLs and network"}"
                    )
                }
            }
        }
    }
}
