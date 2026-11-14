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

@Serializable
enum class AssSubtitleMode(val label: String) {
    STYLED("Styled (ASS)"),
    BURN_IN("Server Burn-In"),
    PLAIN("Plain Text")
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
    val isAdmin: Boolean = false,
    val pinHash: String? = null,
    val autoplayNextEpisode: Boolean = true,
    val autoplayPreviews: Boolean = false,
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val subtitleFontScale: Float = 1.0f,
    val subtitleBackgroundAlpha: Float = 0.6f,
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
    val defaultStartupTab: HomeTabId = HomeTabId.HOME,
    val pinnedLibraryIds: List<String> = emptyList(),
    val hiddenHomeRowIds: List<String> = emptyList(),
    val homeRowOrder: List<String> = emptyList(),
    val metadataLanguage: String = "en",
    val uiLanguage: String = "en"
) {
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
}
