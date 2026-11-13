package com.seerflix.tv.core.data

import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object UpcomingDateFormatter {

    private val shortMonthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun parseLocalDate(rawDate: String?): LocalDate? {
        val trimmed = rawDate?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Try standard YYYY-MM-DD prefix first
        if (trimmed.length >= 10) {
            runCatching { LocalDate.parse(trimmed.substring(0, 10)) }
                .getOrNull()
                ?.let { return it }
        }
        // Fallback: full ISO offset datetime (e.g. Jellyfin PremiereDate)
        return runCatching {
            OffsetDateTime.parse(trimmed)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDate()
        }.getOrNull()
    }

    fun formatComingSoonBadge(
        releaseDateIso: String?,
        episodeLabel: String? = null,
        today: LocalDate = LocalDate.now()
    ): String? {
        val date = parseLocalDate(releaseDateIso) ?: return episodeLabel?.takeIf { it.isNotBlank() }
        val daysUntil = ChronoUnit.DAYS.between(today, date)
        if (daysUntil < 0L) {
            return null
        }

        val dateText = when {
            daysUntil == 0L -> if (!episodeLabel.isNullOrBlank()) "Tonight" else "Today"
            daysUntil == 1L -> "Tomorrow"
            daysUntil <= 14L -> "In $daysUntil days"
            else -> date.format(shortMonthDayFormatter)
        }

        return if (!episodeLabel.isNullOrBlank()) {
            "$episodeLabel • $dateText"
        } else {
            dateText
        }
    }
}
