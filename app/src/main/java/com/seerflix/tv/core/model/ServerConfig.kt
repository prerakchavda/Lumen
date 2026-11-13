package com.seerflix.tv.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerConfig(
    val jellyfinUrl: String = "",
    val seerrUrl: String = "",
    val deviceId: String = ""
) {
    val isConfigured: Boolean
        get() = jellyfinUrl.isNotBlank() && seerrUrl.isNotBlank()
}

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
    val autoplayPreviews: Boolean = true,
    val preferredAudioLang: String? = null,
    val preferredSubtitleLang: String? = null,
    val subtitleFontScale: Float = 1.0f,
    val subtitleBackgroundAlpha: Float = 0.6f
) {
    val hasPin: Boolean
        get() = !pinHash.isNullOrBlank()
}
