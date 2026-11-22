package com.seerflix.tv.core.data

import com.seerflix.tv.core.data.CrossServerMerge.Order
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.network.CrossServerLinks
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinUserDataDto
import com.seerflix.tv.core.network.scopedTo
import com.seerflix.tv.core.network.unscoped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules that turn several Jellyfin servers into one library. */
class CrossServerMergeTest {

    private val friend = "s1a2b3"

    private fun movie(
        id: String,
        name: String,
        tmdb: String? = null,
        imdb: String? = null,
        tvdb: String? = null,
        userData: JellyfinUserDataDto? = null,
        dateCreated: String? = null
    ) = JellyfinItemDto(
        id = id,
        name = name,
        type = "Movie",
        providerIds = buildMap {
            tmdb?.let { put("Tmdb", it) }
            imdb?.let { put("Imdb", it) }
            tvdb?.let { put("Tvdb", it) }
        },
        userData = userData,
        dateCreated = dateCreated
    )

    private fun series(id: String, name: String, tmdb: String? = null, tvdb: String? = null) = JellyfinItemDto(
        id = id,
        name = name,
        type = "Series",
        providerIds = buildMap {
            tmdb?.let { put("Tmdb", it) }
            tvdb?.let { put("Tvdb", it) }
        }
    )

    private fun episode(
        id: String,
        seriesId: String,
        season: Int,
        number: Int,
        userData: JellyfinUserDataDto? = null
    ) = JellyfinItemDto(
        id = id,
        name = "S${season}E$number",
        type = "Episode",
        seriesId = seriesId,
        parentIndexNumber = season,
        indexNumber = number,
        userData = userData
    )

    private fun onFriend(vararg items: JellyfinItemDto) = items.toList().scopedTo(friend)

    // ---------------------------------------------------------------- ids

    @Test
    fun `ids from the home server are untouched and ids from another server carry it`() {
        assertEquals("abc", ServerScopedId.scope(ServerScopedId.HOME, "abc"))
        val scoped = ServerScopedId.scope(friend, "abc")
        assertEquals("s1a2b3~abc", scoped)
        assertEquals(friend, ServerScopedId.serverOf(scoped))
        assertEquals("abc", ServerScopedId.rawOf(scoped))
        assertEquals(ServerScopedId.HOME, ServerScopedId.serverOf("abc"))
        assertEquals("abc", ServerScopedId.rawOf("abc"))
        assertTrue(ServerScopedId.isHome("abc"))
        assertFalse(ServerScopedId.isHome(scoped))
        // Scoping twice is a no-op.
        assertEquals(scoped, ServerScopedId.scope(friend, scoped))
    }

    @Test
    fun `scoping an item scopes every id it carries and can be undone`() {
        val ep = episode("ep1", "show1", 1, 2).copy(seasonId = "season1")
        val scoped = ep.scopedTo(friend)
        assertEquals("s1a2b3~ep1", scoped.id)
        assertEquals("s1a2b3~show1", scoped.seriesId)
        assertEquals("s1a2b3~season1", scoped.seasonId)
        assertEquals(ep, scoped.unscoped())
        // The home server's items are passed through as they are.
        assertTrue(ep.scopedTo(ServerScopedId.HOME) === ep)
    }

    // ---------------------------------------------------------------- movies

    @Test
    fun `a movie on both servers is listed once and the home copy is the one shown`() {
        val home = listOf(movie("h-matrix", "The Matrix", tmdb = "603"), movie("h-heat", "Heat", tmdb = "949"))
        val other = onFriend(movie("f-matrix", "Matrix, The", tmdb = "603"), movie("f-dune", "Dune", tmdb = "438631"))

        val merged = CrossServerMerge.mergeLibrary(listOf(home, other))

        assertEquals(listOf("h-matrix", "h-heat", "s1a2b3~f-dune"), merged.items.map { it.id })
        assertEquals("The Matrix", merged.items.first().name)
        // The friend's copy is remembered as an alternate of the home copy.
        assertEquals("h-matrix", merged.links.canonical("s1a2b3~f-matrix"))
        assertEquals(listOf("h-matrix", "s1a2b3~f-matrix"), merged.links.copiesOf("h-matrix"))
        assertEquals(listOf("h-matrix", "s1a2b3~f-matrix"), merged.links.copiesOf("s1a2b3~f-matrix"))
        assertTrue(merged.links.hasCopies("h-matrix"))
        assertFalse(merged.links.hasCopies("h-heat"))
        assertEquals("s1a2b3~f-dune", merged.links.canonical("s1a2b3~f-dune"))
    }

    @Test
    fun `titles match by TMDB then IMDb then TVDB`() {
        val home = listOf(
            movie("h-tmdb", "By TMDB", tmdb = "1", imdb = "tt0000001"),
            movie("h-imdb", "By IMDb", imdb = "tt0000002"),
            movie("h-tvdb", "By TVDB", tvdb = "3003"),
            movie("h-none", "No ids")
        )
        val other = onFriend(
            // Same TMDB id, different IMDb id on the other server: TMDB wins.
            movie("f-tmdb", "By TMDB", tmdb = "1", imdb = "tt9999999"),
            // No TMDB id there: falls back to IMDb.
            movie("f-imdb", "By IMDb", tmdb = null, imdb = "TT0000002"),
            // Only TVDB in common.
            movie("f-tvdb", "By TVDB", tvdb = "3003"),
            // Same name but nothing to match on: stays a separate title.
            movie("f-none", "No ids")
        )

        val merged = CrossServerMerge.mergeLibrary(listOf(home, other))

        assertEquals(
            listOf("h-tmdb", "h-imdb", "h-tvdb", "h-none", "s1a2b3~f-none"),
            merged.items.map { it.id }
        )
        assertEquals("h-tmdb", merged.links.canonical("s1a2b3~f-tmdb"))
        assertEquals("h-imdb", merged.links.canonical("s1a2b3~f-imdb"))
        assertEquals("h-tvdb", merged.links.canonical("s1a2b3~f-tvdb"))
    }

    @Test
    fun `a movie and a series with the same provider id are different titles`() {
        val home = listOf(movie("h-movie", "Fargo", tmdb = "275"))
        val other = onFriend(series("f-show", "Fargo", tmdb = "275"))

        val merged = CrossServerMerge.mergeLibrary(listOf(home, other))

        assertEquals(listOf("h-movie", "s1a2b3~f-show"), merged.items.map { it.id })
        assertTrue(merged.links.isEmpty)
    }

    @Test
    fun `two entries of one title on the same server stay separate`() {
        // e.g. a 4K library and an HD library on the home server.
        val home = listOf(movie("h-hd", "Dune", tmdb = "438631"), movie("h-4k", "Dune", tmdb = "438631"))
        val other = onFriend(movie("f-dune", "Dune", tmdb = "438631"))

        val merged = CrossServerMerge.mergeLibrary(listOf(home, other))

        assertEquals(listOf("h-hd", "h-4k"), merged.items.map { it.id })
        assertEquals("h-hd", merged.links.canonical("s1a2b3~f-dune"))
    }

    @Test
    fun `a title only the other server has is kept with its scoped id`() {
        val merged = CrossServerMerge.mergeLibrary(listOf(emptyList(), onFriend(movie("f-only", "Only There", tmdb = "77"))))
        assertEquals(listOf("s1a2b3~f-only"), merged.items.map { it.id })
        assertEquals(friend, ServerScopedId.serverOf(merged.items.single().id))
    }

    // ---------------------------------------------------------------- resume / played

    @Test
    fun `resume position comes from whichever server was played most recently`() {
        val homeOld = JellyfinUserDataDto(playbackPositionTicks = 1_000L, lastPlayedDate = "2026-11-01T20:00:00.0000000Z")
        val friendNew = JellyfinUserDataDto(playbackPositionTicks = 9_000L, lastPlayedDate = "2026-11-10T21:30:00.0000000Z")

        val merged = CrossServerMerge.mergeLibrary(
            listOf(
                listOf(movie("h-matrix", "The Matrix", tmdb = "603", userData = homeOld)),
                onFriend(movie("f-matrix", "The Matrix", tmdb = "603", userData = friendNew))
            )
        )

        // Still the home copy, but with the friend's (newer) position.
        val item = merged.items.single()
        assertEquals("h-matrix", item.id)
        assertEquals(9_000L, item.userData?.playbackPositionTicks)

        // And the other way round: the home server's progress is newer.
        assertEquals(
            1_000L,
            CrossServerMerge.mergeUserData(
                listOf(
                    homeOld.copy(lastPlayedDate = "2026-11-12T08:00:00Z"),
                    friendNew
                )
            )?.playbackPositionTicks
        )
    }

    @Test
    fun `played state follows the most recent play and a copy never played does not count`() {
        val finishedAtFriends = JellyfinUserDataDto(played = true, playbackPositionTicks = 0L, lastPlayedDate = "2026-11-10T21:30:00Z")
        val halfwayAtHome = JellyfinUserDataDto(playbackPositionTicks = 5_000L, lastPlayedDate = "2026-11-02T10:00:00Z")
        val neverPlayed = JellyfinUserDataDto()

        // Finished later on the friend's server: watched, nothing to resume.
        val a = CrossServerMerge.mergeUserData(listOf(halfwayAtHome, finishedAtFriends))!!
        assertTrue(a.played)
        assertEquals(0L, a.playbackPositionTicks)

        // Never touched at home, in progress at the friend's: the friend's progress is used.
        assertEquals(5_000L, CrossServerMerge.mergeUserData(listOf(neverPlayed, halfwayAtHome))?.playbackPositionTicks)
        // Never touched anywhere.
        assertEquals(neverPlayed, CrossServerMerge.mergeUserData(listOf(neverPlayed, neverPlayed)))
        assertNull(CrossServerMerge.mergeUserData(listOf(null, null)))
        // One copy only: returned as is.
        assertTrue(CrossServerMerge.mergeUserData(listOf(halfwayAtHome)) === halfwayAtHome)
    }

    @Test
    fun `a favourite on any server makes the merged title a favourite`() {
        val merged = CrossServerMerge.mergeUserData(
            listOf(
                JellyfinUserDataDto(playbackPositionTicks = 10L, lastPlayedDate = "2026-11-05T10:00:00Z"),
                JellyfinUserDataDto(isFavorite = true)
            )
        )!!
        assertTrue(merged.isFavorite)
        assertEquals(10L, merged.playbackPositionTicks)
    }

    @Test
    fun `Continue Watching shows a title once, newest progress first`() {
        val links = CrossServerMerge.mergeLibrary(
            listOf(
                listOf(movie("h-matrix", "The Matrix", tmdb = "603"), series("h-show", "Show", tmdb = "100")),
                onFriend(movie("f-matrix", "The Matrix", tmdb = "603"), series("f-show", "Show", tmdb = "100"))
            )
        ).links

        val homeResume = listOf(
            movie("h-matrix", "The Matrix", tmdb = "603", userData = JellyfinUserDataDto(100L, lastPlayedDate = "2026-11-03T10:00:00Z")),
            episode("h-s1e2", "h-show", 1, 2, JellyfinUserDataDto(200L, lastPlayedDate = "2026-11-09T10:00:00Z"))
        )
        val friendResume = onFriend(
            movie("f-matrix", "The Matrix", tmdb = "603", userData = JellyfinUserDataDto(900L, lastPlayedDate = "2026-11-11T10:00:00Z")),
            // Same episode of the same (linked) series, older progress.
            episode("f-s1e2", "f-show", 1, 2, JellyfinUserDataDto(50L, lastPlayedDate = "2026-11-01T10:00:00Z")),
            // An episode only in progress there.
            episode("f-s1e3", "f-show", 1, 3, JellyfinUserDataDto(70L, lastPlayedDate = "2026-11-10T10:00:00Z"))
        )

        val merged = CrossServerMerge.mergeItems(listOf(homeResume, friendResume), links, Order.LAST_PLAYED)

        assertEquals(listOf("h-matrix", "s1a2b3~f-s1e3", "h-s1e2"), merged.map { it.id })
        // The movie is the home copy carrying the friend's newer position.
        assertEquals(900L, merged[0].userData?.playbackPositionTicks)
        // The episode keeps the home progress, which is the newer of the two.
        assertEquals(200L, merged[2].userData?.playbackPositionTicks)
    }

    @Test
    fun `lists merge on provider ids even before the library index knows the titles`() {
        val home = listOf(movie("h-new", "Brand New", tmdb = "5000", dateCreated = "2026-11-10T10:00:00Z"))
        val other = onFriend(
            movie("f-new", "Brand New", tmdb = "5000", dateCreated = "2026-11-14T10:00:00Z"),
            movie("f-other", "Other", tmdb = "6000", dateCreated = "2026-11-12T10:00:00Z")
        )

        val latest = CrossServerMerge.mergeItems(listOf(home, other), CrossServerLinks.EMPTY, Order.DATE_CREATED)

        // Shown once (the home copy), placed by the most recent time it was added anywhere.
        assertEquals(listOf("h-new", "s1a2b3~f-other"), latest.map { it.id })
    }

    @Test
    fun `with a single server answering the list is returned exactly as it came`() {
        val home = listOf(movie("b", "B", tmdb = "2"), movie("a", "A", tmdb = "2"))
        assertEquals(home, CrossServerMerge.mergeItems(listOf(home, emptyList()), order = Order.LAST_PLAYED))
        assertEquals(home, CrossServerMerge.mergeItems(listOf(home)))
    }

    // ---------------------------------------------------------------- series

    @Test
    fun `a series on both servers is one title and its seasons are the union of both`() {
        val merged = CrossServerMerge.mergeLibrary(
            listOf(listOf(series("h-show", "Show", tmdb = "100")), onFriend(series("f-show", "Show", tvdb = "9", tmdb = "100")))
        )
        assertEquals(listOf("h-show"), merged.items.map { it.id })

        fun season(id: String, number: Int, count: Int) =
            JellyfinItemDto(id = id, name = "Season $number", type = "Season", indexNumber = number, childCount = count)
        val seasons = CrossServerMerge.mergeSeasons(
            listOf(
                listOf(season("h-s1", 1, 8), season("h-s2", 2, 3)),
                listOf(season("f-s2", 2, 10), season("f-s3", 3, 10)).scopedTo(friend)
            )
        )

        assertEquals(listOf(1, 2, 3), seasons.map { it.item.indexNumber })
        // Season ids come from the home server where it has the season, else from the friend's.
        assertEquals(listOf("h-s1", "h-s2", "s1a2b3~f-s3"), seasons.map { it.item.id })
        // Season 2 is only partly at home: the count is what the fuller server has.
        assertEquals(10, seasons[1].item.childCount)
        assertEquals(listOf("h-s2", "s1a2b3~f-s2"), seasons[1].copies.map { it.id })
    }

    @Test
    fun `episodes missing on one server are filled in from the other`() {
        val home = listOf(
            episode("h-e1", "h-show", 1, 1, JellyfinUserDataDto(played = true, lastPlayedDate = "2026-11-01T10:00:00Z")),
            episode("h-e2", "h-show", 1, 2),
            episode("h-e4", "h-show", 1, 4)
        )
        val other = onFriend(
            episode("f-e2", "f-show", 1, 2, JellyfinUserDataDto(playbackPositionTicks = 300L, lastPlayedDate = "2026-11-08T10:00:00Z")),
            episode("f-e3", "f-show", 1, 3),
            episode("f-e5", "f-show", 1, 5)
        )

        val merged = CrossServerMerge.mergeEpisodes(listOf(home, other))

        assertEquals(listOf(1, 2, 3, 4, 5), merged.map { it.item.indexNumber })
        assertEquals(
            listOf("h-e1", "h-e2", "s1a2b3~f-e3", "h-e4", "s1a2b3~f-e5"),
            merged.map { it.item.id }
        )
        // Episode 2 plays from home but resumes where it was left on the friend's server.
        assertEquals(300L, merged[1].item.userData?.playbackPositionTicks)
        assertEquals(listOf("h-e2", "s1a2b3~f-e2"), merged[1].copies.map { it.id })
        assertTrue(merged[0].item.userData?.played == true)
    }

    @Test
    fun `episodes without numbers are kept and never matched`() {
        val home = listOf(JellyfinItemDto(id = "h-x", name = "Extra", type = "Episode", seriesId = "h-show"))
        val other = onFriend(JellyfinItemDto(id = "f-x", name = "Extra", type = "Episode", seriesId = "f-show"))
        assertEquals(2, CrossServerMerge.mergeEpisodes(listOf(home, other)).size)
    }

    // ---------------------------------------------------------------- grid order

    @Test
    fun `library grids interleave servers in the order the servers sorted by`() {
        val byName = CrossServerMerge.browseComparator("SortName", descending = false)!!
        val a = movie("a", "Alien").copy(sortName = "alien")
        val z = movie("z", "Zodiac").copy(sortName = "zodiac")
        assertTrue(byName.compare(a, z) < 0)
        assertTrue(CrossServerMerge.browseComparator("SortName", descending = true)!!.compare(a, z) > 0)

        val byAdded = CrossServerMerge.browseComparator("DateCreated,SortName", descending = true)!!
        val older = movie("o", "Older", dateCreated = "2026-01-01T00:00:00Z")
        val newer = movie("n", "Newer", dateCreated = "2026-11-01T00:00:00Z")
        assertTrue(byAdded.compare(newer, older) < 0)

        val byRating = CrossServerMerge.browseComparator("CommunityRating,SortName", descending = true)!!
        assertTrue(byRating.compare(a.copy(communityRating = 8.5), z.copy(communityRating = 6.0)) < 0)

        // Random has no order to follow.
        assertNull(CrossServerMerge.browseComparator("Random", descending = false))
    }
}
