package com.seerflix.tv.core.model

import com.seerflix.tv.core.i18n.stringsForLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParentalControlsAndConfigTest {

    @Test
    fun `ServerConfig supports Jellyfin-Only mode and orders local candidates first`() {
        val jfOnly = ServerConfig(
            jellyfinUrl = "https://jf.remote.com",
            jellyfinLocalUrl = "http://192.168.1.50:8096",
            seerrUrl = "",
            seerrLocalUrl = ""
        )
        assertTrue(jfOnly.isJellyfinConfigured)
        assertFalse(jfOnly.isSeerrConfigured)
        assertTrue(jfOnly.isConfigured)
        assertEquals(
            listOf("http://192.168.1.50:8096", "https://jf.remote.com"),
            jfOnly.jellyfinCandidates
        )
        assertTrue(jfOnly.seerrCandidates.isEmpty())
    }

    @Test
    fun `UserProfile Kids Mode defaults rating limit to PG and locks requests unless enabled`() {
        val defaultKid = UserProfile(
            id = "k1",
            name = "Mia",
            jellyfinUserId = "jf_k1",
            jellyfinToken = "tok",
            kidsMode = true,
            maxContentRating = ContentRatingLimit.ALL
        )
        assertEquals(ContentRatingLimit.PG, defaultKid.effectiveRatingLimit)
        assertFalse(defaultKid.canSubmitRequests)

        val gKidWithReq = defaultKid.copy(
            maxContentRating = ContentRatingLimit.G,
            allowKidsRequests = true
        )
        assertEquals(ContentRatingLimit.G, gKidWithReq.effectiveRatingLimit)
        assertTrue(gKidWithReq.canSubmitRequests)
    }

    @Test
    fun `ContentRatingHelper filters adult titles, high maturity ratings, and restricted genres in Kids Mode`() {
        val kidProfile = UserProfile(
            id = "k1",
            name = "Kids",
            jellyfinUserId = "jf_k1",
            jellyfinToken = "tok",
            kidsMode = true,
            maxContentRating = ContentRatingLimit.PG
        )
        val items = listOf(
            UnifiedMediaItem(id = "1", mediaType = MediaType.MOVIE, title = "Inside Out", maturityRating = "PG", genres = listOf("Animation", "Family")),
            UnifiedMediaItem(id = "2", mediaType = MediaType.MOVIE, title = "Dune", maturityRating = "PG-13", genres = listOf("Sci-Fi")),
            UnifiedMediaItem(id = "3", mediaType = MediaType.MOVIE, title = "Late Night Scare", maturityRating = "PG", genres = listOf("Horror")),
            UnifiedMediaItem(id = "4", mediaType = MediaType.MOVIE, title = "Adult Title", maturityRating = "PG", isAdult = true),
            UnifiedMediaItem(id = "5", mediaType = MediaType.MOVIE, title = "Frozen", maturityRating = "G", genres = listOf("Family"))
        )

        val filtered = ContentRatingHelper.filterForProfile(items, kidProfile)
        assertEquals(listOf("Inside Out", "Frozen"), filtered.map { it.title })
    }

    @Test
    fun `stringsForLanguage provides localized UI strings for supported locales`() {
        assertEquals("Home", stringsForLanguage("en").navHome)
        assertEquals("Inicio", stringsForLanguage("es").navHome)
        assertEquals("Accueil", stringsForLanguage("fr").navHome)
        assertEquals("Startseite", stringsForLanguage("de").navHome)
        assertEquals("ホーム", stringsForLanguage("ja").navHome)
        assertEquals("Home", stringsForLanguage("unknown").navHome)
    }
}
