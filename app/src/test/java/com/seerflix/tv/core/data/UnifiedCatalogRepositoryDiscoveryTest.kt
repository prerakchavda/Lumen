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
import com.seerflix.tv.core.network.JellyfinUpdateUserDataRequest
import com.seerflix.tv.core.network.JellyfinUserDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrArrServerDto
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
        jellyfinApi = FakeJellyfinApi()
        seerrApi = FakeSeerrApi()
        repository = UnifiedCatalogRepository(jellyfinApi, seerrApi, prefs)
    }

    @Test
    fun `top 10 movies out right now uses 60 day rolling window and filters unreleased future dates`() = runTest {
        seerrApi.discoverMoviesHandler = { page, sortBy, _, gte, lte, voteCount ->
            assertEquals("popularity.desc", sortBy)
            assertEquals("2026-01-31", gte)
            assertEquals("2026-04-01", lte)
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
    fun `top 10 tv airing now filters future premieres and supplements from discover tv`() = runTest {
        seerrApi.upcomingTvHandler = { page ->
            if (page == 1) {
                SeerrDiscoverPageDto(
                    page = 1,
                    results = listOf(
                        tvResult(201, "Airing Show 1", firstAirDate = "2026-02-15"),
                        tvResult(202, "Not Premiered Yet", firstAirDate = "2026-05-01"),
                        tvResult(203, "Airing Show 2", firstAirDate = "2026-03-20")
                    )
                )
            } else SeerrDiscoverPageDto()
        }
        seerrApi.discoverTvHandler = { page, _, _, gte, lte, _ ->
            if (gte == null && lte == "2026-04-01" && page == 1) {
                SeerrDiscoverPageDto(
                    page = 1,
                    results = (204..215).map { id ->
                        tvResult(id, "Popular Series $id", firstAirDate = "2025-11-10")
                    }
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
        assertFalse(topTv.items.any { it.title == "Not Premiered Yet" })
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
        seerrApi.upcomingTvHandler = { page ->
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

    private fun movieResult(
        id: Int,
        title: String,
        releaseDate: String,
        hdStatusCode: Int? = null
    ) = SeerrMediaResultDto(
        id = id,
        mediaType = "movie",
        title = title,
        releaseDate = releaseDate,
        mediaInfo = hdStatusCode?.let { SeerrMediaInfoDto(status = it) }
    )

    private fun tvResult(
        id: Int,
        name: String,
        firstAirDate: String,
        hdStatusCode: Int? = null
    ) = SeerrMediaResultDto(
        id = id,
        mediaType = "tv",
        name = name,
        firstAirDate = firstAirDate,
        mediaInfo = hdStatusCode?.let { SeerrMediaInfoDto(status = it) }
    )

    private class FakeJellyfinApi : JellyfinApi {
        var libraryItems: List<JellyfinItemDto> = emptyList()
        var resumeItems: List<JellyfinItemDto> = emptyList()
        var latestItems: List<JellyfinItemDto> = emptyList()
        var favoriteItems: List<JellyfinItemDto> = emptyList()
        var playedItems: List<JellyfinItemDto> = emptyList()

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

        override suspend fun getLibraryItems(
            userId: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            sortBy: String,
            sortOrder: String,
            limit: Int
        ) = JellyfinItemsResponse(libraryItems, libraryItems.size)

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
        override suspend fun getItemDetails(userId: String, itemId: String) =
            libraryItems.first { it.id == itemId }
        override suspend fun getSeasons(seriesId: String, userId: String, fields: String) = JellyfinItemsResponse()
        override suspend fun getEpisodes(seriesId: String, userId: String, seasonId: String?, fields: String) =
            JellyfinItemsResponse()
        override suspend fun getSimilarItems(itemId: String, userId: String, limit: Int, fields: String) =
            JellyfinItemsResponse()
        override suspend fun getLocalTrailers(itemId: String, userId: String): List<JellyfinItemDto> = emptyList()
        override suspend fun getPlaybackInfo(itemId: String, userId: String, body: JellyfinPlaybackInfoRequest) =
            JellyfinPlaybackInfoResponse()
        override suspend fun getMediaSegments(itemId: String) = JellyfinMediaSegmentsResponse()
        override suspend fun reportPlaybackStart(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun reportPlaybackProgress(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun reportPlaybackStopped(body: JellyfinPlaybackProgressRequest) = Unit
    }

    private class FakeSeerrApi : SeerrApi {
        var discoverMoviesHandler: (Int, String?, String?, String?, String?, Int?) -> SeerrDiscoverPageDto =
            { _, _, _, _, _, _ -> SeerrDiscoverPageDto() }
        var discoverTvHandler: (Int, String?, String?, String?, String?, Int?) -> SeerrDiscoverPageDto =
            { _, _, _, _, _, _ -> SeerrDiscoverPageDto() }
        var upcomingTvHandler: (Int) -> SeerrDiscoverPageDto = { SeerrDiscoverPageDto() }
        var moviesByGenreHandler: (Int, Int) -> SeerrDiscoverPageDto = { _, _ -> SeerrDiscoverPageDto() }
        var tvByGenreHandler: (Int, Int) -> SeerrDiscoverPageDto = { _, _ -> SeerrDiscoverPageDto() }
        var tvDetailsHandler: (Int) -> SeerrTvDetailsDto = { id -> SeerrTvDetailsDto(id = id, name = "Show $id") }
        val createdRequests = mutableListOf<SeerrCreateRequestBody>()

        override suspend fun getStatus() = SeerrStatusDto()
        override suspend fun getMe() = SeerrUserDto(1)
        override suspend fun loginWithJellyfin(body: SeerrJellyfinLoginRequest) = SeerrUserDto(1)
        override suspend fun initiateQuickConnect() = SeerrQuickConnectInitiateResponse("c", "s")
        override suspend fun checkQuickConnect(secret: String) = SeerrQuickConnectCheckResponse(false)
        override suspend fun authenticateQuickConnect(body: SeerrQuickConnectAuthRequest) = SeerrUserDto(1)
        override suspend fun getTrending(page: Int, language: String) = SeerrDiscoverPageDto()

        override suspend fun getDiscoverMovies(
            page: Int,
            language: String,
            sortBy: String?,
            genre: String?,
            primaryReleaseDateGte: String?,
            primaryReleaseDateLte: String?,
            voteCountGte: Int?
        ) = discoverMoviesHandler(page, sortBy, genre, primaryReleaseDateGte, primaryReleaseDateLte, voteCountGte)

        override suspend fun getDiscoverTv(
            page: Int,
            language: String,
            sortBy: String?,
            genre: String?,
            firstAirDateGte: String?,
            firstAirDateLte: String?,
            voteCountGte: Int?
        ) = discoverTvHandler(page, sortBy, genre, firstAirDateGte, firstAirDateLte, voteCountGte)

        override suspend fun getUpcomingMovies(page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun getUpcomingTv(page: Int, language: String) = upcomingTvHandler(page)
        override suspend fun getMoviesByGenre(genreId: Int, page: Int, language: String) =
            moviesByGenreHandler(genreId, page)
        override suspend fun getTvByGenre(genreId: Int, page: Int, language: String) =
            tvByGenreHandler(genreId, page)
        override suspend fun getMovieDetails(movieId: Int, language: String) =
            SeerrMovieDetailsDto(id = movieId, title = "Movie $movieId")
        override suspend fun getTvDetails(tvId: Int, language: String) = tvDetailsHandler(tvId)
        override suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int, language: String) =
            SeerrSeasonDetailDto(seasonNumber = seasonNumber)
        override suspend fun getMovieRecommendations(movieId: Int, page: Int) = SeerrDiscoverPageDto()
        override suspend fun getTvRecommendations(tvId: Int, page: Int) = SeerrDiscoverPageDto()
        override suspend fun search(query: String, page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun getPersonDetails(personId: Int, language: String) =
            SeerrPersonDetailsDto(id = personId, name = "Person $personId")
        override suspend fun getPersonCredits(personId: Int, language: String) = SeerrPersonCreditsDto()
        override suspend fun createRequest(body: SeerrCreateRequestBody): SeerrRequestSummaryDto {
            createdRequests += body
            return SeerrRequestSummaryDto(id = 1, status = 1, is4k = body.is4k)
        }
        override suspend fun getRequests(take: Int, skip: Int, filter: String, sort: String) = SeerrRequestsPageDto()
        override suspend fun deleteRequest(requestId: Int) = Unit
        override suspend fun getRadarrServers(): List<SeerrArrServerDto> = emptyList()
        override suspend fun getSonarrServers(): List<SeerrArrServerDto> = emptyList()
    }
}
