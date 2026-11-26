package com.seerflix.tv.core.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.seerflix.tv.core.model.PlayMethod
import com.seerflix.tv.core.model.QualityPreset
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinPlaybackInfoRequest
import com.seerflix.tv.core.network.JellyfinPlaybackProgressRequest
import com.seerflix.tv.core.network.NetworkModule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Phase1PlaybackParityTest {

    @After
    fun tearDown() {
        FfmpegAudioSupport.availabilityOverrideForTesting = null
    }

    @Test
    fun `buildStreamUrl passes AudioStreamIndex on HLS, transcodingUrl, and burn-in streams`() {
        val transcodeSource = JellyfinMediaSourceDto(
            id = "src-1",
            supportsDirectPlay = false,
            supportsDirectStream = false,
            transcodingUrl = "/videos/item-1/master.m3u8?PlaySessionId=ps1&TranscodeReasons=AudioCodecNotSupported"
        )

        val (transcodeUrl, isHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "https://jf.example",
            token = "tok",
            itemId = "item-1",
            source = transcodeSource,
            playSessionId = "ps1",
            maxBitrateBps = null,
            audioStreamIndex = 3
        )
        assertTrue(isHls)
        assertTrue(transcodeUrl.contains("AudioStreamIndex=3"))
        assertTrue(transcodeUrl.contains("api_key=tok"))

        val (burnInUrl, burnInHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "https://jf.example",
            token = "tok",
            itemId = "item-1",
            source = transcodeSource,
            playSessionId = "ps1",
            maxBitrateBps = 8_000_000L,
            burnInSubtitleIndex = 7,
            audioStreamIndex = 2
        )
        assertTrue(burnInHls)
        assertTrue(burnInUrl.contains("SubtitleStreamIndex=7"))
        assertTrue(burnInUrl.contains("SubtitleMethod=Encode"))
        assertTrue(burnInUrl.contains("AudioStreamIndex=2"))
        assertTrue(burnInUrl.contains("MaxStreamingBitrate=8000000"))

        val (forcedHlsUrl, forcedIsHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "https://jf.example",
            token = "tok",
            itemId = "item-1",
            source = JellyfinMediaSourceDto(id = "src-2", supportsDirectPlay = true),
            playSessionId = "ps2",
            maxBitrateBps = null,
            audioStreamIndex = 4,
            forceHls = true
        )
        assertTrue(forcedIsHls)
        assertTrue(forcedHlsUrl.contains("AudioStreamIndex=4"))
    }

    @Test
    fun `buildDeviceProfile includes PGS and VobSub burn-in Encode when requested and Embed otherwise`() {
        val embedProfile = JellyfinPlaybackManager.buildDeviceProfile(burnInSubtitleIndex = null)
        val pgsEmbed = embedProfile.subtitleProfiles.firstOrNull { it.format == "pgs" }
        val dvdEmbed = embedProfile.subtitleProfiles.firstOrNull { it.format == "dvdsub" }
        assertEquals("Embed", pgsEmbed?.method)
        assertEquals("Embed", dvdEmbed?.method)

        val encodeProfile = JellyfinPlaybackManager.buildDeviceProfile(burnInSubtitleIndex = 5)
        val pgsEncode = encodeProfile.subtitleProfiles.firstOrNull { it.format == "pgs" }
        val dvdEncode = encodeProfile.subtitleProfiles.firstOrNull { it.format == "dvdsub" }
        val dvbEncode = encodeProfile.subtitleProfiles.firstOrNull { it.format == "dvbsub" }
        assertEquals("Encode", pgsEncode?.method)
        assertEquals("Encode", dvdEncode?.method)
        assertEquals("Encode", dvbEncode?.method)
    }

    @Test
    fun `software audio decoding enables surround codecs in DeviceProfile unless stereoDownmix is active`() {
        FfmpegAudioSupport.availabilityOverrideForTesting = true
        val baselineNoPassthrough = DeviceMediaCapabilities.BASELINE.copy(
            softwareAudioDecoderAvailable = true
        )

        val swEnabled = JellyfinPlaybackManager.buildDeviceProfile(
            audioPassthroughEnabled = false,
            stereoDownmixEnabled = false,
            capabilities = baselineNoPassthrough,
            softwareAudioDecodingEnabled = true
        )
        val directAudio = swEnabled.directPlayProfiles.first().audioCodec.split(",")
        assertTrue("ac3" in directAudio)
        assertTrue("eac3" in directAudio)
        assertTrue("dts" in directAudio)
        assertTrue("truehd" in directAudio)

        val swDisabled = JellyfinPlaybackManager.buildDeviceProfile(
            audioPassthroughEnabled = false,
            stereoDownmixEnabled = false,
            capabilities = baselineNoPassthrough,
            softwareAudioDecodingEnabled = false
        )
        val disabledAudio = swDisabled.directPlayProfiles.first().audioCodec.split(",")
        assertFalse("ac3" in disabledAudio)
        assertFalse("dts" in disabledAudio)
        assertFalse("truehd" in disabledAudio)

        val downmixProfile = JellyfinPlaybackManager.buildDeviceProfile(
            audioPassthroughEnabled = false,
            stereoDownmixEnabled = true,
            capabilities = baselineNoPassthrough,
            softwareAudioDecodingEnabled = true
        )
        val downmixAudio = downmixProfile.directPlayProfiles.first().audioCodec.split(",")
        assertFalse("dts" in downmixAudio)
        assertFalse("truehd" in downmixAudio)
    }

    @Test
    fun `resolvePlayMethodAndReasons distinguishes DirectPlay, DirectStream remux, and Transcode`() {
        val directSource = JellyfinMediaSourceDto(id = "s1", supportsDirectPlay = true, supportsDirectStream = true)
        val (dpMethod, dpReasons) = JellyfinPlaybackManager.resolvePlayMethodAndReasons(
            isHls = false,
            source = directSource,
            streamUrl = "https://jf.example/Videos/1/stream?Static=true"
        )
        assertEquals(PlayMethod.DIRECT_PLAY, dpMethod)
        assertTrue(dpReasons.isEmpty())

        val remuxSource = JellyfinMediaSourceDto(id = "s2", supportsDirectPlay = false, supportsDirectStream = true)
        val (dsMethod, dsReasons) = JellyfinPlaybackManager.resolvePlayMethodAndReasons(
            isHls = true,
            source = remuxSource,
            streamUrl = "https://jf.example/Videos/1/master.m3u8?TranscodeReasons=ContainerNotSupported"
        )
        assertEquals(PlayMethod.DIRECT_STREAM, dsMethod)
        assertEquals(listOf("ContainerNotSupported"), dsReasons)

        val transcodeSource = JellyfinMediaSourceDto(id = "s3", supportsDirectPlay = false, supportsDirectStream = false)
        val (tcMethod, tcReasons) = JellyfinPlaybackManager.resolvePlayMethodAndReasons(
            isHls = true,
            source = transcodeSource,
            streamUrl = "https://jf.example/Videos/1/master.m3u8?VideoBitrate=8000000&TranscodeReasons=VideoCodecNotSupported%2CAudioCodecNotSupported",
            burnInSubtitleIndex = 6
        )
        assertEquals(PlayMethod.TRANSCODE, tcMethod)
        assertTrue("VideoCodecNotSupported" in tcReasons)
        assertTrue("AudioCodecNotSupported" in tcReasons)
        assertTrue("SubtitleCodecNotSupported" in tcReasons)
    }

    @Test
    fun `PlaybackProgressRequest and PlaybackInfoRequest serialize Phase 1 session fields`() {
        val json = NetworkModule.provideJson()
        val progress = JellyfinPlaybackProgressRequest(
            itemId = "item-42",
            mediaSourceId = "src-1",
            playSessionId = "ps-1",
            positionTicks = 120_000_000L,
            isPaused = false,
            playMethod = "DirectPlay",
            audioStreamIndex = 1,
            subtitleStreamIndex = 3,
            isMuted = false,
            volumeLevel = 80,
            playbackRate = 1.25f
        )
        val encodedProgress = json.encodeToString(JellyfinPlaybackProgressRequest.serializer(), progress)
        assertTrue(encodedProgress.contains("\"PlayMethod\":\"DirectPlay\""))
        assertTrue(encodedProgress.contains("\"AudioStreamIndex\":1"))
        assertTrue(encodedProgress.contains("\"SubtitleStreamIndex\":3"))
        assertTrue(encodedProgress.contains("\"IsMuted\":false"))
        assertTrue(encodedProgress.contains("\"VolumeLevel\":80"))
        assertTrue(encodedProgress.contains("\"PlaybackRate\":1.25"))

        val infoReq = JellyfinPlaybackInfoRequest(
            userId = "user-1",
            audioStreamIndex = 2,
            subtitleStreamIndex = 5,
            deviceProfile = JellyfinPlaybackManager.buildDeviceProfile()
        )
        val encodedInfo = json.encodeToString(JellyfinPlaybackInfoRequest.serializer(), infoReq)
        assertTrue(encodedInfo.contains("\"AudioStreamIndex\":2"))
        assertTrue(encodedInfo.contains("\"SubtitleStreamIndex\":5"))
    }

    @Test
    fun `QualityPreset includes lower remote bitrate tiers`() {
        assertEquals(8_000_000L, QualityPreset.HD_1080P_8M.maxBitrateBps)
        assertEquals(4_000_000L, QualityPreset.HD_720P.maxBitrateBps)
        assertEquals(1_500_000L, QualityPreset.SD_480P.maxBitrateBps)
    }

    @Test
    fun `AudioDelayProcessor inserts silence on positive delay and drops frames on negative delay`() {
        val processor = AudioDelayProcessor()
        // 1000 Hz, 1 channel, PCM 16-bit -> 2 bytes per frame, 1 frame per ms.
        val format = AudioProcessor.AudioFormat(1000, 1, C.ENCODING_PCM_16BIT)
        processor.configure(format)
        processor.flush()

        // +50ms delay -> inserts 50 silent frames (100 bytes) ahead of 10 input frames (20 bytes)
        processor.setDelayMs(50L)
        val inputPos = ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder())
        repeat(10) { inputPos.putShort((it + 1).toShort()) }
        inputPos.flip()
        processor.queueInput(inputPos)
        val outPos = processor.output
        assertEquals(120, outPos.remaining())
        // First 50 shorts should be 0 (silence), followed by 1..10
        repeat(50) { assertEquals(0.toShort(), outPos.short) }
        assertEquals(1.toShort(), outPos.short)

        // Flush resets applied offset; -5ms delay drops leading 5 frames (10 bytes) of 10 input frames
        processor.flush()
        processor.setDelayMs(-5L)
        val inputNeg = ByteBuffer.allocateDirect(20).order(ByteOrder.nativeOrder())
        repeat(10) { inputNeg.putShort((it + 1).toShort()) }
        inputNeg.flip()
        processor.queueInput(inputNeg)
        val outNeg = processor.output
        assertEquals(10, outNeg.remaining())
        assertEquals(6.toShort(), outNeg.short)
    }
}
