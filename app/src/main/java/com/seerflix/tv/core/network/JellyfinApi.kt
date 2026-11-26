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

    @GET("Users/{userId}")
    suspend fun getUser(
        @Path("userId") userId: String
    ): JellyfinUserDto

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

    @GET("Users/{userId}/Views")
    suspend fun getUserViews(
        @Path("userId") userId: String
    ): JellyfinItemsResponse

    @GET("Users/{userId}/Items")
    suspend fun getLibraryItems(
        @Path("userId") userId: String,
        @Query("Recursive") recursive: Boolean = true,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Fields") fields: String = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,ProductionYear,OfficialRating,CommunityRating,RemoteTrailers,MediaStreams",
        @Query("SortBy") sortBy: String = "DateCreated,SortName",
        @Query("SortOrder") sortOrder: String = "Descending",
        @Query("Limit") limit: Int = 500,
        @Query("ParentId") parentId: String? = null,
        @Query("MaxOfficialRating") maxOfficialRating: String? = null,
        @Query("StartIndex") startIndex: Int? = null,
        // Payload trimming knobs (null = server default, i.e. omitted from the query string).
        @Query("Ids") ids: String? = null,
        @Query("EnableImages") enableImages: Boolean? = null,
        @Query("ImageTypeLimit") imageTypeLimit: Int? = null,
        @Query("EnableImageTypes") enableImageTypes: String? = null,
        @Query("EnableUserData") enableUserData: Boolean? = null,
        @Query("EnableTotalRecordCount") enableTotalRecordCount: Boolean? = null
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
        @Path("itemId") itemId: String,
        @Query("Fields") fields: String = "Chapters,MediaSources,MediaStreams,Overview,Genres,ProviderIds,UserData,People,RemoteTrailers,OfficialRating"
    ): JellyfinItemDto

    @GET("Items/{itemId}/SpecialFeatures")
    suspend fun getSpecialFeatures(
        @Path("itemId") itemId: String,
        @Query("UserId") userId: String
    ): List<JellyfinItemDto>

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
        @Query("Fields") fields: String = "Overview,RunTimeTicks,UserData",
        // Exclude missing/virtual (not yet downloaded) episodes so Up Next never picks one.
        @Query("IsMissing") isMissing: Boolean? = false
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

    @GET("LiveTv/Channels")
    suspend fun getLiveTvChannels(
        @Query("UserId") userId: String,
        @Query("Fields") fields: String = "PrimaryImageAspectRatio,ChannelInfo",
        @Query("AddCurrentProgram") addCurrentProgram: Boolean = true
    ): JellyfinItemsResponse

    @GET("LiveTv/Programs")
    suspend fun getLiveTvPrograms(
        @Query("UserId") userId: String,
        @Query("HasAired") hasAired: Boolean = false,
        @Query("Limit") limit: Int = 100,
        @Query("Fields") fields: String = "Overview,ChannelInfo"
    ): JellyfinItemsResponse

    @GET("LiveTv/Recordings")
    suspend fun getLiveTvRecordings(
        @Query("UserId") userId: String,
        @Query("Limit") limit: Int = 40,
        @Query("Fields") fields: String = "Overview,RunTimeTicks,UserData"
    ): JellyfinItemsResponse

    @GET("SyncPlay/List")
    suspend fun getSyncPlayGroups(): List<JellyfinSyncPlayGroupDto>

    @POST("SyncPlay/New")
    suspend fun createSyncPlayGroup(
        @Body body: JellyfinCreateSyncPlayRequest
    )

    @POST("SyncPlay/Join")
    suspend fun joinSyncPlayGroup(
        @Body body: JellyfinJoinSyncPlayRequest
    )

    @POST("SyncPlay/Leave")
    suspend fun leaveSyncPlayGroup()

    @POST("SyncPlay/Pause")
    suspend fun syncPlayPause()

    @POST("SyncPlay/Unpause")
    suspend fun syncPlayUnpause()

    @POST("SyncPlay/Seek")
    suspend fun syncPlaySeek(
        @Body body: JellyfinSeekSyncPlayRequest
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

    /**
     * Lightweight episode listing used by playback (episode resolution / next-episode lookup):
     * server-default fields only, no image tags, and optionally paged from [startItemId]
     * (`StartItemId` skips the series-ordered list up to that episode, so `Limit=n` returns it
     * plus the following n-1 episodes instead of the whole series).
     */
    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodesLite(
        @Path("seriesId") seriesId: String,
        @Query("UserId") userId: String,
        @Query("SeasonId") seasonId: String? = null,
        @Query("StartItemId") startItemId: String? = null,
        @Query("Limit") limit: Int? = null,
        @Query("Fields") fields: String? = null,
        @Query("EnableImages") enableImages: Boolean = false,
        @Query("IsMissing") isMissing: Boolean? = false
    ): JellyfinItemsResponse

    /** In-progress items under [parentId] (e.g. a series), without images or heavy fields. */
    @GET("Users/{userId}/Items/Resume")
    suspend fun getResumeItemsLite(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("Limit") limit: Int = 10,
        @Query("MediaTypes") mediaTypes: String = "Video",
        @Query("EnableImages") enableImages: Boolean = false
    ): JellyfinItemsResponse

    // ---- Remote control / live connection ----

    /** Registers what this session can do, so other Jellyfin apps can "Play on" this TV. */
    @POST("Sessions/Capabilities/Full")
    suspend fun postSessionCapabilities(@Body body: JellyfinSessionCapabilitiesRequest)

    /** Server clock, used to line SyncPlay commands up with the other group members. */
    @GET("GetUtcTime")
    suspend fun getUtcTime(): JellyfinUtcTimeResponse

    @POST("SyncPlay/SetNewQueue")
    suspend fun syncPlaySetNewQueue(@Body body: JellyfinSyncPlayNewQueueRequest)

    @POST("SyncPlay/Ready")
    suspend fun syncPlayReady(@Body body: JellyfinSyncPlayBufferingRequest)

    @POST("SyncPlay/Buffering")
    suspend fun syncPlayBuffering(@Body body: JellyfinSyncPlayBufferingRequest)

    @POST("SyncPlay/Ping")
    suspend fun syncPlayPing(@Body body: JellyfinSyncPlayPingRequest)

    @POST("SyncPlay/Stop")
    suspend fun syncPlayStop()

    // ---- Diagnostics ----

    /** Stores a log file on the server (Dashboard > Logs); needs "Allow client log upload". */
    @POST("ClientLog/Document")
    suspend fun uploadClientLog(@Body body: okhttp3.RequestBody): JellyfinClientLogResponse

    // ---- DVR ----

    @GET("LiveTv/Timers")
    suspend fun getLiveTvTimers(
        @Query("IsActive") isActive: Boolean? = null,
        @Query("IsScheduled") isScheduled: Boolean? = true
    ): JellyfinTimersResponse

    /** Recording defaults (padding, channel, times) for a programme; posted back to create a timer. */
    @GET("LiveTv/Timers/Defaults")
    suspend fun getLiveTvTimerDefaults(@Query("programId") programId: String): kotlinx.serialization.json.JsonObject

    @POST("LiveTv/Timers")
    suspend fun createLiveTvTimer(@Body body: kotlinx.serialization.json.JsonObject)

    @DELETE("LiveTv/Timers/{timerId}")
    suspend fun cancelLiveTvTimer(@Path("timerId") timerId: String)

    @GET("LiveTv/SeriesTimers")
    suspend fun getLiveTvSeriesTimers(): JellyfinTimersResponse

    @POST("LiveTv/SeriesTimers")
    suspend fun createLiveTvSeriesTimer(@Body body: kotlinx.serialization.json.JsonObject)

    @DELETE("LiveTv/SeriesTimers/{timerId}")
    suspend fun cancelLiveTvSeriesTimer(@Path("timerId") timerId: String)

    // ---- Multiple servers ----

    /**
     * Episodes of one season by season *number*. A series that exists on several servers has a
     * different season id on each, but the same season numbers.
     */
    @GET("Shows/{seriesId}/Episodes")
    suspend fun getEpisodesBySeasonNumber(
        @Path("seriesId") seriesId: String,
        @Query("UserId") userId: String,
        @Query("Season") season: Int? = null,
        @Query("Fields") fields: String? = "Overview,RunTimeTicks,UserData",
        @Query("EnableImages") enableImages: Boolean? = null,
        @Query("IsMissing") isMissing: Boolean? = false
    ): JellyfinItemsResponse

    /** Ends this device's session on the server, revoking its access token. */
    @POST("Sessions/Logout")
    suspend fun logout()

    // ---- Library browsing ----

    /** One page of a library for the A-Z grid, with server-side sort and filters. */
    @GET("Users/{userId}/Items")
    suspend fun browseItems(
        @Path("userId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("Recursive") recursive: Boolean = true,
        @Query("SortBy") sortBy: String = "SortName",
        @Query("SortOrder") sortOrder: String = "Ascending",
        @Query("Filters") filters: String? = null,
        @Query("Genres") genres: String? = null,
        @Query("Years") years: String? = null,
        @Query("NameStartsWith") nameStartsWith: String? = null,
        @Query("NameLessThan") nameLessThan: String? = null,
        @Query("MaxOfficialRating") maxOfficialRating: String? = null,
        @Query("StartIndex") startIndex: Int = 0,
        @Query("Limit") limit: Int = 60,
        @Query("Fields") fields: String = "ProviderIds,ProductionYear,OfficialRating,CommunityRating,UserData,PrimaryImageAspectRatio,Genres,RunTimeTicks",
        @Query("ImageTypeLimit") imageTypeLimit: Int = 1,
        @Query("EnableImageTypes") enableImageTypes: String = "Primary,Backdrop,Thumb",
        @Query("EnableTotalRecordCount") enableTotalRecordCount: Boolean = true
    ): JellyfinItemsResponse

    @GET("Genres")
    suspend fun getGenres(
        @Query("UserId") userId: String,
        @Query("ParentId") parentId: String? = null,
        @Query("IncludeItemTypes") includeItemTypes: String = "Movie,Series",
        @Query("SortBy") sortBy: String = "SortName"
    ): JellyfinItemsResponse
}
