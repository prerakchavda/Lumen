package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.network.SeerrDownloadStatusDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class DownloadProgressParserTest {

    @Test
    fun `empty list returns null`() {
        assertNull(DownloadProgressParser.parse(emptyList(), is4k = false))
    }

    @Test
    fun `computes percentage and ISO ETA accurately`() {
        val now = Instant.parse("2026-11-13T05:00:00Z")
        val eta = "2026-11-13T05:06:30Z"
        val dto = SeerrDownloadStatusDto(
            title = "Dune.Part.Two.2024.2160p",
            status = "downloading",
            size = 10_000_000_000L,
            sizeLeft = 3_600_000_000L,
            estimatedCompletionTime = eta
        )

        val info = DownloadProgressParser.parse(listOf(dto), is4k = true, now = now)
        assertNotNull(info)
        assertEquals(64.0f, info!!.percentage, 0.1f)
        assertEquals("~6m left", info.etaText)
        assertEquals("Downloading 4K • 64% • ~6m left", info.label)
    }

    @Test
    fun `falls back to timeLeft string when ISO timestamp missing`() {
        val dto = SeerrDownloadStatusDto(
            title = "Severance.S02E01.1080p",
            status = "downloading",
            size = 2_000_000_000L,
            sizeLeft = 500_000_000L,
            timeLeft = "01:15:20"
        )

        val info = DownloadProgressParser.parse(listOf(dto), is4k = false)
        assertNotNull(info)
        assertEquals(75.0f, info!!.percentage, 0.1f)
        assertEquals("~1h 15m left", info.etaText)
        assertEquals("Downloading HD • 75% • ~1h 15m left", info.label)
    }

    @Test
    fun `handles unknown size without crashing or showing fake percentage`() {
        val dto = SeerrDownloadStatusDto(
            title = "Queued.Release",
            status = "downloading",
            size = 0L,
            sizeLeft = 0L
        )
        val info = DownloadProgressParser.parse(listOf(dto), is4k = false)
        assertNotNull(info)
        assertEquals(0f, info!!.percentage, 0.01f)
        assertEquals("Downloading HD", info.label)
    }

    @Test
    fun `maps Seerr v3 status codes to AvailabilityTier`() {
        assertEquals(AvailabilityTier.UNKNOWN, AvailabilityTier.fromSeerrCode(1))
        assertEquals(AvailabilityTier.PENDING, AvailabilityTier.fromSeerrCode(2))
        assertEquals(AvailabilityTier.PROCESSING, AvailabilityTier.fromSeerrCode(3))
        assertEquals(AvailabilityTier.PARTIALLY_AVAILABLE, AvailabilityTier.fromSeerrCode(4))
        assertEquals(AvailabilityTier.AVAILABLE, AvailabilityTier.fromSeerrCode(5))
    }
}
