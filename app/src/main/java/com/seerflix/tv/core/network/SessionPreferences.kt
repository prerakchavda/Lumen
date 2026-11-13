package com.seerflix.tv.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seerflix.tv.core.model.ServerConfig
import com.seerflix.tv.core.model.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "seerflix_prefs")

@Singleton
class SessionPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private object Keys {
        val JELLYFIN_URL = stringPreferencesKey("jellyfin_url")
        val SEERR_URL = stringPreferencesKey("seerr_url")
        val DEVICE_ID = stringPreferencesKey("device_id")
        val PROFILES_JSON = stringPreferencesKey("profiles_json")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
    }

    @Volatile
    var cachedJellyfinUrl: String = ""
        private set

    @Volatile
    var cachedSeerrUrl: String = ""
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

    val serverConfigFlow: Flow<ServerConfig> = context.dataStore.data.map { prefs ->
        val deviceId = prefs[Keys.DEVICE_ID] ?: UUID.randomUUID().toString()
        val config = ServerConfig(
            jellyfinUrl = prefs[Keys.JELLYFIN_URL].orEmpty(),
            seerrUrl = prefs[Keys.SEERR_URL].orEmpty(),
            deviceId = deviceId
        )
        cachedJellyfinUrl = config.jellyfinUrl
        cachedSeerrUrl = config.seerrUrl
        cachedDeviceId = deviceId
        config
    }

    val profilesFlow: Flow<List<UserProfile>> = context.dataStore.data.map { prefs ->
        val raw = prefs[Keys.PROFILES_JSON] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<UserProfile>>(raw) }.getOrDefault(emptyList())
    }

    val activeProfileFlow: Flow<UserProfile?> = context.dataStore.data.map { prefs ->
        val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: return@map null
        val raw = prefs[Keys.PROFILES_JSON] ?: return@map null
        val list = runCatching { json.decodeFromString<List<UserProfile>>(raw) }.getOrDefault(emptyList())
        val found = list.firstOrNull { it.id == activeId }
        cachedToken = found?.jellyfinToken
        cachedSeerrCookie = found?.seerrCookie
        found
    }

    suspend fun warmup() {
        serverConfigFlow.first()
        activeProfileFlow.first()
        context.dataStore.edit { prefs ->
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = cachedDeviceId
            }
        }
    }

    suspend fun saveServerUrls(jellyfinUrl: String, seerrUrl: String) {
        val cleanJf = normalizeUrl(jellyfinUrl)
        val cleanSeerr = normalizeUrl(seerrUrl)
        cachedJellyfinUrl = cleanJf
        cachedSeerrUrl = cleanSeerr
        context.dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_URL] = cleanJf
            prefs[Keys.SEERR_URL] = cleanSeerr
            if (prefs[Keys.DEVICE_ID].isNullOrBlank()) {
                prefs[Keys.DEVICE_ID] = cachedDeviceId
            }
        }
    }

    suspend fun upsertAndActivateProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == profile.id } + profile
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            prefs[Keys.ACTIVE_PROFILE_ID] = profile.id
        }
        cachedToken = profile.jellyfinToken
        cachedSeerrCookie = profile.seerrCookie
    }

    suspend fun setActiveProfile(profileId: String?) {
        context.dataStore.edit { prefs ->
            if (profileId == null) {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
                cachedToken = null
                cachedSeerrCookie = null
            } else {
                prefs[Keys.ACTIVE_PROFILE_ID] = profileId
                val current = prefs[Keys.PROFILES_JSON]?.let {
                    runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
                } ?: emptyList()
                val found = current.firstOrNull { it.id == profileId }
                cachedToken = found?.jellyfinToken
                cachedSeerrCookie = found?.seerrCookie
            }
        }
    }

    suspend fun updateActiveSeerrCookie(cookie: String) {
        cachedSeerrCookie = cookie
        context.dataStore.edit { prefs ->
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
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.filterNot { it.id == profileId }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) {
                prefs.remove(Keys.ACTIVE_PROFILE_ID)
                cachedToken = null
                cachedSeerrCookie = null
            }
        }
    }

    companion object {
        fun normalizeUrl(raw: String): String {
            var trimmed = raw.trim().trimEnd('/')
            if (trimmed.isNotEmpty() && !trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "http://$trimmed"
            }
            return trimmed
        }
    }
}
