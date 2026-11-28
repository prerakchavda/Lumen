package com.seerflix.tv.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Public Trakt charts (no user sign-in): what Trakt members actually watched, used for the
 * "Top 10 … Right Now" rows. Requests carry only the app's Trakt client id (see NetworkModule).
 */
interface TraktApi {

    /** Most-watched movies over [period] (`daily`, `weekly`, `monthly`). [years] is e.g. `2025-2026`. */
    @GET("movies/watched/{period}")
    suspend fun watchedMovies(
        @Path("period") period: String = "weekly",
        @Query("limit") limit: Int = 40,
        @Query("years") years: String? = null
    ): List<TraktWatchedMovieDto>

    /** Most-watched shows over [period]. [status] filters by show status, e.g. `returning series`. */
    @GET("shows/watched/{period}")
    suspend fun watchedShows(
        @Path("period") period: String = "weekly",
        @Query("limit") limit: Int = 40,
        @Query("status") status: String? = null
    ): List<TraktWatchedShowDto>

    // ---- Connect Trakt (0.9.5): device sign-in and the member's own recommendations ----

    /** Starts TV sign-in: the user enters [TraktDeviceCodeDto.userCode] at the verification URL. */
    @POST("oauth/device/code")
    suspend fun deviceCode(@Body body: TraktDeviceCodeRequest): TraktDeviceCodeDto

    /**
     * Polled every [TraktDeviceCodeDto.interval] seconds: 200 = signed in, 400 = still waiting,
     * 404/409/410/418 = invalid / used / expired / denied, 429 = poll slower.
     */
    @POST("oauth/device/token")
    suspend fun deviceToken(@Body body: TraktDeviceTokenRequest): retrofit2.Response<TraktTokenDto>

    @POST("oauth/token")
    suspend fun refreshToken(@Body body: TraktRefreshRequest): TraktTokenDto

    @GET("recommendations/movies")
    suspend fun recommendedMovies(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 30,
        @Query("ignore_collected") ignoreCollected: Boolean = false,
        @Query("ignore_watchlisted") ignoreWatchlisted: Boolean = false
    ): List<TraktTitleDto>

    @GET("recommendations/shows")
    suspend fun recommendedShows(
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 30,
        @Query("ignore_collected") ignoreCollected: Boolean = false,
        @Query("ignore_watchlisted") ignoreWatchlisted: Boolean = false
    ): List<TraktTitleDto>
}

@Serializable
data class TraktDeviceCodeRequest(@SerialName("client_id") val clientId: String)

@Serializable
data class TraktDeviceCodeDto(
    @SerialName("device_code") val deviceCode: String = "",
    @SerialName("user_code") val userCode: String = "",
    @SerialName("verification_url") val verificationUrl: String = "https://trakt.tv/activate",
    @SerialName("expires_in") val expiresIn: Int = 600,
    val interval: Int = 5
)

@Serializable
data class TraktDeviceTokenRequest(
    val code: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String
)

@Serializable
data class TraktRefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("redirect_uri") val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
    @SerialName("grant_type") val grantType: String = "refresh_token"
)

@Serializable
data class TraktTokenDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0L,
    @SerialName("created_at") val createdAt: Long = 0L
) {
    /** Refresh a day early; Trakt tokens last three months. */
    fun isExpired(nowSec: Long): Boolean = expiresIn > 0 && nowSec >= createdAt + expiresIn - 86_400
}

@Serializable
data class TraktIdsDto(
    val trakt: Int? = null,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Int? = null
)

@Serializable
data class TraktTitleDto(
    val title: String? = null,
    val year: Int? = null,
    val ids: TraktIdsDto = TraktIdsDto()
)

@Serializable
data class TraktWatchedMovieDto(
    @SerialName("watcher_count") val watcherCount: Int = 0,
    @SerialName("play_count") val playCount: Int = 0,
    val movie: TraktTitleDto? = null
)

@Serializable
data class TraktWatchedShowDto(
    @SerialName("watcher_count") val watcherCount: Int = 0,
    @SerialName("play_count") val playCount: Int = 0,
    val show: TraktTitleDto? = null
)
