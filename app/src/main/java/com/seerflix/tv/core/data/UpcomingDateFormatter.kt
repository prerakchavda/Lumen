package com.seerflix.tv.core.data

import com.seerflix.tv.core.i18n.UiStrings
import java.time.LocalDate
import java.time.LocalTime
import java.time.MonthDay
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
            daysUntil == 0L -> if (!episodeLabel.isNullOrBlank()) TONIGHT else TODAY
            daysUntil == 1L -> TOMORROW
            daysUntil <= 14L -> "$IN_DAYS_PREFIX$daysUntil$IN_DAYS_SUFFIX"
            else -> date.format(shortMonthDayFormatter)
        }

        return if (!episodeLabel.isNullOrBlank()) {
            "$episodeLabel$SEPARATOR$dateText"
        } else {
            dateText
        }
    }

    // ---- Display ----
    // Badge labels are stored on items (and in the Home cache) in this canonical English form.
    // Screens localize them when drawn, and decide the highlight from the parsed badge, never
    // from the localized text.

    /** Set by the catalog on reminder titles that have arrived. */
    const val NOW_IN_LIBRARY = "Now in Library"

    private const val TONIGHT = "Tonight"
    private const val TODAY = "Today"
    private const val TOMORROW = "Tomorrow"
    private const val IN_DAYS_PREFIX = "In "
    private const val IN_DAYS_SUFFIX = " days"
    private const val SEPARATOR = " • "
    private val IN_DAYS = Regex("""^In (\d+) days$""")
    private val COLLECTION_OWNED_OF = Regex("""^(\d+) of (\d+) in Library$""")
    private val COLLECTION_OWNED = Regex("""^(\d+) in Library$""")

    private enum class When { TONIGHT, TODAY, TOMORROW, IN_DAYS, DATE }

    private class DatePart(val kind: When, val days: Int = 0, val monthDay: MonthDay? = null)

    private fun parseDatePart(text: String): DatePart? = when (text) {
        TONIGHT -> DatePart(When.TONIGHT)
        TODAY -> DatePart(When.TODAY)
        TOMORROW -> DatePart(When.TOMORROW)
        else -> IN_DAYS.find(text)?.groupValues?.get(1)?.toIntOrNull()?.let { DatePart(When.IN_DAYS, days = it) }
            ?: runCatching { MonthDay.parse(text, shortMonthDayFormatter) }.getOrNull()
                ?.let { DatePart(When.DATE, monthDay = it) }
    }

    /** Splits "S1 E2 • Tonight" / "Digital • In 3 days" / "Today" into (prefix, date part). */
    private fun splitBadge(label: String): Pair<String?, DatePart>? {
        val idx = label.lastIndexOf(SEPARATOR)
        val prefix = if (idx >= 0) label.substring(0, idx) else null
        val datePart = parseDatePart(if (idx >= 0) label.substring(idx + SEPARATOR.length) else label) ?: return null
        return prefix to datePart
    }

    /** Whether the badge gets the accent highlight: arrived reminders and things airing today. */
    fun isHighlightBadge(label: String?): Boolean {
        if (label.isNullOrBlank()) return false
        if (label == NOW_IN_LIBRARY) return true
        // Collection completion badges are never dates (and would cost a failed date parse).
        if (COLLECTION_OWNED_OF.containsMatchIn(label) || COLLECTION_OWNED.containsMatchIn(label)) return false
        val (prefix, date) = splitBadge(label) ?: return false
        return date.kind == When.TONIGHT || (date.kind == When.TODAY && prefix == null)
    }

    /** "3 of 5 in Library" / "3 in Library" for a collection, in the UI language. */
    fun collectionCompletionLabel(owned: Int, total: Int?, strings: UiStrings): String =
        if (total != null && total > 0) strings.collectionOwnedOfTotal.format(owned, total)
        else strings.collectionOwnedCount.format(owned)

    /** A stored badge label in the UI language; unknown labels are returned unchanged. */
    fun localizeBadge(label: String, strings: UiStrings): String {
        if (label == NOW_IN_LIBRARY) return strings.upcomingNowInLibrary
        COLLECTION_OWNED_OF.find(label)?.let { m ->
            return collectionCompletionLabel(m.groupValues[1].toInt(), m.groupValues[2].toInt(), strings)
        }
        COLLECTION_OWNED.find(label)?.let { m ->
            return collectionCompletionLabel(m.groupValues[1].toInt(), null, strings)
        }
        val (prefix, date) = splitBadge(label) ?: return label
        val dateText = when (date.kind) {
            When.TONIGHT -> strings.upcomingTonight
            When.TODAY -> strings.upcomingToday
            When.TOMORROW -> strings.upcomingTomorrow
            When.IN_DAYS -> strings.upcomingInDays.format(date.days)
            When.DATE -> runCatching {
                date.monthDay!!.format(
                    DateTimeFormatter.ofPattern(strings.upcomingDatePattern, Locale.forLanguageTag(strings.languageCode))
                )
            }.getOrDefault(label.substringAfterLast(SEPARATOR))
        }
        val localizedPrefix = when (prefix) {
            null -> return dateText
            HomeReleaseType.DIGITAL.badgePrefix -> strings.upcomingDigital
            HomeReleaseType.PHYSICAL.badgePrefix -> strings.upcomingDisc
            else -> prefix // episode codes ("S1 E2"), "TV"
        }
        return "$localizedPrefix$SEPARATOR$dateText"
    }
}
