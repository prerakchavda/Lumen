package com.seerflix.tv.core.data

import com.seerflix.tv.core.network.JellyfinMediaStreamDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedCatalogRepositoryBadgesTest {

    @Test
    fun `extractMediaBadges detects 4K Dolby Vision Atmos CC and AD`() {
        val streams = listOf(
            JellyfinMediaStreamDto(
                index = 0,
                type = "Video",
                codec = "hevc",
                width = 3840,
                height = 2160,
                videoRange = "HDR",
                videoRangeType = "DOVIWithHDR10"
            ),
            JellyfinMediaStreamDto(
                index = 1,
                type = "Audio",
                codec = "eac3",
                channels = 6,
                audioSpatialFormat = "DolbyAtmos"
            ),
            JellyfinMediaStreamDto(
                index = 2,
                type = "Audio",
                codec = "aac",
                channels = 2,
                title = "English Audio Description"
            ),
            JellyfinMediaStreamDto(
                index = 3,
                type = "Subtitle",
                codec = "subrip",
                isHearingImpaired = true
            )
        )

        val badges = UnifiedCatalogRepository.extractMediaBadges(streams)
        assertEquals(listOf("4K", "Dolby Vision", "Dolby Atmos", "CC", "AD"), badges)
    }

    @Test
    fun `extractMediaBadges detects HD HDR10+ 5_1 and standard subtitles`() {
        val streams = listOf(
            JellyfinMediaStreamDto(
                index = 0,
                type = "Video",
                width = 1920,
                height = 1080,
                videoRange = "HDR",
                videoRangeType = "HDR10Plus"
            ),
            JellyfinMediaStreamDto(
                index = 1,
                type = "Audio",
                channels = 6
            ),
            JellyfinMediaStreamDto(
                index = 2,
                type = "Subtitle",
                isHearingImpaired = false
            )
        )

        val badges = UnifiedCatalogRepository.extractMediaBadges(streams)
        assertEquals(listOf("HD", "HDR10+", "5.1", "Subtitles"), badges)
    }

    @Test
    fun `extractMediaBadges returns empty list when no streams present`() {
        assertTrue(UnifiedCatalogRepository.extractMediaBadges(emptyList()).isEmpty())
    }
}
