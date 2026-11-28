package com.seerflix.tv.feature.servers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.ServerAccountRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.DiscoveredServer
import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.network.CompanionFormResult
import com.seerflix.tv.core.network.JellyfinDiscoveryClient
import com.seerflix.tv.core.network.SetupCompanionServer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AddServerStep {
    /** Where is the server: address fields, LAN discovery, phone companion. */
    ADDRESS,
    /** Username (the profile's, pre-filled) + password. */
    SIGN_IN,
    /** A Quick Connect code to approve on the other server. */
    QUICK_CONNECT,
    /** Signed in; pulling the library into the merged index. */
    SYNCING,
    DONE
}

data class AddServerUiState(
    val step: AddServerStep = AddServerStep.ADDRESS,
    /** True when signing in again to a server that is already configured. */
    val isReSignIn: Boolean = false,
    val remoteUrl: String = "",
    val localUrl: String = "",
    val trustSelfSignedCerts: Boolean = false,
    val discoveredServers: List<DiscoveredServer> = emptyList(),
    val isScanning: Boolean = false,
    val companionUrl: String? = null,
    val server: JellyfinServer? = null,
    val username: String = "",
    val password: String = "",
    val quickConnectCode: String? = null,
    val isBusy: Boolean = false,
    val errorMessage: String? = null,
    /** Bumped when the phone companion pushed an address, so the UI can move focus to Continue. */
    val companionUpdateCount: Int = 0
)

/**
 * "Add another server": find it (LAN discovery, typed address or the phone companion), then sign
 * in. Sign-in starts from the active profile's username, since a friend usually creates the
 * account with the same name; a different username or Quick Connect are one step away.
 */
@HiltViewModel
class AddServerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val authRepository: AuthRepository,
    private val accounts: ServerAccountRepository,
    private val discoveryClient: JellyfinDiscoveryClient
) : ViewModel() {

    private val reSignInServerId: String? =
        savedStateHandle.get<String>("serverId")?.takeIf { it.isNotBlank() }

    private val _uiState = MutableStateFlow(AddServerUiState())
    val uiState: StateFlow<AddServerUiState> = _uiState.asStateFlow()

    private val companionServer = SetupCompanionServer()
    private var quickConnectJob: Job? = null

    init {
        viewModelScope.launch {
            val profileName = authRepository.activeProfileFlow.first()?.name.orEmpty()
            val existing = reSignInServerId?.let { accounts.server(it) }
            if (existing != null) {
                _uiState.update {
                    it.copy(
                        step = AddServerStep.SIGN_IN,
                        isReSignIn = true,
                        server = existing,
                        remoteUrl = existing.remoteUrl,
                        localUrl = existing.localUrl,
                        trustSelfSignedCerts = existing.trustSelfSignedCerts,
                        username = profileName
                    )
                }
            } else {
                _uiState.update { it.copy(username = profileName) }
                startCompanion()
                scanNetwork()
            }
        }
    }

    private fun startCompanion() {
        val url = companionServer.start(
            currentValues = CompanionFormResult(),
            jellyfinOnly = true,
            onExpired = { _uiState.update { it.copy(companionUrl = null) } }
        ) { submitted ->
            _uiState.update {
                it.copy(
                    remoteUrl = submitted.jellyfinUrl.ifBlank { submitted.jellyfinLocalUrl },
                    localUrl = if (submitted.jellyfinUrl.isBlank()) "" else submitted.jellyfinLocalUrl,
                    trustSelfSignedCerts = submitted.trustSelfSigned,
                    errorMessage = null,
                    companionUpdateCount = it.companionUpdateCount + 1
                )
            }
        }
        _uiState.update { it.copy(companionUrl = url) }
    }

    fun updateRemoteUrl(value: String) = _uiState.update { it.copy(remoteUrl = value, errorMessage = null) }
    fun updateLocalUrl(value: String) = _uiState.update { it.copy(localUrl = value, errorMessage = null) }
    fun updateUsername(value: String) = _uiState.update { it.copy(username = value, errorMessage = null) }
    fun updatePassword(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }
    fun toggleTrustSelfSigned() =
        _uiState.update { it.copy(trustSelfSignedCerts = !it.trustSelfSignedCerts, errorMessage = null) }

    fun scanNetwork() {
        if (_uiState.value.isScanning) return
        _uiState.update { it.copy(isScanning = true) }
        viewModelScope.launch {
            val found = runCatching { discoveryClient.discoverServers() }.getOrDefault(emptyList())
            _uiState.update { it.copy(isScanning = false, discoveredServers = found) }
        }
    }

    /** A server found on this network: it is reachable by its LAN address only. */
    fun selectDiscoveredServer(server: DiscoveredServer) {
        _uiState.update { it.copy(remoteUrl = server.address, localUrl = "", errorMessage = null) }
    }

    /** ADDRESS -> SIGN_IN once the address answers as a Jellyfin server. */
    fun testAddress() {
        val state = _uiState.value
        if (state.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            accounts.testServer(state.remoteUrl, state.localUrl, state.trustSelfSignedCerts)
                .onSuccess { server ->
                    companionServer.stop()
                    _uiState.update {
                        it.copy(isBusy = false, server = server, step = AddServerStep.SIGN_IN, companionUrl = null)
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            errorMessage = UnifiedCatalogRepository.formatHttpError(err, "Couldn't reach that server")
                        )
                    }
                }
        }
    }

    fun signIn() {
        val state = _uiState.value
        val server = state.server ?: return
        if (state.isBusy) return
        if (state.username.isBlank()) {
            _uiState.update { it.copy(errorMessage = "username") }
            return
        }
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        viewModelScope.launch {
            accounts.signInWithPassword(server, state.username, state.password)
                // The password is only needed for that one request.
                .onSuccess { finishSignIn() }
                .onFailure { err ->
                    val rejected = (err as? retrofit2.HttpException)?.code() == 401
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            password = "",
                            errorMessage = if (rejected) "credentials"
                            else UnifiedCatalogRepository.formatHttpError(err, "Sign-in failed")
                        )
                    }
                }
        }
    }

    fun startQuickConnect() {
        val server = _uiState.value.server ?: return
        if (_uiState.value.isBusy) return
        _uiState.update { it.copy(isBusy = true, errorMessage = null) }
        quickConnectJob?.cancel()
        quickConnectJob = viewModelScope.launch {
            val started = accounts.startQuickConnect(server)
            val quickConnect = started.getOrNull()
            if (quickConnect == null) {
                _uiState.update { it.copy(isBusy = false, errorMessage = "quickconnect") }
                return@launch
            }
            _uiState.update {
                it.copy(isBusy = false, step = AddServerStep.QUICK_CONNECT, quickConnectCode = quickConnect.code)
            }
            val deadline = System.currentTimeMillis() + QUICK_CONNECT_WINDOW_MS
            while (isActive && System.currentTimeMillis() < deadline) {
                delay(QUICK_CONNECT_POLL_MS)
                val approved = accounts.completeQuickConnect(server, quickConnect.secret).getOrDefault(false)
                if (approved) {
                    finishSignIn()
                    return@launch
                }
            }
            _uiState.update {
                it.copy(step = AddServerStep.SIGN_IN, quickConnectCode = null, errorMessage = "quickconnect_expired")
            }
        }
    }

    /** Back from the Quick Connect code to the password form. */
    fun cancelQuickConnect() {
        quickConnectJob?.cancel()
        _uiState.update { it.copy(step = AddServerStep.SIGN_IN, quickConnectCode = null, isBusy = false) }
    }

    /** Back from sign-in to the address (not offered when signing in again). */
    fun backToAddress() {
        quickConnectJob?.cancel()
        accounts.cancelDraft()
        _uiState.update {
            it.copy(step = AddServerStep.ADDRESS, server = null, password = "", errorMessage = null, isBusy = false)
        }
        if (companionServer.companionUrl == null) startCompanion()
    }

    private suspend fun finishSignIn() {
        _uiState.update {
            it.copy(step = AddServerStep.SYNCING, isBusy = false, password = "", quickConnectCode = null, errorMessage = null)
        }
        accounts.syncLibrary()
        _uiState.update { it.copy(step = AddServerStep.DONE) }
    }

    override fun onCleared() {
        companionServer.stop()
        // A draft that was never signed in to must not linger in the network layer.
        if (_uiState.value.step != AddServerStep.DONE && _uiState.value.step != AddServerStep.SYNCING) {
            accounts.cancelDraft()
        }
        super.onCleared()
    }

    private companion object {
        const val QUICK_CONNECT_POLL_MS = 2_500L
        const val QUICK_CONNECT_WINDOW_MS = 5 * 60 * 1000L
    }
}
