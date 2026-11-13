package com.seerflix.tv.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSkipHelperTest {

    @Test
    fun `returns true inside segment range when enabled`() {
        assertTrue(
            AutoSkipHelper.shouldAutoSkip(
                enabled = true,
                isTrailer = false,
                itemId = "ep-1",
                currentTicks = 150_000_000L,
                startTicks = 100_000_000L,
                endTicks = 400_000_000L,
                alreadySkippedItemId = null
            )
        )
    }

    @Test
    fun `returns false when disabled`() {
        assertFalse(
            AutoSkipHelper.shouldAutoSkip(
                enabled = false,
                isTrailer = false,
                itemId = "ep-1",
                currentTicks = 150_000_000L,
                startTicks = 100_000_000L,
                endTicks = 400_000_000L,
                alreadySkippedItemId = null
            )
        )
    }

    @Test
    fun `returns false for trailers`() {
        assertFalse(
            AutoSkipHelper.shouldAutoSkip(
                enabled = true,
                isTrailer = true,
                itemId = "trailer-1",
                currentTicks = 150_000_000L,
                startTicks = 100_000_000L,
                endTicks = 400_000_000L,
                alreadySkippedItemId = null
            )
        )
    }

    @Test
    fun `returns false outside segment bounds`() {
        assertFalse(
            AutoSkipHelper.shouldAutoSkip(
                enabled = true,
                isTrailer = false,
                itemId = "ep-1",
                currentTicks = 99_999_999L,
                startTicks = 100_000_000L,
                endTicks = 400_000_000L,
                alreadySkippedItemId = null
            )
        )
        assertFalse(
            AutoSkipHelper.shouldAutoSkip(
                enabled = true,
                isTrailer = false,
                itemId = "ep-1",
                currentTicks = 400_000_000L,
                startTicks = 100_000_000L,
                endTicks = 400_000_000L,
                alreadySkippedItemId = null
            )
        )
    }

    @Test
    fun `returns false when already skipped for same item`() {
        assertFalse(
            AutoSkipHelper.shouldAutoSkip(
                enabled = true,
                isTrailer = false,
                itemId = "ep-1",
                currentTicks = 150_000_000L,
                startTicks = 100_000_000L,
                endTicks = 400_000_000L,
                alreadySkippedItemId = "ep-1"
            )
        )
        assertTrue(
            AutoSkipHelper.shouldAutoSkip(
                enabled = true,
                isTrailer = false,
                itemId = "ep-2",
                currentTicks = 150_000_000L,
                startTicks = 100_000_000L,
                endTicks = 400_000_000L,
                alreadySkippedItemId = "ep-1"
            )
        )
    }
}
