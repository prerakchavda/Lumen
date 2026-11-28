package com.seerflix.tv.core.model

import androidx.compose.runtime.Immutable
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

@Immutable
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
                    if (sizeBytes > 0L) append(" · $pct%")
                }
                if (!etaText.isNullOrBlank()) {
                    append(" · $etaText")
                }
            }
        }
}

@Immutable
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

@Immutable
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

@Immutable
data class CastMember(
    val name: String,
    val role: String,
    val imageUrl: String?,
    val personId: Int? = null,
    val jellyfinPersonId: String? = null
)

@Immutable
data class PersonSearchResult(
    val id: Int,
    val name: String,
    val department: String? = null,
    val imageUrl: String? = null,
    val jellyfinPersonId: String? = null
)

@Immutable
data class RelatedVideoItem(
    val title: String,
    val type: String,
    val youtubeKey: String? = null,
    val localTrailerItemId: String? = null,
    val thumbnailUrl: String? = null
)

@Immutable
data class TrickplayCrop(
    val tileIndex: Int,
    val tileUrl: String,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int
)

@Immutable
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

@Immutable
data class GenreFilterOption(
    val id: String,
    val label: String,
    val movieGenreId: Int?,
    val tvGenreId: Int?
) {
    companion object {
        val ALL_GENRES = listOf(
            GenreFilterOption("all", "All Genres", null, null),
            GenreFilterOption("action", "Action & Adventure", 28, 10759),
            GenreFilterOption("comedy", "Comedies", 35, 35),
            GenreFilterOption("scifi", "Sci-Fi & Fantasy", 878, 10765),
            GenreFilterOption("horror", "Horror & Mystery", 27, 9648),
            GenreFilterOption("drama", "Dramas", 18, 18),
            GenreFilterOption("crime", "Crime", 80, 80),
            GenreFilterOption("thriller", "Thrillers", 53, 9648),
            GenreFilterOption("documentary", "Documentaries", 99, 99),
            GenreFilterOption("animation", "Animation", 16, 16),
            GenreFilterOption("family", "Family", 10751, 10751),
            GenreFilterOption("fantasy", "Fantasy", 14, 10765),
            GenreFilterOption("romance", "Romance", 10749, 10749),
            GenreFilterOption("reality", "Reality TV", null, 10764),
            GenreFilterOption("kids", "Kids", 10751, 10762),
            GenreFilterOption("history", "History & War", 36, 10768),
            GenreFilterOption("music", "Music", 10402, null)
        )

        fun forMediaType(mediaType: MediaType?): List<GenreFilterOption> = when (mediaType) {
            MediaType.MOVIE -> ALL_GENRES.filter { it.id == "all" || it.movieGenreId != null }
            MediaType.SERIES -> ALL_GENRES.filter { it.id == "all" || it.tvGenreId != null }
            else -> ALL_GENRES
        }
    }
}

enum class QualityPreset(val label: String, val maxBitrateBps: Long?) {
    AUTO("Auto (Direct Play)", null),
    UHD_4K("4K (40 Mbps)", 40_000_000L),
    HD_1080P("1080p (15 Mbps)", 15_000_000L),
    HD_1080P_8M("1080p (8 Mbps)", 8_000_000L),
    HD_720P("720p (4 Mbps)", 4_000_000L),
    SD_480P("480p (1.5 Mbps)", 1_500_000L)
}

enum class PlayMethod(val jellyfinValue: String, val displayLabel: String) {
    DIRECT_PLAY("DirectPlay", "Direct Play"),
    DIRECT_STREAM("DirectStream", "Direct Stream / Remux"),
    TRANSCODE("Transcode", "Transcode")
}

@Immutable
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
    val downloadProgress: DownloadProgressInfo?,
    /** The requester's Jellyfin user name (null for Seerr-local accounts). */
    val requestedByJellyfinUsername: String? = null
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

@Immutable
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
    val releaseDate: String? = null,
    val nextAirDate: String? = null,
    val upcomingBadgeLabel: String? = null,
    /** True when this title is in today's Top 10 Movies or TV Shows (renders Netflix's corner ribbon). */
    val isTop10: Boolean = false,
    /** Netflix-style bottom-center status pill on posters (e.g. "Recently Added", "New Season"). */
    val netflixBadgeLabel: String? = null,
    val collectionId: Int? = null,
    val collectionName: String? = null,
    val boxSetId: String? = null,
    val youtubeTrailerKey: String? = null,
    val localTrailerItemId: String? = null,
    val relatedVideos: List<RelatedVideoItem> = emptyList(),
    val seasons: List<SeasonSummary> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val mediaVersions: List<MediaSourceOption> = emptyList(),
    val extras: List<ExtrasItem> = emptyList(),
    val isAdult: Boolean = false,
    val isNextUp: Boolean = false,
    val isFolder: Boolean = false,
    val playlistItemId: String? = null,
    val canDelete: Boolean = false,
    val mediaFilePath: String? = null,
    val mediaContainer: String? = null,
    val mediaFileSizeBytes: Long? = null,
    val mediaBitrate: Long? = null,
    val mediaStreamSpecs: List<MediaStreamSpec> = emptyList(),
    val availableAudioTracks: List<TrackOption> = emptyList(),
    val availableSubtitleTracks: List<TrackOption> = emptyList()
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

@Immutable
data class CollectionSummary(
    val id: String,
    val tmdbCollectionId: Int? = null,
    val name: String,
    val overview: String = "",
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val ownedCount: Int = 0,
    val totalCount: Int? = null,
    val items: List<UnifiedMediaItem> = emptyList()
) {
    val completionLabel: String
        get() = if (totalCount != null && totalCount > 0) {
            "$ownedCount of $totalCount in Library"
        } else {
            "$ownedCount in Library"
        }
}

@Immutable
data class RemoteSubtitleOption(
    val id: String,
    val name: String,
    val language: String,
    val format: String? = null,
    val providerName: String? = null,
    val downloadCount: Int? = null,
    val communityRating: Float? = null,
    val isHashMatch: Boolean = false
)

enum class RowStyle {
    CONTINUE_WATCHING,
    TOP_10,
    POSTER,
    WIDE
}

@Immutable
data class CatalogRow(
    val id: String,
    val title: String,
    val style: RowStyle,
    val items: List<UnifiedMediaItem>
)

@Immutable
data class TrackOption(
    val index: Int,
    val label: String,
    val language: String?,
    val codec: String?,
    val isExternal: Boolean = false,
    val externalUrl: String? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false,
    val isHearingImpaired: Boolean = false,
    val channels: Int? = null,
    val bitRate: Long? = null,
    val sampleRate: Int? = null
)

@Immutable
data class ChapterMarker(
    val name: String,
    val startPositionMs: Long
)

@Immutable
data class MediaSourceOption(
    val id: String,
    val name: String,
    val container: String? = null,
    val bitrate: Long? = null,
    val resolutionLabel: String? = null,
    val videoCodec: String? = null,
    val audioSummary: String? = null,
    /**
     * Set when this version is a copy of the title on another server: the (server scoped) item to
     * play for it. Null = a file of the item being shown / played.
     */
    val itemId: String? = null,
    /** The server this version comes from; only set when that isn't obvious (several servers). */
    val serverName: String? = null
) {
    val displayLabel: String
        get() = listOfNotNull(
            serverName?.takeIf { it.isNotBlank() },
            name.takeIf { it.isNotBlank() },
            resolutionLabel,
            videoCodec,
            audioSummary,
            bitrate?.takeIf { it > 0L }?.let { "${it / 1_000_000L} Mbps" }
        ).distinct().joinToString(" • ").ifBlank { id }
}

@Immutable
data class ExternalSubtitleFile(
    val index: Int,
    val title: String,
    val language: String?,
    val codec: String?,
    val url: String,
    val isDefault: Boolean = false,
    val isForced: Boolean = false
)

@Immutable
data class ExtrasItem(
    val id: String,
    val name: String,
    val overview: String = "",
    val extraType: String? = null,
    val runtimeMinutes: Int? = null,
    val thumbnailUrl: String? = null
)

@Immutable
data class MediaStreamSpec(
    val type: String,
    val title: String,
    val codec: String? = null,
    val language: String? = null,
    val channels: Int? = null,
    val resolution: String? = null,
    val hdrFormat: String? = null,
    val bitrate: Long? = null,
    val bitDepth: Int? = null,
    val isDefault: Boolean = false,
    val isForced: Boolean = false
)

@Immutable
data class PlaylistSummary(
    val id: String,
    val name: String,
    val itemCount: Int = 0,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String = "",
    val items: List<UnifiedMediaItem> = emptyList()
)

@Immutable
data class JellyfinLibraryView(
    val id: String,
    val name: String,
    val collectionType: String? = null,
    val serverName: String? = null
) {
    val displayName: String
        get() = if (!serverName.isNullOrBlank()) "$name · $serverName" else name
}

@Immutable
data class LiveTvProgram(
    val id: String,
    val title: String,
    val overview: String? = null,
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val timeRangeLabel: String = "",
    val isSeries: Boolean = false,
    /** Set when this programme is scheduled to record. */
    val timerId: String? = null,
    /** Set when the whole series is scheduled to record. */
    val seriesTimerId: String? = null,
    val isMovie: Boolean = false,
    val isSports: Boolean = false,
    val isNews: Boolean = false,
    val isKids: Boolean = false
) {
    val isRecording: Boolean get() = !timerId.isNullOrBlank() || !seriesTimerId.isNullOrBlank()

    fun progressFraction(nowMs: Long = System.currentTimeMillis()): Float {
        if (endMs <= startMs) return 0f
        return ((nowMs - startMs).toFloat() / (endMs - startMs).toFloat()).coerceIn(0f, 1f)
    }
}

@Immutable
data class LiveTvChannel(
    val id: String,
    val number: String?,
    val name: String,
    val logoUrl: String?,
    val currentProgram: LiveTvProgram? = null,
    val nextProgram: LiveTvProgram? = null,
    val programs: List<LiveTvProgram> = emptyList(),
    val isFavorite: Boolean = false
)

/** A recording scheduled on the server's DVR (one programme, or a whole series rule). */
@Immutable
data class ScheduledRecording(
    val id: String,
    val title: String,
    val channelName: String?,
    val startMs: Long,
    val timeLabel: String,
    val isSeriesRule: Boolean
)

enum class LiveTvFilter {
    ALL, FAVORITES, MOVIES, SPORTS, NEWS, KIDS;

    fun matches(channel: LiveTvChannel): Boolean {
        val current = channel.currentProgram
        val allProgs = if (channel.programs.isNotEmpty()) channel.programs else listOfNotNull(current, channel.nextProgram)
        return when (this) {
            ALL -> true
            FAVORITES -> channel.isFavorite
            MOVIES -> current?.isMovie == true || allProgs.any { it.isMovie }
            SPORTS -> current?.isSports == true || allProgs.any { it.isSports }
            NEWS -> current?.isNews == true || allProgs.any { it.isNews }
            KIDS -> current?.isKids == true || allProgs.any { it.isKids }
        }
    }
}

/**
 * The user's DVR choices for one recording. `null` keeps the server's default
 * (Dashboard → Live TV), so pressing Record without touching anything behaves like Jellyfin web.
 */
@Immutable
data class DvrTimerOptions(
    val prePaddingSeconds: Int? = null,
    val postPaddingSeconds: Int? = null,
    val recordNewOnly: Boolean? = null,
    val recordAnyChannel: Boolean? = null
)

@Immutable
data class ScreensaverSlide(
    val title: String,
    val subtitle: String? = null,
    val backdropUrl: String
)

/** One page of a library grid. */
data class LibraryPage(
    val items: List<UnifiedMediaItem>,
    val totalCount: Int
)

@Immutable
data class SyncPlayGroup(
    val groupId: String,
    val groupName: String,
    val state: String,
    val participants: List<String> = emptyList(),
    val isPlaying: Boolean = false,
    val positionTicks: Long = 0L,
    val playingItemId: String? = null,
    val playingItemName: String? = null
)

@Immutable
data class ArrQualityProfile(
    val id: Int,
    val name: String
)

@Immutable
data class ArrServerOption(
    val id: Int,
    val name: String,
    val is4k: Boolean,
    val isDefault: Boolean,
    val profiles: List<ArrQualityProfile> = emptyList(),
    val rootFolders: List<String> = emptyList(),
    /** The quality profile / root folder the Seerr admin set as this server's default. */
    val defaultProfileId: Int? = null,
    val defaultRootFolder: String? = null
) {
    val initialProfileId: Int?
        get() = profiles.firstOrNull { it.id == defaultProfileId }?.id ?: profiles.firstOrNull()?.id
    val initialRootFolder: String?
        get() = rootFolders.firstOrNull { it == defaultRootFolder } ?: rootFolders.firstOrNull()
}

enum class SeerrIssueType(val code: Int, val label: String) {
    VIDEO(1, "Video Problem"),
    AUDIO(2, "Audio Sync / Missing"),
    SUBTITLE(3, "Subtitles"),
    OTHER(4, "Other")
}

@Immutable
@kotlinx.serialization.Serializable
data class ReadyRequestAlert(
    val requestId: Int,
    val tmdbId: Int,
    val jellyfinId: String? = null,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String? = null,
    val timestampMs: Long = 0L
)

@Immutable
data class AppUpdateInfo(
    val latestVersion: String,
    val releaseNotes: String,
    val downloadUrl: String?,
    val htmlUrl: String
)

@Immutable
data class DiscoveredServer(
    val id: String,
    val name: String,
    val address: String
)

@Immutable
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
    val subtitleTracks: List<TrackOption> = emptyList(),
    val chapters: List<ChapterMarker> = emptyList(),
    val mediaSources: List<MediaSourceOption> = emptyList(),
    val externalSubtitleFiles: List<ExternalSubtitleFile> = emptyList(),
    val directPlayUrl: String? = null,
    val isLiveTv: Boolean = false,
    val playMethod: PlayMethod = if (isHls) PlayMethod.TRANSCODE else PlayMethod.DIRECT_PLAY,
    val transcodeReasons: List<String> = emptyList(),
    val videoBitrate: Long? = null,
    val audioBitrate: Long? = null,
    val videoFps: Float? = null,
    val container: String? = null,
    val fileSizeBytes: Long? = null,
    val filePath: String? = null,
    val activeAudioStreamIndex: Int? = null,
    val burnedInSubtitleIndex: Int? = null,
    val isCinemaIntro: Boolean = false
)

