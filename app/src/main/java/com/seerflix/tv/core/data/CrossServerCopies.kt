package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.MediaSourceOption
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.network.JellyfinServers
import com.seerflix.tv.core.network.ServerSession
import com.seerflix.tv.core.network.scopedTo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** One copy of a title: the server session it lives on and its (server scoped) item. */
class ItemCopy(val session: ServerSession, val item: JellyfinItemDto)

/**
 * The Versions list for a title: its own files ([ownSources], on [ownServerId]), then the copies on
 * other servers, each labelled with its server. Media source ids are server scoped like item ids,
 * so two servers' files can never be mixed up. [ownServerName] is set when the server isn't
 * obvious (there is another copy, or this one isn't on the home server).
 */
internal fun crossServerVersions(
    ownServerId: String,
    ownServerName: String?,
    ownSources: List<JellyfinMediaSourceDto>,
    others: List<ItemCopy>
): List<MediaSourceOption> {
    val own = ownSources.map { source ->
        JellyfinPlaybackManager.mapMediaSourceOption(source).let {
            it.copy(id = ServerScopedId.scope(ownServerId, it.id), serverName = ownServerName)
        }
    }
    val fromOthers = others.flatMap { copy ->
        copy.item.mediaSources.map { source ->
            JellyfinPlaybackManager.mapMediaSourceOption(source).let {
                it.copy(
                    id = ServerScopedId.scope(copy.session.id, it.id),
                    itemId = copy.item.id,
                    serverName = copy.session.server.displayName
                )
            }
        }
    }
    return own + fromOthers
}

/**
 * Finds the *other* copies of an item on the servers the active profile is signed in to: the same
 * movie or series (linked by the merged library index), or the same episode of a linked series
 * (matched by season and episode number).
 *
 * Used where it matters which copy is acted on: playback (play the home copy first, resume from
 * the most recent progress, offer the other copies as versions) and watched / favourite changes
 * (kept in step on every copy). Lookups are bounded by [timeoutMs] per server and never throw for
 * a server-side problem; a server that doesn't answer is simply left out.
 */
@Singleton
class CrossServerCopies @Inject constructor(
    private val servers: JellyfinServers
) {
    /** True when [item] may exist on another server, i.e. a lookup is worth making. */
    fun mayHaveCopies(item: JellyfinItemDto): Boolean {
        if (!servers.hasExtraServers) return false
        val links = servers.links
        return when {
            item.type.equals("Episode", ignoreCase = true) ->
                item.seriesId?.let { links.hasCopies(it) } == true &&
                    item.parentIndexNumber != null && item.indexNumber != null
            else -> links.hasCopies(item.id)
        }
    }

    /**
     * The copies of [item] on *other* servers, in canonical order (home server first). Each comes
     * with user data; [itemFields] are the extra `Fields` to fetch (e.g. `MediaSources`).
     */
    suspend fun others(
        item: JellyfinItemDto,
        homeUserId: String,
        itemFields: String = "",
        timeoutMs: Long = LOOKUP_TIMEOUT_MS
    ): List<ItemCopy> {
        if (!mayHaveCopies(item)) return emptyList()
        val links = servers.links
        val isEpisode = item.type.equals("Episode", ignoreCase = true)
        val linkedIds = (if (isEpisode) links.copiesOf(item.seriesId!!) else links.copiesOf(item.id))
            .filter { ServerScopedId.serverOf(it) != ServerScopedId.serverOf(item.id) }
        if (linkedIds.isEmpty()) return emptyList()

        return coroutineScope {
            linkedIds.map { linkedId ->
                async {
                    val session = servers.sessionForItem(linkedId, homeUserId) ?: return@async null
                    if (!servers.isAvailable(session.id)) return@async null
                    val dto = onServer(session, timeoutMs) { s ->
                        if (isEpisode) findEpisode(s, s.raw(linkedId), item, itemFields)
                        else s.api.getItemDetails(s.userId, s.raw(linkedId), fields = itemFields)
                    } ?: return@async null
                    ItemCopy(session, dto.scopedTo(session.id))
                }
            }.awaitAll().filterNotNull()
        }
    }

    /** [others] for an item known only by id (one extra lookup of the item itself). */
    suspend fun othersOf(
        scopedId: String,
        homeUserId: String,
        timeoutMs: Long = LOOKUP_TIMEOUT_MS
    ): List<ItemCopy> {
        if (!servers.hasExtraServers) return emptyList()
        // Movies and series are linked by id; only an episode needs its numbers looked up first.
        if (servers.links.hasCopies(scopedId)) {
            return others(JellyfinItemDto(id = scopedId, type = "Movie"), homeUserId, timeoutMs = timeoutMs)
        }
        val session = servers.sessionForItem(scopedId, homeUserId) ?: return emptyList()
        val item = onServer(session, timeoutMs) { s ->
            s.api.getItemDetails(s.userId, s.raw(scopedId), fields = "")
        }?.scopedTo(session.id) ?: return emptyList()
        return others(item, homeUserId, timeoutMs = timeoutMs)
    }

    private suspend fun findEpisode(
        session: ServerSession,
        rawSeriesId: String,
        episode: JellyfinItemDto,
        itemFields: String
    ): JellyfinItemDto {
        val match = session.api.getEpisodesBySeasonNumber(
            seriesId = rawSeriesId,
            userId = session.userId,
            season = episode.parentIndexNumber,
            fields = null,
            enableImages = false
        ).items.firstOrNull {
            it.parentIndexNumber == episode.parentIndexNumber && it.indexNumber == episode.indexNumber
        } ?: throw NoSuchElementException("Episode not on ${session.id}")
        return if (itemFields.isBlank()) match
        else session.api.getItemDetails(session.userId, match.id, fields = itemFields)
    }

    private suspend fun <T : Any> onServer(
        session: ServerSession,
        timeoutMs: Long,
        block: suspend (ServerSession) -> T
    ): T? {
        if (!session.isHome) return servers.callExtra(session, timeoutMs, block)
        return try {
            withTimeoutOrNull(timeoutMs) { block(session) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        /** Playback and user-data changes wait this long, at most, for another server's copy. */
        const val LOOKUP_TIMEOUT_MS = 3_000L
    }
}
