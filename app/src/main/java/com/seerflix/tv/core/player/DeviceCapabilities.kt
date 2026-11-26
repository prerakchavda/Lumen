package com.seerflix.tv.core.player

import android.content.Context
import android.hardware.display.DisplayManager
import android.media.MediaCodecInfo
import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaCodecList
import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this TV box (and the screen / receiver it is plugged into) can actually play.
 *
 * The device profile sent to Jellyfin is built from this, so the server direct-plays only what
 * will work and remuxes, converts audio or tone-maps everything else — instead of the box being
 * told to play HDR on an SDR panel, DTS into a TV that can't take it, or AV1 without a decoder.
 */
data class DeviceMediaCapabilities(
    val hevc: Boolean,
    val hevcMain10: Boolean,
    val vp9: Boolean,
    val vp9Profile2: Boolean,
    val av1: Boolean,
    val av1Main10: Boolean,
    val h264High10: Boolean,
    /** Largest frame per Jellyfin codec name ("h264", "hevc", "vp9", "av1"); missing = no limit sent. */
    val maxResolution: Map<String, Pair<Int, Int>>,
    val dolbyVisionDecoder: Boolean,
    val dolbyVisionProfile5: Boolean,
    val dolbyVisionProfile7: Boolean,
    val displayHdr10: Boolean,
    val displayHdr10Plus: Boolean,
    val displayHlg: Boolean,
    val displayDolbyVision: Boolean,
    val ac3Decoder: Boolean,
    val eac3Decoder: Boolean,
    val dtsDecoder: Boolean,
    val trueHdDecoder: Boolean,
    val passthroughAc3: Boolean,
    val passthroughEac3: Boolean,
    val passthroughDts: Boolean,
    val passthroughTrueHd: Boolean,
    val softwareAudioDecoderAvailable: Boolean = false
) {
    /** One-line summary for logs and the "Playback compatibility" screen. */
    fun summary(): String = buildString {
        append("Video: H.264")
        if (hevc) append(if (hevcMain10) ", HEVC 10-bit" else ", HEVC 8-bit")
        if (vp9) append(", VP9")
        if (av1) append(if (av1Main10) ", AV1 10-bit" else ", AV1")
        maxResolution["hevc"]?.let { (w, h) -> append(" (HEVC up to ${w}x$h)") }
        append(". Display: ")
        val hdr = listOfNotNull(
            "Dolby Vision".takeIf { displayDolbyVision && dolbyVisionDecoder },
            "HDR10".takeIf { displayHdr10 },
            "HDR10+".takeIf { displayHdr10Plus },
            "HLG".takeIf { displayHlg }
        )
        append(if (hdr.isEmpty()) "SDR only" else hdr.joinToString(", "))
        append(". Audio passthrough: ")
        val pt = listOfNotNull(
            "AC3".takeIf { passthroughAc3 },
            "E-AC3".takeIf { passthroughEac3 },
            "DTS".takeIf { passthroughDts },
            "TrueHD".takeIf { passthroughTrueHd }
        )
        append(if (pt.isEmpty()) "none" else pt.joinToString(", "))
        append(". Audio decoders: AAC, MP3, Opus, FLAC")
        if (ac3Decoder) append(", AC3")
        if (eac3Decoder) append(", E-AC3")
        if (dtsDecoder) append(", DTS")
        if (trueHdDecoder) append(", TrueHD")
        if (softwareAudioDecoderAvailable) append(" (+ FFmpeg PCM)")
        append('.')
    }

    companion object {
        /**
         * Everything allowed — the pre-0.7 behaviour. Used when the user turns capability matching
         * off (the box or TV misreports what it supports) and as the default in unit tests.
         */
        val PERMISSIVE = DeviceMediaCapabilities(
            hevc = true, hevcMain10 = true, vp9 = true, vp9Profile2 = true, av1 = true, av1Main10 = true,
            h264High10 = true, maxResolution = emptyMap(),
            dolbyVisionDecoder = true, dolbyVisionProfile5 = true, dolbyVisionProfile7 = true,
            displayHdr10 = true, displayHdr10Plus = true, displayHlg = true, displayDolbyVision = true,
            ac3Decoder = true, eac3Decoder = true, dtsDecoder = true, trueHdDecoder = true,
            passthroughAc3 = true, passthroughEac3 = true, passthroughDts = true, passthroughTrueHd = true
        )

        /** A typical 1080p SDR stick with no passthrough: H.264/HEVC 8-bit only, stereo PCM out. */
        val BASELINE = DeviceMediaCapabilities(
            hevc = true, hevcMain10 = false, vp9 = false, vp9Profile2 = false, av1 = false, av1Main10 = false,
            h264High10 = false, maxResolution = mapOf("h264" to (1920 to 1080), "hevc" to (1920 to 1080)),
            dolbyVisionDecoder = false, dolbyVisionProfile5 = false, dolbyVisionProfile7 = false,
            displayHdr10 = false, displayHdr10Plus = false, displayHlg = false, displayDolbyVision = false,
            ac3Decoder = false, eac3Decoder = false, dtsDecoder = false, trueHdDecoder = false,
            passthroughAc3 = false, passthroughEac3 = false, passthroughDts = false, passthroughTrueHd = false
        )

        /**
         * Jellyfin VideoRangeType values this device can show correctly when played directly.
         * Anything else is tone-mapped / converted by the server.
         *
         * - DOVIWithSDR / DOVIWithHDR10 / DOVIWithHLG / DOVIWithHDR10Plus (profiles 8.x) carry a
         *   standard base layer, so a non-Dolby-Vision device plays that layer.
         * - DOVI (profile 5) has no fallback layer and turns purple/green without a DV decoder.
         * - DOVIWithEL (profile 7) needs a decoder that supports profile 7.
         */
        fun allowedVideoRanges(caps: DeviceMediaCapabilities): List<String> {
            val ranges = mutableListOf("SDR", "DOVIWithSDR")
            if (caps.displayHdr10 || caps.displayHdr10Plus) {
                ranges += listOf("HDR10", "HDR10Plus", "DOVIWithHDR10", "DOVIWithHDR10Plus")
            }
            if (caps.displayHlg) ranges += listOf("HLG", "DOVIWithHLG")
            val dolbyVision = caps.displayDolbyVision && caps.dolbyVisionDecoder
            if (dolbyVision && caps.dolbyVisionProfile5) ranges += "DOVI"
            if (dolbyVision && caps.dolbyVisionProfile7) ranges += listOf("DOVIWithEL", "DOVIWithELHDR10Plus")
            return ranges.distinct()
        }
    }
}

/** Supplies the capabilities used for each playback request (re-read so a new HDMI sink is noticed). */
interface DeviceCapabilityProvider {
    fun current(): DeviceMediaCapabilities

    object Permissive : DeviceCapabilityProvider {
        override fun current() = DeviceMediaCapabilities.PERMISSIVE
    }
}

@Singleton
class AndroidDeviceCapabilityProvider @Inject constructor(
    @ApplicationContext private val context: Context
) : DeviceCapabilityProvider {

    /** Decoder lists never change at runtime; the display and audio sink can (TV / AVR switched). */
    private val decoders: DecoderSupport by lazy { DecoderSupport.detect() }

    override fun current(): DeviceMediaCapabilities {
        val dec = decoders
        val hdr = runCatching { detectDisplayHdr() }.getOrDefault(DisplayHdr())
        val pt = runCatching { detectPassthrough() }.getOrDefault(Passthrough())
        return DeviceMediaCapabilities(
            hevc = dec.hevc,
            hevcMain10 = dec.hevcMain10,
            vp9 = dec.vp9,
            vp9Profile2 = dec.vp9Profile2,
            av1 = dec.av1,
            av1Main10 = dec.av1Main10,
            h264High10 = dec.h264High10,
            maxResolution = dec.maxResolution,
            dolbyVisionDecoder = dec.dolbyVision,
            dolbyVisionProfile5 = dec.dolbyVisionProfile5,
            dolbyVisionProfile7 = dec.dolbyVisionProfile7,
            displayHdr10 = hdr.hdr10,
            displayHdr10Plus = hdr.hdr10Plus,
            displayHlg = hdr.hlg,
            displayDolbyVision = hdr.dolbyVision,
            ac3Decoder = dec.ac3,
            eac3Decoder = dec.eac3,
            dtsDecoder = dec.dts,
            trueHdDecoder = dec.trueHd,
            passthroughAc3 = pt.ac3,
            passthroughEac3 = pt.eac3,
            passthroughDts = pt.dts,
            passthroughTrueHd = pt.trueHd,
            softwareAudioDecoderAvailable = FfmpegAudioSupport.isAvailable()
        ).also { Log.i(TAG, it.summary()) }
    }

    private data class DisplayHdr(
        val hdr10: Boolean = false,
        val hdr10Plus: Boolean = false,
        val hlg: Boolean = false,
        val dolbyVision: Boolean = false
    )

    private fun detectDisplayHdr(): DisplayHdr {
        val display = context.getSystemService(DisplayManager::class.java)
            ?.getDisplay(Display.DEFAULT_DISPLAY) ?: return DisplayHdr()
        val types = mutableSetOf<Int>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Per mode on 14+: the UI may sit in an SDR mode while a 4K HDR mode is available.
            display.supportedModes.forEach { mode -> mode.supportedHdrTypes.forEach { types += it } }
        }
        @Suppress("DEPRECATION")
        display.hdrCapabilities?.supportedHdrTypes?.forEach { types += it }
        return DisplayHdr(
            hdr10 = Display.HdrCapabilities.HDR_TYPE_HDR10 in types,
            hdr10Plus = Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS in types,
            hlg = Display.HdrCapabilities.HDR_TYPE_HLG in types,
            dolbyVision = Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION in types
        )
    }

    private data class Passthrough(
        val ac3: Boolean = false,
        val eac3: Boolean = false,
        val dts: Boolean = false,
        val trueHd: Boolean = false
    )

    @OptIn(UnstableApi::class)
    private fun detectPassthrough(): Passthrough {
        @Suppress("DEPRECATION")
        val caps = AudioCapabilities.getCapabilities(context)
        @Suppress("DEPRECATION")
        return Passthrough(
            ac3 = caps.supportsEncoding(C.ENCODING_AC3),
            eac3 = caps.supportsEncoding(C.ENCODING_E_AC3) || caps.supportsEncoding(C.ENCODING_E_AC3_JOC),
            dts = caps.supportsEncoding(C.ENCODING_DTS) || caps.supportsEncoding(C.ENCODING_DTS_HD),
            trueHd = caps.supportsEncoding(C.ENCODING_DOLBY_TRUEHD)
        )
    }

    private data class DecoderSupport(
        val hevc: Boolean,
        val hevcMain10: Boolean,
        val vp9: Boolean,
        val vp9Profile2: Boolean,
        val av1: Boolean,
        val av1Main10: Boolean,
        val h264High10: Boolean,
        val maxResolution: Map<String, Pair<Int, Int>>,
        val dolbyVision: Boolean,
        val dolbyVisionProfile5: Boolean,
        val dolbyVisionProfile7: Boolean,
        val ac3: Boolean,
        val eac3: Boolean,
        val dts: Boolean,
        val trueHd: Boolean
    ) {
        companion object {
            fun detect(): DecoderSupport {
                val infos = runCatching {
                    MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { !it.isEncoder }
                }.getOrDefault(emptyList())

                fun decodersFor(mime: String) = infos.filter { info ->
                    info.supportedTypes.any { it.equals(mime, ignoreCase = true) }
                }

                fun profiles(mime: String): Set<Int> = decodersFor(mime).flatMapTo(HashSet()) { info ->
                    runCatching { info.getCapabilitiesForType(mime).profileLevels.map { it.profile } }
                        .getOrDefault(emptyList())
                }

                fun sizeOf(info: MediaCodecInfo, mime: String): Pair<Int, Int>? = runCatching {
                    val vc: MediaCodecInfo.VideoCapabilities =
                        info.getCapabilitiesForType(mime).videoCapabilities ?: return@runCatching null
                    vc.supportedWidths.upper to vc.supportedHeights.upper
                }.getOrNull()

                // Software decoders (c2.android.*, OMX.google.*) advertise 4K but can't keep up with it
                // on a TV stick's CPU: with only a software decoder, cap direct play so the server
                // sends something the CPU can decode in real time.
                fun maxSize(mime: String, softwareCap: Pair<Int, Int>): Pair<Int, Int> {
                    val decoders = decodersFor(mime)
                    val hardware = decoders.filter { isHardware(it) }
                    val best = (hardware.ifEmpty { decoders }).mapNotNull { sizeOf(it, mime) }
                        .maxByOrNull { it.first.toLong() * it.second } ?: return 0 to 0
                    return if (hardware.isEmpty()) {
                        minOf(best.first, softwareCap.first) to minOf(best.second, softwareCap.second)
                    } else best
                }

                val hevcProfiles = profiles("video/hevc")
                val vp9Profiles = profiles("video/x-vnd.on2.vp9")
                val av1Profiles = profiles("video/av01")
                val avcProfiles = profiles("video/avc")
                val dvProfiles = profiles("video/dolby-vision")
                val maxResolution = buildMap {
                    listOf(
                        Triple("h264", "video/avc", 1920 to 1088),
                        Triple("hevc", "video/hevc", 1920 to 1088),
                        Triple("vp9", "video/x-vnd.on2.vp9", 1920 to 1088),
                        Triple("av1", "video/av01", 1280 to 720)
                    ).forEach { (codec, mime, softwareCap) ->
                        val size = maxSize(mime, softwareCap)
                        if (size.first > 0 && size.second > 0) put(codec, size)
                    }
                }
                return DecoderSupport(
                    hevc = decodersFor("video/hevc").isNotEmpty(),
                    hevcMain10 = CodecProfileLevel.HEVCProfileMain10 in hevcProfiles ||
                        CodecProfileLevel.HEVCProfileMain10HDR10 in hevcProfiles ||
                        CodecProfileLevel.HEVCProfileMain10HDR10Plus in hevcProfiles,
                    vp9 = decodersFor("video/x-vnd.on2.vp9").isNotEmpty(),
                    vp9Profile2 = CodecProfileLevel.VP9Profile2 in vp9Profiles ||
                        CodecProfileLevel.VP9Profile2HDR in vp9Profiles,
                    av1 = decodersFor("video/av01").isNotEmpty(),
                    av1Main10 = CodecProfileLevel.AV1ProfileMain10 in av1Profiles ||
                        CodecProfileLevel.AV1ProfileMain10HDR10 in av1Profiles,
                    h264High10 = CodecProfileLevel.AVCProfileHigh10 in avcProfiles,
                    maxResolution = maxResolution,
                    dolbyVision = decodersFor("video/dolby-vision").isNotEmpty(),
                    dolbyVisionProfile5 = CodecProfileLevel.DolbyVisionProfileDvheStn in dvProfiles,
                    dolbyVisionProfile7 = CodecProfileLevel.DolbyVisionProfileDvheDtb in dvProfiles,
                    ac3 = decodersFor("audio/ac3").isNotEmpty(),
                    eac3 = decodersFor("audio/eac3").isNotEmpty() || decodersFor("audio/eac3-joc").isNotEmpty(),
                    dts = decodersFor("audio/vnd.dts").isNotEmpty() || decodersFor("audio/vnd.dts.hd").isNotEmpty(),
                    trueHd = decodersFor("audio/true-hd").isNotEmpty()
                )
            }
        }
    }

    private companion object {
        const val TAG = "LumenCaps"

        fun isHardware(info: MediaCodecInfo): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                info.isHardwareAccelerated
            } else {
                val name = info.name.lowercase()
                !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw."))
            }
    }
}
