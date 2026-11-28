package com.seerflix.tv.release

import com.seerflix.tv.core.model.ArrQualityProfile
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.ContentRatingHelper
import com.seerflix.tv.core.network.SeerrKeyMode
import com.seerflix.tv.core.network.seerrKeyMode
import com.seerflix.tv.feature.home.mergeRowOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseOneFixesTest {

    @Test
    fun `Seerr calls act as the profile's own Seerr user when it is known`() {
        assertEquals(SeerrKeyMode.AsUser(7), seerrKeyMode(null, hasApiKey = true, seerrUserId = 7, profileRestricted = false))
        assertEquals(SeerrKeyMode.AsUser(7), seerrKeyMode(null, hasApiKey = true, seerrUserId = 7, profileRestricted = true))
    }

    @Test
    fun `restricted profiles never act as the Seerr admin`() {
        assertEquals(SeerrKeyMode.NONE, seerrKeyMode(null, hasApiKey = true, seerrUserId = null, profileRestricted = true))
        assertEquals(SeerrKeyMode.ADMIN, seerrKeyMode(null, hasApiKey = true, seerrUserId = null, profileRestricted = false))
    }

    @Test
    fun `session-only and admin markers override the profile identity`() {
        assertEquals(SeerrKeyMode.NONE, seerrKeyMode("1", hasApiKey = true, seerrUserId = 7, profileRestricted = false))
        assertEquals(SeerrKeyMode.ADMIN, seerrKeyMode("0", hasApiKey = true, seerrUserId = 7, profileRestricted = false))
        assertEquals(SeerrKeyMode.NONE, seerrKeyMode("0", hasApiKey = false, seerrUserId = 7, profileRestricted = false))
    }

    @Test
    fun `customize home keeps the order of rows the dialog did not show`() {
        val saved = listOf("continue_watching", "trending", "top10", "genre_action", "my_list")
        // Opened from Movies: only trending and genre_action were listed, and the user swapped them.
        assertEquals(
            listOf("continue_watching", "genre_action", "top10", "trending", "my_list"),
            mergeRowOrder(saved, listOf("genre_action", "trending"))
        )
        // New rows the saved order never had go at the end.
        assertEquals(
            listOf("continue_watching", "trending", "top10", "genre_action", "my_list", "next_up"),
            mergeRowOrder(saved, listOf("trending", "next_up"))
        )
        assertEquals(listOf("a", "b"), mergeRowOrder(emptyList(), listOf("a", "b", "a")))
    }

    @Test
    fun `unknown and provider-style ratings fail closed`() {
        assertEquals(ContentRatingHelper.UNRATED_RANK, ContentRatingHelper.ratingRank("Approved"))
        assertEquals(ContentRatingHelper.UNRATED_RANK, ContentRatingHelper.ratingRank("Not Rated"))
        assertEquals(4, ContentRatingHelper.ratingRank("Rated R"))
        assertEquals(4, ContentRatingHelper.ratingRank("US:R"))
        assertEquals(2, ContentRatingHelper.ratingRank("Rated PG"))
        assertEquals(3, ContentRatingHelper.ratingRank("PG-13"))
        assertEquals(1, ContentRatingHelper.ratingRank("TV-Y7"))
    }

    @Test
    fun `request dialog defaults to the Seerr admin's profile and root folder`() {
        val server = ArrServerOption(
            id = 1, name = "Radarr", is4k = false, isDefault = true,
            profiles = listOf(ArrQualityProfile(1, "Any"), ArrQualityProfile(4, "HD-1080p")),
            rootFolders = listOf("/movies", "/movies-kids"),
            defaultProfileId = 4,
            defaultRootFolder = "/movies-kids"
        )
        assertEquals(4, server.initialProfileId)
        assertEquals("/movies-kids", server.initialRootFolder)
        val stale = server.copy(defaultProfileId = 99, defaultRootFolder = "/gone")
        assertEquals(1, stale.initialProfileId)
        assertEquals("/movies", stale.initialRootFolder)
    }
}
