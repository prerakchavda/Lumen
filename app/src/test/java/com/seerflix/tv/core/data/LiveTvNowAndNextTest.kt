package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.LiveTvProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LiveTvNowAndNextTest {

    private val hour = 3_600_000L
    private val a = LiveTvProgram(id = "a", title = "A", startMs = 0L, endMs = hour)
    private val b = LiveTvProgram(id = "b", title = "B", startMs = hour, endMs = 2 * hour)
    private val c = LiveTvProgram(id = "c", title = "C", startMs = 2 * hour, endMs = 3 * hour)
    private val channel = LiveTvChannel(
        id = "ch", number = "1", name = "One", logoUrl = null,
        currentProgram = a, nextProgram = b, programs = listOf(a, b, c)
    )

    @Test
    fun `now and next follow the clock instead of the values loaded with the guide`() {
        assertEquals(a to b, LiveTvEpgHelper.nowAndNext(channel, nowMs = 10 * 60_000L))
        // An hour and a half later the loaded "current" programme has ended.
        assertEquals(b to c, LiveTvEpgHelper.nowAndNext(channel, nowMs = hour + 30 * 60_000L))
    }

    @Test
    fun `an ended programme is never reported as on now`() {
        val sparse = channel.copy(programs = emptyList())
        val (cur, next) = LiveTvEpgHelper.nowAndNext(sparse, nowMs = 2 * hour + 1)
        assertNull(cur)
        assertNull(next)
    }
}
