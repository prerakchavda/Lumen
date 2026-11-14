package com.seerflix.tv.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ServerConfig
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "seerflix_prefs")

@Serializable
data class LocalMyListEntry(
    val id: String,
    val tmdbId: Int,
    val mediaType: MediaType,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val releaseYear: String? = null,
    val voteAverage: Double? = null,
    val overview: String = ""
) {
    fun toUnifiedMediaItem(): UnifiedMediaItem = UnifiedMediaItem(
        id = id,
        tmdbId = tmdbId,
        mediaType = mediaType,
        title = title,
        overview = overview,
        releaseYear = releaseYear,
        voteAverage = voteAverage,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        isInMyList = true
    )
}

@Singleton
class SessionPreferences private constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        json: Json
    ) : this(context.dataStore, json)

    internal constructor(
        testFile: File,
        json: Json
    ) : this(
        PreferenceDataStoreFactory.create(produceFile = { testFile }),
        json
    )

    private object Keys {
        val JELLYFIN_URL = stringPreferencesKey("jellyfin_url")
        val JELLYFIN_LOCAL_URL = stringPreferencesKey("jellyfin_local_url")
        val SEERR_URL = stringPreferencesKey("seerr_url")
        val SEERR_LOCAL_URL = stringPreferencesKey("seerr_local_url")
        val SEERR_API_KEY = stringPreferencesKey("seerr_api_key")
        val TRUST_SELF_SIGNED = stringPreferencesKey("trust_self_signed")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val TRACKED_REQUESTS_JSON = stringPreferencesKey("tracked_requests_json")
        val READY_ALERTS_JSON = stringPreferencesKey("ready_alerts_json")

        fun localMyList(profileId: String) = stringPreferencesKey("local_my_list_$profileId")
        fun reminders(profileId: String) = stringPreferencesKey("reminders_$profileId")
    }

    @Volatile
    var cachedJellyfinUrl: String = ""
        private set

    @Volatile
    var cachedJellyfinLocalUrl: String = ""
        private set

    @Volatile
    var cachedSeerrUrl: String = ""
        private set

    @Volatile
    var cachedSeerrLocalUrl: String = ""
        private set

    @Volatile
    var cachedSeerrApiKey: String = ""
        private set

    @Volatile
    var cachedResolvedJellyfinUrl: String = ""
        internal set

    @Volatile
    var cachedResolvedSeerrUrl: String = ""
        internal set

    @Volatile
    var cachedTrustSelfSigned: Boolean = false
        private set

    @Volatile
    var cachedDeviceId: String = UUID.randomUUID().toString()
        private set

    @Volatile
    var cachedToken: String? = null
        private set

    @Volatile
    var cachedSeerrCookie: String? = null
        private set

    fun setImmediateSeerrCookie(cookie: String?) {
        cachedSeerrCookie = cookie
    }

    @Volatile
    var cachedMetadataLanguage: String = "en"
        private set

    @Volatile
    var cachedUiLanguage: String = "en"
        private set

    val isSeerrConfigured: Boolean
        get() = cachedSeerrUrl.isNotBlank() || cachedSeerrLocalUrl.isNotBlank()

    val jellyfinUrlCandidates: List<String>
        get() = buildList {
            if (cachedResolvedJellyfinUrl.isNotBlank()) add(cachedResolvedJellyfinUrl)
            if (cachedJellyfinLocalUrl.isNotBlank()) add(cachedJellyfinLocalUrl)
            if (cachedJellyfinUrl.isNotBlank()) add(cachedJellyfinUrl)
        }.distinct()

    val seerrUrlCandidates: List<String>
        get() = buildList {
            if (cachedResolvedSeerrUrl.isNotBlank()) add(cachedResolvedSeerrUrl)
            if (cachedSeerrLocalUrl.isNotBlank()) add(cachedSeerrLocalUrl)
            if (cachedSeerrUrl.isNotBlank()) add(cachedSeerrUrl)
        }.distinct()

    val serverConfigFlow: Flow<ServerConfig> = dataStore.data.map { prefs ->
        val deviceId = prefs[Keys.DEVICE_ID] ?: UUID.randomUUID().toString()
        val trust = prefs[Keys.TRUST_SELF_SIGNED] == "true"
        val config = ServerConfig(
            jellyfinUrl = prefs[Keys.JELLYFIN_URL].orEmpty(),
            seerrUrl = prefs[Keys.SEERR_URL].orEmpty(),
            deviceId = deviceId,
            jellyfinLocalUrl = prefs[Keys.JELLYFIN_LOCAL_URL].orEmpty(),
            seerrLocalUrl = prefs[Keys.SEERR_LOCAL_URL].orEmpty(),
            trustSelfSignedCerts = trust,
            seerrApiKey = prefs[Keys.SEERR_API_KEY].orEmpty()
        )
        cachedJellyfinUrl = config.jellyfinUrl
        cachedJellyfinLocalUrl = config.jellyfinLocalUrl
        cachedSeerrUrl = config.seerrUrl
        cachedSeerrLocalUrl = config.seerrLocalUrl
        cachedSeerrApiKey = config.seerrApiKey
        cachedTrustSelfSigned = trust
        if (cachedResolvedJellyfinUrl.isBlank() || cachedResolvedJellyfinUrl !in config.jellyfinCandidates) {
            cachedResolvedJellyfinUrl = config.jellyfinCandidates.firstOrNull().orEmpty()
        }
        if (cachedResolvedSeerrUrl.isBlank() || cachedResolvedSeerrUrl !in config.seerrCandidates) {
            cachedResolvedSeerrUrl = config.seerrCandidates.firstOrNull().orEmpty()
        }
        cachedDeviceId = deviceId
        config
    }

    val profilesFlow: Flow<List<UserProfile>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.PROFILES_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<UserProfile>>(raw) }.getOrDefault(emptyList())
    }

    val activeProfileFlow: Flow<UserProfile?> = dataStore.data.map { prefs ->
        val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: return@map null
        val raw = prefs[Keys.PROFILES_JSON] ?: return@map null
        val list = runCatching { json.decodeFromString<List<UserProfile>>(raw) }.getOrDefault(emptyList())
        val found = list.firstOrNull { it.id == activeId }
        cachedToken = found?.jellyfinToken
        if (!found?.seerrCookie.isNullOrBlank()) {
            cachedSeerrCookie = found?.seerrCookie
        }
        cachedMetadataLanguage = found?.metadataLanguage?.ifBlank { "en" } ?: "en"
        cachedUiLanguage = found?.uiLanguage?.ifBlank { "en" } ?: "en"
        found
    }

    val readyAlertsFlow: Flow<List<com.seerflix.tv.core.model.ReadyRequestAlert>> = dataStore.data.map { prefs ->
        val raw = prefs[Keys.READY_ALERTS_JSON] ?: return@map emptyList()
        runCatching {
            json.decodeFromString<List<com.seerflix.tv.core.model.ReadyRequestAlert>>(raw)
        }.getOrDefault(emptyList())
    }

    suspend fun warmup() {
        serverConfigFlow.first()
        activeProfileFlow.first()
        dataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = cachedDeviceId
            }
        }
    }

    suspend fun saveServerUrls(
        jellyfinUrl: String,
        seerrUrl: String,
        jellyfinLocalUrl: String = "",
        seerrLocalUrl: String = "",
        trustSelfSignedCerts: Boolean = false,
        seerrApiKey: String = cachedSeerrApiKey
    ) {
        val cleanJf = normalizeUrl(jellyfinUrl)
        val cleanJfLocal = normalizeUrl(jellyfinLocalUrl)
        val cleanSeerr = normalizeUrl(seerrUrl)
        val cleanSeerrLocal = normalizeUrl(seerrLocalUrl)
        val cleanApiKey = seerrApiKey.trim()
        cachedJellyfinUrl = cleanJf
        cachedJellyfinLocalUrl = cleanJfLocal
        cachedSeerrUrl = cleanSeerr
        cachedSeerrLocalUrl = cleanSeerrLocal
        cachedSeerrApiKey = cleanApiKey
        cachedTrustSelfSigned = trustSelfSignedCerts
        cachedResolvedJellyfinUrl = cleanJfLocal.ifBlank { cleanJf }
        cachedResolvedSeerrUrl = cleanSeerrLocal.ifBlank { cleanSeerr }
        dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_URL] = cleanJf
            prefs[Keys.JELLYFIN_LOCAL_URL] = cleanJfLocal
            prefs[Keys.SEERR_URL] = cleanSeerr
            prefs[Keys.SEERR_LOCAL_URL] = cleanSeerrLocal
            prefs[Keys.SEERR_API_KEY] = cleanApiKey
            prefs[Keys.TRUST_SELF_SIGNED] = trustSelfSignedCerts.toString()
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = cachedDeviceId
            }
        }
    }

    suspend fun upsertAndActivateProfile(profile: UserProfile) {
        var resolvedProfile = profile
        dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val existing = current.firstOrNull { it.id == profile.id }
            val effectiveCookie = profile.seerrCookie ?: cachedSeerrCookie ?: existing?.seerrCookie
            val merged = if (existing != null) {
                profile.copy(
                    seerrCookie = effectiveCookie,
                    pinHash = existing.pinHash,
                    autoplayNextEpisode = existing.autoplayNextEpisode,
                    autoplayPreviews = existing.autoplayPreviews,
                    preferredAudioLang = existing.preferredAudioLang,
                    preferredSubtitleLang = existing.preferredSubtitleLang,
                    subtitleFontScale = existing.subtitleFontScale,
                    subtitleBackgroundAlpha = existing.subtitleBackgroundAlpha,
                    autoSkipIntro = existing.autoSkipIntro,
                    autoSkipRecap = existing.autoSkipRecap,
                    frameRateMatchMode = existing.frameRateMatchMode,
                    uiPreset = existing.uiPreset,
                    kidsMode = existing.kidsMode,
                    maxContentRating = existing.maxContentRating,
                    allowKidsRequests = existing.allowKidsRequests,
                    audioPassthroughEnabled = existing.audioPassthroughEnabled,
                    stereoDownmixEnabled = existing.stereoDownmixEnabled,
                    assSubtitleMode = existing.assSubtitleMode,
                    defaultStartupTab = existing.defaultStartupTab,
                    pinnedLibraryIds = existing.pinnedLibraryIds,
                    hiddenHomeRowIds = existing.hiddenHomeRowIds,
                    homeRowOrder = existing.homeRowOrder,
                    metadataLanguage = existing.metadataLanguage,
                    uiLanguage = existing.uiLanguage
                )
            } else {
                profile.copy(seerrCookie = effectiveCookie)
            }
            resolvedProfile = merged
            val updated = current.filterNot { it.id == profile.id } + merged
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
        }
        cachedToken = resolvedProfile.jellyfinToken
        cachedSeerrCookie = resolvedProfile.seerrCookie
        cachedMetadataLanguage = resolvedProfile.metadataLanguage.ifBlank { "en" }
        cachedUiLanguage = resolvedProfile.uiLanguage.ifBlank { "en" }
    }

    suspend fun updateProfilePreferences(updatedProfile: UserProfile) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map {
                if (it.id == updatedProfile.id) updatedProfile else it
            }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            if (prefs[Keys.ACTIVE_PROFILE_ID] == updatedProfile.id) {
                cachedMetadataLanguage = updatedProfile.metadataLanguage.ifBlank { "en" }
                cachedUiLanguage = updatedProfile.uiLanguage.ifBlank { "en" }
            }
        }
    }

    suspend fun updateSubtitleAppearance(fontScale: Float, backgroundAlpha: Float) {
        dataStore.edit { prefs ->
            val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: return@edit
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map {
                if (it.id == activeId) {
                    it.copy(
                        subtitleFontScale = fontScale,
                        subtitleBackgroundAlpha = backgroundAlpha
                    )
                } else it
            }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun updatePlaybackPreferences(
        autoSkipIntro: Boolean? = null,
        autoSkipRecap: Boolean? = null,
        frameRateMatchMode: com.seerflix.tv.core.model.FrameRateMatchMode? = null,
        audioPassthroughEnabled: Boolean? = null,
        stereoDownmixEnabled: Boolean? = null,
        assSubtitleMode: com.seerflix.tv.core.model.AssSubtitleMode? = null
    ) {
        dataStore.edit { prefs ->
            val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: return@edit
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map {
                if (it.id == activeId) {
                    it.copy(
                        autoSkipIntro = autoSkipIntro ?: it.autoSkipIntro,
                        autoSkipRecap = autoSkipRecap ?: it.autoSkipRecap,
                        frameRateMatchMode = frameRateMatchMode ?: it.frameRateMatchMode,
                        audioPassthroughEnabled = audioPassthroughEnabled ?: it.audioPassthroughEnabled,
                        stereoDownmixEnabled = stereoDownmixEnabled ?: it.stereoDownmixEnabled,
                        assSubtitleMode = assSubtitleMode ?: it.assSubtitleMode
                    )
                } else it
            }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun updateActiveUiPreset(preset: UiPreset) {
        dataStore.edit { prefs ->
            val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: return@edit
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map {
                if (it.id == activeId) it.copy(uiPreset = preset) else it
            }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun getTrackedPendingRequestIds(): Set<Int> {
        val raw = dataStore.data.first()[Keys.TRACKED_REQUESTS_JSON] ?: return emptySet()
        return runCatching { json.decodeFromString<Set<Int>>(raw) }.getOrDefault(emptySet())
    }

    suspend fun saveTrackedPendingRequestIds(ids: Set<Int>) {
        dataStore.edit { prefs ->
            prefs[Keys.TRACKED_REQUESTS_JSON] = json.encodeToString(ids)
        }
    }

    suspend fun addReadyRequestAlerts(alerts: List<com.seerflix.tv.core.model.ReadyRequestAlert>) {
        if (alerts.isEmpty()) return
        dataStore.edit { prefs ->
            val current = prefs[Keys.READY_ALERTS_JSON]?.let {
                runCatching { json.decodeFromString<List<com.seerflix.tv.core.model.ReadyRequestAlert>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val merged = (alerts + current).distinctBy { it.requestId }.take(10)
            prefs[Keys.READY_ALERTS_JSON] = json.encodeToString(merged)
        }
    }

    suspend fun dismissReadyAlert(requestId: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.READY_ALERTS_JSON]?.let {
                runCatching { json.decodeFromString<List<com.seerflix.tv.core.model.ReadyRequestAlert>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            prefs[Keys.READY_ALERTS_JSON] = json.encodeToString(current.filterNot { it.requestId == requestId })
        }
    }

    suspend fun setActiveProfile(profileId: String?) {
        dataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
                cachedToken = null
                cachedSeerrCookie = null
                cachedMetadataLanguage = "en"
                cachedUiLanguage = "en"
            } else {
                prefs[Keys.ACTIVE_PROFILE_ID] = profileId
                val current = prefs[Keys.PROFILES_JSON]?.let {
                    runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
                } ?: emptyList()
                val found = current.firstOrNull { it.id == profileId }
                cachedToken = found?.jellyfinToken
                cachedSeerrCookie = found?.seerrCookie
                cachedMetadataLanguage = found?.metadataLanguage?.ifBlank { "en" } ?: "en"
                cachedUiLanguage = found?.uiLanguage?.ifBlank { "en" } ?: "en"
            }
        }
    }

    suspend fun updateActiveSeerrCookie(cookie: String) {
        cachedSeerrCookie = cookie
        dataStore.edit { prefs ->
            val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: return@edit
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map {
                if (it.id == activeId) it.copy(seerrCookie = cookie) else it
            }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
        }
    }

    suspend fun removeProfile(profileId: String) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == profileId }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            prefs.remove(Keys.localMyList(profileId))
            prefs.remove(Keys.reminders(profileId))
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
                cachedToken = null
                cachedSeerrCookie = null
            }
        }
    }

    suspend fun getLocalMyList(profileId: String): List<LocalMyListEntry> {
        val raw = dataStore.data.first()[Keys.localMyList(profileId)] ?: return emptyList()
        return runCatching { json.decodeFromString<List<LocalMyListEntry>>(raw) }.getOrDefault(emptyList())
    }

    suspend fun isInLocalMyList(profileId: String, tmdbId: Int, mediaType: MediaType): Boolean {
        return getLocalMyList(profileId).any { it.tmdbId == tmdbId && it.mediaType == mediaType }
    }

    suspend fun toggleLocalMyList(profileId: String, item: UnifiedMediaItem): Boolean {
        val tmdbId = item.tmdbId ?: return false
        var added = false
        dataStore.edit { prefs ->
            val key = Keys.localMyList(profileId)
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<LocalMyListEntry>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val exists = current.any { it.tmdbId == tmdbId && it.mediaType == item.mediaType }
            val updated = if (exists) {
                added = false
                current.filterNot { it.tmdbId == tmdbId && it.mediaType == item.mediaType }
            } else {
                added = true
                current + LocalMyListEntry(
                    id = item.id,
                    tmdbId = tmdbId,
                    mediaType = item.mediaType,
                    title = item.title,
                    posterUrl = item.posterUrl,
                    backdropUrl = item.backdropUrl,
                    releaseYear = item.releaseYear,
                    voteAverage = item.voteAverage,
                    overview = item.overview
                )
            }
            prefs[key] = json.encodeToString(updated)
        }
        return added
    }

    suspend fun removeFromLocalMyList(profileId: String, tmdbId: Int, mediaType: MediaType) {
        dataStore.edit { prefs ->
            val key = Keys.localMyList(profileId)
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<List<LocalMyListEntry>>(it) }.getOrDefault(emptyList())
            } ?: return@edit
            val updated = current.filterNot { it.tmdbId == tmdbId && it.mediaType == mediaType }
            prefs[key] = json.encodeToString(updated)
        }
    }

    suspend fun getReminders(profileId: String): Set<String> {
        val raw = dataStore.data.first()[Keys.reminders(profileId)] ?: return emptySet()
        return runCatching { json.decodeFromString<Set<String>>(raw) }.getOrDefault(emptySet())
    }

    suspend fun isReminderSet(profileId: String, tmdbId: Int, mediaType: MediaType): Boolean {
        return reminderKey(tmdbId, mediaType) in getReminders(profileId)
    }

    suspend fun toggleReminder(profileId: String, tmdbId: Int, mediaType: MediaType): Boolean {
        val target = reminderKey(tmdbId, mediaType)
        var enabled = false
        dataStore.edit { prefs ->
            val key = Keys.reminders(profileId)
            val current = prefs[key]?.let {
                runCatching { json.decodeFromString<Set<String>>(it) }.getOrDefault(emptySet())
            } ?: emptySet()
            val updated = if (target in current) {
                enabled = false
                current - target
            } else {
                enabled = true
                current + target
            }
            prefs[key] = json.encodeToString(updated)
        }
        return enabled
    }

    companion object {
        fun reminderKey(tmdbId: Int, mediaType: MediaType): String = "${mediaType.name}_$tmdbId"

        fun hashPin(pin: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }

        fun verifyPin(pinHash: String?, enteredPin: String): Boolean {
            if (pinHash.isNullOrBlank()) return true
            return pinHash == hashPin(enteredPin)
        }

        fun normalizeUrl(raw: String): String {
            var trimmed = raw.trim().trimEnd('/')
            if (trimmed.isNotEmpty() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "http://$trimmed"
            }
            return trimmed
        }
    }
}
