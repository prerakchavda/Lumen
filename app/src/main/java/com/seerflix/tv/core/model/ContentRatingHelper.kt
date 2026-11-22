package com.seerflix.tv.core.model

object ContentRatingHelper {
    private val KIDS = setOf("G", "TV-Y", "TV-G", "TV-Y7", "TV-Y7-FV", "U", "AL", "E", "ALL", "TP", "L")
    private val FAMILY = setOf("PG", "TV-PG")
    private val TEEN = setOf("PG-13", "TV-14", "12", "12A", "13+", "14+", "M")
    private val MATURE = setOf(
        "R", "NC-17", "TV-MA", "18", "18+", "X", "NR", "UNRATED", "MA", "AO", "XXX", "R18", "R18+"
    )
    private val COUNTRY_PREFIX = Regex("^[A-Z]{2}-(.+)$")
    private val AGE_DIGITS = Regex("(\\d{1,2})")

    /**
     * Rank of a title with no rating at all. Treated as mature: discovery rows built from Seerr
     * carry no certification unless the title is in the library, and counting those as PG let
     * R-rated trending / search results through to Kids profiles.
     */
    const val UNRATED_RANK = 4

    fun ratingRank(officialRating: String?): Int {
        if (officialRating.isNullOrBlank()) return UNRATED_RANK
        val norm = officialRating.trim().uppercase()
        knownRank(norm)?.let { return it }
        // Jellyfin often stores country-prefixed ratings ("DE-16", "GB-15", "AU-MA15+").
        val unprefixed = COUNTRY_PREFIX.find(norm)?.groupValues?.get(1)
        if (unprefixed != null) knownRank(unprefixed)?.let { return it }
        // Age-based systems (FSK-16, "16+", "MA15+", PEGI 18, "15"): rank by the minimum age.
        val age = AGE_DIGITS.find(unprefixed ?: norm)?.groupValues?.get(1)?.toIntOrNull()
        if (age != null) {
            return when {
                age <= 6 -> 1
                age <= 11 -> 2
                age <= 14 -> 3
                else -> 4
            }
        }
        return 2
    }

    private fun knownRank(norm: String): Int? = when (norm) {
        in KIDS -> 1
        in FAMILY -> 2
        in TEEN -> 3
        in MATURE -> 4
        else -> null
    }

    fun isAllowed(item: UnifiedMediaItem, profile: UserProfile?): Boolean {
        if (profile == null) return true
        val limit = profile.effectiveRatingLimit
        if (limit == ContentRatingLimit.ALL) return true
        if (item.isAdult) return false
        val rank = ratingRank(item.maturityRating)
        if (rank > limit.maxRank) return false
        if (profile.kidsMode && limit.maxRank <= ContentRatingLimit.PG.maxRank) {
            val restrictedGenres = setOf("Horror", "Thriller", "Crime")
            if (item.genres.any { g -> restrictedGenres.any { r -> g.contains(r, ignoreCase = true) } }) {
                return false
            }
        }
        return true
    }

    fun filterForProfile(items: List<UnifiedMediaItem>, profile: UserProfile?): List<UnifiedMediaItem> {
        if (profile == null || profile.effectiveRatingLimit == ContentRatingLimit.ALL) return items
        return items.filter { isAllowed(it, profile) }
    }
}
