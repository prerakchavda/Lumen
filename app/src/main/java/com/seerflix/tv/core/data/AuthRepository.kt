package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.ServerConfig
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinAuthRequest
import com.seerflix.tv.core.network.JellyfinQuickConnectAuthRequest
import com.seerflix.tv.core.network.JellyfinQuickConnectState
import com.seerflix.tv.core.network.JellyfinUserDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrJellyfinLoginRequest
import com.seerflix.tv.core.network.SeerrQuickConnectAuthRequest
import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.absoluteValue

@Singleton
class AuthRepository @Inject constructor(
    private val prefs: SessionPreferences,
    private val jellyfinApi: JellyfinApi,
    private val seerrApi: SeerrApi
) {
    val serverConfigFlow: Flow<ServerConfig> = prefs.serverConfigFlow
    val profilesFlow: Flow<List<UserProfile>> = prefs.profilesFlow
    val activeProfileFlow: Flow<UserProfile?> = prefs.activeProfileFlow

    suspend fun warmup() = prefs.warmup()

    suspend fun testAndSaveServers(
        jellyfinUrl: String,
        seerrUrl: String,
        jellyfinLocalUrl: String = "",
        seerrLocalUrl: String = "",
        trustSelfSignedCerts: Boolean = false,
        seerrApiKey: String = ""
    ): Result<Pair<String, String>> = runCatching {
        prefs.saveServerUrls(
            jellyfinUrl = jellyfinUrl,
            seerrUrl = seerrUrl,
            jellyfinLocalUrl = jellyfinLocalUrl,
            seerrLocalUrl = seerrLocalUrl,
            trustSelfSignedCerts = trustSelfSignedCerts,
            seerrApiKey = seerrApiKey
        )
        val jfInfo = jellyfinApi.getPublicSystemInfo()
        val jfName = jfInfo.serverName ?: "Jellyfin"
        val seerrVer = if (prefs.isSeerrConfigured) {
            val seerrStatus = seerrApi.getStatus()
            seerrStatus.version ?: "Seerr"
        } else {
            "Not Configured (Jellyfin-Only Mode)"
        }
        jfName to seerrVer
    }

    suspend fun fetchPublicUsers(): Result<List<JellyfinUserDto>> = runCatching {
        jellyfinApi.getPublicUsers()
    }

    suspend fun signInWithPassword(username: String, password: String): Result<UserProfile> = runCatching {
        prefs.setImmediateSeerrCookie(null)
        val jfAuth = jellyfinApi.authenticateByName(
            JellyfinAuthRequest(username = username.trim(), password = password)
        )
        val activeJfBase = prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }
        if (prefs.isSeerrConfigured) {
            // Best-effort Seerr v3+ login using the same Jellyfin credentials
            val primaryAttempt = runCatching {
                seerrApi.loginWithJellyfin(
                    SeerrJellyfinLoginRequest(username = username.trim(), password = password)
                )
            }
            if (primaryAttempt.isFailure && activeJfBase.isNotBlank()) {
                runCatching {
                    seerrApi.loginWithJellyfin(
                        SeerrJellyfinLoginRequest(
                            username = username.trim(),
                            password = password,
                            hostname = activeJfBase
                        )
                    )
                }
            }
        }
        val profile = UserProfile(
            id = jfAuth.user.id,
            name = jfAuth.user.name,
            jellyfinUserId = jfAuth.user.id,
            jellyfinToken = jfAuth.accessToken,
            avatarUrl = jfAuth.user.primaryImageTag?.let {
                MediaImageUrls.jellyfinUserAvatar(activeJfBase, jfAuth.user.id)
            },
            avatarColorHex = pickNetflixAvatarColor(jfAuth.user.name),
            seerrCookie = prefs.cachedSeerrCookie,
            isAdmin = jfAuth.user.policy?.isAdministrator == true
        )
        prefs.upsertAndActivateProfile(profile)
        profile
    }

    suspend fun initiateQuickConnect(): Result<JellyfinQuickConnectState> = runCatching {
        val seerrQc = if (prefs.isSeerrConfigured) {
            runCatching { seerrApi.initiateQuickConnect() }.getOrNull()
        } else null
        if (seerrQc != null) {
            JellyfinQuickConnectState(
                secret = seerrQc.secret,
                code = seerrQc.code,
                authenticated = false
            )
        } else {
            jellyfinApi.initiateQuickConnect()
        }
    }

    suspend fun pollAndCompleteQuickConnect(secret: String): Result<UserProfile?> = runCatching {
        val seerrCheck = if (prefs.isSeerrConfigured) {
            runCatching { seerrApi.checkQuickConnect(secret) }.getOrNull()
        } else null
        val isReady = seerrCheck?.authenticated ?: jellyfinApi.checkQuickConnect(secret).authenticated
        if (!isReady) return@runCatching null

        prefs.setImmediateSeerrCookie(null)
        val jfAuth = jellyfinApi.authenticateWithQuickConnect(
            JellyfinQuickConnectAuthRequest(secret = secret)
        )
        if (prefs.isSeerrConfigured) {
            runCatching {
                seerrApi.authenticateQuickConnect(SeerrQuickConnectAuthRequest(secret = secret))
            }
        }

        val activeJfBase = prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }
        val profile = UserProfile(
            id = jfAuth.user.id,
            name = jfAuth.user.name,
            jellyfinUserId = jfAuth.user.id,
            jellyfinToken = jfAuth.accessToken,
            avatarUrl = jfAuth.user.primaryImageTag?.let {
                MediaImageUrls.jellyfinUserAvatar(activeJfBase, jfAuth.user.id)
            },
            avatarColorHex = pickNetflixAvatarColor(jfAuth.user.name),
            seerrCookie = prefs.cachedSeerrCookie,
            isAdmin = jfAuth.user.policy?.isAdministrator == true
        )
        prefs.upsertAndActivateProfile(profile)
        profile
    }

    suspend fun switchProfile(profileId: String) {
        prefs.setActiveProfile(profileId)
    }

    suspend fun clearActiveProfile() {
        prefs.setActiveProfile(null)
    }

    suspend fun removeProfile(profileId: String) {
        prefs.removeProfile(profileId)
    }

    suspend fun updateProfilePreferences(profile: UserProfile) {
        prefs.updateProfilePreferences(profile)
    }

    suspend fun updateSubtitleAppearance(fontScale: Float, backgroundAlpha: Float) {
        prefs.updateSubtitleAppearance(fontScale, backgroundAlpha)
    }

    suspend fun updatePlaybackPreferences(
        autoSkipIntro: Boolean? = null,
        autoSkipRecap: Boolean? = null,
        frameRateMatchMode: com.seerflix.tv.core.model.FrameRateMatchMode? = null,
        audioPassthroughEnabled: Boolean? = null,
        stereoDownmixEnabled: Boolean? = null,
        assSubtitleMode: com.seerflix.tv.core.model.AssSubtitleMode? = null
    ) {
        prefs.updatePlaybackPreferences(
            autoSkipIntro = autoSkipIntro,
            autoSkipRecap = autoSkipRecap,
            frameRateMatchMode = frameRateMatchMode,
            audioPassthroughEnabled = audioPassthroughEnabled,
            stereoDownmixEnabled = stereoDownmixEnabled,
            assSubtitleMode = assSubtitleMode
        )
    }

    suspend fun updateActiveUiPreset(preset: UiPreset) {
        prefs.updateActiveUiPreset(preset)
    }

    fun verifyProfilePin(profile: UserProfile, enteredPin: String): Boolean {
        return SessionPreferences.verifyPin(profile.pinHash, enteredPin)
    }

    private fun pickNetflixAvatarColor(name: String): Long {
        val palette = listOf(
            0xFFE50914L, // Netflix Red
            0xFF0071EBL, // Cobalt Blue
            0xFFE87C03L, // Amber
            0xFF46D369L, // Emerald
            0xFF9B51E0L  // Violet
        )
        val idx = (name.hashCode().absoluteValue) % palette.size
        return palette[idx]
    }
}
