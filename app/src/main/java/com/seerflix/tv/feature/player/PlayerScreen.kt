package com.seerflix.tv.feature.player

import android.view.KeyEvent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.theme.NetflixColors
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val player = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var osdVisible by remember { mutableStateOf(true) }
    var showTracksDialog by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.reportStopped(player.currentPosition.coerceAtLeast(0L))
            player.release()
        }
    }

    LaunchedEffect(state.session?.itemId) {
        val session = state.session ?: return@LaunchedEffect
        val mediaItemBuilder = MediaItem.Builder().setUri(session.streamUrl)
        if (session.isHls) {
            mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
        }
        player.setMediaItem(mediaItemBuilder.build())
        val resumeMs = (session.resumeTicks / 10_000L).coerceAtLeast(0L)
        if (resumeMs > 5_000L) {
            player.seekTo(resumeMs)
        }
        player.prepare()
        player.play()
        viewModel.reportStart(resumeMs)
    }

    // Track position & report progress to Jellyfin every 10s
    LaunchedEffect(state.session?.itemId) {
        var tick = 0
        while (true) {
            delay(500L)
            isPlaying = player.isPlaying
            currentPosMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            tick++
            if (tick % 20 == 0 && state.session != null) {
                viewModel.reportProgress(currentPosMs, !isPlaying)
            }
        }
    }

    // Auto-hide Netflix OSD after 4.5s
    LaunchedEffect(osdVisible, showTracksDialog, isPlaying) {
        if (osdVisible && !showTracksDialog && isPlaying) {
            delay(4500L)
            osdVisible = false
        }
    }

    val session = state.session
    val currentTicks = currentPosMs * 10_000L
    val showSkipIntro = session?.introStartTicks != null &&
        session.introEndTicks != null &&
        currentTicks in session.introStartTicks..session.introEndTicks

    val showNextEpisode = session?.nextEpisodeId != null && (
        (session.creditsStartTicks != null && currentTicks >= session.creditsStartTicks) ||
            (durationMs > 60_000L && currentPosMs >= durationMs - 25_000L)
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (!osdVisible) {
                                osdVisible = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_UP,
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            if (!osdVisible) {
                                osdVisible = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!osdVisible) {
                                player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L))
                                osdVisible = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!osdVisible) {
                                player.seekTo(player.currentPosition + 10_000L)
                                osdVisible = true
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = NetflixColors.NetflixRed,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            state.errorMessage != null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage!!, color = NetflixColors.NetflixRed, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    HeroPillButton("Back", Icons.AutoMirrored.Filled.ArrowBack, isPrimary = true, onClick = onBack)
                }
            }
            else -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    update = { view ->
                        if (view.player !== player) view.player = player
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Skip Intro Floating Pill
                if (showSkipIntro && session?.introEndTicks != null) {
                    HeroPillButton(
                        text = "Skip Intro",
                        icon = Icons.Filled.SkipNext,
                        isPrimary = true,
                        onClick = {
                            player.seekTo(session.introEndTicks / 10_000L)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 48.dp, bottom = if (osdVisible) 130.dp else 48.dp)
                    )
                }

                // Next Episode Overlay Card
                if (showNextEpisode && session?.nextEpisodeId != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 48.dp, bottom = if (osdVisible) 130.dp else 48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xE6181818))
                            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(8.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("Up Next", color = NetflixColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(session.nextEpisodeTitle ?: "Next Episode", color = NetflixColors.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        HeroPillButton(
                            text = "Play Next Episode",
                            icon = Icons.Filled.SkipNext,
                            isPrimary = true,
                            onClick = {
                                viewModel.reportStopped(currentPosMs)
                                viewModel.loadItem(session.nextEpisodeId)
                            }
                        )
                    }
                }

                // Netflix OSD Overlay
                AnimatedVisibility(
                    visible = osdVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top gradient & title
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
                                )
                                .padding(horizontal = 48.dp, vertical = 28.dp)
                        ) {
                            PlayerControlIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                            Spacer(Modifier.width(18.dp))
                            Column {
                                Text(
                                    text = session?.title.orEmpty(),
                                    color = NetflixColors.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                session?.subtitle?.let { sub ->
                                    Text(text = sub, color = NetflixColors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                        }

                        // Bottom scrubber & transport buttons
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))
                                )
                                .padding(horizontal = 48.dp, vertical = 28.dp)
                        ) {
                            val totalFloat = durationMs.coerceAtLeast(1L).toFloat()
                            Slider(
                                value = (currentPosMs.toFloat() / totalFloat).coerceIn(0f, 1f),
                                onValueChange = { frac ->
                                    player.seekTo((frac * totalFloat).toLong())
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = NetflixColors.NetflixRed,
                                    activeTrackColor = NetflixColors.NetflixRed,
                                    inactiveTrackColor = Color(0x55FFFFFF)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    PlayerControlIcon(
                                        icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                        label = if (isPlaying) "Pause" else "Play",
                                        onClick = { if (player.isPlaying) player.pause() else player.play() }
                                    )
                                    PlayerControlIcon(
                                        icon = Icons.Filled.Replay10,
                                        label = "Back 10s",
                                        onClick = { player.seekTo((player.currentPosition - 10_000L).coerceAtLeast(0L)) }
                                    )
                                    PlayerControlIcon(
                                        icon = Icons.Filled.Forward10,
                                        label = "Forward 10s",
                                        onClick = { player.seekTo(player.currentPosition + 10_000L) }
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = "${formatMs(currentPosMs)} / ${formatMs(durationMs)}",
                                        color = NetflixColors.TextSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    PlayerControlIcon(
                                        icon = Icons.Filled.Subtitles,
                                        label = "Audio & Subtitles",
                                        onClick = { showTracksDialog = true }
                                    )
                                    session?.nextEpisodeId?.let { nextId ->
                                        PlayerControlIcon(
                                            icon = Icons.Filled.SkipNext,
                                            label = "Next Episode",
                                            onClick = {
                                                viewModel.reportStopped(currentPosMs)
                                                viewModel.loadItem(nextId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTracksDialog) {
        AudioSubtitleDialog(
            player = player,
            onDismiss = { showTracksDialog = false }
        )
    }
}

@Composable
private fun PlayerControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (focused) NetflixColors.White else Color(0x66222222))
            .then(if (focused) Modifier.border(2.dp, NetflixColors.NetflixRed, RoundedCornerShape(23.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) NetflixColors.DeepBlack else NetflixColors.White,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun AudioSubtitleDialog(
    player: ExoPlayer,
    onDismiss: () -> Unit
) {
    val groups = player.currentTracks.groups
    val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val textGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF181818))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Audio & Subtitles", color = NetflixColors.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            if (audioGroups.isNotEmpty()) {
                Text("Audio Tracks", color = NetflixColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                audioGroups.forEachIndexed { idx, group ->
                    val format = group.getTrackFormat(0)
                    val label = listOfNotNull(format.language?.uppercase(), format.label).joinToString(" • ").ifBlank { "Audio ${idx + 1}" }
                    TrackChoiceRow(
                        label = label,
                        selected = group.isSelected,
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                .build()
                            onDismiss()
                        }
                    )
                }
            }

            if (textGroups.isNotEmpty()) {
                Text("Subtitles", color = NetflixColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TrackChoiceRow(
                    label = "Off",
                    selected = textGroups.none { it.isSelected },
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        onDismiss()
                    }
                )
                textGroups.forEachIndexed { idx, group ->
                    val format = group.getTrackFormat(0)
                    val label = listOfNotNull(format.language?.uppercase(), format.label).joinToString(" • ").ifBlank { "Subtitle ${idx + 1}" }
                    TrackChoiceRow(
                        label = label,
                        selected = group.isSelected,
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                .build()
                            onDismiss()
                        }
                    )
                }
            }

            if (audioGroups.isEmpty() && textGroups.isEmpty()) {
                Text("No alternate tracks detected in this stream.", color = NetflixColors.TextSecondary, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TrackChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    focused -> Color(0xFF3A3A3A)
                    selected -> Color(0xFF282828)
                    else -> Color(0xFF202020)
                }
            )
            .then(if (focused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = NetflixColors.White, fontSize = 14.sp)
        if (selected) {
            Text("Active", color = NetflixColors.NetflixRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}
