package com.seerflix.tv.core.player

import com.seerflix.tv.core.data.CrossServerCopies
import com.seerflix.tv.core.data.CrossServerMerge
import com.seerflix.tv.core.data.ItemCopy
import com.seerflix.tv.core.data.crossServerVersions
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
import com.seerflix.tv.core.model.AssSubtitleMode
import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.network.JellyfinServers
import com.seerflix.tv.core.network.ServerSession
import com.seerflix.tv.core.network.scopedTo
import com.seerflix.tv.core.network.unscoped
import com.seerflix.tv.core.model.ChapterMarker
import com.seerflix.tv.core.model.ExternalSubtitleFile
import com.seerflix.tv.core.model.MediaSourceOption
import com.seerflix.tv.core.model.PlayMethod
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.TrackOption
import com.seerflix.tv.core.model.TrickplayInfo
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinDeviceProfileDto
import com.seerflix.tv.core.network.JellyfinCodecProfileDto
import com.seerflix.tv.core.network.JellyfinProfileConditionDto
import com.seerflix.tv.core.network.JellyfinDirectPlayProfileDto
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinMediaSegmentDto
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinPlaybackInfoRequest
import com.seerflix.tv.core.network.JellyfinPlaybackInfoResponse
import com.seerflix.tv.core.network.JellyfinPlaybackProgressRequest
import com.seerflix.tv.core.network.JellyfinSubtitleProfileDto
import com.seerflix.tv.core.network.JellyfinTranscodingProfileDto
import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Like [runCatching] but rethrows [CancellationException] so cancelled coroutines stay cancelled
 * instead of being reported as a regular failure (e.g. "Playback initialization failed").
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}

/** Intro / recap / credits markers (Jellyfin media segments). Not needed for the first frame. */
data class PlaybackSegments(
    val introStartTicks: Long? = null,
    val introEndTicks: Long? = null,
    val recapStartTicks: Long? = null,
    val recapEndTicks: Long? = null,
    val creditsStartTicks: Long? = null
) {
    fun applyTo(session: PlaybackSessionData): PlaybackSessionData = session.copy(
        introStartTicks = introStartTicks,
        introEndTicks = introEndTicks,
        recapStartTicks = recapStartTicks,
        recapEndTicks = recapEndTicks,
        creditsStartTicks = creditsStartTicks
    )

    companion object {
        fun from(segments: List<JellyfinMediaSegmentDto>): PlaybackSegments {
            val intro = segments.firstOrNull { it.type.equals("Intro", ignoreCase = true) }
            val recap = segments.firstOrNull { it.type.equals("Recap", ignoreCase = true) }
            val credits = segments.firstOrNull {
                it.type.equals("Outro", ignoreCase = true) || it.type.equals("Credits", ignoreCase = true)
            }
            return PlaybackSegments(
                introStartTicks = intro?.startTicks,
                introEndTicks = intro?.endTicks,
                recapStartTicks = recap?.startTicks,
                recapEndTicks = recap?.endTicks,
                creditsStartTicks = credits?.startTicks
            )
        }

        fun of(session: PlaybackSessionData) = PlaybackSegments(
            introStartTicks = session.introStartTicks,
            introEndTicks = session.introEndTicks,
            recapStartTicks = session.recapStartTicks,
            recapEndTicks = session.recapEndTicks,
            creditsStartTicks = session.creditsStartTicks
        )
    }
}

/** The episode that follows the one being played ("Up Next"). Only needed near the end. */
data class NextEpisodeInfo(val id: String, val title: String) {
    companion object {
        fun of(item: JellyfinItemDto) = NextEpisodeInfo(
            id = item.id,
            title = "S${item.parentIndexNumber ?: 1}:E${item.indexNumber ?: 0} • ${item.name}"
        )
    }
}

/**
 * A playback session that is ready to start, plus the data that is fetched in the background so
 * it doesn't delay the first frame. A deferred completes with `null` when its lookup failed
 * (as opposed to "there is none", e.g. [PlaybackSegments] with no markers / a null next episode
 * inside a successful [NextEpisodeLookup]).
 */
class PreparedPlayback(
    val session: PlaybackSessionData,
    /** Frame rate of the primary video stream per Jellyfin's metadata, for early display-mode matching. */
    val videoFrameRate: Float?,
    val segments: Deferred<PlaybackSegments?>?,
    val nextEpisode: Deferred<NextEpisodeLookup?>?
)

/** Wrapper so a successful "no next episode" (null [next]) differs from a failed lookup. */
data class NextEpisodeLookup(val next: NextEpisodeInfo?)

/** Live player state included in Jellyfin `/Sessions/Playing*` progress reports. */
data class PlaybackReportState(
    val playMethod: String? = null,
    val audioStreamIndex: Int? = null,
    val subtitleStreamIndex: Int? = null,
    val isMuted: Boolean? = null,
    val volumeLevel: Int? = null,
    val playbackRate: Float? = null
)

@Singleton
class JellyfinPlaybackManager @Inject constructor(
    private val jellyfinApi: JellyfinApi,
    private val prefs: SessionPreferences,
    private val capabilityProvider: DeviceCapabilityProvider = DeviceCapabilityProvider.Permissive,
    /** The extra servers; null (unit tests) = the home server only. [jellyfinApi] is the home server. */
    private val servers: JellyfinServers? = null,
    private val copies: CrossServerCopies? = null
) {
    /** What is about to be played: the server it is on and the item with that server's raw ids. */
    private class Target(val session: ServerSession, val item: JellyfinItemDto)

    /** UI strings for the active profile's language (error texts and fallback track labels). */
    private val strings: UiStrings
        get() = stringsForLanguage(prefs.cachedUiLanguage)

    private fun homeSession(userId: String): ServerSession =
        ServerSession(prefs.serverById(ServerScopedId.HOME) ?: HOME_SERVER, jellyfinApi, userId)

    /** The session of the server that owns [scopedId]; null when the profile has no account there. */
    private fun sessionFor(scopedId: String, userId: String): ServerSession? =
        if (ServerScopedId.isHome(scopedId)) homeSession(userId) else servers?.sessionForItem(scopedId, userId)

    /** Requested quality limited by the server's own streaming cap (home or extra server). */
    private fun cappedBitrate(requested: Long?, session: ServerSession): Long? =
        listOfNotNull(requested, session.server.maxStreamingBitrateBps).minOrNull()

    /**
     * Process-lifetime scope for fire-and-forget reports (e.g. "playback stopped") that must
     * survive the PlayerScreen being disposed and its ViewModel being cleared.
     */
    private val reportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Resolves a playable session. Only the calls needed to start the stream are awaited; media
     * segments and the next episode are not fetched (use [preparePlaybackWithExtras] for those).
     */
    suspend fun preparePlayback(
        userId: String,
        itemId: String,
        maxBitrateBps: Long? = null,
        mediaSourceId: String? = null,
        audioPassthroughEnabled: Boolean = true,
        stereoDownmixEnabled: Boolean = false,
        assSubtitleMode: AssSubtitleMode = AssSubtitleMode.STYLED,
        burnInSubtitleIndex: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        softwareAudioDecodingEnabled: Boolean = prefs.cachedSoftwareAudioDecoding,
        forceHls: Boolean = false
    ): Result<PlaybackSessionData> = prepareInternal(
        extrasScope = null,
        userId = userId,
        itemId = itemId,
        maxBitrateBps = maxBitrateBps,
        mediaSourceId = mediaSourceId,
        audioPassthroughEnabled = audioPassthroughEnabled,
        stereoDownmixEnabled = stereoDownmixEnabled,
        assSubtitleMode = assSubtitleMode,
        burnInSubtitleIndex = burnInSubtitleIndex,
        audioStreamIndex = audioStreamIndex,
        subtitleStreamIndex = subtitleStreamIndex,
        softwareAudioDecodingEnabled = softwareAudioDecodingEnabled,
        forceHls = forceHls
    ).map { it.session }

    /**
     * Like [preparePlayback], but also starts fetching media segments (skip intro/recap, credits)
     * and the next episode in [extrasScope] as soon as the item is resolved. The session is
     * returned as soon as PlaybackInfo is known; the extras complete later and must be merged by
     * the caller. Cancelling [extrasScope] cancels them.
     */
    suspend fun preparePlaybackWithExtras(
        extrasScope: CoroutineScope,
        userId: String,
        itemId: String,
        maxBitrateBps: Long? = null,
        mediaSourceId: String? = null,
        audioPassthroughEnabled: Boolean = true,
        stereoDownmixEnabled: Boolean = false,
        assSubtitleMode: AssSubtitleMode = AssSubtitleMode.STYLED,
        burnInSubtitleIndex: Int? = null,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
        softwareAudioDecodingEnabled: Boolean = prefs.cachedSoftwareAudioDecoding,
        forceHls: Boolean = false
    ): Result<PreparedPlayback> = prepareInternal(
        extrasScope = extrasScope,
        userId = userId,
        itemId = itemId,
        maxBitrateBps = maxBitrateBps,
        mediaSourceId = mediaSourceId,
        audioPassthroughEnabled = audioPassthroughEnabled,
        stereoDownmixEnabled = stereoDownmixEnabled,
        assSubtitleMode = assSubtitleMode,
        burnInSubtitleIndex = burnInSubtitleIndex,
        audioStreamIndex = audioStreamIndex,
        subtitleStreamIndex = subtitleStreamIndex,
        softwareAudioDecodingEnabled = softwareAudioDecodingEnabled,
        forceHls = forceHls
    )

    private suspend fun prepareInternal(
        extrasScope: CoroutineScope?,
        userId: String,
        itemId: String,
        maxBitrateBps: Long?,
        mediaSourceId: String?,
        audioPassthroughEnabled: Boolean,
        stereoDownmixEnabled: Boolean,
        assSubtitleMode: AssSubtitleMode,
        burnInSubtitleIndex: Int?,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        softwareAudioDecodingEnabled: Boolean,
        forceHls: Boolean
    ): Result<PreparedPlayback> = runCatchingCancellable {
        coroutineScope {
            // Ids arrive server scoped; from here on each call goes to the owning server with the
            // raw id, and only the finished session carries scoped ids again.
            val requestedSession = sessionFor(itemId, userId)
                ?: error(strings.playerErrorSignInAgain)
            val requestedSourceId = mediaSourceId?.let { ServerScopedId.rawOf(it) }
            val capabilities = if (prefs.cachedMatchDeviceCapabilities) {
                capabilityProvider.current()
            } else {
                DeviceMediaCapabilities.PERMISSIVE
            }
            fun deviceProfileFor(session: ServerSession) = buildDeviceProfile(
                maxBitrateBps = cappedBitrate(maxBitrateBps, session),
                audioPassthroughEnabled = audioPassthroughEnabled,
                stereoDownmixEnabled = stereoDownmixEnabled,
                assSubtitleMode = assSubtitleMode,
                burnInSubtitleIndex = burnInSubtitleIndex,
                capabilities = capabilities,
                softwareAudioDecodingEnabled = softwareAudioDecodingEnabled
            )
            val initialEnableDirectPlay = if (forceHls || burnInSubtitleIndex != null) false else null
            fun playbackInfoAsync(
                session: ServerSession,
                id: String,
                sourceId: String?,
                enableDirectPlay: Boolean? = initialEnableDirectPlay
            ): Deferred<JellyfinPlaybackInfoResponse?> = async {
                runCatchingCancellable {
                    session.api.getPlaybackInfo(
                        itemId = id,
                        userId = session.userId,
                        body = JellyfinPlaybackInfoRequest(
                            userId = session.userId,
                            mediaSourceId = sourceId,
                            audioStreamIndex = audioStreamIndex,
                            subtitleStreamIndex = burnInSubtitleIndex ?: subtitleStreamIndex,
                            maxAudioChannels = if (stereoDownmixEnabled) 2 else null,
                            deviceProfile = deviceProfileFor(session),
                            enableDirectPlay = enableDirectPlay
                        )
                    )
                }.getOrNull()
            }

            // Almost every request is a direct movie/episode id, so PlaybackInfo (the slowest call)
            // is requested speculatively alongside the item details instead of after them. For a
            // Series/Season the speculative request is dropped once the episode is resolved.
            val requestedRawId = requestedSession.raw(itemId)
            val speculativePlayback = playbackInfoAsync(requestedSession, requestedRawId, requestedSourceId)
            val requested = requestedSession.api.getItemDetails(
                requestedSession.userId, requestedRawId, fields = PLAYBACK_ITEM_FIELDS
            )
            var target: Target
            var sourceId = requestedSourceId
            var playbackDeferred: Deferred<JellyfinPlaybackInfoResponse?>
            if (requested.type.equals("Series", ignoreCase = true) || requested.type.equals("Season", ignoreCase = true)) {
                speculativePlayback.cancel()
                val candidate = resolveEpisodeToPlay(userId, requestedSession, requested)
                // The episode id is known now: fetch its details and PlaybackInfo in parallel.
                val detailsDeferred = async {
                    runCatchingCancellable {
                        candidate.session.api.getItemDetails(
                            candidate.session.userId, candidate.item.id, fields = PLAYBACK_ITEM_FIELDS
                        )
                    }.getOrDefault(candidate.item)
                }
                playbackDeferred = playbackInfoAsync(candidate.session, candidate.item.id, requestedSourceId)
                target = Target(candidate.session, detailsDeferred.await())
            } else {
                target = Target(requestedSession, requested)
                playbackDeferred = speculativePlayback
            }

            // The same title on the other servers (empty unless it really is on several): the home
            // server's copy is played first, the rest become extra versions, and the most recent
            // progress of any copy is where playback resumes.
            var others = copies?.others(
                target.item.scopedTo(target.session.id), userId, itemFields = PLAYBACK_ITEM_FIELDS
            ).orEmpty()
            // An explicitly chosen version (Versions picker / in-player switch) pins this copy.
            val versionChosen = sourceId != null && target.item.mediaSources.any { it.id == sourceId }
            val homeCopy = others.firstOrNull { it.session.isHome }
            if (!versionChosen && !target.session.isHome && homeCopy != null) {
                playbackDeferred.cancel()
                val asked = target
                target = Target(homeCopy.session, homeCopy.item.unscoped())
                sourceId = null
                playbackDeferred = playbackInfoAsync(target.session, target.item.id, null)
                others = others.filterNot { it === homeCopy } +
                    ItemCopy(asked.session, asked.item.scopedTo(asked.session.id))
            }
            val session = target.session
            val item = target.item
            var playback = playbackDeferred.await()

            // Server quirk: when a file's audio track isn't flagged default (or user picked a track
            // whose codec requires server conversion), Jellyfin may approve direct play without
            // checking the audio codec. Ask again with direct play off to get converted audio.
            if (playback != null && directPlaysUnsupportedAudio(playback, sourceId, deviceProfileFor(session), audioStreamIndex)) {
                playback = playbackInfoAsync(session, item.id, sourceId, enableDirectPlay = false).await() ?: playback
            }

            // Start the non-critical lookups now so they overlap with session building / player
            // preparation. They never fail (a failure completes them with null).
            val segmentsDeferred = extrasScope?.async {
                runCatchingCancellable { PlaybackSegments.from(session.api.getMediaSegments(item.id).items) }.getOrNull()
            }
            val nextEpisodeDeferred = extrasScope?.async {
                runCatchingCancellable { NextEpisodeLookup(findNextEpisode(userId, session, item)) }.getOrNull()
            }
            try {
                val (prepared, frameRate) = buildSession(
                    server = session,
                    others = others,
                    item = item,
                    playback = playback,
                    mediaSourceId = sourceId,
                    maxBitrateBps = maxBitrateBps,
                    stereoDownmixEnabled = stereoDownmixEnabled,
                    burnInSubtitleIndex = burnInSubtitleIndex,
                    audioStreamIndex = audioStreamIndex,
                    forceHls = forceHls,
                    hlsCodecs = hlsTranscodeCodecs(
                        caps = capabilities,
                        audioPassthroughEnabled = audioPassthroughEnabled,
                        stereoDownmixEnabled = stereoDownmixEnabled,
                        softwareAudioDecodingEnabled = softwareAudioDecodingEnabled
                    )
                )
                PreparedPlayback(prepared, frameRate, segmentsDeferred, nextEpisodeDeferred)
            } catch (t: Throwable) {
                segmentsDeferred?.cancel()
                nextEpisodeDeferred?.cancel()
                throw t
            }
        }
    }

    /** Picks the episode to play for a Series/Season: in-progress first, then first unwatched. */
    private suspend fun resolveEpisodeToPlay(
        homeUserId: String,
        session: ServerSession,
        seriesOrSeason: JellyfinItemDto
    ): Target {
        val isSeason = seriesOrSeason.type.equals("Season", ignoreCase = true)
        val targetSeriesId = if (isSeason) seriesOrSeason.seriesId ?: seriesOrSeason.id else seriesOrSeason.id
        val targetSeasonId = if (isSeason) seriesOrSeason.id else null
        // A series that is on several servers: pick from the merged episode list instead.
        mergedSeriesEpisodes(homeUserId, session.scope(targetSeriesId))?.let { merged ->
            val pool = merged
                .filter { (it.parentIndexNumber ?: 1) > 0 }
                .filter { !isSeason || seriesOrSeason.indexNumber == null || it.parentIndexNumber == seriesOrSeason.indexNumber }
            val pick = pool.filter { (it.userData?.playbackPositionTicks ?: 0L) > 0L }
                .maxByOrNull { CrossServerMerge.parseDateMs(it.userData?.lastPlayedDate) ?: Long.MIN_VALUE }
                ?: pool.firstOrNull { it.userData?.played != true }
                ?: pool.firstOrNull()
            val owner = pick?.let { sessionFor(it.id, homeUserId) }
            if (pick != null && owner != null) return Target(owner, pick.unscoped())
        }
        val userId = session.userId
        val jellyfinApi = session.api
        val resumeCandidate = if (targetSeasonId == null) {
            runCatchingCancellable {
                jellyfinApi.getResumeItemsLite(userId, parentId = targetSeriesId, limit = 10)
                    .items.firstOrNull { it.seriesId == targetSeriesId }
            }.getOrNull()
        } else {
            null
        }
        val episode = resumeCandidate ?: runCatchingCancellable {
            val sortedEpisodes = jellyfinApi.getEpisodesLite(
                seriesId = targetSeriesId,
                userId = userId,
                seasonId = targetSeasonId
            ).items
                .filter { (it.parentIndexNumber ?: 1) > 0 }
                .sortedWith(compareBy({ it.parentIndexNumber ?: 1 }, { it.indexNumber ?: 1 }))
            sortedEpisodes.firstOrNull { (it.userData?.playbackPositionTicks ?: 0L) > 0L }
                ?: sortedEpisodes.firstOrNull { it.userData?.played != true }
                ?: sortedEpisodes.firstOrNull()
        }.getOrNull() ?: error(strings.playerErrorNoEpisodes.format(seriesOrSeason.name))
        return Target(session, episode)
    }

    /**
     * Every episode of a series that exists on several servers, merged by season / episode number
     * (ids server scoped, watch state from the most recently played copy, the home server's copy
     * of each episode first). Null when the series is on one server only.
     */
    private suspend fun mergedSeriesEpisodes(homeUserId: String, scopedSeriesId: String): List<JellyfinItemDto>? {
        val srv = servers ?: return null
        val seriesCopies = srv.links.copiesOf(scopedSeriesId)
        if (seriesCopies.size < 2) return null
        val perServer = coroutineScope {
            seriesCopies.map { copyId ->
                async {
                    val session = sessionFor(copyId, homeUserId) ?: return@async emptyList()
                    if (!srv.isAvailable(session.id)) return@async emptyList()
                    suspend fun fetch(s: ServerSession) =
                        s.api.getEpisodesLite(seriesId = s.raw(copyId), userId = s.userId).items
                    val episodes = if (session.isHome) runCatchingCancellable { fetch(session) }.getOrNull()
                    else srv.callExtra(session, CrossServerCopies.LOOKUP_TIMEOUT_MS) { fetch(it) }
                    episodes?.scopedTo(session.id).orEmpty()
                }
            }.awaitAll()
        }
        if (perServer.count { it.isNotEmpty() } < 2) return null
        return CrossServerMerge.mergeEpisodes(perServer).map { it.item }
    }

    /**
     * Finds the episode after [item]: the next one in the season, or for a season finale the
     * first episode of the next (non-special) season. Uses a small `StartItemId` page of the
     * series instead of downloading whole season / series episode lists.
     */
    private suspend fun findNextEpisode(homeUserId: String, session: ServerSession, item: JellyfinItemDto): NextEpisodeInfo? {
        val seriesId = item.seriesId
        if (!item.type.equals("Episode", ignoreCase = true) || seriesId.isNullOrBlank()) return null
        // On several servers: the next episode may only exist on another one.
        mergedSeriesEpisodes(homeUserId, session.scope(seriesId))?.let { merged ->
            val idx = merged.indexOfFirst {
                it.parentIndexNumber == item.parentIndexNumber && it.indexNumber == item.indexNumber
            }
            if (idx >= 0) return pickNextEpisode(item, merged.drop(idx + 1))?.let(NextEpisodeInfo::of)
        }
        return findNextEpisodeOnServer(session, item, seriesId)?.let { it.copy(id = session.scope(it.id)) }
    }

    private suspend fun findNextEpisodeOnServer(
        session: ServerSession,
        item: JellyfinItemDto,
        seriesId: String
    ): NextEpisodeInfo? {
        val userId = session.userId
        val jellyfinApi = session.api
        val page = runCatchingCancellable {
            jellyfinApi.getEpisodesLite(
                seriesId = seriesId,
                userId = userId,
                startItemId = item.id,
                limit = NEXT_EPISODE_LOOKAHEAD
            ).items
        }.getOrDefault(emptyList())
        val currentIdx = page.indexOfFirst { it.id == item.id }
        if (currentIdx >= 0) {
            return pickNextEpisode(item, page.drop(currentIdx + 1))?.let(NextEpisodeInfo::of)
        }
        // Server ignored StartItemId (or the current episode is filtered out): fall back to the
        // season list, then the series list for a season finale.
        val seasonEps = jellyfinApi.getEpisodesLite(
            seriesId = seriesId,
            userId = userId,
            seasonId = item.seasonId
        ).items.sortedBy { it.indexNumber ?: 0 }
        val idx = seasonEps.indexOfFirst { it.id == item.id }
        val currentSeasonNumber = item.parentIndexNumber ?: 0
        val nextEp = if (idx >= 0 && idx + 1 < seasonEps.size) {
            seasonEps[idx + 1]
        } else if (idx >= 0 && currentSeasonNumber > 0) {
            jellyfinApi.getEpisodesLite(seriesId = seriesId, userId = userId).items
                .filter { (it.parentIndexNumber ?: 0) > currentSeasonNumber }
                .minWithOrNull(compareBy({ it.parentIndexNumber ?: 0 }, { it.indexNumber ?: 0 }))
        } else {
            null
        }
        return nextEp?.let(NextEpisodeInfo::of)
    }

    private fun buildSession(
        server: ServerSession,
        others: List<ItemCopy>,
        item: JellyfinItemDto,
        playback: JellyfinPlaybackInfoResponse?,
        mediaSourceId: String?,
        maxBitrateBps: Long?,
        stereoDownmixEnabled: Boolean,
        burnInSubtitleIndex: Int?,
        audioStreamIndex: Int?,
        forceHls: Boolean = false,
        hlsCodecs: HlsCodecs = HlsCodecs.DEFAULT
    ): Pair<PlaybackSessionData, Float?> {
        val labels = strings
        val availableSources = playback?.mediaSources?.takeIf { it.isNotEmpty() }
            ?: item.mediaSources.takeIf { it.isNotEmpty() }
            ?: listOf(
                JellyfinMediaSourceDto(
                    id = item.id,
                    name = item.name,
                    supportsDirectPlay = true,
                    supportsDirectStream = true,
                    mediaStreams = item.mediaStreams
                )
            )

        val source = if (!mediaSourceId.isNullOrBlank()) {
            availableSources.firstOrNull { it.id == mediaSourceId }
                ?: availableSources.firstOrNull()
        } else {
            availableSources.firstOrNull()
        } ?: error(strings.playerErrorNoSources)

        val playSessionId = playback?.playSessionId ?: "${System.currentTimeMillis()}"
        // Stream, subtitle and trickplay URLs go to the server that owns this copy, with its token.
        val base = if (server.isHome) {
            prefs.cachedResolvedJellyfinUrl
                .ifBlank { prefs.cachedJellyfinLocalUrl }
                .ifBlank { prefs.cachedJellyfinUrl }
                .trim()
                .trimEnd('/')
        } else {
            servers?.baseUrl(server.id).orEmpty()
        }
        val token = if (server.isHome) prefs.cachedToken.orEmpty() else servers?.token(server.id).orEmpty()

        // The server's own cap is normally applied by the server (it is in the device profile sent
        // with PlaybackInfo, so a file under the cap still direct plays). Only when that answer is
        // missing does the URL itself have to ask for a capped stream.
        val serverCap = server.server.maxStreamingBitrateBps
        val urlBitrate = maxBitrateBps?.let { cappedBitrate(it, server) }
            ?: serverCap?.takeIf { cap -> playback == null && (source.bitrate ?: 0L) > cap }

        val (streamUrl, isHls) = buildStreamUrl(
            base = base,
            token = token,
            itemId = item.id,
            source = source,
            playSessionId = playSessionId,
            maxBitrateBps = urlBitrate,
            stereoDownmix = stereoDownmixEnabled,
            burnInSubtitleIndex = burnInSubtitleIndex,
            audioStreamIndex = audioStreamIndex,
            forceHls = forceHls,
            hlsCodecs = hlsCodecs
        )

        val audioTracks = source.mediaStreams
            .filter { it.type.equals("Audio", ignoreCase = true) }
            .map {
                TrackOption(
                    index = it.index,
                    label = it.displayTitle ?: it.language?.uppercase() ?: labels.playerAudioTrackN.format(it.index),
                    language = it.language,
                    codec = it.codec,
                    isDefault = it.isDefault,
                    isForced = it.isForced,
                    isHearingImpaired = it.isHearingImpaired,
                    channels = it.channels,
                    bitRate = it.bitRate,
                    sampleRate = it.sampleRate
                )
            }

        val externalSubtitles = source.mediaStreams
            .filter { it.type.equals("Subtitle", ignoreCase = true) }
            .mapNotNull { stream ->
                val ext = normalizeSubtitleFormat(stream.codec)
                val delivery = stream.deliveryUrl?.takeIf { it.isNotBlank() }?.let { path ->
                    resolveRelativeJellyfinUrl(base, path, token)
                } ?: run {
                    // Image-based subtitles (PGS/VobSub/DVB) cannot be served as text; side-loading a
                    // "Stream.srt" for them fails on the server and makes ExoPlayer abort playback.
                    if (isImageBasedSubtitle(stream.codec)) return@mapNotNull null
                    "$base/Videos/${item.id}/${source.id}/Subtitles/${stream.index}/0/Stream.$ext?api_key=$token"
                }
                ExternalSubtitleFile(
                    index = stream.index,
                    title = stream.displayTitle ?: stream.title ?: stream.language?.uppercase() ?: labels.playerSubtitleTrackN.format(stream.index),
                    language = stream.language,
                    codec = stream.codec,
                    url = delivery,
                    isDefault = stream.isDefault,
                    isForced = stream.isForced
                )
            }

        val subtitleTracks = source.mediaStreams
            .filter { it.type.equals("Subtitle", ignoreCase = true) }
            .map { stream ->
                val matchingExt = externalSubtitles.firstOrNull { it.index == stream.index }
                TrackOption(
                    index = stream.index,
                    label = stream.displayTitle ?: stream.language?.uppercase() ?: labels.playerSubtitleTrackN.format(stream.index),
                    language = stream.language,
                    codec = stream.codec,
                    isExternal = stream.isExternal,
                    externalUrl = matchingExt?.url,
                    isDefault = stream.isDefault,
                    isForced = stream.isForced,
                    isHearingImpaired = stream.isHearingImpaired
                )
            }

        val chapters = item.chapters
            .filter { !it.name.isNullOrBlank() || it.startPositionTicks > 0L }
            .mapIndexed { idx, ch ->
                ChapterMarker(
                    name = ch.name?.takeIf { it.isNotBlank() } ?: labels.playerChapterN.format(idx + 1),
                    startPositionMs = (ch.startPositionTicks / 10_000L).coerceAtLeast(0L)
                )
            }
            .sortedBy { it.startPositionMs }

        // This copy's files, then the copies on other servers (labelled with their server).
        val mediaSourceOptions = crossServerVersions(
            ownServerId = server.id,
            ownServerName = if (others.isNotEmpty() || !server.isHome) {
                server.server.displayName.takeIf { servers != null }
            } else null,
            ownSources = availableSources,
            others = others
        )
        // Resume from whichever copy was played most recently.
        val watchState = CrossServerMerge.mergeUserData(listOf(item.userData) + others.map { it.item.userData })

        val primaryVideo = source.mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
        val activeAudioIdx = audioStreamIndex
            ?: source.defaultAudioStreamIndex
            ?: audioTracks.firstOrNull { it.isDefault }?.index
            ?: audioTracks.firstOrNull()?.index
        val primaryAudio = source.mediaStreams.firstOrNull {
            it.type.equals("Audio", ignoreCase = true) && it.index == activeAudioIdx
        } ?: source.mediaStreams.firstOrNull { it.type.equals("Audio", ignoreCase = true) }

        val resolutionLabel = primaryVideo?.let { v ->
            when {
                (v.width ?: 0) >= 3800 || (v.height ?: 0) >= 2000 -> "4K UHD"
                (v.width ?: 0) >= 1900 || (v.height ?: 0) >= 1000 -> "1080p"
                (v.width ?: 0) >= 1200 || (v.height ?: 0) >= 700 -> "720p"
                v.height != null -> "${v.height}p"
                else -> null
            }
        }
        val hdrFormat = primaryVideo?.let { v ->
            val rt = v.videoRangeType.orEmpty()
            when {
                rt.contains("DOVI", ignoreCase = true) || rt.contains("Dolby", ignoreCase = true) -> "Dolby Vision"
                rt.contains("HDR10Plus", ignoreCase = true) -> "HDR10+"
                rt.contains("HDR10", ignoreCase = true) -> "HDR10"
                v.videoRange.equals("HDR", ignoreCase = true) -> "HDR"
                else -> null
            }
        }
        val audioSummary = primaryAudio?.let { a ->
            val codec = a.codec?.uppercase()
            val ch = when (a.channels) {
                8 -> "7.1"
                6 -> "5.1"
                2 -> "2.0"
                else -> null
            }
            val atmos = if (a.audioSpatialFormat.orEmpty().contains("Atmos", ignoreCase = true)) "Atmos" else null
            listOfNotNull(codec, ch, atmos).joinToString(" ").ifBlank { null }
        }
        val videoFrameRate = (primaryVideo?.realFrameRate ?: primaryVideo?.averageFrameRate)
            ?.takeIf { it > 1f && it < 400f }

        val (playMethod, transcodeReasons) = resolvePlayMethodAndReasons(
            isHls = isHls,
            source = source,
            streamUrl = streamUrl,
            maxBitrateBps = urlBitrate,
            stereoDownmix = stereoDownmixEnabled,
            burnInSubtitleIndex = burnInSubtitleIndex
        )

        val trickplay = extractTrickplayInfo(item, source.id, base, token)

        val title = if (item.type.equals("Episode", ignoreCase = true) && !item.seriesName.isNullOrBlank()) {
            item.seriesName
        } else item.name

        val subtitle = if (item.type.equals("Episode", ignoreCase = true)) {
            "S${item.parentIndexNumber ?: 1}:E${item.indexNumber ?: 0} • ${item.name}"
        } else item.productionYear?.toString()

        val directPlayUrl = "$base/Videos/${item.id}/stream?Static=true&MediaSourceId=${source.id}&api_key=$token"

        val session = PlaybackSessionData(
            itemId = server.scope(item.id),
            title = title,
            subtitle = subtitle,
            streamUrl = streamUrl,
            isHls = isHls,
            mediaSourceId = server.scope(source.id),
            playSessionId = playSessionId,
            resumeTicks = watchState?.playbackPositionTicks ?: 0L,
            runtimeTicks = source.runTimeTicks ?: item.runTimeTicks ?: 0L,
            trickplayInfo = trickplay,
            seriesId = item.seriesId?.let { server.scope(it) },
            seasonId = item.seasonId?.let { server.scope(it) },
            videoCodec = primaryVideo?.codec?.uppercase(),
            audioCodec = audioSummary,
            resolutionLabel = resolutionLabel,
            hdrFormat = hdrFormat,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            chapters = chapters,
            mediaSources = mediaSourceOptions,
            externalSubtitleFiles = externalSubtitles,
            directPlayUrl = directPlayUrl,
            isLiveTv = item.type.equals("TvChannel", ignoreCase = true),
            playMethod = playMethod,
            transcodeReasons = transcodeReasons,
            videoBitrate = primaryVideo?.bitRate ?: source.bitrate,
            audioBitrate = primaryAudio?.bitRate,
            videoFps = videoFrameRate,
            container = source.container ?: item.container,
            fileSizeBytes = source.size,
            filePath = source.path ?: item.path,
            activeAudioStreamIndex = activeAudioIdx,
            burnedInSubtitleIndex = burnInSubtitleIndex
        )
        return session to videoFrameRate
    }

    suspend fun prepareLiveTvPlayback(
        userId: String,
        channelId: String
    ): Result<PlaybackSessionData> = preparePlayback(
        userId = userId,
        itemId = channelId
    ).map { session ->
        session.copy(isLiveTv = true)
    }

    /**
     * Queries Jellyfin's `/Users/{userId}/Items/{itemId}/Intros` for cinema pre-roll clips
     * (trailers/intros configured on the server) and returns scoped item IDs ready to play.
     */
    suspend fun loadCinemaIntros(userId: String, itemId: String): List<String> {
        val server = sessionFor(itemId, userId) ?: return emptyList()
        return runCatchingCancellable {
            server.api.getIntros(userId = server.userId, itemId = server.raw(itemId))
                .items
                .mapNotNull { dto -> dto.id.takeIf { it.isNotBlank() }?.let(server::scope) }
        }.getOrDefault(emptyList())
    }

    suspend fun searchRemoteSubtitles(
        itemId: String,
        language: String = "eng"
    ): Result<List<com.seerflix.tv.core.model.RemoteSubtitleOption>> = runCatchingCancellable {
        val labels = strings
        val server = sessionFor(itemId, prefs.cachedHomeUserId) ?: error(labels.playerErrorNotSignedIn)
        server.api.searchRemoteSubtitles(itemId = server.raw(itemId), language = language)
            .map { dto ->
                com.seerflix.tv.core.model.RemoteSubtitleOption(
                    id = dto.id,
                    name = dto.name ?: dto.comment ?: labels.playerRemoteSubtitleFallback.format(dto.id),
                    language = dto.threeLetterIsoLanguageName ?: language,
                    format = dto.format,
                    providerName = dto.providerName,
                    downloadCount = dto.downloadCount,
                    communityRating = dto.communityRating,
                    isHashMatch = dto.isHashMatch == true
                )
            }
            .sortedWith(
                compareByDescending<com.seerflix.tv.core.model.RemoteSubtitleOption> { it.isHashMatch }
                    .thenByDescending { it.downloadCount ?: 0 }
                    .thenByDescending { it.communityRating ?: 0f }
            )
    }

    suspend fun downloadRemoteSubtitle(
        itemId: String,
        subtitleId: String
    ): Result<Unit> = runCatchingCancellable {
        val server = sessionFor(itemId, prefs.cachedHomeUserId) ?: error(strings.playerErrorNotSignedIn)
        server.api.downloadRemoteSubtitle(itemId = server.raw(itemId), subtitleId = subtitleId)
    }

    /**
     * Start / progress / stop go only to the server that is actually streaming [session], with
     * that server's raw ids.
     */
    private suspend fun report(
        session: PlaybackSessionData,
        positionTicks: Long,
        isPaused: Boolean,
        reportState: PlaybackReportState? = null,
        send: suspend (JellyfinApi, JellyfinPlaybackProgressRequest) -> Unit
    ) {
        if (session.isTrailer || session.isCinemaIntro) return
        val server = sessionFor(session.itemId, prefs.cachedHomeUserId) ?: return
        runCatchingCancellable {
            send(
                server.api,
                JellyfinPlaybackProgressRequest(
                    itemId = server.raw(session.itemId),
                    mediaSourceId = ServerScopedId.rawOf(session.mediaSourceId),
                    playSessionId = session.playSessionId,
                    positionTicks = positionTicks,
                    isPaused = isPaused,
                    playMethod = reportState?.playMethod ?: session.playMethod.jellyfinValue,
                    audioStreamIndex = reportState?.audioStreamIndex ?: session.activeAudioStreamIndex,
                    subtitleStreamIndex = reportState?.subtitleStreamIndex ?: session.burnedInSubtitleIndex,
                    isMuted = reportState?.isMuted,
                    volumeLevel = reportState?.volumeLevel,
                    playbackRate = reportState?.playbackRate,
                    // Live TV can't be seeked; everything else can (remote-control seek bars).
                    canSeek = !session.isLiveTv
                )
            )
        }
    }

    suspend fun reportStart(
        session: PlaybackSessionData,
        positionTicks: Long,
        reportState: PlaybackReportState? = null
    ) {
        report(session, positionTicks, isPaused = false, reportState = reportState) { api, body ->
            api.reportPlaybackStart(body)
        }
    }

    suspend fun reportProgress(
        session: PlaybackSessionData,
        positionTicks: Long,
        isPaused: Boolean,
        reportState: PlaybackReportState? = null
    ) {
        report(session, positionTicks, isPaused, reportState = reportState) { api, body ->
            api.reportPlaybackProgress(body)
        }
    }

    /**
     * Sends the "stopped" report on a process-lifetime scope so it is not cancelled when the
     * caller's scope (e.g. a ViewModel being cleared on exit) goes away.
     */
    fun reportStoppedDetached(
        session: PlaybackSessionData,
        positionTicks: Long,
        reportState: PlaybackReportState? = null
    ) {
        reportScope.launch { reportStopped(session, positionTicks, reportState) }
    }

    suspend fun reportStopped(
        session: PlaybackSessionData,
        positionTicks: Long,
        reportState: PlaybackReportState? = null
    ) {
        report(session, positionTicks, isPaused = false, reportState = reportState) { api, body ->
            api.reportPlaybackStopped(body)
        }
    }

    companion object {
        private val HOME_SERVER = JellyfinServer(id = ServerScopedId.HOME, isHome = true)

        /**
         * Only what playback reads from the item (chapters, sources/streams, trickplay; user data
         * and series/season numbering are always included) — not People/Overview/etc.
         */
        const val PLAYBACK_ITEM_FIELDS = "Chapters,MediaSources,MediaStreams,Trickplay"

        /** Episodes requested after the current one; enough to skip a few interleaved specials. */
        const val NEXT_EPISODE_LOOKAHEAD = 8

        /**
         * Picks the next episode from [following] (episodes after [current] in the server's
         * series order): specials are skipped unless the current episode is itself a special.
         */
        fun pickNextEpisode(current: JellyfinItemDto, following: List<JellyfinItemDto>): JellyfinItemDto? {
            val currentIsSpecial = (current.parentIndexNumber ?: 1) == 0
            return following.firstOrNull { candidate ->
                val candidateIsSpecial = (candidate.parentIndexNumber ?: 1) == 0
                candidate.id != current.id && candidateIsSpecial == currentIsSpecial
            }
        }

        private val IN_BAND_TEXT_SUBTITLE_CODECS_MKV = setOf(
            "srt", "subrip", "ass", "ssa", "webvtt", "vtt"
        )
        private val IN_BAND_TEXT_SUBTITLE_CODECS_MP4 = setOf("mov_text", "tx3g", "webvtt", "wvtt")

        /**
         * True when an embedded text subtitle is demuxed by ExoPlayer from the directly played
         * container itself, so it must NOT also be side-loaded: every side-loaded subtitle is an
         * extra HTTP request on the critical path (the player only becomes prepared once each one
         * has responded, and Jellyfin first extracts embedded subtitles from the whole file) and
         * it duplicated the in-band track in the subtitle menu.
         */
        fun isInBandTextSubtitle(
            container: String?,
            codec: String?,
            isExternal: Boolean,
            isHls: Boolean
        ): Boolean {
            if (isHls || isExternal) return false
            val normalizedCodec = codec?.trim()?.lowercase() ?: return false
            val containers = container.orEmpty().lowercase().split(',').map { it.trim() }.toSet()
            return when {
                containers.any { it == "mkv" || it == "matroska" || it == "webm" } ->
                    normalizedCodec in IN_BAND_TEXT_SUBTITLE_CODECS_MKV
                containers.any { it == "mp4" || it == "m4v" || it == "mov" } ->
                    normalizedCodec in IN_BAND_TEXT_SUBTITLE_CODECS_MP4
                else -> false
            }
        }

        /**
         * The profile Jellyfin uses to decide between direct play, remux and transcode.
         * Built from what the device reports ([capabilities]) so the server converts anything the
         * box, screen or receiver can't handle instead of the stream failing or looking wrong.
         */
        private fun transcodeAudioCodecs(stereoDownmixEnabled: Boolean, surroundAudio: List<String>): String =
            if (stereoDownmixEnabled) {
                "aac,mp3"
            } else {
                (listOf("aac") + surroundAudio.filter { it == "ac3" || it == "eac3" } + "mp3").joinToString(",")
            }

        /**
         * Codecs for the HLS URLs the app builds itself (burn-in, quality cap, downmix, the error
         * fallback). They used to hard-code h264,hevc + ac3/eac3, so the server could stream-copy
         * a 10-bit HEVC or AC3 track the box can't decode — often the very stream that had just
         * failed. Same rules as the device profile's transcoding target.
         */
        fun hlsTranscodeCodecs(
            caps: DeviceMediaCapabilities,
            audioPassthroughEnabled: Boolean,
            stereoDownmixEnabled: Boolean,
            softwareAudioDecodingEnabled: Boolean
        ): HlsCodecs {
            val swAudioActive = softwareAudioDecodingEnabled &&
                (caps.softwareAudioDecoderAvailable || FfmpegAudioSupport.isAvailable())
            fun audioOk(passthrough: Boolean, decoder: Boolean) =
                (audioPassthroughEnabled && passthrough) || decoder || swAudioActive
            val surround = buildList {
                if (audioOk(caps.passthroughAc3, caps.ac3Decoder)) add("ac3")
                if (audioOk(caps.passthroughEac3, caps.eac3Decoder)) add("eac3")
            }
            return HlsCodecs(
                video = if (caps.hevc) "h264,hevc" else "h264",
                audio = transcodeAudioCodecs(stereoDownmixEnabled, surround),
                hevcMaxBitDepth = if (caps.hevc && !caps.hevcMain10) 8 else null
            )
        }

        fun buildDeviceProfile(
            maxBitrateBps: Long? = null,
            audioPassthroughEnabled: Boolean = true,
            stereoDownmixEnabled: Boolean = false,
            assSubtitleMode: AssSubtitleMode = AssSubtitleMode.STYLED,
            burnInSubtitleIndex: Int? = null,
            capabilities: DeviceMediaCapabilities = DeviceMediaCapabilities.PERMISSIVE,
            softwareAudioDecodingEnabled: Boolean = false
        ): JellyfinDeviceProfileDto {
            val caps = capabilities
            val baseAudio = listOf("aac", "mp3", "opus", "flac", "vorbis")
            val swAudioActive = softwareAudioDecodingEnabled &&
                (caps.softwareAudioDecoderAvailable || FfmpegAudioSupport.isAvailable())
            // Passthrough needs both the user's toggle and a sink (TV / AVR) that accepts the format;
            // otherwise the box has to decode it, via platform MediaCodec or bundled FFmpeg PCM.
            fun audioOk(passthrough: Boolean, decoder: Boolean) =
                (audioPassthroughEnabled && passthrough) || decoder || swAudioActive
            val surroundAudio = buildList {
                if (audioOk(caps.passthroughAc3, caps.ac3Decoder)) add("ac3")
                if (audioOk(caps.passthroughEac3, caps.eac3Decoder)) add("eac3")
                if (audioOk(caps.passthroughDts, caps.dtsDecoder)) { add("dts"); add("dca") }
                if (audioOk(caps.passthroughTrueHd, caps.trueHdDecoder)) add("truehd")
            }
            val directAudioCodecs = if (stereoDownmixEnabled) {
                baseAudio.joinToString(",")
            } else {
                (baseAudio + surroundAudio).joinToString(",")
            }
            val transcodeAudioCodecs = transcodeAudioCodecs(stereoDownmixEnabled, surroundAudio)

            val directVideo = buildList {
                add("h264")
                if (caps.hevc) add("hevc")
                if (caps.vp9) add("vp9")
                if (caps.av1) add("av1")
            }
            val transcodeVideo = if (caps.hevc) "h264,hevc" else "h264"

            val ranges = DeviceMediaCapabilities.allowedVideoRanges(caps)
            fun rangeCondition(allowed: List<String>) =
                JellyfinProfileConditionDto("EqualsAny", "VideoRangeType", allowed.joinToString("|"))
            fun sizeConditions(codec: String) = caps.maxResolution[codec]?.let { (w, h) ->
                listOf(
                    JellyfinProfileConditionDto("LessThanEqual", "Width", w.toString()),
                    JellyfinProfileConditionDto("LessThanEqual", "Height", h.toString())
                )
            }.orEmpty()
            fun bitDepthCondition(tenBit: Boolean) = if (tenBit) emptyList() else
                listOf(JellyfinProfileConditionDto("LessThanEqual", "VideoBitDepth", "8"))

            val codecProfiles = buildList {
                add(
                    JellyfinCodecProfileDto(
                        type = "Video",
                        codec = "h264",
                        conditions = listOf(rangeCondition(listOf("SDR"))) +
                            bitDepthCondition(caps.h264High10) + sizeConditions("h264")
                    )
                )
                if (caps.hevc) {
                    add(
                        JellyfinCodecProfileDto(
                            type = "Video",
                            codec = "hevc",
                            // An 8-bit-only HEVC decoder can't take HDR (always 10-bit) either.
                            conditions = listOf(
                                rangeCondition(if (caps.hevcMain10) ranges else listOf("SDR", "DOVIWithSDR"))
                            ) + bitDepthCondition(caps.hevcMain10) + sizeConditions("hevc")
                        )
                    )
                }
                if (caps.vp9) {
                    add(
                        JellyfinCodecProfileDto(
                            type = "Video",
                            codec = "vp9",
                            conditions = listOf(
                                rangeCondition(
                                    if (caps.vp9Profile2) ranges.filterNot { it.startsWith("DOVI") } else listOf("SDR")
                                )
                            ) + bitDepthCondition(caps.vp9Profile2) + sizeConditions("vp9")
                        )
                    )
                }
                if (caps.av1) {
                    add(
                        JellyfinCodecProfileDto(
                            type = "Video",
                            codec = "av1",
                            conditions = listOf(
                                rangeCondition(if (caps.av1Main10) ranges else listOf("SDR", "DOVIWithSDR"))
                            ) + bitDepthCondition(caps.av1Main10) + sizeConditions("av1")
                        )
                    )
                }
            }

            val assMethod = if (assSubtitleMode == AssSubtitleMode.BURN_IN && burnInSubtitleIndex != null) {
                "Encode"
            } else {
                "External"
            }
            val bitmapSubMethod = if (burnInSubtitleIndex != null) "Encode" else "Embed"
            return JellyfinDeviceProfileDto(
                maxStreamingBitrate = maxBitrateBps ?: 120_000_000L,
                directPlayProfiles = listOf(
                    JellyfinDirectPlayProfileDto(
                        type = "Video",
                        container = "mp4,m4v,mkv,webm,mov,ts",
                        videoCodec = directVideo.joinToString(","),
                        audioCodec = directAudioCodecs
                    )
                ),
                transcodingProfiles = listOf(
                    JellyfinTranscodingProfileDto(
                        container = "ts",
                        type = "Video",
                        videoCodec = transcodeVideo,
                        audioCodec = transcodeAudioCodecs,
                        protocol = "hls"
                    )
                ),
                subtitleProfiles = listOf(
                    JellyfinSubtitleProfileDto("ass", assMethod),
                    JellyfinSubtitleProfileDto("ssa", assMethod),
                    JellyfinSubtitleProfileDto("srt", "External"),
                    JellyfinSubtitleProfileDto("vtt", "External"),
                    JellyfinSubtitleProfileDto("subrip", "External"),
                    JellyfinSubtitleProfileDto("pgssub", bitmapSubMethod),
                    JellyfinSubtitleProfileDto("pgs", bitmapSubMethod),
                    JellyfinSubtitleProfileDto("dvdsub", bitmapSubMethod),
                    JellyfinSubtitleProfileDto("vobsub", bitmapSubMethod),
                    JellyfinSubtitleProfileDto("dvbsub", bitmapSubMethod)
                ),
                codecProfiles = codecProfiles
            )
        }

        /**
         * True when the server approved direct play although the source's audio ([audioStreamIndex]
         * if explicitly requested, else the default track, else the first) is not in the profile's
         * direct-play audio codecs.
         */
        fun directPlaysUnsupportedAudio(
            playback: JellyfinPlaybackInfoResponse,
            mediaSourceId: String?,
            profile: JellyfinDeviceProfileDto,
            audioStreamIndex: Int? = null
        ): Boolean {
            val source = playback.mediaSources.firstOrNull { it.id == mediaSourceId }
                ?: playback.mediaSources.firstOrNull() ?: return false
            if (!source.supportsDirectPlay) return false
            val allowed = profile.directPlayProfiles
                .flatMap { it.audioCodec.split(',') }
                .map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .toSet()
            if (allowed.isEmpty()) return false
            val audio = source.mediaStreams.filter { it.type.equals("Audio", ignoreCase = true) }
            val chosen = audioStreamIndex?.let { idx -> audio.firstOrNull { it.index == idx } }
                ?: audio.firstOrNull { it.isDefault }
                ?: audio.firstOrNull()
                ?: return false
            val codec = chosen.codec?.trim()?.lowercase() ?: return false
            return codec !in allowed
        }

        fun mapMediaSourceOption(source: JellyfinMediaSourceDto): MediaSourceOption {
            val video = source.mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
            val audio = source.mediaStreams.firstOrNull { it.type.equals("Audio", ignoreCase = true) }
            val res = video?.let {
                when {
                    (it.width ?: 0) >= 3800 || (it.height ?: 0) >= 2000 -> "4K"
                    (it.width ?: 0) >= 1900 || (it.height ?: 0) >= 1000 -> "1080p"
                    (it.width ?: 0) >= 1200 || (it.height ?: 0) >= 700 -> "720p"
                    it.height != null -> "${it.height}p"
                    else -> null
                }
            }
            val audioLabel = audio?.let { a ->
                val ch = when (a.channels) {
                    8 -> "7.1"
                    6 -> "5.1"
                    2 -> "2.0"
                    else -> null
                }
                listOfNotNull(a.codec?.uppercase(), ch).joinToString(" ").ifBlank { null }
            }
            return MediaSourceOption(
                id = source.id,
                name = source.name ?: source.container?.uppercase() ?: source.id,
                container = source.container,
                bitrate = source.bitrate,
                resolutionLabel = res,
                videoCodec = video?.codec?.uppercase(),
                audioSummary = audioLabel
            )
        }

        fun normalizeSubtitleFormat(codec: String?): String = when (codec?.lowercase()) {
            "subrip", "srt" -> "srt"
            "ass" -> "ass"
            "ssa" -> "ssa"
            "vtt", "webvtt" -> "vtt"
            else -> "srt"
        }

        private val IMAGE_SUBTITLE_CODECS = setOf(
            "pgs", "pgssub", "hdmv_pgs_subtitle", "dvdsub", "dvd_subtitle", "vobsub",
            "dvbsub", "dvb_subtitle", "xsub"
        )

        fun isImageBasedSubtitle(codec: String?): Boolean {
            val normalized = codec?.trim()?.lowercase() ?: return false
            return normalized in IMAGE_SUBTITLE_CODECS
        }

        fun resolveRelativeJellyfinUrl(base: String, rawPath: String, token: String): String {
            val cleanBase = base.trim().trimEnd('/')
            val fullUrl = if (rawPath.startsWith("http://", ignoreCase = true) || rawPath.startsWith("https://", ignoreCase = true)) {
                rawPath
            } else {
                "$cleanBase/${rawPath.trimStart('/')}"
            }
            return if (token.isNotBlank() && !fullUrl.contains("api_key=", ignoreCase = true)) {
                val sep = if ('?' in fullUrl) "&" else "?"
                "${fullUrl}${sep}api_key=$token"
            } else {
                fullUrl
            }
        }

        fun buildStreamUrl(
            base: String,
            token: String,
            itemId: String,
            source: JellyfinMediaSourceDto,
            playSessionId: String,
            maxBitrateBps: Long?,
            stereoDownmix: Boolean = false,
            burnInSubtitleIndex: Int? = null,
            audioStreamIndex: Int? = null,
            forceHls: Boolean = false,
            hlsCodecs: HlsCodecs = HlsCodecs.DEFAULT
        ): Pair<String, Boolean> {
            val cleanBase = base.trim().trimEnd('/')
            val codecParams = hlsCodecs.queryParams
            val audioParam = if (stereoDownmix) "&MaxAudioChannels=2" else ""
            val audioIdxParam = audioStreamIndex?.let { "&AudioStreamIndex=$it" }.orEmpty()
            val bitrateParam = maxBitrateBps?.let { "&MaxStreamingBitrate=$it" }.orEmpty()
            if (burnInSubtitleIndex != null) {
                val url = "$cleanBase/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId$audioIdxParam&SubtitleStreamIndex=$burnInSubtitleIndex&SubtitleMethod=Encode$bitrateParam$audioParam$codecParams&api_key=$token"
                return url to true
            }
            if (maxBitrateBps != null || stereoDownmix || forceHls) {
                val br = maxBitrateBps ?: 120_000_000L
                val url = "$cleanBase/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId&MaxStreamingBitrate=$br$audioIdxParam$audioParam$codecParams&api_key=$token"
                return url to true
            }
            // DirectStream-only sources need the server's remux/transcode URL: serving the raw file
            // (Static=true) for a container/codec the device profile rejected fails in ExoPlayer.
            return if (source.supportsDirectPlay ||
                (source.supportsDirectStream && source.transcodingUrl.isNullOrBlank())
            ) {
                val url = "$cleanBase/Videos/$itemId/stream?Static=true&MediaSourceId=${source.id}&api_key=$token"
                url to false
            } else if (!source.transcodingUrl.isNullOrBlank()) {
                var url = resolveRelativeJellyfinUrl(cleanBase, source.transcodingUrl, token)
                if (audioStreamIndex != null && !url.contains("AudioStreamIndex=", ignoreCase = true)) {
                    val sep = if ('?' in url) "&" else "?"
                    url = "${url}${sep}AudioStreamIndex=$audioStreamIndex"
                }
                url to true
            } else {
                val url = "$cleanBase/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId$audioIdxParam&api_key=$token"
                url to true
            }
        }

        /**
         * Determines the actual Jellyfin [PlayMethod] (`DirectPlay`, `DirectStream`, `Transcode`)
         * and human-readable transcode reasons from the media source and stream URL.
         */
        fun resolvePlayMethodAndReasons(
            isHls: Boolean,
            source: JellyfinMediaSourceDto,
            streamUrl: String,
            maxBitrateBps: Long? = null,
            stereoDownmix: Boolean = false,
            burnInSubtitleIndex: Int? = null
        ): Pair<PlayMethod, List<String>> {
            if (!isHls) return PlayMethod.DIRECT_PLAY to emptyList()
            val reasons = mutableListOf<String>()
            val query = streamUrl.substringAfter('?', "")
            if (query.isNotBlank()) {
                query.split('&').forEach { part ->
                    val (k, v) = part.split('=', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                    if (k.equals("TranscodeReasons", ignoreCase = true) && v.isNotBlank()) {
                        v.split(',', ';')
                            .map { it.replace("%2C", ",", ignoreCase = true).trim() }
                            .flatMap { it.split(',') }
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .forEach { reasons += it }
                    }
                }
            }
            if (burnInSubtitleIndex != null && reasons.none { it.contains("Subtitle", ignoreCase = true) }) {
                reasons += "SubtitleCodecNotSupported"
            }
            if (maxBitrateBps != null && reasons.none { it.contains("Bitrate", ignoreCase = true) }) {
                reasons += "ContainerBitrateExceedsLimit"
            }
            if (stereoDownmix && reasons.none { it.contains("Audio", ignoreCase = true) }) {
                reasons += "AudioChannelsNotSupported"
            }
            val distinctReasons = reasons.distinct()
            val isRemuxOnly = burnInSubtitleIndex == null &&
                maxBitrateBps == null &&
                !stereoDownmix &&
                source.supportsDirectStream &&
                !streamUrl.contains("VideoBitrate=", ignoreCase = true) &&
                (distinctReasons.isEmpty() || distinctReasons.all { it.contains("Container", ignoreCase = true) })
            val method = if (isRemuxOnly) PlayMethod.DIRECT_STREAM else PlayMethod.TRANSCODE
            return method to distinctReasons
        }

        fun extractTrickplayInfo(
            item: JellyfinItemDto,
            mediaSourceId: String,
            base: String,
            token: String
        ): TrickplayInfo? {
            if (item.trickplay.isEmpty() || base.isBlank()) return null
            val byWidth = item.trickplay[mediaSourceId] ?: item.trickplay.values.firstOrNull() ?: return null
            val chosen = byWidth["320"] ?: byWidth.values.minByOrNull { kotlin.math.abs(it.width - 320) } ?: return null
            if (chosen.thumbnailCount <= 0 || chosen.width <= 0 || chosen.height <= 0) return null
            val template = "$base/Videos/${item.id}/Trickplay/${chosen.width}/{tileIndex}.jpg?mediaSourceId=$mediaSourceId&api_key=$token"
            return TrickplayInfo(
                width = chosen.width,
                height = chosen.height,
                tileWidth = chosen.tileWidth.coerceAtLeast(1),
                tileHeight = chosen.tileHeight.coerceAtLeast(1),
                thumbnailCount = chosen.thumbnailCount,
                intervalMs = chosen.interval.coerceAtLeast(1000),
                baseUrlTemplate = template
            )
        }
    }
}

/** VideoCodec / AudioCodec (and HEVC bit-depth limit) for app-built HLS stream URLs. */
data class HlsCodecs(
    val video: String,
    val audio: String,
    val hevcMaxBitDepth: Int? = null
) {
    val queryParams: String
        get() = "&VideoCodec=$video&AudioCodec=$audio" +
            (hevcMaxBitDepth?.let { "&hevc-videobitdepth=$it" } ?: "")

    companion object {
        val DEFAULT = HlsCodecs(video = "h264,hevc", audio = "aac,ac3,eac3,mp3")
    }
}
