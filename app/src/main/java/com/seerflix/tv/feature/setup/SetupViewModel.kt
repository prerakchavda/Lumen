package com.seerflix.tv.feature.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AppUpdateChecker
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.model.AppUpdateInfo
import com.seerflix.tv.core.model.DiscoveredServer
import com.seerflix.tv.core.network.CompanionFormResult
import com.seerflix.tv.core.network.JellyfinDiscoveryClient
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
    val jellyfinUrl: String = "http://192.168.1.100:8096",
    val jellyfinLocalUrl: String = "",
    val seerrUrl: String = "http://192.168.1.100:5055",
    val seerrLocalUrl: String = "",
    val trustSelfSignedCerts: Boolean = false,
    val jellyfinOnlyMode: Boolean = false,
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val isScanning: Boolean = false,
    val companionUrl: String? = null,
    val updateInfo: AppUpdateInfo? = null,
    val isTesting: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val discoveryClient: JellyfinDiscoveryClient,
    private val appUpdateChecker: AppUpdateChecker
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

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
                trustSelfSigned = cur.trustSelfSignedCerts
            )
        ) { submitted ->
            val onlyJf = submitted.seerrUrl.isBlank() && submitted.seerrLocalUrl.isBlank()
            _uiState.update {
                it.copy(
                    jellyfinUrl = submitted.jellyfinUrl.ifBlank { submitted.jellyfinLocalUrl.ifBlank { it.jellyfinUrl } },
                    jellyfinLocalUrl = submitted.jellyfinLocalUrl,
                    seerrUrl = submitted.seerrUrl,
                    seerrLocalUrl = submitted.seerrLocalUrl,
                    jellyfinOnlyMode = onlyJf,
                    trustSelfSignedCerts = submitted.trustSelfSigned,
                    errorMessage = null,
                    statusMessage = "Received settings from phone companion! Ready to connect."
                )
            }
        }
        _uiState.update { it.copy(companionUrl = url) }
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

    fun toggleTrustSelfSigned() {
        _uiState.update { it.copy(trustSelfSignedCerts = !it.trustSelfSignedCerts, errorMessage = null) }
    }

    fun toggleJellyfinOnlyMode() {
        _uiState.update {
            val next = !it.jellyfinOnlyMode
            it.copy(
                jellyfinOnlyMode = next,
                errorMessage = null,
                statusMessage = if (next) "Jellyfin-Only Mode enabled (Seerr requests optional)" else null
            )
        }
    }

    fun scanNetwork() {
        if (_uiState.value.isScanning) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val found = runCatching { discoveryClient.discoverServers() }.getOrDefault(emptyList())
            _uiState.update { state ->
                val msg = when {
                    found.isNotEmpty() -> "Found ${found.size} Jellyfin server(s) on your network."
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
                    jellyfinUrl = if (state.jellyfinUrl == "http://192.168.1.100:8096" && found.isNotEmpty()) {
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
                statusMessage = "Selected ${server.name} (${server.address})"
            )
        }
    }

    fun connectAndContinue(onSuccess: () -> Unit) {
        val state = _uiState.value
        val hasJf = state.jellyfinUrl.isNotBlank() || state.jellyfinLocalUrl.isNotBlank()
        val hasSeerr = state.seerrUrl.isNotBlank() || state.seerrLocalUrl.isNotBlank()

        if (!hasJf) {
            _uiState.update { it.copy(errorMessage = "Please enter a Jellyfin URL or pick a discovered server.") }
            return
        }
        if (!state.jellyfinOnlyMode && !hasSeerr) {
            _uiState.update { it.copy(errorMessage = "Please enter a Seerr URL, or enable Jellyfin-Only Mode.") }
            return
        }

        val effectiveJfUrl = state.jellyfinUrl.ifBlank { state.jellyfinLocalUrl }
        val effectiveSeerrUrl = if (state.jellyfinOnlyMode) "" else state.seerrUrl.ifBlank { state.seerrLocalUrl }
        val effectiveSeerrLocal = if (state.jellyfinOnlyMode) "" else state.seerrLocalUrl

        viewModelScope.launch {
            _uiState.update { it.copy(isTesting = true, errorMessage = null, statusMessage = "Testing server connections...") }
            val result = authRepository.testAndSaveServers(
                jellyfinUrl = effectiveJfUrl,
                seerrUrl = effectiveSeerrUrl,
                jellyfinLocalUrl = state.jellyfinLocalUrl,
                seerrLocalUrl = effectiveSeerrLocal,
                trustSelfSignedCerts = state.trustSelfSignedCerts
            )
            result.onSuccess { (jfName, seerrVer) ->
                _uiState.update {
                    it.copy(
                        isTesting = false,
                        statusMessage = "Connected to $jfName ($seerrVer)"
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

