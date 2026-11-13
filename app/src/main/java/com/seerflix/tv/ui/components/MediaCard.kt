package com.seerflix.tv.ui.components

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.theme.NetflixColors
import kotlin.math.roundToInt

@Composable
fun AvailabilityBadge(
    item: UnifiedMediaItem,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
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
            Triple(label, NetflixColors.DownloadingBlue.copy(alpha = 0.92f), NetflixColors.White)
        }
        item.overallAvailability == AvailabilityTier.PROCESSING -> {
            Triple(if (compact) "DL..." else "Downloading...", NetflixColors.DownloadingBlue.copy(alpha = 0.92f), NetflixColors.White)
        }
        item.overallAvailability == AvailabilityTier.PENDING -> {
            Triple("Requested", NetflixColors.PendingAmber.copy(alpha = 0.92f), NetflixColors.DeepBlack)
        }
        item.has4k -> {
            Triple(if (compact) "4K" else "4K Available", NetflixColors.MatchGreen.copy(alpha = 0.9f), NetflixColors.DeepBlack)
        }
        item.hasHd -> {
            Triple(if (compact) "PLAY" else "In Library", NetflixColors.NetflixRed.copy(alpha = 0.92f), NetflixColors.White)
        }
        item.overallAvailability == AvailabilityTier.PARTIALLY_AVAILABLE -> {
            Triple("Partial", NetflixColors.PendingAmber.copy(alpha = 0.9f), NetflixColors.DeepBlack)
        }
        item.canRequestHd -> {
            Triple(if (compact) "+ REQ" else "Requestable", Color(0xCC1B1B1E), NetflixColors.White)
        }
        else -> return
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .then(
                if (isRequestableBadge) Modifier.border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(4.dp))
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
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.07f else 1f, label = "posterScale")

    Box(
        modifier = modifier
            .width(136.dp)
            .aspectRatio(2f / 3f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
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
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOptionsMenu
            )
            .clip(RoundedCornerShape(6.dp))
            .background(NetflixColors.Surface)
            .then(
                if (focused) {
                    Modifier.border(2.5.dp, NetflixColors.White, RoundedCornerShape(6.dp))
                } else {
                    Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                }
            )
    ) {
        AsyncImage(
            model = item.posterUrl ?: item.backdropUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Subtle bottom gradient for legibility
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xCC0A0A0A))
                    )
                )
        )

        // Top-left availability or collection badge
        if (item.boxSetId != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xDD1F1F1F))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "COLLECTION",
                    color = NetflixColors.White,
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
        val hasBottomTitle = item.posterUrl == null || focused
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
                        color = NetflixColors.White,
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
                                if (isHighlight) NetflixColors.NetflixRed.copy(alpha = 0.92f)
                                else Color(0xDD111114)
                            )
                            .border(
                                1.dp,
                                if (isHighlight) Color.Transparent else Color(0x44FFFFFF),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.upcomingBadgeLabel!!,
                            color = NetflixColors.White,
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
                        .background(NetflixColors.NetflixRed)
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
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "top10Scale")

    Box(
        modifier = modifier
            .width(198.dp)
            .height(196.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
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
            .focusable()
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
            val text = rank.toString()
            drawIntoCanvas { canvas ->
                val strokePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 6f
                    color = Color(0xFF707070).toArgb()
                    textSize = if (rank >= 10) size.height * 0.78f else size.height * 0.92f
                    typeface = android.graphics.Typeface.create(
                        android.graphics.Typeface.SANS_SERIF,
                        android.graphics.Typeface.BOLD
                    )
                    letterSpacing = -0.1f
                }
                val fillPaint = android.graphics.Paint(strokePaint).apply {
                    style = android.graphics.Paint.Style.FILL
                    color = NetflixColors.DeepBlack.toArgb()
                }
                val y = size.height * 0.92f
                val x = if (rank >= 10) -6f else 6f
                canvas.nativeCanvas.drawText(text, x, y, fillPaint)
                canvas.nativeCanvas.drawText(text, x, y, strokePaint)
            }
        }

        // Poster tucked neatly over the right side of the rank number
        Box(
            modifier = Modifier
                .width(126.dp)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .clip(RoundedCornerShape(6.dp))
                .background(NetflixColors.Surface)
                .then(
                    if (focused) {
                        Modifier.border(2.5.dp, NetflixColors.White, RoundedCornerShape(6.dp))
                    } else {
                        Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                    }
                )
        ) {
            AsyncImage(
                model = item.posterUrl ?: item.backdropUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContinueWatchingCard(
    item: UnifiedMediaItem,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onOptionsMenu: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "cwScale")

    Column(
        modifier = modifier
            .width(212.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
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
            .focusable()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onOptionsMenu
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(NetflixColors.Surface)
                .then(
                    if (focused) {
                        Modifier.border(2.5.dp, NetflixColors.White, RoundedCornerShape(6.dp))
                    } else {
                        Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
                    }
                )
        ) {
            AsyncImage(
                model = item.backdropUrl ?: item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0A0A)))
                    )
            )

            if (!item.episodeLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xBB0A0A0A))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = item.episodeLabel,
                        color = NetflixColors.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (focused && onOptionsMenu != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xBB0A0A0A))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Hold OK",
                        color = NetflixColors.TextSecondary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Netflix Red progress bar
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
                        .background(NetflixColors.NetflixRed)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            text = item.title,
            color = if (focused) NetflixColors.White else NetflixColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
