package com.seerflix.tv.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AppUpdateChecker
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
import com.seerflix.tv.core.model.AppUpdateInfo
import com.seerflix.tv.core.model.DiscoveredServer
import com.seerflix.tv.core.network.CompanionFormResult
import com.seerflix.tv.core.network.JellyfinDiscoveryClient
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.network.SetupCompanionServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val jellyfinUrl: String = "",
    val jellyfinLocalUrl: String = "",
    val seerrUrl: String = "",
    val seerrLocalUrl: String = "",
    val seerrApiKey: String = "",
    val trustSelfSignedCerts: Boolean = false,
    val jellyfinOnlyMode: Boolean = false,
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val isScanning: Boolean = false,
    val companionUrl: String? = null,
    val updateInfo: AppUpdateInfo? = null,
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    /** Bumped each time the phone companion pushes values so the UI can move focus to Connect. */
    val companionUpdateCount: Int = 0
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val discoveryClient: JellyfinDiscoveryClient,
    private val appUpdateChecker: AppUpdateChecker,
    private val sessionPreferences: SessionPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    /** UI strings for the active profile's language, read when a message is produced. */
    private val strings: UiStrings
        get() = stringsForLanguage(sessionPreferences.cachedUiLanguage)

    private val companionServer = SetupCompanionServer()

    init {
        viewModelScope.launch {
            val cfg = authRepository.serverConfigFlow.first()
            if (cfg.isJellyfinConfigured || cfg.isSeerrConfigured) {
                _uiState.update {
                    it.copy(
                        jellyfinUrl = cfg.jellyfinUrl.ifBlank { it.jellyfinUrl },
                        jellyfinLocalUrl = cfg.jellyfinLocalUrl,
                        seerrUrl = cfg.seerrUrl,
                        seerrLocalUrl = cfg.seerrLocalUrl,
                        seerrApiKey = cfg.seerrApiKey,
                        trustSelfSignedCerts = cfg.trustSelfSignedCerts,
                        jellyfinOnlyMode = cfg.isJellyfinConfigured && !cfg.isSeerrConfigured
                    )
                }
            }
            startCompanion()
        }
        scanNetwork()
        viewModelScope.launch {
            val update = runCatching { appUpdateChecker.checkForUpdate() }.getOrNull()
            if (update != null) {
                _uiState.update { it.copy(updateInfo = update) }
            }
        }
    }

    private fun startCompanion() {
        val cur = _uiState.value
        val url = companionServer.start(
            currentValues = CompanionFormResult(
                jellyfinUrl = cur.jellyfinUrl,
                jellyfinLocalUrl = cur.jellyfinLocalUrl,
                seerrUrl = if (cur.jellyfinOnlyMode) "" else cur.seerrUrl,
                seerrLocalUrl = if (cur.jellyfinOnlyMode) "" else cur.seerrLocalUrl,
                trustSelfSigned = cur.trustSelfSignedCerts,
                // Never served back over plain HTTP; a blank submit keeps the current key.
                seerrApiKey = ""
            )
        ) { submitted ->
            val onlyJf = submitted.seerrUrl.isBlank() && submitted.seerrLocalUrl.isBlank()
            _uiState.update {
                it.copy(
                    jellyfinUrl = submitted.jellyfinUrl.ifBlank { submitted.jellyfinLocalUrl.ifBlank { it.jellyfinUrl } },
                    jellyfinLocalUrl = submitted.jellyfinLocalUrl,
                    seerrUrl = submitted.seerrUrl,
                    seerrLocalUrl = submitted.seerrLocalUrl,
                    seerrApiKey = submitted.seerrApiKey.ifBlank { it.seerrApiKey },
                    jellyfinOnlyMode = onlyJf,
                    trustSelfSignedCerts = submitted.trustSelfSigned,
                    errorMessage = null,
                    statusMessage = strings.setupCompanionReceived,
                    companionUpdateCount = it.companionUpdateCount + 1
                )
            }
        }
        _uiState.update { it.copy(companionUrl = url) }
    }

    // The phone form stays reachable only while this screen is in the foreground.
    private var companionPaused = false

    fun onScreenStopped() {
        if (companionServer.companionUrl == null) return
        companionServer.stop()
        companionPaused = true
        _uiState.update { it.copy(companionUrl = null) }
    }

    fun onScreenStarted() {
        if (!companionPaused) return
        companionPaused = false
        startCompanion()
    }

    override fun onCleared() {
        companionServer.stop()
        super.onCleared()
    }

    fun updateJellyfinUrl(value: String) {
        _uiState.update { it.copy(jellyfinUrl = value, errorMessage = null) }
    }

    fun updateJellyfinLocalUrl(value: String) {
        _uiState.update { it.copy(jellyfinLocalUrl = value, errorMessage = null) }
    }

    fun updateSeerrUrl(value: String) {
        _uiState.update { it.copy(seerrUrl = value, errorMessage = null) }
    }

    fun updateSeerrLocalUrl(value: String) {
        _uiState.update { it.copy(seerrLocalUrl = value, errorMessage = null) }
    }

    fun updateSeerrApiKey(value: String) {
        _uiState.update { it.copy(seerrApiKey = value, errorMessage = null) }
    }

    fun toggleTrustSelfSigned() {
        _uiState.update { it.copy(trustSelfSignedCerts = !it.trustSelfSignedCerts, errorMessage = null) }
    }

    fun toggleJellyfinOnlyMode() {
        _uiState.update {
            val next = !it.jellyfinOnlyMode
            it.copy(
                jellyfinOnlyMode = next,
                errorMessage = null,
                statusMessage = if (next) strings.setupJellyfinOnlyEnabled else null
            )
        }
    }

    fun scanNetwork() {
        if (_uiState.value.isScanning) return
        // Set synchronously so a double-press can't start two concurrent UDP scans.
        _uiState.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            val found = runCatching { discoveryClient.discoverServers() }.getOrDefault(emptyList())
            _uiState.update { state ->
                val msg = when {
                    found.isNotEmpty() -> strings.setupFoundServers.format(found.size)
                    state.statusMessage != null -> state.statusMessage
                    else -> null
                }
                state.copy(
                    isScanning = false,
                    discoveredServers = found,
                    jellyfinLocalUrl = if (state.jellyfinLocalUrl.isBlank() && found.isNotEmpty()) {
                        found.first().address
                    } else {
                        state.jellyfinLocalUrl
                    },
                    jellyfinUrl = if (state.jellyfinUrl.isBlank() && found.isNotEmpty()) {
                        found.first().address
                    } else {
                        state.jellyfinUrl
                    },
                    statusMessage = msg
                )
            }
        }
    }

    fun selectDiscoveredServer(server: DiscoveredServer) {
        _uiState.update {
            it.copy(
                jellyfinUrl = server.address,
                jellyfinLocalUrl = server.address,
                errorMessage = null,
                statusMessage = strings.setupSelectedServer.format(server.name, server.address)
            )
        }
    }

    fun connectAndContinue(onSuccess: () -> Unit) {
        val state = _uiState.value
        if (state.isTesting) return
        val hasJf = state.jellyfinUrl.isNotBlank() || state.jellyfinLocalUrl.isNotBlank()
        val hasSeerr = state.seerrUrl.isNotBlank() || state.seerrLocalUrl.isNotBlank()

        if (!hasJf) {
            _uiState.update { it.copy(errorMessage = strings.setupNeedJellyfinUrl) }
            return
        }
        if (!state.jellyfinOnlyMode && !hasSeerr) {
            _uiState.update { it.copy(errorMessage = strings.setupNeedSeerrUrl) }
            return
        }

        val effectiveJfUrl = state.jellyfinUrl.ifBlank { state.jellyfinLocalUrl }
        val effectiveSeerrUrl = if (state.jellyfinOnlyMode) "" else state.seerrUrl.ifBlank { state.seerrLocalUrl }
        val effectiveSeerrLocal = if (state.jellyfinOnlyMode) "" else state.seerrLocalUrl
        val effectiveSeerrApiKey = if (state.jellyfinOnlyMode) "" else state.seerrApiKey

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, errorMessage = null, statusMessage = strings.setupTesting) }
            val result = authRepository.testAndSaveServers(
                jellyfinUrl = effectiveJfUrl,
                seerrUrl = effectiveSeerrUrl,
                jellyfinLocalUrl = state.jellyfinLocalUrl,
                seerrLocalUrl = effectiveSeerrLocal,
                trustSelfSignedCerts = state.trustSelfSignedCerts,
                seerrApiKey = effectiveSeerrApiKey
            )
            result.onSuccess { (jfName, seerrVer) ->
                val s = strings
                // In Jellyfin-only mode the repository reports an English "Not Configured" label.
                val seerrLabel = if (state.jellyfinOnlyMode) s.homeJellyfinOnlyMode else seerrVer
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        statusMessage = s.setupConnected.format(jfName, seerrLabel),
                        companionUrl = null
                    )
                }
                // Setup is done: stop the phone-companion HTTP server now rather than waiting
                // for the back stack entry (and this ViewModel) to be cleared.
                companionServer.stop()
                onSuccess()
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        statusMessage = null,
                        errorMessage = strings.setupConnectionFailed.format(err.message ?: strings.setupCheckUrls)
                    )
                }
            }
        }
    }
}

