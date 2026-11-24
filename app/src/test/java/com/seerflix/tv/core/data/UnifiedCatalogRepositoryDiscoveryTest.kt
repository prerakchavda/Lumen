package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinAuthRequest
import com.seerflix.tv.core.network.JellyfinAuthResult
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinItemsResponse
import com.seerflix.tv.core.network.JellyfinMediaSegmentsResponse
import com.seerflix.tv.core.network.JellyfinPlaybackInfoRequest
import com.seerflix.tv.core.network.JellyfinPlaybackInfoResponse
import com.seerflix.tv.core.network.JellyfinPlaybackProgressRequest
import com.seerflix.tv.core.network.JellyfinPublicSystemInfo
import com.seerflix.tv.core.network.JellyfinQuickConnectAuthRequest
import com.seerflix.tv.core.network.JellyfinQuickConnectState
import com.seerflix.tv.core.network.JellyfinRemoteSubtitleDto
import com.seerflix.tv.core.network.JellyfinUpdateUserDataRequest
import com.seerflix.tv.core.network.JellyfinUserDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrArrServerDto
import com.seerflix.tv.core.network.SeerrBelongsToCollectionDto
import com.seerflix.tv.core.network.SeerrCollectionDetailsDto
import com.seerflix.tv.core.network.SeerrCreateRequestBody
import com.seerflix.tv.core.network.SeerrDiscoverPageDto
import com.seerflix.tv.core.network.SeerrJellyfinLoginRequest
import com.seerflix.tv.core.network.SeerrMediaInfoDto
import com.seerflix.tv.core.network.SeerrMediaResultDto
import com.seerflix.tv.core.network.SeerrMovieDetailsDto
import com.seerflix.tv.core.network.SeerrPersonCreditsDto
import com.seerflix.tv.core.network.SeerrPersonDetailsDto
import com.seerflix.tv.core.network.SeerrQuickConnectAuthRequest
import com.seerflix.tv.core.network.SeerrQuickConnectCheckResponse
import com.seerflix.tv.core.network.SeerrQuickConnectInitiateResponse
import com.seerflix.tv.core.network.SeerrRequestSummaryDto
import com.seerflix.tv.core.network.SeerrRequestsPageDto
import com.seerflix.tv.core.network.SeerrSeasonDetailDto
import com.seerflix.tv.core.network.SeerrStatusDto
import com.seerflix.tv.core.network.SeerrTvDetailsDto
import com.seerflix.tv.core.network.SeerrUserDto
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.network.TraktApi
import com.seerflix.tv.core.network.TraktIdsDto
import com.seerflix.tv.core.network.TraktTitleDto
import com.seerflix.tv.core.network.TraktWatchedMovieDto
import com.seerflix.tv.core.network.TraktWatchedShowDto
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class UnifiedCatalogRepositoryDiscoveryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var prefs: SessionPreferences
    private lateinit var jellyfinApi: FakeJellyfinApi
    private lateinit var seerrApi: FakeSeerrApi
    private lateinit var repository: UnifiedCatalogRepository

    private val fixedToday = LocalDate.of(2026, 4, 1)

    @Before
    fun setUp() {
        prefs = SessionPreferences(
            testFile = tempFolder.newFile("seerflix_test.preferences_pb"),
            json = Json { ignoreUnknownKeys = true }
        )
        kotlinx.coroutines.runBlocking {
            prefs.saveServerUrls(
                jellyfinUrl = "http://jf:8096",
                seerrUrl = "http://seerr:5055"
            )
        }
        jellyfinApi = FakeJellyfinApi()
        seerrApi = FakeSeerrApi()
        repository = UnifiedCatalogRepository(jellyfinApi, seerrApi, prefs)
    }

    @Test
    fun `top 10 movies out right now uses 90 day rolling window and filters unreleased future dates`() = runTest {
        seerrApi.discoverMoviesHandler = { page, sortBy, _, gte, lte, voteCount ->
            assertEquals("popularity.desc", sortBy)
            // Only assert the Top-10 window call (home-release candidate pool uses a wider 150-day window).
            if (gte == "2026-01-01" && lte == "2026-04-01") {
                assertEquals(10, voteCount)
                if (page == 1) {
                    SeerrDiscoverPageDto(
                        page = 1,
                        results = listOf(
                            movieResult(101, "Released Hit", releaseDate = "2026-03-10"),
                            movieResult(102, "Future Leak", releaseDate = "2026-04-15"),
                            movieResult(103, "Borderline Today", releaseDate = "2026-04-01")
                        )
                    )
                } else SeerrDiscoverPageDto()
            } else SeerrDiscoverPageDto()
        }

        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = MediaType.MOVIE,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )

        val topNow = rows.first { it.id == "top10_movies_now" }
        assertEquals("Top 10 Movies Out Right Now", topNow.title)
        assertEquals(RowStyle.TOP_10, topNow.style)
        assertEquals(listOf("Released Hit", "Borderline Today"), topNow.items.map { it.title })
    }

    @Test
    fun `top 10 tv airing now uses returning series and drops unpremiered or undated shows`() = runTest {
        seerrApi.upcomingTvHandler = { throw AssertionError("upcoming TV is premieres from today on, not airing now") }
        seerrApi.airingTvHandler = { page, lte, status ->
            assertEquals("2026-04-01", lte)
            assertEquals("0", status)
            if (page == 1) {
                SeerrDiscoverPageDto(
                    page = 1,
                    results = listOf(
                        tvResult(201, "Airing Show 1", firstAirDate = "2026-02-15"),
                        tvResult(202, "Not Premiered Yet", firstAirDate = "2026-05-01"),
                        tvResult(203, "Airing Show 2", firstAirDate = "2019-03-20"),
                        tvResult(299, "No Date", firstAirDate = "")
                    ) + (204..215).map { id -> tvResult(id, "Popular Series $id", firstAirDate = "2025-11-10") }
                )
            } else SeerrDiscoverPageDto()
        }

        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = MediaType.SERIES,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )

        val topTv = rows.first { it.id == "top10_tv_now" }
        assertEquals(RowStyle.TOP_10, topTv.style)
        assertEquals(10, topTv.items.size)
        assertFalse(topTv.items.any { it.title == "Not Premiered Yet" || it.title == "No Date" })
        assertEquals("Airing Show 1", topTv.items[0].title)
        assertEquals("Airing Show 2", topTv.items[1].title)
        assertEquals("Popular Series 204", topTv.items[2].title)
    }

    @Test
    fun `home hub includes mix of TV and Movie Top 10 rows`() = runTest {
        seerrApi.discoverMoviesHandler = { page, _, _, _, _, _ ->
            if (page == 1) SeerrDiscoverPageDto(results = listOf(movieResult(1, "Now Movie", "2026-03-01")))
            else SeerrDiscoverPageDto()
        }
        seerrApi.airingTvHandler = { page, _, _ ->
            if (page == 1) SeerrDiscoverPageDto(results = listOf(tvResult(2, "Airing Show", "2026-02-01")))
            else SeerrDiscoverPageDto()
        }
        seerrApi.moviesByGenreHandler = { genreId, page ->
            if (page == 1) {
                val title = if (genreId == 35) "Comedy Hit" else "Action Hit"
                SeerrDiscoverPageDto(results = listOf(movieResult(10 + genreId, title, "2025-06-01")))
            } else SeerrDiscoverPageDto()
        }
        seerrApi.tvByGenreHandler = { genreId, page ->
            if (page == 1 && genreId == 10765) {
                SeerrDiscoverPageDto(results = listOf(tvResult(300, "Sci-Fi Series", "2025-07-01")))
            } else SeerrDiscoverPageDto()
        }

        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = null,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )

        val rowIds = rows.map { it.id }
        assertTrue("top10_movies_now" in rowIds)
        assertTrue("top10_tv_now" in rowIds)
        assertTrue("top10_comedy_movies" in rowIds)
        assertTrue("top10_scifi_tv" in rowIds)
        assertTrue("top10_action_movies" in rowIds)
        rows.filter { it.id.startsWith("top10_") }.forEach {
            assertEquals(RowStyle.TOP_10, it.style)
        }
    }

    @Test
    fun `genre filter produces Top 10 and More rows with TOP_10 styling`() = runTest {
        val comedy = GenreFilterOption.ALL_GENRES.first { it.id == "comedy" }
        seerrApi.moviesByGenreHandler = { genreId, page ->
            assertEquals(35, genreId)
            if (page == 1) {
                SeerrDiscoverPageDto(results = (1..14).map { idx ->
                    movieResult(idx, "Comedy Movie $idx", "2025-01-01")
                })
            } else SeerrDiscoverPageDto()
        }
        seerrApi.tvByGenreHandler = { genreId, page ->
            assertEquals(35, genreId)
            if (page == 1) {
                SeerrDiscoverPageDto(results = (101..105).map { idx ->
                    tvResult(idx, "Comedy Show $idx", "2025-01-01")
                })
            } else SeerrDiscoverPageDto()
        }

        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = null,
            genreFilter = comedy,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )

        val topComedyMovies = rows.first { it.id == "genre_top10_movies_comedy" }
        val moreComedyMovies = rows.first { it.id == "genre_more_movies_comedy" }
        val topComedyTv = rows.first { it.id == "genre_top10_tv_comedy" }

        assertEquals(RowStyle.TOP_10, topComedyMovies.style)
        assertEquals(10, topComedyMovies.items.size)
        assertEquals(RowStyle.POSTER, moreComedyMovies.style)
        assertEquals(4, moreComedyMovies.items.size)
        assertEquals(RowStyle.TOP_10, topComedyTv.style)
        assertEquals(5, topComedyTv.items.size)
    }

    @Test
    fun `availabilityFilter REQUESTABLE backfills across pages and excludes owned or pending items`() = runTest {
        jellyfinApi.libraryItems = listOf(
            JellyfinItemDto(id = "jf-owned", name = "Owned Movie", type = "Movie", providerIds = mapOf("Tmdb" to "1"))
        )
        seerrApi.discoverMoviesHandler = { page, _, _, _, _, _ ->
            when (page) {
                1 -> SeerrDiscoverPageDto(
                    page = 1,
                    results = listOf(
                        movieResult(1, "Owned Movie", "2026-03-01"),
                        movieResult(2, "Already Pending", "2026-03-02", hdStatusCode = 2),
                        movieResult(3, "Already Processing", "2026-03-03", hdStatusCode = 3),
                        movieResult(4, "Requestable P1 A", "2026-03-04"),
                        movieResult(5, "Requestable P1 B", "2026-03-05"),
                        movieResult(6, "Requestable P1 C", "2026-03-06")
                    )
                )
                2 -> SeerrDiscoverPageDto(
                    page = 2,
                    results = (7..15).map { id ->
                        movieResult(id, "Requestable P2 $id", "2026-03-10")
                    }
                )
                else -> SeerrDiscoverPageDto()
            }
        }

        val requestableRows = repository.loadHomeRows(
            userId = "u1",
            filterType = MediaType.MOVIE,
            availabilityFilter = AvailabilityFilter.REQUESTABLE,
            today = fixedToday
        )
        val topReq = requestableRows.first { it.id == "top10_movies_now" }
        assertEquals(10, topReq.items.size)
        assertTrue(topReq.items.all { !it.isPlayable && it.canRequestHd })
        assertFalse(topReq.items.any { it.tmdbId in setOf(1, 2, 3) })

        val watchNowRows = repository.loadHomeRows(
            userId = "u1",
            filterType = MediaType.MOVIE,
            availabilityFilter = AvailabilityFilter.WATCH_NOW,
            today = fixedToday
        )
        val topWatchNow = watchNowRows.first { it.id == "top10_movies_now" }
        assertEquals(listOf("Owned Movie"), topWatchNow.items.map { it.title })
        assertTrue(topWatchNow.items.all { it.isPlayable })
    }

    @Test
    fun `genre top 10 rows skip unreleased and undated blockbusters`() = runTest {
        seerrApi.moviesByGenreHandler = { genreId, page ->
            if (page == 1) {
                SeerrDiscoverPageDto(
                    results = listOf(
                        movieResult(900 + genreId, "Hyped Sequel (out in December)", "2026-12-18"),
                        movieResult(800 + genreId, "No Release Date", ""),
                        movieResult(700 + genreId, "Released Hit $genreId", "2025-06-01")
                    )
                )
            } else SeerrDiscoverPageDto()
        }

        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = MediaType.MOVIE,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )

        val action = rows.first { it.id == "top10_action_movies" }
        assertEquals(listOf("Released Hit 28"), action.items.map { it.title })
        val movieGenreCalls = seerrApi.genreDiscoverCalls.filter { it[0] == "movie" }
        assertTrue(movieGenreCalls.isNotEmpty())
        movieGenreCalls.forEach { call ->
            assertEquals("2026-04-01", call[2])
            assertEquals(25, call[3])
        }
    }

    @Test
    fun `trending now leaves unreleased titles to the coming soon rows`() = runTest {
        seerrApi.trendingHandler = {
            SeerrDiscoverPageDto(
                results = listOf(
                    movieResult(1, "Hyped Sequel", "2026-12-18"),
                    movieResult(2, "Out Now", "2026-03-01"),
                    tvResult(3, "Running Show", "2024-01-01")
                )
            )
        }

        val home = repository.loadHomeRows(userId = "u1", availabilityFilter = AvailabilityFilter.ALL, today = fixedToday)
        assertEquals(listOf("Out Now", "Running Show"), home.first { it.id == "trending_now" }.items.map { it.title })

        val newAndPopular = repository.loadNewAndPopular(userId = "u1", today = fixedToday)
        val npTrending = newAndPopular.first { it.id == "np_trending" }
        assertEquals("Trending Now", npTrending.title)
        assertEquals(listOf("Out Now", "Running Show"), npTrending.items.map { it.title })
    }

    @Test
    fun `trakt chart drives top 10 movies in chart order and skips unreleased`() = runTest {
        val trakt = FakeTraktApi().apply {
            movies = (501..515).map { traktMovie(it) }
        }
        seerrApi.movieDetailsHandler = { id ->
            SeerrMovieDetailsDto(
                id = id,
                title = "Chart $id",
                releaseDate = if (id == 502) "2026-12-18" else "2026-02-01",
                posterPath = "/p$id.jpg"
            )
        }
        seerrApi.discoverMoviesHandler = { _, _, _, _, _, _ -> throw AssertionError("Seerr fallback not needed") }
        val repo = repositoryWithTrakt(trakt)

        val rows = repo.loadHomeRows(
            userId = "u1",
            filterType = MediaType.MOVIE,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )

        val top = rows.first { it.id == "top10_movies_now" }
        assertEquals(listOf(501) + (503..511), top.items.map { it.tmdbId })
        assertEquals("2025-2026", trakt.lastMovieYears)
        // One parallel batch of 12 covers the row; the rest of the chart isn't looked up.
        assertEquals((501..512).toSet(), seerrApi.movieDetailsRequested.toSet())
    }

    @Test
    fun `trakt chart is topped up from seerr and seerr takes over when trakt fails`() = runTest {
        seerrApi.discoverMoviesHandler = { page, _, _, _, _, _ ->
            if (page == 1) SeerrDiscoverPageDto(results = (601..615).map { movieResult(it, "Seerr $it", "2026-03-01") })
            else SeerrDiscoverPageDto()
        }
        seerrApi.movieDetailsHandler = { id -> SeerrMovieDetailsDto(id = id, title = "Chart $id", releaseDate = "2026-02-01") }

        val partial = repositoryWithTrakt(FakeTraktApi().apply { movies = listOf(traktMovie(501), traktMovie(502)) })
        val topped = partial.loadHomeRows(userId = "u1", filterType = MediaType.MOVIE, today = fixedToday)
            .first { it.id == "top10_movies_now" }
        assertEquals(listOf(501, 502) + (601..608), topped.items.map { it.tmdbId })

        val failing = repositoryWithTrakt(FakeTraktApi().apply { fail = true })
        val fallback = failing.loadHomeRows(userId = "u1", filterType = MediaType.MOVIE, today = fixedToday)
            .first { it.id == "top10_movies_now" }
        assertEquals((601..610).toList(), fallback.items.map { it.tmdbId })
    }

    @Test
    fun `trakt shows chart asks for running shows only`() = runTest {
        val trakt = FakeTraktApi().apply { shows = (701..712).map { traktShow(it) } }
        seerrApi.tvDetailsHandler = { id -> SeerrTvDetailsDto(id = id, name = "Show $id", firstAirDate = "2024-09-01") }
        seerrApi.airingTvHandler = { _, _, _ -> throw AssertionError("Seerr fallback not needed") }

        val rows = repositoryWithTrakt(trakt).loadHomeRows(
            userId = "u1",
            filterType = MediaType.SERIES,
            today = fixedToday
        )

        assertEquals((701..710).toList(), rows.first { it.id == "top10_tv_now" }.items.map { it.tmdbId })
        assertEquals("returning series", trakt.lastShowStatus)
    }

    private fun repositoryWithTrakt(api: TraktApi) = UnifiedCatalogRepository(
        jellyfinApi,
        seerrApi,
        prefs,
        traktCharts = TraktCharts(api, { tempFolder.newFolder() }, Json { ignoreUnknownKeys = true })
    )

    private fun traktMovie(tmdb: Int) =
        TraktWatchedMovieDto(watcherCount = 1000 - tmdb, movie = TraktTitleDto("Trakt $tmdb", 2026, TraktIdsDto(tmdb = tmdb)))

    private fun traktShow(tmdb: Int) =
        TraktWatchedShowDto(watcherCount = 1000 - tmdb, show = TraktTitleDto("Trakt $tmdb", 2024, TraktIdsDto(tmdb = tmdb)))

    private class FakeTraktApi : TraktApi {
        var movies: List<TraktWatchedMovieDto> = emptyList()
        var shows: List<TraktWatchedShowDto> = emptyList()
        var fail = false
        var lastMovieYears: String? = null
        var lastShowStatus: String? = null

        override suspend fun watchedMovies(period: String, limit: Int, years: String?): List<TraktWatchedMovieDto> {
            if (fail) throw java.io.IOException("trakt down")
            lastMovieYears = years
            return movies
        }

        override suspend fun watchedShows(period: String, limit: Int, status: String?): List<TraktWatchedShowDto> {
            if (fail) throw java.io.IOException("trakt down")
            lastShowStatus = status
            return shows
        }
    }

    @Test
    fun `submitRequest auto resolves non-special TV seasons when seasons param is omitted`() = runTest {
        seerrApi.tvDetailsHandler = { tvId ->
            SeerrTvDetailsDto(
                id = tvId,
                name = "Severance",
                seasons = listOf(
                    SeerrSeasonDetailDto(seasonNumber = 0, name = "Specials"),
                    SeerrSeasonDetailDto(seasonNumber = 1, name = "Season 1"),
                    SeerrSeasonDetailDto(seasonNumber = 2, name = "Season 2")
                )
            )
        }

        val result = repository.submitRequest(tmdbId = 95396, mediaType = MediaType.SERIES, is4k = false, seasons = null)
        assertTrue(result.isSuccess)
        val captured = seerrApi.createdRequests.singleOrNull()
        assertNotNull(captured)
        assertEquals("tv", captured!!.mediaType)
        assertEquals(95396, captured.mediaId)
        assertFalse(captured.is4k)
        assertEquals(listOf(1, 2), captured.seasons)
    }

    @Test
    fun `loadBoxSetDetails merges Jellyfin BoxSet with missing Seerr saga entries in chronological order`() = runTest {
        jellyfinApi.libraryItems = listOf(
            JellyfinItemDto(id = "jf_jw1", name = "John Wick", type = "Movie", providerIds = mapOf("Tmdb" to "245891"), productionYear = 2014),
            JellyfinItemDto(id = "jf_jw2", name = "John Wick: Chapter 2", type = "Movie", providerIds = mapOf("Tmdb" to "324552"), productionYear = 2017)
        )
        jellyfinApi.collectionItemsMap["box_jw"] = jellyfinApi.libraryItems
        seerrApi.movieDetailsHandler = { movieId ->
            SeerrMovieDetailsDto(
                id = movieId,
                title = "Movie $movieId",
                collection = SeerrBelongsToCollectionDto(id = 404609, name = "John Wick Collection")
            )
        }
        seerrApi.collectionDetailsHandler = { colId ->
            SeerrCollectionDetailsDto(
                id = colId,
                name = "John Wick Collection",
                parts = listOf(
                    movieResult(458156, "John Wick: Chapter 3 - Parabellum", "2019-05-15"),
                    movieResult(245891, "John Wick", "2014-10-22"),
                    movieResult(603692, "John Wick: Chapter 4", "2023-03-22"),
                    movieResult(324552, "John Wick: Chapter 2", "2017-02-08")
                )
            )
        }

        val summary = repository.loadBoxSetDetails("u1", "box_jw", "John Wick Collection")
        assertNotNull(summary)
        assertEquals(2, summary!!.ownedCount)
        assertEquals(4, summary.totalCount)
        assertEquals("2 of 4 in Library", summary.completionLabel)
        assertEquals(
            listOf("John Wick", "John Wick: Chapter 2", "John Wick: Chapter 3 - Parabellum", "John Wick: Chapter 4"),
            summary.items.map { it.title }
        )
        assertTrue(summary.items[0].isPlayable)
        assertTrue(summary.items[1].isPlayable)
        assertFalse(summary.items[2].isPlayable)
        assertTrue(summary.items[2].canRequestHd)
    }

    @Test
    fun `home rows include Ready to Watch reminder alert and New Episodes Coming This Week`() = runTest {
        prefs.toggleReminder("u1", tmdbId = 555, mediaType = MediaType.MOVIE)
        jellyfinApi.libraryItems = listOf(
            JellyfinItemDto(id = "jf_reminded", name = "Reminded Movie", type = "Movie", providerIds = mapOf("Tmdb" to "555"))
        )
        jellyfinApi.upcomingEpisodes = listOf(
            JellyfinItemDto(
                id = "ep_next",
                name = "The Finale",
                type = "Episode",
                seriesId = "jf_series_1",
                seriesName = "Severance",
                parentIndexNumber = 2,
                indexNumber = 10,
                // Midday UTC so the air date is Apr 2 in (almost) any JVM default time zone.
                premiereDate = "2026-04-02T12:00:00Z"
            )
        )

        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = null,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )

        assertEquals("ready_to_watch", rows.first().id)
        assertEquals(listOf("Reminded Movie"), rows.first().items.map { it.title })
        assertEquals("Now in Library", rows.first().items.first().upcomingBadgeLabel)

        val airingRow = rows.first { it.id == "airing_this_week" }
        assertEquals("New Episodes Coming This Week", airingRow.title)
        assertEquals("S2 E10 • Tomorrow", airingRow.items.first().upcomingBadgeLabel)
    }

    private fun ownedMovie(id: String = "jf-owned", tmdb: Int = 1, overview: String? = "Owned overview") =
        JellyfinItemDto(
            id = id,
            name = "Owned Movie",
            type = "Movie",
            overview = overview,
            genres = listOf("Comedy"),
            officialRating = "PG",
            providerIds = mapOf("Tmdb" to tmdb.toString())
        )

    private fun repoWithStore(): Pair<UnifiedCatalogRepository, LibraryIndexStore> {
        val dir = java.io.File(tempFolder.root, "library_index")
        val store = LibraryIndexStore { dir }
        return UnifiedCatalogRepository(jellyfinApi, seerrApi, prefs, store) to store
    }

    @Test
    fun `library index pages past the first 1000 items even when the server omits the total`() = runTest {
        // 2500 owned movies; the oldest ones (last pages) must still be matched as owned.
        jellyfinApi.libraryItems = (1..2500).map { n -> ownedMovie(id = "jf-$n", tmdb = n) }
        val (repo, _) = repoWithStore()
        repo.refreshLibraryIndex("u1")
        assertEquals("jf-1", repo.findJellyfinIdForTmdb(1, MediaType.MOVIE))
        assertEquals("jf-1500", repo.findJellyfinIdForTmdb(1500, MediaType.MOVIE))
        assertEquals("jf-2500", repo.findJellyfinIdForTmdb(2500, MediaType.MOVIE))
    }

    @Test
    fun `library index is persisted and a cold start serves the disk copy without refetching`() = runTest {
        jellyfinApi.libraryItems = listOf(ownedMovie())
        val (first, _) = repoWithStore()
        first.refreshLibraryIndex("u1")
        assertEquals(1, jellyfinApi.indexFetchCount)
        // Trimmed payload: no Overview / MediaStreams / RemoteTrailers in the index fetch.
        assertFalse(jellyfinApi.lastIndexFields.orEmpty().contains("Overview"))

        // "Cold start": a new repository instance, server now unreachable for the index.
        jellyfinApi.failIndexFetch = true
        val (second, _) = repoWithStore()
        val rows = second.loadHomeRows(
            userId = "u1",
            filterType = MediaType.MOVIE,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )
        assertNotNull(rows)
        assertEquals(1, jellyfinApi.indexFetchCount)
        assertEquals("jf-owned", second.findJellyfinIdForTmdb(1, MediaType.MOVIE))
    }

    @Test
    fun `stale disk copy is served immediately by loaders and refreshLibraryIndex revalidates it`() = runTest {
        val (_, store) = repoWithStore()
        store.write(
            userId = "u1",
            server = "http://jf:8096",
            fetchedAtMs = System.currentTimeMillis() - 3_600_000L,
            items = listOf(ownedMovie().toIndexEntry())
        )
        jellyfinApi.libraryItems = emptyList()
        val (repo, _) = repoWithStore()

        repo.loadHomeRows("u1", filterType = MediaType.MOVIE, today = fixedToday)
        // Served from disk; the background revalidation is deferred, so nothing blocked on it.
        assertEquals(0, jellyfinApi.indexFetchCount)
        assertEquals("jf-owned", repo.findJellyfinIdForTmdb(1, MediaType.MOVIE))

        // Pollers that need fresh data still block on a refetch of a stale copy.
        repo.refreshLibraryIndex("u1")
        assertEquals(1, jellyfinApi.indexFetchCount)
        assertEquals(null, repo.findJellyfinIdForTmdb(1, MediaType.MOVIE))
    }

    @Test
    fun `another user's index is never served after a profile switch even if the fetch fails`() = runTest {
        jellyfinApi.libraryItems = listOf(ownedMovie())
        val (repo, _) = repoWithStore()
        repo.refreshLibraryIndex("u1")
        assertEquals("jf-owned", repo.findJellyfinIdForTmdb(1, MediaType.MOVIE))

        jellyfinApi.failIndexFetch = true
        repo.loadHomeRows("u2", filterType = MediaType.MOVIE, today = fixedToday)
        assertEquals(null, repo.findJellyfinIdForTmdb(1, MediaType.MOVIE))
    }

    @Test
    fun `concurrent index requests share a single fetch and forceRefresh refetches`() = runTest {
        jellyfinApi.libraryItems = listOf(ownedMovie())
        jellyfinApi.indexFetchDelayMs = 200L
        val (repo, _) = repoWithStore()
        kotlinx.coroutines.coroutineScope {
            repeat(5) { launch { repo.refreshLibraryIndex("u1") } }
        }
        assertEquals(1, jellyfinApi.indexFetchCount)

        repo.refreshLibraryIndex("u1")
        assertEquals(1, jellyfinApi.indexFetchCount)
        repo.refreshLibraryIndex("u1", forceRefresh = true)
        assertEquals(2, jellyfinApi.indexFetchCount)
    }

    @Test
    fun `genre library row back-fills overviews that the trimmed index does not carry`() = runTest {
        jellyfinApi.libraryItems = listOf(ownedMovie(overview = "A very funny film"))
        val genre = GenreFilterOption(id = "comedy", label = "Comedy", movieGenreId = 35, tvGenreId = 35)
        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = MediaType.MOVIE,
            genreFilter = genre,
            availabilityFilter = AvailabilityFilter.ALL,
            today = fixedToday
        )
        val libraryRow = rows.first { it.id == "genre_library_comedy" }
        assertEquals(listOf("A very funny film"), libraryRow.items.map { it.overview })
        assertEquals(1, jellyfinApi.overviewBackfillCalls)
    }

    @Test
    fun `streaming service top 10 rows query watchProviders and rank combined movies and tv by popularity`() = runTest {
        seerrApi.streamingMoviesHandler = { providers, page ->
            if (page == 1 && providers == StreamingService.NETFLIX.providerIds) {
                SeerrDiscoverPageDto(
                    results = listOf(
                        movieResult(10, "Netflix Movie Hit", "2026-02-10", popularity = 250.0),
                        movieResult(11, "Netflix Unreleased Leak", "2026-06-01", popularity = 999.0)
                    )
                )
            } else if (page == 1 && providers == StreamingService.PRIME_VIDEO.providerIds) {
                SeerrDiscoverPageDto(
                    results = listOf(movieResult(20, "Prime Action", "2026-01-20", popularity = 180.0))
                )
            } else SeerrDiscoverPageDto()
        }
        seerrApi.streamingTvHandler = { providers, page ->
            if (page == 1 && providers == StreamingService.NETFLIX.providerIds) {
                SeerrDiscoverPageDto(
                    results = listOf(
                        tvResult(12, "Netflix Series #1", "2025-11-01", popularity = 420.0),
                        tvResult(13, "Netflix Series #3", "2025-09-01", popularity = 120.0)
                    )
                )
            } else SeerrDiscoverPageDto()
        }

        val homeRows = repository.loadHomeRows(userId = "u1", filterType = null, today = fixedToday)
        val netflixRow = homeRows.first { it.id == StreamingService.NETFLIX.homeRowId }
        assertEquals("Top 10 on Netflix", netflixRow.title)
        assertEquals(RowStyle.TOP_10, netflixRow.style)
        assertEquals(
            listOf("Netflix Series #1", "Netflix Movie Hit", "Netflix Series #3"),
            netflixRow.items.map { it.title }
        )

        val primeRow = homeRows.first { it.id == StreamingService.PRIME_VIDEO.homeRowId }
        assertEquals("Top 10 on Prime Video", primeRow.title)
        assertEquals(listOf("Prime Action"), primeRow.items.map { it.title })

        val movieTabRows = repository.loadHomeRows(userId = "u1", filterType = MediaType.MOVIE, today = fixedToday)
        val netflixMovies = movieTabRows.first { it.id == StreamingService.NETFLIX.homeRowId }
        assertEquals("Top 10 Movies on Netflix", netflixMovies.title)
        assertEquals(listOf("Netflix Movie Hit"), netflixMovies.items.map { it.title })

        val tvTabRows = repository.loadHomeRows(userId = "u1", filterType = MediaType.SERIES, today = fixedToday)
        val netflixTv = tvTabRows.first { it.id == StreamingService.NETFLIX.homeRowId }
        assertEquals("Top 10 TV Shows on Netflix", netflixTv.title)
        assertEquals(listOf("Netflix Series #1", "Netflix Series #3"), netflixTv.items.map { it.title })
    }

    @Test
    fun `theater-only movies are excluded from Top 10 and Trending while Digital and DVD rows surface home releases`() = runTest {
        seerrApi.discoverMoviesHandler = { page, _, _, _, _, _ ->
            if (page == 1) {
                SeerrDiscoverPageDto(
                    results = listOf(
                        movieResult(301, "In Theaters Only", "2026-03-20", popularity = 500.0),
                        movieResult(302, "Just Hit Digital", "2026-02-10", popularity = 300.0),
                        movieResult(303, "Coming to Digital Next Week", "2026-02-20", popularity = 400.0),
                        movieResult(304, "Coming to DVD Next Month", "2026-01-15", popularity = 200.0)
                    )
                )
            } else SeerrDiscoverPageDto()
        }
        seerrApi.trendingHandler = {
            SeerrDiscoverPageDto(
                results = listOf(
                    movieResult(301, "In Theaters Only", "2026-03-20"),
                    movieResult(302, "Just Hit Digital", "2026-02-10")
                )
            )
        }
        seerrApi.movieDetailsHandler = { id ->
            when (id) {
                301 -> movieDetailsWithReleases(301, "In Theaters Only", "2026-03-20", listOf(3 to "2026-03-20T00:00:00.000Z"))
                302 -> movieDetailsWithReleases(
                    302, "Just Hit Digital", "2026-02-10",
                    listOf(3 to "2026-02-10T00:00:00.000Z", 4 to "2026-03-25T00:00:00.000Z")
                )
                303 -> movieDetailsWithReleases(
                    303, "Coming to Digital Next Week", "2026-02-20",
                    listOf(3 to "2026-02-20T00:00:00.000Z", 4 to "2026-04-08T00:00:00.000Z")
                )
                304 -> movieDetailsWithReleases(
                    304, "Coming to DVD Next Month", "2026-01-15",
                    listOf(3 to "2026-01-15T00:00:00.000Z", 5 to "2026-05-05T00:00:00.000Z")
                )
                else -> SeerrMovieDetailsDto(id = id, title = "Movie $id")
            }
        }

        val rows = repository.loadHomeRows(userId = "u1", filterType = MediaType.MOVIE, today = fixedToday)

        val top10 = rows.first { it.id == "top10_movies_now" }
        assertEquals(listOf("Just Hit Digital"), top10.items.map { it.title })

        val trending = rows.first { it.id == "trending_now" }
        assertEquals(listOf("Just Hit Digital"), trending.items.map { it.title })

        val newDigital = rows.first { it.id == "new_on_digital" }
        assertEquals("New on Digital & Streaming", newDigital.title)
        assertEquals(listOf("Just Hit Digital"), newDigital.items.map { it.title })

        val comingHome = rows.first { it.id == "coming_to_streaming_dvd" }
        assertEquals("Coming Soon to Streaming & DVD", comingHome.title)
        assertEquals(
            listOf("Coming to Digital Next Week", "Coming to DVD Next Month"),
            comingHome.items.map { it.title }
        )
        assertEquals("Digital • In 7 days", comingHome.items[0].upcomingBadgeLabel)
        assertEquals("DVD / Blu-ray • May 5", comingHome.items[1].upcomingBadgeLabel)
        assertFalse(comingHome.items.any { it.title == "In Theaters Only" })
    }

    private fun movieDetailsWithReleases(
        id: Int,
        title: String,
        releaseDate: String,
        typedDates: List<Pair<Int, String>>
    ) = SeerrMovieDetailsDto(
        id = id,
        title = title,
        releaseDate = releaseDate,
        releases = com.seerflix.tv.core.network.SeerrReleasesDto(
            results = listOf(
                com.seerflix.tv.core.network.SeerrReleaseCountryDto(
                    countryCode = "US",
                    releaseDates = typedDates.map { (type, iso) ->
                        com.seerflix.tv.core.network.SeerrReleaseDateDto(releaseDate = iso, type = type)
                    }
                )
            )
        )
    )

    private fun movieResult(
        id: Int,
        title: String,
        releaseDate: String,
        hdStatusCode: Int? = null,
        popularity: Double? = null
    ) = SeerrMediaResultDto(
        id = id,
        mediaType = "movie",
        title = title,
        releaseDate = releaseDate,
        popularity = popularity,
        mediaInfo = hdStatusCode?.let { SeerrMediaInfoDto(status = it) }
    )

    private fun tvResult(
        id: Int,
        name: String,
        firstAirDate: String,
        hdStatusCode: Int? = null,
        popularity: Double? = null
    ) = SeerrMediaResultDto(
        id = id,
        mediaType = "tv",
        name = name,
        firstAirDate = firstAirDate,
        popularity = popularity,
        mediaInfo = hdStatusCode?.let { SeerrMediaInfoDto(status = it) }
    )

    private class FakeJellyfinApi : JellyfinApi {
        var libraryItems: List<JellyfinItemDto> = emptyList()
        @Volatile var indexFetchCount = 0
        @Volatile var overviewBackfillCalls = 0
        @Volatile var lastIndexFields: String? = null
        @Volatile var indexFetchDelayMs = 0L
        @Volatile var failIndexFetch = false
        var resumeItems: List<JellyfinItemDto> = emptyList()
        var latestItems: List<JellyfinItemDto> = emptyList()
        var favoriteItems: List<JellyfinItemDto> = emptyList()
        var playedItems: List<JellyfinItemDto> = emptyList()
        var collections: List<JellyfinItemDto> = emptyList()
        val collectionItemsMap: MutableMap<String, List<JellyfinItemDto>> = mutableMapOf()
        var upcomingEpisodes: List<JellyfinItemDto> = emptyList()
        var remoteSubtitles: List<JellyfinRemoteSubtitleDto> = emptyList()

        override suspend fun getPublicSystemInfo() = JellyfinPublicSystemInfo()
        override suspend fun getPublicUsers(): List<JellyfinUserDto> = emptyList()
        override suspend fun authenticateByName(body: JellyfinAuthRequest) =
            JellyfinAuthResult(JellyfinUserDto("u1", "User"), "tok")
        override suspend fun initiateQuickConnect() = JellyfinQuickConnectState("s", "c", false)
        override suspend fun checkQuickConnect(secret: String) = JellyfinQuickConnectState(secret, "c", false)
        override suspend fun authenticateWithQuickConnect(body: JellyfinQuickConnectAuthRequest) =
            JellyfinAuthResult(JellyfinUserDto("u1", "User"), "tok")

        override suspend fun getResumeItems(userId: String, limit: Int, fields: String, mediaTypes: String) =
            JellyfinItemsResponse(resumeItems, resumeItems.size)

        override suspend fun getLatestItems(userId: String, limit: Int, includeItemTypes: String, fields: String) =
            latestItems

        override suspend fun getUserViews(userId: String) = JellyfinItemsResponse()

        override suspend fun getLibraryItems(
            userId: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            sortBy: String,
            sortOrder: String,
            limit: Int,
            parentId: String?,
            maxOfficialRating: String?,
            startIndex: Int?,
            ids: String?,
            enableImages: Boolean?,
            imageTypeLimit: Int?,
            enableImageTypes: String?,
            enableUserData: Boolean?,
            enableTotalRecordCount: Boolean?
        ): JellyfinItemsResponse {
            if (ids != null) {
                overviewBackfillCalls++
                val wanted = ids.split(",").toSet()
                val hits = libraryItems.filter { it.id in wanted }
                return JellyfinItemsResponse(hits, hits.size)
            }
            if (parentId == null) {
                indexFetchCount++
                lastIndexFields = fields
                if (indexFetchDelayMs > 0) kotlinx.coroutines.delay(indexFetchDelayMs)
                if (failIndexFetch) throw java.io.IOException("offline")
            }
            // Page like a real server. With EnableTotalRecordCount=false Jellyfin reports the
            // page size as TotalRecordCount, which once made the index stop after one page.
            val from = (startIndex ?: 0).coerceAtMost(libraryItems.size)
            val page = libraryItems.subList(from, (from + limit).coerceAtMost(libraryItems.size))
            val total = if (enableTotalRecordCount == false) page.size else libraryItems.size
            return JellyfinItemsResponse(page, total)
        }

        override suspend fun getFavoriteItems(
            userId: String,
            filters: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            limit: Int
        ) = JellyfinItemsResponse(favoriteItems, favoriteItems.size)

        override suspend fun getPlayedItems(
            userId: String,
            filters: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            sortBy: String,
            sortOrder: String,
            limit: Int
        ) = JellyfinItemsResponse(playedItems, playedItems.size)

        override suspend fun addFavorite(userId: String, itemId: String) = Unit
        override suspend fun removeFavorite(userId: String, itemId: String) = Unit
        override suspend fun updateUserData(userId: String, itemId: String, body: JellyfinUpdateUserDataRequest) = Unit
        override suspend fun markPlayed(userId: String, itemId: String) = Unit
        override suspend fun markUnplayed(userId: String, itemId: String) = Unit
        override suspend fun searchItems(
            userId: String,
            searchTerm: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            limit: Int
        ) = JellyfinItemsResponse()
        override suspend fun getItemDetails(userId: String, itemId: String, fields: String) =
            libraryItems.first { it.id == itemId }
        override suspend fun getSpecialFeatures(itemId: String, userId: String): List<JellyfinItemDto> = emptyList()
        override suspend fun getSeasons(seriesId: String, userId: String, fields: String) = JellyfinItemsResponse()
        override suspend fun getEpisodes(seriesId: String, userId: String, seasonId: String?, fields: String, isMissing: Boolean?) =
            JellyfinItemsResponse()
        override suspend fun getSimilarItems(itemId: String, userId: String, limit: Int, fields: String) =
            JellyfinItemsResponse()
        override suspend fun getLocalTrailers(itemId: String, userId: String): List<JellyfinItemDto> = emptyList()
        override suspend fun getPlaybackInfo(itemId: String, userId: String, body: JellyfinPlaybackInfoRequest) =
            JellyfinPlaybackInfoResponse()
        override suspend fun getMediaSegments(itemId: String) = JellyfinMediaSegmentsResponse()
        override suspend fun getCollections(
            userId: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            sortBy: String,
            sortOrder: String,
            limit: Int
        ) = JellyfinItemsResponse(collections, collections.size)
        override suspend fun getCollectionItems(
            userId: String,
            parentId: String,
            recursive: Boolean,
            fields: String,
            sortBy: String,
            sortOrder: String,
            limit: Int
        ) = JellyfinItemsResponse(collectionItemsMap[parentId].orEmpty(), collectionItemsMap[parentId]?.size ?: 0)
        override suspend fun getUpcomingEpisodes(userId: String, limit: Int, fields: String) =
            JellyfinItemsResponse(upcomingEpisodes, upcomingEpisodes.size)
        override suspend fun searchRemoteSubtitles(itemId: String, language: String) = remoteSubtitles
        override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String) = Unit
        override suspend fun getLiveTvChannels(userId: String, fields: String, addCurrentProgram: Boolean) = JellyfinItemsResponse()
        override suspend fun getLiveTvPrograms(userId: String, hasAired: Boolean, limit: Int, fields: String) = JellyfinItemsResponse()
        override suspend fun getLiveTvRecordings(userId: String, limit: Int, fields: String) = JellyfinItemsResponse()
        override suspend fun getSyncPlayGroups(): List<com.seerflix.tv.core.network.JellyfinSyncPlayGroupDto> = emptyList()
        override suspend fun createSyncPlayGroup(body: com.seerflix.tv.core.network.JellyfinCreateSyncPlayRequest) = Unit
        override suspend fun joinSyncPlayGroup(body: com.seerflix.tv.core.network.JellyfinJoinSyncPlayRequest) = Unit
        override suspend fun leaveSyncPlayGroup() = Unit
        // 0.7 endpoints (unused by these tests)
        override suspend fun postSessionCapabilities(body: com.seerflix.tv.core.network.JellyfinSessionCapabilitiesRequest) = Unit
        override suspend fun getUtcTime() = com.seerflix.tv.core.network.JellyfinUtcTimeResponse()
        override suspend fun syncPlaySetNewQueue(body: com.seerflix.tv.core.network.JellyfinSyncPlayNewQueueRequest) = Unit
        override suspend fun syncPlayReady(body: com.seerflix.tv.core.network.JellyfinSyncPlayBufferingRequest) = Unit
        override suspend fun syncPlayBuffering(body: com.seerflix.tv.core.network.JellyfinSyncPlayBufferingRequest) = Unit
        override suspend fun syncPlayPing(body: com.seerflix.tv.core.network.JellyfinSyncPlayPingRequest) = Unit
        override suspend fun syncPlayStop() = Unit
        override suspend fun uploadClientLog(body: okhttp3.RequestBody) = com.seerflix.tv.core.network.JellyfinClientLogResponse("test.log")
        override suspend fun getLiveTvTimers(isActive: Boolean?, isScheduled: Boolean?) = com.seerflix.tv.core.network.JellyfinTimersResponse()
        override suspend fun getLiveTvTimerDefaults(programId: String) = kotlinx.serialization.json.JsonObject(emptyMap())
        override suspend fun createLiveTvTimer(body: kotlinx.serialization.json.JsonObject) = Unit
        override suspend fun cancelLiveTvTimer(timerId: String) = Unit
        override suspend fun getLiveTvSeriesTimers() = com.seerflix.tv.core.network.JellyfinTimersResponse()
        override suspend fun createLiveTvSeriesTimer(body: kotlinx.serialization.json.JsonObject) = Unit
        override suspend fun cancelLiveTvSeriesTimer(timerId: String) = Unit
        override suspend fun browseItems(
            userId: String, parentId: String?, includeItemTypes: String, recursive: Boolean, sortBy: String,
            sortOrder: String, filters: String?, genres: String?, years: String?, nameStartsWith: String?,
            nameLessThan: String?, maxOfficialRating: String?, startIndex: Int, limit: Int, fields: String,
            imageTypeLimit: Int, enableImageTypes: String, enableTotalRecordCount: Boolean
        ) = com.seerflix.tv.core.network.JellyfinItemsResponse()
        override suspend fun getGenres(userId: String, parentId: String?, includeItemTypes: String, sortBy: String) =
            com.seerflix.tv.core.network.JellyfinItemsResponse()
        override suspend fun syncPlayPause() = Unit
        override suspend fun syncPlayUnpause() = Unit
        override suspend fun syncPlaySeek(body: com.seerflix.tv.core.network.JellyfinSeekSyncPlayRequest) = Unit
        override suspend fun reportPlaybackStart(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun reportPlaybackProgress(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun reportPlaybackStopped(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun getEpisodesBySeasonNumber(seriesId: String, userId: String, season: Int?, fields: String?, enableImages: Boolean?, isMissing: Boolean?) = JellyfinItemsResponse()
        override suspend fun logout() = Unit
        override suspend fun getEpisodesLite(seriesId: String, userId: String, seasonId: String?, startItemId: String?, limit: Int?, fields: String?, enableImages: Boolean, isMissing: Boolean?) = JellyfinItemsResponse()
        override suspend fun getResumeItemsLite(userId: String, parentId: String?, limit: Int, mediaTypes: String, enableImages: Boolean) = JellyfinItemsResponse()
    }

    private class FakeSeerrApi : SeerrApi {
        var discoverMoviesHandler: (Int, String?, String?, String?, String?, Int?) -> SeerrDiscoverPageDto =
            { _, _, _, _, _, _ -> SeerrDiscoverPageDto() }
        var discoverTvHandler: (Int, String?, String?, String?, String?, Int?) -> SeerrDiscoverPageDto =
            { _, _, _, _, _, _ -> SeerrDiscoverPageDto() }
        var streamingMoviesHandler: (String, Int) -> SeerrDiscoverPageDto = { _, _ -> SeerrDiscoverPageDto() }
        var streamingTvHandler: (String, Int) -> SeerrDiscoverPageDto = { _, _ -> SeerrDiscoverPageDto() }
        var upcomingTvHandler: (Int) -> SeerrDiscoverPageDto = { SeerrDiscoverPageDto() }
        /** `/discover/tv` with a TMDB status (Top 10 TV Airing Now): (page, firstAirDateLte, status). */
        var airingTvHandler: (Int, String?, String?) -> SeerrDiscoverPageDto = { _, _, _ -> SeerrDiscoverPageDto() }
        var trendingHandler: () -> SeerrDiscoverPageDto = { SeerrDiscoverPageDto() }
        /** (type, genre, release-date upper bound, min votes) for every genre discover call. */
        val genreDiscoverCalls = mutableListOf<List<Any?>>()
        val movieDetailsRequested = mutableListOf<Int>()
        var moviesByGenreHandler: (Int, Int) -> SeerrDiscoverPageDto = { _, _ -> SeerrDiscoverPageDto() }
        var tvByGenreHandler: (Int, Int) -> SeerrDiscoverPageDto = { _, _ -> SeerrDiscoverPageDto() }
        var movieDetailsHandler: (Int) -> SeerrMovieDetailsDto = { id -> SeerrMovieDetailsDto(id = id, title = "Movie $id") }
        var tvDetailsHandler: (Int) -> SeerrTvDetailsDto = { id -> SeerrTvDetailsDto(id = id, name = "Show $id") }
        var collectionDetailsHandler: (Int) -> SeerrCollectionDetailsDto = { id -> SeerrCollectionDetailsDto(id = id, name = "Collection $id") }
        val createdRequests = mutableListOf<SeerrCreateRequestBody>()

        override suspend fun getStatus() = SeerrStatusDto()
        override suspend fun getMe() = SeerrUserDto(1)
        override suspend fun loginWithJellyfin(body: SeerrJellyfinLoginRequest) = SeerrUserDto(1)
        override suspend fun initiateQuickConnect() = SeerrQuickConnectInitiateResponse("c", "s")
        override suspend fun checkQuickConnect(secret: String) = SeerrQuickConnectCheckResponse(false)
        override suspend fun authenticateQuickConnect(body: SeerrQuickConnectAuthRequest) = SeerrUserDto(1)
        override suspend fun getTrending(page: Int, language: String) = trendingHandler()

        override suspend fun getDiscoverMovies(
            page: Int,
            language: String,
            sortBy: String?,
            genre: String?,
            primaryReleaseDateGte: String?,
            primaryReleaseDateLte: String?,
            voteCountGte: Int?,
            watchRegion: String?,
            watchProviders: String?
        ) = when {
            watchProviders != null -> streamingMoviesHandler(watchProviders, page)
            genre != null -> {
                genreDiscoverCalls += listOf("movie", genre, primaryReleaseDateLte, voteCountGte)
                moviesByGenreHandler(genre.toInt(), page)
            }
            else -> discoverMoviesHandler(page, sortBy, genre, primaryReleaseDateGte, primaryReleaseDateLte, voteCountGte)
        }

        override suspend fun getDiscoverTv(
            page: Int,
            language: String,
            sortBy: String?,
            genre: String?,
            firstAirDateGte: String?,
            firstAirDateLte: String?,
            voteCountGte: Int?,
            status: String?,
            watchRegion: String?,
            watchProviders: String?
        ) = when {
            watchProviders != null -> streamingTvHandler(watchProviders, page)
            genre != null -> {
                genreDiscoverCalls += listOf("tv", genre, firstAirDateLte, voteCountGte)
                tvByGenreHandler(genre.toInt(), page)
            }
            status != null -> airingTvHandler(page, firstAirDateLte, status)
            else -> discoverTvHandler(page, sortBy, genre, firstAirDateGte, firstAirDateLte, voteCountGte)
        }

        override suspend fun getUpcomingMovies(page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun getUpcomingTv(page: Int, language: String) = upcomingTvHandler(page)
        // The genre endpoints have no release-date bound; Top 10 rows must not use them.
        override suspend fun getMoviesByGenre(genreId: Int, page: Int, language: String): SeerrDiscoverPageDto =
            throw AssertionError("undated genre endpoint used")
        override suspend fun getTvByGenre(genreId: Int, page: Int, language: String): SeerrDiscoverPageDto =
            throw AssertionError("undated genre endpoint used")
        override suspend fun getMovieDetails(movieId: Int, language: String): SeerrMovieDetailsDto {
            movieDetailsRequested += movieId
            return movieDetailsHandler(movieId)
        }
        override suspend fun getTvDetails(tvId: Int, language: String) = tvDetailsHandler(tvId)
        override suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int, language: String) =
            SeerrSeasonDetailDto(seasonNumber = seasonNumber)
        override suspend fun getCollectionDetails(collectionId: Int, language: String) =
            collectionDetailsHandler(collectionId)
        override suspend fun getMovieRecommendations(movieId: Int, page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun getTvRecommendations(tvId: Int, page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun search(query: String, page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun getPersonDetails(personId: Int, language: String) =
            SeerrPersonDetailsDto(id = personId, name = "Person $personId")
        override suspend fun getPersonCredits(personId: Int, language: String) = SeerrPersonCreditsDto()
        override suspend fun createRequest(body: SeerrCreateRequestBody): SeerrRequestSummaryDto {
            createdRequests += body
            return SeerrRequestSummaryDto(id = 1, status = 1, is4k = body.is4k)
        }
        override suspend fun approveRequest(requestId: Int) = SeerrRequestSummaryDto(id = requestId, status = 2)
        override suspend fun declineRequest(requestId: Int) = SeerrRequestSummaryDto(id = requestId, status = 3)
        override suspend fun getRequests(take: Int, skip: Int, filter: String, sort: String) = SeerrRequestsPageDto()
        override suspend fun deleteRequest(requestId: Int) = Unit
        override suspend fun createIssue(body: com.seerflix.tv.core.network.SeerrCreateIssueBody) = Unit
        override suspend fun getRadarrServers(): List<SeerrArrServerDto> = emptyList()
        override suspend fun getRadarrServerDetails(serverId: Int) = com.seerflix.tv.core.network.SeerrArrServerDetailsResponseDto()
        override suspend fun getSonarrServers(): List<SeerrArrServerDto> = emptyList()
        override suspend fun getSonarrServerDetails(serverId: Int) = com.seerflix.tv.core.network.SeerrArrServerDetailsResponseDto()
    }
}
