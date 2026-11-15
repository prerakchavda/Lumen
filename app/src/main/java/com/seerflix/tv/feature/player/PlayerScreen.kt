package com.seerflix.tv.feature.player

import android.net.Uri
import android.os.Build
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.onKeyEvent
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaSession
import com.seerflix.tv.core.player.SyncPlayAction
import com.seerflix.tv.core.model.SubtitleEdgeStyle
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import coil3.compose.AsyncImage
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.i18n.UiStrings
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
import com.seerflix.tv.core.model.TrickplayInfo
import com.seerflix.tv.core.player.AssActiveCuesOverlay
import com.seerflix.tv.core.player.AssCue
import com.seerflix.tv.core.player.AssCueIndex
import com.seerflix.tv.core.player.AutoSkipHelper
import com.seerflix.tv.core.player.FrameRateMatcher
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.player.OffsetSubtitleParserFactory
import com.seerflix.tv.core.player.PlayerTuning
import com.seerflix.tv.core.player.SkipSegmentType
import com.seerflix.tv.core.player.TrickplayThumbnailLoader
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collectLatest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = remember(context) { FrameRateMatcher.findActivity(context) }

    val subtitleOffsetUsRef = remember { AtomicLong(0L) }
    val player = remember(context) {
        val subtitleParserFactory = OffsetSubtitleParserFactory { subtitleOffsetUsRef.get() }
        val upstreamFactory = OkHttpDataSource.Factory(viewModel.mediaHttpClient)
        val mediaSourceFactory = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, upstreamFactory))
            .setSubtitleParserFactory(subtitleParserFactory)
            .experimentalParseSubtitlesDuringExtraction(true)
        ExoPlayer.Builder(context, PlayerTuning.buildRenderersFactory(context))
            .setMediaSourceFactory(mediaSourceFactory)
            // Faster start + a heap-bounded buffer so 4K remuxes can't OOM 2 GB sticks.
            .setLoadControl(PlayerTuning.buildLoadControl())
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
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
    var syncPlayNotice by remember { mutableStateOf<String?>(null) }
    var hasInitializedOffsetEffect by remember { mutableStateOf(false) }
    // Written on every remote key press but only read inside effects (never during composition), so
    // key presses / held D-pad repeats don't recompose the whole screen.
    val lastInteractionTick = remember { mutableLongStateOf(0L) }
    val playPauseFocusRequester = remember { FocusRequester() }
    val trickplayLoader = remember { TrickplayThumbnailLoader(viewModel.mediaHttpClient) }
    DisposableEffect(trickplayLoader) { onDispose { trickplayLoader.close() } }

    // Relative seeks (D-pad, ±10 s, scrubbing) snap to a nearby keyframe: no decode-and-drop up to
    // an exact frame, which is what makes seeking in 4K HEVC remuxes sluggish on weak SoCs.
    // Absolute targets (skip intro/recap end, chapters, undo) stay frame-exact.
    val seekFast: (Long) -> Unit = { target ->
        player.setSeekParameters(PlayerTuning.FAST_SEEK_PARAMETERS)
        player.seekTo(target.coerceAtLeast(0L))
    }
    val seekExact: (Long) -> Unit = { target ->
        player.setSeekParameters(SeekParameters.DEFAULT)
        player.seekTo(target.coerceAtLeast(0L))
    }
    val rootFocusRequester = remember { FocusRequester() }
    // Window display mode before we touched it, restored on exit / when matching is turned off.
    val originalDisplayModeId = remember(activity) { activity?.window?.attributes?.preferredDisplayModeId ?: 0 }

    // Guard against double navigation (e.g. Back pressed twice during the exit transition popping
    // the screen underneath the player as well).
    var hasExited by remember { mutableStateOf(false) }
    val exitPlayer: () -> Unit = {
        if (!hasExited) {
            hasExited = true
            onBack()
        }
    }

    val togglePlayPause: () -> Unit = {
        // playWhenReady (not isPlaying) so a press while buffering pauses instead of being a no-op.
        if (viewModel.isSyncPlayActive()) {
            // Watching together: ask the group; the server's timed command pauses/plays every TV.
            if (player.playWhenReady) viewModel.requestGroupPause() else viewModel.requestGroupUnpause()
        } else if (player.playWhenReady) {
            player.pause()
        } else {
            player.play()
        }
    }

    LaunchedEffect(osdVisible, showEpisodesDrawer, showTracksDialog, state.isLoading, state.errorMessage) {
        // The error screen focuses its own Retry button.
        if (showEpisodesDrawer || showTracksDialog || state.errorMessage != null) return@LaunchedEffect
        delay(60L)
        if (osdVisible) {
            runCatching { playPauseFocusRequester.requestFocus() }
        } else {
            // Keep focus on the player surface while the OSD is hidden so D-pad / media keys keep
            // reaching onPreviewKeyEvent instead of being lost (or moving focus to a floating pill).
            runCatching { rootFocusRequester.requestFocus() }
        }
    }

    // Leaving the app (Home / input switch / screensaver) must not keep playing in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, player) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && player.playWhenReady) {
                player.pause()
                osdVisible = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // True once the current item has played to its end (STATE_ENDED); cleared as soon as the
    // player leaves that state (next item buffering, seek back, retry).
    var playbackEnded by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            // Pause / resume shows up right away in other Jellyfin apps (remote control, dashboard)
            // instead of at the next 10 s progress report.
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                viewModel.reportProgress(player.currentPosition.coerceAtLeast(0L), !playWhenReady)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playbackEnded = playbackState == Player.STATE_ENDED
                when (playbackState) {
                    Player.STATE_BUFFERING -> viewModel.reportSyncPlayState(
                        ready = false, positionMs = player.currentPosition, isPlaying = player.playWhenReady
                    )
                    Player.STATE_READY -> viewModel.reportSyncPlayState(
                        ready = true, positionMs = player.currentPosition, isPlaying = player.playWhenReady
                    )
                }
            }

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
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    // Live TV fell out of the DVR window: jump back to the live edge instead of failing.
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                viewModel.onPlaybackError(error.message, player.currentPosition.coerceAtLeast(0L))
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            activity?.window?.let { win ->
                val params = win.attributes
                if (params.preferredDisplayModeId != originalDisplayModeId) {
                    params.preferredDisplayModeId = originalDisplayModeId
                    win.attributes = params
                }
            }
            viewModel.reportStopped(player.currentPosition.coerceAtLeast(0L))
            player.release()
        }
    }

    // Media session: Google Assistant ("Hey Google, pause"), HDMI-CEC / Bluetooth remotes and the
    // system "Now playing" controls drive the player through it. Play/pause/seek coming from the
    // session go through the same SyncPlay routing as the on-screen controls.
    val mediaSession = remember(player) {
        val sessionPlayer = object : ForwardingPlayer(player) {
            override fun play() {
                if (viewModel.isSyncPlayActive()) viewModel.requestGroupUnpause() else super.play()
            }

            override fun pause() {
                if (viewModel.isSyncPlayActive()) viewModel.requestGroupPause() else super.pause()
            }

            override fun seekTo(positionMs: Long) {
                super.seekTo(positionMs)
                viewModel.notifySyncPlaySeek(positionMs)
            }
        }
        runCatching {
            MediaSession.Builder(context, sessionPlayer)
                .setId("lumen-player-${System.nanoTime()}")
                .build()
        }.getOrNull()
    }
    DisposableEffect(mediaSession) {
        onDispose { mediaSession?.release() }
    }

    // Apply live subtitle sync offset & re-parse buffered cues when user adjusts offset
    LaunchedEffect(state.subtitleOffsetMs) {
        subtitleOffsetUsRef.set(state.subtitleOffsetMs * 1000L)
        if (!hasInitializedOffsetEffect) {
            hasInitializedOffsetEffect = true
            return@LaunchedEffect
        }
        // Loading a new item resets the offset to 0 — nothing to re-apply and no HUD needed.
        if (state.isLoading) return@LaunchedEffect
        val sec = state.subtitleOffsetMs / 1000.0
        subtitleOffsetHudText = strings.playerSubtitleSyncHud.format(String.format(Locale.US, "%+.2fs", sec))
        val activeSession = state.session
        val assOverlayActive = state.assSubtitleMode == AssSubtitleMode.STYLED && state.activeAssCues.isNotEmpty()
        val textTrackSelected = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected }
        if (activeSession != null && !activeSession.isTrailer && !assOverlayActive && textTrackSelected) {
            // The offset is applied when cues are parsed, and a seek inside the buffer reuses the
            // already-parsed cues (side-loaded subtitle files are fully buffered), so a plain seekTo
            // never applied the new offset. Reload the current item in place (debounced so repeated
            // ±0.25s presses cause a single rebuffer).
            delay(SUBTITLE_OFFSET_RELOAD_DEBOUNCE_MS)
            if (!state.isLoading && state.session?.playSessionId == activeSession.playSessionId) {
                player.currentMediaItem?.let { item ->
                    player.setMediaItem(item, /* resetPosition = */ false)
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                }
            }
            delay(2500L - SUBTITLE_OFFSET_RELOAD_DEBOUNCE_MS)
        } else {
            delay(2500L)
        }
        subtitleOffsetHudText = null
    }

    // Apply 24fps / frame-rate matching strategy & optional Display.Mode on TV.
    // Declared before the media-setup effect: with Jellyfin's frame-rate metadata the display mode
    // switch (an HDMI resync of 1-3 s on most TVs) is requested before prepare() and overlaps with
    // initial buffering, instead of blanking the screen right after the first frame.
    LaunchedEffect(state.frameRateMatchMode, state.session?.isTrailer, detectedVideoFrameRate, state.videoFrameRateHint) {
        val isTrailer = state.session?.isTrailer == true
        val mode = state.frameRateMatchMode
        val videoFrameRate = detectedVideoFrameRate.takeIf { it > 1f } ?: state.videoFrameRateHint ?: 0f
        player.videoChangeFrameRateStrategy = if (isTrailer || mode == FrameRateMatchMode.OFF) {
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
        } else {
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }

        val win = activity?.window ?: return@LaunchedEffect
        if (isTrailer || mode != FrameRateMatchMode.ALWAYS) {
            val params = win.attributes
            if (params.preferredDisplayModeId != originalDisplayModeId) {
                params.preferredDisplayModeId = originalDisplayModeId
                win.attributes = params
            }
            return@LaunchedEffect
        }

        if (videoFrameRate > 1f) {
            @Suppress("DEPRECATION")
            val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                activity.windowManager.defaultDisplay
            }
            if (display != null) {
                val bestMode = FrameRateMatcher.findBestAndroidDisplayMode(display, videoFrameRate, mode)
                if (bestMode != null && win.attributes.preferredDisplayModeId != bestMode.modeId) {
                    val params = win.attributes
                    params.preferredDisplayModeId = bestMode.modeId
                    win.attributes = params
                }
            }
        }
    }

    // Keyed on playSessionId too: retry, audio/downmix toggles and subtitle downloads reload the
    // same item with an identical direct-play URL, and must still re-prepare the player (otherwise
    // a player left in the error state stays black).
    LaunchedEffect(state.session?.itemId, state.session?.playSessionId, state.session?.streamUrl) {
        val session = state.session ?: return@LaunchedEffect
        detectedVideoFrameRate = 0f
        autoSkipUndoPrompt = null
        // Drop the previous item's position so auto-skip / up-next don't evaluate the new item
        // against stale values before the progress loop catches up.
        currentPosMs = (session.resumeTicks / 10_000L).coerceAtLeast(0L)
        durationMs = 0L
        if (session.trailerAudioUrl != null) {
            val dsFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            val vSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(session.streamUrl))
            val aSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(session.trailerAudioUrl))
            player.setMediaSource(MergingMediaSource(vSrc, aSrc))
        } else {
            val mediaItemBuilder = MediaItem.Builder()
                .setUri(session.streamUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(session.title)
                        .setDisplayTitle(session.title)
                        .setSubtitle(session.subtitle)
                        .build()
                )
            if (session.isHls) {
                mediaItemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
            // Embedded text subtitles of a directly played MKV/MP4 are demuxed in-band by ExoPlayer;
            // side-loading them too delayed preparation until Jellyfin had extracted each one.
            val sourceContainer = session.mediaSources.firstOrNull { it.id == session.mediaSourceId }?.container
            val inBandSubtitleIndexes = session.subtitleTracks
                .filter {
                    JellyfinPlaybackManager.isInBandTextSubtitle(
                        container = sourceContainer,
                        codec = it.codec,
                        isExternal = it.isExternal,
                        isHls = session.isHls
                    )
                }
                .mapTo(HashSet()) { it.index }
            val subConfigs = session.externalSubtitleFiles.mapNotNull { ext ->
                if (ext.index in inBandSubtitleIndexes) return@mapNotNull null
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
            seekExact(resumeMs)
        }
        player.prepare()
        if (viewModel.isSyncPlayActive()) {
            // The group starts playback for everyone once all members report Ready.
            player.pause()
        } else {
            player.play()
        }
        viewModel.reportStart(resumeMs)
    }

    LaunchedEffect(state.selectedSpeed) {
        player.setPlaybackSpeed(state.selectedSpeed)
    }

    // Track position & report progress to Jellyfin every 10s.
    // currentPosMs is only read in narrowly scoped places (derived flags, the OSD seek bar, effects)
    // so this 2 Hz update doesn't recompose the whole screen.
    LaunchedEffect(state.session?.itemId) {
        var lastReportAt = System.currentTimeMillis()
        while (true) {
            isPlaying = player.isPlaying
            currentPosMs = player.currentPosition.coerceAtLeast(0L)
            durationMs = player.duration.coerceAtLeast(0L)
            val now = System.currentTimeMillis()
            if (now - lastReportAt >= PROGRESS_REPORT_INTERVAL_MS && state.session != null) {
                lastReportAt = now
                viewModel.reportProgress(currentPosMs, !player.playWhenReady)
            }
            // 4 Hz while the OSD (time label / seek bar) is on screen, 2 Hz otherwise.
            delay(if (osdVisible) 250L else 500L)
        }
    }

    // Stop the previous stream while the next one is resolved (next episode, version / quality /
    // audio switch, retry) so it doesn't keep downloading and decoding in the background,
    // competing with the new PlaybackInfo request and start-up buffering.
    LaunchedEffect(state.isLoading) {
        if (state.isLoading && player.playbackState != Player.STATE_IDLE) {
            player.stop()
        }
    }

    val useAssOverlay = state.assSubtitleMode == AssSubtitleMode.STYLED && state.activeAssCues.isNotEmpty()

    // Auto-hide Netflix OSD after 4.5s of no remote interaction
    val isScrubbing by remember { derivedStateOf { scrubPreviewMs != null } }
    LaunchedEffect(osdVisible, showTracksDialog, showEpisodesDrawer, isScrubbing, isPlaying) {
        if (osdVisible && !showTracksDialog && !showEpisodesDrawer && !isScrubbing && isPlaying) {
            // Restarts the countdown on every key press without recomposing on each press.
            snapshotFlow { lastInteractionTick.longValue }.collectLatest {
                delay(4500L)
                osdVisible = false
            }
        }
    }

    // Seek-bar scrubbing only moves the preview; the actual (keyframe-snapped) seek is debounced so
    // holding the D-pad doesn't fire a seek — and a decoder flush — on every step.
    LaunchedEffect(player) {
        snapshotFlow { scrubPreviewMs }.collectLatest { target ->
            if (target != null) {
                delay(SCRUB_SEEK_DEBOUNCE_MS)
                seekFast(target)
            }
        }
    }

    val session = state.session

    // Loop-safe Auto-Skip Recap & Intro. Evaluated on position updates (not as a composition key),
    // reading the latest session so markers that arrive after playback started are honoured.
    LaunchedEffect(session?.itemId) {
        snapshotFlow { currentPosMs }.collect {
            val activeSession = state.session ?: return@collect
            // Only evaluate once the new item is actually loaded and ready; right after a session switch
            // currentPosMs can still hold the previous item's position.
            if (state.isLoading || player.playbackState != Player.STATE_READY) return@collect
            val liveTicks = player.currentPosition.coerceAtLeast(0L) * 10_000L
            if (AutoSkipHelper.shouldAutoSkip(
                    enabled = state.autoSkipRecap,
                    isTrailer = activeSession.isTrailer,
                    itemId = activeSession.itemId,
                    currentTicks = liveTicks,
                    startTicks = activeSession.recapStartTicks,
                    endTicks = activeSession.recapEndTicks,
                    alreadySkippedItemId = skippedRecapItemId
                )
            ) {
                skippedRecapItemId = activeSession.itemId
                val startMs = (activeSession.recapStartTicks ?: 0L) / 10_000L
                val targetMs = (activeSession.recapEndTicks ?: 0L) / 10_000L
                seekExact(targetMs)
                currentPosMs = targetMs
                autoSkipUndoPrompt = SkipSegmentType.RECAP to startMs
            } else if (AutoSkipHelper.shouldAutoSkip(
                    enabled = state.autoSkipIntro,
                    isTrailer = activeSession.isTrailer,
                    itemId = activeSession.itemId,
                    currentTicks = liveTicks,
                    startTicks = activeSession.introStartTicks,
                    endTicks = activeSession.introEndTicks,
                    alreadySkippedItemId = skippedIntroItemId
                )
            ) {
                skippedIntroItemId = activeSession.itemId
                val startMs = (activeSession.introStartTicks ?: 0L) / 10_000L
                val targetMs = (activeSession.introEndTicks ?: 0L) / 10_000L
                seekExact(targetMs)
                currentPosMs = targetMs
                autoSkipUndoPrompt = SkipSegmentType.INTRO to startMs
            }
        }
    }

    LaunchedEffect(autoSkipUndoPrompt) {
        if (autoSkipUndoPrompt != null) {
            delay(4000L)
            autoSkipUndoPrompt = null
        }
    }

    // derivedStateOf: the screen only recomposes when these flip, not on every position tick.
    val showSkipRecap by remember {
        derivedStateOf {
            val s = state.session
            val ticks = currentPosMs * 10_000L
            s?.recapStartTicks != null && s.recapEndTicks != null && ticks in s.recapStartTicks..s.recapEndTicks
        }
    }

    val showSkipIntro by remember {
        derivedStateOf {
            val s = state.session
            val ticks = currentPosMs * 10_000L
            s?.introStartTicks != null && s.introEndTicks != null && ticks in s.introStartTicks..s.introEndTicks
        }
    }

    // nextEpisodeId arrives asynchronously after playback started; until then this stays false.
    val showNextEpisode by remember {
        derivedStateOf {
            val s = state.session
            val pos = currentPosMs
            val dur = durationMs
            // Last 25 s, or the last fifth for short episodes (a fixed 25 s window with a 60 s
            // minimum never showed Up Next for short-form shows), or once playback has ended.
            s?.nextEpisodeId != null && (
                (s.creditsStartTicks != null && pos * 10_000L >= s.creditsStartTicks) ||
                    (dur > 0L && pos >= dur - minOf(25_000L, dur / 5)) ||
                    playbackEnded
                )
        }
    }

    val activeChapterName by remember {
        derivedStateOf { state.session?.chapters?.lastOrNull { it.startPositionMs <= currentPosMs }?.name }
    }

    var nextEpisodeCountdown by remember(session?.itemId, state.autoplayNextEpisode, state.autoplayCancelled) {
        mutableIntStateOf(5)
    }

    // Keyed on isLoading so the countdown stops once "Play Now"/Next already started loading the
    // next episode (otherwise it fired a second stopped report + loadItem).
    LaunchedEffect(showNextEpisode, session?.itemId, state.autoplayNextEpisode, state.autoplayCancelled, state.isLoading) {
        val nextId = state.session?.nextEpisodeId
        if (showNextEpisode && !state.isLoading && state.autoplayNextEpisode && !state.autoplayCancelled && nextId != null) {
            nextEpisodeCountdown = 5
            while (nextEpisodeCountdown > 0) {
                delay(1000L)
                nextEpisodeCountdown--
            }
            viewModel.reportStopped(currentPosMs)
            viewModel.loadItem(nextId)
        }
    }

    // Reaching the end of the stream: roll straight into the next episode when autoplay is on
    // (e.g. the countdown was skipped by seeking to the end), otherwise leave the player instead
    // of sitting frozen on the last frame (movies, last episode, autoplay cancelled/off).
    LaunchedEffect(playbackEnded, state.isLoading) {
        if (!playbackEnded || state.isLoading) return@LaunchedEffect
        val s = state.session ?: return@LaunchedEffect
        val nextId = s.nextEpisodeId
        if (nextId != null && state.autoplayNextEpisode && !state.autoplayCancelled) {
            delay(400L) // let an in-flight Up Next countdown win if it's about to fire
            if (state.isLoading) return@LaunchedEffect
            viewModel.reportStopped(durationMs.coerceAtLeast(currentPosMs))
            viewModel.loadItem(nextId)
        } else if (nextId == null || !state.autoplayNextEpisode || state.autoplayCancelled) {
            // Keep the Up Next card up when autoplay is off/cancelled but a next episode exists;
            // only leave automatically when there's nothing to go to next.
            if (nextId == null) {
                delay(1500L)
                if (playbackEnded) exitPlayer()
            }
        }
    }

    // Back closes the episodes drawer, then hides the OSD, and only then leaves the player.
    // (The settings Dialog handles Back itself via onDismissRequest.)
    val playerSurfaceActive = session != null && !state.isLoading && state.errorMessage == null
    BackHandler(enabled = !hasExited && (showEpisodesDrawer || (osdVisible && playerSurfaceActive))) {
        if (showEpisodesDrawer) {
            showEpisodesDrawer = false
        } else {
            osdVisible = false
        }
    }

    val seekBy: (Long) -> Unit = { deltaMs ->
        val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
        seekFast(target)
        viewModel.notifySyncPlaySeek(target)
        currentPosMs = target
    }

    // SyncPlay: the server's timed commands, applied at the same wall-clock moment on every TV.
    LaunchedEffect(player) {
        viewModel.syncPlayActions.collect { action ->
            when (action) {
                is SyncPlayAction.LoadItem -> {
                    val startMs = action.startPositionTicks / 10_000L
                    if (viewModel.isCurrentItem(action.itemId)) {
                        player.pause()
                        seekExact(startMs)
                        if (player.playbackState == Player.STATE_READY) {
                            viewModel.reportSyncPlayState(ready = true, positionMs = startMs, isPlaying = false)
                        }
                    } else {
                        viewModel.reportStopped(player.currentPosition.coerceAtLeast(0L))
                        viewModel.loadItem(action.itemId, resumeOverrideMs = startMs, fromSyncPlay = true)
                    }
                }
                is SyncPlayAction.Command -> launch {
                    val positionMs = action.positionTicks / 10_000L
                    val waitMs = action.atLocalMs - System.currentTimeMillis()
                    when (action.command) {
                        "Unpause" -> {
                            if (waitMs > 0) delay(waitMs)
                            // Late (network delay): start where the group already is.
                            val lateMs = (System.currentTimeMillis() - action.atLocalMs).coerceAtLeast(0L)
                            val target = positionMs + lateMs
                            if (kotlin.math.abs(player.currentPosition - target) > SYNCPLAY_DRIFT_TOLERANCE_MS) {
                                seekExact(target)
                            }
                            player.play()
                        }
                        "Pause" -> {
                            if (waitMs > 0) delay(waitMs)
                            player.pause()
                            if (kotlin.math.abs(player.currentPosition - positionMs) > SYNCPLAY_DRIFT_TOLERANCE_MS) {
                                seekExact(positionMs)
                            }
                        }
                        "Seek" -> {
                            player.pause()
                            seekExact(positionMs)
                            currentPosMs = positionMs
                        }
                        "Stop" -> {
                            player.pause()
                            exitPlayer()
                        }
                    }
                    osdVisible = true
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.syncPlayNotices.collect { notice -> syncPlayNotice = notice }
    }
    LaunchedEffect(syncPlayNotice) {
        if (syncPlayNotice != null) {
            delay(3500L)
            syncPlayNotice = null
        }
    }

    // Remote control from another Jellyfin app (phone, web) while this TV is playing.
    LaunchedEffect(player) {
        viewModel.remotePlaystate.collect { cmd ->
            when (cmd.command) {
                "Pause" -> if (player.playWhenReady) togglePlayPause()
                "Unpause" -> if (!player.playWhenReady) togglePlayPause()
                "PlayPause" -> togglePlayPause()
                "Stop" -> exitPlayer()
                "Seek" -> cmd.seekPositionTicks?.let { ticks ->
                    val target = ticks / 10_000L
                    seekExact(target)
                    viewModel.notifySyncPlaySeek(target)
                    currentPosMs = target
                }
                "Rewind" -> seekBy(-10_000L)
                "FastForward" -> seekBy(10_000L)
                "NextTrack" -> state.session?.nextEpisodeId?.let { nextId ->
                    viewModel.reportStopped(player.currentPosition.coerceAtLeast(0L))
                    viewModel.loadItem(nextId)
                }
                "PreviousTrack" -> seekExact(0L)
            }
            osdVisible = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    // Any remote interaction restarts the OSD auto-hide timer.
                    lastInteractionTick.longValue += 1
                    // Loading spinner / error screen: let the Retry/Back buttons get normal key handling.
                    if (!playerSurfaceActive) return@onPreviewKeyEvent false
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            togglePlayPause()
                            osdVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY -> {
                            if (!player.playWhenReady) togglePlayPause()
                            osdVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            if (player.playWhenReady) togglePlayPause()
                            osdVisible = true
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            if (session?.isLiveTv != true) {
                                seekBy(if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) -10_000L else 10_000L)
                                osdVisible = true
                            }
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
                                seekBy(-10_000L)
                                osdVisible = true
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!osdVisible && session?.isLiveTv != true) {
                                seekBy(10_000L)
                                osdVisible = true
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }
            .focusRequester(rootFocusRequester)
            .focusable()
    ) {
        // The video surface stays composed across loads (next episode, version/quality switch,
        // retry): the SurfaceView isn't torn down and re-created each time, and on first start the
        // surface already exists when the decoder is configured. Loading / error states cover it.
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    // Keys are handled by the Compose root; the view must never take focus.
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    this.player = player
                }
            },
            update = { view ->
                if (view.player !== player) view.player = player
                // Stop the TV screensaver / sleep timer from kicking in mid-movie.
                view.keepScreenOn = isPlaying
                view.subtitleView?.let { sv ->
                    sv.visibility = if (useAssOverlay) View.GONE else View.VISIBLE
                    sv.setApplyEmbeddedStyles(false)
                    sv.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * state.subtitleFontScale)
                    val alphaInt = (state.subtitleBackgroundAlpha * 255f).toInt().coerceIn(0, 255)
                    val bgArgb = android.graphics.Color.argb(alphaInt, 0, 0, 0)
                    sv.setBottomPaddingFraction(state.subtitlePosition.bottomFraction)
                    sv.setStyle(
                        CaptionStyleCompat(
                            state.subtitleTextColor.argb.toInt(),
                            bgArgb,
                            android.graphics.Color.TRANSPARENT,
                            when (state.subtitleEdgeStyle) {
                                SubtitleEdgeStyle.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                SubtitleEdgeStyle.DROP_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                SubtitleEdgeStyle.RAISED -> CaptionStyleCompat.EDGE_TYPE_RAISED
                                SubtitleEdgeStyle.NONE -> CaptionStyleCompat.EDGE_TYPE_NONE
                            },
                            android.graphics.Color.BLACK,
                            null
                        )
                    )
                }
            },
            // Detach on disposal so the player doesn't keep a listener + surface reference to a dead view.
            onRelease = { view -> view.player = null },
            modifier = Modifier.fillMaxSize()
        )

        when {
            state.isLoading -> {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            state.errorMessage != null -> {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black))
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
                            text = strings.commonRetry,
                            icon = Icons.Filled.Refresh,
                            isPrimary = true,
                            onClick = { viewModel.retryPlayback(currentPosMs) },
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
                            text = strings.commonBack,
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            isPrimary = false,
                            onClick = exitPlayer
                        )
                    }
                }
            }
            else -> {

                if (useAssOverlay) {
                    AssOverlayHost(
                        player = player,
                        cues = state.activeAssCues,
                        // Same sign as the ExoPlayer path: a positive offset delays subtitles.
                        offsetMs = state.subtitleOffsetMs,
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
                (subtitleOffsetHudText ?: syncPlayNotice ?: state.feedbackMessage)?.let { hudText ->
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
                            text = when (segmentType) {
                                SkipSegmentType.RECAP -> strings.playerSkippedRecapUndo
                                SkipSegmentType.INTRO -> strings.playerSkippedIntroUndo
                            },
                            icon = Icons.AutoMirrored.Filled.Undo,
                            isPrimary = false,
                            onClick = {
                                seekExact(seekBackMs)
                                viewModel.notifySyncPlaySeek(seekBackMs)
                                currentPosMs = seekBackMs
                                autoSkipUndoPrompt = null
                            }
                        )
                    }
                    if (showSkipRecap && session?.recapEndTicks != null) {
                        HeroPillButton(
                            text = strings.playerSkipRecap,
                            icon = Icons.Filled.SkipNext,
                            isPrimary = true,
                            onClick = {
                                val target = session.recapEndTicks / 10_000L
                                seekExact(target)
                                viewModel.notifySyncPlaySeek(target)
                            }
                        )
                    }
                    if (showSkipIntro && session?.introEndTicks != null) {
                        HeroPillButton(
                            text = strings.playerSkipIntro,
                            icon = Icons.Filled.SkipNext,
                            isPrimary = true,
                            onClick = {
                                val target = session.introEndTicks / 10_000L
                                seekExact(target)
                                viewModel.notifySyncPlaySeek(target)
                            }
                        )
                    }
                }

                // Next Episode Overlay Card with Auto-Play Countdown
                if (showNextEpisode && session?.nextEpisodeId != null && !showEpisodesDrawer) {
                    NextEpisodeOverlayCard(
                        nextEpisodeTitle = session.nextEpisodeTitle ?: strings.playerNextEpisode,
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
                        // Legibility scrims over bright video (only while the OSD is visible):
                        // one cheap vertical gradient each, no blur.
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(OSD_TOP_SCRIM)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(300.dp)
                                .background(OSD_BOTTOM_SCRIM)
                        )
                        // Title + SyncPlay room badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 48.dp, vertical = 28.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlayerControlIcon(Icons.AutoMirrored.Filled.ArrowBack, strings.commonBack, onClick = exitPlayer)
                                Spacer(Modifier.width(18.dp))
                                Column {
                                    Text(
                                        text = session?.title.orEmpty(),
                                        color = colors.white,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    val subtitleParts = listOfNotNull(
                                        session?.subtitle,
                                        activeChapterName
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
                                .padding(horizontal = 48.dp, vertical = 28.dp)
                        ) {
                            // Position-dependent parts live in their own composable so the 2 Hz
                            // position updates only recompose the seek bar, not the whole OSD.
                            OsdSeekBar(
                                positionMs = { currentPosMs },
                                durationMs = { durationMs },
                                scrubPreviewMs = { scrubPreviewMs },
                                trickplayInfo = session?.trickplayInfo,
                                trickplayLoader = trickplayLoader,
                                chapters = session?.chapters.orEmpty(),
                                onScrub = { targetMs -> scrubPreviewMs = targetMs },
                                onScrubFinished = {
                                    val target = scrubPreviewMs
                                    scrubPreviewMs = null
                                    if (target != null) {
                                        seekFast(target)
                                        currentPosMs = target
                                        viewModel.notifySyncPlaySeek(target)
                                    }
                                }
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
                                        label = if (isPlaying) strings.playerPause else strings.actionPlay,
                                        onClick = togglePlayPause,
                                        modifier = Modifier.focusRequester(playPauseFocusRequester)
                                    )
                                    if (session?.isLiveTv != true) {
                                        PlayerControlIcon(
                                            icon = Icons.Filled.Replay10,
                                            label = strings.playerBack10,
                                            onClick = { seekBy(-10_000L) }
                                        )
                                        PlayerControlIcon(
                                            icon = Icons.Filled.Forward10,
                                            label = strings.playerForward10,
                                            onClick = { seekBy(10_000L) }
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    OsdTimeText(
                                        isLiveTv = session?.isLiveTv == true,
                                        positionMs = { currentPosMs },
                                        durationMs = { durationMs }
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
                                            label = strings.playerEpisodes,
                                            onClick = {
                                                viewModel.ensureDrawerLoaded()
                                                showEpisodesDrawer = true
                                            }
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
                                        label = strings.playerAudioSubtitles,
                                        onClick = { showTracksDialog = true }
                                    )
                                    PlayerControlIcon(
                                        icon = Icons.Filled.Settings,
                                        label = strings.playerPlaybackSettings,
                                        onClick = { showTracksDialog = true }
                                    )
                                    session?.nextEpisodeId?.let { nextId ->
                                        PlayerControlIcon(
                                            icon = Icons.Filled.SkipNext,
                                            label = strings.playerNextEpisode,
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
                seekExact(chapter.startPositionMs)
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
    val strings = AppStrings.current
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
            text = videoParts.joinToString(" • ").ifBlank { strings.playerVideoStream },
            color = colors.white,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        session.audioCodec?.let {
            Text(text = strings.playerAudioInfo.format(it), color = colors.textSecondary, fontSize = 11.sp)
        }
        Text(
            text = strings.playerQualityInfo.format(selectedQuality.localized(strings), "${playbackSpeed}x"),
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
    val strings = AppStrings.current
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
            text = if (autoplayEnabled) strings.playerNextIn.format(countdownSeconds) else strings.playerUpNext,
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
                text = strings.playerPlayNow,
                icon = Icons.Filled.SkipNext,
                isPrimary = true,
                onClick = onPlayNow
            )
            if (autoplayEnabled) {
                HeroPillButton(
                    text = strings.playerWatchCredits,
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = onWatchCredits
                )
            }
        }
    }
}

/**
 * Seek bar + trickplay preview. Reads the playback position through lambdas so only this
 * composable recomposes on position ticks / scrub steps.
 */
@Composable
private fun OsdSeekBar(
    positionMs: () -> Long,
    durationMs: () -> Long,
    scrubPreviewMs: () -> Long?,
    trickplayInfo: TrickplayInfo?,
    trickplayLoader: TrickplayThumbnailLoader,
    chapters: List<ChapterMarker>,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val duration = durationMs()
    val previewTargetMs = scrubPreviewMs()

    // Warm the trickplay sheet for the current position as soon as the OSD shows, so the first
    // scrub step already has a thumbnail.
    LaunchedEffect(trickplayInfo) {
        trickplayInfo?.cropForPosition(positionMs())?.let(trickplayLoader::prefetch)
    }

    // Seekbar Trickplay Thumbnail Preview
    if (previewTargetMs != null && duration > 0L) {
        val crop = trickplayInfo?.cropForPosition(previewTargetMs)
        val frac = (previewTargetMs.toFloat() / duration.toFloat()).coerceIn(0.05f, 0.95f)
        Row(modifier = Modifier.fillMaxWidth()) {
            if (frac > 0.06f) {
                Spacer(Modifier.weight(frac))
            }
            TrickplayPreviewCard(
                positionMs = previewTargetMs,
                crop = crop,
                loader = trickplayLoader
            )
            if (1f - frac > 0.06f) {
                Spacer(Modifier.weight(1f - frac))
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    val totalFloat = duration.coerceAtLeast(1L).toFloat()
    val barPosMs = previewTargetMs ?: positionMs()
    val accent = colors.accent
    var focused by remember { mutableStateOf(false) }
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                val native = event.nativeKeyEvent
                when (native.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (duration <= 0L) return@onKeyEvent false
                        if (native.action == KeyEvent.ACTION_DOWN) {
                            // Accelerates while the key is held: 10 s, then 30 s, then 60 s steps.
                            val stepMs = when {
                                native.repeatCount < 5 -> 10_000L
                                native.repeatCount < 15 -> 30_000L
                                else -> 60_000L
                            }
                            val direction = if (native.keyCode == KeyEvent.KEYCODE_DPAD_LEFT) -1L else 1L
                            val base = scrubPreviewMs() ?: positionMs()
                            onScrub((base + direction * stepMs).coerceIn(0L, duration))
                        } else if (native.action == KeyEvent.ACTION_UP) {
                            onScrubFinished()
                        }
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        if (scrubPreviewMs() != null) {
                            if (native.action == KeyEvent.ACTION_UP) onScrubFinished()
                            true
                        } else {
                            false
                        }
                    }
                    else -> false
                }
            }
            .focusable()
    ) {
        // Lightweight seek bar (replaces Material3 Slider, whose vertical-bar thumb and gapped
        // track rendered as stray bars over the video): track, progress, chapter ticks, and a
        // round thumb only while focused.
        val frac = (barPosMs.toFloat() / totalFloat).coerceIn(0f, 1f)
        val trackHeight = if (focused) 6.dp.toPx() else 4.dp.toPx()
        val centerY = size.height / 2f
        val radius = trackHeight / 2f
        drawRoundRect(
            color = Color(0x55FFFFFF),
            topLeft = Offset(0f, centerY - radius),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(radius, radius)
        )
        if (frac > 0f) {
            drawRoundRect(
                color = accent,
                topLeft = Offset(0f, centerY - radius),
                size = Size(size.width * frac, trackHeight),
                cornerRadius = CornerRadius(radius, radius)
            )
        }
        if (duration > 0L) {
            chapters.forEach { ch ->
                if (ch.startPositionMs > 0L && ch.startPositionMs < duration) {
                    val x = size.width * (ch.startPositionMs.toFloat() / totalFloat)
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(x, centerY - radius),
                        end = Offset(x, centerY + radius),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
        if (focused) {
            drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(size.width * frac, centerY))
        }
    }
}

@Composable
private fun OsdTimeText(
    isLiveTv: Boolean,
    positionMs: () -> Long,
    durationMs: () -> Long
) {
    val colors = SeerflixTokens.colors
    Text(
        text = if (isLiveTv) AppStrings.current.playerLive else "${formatMs(positionMs())} / ${formatMs(durationMs())}",
        color = if (isLiveTv) colors.accent else colors.textSecondary,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold
    )
}

/**
 * Styled ASS/SSA overlay with its own ~10 Hz position ticker. It only recomposes when the set of
 * visible cues changes (instead of the whole player screen 10x per second).
 */
@Composable
private fun AssOverlayHost(
    player: Player,
    cues: List<AssCue>,
    offsetMs: Long,
    fontScale: Float,
    backgroundAlpha: Float
) {
    val index = remember(cues) { AssCueIndex(cues) }
    var activeCues by remember(index) { mutableStateOf(emptyList<AssCue>()) }
    LaunchedEffect(index, offsetMs, player) {
        while (true) {
            // Structural equality: assigning an equal list doesn't invalidate anything.
            activeCues = index.activeAt(player.currentPosition.coerceAtLeast(0L) - offsetMs)
            delay(ASS_TICK_MS)
        }
    }
    AssActiveCuesOverlay(
        activeCues = activeCues,
        fontScale = fontScale,
        backgroundAlpha = backgroundAlpha
    )
}

@Composable
private fun TrickplayPreviewCard(
    positionMs: Long,
    crop: TrickplayCrop?,
    loader: TrickplayThumbnailLoader,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    // Keeps the last thumbnail while the next one decodes (no blank flicker while scrubbing).
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(crop) {
        if (crop == null) {
            thumbnail = null
            return@LaunchedEffect
        }
        loader.peek(crop)?.let {
            thumbnail = it
            return@LaunchedEffect
        }
        // Region-decoded off the main thread; only a 320x180-ish RGB_565 bitmap is allocated.
        loader.load(crop)?.let { thumbnail = it }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        thumbnail?.let { bitmap ->
            Box(
                modifier = Modifier
                    .width(176.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, colors.focusBorder, RoundedCornerShape(6.dp))
                    .background(Color.Black)
            ) {
                Image(
                    bitmap = bitmap,
                    contentDescription = AppStrings.current.playerSeekPreview,
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
    val strings = AppStrings.current
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
                        text = seriesTitle.ifBlank { strings.playerEpisodes },
                        color = colors.white,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(strings.playerSelectEpisode, color = colors.textSecondary, fontSize = 12.sp)
                }
                PlayerControlIcon(Icons.Filled.Close, strings.commonClose, onClick = onClose)
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
                                    text = if (!ep.isAvailableInJellyfin) strings.playerNotOnJellyfin
                                    else if (isCurrent) strings.playerNowPlaying
                                    else ep.overview.ifBlank { strings.commonMinutesShort.format(ep.runtimeMinutes) },
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
    val strings = AppStrings.current
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
                Text(strings.playerPlaybackSettings, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                PlayerControlIcon(
                    icon = Icons.Filled.Close,
                    label = strings.commonClose,
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(closeDialogFocusRequester)
                )
            }

            // Versions & Editions (Multiple MediaSources)
            val sources = session?.mediaSources.orEmpty()
            if (!isTrailer && sources.size > 1) {
                Text(strings.labelVersions, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                Text(strings.playerChaptersCount.format(chapters.size), color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                chapters.take(12).forEach { chapter ->
                    TrackChoiceRow(
                        label = "${formatMs(chapter.startPositionMs)} • ${chapter.name}",
                        selected = false,
                        onClick = { onJumpToChapter(chapter) }
                    )
                }
            }

            if (audioGroups.isNotEmpty()) {
                Text(strings.playerAudioTracks, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                audioGroups.forEachIndexed { idx, group ->
                    val format = group.getTrackFormat(0)
                    val label = listOfNotNull(format.language?.uppercase(), format.label).joinToString(" • ").ifBlank { strings.playerAudioTrackN.format(idx + 1) }
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
                Text(strings.settingsSubtitlesSection, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TrackChoiceRow(
                    label = strings.optionOff,
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
                    val label = listOfNotNull(format.language?.uppercase(), format.label).joinToString(" • ").ifBlank { strings.playerSubtitleTrackN.format(idx + 1) }
                    TrackChoiceRow(
                        label = label,
                        selected = group.isSelected,
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
                                .build()
                            // The styled ASS overlay hides ExoPlayer's SubtitleView; drop it so the
                            // chosen track is actually visible.
                            if (activeAssSubtitleIndex != null) onSelectExternalAssTrack(null)
                            onDismiss()
                        }
                    )
                }
            }

            // ASS / SSA Anime & Styled Subtitle Engine
            if (!isTrailer && assExternalTracks.isNotEmpty()) {
                Text(strings.playerAssEngine, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssSubtitleMode.entries.forEach { mode ->
                        SettingChip(
                            label = mode.localized(strings),
                            selected = assSubtitleMode == mode,
                            onClick = { onSelectAssMode(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                assExternalTracks.forEach { ext ->
                    val label = listOfNotNull(
                        ext.language?.uppercase(),
                        ext.title.ifBlank { strings.playerAssTrackN.format(ext.index) },
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
                    text = strings.playerSubtitleSync.format(offsetLabel),
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (isBitmapSubtitle) {
                    Text(
                        text = strings.playerSubtitleSyncBitmap,
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
                Text(strings.playerSearchSubtitles, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                        label = if (remoteSubtitlesLoading) strings.playerSearching else strings.navSearch,
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
            Text(strings.playerSpeed, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                Text(strings.playerVideoQuality, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                QualityPreset.entries.forEach { preset ->
                    TrackChoiceRow(
                        label = preset.localized(strings),
                        selected = selectedQuality == preset,
                        onClick = { onSelectQuality(preset) }
                    )
                }
            }

            // Audio Routing & Home Theater
            if (!isTrailer) {
                Text(strings.playerAudioRouting, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TrackChoiceRow(
                    label = strings.playerPassthrough,
                    selected = audioPassthroughEnabled,
                    onClick = onToggleAudioPassthrough
                )
                TrackChoiceRow(
                    label = strings.playerDownmix,
                    selected = stereoDownmixEnabled,
                    onClick = onToggleStereoDownmix
                )
            }

            // Auto-Skip & 24fps Frame-Rate Matching
            if (!isTrailer) {
                Text(strings.playerAutomation, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                TrackChoiceRow(
                    label = strings.settingsAutoSkipIntroTitle,
                    selected = autoSkipIntro,
                    onClick = onToggleAutoSkipIntro
                )
                TrackChoiceRow(
                    label = strings.settingsAutoSkipRecapTitle,
                    selected = autoSkipRecap,
                    onClick = onToggleAutoSkipRecap
                )
                Text(strings.settingsFrameRateTitle, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FrameRateMatchMode.entries.forEach { mode ->
                        SettingChip(
                            label = mode.localized(strings),
                            selected = frameRateMatchMode == mode,
                            onClick = { onSelectFrameRateMatchMode(mode) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Subtitle Size & Background
            Text(strings.playerSubtitleSize, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(strings.sizeSmall to 0.8f, strings.sizeMedium to 1.0f, strings.sizeLarge to 1.3f).forEach { (label, scale) ->
                    SettingChip(
                        label = label,
                        selected = kotlin.math.abs(subtitleFontScale - scale) < 0.05f,
                        onClick = { onSetSubtitleAppearance(scale, subtitleBackgroundAlpha) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Text(strings.playerSubtitleBackground, color = colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(strings.backgroundNone to 0f, "50%" to 0.5f, "80%" to 0.8f).forEach { (label, alpha) ->
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
                label = strings.playerStreamInfo,
                selected = showStreamInfo,
                onClick = onToggleStreamInfo
            )
            if (!isTrailer) {
                TrackChoiceRow(
                    label = strings.actionExternalPlayer,
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
            Text(AppStrings.current.commonActive, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private const val SUBTITLE_OFFSET_RELOAD_DEBOUNCE_MS = 800L
private const val SCRUB_SEEK_DEBOUNCE_MS = 300L
/** SyncPlay: skip a corrective seek when already this close to the group position. */
private const val SYNCPLAY_DRIFT_TOLERANCE_MS = 300L
private const val ASS_TICK_MS = 100L
private const val PROGRESS_REPORT_INTERVAL_MS = 10_000L

private val OSD_TOP_SCRIM = Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent))
private val OSD_BOTTOM_SCRIM = Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))

private fun QualityPreset.localized(s: UiStrings): String = when (this) {
    QualityPreset.AUTO -> s.qualityAuto
    else -> label
}

private fun AssSubtitleMode.localized(s: UiStrings): String = when (this) {
    AssSubtitleMode.STYLED -> s.assStyled
    AssSubtitleMode.BURN_IN -> s.assBurnIn
    AssSubtitleMode.PLAIN -> s.assPlain
}

private fun FrameRateMatchMode.localized(s: UiStrings): String = when (this) {
    FrameRateMatchMode.OFF -> s.optionOff
    FrameRateMatchMode.SEAMLESS_ONLY -> s.frameRateSeamless
    FrameRateMatchMode.ALWAYS -> s.frameRateAlways
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val h = totalSec / 3600L
    val m = (totalSec % 3600L) / 60L
    val s = totalSec % 60L
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

