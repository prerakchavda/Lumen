package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.ServerConfig
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
        seerrUrl: String
    ): Result<Pair<String, String>> = runCatching {
        prefs.saveServerUrls(jellyfinUrl, seerrUrl)
        val jfInfo = jellyfinApi.getPublicSystemInfo()
        val seerrStatus = seerrApi.getStatus()
        val jfName = jfInfo.serverName ?: "Jellyfin"
        val seerrVer = seerrStatus.version ?: "Seerr"
        jfName to seerrVer
    }

    suspend fun fetchPublicUsers(): Result<List<JellyfinUserDto>> = runCatching {
        jellyfinApi.getPublicUsers()
    }

    suspend fun signInWithPassword(username: String, password: String): Result<UserProfile> = runCatching {
        val jfAuth = jellyfinApi.authenticateByName(
            JellyfinAuthRequest(username = username.trim(), password = password)
        )
        // Best-effort Seerr v3+ login using the same Jellyfin credentials
        runCatching {
            seerrApi.loginWithJellyfin(
                SeerrJellyfinLoginRequest(username = username.trim(), password = password)
            )
        }
        val profile = UserProfile(
            id = jfAuth.user.id,
            name = jfAuth.user.name,
            jellyfinUserId = jfAuth.user.id,
            jellyfinToken = jfAuth.accessToken,
            avatarUrl = jfAuth.user.primaryImageTag?.let {
                MediaImageUrls.jellyfinUserAvatar(prefs.cachedJellyfinUrl, jfAuth.user.id)
            },
            avatarColorHex = pickNetflixAvatarColor(jfAuth.user.name),
            seerrCookie = prefs.cachedSeerrCookie,
            isAdmin = jfAuth.user.policy?.isAdministrator == true
        )
        prefs.upsertAndActivateProfile(profile)
        profile
    }

    suspend fun initiateQuickConnect(): Result<JellyfinQuickConnectState> = runCatching {
        // Prefer Seerr v3+ Quick Connect endpoint if available so one code signs into both!
        val seerrQc = runCatching { seerrApi.initiateQuickConnect() }.getOrNull()
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
        // Check Seerr Quick Connect first, fallback to Jellyfin Quick Connect
        val seerrCheck = runCatching { seerrApi.checkQuickConnect(secret) }.getOrNull()
        val isReady = seerrCheck?.authenticated ?: jellyfinApi.checkQuickConnect(secret).authenticated
        if (!isReady) return@runCatching null

        val jfAuth = jellyfinApi.authenticateWithQuickConnect(
            JellyfinQuickConnectAuthRequest(secret = secret)
        )
        runCatching {
            seerrApi.authenticateQuickConnect(SeerrQuickConnectAuthRequest(secret = secret))
        }

        val profile = UserProfile(
            id = jfAuth.user.id,
            name = jfAuth.user.name,
            jellyfinUserId = jfAuth.user.id,
            jellyfinToken = jfAuth.accessToken,
            avatarUrl = jfAuth.user.primaryImageTag?.let {
                MediaImageUrls.jellyfinUserAvatar(prefs.cachedJellyfinUrl, jfAuth.user.id)
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
