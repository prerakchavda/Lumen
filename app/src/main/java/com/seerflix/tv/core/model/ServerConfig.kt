package com.seerflix.tv.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ServerConfig(
    val jellyfinUrl: String = "",
    val seerrUrl: String = "",
    val deviceId: String = "",
    val jellyfinLocalUrl: String = "",
    val seerrLocalUrl: String = "",
    val trustSelfSignedCerts: Boolean = false,
    val seerrApiKey: String = ""
) {
    val isJellyfinConfigured: Boolean
        get() = jellyfinUrl.isNotBlank() || jellyfinLocalUrl.isNotBlank()

    val isSeerrConfigured: Boolean
        get() = seerrUrl.isNotBlank() || seerrLocalUrl.isNotBlank()

    val isConfigured: Boolean
        get() = isJellyfinConfigured

    val jellyfinCandidates: List<String>
        get() = listOf(jellyfinLocalUrl, jellyfinUrl)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    val seerrCandidates: List<String>
        get() = listOf(seerrLocalUrl, seerrUrl)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
}

@Serializable
enum class FrameRateMatchMode(val label: String) {
    OFF("Off"),
    SEAMLESS_ONLY("Seamless"),
    ALWAYS("Always (HDMI)")
}

/** Subtitle text colour (ARGB). */
@Serializable
enum class SubtitleTextColor(val label: String, val argb: Long) {
    WHITE("White", 0xFFFFFFFF),
    YELLOW("Yellow", 0xFFFFE14D),
    CYAN("Cyan", 0xFF7FE7FF),
    GREEN("Green", 0xFF9CFF8A),
    GREY("Light grey", 0xFFD0D0D0)
}

@Serializable
enum class SubtitleEdgeStyle(val label: String) {
    OUTLINE("Outline"),
    DROP_SHADOW("Drop shadow"),
    RAISED("Raised"),
    NONE("None")
}

/** How far above the bottom edge subtitles sit, as a fraction of the video height. */
@Serializable
enum class SubtitlePosition(val label: String, val bottomFraction: Float) {
    BOTTOM("Bottom", 0.08f),
    RAISED("Raised", 0.15f),
    HIGH("High", 0.24f)
}

@Serializable
enum class AssSubtitleMode(val label: String) {
    STYLED("Styled (ASS)"),
    BURN_IN("Server Burn-In"),
    PLAIN("Plain Text")
}

@Serializable
enum class SubtitleSelectionMode(val label: String) {
    DEFAULT("Default"),
    SMART("Smart"),
    ONLY_FORCED("Only Forced"),
    ALWAYS("Always"),
    NONE("None")
}

@Serializable
enum class VideoResizeMode(val label: String) {
    FIT("Fit"),
    FILL("Fill / Crop"),
    STRETCH("Stretch")
}

enum class SleepTimerOption(val minutes: Int?) {
    OFF(null),
    MIN_15(15),
    MIN_30(30),
    MIN_45(45),
    MIN_60(60),
    END_OF_VIDEO(-1)
}

@Serializable
enum class ContentRatingLimit(val label: String, val maxRank: Int, val jellyfinMaxOfficialRating: String?) {
    ALL("All Ages (Unrestricted)", 4, null),
    PG_13("Teen (PG-13 / TV-14)", 3, "PG-13"),
    PG("Family (PG / TV-PG)", 2, "PG"),
    G("Kids Only (G / TV-Y)", 1, "G")
}

@Serializable
enum class HomeTabId(val label: String) {
    HOME("Home"),
    SHOWS("TV Shows"),
    MOVIES("Movies"),
    COLLECTIONS("Collections"),
    LIVE_TV("Live TV"),
    NEW_AND_POPULAR("New & Popular"),
    MY_LIST("My List")
}

@Immutable
@Serializable
data class UserProfile(
    val id: String,
    val name: String,
    val jellyfinUserId: String,
    val jellyfinToken: String,
    val avatarUrl: String? = null,
    val avatarColorHex: Long = 0xFFE50914,
    val seerrCookie: String? = null,
    /** This profile's Seerr user id: Seerr calls made with the API key act as this user. */
    val seerrUserId: Int? = null,
    val isAdmin: Boolean = false,
    val pinHash: String? = null,
    val autoplayNextEpisode: Boolean = true,
    val autoplayPreviews: Boolean = false,
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val subtitleSelectionMode: SubtitleSelectionMode = SubtitleSelectionMode.DEFAULT,
    val playDefaultAudioTrack: Boolean = true,
    val hasLocalTrackPreferenceOverride: Boolean = false,
    val subtitleFontScale: Float = 1.0f,
    val subtitleBackgroundAlpha: Float = 0.6f,
    val subtitleTextColor: SubtitleTextColor = SubtitleTextColor.WHITE,
    val subtitleEdgeStyle: SubtitleEdgeStyle = SubtitleEdgeStyle.OUTLINE,
    val subtitlePosition: SubtitlePosition = SubtitlePosition.BOTTOM,
    val autoSkipIntro: Boolean = false,
    val autoSkipRecap: Boolean = false,
    val frameRateMatchMode: FrameRateMatchMode = FrameRateMatchMode.OFF,
    val uiPreset: UiPreset = UiPreset.NETFLIX,
    val kidsMode: Boolean = false,
    val maxContentRating: ContentRatingLimit = ContentRatingLimit.ALL,
    val allowKidsRequests: Boolean = false,
    val audioPassthroughEnabled: Boolean = true,
    val stereoDownmixEnabled: Boolean = false,
    val assSubtitleMode: AssSubtitleMode = AssSubtitleMode.STYLED,
    val bingeLimitEpisodes: Int = 0,
    val mergeNextUpIntoContinueWatching: Boolean = true,
    val screensaverTimeoutMinutes: Int = 0,
    val cinemaIntrosEnabled: Boolean = false,
    val themeSongsEnabled: Boolean = false,
    val defaultStartupTab: HomeTabId = HomeTabId.HOME,
    val pinnedLibraryIds: List<String> = emptyList(),
    val hiddenHomeRowIds: List<String> = emptyList(),
    val homeRowOrder: List<String> = emptyList(),
    val metadataLanguage: String = "en",
    val uiLanguage: String = "en",
    /**
     * Accounts on the extra (non-home) servers, keyed by [JellyfinServer.id]. The home server's
     * account stays in [jellyfinUserId] / [jellyfinToken], which is also what older versions read.
     */
    val serverCredentials: Map<String, ServerCredentials> = emptyMap()
) {
    /** The account this profile uses on [serverId], or null when it has none there. */
    fun credentialsFor(serverId: String): ServerCredentials? =
        if (serverId == ServerScopedId.HOME) {
            if (jellyfinUserId.isBlank()) null
            else ServerCredentials(userId = jellyfinUserId, accessToken = jellyfinToken, userName = name)
        } else {
            serverCredentials[serverId]
        }

    val hasPin: Boolean
        get() = !pinHash.isNullOrBlank()

    val effectiveRatingLimit: ContentRatingLimit
        get() = if (kidsMode && maxContentRating == ContentRatingLimit.ALL) {
            ContentRatingLimit.PG
        } else {
            maxContentRating
        }

    val canSubmitRequests: Boolean
        get() = !kidsMode || allowKidsRequests

    /** Kids Mode or a rating limit: may not change server settings or edit itself without a parent. */
    val isRestricted: Boolean
        get() = kidsMode || effectiveRatingLimit != ContentRatingLimit.ALL
}
