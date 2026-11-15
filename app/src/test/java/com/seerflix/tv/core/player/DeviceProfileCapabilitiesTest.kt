package com.seerflix.tv.core.player

import com.seerflix.tv.core.network.JellyfinDeviceProfileDto
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinMediaStreamDto
import com.seerflix.tv.core.network.JellyfinPlaybackInfoResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceProfileCapabilitiesTest {

    private fun JellyfinDeviceProfileDto.rangesFor(codec: String): List<String> =
        codecProfiles.first { it.codec == codec }.conditions
            .first { it.property == "VideoRangeType" }.value.split('|')

    private fun JellyfinDeviceProfileDto.directAudio() = directPlayProfiles.first().audioCodec.split(',')
    private fun JellyfinDeviceProfileDto.directVideo() = directPlayProfiles.first().videoCodec.split(',')

    @Test
    fun `SDR display gets HDR tone-mapped by the server`() {
        val profile = JellyfinPlaybackManager.buildDeviceProfile(
            capabilities = DeviceMediaCapabilities.BASELINE.copy(hevcMain10 = true)
        )
        val hevc = profile.rangesFor("hevc")
        assertTrue("SDR" in hevc)
        assertTrue("DOVIWithSDR" in hevc)
        assertFalse("HDR10" in hevc)
        assertFalse("DOVIWithHDR10" in hevc)
        assertFalse("DOVI" in hevc)
    }

    @Test
    fun `HDR10 display plays HDR10 and Dolby Vision profile 8 base layer but not profile 5 or 7`() {
        val caps = DeviceMediaCapabilities.BASELINE.copy(hevcMain10 = true, displayHdr10 = true)
        val hevc = JellyfinPlaybackManager.buildDeviceProfile(capabilities = caps).rangesFor("hevc")
        assertTrue("HDR10" in hevc)
        assertTrue("HDR10Plus" in hevc)
        assertTrue("DOVIWithHDR10" in hevc)
        assertFalse("DOVI" in hevc)
        assertFalse("DOVIWithEL" in hevc)
        assertFalse("HLG" in hevc)
    }

    @Test
    fun `Dolby Vision needs both a DV display and a DV decoder`() {
        val displayOnly = DeviceMediaCapabilities.BASELINE.copy(
            hevcMain10 = true, displayHdr10 = true, displayDolbyVision = true
        )
        assertFalse("DOVI" in DeviceMediaCapabilities.allowedVideoRanges(displayOnly))

        val full = displayOnly.copy(dolbyVisionDecoder = true, dolbyVisionProfile5 = true)
        assertTrue("DOVI" in DeviceMediaCapabilities.allowedVideoRanges(full))
        assertFalse("DOVIWithEL" in DeviceMediaCapabilities.allowedVideoRanges(full))
    }

    @Test
    fun `8-bit HEVC decoder limits bit depth and refuses HDR`() {
        val profile = JellyfinPlaybackManager.buildDeviceProfile(
            capabilities = DeviceMediaCapabilities.BASELINE.copy(displayHdr10 = true)
        )
        val hevc = profile.codecProfiles.first { it.codec == "hevc" }
        assertEquals("8", hevc.conditions.first { it.property == "VideoBitDepth" }.value)
        assertFalse("HDR10" in profile.rangesFor("hevc"))
    }

    @Test
    fun `missing decoders are not offered for direct play or transcoding`() {
        val profile = JellyfinPlaybackManager.buildDeviceProfile(
            capabilities = DeviceMediaCapabilities.BASELINE.copy(hevc = false)
        )
        assertEquals(listOf("h264"), profile.directVideo())
        assertEquals("h264", profile.transcodingProfiles.first().videoCodec)
        assertTrue(profile.codecProfiles.none { it.codec == "hevc" || it.codec == "av1" })
    }

    @Test
    fun `resolution limits are per codec`() {
        val profile = JellyfinPlaybackManager.buildDeviceProfile(
            capabilities = DeviceMediaCapabilities.BASELINE.copy(
                maxResolution = mapOf("h264" to (1920 to 1088), "hevc" to (3840 to 2160))
            )
        )
        val h264 = profile.codecProfiles.first { it.codec == "h264" }.conditions
        val hevc = profile.codecProfiles.first { it.codec == "hevc" }.conditions
        assertEquals("1920", h264.first { it.property == "Width" }.value)
        assertEquals("3840", hevc.first { it.property == "Width" }.value)
    }

    @Test
    fun `DTS and TrueHD direct play only with passthrough support or a decoder`() {
        val noSink = JellyfinPlaybackManager.buildDeviceProfile(
            audioPassthroughEnabled = true,
            capabilities = DeviceMediaCapabilities.BASELINE
        )
        assertFalse("dts" in noSink.directAudio())
        assertFalse("truehd" in noSink.directAudio())
        assertFalse("ac3" in noSink.directAudio())
        assertEquals("aac,mp3", noSink.transcodingProfiles.first().audioCodec)

        val avr = DeviceMediaCapabilities.BASELINE.copy(
            passthroughAc3 = true, passthroughEac3 = true, passthroughDts = true, passthroughTrueHd = true
        )
        val withSink = JellyfinPlaybackManager.buildDeviceProfile(audioPassthroughEnabled = true, capabilities = avr)
        assertTrue("dts" in withSink.directAudio())
        assertTrue("truehd" in withSink.directAudio())
        assertEquals("aac,ac3,eac3,mp3", withSink.transcodingProfiles.first().audioCodec)

        // Passthrough switched off in the player: only formats the box can decode itself remain.
        val toggledOff = JellyfinPlaybackManager.buildDeviceProfile(
            audioPassthroughEnabled = false,
            capabilities = avr.copy(ac3Decoder = true)
        )
        assertTrue("ac3" in toggledOff.directAudio())
        assertFalse("dts" in toggledOff.directAudio())
        assertFalse("truehd" in toggledOff.directAudio())
    }

    @Test
    fun `permissive capabilities keep the previous everything-allowed profile`() {
        val profile = JellyfinPlaybackManager.buildDeviceProfile(capabilities = DeviceMediaCapabilities.PERMISSIVE)
        assertEquals(listOf("h264", "hevc", "vp9", "av1"), profile.directVideo())
        assertTrue("DOVIWithEL" in profile.rangesFor("hevc"))
        assertTrue(profile.codecProfiles.flatMap { it.conditions }.none { it.property == "Width" })
    }

    @Test
    fun `summary describes the device in plain words`() {
        val text = DeviceMediaCapabilities.BASELINE.summary()
        assertTrue(text.contains("SDR only"))
        assertTrue(text.contains("Audio passthrough: none"))
    }

    @Test
    fun `direct play approved for audio outside the profile is detected`() {
        val profile = JellyfinPlaybackManager.buildDeviceProfile(capabilities = DeviceMediaCapabilities.BASELINE)
        fun info(direct: Boolean, vararg audio: Pair<String, Boolean>) = JellyfinPlaybackInfoResponse(
            mediaSources = listOf(
                JellyfinMediaSourceDto(
                    id = "src",
                    supportsDirectPlay = direct,
                    mediaStreams = listOf(JellyfinMediaStreamDto(index = 0, type = "Video", codec = "h264")) +
                        audio.mapIndexed { i, (codec, isDefault) ->
                            JellyfinMediaStreamDto(index = i + 1, type = "Audio", codec = codec, isDefault = isDefault)
                        }
                )
            )
        )
        // Lone DTS track without the default flag: the server's quirk case.
        assertTrue(JellyfinPlaybackManager.directPlaysUnsupportedAudio(info(true, "dts" to false), null, profile))
        // Default AAC track: fine even if a DTS track exists.
        assertFalse(JellyfinPlaybackManager.directPlaysUnsupportedAudio(info(true, "dts" to false, "aac" to true), null, profile))
        // Server already chose a transcode.
        assertFalse(JellyfinPlaybackManager.directPlaysUnsupportedAudio(info(false, "dts" to false), null, profile))
        // Permissive profile allows DTS.
        val permissive = JellyfinPlaybackManager.buildDeviceProfile()
        assertFalse(JellyfinPlaybackManager.directPlaysUnsupportedAudio(info(true, "dts" to false), null, permissive))
    }
}
