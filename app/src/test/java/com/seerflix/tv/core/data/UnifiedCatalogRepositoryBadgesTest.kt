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

    @Test
    fun `formatHttpError translates raw HTTP status codes into friendly messages`() {
        val authErr = UnifiedCatalogRepository.formatHttpError(IllegalStateException("HTTP 401 Unauthorized"))
        assertTrue(authErr.contains("Authentication required (HTTP 401)"))

        val forbiddenErr = UnifiedCatalogRepository.formatHttpError(IllegalStateException("HTTP 403 Forbidden"))
        assertTrue(forbiddenErr.contains("Authentication required (HTTP 403)"))

        val notFoundErr = UnifiedCatalogRepository.formatHttpError(IllegalStateException("HTTP 404 Not Found"))
        assertEquals("Media item not found on server (HTTP 404)", notFoundErr)

        val conflictErr = UnifiedCatalogRepository.formatHttpError(IllegalStateException("HTTP 409 Conflict"))
        assertEquals("This item has already been requested (HTTP 409)", conflictErr)

        val serverErr = UnifiedCatalogRepository.formatHttpError(IllegalStateException("HTTP 500 Internal Server Error"))
        assertTrue(serverErr.contains("Server error (HTTP 500)"))
    }

    @Test
    fun `formatHttpError speaks the profile language`() {
        val es = com.seerflix.tv.core.i18n.stringsForLanguage("es")
        val notFound = UnifiedCatalogRepository.formatHttpError(IllegalStateException("HTTP 404 Not Found"), strings = es)
        assertEquals("El elemento no se encontró en el servidor (HTTP 404)", notFound)
        val server = UnifiedCatalogRepository.formatHttpError(IllegalStateException("HTTP 503"), strings = es)
        assertTrue(server.contains("HTTP 503"))
        // Anything else is still the raw message, or the caller's fallback.
        assertEquals("boom", UnifiedCatalogRepository.formatHttpError(IllegalStateException("boom"), "x", es))
        assertEquals("x", UnifiedCatalogRepository.formatHttpError(IllegalStateException(""), "x", es))
    }
}
