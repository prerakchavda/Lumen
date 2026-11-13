package com.seerflix.tv.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SeerrStatusDto(
    val version: String? = null,
    val commitTag: String? = null
)

@Serializable
data class SeerrUserDto(
    val id: Int,
    val username: String? = null,
    val displayName: String? = null,
    val jellyfinUsername: String? = null,
    val permissions: Long = 0L
)

@Serializable
data class SeerrJellyfinLoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class SeerrQuickConnectInitiateResponse(
    val code: String,
    val secret: String
)

@Serializable
data class SeerrQuickConnectCheckResponse(
    val authenticated: Boolean = false
)

@Serializable
data class SeerrQuickConnectAuthRequest(
    val secret: String
)

@Serializable
data class SeerrDiscoverPageDto(
    val page: Int = 1,
    val totalPages: Int = 1,
    val totalResults: Int = 0,
    val results: List<SeerrMediaResultDto> = emptyList()
)

@Serializable
data class SeerrMediaResultDto(
    val id: Int,
    val mediaType: String? = null,
    val title: String? = null,
    val name: String? = null,
    val originalTitle: String? = null,
    val originalName: String? = null,
    val overview: String? = null,
    val releaseDate: String? = null,
    val firstAirDate: String? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val voteAverage: Double? = null,
    val genreIds: List<Int> = emptyList(),
    val mediaInfo: SeerrMediaInfoDto? = null
)

@Serializable
data class SeerrMediaInfoDto(
    val id: Int? = null,
    val mediaType: String? = null,
    val tmdbId: Int? = null,
    val tvdbId: Int? = null,
    val imdbId: String? = null,
    val status: Int? = null,
    val status4k: Int? = null,
    val jellyfinMediaId: String? = null,
    val jellyfinMediaId4k: String? = null,
    val requests: List<SeerrRequestSummaryDto> = emptyList(),
    val downloadStatus: List<SeerrDownloadStatusDto> = emptyList(),
    val downloadStatus4k: List<SeerrDownloadStatusDto> = emptyList(),
    val seasons: List<SeerrSeasonStatusDto> = emptyList()
)

@Serializable
data class SeerrSeasonStatusDto(
    val seasonNumber: Int,
    val status: Int? = null,
    val status4k: Int? = null
)

@Serializable
data class SeerrDownloadStatusDto(
    val title: String? = null,
    val status: String? = null,
    val size: Long = 0L,
    val sizeLeft: Long = 0L,
    val estimatedCompletionTime: String? = null,
    val timeLeft: String? = null,
    val externalId: Int? = null
)

@Serializable
data class SeerrRequestSummaryDto(
    val id: Int,
    val status: Int,
    val is4k: Boolean = false,
    val createdAt: String? = null,
    val requestedBy: SeerrUserDto? = null,
    val media: SeerrMediaInfoDto? = null,
    val seasons: List<SeerrRequestedSeasonDto> = emptyList()
)

@Serializable
data class SeerrRequestedSeasonDto(
    val seasonNumber: Int,
    val status: Int? = null
)

@Serializable
data class SeerrGenreDto(
    val id: Int,
    val name: String
)

@Serializable
data class SeerrRelatedVideoDto(
    val url: String? = null,
    val key: String? = null,
    val name: String? = null,
    val size: Int? = null,
    val type: String? = null,
    val site: String? = null
)

@Serializable
data class SeerrCastDto(
    val id: Int? = null,
    val name: String,
    val character: String? = null,
    val profilePath: String? = null
)

@Serializable
data class SeerrCreditsDto(
    val cast: List<SeerrCastDto> = emptyList()
)

@Serializable
data class SeerrExternalIdsDto(
    val imdbId: String? = null,
    val tvdbId: Int? = null
)

@Serializable
data class SeerrSeasonDetailDto(
    val id: Int? = null,
    val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    val episodeCount: Int? = null,
    val posterPath: String? = null,
    val episodes: List<SeerrEpisodeDto> = emptyList()
)

@Serializable
data class SeerrEpisodeDto(
    val id: Int? = null,
    val episodeNumber: Int,
    val seasonNumber: Int,
    val name: String? = null,
    val overview: String? = null,
    val stillPath: String? = null,
    val airDate: String? = null
)

@Serializable
data class SeerrMovieDetailsDto(
    val id: Int,
    val title: String,
    val overview: String? = null,
    val tagline: String? = null,
    val releaseDate: String? = null,
    val runtime: Int? = null,
    val voteAverage: Double? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val genres: List<SeerrGenreDto> = emptyList(),
    val credits: SeerrCreditsDto? = null,
    val relatedVideos: List<SeerrRelatedVideoDto> = emptyList(),
    val externalIds: SeerrExternalIdsDto? = null,
    val mediaInfo: SeerrMediaInfoDto? = null
)

@Serializable
data class SeerrTvDetailsDto(
    val id: Int,
    val name: String,
    val overview: String? = null,
    val tagline: String? = null,
    val firstAirDate: String? = null,
    val episodeRunTime: List<Int> = emptyList(),
    val voteAverage: Double? = null,
    val posterPath: String? = null,
    val backdropPath: String? = null,
    val genres: List<SeerrGenreDto> = emptyList(),
    val credits: SeerrCreditsDto? = null,
    val relatedVideos: List<SeerrRelatedVideoDto> = emptyList(),
    val externalIds: SeerrExternalIdsDto? = null,
    val seasons: List<SeerrSeasonDetailDto> = emptyList(),
    val mediaInfo: SeerrMediaInfoDto? = null
)

@Serializable
data class SeerrCreateRequestBody(
    val mediaType: String,
    val mediaId: Int,
    val is4k: Boolean = false,
    val seasons: List<Int>? = null
)

@Serializable
data class SeerrRequestsPageDto(
    val pageInfo: SeerrPageInfoDto? = null,
    val results: List<SeerrRequestSummaryDto> = emptyList()
)

@Serializable
data class SeerrPageInfoDto(
    val pages: Int = 1,
    val page: Int = 1,
    val results: Int = 0
)

@Serializable
data class SeerrArrServerDto(
    val id: Int,
    val name: String? = null,
    val is4k: Boolean = false,
    val isDefault: Boolean = false
)
