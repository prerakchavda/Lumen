package com.seerflix.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.BuildConfig
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.diagnostics.DiagnosticsRepository
import com.seerflix.tv.core.diagnostics.LogUploadResult
import com.seerflix.tv.core.model.FrameRateMatchMode
import com.seerflix.tv.core.model.SubtitleEdgeStyle
import com.seerflix.tv.core.model.SubtitlePosition
import com.seerflix.tv.core.model.SubtitleTextColor
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.JellyfinSocketClient
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.network.SocketState
import com.seerflix.tv.core.player.DeviceCapabilityProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class SettingsUiState(
    val profile: UserProfile? = null,
    val matchDeviceCapabilities: Boolean = true,
    val capabilitySummary: String = "",
    val allowRemoteControl: Boolean = true,
    val socketState: SocketState = SocketState.DISCONNECTED,
    val logUploadInProgress: Boolean = false,
    val logUploadMessage: String? = null,
    val hasPendingCrash: Boolean = false,
    val appVersion: String = BuildConfig.VERSION_NAME
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: SessionPreferences,
    private val capabilityProvider: DeviceCapabilityProvider,
    private val diagnostics: DiagnosticsRepository,
    private val socketClient: JellyfinSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.activeProfileFlow.collect { p -> _uiState.update { it.copy(profile = p) } }
        }
        viewModelScope.launch {
            prefs.matchDeviceCapabilitiesFlow.collect { v -> _uiState.update { it.copy(matchDeviceCapabilities = v) } }
        }
        viewModelScope.launch {
            prefs.allowRemoteControlFlow.collect { v -> _uiState.update { it.copy(allowRemoteControl = v) } }
        }
        viewModelScope.launch {
            socketClient.state.collect { v -> _uiState.update { it.copy(socketState = v) } }
        }
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) { runCatching { capabilityProvider.current().summary() }.getOrDefault("") }
            val crash = withContext(Dispatchers.IO) { diagnostics.pendingCrash() != null }
            _uiState.update { it.copy(capabilitySummary = summary, hasPendingCrash = crash) }
        }
    }

    fun setMatchDeviceCapabilities(enabled: Boolean) {
        viewModelScope.launch { prefs.setMatchDeviceCapabilities(enabled) }
    }

    fun setAllowRemoteControl(enabled: Boolean) {
        viewModelScope.launch {
            prefs.setAllowRemoteControl(enabled)
            // Reconnect so the server learns the new capabilities (a fresh session without them
            // drops this TV from other apps' "Play on" lists).
            socketClient.stop()
            socketClient.start()
        }
    }

    fun setAudioPassthrough(enabled: Boolean) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(audioPassthroughEnabled = enabled)
    }

    fun setStereoDownmix(enabled: Boolean) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(stereoDownmixEnabled = enabled)
    }

    fun setFrameRateMatch(mode: FrameRateMatchMode) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(frameRateMatchMode = mode)
    }

    fun setAutoSkipIntro(enabled: Boolean) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(autoSkipIntro = enabled)
    }

    fun setAutoSkipRecap(enabled: Boolean) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(autoSkipRecap = enabled)
    }

    fun setAutoplayNext(enabled: Boolean) = viewModelScope.launch {
        val profile = _uiState.value.profile ?: return@launch
        authRepository.updateProfilePreferences(profile.copy(autoplayNextEpisode = enabled))
    }

    fun setSubtitleSize(scale: Float) = viewModelScope.launch {
        val profile = _uiState.value.profile ?: return@launch
        authRepository.updateSubtitleAppearance(scale, profile.subtitleBackgroundAlpha)
    }

    fun setSubtitleBackground(alpha: Float) = viewModelScope.launch {
        val profile = _uiState.value.profile ?: return@launch
        authRepository.updateSubtitleAppearance(profile.subtitleFontScale, alpha)
    }

    fun setSubtitleColor(color: SubtitleTextColor) = viewModelScope.launch {
        authRepository.updateSubtitleStyle(textColor = color)
    }

    fun setSubtitleEdge(edge: SubtitleEdgeStyle) = viewModelScope.launch {
        authRepository.updateSubtitleStyle(edgeStyle = edge)
    }

    fun setSubtitlePosition(position: SubtitlePosition) = viewModelScope.launch {
        authRepository.updateSubtitleStyle(position = position)
    }

    fun sendLogs() {
        if (_uiState.value.logUploadInProgress) return
        _uiState.update { it.copy(logUploadInProgress = true, logUploadMessage = null) }
        viewModelScope.launch {
            val result = diagnostics.upload()
            _uiState.update {
                it.copy(
                    logUploadInProgress = false,
                    hasPendingCrash = diagnostics.pendingCrash() != null,
                    logUploadMessage = when (result) {
                        is LogUploadResult.Uploaded -> "uploaded:${result.fileName}"
                        LogUploadResult.DisabledOnServer -> "disabled"
                        is LogUploadResult.Failed -> "failed:${result.message}"
                    }
                )
            }
        }
    }
}
