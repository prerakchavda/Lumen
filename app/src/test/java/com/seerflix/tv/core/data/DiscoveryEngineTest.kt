package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinPersonDto
import com.seerflix.tv.core.network.JellyfinUserDataDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DiscoveryEngineTest {

    private fun movie(tmdb: Int, title: String = "M$tmdb", genres: List<String> = emptyList(), jf: String? = null, vote: Double? = 7.0, watched: Boolean = false) =
        UnifiedMediaItem(id = jf ?: "tmdb_$tmdb", tmdbId = tmdb, jellyfinId = jf, mediaType = MediaType.MOVIE, title = title, genres = genres, voteAverage = vote, isWatched = watched)

    private fun show(tmdb: Int, title: String = "S$tmdb", genres: List<String> = emptyList()) =
        UnifiedMediaItem(id = "tmdb_tv_$tmdb", tmdbId = tmdb, mediaType = MediaType.SERIES, title = title, genres = genres)

    private fun dto(
        id: String,
        type: String = "Movie",
        genres: List<String> = emptyList(),
        rating: Double? = 7.0,
        year: Int? = 2015,
        played: Boolean = false,
        position: Long = 0L,
        createdDaysAgo: Long? = null,
        runtimeMin: Long? = 110,
        favorite: Boolean = false,
        lastPlayed: Instant? = null,
        seriesId: String? = null
    ) = JellyfinItemDto(
        id = id,
        name = id,
        type = type,
        genres = genres,
        communityRating = rating,
        productionYear = year,
        runTimeTicks = runtimeMin?.let { it * 600_000_000L },
        dateCreated = createdDaysAgo?.let { NOW.minusSeconds(it * 86_400).toString() },
        seriesId = seriesId,
        userData = JellyfinUserDataDto(
            playbackPositionTicks = position,
            played = played,
            isFavorite = favorite,
            lastPlayedDate = lastPlayed?.toString()
        )
    )

    @Test
    fun `keys match the same title by TMDB id or Jellyfin id and treat episodes as series`() {
        val rated = TasteData().withRating(movie(10, jf = "jf10"), TasteRating.LIKE, 1L)
        assertEquals(TasteRating.LIKE, rated.ratingFor(movie(10)))
        assertEquals(TasteRating.LIKE, rated.ratingFor(movie(99, jf = "jf10")))
        assertNull(rated.ratingFor(show(10)))

        val ep = UnifiedMediaItem(id = "e", tmdbId = 5, mediaType = MediaType.EPISODE, title = "ep")
        val showRated = TasteData().withRating(ep, TasteRating.LOVE, 1L)
        assertEquals(TasteRating.LOVE, showRated.ratingFor(show(5)))
    }

    @Test
    fun `thumbs up clears not interested and clearing a rating removes it`() {
        val item = movie(1)
        val t = TasteData().withNotInterested(item, true, 1L)
        assertTrue(t.isNotInterested(item))
        val liked = t.withRating(item, TasteRating.LIKE, 2L)
        assertFalse(liked.isNotInterested(item))
        assertNull(liked.withRating(item, null, 3L).ratingFor(item))
    }

    @Test
    fun `seeds weight recent history, boost loved titles and never seed disliked ones`() {
        val a = movie(1, genres = listOf("Action"))
        val b = movie(2, genres = listOf("Drama"))
        val c = movie(3, genres = listOf("Horror"))
        val taste = TasteData()
            .withRating(b, TasteRating.LOVE, 1L)
            .withRating(c, TasteRating.DISLIKE, 2L)
        val seeds = DiscoveryEngine.buildSeeds(resume = emptyList(), played = listOf(a, b, c), favorites = emptyList(), taste = taste)

        assertEquals(listOf(2, 1), seeds.map { it.item.tmdbId })
        assertEquals(DiscoveryEngine.SeedReason.LOVED, seeds.first().reason)
    }

    @Test
    fun `genre affinity favors seed genres and subtracts disliked genres`() {
        val seeds = DiscoveryEngine.buildSeeds(
            resume = listOf(movie(1, genres = listOf("Science Fiction", "Action"))),
            played = listOf(movie(2, genres = listOf("Sci-Fi & Fantasy"))),
            favorites = emptyList(),
            taste = TasteData()
        )
        val taste = TasteData().withRating(movie(9, genres = listOf("Romance")), TasteRating.DISLIKE, 1L)
        val affinity = DiscoveryEngine.genreAffinity(seeds, taste)

        assertEquals(1.0, affinity.getValue("sci-fi"), 0.0001)
        assertTrue(affinity.getValue("action") > 0)
        assertTrue(affinity.getValue("romance") < 0)
    }

    @Test
    fun `titles recommended by several seeds rank above single-seed picks`() {
        val s1 = DiscoveryEngine.Seed(movie(1), 1.0, DiscoveryEngine.SeedReason.WATCHED)
        val s2 = DiscoveryEngine.Seed(movie(2), 1.0, DiscoveryEngine.SeedReason.WATCHED)
        val shared = movie(50)
        val ranked = DiscoveryEngine.rankRecommendations(
            perSeed = listOf(
                s1 to listOf(movie(40), shared, movie(2)),
                s2 to listOf(movie(41), shared)
            ),
            affinity = emptyMap(),
            taste = TasteData().withNotInterested(movie(41), true, 1L),
            exclude = emptySet()
        )
        assertEquals(50, ranked.first().tmdbId)
        // A seed is never recommended back; "Not interested" is dropped.
        assertFalse(ranked.any { it.tmdbId == 2 })
        assertFalse(ranked.any { it.tmdbId == 41 })
    }

    @Test
    fun `library rows pick untouched titles by taste, rating, age and runtime`() {
        val index = listOf(
            dto("watched", genres = listOf("Action"), played = true, rating = 9.0),
            dto("started", genres = listOf("Action"), position = 5_000L, rating = 9.0),
            dto("action", genres = listOf("Action"), rating = 6.5, createdDaysAgo = 60, runtimeMin = 95),
            dto("drama", genres = listOf("Drama"), rating = 7.0, createdDaysAgo = 3, runtimeMin = 95),
            dto("gem", genres = listOf("Drama"), rating = 8.1, year = 2001, createdDaysAgo = 400),
            dto("new-gem", genres = listOf("Drama"), rating = 8.5, year = 2025),
            dto("long", genres = listOf("Action"), rating = 6.0, runtimeMin = 170)
        )
        val affinity = mapOf("action" to 1.0)

        val unwatched = DiscoveryEngine.unwatchedInLibrary(index, affinity).map { it.id }
        assertFalse("watched" in unwatched || "started" in unwatched)
        assertEquals("action", unwatched.first())

        assertEquals(listOf("gem"), DiscoveryEngine.hiddenGems(index, affinity, currentYear = 2026).map { it.id })

        val forgotten = DiscoveryEngine.forgottenInLibrary(index, affinity, NOW.toEpochMilli()).map { it.id }
        assertEquals(listOf("action", "gem"), forgotten)

        val short = DiscoveryEngine.shortTonight(index, affinity).map { it.id }
        assertEquals(listOf("action", "drama"), short)
    }

    @Test
    fun `favorite person needs two titles and prefers directors`() {
        val nolan = JellyfinPersonDto(name = "Christopher Nolan", type = "Director", id = "p1")
        val actor = JellyfinPersonDto(name = "Lead", type = "Actor", id = "p2")
        val oneOff = JellyfinPersonDto(name = "Once", type = "Actor", id = "p3")
        val pick = DiscoveryEngine.favoritePerson(
            listOf(
                1.0 to listOf(nolan, actor, oneOff),
                1.0 to listOf(nolan, actor)
            )
        )
        assertEquals("p1", pick?.id)
        assertTrue(pick!!.isDirector)
        assertNull(DiscoveryEngine.favoritePerson(listOf(1.0 to listOf(oneOff))))
    }

    @Test
    fun `household ranks titles watched by more people and counts episodes toward their series`() {
        val recent = NOW.minusSeconds(86_400)
        val users = listOf(
            listOf(dto("ep1", type = "Episode", seriesId = "showA", lastPlayed = recent), dto("m1", lastPlayed = recent)),
            listOf(dto("ep2", type = "Episode", seriesId = "showA", lastPlayed = recent)),
            listOf(dto("old", lastPlayed = NOW.minusSeconds(400L * 86_400)))
        )
        val ranked = DiscoveryEngine.householdPopular(users, NOW.toEpochMilli())
        assertEquals(listOf("showA", "m1"), ranked.map { it.titleId })
        assertEquals(2, ranked.first().viewers)
    }

    @Test
    fun `dedupe drops repeats from later discovery rows but keeps charts and personal rows`() {
        val shared = (1..8).map { movie(it) }
        val rows = listOf(
            CatalogRow("continue_watching", "CW", RowStyle.CONTINUE_WATCHING, shared.take(2)),
            CatalogRow("todays_top_picks", "Picks", RowStyle.POSTER, shared),
            CatalogRow("top10_movies_now", "Top 10", RowStyle.TOP_10, shared.take(3)),
            CatalogRow("because_you_watched", "B", RowStyle.POSTER, shared.take(4) + (20..27).map { movie(it) }),
            CatalogRow("hidden_gems", "Gems", RowStyle.POSTER, shared.take(5) + movie(30))
        )
        val out = DiscoveryEngine.dedupeAcrossRows(rows).associateBy { it.id }
        assertEquals(3, out.getValue("top10_movies_now").items.size)
        assertEquals((20..27).toList(), out.getValue("because_you_watched").items.map { it.tmdbId })
        // Too few would be left: the row keeps its own list rather than becoming a stub.
        assertEquals(6, out.getValue("hidden_gems").items.size)
    }

    @Test
    fun `not interested titles leave discovery rows but not My List`() {
        val item = movie(1)
        val taste = TasteData().withNotInterested(item, true, 1L)
        val rows = listOf(
            CatalogRow("my_list", "My List", RowStyle.POSTER, listOf(item)),
            CatalogRow("todays_top_picks", "Picks", RowStyle.POSTER, listOf(item, movie(2)))
        )
        val out = DiscoveryEngine.filterDisliked(rows, taste)
        assertEquals(1, out.first { it.id == "my_list" }.items.size)
        assertEquals(listOf(2), out.first { it.id == "todays_top_picks" }.items.map { it.tmdbId })
    }

    @Test
    fun `rows the profile opens move up while pinned rows stay on top`() {
        val rows = listOf("continue_watching", "a", "b", "c", "d", "because_you_watched_3").map {
            CatalogRow(it, it, RowStyle.POSTER, listOf(movie(1)))
        }
        val opens = TasteData()
            .withRowOpened("because_you_watched")
            .withRowOpened("because_you_watched_2")
            .withRowOpened("because_you_watched")
            .rowOpens
        val ordered = DiscoveryEngine.orderByEngagement(rows, opens).map { it.id }
        assertEquals("continue_watching", ordered.first())
        assertTrue(ordered.indexOf("because_you_watched_3") < 5)
        assertEquals(rows.map { it.id }, DiscoveryEngine.orderByEngagement(rows, emptyMap()).map { it.id })
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-28T12:00:00Z")
    }
}
