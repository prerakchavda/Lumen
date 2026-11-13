package com.seerflix.tv.core.model

object ContentRatingHelper {
    fun ratingRank(officialRating: String?): Int {
        if (officialRating.isNullOrBlank()) return 2
        val norm = officialRating.trim().uppercase()
        return when {
            norm in setOf("G", "TV-Y", "TV-G", "TV-Y7", "TV-Y7-FV", "U", "AL") -> 1
            norm in setOf("PG", "TV-PG") -> 2
            norm in setOf("PG-13", "TV-14", "12", "12A", "13+", "14+") -> 3
            norm in setOf("R", "NC-17", "TV-MA", "18", "18+", "X", "NR", "UNRATED") -> 4
            else -> 2
        }
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
