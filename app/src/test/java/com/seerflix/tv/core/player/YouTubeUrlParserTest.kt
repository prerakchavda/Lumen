package com.seerflix.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun `extracts key from standard watch URL`() {
        assertEquals(
            "Way9Dexny3w",
            YouTubeUrlParser.extractVideoKey("https://www.youtube.com/watch?v=Way9Dexny3w&feature=share")
        )
    }

    @Test
    fun `extracts key from youtu_be short URL`() {
        assertEquals(
            "dQw4w9WgXcQ",
            YouTubeUrlParser.extractVideoKey("https://youtu.be/dQw4w9WgXcQ?t=10")
        )
    }

    @Test
    fun `extracts key from embed URL`() {
        assertEquals(
            "abc123XYZ_0",
            YouTubeUrlParser.extractVideoKey("https://www.youtube.com/embed/abc123XYZ_0")
        )
    }

    @Test
    fun `returns null for invalid or blank URLs`() {
        assertNull(YouTubeUrlParser.extractVideoKey(null))
        assertNull(YouTubeUrlParser.extractVideoKey("https://example.com/trailer.mp4"))
    }
}
