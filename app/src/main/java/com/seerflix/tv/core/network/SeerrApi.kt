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
        @Query("language") language: String = "en"
    ): SeerrDiscoverPageDto

    @GET("api/v1/discover/tv")
    suspend fun getDiscoverTv(
        @Query("page") page: Int = 1,
        @Query("language") language: String = "en"
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

    @GET("api/v1/movie/{movieId}/recommendations")
    suspend fun getMovieRecommendations(
        @Path("movieId") movieId: Int,
        @Query("page") page: Int = 1
    ): SeerrDiscoverPageDto

    @GET("api/v1/tv/{tvId}/recommendations")
    suspend fun getTvRecommendations(
        @Path("tvId") tvId: Int,
        @Query("page") page: Int = 1
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

    @GET("api/v1/settings/radarr")
    suspend fun getRadarrServers(): List<SeerrArrServerDto>

    @GET("api/v1/settings/sonarr")
    suspend fun getSonarrServers(): List<SeerrArrServerDto>
}
