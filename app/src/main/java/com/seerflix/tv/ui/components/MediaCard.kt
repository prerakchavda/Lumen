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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
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
import com.seerflix.tv.core.i18n.AppStrings
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
    val strings = AppStrings.current
    val subtitle = remember(item.releaseYear, item.mediaType, strings) {
        listOfNotNull(
            item.releaseYear,
            if (item.mediaType.name == "MOVIE") strings.commonMovie else strings.commonSeries
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
    val strings = AppStrings.current
    val activeDownload = item.activeDownload
    val isRequestableBadge = activeDownload == null &&
        item.overallAvailability == AvailabilityTier.UNKNOWN &&
        item.canRequestHd

    val (text, bgColor, textColor) = when {
        activeDownload != null -> {
            val label = if (compact) {
                if (activeDownload.sizeBytes > 0) "↓ ${formatPercent(activeDownload.percentage)}" else "↓ …"
            } else {
                activeDownload.label
            }
            Triple(label, colors.downloadingBlue.copy(alpha = 0.92f), colors.white)
        }
        item.overallAvailability == AvailabilityTier.PROCESSING -> {
            Triple(if (compact) "↓ …" else strings.cardDownloading, colors.downloadingBlue.copy(alpha = 0.92f), colors.white)
        }
        item.overallAvailability == AvailabilityTier.PENDING -> {
            Triple(strings.commonRequested, colors.pendingAmber.copy(alpha = 0.92f), colors.deepBlack)
        }
        item.has4k -> {
            Triple(if (compact) "4K" else strings.detailsTierAvailable.format("4K"), colors.matchGreen.copy(alpha = 0.9f), colors.deepBlack)
        }
        item.hasHd -> {
            // On cards "it's in your library" is the normal case, so it gets no badge at all
            // (a PLAY badge on every poster told you nothing). Banner / Details still say it.
            if (compact) return
            Triple(strings.commonInLibrary, colors.accent.copy(alpha = 0.92f), colors.white)
        }
        item.overallAvailability == AvailabilityTier.PARTIALLY_AVAILABLE -> {
            Triple(strings.cardPartial, colors.pendingAmber.copy(alpha = 0.9f), colors.deepBlack)
        }
        item.canRequestHd -> {
            Triple(if (compact) strings.cardBadgeRequest else strings.homeFilterRequestable, colors.surfaceElevated.copy(alpha = 0.88f), colors.white)
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
            maxLines = 1,
            style = TabularNumbers
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
                if (item.mediaType.name == "MOVIE") AppStrings.current.commonMovie else AppStrings.current.commonSeries
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
            .aspectRatio(2f / 3f)
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
            width = cardWidth,
            height = cardWidth * 1.5f
        )

        val hasUpcomingBadge = !item.upcomingBadgeLabel.isNullOrBlank()
        val hasNetflixBadge = !hasUpcomingBadge &&
            !item.netflixBadgeLabel.isNullOrBlank() &&
            spec.preset == UiPreset.NETFLIX &&
            item.boxSetId == null
        val hasBottomBadge = hasUpcomingBadge || hasNetflixBadge
        if (hasBottomBadge) {
            // Legibility gradient, only when something is drawn over the bottom of the poster.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .align(Alignment.BottomCenter)
                    .background(rememberBottomScrim(colors.deepBlack, 0.78f))
            )
        }

        // Top-left Netflix TOP 10 corner ribbon, Folder, or Collection badge
        if (item.boxSetId != null || item.isFolder) {
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
                    text = if (item.isFolder) AppStrings.current.libraryFolderBadge else AppStrings.current.cardCollection,
                    color = colors.white,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            if (item.isTop10 && spec.preset == UiPreset.NETFLIX) {
                Top10CornerRibbon(
                    modifier = Modifier.align(Alignment.TopStart)
                )
            }
            // Placed at TopEnd so it never collides with Netflix's TopStart TOP 10 ribbon
            AvailabilityBadge(
                item = item,
                compact = true,
                modifier = Modifier
                    .align(if (spec.preset == UiPreset.NETFLIX) Alignment.TopEnd else Alignment.TopStart)
                    .padding(6.dp)
            )
        }

        // Upcoming countdown / completion pill OR Netflix centered bottom status ribbon
        if (hasUpcomingBadge) {
            val isHighlight = item.upcomingBadgeLabel.equals("Now in Library", ignoreCase = true) ||
                item.upcomingBadgeLabel?.contains("Tonight", ignoreCase = true) == true ||
                item.upcomingBadgeLabel?.equals("Today", ignoreCase = true) == true
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 6.dp, vertical = if (item.watchProgressFraction > 0f) 8.dp else 6.dp)
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
                    overflow = TextOverflow.Ellipsis,
                    style = TabularNumbers
                )
            }
        } else if (hasNetflixBadge) {
            NetflixBottomRibbon(
                label = item.netflixBadgeLabel!!,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (item.watchProgressFraction > 0f) 4.dp else 0.dp)
            )
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
    if (spec.showPosterTitleBelow) {
        // Title under poster for presets that show labels (Apple TV+ / Disney+); Netflix keeps full-bleed posters.
        Spacer(Modifier.height(5.dp))
        Text(
            text = item.title,
            color = if (focused) colors.white else colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    }
}

/** Netflix's signature red top-left "TOP 10" corner ribbon shown on Top 10 titles across regular rows. */
@Composable
fun Top10CornerRibbon(modifier: Modifier = Modifier) {
    val colors = SeerflixTokens.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(bottomEnd = 4.dp))
            .background(colors.accent)
            .padding(horizontal = 5.dp, vertical = 3.dp)
    ) {
        Text(
            text = "TOP",
            color = colors.white,
            fontSize = 6.5.sp,
            lineHeight = 7.5.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.4.sp,
            maxLines = 1
        )
        Text(
            text = "10",
            color = colors.white,
            fontSize = 10.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            style = TabularNumbers
        )
    }
}

/** Netflix's centered bottom-edge red pill ("Recently Added", "New Season", "New Episode"). */
@Composable
fun NetflixBottomRibbon(
    label: String,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            .background(colors.accent)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            color = colors.white,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
                            fontWeight = FontWeight.Black,
                            style = TabularNumbers
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
                        text = AppStrings.current.cardTopRank.format(rank),
                        color = colors.white,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        style = TabularNumbers
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
            // Heavy condensed face (Roboto Condensed Bold ships on every Android TV), so "1"
            // and "8" sit at the same visual weight and "10" fits without shrinking to a smudge.
            val numeralTypeface = remember {
                android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)
            }
            val strokePaint = remember {
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    typeface = numeralTypeface
                }
            }
            val fillPaint = remember {
                android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.FILL
                    typeface = numeralTypeface
                }
            }
            val glyphBounds = remember { android.graphics.Rect() }
            val strokeColor = if (focused) colors.white else TOP10_NUMERAL_IDLE
            val fillColor = colors.background
            Box(
                modifier = modifier
                    .width(TOP10_NUMERAL_SLOT + TOP10_POSTER_WIDTH)
                    .height(TOP10_POSTER_HEIGHT)
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
                // The rank numeral: bottom flush with the poster's bottom edge, right edge tucked
                // TOP10_NUMERAL_TUCK under the poster — the same overlap for every rank, whatever
                // the digit's width. Two-digit ranks are narrowed rather than shrunk.
                Canvas(
                    modifier = Modifier
                        .width(TOP10_NUMERAL_SLOT + TOP10_NUMERAL_TUCK)
                        .fillMaxHeight()
                        .align(Alignment.BottomStart)
                ) {
                    val strokePx = TOP10_NUMERAL_STROKE.toPx()
                    strokePaint.strokeWidth = strokePx
                    strokePaint.color = strokeColor.toArgb()
                    fillPaint.color = fillColor.toArgb()
                    val scaleX = if (rankText.length > 1) 0.72f else 1f
                    strokePaint.textScaleX = scaleX
                    fillPaint.textScaleX = scaleX
                    // Digit height ~= 88% of the poster: Roboto's digits are ~0.71 em tall.
                    var textSize = size.height * 0.88f / 0.71f
                    strokePaint.textSize = textSize
                    strokePaint.getTextBounds(rankText, 0, rankText.length, glyphBounds)
                    val maxWidth = size.width - strokePx
                    if (glyphBounds.width() > maxWidth) {
                        textSize *= maxWidth / glyphBounds.width()
                        strokePaint.textSize = textSize
                        strokePaint.getTextBounds(rankText, 0, rankText.length, glyphBounds)
                    }
                    fillPaint.textSize = textSize
                    val x = size.width - glyphBounds.right - strokePx / 2f
                    val y = size.height - glyphBounds.bottom - strokePx / 2f
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawText(rankText, x, y, fillPaint)
                        canvas.nativeCanvas.drawText(rankText, x, y, strokePaint)
                    }
                }

                // Poster over the right side of the numeral
                Box(
                    modifier = Modifier
                        .width(TOP10_POSTER_WIDTH)
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
                        width = TOP10_POSTER_WIDTH,
                        height = TOP10_POSTER_HEIGHT
                    )
                    AvailabilityBadge(
                        item = item,
                        compact = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                    )
                    if (!item.netflixBadgeLabel.isNullOrBlank()) {
                        NetflixBottomRibbon(
                            label = item.netflixBadgeLabel,
                            modifier = Modifier.align(Alignment.BottomCenter)
                        )
                    }
                }
            }
        }
    }
}

private val TOP10_POSTER_WIDTH = 128.dp
private val TOP10_POSTER_HEIGHT = 192.dp
/** Visible width left of the poster for the numeral. */
private val TOP10_NUMERAL_SLOT = 76.dp
/** How far the numeral runs under the poster's left edge. */
private val TOP10_NUMERAL_TUCK = 22.dp
private val TOP10_NUMERAL_STROKE = 3.dp
private val TOP10_NUMERAL_IDLE = Color(0xFFD0D0D0)

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

            // Netflix-style centered circular Play button indicator
            if (spec.preset == UiPreset.NETFLIX) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(if (focused) 44.dp else 40.dp)
                        .clip(CircleShape)
                        .background(Color(if (focused) 0xCC000000 else 0x8C000000))
                        .border(1.5.dp, colors.white.copy(alpha = if (focused) 1f else 0.88f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = colors.white,
                        modifier = Modifier.size(if (focused) 26.dp else 24.dp)
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
                        text = AppStrings.current.cardHoldOk,
                        color = colors.textSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Progress bar (shown for in-progress titles; omitted for unstarted Next Up episodes)
            if (item.resumePositionTicks > 0L || !item.isNextUp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x55FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.watchProgressFraction.coerceAtLeast(0.05f))
                            .fillMaxHeight()
                            .background(colors.progressFill)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.seriesName?.takeIf { it.isNotBlank() && !item.episodeLabel.isNullOrBlank() } ?: item.title,
            color = if (focused) colors.white else colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        // "S2 E5 · Up Next" for Next Up episodes, or "S2 E4 · 32m left" for in-progress titles.
        val strings = AppStrings.current
        val subtitle = remember(item.episodeLabel, item.runtimeTicks, item.resumePositionTicks, item.isNextUp, strings) {
            val statusPart = if (item.isNextUp && item.resumePositionTicks <= 0L) {
                strings.cardUpNext
            } else {
                formatTimeLeft(item.runtimeTicks, item.resumePositionTicks)?.let { strings.cardTimeLeft.format(it) }
            }
            listOfNotNull(
                item.episodeLabel?.takeIf { it.isNotBlank() }?.let(::normalizeEpisodeCode),
                statusPart
            ).joinToString(" · ")
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                color = if (item.isNextUp && item.resumePositionTicks <= 0L) colors.accent else colors.textSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TabularNumbers
            )
        }
    }
}
