package com.seerflix.tv.core.model

import kotlin.math.roundToInt

enum class MediaType {
    MOVIE,
    SERIES,
    EPISODE
}

enum class AvailabilityTier(val code: Int) {
    UNKNOWN(1),
    PENDING(2),
    PROCESSING(3),
    PARTIALLY_AVAILABLE(4),
    AVAILABLE(5),
    BLOCKLISTED(6),
    DELETED(7);

    val isInLibrary: Boolean
        get() = this == AVAILABLE || this == PARTIALLY_AVAILABLE

    val isRequestedOrDownloading: Boolean
        get() = this == PENDING || this == PROCESSING

    companion object {
        fun fromSeerrCode(code: Int?): AvailabilityTier =
            entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

data class DownloadProgressInfo(
    val is4k: Boolean,
    val status: String,
    val title: String?,
    val sizeBytes: Long,
    val sizeLeftBytes: Long,
    val percentage: Float,
    val etaText: String?,
    val estimatedCompletionIso: String?
) {
    val label: String
        get() {
            val tierTag = if (is4k) "4K" else "HD"
            val pct = percentage.roundToInt().coerceIn(0, 100)
            return buildString {
                if (status.equals("importing", ignoreCase = true)) {
                    append("Importing $tierTag")
                } else {
                    append("Downloading $tierTag")
                    if (sizeBytes > 0L) append(" • $pct%")
                }
                if (!etaText.isNullOrBlank()) {
                    append(" • $etaText")
                }
            }
        }
}

data class SeasonSummary(
    val seasonNumber: Int,
    val name: String,
    val episodeCount: Int,
    val overview: String? = null,
    val posterUrl: String? = null,
    val hdStatus: AvailabilityTier = AvailabilityTier.UNKNOWN,
    val uhdStatus: AvailabilityTier = AvailabilityTier.UNKNOWN,
    val jellyfinSeasonId: String? = null
) {
    val isPlayable: Boolean
        get() = jellyfinSeasonId != null || hdStatus.isInLibrary || uhdStatus.isInLibrary
}

data class EpisodeSummary(
    val id: String,
    val name: String,
    val overview: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val runtimeTicks: Long,
    val resumePositionTicks: Long,
    val stillUrl: String?,
    val isAvailableInJellyfin: Boolean,
    val isWatched: Boolean
) {
    val progressFraction: Float
        get() = if (runtimeTicks > 0L && resumePositionTicks > 0L) {
            (resumePositionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val runtimeMinutes: Int
        get() = (runtimeTicks / 600_000_000L).toInt()
}

data class CastMember(
    val name: String,
    val role: String,
    val imageUrl: String?,
    val personId: Int? = null
)

data class PersonSearchResult(
    val id: Int,
    val name: String,
    val department: String? = null,
    val imageUrl: String? = null
)

data class RelatedVideoItem(
    val title: String,
    val type: String,
    val youtubeKey: String? = null,
    val localTrailerItemId: String? = null,
    val thumbnailUrl: String? = null
)

data class TrickplayCrop(
    val tileIndex: Int,
    val tileUrl: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

data class TrickplayInfo(
    val width: Int,
    val height: Int,
    val tileWidth: Int,
    val tileHeight: Int,
    val thumbnailCount: Int,
    val intervalMs: Int,
    val baseUrlTemplate: String
) {
    fun tileUrl(tileIndex: Int): String =
        baseUrlTemplate.replace("{tileIndex}", tileIndex.toString())

    fun cropForPosition(positionMs: Long): TrickplayCrop? {
        if (intervalMs <= 0 || tileWidth <= 0 || tileHeight <= 0 || width <= 0 || height <= 0) return null
        val maxIndex = (thumbnailCount - 1).coerceAtLeast(0)
        val thumbIndex = (positionMs.coerceAtLeast(0L) / intervalMs.toLong()).toInt().coerceIn(0, maxIndex)
        val thumbsPerTile = tileWidth * tileHeight
        val tileIndex = thumbIndex / thumbsPerTile
        val indexInTile = thumbIndex % thumbsPerTile
        val col = indexInTile % tileWidth
        val row = indexInTile / tileWidth
        return TrickplayCrop(
            tileIndex = tileIndex,
            tileUrl = tileUrl(tileIndex),
            x = col * width,
            y = row * height,
            width = width,
            height = height
        )
    }
}

enum class AvailabilityFilter(val label: String) {
    ALL("All"),
    WATCH_NOW("Watch Now"),
    REQUESTABLE("Requestable");

    fun matches(item: UnifiedMediaItem): Boolean = when (this) {
        ALL -> true
        WATCH_NOW -> item.isPlayable
        REQUESTABLE -> !item.isPlayable && item.canRequestHd
    }
}

data class GenreFilterOption(
    val id: String,
    val label: String,
    val movieGenreId: Int?,
    val tvGenreId: Int?
) {
    companion object {
        val ALL_GENRES = listOf(
            GenreFilterOption("all", "All Genres", null, null),
            GenreFilterOption("action", "Action", 28, 10759),
            GenreFilterOption("comedy", "Comedy", 35, 35),
            GenreFilterOption("scifi", "Sci-Fi", 878, 10765),
            GenreFilterOption("horror", "Horror & Mystery", 27, 9648),
            GenreFilterOption("drama", "Drama", 18, 18),
            GenreFilterOption("crime", "Crime", 80, 80),
            GenreFilterOption("thriller", "Thriller", 53, 9648),
            GenreFilterOption("documentary", "Documentary", 99, 99),
            GenreFilterOption("animation", "Animation", 16, 16)
        )
    }
}

enum class QualityPreset(val label: String, val maxBitrateBps: Long?) {
    AUTO("Auto (Direct Play)", null),
    UHD_4K("4K (40 Mbps)", 40_000_000L),
    HD_1080P("1080p (15 Mbps)", 15_000_000L),
    HD_720P("720p (6 Mbps)", 6_000_000L)
}

data class MediaRequestSummary(
    val requestId: Int,
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val is4k: Boolean,
    val requestStatus: Int,
    val mediaStatus: AvailabilityTier,
    val requestedBy: String,
    val createdAt: String,
    val downloadProgress: DownloadProgressInfo?
) {
    val statusBadgeText: String
        get() = when {
            downloadProgress != null -> downloadProgress.label
            mediaStatus == AvailabilityTier.AVAILABLE -> if (is4k) "4K Available" else "Available"
            mediaStatus == AvailabilityTier.PARTIALLY_AVAILABLE -> "Partially Available"
            mediaStatus == AvailabilityTier.PROCESSING -> if (is4k) "Downloading 4K..." else "Downloading..."
            requestStatus == 1 -> "Pending Approval"
            mediaStatus == AvailabilityTier.PENDING -> "Requested"
            requestStatus == 3 -> "Declined"
            else -> "Requested"
        }
}

data class UnifiedMediaItem(
    val id: String,
    val tmdbId: Int? = null,
    val jellyfinId: String? = null,
    val imdbId: String? = null,
    val tvdbId: Int? = null,
    val mediaType: MediaType,
    val title: String,
    val overview: String = "",
    val tagline: String? = null,
    val releaseYear: String? = null,
    val runtimeMinutes: Int? = null,
    val maturityRating: String? = null,
    val voteAverage: Double? = null,
    val genres: List<String> = emptyList(),
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val clearLogoUrl: String? = null,
    val hdStatus: AvailabilityTier = AvailabilityTier.UNKNOWN,
    val uhdStatus: AvailabilityTier = AvailabilityTier.UNKNOWN,
    val hdDownload: DownloadProgressInfo? = null,
    val uhdDownload: DownloadProgressInfo? = null,
    val hdRequestId: Int? = null,
    val uhdRequestId: Int? = null,
    val resumePositionTicks: Long = 0L,
    val runtimeTicks: Long = 0L,
    val isWatched: Boolean = false,
    val isInMyList: Boolean = false,
    val isReminderSet: Boolean = false,
    val mediaBadges: List<String> = emptyList(),
    val episodeLabel: String? = null,
    val seriesName: String? = null,
    val youtubeTrailerKey: String? = null,
    val localTrailerItemId: String? = null,
    val relatedVideos: List<RelatedVideoItem> = emptyList(),
    val seasons: List<SeasonSummary> = emptyList(),
    val cast: List<CastMember> = emptyList()
) {
    val isPlayable: Boolean
        get() = jellyfinId != null

    val hasHd: Boolean
        get() = hdStatus.isInLibrary || jellyfinId != null

    val has4k: Boolean
        get() = uhdStatus.isInLibrary

    val canRequestHd: Boolean
        get() = tmdbId != null && hdStatus == AvailabilityTier.UNKNOWN && jellyfinId == null

    val canRequest4k: Boolean
        get() = tmdbId != null && uhdStatus == AvailabilityTier.UNKNOWN

    val activeDownload: DownloadProgressInfo?
        get() = uhdDownload ?: hdDownload

    val overallAvailability: AvailabilityTier
        get() = when {
            jellyfinId != null || hdStatus == AvailabilityTier.AVAILABLE || uhdStatus == AvailabilityTier.AVAILABLE ->
                AvailabilityTier.AVAILABLE
            hdStatus == AvailabilityTier.PARTIALLY_AVAILABLE || uhdStatus == AvailabilityTier.PARTIALLY_AVAILABLE ->
                AvailabilityTier.PARTIALLY_AVAILABLE
            hdDownload != null || uhdDownload != null ||
                hdStatus == AvailabilityTier.PROCESSING || uhdStatus == AvailabilityTier.PROCESSING ->
                AvailabilityTier.PROCESSING
            hdStatus == AvailabilityTier.PENDING || uhdStatus == AvailabilityTier.PENDING ->
                AvailabilityTier.PENDING
            else -> AvailabilityTier.UNKNOWN
        }

    val matchPercent: Int?
        get() = voteAverage?.takeIf { it > 0.0 }?.let { (it * 10.0).roundToInt().coerceIn(50, 99) }

    val watchProgressFraction: Float
        get() = if (runtimeTicks > 0L && resumePositionTicks > 0L) {
            (resumePositionTicks.toFloat() / runtimeTicks.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedRuntime: String?
        get() = runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
            val h = mins / 60
            val m = mins % 60
            if (h > 0) "${h}h ${m}m" else "${m}m"
        }
}

enum class RowStyle {
    CONTINUE_WATCHING,
    TOP_10,
    POSTER,
    WIDE
}

data class CatalogRow(
    val id: String,
    val title: String,
    val style: RowStyle,
    val items: List<UnifiedMediaItem>
)

data class TrackOption(
    val index: Int,
    val label: String,
    val language: String?,
    val codec: String?
)

data class PlaybackSessionData(
    val itemId: String,
    val title: String,
    val subtitle: String?,
    val streamUrl: String,
    val isHls: Boolean,
    val mediaSourceId: String,
    val playSessionId: String,
    val resumeTicks: Long,
    val runtimeTicks: Long,
    val introStartTicks: Long? = null,
    val introEndTicks: Long? = null,
    val recapStartTicks: Long? = null,
    val recapEndTicks: Long? = null,
    val creditsStartTicks: Long? = null,
    val nextEpisodeId: String? = null,
    val nextEpisodeTitle: String? = null,
    val trickplayInfo: TrickplayInfo? = null,
    val seriesId: String? = null,
    val seasonId: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val resolutionLabel: String? = null,
    val hdrFormat: String? = null,
    val isTrailer: Boolean = false,
    val trailerAudioUrl: String? = null,
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList()
)
