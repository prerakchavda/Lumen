package com.seerflix.tv.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilePickerUiState(
    val profiles: List<UserProfile> = emptyList(),
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

    init {
        viewModelScope.launch {
            authRepository.profilesFlow.collect { list ->
                _uiState.update { it.copy(profiles = list) }
            }
        }
    }

    fun selectProfile(profile: UserProfile, onSignedIn: () -> Unit) {
        viewModelScope.launch {
            authRepository.switchProfile(profile.id)
            onSignedIn()
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
        startQuickConnect(onSignedIn)
    }

    fun closeAuthDialog() {
        qcPollingJob?.cancel()
        _uiState.update { it.copy(showAuthDialog = false, isAuthenticating = false, errorMessage = null) }
    }

    fun toggleAuthMode(usePassword: Boolean, onSignedIn: () -> Unit) {
        _uiState.update { it.copy(usePasswordMode = usePassword, errorMessage = null) }
        if (usePassword) {
            qcPollingJob?.cancel()
        } else {
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
                    _uiState.update {
                        it.copy(
                            isAuthenticating = false,
                            errorMessage = err.message ?: "Sign-in failed"
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
            initResult.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isAuthenticating = false,
                        errorMessage = "Quick Connect unavailable (${err.message}). Try Password login."
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

            while (isActive) {
                delay(2500L)
                val poll = authRepository.pollAndCompleteQuickConnect(qc.secret)
                val signedInProfile = poll.getOrNull()
                if (signedInProfile != null) {
                    _uiState.update { it.copy(showAuthDialog = false) }
                    onSignedIn()
                    break
                }
            }
        }
    }
}
