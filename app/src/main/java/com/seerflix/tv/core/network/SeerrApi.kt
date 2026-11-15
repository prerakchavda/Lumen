package com.seerflix.tv.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface SeerrApi {
    @GET("api/v1/status")
    suspend fun getStatus(): SeerrStatusDto

    @GET("api/v1/auth/me")
    suspend fun getMe(): SeerrUserDto

    @POST("api/v1/auth/jellyfin")
    suspend fun loginWithJellyfin(
        @Body body: SeerrJellyfinLoginRequest
    ): SeerrUserDto

    @POST("api/v1/auth/jellyfin/quickconnect/initiate")
    suspend fun initiateQuickConnect(): SeerrQuickConnectInitiateResponse

    @GET("api/v1/auth/jellyfin/quickconnect/check")
    suspend fun checkQuickConnect(
        @Query("secret") secret: String
    ): SeerrQuickConnectCheckResponse

    @POST("api/v1/auth/jellyfin/quickconnect/authenticate")
    suspend fun authenticateQuickConnect(
        @Body body: SeerrQuickConnectAuthRequest
    ): SeerrUserDto

    @GET("api/v1/discover/trending")
    suspend fun getTrending(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/discover/movies")
    suspend fun getDiscoverMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en",
        @Query("sortBy") sortBy: String? = null,
        @Query("genre") genre: String? = null,
        @Query("primaryReleaseDateGte") primaryReleaseDateGte: String? = null,
        @Query("primaryReleaseDateLte") primaryReleaseDateLte: String? = null,
        @Query("voteCountGte") voteCountGte: Int? = null
    ): SeerrDiscoverPageDto

    @GET("api/v1/discover/tv")
    suspend fun getDiscoverTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en",
        @Query("sortBy") sortBy: String? = null,
        @Query("genre") genre: String? = null,
        @Query("firstAirDateGte") firstAirDateGte: String? = null,
        @Query("firstAirDateLte") firstAirDateLte: String? = null,
        @Query("voteCountGte") voteCountGte: Int? = null
    ): SeerrDiscoverPageDto

    @GET("api/v1/discover/movies/upcoming")
    suspend fun getUpcomingMovies(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/discover/tv/upcoming")
    suspend fun getUpcomingTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/discover/movies/genre/{genreId}")
    suspend fun getMoviesByGenre(
        @Path("genreId") genreId: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/discover/tv/genre/{genreId}")
    suspend fun getTvByGenre(
        @Path("genreId") genreId: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/movie/{movieId}")
    suspend fun getMovieDetails(
        @Path("movieId") movieId: Int,
        @Query("language") language: String = "en"
    ): SeerrMovieDetailsDto

    @GET("api/v1/tv/{tvId}")
    suspend fun getTvDetails(
        @Path("tvId") tvId: Int,
        @Query("language") language: String = "en"
    ): SeerrTvDetailsDto

    @GET("api/v1/tv/{tvId}/season/{seasonNumber}")
    suspend fun getTvSeasonDetails(
        @Path("tvId") tvId: Int,
        @Path("seasonNumber") seasonNumber: Int,
        @Query("language") language: String = "en"
    ): SeerrSeasonDetailDto

    @GET("api/v1/collection/{collectionId}")
    suspend fun getCollectionDetails(
        @Path("collectionId") collectionId: Int,
        @Query("language") language: String = "en"
    ): SeerrCollectionDetailsDto

    @GET("api/v1/movie/{movieId}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("movieId") movieId: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/tv/{tvId}/recommendations")
    suspend fun getTvRecommendations(
        @Path("tvId") tvId: Int,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/person/{personId}")
    suspend fun getPersonDetails(
        @Path("personId") personId: Int,
        @Query("language") language: String = "en"
    ): SeerrPersonDetailsDto

    @GET("api/v1/person/{personId}/combined_credits")
    suspend fun getPersonCredits(
        @Path("personId") personId: Int,
        @Query("language") language: String = "en"
    ): SeerrPersonCreditsDto

    @POST("api/v1/request")
    suspend fun createRequest(
        @Body body: SeerrCreateRequestBody
    ): SeerrRequestSummaryDto

    @POST("api/v1/request/{requestId}/approve")
    suspend fun approveRequest(
        @Path("requestId") requestId: Int
    ): SeerrRequestSummaryDto

    @POST("api/v1/request/{requestId}/decline")
    suspend fun declineRequest(
        @Path("requestId") requestId: Int
    ): SeerrRequestSummaryDto

    @GET("api/v1/request")
    suspend fun getRequests(
        @Query("take") take: Int = 50,
        @Query("skip") skip: Int = 0,
        @Query("filter") filter: String = "all",
        @Query("sort") sort: String = "added"
    ): SeerrRequestsPageDto

    @DELETE("api/v1/request/{requestId}")
    suspend fun deleteRequest(
        @Path("requestId") requestId: Int
    )

    @POST("api/v1/issue")
    suspend fun createIssue(
        @Body body: SeerrCreateIssueBody
    )

    // `/service/*` is available to every signed-in user and returns {server, profiles, rootFolders}
    // for a single instance. `/settings/*` is admin-only (and has no GET-by-id route).
    @GET("api/v1/service/radarr")
    suspend fun getRadarrServers(): List<SeerrArrServerDto>

    @GET("api/v1/service/radarr/{serverId}")
    suspend fun getRadarrServerDetails(
        @Path("serverId") serverId: Int
    ): SeerrArrServerDetailsResponseDto

    @GET("api/v1/service/sonarr")
    suspend fun getSonarrServers(): List<SeerrArrServerDto>

    @GET("api/v1/service/sonarr/{serverId}")
    suspend fun getSonarrServerDetails(
        @Path("serverId") serverId: Int
    ): SeerrArrServerDetailsResponseDto
}
