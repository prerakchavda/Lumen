package com.seerflix.tv.feature.servers

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.model.DiscoveredServer
import com.seerflix.tv.core.network.QrCodeGenerator
import com.seerflix.tv.ui.components.TvTextFieldContainer
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay

/**
 * Adds another Jellyfin server (or signs in again to one whose token was rejected). Everything is
 * reachable with the D-pad; text fields only open the keyboard on OK.
 */
@Composable
fun AddServerScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddServerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        viewModel.onScreenStopped()
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) {
        viewModel.onScreenStarted()
    }
    val strings = AppStrings.current
    val colors = SeerflixTokens.colors

    BackHandler {
        when {
            state.step == AddServerStep.QUICK_CONNECT -> viewModel.cancelQuickConnect()
            state.step == AddServerStep.SIGN_IN && !state.isReSignIn -> viewModel.backToAddress()
            // Don't walk away half-way through adding the library.
            state.step == AddServerStep.SYNCING -> Unit
            else -> onBack()
        }
    }
    LaunchedEffect(state.step) {
        if (state.step == AddServerStep.DONE) onDone()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 64.dp, vertical = 36.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = if (state.isReSignIn) strings.serverSignInAgain else strings.serversAddTitle,
                color = colors.textPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(18.dp))
            when (state.step) {
                AddServerStep.ADDRESS -> AddressStep(state, viewModel, strings)
                AddServerStep.SIGN_IN -> SignInStep(state, viewModel, strings)
                AddServerStep.QUICK_CONNECT -> QuickConnectStep(state, viewModel, strings)
                AddServerStep.SYNCING, AddServerStep.DONE -> SyncingStep(state, strings)
            }
        }
    }
}

@Composable
private fun AddressStep(state: AddServerUiState, viewModel: AddServerViewModel, strings: UiStrings) {
    val colors = SeerflixTokens.colors
    val firstField = remember { FocusRequester() }
    val continueButton = remember { FocusRequester() }
    val qrBitmap = remember(state.companionUrl) {
        state.companionUrl?.let { QrCodeGenerator.generate(it, 200) }
    }
    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { firstField.requestFocus() }
    }
    // The phone companion filled the address in: put focus on Continue.
    LaunchedEffect(state.companionUpdateCount) {
        if (state.companionUpdateCount > 0) runCatching { continueButton.requestFocus() }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(28.dp), modifier = Modifier.fillMaxSize()) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1.25f)
                .verticalScroll(rememberScrollState())
        ) {
            Text(strings.addServerIntro, color = colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp)
            ServerTextField(
                value = state.remoteUrl,
                onValueChange = viewModel::updateRemoteUrl,
                label = strings.addServerAddress,
                placeholder = "https://jellyfin.example.com",
                keyboardType = KeyboardType.Uri,
                containerFocusRequester = firstField
            )
            ServerTextField(
                value = state.localUrl,
                onValueChange = viewModel::updateLocalUrl,
                label = strings.addServerLocalAddress,
                placeholder = "http://192.168.1.100:8096",
                keyboardType = KeyboardType.Uri
            )
            ServerToggle(
                title = strings.addServerTrustSelfSigned,
                checked = state.trustSelfSignedCerts,
                strings = strings,
                onToggle = viewModel::toggleTrustSelfSigned
            )
            StatusLine(state, strings)
            ServerButton(
                label = if (state.isBusy) strings.addServerChecking else strings.addServerContinue,
                primary = true,
                modifier = Modifier.focusRequester(continueButton),
                onClick = viewModel::testAddress
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surface)
                .border(1.dp, colors.badgeBorder, RoundedCornerShape(10.dp))
                .padding(18.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.setupDiscovery, color = colors.white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                ServerButton(
                    label = if (state.isScanning) strings.setupScanning else strings.setupScan,
                    primary = false,
                    compact = true,
                    onClick = viewModel::scanNetwork
                )
            }
            if (state.discoveredServers.isEmpty()) {
                Text(
                    text = if (state.isScanning) strings.setupDiscoveryScanning else strings.setupDiscoveryNone,
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            } else {
                state.discoveredServers.forEach { server ->
                    DiscoveredChip(
                        server = server,
                        selected = state.remoteUrl == server.address,
                        onClick = { viewModel.selectDiscoveredServer(server) }
                    )
                }
            }
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.badgeBorder)
            )
            Text(strings.addServerPhoneHint, color = colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            if (qrBitmap != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.white)
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = strings.setupCompanionQr,
                            modifier = Modifier.size(132.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        state.companionUrl.orEmpty(),
                        color = colors.matchGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                Text(strings.setupCompanionOffline, color = colors.textMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SignInStep(state: AddServerUiState, viewModel: AddServerViewModel, strings: UiStrings) {
    val colors = SeerflixTokens.colors
    val passwordField = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { passwordField.requestFocus() }
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .widthIn(max = 760.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            strings.addServerSignInTitle.format(state.server?.displayName.orEmpty()),
            color = colors.white,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(strings.addServerSignInHint, color = colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        ServerTextField(
            value = state.username,
            onValueChange = viewModel::updateUsername,
            label = strings.addServerUsername,
            placeholder = ""
        )
        ServerTextField(
            value = state.password,
            onValueChange = viewModel::updatePassword,
            label = strings.addServerPassword,
            placeholder = "",
            keyboardType = KeyboardType.Password,
            isPassword = true,
            containerFocusRequester = passwordField
        )
        StatusLine(state, strings)
        // Stacked, so Down from the password always lands on "Sign in" (side by side, the D-pad
        // picked whichever button sat closer to the middle of the field).
        ServerButton(
            label = if (state.isBusy) strings.addServerSigningIn else strings.serverSignIn,
            primary = true,
            onClick = viewModel::signIn
        )
        ServerButton(
            label = strings.addServerUseQuickConnect,
            primary = false,
            onClick = viewModel::startQuickConnect
        )
    }
}

@Composable
private fun QuickConnectStep(state: AddServerUiState, viewModel: AddServerViewModel, strings: UiStrings) {
    val colors = SeerflixTokens.colors
    val backButton = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { backButton.requestFocus() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.widthIn(max = 760.dp)) {
        Text(
            strings.addServerQuickConnectHint.format(state.server?.displayName.orEmpty()),
            color = colors.textSecondary,
            fontSize = 16.sp,
            lineHeight = 22.sp
        )
        Text(
            state.quickConnectCode.orEmpty(),
            color = colors.white,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 8.sp
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(strings.addServerQuickConnectWaiting, color = colors.textMuted, fontSize = 14.sp)
        }
        ServerButton(
            label = strings.addServerUsePassword,
            primary = false,
            modifier = Modifier.focusRequester(backButton),
            onClick = viewModel::cancelQuickConnect
        )
    }
}

@Composable
private fun SyncingStep(state: AddServerUiState, strings: UiStrings) {
    val colors = SeerflixTokens.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(color = colors.accent, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            strings.addServerSyncing.format(state.server?.displayName.orEmpty()),
            color = colors.textPrimary,
            fontSize = 18.sp
        )
    }
}

/** Error line; the view model reports a few well-known failures by key so they can be translated. */
@Composable
private fun StatusLine(state: AddServerUiState, strings: UiStrings) {
    val message = when (val error = state.errorMessage) {
        null -> return
        "username" -> strings.addServerErrorUsername
        "credentials" -> strings.addServerErrorCredentials.format(state.username)
        "quickconnect" -> strings.addServerErrorQuickConnect
        "quickconnect_expired" -> strings.addServerErrorQuickConnectExpired
        else -> error
    }
    Text(message, color = SeerflixTokens.colors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun ServerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    containerFocusRequester: FocusRequester? = null
) {
    val colors = SeerflixTokens.colors
    TvTextFieldContainer(
        modifier = Modifier.fillMaxWidth(),
        focusRingColor = colors.focusBorder,
        containerFocusRequester = containerFocusRequester
    ) { fieldModifier ->
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 13.sp) },
            placeholder = { Text(placeholder, fontSize = 13.sp) },
            singleLine = true,
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = colors.white,
                unfocusedTextColor = colors.textPrimary,
                focusedBorderColor = colors.accent,
                unfocusedBorderColor = colors.badgeBorder,
                focusedLabelColor = colors.white,
                unfocusedLabelColor = colors.textSecondary
            ),
            modifier = fieldModifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ServerToggle(title: String, checked: Boolean, strings: UiStrings, onToggle: () -> Unit) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .border(2.dp, if (focused) colors.focusBorder else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(title, color = colors.white, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Text(
            if (checked) strings.optionOn else strings.optionOff,
            color = if (checked) colors.matchGreen else colors.textMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ServerButton(
    label: String,
    primary: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(SeerflixTokens.spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                when {
                    focused -> colors.white
                    primary -> colors.accent
                    else -> colors.secondaryButtonBg
                }
            )
            .border(2.dp, if (focused) colors.focusBorder else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 22.dp, vertical = if (compact) 6.dp else 11.dp)
    ) {
        Text(
            label,
            color = if (focused) colors.deepBlack else colors.white,
            fontSize = if (compact) 13.sp else 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DiscoveredChip(server: DiscoveredServer, selected: Boolean, onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(6.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    selected -> colors.accent.copy(alpha = 0.22f)
                    focused -> colors.surfaceElevated
                    else -> colors.dialogSurface
                }
            )
            .border(
                1.5.dp,
                when {
                    focused -> colors.focusBorder
                    selected -> colors.accent
                    else -> colors.badgeBorder
                },
                shape
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(server.name, color = colors.white, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(server.address, color = colors.textSecondary, fontSize = 12.sp)
    }
}
