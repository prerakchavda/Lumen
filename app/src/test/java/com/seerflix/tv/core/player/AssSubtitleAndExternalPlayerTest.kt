package com.seerflix.tv.core.player

import android.content.Intent
import androidx.compose.ui.graphics.Color
import com.seerflix.tv.core.model.AssSubtitleMode
import com.seerflix.tv.core.model.ExternalSubtitleFile
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinMediaStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssSubtitleAndExternalPlayerTest {

    @Test
    fun `AssSubtitleParser parses V4+ styles, inline override tags, newlines, and sorts cues`() {
        val ass = """
            [Script Info]
            Title: Test Anime

            [V4+ Styles]
            Format: Name, Fontname, Fontsize, PrimaryColour, Bold, Italic, Alignment
            Style: Default,Arial,48,&H00FFFFFF,0,0,2
            Style: Sign,Arial,36,&H0000FFFF,-1,-1,8

            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:10.00,0:00:14.50,Default,,0,0,0,,Line one\NLine two
            Dialogue: 0,0:00:02.00,0:00:05.00,Sign,,0,0,0,,{\an7\b1}Episode Title
        """.trimIndent()

        val cues = AssSubtitleParser.parse(ass)
        assertEquals(2, cues.size)

        val first = cues[0]
        assertEquals(2_000L, first.startMs)
        assertEquals(5_000L, first.endMs)
        assertEquals("Episode Title", first.text)
        assertEquals(7, first.alignment)
        assertTrue(first.isBold)
        assertTrue(first.isItalic)
        // &H0000FFFF (BB=00, GG=FF, RR=FF) -> Yellow
        assertEquals(Color(255, 255, 0, 255), first.primaryColor)

        val second = cues[1]
        assertEquals(10_000L, second.startMs)
        assertEquals(14_500L, second.endMs)
        assertEquals("Line one\nLine two", second.text)
        assertEquals(2, second.alignment)
        assertFalse(second.isBold)
    }

    @Test
    fun `ExternalPlayerHelper builds ACTION_VIEW intent with resume position and external subtitles`() {
        val subs = listOf(
            ExternalSubtitleFile(
                index = 3,
                title = "English (ASS)",
                language = "eng",
                codec = "ass",
                url = "http://jf:8096/sub.ass"
            )
        )
        val intent = ExternalPlayerHelper.buildExternalPlayerIntent(
            streamUrl = "http://jf:8096/Videos/1/stream?Static=true",
            title = "Attack on Titan",
            positionMs = 123_456L,
            externalSubtitles = subs,
            packageName = "org.videolan.vlc"
        )

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("org.videolan.vlc", intent.`package`)
        assertEquals("Attack on Titan", intent.getStringExtra("title"))
        assertEquals(123_456, intent.getIntExtra("position", 0))
        assertEquals("http://jf:8096/sub.ass", intent.getStringExtra("subtitles_location"))
    }

    @Test
    fun `JellyfinPlaybackManager buildDeviceProfile adapts audio codecs and ASS burn-in mode`() {
        val passthroughProfile = JellyfinPlaybackManager.buildDeviceProfile(
            audioPassthroughEnabled = true,
            stereoDownmixEnabled = false,
            assSubtitleMode = AssSubtitleMode.STYLED,
            burnInSubtitleIndex = null
        )
        val directAudio = passthroughProfile.directPlayProfiles.first().audioCodec
        assertTrue(directAudio.contains("truehd"))
        assertTrue(directAudio.contains("dts"))
        assertEquals("External", passthroughProfile.subtitleProfiles.first { it.format == "ass" }.method)

        val burnInStereoProfile = JellyfinPlaybackManager.buildDeviceProfile(
            audioPassthroughEnabled = false,
            stereoDownmixEnabled = true,
            assSubtitleMode = AssSubtitleMode.BURN_IN,
            burnInSubtitleIndex = 4
        )
        val downmixAudio = burnInStereoProfile.directPlayProfiles.first().audioCodec
        assertFalse(downmixAudio.contains("truehd"))
        assertFalse(downmixAudio.contains("dts"))
        assertEquals("Encode", burnInStereoProfile.subtitleProfiles.first { it.format == "ass" }.method)
    }

    @Test
    fun `JellyfinPlaybackManager buildStreamUrl and mapMediaSourceOption encode options accurately`() {
        val source = JellyfinMediaSourceDto(
            id = "remux_id",
            name = "4K Remux",
            container = "mkv",
            bitrate = 65_000_000L,
            mediaStreams = listOf(
                JellyfinMediaStreamDto(index = 0, type = "Video", codec = "hevc", width = 3840, height = 2160),
                JellyfinMediaStreamDto(index = 1, type = "Audio", codec = "truehd", channels = 8)
            )
        )
        val mapped = JellyfinPlaybackManager.mapMediaSourceOption(source)
        assertEquals("4K", mapped.resolutionLabel)
        assertEquals("HEVC", mapped.videoCodec)
        assertEquals("TRUEHD 7.1", mapped.audioSummary)
        assertTrue(mapped.displayLabel.contains("65 Mbps"))

        val (burnUrl, isHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "http://jf:8096",
            token = "tok",
            itemId = "item1",
            source = source,
            playSessionId = "sess1",
            maxBitrateBps = null,
            stereoDownmix = true,
            burnInSubtitleIndex = 7
        )
        assertTrue(isHls)
        assertTrue(burnUrl.contains("SubtitleStreamIndex=7"))
        assertTrue(burnUrl.contains("SubtitleMethod=Encode"))
        assertTrue(burnUrl.contains("MaxAudioChannels=2"))
    }
}
