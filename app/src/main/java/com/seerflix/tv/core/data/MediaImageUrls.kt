package com.seerflix.tv.core.data

object MediaImageUrls {
    fun tmdbPoster(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            val clean = if (it.startsWith("/")) it else "/$it"
            "https://image.tmdb.org/t/p/w342$clean"
        }

    fun tmdbBackdrop(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            val clean = if (it.startsWith("/")) it else "/$it"
            "https://image.tmdb.org/t/p/w780$clean"
        }

    fun tmdbProfile(path: String?): String? =
        path?.takeIf { it.isNotBlank() }?.let {
            val clean = if (it.startsWith("/")) it else "/$it"
            "https://image.tmdb.org/t/p/w185$clean"
        }

    fun jellyfinPrimary(baseUrl: String, itemId: String, maxWidth: Int = 340): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && itemId.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=82"
        }

    fun jellyfinBackdrop(baseUrl: String, itemId: String, maxWidth: Int = 960): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && itemId.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Backdrop/0?maxWidth=$maxWidth&quality=80"
        }

    fun jellyfinLogo(baseUrl: String, itemId: String, maxWidth: Int = 400): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && itemId.isNotBlank() }?.let {
            "$it/Items/$itemId/Images/Logo?maxWidth=$maxWidth&quality=85"
        }

    fun jellyfinUserAvatar(baseUrl: String, userId: String): String? =
        baseUrl.trim().trimEnd('/').takeIf { it.isNotBlank() && userId.isNotBlank() }?.let {
            "$it/Users/$userId/Images/Primary?maxWidth=200&quality=82"
        }

    // ---------------------------------------------------------------------------------------
    // Display-size rewriting.
    //
    // The repository builds one URL per artwork (e.g. a 960 px Jellyfin backdrop) that is shown
    // both as a ~450 px row card and as a ~1800 px hero. Downloading + decoding a 960 px JPEG for
    // every row card is the single biggest image cost on a quad-A55 TV stick, so the UI asks for a
    // server-side resized variant that matches the slot it is drawn in.
    // ---------------------------------------------------------------------------------------

    /** Jellyfin `maxWidth` buckets: few distinct sizes keep the HTTP / memory caches effective. */
    private val JELLYFIN_WIDTH_BUCKETS = intArrayOf(160, 240, 340, 480, 640, 960, 1280, 1920)
    private val JELLYFIN_MAX_WIDTH = Regex("([?&])maxWidth=\\d+")
    private val TMDB_SIZE = Regex("/t/p/(w\\d+|original)/")

    /**
     * Returns [url] rewritten to request roughly [targetWidthPx] wide artwork from the server, or
     * [url] unchanged when it is not a Jellyfin `/Images/` or TMDB URL (e.g. preview artwork in
     * screenshot tests). [maxWidthPx] caps the request (the hero does not need 4K backdrops).
     */
    fun forDisplayWidth(url: String?, targetWidthPx: Int, maxWidthPx: Int = 1280): String? {
        if (url.isNullOrBlank() || targetWidthPx <= 0) return url
        val target = targetWidthPx.coerceAtMost(maxWidthPx)
        return when {
            url.contains("image.tmdb.org/t/p/") -> rewriteTmdb(url, target)
            url.contains("/Images/") && JELLYFIN_MAX_WIDTH.containsMatchIn(url) -> {
                val bucket = JELLYFIN_WIDTH_BUCKETS.firstOrNull { it >= target }
                    ?: JELLYFIN_WIDTH_BUCKETS.last()
                JELLYFIN_MAX_WIDTH.replaceFirst(url, "$1maxWidth=$bucket")
            }
            else -> url
        }
    }

    /**
     * TMDB only serves a fixed size ladder per image type, so only the transitions that are valid
     * for that type are made: backdrops w780 <-> w1280, posters w342 -> w500. Anything else
     * (profiles, `original`, unknown sizes) is left untouched.
     */
    private fun rewriteTmdb(url: String, target: Int): String {
        val current = TMDB_SIZE.find(url)?.groupValues?.get(1) ?: return url
        val next = when (current) {
            "w780", "w1280" -> if (target > 800) "w1280" else "w780"
            "w342", "w500" -> if (target > 360) "w500" else "w342"
            else -> current
        }
        return if (next == current) url else url.replaceFirst("/t/p/$current/", "/t/p/$next/")
    }
}
