package com.seerflix.tv.core.player

import com.seerflix.tv.core.data.MediaImageUrls
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinTrickplayInfoDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinPlaybackManagerTest {

    @Test
    fun `buildStreamUrl uses direct play static stream when maxBitrateBps is null`() {
        val source = JellyfinMediaSourceDto(
            id = "src1",
            supportsDirectPlay = true
        )
        val (url, isHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "http://jf:8096",
            token = "tok123",
            itemId = "item1",
            source = source,
            playSessionId = "sess1",
            maxBitrateBps = null
        )

        assertFalse(isHls)
        assertEquals("http://jf:8096/Videos/item1/stream?Static=true&MediaSourceId=src1&api_key=tok123", url)
    }

    @Test
    fun `buildStreamUrl builds HLS transcode URL when maxBitrateBps is specified`() {
        val source = JellyfinMediaSourceDto(
            id = "src1",
            supportsDirectPlay = true
        )
        val (url, isHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "http://jf:8096",
            token = "tok123",
            itemId = "item1",
            source = source,
            playSessionId = "sess1",
            maxBitrateBps = 15_000_000L
        )

        assertTrue(isHls)
        assertTrue(url.contains("/Videos/item1/master.m3u8"))
        assertTrue(url.contains("MaxStreamingBitrate=15000000"))
        assertTrue(url.contains("PlaySessionId=sess1"))
        assertTrue(url.contains("api_key=tok123"))
    }

    @Test
    fun `buildStreamUrl uses transcodingUrl when direct play unsupported`() {
        val source = JellyfinMediaSourceDto(
            id = "src1",
            supportsDirectPlay = false,
            supportsDirectStream = false,
            transcodingUrl = "/Videos/item1/hls1/main.m3u8?api_key=tok123"
        )
        val (url, isHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "http://jf:8096",
            token = "tok123",
            itemId = "item1",
            source = source,
            playSessionId = "sess1",
            maxBitrateBps = null
        )

        assertTrue(isHls)
        assertEquals("http://jf:8096/Videos/item1/hls1/main.m3u8?api_key=tok123", url)
    }

    @Test
    fun `buildStreamUrl prefers server remux URL for direct-stream-only sources`() {
        val source = JellyfinMediaSourceDto(
            id = "src1",
            supportsDirectPlay = false,
            supportsDirectStream = true,
            transcodingUrl = "/Videos/item1/master.m3u8?MediaSourceId=src1&PlaySessionId=sess1"
        )
        val (url, isHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "http://jf:8096",
            token = "tok123",
            itemId = "item1",
            source = source,
            playSessionId = "sess1",
            maxBitrateBps = null
        )

        assertTrue(isHls)
        assertEquals(
            "http://jf:8096/Videos/item1/master.m3u8?MediaSourceId=src1&PlaySessionId=sess1&api_key=tok123",
            url
        )
    }

    @Test
    fun `buildStreamUrl falls back to static stream for direct-stream source without transcodingUrl`() {
        val source = JellyfinMediaSourceDto(
            id = "src1",
            supportsDirectPlay = false,
            supportsDirectStream = true
        )
        val (url, isHls) = JellyfinPlaybackManager.buildStreamUrl(
            base = "http://jf:8096",
            token = "tok123",
            itemId = "item1",
            source = source,
            playSessionId = "sess1",
            maxBitrateBps = null
        )

        assertFalse(isHls)
        assertEquals("http://jf:8096/Videos/item1/stream?Static=true&MediaSourceId=src1&api_key=tok123", url)
    }

    @Test
    fun `isImageBasedSubtitle flags bitmap codecs only`() {
        assertTrue(JellyfinPlaybackManager.isImageBasedSubtitle("PGSSUB"))
        assertTrue(JellyfinPlaybackManager.isImageBasedSubtitle("hdmv_pgs_subtitle"))
        assertTrue(JellyfinPlaybackManager.isImageBasedSubtitle("dvdsub"))
        assertTrue(JellyfinPlaybackManager.isImageBasedSubtitle("dvb_subtitle"))
        assertFalse(JellyfinPlaybackManager.isImageBasedSubtitle("subrip"))
        assertFalse(JellyfinPlaybackManager.isImageBasedSubtitle("ass"))
        assertFalse(JellyfinPlaybackManager.isImageBasedSubtitle("webvtt"))
        assertFalse(JellyfinPlaybackManager.isImageBasedSubtitle(null))
    }

    @Test
    fun `runCatchingCancellable rethrows cancellation but captures other failures`() {
        val failure = runCatchingCancellable<Int> { throw IllegalStateException("boom") }
        assertTrue(failure.isFailure)

        var rethrown = false
        try {
            runCatchingCancellable<Int> { throw kotlinx.coroutines.CancellationException("cancelled") }
        } catch (e: kotlinx.coroutines.CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)
    }

    @Test
    fun `resolveRelativeJellyfinUrl normalizes slashes and appends api_key when missing`() {
        val resolvedNoKey = JellyfinPlaybackManager.resolveRelativeJellyfinUrl(
            base = "http://jf:8096/",
            rawPath = "Videos/item1/src1/Subtitles/2/0/Stream.srt",
            token = "tok123"
        )
        assertEquals(
            "http://jf:8096/Videos/item1/src1/Subtitles/2/0/Stream.srt?api_key=tok123",
            resolvedNoKey
        )

        val resolvedWithQuery = JellyfinPlaybackManager.resolveRelativeJellyfinUrl(
            base = "http://jf:8096",
            rawPath = "/Videos/item1/hls1/main.m3u8?PlaySessionId=s1",
            token = "tok123"
        )
        assertEquals(
            "http://jf:8096/Videos/item1/hls1/main.m3u8?PlaySessionId=s1&api_key=tok123",
            resolvedWithQuery
        )

        val resolvedExistingKey = JellyfinPlaybackManager.resolveRelativeJellyfinUrl(
            base = "http://jf:8096/",
            rawPath = "/Videos/item1/hls1/main.m3u8?api_key=tok123",
            token = "tok123"
        )
        assertEquals(
            "http://jf:8096/Videos/item1/hls1/main.m3u8?api_key=tok123",
            resolvedExistingKey
        )
    }

    @Test
    fun `MediaImageUrls trims trailing slashes from baseUrl`() {
        assertEquals(
            "http://jf:8096/Items/abc/Images/Primary?maxWidth=340&quality=82",
            MediaImageUrls.jellyfinPrimary("http://jf:8096/", "abc")
        )
        assertEquals(
            "http://jf:8096/Items/abc/Images/Backdrop/0?maxWidth=960&quality=80",
            MediaImageUrls.jellyfinBackdrop("http://jf:8096///", "abc")
        )
    }

    @Test
    fun `extractTrickplayInfo prefers 320w and falls back to closest width`() {
        val item = JellyfinItemDto(
            id = "item1",
            name = "Movie",
            type = "Movie",
            trickplay = mapOf(
                "src1" to mapOf(
                    "160" to JellyfinTrickplayInfoDto(width = 160, height = 90, thumbnailCount = 20, interval = 10000),
                    "320" to JellyfinTrickplayInfoDto(width = 320, height = 180, tileWidth = 10, tileHeight = 10, thumbnailCount = 120, interval = 10000)
                )
            )
        )

        val info = JellyfinPlaybackManager.extractTrickplayInfo(item, "src1", "http://jf:8096", "tok")
        assertNotNull(info)
        assertEquals(320, info!!.width)
        assertEquals(180, info.height)
        assertEquals(120, info.thumbnailCount)
        assertEquals(
            "http://jf:8096/Videos/item1/Trickplay/320/{tileIndex}.jpg?mediaSourceId=src1&api_key=tok",
            info.baseUrlTemplate
        )

        val emptyItem = item.copy(trickplay = emptyMap())
        assertNull(JellyfinPlaybackManager.extractTrickplayInfo(emptyItem, "src1", "http://jf:8096", "tok"))
    }
}
