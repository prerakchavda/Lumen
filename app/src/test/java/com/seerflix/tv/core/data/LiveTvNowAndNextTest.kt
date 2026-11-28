package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.LiveTvProgram
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    @Test
    fun `programme times follow the formatter the guide passes, 12 or 24 hour`() {
        val evening = LiveTvProgram(id = "e", title = "E", startMs = 20 * hour + 30 * 60_000L, endMs = 22 * hour)
        val twelve = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(ZoneOffset.UTC)
        val twentyFour = DateTimeFormatter.ofPattern("HH:mm", Locale.US).withZone(ZoneOffset.UTC)
        assertEquals("8:30 PM - 10:00 PM", LiveTvEpgHelper.timeRangeLabel(evening, twelve))
        assertEquals("20:30 - 22:00", LiveTvEpgHelper.timeRangeLabel(evening, twentyFour))
        // No times: the preformatted label (usually blank) is kept.
        val untimed = LiveTvProgram(id = "u", title = "U", timeRangeLabel = "")
        assertEquals("", LiveTvEpgHelper.timeRangeLabel(untimed, twelve))
    }

    @Test
    fun `guide programme cap grows with the lineup but stays bounded`() {
        assertEquals(12_000, UnifiedCatalogRepository.liveTvProgramCap(0))
        assertEquals(12_000, UnifiedCatalogRepository.liveTvProgramCap(40))
        // 300 channels x 72 half-hour slots = 21,600 programmes must fit.
        assertEquals(true, UnifiedCatalogRepository.liveTvProgramCap(300) >= 300 * 72)
        assertEquals(50_000, UnifiedCatalogRepository.liveTvProgramCap(10_000))
    }
}
