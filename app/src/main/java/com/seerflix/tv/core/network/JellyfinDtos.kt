package com.seerflix.tv.core.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JellyfinPublicSystemInfo(
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Version") val version: String? = null,
    @SerialName("Id") val id: String? = null
)

@Serializable
data class JellyfinUserDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("Policy") val policy: JellyfinUserPolicyDto? = null
)

@Serializable
data class JellyfinUserPolicyDto(
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false
)

@Serializable
data class JellyfinAuthRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String
)

@Serializable
data class JellyfinQuickConnectAuthRequest(
    @SerialName("Secret") val secret: String
)

@Serializable
data class JellyfinAuthResult(
    @SerialName("User") val user: JellyfinUserDto,
    @SerialName("AccessToken") val accessToken: String
)

@Serializable
data class JellyfinQuickConnectState(
    @SerialName("Secret") val secret: String,
    @SerialName("Code") val code: String,
    @SerialName("Authenticated") val authenticated: Boolean = false
)

@Serializable
data class JellyfinItemsResponse(
    @SerialName("Items") val items: List<JellyfinItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

@Serializable
data class JellyfinUserDataDto(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0L,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null
)

@Serializable
data class JellyfinPersonDto(
    @SerialName("Name") val name: String,
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null
)

@Serializable
data class JellyfinMediaUrlDto(
    @SerialName("Name") val name: String? = null,
    @SerialName("Url") val url: String? = null
)

@Serializable
data class JellyfinItemDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Type") val type: String,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("ProviderIds") val providerIds: Map<String, String> = emptyMap(),
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
    @SerialName("UserData") val userData: JellyfinUserDataDto? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("People") val people: List<JellyfinPersonDto> = emptyList(),
    @SerialName("RemoteTrailers") val remoteTrailers: List<JellyfinMediaUrlDto> = emptyList(),
    @SerialName("LocalTrailerCount") val localTrailerCount: Int? = null
)

@Serializable
data class JellyfinPlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
    @SerialName("DeviceProfile") val deviceProfile: JellyfinDeviceProfileDto
)

@Serializable
data class JellyfinDeviceProfileDto(
    @SerialName("Name") val name: String = "Seerflix Google TV",
    @SerialName("MaxStreamingBitrate") val maxStreamingBitrate: Long = 120_000_000L,
    @SerialName("DirectPlayProfiles") val directPlayProfiles: List<JellyfinDirectPlayProfileDto> = listOf(
        JellyfinDirectPlayProfileDto(
            type = "Video",
            container = "mp4,m4v,mkv,webm,mov",
            videoCodec = "h264,hevc,vp9,av1",
            audioCodec = "aac,mp3,ac3,eac3,opus,flac,vorbis"
        )
    ),
    @SerialName("TranscodingProfiles") val transcodingProfiles: List<JellyfinTranscodingProfileDto> = listOf(
        JellyfinTranscodingProfileDto(
            container = "ts",
            type = "Video",
            videoCodec = "h264,hevc",
            audioCodec = "aac,ac3,eac3,mp3",
            protocol = "hls"
        )
    )
)

@Serializable
data class JellyfinDirectPlayProfileDto(
    @SerialName("Type") val type: String,
    @SerialName("Container") val container: String,
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String
)

@Serializable
data class JellyfinTranscodingProfileDto(
    @SerialName("Container") val container: String,
    @SerialName("Type") val type: String,
    @SerialName("VideoCodec") val videoCodec: String,
    @SerialName("AudioCodec") val audioCodec: String,
    @SerialName("Protocol") val protocol: String
)

@Serializable
data class JellyfinPlaybackInfoResponse(
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSourceDto> = emptyList(),
    @SerialName("PlaySessionId") val playSessionId: String? = null
)

@Serializable
data class JellyfinMediaSourceDto(
    @SerialName("Id") val id: String,
    @SerialName("Container") val container: String? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStreamDto> = emptyList()
)

@Serializable
data class JellyfinMediaStreamDto(
    @SerialName("Index") val index: Int,
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null
)

@Serializable
data class JellyfinMediaSegmentsResponse(
    @SerialName("Items") val items: List<JellyfinMediaSegmentDto> = emptyList()
)

@Serializable
data class JellyfinMediaSegmentDto(
    @SerialName("Type") val type: String,
    @SerialName("StartTicks") val startTicks: Long = 0L,
    @SerialName("EndTicks") val endTicks: Long = 0L
)

@Serializable
data class JellyfinPlaybackProgressRequest(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean = false
)
