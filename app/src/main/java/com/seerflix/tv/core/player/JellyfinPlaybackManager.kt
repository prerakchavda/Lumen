package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.AssSubtitleMode
import com.seerflix.tv.core.model.ChapterMarker
import com.seerflix.tv.core.model.ExternalSubtitleFile
import com.seerflix.tv.core.model.MediaSourceOption
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.TrackOption
import com.seerflix.tv.core.model.TrickplayInfo
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinDeviceProfileDto
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

@Singleton
class JellyfinPlaybackManager @Inject constructor(
    private val jellyfinApi: JellyfinApi,
    private val prefs: SessionPreferences
) {
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
        burnInSubtitleIndex: Int? = null
    ): Result<PlaybackSessionData> = prepareInternal(
        extrasScope = null,
        userId = userId,
        itemId = itemId,
        maxBitrateBps = maxBitrateBps,
        mediaSourceId = mediaSourceId,
        audioPassthroughEnabled = audioPassthroughEnabled,
        stereoDownmixEnabled = stereoDownmixEnabled,
        assSubtitleMode = assSubtitleMode,
        burnInSubtitleIndex = burnInSubtitleIndex
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
        burnInSubtitleIndex: Int? = null
    ): Result<PreparedPlayback> = prepareInternal(
        extrasScope = extrasScope,
        userId = userId,
        itemId = itemId,
        maxBitrateBps = maxBitrateBps,
        mediaSourceId = mediaSourceId,
        audioPassthroughEnabled = audioPassthroughEnabled,
        stereoDownmixEnabled = stereoDownmixEnabled,
        assSubtitleMode = assSubtitleMode,
        burnInSubtitleIndex = burnInSubtitleIndex
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
        burnInSubtitleIndex: Int?
    ): Result<PreparedPlayback> = runCatchingCancellable {
        coroutineScope {
            val deviceProfile = buildDeviceProfile(
                maxBitrateBps = maxBitrateBps,
                audioPassthroughEnabled = audioPassthroughEnabled,
                stereoDownmixEnabled = stereoDownmixEnabled,
                assSubtitleMode = assSubtitleMode,
                burnInSubtitleIndex = burnInSubtitleIndex
            )
            fun playbackInfoAsync(id: String): Deferred<JellyfinPlaybackInfoResponse?> = async {
                runCatchingCancellable {
                    jellyfinApi.getPlaybackInfo(
                        itemId = id,
                        userId = userId,
                        body = JellyfinPlaybackInfoRequest(
                            userId = userId,
                            mediaSourceId = mediaSourceId,
                            subtitleStreamIndex = if (assSubtitleMode == AssSubtitleMode.BURN_IN) burnInSubtitleIndex else null,
                            maxAudioChannels = if (stereoDownmixEnabled) 2 else null,
                            deviceProfile = deviceProfile
                        )
                    )
                }.getOrNull()
            }

            // Almost every request is a direct movie/episode id, so PlaybackInfo (the slowest call)
            // is requested speculatively alongside the item details instead of after them. For a
            // Series/Season the speculative request is dropped once the episode is resolved.
            val speculativePlayback = playbackInfoAsync(itemId)
            val requested = jellyfinApi.getItemDetails(userId, itemId, fields = PLAYBACK_ITEM_FIELDS)
            val item: JellyfinItemDto
            val playback: JellyfinPlaybackInfoResponse?
            if (requested.type.equals("Series", ignoreCase = true) || requested.type.equals("Season", ignoreCase = true)) {
                speculativePlayback.cancel()
                val candidateEpisode = resolveEpisodeToPlay(userId, requested)
                // The episode id is known now: fetch its details and PlaybackInfo in parallel.
                val detailsDeferred = async {
                    runCatchingCancellable {
                        jellyfinApi.getItemDetails(userId, candidateEpisode.id, fields = PLAYBACK_ITEM_FIELDS)
                    }.getOrDefault(candidateEpisode)
                }
                val playbackDeferred = playbackInfoAsync(candidateEpisode.id)
                item = detailsDeferred.await()
                playback = playbackDeferred.await()
            } else {
                item = requested
                playback = speculativePlayback.await()
            }

            // Start the non-critical lookups now so they overlap with session building / player
            // preparation. They never fail (a failure completes them with null).
            val segmentsDeferred = extrasScope?.async {
                runCatchingCancellable { PlaybackSegments.from(jellyfinApi.getMediaSegments(item.id).items) }.getOrNull()
            }
            val nextEpisodeDeferred = extrasScope?.async {
                runCatchingCancellable { NextEpisodeLookup(findNextEpisode(userId, item)) }.getOrNull()
            }
            try {
                val (session, frameRate) = buildSession(
                    item = item,
                    playback = playback,
                    mediaSourceId = mediaSourceId,
                    maxBitrateBps = maxBitrateBps,
                    stereoDownmixEnabled = stereoDownmixEnabled,
                    assSubtitleMode = assSubtitleMode,
                    burnInSubtitleIndex = burnInSubtitleIndex
                )
                PreparedPlayback(session, frameRate, segmentsDeferred, nextEpisodeDeferred)
            } catch (t: Throwable) {
                segmentsDeferred?.cancel()
                nextEpisodeDeferred?.cancel()
                throw t
            }
        }
    }

    /** Picks the episode to play for a Series/Season: in-progress first, then first unwatched. */
    private suspend fun resolveEpisodeToPlay(userId: String, seriesOrSeason: JellyfinItemDto): JellyfinItemDto {
        val isSeason = seriesOrSeason.type.equals("Season", ignoreCase = true)
        val targetSeriesId = if (isSeason) seriesOrSeason.seriesId ?: seriesOrSeason.id else seriesOrSeason.id
        val targetSeasonId = if (isSeason) seriesOrSeason.id else null
        val resumeCandidate = if (targetSeasonId == null) {
            runCatchingCancellable {
                jellyfinApi.getResumeItemsLite(userId, parentId = targetSeriesId, limit = 10)
                    .items.firstOrNull { it.seriesId == targetSeriesId }
            }.getOrNull()
        } else {
            null
        }
        return resumeCandidate ?: runCatchingCancellable {
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
        }.getOrNull() ?: error("No playable episodes found for ${seriesOrSeason.name}")
    }

    /**
     * Finds the episode after [item]: the next one in the season, or for a season finale the
     * first episode of the next (non-special) season. Uses a small `StartItemId` page of the
     * series instead of downloading whole season / series episode lists.
     */
    private suspend fun findNextEpisode(userId: String, item: JellyfinItemDto): NextEpisodeInfo? {
        val seriesId = item.seriesId
        if (!item.type.equals("Episode", ignoreCase = true) || seriesId.isNullOrBlank()) return null
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
        item: JellyfinItemDto,
        playback: JellyfinPlaybackInfoResponse?,
        mediaSourceId: String?,
        maxBitrateBps: Long?,
        stereoDownmixEnabled: Boolean,
        assSubtitleMode: AssSubtitleMode,
        burnInSubtitleIndex: Int?
    ): Pair<PlaybackSessionData, Float?> {
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
        } ?: error("No playable media sources returned by Jellyfin")

        val playSessionId = playback?.playSessionId ?: "${System.currentTimeMillis()}"
        val base = prefs.cachedResolvedJellyfinUrl
            .ifBlank { prefs.cachedJellyfinLocalUrl }
            .ifBlank { prefs.cachedJellyfinUrl }
            .trim()
            .trimEnd('/')
        val token = prefs.cachedToken.orEmpty()

        val (streamUrl, isHls) = buildStreamUrl(
            base = base,
            token = token,
            itemId = item.id,
            source = source,
            playSessionId = playSessionId,
            maxBitrateBps = maxBitrateBps,
            stereoDownmix = stereoDownmixEnabled,
            burnInSubtitleIndex = if (assSubtitleMode == AssSubtitleMode.BURN_IN) burnInSubtitleIndex else null
        )

        val audioTracks = source.mediaStreams
            .filter { it.type.equals("Audio", ignoreCase = true) }
            .map {
                TrackOption(
                    index = it.index,
                    label = it.displayTitle ?: it.language?.uppercase() ?: "Audio ${it.index}",
                    language = it.language,
                    codec = it.codec
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
                    title = stream.displayTitle ?: stream.title ?: stream.language?.uppercase() ?: "Sub ${stream.index}",
                    language = stream.language,
                    codec = stream.codec,
                    url = delivery
                )
            }

        val subtitleTracks = source.mediaStreams
            .filter { it.type.equals("Subtitle", ignoreCase = true) }
            .map { stream ->
                val matchingExt = externalSubtitles.firstOrNull { it.index == stream.index }
                TrackOption(
                    index = stream.index,
                    label = stream.displayTitle ?: stream.language?.uppercase() ?: "Subtitle ${stream.index}",
                    language = stream.language,
                    codec = stream.codec,
                    isExternal = stream.isExternal,
                    externalUrl = matchingExt?.url
                )
            }

        val chapters = item.chapters
            .filter { !it.name.isNullOrBlank() || it.startPositionTicks > 0L }
            .mapIndexed { idx, ch ->
                ChapterMarker(
                    name = ch.name?.takeIf { it.isNotBlank() } ?: "Chapter ${idx + 1}",
                    startPositionMs = (ch.startPositionTicks / 10_000L).coerceAtLeast(0L)
                )
            }
            .sortedBy { it.startPositionMs }

        val mediaSourceOptions = availableSources.map { ms -> mapMediaSourceOption(ms) }

        val primaryVideo = source.mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
        val primaryAudio = source.mediaStreams.firstOrNull { it.type.equals("Audio", ignoreCase = true) }
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

        val trickplay = extractTrickplayInfo(item, source.id, base, token)

        val title = if (item.type.equals("Episode", ignoreCase = true) && !item.seriesName.isNullOrBlank()) {
            item.seriesName
        } else item.name

        val subtitle = if (item.type.equals("Episode", ignoreCase = true)) {
            "S${item.parentIndexNumber ?: 1}:E${item.indexNumber ?: 0} • ${item.name}"
        } else item.productionYear?.toString()

        val directPlayUrl = "$base/Videos/${item.id}/stream?Static=true&MediaSourceId=${source.id}&api_key=$token"

        val session = PlaybackSessionData(
            itemId = item.id,
            title = title,
            subtitle = subtitle,
            streamUrl = streamUrl,
            isHls = isHls,
            mediaSourceId = source.id,
            playSessionId = playSessionId,
            resumeTicks = item.userData?.playbackPositionTicks ?: 0L,
            runtimeTicks = source.runTimeTicks ?: item.runTimeTicks ?: 0L,
            trickplayInfo = trickplay,
            seriesId = item.seriesId,
            seasonId = item.seasonId,
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
            isLiveTv = item.type.equals("TvChannel", ignoreCase = true)
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

    suspend fun searchRemoteSubtitles(
        itemId: String,
        language: String = "eng"
    ): Result<List<com.seerflix.tv.core.model.RemoteSubtitleOption>> = runCatchingCancellable {
        jellyfinApi.searchRemoteSubtitles(itemId = itemId, language = language)
            .map { dto ->
                com.seerflix.tv.core.model.RemoteSubtitleOption(
                    id = dto.id,
                    name = dto.name ?: dto.comment ?: "Subtitle ${dto.id}",
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
        jellyfinApi.downloadRemoteSubtitle(itemId = itemId, subtitleId = subtitleId)
    }

    suspend fun reportStart(session: PlaybackSessionData, positionTicks: Long) {
        runCatchingCancellable {
            jellyfinApi.reportPlaybackStart(
                JellyfinPlaybackProgressRequest(
                    itemId = session.itemId,
                    mediaSourceId = session.mediaSourceId,
                    playSessionId = session.playSessionId,
                    positionTicks = positionTicks,
                    isPaused = false
                )
            )
        }
    }

    suspend fun reportProgress(session: PlaybackSessionData, positionTicks: Long, isPaused: Boolean) {
        runCatchingCancellable {
            jellyfinApi.reportPlaybackProgress(
                JellyfinPlaybackProgressRequest(
                    itemId = session.itemId,
                    mediaSourceId = session.mediaSourceId,
                    playSessionId = session.playSessionId,
                    positionTicks = positionTicks,
                    isPaused = isPaused
                )
            )
        }
    }

    /**
     * Sends the "stopped" report on a process-lifetime scope so it is not cancelled when the
     * caller's scope (e.g. a ViewModel being cleared on exit) goes away.
     */
    fun reportStoppedDetached(session: PlaybackSessionData, positionTicks: Long) {
        reportScope.launch { reportStopped(session, positionTicks) }
    }

    suspend fun reportStopped(session: PlaybackSessionData, positionTicks: Long) {
        runCatchingCancellable {
            jellyfinApi.reportPlaybackStopped(
                JellyfinPlaybackProgressRequest(
                    itemId = session.itemId,
                    mediaSourceId = session.mediaSourceId,
                    playSessionId = session.playSessionId,
                    positionTicks = positionTicks,
                    isPaused = false
                )
            )
        }
    }

    companion object {
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

        fun buildDeviceProfile(
            maxBitrateBps: Long? = null,
            audioPassthroughEnabled: Boolean = true,
            stereoDownmixEnabled: Boolean = false,
            assSubtitleMode: AssSubtitleMode = AssSubtitleMode.STYLED,
            burnInSubtitleIndex: Int? = null
        ): JellyfinDeviceProfileDto {
            val directAudioCodecs = when {
                stereoDownmixEnabled -> "aac,mp3,opus,flac,vorbis"
                audioPassthroughEnabled -> "aac,mp3,ac3,eac3,dts,truehd,opus,flac,vorbis"
                else -> "aac,mp3,ac3,opus,flac,vorbis"
            }
            val transcodeAudioCodecs = if (stereoDownmixEnabled) {
                "aac,mp3"
            } else if (audioPassthroughEnabled) {
                "aac,ac3,eac3,mp3"
            } else {
                "aac,mp3"
            }
            val assMethod = if (assSubtitleMode == AssSubtitleMode.BURN_IN && burnInSubtitleIndex != null) {
                "Encode"
            } else {
                "External"
            }
            return JellyfinDeviceProfileDto(
                maxStreamingBitrate = maxBitrateBps ?: 120_000_000L,
                directPlayProfiles = listOf(
                    JellyfinDirectPlayProfileDto(
                        type = "Video",
                        container = "mp4,m4v,mkv,webm,mov,ts",
                        videoCodec = "h264,hevc,vp9,av1",
                        audioCodec = directAudioCodecs
                    )
                ),
                transcodingProfiles = listOf(
                    JellyfinTranscodingProfileDto(
                        container = "ts",
                        type = "Video",
                        videoCodec = "h264,hevc",
                        audioCodec = transcodeAudioCodecs,
                        protocol = "hls"
                    )
                ),
                subtitleProfiles = listOf(
                    JellyfinSubtitleProfileDto("ass", assMethod),
                    JellyfinSubtitleProfileDto("ssa", assMethod),
                    JellyfinSubtitleProfileDto("srt", "External"),
                    JellyfinSubtitleProfileDto("vtt", "External"),
                    JellyfinSubtitleProfileDto("subrip", "External")
                )
            )
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
            burnInSubtitleIndex: Int? = null
        ): Pair<String, Boolean> {
            val cleanBase = base.trim().trimEnd('/')
            val audioParam = if (stereoDownmix) "&MaxAudioChannels=2" else ""
            if (burnInSubtitleIndex != null) {
                val url = "$cleanBase/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId&SubtitleStreamIndex=$burnInSubtitleIndex&SubtitleMethod=Encode$audioParam&VideoCodec=h264,hevc&AudioCodec=aac,ac3,eac3,mp3&api_key=$token"
                return url to true
            }
            if (maxBitrateBps != null || stereoDownmix) {
                val br = maxBitrateBps ?: 120_000_000L
                val url = "$cleanBase/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId&MaxStreamingBitrate=$br$audioParam&VideoCodec=h264,hevc&AudioCodec=aac,ac3,eac3,mp3&api_key=$token"
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
                val url = resolveRelativeJellyfinUrl(cleanBase, source.transcodingUrl, token)
                url to true
            } else {
                val url = "$cleanBase/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId&api_key=$token"
                url to true
            }
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
