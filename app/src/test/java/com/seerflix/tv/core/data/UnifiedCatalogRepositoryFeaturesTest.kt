package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.ContentRatingLimit
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinAuthRequest
import com.seerflix.tv.core.network.JellyfinAuthResult
import com.seerflix.tv.core.network.JellyfinCreateSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinItemsResponse
import com.seerflix.tv.core.network.JellyfinJoinSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinMediaSegmentsResponse
import com.seerflix.tv.core.network.JellyfinPlaybackInfoRequest
import com.seerflix.tv.core.network.JellyfinPlaybackInfoResponse
import com.seerflix.tv.core.network.JellyfinPlaybackProgressRequest
import com.seerflix.tv.core.network.JellyfinPublicSystemInfo
import com.seerflix.tv.core.network.JellyfinQuickConnectAuthRequest
import com.seerflix.tv.core.network.JellyfinQuickConnectState
import com.seerflix.tv.core.network.JellyfinRemoteSubtitleDto
import com.seerflix.tv.core.network.JellyfinSeekSyncPlayRequest
import com.seerflix.tv.core.network.JellyfinSyncPlayGroupDto
import com.seerflix.tv.core.network.JellyfinUpdateUserDataRequest
import com.seerflix.tv.core.network.JellyfinUserDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrArrProfileDto
import com.seerflix.tv.core.network.SeerrArrRootFolderDto
import com.seerflix.tv.core.network.SeerrArrServerDetailsResponseDto
import com.seerflix.tv.core.network.SeerrArrServerDto
import com.seerflix.tv.core.network.SeerrCollectionDetailsDto
import com.seerflix.tv.core.network.SeerrCreateIssueBody
import com.seerflix.tv.core.network.SeerrCreateRequestBody
import com.seerflix.tv.core.network.SeerrDiscoverPageDto
import com.seerflix.tv.core.network.SeerrJellyfinLoginRequest
import com.seerflix.tv.core.network.SeerrMediaInfoDto
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UnifiedCatalogRepositoryFeaturesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var prefs: SessionPreferences
    private lateinit var jellyfinApi: FakeJellyfinApi
    private lateinit var seerrApi: FakeSeerrApi
    private lateinit var repository: UnifiedCatalogRepository

    @Before
    fun setUp() {
        prefs = SessionPreferences(
            testFile = tempFolder.newFile("seerflix_features.preferences_pb"),
            json = Json { ignoreUnknownKeys = true }
        )
        jellyfinApi = FakeJellyfinApi()
        seerrApi = FakeSeerrApi()
        repository = UnifiedCatalogRepository(jellyfinApi, seerrApi, prefs)
    }

    @Test
    fun `Jellyfin-Only mode skips Seerr calls and loads library rows cleanly`() = runTest {
        prefs.saveServerUrls(jellyfinUrl = "http://jf:8096", seerrUrl = "")
        jellyfinApi.latestItems = listOf(
            JellyfinItemDto(id = "jf1", name = "Dune: Part Two", type = "Movie", officialRating = "PG-13")
        )
        jellyfinApi.libraryItems = jellyfinApi.latestItems

        val rows = repository.loadHomeRows(
            userId = "u1",
            filterType = null,
            availabilityFilter = AvailabilityFilter.ALL
        )
        assertTrue(rows.any { it.id == "recently_added" })
        assertEquals(0, seerrApi.seerrCallCount)
        assertFalse(repository.has4kServerConfigured(MediaType.MOVIE))
    }

    @Test
    fun `admin approveRequest, declineRequest, reportIssue, and Arr server selection work end-to-end`() = runTest {
        prefs.saveServerUrls(jellyfinUrl = "http://jf:8096", seerrUrl = "http://seerr:5055")
        seerrApi.radarrServers = listOf(SeerrArrServerDto(id = 7, name = "Radarr 4K", is4k = true, isDefault = true))
        seerrApi.radarrServerDetails[7] = SeerrArrServerDetailsResponseDto(
            profiles = listOf(SeerrArrProfileDto(id = 4, name = "Ultra-HD")),
            rootFolders = listOf(SeerrArrRootFolderDto(path = "/movies-4k"))
        )

        val servers = repository.loadArrServers(MediaType.MOVIE)
        assertEquals(1, servers.size)
        assertEquals("Ultra-HD", servers.first().profiles.first().name)
        assertEquals("/movies-4k", servers.first().rootFolders.first())

        val reqResult = repository.submitRequest(
            tmdbId = 693134,
            mediaType = MediaType.MOVIE,
            is4k = true,
            serverId = 7,
            profileId = 4,
            rootFolder = "/movies-4k"
        )
        assertTrue(reqResult.isSuccess)
        assertEquals(7, seerrApi.lastCreatedRequest?.serverId)
        assertEquals(4, seerrApi.lastCreatedRequest?.profileId)
        assertEquals("/movies-4k", seerrApi.lastCreatedRequest?.rootFolder)

        assertTrue(repository.approveRequest(55).isSuccess)
        assertTrue(repository.declineRequest(56).isSuccess)
        assertEquals(listOf(55), seerrApi.approvedIds)
        assertEquals(listOf(56), seerrApi.declinedIds)

        assertTrue(
            repository.reportIssue(
                tmdbId = 693134,
                mediaType = MediaType.MOVIE,
                issueType = SeerrIssueType.SUBTITLE,
                message = "Out of sync"
            ).isSuccess
        )
        assertEquals(3, seerrApi.createdIssues.single().issueType)
        assertEquals(999, seerrApi.createdIssues.single().mediaId)
        assertEquals("Out of sync", seerrApi.createdIssues.single().message)
    }

    @Test
    fun `markWatched toggles played status and pinned or hidden home rows follow profile customization`() = runTest {
        prefs.saveServerUrls(jellyfinUrl = "http://jf:8096", seerrUrl = "")
        repository.markWatched("u1", "item_a", watched = true)
        repository.markWatched("u1", "item_b", watched = false)
        assertEquals(listOf("item_a"), jellyfinApi.markedPlayed)
        assertEquals(listOf("item_b"), jellyfinApi.markedUnplayed)

        jellyfinApi.userViews = listOf(
            JellyfinItemDto(id = "lib_anime", name = "Anime Vault", type = "CollectionFolder", collectionType = "tvshows")
        )
        jellyfinApi.latestItems = listOf(
            JellyfinItemDto(id = "m1", name = "Kid Movie", type = "Movie", officialRating = "G"),
            JellyfinItemDto(id = "m2", name = "Mature Movie", type = "Movie", officialRating = "R")
        )
        jellyfinApi.libraryItems = jellyfinApi.latestItems

        val profile = UserProfile(
            id = "u1",
            name = "Kids",
            jellyfinUserId = "u1",
            jellyfinToken = "tok",
            kidsMode = true,
            maxContentRating = ContentRatingLimit.PG,
            pinnedLibraryIds = listOf("lib_anime"),
            hiddenHomeRowIds = listOf("recently_added"),
            homeRowOrder = listOf("lib_lib_anime")
        )

        val rows = repository.loadHomeRows("u1", filterType = null, activeProfile = profile)
        assertFalse(rows.any { it.id == "recently_added" })
        val pinnedRow = rows.first { it.id == "lib_lib_anime" }
        assertEquals("Anime Vault", pinnedRow.title)
        assertEquals(listOf("Kid Movie"), pinnedRow.items.map { it.title })
    }

    @Test
    fun `loadLiveTvChannelsAndPrograms joins active and upcoming programs by channelId`() = runTest {
        jellyfinApi.liveTvChannels = listOf(
            JellyfinItemDto(id = "ch1", name = "HBO HD", channelNumber = "501", type = "TvChannel")
        )
        jellyfinApi.liveTvPrograms = listOf(
            JellyfinItemDto(
                id = "p1",
                name = "Dune: Part Two",
                type = "Program",
                channelId = "ch1",
                startDate = "2026-11-13T18:00:00Z",
                endDate = "2026-11-13T21:00:00Z"
            )
        )

        val channels = repository.loadLiveTvChannelsAndPrograms("u1")
        assertEquals(1, channels.size)
        assertEquals("501", channels.first().number)
        assertEquals("HBO HD", channels.first().name)
    }

    @Test
    fun `launcher ready promotions are checked against the active profile's rating limit`() = runTest {
        jellyfinApi.libraryItems = listOf(
            JellyfinItemDto(id = "m1", name = "Family Movie", type = "Movie", officialRating = "PG"),
            JellyfinItemDto(id = "m2", name = "Mature Movie", type = "Movie", officialRating = "R")
        )
        repository.refreshLibraryIndex("u1")
        val kids = UserProfile(
            id = "u1", name = "Kids", jellyfinUserId = "u1", jellyfinToken = "tok",
            kidsMode = true, maxContentRating = ContentRatingLimit.PG
        )
        val adult = UserProfile(id = "u1", name = "Adult", jellyfinUserId = "u1", jellyfinToken = "tok")
        assertTrue(repository.isLibraryTitleAllowed("m1", kids))
        assertFalse(repository.isLibraryTitleAllowed("m2", kids))
        // Unknown to the index: not shown to a restricted profile.
        assertFalse(repository.isLibraryTitleAllowed("gone", kids))
        assertTrue(repository.isLibraryTitleAllowed("m2", adult))
        assertTrue(repository.isLibraryTitleAllowed("gone", null))
    }

    private class FakeJellyfinApi : JellyfinApi {
        var libraryItems: List<JellyfinItemDto> = emptyList()
        var latestItems: List<JellyfinItemDto> = emptyList()
        var userViews: List<JellyfinItemDto> = emptyList()
        var liveTvChannels: List<JellyfinItemDto> = emptyList()
        var liveTvPrograms: List<JellyfinItemDto> = emptyList()
        val markedPlayed = mutableListOf<String>()
        val markedUnplayed = mutableListOf<String>()

        override suspend fun getPublicSystemInfo() = JellyfinPublicSystemInfo()
        override suspend fun getPublicUsers(): List<JellyfinUserDto> = emptyList()
        override suspend fun getUser(userId: String) = JellyfinUserDto(userId, "User")
        override suspend fun authenticateByName(body: JellyfinAuthRequest) =
            JellyfinAuthResult(JellyfinUserDto("u1", "User"), "tok")
        override suspend fun initiateQuickConnect() = JellyfinQuickConnectState("s", "c", false)
        override suspend fun checkQuickConnect(secret: String) = JellyfinQuickConnectState(secret, "c", false)
        override suspend fun authenticateWithQuickConnect(body: JellyfinQuickConnectAuthRequest) =
            JellyfinAuthResult(JellyfinUserDto("u1", "User"), "tok")
        override suspend fun getResumeItems(userId: String, limit: Int, fields: String, mediaTypes: String) =
            JellyfinItemsResponse()
        override suspend fun getLatestItems(userId: String, limit: Int, includeItemTypes: String, fields: String) =
            latestItems
        override suspend fun getUserViews(userId: String) = JellyfinItemsResponse(userViews, userViews.size)
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
        ) = JellyfinItemsResponse(libraryItems, libraryItems.size)
        override suspend fun getFavoriteItems(
            userId: String,
            filters: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            limit: Int
        ) = JellyfinItemsResponse()
        override suspend fun getPlayedItems(
            userId: String,
            filters: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            sortBy: String,
            sortOrder: String,
            limit: Int
        ) = JellyfinItemsResponse()
        val ratedItems = mutableMapOf<String, Boolean?>()
        override suspend fun updateUserItemRating(userId: String, itemId: String, likes: Boolean) { ratedItems[itemId] = likes }
        override suspend fun deleteUserItemRating(userId: String, itemId: String) { ratedItems[itemId] = null }
        var serverUsers: List<JellyfinUserDto> = emptyList()
        override suspend fun getUsers(): List<JellyfinUserDto> = serverUsers
        var playedByUser: Map<String, List<JellyfinItemDto>> = emptyMap()
        override suspend fun getRecentlyPlayedForUser(
            userToken: String?,
            userId: String,
            filters: String,
            recursive: Boolean,
            includeItemTypes: String,
            fields: String,
            sortBy: String,
            sortOrder: String,
            limit: Int,
            enableImages: Boolean,
            enableTotalRecordCount: Boolean
        ) = JellyfinItemsResponse(playedByUser[userId].orEmpty(), playedByUser[userId].orEmpty().size)
        override suspend fun addFavorite(userId: String, itemId: String) = Unit
        override suspend fun removeFavorite(userId: String, itemId: String) = Unit
        override suspend fun updateUserData(userId: String, itemId: String, body: JellyfinUpdateUserDataRequest) = Unit
        override suspend fun markPlayed(userId: String, itemId: String) {
            markedPlayed += itemId
        }
        override suspend fun markUnplayed(userId: String, itemId: String) {
            markedUnplayed += itemId
        }
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
        override suspend fun getEpisodes(seriesId: String, userId: String, seasonId: String?, fields: String, isMissing: Boolean?) = JellyfinItemsResponse()
        override suspend fun getSimilarItems(itemId: String, userId: String, limit: Int, fields: String) = JellyfinItemsResponse()
        override suspend fun getLocalTrailers(itemId: String, userId: String): List<JellyfinItemDto> = emptyList()
        override suspend fun getPlaybackInfo(itemId: String, userId: String, body: JellyfinPlaybackInfoRequest) = JellyfinPlaybackInfoResponse()
        override suspend fun getMediaSegments(itemId: String) = JellyfinMediaSegmentsResponse()
        override suspend fun getCollections(userId: String, recursive: Boolean, includeItemTypes: String, fields: String, sortBy: String, sortOrder: String, limit: Int) = JellyfinItemsResponse()
        override suspend fun getCollectionItems(userId: String, parentId: String, recursive: Boolean, fields: String, sortBy: String, sortOrder: String, limit: Int) = JellyfinItemsResponse()
        override suspend fun getUpcomingEpisodes(userId: String, limit: Int, fields: String) = JellyfinItemsResponse()
        override suspend fun searchRemoteSubtitles(itemId: String, language: String) = emptyList<JellyfinRemoteSubtitleDto>()
        override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String) = Unit
        override suspend fun getLiveTvChannels(userId: String, fields: String, addCurrentProgram: Boolean, enableFavoriteSorting: Boolean?) =
            JellyfinItemsResponse(liveTvChannels, liveTvChannels.size)
        override suspend fun getLiveTvPrograms(
            userId: String,
            hasAired: Boolean,
            limit: Int,
            startIndex: Int?,
            fields: String,
            minEndDate: String?,
            maxStartDate: String?,
            sortBy: String?,
            enableTotalRecordCount: Boolean?
        ) = JellyfinItemsResponse(liveTvPrograms, liveTvPrograms.size)
        override suspend fun getLiveTvRecordings(userId: String, limit: Int, fields: String) = JellyfinItemsResponse()
        override suspend fun getSyncPlayGroups(): List<JellyfinSyncPlayGroupDto> = emptyList()
        override suspend fun createSyncPlayGroup(body: JellyfinCreateSyncPlayRequest) = Unit
        override suspend fun joinSyncPlayGroup(body: JellyfinJoinSyncPlayRequest) = Unit
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
        override suspend fun syncPlaySeek(body: JellyfinSeekSyncPlayRequest) = Unit
        override suspend fun reportPlaybackStart(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun reportPlaybackProgress(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun reportPlaybackStopped(body: JellyfinPlaybackProgressRequest) = Unit
        override suspend fun getEpisodesBySeasonNumber(seriesId: String, userId: String, season: Int?, fields: String?, enableImages: Boolean?, isMissing: Boolean?) = JellyfinItemsResponse()
        override suspend fun logout() = Unit
        override suspend fun getEpisodesLite(seriesId: String, userId: String, seasonId: String?, startItemId: String?, limit: Int?, fields: String?, enableImages: Boolean, isMissing: Boolean?) = JellyfinItemsResponse()
        override suspend fun getResumeItemsLite(userId: String, parentId: String?, limit: Int, mediaTypes: String, enableImages: Boolean) = JellyfinItemsResponse()
        override suspend fun getNextUp(userId: String, limit: Int, fields: String, enableRewatching: Boolean, disableFirstEpisode: Boolean, nextUpDateCutoff: String?) = JellyfinItemsResponse()
        override suspend fun getPlaylists(userId: String, recursive: Boolean, includeItemTypes: String, fields: String, sortBy: String, sortOrder: String, limit: Int) = JellyfinItemsResponse()
        override suspend fun getPlaylistItems(playlistId: String, userId: String, fields: String, limit: Int) = JellyfinItemsResponse()
        override suspend fun createPlaylist(body: com.seerflix.tv.core.network.JellyfinCreatePlaylistRequest) = com.seerflix.tv.core.network.JellyfinCreatePlaylistResponse("pl-1")
        override suspend fun addItemsToPlaylist(playlistId: String, ids: String, userId: String) = Unit
        override suspend fun removeItemsFromPlaylist(playlistId: String, entryIds: String) = Unit
        override suspend fun getPersons(searchTerm: String, limit: Int, fields: String) = JellyfinItemsResponse()
        override suspend fun getItemsByPerson(userId: String, personIds: String, recursive: Boolean, includeItemTypes: String, fields: String, sortBy: String, sortOrder: String, limit: Int) = JellyfinItemsResponse()
        override suspend fun deleteItem(itemId: String) = Unit
        override suspend fun getIntros(userId: String, itemId: String) = JellyfinItemsResponse()
        override suspend fun getThemeSongs(itemId: String, userId: String, inheritFromParent: Boolean) = JellyfinItemsResponse()
    }

    private class FakeSeerrApi : SeerrApi {
        var seerrCallCount = 0
        var radarrServers: List<SeerrArrServerDto> = emptyList()
        val radarrServerDetails = mutableMapOf<Int, SeerrArrServerDetailsResponseDto>()
        var lastCreatedRequest: SeerrCreateRequestBody? = null
        val approvedIds = mutableListOf<Int>()
        val declinedIds = mutableListOf<Int>()
        val createdIssues = mutableListOf<SeerrCreateIssueBody>()

        override suspend fun getStatus(): SeerrStatusDto { seerrCallCount++; return SeerrStatusDto() }
        override suspend fun getMe(): SeerrUserDto { seerrCallCount++; return SeerrUserDto(1) }
        override suspend fun loginWithJellyfin(body: SeerrJellyfinLoginRequest) = SeerrUserDto(1)
        override suspend fun initiateQuickConnect() = SeerrQuickConnectInitiateResponse("c", "s")
        override suspend fun checkQuickConnect(secret: String) = SeerrQuickConnectCheckResponse(false)
        override suspend fun authenticateQuickConnect(body: SeerrQuickConnectAuthRequest) = SeerrUserDto(1)
        override suspend fun getTrending(page: Int, language: String): SeerrDiscoverPageDto { seerrCallCount++; return SeerrDiscoverPageDto() }
        override suspend fun getDiscoverMovies(page: Int, language: String, sortBy: String?, genre: String?, primaryReleaseDateGte: String?, primaryReleaseDateLte: String?, voteAverageGte: Double?, voteCountGte: Int?, withRuntimeLte: Int?, watchRegion: String?, watchProviders: String?) =
            SeerrDiscoverPageDto().also { seerrCallCount++ }
        override suspend fun getDiscoverTv(page: Int, language: String, sortBy: String?, genre: String?, firstAirDateGte: String?, firstAirDateLte: String?, voteAverageGte: Double?, voteCountGte: Int?, withRuntimeLte: Int?, status: String?, watchRegion: String?, watchProviders: String?) =
            SeerrDiscoverPageDto().also { seerrCallCount++ }
        override suspend fun getUpcomingMovies(page: Int, language: String) = SeerrDiscoverPageDto().also { seerrCallCount++ }
        override suspend fun getUpcomingTv(page: Int, language: String) = SeerrDiscoverPageDto().also { seerrCallCount++ }
        override suspend fun getMoviesByGenre(genreId: Int, page: Int, language: String) = SeerrDiscoverPageDto().also { seerrCallCount++ }
        override suspend fun getTvByGenre(genreId: Int, page: Int, language: String) = SeerrDiscoverPageDto().also { seerrCallCount++ }
        override suspend fun getMovieDetails(movieId: Int, language: String) =
            SeerrMovieDetailsDto(id = movieId, title = "M", mediaInfo = SeerrMediaInfoDto(id = 999))
        override suspend fun getTvDetails(tvId: Int, language: String) = SeerrTvDetailsDto(id = tvId, name = "S")
        override suspend fun getTvSeasonDetails(tvId: Int, seasonNumber: Int, language: String) = SeerrSeasonDetailDto(seasonNumber = seasonNumber)
        override suspend fun getCollectionDetails(collectionId: Int, language: String) = SeerrCollectionDetailsDto(id = collectionId, name = "C")
        override suspend fun getMovieRecommendations(movieId: Int, page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun getTvRecommendations(tvId: Int, page: Int, language: String) = SeerrDiscoverPageDto()
        override suspend fun search(query: String, page: Int, language: String) = SeerrDiscoverPageDto().also { seerrCallCount++ }
        override suspend fun getPersonDetails(personId: Int, language: String) = SeerrPersonDetailsDto(id = personId, name = "P")
        override suspend fun getPersonCredits(personId: Int, language: String) = SeerrPersonCreditsDto()
        override suspend fun createRequest(body: SeerrCreateRequestBody): SeerrRequestSummaryDto {
            seerrCallCount++
            lastCreatedRequest = body
            return SeerrRequestSummaryDto(id = 1, status = 1, is4k = body.is4k)
        }
        override suspend fun approveRequest(requestId: Int): SeerrRequestSummaryDto {
            approvedIds += requestId
            return SeerrRequestSummaryDto(id = requestId, status = 2)
        }
        override suspend fun declineRequest(requestId: Int): SeerrRequestSummaryDto {
            declinedIds += requestId
            return SeerrRequestSummaryDto(id = requestId, status = 3)
        }
        override suspend fun getRequests(take: Int, skip: Int, filter: String, sort: String) = SeerrRequestsPageDto()
        override suspend fun deleteRequest(requestId: Int) = Unit
        override suspend fun createIssue(body: SeerrCreateIssueBody) {
            createdIssues += body
        }
        override suspend fun getRadarrServers(): List<SeerrArrServerDto> {
            seerrCallCount++
            return radarrServers
        }
        override suspend fun getRadarrServerDetails(serverId: Int): SeerrArrServerDetailsResponseDto {
            return radarrServerDetails[serverId] ?: SeerrArrServerDetailsResponseDto()
        }
        override suspend fun getSonarrServers(): List<SeerrArrServerDto> {
            seerrCallCount++
            return emptyList()
        }
        override suspend fun getSonarrServerDetails(serverId: Int) = SeerrArrServerDetailsResponseDto()
    }
}
