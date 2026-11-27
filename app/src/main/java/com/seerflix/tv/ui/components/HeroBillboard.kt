package com.seerflix.tv.ui.components

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.ui.theme.NavLayoutStyle
import com.seerflix.tv.ui.theme.SeerflixTokens
import okhttp3.OkHttpClient

private const val PREVIEW_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"

@OptIn(UnstableApi::class)
@Composable
fun HeroBillboard(
    item: UnifiedMediaItem,
    previewStream: ResolvedPreviewStream?,
    isMuted: Boolean,
    onToggleMute: () -> Unit,
    onPrimaryAction: () -> Unit,
    onMoreInfo: () -> Unit,
    onToggleMyList: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    /**
     * When provided, the caller owns initial focus (Home requests it once). When null the
     * billboard focuses its primary action itself whenever it enters composition.
     */
    primaryFocusRequester: FocusRequester? = null,
    /**
     * Client for the trailer preview (honours the self-signed-certificate toggle). Falls back to
     * Media3's DefaultHttpDataSource when null (e.g. screenshot tests).
     */
    previewHttpClient: OkHttpClient? = null
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    var isVideoReady by remember(item.id, previewStream?.videoUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(spec.heroHeightDp.dp)
            .background(colors.background)
    ) {
        // Static backdrop image (avoids continuous 60fps full-screen GPU redraws on low-power TV sticks).
        // Requested at display size (capped at 1280 px server-side) and keeps showing the previous
        // backdrop until the next one is decoded, so a featured-item change is a single swap with
        // no background flash and no crossfade layer.
        SizedAsyncImage(
            url = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            width = LocalConfiguration.current.screenWidthDp.dp,
            height = spec.heroHeightDp.dp,
            maxRequestWidthPx = 1280,
            keepPreviousWhileLoading = true,
            modifier = Modifier.fillMaxSize()
        )

        if (previewStream != null) {
            HeroPreviewVideoLayer(
                previewStream = previewStream,
                previewHttpClient = previewHttpClient,
                isMuted = isMuted,
                isVideoReady = isVideoReady,
                onFirstFrame = { isVideoReady = true }
            )
        }

        // Preset-aware horizontal & vertical cinematic gradients, drawn by ONE node with cached
        // shaders. The vertical scrim is transparent between 32% and 60% of the height, so only its
        // top and bottom bands are filled (less overdraw than two stacked full-size gradient boxes).
        val topScrim = if (spec.navStyle != NavLayoutStyle.LEFT_RAIL) {
            colors.deepBlack.copy(alpha = 0.72f)
        } else {
            colors.deepBlack.copy(alpha = 0.34f)
        }
        val heroBg = colors.background
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val horizontal = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to heroBg.copy(alpha = 0.96f),
                            0.38f to heroBg.copy(alpha = 0.78f),
                            0.72f to heroBg.copy(alpha = 0.25f),
                            1.0f to Color.Transparent
                        )
                    )
                    val topBandEnd = size.height * 0.32f
                    val bottomBandStart = size.height * 0.60f
                    val top = Brush.verticalGradient(
                        colors = listOf(topScrim, Color.Transparent),
                        startY = 0f,
                        endY = topBandEnd
                    )
                    val bottom = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, heroBg),
                        startY = bottomBandStart,
                        endY = size.height
                    )
                    onDrawBehind {
                        drawRect(horizontal)
                        drawRect(top, size = Size(size.width, topBandEnd))
                        drawRect(
                            bottom,
                            topLeft = Offset(0f, bottomBandStart),
                            size = Size(size.width, size.height - bottomBandStart)
                        )
                    }
                }
        )

        // Left info & CTA block
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = spec.contentStartPaddingDp.dp,
                    bottom = if (spec.preset == UiPreset.APPLE_TV) 22.dp else 16.dp
                )
                // Proportional instead of a fixed 340dp end inset: with the Netflix rail expanded
                // the fixed inset left too little width and the CTA pills wrapped/clipped.
                .fillMaxWidth(0.62f)
        ) {
            // Preset-aware micro-header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (spec.showBrandMicroMark) {
                    Text(
                        text = "L",
                        color = colors.accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (item.mediaType.name == "MOVIE") strings.heroFilm else strings.heroSeries,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                } else {
                    val tagText = when (spec.preset) {
                        UiPreset.APPLE_TV -> "APPLE TV+ • " + if (item.mediaType.name == "MOVIE") strings.heroFilm else strings.heroSeries
                        UiPreset.DISNEY_PLUS -> if (item.mediaType.name == "MOVIE") strings.heroFeaturedFilm else strings.heroFeaturedSeries
                        UiPreset.NETFLIX -> if (item.mediaType.name == "MOVIE") strings.heroFilm else strings.heroSeries
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(if (spec.preset == UiPreset.APPLE_TV) 12.dp else 4.dp))
                            .background(colors.surfaceElevated.copy(alpha = 0.85f))
                            .border(
                                1.dp,
                                colors.badgeBorder,
                                RoundedCornerShape(if (spec.preset == UiPreset.APPLE_TV) 12.dp else 4.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = tagText,
                            color = colors.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                    }
                }
                AvailabilityBadge(item = item)
            }

            Spacer(Modifier.height(6.dp))

            // Jellyfin logo URLs are built for every library item whether or not a logo exists, so a
            // 404 is common (new items, Jellyfin-only servers). The title text is shown until the
            // logo has actually loaded, and stays if it fails — the billboard is never untitled.
            val logoUrl = item.clearLogoUrl?.takeIf { it.isNotBlank() }
            var logoLoaded by remember(logoUrl) { mutableStateOf(false) }
            Box(contentAlignment = Alignment.CenterStart) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = item.title,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        onSuccess = { logoLoaded = true },
                        onError = { logoLoaded = false },
                        modifier = Modifier
                            .height(if (spec.preset == UiPreset.APPLE_TV) 66.dp else 56.dp)
                            .fillMaxWidth(0.75f)
                    )
                }
                if (!logoLoaded) {
                    Text(
                        text = item.title,
                        color = colors.white,
                        fontSize = if (spec.preset == UiPreset.APPLE_TV) 36.sp else 32.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 38.sp
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Rating, year, maturity, runtime, format badges — same line as Details and the header.
            TitleMetaLine(item = item)

            // Netflix-style concise genre descriptor line (e.g. "Movie • Sci-Fi • Adventure • Drama")
            val genreLine = remember(item.genres, item.mediaType, strings) {
                val topGenres = item.genres.filter { it.isNotBlank() }.distinct().take(3)
                if (topGenres.isEmpty()) null
                else {
                    val kind = if (item.mediaType.name == "MOVIE") strings.commonMovie else strings.commonSeries
                    (listOf(kind) + topGenres).joinToString(" • ")
                }
            }
            if (genreLine != null) {
                Spacer(Modifier.height(5.dp))
                Text(
                    text = genreLine,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.overview,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    // The Disney+ banner is shorter (its header bar takes room) — one line there.
                    maxLines = if (spec.heroHeightDp < 240) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            val ownFocusRequester = remember { FocusRequester() }
            val primaryActionFocusRequester = primaryFocusRequester ?: ownFocusRequester
            if (primaryFocusRequester == null) {
                LaunchedEffect(Unit) {
                    runCatching { primaryActionFocusRequester.requestFocus() }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val primaryLabel = when {
                    item.isPlayable && item.resumePositionTicks > 0L -> strings.actionResume
                    item.isPlayable && item.mediaType.name == "MOVIE" -> strings.actionPlayMovie
                    item.isPlayable -> strings.actionPlayShow
                    item.activeDownload != null || item.overallAvailability == AvailabilityTier.PROCESSING -> strings.heroViewProgress
                    item.overallAvailability == AvailabilityTier.PENDING -> strings.commonRequested
                    else -> strings.actionRequestMore
                }
                val primaryIcon = if (item.isPlayable) Icons.Filled.PlayArrow else Icons.Filled.Add

                HeroPillButton(
                    text = primaryLabel,
                    icon = primaryIcon,
                    isPrimary = true,
                    onClick = onPrimaryAction,
                    modifier = Modifier.focusRequester(primaryActionFocusRequester)
                )
                HeroPillButton(
                    text = strings.actionMoreInfo,
                    icon = Icons.Filled.Info,
                    isPrimary = false,
                    onClick = onMoreInfo
                )
                if (onToggleMyList != null) {
                    HeroPillButton(
                        text = strings.navMyList,
                        icon = if (item.isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                        isPrimary = false,
                        onClick = onToggleMyList
                    )
                }
            }
        }

        // Mute / Unmute preview button on bottom-right when trailer is playing
        if (previewStream != null && isVideoReady) {
            var muteFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 36.dp, bottom = 28.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.background.copy(alpha = 0.72f))
                    .border(
                        width = if (muteFocused) 2.dp else 1.dp,
                        color = if (muteFocused) colors.focusBorder else colors.badgeBorder,
                        shape = CircleShape
                    )
                    .onFocusChanged { muteFocused = it.isFocused || it.hasFocus }
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = strings.heroTogglePreviewAudio,
                    tint = colors.white,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun HeroPillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "pillScale"
    )

    val bg = when {
        isPrimary -> colors.primaryButtonBg
        focused -> colors.secondaryButtonFocusedBg
        else -> colors.secondaryButtonBg
    }
    val fg = if (isPrimary) colors.primaryButtonFg else colors.white
    val shape = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(bg)
            .then(
                if (focused) {
                    Modifier.border(
                        2.dp,
                        if (isPrimary) colors.primaryButtonFocusBorder else colors.focusBorder,
                        shape
                    )
                } else if (!isPrimary && (spec.preset == UiPreset.APPLE_TV || spec.preset == UiPreset.NETFLIX)) {
                    Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.65f), shape)
                } else Modifier
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (spec.preset == UiPreset.APPLE_TV || spec.preset == UiPreset.NETFLIX) 22.dp else 20.dp,
                vertical = 10.dp
            )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = fg,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HeroPreviewVideoLayer(
    previewStream: ResolvedPreviewStream,
    previewHttpClient: OkHttpClient?,
    isMuted: Boolean,
    isVideoReady: Boolean,
    onFirstFrame: () -> Unit
) {
    val context = LocalContext.current
    // Use the app's media OkHttpClient when available so self-signed HTTPS Jellyfin servers
    // (local trailers) work; DefaultHttpDataSource ignores the "trust self-signed" toggle.
    val httpDataSourceFactory: DataSource.Factory = remember(previewHttpClient) {
        if (previewHttpClient != null) {
            OkHttpDataSource.Factory(previewHttpClient).setUserAgent(PREVIEW_USER_AGENT)
        } else {
            DefaultHttpDataSource.Factory().setUserAgent(PREVIEW_USER_AGENT)
        }
    }
    val previewPlayer = remember(context, httpDataSourceFactory) {
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(DefaultDataSource.Factory(context, httpDataSourceFactory))
            )
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_ONE
                playWhenReady = true
            }
    }

    val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)
    DisposableEffect(previewPlayer) {
        // Registered once per player (it used to be re-added on every URL change, leaking
        // listeners that each captured a stale callback).
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                currentOnFirstFrame()
            }
        }
        previewPlayer.addListener(listener)
        onDispose {
            previewPlayer.removeListener(listener)
            previewPlayer.release()
        }
    }

    // Home stays composed while the activity is stopped (remote Home button / screensaver), so
    // without this the trailer kept playing — with audio if unmuted — in the background.
    // Declared after the release effect so it is disposed (paused) before the player is released.
    // The in-app screensaver is an overlay (the activity stays resumed): pause under it too.
    val screensaverVisible = LocalScreensaverVisible.current
    LifecycleResumeEffect(previewPlayer, screensaverVisible) {
        previewPlayer.playWhenReady = !screensaverVisible
        onPauseOrDispose { previewPlayer.playWhenReady = false }
    }

    LaunchedEffect(previewPlayer, isMuted) {
        previewPlayer.volume = if (isMuted) 0f else 0.7f
    }

    LaunchedEffect(previewPlayer, previewStream.videoUrl) {
        val dsFactory = httpDataSourceFactory
        if (previewStream.audioUrl != null) {
            val vSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(previewStream.videoUrl))
            val aSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(previewStream.audioUrl))
            previewPlayer.setMediaSource(MergingMediaSource(vSrc, aSrc))
        } else {
            previewPlayer.setMediaItem(MediaItem.fromUri(previewStream.videoUrl))
        }
        // playWhenReady is driven by the lifecycle effect above; don't force play() while paused.
        previewPlayer.prepare()
    }

    AnimatedVisibility(
        visible = isVideoReady,
        enter = fadeIn(tween(500)),
        exit = fadeOut(tween(300))
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    player = previewPlayer
                }
            },
            update = { view ->
                if (view.player !== previewPlayer) {
                    view.player = previewPlayer
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
