package com.seerflix.tv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.model.FrameRateMatchMode
import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.ServerHealth
import com.seerflix.tv.core.model.SubtitleEdgeStyle
import com.seerflix.tv.core.model.SubtitlePosition
import com.seerflix.tv.core.model.SubtitleTextColor
import com.seerflix.tv.core.network.SocketState
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenServerSettings: () -> Unit,
    onAddServer: () -> Unit = {},
    onSignInToServer: (serverId: String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val strings = AppStrings.current
    val colors = SeerflixTokens.colors
    val profile = state.profile
    val firstFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { firstFocus.requestFocus() }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 64.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                strings.settingsTitle,
                color = colors.textPrimary,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 36.dp, bottom = 8.dp)
            )
        }

        // ---- Playback ----
        item { SectionHeader(strings.settingsPlaybackSection) }
        item {
            ToggleRow(
                title = strings.settingsMatchCapsTitle,
                description = strings.settingsMatchCapsDesc +
                    (if (state.capabilitySummary.isNotBlank()) "\n${strings.settingsDetected}: ${state.capabilitySummary}" else ""),
                checked = state.matchDeviceCapabilities,
                onToggle = { viewModel.setMatchDeviceCapabilities(!state.matchDeviceCapabilities) },
                strings = strings,
                modifier = Modifier.focusRequester(firstFocus)
            )
        }
        item {
            ToggleRow(
                title = strings.settingsSoftwareAudioTitle,
                description = strings.settingsSoftwareAudioDesc,
                checked = state.softwareAudioDecoding,
                onToggle = { viewModel.setSoftwareAudioDecoding(!state.softwareAudioDecoding) },
                strings = strings
            )
        }
        if (profile != null) {
            item {
                ToggleRow(
                    strings.settingsPassthroughTitle, strings.settingsPassthroughDesc,
                    profile.audioPassthroughEnabled, { viewModel.setAudioPassthrough(!profile.audioPassthroughEnabled) }, strings
                )
            }
            item {
                ToggleRow(
                    strings.settingsDownmixTitle, strings.settingsDownmixDesc,
                    profile.stereoDownmixEnabled, { viewModel.setStereoDownmix(!profile.stereoDownmixEnabled) }, strings
                )
            }
            item {
                ChoiceRow(
                    title = strings.settingsFrameRateTitle,
                    options = listOf(strings.optionOff, strings.frameRateSeamless, strings.frameRateAlways),
                    selectedIndex = FrameRateMatchMode.entries.indexOf(profile.frameRateMatchMode),
                    onSelect = { viewModel.setFrameRateMatch(FrameRateMatchMode.entries[it]) }
                )
            }
            item {
                ToggleRow(
                    strings.settingsAutoplayNextTitle, null,
                    profile.autoplayNextEpisode, { viewModel.setAutoplayNext(!profile.autoplayNextEpisode) }, strings
                )
            }
            item {
                ChoiceRow(
                    title = strings.settingsBingeLimitTitle,
                    options = BINGE_LIMIT_CHOICES.map { count ->
                        if (count == 0) strings.optionOff else strings.settingsBingeEpisodes.format(count)
                    },
                    selectedIndex = BINGE_LIMIT_CHOICES.indexOf(profile.bingeLimitEpisodes).coerceAtLeast(0),
                    onSelect = { viewModel.setBingeLimitEpisodes(BINGE_LIMIT_CHOICES[it]) }
                )
            }
            item {
                ToggleRow(
                    strings.settingsAutoSkipIntroTitle, null,
                    profile.autoSkipIntro, { viewModel.setAutoSkipIntro(!profile.autoSkipIntro) }, strings
                )
            }
            item {
                ToggleRow(
                    strings.settingsAutoSkipRecapTitle, null,
                    profile.autoSkipRecap, { viewModel.setAutoSkipRecap(!profile.autoSkipRecap) }, strings
                )
            }

            // ---- Subtitles ----
            item { SectionHeader(strings.settingsSubtitlesSection) }
            item {
                ChoiceRow(
                    title = strings.settingsSubtitleModeTitle,
                    options = com.seerflix.tv.core.model.SubtitleSelectionMode.entries.map { it.localized(strings) },
                    selectedIndex = com.seerflix.tv.core.model.SubtitleSelectionMode.entries.indexOf(profile.subtitleSelectionMode),
                    onSelect = { viewModel.setSubtitleSelectionMode(com.seerflix.tv.core.model.SubtitleSelectionMode.entries[it]) }
                )
            }
            item { SubtitlePreview(profile.subtitleFontScale, profile.subtitleBackgroundAlpha, profile.subtitleTextColor, profile.subtitleEdgeStyle, strings) }
            item {
                ChoiceRow(
                    title = strings.settingsSubtitleSize,
                    options = listOf(strings.sizeSmall, strings.sizeMedium, strings.sizeLarge, strings.sizeExtraLarge),
                    selectedIndex = SUBTITLE_SIZES.indexOfFirst { kotlin.math.abs(it - profile.subtitleFontScale) < 0.05f },
                    onSelect = { viewModel.setSubtitleSize(SUBTITLE_SIZES[it]) }
                )
            }
            item {
                ChoiceRow(
                    title = strings.settingsSubtitleColor,
                    options = SubtitleTextColor.entries.map { it.localized(strings) },
                    selectedIndex = SubtitleTextColor.entries.indexOf(profile.subtitleTextColor),
                    onSelect = { viewModel.setSubtitleColor(SubtitleTextColor.entries[it]) }
                )
            }
            item {
                ChoiceRow(
                    title = strings.settingsSubtitleBackground,
                    options = listOf(strings.backgroundNone, strings.backgroundLight, strings.backgroundMedium, strings.backgroundSolid),
                    selectedIndex = SUBTITLE_BACKGROUNDS.indexOfFirst { kotlin.math.abs(it - profile.subtitleBackgroundAlpha) < 0.05f },
                    onSelect = { viewModel.setSubtitleBackground(SUBTITLE_BACKGROUNDS[it]) }
                )
            }
            item {
                ChoiceRow(
                    title = strings.settingsSubtitleEdge,
                    options = SubtitleEdgeStyle.entries.map { it.localized(strings) },
                    selectedIndex = SubtitleEdgeStyle.entries.indexOf(profile.subtitleEdgeStyle),
                    onSelect = { viewModel.setSubtitleEdge(SubtitleEdgeStyle.entries[it]) }
                )
            }
            item {
                ChoiceRow(
                    title = strings.settingsSubtitlePosition,
                    options = SubtitlePosition.entries.map { it.localized(strings) },
                    selectedIndex = SubtitlePosition.entries.indexOf(profile.subtitlePosition),
                    onSelect = { viewModel.setSubtitlePosition(SubtitlePosition.entries[it]) }
                )
            }
        }

        // ---- Remote control ----
        item { SectionHeader(strings.settingsRemoteSection) }
        item {
            val status = when (state.socketState) {
                SocketState.CONNECTED -> strings.settingsConnected
                SocketState.CONNECTING -> strings.settingsConnecting
                SocketState.DISCONNECTED -> strings.settingsDisconnected
            }
            ToggleRow(
                strings.settingsRemoteTitle, "${strings.settingsRemoteDesc}\n$status",
                state.allowRemoteControl, { viewModel.setAllowRemoteControl(!state.allowRemoteControl) }, strings
            )
        }

        // ---- Diagnostics ----
        item { SectionHeader(strings.settingsDiagnosticsSection) }
        item {
            val result = state.logUploadMessage?.let { msg ->
                when {
                    msg.startsWith("uploaded:") -> strings.settingsLogsSent.format(msg.removePrefix("uploaded:"))
                    msg == "disabled" -> strings.settingsLogsDisabled
                    else -> strings.settingsLogsFailed.format(msg.removePrefix("failed:"))
                }
            }
            val description = listOfNotNull(
                strings.settingsSendLogsDesc,
                strings.settingsCrashPending.takeIf { state.hasPendingCrash },
                if (state.logUploadInProgress) strings.settingsSendingLogs else result
            ).joinToString("\n")
            ActionRow(strings.settingsSendLogsTitle, description, onClick = viewModel::sendLogs)
        }

        // ---- Server ----
        // Kids / rating-limited profiles don't get server settings: they show the Seerr API key and
        // would let a child repoint or reconfigure the app.
        if (state.profile?.isRestricted != true) {
            item { SectionHeader(strings.settingsServerSection) }
            item { ActionRow(strings.settingsServerTitle, strings.settingsServerDesc, onClick = onOpenServerSettings) }
            state.homeServer?.let { home ->
                item(key = "home_server_bitrate") {
                    ChoiceRow(
                        title = strings.settingsHomeBitrateTitle,
                        options = JellyfinServer.BITRATE_CHOICES.map { bps ->
                            if (bps == null) strings.serverBitrateNoLimit else JellyfinServer.bitrateLabel(bps)
                        },
                        selectedIndex = JellyfinServer.BITRATE_CHOICES.indexOf(home.maxStreamingBitrateBps),
                        onSelect = { viewModel.setServerBitrateCap(com.seerflix.tv.core.model.ServerScopedId.HOME, JellyfinServer.BITRATE_CHOICES[it]) }
                    )
                }
            }

            // ---- Other servers (merged into the same library) ----
            item { SectionHeader(strings.serversSection) }
            state.servers.forEach { status ->
                val server = status.server
                val needsSignIn = status.health == ServerHealth.AUTH_REQUIRED || status.health == ServerHealth.NOT_SIGNED_IN
                item(key = "server_${server.id}") {
                    val statusText = when (status.health) {
                        ServerHealth.ONLINE -> strings.serverStatusOnline.format(status.userName)
                        ServerHealth.UNREACHABLE -> strings.serverStatusOffline
                        ServerHealth.AUTH_REQUIRED -> strings.serverStatusAuth
                        ServerHealth.NOT_SIGNED_IN -> strings.serverStatusNotSignedIn
                    }
                    ActionRow(
                        title = server.displayName,
                        description = "${server.remoteUrl.ifBlank { server.localUrl }}\n$statusText",
                        onClick = { if (needsSignIn) onSignInToServer(server.id) }
                    )
                }
                item(key = "server_bitrate_${server.id}") {
                    ChoiceRow(
                        title = strings.serverBitrateTitle.format(server.displayName),
                        options = JellyfinServer.BITRATE_CHOICES.map { bps ->
                            if (bps == null) strings.serverBitrateNoLimit else JellyfinServer.bitrateLabel(bps)
                        },
                        selectedIndex = JellyfinServer.BITRATE_CHOICES.indexOf(server.maxStreamingBitrateBps),
                        onSelect = { viewModel.setServerBitrateCap(server.id, JellyfinServer.BITRATE_CHOICES[it]) }
                    )
                }
                item(key = "server_remove_${server.id}") {
                    val armed = state.pendingRemoveServerId == server.id
                    ActionRow(
                        title = if (armed) strings.serverRemoveConfirm.format(server.displayName)
                        else strings.serverRemove.format(server.displayName),
                        description = strings.serverRemoveDesc,
                        onClick = { viewModel.removeServer(server.id) }
                    )
                }
            }
            item { ActionRow(strings.serversAddTitle, strings.serversAddDesc, onClick = onAddServer) }
        }

        item { SectionHeader(strings.settingsAboutSection) }
        item {
            Text(
                strings.settingsVersion.format(state.appVersion),
                color = colors.textSecondary,
                fontSize = 15.sp,
                modifier = Modifier.padding(bottom = 48.dp)
            )
        }
    }
}

private val SUBTITLE_SIZES = listOf(0.8f, 1.0f, 1.25f, 1.5f)
private val SUBTITLE_BACKGROUNDS = listOf(0f, 0.3f, 0.6f, 0.9f)
private val BINGE_LIMIT_CHOICES = listOf(0, 3, 5, 8)

fun com.seerflix.tv.core.model.SubtitleSelectionMode.localized(s: UiStrings) = when (this) {
    com.seerflix.tv.core.model.SubtitleSelectionMode.DEFAULT -> s.subtitleModeDefault
    com.seerflix.tv.core.model.SubtitleSelectionMode.SMART -> s.subtitleModeSmart
    com.seerflix.tv.core.model.SubtitleSelectionMode.ONLY_FORCED -> s.subtitleModeOnlyForced
    com.seerflix.tv.core.model.SubtitleSelectionMode.ALWAYS -> s.subtitleModeAlways
    com.seerflix.tv.core.model.SubtitleSelectionMode.NONE -> s.subtitleModeNone
}

fun SubtitleTextColor.localized(s: UiStrings) = when (this) {
    SubtitleTextColor.WHITE -> s.colorWhite
    SubtitleTextColor.YELLOW -> s.colorYellow
    SubtitleTextColor.CYAN -> s.colorCyan
    SubtitleTextColor.GREEN -> s.colorGreen
    SubtitleTextColor.GREY -> s.colorGrey
}

fun SubtitleEdgeStyle.localized(s: UiStrings) = when (this) {
    SubtitleEdgeStyle.OUTLINE -> s.edgeOutline
    SubtitleEdgeStyle.DROP_SHADOW -> s.edgeDropShadow
    SubtitleEdgeStyle.RAISED -> s.edgeRaised
    SubtitleEdgeStyle.NONE -> s.edgeNone
}

fun SubtitlePosition.localized(s: UiStrings) = when (this) {
    SubtitlePosition.BOTTOM -> s.positionBottom
    SubtitlePosition.RAISED -> s.positionRaised
    SubtitlePosition.HIGH -> s.positionHigh
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = SeerflixTokens.colors.accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 22.dp, bottom = 2.dp)
    )
}

@Composable
private fun SettingsCard(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable (focused: Boolean) -> Unit
) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(SeerflixTokens.spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 1100.dp)
            .background(if (focused) colors.surfaceElevated else colors.surface, shape)
            .border(2.dp, if (focused) colors.focusBorder else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        content(focused)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    strings: UiStrings,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    SettingsCard(onClick = onToggle, modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (!description.isNullOrBlank()) {
                    Text(description, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            Spacer(Modifier.width(24.dp))
            val pillShape = RoundedCornerShape(50)
            Box(
                modifier = Modifier
                    .background(if (checked) colors.accent else colors.secondaryButtonBg, pillShape)
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Text(
                    if (checked) strings.optionOn else strings.optionOff,
                    color = colors.white,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun ActionRow(title: String, description: String?, onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    SettingsCard(onClick = onClick) {
        Column {
            Text(title, color = colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (!description.isNullOrBlank()) {
                Text(description, color = colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** A titled row of option chips; Left/Right moves between options, OK selects. */
@Composable
private fun ChoiceRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    val colors = SeerflixTokens.colors
    Column(modifier = Modifier.fillMaxWidth().widthIn(max = 1100.dp)) {
        Text(title, color = colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.focusGroup()
        ) {
            itemsIndexed(options) { index, label ->
                OptionChip(label = label, selected = index == selectedIndex, onClick = { onSelect(index) })
            }
        }
    }
}

@Composable
private fun OptionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(SeerflixTokens.spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .background(
                when {
                    selected -> colors.white
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                },
                shape
            )
            .border(2.dp, if (focused) colors.accent else Color.Transparent, shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            color = if (selected) colors.deepBlack else colors.white,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SubtitlePreview(
    fontScale: Float,
    backgroundAlpha: Float,
    textColor: SubtitleTextColor,
    edge: SubtitleEdgeStyle,
    strings: UiStrings
) {
    val shadow = when (edge) {
        SubtitleEdgeStyle.NONE -> null
        SubtitleEdgeStyle.DROP_SHADOW -> Shadow(Color.Black, androidx.compose.ui.geometry.Offset(3f, 3f), 4f)
        SubtitleEdgeStyle.RAISED -> Shadow(Color.Black, androidx.compose.ui.geometry.Offset(-2f, -2f), 2f)
        SubtitleEdgeStyle.OUTLINE -> Shadow(Color.Black, androidx.compose.ui.geometry.Offset(0f, 0f), 6f)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 1100.dp)
            .height(110.dp)
            .background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF2B3A55), Color(0xFF5A3A2E))),
                RoundedCornerShape(10.dp)
            ),
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(
            strings.settingsSubtitlePreview,
            style = TextStyle(
                color = Color(textColor.argb),
                fontSize = (22f * fontScale).sp,
                fontWeight = FontWeight.Medium,
                shadow = shadow
            ),
            modifier = Modifier
                .padding(bottom = 14.dp)
                .background(Color.Black.copy(alpha = backgroundAlpha), RoundedCornerShape(4.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
