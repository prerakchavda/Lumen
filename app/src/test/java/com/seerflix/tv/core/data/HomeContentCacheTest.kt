package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.ContentRatingLimit
import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HomeContentCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val base = "http://jf.lan:8096"

    private val profile = UserProfile(id = "p1", name = "Adult", jellyfinUserId = "u1", jellyfinToken = "tok")

    private fun key(p: UserProfile = profile, tab: String = "HOME") = HomeContentCache.keyFor(
        profile = p,
        tab = tab,
        genreId = "all",
        availability = "ALL",
        seerrConfigured = true,
        jellyfinServer = "http://jf:8096",
        seerrServer = "http://seerr:5055",
        metadataLanguage = "en"
    )

    private val item = UnifiedMediaItem(
        id = "tmdb_MOVIE_1",
        tmdbId = 1,
        jellyfinId = "jf1",
        mediaType = MediaType.MOVIE,
        title = "Dune",
        overview = "Spice",
        maturityRating = "PG-13",
        posterUrl = "$base/Items/jf1/Images/Primary?maxWidth=340&quality=82",
        backdropUrl = "https://image.tmdb.org/t/p/w780/x.jpg",
        hdStatus = AvailabilityTier.AVAILABLE,
        hdDownload = DownloadProgressInfo(false, "downloading", "Dune", 100, 50, 50f, "1h", null),
        resumePositionTicks = 42L,
        mediaBadges = listOf("4K"),
        isTop10 = true,
        netflixBadgeLabel = "Recently Added",
        youtubeTrailerKey = "abc"
    )

    private val rows = listOf(CatalogRow("todays_top_picks", "Today's Top Picks for You", RowStyle.POSTER, listOf(item)))

    private fun newCache(dir: File = File(tempFolder.root, "home")) = HomeContentCache { dir }

    @Test
    fun `rows round-trip through disk with card fields intact`() {
        newCache().write(key(), rows, base)
        val restored = newCache().read(key(), base)
        assertEquals(rows, restored)
        val restoredItem = restored!!.single().items.single()
        assertEquals(true, restoredItem.isTop10)
        assertEquals("Recently Added", restoredItem.netflixBadgeLabel)
    }

    @Test
    fun `a different tab, profile or rating limit misses the cache`() {
        val cache = newCache()
        cache.write(key(), rows, base)
        assertNull(newCache().read(key(tab = "MOVIES"), base))
        assertNull(newCache().read(key(p = profile.copy(id = "p2")), base))
        assertNull(newCache().read(key(p = profile.copy(maxContentRating = ContentRatingLimit.PG)), base))
        assertNull(newCache().read(key(p = profile.copy(hiddenHomeRowIds = listOf("trending_now"))), base))
    }

    @Test
    fun `corrupt file is ignored and removed`() {
        val dir = File(tempFolder.root, "home")
        val cache = newCache(dir)
        cache.write(key(), rows, base)
        val file = dir.listFiles()!!.single { it.name.endsWith(".json") }
        file.writeText("{not json")
        assertNull(newCache(dir).read(key(), base))
        assertEquals(false, file.exists())
    }

    @Test
    fun `jellyfin image urls are rebased onto the address currently in use`() {
        newCache().write(key(), rows, base)
        val restored = newCache().read(key(), "https://jf.example.com/")
        assertNotNull(restored)
        val restoredItem = restored!!.single().items.single()
        assertEquals("https://jf.example.com/Items/jf1/Images/Primary?maxWidth=340&quality=82", restoredItem.posterUrl)
        assertEquals(item.backdropUrl, restoredItem.backdropUrl)
    }

    @Test
    fun `rows and items are capped`() {
        val many = (1..50).map { r ->
            CatalogRow("row$r", "Row $r", RowStyle.POSTER, (1..80).map { item.copy(id = "i$it") })
        }
        newCache().write(key(), many, base)
        val restored = newCache().read(key(), base)!!
        assertEquals(HomeContentCache.MAX_ROWS, restored.size)
        assertEquals(HomeContentCache.MAX_ITEMS_PER_ROW, restored.first().items.size)
    }
}
