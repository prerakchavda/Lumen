package com.seerflix.tv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.theme.CardPresentationStyle
import com.seerflix.tv.ui.theme.SeerflixTokens
import com.seerflix.tv.ui.theme.Top10Style
import kotlin.math.roundToInt

private val WIDESCREEN_CARD_WIDTH = 224.dp

/** Card legibility scrim; cached so focus-driven recompositions don't re-allocate the shader brush. */
@Composable
private fun rememberBottomScrim(base: Color, alpha: Float): Brush =
    remember(base, alpha) { Brush.verticalGradient(listOf(Color.Transparent, base.copy(alpha = alpha))) }

/**
 * Card artwork with an identifiable fallback: when the URL is missing or fails to load (common for
 * newly-added items before metadata/artwork is fetched) a gradient tile with the title + year is
 * drawn instead of an empty grey box. Returns true while the fallback is shown.
 */
@Composable
private fun CardArtwork(
    url: String?,
    item: UnifiedMediaItem,
    width: Dp,
    height: Dp
): Boolean {
    var failed by remember(url) { mutableStateOf(url.isNullOrBlank()) }
    if (failed) {
        ArtworkFallbackTile(item = item, compact = height < 120.dp, modifier = Modifier.fillMaxSize())
    } else {
        SizedAsyncImage(
            url = url,
            contentDescription = item.title,
            width = width,
            height = height,
            modifier = Modifier.fillMaxSize(),
            onError = { failed = true }
        )
    }
    return failed
}

@Composable
fun ArtworkFallbackTile(
    item: UnifiedMediaItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = SeerflixTokens.colors
    val tileBrush = remember(colors.surfaceElevated, colors.surface) {
        Brush.verticalGradient(listOf(colors.surfaceElevated, colors.surface))
    }
    val subtitle = remember(item.releaseYear, item.mediaType) {
        listOfNotNull(
            item.releaseYear,
            if (item.mediaType.name == "MOVIE") "Movie" else "Series"
        ).joinToString(" • ")
    }
    Box(
        modifier = modifier
            .background(tileBrush)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = item.title,
                color = colors.textPrimary,
                fontSize = if (compact) 13.sp else 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (compact) 16.sp else 18.sp
            )
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AvailabilityBadge(
    item: UnifiedMediaItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val colors = SeerflixTokens.colors
    val activeDownload = item.activeDownload
    val isRequestableBadge = activeDownload == null &&
        item.overallAvailability == AvailabilityTier.UNKNOWN &&
        item.canRequestHd

    val (text, bgColor, textColor) = when {
        activeDownload != null -> {
            val pct = activeDownload.percentage.roundToInt()
            val label = if (compact) {
                if (activeDownload.sizeBytes > 0) "DL $pct%" else "DL..."
            } else {
                activeDownload.label
            }
            Triple(label, colors.downloadingBlue.copy(alpha = 0.92f), colors.white)
        }
        item.overallAvailability == AvailabilityTier.PROCESSING -> {
            Triple(if (compact) "DL..." else "Downloading...", colors.downloadingBlue.copy(alpha = 0.92f), colors.white)
        }
        item.overallAvailability == AvailabilityTier.PENDING -> {
            Triple("Requested", colors.pendingAmber.copy(alpha = 0.92f), colors.deepBlack)
        }
        item.has4k -> {
            Triple(if (compact) "4K" else "4K Available", colors.matchGreen.copy(alpha = 0.9f), colors.deepBlack)
        }
        item.hasHd -> {
            Triple(if (compact) "PLAY" else "In Library", colors.accent.copy(alpha = 0.92f), colors.white)
        }
        item.overallAvailability == AvailabilityTier.PARTIALLY_AVAILABLE -> {
            Triple("Partial", colors.pendingAmber.copy(alpha = 0.9f), colors.deepBlack)
        }
        item.canRequestHd -> {
            Triple(if (compact) "+ REQ" else "Requestable", colors.surfaceElevated.copy(alpha = 0.88f), colors.white)
        }
        else -> return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .then(
                if (isRequestableBadge) Modifier.border(1.dp, colors.badgeBorder, RoundedCornerShape(4.dp))
                else Modifier
            )
            .padding(horizontal = if (compact) 6.dp else 9.dp, vertical = if (compact) 2.dp else 4.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NetflixPosterCard(
    item: UnifiedMediaItem,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onOptionsMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    forcePortrait: Boolean = false
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val isWidescreen = !forcePortrait && spec.cardStyle == CardPresentationStyle.WIDESCREEN_16_9
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)

    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.07f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "posterScale"
    )

    if (isWidescreen) {
        Column(
            modifier = modifier
                .width(WIDESCREEN_CARD_WIDTH)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .onFocusChanged {
                    val isNowFocused = it.isFocused || it.hasFocus
                    focused = isNowFocused
                    if (isNowFocused) onFocus()
                }
                .onPreviewKeyEvent { keyEvent ->
                    if (onOptionsMenu != null &&
                        keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                    ) {
                        onOptionsMenu()
                        true
                    } else false
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onOptionsMenu
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(corner)
                    .background(colors.surface)
                    .then(
                        if (focused) {
                            Modifier.border(2.5.dp, colors.focusBorder, corner)
                        } else {
                            Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.45f), corner)
                        }
                    )
            ) {
                CardArtwork(
                    url = item.backdropUrl ?: item.posterUrl,
                    item = item,
                    width = WIDESCREEN_CARD_WIDTH,
                    height = WIDESCREEN_CARD_WIDTH * 9f / 16f
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .align(Alignment.BottomCenter)
                        .background(rememberBottomScrim(colors.deepBlack, 0.85f))
                )
                AvailabilityBadge(
                    item = item,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                )
                if (item.watchProgressFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color(0x55FFFFFF))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(item.watchProgressFraction)
                                .fillMaxHeight()
                                .background(colors.progressFill)
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                color = if (focused) colors.white else colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                item.releaseYear,
                if (item.mediaType.name == "MOVIE") "Movie" else "Series"
            ).joinToString(" • ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    val cardWidth = if (spec.preset == UiPreset.DISNEY_PLUS) 142.dp else 136.dp
    Box(
        modifier = modifier
            .width(cardWidth)
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                val isNowFocused = it.isFocused || it.hasFocus
                focused = isNowFocused
                if (isNowFocused) onFocus()
            }
            .onPreviewKeyEvent { keyEvent ->
                if (onOptionsMenu != null &&
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    onOptionsMenu()
                    true
                } else false
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOptionsMenu
            )
            .clip(corner)
            .background(colors.surface)
            .then(
                if (focused) {
                    Modifier.border(2.5.dp, colors.focusBorder, corner)
                } else {
                    Modifier.border(1.dp, Color(0x22FFFFFF), corner)
                }
            )
    ) {
        val artworkFailed = CardArtwork(
            url = item.posterUrl ?: item.backdropUrl,
            item = item,
            width = cardWidth,
            height = cardWidth * 1.5f
        )

        // Subtle bottom gradient for legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter)
                .background(rememberBottomScrim(colors.deepBlack, 0.82f))
        )

        // Top-left availability or collection badge
        if (item.boxSetId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.navBarBackground)
                    .border(1.dp, colors.badgeBorder, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "COLLECTION",
                    color = colors.white,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            AvailabilityBadge(
                item = item,
                compact = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }

        // Bottom metadata (title when missing/focused + upcoming countdown / completion pill)
        // The fallback tile already shows the title in the middle of the card.
        val hasBottomTitle = (item.posterUrl == null || focused) && !artworkFailed
        val hasBottomBadge = !item.upcomingBadgeLabel.isNullOrBlank()
        if (hasBottomTitle || hasBottomBadge) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = if (item.watchProgressFraction > 0f) 8.dp else 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasBottomTitle) {
                    Text(
                        text = item.title,
                        color = colors.white,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }
                if (hasBottomBadge) {
                    val isHighlight = item.upcomingBadgeLabel.equals("Now in Library", ignoreCase = true) ||
                        item.upcomingBadgeLabel?.contains("Tonight", ignoreCase = true) == true ||
                        item.upcomingBadgeLabel?.equals("Today", ignoreCase = true) == true
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (isHighlight) colors.accent.copy(alpha = 0.92f)
                                else colors.navBarBackground
                            )
                            .border(
                                1.dp,
                                if (isHighlight) Color.Transparent else colors.badgeBorder,
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.upcomingBadgeLabel!!,
                            color = colors.white,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (item.watchProgressFraction > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0x66FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.watchProgressFraction)
                        .fillMaxHeight()
                        .background(colors.progressFill)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Top10PosterCard(
    rank: Int,
    item: UnifiedMediaItem,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onOptionsMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "top10Scale"
    )

    when (spec.top10Style) {
        Top10Style.CORNER_PILL_BADGE -> {
            Column(
                modifier = modifier
                    .width(232.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .onFocusChanged {
                        val isNowFocused = it.isFocused || it.hasFocus
                        focused = isNowFocused
                        if (isNowFocused) onFocus()
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (onOptionsMenu != null &&
                            keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                        ) {
                            onOptionsMenu()
                            true
                        } else false
                    }
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onOptionsMenu
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(corner)
                        .background(colors.surface)
                        .then(
                            if (focused) Modifier.border(2.5.dp, colors.focusBorder, corner)
                            else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.45f), corner)
                        )
                ) {
                    CardArtwork(
                        url = item.backdropUrl ?: item.posterUrl,
                        item = item,
                        width = 232.dp,
                        height = 232.dp * 9f / 16f
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.deepBlack.copy(alpha = 0.82f))
                            .border(1.dp, colors.badgeBorder, RoundedCornerShape(10.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "#$rank",
                            color = colors.white,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    AvailabilityBadge(
                        item = item,
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.title,
                    color = if (focused) colors.white else colors.textPrimary,
                    fontSize = 13.sp,
                    fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Top10Style.SAPPHIRE_BADGE -> {
            Box(
                modifier = modifier
                    .width(146.dp)
                    .aspectRatio(2f / 3f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .onFocusChanged {
                        val isNowFocused = it.isFocused || it.hasFocus
                        focused = isNowFocused
                        if (isNowFocused) onFocus()
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (onOptionsMenu != null &&
                            keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                        ) {
                            onOptionsMenu()
                            true
                        } else false
                    }
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onOptionsMenu
                    )
                    .clip(corner)
                    .background(colors.surface)
                    .then(
                        if (focused) Modifier.border(2.5.dp, colors.focusBorder, corner)
                        else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.4f), corner)
                    )
            ) {
                CardArtwork(
                    url = item.posterUrl ?: item.backdropUrl,
                    item = item,
                    width = 146.dp,
                    height = 219.dp
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.accent.copy(alpha = 0.95f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "TOP $rank",
                        color = colors.white,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                }
                AvailabilityBadge(
                    item = item,
                    compact = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                )
            }
        }
        Top10Style.OUTLINED_NUMERAL -> {
            val rankText = remember(rank) { rank.toString() }
            val (strokePaint, fillPaint) = remember(rank, colors.deepBlack) {
                val boldSans = android.graphics.Typeface.create(
                    android.graphics.Typeface.SANS_SERIF,
                    android.graphics.Typeface.BOLD
                )
                val sp = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 6f
                    color = Color(0xFF707070).toArgb()
                    typeface = boldSans
                    letterSpacing = -0.1f
                }
                val fp = android.graphics.Paint(sp).apply {
                    style = android.graphics.Paint.Style.FILL
                    color = colors.deepBlack.toArgb()
                }
                sp to fp
            }
            Box(
                modifier = modifier
                    .width(198.dp)
                    .height(196.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }
                    .onFocusChanged {
                        val isNowFocused = it.isFocused || it.hasFocus
                        focused = isNowFocused
                        if (isNowFocused) onFocus()
                    }
                    .onPreviewKeyEvent { keyEvent ->
                        if (onOptionsMenu != null &&
                            keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                            keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                        ) {
                            onOptionsMenu()
                            true
                        } else false
                    }
                    .combinedClickable(
                        onClick = onClick,
                        onLongClick = onOptionsMenu
                    )
            ) {
                // Giant outlined Netflix Top-10 rank numeral on the left
                Canvas(
                    modifier = Modifier
                        .width(92.dp)
                        .fillMaxHeight()
                        .align(Alignment.BottomStart)
                ) {
                    val targetTextSize = if (rank >= 10) size.height * 0.78f else size.height * 0.92f
                    strokePaint.textSize = targetTextSize
                    fillPaint.textSize = targetTextSize
                    drawIntoCanvas { canvas ->
                        val y = size.height * 0.92f
                        val x = if (rank >= 10) -6f else 6f
                        canvas.nativeCanvas.drawText(rankText, x, y, fillPaint)
                        canvas.nativeCanvas.drawText(rankText, x, y, strokePaint)
                    }
                }

                // Poster tucked neatly over the right side of the rank number
                Box(
                    modifier = Modifier
                        .width(126.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                        .clip(corner)
                        .background(colors.surface)
                        .then(
                            if (focused) {
                                Modifier.border(2.5.dp, colors.focusBorder, corner)
                            } else {
                                Modifier.border(1.dp, Color(0x22FFFFFF), corner)
                            }
                        )
                ) {
                    CardArtwork(
                        url = item.posterUrl ?: item.backdropUrl,
                        item = item,
                        width = 126.dp,
                        height = 196.dp
                    )
                    AvailabilityBadge(
                        item = item,
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    item: UnifiedMediaItem,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onOptionsMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    val cardWidth = if (spec.preset == UiPreset.APPLE_TV) 228.dp else 212.dp
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "cwScale"
    )

    Column(
        modifier = modifier
            .width(cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                val isNowFocused = it.isFocused || it.hasFocus
                focused = isNowFocused
                if (isNowFocused) onFocus()
            }
            .onPreviewKeyEvent { keyEvent ->
                if (onOptionsMenu != null &&
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_MENU
                ) {
                    onOptionsMenu()
                    true
                } else false
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOptionsMenu
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(corner)
                .background(colors.surface)
                .then(
                    if (focused) {
                        Modifier.border(2.5.dp, colors.focusBorder, corner)
                    } else {
                        Modifier.border(1.dp, Color(0x22FFFFFF), corner)
                    }
                )
        ) {
            CardArtwork(
                url = item.backdropUrl ?: item.posterUrl,
                item = item,
                width = cardWidth,
                height = cardWidth * 9f / 16f
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(rememberBottomScrim(colors.deepBlack, 0.85f))
            )

            if (!item.episodeLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.deepBlack.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.episodeLabel,
                        color = colors.white,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (focused && onOptionsMenu != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.deepBlack.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Hold OK",
                        color = colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(0x66FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.watchProgressFraction.coerceAtLeast(0.05f))
                        .fillMaxHeight()
                        .background(colors.progressFill)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            color = if (focused) colors.white else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
