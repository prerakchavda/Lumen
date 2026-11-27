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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

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
    ): Result<Pair<String, String>> {
        // The API clients resolve their base URL from prefs, so the new URLs must be saved to be
        // tested. If the test fails, put back the previous working config so backing out of
        // Settings doesn't leave the app pointed at a broken server.
        val previous = prefs.serverConfigFlow.first()
        val result = testAndSaveServersInternal(
            jellyfinUrl, seerrUrl, jellyfinLocalUrl, seerrLocalUrl, trustSelfSignedCerts, seerrApiKey
        )
        if (result.isFailure && previous.isConfigured) {
            prefs.saveServerUrls(
                jellyfinUrl = previous.jellyfinUrl,
                seerrUrl = previous.seerrUrl,
                jellyfinLocalUrl = previous.jellyfinLocalUrl,
                seerrLocalUrl = previous.seerrLocalUrl,
                trustSelfSignedCerts = previous.trustSelfSignedCerts,
                seerrApiKey = previous.seerrApiKey
            )
        }
        return result
    }

    private suspend fun testAndSaveServersInternal(
        jellyfinUrl: String,
        seerrUrl: String,
        jellyfinLocalUrl: String,
        seerrLocalUrl: String,
        trustSelfSignedCerts: Boolean,
        seerrApiKey: String
    ): Result<Pair<String, String>> = runCatchingCancellable {
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

    suspend fun fetchPublicUsers(): Result<List<JellyfinUserDto>> = runCatchingCancellable {
        jellyfinApi.getPublicUsers()
    }

    suspend fun signInWithPassword(username: String, password: String): Result<UserProfile> {
        // A failed sign-in (wrong password, cancelled "add profile") must not log the still-active
        // profile out of Seerr for the rest of the session.
        val previousSeerrCookie = prefs.cachedSeerrCookie
        return signInWithPasswordInternal(username, password)
            .onFailure { prefs.setImmediateSeerrCookie(previousSeerrCookie) }
    }

    private suspend fun signInWithPasswordInternal(username: String, password: String): Result<UserProfile> = runCatchingCancellable {
        prefs.setImmediateSeerrCookie(null)
        val jfAuth = jellyfinApi.authenticateByName(
            JellyfinAuthRequest(username = username.trim(), password = password)
        )
        val activeJfBase = prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }
        if (prefs.isSeerrConfigured) {
            // Best-effort Seerr v3+ login using the same Jellyfin credentials
            val primaryAttempt = runCatchingCancellable {
                seerrApi.loginWithJellyfin(
                    SeerrJellyfinLoginRequest(username = username.trim(), password = password)
                )
            }
            if (primaryAttempt.isFailure && activeJfBase.isNotBlank()) {
                runCatchingCancellable {
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
        // AuthenticateByName returns the user's Configuration. If it ever doesn't, the sync that runs
        // once this profile is active fills the track defaults in (a lookup here would still go out
        // with the previous profile's token).
        val serverCfg = jfAuth.user.configuration
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
            isAdmin = jfAuth.user.policy?.isAdministrator == true,
            preferredAudioLang = serverCfg?.audioLanguagePreference?.takeIf { it.isNotBlank() },
            preferredSubtitleLang = serverCfg?.subtitleLanguagePreference?.takeIf { it.isNotBlank() },
            subtitleSelectionMode = com.seerflix.tv.core.player.SubtitleTrackSelector.fromJellyfinSubtitleMode(serverCfg?.subtitleMode),
            playDefaultAudioTrack = serverCfg?.playDefaultAudioTrack ?: true
        )
        prefs.upsertAndActivateProfile(profile)
        profile
    }

    suspend fun initiateQuickConnect(): Result<JellyfinQuickConnectState> = runCatchingCancellable {
        val seerrQc = if (prefs.isSeerrConfigured) {
            runCatchingCancellable { seerrApi.initiateQuickConnect() }.getOrNull()
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

    suspend fun pollAndCompleteQuickConnect(secret: String): Result<UserProfile?> {
        val previousSeerrCookie = prefs.cachedSeerrCookie
        return pollAndCompleteQuickConnectInternal(secret)
            .onFailure { prefs.setImmediateSeerrCookie(previousSeerrCookie) }
    }

    private suspend fun pollAndCompleteQuickConnectInternal(secret: String): Result<UserProfile?> = runCatchingCancellable {
        val seerrCheck = if (prefs.isSeerrConfigured) {
            runCatchingCancellable { seerrApi.checkQuickConnect(secret) }.getOrNull()
        } else null
        val isReady = seerrCheck?.authenticated ?: jellyfinApi.checkQuickConnect(secret).authenticated
        if (!isReady) return@runCatchingCancellable null

        prefs.setImmediateSeerrCookie(null)
        val jfAuth = jellyfinApi.authenticateWithQuickConnect(
            JellyfinQuickConnectAuthRequest(secret = secret)
        )
        if (prefs.isSeerrConfigured) {
            runCatchingCancellable {
                seerrApi.authenticateQuickConnect(SeerrQuickConnectAuthRequest(secret = secret))
            }
        }

        val activeJfBase = prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }
        // AuthenticateByName returns the user's Configuration. If it ever doesn't, the sync that runs
        // once this profile is active fills the track defaults in (a lookup here would still go out
        // with the previous profile's token).
        val serverCfg = jfAuth.user.configuration
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
            isAdmin = jfAuth.user.policy?.isAdministrator == true,
            preferredAudioLang = serverCfg?.audioLanguagePreference?.takeIf { it.isNotBlank() },
            preferredSubtitleLang = serverCfg?.subtitleLanguagePreference?.takeIf { it.isNotBlank() },
            subtitleSelectionMode = com.seerflix.tv.core.player.SubtitleTrackSelector.fromJellyfinSubtitleMode(serverCfg?.subtitleMode),
            playDefaultAudioTrack = serverCfg?.playDefaultAudioTrack ?: true
        )
        prefs.upsertAndActivateProfile(profile)
        profile
    }

    /**
     * Pulls Jellyfin's `/Users/{userId}` configuration (AudioLanguagePreference,
     * SubtitleLanguagePreference, SubtitleMode, PlayDefaultAudioTrack) as defaults whenever the
     * active local profile has not explicitly overridden track preferences.
     */
    suspend fun syncUserConfigurationFromServer() {
        val profile = prefs.activeProfileFlow.first() ?: return
        if (profile.jellyfinUserId.isBlank() || profile.hasLocalTrackPreferenceOverride) return
        val cfg = runCatchingCancellable {
            jellyfinApi.getUser(profile.jellyfinUserId).configuration
        }.getOrNull() ?: return
        prefs.applyServerTrackDefaults(
            profileId = profile.id,
            audioLang = cfg.audioLanguagePreference,
            subtitleLang = cfg.subtitleLanguagePreference,
            subtitleMode = com.seerflix.tv.core.player.SubtitleTrackSelector.fromJellyfinSubtitleMode(cfg.subtitleMode),
            playDefaultAudioTrack = cfg.playDefaultAudioTrack
        )
    }

    suspend fun switchProfile(profileId: String) {
        prefs.setActiveProfile(profileId)
        runCatchingCancellable { syncUserConfigurationFromServer() }
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

    suspend fun updateSubtitleStyle(
        textColor: com.seerflix.tv.core.model.SubtitleTextColor? = null,
        edgeStyle: com.seerflix.tv.core.model.SubtitleEdgeStyle? = null,
        position: com.seerflix.tv.core.model.SubtitlePosition? = null
    ) {
        prefs.updateSubtitleStyle(textColor, edgeStyle, position)
    }

    suspend fun updatePlaybackPreferences(
        autoSkipIntro: Boolean? = null,
        autoSkipRecap: Boolean? = null,
        frameRateMatchMode: com.seerflix.tv.core.model.FrameRateMatchMode? = null,
        audioPassthroughEnabled: Boolean? = null,
        stereoDownmixEnabled: Boolean? = null,
        assSubtitleMode: com.seerflix.tv.core.model.AssSubtitleMode? = null,
        subtitleSelectionMode: com.seerflix.tv.core.model.SubtitleSelectionMode? = null,
        bingeLimitEpisodes: Int? = null,
        mergeNextUpIntoContinueWatching: Boolean? = null,
        screensaverTimeoutMinutes: Int? = null,
        cinemaIntrosEnabled: Boolean? = null,
        themeSongsEnabled: Boolean? = null
    ) {
        prefs.updatePlaybackPreferences(
            autoSkipIntro = autoSkipIntro,
            autoSkipRecap = autoSkipRecap,
            frameRateMatchMode = frameRateMatchMode,
            audioPassthroughEnabled = audioPassthroughEnabled,
            stereoDownmixEnabled = stereoDownmixEnabled,
            assSubtitleMode = assSubtitleMode,
            subtitleSelectionMode = subtitleSelectionMode,
            bingeLimitEpisodes = bingeLimitEpisodes,
            mergeNextUpIntoContinueWatching = mergeNextUpIntoContinueWatching,
            screensaverTimeoutMinutes = screensaverTimeoutMinutes,
            cinemaIntrosEnabled = cinemaIntrosEnabled,
            themeSongsEnabled = themeSongsEnabled
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
        // floorMod: Int.MIN_VALUE.absoluteValue is still negative and would index out of bounds.
        val idx = Math.floorMod(name.hashCode(), palette.size)
        return palette[idx]
    }
}
