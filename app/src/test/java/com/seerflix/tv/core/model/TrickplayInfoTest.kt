package com.seerflix.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrickplayInfoTest {

    private val info = TrickplayInfo(
        width = 320,
        height = 180,
        tileWidth = 3,
        tileHeight = 2, // 6 thumbs per tile
        thumbnailCount = 10,
        intervalMs = 10_000,
        baseUrlTemplate = "http://jellyfin:8096/Videos/item1/Trickplay/320/{tileIndex}.jpg?api_key=tok"
    )

    @Test
    fun `cropForPosition computes tile index and pixel coordinates`() {
        // Thumb index 0 -> tile 0, col 0, row 0
        val start = info.cropForPosition(0L)!!
        assertEquals(0, start.tileIndex)
        assertEquals(0, start.x)
        assertEquals(0, start.y)
        assertEquals(320, start.width)
        assertEquals(180, start.height)
        assertEquals(
            "http://jellyfin:8096/Videos/item1/Trickplay/320/0.jpg?api_key=tok",
            start.tileUrl
        )

        // 45_000ms -> thumb index 4 -> tile 0, row 1, col 1
        val midTile0 = info.cropForPosition(45_000L)!!
        assertEquals(0, midTile0.tileIndex)
        assertEquals(320, midTile0.x)
        assertEquals(180, midTile0.y)

        // 72_000ms -> thumb index 7 -> tile 1, indexInTile 1 -> row 0, col 1
        val tile1 = info.cropForPosition(72_000L)!!
        assertEquals(1, tile1.tileIndex)
        assertEquals(320, tile1.x)
        assertEquals(0, tile1.y)
        assertEquals(
            "http://jellyfin:8096/Videos/item1/Trickplay/320/1.jpg?api_key=tok",
            tile1.tileUrl
        )
    }

    @Test
    fun `cropForPosition clamps negative and past-end positions`() {
        val negative = info.cropForPosition(-5_000L)!!
        assertEquals(0, negative.tileIndex)
        assertEquals(0, negative.x)
        assertEquals(0, negative.y)

        // thumbnailCount = 10 -> max thumb index = 9 -> tile 1, indexInTile 3 -> row 1, col 0
        val pastEnd = info.cropForPosition(999_000L)!!
        assertEquals(1, pastEnd.tileIndex)
        assertEquals(0, pastEnd.x)
        assertEquals(180, pastEnd.y)
    }

    @Test
    fun `cropForPosition returns null for invalid dimensions or interval`() {
        assertNull(info.copy(intervalMs = 0).cropForPosition(10_000L))
        assertNull(info.copy(tileWidth = 0).cropForPosition(10_000L))
        assertNull(info.copy(width = 0).cropForPosition(10_000L))
    }
}
