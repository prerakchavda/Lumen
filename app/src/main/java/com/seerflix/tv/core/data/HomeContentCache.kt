package com.seerflix.tv.core.data

import android.content.Context
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last successfully loaded Home rows per (profile, tab, genre, availability filter), persisted so a
 * cold start / tab switch can paint real content instantly and refresh in the background.
 *
 * Stored as a compact card-level DTO (no cast/seasons/versions/extras/related videos: Home cards and
 * the hero never read them, and the hero re-enriches on focus when it needs more). Everything that
 * changes which rows/items a profile may see (Jellyfin user, rating limit, hidden/ordered/pinned
 * rows, servers, language) is folded into the key, so a changed profile simply misses the cache.
 */
@Singleton
class HomeContentCache internal constructor(
    private val dirProvider: () -> File
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ File(context.cacheDir, DIR_NAME) })

    /** Opaque cache key; build with [keyFor]. */
    class Key internal constructor(internal val raw: String) {
        internal val fileName: String = "home_" + sha1(raw) + ".json"
        override fun equals(other: Any?) = other is Key && other.raw == raw
        override fun hashCode() = raw.hashCode()
    }

    private class MemoryEntry(val base: String, val rows: List<CatalogRow>)

    private val memory = object : LinkedHashMap<String, MemoryEntry>(8, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MemoryEntry>?) = size > MEMORY_ENTRIES
    }

    /**
     * Cached rows for [key], with Jellyfin image URLs rebased onto [currentJellyfinBase] if the
     * server was reached through a different (LAN/remote) address when they were saved.
     */
    @OptIn(ExperimentalSerializationApi::class)
    fun read(key: Key, currentJellyfinBase: String): List<CatalogRow>? {
        val base = normalizeBase(currentJellyfinBase)
        synchronized(memory) { memory[key.raw] }?.let { return rebase(it.rows, it.base, base) }
        val file = File(dirProvider(), key.fileName)
        if (!file.isFile) return null
        return try {
            val parsed = file.inputStream().buffered().use { json.decodeFromStream<HomeCacheFile>(it) }
            if (parsed.version != VERSION || parsed.key != key.raw) return null
            val rows = parsed.rows.mapNotNull { it.toRow() }
            if (rows.isEmpty()) return null
            synchronized(memory) { memory[key.raw] = MemoryEntry(parsed.jellyfinBase, rows) }
            rebase(rows, parsed.jellyfinBase, base)
        } catch (e: Exception) {
            // Corrupt or from an older schema: forget it, the network load repopulates it.
            runCatching { file.delete() }
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun write(key: Key, rows: List<CatalogRow>, jellyfinBase: String) {
        if (rows.isEmpty()) return
        val base = normalizeBase(jellyfinBase)
        val trimmed = rows.take(MAX_ROWS).map { it.copy(items = it.items.take(MAX_ITEMS_PER_ROW)) }
        synchronized(memory) { memory[key.raw] = MemoryEntry(base, trimmed) }
        synchronized(this) {
            try {
                val dir = dirProvider().apply { mkdirs() }
                val target = File(dir, key.fileName)
                val tmp = File(dir, key.fileName + ".tmp")
                tmp.outputStream().buffered().use { out ->
                    json.encodeToStream(
                        HomeCacheFile(
                            version = VERSION,
                            key = key.raw,
                            jellyfinBase = base,
                            savedAtMs = System.currentTimeMillis(),
                            rows = trimmed.map { it.toCached() }
                        ),
                        out
                    )
                }
                if (!tmp.renameTo(target)) {
                    target.delete()
                    tmp.renameTo(target)
                }
                prune(dir)
            } catch (_: Exception) {
                // Best effort only.
            }
        }
    }

    private fun prune(dir: File) {
        val files = dir.listFiles { f -> f.name.startsWith("home_") && f.name.endsWith(".json") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach { it.delete() }
    }

    companion object {
        private const val DIR_NAME = "home_cache"
        internal const val VERSION = 2
        internal const val MAX_ROWS = 30
        internal const val MAX_ITEMS_PER_ROW = 40
        private const val MAX_FILES = 40
        private const val MEMORY_ENTRIES = 6

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }

        /**
         * Key for one Home view. Anything that changes which rows/items this profile may see is
         * part of it, so e.g. tightening a Kids profile's rating limit can never paint cached rows
         * that were filtered under the old limit.
         */
        fun keyFor(
            profile: UserProfile,
            tab: String,
            genreId: String,
            availability: String,
            seerrConfigured: Boolean,
            jellyfinServer: String,
            seerrServer: String,
            metadataLanguage: String
        ): Key = Key(
            listOf(
                "p=${profile.id}",
                "u=${profile.jellyfinUserId}",
                "t=$tab",
                "g=$genreId",
                "a=$availability",
                "r=${profile.effectiveRatingLimit.name}",
                "k=${profile.kidsMode}",
                "h=${profile.hiddenHomeRowIds.joinToString(",")}",
                "o=${profile.homeRowOrder.joinToString(",")}",
                "l=${profile.pinnedLibraryIds.joinToString(",")}",
                "s=$seerrConfigured",
                "j=${normalizeBase(jellyfinServer)}",
                "e=${normalizeBase(seerrServer)}",
                "m=$metadataLanguage"
            ).joinToString("|")
        )

        private fun normalizeBase(url: String) = url.trim().trimEnd('/')

        private fun sha1(text: String): String =
            MessageDigest.getInstance("SHA-1").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

        private fun rebase(rows: List<CatalogRow>, savedBase: String, currentBase: String): List<CatalogRow> {
            if (savedBase.isBlank() || currentBase.isBlank() || savedBase == currentBase) return rows
            fun String?.re(): String? =
                if (this != null && startsWith("$savedBase/")) currentBase + substring(savedBase.length) else this
            return rows.map { row ->
                row.copy(items = row.items.map {
                    it.copy(posterUrl = it.posterUrl.re(), backdropUrl = it.backdropUrl.re(), clearLogoUrl = it.clearLogoUrl.re())
                })
            }
        }
    }
}

@Serializable
internal data class HomeCacheFile(
    @SerialName("v") val version: Int,
    @SerialName("k") val key: String,
    @SerialName("b") val jellyfinBase: String = "",
    @SerialName("ts") val savedAtMs: Long = 0L,
    @SerialName("rows") val rows: List<CachedRow> = emptyList()
)

@Serializable
internal data class CachedRow(
    @SerialName("id") val id: String,
    @SerialName("t") val title: String,
    @SerialName("s") val style: String,
    @SerialName("i") val items: List<CachedItem> = emptyList()
) {
    /** null when the row can't be restored (e.g. a RowStyle that no longer exists). */
    fun toRow(): CatalogRow? {
        val rowStyle = RowStyle.entries.firstOrNull { it.name == style } ?: return null
        val restored = items.mapNotNull { it.toItem() }
        if (restored.isEmpty()) return null
        return CatalogRow(id = id, title = title, style = rowStyle, items = restored)
    }
}

@Serializable
internal data class CachedDownload(
    @SerialName("k") val is4k: Boolean,
    @SerialName("s") val status: String,
    @SerialName("t") val title: String? = null,
    @SerialName("z") val sizeBytes: Long = 0L,
    @SerialName("l") val sizeLeftBytes: Long = 0L,
    @SerialName("p") val percentage: Float = 0f,
    @SerialName("e") val etaText: String? = null,
    @SerialName("c") val estimatedCompletionIso: String? = null
)

@Serializable
internal data class CachedItem(
    @SerialName("id") val id: String,
    @SerialName("mt") val mediaType: String,
    @SerialName("ti") val title: String,
    @SerialName("tm") val tmdbId: Int? = null,
    @SerialName("jf") val jellyfinId: String? = null,
    @SerialName("im") val imdbId: String? = null,
    @SerialName("tv") val tvdbId: Int? = null,
    @SerialName("ov") val overview: String = "",
    @SerialName("tg") val tagline: String? = null,
    @SerialName("ry") val releaseYear: String? = null,
    @SerialName("rm") val runtimeMinutes: Int? = null,
    @SerialName("mr") val maturityRating: String? = null,
    @SerialName("va") val voteAverage: Double? = null,
    @SerialName("ge") val genres: List<String> = emptyList(),
    @SerialName("po") val posterUrl: String? = null,
    @SerialName("bd") val backdropUrl: String? = null,
    @SerialName("lo") val clearLogoUrl: String? = null,
    @SerialName("hs") val hdStatus: String? = null,
    @SerialName("us") val uhdStatus: String? = null,
    @SerialName("hd") val hdDownload: CachedDownload? = null,
    @SerialName("ud") val uhdDownload: CachedDownload? = null,
    @SerialName("hr") val hdRequestId: Int? = null,
    @SerialName("ur") val uhdRequestId: Int? = null,
    @SerialName("rp") val resumePositionTicks: Long = 0L,
    @SerialName("rt") val runtimeTicks: Long = 0L,
    @SerialName("w") val isWatched: Boolean = false,
    @SerialName("ml") val isInMyList: Boolean = false,
    @SerialName("rs") val isReminderSet: Boolean = false,
    @SerialName("mb") val mediaBadges: List<String> = emptyList(),
    @SerialName("el") val episodeLabel: String? = null,
    @SerialName("sn") val seriesName: String? = null,
    @SerialName("rd") val releaseDate: String? = null,
    @SerialName("na") val nextAirDate: String? = null,
    @SerialName("ub") val upcomingBadgeLabel: String? = null,
    @SerialName("t10") val isTop10: Boolean = false,
    @SerialName("nb") val netflixBadgeLabel: String? = null,
    @SerialName("ci") val collectionId: Int? = null,
    @SerialName("cn") val collectionName: String? = null,
    @SerialName("bx") val boxSetId: String? = null,
    @SerialName("yt") val youtubeTrailerKey: String? = null,
    @SerialName("lt") val localTrailerItemId: String? = null,
    @SerialName("ad") val isAdult: Boolean = false
) {
    fun toItem(): UnifiedMediaItem? {
        val type = MediaType.entries.firstOrNull { it.name == mediaType } ?: return null
        return UnifiedMediaItem(
            id = id,
            tmdbId = tmdbId,
            jellyfinId = jellyfinId,
            imdbId = imdbId,
            tvdbId = tvdbId,
            mediaType = type,
            title = title,
            overview = overview,
            tagline = tagline,
            releaseYear = releaseYear,
            runtimeMinutes = runtimeMinutes,
            maturityRating = maturityRating,
            voteAverage = voteAverage,
            genres = genres,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            clearLogoUrl = clearLogoUrl,
            hdStatus = tier(hdStatus),
            uhdStatus = tier(uhdStatus),
            hdDownload = hdDownload?.toModel(),
            uhdDownload = uhdDownload?.toModel(),
            hdRequestId = hdRequestId,
            uhdRequestId = uhdRequestId,
            resumePositionTicks = resumePositionTicks,
            runtimeTicks = runtimeTicks,
            isWatched = isWatched,
            isInMyList = isInMyList,
            isReminderSet = isReminderSet,
            mediaBadges = mediaBadges,
            episodeLabel = episodeLabel,
            seriesName = seriesName,
            releaseDate = releaseDate,
            nextAirDate = nextAirDate,
            upcomingBadgeLabel = upcomingBadgeLabel,
            isTop10 = isTop10,
            netflixBadgeLabel = netflixBadgeLabel,
            collectionId = collectionId,
            collectionName = collectionName,
            boxSetId = boxSetId,
            youtubeTrailerKey = youtubeTrailerKey,
            localTrailerItemId = localTrailerItemId,
            isAdult = isAdult
        )
    }

    private fun tier(name: String?): AvailabilityTier =
        AvailabilityTier.entries.firstOrNull { it.name == name } ?: AvailabilityTier.UNKNOWN
}

private fun CachedDownload.toModel() = DownloadProgressInfo(
    is4k = is4k,
    status = status,
    title = title,
    sizeBytes = sizeBytes,
    sizeLeftBytes = sizeLeftBytes,
    percentage = percentage,
    etaText = etaText,
    estimatedCompletionIso = estimatedCompletionIso
)

private fun DownloadProgressInfo.toCached() = CachedDownload(
    is4k = is4k,
    status = status,
    title = title,
    sizeBytes = sizeBytes,
    sizeLeftBytes = sizeLeftBytes,
    percentage = percentage,
    etaText = etaText,
    estimatedCompletionIso = estimatedCompletionIso
)

private fun CatalogRow.toCached() = CachedRow(
    id = id,
    title = title,
    style = style.name,
    items = items.map { it.toCached() }
)

private fun UnifiedMediaItem.toCached() = CachedItem(
    id = id,
    mediaType = mediaType.name,
    title = title,
    tmdbId = tmdbId,
    jellyfinId = jellyfinId,
    imdbId = imdbId,
    tvdbId = tvdbId,
    overview = overview,
    tagline = tagline,
    releaseYear = releaseYear,
    runtimeMinutes = runtimeMinutes,
    maturityRating = maturityRating,
    voteAverage = voteAverage,
    genres = genres,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    clearLogoUrl = clearLogoUrl,
    hdStatus = hdStatus.takeIf { it != AvailabilityTier.UNKNOWN }?.name,
    uhdStatus = uhdStatus.takeIf { it != AvailabilityTier.UNKNOWN }?.name,
    hdDownload = hdDownload?.toCached(),
    uhdDownload = uhdDownload?.toCached(),
    hdRequestId = hdRequestId,
    uhdRequestId = uhdRequestId,
    resumePositionTicks = resumePositionTicks,
    runtimeTicks = runtimeTicks,
    isWatched = isWatched,
    isInMyList = isInMyList,
    isReminderSet = isReminderSet,
    mediaBadges = mediaBadges,
    episodeLabel = episodeLabel,
    seriesName = seriesName,
    releaseDate = releaseDate,
    nextAirDate = nextAirDate,
    upcomingBadgeLabel = upcomingBadgeLabel,
    isTop10 = isTop10,
    netflixBadgeLabel = netflixBadgeLabel,
    collectionId = collectionId,
    collectionName = collectionName,
    boxSetId = boxSetId,
    youtubeTrailerKey = youtubeTrailerKey,
    localTrailerItemId = localTrailerItemId,
    isAdult = isAdult
)
