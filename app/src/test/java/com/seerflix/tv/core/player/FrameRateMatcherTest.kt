package com.seerflix.tv.core.player

import com.seerflix.tv.core.model.FrameRateMatchMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameRateMatcherTest {

    private val mode60 = DisplayModeCandidate(1, 3840, 2160, 60.0f)
    private val mode23976 = DisplayModeCandidate(2, 3840, 2160, 23.976f)
    private val mode24 = DisplayModeCandidate(3, 3840, 2160, 24.0f)
    private val mode48 = DisplayModeCandidate(4, 3840, 2160, 48.0f)
    private val mode120 = DisplayModeCandidate(5, 3840, 2160, 120.0f)
    private val mode11988 = DisplayModeCandidate(6, 3840, 2160, 119.88f)
    private val mode1080p24 = DisplayModeCandidate(7, 1920, 1080, 24.0f)

    @Test
    fun `returns null when mode is OFF`() {
        val selected = FrameRateMatcher.selectBestDisplayMode(
            videoFrameRate = 23.976f,
            currentMode = mode60,
            supportedModes = listOf(mode60, mode23976, mode24),
            matchMode = FrameRateMatchMode.OFF
        )
        assertNull(selected)
    }

    @Test
    fun `matches 23_976fps to exact 23_976Hz mode over 24Hz`() {
        val selected = FrameRateMatcher.selectBestDisplayMode(
            videoFrameRate = 23.976f,
            currentMode = mode60,
            supportedModes = listOf(mode60, mode24, mode23976, mode11988),
            matchMode = FrameRateMatchMode.SEAMLESS_ONLY
        )
        assertEquals(mode23976, selected)
    }

    @Test
    fun `matches 23_976fps to 119_88Hz 5x multiple when exact mode absent`() {
        val selected = FrameRateMatcher.selectBestDisplayMode(
            videoFrameRate = 23.976f,
            currentMode = mode60,
            supportedModes = listOf(mode60, mode11988),
            matchMode = FrameRateMatchMode.ALWAYS
        )
        assertEquals(mode11988, selected)
    }

    @Test
    fun `matches 24fps to 24Hz, 48Hz, or 120Hz multiples`() {
        assertEquals(
            mode24,
            FrameRateMatcher.selectBestDisplayMode(
                videoFrameRate = 24.0f,
                currentMode = mode60,
                supportedModes = listOf(mode60, mode24, mode48, mode120),
                matchMode = FrameRateMatchMode.ALWAYS
            )
        )
        assertEquals(
            mode48,
            FrameRateMatcher.selectBestDisplayMode(
                videoFrameRate = 24.0f,
                currentMode = mode60,
                supportedModes = listOf(mode60, mode48, mode120),
                matchMode = FrameRateMatchMode.ALWAYS
            )
        )
        assertEquals(
            mode120,
            FrameRateMatcher.selectBestDisplayMode(
                videoFrameRate = 24.0f,
                currentMode = mode60,
                supportedModes = listOf(mode60, mode120),
                matchMode = FrameRateMatchMode.ALWAYS
            )
        )
    }

    @Test
    fun `ignores modes with different physical resolution`() {
        val selected = FrameRateMatcher.selectBestDisplayMode(
            videoFrameRate = 24.0f,
            currentMode = mode60,
            supportedModes = listOf(mode60, mode1080p24),
            matchMode = FrameRateMatchMode.ALWAYS
        )
        assertNull(selected)
    }

    @Test
    fun `returns null when current mode is already optimal`() {
        val selected = FrameRateMatcher.selectBestDisplayMode(
            videoFrameRate = 23.976f,
            currentMode = mode23976,
            supportedModes = listOf(mode60, mode23976, mode11988),
            matchMode = FrameRateMatchMode.ALWAYS
        )
        assertNull(selected)
    }
}
