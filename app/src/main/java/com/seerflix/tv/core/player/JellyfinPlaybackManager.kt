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
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinPlaybackInfoRequest
import com.seerflix.tv.core.network.JellyfinPlaybackProgressRequest
import com.seerflix.tv.core.network.JellyfinSubtitleProfileDto
import com.seerflix.tv.core.network.JellyfinTranscodingProfileDto
import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinPlaybackManager @Inject constructor(
    private val jellyfinApi: JellyfinApi,
    private val prefs: SessionPreferences
) {
    suspend fun preparePlayback(
        userId: String,
        itemId: String,
        maxBitrateBps: Long? = null,
        mediaSourceId: String? = null,
        audioPassthroughEnabled: Boolean = true,
        stereoDownmixEnabled: Boolean = false,
        assSubtitleMode: AssSubtitleMode = AssSubtitleMode.STYLED,
        burnInSubtitleIndex: Int? = null
    ): Result<PlaybackSessionData> = runCatching {
        coroutineScope {
            val deviceProfile = buildDeviceProfile(
                maxBitrateBps = maxBitrateBps,
                audioPassthroughEnabled = audioPassthroughEnabled,
                stereoDownmixEnabled = stereoDownmixEnabled,
                assSubtitleMode = assSubtitleMode,
                burnInSubtitleIndex = burnInSubtitleIndex
            )
            var item = jellyfinApi.getItemDetails(userId, itemId)
            if (item.type.equals("Series", ignoreCase = true) || item.type.equals("Season", ignoreCase = true)) {
                val targetSeriesId = if (item.type.equals("Season", ignoreCase = true)) {
                    item.seriesId ?: item.id
                } else {
                    item.id
                }
                val targetSeasonId = if (item.type.equals("Season", ignoreCase = true)) item.id else null
                val candidateEpisode = if (targetSeasonId == null) {
                    runCatching {
                        jellyfinApi.getResumeItems(userId, limit = 10).items.firstOrNull { it.seriesId == targetSeriesId }
                    }.getOrNull()
                } else {
                    null
                } ?: runCatching {
                    val sortedEpisodes = jellyfinApi.getEpisodes(
                        seriesId = targetSeriesId,
                        userId = userId,
                        seasonId = targetSeasonId
                    ).items
                        .filter { (it.parentIndexNumber ?: 1) > 0 }
                        .sortedWith(compareBy({ it.parentIndexNumber ?: 1 }, { it.indexNumber ?: 1 }))
                    sortedEpisodes.firstOrNull { (it.userData?.playbackPositionTicks ?: 0L) > 0L }
                        ?: sortedEpisodes.firstOrNull { it.userData?.played != true }
                        ?: sortedEpisodes.firstOrNull()
                }.getOrNull() ?: error("No playable episodes found for ${item.name}")

                item = runCatching {
                    jellyfinApi.getItemDetails(userId, candidateEpisode.id)
                }.getOrDefault(candidateEpisode)
            }

            val resolvedItemId = item.id
            val playbackDeferred = async {
                runCatching {
                    jellyfinApi.getPlaybackInfo(
                        itemId = resolvedItemId,
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
            val segmentsDeferred = async {
                runCatching { jellyfinApi.getMediaSegments(resolvedItemId).items }.getOrDefault(emptyList())
            }

            val playback = playbackDeferred.await()
            val segments = segmentsDeferred.await()
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

            val introSeg = segments.firstOrNull { it.type.equals("Intro", ignoreCase = true) }
            val recapSeg = segments.firstOrNull { it.type.equals("Recap", ignoreCase = true) }
            val creditsSeg = segments.firstOrNull {
                it.type.equals("Outro", ignoreCase = true) || it.type.equals("Credits", ignoreCase = true)
            }

            var nextEpisodeId: String? = null
            var nextEpisodeTitle: String? = null
            if (item.type.equals("Episode", ignoreCase = true) && !item.seriesId.isNullOrBlank()) {
                val seasonEps = runCatching {
                    jellyfinApi.getEpisodes(
                        seriesId = item.seriesId,
                        userId = userId,
                        seasonId = item.seasonId
                    ).items.sortedBy { it.indexNumber ?: 0 }
                }.getOrDefault(emptyList())
                val currentIdx = seasonEps.indexOfFirst { it.id == item.id }
                if (currentIdx >= 0 && currentIdx + 1 < seasonEps.size) {
                    val nextEp = seasonEps[currentIdx + 1]
                    nextEpisodeId = nextEp.id
                    nextEpisodeTitle = "S${nextEp.parentIndexNumber ?: 1}:E${nextEp.indexNumber ?: 0} • ${nextEp.name}"
                }
            }

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
                    } ?: "$base/Videos/${item.id}/${source.id}/Subtitles/${stream.index}/0/Stream.$ext?api_key=$token"
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

            val trickplay = extractTrickplayInfo(item, source.id, base, token)

            val title = if (item.type.equals("Episode", ignoreCase = true) && !item.seriesName.isNullOrBlank()) {
                item.seriesName
            } else item.name

            val subtitle = if (item.type.equals("Episode", ignoreCase = true)) {
                "S${item.parentIndexNumber ?: 1}:E${item.indexNumber ?: 0} • ${item.name}"
            } else item.productionYear?.toString()

            val directPlayUrl = "$base/Videos/${item.id}/stream?Static=true&MediaSourceId=${source.id}&api_key=$token"

            PlaybackSessionData(
                itemId = item.id,
                title = title,
                subtitle = subtitle,
                streamUrl = streamUrl,
                isHls = isHls,
                mediaSourceId = source.id,
                playSessionId = playSessionId,
                resumeTicks = item.userData?.playbackPositionTicks ?: 0L,
                runtimeTicks = source.runTimeTicks ?: item.runTimeTicks ?: 0L,
                introStartTicks = introSeg?.startTicks,
                introEndTicks = introSeg?.endTicks,
                recapStartTicks = recapSeg?.startTicks,
                recapEndTicks = recapSeg?.endTicks,
                creditsStartTicks = creditsSeg?.startTicks,
                nextEpisodeId = nextEpisodeId,
                nextEpisodeTitle = nextEpisodeTitle,
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
        }
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
    ): Result<List<com.seerflix.tv.core.model.RemoteSubtitleOption>> = runCatching {
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
    ): Result<Unit> = runCatching {
        jellyfinApi.downloadRemoteSubtitle(itemId = itemId, subtitleId = subtitleId)
    }

    suspend fun reportStart(session: PlaybackSessionData, positionTicks: Long) {
        runCatching {
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
        runCatching {
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

    suspend fun reportStopped(session: PlaybackSessionData, positionTicks: Long) {
        runCatching {
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
            return if (source.supportsDirectPlay || source.supportsDirectStream) {
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
