package com.seerflix.tv.core.player

/**
 * Runtime detection for the bundled Jellyfin Media3 FFmpeg audio decoder extension
 * (`org.jellyfin.media3:media3-ffmpeg-decoder`).
 *
 * Uses reflection so JVM unit tests (where Android native `.so` libraries cannot be loaded)
 * gracefully treat FFmpeg as unavailable unless explicitly overridden via
 * [availabilityOverrideForTesting].
 */
object FfmpegAudioSupport {
    /** Surround / lossless codecs the bundled FFmpeg audio extension can decode locally to PCM. */
    val SOFTWARE_SURROUND_CODECS: List<String> = listOf("ac3", "eac3", "dts", "dca", "truehd")

    /** Full set of audio codecs handled locally when software audio decoding is active. */
    val SOFTWARE_DECODABLE_CODECS: Set<String> = setOf(
        "aac", "mp3", "mp2", "opus", "flac", "vorbis", "ac3", "eac3", "dts", "dca", "truehd", "alac"
    )

    @Volatile
    internal var availabilityOverrideForTesting: Boolean? = null

    private val detectedAvailable: Boolean by lazy {
        runCatching {
            val clazz = Class.forName("androidx.media3.decoder.ffmpeg.FfmpegLibrary")
            val method = clazz.getMethod("isAvailable")
            method.invoke(null) as? Boolean == true
        }.getOrDefault(false)
    }

    fun isAvailable(): Boolean = availabilityOverrideForTesting ?: detectedAvailable
}
