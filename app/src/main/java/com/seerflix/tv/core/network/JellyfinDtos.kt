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
    @SerialName("Policy") val policy: JellyfinUserPolicyDto? = null,
    @SerialName("Configuration") val configuration: JellyfinUserConfigurationDto? = null
)

@Serializable
data class JellyfinUserPolicyDto(
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false
)

@Serializable
data class JellyfinUserConfigurationDto(
    @SerialName("AudioLanguagePreference") val audioLanguagePreference: String? = null,
    @SerialName("PlayDefaultAudioTrack") val playDefaultAudioTrack: Boolean = true,
    @SerialName("SubtitleLanguagePreference") val subtitleLanguagePreference: String? = null,
    @SerialName("SubtitleMode") val subtitleMode: String? = null
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
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
    /** When the item was last played; decides which server's progress wins for a merged title. */
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null
)

@Serializable
data class JellyfinUpdateUserDataRequest(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0L
)

@Serializable
data class JellyfinPersonDto(
    @SerialName("Name") val name: String = "",
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
data class JellyfinTrickplayInfoDto(
    @SerialName("Width") val width: Int = 320,
    @SerialName("Height") val height: Int = 180,
    @SerialName("TileWidth") val tileWidth: Int = 10,
    @SerialName("TileHeight") val tileHeight: Int = 10,
    @SerialName("ThumbnailCount") val thumbnailCount: Int = 0,
    @SerialName("Interval") val interval: Int = 10000,
    @SerialName("Bandwidth") val bandwidth: Long = 0L
)

@Serializable
data class JellyfinItemDto(
    @SerialName("Id") val id: String,
    // BaseItemDto.Name is nullable server-side; one nameless item must not fail a whole page.
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
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
    @SerialName("LocalTrailerCount") val localTrailerCount: Int? = null,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStreamDto> = emptyList(),
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, JellyfinTrickplayInfoDto>> = emptyMap(),
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("MediaSources") val mediaSources: List<JellyfinMediaSourceDto> = emptyList(),
    @SerialName("Chapters") val chapters: List<JellyfinChapterDto> = emptyList(),
    @SerialName("ExtraType") val extraType: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("ChannelNumber") val channelNumber: String? = null,
    @SerialName("CurrentProgram") val currentProgram: JellyfinLiveTvProgramDto? = null,
    // Live TV programmes: set when a recording is scheduled for the programme / its series.
    @SerialName("TimerId") val timerId: String? = null,
    @SerialName("SeriesTimerId") val seriesTimerId: String? = null,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("IsSeries") val isSeries: Boolean = false,
    @SerialName("ChannelId") val channelId: String? = null,
    // Only requested (and only read) when lists from several servers are interleaved.
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("SortName") val sortName: String? = null,
    @SerialName("CanDelete") val canDelete: Boolean? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("IsMovie") val isMovie: Boolean = false,
    @SerialName("IsSports") val isSports: Boolean = false,
    @SerialName("IsNews") val isNews: Boolean = false,
    @SerialName("IsKids") val isKids: Boolean = false,
    @SerialName("PlaylistItemId") val playlistItemId: String? = null
)

@Serializable
data class JellyfinChapterDto(
    @SerialName("StartPositionTicks") val startPositionTicks: Long = 0L,
    @SerialName("Name") val name: String? = null
)

@Serializable
data class JellyfinLiveTvProgramDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("ChannelId") val channelId: String? = null,
    @SerialName("IsSeries") val isSeries: Boolean = false,
    /** Set when a recording is scheduled for this programme / its series. */
    @SerialName("TimerId") val timerId: String? = null,
    @SerialName("SeriesTimerId") val seriesTimerId: String? = null,
    @SerialName("IsMovie") val isMovie: Boolean = false,
    @SerialName("IsSports") val isSports: Boolean = false,
    @SerialName("IsNews") val isNews: Boolean = false,
    @SerialName("IsKids") val isKids: Boolean = false
)

@Serializable
data class JellyfinSyncPlayGroupDto(
    @SerialName("GroupId") val groupId: String = "",
    @SerialName("GroupName") val groupName: String = "",
    @SerialName("State") val state: String = "Idle",
    @SerialName("Participants") val participants: List<String> = emptyList(),
    @SerialName("LastUpdatedAt") val lastUpdatedAt: String? = null
)

@Serializable
data class JellyfinCreateSyncPlayRequest(
    @SerialName("GroupName") val groupName: String
)

@Serializable
data class JellyfinJoinSyncPlayRequest(
    @SerialName("GroupId") val groupId: String
)

@Serializable
data class JellyfinSeekSyncPlayRequest(
    @SerialName("PositionTicks") val positionTicks: Long
)

@Serializable
data class JellyfinRemoteSubtitleDto(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("ProviderName") val providerName: String? = null,
    @SerialName("Format") val format: String? = null,
    @SerialName("Author") val author: String? = null,
    @SerialName("Comment") val comment: String? = null,
    @SerialName("CommunityRating") val communityRating: Float? = null,
    @SerialName("DownloadCount") val downloadCount: Int? = null,
    @SerialName("IsHashMatch") val isHashMatch: Boolean? = null,
    @SerialName("ThreeLetterISOLanguageName") val threeLetterIsoLanguageName: String? = null
)

@Serializable
data class JellyfinPlaybackInfoRequest(
    @SerialName("UserId") val userId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("MaxAudioChannels") val maxAudioChannels: Int? = null,
    @SerialName("DeviceProfile") val deviceProfile: JellyfinDeviceProfileDto,
    /** null = server default (allowed); false forces a remux / transcode. */
    @SerialName("EnableDirectPlay") val enableDirectPlay: Boolean? = null
)

@Serializable
data class JellyfinDeviceProfileDto(
    @SerialName("Name") val name: String = "Lumen Google TV",
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
    ),
    @SerialName("SubtitleProfiles") val subtitleProfiles: List<JellyfinSubtitleProfileDto> = emptyList(),
    @SerialName("CodecProfiles") val codecProfiles: List<JellyfinCodecProfileDto> = emptyList()
)

/** Limits on a codec (bit depth, HDR type, size) that decide direct play vs remux / transcode. */
@Serializable
data class JellyfinCodecProfileDto(
    @SerialName("Type") val type: String,
    @SerialName("Codec") val codec: String,
    @SerialName("Conditions") val conditions: List<JellyfinProfileConditionDto> = emptyList()
)

@Serializable
data class JellyfinProfileConditionDto(
    /** Equals, NotEquals, LessThanEqual, GreaterThanEqual or EqualsAny (values separated by '|'). */
    @SerialName("Condition") val condition: String,
    @SerialName("Property") val property: String,
    @SerialName("Value") val value: String,
    @SerialName("IsRequired") val isRequired: Boolean = false
)

@Serializable
data class JellyfinSubtitleProfileDto(
    @SerialName("Format") val format: String,
    @SerialName("Method") val method: String
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
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Bitrate") val bitrate: Long? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean = false,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean = false,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean = false,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("TranscodingSubProtocol") val transcodingSubProtocol: String? = null,
    @SerialName("DefaultAudioStreamIndex") val defaultAudioStreamIndex: Int? = null,
    @SerialName("DefaultSubtitleStreamIndex") val defaultSubtitleStreamIndex: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("MediaStreams") val mediaStreams: List<JellyfinMediaStreamDto> = emptyList()
)

@Serializable
data class JellyfinMediaStreamDto(
    @SerialName("Index") val index: Int,
    @SerialName("Type") val type: String = "",
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("BitRate") val bitRate: Long? = null,
    @SerialName("BitDepth") val bitDepth: Int? = null,
    @SerialName("Profile") val profile: String? = null,
    @SerialName("VideoRange") val videoRange: String? = null,
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("ChannelLayout") val channelLayout: String? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("IsHearingImpaired") val isHearingImpaired: Boolean = false,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("DeliveryMethod") val deliveryMethod: String? = null,
    @SerialName("AudioSpatialFormat") val audioSpatialFormat: String? = null,
    @SerialName("RealFrameRate") val realFrameRate: Float? = null,
    @SerialName("AverageFrameRate") val averageFrameRate: Float? = null
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
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("PlayMethod") val playMethod: String? = null,
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
    @SerialName("IsMuted") val isMuted: Boolean? = null,
    @SerialName("VolumeLevel") val volumeLevel: Int? = null,
    @SerialName("PlaybackRate") val playbackRate: Float? = null
)

@Serializable
data class JellyfinSessionCapabilitiesRequest(
    @SerialName("PlayableMediaTypes") val playableMediaTypes: List<String>,
    @SerialName("SupportedCommands") val supportedCommands: List<String>,
    @SerialName("SupportsMediaControl") val supportsMediaControl: Boolean,
    @SerialName("SupportsPersistentIdentifier") val supportsPersistentIdentifier: Boolean
)

@Serializable
data class JellyfinUtcTimeResponse(
    @SerialName("RequestReceptionTime") val requestReceptionTime: String? = null,
    @SerialName("ResponseTransmissionTime") val responseTransmissionTime: String? = null
)

@Serializable
data class JellyfinSyncPlayNewQueueRequest(
    @SerialName("PlayingQueue") val playingQueue: List<String>,
    @SerialName("PlayingItemPosition") val playingItemPosition: Int = 0,
    @SerialName("StartPositionTicks") val startPositionTicks: Long = 0L
)

@Serializable
data class JellyfinSyncPlayBufferingRequest(
    @SerialName("When") val whenUtc: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPlaying") val isPlaying: Boolean,
    @SerialName("PlaylistItemId") val playlistItemId: String
)

@Serializable
data class JellyfinSyncPlayPingRequest(
    @SerialName("Ping") val ping: Long
)

@Serializable
data class JellyfinClientLogResponse(
    @SerialName("FileName") val fileName: String = ""
)

@Serializable
data class JellyfinTimersResponse(
    @SerialName("Items") val items: List<JellyfinTimerDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0
)

@Serializable
data class JellyfinTimerDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("ChannelId") val channelId: String? = null,
    @SerialName("ChannelName") val channelName: String? = null,
    @SerialName("ProgramId") val programId: String? = null,
    @SerialName("SeriesTimerId") val seriesTimerId: String? = null,
    @SerialName("StartDate") val startDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("RecordAnyChannel") val recordAnyChannel: Boolean? = null,
    @SerialName("RecordNewOnly") val recordNewOnly: Boolean? = null
)

@Serializable
data class JellyfinCreatePlaylistRequest(
    @SerialName("Name") val name: String,
    @SerialName("Ids") val ids: List<String> = emptyList(),
    @SerialName("UserId") val userId: String,
    @SerialName("MediaType") val mediaType: String = "Video"
)

@Serializable
data class JellyfinCreatePlaylistResponse(
    @SerialName("Id") val id: String = ""
)
