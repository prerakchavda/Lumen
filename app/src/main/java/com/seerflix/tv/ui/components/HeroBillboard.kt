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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.ui.theme.NavLayoutStyle
import com.seerflix.tv.ui.theme.SeerflixTokens

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
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    var isVideoReady by remember(item.id, previewStream?.videoUrl) { mutableStateOf(false) }

    // Subtle Ken Burns motion when showing static backdrop
    val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
    val kbScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kbScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(spec.heroHeightDp.dp)
            .background(colors.background)
    ) {
        // Backdrop image (always rendered beneath, animated with subtle Ken Burns)
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = if (isVideoReady) 1f else kbScale
                    scaleY = if (isVideoReady) 1f else kbScale
                }
        )

        if (previewStream != null) {
            HeroPreviewVideoLayer(
                previewStream = previewStream,
                isMuted = isMuted,
                isVideoReady = isVideoReady,
                onFirstFrame = { isVideoReady = true }
            )
        }

        // Preset-aware horizontal & vertical cinematic gradients
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to colors.background.copy(alpha = 0.96f),
                            0.38f to colors.background.copy(alpha = 0.78f),
                            0.72f to colors.background.copy(alpha = 0.25f),
                            1.0f to Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to if (spec.navStyle != NavLayoutStyle.LEFT_RAIL) {
                                colors.deepBlack.copy(alpha = 0.72f)
                            } else {
                                colors.deepBlack.copy(alpha = 0.34f)
                            },
                            0.32f to Color.Transparent,
                            0.60f to Color.Transparent,
                            1.0f to colors.background
                        )
                    )
                )
        )

        // Left info & CTA block
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = spec.contentStartPaddingDp.dp,
                    bottom = if (spec.preset == UiPreset.APPLE_TV) 28.dp else 24.dp,
                    end = 340.dp
                )
        ) {
            // Preset-aware micro-header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (spec.showBrandMicroMark) {
                    Text(
                        text = "S",
                        color = colors.accent,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = if (item.mediaType.name == "MOVIE") "FILM" else "SERIES",
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    )
                } else {
                    val tagText = when (spec.preset) {
                        UiPreset.APPLE_TV -> if (item.mediaType.name == "MOVIE") "APPLE TV+ • FILM" else "APPLE TV+ • SERIES"
                        UiPreset.DISNEY_PLUS -> if (item.mediaType.name == "MOVIE") "FEATURED FILM" else "FEATURED SERIES"
                        UiPreset.NETFLIX -> if (item.mediaType.name == "MOVIE") "FILM" else "SERIES"
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

            if (!item.clearLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.clearLogoUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .height(if (spec.preset == UiPreset.APPLE_TV) 74.dp else 68.dp)
                        .fillMaxWidth(0.75f)
                )
            } else {
                Text(
                    text = item.title,
                    color = colors.white,
                    fontSize = if (spec.preset == UiPreset.APPLE_TV) 40.sp else 36.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 42.sp
                )
            }

            Spacer(Modifier.height(10.dp))

            // Match score, Year, Rating, Runtime, Media Badges
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item.matchPercent?.let { match ->
                    Text(
                        text = "$match% Match",
                        color = colors.matchGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                item.releaseYear?.let { year ->
                    Text(text = year, color = colors.textSecondary, fontSize = 13.sp)
                }
                item.maturityRating?.let { rating ->
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.textMuted, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(text = rating, color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
                item.formattedRuntime?.let { rt ->
                    Text(text = rt, color = colors.textSecondary, fontSize = 13.sp)
                }
                if (item.has4k) {
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.textSecondary, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "4K UHD",
                            color = colors.white,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                item.mediaBadges.filter { it != "4K" && it != "HD" }.take(2).forEach { badge ->
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.textMuted, RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badge,
                            color = colors.textPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.overview,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val primaryLabel = when {
                    item.isPlayable && item.resumePositionTicks > 0L -> "Resume"
                    item.isPlayable -> "Play"
                    item.activeDownload != null || item.overallAvailability == AvailabilityTier.PROCESSING -> "View Progress"
                    item.overallAvailability == AvailabilityTier.PENDING -> "Requested"
                    else -> "Request"
                }
                val primaryIcon = if (item.isPlayable) Icons.Filled.PlayArrow else Icons.Filled.Add

                HeroPillButton(
                    text = primaryLabel,
                    icon = primaryIcon,
                    isPrimary = true,
                    onClick = onPrimaryAction
                )
                HeroPillButton(
                    text = "More Info",
                    icon = Icons.Filled.Info,
                    isPrimary = false,
                    onClick = onMoreInfo
                )
                if (onToggleMyList != null) {
                    HeroPillButton(
                        text = "My List",
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
                    .onFocusChanged { muteFocused = it.isFocused }
                    .focusable()
                    .clickable(onClick = onToggleMute),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "Toggle preview audio",
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
    val scale by animateFloatAsState(if (focused) 1.05f else 1f, label = "pillScale")

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
                } else if (!isPrimary && spec.preset == UiPreset.APPLE_TV) {
                    Modifier.border(1.dp, colors.badgeBorder, shape)
                } else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (spec.preset == UiPreset.APPLE_TV) 22.dp else 20.dp,
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
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun HeroPreviewVideoLayer(
    previewStream: ResolvedPreviewStream,
    isMuted: Boolean,
    isVideoReady: Boolean,
    onFirstFrame: () -> Unit
) {
    val context = LocalContext.current
    val previewPlayer = remember(context) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { previewPlayer.release() }
    }

    LaunchedEffect(previewPlayer, isMuted) {
        previewPlayer.volume = if (isMuted) 0f else 0.7f
    }

    LaunchedEffect(previewStream.videoUrl) {
        val dsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
        if (previewStream.audioUrl != null) {
            val vSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(previewStream.videoUrl))
            val aSrc = ProgressiveMediaSource.Factory(dsFactory)
                .createMediaSource(MediaItem.fromUri(previewStream.audioUrl))
            previewPlayer.setMediaSource(MergingMediaSource(vSrc, aSrc))
        } else {
            previewPlayer.setMediaItem(MediaItem.fromUri(previewStream.videoUrl))
        }
        previewPlayer.addListener(object : Player.Listener {
            override fun onRenderedFirstFrame() {
                onFirstFrame()
            }
        })
        previewPlayer.prepare()
        previewPlayer.play()
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
