package com.seerflix.tv.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.SessionPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilePickerUiState(
    val profiles: List<UserProfile> = emptyList(),
    val publicUsers: List<String> = emptyList(),
    val isManageMode: Boolean = false,
    val editingProfile: UserProfile? = null,
    val pinChallengeProfile: UserProfile? = null,
    /** True when the PIN challenge gates "Manage Profiles" editing rather than signing in. */
    val pinChallengeForEdit: Boolean = false,
    /** True when the challenge asks for a parent's PIN (editing a Kids / rating-limited profile). */
    val pinChallengeGuardian: Boolean = false,
    val pinEntryInput: String = "",
    val pinError: String? = null,
    val showAuthDialog: Boolean = false,
    val usePasswordMode: Boolean = false,
    val quickConnectCode: String? = null,
    val quickConnectSecret: String? = null,
    val username: String = "",
    val password: String = "",
    val isAuthenticating: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ProfilePickerViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilePickerUiState())
    val uiState: StateFlow<ProfilePickerUiState> = _uiState.asStateFlow()

    private var qcPollingJob: Job? = null
    private var pinFailedAttempts = 0
    private var pinLockedUntilMs = 0L

    init {
        viewModelScope.launch {
            authRepository.profilesFlow.collect { list ->
                _uiState.update { it.copy(profiles = list) }
            }
        }
    }

    fun toggleManageMode() {
        _uiState.update { it.copy(isManageMode = !it.isManageMode) }
    }

    /** Unrestricted profiles protected by a PIN: their PIN unlocks editing restricted profiles. */
    private fun guardians(): List<UserProfile> =
        _uiState.value.profiles.filter { it.hasPin && !it.isRestricted }

    /** Run after a parent PIN unlocks the "Servers" button. */
    private var pendingServersAction: (() -> Unit)? = null

    /**
     * "Servers" shows the Seerr API key and can repoint the whole app, so it needs a parent's PIN
     * whenever an unrestricted profile has one.
     */
    fun openServers(onOpen: () -> Unit) {
        val guardian = guardians().firstOrNull()
        if (guardian == null) {
            onOpen()
            return
        }
        pendingServersAction = onOpen
        _uiState.update {
            it.copy(
                pinChallengeProfile = guardian,
                pinChallengeForEdit = false,
                pinChallengeGuardian = true,
                pinEntryInput = "",
                pinError = null
            )
        }
    }

    fun selectProfile(profile: UserProfile, onSignedIn: () -> Unit) {
        val manage = _uiState.value.isManageMode
        // A Kids / rating-limited profile without its own PIN could otherwise open its own settings
        // and turn Kids Mode off. Editing it needs a parent's PIN whenever a parent profile has one.
        if (manage && profile.isRestricted && guardians().isNotEmpty()) {
            _uiState.update {
                it.copy(
                    pinChallengeProfile = profile,
                    pinChallengeForEdit = true,
                    pinChallengeGuardian = true,
                    pinEntryInput = "",
                    pinError = null
                )
            }
            return
        }
        // A PIN-locked profile must also be unlocked before it can be edited, otherwise anyone could
        // open Manage Profiles, hit "Remove PIN" (or turn off Kids Mode) and walk straight in.
        if (profile.hasPin) {
            _uiState.update {
                it.copy(
                    pinChallengeProfile = profile,
                    pinChallengeForEdit = manage,
                    pinEntryInput = "",
                    pinError = null
                )
            }
            return
        }
        if (manage) {
            _uiState.update { it.copy(editingProfile = profile) }
            return
        }
        viewModelScope.launch {
            authRepository.switchProfile(profile.id)
            onSignedIn()
        }
    }

    fun appendPinDigit(digit: String, onSignedIn: () -> Unit) {
        if (isPinLockedOut()) return
        val current = _uiState.value.pinEntryInput
        if (current.length >= 4) return
        val next = current + digit
        _uiState.update { it.copy(pinEntryInput = next, pinError = null) }
        if (next.length == 4) {
            submitPinChallenge(onSignedIn)
        }
    }

    fun backspacePin() {
        val current = _uiState.value.pinEntryInput
        if (current.isEmpty()) return
        _uiState.update { it.copy(pinEntryInput = current.dropLast(1), pinError = null) }
    }

    fun clearPinInput() {
        _uiState.update { it.copy(pinEntryInput = "", pinError = null) }
    }

    fun submitPinChallenge(onSignedIn: () -> Unit) {
        val target = _uiState.value.pinChallengeProfile ?: return
        if (isPinLockedOut()) return
        val pin = _uiState.value.pinEntryInput
        if (pin.length != 4) {
            _uiState.update { it.copy(pinError = "Enter 4 digits") }
            return
        }
        val verified = if (_uiState.value.pinChallengeGuardian) {
            guardians().any { authRepository.verifyProfilePin(it, pin) }
        } else {
            authRepository.verifyProfilePin(target, pin)
        }
        if (verified) {
            pinFailedAttempts = 0
            val serversAction = pendingServersAction
            if (serversAction != null) {
                pendingServersAction = null
                _uiState.update {
                    it.copy(pinChallengeProfile = null, pinChallengeGuardian = false, pinEntryInput = "", pinError = null)
                }
                serversAction()
                return
            }
            val forEdit = _uiState.value.pinChallengeForEdit
            _uiState.update {
                it.copy(
                    pinChallengeProfile = null,
                    pinChallengeForEdit = false,
                    pinChallengeGuardian = false,
                    pinEntryInput = "",
                    pinError = null,
                    editingProfile = if (forEdit) target else it.editingProfile
                )
            }
            if (!forEdit) {
                viewModelScope.launch {
                    authRepository.switchProfile(target.id)
                    onSignedIn()
                }
            }
        } else {
            pinFailedAttempts++
            val error = if (pinFailedAttempts >= MAX_PIN_ATTEMPTS) {
                pinFailedAttempts = 0
                pinLockedUntilMs = System.currentTimeMillis() + PIN_LOCKOUT_MS
                "Too many incorrect attempts. Try again in ${PIN_LOCKOUT_MS / 1000}s."
            } else {
                "Incorrect PIN. Try again."
            }
            _uiState.update { it.copy(pinEntryInput = "", pinError = error) }
        }
    }

    /** Simple brute-force throttle: a 4-digit PIN is otherwise trivially enumerable with a remote. */
    private fun isPinLockedOut(): Boolean {
        val remainingMs = pinLockedUntilMs - System.currentTimeMillis()
        if (remainingMs <= 0L) return false
        val secs = (remainingMs + 999L) / 1000L
        _uiState.update {
            it.copy(pinEntryInput = "", pinError = "Too many incorrect attempts. Try again in ${secs}s.")
        }
        return true
    }

    fun dismissPinChallenge() {
        pendingServersAction = null
        _uiState.update {
            it.copy(
                pinChallengeProfile = null,
                pinChallengeForEdit = false,
                pinChallengeGuardian = false,
                pinEntryInput = "",
                pinError = null
            )
        }
    }

    fun closeProfileSettings() {
        _uiState.update { it.copy(editingProfile = null) }
    }

    fun saveProfilePreferences(
        updated: UserProfile,
        newPin: String,
        clearPin: Boolean
    ) {
        val finalHash = when {
            clearPin -> null
            newPin.length == 4 && newPin.all { it.isDigit() } -> SessionPreferences.hashPin(newPin)
            else -> updated.pinHash
        }
        val toSave = updated.copy(pinHash = finalHash)
        viewModelScope.launch {
            authRepository.updateProfilePreferences(toSave)
            _uiState.update { it.copy(editingProfile = null) }
        }
    }

    fun removeProfile(profileId: String) {
        viewModelScope.launch {
            authRepository.removeProfile(profileId)
            _uiState.update { it.copy(editingProfile = null) }
        }
    }

    fun openAddProfile(onSignedIn: () -> Unit) {
        _uiState.update {
            it.copy(
                showAuthDialog = true,
                usePasswordMode = false,
                errorMessage = null,
                quickConnectCode = null,
                quickConnectSecret = null
            )
        }
        viewModelScope.launch {
            val users = authRepository.fetchPublicUsers().getOrDefault(emptyList())
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (users.isNotEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        publicUsers = users,
                        username = state.username.ifBlank { users.first() }
                    )
                }
            }
        }
        startQuickConnect(onSignedIn)
    }

    fun closeAuthDialog() {
        qcPollingJob?.cancel()
        _uiState.update {
            it.copy(
                showAuthDialog = false,
                isAuthenticating = false,
                errorMessage = null,
                password = "",
                quickConnectCode = null,
                quickConnectSecret = null
            )
        }
    }

    fun toggleAuthMode(usePassword: Boolean, onSignedIn: () -> Unit) {
        if (usePassword) {
            qcPollingJob?.cancel()
            // A cancelled Quick Connect init leaves isAuthenticating=true, which would block the
            // password Sign In button; reset it along with the stale code.
            _uiState.update {
                it.copy(
                    usePasswordMode = true,
                    errorMessage = null,
                    isAuthenticating = false,
                    quickConnectCode = null,
                    quickConnectSecret = null
                )
            }
        } else {
            _uiState.update { it.copy(usePasswordMode = false, errorMessage = null) }
            startQuickConnect(onSignedIn)
        }
    }

    fun updateUsername(value: String) {
        _uiState.update { it.copy(username = value, errorMessage = null) }
    }

    fun updatePassword(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun signInWithPassword(onSignedIn: () -> Unit) {
        if (_uiState.value.isAuthenticating) return
        val u = _uiState.value.username
        val p = _uiState.value.password
        if (u.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter your Jellyfin username") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, errorMessage = null) }
            authRepository.signInWithPassword(u, p)
                .onSuccess {
                    _uiState.update { it.copy(isAuthenticating = false, showAuthDialog = false, password = "") }
                    onSignedIn()
                }
                .onFailure { err ->
                    val raw = err.message.orEmpty()
                    val friendly = when {
                        raw.contains("401") -> "Invalid Jellyfin username or password."
                        raw.contains("403") -> "Sign-in forbidden (HTTP 403). Check account permissions."
                        raw.isNotBlank() -> raw
                        else -> "Sign-in failed"
                    }
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            errorMessage = friendly
                        )
                    }
                }
        }
    }

    fun startQuickConnect(onSignedIn: () -> Unit) {
        qcPollingJob?.cancel()
        qcPollingJob = viewModelScope.launch {
            _uiState.update { it.copy(isAuthenticating = true, errorMessage = null) }
            val initResult = authRepository.initiateQuickConnect()
            // initiateQuickConnect() uses runCatching, so a cancel (dialog closed / switched to
            // password) surfaces as a failure; don't report it as "Quick Connect not enabled".
            ensureActive()
            initResult.onFailure {
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        usePasswordMode = true,
                        errorMessage = "Quick Connect is not enabled on this server — sign in with your password below."
                    )
                }
                return@launch
            }
            val qc = initResult.getOrThrow()
            _uiState.update {
                it.copy(
                    isAuthenticating = false,
                    quickConnectCode = qc.code,
                    quickConnectSecret = qc.secret
                )
            }

            var consecutiveFailures = 0
            while (isActive) {
                delay(2500L)
                val poll = authRepository.pollAndCompleteQuickConnect(qc.secret)
                ensureActive()
                val signedInProfile = poll.getOrNull()
                if (signedInProfile != null) {
                    _uiState.update {
                        it.copy(showAuthDialog = false, quickConnectCode = null, quickConnectSecret = null)
                    }
                    onSignedIn()
                    break
                }
                if (poll.isFailure) {
                    // Expired/invalid secrets fail on every poll; stop instead of hammering the
                    // server every 2.5s for as long as the dialog stays open.
                    consecutiveFailures++
                    if (consecutiveFailures >= MAX_QC_POLL_FAILURES) {
                        _uiState.update {
                            it.copy(
                                quickConnectCode = null,
                                quickConnectSecret = null,
                                errorMessage = "Quick Connect code expired or the server stopped responding — sign in with your password, or switch back to Quick Connect for a new code."
                            )
                        }
                        break
                    }
                } else {
                    consecutiveFailures = 0
                }
            }
        }
    }

    private companion object {
        const val MAX_PIN_ATTEMPTS = 5
        const val PIN_LOCKOUT_MS = 30_000L
        const val MAX_QC_POLL_FAILURES = 6
    }
}

