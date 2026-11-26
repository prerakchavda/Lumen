package com.seerflix.tv.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.ScreensaverSlide
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * OLED-safe living-room ambient screensaver: dims to rotating high-resolution library backdrops
 * with a softly drifting clock and title to avoid static burn-in, and wakes on any key press.
 */
@Composable
fun ScreensaverOverlay(
    slides: List<ScreensaverSlide>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = AppStrings.current
    val focusRequester = remember { FocusRequester() }
    var slideIndex by remember { mutableIntStateOf(0) }
    var driftPositionIndex by remember { mutableIntStateOf(0) }
    var nowText by remember { mutableStateOf(formatClockNow()) }
    var dateText by remember { mutableStateOf(formatDateNow()) }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    // Update clock every 15s and drift corner every 30s for OLED burn-in protection.
    LaunchedEffect(Unit) {
        var tick = 0
        while (true) {
            delay(15_000L)
            nowText = formatClockNow()
            dateText = formatDateNow()
            tick++
            if (tick % 2 == 0) {
                driftPositionIndex = (driftPositionIndex + 1) % DRIFT_ALIGNMENTS.size
            }
        }
    }

    // Rotate backdrops every 18s when slides are available.
    LaunchedEffect(slides) {
        if (slides.size <= 1) return@LaunchedEffect
        while (true) {
            delay(18_000L)
            slideIndex = (slideIndex + 1) % slides.size
        }
    }

    val currentSlide = slides.getOrNull(slideIndex.coerceIn(0, (slides.size - 1).coerceAtLeast(0)))
    val clockAlignment = DRIFT_ALIGNMENTS[driftPositionIndex]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    onDismiss()
                    true
                } else {
                    true
                }
            }
            .clickable(onClick = onDismiss)
    ) {
        if (currentSlide != null) {
            Crossfade(
                targetState = currentSlide,
                animationSpec = tween(durationMillis = 1200),
                label = "screensaver_backdrop"
            ) { slide ->
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = slide.backdropUrl,
                        contentDescription = slide.title,
                        contentScale = ContentScale.Crop,
                        alpha = 0.42f,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Deep OLED vignette around edges so pixels stay near-black
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.65f),
                                        Color.Black.copy(alpha = 0.20f),
                                        Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                }
            }
        }

        // Drifting clock & artwork caption (shifts quadrant periodically to prevent OLED burn-in)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 64.dp, vertical = 52.dp),
            contentAlignment = clockAlignment
        ) {
            Column(
                horizontalAlignment = when (clockAlignment) {
                    Alignment.TopEnd, Alignment.BottomEnd -> Alignment.End
                    else -> Alignment.Start
                },
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = nowText,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 54.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 1.sp
                )
                Text(
                    text = dateText,
                    color = Color.White.copy(alpha = 0.56f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
                if (currentSlide != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = currentSlide.title,
                        color = Color.White.copy(alpha = 0.82f),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!currentSlide.subtitle.isNullOrBlank()) {
                        Text(
                            text = currentSlide.subtitle,
                            color = Color.White.copy(alpha = 0.48f),
                            fontSize = 13.sp
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = strings.screensaverWakeHint,
                    color = Color.White.copy(alpha = 0.34f),
                    fontSize = 12.sp
                )
            }
        }
    }
}

private val DRIFT_ALIGNMENTS = listOf(
    Alignment.BottomStart,
    Alignment.BottomEnd,
    Alignment.TopEnd,
    Alignment.TopStart
)

private val CLOCK_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMM")

private fun formatClockNow(): String = LocalDateTime.now().format(CLOCK_FMT)
private fun formatDateNow(): String = LocalDateTime.now().format(DATE_FMT)
