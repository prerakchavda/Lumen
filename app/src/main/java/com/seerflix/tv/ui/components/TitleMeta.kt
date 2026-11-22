package com.seerflix.tv.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.theme.SeerflixTokens
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Numbers in Lumen share one style: tabular (fixed-width) digits, so a percentage or countdown
 * doesn't wobble as it updates and columns of numbers line up.
 */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")

/** "7.8" — the TMDB / community rating on its own 0-10 scale (shown after a star). */
fun formatRating(voteAverage: Double?): String? =
    voteAverage?.takeIf { it > 0.0 }?.let { String.format(Locale.US, "%.1f", it) }

/** Download / progress percentage, clamped and rounded: "64%". */
fun formatPercent(percentage: Float): String = "${percentage.roundToInt().coerceIn(0, 100)}%"

/** Minutes left from Jellyfin ticks (100 ns), in the same style as runtimes: "32m" / "1h 5m". */
fun formatTimeLeft(runtimeTicks: Long, positionTicks: Long): String? {
    if (runtimeTicks <= 0L || positionTicks <= 0L) return null
    val minutes = ((runtimeTicks - positionTicks) / TICKS_PER_MINUTE).toInt()
    if (minutes <= 0) return null
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private const val TICKS_PER_MINUTE = 600_000_000L

/**
 * "S2 E4" for any of the episode code spellings the app receives ("S2 • E4", "S2:E4", "S02E04",
 * "S2E4"); anything else is returned unchanged.
 */
fun normalizeEpisodeCode(label: String): String {
    val match = EPISODE_CODE.find(label) ?: return label
    val season = match.groupValues[1].toIntOrNull() ?: return label
    val episode = match.groupValues[2].toIntOrNull() ?: return label
    val code = "S$season E$episode"
    return label.replaceRange(match.range, code)
}

private val EPISODE_CODE = Regex("""\bS(\d{1,3})\s*[•·:\-]?\s*E(\d{1,4})\b""", RegexOption.IGNORE_CASE)

/**
 * The one metadata line used by the banner, the compact header and Details:
 * ★ 7.8 · 2024 · [PG-13] · 2h 46m · [4K] [Dolby Vision]
 */
@Composable
fun TitleMetaLine(
    item: UnifiedMediaItem,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
    maxBadges: Int = 2,
    extraText: List<String> = emptyList()
) {
    val colors = SeerflixTokens.colors
    val rating = remember(item.voteAverage) { formatRating(item.voteAverage) }
    val badges = remember(item.mediaBadges, item.has4k, maxBadges) {
        buildList {
            if (item.has4k) add("4K")
            addAll(item.mediaBadges.filter { it != "4K" && it != "HD" })
        }.distinct().take(maxBadges + 1)
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val segments = buildList<@Composable () -> Unit> {
            if (rating != null) add {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("★", color = colors.pendingAmber, fontSize = fontSize, fontWeight = FontWeight.Bold)
                    Text(
                        text = " $rating",
                        color = colors.white,
                        fontSize = fontSize,
                        fontWeight = FontWeight.SemiBold,
                        style = TabularNumbers
                    )
                }
            }
            item.releaseYear?.let { year ->
                add { Text(year, color = colors.textSecondary, fontSize = fontSize, style = TabularNumbers) }
            }
            item.maturityRating?.let { mr ->
                add { OutlinedTag(mr, colors.textMuted, colors.textSecondary, fontSize * 0.85f) }
            }
            item.formattedRuntime?.let { rt ->
                add { Text(rt, color = colors.textSecondary, fontSize = fontSize, style = TabularNumbers) }
            }
            extraText.forEach { text ->
                add { Text(text, color = colors.textSecondary, fontSize = fontSize, maxLines = 1, style = TabularNumbers) }
            }
        }
        segments.forEachIndexed { index, segment ->
            if (index > 0) MetaDot(colors.textMuted, fontSize)
            segment()
        }
        badges.forEach { badge ->
            OutlinedTag(badge, colors.textSecondary, colors.white, fontSize * 0.8f, bold = true)
        }
    }
}

@Composable
private fun MetaDot(color: Color, fontSize: TextUnit) {
    Text("·", color = color, fontSize = fontSize, fontWeight = FontWeight.Bold)
}

@Composable
fun OutlinedTag(
    text: String,
    borderColor: Color,
    textColor: Color,
    fontSize: TextUnit = 11.sp,
    bold: Boolean = false
) {
    Box(
        modifier = Modifier
            .border(1.dp, borderColor, RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = fontSize,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}
