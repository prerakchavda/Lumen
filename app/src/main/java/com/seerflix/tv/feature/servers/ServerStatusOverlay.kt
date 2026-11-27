package com.seerflix.tv.feature.servers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.ServerAccountRepository
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.ServerHealth
import com.seerflix.tv.core.model.ServerStatus
import com.seerflix.tv.ui.theme.SeerflixTokens
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ServerStatusUiState(
    /** Extra servers that are offline right now (quiet pill only). */
    val offline: List<ServerStatus> = emptyList(),
    /** Extra servers that rejected the stored token: the profile has to sign in again. */
    val needSignIn: List<ServerStatus> = emptyList(),
    /** Ids the user already answered (sign in / later); not prompted again. */
    val dismissedPrompts: Set<String> = emptySet()
)

@HiltViewModel
class ServerStatusViewModel @Inject constructor(
    accounts: ServerAccountRepository,
    authRepository: AuthRepository
) : ViewModel() {
    // Kept here, not in a composable remember, so leaving Home (Details, Settings...) and coming
    // back doesn't ask again.
    private val dismissed = MutableStateFlow(emptySet<String>())

    val uiState: StateFlow<ServerStatusUiState> =
        combine(accounts.statusFlow, authRepository.activeProfileFlow, dismissed) { statuses, profile, dismissedIds ->
            ServerStatusUiState(
                offline = statuses.filter { it.health == ServerHealth.UNREACHABLE },
                // A Kids / rating-limited profile can't manage servers, so it is never asked to.
                needSignIn = if (profile?.isRestricted == true) emptyList()
                else statuses.filter { it.health == ServerHealth.AUTH_REQUIRED },
                dismissedPrompts = dismissedIds
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ServerStatusUiState())

    fun dismissPrompt(serverId: String) {
        dismissed.update { it + serverId }
    }
}

/**
 * Sits on top of Home without taking part in its focus handling:
 *  - a small, non-focusable note while an extra server is offline (the rest of the library keeps
 *    working, so this is information, not an error), and
 *  - a one-time prompt to sign in again when an extra server rejected the stored token.
 */
@Composable
fun ServerStatusOverlay(
    onSignInAgain: (serverId: String) -> Unit,
    viewModel: ServerStatusViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val strings = AppStrings.current
    val colors = SeerflixTokens.colors

    val note = when {
        state.needSignIn.isNotEmpty() ->
            strings.homeServerNeedsSignIn.format(state.needSignIn.joinToString { it.server.displayName })
        state.offline.isNotEmpty() ->
            strings.homeServerOffline.format(state.offline.joinToString { it.server.displayName })
        else -> null
    }
    if (note != null) {
        // Top corner, over the hero artwork: clear of the rows and their titles.
        Box(modifier = Modifier.fillMaxSize().padding(end = 32.dp, top = 24.dp), contentAlignment = Alignment.TopEnd) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(colors.deepBlack.copy(alpha = 0.92f), RoundedCornerShape(50))
                    .border(1.dp, colors.badgeBorder, RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Box(Modifier.size(8.dp).background(colors.textMuted, CircleShape))
                Spacer(Modifier.width(8.dp))
                Text(note, color = colors.textSecondary, fontSize = 13.sp)
            }
        }
    }

    // Ask once per server while Home is on the stack; "Later" leaves the note above and Settings
    // as the way back.
    val asking = state.needSignIn.firstOrNull { it.server.id !in state.dismissedPrompts }
    if (asking != null) {
        val signInFocus = remember { FocusRequester() }
        LaunchedEffect(asking.server.id) {
            delay(80L)
            runCatching { signInFocus.requestFocus() }
        }
        val dismiss = { viewModel.dismissPrompt(asking.server.id) }
        Dialog(onDismissRequest = dismiss) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .background(colors.dialogSurface, RoundedCornerShape(SeerflixTokens.spec.dialogCornerRadiusDp.dp))
                    .padding(28.dp)
            ) {
                Text(
                    strings.serverReSignInTitle.format(asking.server.displayName),
                    color = colors.textPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))
                Text(strings.serverReSignInBody, color = colors.textSecondary, fontSize = 16.sp)
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OverlayButton(
                        label = strings.serverSignInAgain,
                        primary = true,
                        modifier = Modifier.focusRequester(signInFocus),
                        onClick = {
                            dismiss()
                            onSignInAgain(asking.server.id)
                        }
                    )
                    OverlayButton(label = strings.serverReSignInLater, primary = false, onClick = dismiss)
                }
            }
        }
    }
}

@Composable
private fun OverlayButton(label: String, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(SeerflixTokens.spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                when {
                    focused -> colors.white
                    primary -> colors.primaryButtonBg
                    else -> colors.secondaryButtonBg
                },
                shape
            )
            .border(2.dp, if (focused) colors.primaryButtonFocusBorder else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp)
    ) {
        Text(
            label,
            color = if (focused) colors.deepBlack else if (primary) colors.primaryButtonFg else colors.white,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
