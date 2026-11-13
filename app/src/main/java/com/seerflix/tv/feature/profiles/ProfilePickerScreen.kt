package com.seerflix.tv.feature.profiles

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.ui.theme.SeerflixTokens

@Composable
fun ProfilePickerScreen(
    onProfileSelected: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfilePickerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    ProfilePickerContent(
        state = state,
        onOpenSettings = onOpenSettings,
        onSelectProfile = { profile -> viewModel.selectProfile(profile, onProfileSelected) },
        onOpenAddProfile = { viewModel.openAddProfile(onProfileSelected) },
        onCloseAuthDialog = viewModel::closeAuthDialog,
        onUpdateUsername = viewModel::updateUsername,
        onUpdatePassword = viewModel::updatePassword,
        onSignInWithPassword = { viewModel.signInWithPassword(onProfileSelected) },
        onToggleAuthMode = { usePassword -> viewModel.toggleAuthMode(usePassword, onProfileSelected) },
        onToggleManageMode = viewModel::toggleManageMode,
        onAppendPinDigit = { d -> viewModel.appendPinDigit(d, onProfileSelected) },
        onBackspacePin = viewModel::backspacePin,
        onClearPinInput = viewModel::clearPinInput,
        onDismissPinChallenge = viewModel::dismissPinChallenge,
        onCloseProfileSettings = viewModel::closeProfileSettings,
        onSaveProfilePreferences = viewModel::saveProfilePreferences,
        onRemoveProfile = viewModel::removeProfile
    )
}

@Composable
fun ProfilePickerContent(
    state: ProfilePickerUiState,
    onOpenSettings: () -> Unit,
    onSelectProfile: (UserProfile) -> Unit,
    onOpenAddProfile: () -> Unit,
    onCloseAuthDialog: () -> Unit,
    onUpdateUsername: (String) -> Unit,
    onUpdatePassword: (String) -> Unit,
    onSignInWithPassword: () -> Unit,
    onToggleAuthMode: (Boolean) -> Unit,
    onToggleManageMode: () -> Unit = {},
    onAppendPinDigit: (String) -> Unit = {},
    onBackspacePin: () -> Unit = {},
    onClearPinInput: () -> Unit = {},
    onDismissPinChallenge: () -> Unit = {},
    onCloseProfileSettings: () -> Unit = {},
    onSaveProfilePreferences: (UserProfile, String, Boolean) -> Unit = { _, _, _ -> },
    onRemoveProfile: (String) -> Unit = {}
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Top-right Manage Profiles & Server Settings buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(32.dp)
        ) {
            if (state.profiles.isNotEmpty()) {
                TopCornerButton(
                    icon = if (state.isManageMode) Icons.Filled.Check else Icons.Filled.Edit,
                    label = if (state.isManageMode) "Done" else "Manage Profiles",
                    active = state.isManageMode,
                    onClick = onToggleManageMode
                )
            }
            TopCornerButton(
                icon = Icons.Filled.Settings,
                label = "Servers",
                active = false,
                onClick = onOpenSettings
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (state.isManageMode) "Manage Profiles" else "Who's watching?",
                color = colors.white,
                fontSize = 38.sp,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(40.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                state.profiles.forEach { profile ->
                    NetflixAvatarTile(
                        profile = profile,
                        isManageMode = state.isManageMode,
                        onClick = { onSelectProfile(profile) }
                    )
                }

                AddProfileTile(
                    onClick = onOpenAddProfile
                )
            }
        }
    }

    state.pinChallengeProfile?.let { profile ->
        PinEntryDialog(
            profileName = profile.name,
            pinInput = state.pinEntryInput,
            errorMessage = state.pinError,
            onDigit = onAppendPinDigit,
            onBackspace = onBackspacePin,
            onClear = onClearPinInput,
            onDismiss = onDismissPinChallenge
        )
    }

    state.editingProfile?.let { profile ->
        ProfileSettingsDialog(
            profile = profile,
            onSave = onSaveProfilePreferences,
            onRemove = { onRemoveProfile(profile.id) },
            onDismiss = onCloseProfileSettings
        )
    }

    if (state.showAuthDialog) {
        val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
        Dialog(onDismissRequest = onCloseAuthDialog) {
            Column(
                modifier = Modifier
                    .width(480.dp)
                    .clip(dialogShape)
                    .background(colors.dialogSurface)
                    .border(1.dp, colors.badgeBorder, dialogShape)
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (state.usePasswordMode) "Sign In with Password" else "Quick Connect",
                    color = colors.white,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                if (!state.usePasswordMode) {
                    Text(
                        text = "Open Jellyfin or Seerr on your phone/computer → Quick Connect, and enter this code:",
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )

                    if (state.quickConnectCode != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.surfaceElevated)
                                .border(1.dp, colors.accent, RoundedCornerShape(8.dp))
                                .padding(horizontal = 32.dp, vertical = 18.dp)
                        ) {
                            Text(
                                text = state.quickConnectCode!!,
                                color = colors.white,
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 8.sp
                            )
                        }
                        Text(
                            text = "Waiting for approval…",
                            color = colors.matchGreen,
                            fontSize = 13.sp
                        )
                    } else if (state.isAuthenticating) {
                        CircularProgressIndicator(color = colors.accent)
                    }
                } else {
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = onUpdateUsername,
                        label = { Text("Jellyfin Username") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.white,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = onUpdatePassword,
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = colors.white,
                            unfocusedTextColor = colors.textPrimary,
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = Color(0x55FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    DialogButton(
                        text = if (state.isAuthenticating) "Signing in..." else "Sign In",
                        isPrimary = true,
                        onClick = onSignInWithPassword,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                state.errorMessage?.let { err ->
                    Text(text = err, color = colors.accent, fontSize = 13.sp)
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DialogButton(
                        text = if (state.usePasswordMode) "Use Quick Connect" else "Use Password",
                        isPrimary = false,
                        onClick = { onToggleAuthMode(!state.usePasswordMode) },
                        modifier = Modifier.weight(1f)
                    )
                    DialogButton(
                        text = "Cancel",
                        isPrimary = false,
                        onClick = onCloseAuthDialog,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TopCornerButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    active -> colors.white
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                }
            )
            .then(if (focused) Modifier.border(2.dp, colors.accent, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (active) colors.deepBlack else colors.textSecondary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            color = if (active) colors.deepBlack else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PinEntryDialog(
    profileName: String,
    pinInput: String,
    errorMessage: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        PinEntryCard(
            profileName = profileName,
            pinInput = pinInput,
            errorMessage = errorMessage,
            onDigit = onDigit,
            onBackspace = onBackspace,
            onClear = onClear,
            onDismiss = onDismiss
        )
    }
}

@Composable
fun PinEntryCard(
    profileName: String,
    pinInput: String,
    errorMessage: String?,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    Column(
        modifier = Modifier
            .width(380.dp)
            .clip(dialogShape)
            .background(colors.dialogSurface)
            .border(1.dp, colors.badgeBorder, dialogShape)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
        Text("Enter PIN for $profileName", color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { idx ->
                val filled = idx < pinInput.length
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surfaceElevated)
                        .border(
                            1.5.dp,
                            if (filled) colors.white else colors.badgeBorder,
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (filled) "●" else "",
                        color = colors.white,
                        fontSize = 22.sp
                    )
                }
            }
        }

        errorMessage?.let {
            Text(it, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        // D-pad friendly numeric keypad
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("CLR", "0", "⌫")
        )
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { rowKeys ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rowKeys.forEach { key ->
                        DialogButton(
                            text = key,
                            isPrimary = false,
                            onClick = {
                                when (key) {
                                    "CLR" -> onClear()
                                    "⌫" -> onBackspace()
                                    else -> onDigit(key)
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        DialogButton(
            text = "Cancel",
            isPrimary = false,
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ProfileSettingsDialog(
    profile: UserProfile,
    onSave: (UserProfile, String, Boolean) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)

    var activeSection by remember(profile.id) { mutableStateOf(0) } // 0=Appearance, 1=Theater, 2=Kids&PIN

    var selectedPreset by remember(profile.id) { mutableStateOf(profile.uiPreset) }
    var uiLanguage by remember(profile.id) { mutableStateOf(profile.uiLanguage) }
    var metadataLanguage by remember(profile.id) { mutableStateOf(profile.metadataLanguage) }
    var defaultStartupTab by remember(profile.id) { mutableStateOf(profile.defaultStartupTab) }

    var autoplayNext by remember(profile.id) { mutableStateOf(profile.autoplayNextEpisode) }
    var autoplayPreviews by remember(profile.id) { mutableStateOf(profile.autoplayPreviews) }
    var autoSkipIntro by remember(profile.id) { mutableStateOf(profile.autoSkipIntro) }
    var autoSkipRecap by remember(profile.id) { mutableStateOf(profile.autoSkipRecap) }
    var frameRateMatchMode by remember(profile.id) { mutableStateOf(profile.frameRateMatchMode) }
    var audioPassthrough by remember(profile.id) { mutableStateOf(profile.audioPassthroughEnabled) }
    var stereoDownmix by remember(profile.id) { mutableStateOf(profile.stereoDownmixEnabled) }
    var assSubtitleMode by remember(profile.id) { mutableStateOf(profile.assSubtitleMode) }
    var audioLang by remember(profile.id) { mutableStateOf(profile.preferredAudioLang) }
    var subLang by remember(profile.id) { mutableStateOf(profile.preferredSubtitleLang) }

    var kidsMode by remember(profile.id) { mutableStateOf(profile.kidsMode) }
    var maxContentRating by remember(profile.id) { mutableStateOf(profile.maxContentRating) }
    var allowKidsRequests by remember(profile.id) { mutableStateOf(profile.allowKidsRequests) }
    var pinCode by remember(profile.id) { mutableStateOf("") }
    var clearPin by remember(profile.id) { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(620.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Profile Settings • ${profile.name}",
                color = colors.white,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // Section tabs
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Appearance & Language", "Home Theater", "Kids & PIN").forEachIndexed { idx, title ->
                    PrefChip(
                        label = title,
                        selected = activeSection == idx,
                        onClick = { activeSection = idx },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when (activeSection) {
                0 -> {
                    Text("UI Preset", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiPreset.entries.forEach { preset ->
                            PrefChip(
                                label = preset.displayName,
                                selected = selectedPreset == preset,
                                onClick = { selectedPreset = preset },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Text(
                        text = selectedPreset.subtitle,
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )

                    Text("App UI Language", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.seerflix.tv.core.i18n.SupportedLanguages.forEach { (code, label) ->
                            PrefChip(
                                label = label,
                                selected = uiLanguage == code,
                                onClick = { uiLanguage = code },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Metadata / Seerr Language", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.seerflix.tv.core.i18n.SupportedLanguages.forEach { (code, label) ->
                            PrefChip(
                                label = label,
                                selected = metadataLanguage == code,
                                onClick = { metadataLanguage = code },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Default Startup Tab", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    val tabList = com.seerflix.tv.core.model.HomeTabId.entries
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabList.take(4).forEach { tab ->
                            PrefChip(
                                label = tab.label,
                                selected = defaultStartupTab == tab,
                                onClick = { defaultStartupTab = tab },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        tabList.drop(4).forEach { tab ->
                            PrefChip(
                                label = tab.label,
                                selected = defaultStartupTab == tab,
                                onClick = { defaultStartupTab = tab },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                1 -> {
                    PreferenceSwitchRow(
                        label = "Autoplay Next Episode",
                        checked = autoplayNext,
                        onToggle = { autoplayNext = !autoplayNext }
                    )
                    PreferenceSwitchRow(
                        label = "Autoplay Previews While Browsing",
                        checked = autoplayPreviews,
                        onToggle = { autoplayPreviews = !autoplayPreviews }
                    )
                    PreferenceSwitchRow(
                        label = "Auto-Skip Intro",
                        checked = autoSkipIntro,
                        onToggle = { autoSkipIntro = !autoSkipIntro }
                    )
                    PreferenceSwitchRow(
                        label = "Auto-Skip Recap",
                        checked = autoSkipRecap,
                        onToggle = { autoSkipRecap = !autoSkipRecap }
                    )
                    PreferenceSwitchRow(
                        label = "HDMI Audio Passthrough (TrueHD / DTS-HD / EAC3)",
                        checked = audioPassthrough,
                        onToggle = { audioPassthrough = !audioPassthrough }
                    )
                    PreferenceSwitchRow(
                        label = "Stereo Downmix (TV Speakers / Night Mode)",
                        checked = stereoDownmix,
                        onToggle = { stereoDownmix = !stereoDownmix }
                    )

                    Text("ASS / SSA Anime Subtitles", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.seerflix.tv.core.model.AssSubtitleMode.entries.forEach { mode ->
                            PrefChip(
                                label = mode.label,
                                selected = assSubtitleMode == mode,
                                onClick = { assSubtitleMode = mode },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("24fps Frame-Rate Matching", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.seerflix.tv.core.model.FrameRateMatchMode.entries.forEach { mode ->
                            PrefChip(
                                label = mode.label,
                                selected = frameRateMatchMode == mode,
                                onClick = { frameRateMatchMode = mode },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Text("Preferred Audio / Subtitle Language", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Default", "eng" to "Eng", "spa" to "Spa", "jpn" to "Jpn").forEach { (code, label) ->
                            PrefChip(
                                label = "Aud: $label",
                                selected = audioLang == code,
                                onClick = { audioLang = code },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(null to "Default", "off" to "Off", "eng" to "Eng", "spa" to "Spa").forEach { (code, label) ->
                            PrefChip(
                                label = "Sub: $label",
                                selected = subLang == code,
                                onClick = { subLang = code },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                2 -> {
                    PreferenceSwitchRow(
                        label = "Kids Profile Mode (Parental Filter)",
                        checked = kidsMode,
                        onToggle = {
                            kidsMode = !kidsMode
                            if (kidsMode && maxContentRating == com.seerflix.tv.core.model.ContentRatingLimit.ALL) {
                                maxContentRating = com.seerflix.tv.core.model.ContentRatingLimit.PG
                            }
                        }
                    )
                    if (kidsMode) {
                        PreferenceSwitchRow(
                            label = "Allow Seerr Requests in Kids Mode",
                            checked = allowKidsRequests,
                            onToggle = { allowKidsRequests = !allowKidsRequests }
                        )
                    }

                    Text("Max Allowed Content Rating", color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.seerflix.tv.core.model.ContentRatingLimit.entries.forEach { limit ->
                            PrefChip(
                                label = limit.label,
                                selected = maxContentRating == limit,
                                onClick = { maxContentRating = limit },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Text(
                        text = if (profile.hasPin && !clearPin) "Profile PIN (Currently Locked)" else "Set 4-Digit Profile PIN",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = pinCode,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(4)
                                pinCode = digits
                                if (digits.isNotEmpty()) clearPin = false
                            },
                            placeholder = { Text(if (profile.hasPin && !clearPin) "•••• (enter new 4 digits)" else "4 digits (optional)") },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colors.white,
                                unfocusedTextColor = colors.textPrimary,
                                focusedBorderColor = colors.accent,
                                unfocusedBorderColor = Color(0x55FFFFFF)
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (profile.hasPin && !clearPin) {
                            DialogButton(
                                text = "Remove PIN",
                                isPrimary = false,
                                onClick = { clearPin = true; pinCode = "" },
                                modifier = Modifier.width(120.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DialogButton(
                    text = "Save",
                    isPrimary = true,
                    onClick = {
                        val updated = profile.copy(
                            autoplayNextEpisode = autoplayNext,
                            autoplayPreviews = autoplayPreviews,
                            autoSkipIntro = autoSkipIntro,
                            autoSkipRecap = autoSkipRecap,
                            frameRateMatchMode = frameRateMatchMode,
                            audioPassthroughEnabled = audioPassthrough,
                            stereoDownmixEnabled = stereoDownmix,
                            assSubtitleMode = assSubtitleMode,
                            preferredAudioLang = audioLang,
                            preferredSubtitleLang = subLang,
                            uiPreset = selectedPreset,
                            uiLanguage = uiLanguage,
                            metadataLanguage = metadataLanguage,
                            defaultStartupTab = defaultStartupTab,
                            kidsMode = kidsMode,
                            maxContentRating = maxContentRating,
                            allowKidsRequests = allowKidsRequests
                        )
                        onSave(updated, pinCode, clearPin)
                    },
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = "Delete Profile",
                    isPrimary = false,
                    onClick = onRemove,
                    modifier = Modifier.weight(1f)
                )
                DialogButton(
                    text = "Cancel",
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, color = colors.white, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Switch(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(checkedThumbColor = colors.white, checkedTrackColor = colors.accent)
        )
    }
}

@Composable
private fun PrefChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    selected -> colors.white
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                }
            )
            .then(if (focused) Modifier.border(2.dp, colors.accent, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) colors.deepBlack else colors.white,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun NetflixAvatarTile(
    profile: UserProfile,
    isManageMode: Boolean = false,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "avatarScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(profile.avatarColorHex))
                .then(
                    if (focused) Modifier.border(3.dp, colors.focusBorder, RoundedCornerShape(8.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!profile.avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = profile.avatarUrl,
                    contentDescription = profile.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = profile.name.take(1).uppercase(),
                    color = colors.white,
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black
                )
            }

            if (isManageMode) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x88000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit Profile", tint = colors.white, modifier = Modifier.size(36.dp))
                }
            } else if (profile.hasPin) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(colors.background.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "PIN Protected", tint = colors.white, modifier = Modifier.size(14.dp))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.name,
            color = if (focused) colors.white else colors.textSecondary,
            fontSize = 16.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal
        )
        if (isManageMode) {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(profile.uiPreset.accentHex).copy(alpha = 0.22f))
                    .border(1.dp, Color(profile.uiPreset.accentHex).copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = profile.uiPreset.shortLabel,
                    color = colors.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.08f else 1f, label = "addProfileScale")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(132.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (focused) colors.surfaceElevated else colors.surface)
                .then(
                    if (focused) Modifier.border(3.dp, colors.focusBorder, RoundedCornerShape(8.dp))
                    else Modifier.border(1.dp, colors.badgeBorder, RoundedCornerShape(8.dp))
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add Profile",
                tint = colors.textSecondary,
                modifier = Modifier.size(52.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Add Profile",
            color = if (focused) colors.white else colors.textSecondary,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun DialogButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isPrimary -> colors.accent
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                }
            )
            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = colors.white, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

