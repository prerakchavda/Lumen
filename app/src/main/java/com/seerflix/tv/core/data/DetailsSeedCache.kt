package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.UnifiedMediaItem

/**
 * In-memory handoff from a clicked card to the Details screen.
 *
 * The card already knows the title, artwork, year, rating and usually the overview, so Details can
 * paint those immediately instead of showing a spinner while `loadFullDetails` makes its network
 * round trips. Keyed exactly like the `details/{tmdbId}/{jellyfinId}/{mediaType}` route so the
 * ViewModel can find the seed from its nav arguments alone.
 *
 * Deliberately tiny (a handful of entries, no persistence): it only needs to survive the hop from
 * `navigate()` to `DetailsViewModel.init`, plus back-stack re-creation.
 */
object DetailsSeedCache {
    private const val MAX_ENTRIES = 8

    private val entries = object : LinkedHashMap<String, UnifiedMediaItem>(MAX_ENTRIES + 1, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, UnifiedMediaItem>?): Boolean =
            size > MAX_ENTRIES
    }

    fun keyFor(tmdbId: Int?, jellyfinId: String?, mediaType: MediaType): String {
        val tmdb = tmdbId?.takeIf { it > 0 } ?: 0
        val jf = jellyfinId?.takeIf { it.isNotBlank() && it != "none" } ?: "none"
        return "$tmdb/$jf/${mediaType.name}"
    }

    fun put(item: UnifiedMediaItem) {
        val key = keyFor(item.tmdbId, item.jellyfinId, item.mediaType)
        synchronized(entries) { entries[key] = item }
    }

    fun get(tmdbId: Int?, jellyfinId: String?, mediaType: MediaType): UnifiedMediaItem? {
        val key = keyFor(tmdbId, jellyfinId, mediaType)
        return synchronized(entries) { entries[key] }
    }
}
