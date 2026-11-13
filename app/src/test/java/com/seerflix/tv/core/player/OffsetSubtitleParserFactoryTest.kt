package com.seerflix.tv.core.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@OptIn(UnstableApi::class)
class OffsetSubtitleParserFactoryTest {

    private val sampleCue = Cue.Builder().setText("Hello world").build()

    @Test
    fun `positive offset shifts start time forward`() {
        val original = CuesWithTiming(listOf(sampleCue), 5_000_000L, 2_000_000L)

        val shifted = OffsetSubtitleParserFactory.shiftCuesWithTiming(original, 750_000L)

        assertEquals(5_750_000L, shifted.startTimeUs)
        assertEquals(2_000_000L, shifted.durationUs)
        assertEquals(listOf(sampleCue), shifted.cues)
    }

    @Test
    fun `negative offset shifts start time backward`() {
        val original = CuesWithTiming(listOf(sampleCue), 5_000_000L, 2_000_000L)

        val shifted = OffsetSubtitleParserFactory.shiftCuesWithTiming(original, -1_250_000L)

        assertEquals(3_750_000L, shifted.startTimeUs)
        assertEquals(2_000_000L, shifted.durationUs)
    }

    @Test
    fun `negative offset clamps start time to zero`() {
        val original = CuesWithTiming(listOf(sampleCue), 400_000L, 1_500_000L)

        val shifted = OffsetSubtitleParserFactory.shiftCuesWithTiming(original, -1_000_000L)

        assertEquals(0L, shifted.startTimeUs)
        assertEquals(1_500_000L, shifted.durationUs)
    }

    @Test
    fun `time unset is preserved unchanged`() {
        val original = CuesWithTiming(listOf(sampleCue), C.TIME_UNSET, 1_000_000L)

        val shifted = OffsetSubtitleParserFactory.shiftCuesWithTiming(original, 500_000L)

        assertSame(original, shifted)
        assertEquals(C.TIME_UNSET, shifted.startTimeUs)
    }

    @Test
    fun `zero offset passes through original instance`() {
        val original = CuesWithTiming(listOf(sampleCue), 2_000_000L, 1_000_000L)

        val shifted = OffsetSubtitleParserFactory.shiftCuesWithTiming(original, 0L)

        assertSame(original, shifted)
    }
}
