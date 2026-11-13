package com.seerflix.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedMediaItemTest {

    @Test
    fun `item matched in Jellyfin is playable and has Available overall status`() {
        val item = UnifiedMediaItem(
            id = "tmdb_MOVIE_693134",
            tmdbId = 693134,
            jellyfinId = "jf-abc-123",
            mediaType = MediaType.MOVIE,
            title = "Dune: Part Two",
            hdStatus = AvailabilityTier.AVAILABLE,
            uhdStatus = AvailabilityTier.UNKNOWN,
            runtimeTicks = 10_000_000_000L,
            resumePositionTicks = 2_500_000_000L
        )

        assertTrue(item.isPlayable)
        assertTrue(item.hasHd)
        assertFalse(item.has4k)
        assertFalse(item.canRequestHd)
        assertTrue(item.canRequest4k)
        assertEquals(AvailabilityTier.AVAILABLE, item.overallAvailability)
        assertEquals(0.25f, item.watchProgressFraction, 0.01f)
    }

    @Test
    fun `item downloading 4K while HD available reports active download and keeps HD playable`() {
        val dl = DownloadProgressInfo(
            is4k = true,
            status = "downloading",
            title = "Dune.Part.Two.2160p",
            sizeBytes = 20_000L,
            sizeLeftBytes = 10_000L,
            percentage = 50f,
            etaText = "~12m left",
            estimatedCompletionIso = null
        )
        val item = UnifiedMediaItem(
            id = "tmdb_MOVIE_693134",
            tmdbId = 693134,
            jellyfinId = "jf-abc-123",
            mediaType = MediaType.MOVIE,
            title = "Dune: Part Two",
            hdStatus = AvailabilityTier.AVAILABLE,
            uhdStatus = AvailabilityTier.PROCESSING,
            uhdDownload = dl
        )

        assertTrue(item.isPlayable)
        assertEquals(dl, item.activeDownload)
        assertFalse(item.canRequest4k)
    }

    @Test
    fun `unowned item not requested allows both HD and 4K requests`() {
        val item = UnifiedMediaItem(
            id = "tmdb_MOVIE_999",
            tmdbId = 999,
            jellyfinId = null,
            mediaType = MediaType.MOVIE,
            title = "Unowned Film",
            hdStatus = AvailabilityTier.UNKNOWN,
            uhdStatus = AvailabilityTier.UNKNOWN
        )

        assertFalse(item.isPlayable)
        assertTrue(item.canRequestHd)
        assertTrue(item.canRequest4k)
        assertEquals(AvailabilityTier.UNKNOWN, item.overallAvailability)
    }
}
