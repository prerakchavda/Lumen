package com.seerflix.tv.core.player

import android.util.LruCache
import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NpRequest
import org.schabi.newpipe.extractor.downloader.Response as NpResponse
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedPreviewStream(
    val videoUrl: String,
    val audioUrl: String? = null,
    val isYouTube: Boolean = false
)

@Singleton
class YouTubeTrailerResolver @Inject constructor(
    private val prefs: SessionPreferences
) {
    private val cache = LruCache<String, ResolvedPreviewStream>(40)

    @Volatile
    private var initialized = false

    private val npOkHttp by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun ensureInit() {
        if (initialized) return
        synchronized(this) {
            if (!initialized) {
                NewPipe.init(OkHttpNewPipeDownloader(npOkHttp), Localization.DEFAULT)
                initialized = true
            }
        }
    }

    fun buildJellyfinTrailerUrl(localTrailerId: String): String? {
        val base = prefs.cachedJellyfinUrl.takeIf { it.isNotBlank() } ?: return null
        val token = prefs.cachedToken.orEmpty()
        return "$base/Videos/$localTrailerId/stream?Static=true&api_key=$token"
    }

    suspend fun resolvePreview(
        localTrailerId: String?,
        youtubeKey: String?
    ): ResolvedPreviewStream? {
        if (!localTrailerId.isNullOrBlank()) {
            buildJellyfinTrailerUrl(localTrailerId)?.let { url ->
                return ResolvedPreviewStream(videoUrl = url, isYouTube = false)
            }
        }
        if (youtubeKey.isNullOrBlank()) return null

        cache.get(youtubeKey)?.let { return it }

        return withContext(Dispatchers.IO) {
            withTimeoutOrNull(7_000L) {
                runCatching {
                    ensureInit()
                    val watchUrl = "https://www.youtube.com/watch?v=$youtubeKey"
                    val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

                    // Prefer 720p/1080p muxed MP4 stream for low-latency hero previews
                    val muxed = info.videoStreams
                        .filter { it.format?.mimeType?.contains("mp4", ignoreCase = true) == true }
                        .sortedByDescending { it.height }
                        .firstOrNull { it.height in 480..1080 }
                        ?: info.videoStreams.firstOrNull()

                    if (muxed != null && !muxed.content.isNullOrBlank()) {
                        ResolvedPreviewStream(
                            videoUrl = muxed.content,
                            isYouTube = true
                        ).also { cache.put(youtubeKey, it) }
                    } else {
                        // Adaptive video-only + audio fallback
                        val videoOnly = info.videoOnlyStreams
                            .filter { it.height in 480..1080 }
                            .maxByOrNull { it.height }
                        val audioOnly = info.audioStreams.maxByOrNull { it.averageBitrate }
                        if (videoOnly != null && !videoOnly.content.isNullOrBlank()) {
                            ResolvedPreviewStream(
                                videoUrl = videoOnly.content,
                                audioUrl = audioOnly?.content,
                                isYouTube = true
                            ).also { cache.put(youtubeKey, it) }
                        } else null
                    }
                }.getOrNull()
            }
        }
    }
}

private class OkHttpNewPipeDownloader(
    private val client: OkHttpClient
) : Downloader() {
    override fun execute(request: NpRequest): NpResponse {
        val builder = Request.Builder()
            .url(request.url())
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
            )

        request.headers().forEach { (key, values) ->
            values.forEach { value -> builder.addHeader(key, value) }
        }

        val dataToSend = request.dataToSend()
        when (request.httpMethod()) {
            "POST" -> builder.post((dataToSend ?: ByteArray(0)).toRequestBody())
            "HEAD" -> builder.head()
            else -> builder.get()
        }

        val resp = client.newCall(builder.build()).execute()
        val bodyStr = resp.body?.string().orEmpty()
        val latestUrl = resp.request.url.toString()
        val headersMap = resp.headers.toMultimap()
        return NpResponse(resp.code, resp.message, headersMap, bodyStr, latestUrl)
    }
}
