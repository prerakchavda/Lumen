package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ServerCredentials
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinItemsResponse
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinPlaybackInfoResponse
import com.seerflix.tv.core.network.JellyfinPlaybackProgressRequest
import com.seerflix.tv.core.network.JellyfinServers
import com.seerflix.tv.core.network.JellyfinUserDataDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections

/**
 * The catalog repository and the playback manager against two fake servers: "home" and a friend's
 * server with an overlapping library.
 */
class MultiServerRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val friendId = "s1a2b3"
    private val homeUser = "home-user"
    private val friendUser = "friend-user"

    private lateinit var prefs: SessionPreferences
    private lateinit var servers: JellyfinServers
    private lateinit var repo: UnifiedCatalogRepository
    private lateinit var playback: JellyfinPlaybackManager

    private val home = FakeServer("home")
    private val friend = FakeServer("friend")

    /** One fake Jellyfin server: its library plus a log of what it was asked to do. */
    private class FakeServer(val label: String) {
        val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())
        var library: List<JellyfinItemDto> = emptyList()
        var resume: List<JellyfinItemDto> = emptyList()
        var episodes: Map<String, List<JellyfinItemDto>> = emptyMap()
        var seasons: Map<String, List<JellyfinItemDto>> = emptyMap()
        var failing = false

        private fun item(id: String): JellyfinItemDto =
            (library + resume + episodes.values.flatten()).firstOrNull { it.id == id }
                ?: error("$label has no item $id")

        val api: JellyfinApi = Proxy.newProxyInstance(
            JellyfinApi::class.java.classLoader,
            arrayOf(JellyfinApi::class.java)
        ) { _, method, args ->
            if (failing && method.name !in setOf("toString", "hashCode", "equals")) {
                throw IllegalStateException("$label is down")
            }
            when (method.name) {
                "getLibraryItems" -> {
                    val ids = (args[10] as String?)?.split(",")
                    JellyfinItemsResponse(items = if (ids == null) library else library.filter { it.id in ids })
                }
                "getResumeItems" -> JellyfinItemsResponse(items = resume)
                "getLatestItems" -> emptyList<JellyfinItemDto>()
                "getPlayedItems", "getFavoriteItems", "getCollections", "getUpcomingEpisodes", "searchItems" ->
                    JellyfinItemsResponse()
                "getItemDetails" -> {
                    calls += "details:${args[1]}"
                    item(args[1] as String)
                }
                "getSeasons" -> JellyfinItemsResponse(items = seasons[args[0] as String].orEmpty())
                "getEpisodes" -> JellyfinItemsResponse(items = episodes[args[0] as String].orEmpty())
                "getEpisodesBySeasonNumber" -> JellyfinItemsResponse(
                    items = episodes[args[0] as String].orEmpty().filter { args[2] == null || it.parentIndexNumber == args[2] }
                )
                "getEpisodesLite" -> JellyfinItemsResponse(items = episodes[args[0] as String].orEmpty())
                "getSpecialFeatures", "getLocalTrailers" -> emptyList<JellyfinItemDto>()
                "getPlaybackInfo" -> {
                    calls += "playbackInfo:${args[0]}"
                    JellyfinPlaybackInfoResponse(mediaSources = item(args[0] as String).mediaSources, playSessionId = "ps-$label")
                }
                "markPlayed" -> { calls += "markPlayed:${args[0]}:${args[1]}"; Unit }
                "markUnplayed" -> { calls += "markUnplayed:${args[0]}:${args[1]}"; Unit }
                "reportPlaybackStart" -> { calls += "start:${(args[0] as JellyfinPlaybackProgressRequest).itemId}"; Unit }
                "reportPlaybackProgress" -> { calls += "progress:${(args[0] as JellyfinPlaybackProgressRequest).itemId}"; Unit }
                "reportPlaybackStopped" -> { calls += "stopped:${(args[0] as JellyfinPlaybackProgressRequest).itemId}"; Unit }
                "toString" -> "FakeJellyfin($label)"
                "hashCode" -> label.hashCode()
                "equals" -> false
                else -> error("unexpected call ${method.name} on $label")
            }
        } as JellyfinApi
    }

    private val noSeerr: SeerrApi = Proxy.newProxyInstance(
        SeerrApi::class.java.classLoader,
        arrayOf(SeerrApi::class.java)
    ) { _, method, _ -> error("Seerr is not configured in this test (${method.name})") } as SeerrApi

    private fun source(id: String, bitrate: Long = 8_000_000L) =
        JellyfinMediaSourceDto(id = id, name = id, container = "mkv", bitrate = bitrate, supportsDirectPlay = true)

    private fun movie(id: String, name: String, tmdb: String, userData: JellyfinUserDataDto? = null, rating: String? = null) =
        JellyfinItemDto(
            id = id,
            name = name,
            type = "Movie",
            providerIds = mapOf("Tmdb" to tmdb),
            officialRating = rating,
            userData = userData,
            mediaSources = listOf(source("src-$id"))
        )

    private fun episode(id: String, seriesId: String, number: Int, userData: JellyfinUserDataDto? = null) = JellyfinItemDto(
        id = id,
        name = "Episode $number",
        type = "Episode",
        seriesId = seriesId,
        seriesName = "Show",
        parentIndexNumber = 1,
        indexNumber = number,
        userData = userData,
        mediaSources = listOf(source("src-$id"))
    )

    @Before
    fun setUp() = runBlocking {
        prefs = SessionPreferences(File(tempFolder.root, "multi_repo.preferences_pb"), Json { ignoreUnknownKeys = true })
        prefs.saveServerUrls(jellyfinUrl = "http://home.test:8096", seerrUrl = "")
        prefs.upsertServer(
            JellyfinServer(
                id = friendId,
                name = "Friend",
                remoteUrl = "https://friend.test",
                maxStreamingBitrateBps = JellyfinServer.DEFAULT_EXTRA_SERVER_BITRATE_BPS
            )
        )
        prefs.upsertAndActivateProfile(
            UserProfile(
                id = homeUser,
                name = "Prerak",
                jellyfinUserId = homeUser,
                jellyfinToken = "home-token",
                serverCredentials = mapOf(friendId to ServerCredentials(friendUser, "friend-token"))
            )
        )
        servers = JellyfinServers(prefs, home.api, { friend.api })
        val copies = CrossServerCopies(servers)
        repo = UnifiedCatalogRepository(home.api, noSeerr, prefs, servers = servers, copies = copies)
        playback = JellyfinPlaybackManager(home.api, prefs, servers = servers, copies = copies)

        home.library = listOf(
            movie("matrix", "The Matrix", "603", JellyfinUserDataDto(1_000L, lastPlayedDate = "2026-11-01T10:00:00Z")),
            movie("heat", "Heat", "949"),
            JellyfinItemDto(id = "show", name = "Show", type = "Series", providerIds = mapOf("Tmdb" to "100"))
        )
        friend.library = listOf(
            // Same title, same raw id as at home (ids are only unique per server), newer progress.
            movie("matrix", "The Matrix", "603", JellyfinUserDataDto(9_000L, lastPlayedDate = "2026-11-10T10:00:00Z")),
            movie("dune", "Dune", "438631"),
            movie("saw", "Saw", "176", rating = "R"),
            JellyfinItemDto(id = "fshow", name = "Show", type = "Series", providerIds = mapOf("Tmdb" to "100"))
        )
        home.episodes = mapOf("show" to listOf(episode("h-e1", "show", 1), episode("h-e2", "show", 2)))
        friend.episodes = mapOf(
            "fshow" to listOf(
                episode("f-e2", "fshow", 2, JellyfinUserDataDto(4_000L, lastPlayedDate = "2026-11-12T10:00:00Z")),
                episode("f-e3", "fshow", 3)
            )
        )
    }

    private suspend fun libraryRow(id: String) =
        repo.loadHomeRows(userId = homeUser).firstOrNull { it.id == id }?.items.orEmpty()

    @Test
    fun `Home merges both libraries and shows a shared title once as the home copy`() = runBlocking {
        val movies = libraryRow("jellyfin_movies")

        assertEquals(listOf("The Matrix", "Heat", "Dune", "Saw"), movies.map { it.title })
        assertEquals(listOf("matrix", "heat", "s1a2b3~dune", "s1a2b3~saw"), movies.map { it.jellyfinId })
        // The shared title is the home copy, resuming from the more recent (friend's) position.
        assertEquals(9_000L, movies.first().resumePositionTicks)
        // Artwork comes from the server that owns the item.
        assertTrue(movies[0].posterUrl!!.startsWith("http://home.test:8096/Items/matrix/"))
        assertTrue(movies[2].posterUrl!!.startsWith("https://friend.test/Items/dune/"))
        // The series is one title too.
        assertEquals(listOf("show"), libraryRow("jellyfin_series").map { it.jellyfinId })
        // A title the friend has is "in the library" for TMDB matching.
        assertEquals("s1a2b3~dune", repo.findJellyfinIdForTmdb(438631, MediaType.MOVIE))
        assertEquals("matrix", repo.findJellyfinIdForTmdb(603, MediaType.MOVIE))
    }

    @Test
    fun `Continue Watching merges both servers`() = runBlocking {
        home.resume = listOf(home.library[0])
        friend.resume = listOf(friend.library[0], friend.episodes.getValue("fshow")[0])
        repo.refreshLibraryIndex(homeUser)

        val items = repo.loadContinueWatchingItems(homeUser, activeProfile = null)

        // The friend's episode was played last; the movie appears once with the newer position.
        assertEquals(listOf("s1a2b3~f-e2", "matrix"), items.map { it.jellyfinId })
        assertEquals(9_000L, items[1].resumePositionTicks)
    }

    @Test
    fun `Kids filtering applies to titles from every server`() = runBlocking {
        val kid = UserProfile(
            id = homeUser, name = "Kid", jellyfinUserId = homeUser, jellyfinToken = "home-token",
            kidsMode = true,
            serverCredentials = mapOf(friendId to ServerCredentials(friendUser, "friend-token"))
        )
        val movies = repo.loadHomeRows(userId = homeUser, activeProfile = kid)
            .firstOrNull { it.id == "jellyfin_movies" }?.items.orEmpty()
        assertFalse("the friend's R-rated title is filtered out", movies.any { it.title == "Saw" })
    }

    @Test
    fun `when the other server is down Home still loads from the home server`() = runBlocking {
        friend.failing = true

        val movies = libraryRow("jellyfin_movies")

        assertEquals(listOf("matrix", "heat"), movies.map { it.jellyfinId })
        assertEquals(1_000L, movies.first().resumePositionTicks)
    }

    @Test
    fun `details list the other server's copy as a version and episodes fill each other in`() = runBlocking {
        repo.refreshLibraryIndex(homeUser)

        // Asking for the friend's copy opens the title as the home copy.
        val details = repo.loadFullDetails(homeUser, tmdbId = null, jellyfinId = "s1a2b3~matrix", mediaType = MediaType.MOVIE).getOrThrow()
        assertEquals("matrix", details.jellyfinId)
        assertEquals(9_000L, details.resumePositionTicks)
        assertEquals(listOf("src-matrix", "s1a2b3~src-matrix"), details.mediaVersions.map { it.id })
        assertNull(details.mediaVersions[0].itemId)
        assertEquals("s1a2b3~matrix", details.mediaVersions[1].itemId)
        assertEquals("Friend", details.mediaVersions[1].serverName)
        assertTrue(details.mediaVersions[1].displayLabel.startsWith("Friend"))

        // A title only the friend has: one version, labelled with its server.
        val dune = repo.loadFullDetails(homeUser, tmdbId = null, jellyfinId = "s1a2b3~dune", mediaType = MediaType.MOVIE).getOrThrow()
        assertEquals("s1a2b3~dune", dune.jellyfinId)
        assertEquals(listOf("Friend"), dune.mediaVersions.map { it.serverName })

        // Episodes: 1 and 2 from home, 3 only at the friend's; 2 resumes from the friend's progress.
        val episodes = repo.loadSeasonEpisodes(homeUser, tmdbId = null, jellyfinSeriesId = "show", jellyfinSeasonId = null, seasonNumber = 1)
        assertEquals(listOf("h-e1", "h-e2", "s1a2b3~f-e3"), episodes.map { it.id })
        assertEquals(4_000L, episodes[1].resumePositionTicks)
    }

    @Test
    fun `watched changes go to the server that owns the item`() = runBlocking {
        repo.refreshLibraryIndex(homeUser)

        repo.markWatched(homeUser, "s1a2b3~dune", watched = true).getOrThrow()
        assertEquals(listOf("markPlayed:$friendUser:dune"), friend.calls.filter { it.startsWith("mark") })
        assertTrue(home.calls.none { it.startsWith("mark") })

        // Un-watching a title that is on both servers clears it on both (the newest state wins).
        repo.markWatched(homeUser, "matrix", watched = false).getOrThrow()
        assertTrue(home.calls.contains("markUnplayed:$homeUser:matrix"))
        assertTrue(friend.calls.contains("markUnplayed:$friendUser:matrix"))
    }

    @Test
    fun `playback prefers the home copy, resumes from the newest progress and reports only to the playing server`() = runBlocking {
        repo.refreshLibraryIndex(homeUser)

        // Started from the friend's copy (e.g. its Continue Watching entry): the home copy plays.
        val session = playback.preparePlayback(userId = homeUser, itemId = "s1a2b3~matrix").getOrThrow()
        assertEquals("matrix", session.itemId)
        assertTrue(session.streamUrl.startsWith("http://home.test:8096/Videos/matrix/"))
        assertTrue(session.streamUrl.contains("api_key=home-token"))
        assertEquals(9_000L, session.resumeTicks)
        assertEquals(listOf("src-matrix", "s1a2b3~src-matrix"), session.mediaSources.map { it.id })

        playback.reportStart(session, 9_000L)
        playback.reportProgress(session, 9_500L, isPaused = false)
        playback.reportStopped(session, 9_900L)
        assertEquals(listOf("start:matrix", "progress:matrix", "stopped:matrix"), home.calls.filter { it.substringBefore(":") in setOf("start", "progress", "stopped") })
        assertTrue(friend.calls.none { it.startsWith("start") || it.startsWith("progress") || it.startsWith("stopped") })
    }

    @Test
    fun `picking the other server's version plays that copy with its token and cap and reports there`() = runBlocking {
        repo.refreshLibraryIndex(homeUser)
        home.calls.clear()

        val session = playback.preparePlayback(
            userId = homeUser,
            itemId = "s1a2b3~matrix",
            mediaSourceId = "s1a2b3~src-matrix"
        ).getOrThrow()

        assertEquals("s1a2b3~matrix", session.itemId)
        assertEquals("s1a2b3~src-matrix", session.mediaSourceId)
        assertTrue(session.streamUrl.startsWith("https://friend.test/Videos/matrix/"))
        assertTrue(session.streamUrl.contains("api_key=friend-token"))
        assertFalse(session.streamUrl.contains("home-token"))
        // Still resumes from the newest progress, and still offers the home copy as a version.
        assertEquals(9_000L, session.resumeTicks)
        assertEquals("matrix", session.mediaSources.first { it.itemId != null }.itemId)

        playback.reportStopped(session, 9_900L)
        assertEquals(listOf("stopped:matrix"), friend.calls.filter { it.startsWith("stopped") })
        assertTrue(home.calls.none { it.startsWith("stopped") || it.startsWith("playbackInfo") })
    }

    @Test
    fun `a title only on the other server plays from it and the next episode can come from either server`() = runBlocking {
        repo.refreshLibraryIndex(homeUser)

        val dune = playback.preparePlayback(userId = homeUser, itemId = "s1a2b3~dune").getOrThrow()
        assertEquals("s1a2b3~dune", dune.itemId)
        assertTrue(dune.streamUrl.startsWith("https://friend.test/Videos/dune/"))

        // Episode 2 is at home; episode 3 only exists at the friend's.
        val prepared = playback.preparePlaybackWithExtras(extrasScope = this, userId = homeUser, itemId = "h-e2").getOrThrow()
        assertEquals("h-e2", prepared.session.itemId)
        assertEquals(4_000L, prepared.session.resumeTicks)
        val next = prepared.nextEpisode!!.await()
        assertNotNull(next)
        assertEquals("s1a2b3~f-e3", next!!.next?.id)

        // Playing the series picks the most recently watched in-progress episode: the home copy of E2.
        val fromSeries = playback.preparePlayback(userId = homeUser, itemId = "show").getOrThrow()
        assertEquals("h-e2", fromSeries.itemId)
        assertEquals(4_000L, fromSeries.resumeTicks)
    }

    @Test
    fun `the home server keeps its behaviour and the other server gets its bitrate cap`() {
        assertNull(prefs.serverById(ServerScopedId.HOME)?.maxStreamingBitrateBps)
        assertEquals(20_000_000L, prefs.serverById(friendId)?.maxStreamingBitrateBps)
        // Uncapped (home) profile vs capped (friend) profile sent with PlaybackInfo.
        assertEquals(120_000_000L, JellyfinPlaybackManager.buildDeviceProfile(maxBitrateBps = null).maxStreamingBitrate)
        assertEquals(20_000_000L, JellyfinPlaybackManager.buildDeviceProfile(maxBitrateBps = 20_000_000L).maxStreamingBitrate)
    }
}
