package com.seerflix.tv.core.data

import com.seerflix.tv.core.network.SeerrMovieDetailsDto
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Major streaming platforms surfaced as Top 10 rows via Seerr/TMDB watch providers.
 *
 * Multiple pipe-separated IDs cover regional / legacy TMDB provider IDs for the same service
 * (e.g. Prime Video uses 9 in US/UK and 119 in other regions; Max uses 1899 and legacy 384).
 */
enum class StreamingService(
    val id: String,
    val displayName: String,
    val providerIds: String
) {
    NETFLIX("netflix", "Netflix", "8"),
    PRIME_VIDEO("prime", "Prime Video", "9|119"),
    DISNEY_PLUS("disney_plus", "Disney+", "337"),
    APPLE_TV("apple_tv", "Apple TV+", "350"),
    MAX("max", "Max", "1899|384"),
    HULU("hulu", "Hulu", "15");

    val homeRowId: String get() = "top10_streaming_$id"
    val newAndPopularRowId: String get() = "np_top10_$id"
}

enum class HomeReleaseType(val badgePrefix: String) {
    DIGITAL("Digital"),
    PHYSICAL("DVD / Blu-ray"),
    TV("TV")
}

data class HomeReleaseDate(
    val date: LocalDate,
    val type: HomeReleaseType
)

/**
 * Classifies whether a movie is actually watchable at home (Digital / Streaming / DVD / TV)
 * versus still unreleased or playing in theaters only, using TMDB's typed `releases` metadata
 * forwarded by Seerr on `/api/v1/movie/{id}`.
 */
object MovieReleaseClassifier {
    /** TMDB release types representing a home/streaming/broadcast release. */
    private const val TYPE_PREMIERE = 1
    private const val TYPE_THEATRICAL_LIMITED = 2
    private const val TYPE_THEATRICAL = 3
    private const val TYPE_DIGITAL = 4
    private const val TYPE_PHYSICAL = 5
    private const val TYPE_TV = 6

    /**
     * When TMDB only has theatrical dates (no Digital/Physical entry yet), treat movies released
     * in theaters within this window as still theater-only rather than watchable at home.
     */
    const val THEATRICAL_ONLY_WINDOW_DAYS = 120L

    /**
     * Earliest Digital (type 4), Physical (type 5), or TV (type 6) release date for [details],
     * preferring [country], then US, then any available region.
     */
    fun homeReleaseDate(
        details: SeerrMovieDetailsDto,
        country: String = "US"
    ): HomeReleaseDate? {
        val byCountry = details.releases?.results.orEmpty()
        if (byCountry.isEmpty()) return null

        val preferred = byCountry.filter { it.countryCode.equals(country, ignoreCase = true) }
        val us = if (!country.equals("US", ignoreCase = true)) {
            byCountry.filter { it.countryCode.equals("US", ignoreCase = true) }
        } else emptyList()
        val others = byCountry.filterNot {
            it.countryCode.equals(country, ignoreCase = true) || it.countryCode.equals("US", ignoreCase = true)
        }

        for (bucket in listOf(preferred, us, others)) {
            val found = bucket.asSequence()
                .flatMap { it.releaseDates.asSequence() }
                .mapNotNull { entry ->
                    val releaseType = when (entry.type) {
                        TYPE_DIGITAL -> HomeReleaseType.DIGITAL
                        TYPE_PHYSICAL -> HomeReleaseType.PHYSICAL
                        TYPE_TV -> HomeReleaseType.TV
                        else -> null
                    } ?: return@mapNotNull null
                    val parsed = UpcomingDateFormatter.parseLocalDate(entry.releaseDate) ?: return@mapNotNull null
                    HomeReleaseDate(parsed, releaseType)
                }
                .minByOrNull { it.date }
            if (found != null) return found
        }
        return null
    }

    /**
     * Earliest known theatrical/premiere release date from `releases` (or top-level `releaseDate`).
     */
    fun earliestTheatricalDate(details: SeerrMovieDetailsDto): LocalDate? {
        val fromReleases = details.releases?.results.orEmpty()
            .asSequence()
            .flatMap { it.releaseDates.asSequence() }
            .filter { it.type in setOf(TYPE_PREMIERE, TYPE_THEATRICAL_LIMITED, TYPE_THEATRICAL) }
            .mapNotNull { UpcomingDateFormatter.parseLocalDate(it.releaseDate) }
            .minOrNull()
        return fromReleases ?: UpcomingDateFormatter.parseLocalDate(details.releaseDate)
    }

    /**
     * True if the movie is watchable at home on [today] (already out on Digital, Physical, or TV).
     *
     * Rules:
     * 1. If a Digital/Physical/TV date exists, it is available iff that date is on or before [today].
     * 2. If `releases` contains only theatrical/premiere dates (no Digital/Physical/TV entry), it is
     *    excluded while inside the theatrical window (future or < [THEATRICAL_ONLY_WINDOW_DAYS] old).
     * 3. If `releases` has no typed entries at all (older server / minimal metadata), fall back to
     *    top-level `releaseDate <= today`.
     */
    fun isAvailableAtHome(
        details: SeerrMovieDetailsDto,
        today: LocalDate,
        country: String = "US",
        fallbackReleaseDate: String? = null
    ): Boolean {
        val homeDate = homeReleaseDate(details, country)
        if (homeDate != null) {
            return !homeDate.date.isAfter(today)
        }

        val hasTypedEntries = details.releases?.results.orEmpty()
            .any { c -> c.releaseDates.any { it.type != null } }
        if (hasTypedEntries) {
            val theatrical = earliestTheatricalDate(details)
                ?: UpcomingDateFormatter.parseLocalDate(fallbackReleaseDate)
                ?: return false
            if (theatrical.isAfter(today)) return false
            val ageDays = ChronoUnit.DAYS.between(theatrical, today)
            return ageDays >= THEATRICAL_ONLY_WINDOW_DAYS
        }

        val fallbackDate = UpcomingDateFormatter.parseLocalDate(details.releaseDate)
            ?: UpcomingDateFormatter.parseLocalDate(fallbackReleaseDate)
            ?: return false
        return !fallbackDate.isAfter(today)
    }

    /**
     * Formats a concise badge like `"Digital • Tomorrow"` or `"DVD / Blu-ray • May 12"` for
     * movies arriving on streaming or physical media.
     */
    fun formatUpcomingHomeBadge(
        homeRelease: HomeReleaseDate,
        today: LocalDate
    ): String? {
        val dateText = UpcomingDateFormatter.formatComingSoonBadge(
            releaseDateIso = homeRelease.date.toString(),
            today = today
        ) ?: return null
        return "${homeRelease.type.badgePrefix} • $dateText"
    }
}
