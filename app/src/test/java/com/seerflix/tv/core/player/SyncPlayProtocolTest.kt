package com.seerflix.tv.core.player

import com.seerflix.tv.core.network.JellyfinSocketClient
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlayProtocolTest {

    @Test
    fun `clock sample removes server processing time and splits the network delay`() {
        // Local clock is 5 s behind the server; 40 ms each way, server took 10 ms.
        val t0 = 1_000L
        val t1 = 1_000L + 40 + 5_000
        val t2 = t1 + 10
        val t3 = t0 + 40 + 10 + 40
        val (rtt, offset) = SyncPlayManager.clockSample(t0, t1, t2, t3)
        assertEquals(80L, rtt)
        assertEquals(5_000L, offset)
    }

    @Test
    fun `server times with seven fractional digits parse to epoch millis`() {
        assertEquals(1_763_200_800_123L, JellyfinSocketClient.parseServerTime("2025-11-15T10:00:00.1234567Z"))
        assertEquals(1_763_200_800_000L, JellyfinSocketClient.parseServerTime("2025-11-15T10:00:00Z"))
        assertNull(JellyfinSocketClient.parseServerTime(""))
        assertNull(JellyfinSocketClient.parseServerTime("not a date"))
    }

    @Test
    fun `formatted times round trip through the parser`() {
        val now = 1_763_200_800_456L
        assertEquals(now, JellyfinSocketClient.parseServerTime(JellyfinSocketClient.formatServerTime(now)))
    }

    @Test
    fun `play queue update picks the playing entry and normalizes its id`() {
        val json = Json.parseToJsonElement(
            """
            {"Reason":"NewPlaylist","LastUpdate":"2025-11-15T10:00:00Z",
             "Playlist":[
               {"ItemId":"aaaa","PlaylistItemId":"p1"},
               {"ItemId":"6F1C2B3A-0000-4000-8000-00000000ABCD","PlaylistItemId":"p2"}
             ],
             "PlayingItemIndex":1,"StartPositionTicks":600000000,"IsPlaying":true,
             "ShuffleMode":"Sorted","RepeatMode":"RepeatNone"}
            """.trimIndent()
        ).jsonObject
        val update = SyncPlayManager.parsePlayQueue(json)!!
        assertEquals("NewPlaylist", update.reason)
        assertEquals("6f1c2b3a00004000800000000000abcd", update.itemId)
        assertEquals("p2", update.playlistItemId)
        assertEquals(600_000_000L, update.startPositionTicks)
        assertTrue(update.isPlaying)
    }

    @Test
    fun `play queue without a playlist is ignored`() {
        val json = Json.parseToJsonElement("""{"Reason":"RemoveItems","PlayingItemIndex":0}""").jsonObject
        assertNull(SyncPlayManager.parsePlayQueue(json))
    }

    @Test
    fun `ids compare equal with or without dashes`() {
        assertEquals(
            SyncPlayManager.normalizeId("6f1c2b3a-0000-4000-8000-00000000abcd"),
            SyncPlayManager.normalizeId("6F1C2B3A00004000800000000000ABCD")
        )
        assertFalse(SyncPlayManager.normalizeId("abc") == SyncPlayManager.normalizeId("abd"))
    }
}
