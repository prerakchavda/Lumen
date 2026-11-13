package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.TrackOption
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinDeviceProfileDto
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
    suspend fun preparePlayback(userId: String, itemId: String): Result<PlaybackSessionData> = runCatching {
        coroutineScope {
            val itemDeferred = async { jellyfinApi.getItemDetails(userId, itemId) }
            val playbackDeferred = async {
                jellyfinApi.getPlaybackInfo(
                    itemId = itemId,
                    userId = userId,
                    body = JellyfinPlaybackInfoRequest(
                        userId = userId,
                        deviceProfile = JellyfinDeviceProfileDto()
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

            val (streamUrl, isHls) = if (source.supportsDirectPlay || source.supportsDirectStream) {
                val url = "$base/Videos/${item.id}/stream?Static=true&MediaSourceId=${source.id}&api_key=$token"
                url to false
            } else if (!source.transcodingUrl.isNullOrBlank()) {
                val path = source.transcodingUrl
                val url = if (path.startsWith("http")) path else "$base$path"
                url to true
            } else {
                val url = "$base/Videos/${item.id}/master.m3u8?MediaSourceId=${source.id}&PlaySessionId=$playSessionId&api_key=$token"
                url to true
            }

            val introSeg = segments.firstOrNull { it.type.equals("Intro", ignoreCase = true) }
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
                creditsStartTicks = creditsSeg?.startTicks,
                nextEpisodeId = nextEpisodeId,
                nextEpisodeTitle = nextEpisodeTitle,
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
}
