package com.seerflix.tv.core.data

import android.content.Context
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinUserDataDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One owned Movie/Series in the compact library index. Holds only what the index consumers in
 * [UnifiedCatalogRepository] read (TMDB matching, availability/rating/progress badges, genre rows,
 * Jellyfin-only library rows). Overview is deliberately NOT stored: it is the bulk of the payload
 * and is back-filled for the handful of items a row actually displays.
 */
@Serializable
internal data class LibraryIndexEntry(
    @SerialName("i") val id: String,
    @SerialName("t") val type: String,
    @SerialName("n") val name: String = "",
    @SerialName("tm") val tmdb: Int? = null,
    @SerialName("im") val imdb: String? = null,
    @SerialName("tv") val tvdb: String? = null,
    @SerialName("g") val genres: List<String> = emptyList(),
    @SerialName("r") val runTimeTicks: Long? = null,
    @SerialName("y") val productionYear: Int? = null,
    @SerialName("o") val officialRating: String? = null,
    @SerialName("c") val communityRating: Double? = null,
    @SerialName("pp") val playbackPositionTicks: Long = 0L,
    @SerialName("pl") val played: Boolean = false,
    @SerialName("f") val isFavorite: Boolean = false,
    @SerialName("b") val hasBackdrop: Boolean = false,
    /** When it was last played (epoch ms): decides whose progress wins for a title on several servers. */
    @SerialName("lp") val lastPlayedMs: Long? = null,
    /** When it was added to the server (epoch ms): "Added a While Ago, Never Started". */
    @SerialName("dc") val dateCreatedMs: Long? = null,
    /** Thumbs up / down set on the server (UserData.Likes). */
    @SerialName("lk") val likes: Boolean? = null
)

internal fun JellyfinItemDto.toIndexEntry(): LibraryIndexEntry = LibraryIndexEntry(
    id = id,
    type = type,
    name = name,
    tmdb = providerIds["Tmdb"]?.toIntOrNull() ?: providerIds["tmdb"]?.toIntOrNull(),
    imdb = providerIds["Imdb"],
    tvdb = providerIds["Tvdb"],
    genres = genres,
    runTimeTicks = runTimeTicks,
    productionYear = productionYear,
    officialRating = officialRating,
    communityRating = communityRating,
    playbackPositionTicks = userData?.playbackPositionTicks ?: 0L,
    played = userData?.played == true,
    isFavorite = userData?.isFavorite == true,
    hasBackdrop = backdropImageTags.isNotEmpty(),
    lastPlayedMs = CrossServerMerge.parseDateMs(userData?.lastPlayedDate),
    dateCreatedMs = CrossServerMerge.parseDateMs(dateCreated),
    likes = userData?.likes
)

internal fun LibraryIndexEntry.toItemDto(): JellyfinItemDto = JellyfinItemDto(
    id = id,
    name = name,
    type = type,
    productionYear = productionYear,
    officialRating = officialRating,
    communityRating = communityRating,
    runTimeTicks = runTimeTicks,
    genres = genres,
    dateCreated = dateCreatedMs?.let { java.time.Instant.ofEpochMilli(it).toString() },
    providerIds = buildMap(3) {
        tmdb?.let { put("Tmdb", it.toString()) }
        imdb?.let { put("Imdb", it) }
        tvdb?.let { put("Tvdb", it) }
    },
    // Only emptiness is ever checked (backdrop vs. primary-image fallback).
    backdropImageTags = if (hasBackdrop) BACKDROP_PRESENT else emptyList(),
    userData = JellyfinUserDataDto(
        playbackPositionTicks = playbackPositionTicks,
        played = played,
        isFavorite = isFavorite,
        lastPlayedDate = lastPlayedMs?.let { java.time.Instant.ofEpochMilli(it).toString() },
        likes = likes
    )
)

private val BACKDROP_PRESENT = listOf("1")

@Serializable
internal data class LibraryIndexFile(
    @SerialName("v") val version: Int,
    @SerialName("u") val userId: String,
    @SerialName("s") val server: String = "",
    @SerialName("ts") val fetchedAtMs: Long,
    @SerialName("items") val items: List<LibraryIndexEntry> = emptyList()
)

/**
 * Disk copy of the Jellyfin library index, one small JSON file per Jellyfin user, so a cold start
 * can serve the last index immediately and revalidate it in the background.
 */
@Singleton
class LibraryIndexStore internal constructor(
    private val dirProvider: () -> File
) {
    @Inject
    constructor(@ApplicationContext context: Context) : this({ File(context.cacheDir, DIR_NAME) })

    internal data class Snapshot(val fetchedAtMs: Long, val items: List<LibraryIndexEntry>)

    @OptIn(ExperimentalSerializationApi::class)
    internal fun read(userId: String, server: String): Snapshot? {
        val file = fileFor(userId)
        if (!file.isFile) return null
        return try {
            val parsed = file.inputStream().buffered().use { json.decodeFromStream<LibraryIndexFile>(it) }
            if (parsed.version != VERSION || parsed.userId != userId || parsed.server != server) null
            else Snapshot(parsed.fetchedAtMs, parsed.items)
        } catch (e: Exception) {
            // Corrupt / schema-changed file: drop it and fall back to a network fetch.
            runCatching { file.delete() }
            null
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Synchronized
    internal fun write(userId: String, server: String, fetchedAtMs: Long, items: List<LibraryIndexEntry>) {
        try {
            val dir = dirProvider().apply { mkdirs() }
            val target = File(dir, fileNameFor(userId))
            val tmp = File(dir, target.name + ".tmp")
            tmp.outputStream().buffered().use {
                json.encodeToStream(LibraryIndexFile(VERSION, userId, server, fetchedAtMs, items), it)
            }
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        } catch (_: Exception) {
            // Best effort: the in-memory index is still served.
        }
    }

    private fun fileFor(userId: String) = File(dirProvider(), fileNameFor(userId))

    private fun fileNameFor(userId: String) =
        "index_" + userId.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".json"

    companion object {
        private const val DIR_NAME = "library_index"
        internal const val VERSION = 2

        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = false
        }
    }
}
