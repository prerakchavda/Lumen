package com.seerflix.tv.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.exoplayer.upstream.DefaultAllocator

/**
 * ExoPlayer tuning for low-RAM Google TV sticks (e.g. 2 GB Amlogic devices) playing anything from
 * 720p transcodes to 4K remuxes.
 */
@OptIn(UnstableApi::class)
object PlayerTuning {
    /** Start as soon as ~1.25 s is buffered (ExoPlayer default: 2.5 s). */
    const val BUFFER_FOR_PLAYBACK_MS = 1_250

    /** After a stall, wait for a bit more so we don't immediately stall again (default: 5 s). */
    const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 2_500

    /** Keep loading until this much is buffered (unless the byte budget is hit first). */
    const val MIN_BUFFER_MS = 20_000

    /** Stop loading above this; resume below [MIN_BUFFER_MS]. */
    const val MAX_BUFFER_MS = 45_000

    const val MIN_TARGET_BUFFER_BYTES = 32 * 1024 * 1024
    const val MAX_TARGET_BUFFER_BYTES = 96 * 1024 * 1024

    /**
     * Seek parameters for relative seeks: land on a sync sample within ±3 s of the target if there
     * is one (cheap: no decoding up to an exact frame), otherwise seek exactly. The bounded window
     * guarantees a ±10 s press always moves at least 7 s in the right direction (plain
     * CLOSEST_SYNC can snap back to the start position with long GOPs).
     */
    val FAST_SEEK_PARAMETERS: SeekParameters = SeekParameters(3_000_000L, 3_000_000L)

    /** Share of the app's Java heap limit the media buffer may use (ExoPlayer buffers live on the heap). */
    private const val HEAP_FRACTION = 0.35

    /**
     * ExoPlayer's default video target is ~144 MB of Java-heap byte arrays (2000 × 64 KB for video
     * plus audio), which a 4K remux reaches within seconds and which doesn't fit next to Compose and
     * the image cache on a stick whose heap limit is ~192–256 MB. Budget a fraction of the actual
     * heap limit instead.
     */
    fun targetBufferBytes(maxHeapBytes: Long): Int =
        (maxHeapBytes * HEAP_FRACTION).toLong()
            .coerceIn(MIN_TARGET_BUFFER_BYTES.toLong(), MAX_TARGET_BUFFER_BYTES.toLong())
            .toInt()

    fun buildLoadControl(maxHeapBytes: Long = Runtime.getRuntime().maxMemory()): LoadControl =
        DefaultLoadControl.Builder()
            .setAllocator(DefaultAllocator(/* trimOnReset = */ true, C.DEFAULT_BUFFER_SEGMENT_SIZE))
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .setTargetBufferBytes(targetBufferBytes(maxHeapBytes))
            // The byte budget wins over the time thresholds, so a high-bitrate remux can't OOM.
            .setPrioritizeTimeOverSizeThresholds(false)
            .build()

    /**
     * Decoder fallback lets ExoPlayer try the next decoder (e.g. a software one) if the primary
     * hardware decoder fails to initialise, instead of failing playback. When software audio
     * decoding is enabled and the bundled Jellyfin FFmpeg audio decoder is available, extension
     * mode is ON so hardware/passthrough codecs stay preferred while FFmpeg handles AC3, E-AC3,
     * DTS, TrueHD, Vorbis, FLAC and Opus locally to PCM. Tunneling stays off (default): it is
     * flaky on Amlogic and breaks subtitles/frame stepping.
     */
    fun buildRenderersFactory(
        context: Context,
        enableSoftwareAudioDecoding: Boolean = true,
        audioDelayProcessor: AudioDelayProcessor? = null,
        /**
         * False: ignore the HDMI sink's encoded-audio capabilities so AC3 / E-AC3 / DTS are decoded
         * to PCM locally (which is also what lets the PCM audio-delay processor act on them).
         * Changing it requires a new player: the audio sink is built with the renderers.
         */
        audioPassthroughEnabled: Boolean = true
    ): DefaultRenderersFactory {
        val extMode = if (enableSoftwareAudioDecoding && FfmpegAudioSupport.isAvailable()) {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
        } else {
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
        }
        return object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink {
                // A context-built sink always queries the HDMI capabilities (and ignores
                // setAudioCapabilities), so the PCM-only sink is built without a context.
                @Suppress("DEPRECATION")
                val builder = if (audioPassthroughEnabled) {
                    androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                } else {
                    androidx.media3.exoplayer.audio.DefaultAudioSink.Builder()
                        .setAudioCapabilities(androidx.media3.exoplayer.audio.AudioCapabilities.DEFAULT_AUDIO_CAPABILITIES)
                }
                builder
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                if (audioDelayProcessor != null) {
                    builder.setAudioProcessors(arrayOf(audioDelayProcessor))
                }
                return builder.build()
            }
        }
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(extMode)
    }
}
