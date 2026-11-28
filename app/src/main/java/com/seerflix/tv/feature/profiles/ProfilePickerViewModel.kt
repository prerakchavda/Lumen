package com.seerflix.tv.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.PinAttemptThrottle
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
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
    private val authRepository: AuthRepository,
    private val sessionPreferences: SessionPreferences,
    private val pinThrottle: PinAttemptThrottle
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilePickerUiState())
    val uiState: StateFlow<ProfilePickerUiState> = _uiState.asStateFlow()

    /** UI strings for the active profile's language, read when a message is produced. */
    private val strings: UiStrings
        get() = stringsForLanguage(sessionPreferences.cachedUiLanguage)

    private var qcPollingJob: Job? = null

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
        if (pinCheckJob?.isActive == true) return
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

    private var pinCheckJob: Job? = null

    fun submitPinChallenge(onSignedIn: () -> Unit) {
        val target = _uiState.value.pinChallengeProfile ?: return
        if (pinCheckJob?.isActive == true) return
        val pin = _uiState.value.pinEntryInput
        if (pin.length != 4) {
            _uiState.update { it.copy(pinError = strings.profilePinInvalid) }
            return
        }
        val guardianChallenge = _uiState.value.pinChallengeGuardian
        // The profiles whose PIN this guess is tested against; each is throttled on its own.
        val candidates = if (guardianChallenge) guardians() else listOf(target)
        val candidateIds = candidates.map { it.id }
        pinCheckJob = viewModelScope.launch {
            val lockedMs = pinThrottle.remainingLockoutMs(candidateIds)
            if (lockedMs > 0L) {
                showPinLockout(lockedMs)
                return@launch
            }
            val matched = candidates.firstOrNull { authRepository.verifyProfilePin(it, pin) }
            if (matched == null) {
                val lockoutMs = pinThrottle.recordFailure(candidateIds)
                if (lockoutMs > 0L) {
                    showPinLockout(lockoutMs)
                } else {
                    _uiState.update { it.copy(pinEntryInput = "", pinError = strings.profilePinIncorrect) }
                }
                return@launch
            }
            pinThrottle.recordSuccess(matched.id)
            // The challenge may have been dismissed while the throttle was consulted.
            if (_uiState.value.pinChallengeProfile?.id != target.id) return@launch
            val serversAction = pendingServersAction
            if (serversAction != null) {
                pendingServersAction = null
                _uiState.update {
                    it.copy(pinChallengeProfile = null, pinChallengeGuardian = false, pinEntryInput = "", pinError = null)
                }
                serversAction()
                return@launch
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
                authRepository.switchProfile(target.id)
                onSignedIn()
            }
        }
    }

    private fun showPinLockout(remainingMs: Long) {
        val secs = (remainingMs + 999L) / 1000L
        _uiState.update {
            it.copy(pinEntryInput = "", pinError = strings.profilePinLockedOut.format(secs))
        }
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
            _uiState.update { it.copy(errorMessage = strings.addServerErrorUsername) }
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
                    val s = strings
                    val friendly = when {
                        raw.contains("401") -> s.profileInvalidCredentials
                        raw.contains("403") -> s.profileSignInForbidden
                        raw.isNotBlank() -> raw
                        else -> s.profileSignInFailed
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
                        errorMessage = strings.profileQuickConnectDisabled
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
                                errorMessage = strings.profileQuickConnectExpired
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
        const val MAX_QC_POLL_FAILURES = 6
    }
}

