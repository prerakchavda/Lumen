package com.seerflix.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.BuildConfig
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.ServerAccountRepository
import com.seerflix.tv.core.model.ServerStatus
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
    val softwareAudioDecoding: Boolean = true,
    val capabilitySummary: String = "",
    val allowRemoteControl: Boolean = true,
    val socketState: SocketState = SocketState.DISCONNECTED,
    val logUploadInProgress: Boolean = false,
    val logUploadMessage: String? = null,
    val hasPendingCrash: Boolean = false,
    val appVersion: String = BuildConfig.VERSION_NAME,
    /** The home Jellyfin server (for configuring its optional streaming bitrate cap). */
    val homeServer: com.seerflix.tv.core.model.JellyfinServer? = null,
    /** The extra Jellyfin servers merged into the library, with their state for this profile. */
    val servers: List<ServerStatus> = emptyList(),
    /** Server whose "Remove" was pressed once; a second press removes it. */
    val pendingRemoveServerId: String? = null,
    /** Parent PIN prompt shown before server settings open (null = no prompt). */
    val serverPinInput: String? = null,
    val serverPinError: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val prefs: SessionPreferences,
    private val capabilityProvider: DeviceCapabilityProvider,
    private val diagnostics: DiagnosticsRepository,
    private val socketClient: JellyfinSocketClient,
    private val serverAccounts: ServerAccountRepository,
    private val pinLimiter: com.seerflix.tv.core.data.PinAttemptLimiter
) : ViewModel() {

    private var profiles: List<UserProfile> = emptyList()
    private var serverAccessUnlocked = false
    private var pendingServerAction: (() -> Unit)? = null

    private val strings: com.seerflix.tv.core.i18n.UiStrings
        get() = com.seerflix.tv.core.i18n.stringsForLanguage(prefs.cachedUiLanguage)

    /** Unrestricted profiles protected by a PIN: one of their PINs unlocks server settings. */
    private fun guardians(): List<UserProfile> = profiles.filter { it.hasPin && !it.isRestricted }

    /**
     * Server settings show the Seerr API key and can repoint, add or remove servers. The profile
     * picker's "Servers" button asks for a parent PIN; Settings must too, or any unrestricted
     * profile without a PIN (a teenager's) could get there from Home.
     */
    fun requestServerAccess(action: () -> Unit) {
        val active = _uiState.value.profile
        val guardians = guardians()
        if (serverAccessUnlocked || guardians.isEmpty() || guardians.any { it.id == active?.id }) {
            action()
            return
        }
        pendingServerAction = action
        _uiState.update { it.copy(serverPinInput = "", serverPinError = lockoutMessage()) }
    }

    fun appendServerPinDigit(digit: String) {
        val current = _uiState.value.serverPinInput ?: return
        lockoutMessage()?.let { msg -> _uiState.update { it.copy(serverPinInput = "", serverPinError = msg) }; return }
        if (current.length >= 4) return
        val next = current + digit
        _uiState.update { it.copy(serverPinInput = next, serverPinError = null) }
        if (next.length < 4) return
        if (guardians().any { authRepository.verifyProfilePin(it, next) }) {
            pinLimiter.recordSuccess()
            serverAccessUnlocked = true
            val action = pendingServerAction
            pendingServerAction = null
            _uiState.update { it.copy(serverPinInput = null, serverPinError = null) }
            action?.invoke()
        } else {
            val error = if (pinLimiter.recordFailure()) {
                strings.profilePinLockedOut.format(com.seerflix.tv.core.data.PinAttemptLimiter.LOCKOUT_MS / 1000)
            } else strings.profilePinIncorrect
            _uiState.update { it.copy(serverPinInput = "", serverPinError = error) }
        }
    }

    fun backspaceServerPin() {
        val current = _uiState.value.serverPinInput ?: return
        if (current.isNotEmpty()) _uiState.update { it.copy(serverPinInput = current.dropLast(1), serverPinError = null) }
    }

    fun clearServerPin() {
        if (_uiState.value.serverPinInput != null) _uiState.update { it.copy(serverPinInput = "", serverPinError = null) }
    }

    fun dismissServerPin() {
        pendingServerAction = null
        _uiState.update { it.copy(serverPinInput = null, serverPinError = null) }
    }

    private fun lockoutMessage(): String? {
        val remaining = pinLimiter.remainingLockoutMs()
        if (remaining <= 0L) return null
        return strings.profilePinLockedOut.format((remaining + 999L) / 1000L)
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.activeProfileFlow.collect { p -> _uiState.update { it.copy(profile = p) } }
        }
        viewModelScope.launch {
            authRepository.profilesFlow.collect { profiles = it }
        }
        viewModelScope.launch {
            prefs.matchDeviceCapabilitiesFlow.collect { v -> _uiState.update { it.copy(matchDeviceCapabilities = v) } }
        }
        viewModelScope.launch {
            prefs.softwareAudioDecodingFlow.collect { v -> _uiState.update { it.copy(softwareAudioDecoding = v) } }
        }
        viewModelScope.launch {
            prefs.serversFlow.collect { list ->
                _uiState.update { it.copy(homeServer = list.firstOrNull { s -> s.isHome }) }
            }
        }
        viewModelScope.launch {
            prefs.allowRemoteControlFlow.collect { v -> _uiState.update { it.copy(allowRemoteControl = v) } }
        }
        viewModelScope.launch {
            socketClient.state.collect { v -> _uiState.update { it.copy(socketState = v) } }
        }
        viewModelScope.launch {
            serverAccounts.statusFlow.collect { v -> _uiState.update { it.copy(servers = v) } }
        }
        viewModelScope.launch {
            val summary = withContext(Dispatchers.IO) { runCatching { capabilityProvider.current().summary() }.getOrDefault("") }
            val crash = withContext(Dispatchers.IO) { diagnostics.pendingCrash() != null }
            _uiState.update { it.copy(capabilitySummary = summary, hasPendingCrash = crash) }
        }
    }

    fun setServerBitrateCap(serverId: String, maxBitrateBps: Long?) {
        viewModelScope.launch { serverAccounts.setBitrateCap(serverId, maxBitrateBps) }
    }

    /** First press arms the removal, the second one (on the same server) does it. */
    fun removeServer(serverId: String) {
        if (_uiState.value.pendingRemoveServerId != serverId) {
            _uiState.update { it.copy(pendingRemoveServerId = serverId) }
            return
        }
        _uiState.update { it.copy(pendingRemoveServerId = null) }
        viewModelScope.launch { serverAccounts.removeServer(serverId) }
    }

    fun setMatchDeviceCapabilities(enabled: Boolean) {
        viewModelScope.launch { prefs.setMatchDeviceCapabilities(enabled) }
    }

    fun setSoftwareAudioDecoding(enabled: Boolean) {
        viewModelScope.launch { prefs.setSoftwareAudioDecoding(enabled) }
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

    fun setSubtitleSelectionMode(mode: com.seerflix.tv.core.model.SubtitleSelectionMode) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(subtitleSelectionMode = mode)
    }

    fun setBingeLimitEpisodes(limit: Int) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(bingeLimitEpisodes = limit)
    }

    fun setScreensaverTimeoutMinutes(minutes: Int) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(screensaverTimeoutMinutes = minutes)
    }

    fun setCinemaIntrosEnabled(enabled: Boolean) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(cinemaIntrosEnabled = enabled)
    }

    fun setThemeSongsEnabled(enabled: Boolean) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(themeSongsEnabled = enabled)
    }

    fun setMergeNextUpIntoContinueWatching(enabled: Boolean) = viewModelScope.launch {
        authRepository.updatePlaybackPreferences(mergeNextUpIntoContinueWatching = enabled)
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
