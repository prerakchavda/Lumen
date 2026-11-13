package com.seerflix.tv.core.player

object YouTubeUrlParser {
    private val watchRegex = Regex("[?&]v=([A-Za-z0-9_-]{11})")
    private val shortRegex = Regex("youtu\\.be/([A-Za-z0-9_-]{11})")
    private val embedRegex = Regex("youtube\\.com/(?:embed|shorts)/([A-Za-z0-9_-]{11})")

    fun extractVideoKey(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return watchRegex.find(url)?.groupValues?.getOrNull(1)
            ?: shortRegex.find(url)?.groupValues?.getOrNull(1)
            ?: embedRegex.find(url)?.groupValues?.getOrNull(1)
    }
}
