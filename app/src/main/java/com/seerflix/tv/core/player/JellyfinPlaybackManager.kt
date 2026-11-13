package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.TrackOption
import com.seerflix.tv.core.model.TrickplayInfo
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinDeviceProfileDto
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinPlaybackInfoRequest
import com.seerflix.tv.core.network.JellyfinPlaybackProgressRequest
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
        maxBitrateBps: Long? = null
    ): Result<PlaybackSessionData> = runCatching {
        coroutineScope {
            val itemDeferred = async { jellyfinApi.getItemDetails(userId, itemId) }
            val playbackDeferred = async {
                jellyfinApi.getPlaybackInfo(
                    itemId = itemId,
                    userId = userId,
                    body = JellyfinPlaybackInfoRequest(
                        userId = userId,
                        deviceProfile = JellyfinDeviceProfileDto(
                            maxStreamingBitrate = maxBitrateBps ?: 120_000_000L
                        )
                    )
                )
            }
            val segmentsDeferred = async {
                runCatching { jellyfinApi.getMediaSegments(itemId).items }.getOrDefault(emptyList())
            }

            val item = itemDeferred.await()
            val playback = playbackDeferred.await()
            val segments = segmentsDeferred.await()

            val source = playback.mediaSources.firstOrNull()
                ?: error("No playable media sources returned by Jellyfin")
            val playSessionId = playback.playSessionId ?: "${System.currentTimeMillis()}"
            val base = prefs.cachedJellyfinUrl
            val token = prefs.cachedToken.orEmpty()

            val (streamUrl, isHls) = buildStreamUrl(
                base = base,
                token = token,
                itemId = item.id,
                source = source,
                playSessionId = playSessionId,
                maxBitrateBps = maxBitrateBps
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

            val subtitleTracks = source.mediaStreams
                .filter { it.type.equals("Subtitle", ignoreCase = true) }
                .map {
                    TrackOption(
                        index = it.index,
                        label = it.displayTitle ?: it.language?.uppercase() ?: "Subtitle ${it.index}",
                        language = it.language,
                        codec = it.codec
                    )
                }

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
                subtitleTracks = subtitleTracks
            )
        }
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
        fun buildStreamUrl(
            base: String,
            token: String,
            itemId: String,
            source: JellyfinMediaSourceDto,
            playSessionId: String,
            maxBitrateBps: Long?
        ): Pair<String, Boolean> {
            if (maxBitrateBps != null) {
                val url = "$base/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId&MaxStreamingBitrate=$maxBitrateBps&VideoCodec=h264,hevc&AudioCodec=aac,ac3,eac3,mp3&api_key=$token"
                return url to true
            }
            return if (source.supportsDirectPlay || source.supportsDirectStream) {
                val url = "$base/Videos/$itemId/stream?Static=true&MediaSourceId=${source.id}&api_key=$token"
                url to false
            } else if (!source.transcodingUrl.isNullOrBlank()) {
                val path = source.transcodingUrl
                val url = if (path.startsWith("http")) path else "$base$path"
                url to true
            } else {
                val url = "$base/Videos/$itemId/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId&api_key=$token"
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
