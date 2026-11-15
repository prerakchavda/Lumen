package com.seerflix.tv.core.data

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object UpcomingDateFormatter {

    private val shortMonthDayFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    fun parseLocalDate(rawDate: String?, zone: ZoneId = ZoneId.systemDefault()): LocalDate? {
        val trimmed = rawDate?.trim()?.takeIf { it.isNotBlank() } ?: return null
        // Full timestamp with an offset (e.g. Jellyfin episode PremiereDate "2026-04-02T01:00:00Z").
        // A real time-of-day is an air time and must be shown in the viewer's zone, otherwise an
        // episode airing 9pm EDT (01:00Z next day) shows as "Tomorrow" instead of "Tonight".
        // Midnight timestamps are how Jellyfin/TMDB encode plain dates, so keep their calendar date.
        if (trimmed.length > 10 && (trimmed[10] == 'T' || trimmed[10] == 't')) {
            val odt = runCatching { OffsetDateTime.parse(trimmed) }.getOrNull()
            if (odt != null) {
                return if (odt.toLocalTime() == LocalTime.MIDNIGHT) {
                    odt.toLocalDate()
                } else {
                    odt.atZoneSameInstant(zone).toLocalDate()
                }
            }
        }
        // Plain YYYY-MM-DD (or a timestamp without offset): use the date prefix as-is.
        if (trimmed.length >= 10) {
            runCatching { LocalDate.parse(trimmed.substring(0, 10)) }
                .getOrNull()
                ?.let { return it }
        }
        return null
    }

    fun formatComingSoonBadge(
        releaseDateIso: String?,
        episodeLabel: String? = null,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): String? {
        val date = parseLocalDate(releaseDateIso, zone) ?: return episodeLabel?.takeIf { it.isNotBlank() }
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
