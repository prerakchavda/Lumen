package com.seerflix.tv.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
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
