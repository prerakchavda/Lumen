package com.seerflix.tv.feature.player

import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.AssSubtitleMode
import com.seerflix.tv.core.model.ChapterMarker
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.ExternalSubtitleFile
import com.seerflix.tv.core.model.FrameRateMatchMode
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.QualityPreset
import com.seerflix.tv.core.model.RemoteSubtitleOption
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.TrickplayCrop
import com.seerflix.tv.core.player.AssSubtitleOverlay
import com.seerflix.tv.core.player.AutoSkipHelper
import com.seerflix.tv.core.player.FrameRateMatcher
import com.seerflix.tv.core.player.OffsetSubtitleParserFactory
import com.seerflix.tv.core.player.SkipSegmentType
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val colors = SeerflixTokens.colors
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { FrameRateMatcher.findActivity(context) }

    val subtitleOffsetUsRef = remember { AtomicLong(0L) }
    val player = remember(context) {
        val subtitleParserFactory = OffsetSubtitleParserFactory { subtitleOffsetUsRef.get() }
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setSubtitleParserFactory(subtitleParserFactory)
            .experimentalParseSubtitlesDuringExtraction(true)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                playWhenReady = true
            }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var osdVisible by remember { mutableStateOf(true) }
    var showTracksDialog by remember { mutableStateOf(false) }
    var showEpisodesDrawer by remember { mutableStateOf(false) }
    var scrubPreviewMs by remember { mutableStateOf<Long?>(null) }
    var detectedVideoFrameRate by remember { mutableFloatStateOf(0f) }
    var skippedIntroItemId by remember { mutableStateOf<String?>(null) }
    var skippedRecapItemId by remember { mutableStateOf<String?>(null) }
    var autoSkipUndoPrompt by remember { mutableStateOf<Pair<SkipSegmentType, Long>?>(null) }
    var subtitleOffsetHudText by remember { mutableStateOf<String?>(null) }
    var hasInitializedOffsetEffect by remember { mutableStateOf(false) }
    val playPauseFocusRequester = remember { FocusRequester() }

    LaunchedEffect(osdVisible, showEpisodesDrawer, showTracksDialog) {
        if (osdVisible && !showEpisodesDrawer && !showTracksDialog) {
            delay(60L)
            runCatching { playPauseFocusRequester.requestFocus() }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val videoRate = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    .firstNotNullOfOrNull { group ->
                        (0 until group.length)
                            .firstOrNull { group.isTrackSelected(it) }
                            ?.let { idx -> group.getTrackFormat(idx).frameRate }
                    } ?: 0f
                if (videoRate > 1f) {
                    detectedVideoFrameRate = videoRate
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val videoRate = player.videoFormat?.frameRate ?: 0f
                if (videoRate > 1f) {
                    detectedVideoFrameRate = videoRate
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(error.message, player.currentPosition.coerceAtLeast(0L))
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            activity?.window?.let { win ->
                val params = win.attributes
                if (params.preferredDisplayModeId != 0) {
                    params.preferredDisplayModeId = 0
                    win.attributes = params
                }
            }
            viewModel.reportStopped(player.currentPosition.coerceAtLeast(0L))
            player.release()
        }
    }

    // Apply live subtitle sync offset & re-parse buffered cues when user adjusts offset
    LaunchedEffect(state.subtitleOffsetMs) {
        subtitleOffsetUsRef.set(state.subtitleOffsetMs * 1000L)
        if (!hasInitializedOffsetEffect) {
            hasInitializedOffsetEffect = true
            return@LaunchedEffect
        }
        if (state.session != null && state.session?.isTrailer != true) {
            player.seekTo(player.currentPosition.coerceAtLeast(0L))
        }
        val sec = state.subtitleOffsetMs / 1000.0
        subtitleOffsetHudText = "Subtitle Sync: ${String.format(Locale.US, "%+.2fs", sec)}"
        delay(2500L)
        subtitleOffsetHudText = null
    }

    // Apply 24fps / frame-rate matching strategy & optional Display.Mode on TV
    LaunchedEffect(state.frameRateMatchMode, state.session?.isTrailer, detectedVideoFrameRate) {
        val isTrailer = state.session?.isTrailer == true
        val mode = state.frameRateMatchMode
        player.videoChangeFrameRateStrategy = if (isTrailer || mode == FrameRateMatchMode.OFF) {
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        } else {
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }

        val win = activity?.window ?: return@LaunchedEffect
        if (isTrailer || mode != FrameRateMatchMode.ALWAYS) {
            val params = win.attributes
            if (params.preferredDisplayModeId != 0) {
                params.preferredDisplayModeId = 0
                win.attributes = params
            }
            return@LaunchedEffect
        }

        if (detectedVideoFrameRate > 1f) {
            @Suppress("DEPRECATION")
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                activity.windowManager.defaultDisplay
            }
            if (display != null) {
                val bestMode = FrameRateMatcher.findBestAndroidDisplayMode(display, detectedVideoFrameRate, mode)
                if (bestMode != null && win.attributes.preferredDisplayModeId != bestMode.modeId) {
                    val params = win.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    win.attributes = params
                }
            }
        }
    }

    LaunchedEffect(state.session?.itemId, state.session?.streamUrl) {
        val session = state.session ?: return@LaunchedEffect
        detectedVideoFrameRate = 0f
        autoSkipUndoPrompt = null
        if (session.trailerAudioUrl != null) {
            val dsFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            val vSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(session.streamUrl))
            val aSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(session.trailerAudioUrl))
            player.setMediaSource(MergingMediaSource(vSrc, aSrc))
        } else {
            val mediaItemBuilder = MediaItem.Builder().setUri(session.streamUrl)
            if (session.isHls) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            val subConfigs = session.externalSubtitleFiles.mapNotNull { ext ->
                val isAss = ext.codec.equals("ass", ignoreCase = true) || ext.codec.equals("ssa", ignoreCase = true)
                if (isAss && state.assSubtitleMode != AssSubtitleMode.PLAIN) return@mapNotNull null
                val mimeType = when (ext.codec?.lowercase()) {
                    "vtt", "webvtt" -> MimeTypes.TEXT_VTT
                    "ass", "ssa" -> MimeTypes.TEXT_SSA
                    else -> MimeTypes.APPLICATION_SUBRIP
                }
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(ext.url))
                    .setMimeType(mimeType)
                    .setLanguage(ext.language)
                    .setLabel(ext.title)
                    .build()
            }
            if (subConfigs.isNotEmpty()) {
                mediaItemBuilder.setSubtitleConfigurations(subConfigs)
            }
            player.setMediaItem(mediaItemBuilder.build())
        }

        // Apply profile preferred audio & subtitle defaults
        val paramsBuilder = player.trackSelectionParameters.buildUpon()
        state.preferredAudioLang?.takeIf { it.isNotBlank() }?.let { lang ->
            paramsBuilder.setPreferredAudioLanguage(lang)
        }
        when (val subLang = state.preferredSubtitleLang) {
            "off" -> paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            null, "" -> {}
            else -> {
                paramsBuilder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                paramsBuilder.setPreferredTextLanguage(subLang)
            }
        }
        player.trackSelectionParameters = paramsBuilder.build()

        val resumeMs = (session.resumeTicks / 10_000L).coerceAtLeast(0L)
        if (resumeMs > 5_000L) {
            player.seekTo(resumeMs)
        }
        player.prepare()
        player.play()
        viewModel.reportStart(resumeMs)
    }

    LaunchedEffect(state.selectedSpeed) {
        player.setPlaybackSpeed(state.selectedSpeed)
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
    LaunchedEffect(osdVisible, showTracksDialog, showEpisodesDrawer, scrubPreviewMs, isPlaying) {
        if (osdVisible && !showTracksDialog && !showEpisodesDrawer && scrubPreviewMs == null && isPlaying) {
            delay(4500L)
            osdVisible = false
        }
    }

    val session = state.session
    val currentTicks = currentPosMs * 10_000L

    // Loop-safe Auto-Skip Recap & Intro
    LaunchedEffect(currentTicks, session?.itemId, state.autoSkipIntro, state.autoSkipRecap) {
        val activeSession = session ?: return@LaunchedEffect
        if (AutoSkipHelper.shouldAutoSkip(
                enabled = state.autoSkipRecap,
                isTrailer = activeSession.isTrailer,
                itemId = activeSession.itemId,
                currentTicks = currentTicks,
                startTicks = activeSession.recapStartTicks,
                endTicks = activeSession.recapEndTicks,
                alreadySkippedItemId = skippedRecapItemId
            )
        ) {
            skippedRecapItemId = activeSession.itemId
            val startMs = (activeSession.recapStartTicks ?: 0L) / 10_000L
            val targetMs = (activeSession.recapEndTicks ?: 0L) / 10_000L
            player.seekTo(targetMs)
            currentPosMs = targetMs
            autoSkipUndoPrompt = SkipSegmentType.RECAP to startMs
        } else if (AutoSkipHelper.shouldAutoSkip(
                enabled = state.autoSkipIntro,
                isTrailer = activeSession.isTrailer,
                itemId = activeSession.itemId,
                currentTicks = currentTicks,
                startTicks = activeSession.introStartTicks,
                endTicks = activeSession.introEndTicks,
                alreadySkippedItemId = skippedIntroItemId
            )
        ) {
            skippedIntroItemId = activeSession.itemId
            val startMs = (activeSession.introStartTicks ?: 0L) / 10_000L
            val targetMs = (activeSession.introEndTicks ?: 0L) / 10_000L
            player.seekTo(targetMs)
            currentPosMs = targetMs
            autoSkipUndoPrompt = SkipSegmentType.INTRO to startMs
        }
    }

    LaunchedEffect(autoSkipUndoPrompt) {
        if (autoSkipUndoPrompt != null) {
            delay(4000L)
            autoSkipUndoPrompt = null
        }
    }

    val showSkipRecap = session?.recapStartTicks != null &&
        session.recapEndTicks != null &&
        currentTicks in session.recapStartTicks..session.recapEndTicks

    val showSkipIntro = session?.introStartTicks != null &&
        session.introEndTicks != null &&
        currentTicks in session.introStartTicks..session.introEndTicks

    val showNextEpisode = session?.nextEpisodeId != null && (
        (session.creditsStartTicks != null && currentTicks >= session.creditsStartTicks) ||
            (durationMs > 60_000L && currentPosMs >= durationMs - 25_000L)
        )

    var nextEpisodeCountdown by remember(session?.itemId, state.autoplayNextEpisode, state.autoplayCancelled) {
        mutableIntStateOf(5)
    }

    LaunchedEffect(showNextEpisode, session?.itemId, state.autoplayNextEpisode, state.autoplayCancelled) {
        if (showNextEpisode && state.autoplayNextEpisode && !state.autoplayCancelled && session?.nextEpisodeId != null) {
            nextEpisodeCountdown = 5
            while (nextEpisodeCountdown > 0) {
                delay(1000L)
                nextEpisodeCountdown--
            }
            viewModel.reportStopped(currentPosMs)
            viewModel.loadItem(session.nextEpisodeId)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_BACK -> {
                            when {
                                showEpisodesDrawer -> { showEpisodesDrawer = false; true }
                                showTracksDialog -> { showTracksDialog = false; true }
                                else -> false
                            }
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            if (player.isPlaying) {
                                player.pause()
                                viewModel.notifySyncPlayPause()
                            } else {
                                player.play()
                                viewModel.notifySyncPlayUnpause()
                            }
                            osdVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER,
                        KeyEvent.KEYCODE_ENTER -> {
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
                            if (!osdVisible && session?.isLiveTv != true) {
                                val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                player.seekTo(target)
                                viewModel.notifySyncPlaySeek(target)
                                currentPosMs = target
                                osdVisible = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!osdVisible && session?.isLiveTv != true) {
                                val target = player.currentPosition + 10_000L
                                player.seekTo(target)
                                viewModel.notifySyncPlaySeek(target)
                                currentPosMs = target
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
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            state.errorMessage != null -> {
                val errorRetryFocusRequester = remember { FocusRequester() }
                LaunchedEffect(state.errorMessage) {
                    delay(60L)
                    runCatching { errorRetryFocusRequester.requestFocus() }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage!!, color = colors.accent, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeroPillButton(
                            text = "Retry",
                            icon = Icons.Filled.Refresh,
                            isPrimary = true,
                            onClick = { viewModel.retryPlayback() },
                            modifier = Modifier.focusRequester(errorRetryFocusRequester)
                        )
                        if (session != null && !session.isTrailer) {
                            HeroPillButton(
                                text = AppStrings.current.actionExternalPlayer,
                                icon = Icons.AutoMirrored.Filled.OpenInNew,
                                isPrimary = false,
                                onClick = { viewModel.openInExternalPlayer(context, currentPosMs) }
                            )
                        }
                        HeroPillButton(
                            text = "Back",
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            isPrimary = false,
                            onClick = onBack
                        )
                    }
                }
            }
            else -> {
                val useAssOverlay = state.assSubtitleMode == AssSubtitleMode.STYLED && state.activeAssCues.isNotEmpty()
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            this.player = player
                        }
                    },
                    update = { view ->
                        if (view.player !== player) view.player = player
                        view.subtitleView?.let { sv ->
                            sv.visibility = if (useAssOverlay) View.GONE else View.VISIBLE
                            sv.setApplyEmbeddedStyles(false)
                            sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * state.subtitleFontScale)
                            val alphaInt = (state.subtitleBackgroundAlpha * 255f).toInt().coerceIn(0, 255)
                            val bgArgb = android.graphics.Color.argb(alphaInt, 0, 0, 0)
                            sv.setStyle(
                                CaptionStyleCompat(
                                    android.graphics.Color.WHITE,
                                    bgArgb,
                                    android.graphics.Color.TRANSPARENT,
                                    CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                                    android.graphics.Color.BLACK,
                                    null
                                )
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (useAssOverlay) {
                    AssSubtitleOverlay(
                        cues = state.activeAssCues,
                        positionMs = currentPosMs + state.subtitleOffsetMs,
                        fontScale = state.subtitleFontScale,
                        backgroundAlpha = state.subtitleBackgroundAlpha
                    )
                }

                // Stream Info HUD Badge (top-right)
                if (state.showStreamInfo && session != null) {
                    StreamInfoOverlay(
                        session = session,
                        selectedQuality = state.selectedQuality,
                        playbackSpeed = state.selectedSpeed,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 28.dp, end = 48.dp)
                    )
                }

                // Subtitle Offset / Feedback Quick HUD Badge (top-center)
                (subtitleOffsetHudText ?: state.feedbackMessage)?.let { hudText ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.navBarBackground)
                            .border(1.dp, colors.badgeBorder, RoundedCornerShape(6.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = hudText,
                            color = colors.white,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Skip Recap / Skip Intro / Auto-Skip Undo Floating Pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 48.dp, bottom = if (osdVisible) 136.dp else 48.dp)
                ) {
                    autoSkipUndoPrompt?.let { (segmentType, seekBackMs) ->
                        HeroPillButton(
                            text = "Skipped ${segmentType.label} • Undo",
                            icon = Icons.AutoMirrored.Filled.Undo,
                            isPrimary = false,
                            onClick = {
                                player.seekTo(seekBackMs)
                                viewModel.notifySyncPlaySeek(seekBackMs)
                                currentPosMs = seekBackMs
                                autoSkipUndoPrompt = null
                            }
                        )
                    }
                    if (showSkipRecap && session?.recapEndTicks != null) {
                        HeroPillButton(
                            text = "Skip Recap",
                            icon = Icons.Filled.SkipNext,
                            isPrimary = true,
                            onClick = {
                                val target = session.recapEndTicks / 10_000L
                                player.seekTo(target)
                                viewModel.notifySyncPlaySeek(target)
                            }
                        )
                    }
                    if (showSkipIntro && session?.introEndTicks != null) {
                        HeroPillButton(
                            text = "Skip Intro",
                            icon = Icons.Filled.SkipNext,
                            isPrimary = true,
                            onClick = {
                                val target = session.introEndTicks / 10_000L
                                player.seekTo(target)
                                viewModel.notifySyncPlaySeek(target)
                            }
                        )
                    }
                }

                // Next Episode Overlay Card with Auto-Play Countdown
                if (showNextEpisode && session?.nextEpisodeId != null && !showEpisodesDrawer) {
                    NextEpisodeOverlayCard(
                        nextEpisodeTitle = session.nextEpisodeTitle ?: "Next Episode",
                        autoplayEnabled = state.autoplayNextEpisode && !state.autoplayCancelled,
                        countdownSeconds = nextEpisodeCountdown,
                        onPlayNow = {
                            viewModel.reportStopped(currentPosMs)
                            viewModel.loadItem(session.nextEpisodeId)
                        },
                        onWatchCredits = viewModel::cancelAutoplayNext,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 48.dp, bottom = if (osdVisible) 136.dp else 48.dp)
                    )
                }

                // OSD Overlay
                AnimatedVisibility(
                    visible = osdVisible && !showEpisodesDrawer,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top gradient & title + SyncPlay room badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent))
                                )
                                .padding(horizontal = 48.dp, vertical = 28.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlayerControlIcon(Icons.AutoMirrored.Filled.ArrowBack, "Back", onClick = onBack)
                                Spacer(Modifier.width(18.dp))
                                Column {
                                    Text(
                                        text = session?.title.orEmpty(),
                                        color = colors.white,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val activeChapter = session?.chapters?.lastOrNull { it.startPositionMs <= currentPosMs }
                                    val subtitleParts = listOfNotNull(
                                        session?.subtitle,
                                        activeChapter?.name
                                    )
                                    if (subtitleParts.isNotEmpty()) {
                                        Text(
                                            text = subtitleParts.joinToString(" • "),
                                            color = colors.textSecondary,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            state.syncPlayGroup?.let { group ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.accent.copy(alpha = 0.22f))
                                        .border(1.dp, colors.accent, RoundedCornerShape(16.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Filled.Groups, contentDescription = null, tint = colors.white, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "${AppStrings.current.actionWatchTogether}: ${group.groupName} (${group.participants.size})",
                                        color = colors.white,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
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
                            // Seekbar Trickplay Thumbnail Preview
                            val previewTargetMs = scrubPreviewMs
                            if (previewTargetMs != null && durationMs > 0L) {
                                val crop = session?.trickplayInfo?.cropForPosition(previewTargetMs)
                                val frac = (previewTargetMs.toFloat() / durationMs.toFloat()).coerceIn(0.05f, 0.95f)
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    if (frac > 0.06f) {
                                        Spacer(Modifier.weight(frac))
                                    }
                                    TrickplayPreviewCard(
                                        positionMs = previewTargetMs,
                                        crop = crop
                                    )
                                    if (1f - frac > 0.06f) {
                                        Spacer(Modifier.weight(1f - frac))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            val totalFloat = durationMs.coerceAtLeast(1L).toFloat()
                            val sliderPosMs = scrubPreviewMs ?: currentPosMs
                            Box(modifier = Modifier.fillMaxWidth()) {
                                Slider(
                                    value = (sliderPosMs.toFloat() / totalFloat).coerceIn(0f, 1f),
                                    onValueChange = { frac ->
                                        val targetMs = (frac * totalFloat).toLong()
                                        scrubPreviewMs = targetMs
                                        player.seekTo(targetMs)
                                    },
                                    onValueChangeFinished = {
                                        val target = scrubPreviewMs
                                        scrubPreviewMs = null
                                        if (target != null) {
                                            viewModel.notifySyncPlaySeek(target)
                                        }
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accent,
                                        activeTrackColor = colors.accent,
                                        inactiveTrackColor = Color(0x55FFFFFF)
                                    )
                                )
                                val chapters = session?.chapters.orEmpty()
                                if (chapters.isNotEmpty() && durationMs > 0L) {
                                    Canvas(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(6.dp)
                                            .align(Alignment.Center)
                                            .padding(horizontal = 10.dp)
                                    ) {
                                        chapters.forEach { ch ->
                                            if (ch.startPositionMs > 0L && ch.startPositionMs < durationMs) {
                                                val frac = (ch.startPositionMs.toFloat() / totalFloat).coerceIn(0.01f, 0.99f)
                                                val x = size.width * frac
                                                drawLine(
                                                    color = Color.White.copy(alpha = 0.85f),
                                                    start = Offset(x, 0f),
                                                    end = Offset(x, size.height),
                                                    strokeWidth = 3f
                                                )
                                            }
                                        }
                                    }
                                }
                            }

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
                                        onClick = {
                                            if (player.isPlaying) {
                                                player.pause()
                                                viewModel.notifySyncPlayPause()
                                            } else {
                                                player.play()
                                                viewModel.notifySyncPlayUnpause()
                                            }
                                        },
                                        modifier = Modifier.focusRequester(playPauseFocusRequester)
                                    )
                                    if (session?.isLiveTv != true) {
                                        PlayerControlIcon(
                                            icon = Icons.Filled.Replay10,
                                            label = "Back 10s",
                                            onClick = {
                                                val target = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                                                player.seekTo(target)
                                                viewModel.notifySyncPlaySeek(target)
                                            }
                                        )
                                        PlayerControlIcon(
                                            icon = Icons.Filled.Forward10,
                                            label = "Forward 10s",
                                            onClick = {
                                                val target = player.currentPosition + 10_000L
                                                player.seekTo(target)
                                                viewModel.notifySyncPlaySeek(target)
                                            }
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = if (session?.isLiveTv == true) "LIVE" else "${formatMs(currentPosMs)} / ${formatMs(durationMs)}",
                                        color = if (session?.isLiveTv == true) colors.accent else colors.textSecondary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (state.selectedSpeed != 1.0f) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(colors.surfaceElevated)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text("${state.selectedSpeed}x", color = colors.white, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    if (state.drawerSeasons.isNotEmpty() || !session?.seriesId.isNullOrBlank()) {
                                        PlayerControlIcon(
                                            icon = Icons.AutoMirrored.Filled.List,
                                            label = "Episodes",
                                            onClick = { showEpisodesDrawer = true }
                                        )
                                    }
                                    PlayerControlIcon(
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        label = AppStrings.current.actionExternalPlayer,
                                        onClick = {
                                            player.pause()
                                            viewModel.openInExternalPlayer(context, currentPosMs)
                                        }
                                    )
                                    PlayerControlIcon(
                                        icon = Icons.Filled.Subtitles,
                                        label = "Audio & Subtitles",
                                        onClick = { showTracksDialog = true }
                                    )
                                    PlayerControlIcon(
                                        icon = Icons.Filled.Settings,
                                        label = "Playback Settings",
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

                // In-Player Episodes Drawer Overlay
                if (showEpisodesDrawer) {
                    EpisodesDrawerOverlay(
                        seriesTitle = session?.title.orEmpty(),
                        seasons = state.drawerSeasons,
                        selectedSeason = state.drawerSelectedSeason,
                        episodes = state.drawerEpisodes,
                        isLoading = state.drawerEpisodesLoading,
                        currentEpisodeId = session?.itemId,
                        onSelectSeason = viewModel::selectDrawerSeason,
                        onSelectEpisode = { ep ->
                            if (ep.isAvailableInJellyfin) {
                                showEpisodesDrawer = false
                                viewModel.reportStopped(currentPosMs)
                                viewModel.loadItem(ep.id)
                            }
                        },
                        onClose = { showEpisodesDrawer = false }
                    )
                }
            }
        }
    }

    if (showTracksDialog) {
        PlayerSettingsDialog(
            player = player,
            session = session,
            isTrailer = session?.isTrailer == true,
            selectedSpeed = state.selectedSpeed,
            selectedQuality = state.selectedQuality,
            selectedMediaSourceId = state.selectedMediaSourceId,
            subtitleFontScale = state.subtitleFontScale,
            subtitleBackgroundAlpha = state.subtitleBackgroundAlpha,
            subtitleOffsetMs = state.subtitleOffsetMs,
            autoSkipIntro = state.autoSkipIntro,
            autoSkipRecap = state.autoSkipRecap,
            frameRateMatchMode = state.frameRateMatchMode,
            audioPassthroughEnabled = state.audioPassthroughEnabled,
            stereoDownmixEnabled = state.stereoDownmixEnabled,
            assSubtitleMode = state.assSubtitleMode,
            activeAssSubtitleIndex = state.activeAssSubtitleIndex,
            remoteSubtitles = state.remoteSubtitles,
            remoteSubtitlesLoading = state.remoteSubtitlesLoading,
            remoteSubtitleLanguage = state.remoteSubtitleLanguage,
            downloadingSubtitleId = state.downloadingSubtitleId,
            subtitleSearchMessage = state.subtitleSearchMessage,
            showStreamInfo = state.showStreamInfo,
            onSelectSpeed = viewModel::setPlaybackSpeed,
            onSelectQuality = { preset ->
                showTracksDialog = false
                viewModel.selectQuality(preset, currentPosMs)
            },
            onSwitchMediaSource = { sourceId ->
                showTracksDialog = false
                viewModel.switchMediaSource(sourceId, currentPosMs)
            },
            onJumpToChapter = { chapter ->
                showTracksDialog = false
                player.seekTo(chapter.startPositionMs)
                viewModel.notifySyncPlaySeek(chapter.startPositionMs)
                currentPosMs = chapter.startPositionMs
            },
            onSetSubtitleAppearance = viewModel::setSubtitleAppearance,
            onAdjustSubtitleOffset = viewModel::adjustSubtitleOffset,
            onResetSubtitleOffset = viewModel::resetSubtitleOffset,
            onToggleAutoSkipIntro = viewModel::toggleAutoSkipIntro,
            onToggleAutoSkipRecap = viewModel::toggleAutoSkipRecap,
            onSelectFrameRateMatchMode = viewModel::setFrameRateMatchMode,
            onToggleAudioPassthrough = {
                showTracksDialog = false
                viewModel.toggleAudioPassthrough(currentPosMs)
            },
            onToggleStereoDownmix = {
                showTracksDialog = false
                viewModel.toggleStereoDownmix(currentPosMs)
            },
            onSelectAssMode = { mode ->
                viewModel.setAssSubtitleMode(mode, currentPosMs)
            },
            onSelectExternalAssTrack = { ext ->
                viewModel.selectExternalAssTrack(ext, currentPosMs)
                showTracksDialog = false
            },
            onOpenExternalPlayer = {
                showTracksDialog = false
                player.pause()
                viewModel.openInExternalPlayer(context, currentPosMs)
            },
            onSearchRemoteSubtitles = viewModel::searchRemoteSubtitles,
            onDownloadRemoteSubtitle = { subId ->
                showTracksDialog = false
                viewModel.downloadRemoteSubtitle(subId, currentPosMs)
            },
            onToggleStreamInfo = viewModel::toggleStreamInfo,
            onDismiss = { showTracksDialog = false }
        )
    }
}

private fun Modifier.wrapAlignEnd(): Modifier = this

@Composable
fun StreamInfoOverlay(
    session: PlaybackSessionData,
    selectedQuality: QualityPreset,
    playbackSpeed: Float,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    Column(
        modifier = modifier
            .clip(corner)
            .background(colors.dialogSurface)
            .border(1.dp, colors.badgeBorder, corner)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        val videoParts = listOfNotNull(session.resolutionLabel, session.hdrFormat, session.videoCodec)
        Text(
            text = videoParts.joinToString(" • ").ifBlank { "Video Stream" },
            color = colors.white,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        session.audioCodec?.let {
            Text(text = "Audio: $it", color = colors.textSecondary, fontSize = 11.sp)
        }
        Text(
            text = "Quality: ${selectedQuality.label} • ${playbackSpeed}x",
            color = colors.textSecondary,
            fontSize = 11.sp
        )
    }
}

@Composable
fun NextEpisodeOverlayCard(
    nextEpisodeTitle: String,
    autoplayEnabled: Boolean,
    countdownSeconds: Int,
    onPlayNow: () -> Unit,
    onWatchCredits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    Column(
        modifier = modifier
            .width(340.dp)
            .clip(corner)
            .background(colors.dialogSurface)
            .border(1.dp, colors.badgeBorder, corner)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (autoplayEnabled) "Next Episode in ${countdownSeconds}s" else "Up Next",
            color = if (autoplayEnabled) colors.accent else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = nextEpisodeTitle,
            color = colors.white,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HeroPillButton(
                text = "Play Now",
                icon = Icons.Filled.SkipNext,
                isPrimary = true,
                onClick = onPlayNow
            )
            if (autoplayEnabled) {
                HeroPillButton(
                    text = "Watch Credits",
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = onWatchCredits
                )
            }
        }
    }
}

@Composable
private fun TrickplayPreviewCard(
    positionMs: Long,
    crop: TrickplayCrop?,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val context = LocalContext.current
    var croppedBitmap by remember(crop?.tileUrl, crop?.x, crop?.y) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(crop?.tileUrl, crop?.x, crop?.y) {
        if (crop == null) {
            croppedBitmap = null
            return@LaunchedEffect
        }
        runCatching {
            val loader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(crop.tileUrl)
                .allowHardware(false)
                .build()
            val result = loader.execute(request) as? SuccessResult ?: return@runCatching
            val fullBmp = result.image.toBitmap()
            val safeX = crop.x.coerceIn(0, (fullBmp.width - 1).coerceAtLeast(0))
            val safeY = crop.y.coerceIn(0, (fullBmp.height - 1).coerceAtLeast(0))
            val safeW = crop.width.coerceAtMost(fullBmp.width - safeX).coerceAtLeast(1)
            val safeH = crop.height.coerceAtMost(fullBmp.height - safeY).coerceAtLeast(1)
            croppedBitmap = Bitmap.createBitmap(fullBmp, safeX, safeY, safeW, safeH).asImageBitmap()
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (croppedBitmap != null) {
            Box(
                modifier = Modifier
                    .width(176.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, colors.focusBorder, RoundedCornerShape(6.dp))
                    .background(Color.Black)
            ) {
                Image(
                    bitmap = croppedBitmap!!,
                    contentDescription = "Seek preview",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Spacer(Modifier.height(6.dp))
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.dialogSurface)
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = formatMs(positionMs),
                color = colors.white,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EpisodesDrawerOverlay(
    seriesTitle: String,
    seasons: List<SeasonSummary>,
    selectedSeason: Int,
    episodes: List<EpisodeSummary>,
    isLoading: Boolean,
    currentEpisodeId: String?,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (EpisodeSummary) -> Unit,
    onClose: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val cardCorner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xAA000000))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(520.dp)
                .fillMaxHeight()
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder)
                .padding(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = seriesTitle.ifBlank { "Episodes" },
                        color = colors.white,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Select an episode to play", color = colors.textSecondary, fontSize = 12.sp)
                }
                PlayerControlIcon(Icons.Filled.Close, "Close", onClick = onClose)
            }

            if (seasons.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(seasons, key = { it.seasonNumber }) { season ->
                        var focused by remember { mutableStateOf(false) }
                        val selected = season.seasonNumber == selectedSeason
                        val tabCorner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)
                        Box(
                            modifier = Modifier
                                .clip(tabCorner)
                                .background(
                                    when {
                                        selected -> colors.white
                                        focused -> colors.surfaceElevated
                                        else -> colors.surface
                                    }
                                )
                                .then(
                                    if (focused) Modifier.border(2.dp, colors.accent, tabCorner)
                                    else Modifier
                                )
                                .onFocusChanged { focused = it.isFocused }
                                .clickable { onSelectSeason(season.seasonNumber) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = season.name,
                                color = if (selected) colors.deepBlack else colors.white,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else {
                val activeEpisodeFocusRequester = remember { FocusRequester() }
                val targetFocusEpisodeId = remember(episodes, currentEpisodeId) {
                    episodes.firstOrNull { it.id == currentEpisodeId }?.id ?: episodes.firstOrNull()?.id
                }
                LaunchedEffect(targetFocusEpisodeId) {
                    if (targetFocusEpisodeId != null) {
                        delay(60L)
                        runCatching { activeEpisodeFocusRequester.requestFocus() }
                    }
                }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(episodes, key = { it.id }) { ep ->
                        var focused by remember { mutableStateOf(false) }
                        val isCurrent = ep.id == currentEpisodeId
                        val isFocusTarget = ep.id == targetFocusEpisodeId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isFocusTarget) Modifier.focusRequester(activeEpisodeFocusRequester) else Modifier)
                                .clip(cardCorner)
                                .background(
                                    when {
                                        focused -> colors.surfaceElevated
                                        isCurrent -> colors.surfaceElevated.copy(alpha = 0.7f)
                                        else -> colors.surface
                                    }
                                )
                                .then(
                                    if (focused) Modifier.border(2.dp, colors.focusBorder, cardCorner)
                                    else if (isCurrent) Modifier.border(1.dp, colors.accent, cardCorner)
                                    else Modifier
                                )
                                .onFocusChanged { focused = it.isFocused }
                                .clickable { onSelectEpisode(ep) }
                                .padding(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(120.dp)
                                    .aspectRatio(16f / 9f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.surfaceElevated)
                            ) {
                                AsyncImage(
                                    model = ep.stillUrl,
                                    contentDescription = ep.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${ep.episodeNumber}. ${ep.name}",
                                    color = colors.white,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    text = if (!ep.isAvailableInJellyfin) "Not available on Jellyfin"
                                    else if (isCurrent) "Now Playing"
                                    else ep.overview.ifBlank { "${ep.runtimeMinutes}m" },
                                    color = if (isCurrent) colors.accent else colors.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (focused) colors.white else colors.surface.copy(alpha = 0.72f))
            .then(if (focused) Modifier.border(2.dp, colors.accent, RoundedCornerShape(23.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused) colors.deepBlack else colors.white,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayerSettingsDialog(
    player: ExoPlayer,
    session: PlaybackSessionData?,
    isTrailer: Boolean,
    selectedSpeed: Float,
    selectedQuality: QualityPreset,
    selectedMediaSourceId: String?,
    subtitleFontScale: Float,
    subtitleBackgroundAlpha: Float,
    subtitleOffsetMs: Long,
    autoSkipIntro: Boolean,
    autoSkipRecap: Boolean,
    frameRateMatchMode: FrameRateMatchMode,
    audioPassthroughEnabled: Boolean,
    stereoDownmixEnabled: Boolean,
    assSubtitleMode: AssSubtitleMode,
    activeAssSubtitleIndex: Int?,
    remoteSubtitles: List<RemoteSubtitleOption>,
    remoteSubtitlesLoading: Boolean,
    remoteSubtitleLanguage: String,
    downloadingSubtitleId: String?,
    subtitleSearchMessage: String?,
    showStreamInfo: Boolean,
    onSelectSpeed: (Float) -> Unit,
    onSelectQuality: (QualityPreset) -> Unit,
    onSwitchMediaSource: (String) -> Unit,
    onJumpToChapter: (ChapterMarker) -> Unit,
    onSetSubtitleAppearance: (Float, Float) -> Unit,
    onAdjustSubtitleOffset: (Long) -> Unit,
    onResetSubtitleOffset: () -> Unit,
    onToggleAutoSkipIntro: () -> Unit,
    onToggleAutoSkipRecap: () -> Unit,
    onSelectFrameRateMatchMode: (FrameRateMatchMode) -> Unit,
    onToggleAudioPassthrough: () -> Unit,
    onToggleStereoDownmix: () -> Unit,
    onSelectAssMode: (AssSubtitleMode) -> Unit,
    onSelectExternalAssTrack: (ExternalSubtitleFile?) -> Unit,
    onOpenExternalPlayer: () -> Unit,
    onSearchRemoteSubtitles: (String) -> Unit,
    onDownloadRemoteSubtitle: (String) -> Unit,
    onToggleStreamInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val closeDialogFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60L)
        runCatching { closeDialogFocusRequester.requestFocus() }
    }
    val groups = player.currentTracks.groups
    val audioGroups = groups.filter { it.type == C.TRACK_TYPE_AUDIO }
    val textGroups = groups.filter { it.type == C.TRACK_TYPE_TEXT }
    val selectedTextMime = textGroups
        .firstOrNull { it.isSelected }
        ?.let { group ->
            (0 until group.length)
                .firstOrNull { group.isTrackSelected(it) }
                ?.let { idx -> group.getTrackFormat(idx).sampleMimeType }
        }
    val isBitmapSubtitle = selectedTextMime == MimeTypes.APPLICATION_PGS ||
        selectedTextMime == MimeTypes.APPLICATION_VOBSUB ||
        selectedTextMime == MimeTypes.APPLICATION_DVBSUBS
    val assExternalTracks = session?.externalSubtitleFiles.orEmpty().filter {
        it.codec.equals("ass", ignoreCase = true) ||
            it.codec.equals("ssa", ignoreCase = true) ||
            it.url.contains(".ass", ignoreCase = true) ||
            it.url.contains(".ssa", ignoreCase = true)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(580.dp)
                .clip(dialogCorner)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogCorner)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Playback Settings", color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                PlayerControlIcon(
                    icon = Icons.Filled.Close,
                    label = "Close",
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(closeDialogFocusRequester)
                )
            }

            // Versions & Editions (Multiple MediaSources)
            val sources = session?.mediaSources.orEmpty()
            if (!isTrailer && sources.size > 1) {
                Text("Versions & Editions", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                sources.forEach { src ->
                    TrackChoiceRow(
                        label = src.displayLabel,
                        selected = src.id == (selectedMediaSourceId ?: session?.mediaSourceId),
                        onClick = { onSwitchMediaSource(src.id) }
                    )
                }
            }

            // Chapter Jump List
            val chapters = session?.chapters.orEmpty()
            if (!isTrailer && chapters.isNotEmpty()) {
                Text("Chapters (${chapters.size})", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                chapters.take(12).forEach { chapter ->
                    TrackChoiceRow(
                        label = "${formatMs(chapter.startPositionMs)} • ${chapter.name}",
                        selected = false,
                        onClick = { onJumpToChapter(chapter) }
                    )
                }
            }

            if (audioGroups.isNotEmpty()) {
                Text("Audio Tracks", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                Text("Subtitles", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TrackChoiceRow(
                    label = "Off",
                    selected = textGroups.none { it.isSelected } && activeAssSubtitleIndex == null,
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                            .build()
                        onSelectExternalAssTrack(null)
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

            // ASS / SSA Anime & Styled Subtitle Engine
            if (!isTrailer && assExternalTracks.isNotEmpty()) {
                Text("ASS / SSA Subtitle Engine", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssSubtitleMode.entries.forEach { mode ->
                        SettingChip(
                            label = mode.label,
                            selected = assSubtitleMode == mode,
                            onClick = { onSelectAssMode(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                assExternalTracks.forEach { ext ->
                    val label = listOfNotNull(
                        ext.language?.uppercase(),
                        ext.title.ifBlank { "ASS Track #${ext.index}" },
                        ext.codec?.uppercase()
                    ).distinct().joinToString(" • ")
                    TrackChoiceRow(
                        label = label,
                        selected = activeAssSubtitleIndex == ext.index,
                        onClick = { onSelectExternalAssTrack(ext) }
                    )
                }
            }

            // Subtitle Timing Sync (±ms)
            if (!isTrailer) {
                val offsetSec = subtitleOffsetMs / 1000.0
                val offsetLabel = String.format(Locale.US, "%+.2fs", offsetSec)
                Text(
                    text = "Subtitle Sync ($offsetLabel)",
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isBitmapSubtitle) {
                    Text(
                        text = "Text subtitles only — bitmap tracks (PGS/VobSub) cannot be shifted.",
                        color = Color(0xFFFFB74D),
                        fontSize = 11.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingChip(label = "-0.5s", selected = false, onClick = { onAdjustSubtitleOffset(-500L) }, modifier = Modifier.weight(1f))
                    SettingChip(label = "-0.25s", selected = false, onClick = { onAdjustSubtitleOffset(-250L) }, modifier = Modifier.weight(1f))
                    SettingChip(label = "0.0s", selected = subtitleOffsetMs == 0L, onClick = onResetSubtitleOffset, modifier = Modifier.weight(1f))
                    SettingChip(label = "+0.25s", selected = false, onClick = { onAdjustSubtitleOffset(250L) }, modifier = Modifier.weight(1f))
                    SettingChip(label = "+0.5s", selected = false, onClick = { onAdjustSubtitleOffset(500L) }, modifier = Modifier.weight(1f))
                }

                // On-Demand Jellyfin Remote Subtitle Search / Download
                Text("Search / Download Subtitles", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("eng" to "EN", "spa" to "ES", "fra" to "FR", "jpn" to "JP").forEach { (code, label) ->
                        SettingChip(
                            label = label,
                            selected = remoteSubtitleLanguage == code,
                            onClick = { onSearchRemoteSubtitles(code) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SettingChip(
                        label = if (remoteSubtitlesLoading) "Searching…" else "Search",
                        selected = false,
                        onClick = { onSearchRemoteSubtitles(remoteSubtitleLanguage) },
                        modifier = Modifier.weight(1.3f)
                    )
                }
                subtitleSearchMessage?.let { msg ->
                    Text(msg, color = colors.textSecondary, fontSize = 12.sp)
                }
                if (remoteSubtitles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        remoteSubtitles.take(5).forEach { sub ->
                            val details = listOfNotNull(
                                sub.format?.uppercase(),
                                sub.downloadCount?.let { "$it dl" },
                                if (sub.communityRating != null) String.format(Locale.US, "★%.1f", sub.communityRating) else null
                            ).joinToString(" • ")
                            val label = if (details.isBlank()) sub.name else "${sub.name} ($details)"
                            TrackChoiceRow(
                                label = label,
                                selected = downloadingSubtitleId == sub.id,
                                onClick = { onDownloadRemoteSubtitle(sub.id) }
                            )
                        }
                    }
                }
            }

            // Playback Speed
            Text("Playback Speed", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                    SettingChip(
                        label = "${spd}x",
                        selected = selectedSpeed == spd,
                        onClick = { onSelectSpeed(spd) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Quality / Bitrate Cap
            if (!isTrailer) {
                Text("Video Quality", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                QualityPreset.entries.forEach { preset ->
                    TrackChoiceRow(
                        label = preset.label,
                        selected = selectedQuality == preset,
                        onClick = { onSelectQuality(preset) }
                    )
                }
            }

            // Audio Routing & Home Theater
            if (!isTrailer) {
                Text("Home Theater & Audio Routing", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TrackChoiceRow(
                    label = "Audio Passthrough (HDMI / Atmos / DTS)",
                    selected = audioPassthroughEnabled,
                    onClick = onToggleAudioPassthrough
                )
                TrackChoiceRow(
                    label = "Stereo 2.0 Downmix (Night / Soundbar)",
                    selected = stereoDownmixEnabled,
                    onClick = onToggleStereoDownmix
                )
            }

            // Auto-Skip & 24fps Frame-Rate Matching
            if (!isTrailer) {
                Text("Automation & Display", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TrackChoiceRow(
                    label = "Auto-Skip Intro",
                    selected = autoSkipIntro,
                    onClick = onToggleAutoSkipIntro
                )
                TrackChoiceRow(
                    label = "Auto-Skip Recap",
                    selected = autoSkipRecap,
                    onClick = onToggleAutoSkipRecap
                )
                Text("24fps Frame-Rate Matching", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrameRateMatchMode.entries.forEach { mode ->
                        SettingChip(
                            label = mode.label,
                            selected = frameRateMatchMode == mode,
                            onClick = { onSelectFrameRateMatchMode(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Subtitle Size & Background
            Text("Subtitle Size", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Small" to 0.8f, "Medium" to 1.0f, "Large" to 1.3f).forEach { (label, scale) ->
                    SettingChip(
                        label = label,
                        selected = kotlin.math.abs(subtitleFontScale - scale) < 0.05f,
                        onClick = { onSetSubtitleAppearance(scale, subtitleBackgroundAlpha) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text("Subtitle Background", color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("None" to 0f, "50%" to 0.5f, "80%" to 0.8f).forEach { (label, alpha) ->
                    SettingChip(
                        label = label,
                        selected = kotlin.math.abs(subtitleBackgroundAlpha - alpha) < 0.05f,
                        onClick = { onSetSubtitleAppearance(subtitleFontScale, alpha) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Stream Info Overlay toggle & External Player launch
            TrackChoiceRow(
                label = "Stream Info Overlay",
                selected = showStreamInfo,
                onClick = onToggleStreamInfo
            )
            if (!isTrailer) {
                TrackChoiceRow(
                    label = AppStrings.current.actionExternalPlayer,
                    selected = false,
                    onClick = onOpenExternalPlayer
                )
            }
        }
    }
}

@Composable
private fun SettingChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(corner)
            .background(
                when {
                    selected -> colors.white
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, colors.accent, corner)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
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
private fun TrackChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(corner)
            .background(
                when {
                    focused -> colors.surfaceElevated
                    selected -> colors.surfaceElevated.copy(alpha = 0.7f)
                    else -> colors.surface
                }
            )
            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, corner) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = colors.white, fontSize = 14.sp)
        if (selected) {
            Text("Active", color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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

