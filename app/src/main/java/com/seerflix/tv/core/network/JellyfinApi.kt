package com.seerflix.tv.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface JellyfinApi {
    @GET("System/Info/Public")
    suspend fun getPublicSystemInfo(): JellyfinPublicSystemInfo

    @GET("Users/Public")
    suspend fun getPublicUsers(): List<JellyfinUserDto>

    @POST("Users/AuthenticateByName")
    suspend fun authenticateByName(
        @Body body: JellyfinAuthRequest
    ): JellyfinAuthResult

    @POST("QuickConnect/Initiate")
    suspend fun initiateQuickConnect(): JellyfinQuickConnectState

    @GET("QuickConnect/Connect")
    suspend fun checkQuickConnect(
        @Query("secret") secret: String
    ): JellyfinQuickConnectState

    @POST("Users/AuthenticateWithQuickConnect")
    suspend fun authenticateWithQuickConnect(
        @Body body: JellyfinQuickConnectAuthRequest
    ): JellyfinAuthResult

    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItems(
        @Path("userId") userId: String,
        @Query("Limit") limit: Int = 20,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,BasicSyncInfo,Overview,Genres,ProviderIds,UserData,RunTimeTicks,MediaStreams",
        @Query("MediaTypes") mediaTypes: String = "Video"
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items/Latest")
    suspend fun getLatestItems(
        @Path("userId") userId: String,
        @Query("Limit") limit: Int = 24,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,Overview,Genres,ProviderIds,UserData,RunTimeTicks,RemoteTrailers,MediaStreams"
    ): List<JellyfinItemDto>

    @GET("Users/{userId}/Items")
    suspend fun getLibraryItems(
        @Path("userId") userId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Fields") fields: String = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,ProductionYear,OfficialRating,CommunityRating,RemoteTrailers,MediaStreams",
        @Query("SortBy") sortBy: String = "DateCreated,SortName",
        @Query("SortOrder") sortOrder: String = "Descending",
        @Query("Limit") limit: Int = 500
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getFavoriteItems(
        @Path("userId") userId: String,
        @Query("Filters") filters: String = "IsFavorite",
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Fields") fields: String = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,ProductionYear,OfficialRating,CommunityRating,RemoteTrailers,MediaStreams",
        @Query("Limit") limit: Int = 100
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getPlayedItems(
        @Path("userId") userId: String,
        @Query("Filters") filters: String = "IsPlayed",
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Fields") fields: String = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,ProductionYear,OfficialRating,CommunityRating,MediaStreams",
        @Query("SortBy") sortBy: String = "DatePlayed",
        @Query("SortOrder") sortOrder: String = "Descending",
        @Query("Limit") limit: Int = 24
    ): JellyfinItemsResponse

    @POST("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun addFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    )

    @DELETE("Users/{userId}/FavoriteItems/{itemId}")
    suspend fun removeFavorite(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    )

    @POST("Users/{userId}/Items/{itemId}/UserData")
    suspend fun updateUserData(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String,
        @Body body: JellyfinUpdateUserDataRequest
    )

    @POST("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markPlayed(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    )

    @DELETE("Users/{userId}/PlayedItems/{itemId}")
    suspend fun markUnplayed(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    )

    @GET("Users/{userId}/Items")
    suspend fun searchItems(
        @Path("userId") userId: String,
        @Query("SearchTerm") searchTerm: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Fields") fields: String = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,ProductionYear,OfficialRating,CommunityRating,MediaStreams",
        @Query("Limit") limit: Int = 30
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items/{itemId}")
    suspend fun getItemDetails(
        @Path("userId") userId: String,
        @Path("itemId") itemId: String
    ): JellyfinItemDto

    @GET("Shows/{seriesId}/Seasons")
    suspend fun getSeasons(
        @Path("seriesId") seriesId: String,
        @Query("UserId") userId: String,
        @Query("Fields") fields: String = "ItemCounts,Overview"
    ): JellyfinItemsResponse

    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodes(
        @Path("seriesId") seriesId: String,
        @Query("UserId") userId: String,
        @Query("SeasonId") seasonId: String? = null,
        @Query("Fields") fields: String = "Overview,RunTimeTicks,UserData"
    ): JellyfinItemsResponse

    @GET("Items/{itemId}/Similar")
    suspend fun getSimilarItems(
        @Path("itemId") itemId: String,
        @Query("UserId") userId: String,
        @Query("Limit") limit: Int = 15,
        @Query("Fields") fields: String = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,MediaStreams"
    ): JellyfinItemsResponse

    @GET("Items/{itemId}/LocalTrailers")
    suspend fun getLocalTrailers(
        @Path("itemId") itemId: String,
        @Query("UserId") userId: String
    ): List<JellyfinItemDto>

    @POST("Items/{itemId}/PlaybackInfo")
    suspend fun getPlaybackInfo(
        @Path("itemId") itemId: String,
        @Query("UserId") userId: String,
        @Body body: JellyfinPlaybackInfoRequest
    ): JellyfinPlaybackInfoResponse

    @GET("MediaSegments/{itemId}")
    suspend fun getMediaSegments(
        @Path("itemId") itemId: String
    ): JellyfinMediaSegmentsResponse

    @GET("Users/{userId}/Items")
    suspend fun getCollections(
        @Path("userId") userId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "BoxSet",
        @Query("Fields") fields: String = "Overview,ChildCount,ProviderIds,ProductionYear",
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Limit") limit: Int = 100
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getCollectionItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("Fields") fields: String = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,ProductionYear,OfficialRating,CommunityRating,MediaStreams,PremiereDate",
        @Query("SortBy") sortBy: String = "PremiereDate,ProductionYear,SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Limit") limit: Int = 100
    ): JellyfinItemsResponse

    @GET("Shows/Upcoming")
    suspend fun getUpcomingEpisodes(
        @Query("UserId") userId: String,
        @Query("Limit") limit: Int = 24,
        @Query("Fields") fields: String = "Overview,AirTime,SeriesStudio,ProviderIds,UserData,PremiereDate"
    ): JellyfinItemsResponse

    @GET("Items/{itemId}/RemoteSearch/Subtitles/{language}")
    suspend fun searchRemoteSubtitles(
        @Path("itemId") itemId: String,
        @Path("language") language: String = "eng"
    ): List<JellyfinRemoteSubtitleDto>

    @POST("Items/{itemId}/RemoteSearch/Subtitles/{subtitleId}")
    suspend fun downloadRemoteSubtitle(
        @Path("itemId") itemId: String,
        @Path("subtitleId") subtitleId: String
    )

    @POST("Sessions/Playing")
    suspend fun reportPlaybackStart(
        @Body body: JellyfinPlaybackProgressRequest
    )

    @POST("Sessions/Playing/Progress")
    suspend fun reportPlaybackProgress(
        @Body body: JellyfinPlaybackProgressRequest
    )

    @POST("Sessions/Playing/Stopped")
    suspend fun reportPlaybackStopped(
        @Body body: JellyfinPlaybackProgressRequest
    )
}
