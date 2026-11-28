package com.seerflix.tv.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ServerConfig
import com.seerflix.tv.core.model.ServerCredentials
import com.seerflix.tv.core.model.ServerScopedId
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

// A corrupted prefs file would otherwise throw from every read (crash loop on launch).
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "seerflix_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

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
        val SEEN_REQUESTS_JSON = stringPreferencesKey("seen_requests_json")
        val SEEN_REQUESTS_OWNER = stringPreferencesKey("seen_requests_owner")
        val READY_ALERTS_JSON = stringPreferencesKey("ready_alerts_json")
        val MATCH_DEVICE_CAPS = stringPreferencesKey("match_device_capabilities")
        val SOFTWARE_AUDIO_DECODING = stringPreferencesKey("software_audio_decoding")
        val ALLOW_REMOTE_CONTROL = stringPreferencesKey("allow_remote_control")
        /** Every Jellyfin server (home first). The home entry's URLs live in the keys above. */
        val JELLYFIN_SERVERS_JSON = stringPreferencesKey("jellyfin_servers_json")
        val SCHEMA_VERSION = stringPreferencesKey("schema_version")

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

    /** The active profile's Seerr user id (null = not known yet). */
    @Volatile
    var cachedSeerrUserId: Int? = null
        private set

    /** Kids / rating-limited profile: never lets the admin API key act on its behalf. */
    @Volatile
    var cachedProfileRestricted: Boolean = false
        private set

    /** The active profile is a Jellyfin administrator (and not restricted). */
    @Volatile
    var cachedProfileIsAdmin: Boolean = false
        private set

    fun setImmediateSeerrUserId(id: Int?) {
        cachedSeerrUserId = id
    }

    private fun installSeerrIdentity(profile: UserProfile?) {
        cachedSeerrUserId = profile?.seerrUserId
        cachedProfileRestricted = profile?.isRestricted == true
        cachedProfileIsAdmin = profile?.isAdmin == true
    }

    /** Another Seerr server (or key): the stored Seerr user ids belong to the old one. */
    suspend fun clearSeerrUserIds() {
        cachedSeerrUserId = null
        dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: return@edit
            if (current.none { it.seerrUserId != null }) return@edit
            prefs[Keys.PROFILES_JSON] = json.encodeToString(current.map { it.copy(seerrUserId = null) })
        }
    }

    suspend fun updateProfileSeerrUserId(profileId: String, seerrUserId: Int) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: return@edit
            if (current.none { it.id == profileId }) return@edit
            prefs[Keys.PROFILES_JSON] = json.encodeToString(
                current.map { if (it.id == profileId) it.copy(seerrUserId = seerrUserId) else it }
            )
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) cachedSeerrUserId = seerrUserId
        }
    }

    // ---- Multiple Jellyfin servers ----

    /** Every configured server, home first. Kept in sync by [serversFlow] and the write paths. */
    @Volatile
    var cachedServers: List<JellyfinServer> = emptyList()
        private set

    /** The active profile's accounts on the extra servers, by server id. */
    @Volatile
    var cachedServerCredentials: Map<String, ServerCredentials> = emptyMap()
        private set

    /** The active profile's user id on the home server. */
    @Volatile
    var cachedHomeUserId: String = ""
        private set

    /** Bumped whenever the server list or the active profile's accounts change. */
    @Volatile
    var serversRevision: Int = 0
        private set

    /**
     * A server that is being added (URL tested, not signed in yet). It can be reached through the
     * network layer like a saved one, but nothing is persisted until sign-in succeeds.
     */
    @Volatile
    var draftServer: JellyfinServer? = null
        private set

    private val resolvedServerUrls = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun setDraftServer(server: JellyfinServer?) {
        draftServer = server
    }

    val extraServers: List<JellyfinServer>
        get() = cachedServers.filterNot { it.isHome }

    fun serverById(serverId: String): JellyfinServer? =
        draftServer?.takeIf { it.id == serverId } ?: cachedServers.firstOrNull { it.id == serverId }

    /** Base URLs to try for [serverId]: the one that last worked, then LAN, then remote. */
    fun urlCandidatesFor(serverId: String): List<String> {
        if (serverId == ServerScopedId.HOME) return jellyfinUrlCandidates
        val server = serverById(serverId) ?: return emptyList()
        val resolved = resolvedServerUrls[serverId]
        return buildList {
            if (!resolved.isNullOrBlank() && resolved in server.candidates) add(resolved)
            addAll(server.candidates)
        }.distinct()
    }

    fun onResolvedServerUrl(serverId: String, url: String) {
        if (serverId == ServerScopedId.HOME) cachedResolvedJellyfinUrl = url else resolvedServerUrls[serverId] = url
    }

    /** The base URL currently used for [serverId] (for image / stream URLs). */
    fun resolvedUrlFor(serverId: String): String {
        if (serverId == ServerScopedId.HOME) {
            return cachedResolvedJellyfinUrl.ifBlank { cachedJellyfinLocalUrl }.ifBlank { cachedJellyfinUrl }
        }
        return urlCandidatesFor(serverId).firstOrNull().orEmpty()
    }

    fun tokenFor(serverId: String): String? =
        if (serverId == ServerScopedId.HOME) cachedToken else cachedServerCredentials[serverId]?.accessToken

    fun userIdFor(serverId: String): String? =
        if (serverId == ServerScopedId.HOME) cachedHomeUserId.takeIf { it.isNotBlank() }
        else cachedServerCredentials[serverId]?.userId

    /** Hosts whose certificates are accepted as-is (the per-server "trust self-signed" toggle). */
    @Volatile
    private var selfSignedHosts: Set<String> = emptySet()

    val anySelfSignedTrusted: Boolean
        get() = selfSignedHosts.isNotEmpty() || draftServer?.trustSelfSignedCerts == true

    fun trustsSelfSignedHost(host: String): Boolean {
        val h = host.lowercase()
        if (h in selfSignedHosts) return true
        val draft = draftServer
        return draft != null && draft.trustSelfSignedCerts && draft.candidates.any { hostOf(it) == h }
    }

    private fun rebuildSelfSignedHosts() {
        val hosts = HashSet<String>()
        if (cachedTrustSelfSigned) {
            // The home toggle covers the home Jellyfin server and Seerr, as it always has.
            listOf(cachedJellyfinUrl, cachedJellyfinLocalUrl, cachedSeerrUrl, cachedSeerrLocalUrl)
                .mapNotNullTo(hosts) { hostOf(it) }
        }
        cachedServers.filter { !it.isHome && it.trustSelfSignedCerts }
            .forEach { server -> server.candidates.mapNotNullTo(hosts) { hostOf(it) } }
        selfSignedHosts = hosts
    }

    private fun decodeServers(raw: String?): List<JellyfinServer> =
        raw?.let { runCatching { json.decodeFromString<List<JellyfinServer>>(it) }.getOrNull() }.orEmpty()

    /**
     * The stored list with the home entry's URLs / trust flag overlaid from the classic keys, which
     * stay the single source of truth for the home server (and keep a downgrade working).
     */
    private fun readServers(prefs: Preferences): List<JellyfinServer> {
        val stored = decodeServers(prefs[Keys.JELLYFIN_SERVERS_JSON])
        val storedHome = stored.firstOrNull { it.isHome || it.id == ServerScopedId.HOME }
        val home = (storedHome ?: JellyfinServer(id = ServerScopedId.HOME)).copy(
            id = ServerScopedId.HOME,
            isHome = true,
            remoteUrl = prefs[Keys.JELLYFIN_URL].orEmpty(),
            localUrl = prefs[Keys.JELLYFIN_LOCAL_URL].orEmpty(),
            trustSelfSignedCerts = prefs[Keys.TRUST_SELF_SIGNED] == "true"
        )
        return listOf(home) + stored.filterNot { it.isHome || it.id == ServerScopedId.HOME }
    }

    private fun installServers(servers: List<JellyfinServer>) {
        if (servers != cachedServers) {
            cachedServers = servers
            serversRevision++
        }
        rebuildSelfSignedHosts()
    }

    private fun installActiveProfile(profile: UserProfile?) {
        installSeerrIdentity(profile)
        val creds = profile?.serverCredentials.orEmpty()
        val homeUser = profile?.jellyfinUserId.orEmpty()
        if (creds != cachedServerCredentials || homeUser != cachedHomeUserId) {
            cachedServerCredentials = creds
            cachedHomeUserId = homeUser
            serversRevision++
        }
    }

    val serversFlow: Flow<List<JellyfinServer>> = dataStore.data.map { prefs ->
        readServers(prefs).also { installServers(it) }
    }

    /**
     * One-time upgrade from the single-server layout (0.9 and earlier): the configured server
     * becomes the "home" entry of the server list. Profiles, PINs, My List and every other key are
     * left exactly as they are; a profile's home account stays in its existing fields.
     */
    private suspend fun migrateToServerList() {
        dataStore.edit { prefs ->
            if (prefs[Keys.SCHEMA_VERSION] == SCHEMA_MULTI_SERVER) return@edit
            val stored = decodeServers(prefs[Keys.JELLYFIN_SERVERS_JSON])
            if (stored.none { it.isHome || it.id == ServerScopedId.HOME }) {
                prefs[Keys.JELLYFIN_SERVERS_JSON] = json.encodeToString(readServers(prefs))
            }
            prefs[Keys.SCHEMA_VERSION] = SCHEMA_MULTI_SERVER
        }
    }

    /** Adds [server] or replaces the stored one with the same id (the home entry keeps its URLs). */
    suspend fun upsertServer(server: JellyfinServer) {
        val clean = server.copy(
            localUrl = normalizeUrl(server.localUrl),
            remoteUrl = normalizeUrl(server.remoteUrl),
            isHome = server.id == ServerScopedId.HOME
        )
        dataStore.edit { prefs ->
            val current = readServers(prefs)
            val updated = if (current.any { it.id == clean.id }) {
                current.map { if (it.id == clean.id) clean else it }
            } else current + clean
            prefs[Keys.JELLYFIN_SERVERS_JSON] = json.encodeToString(updated)
            installServers(readServers(prefs))
        }
        if (draftServer?.id == clean.id) draftServer = null
    }

    /** Removes an extra server and every profile's account on it. The home server can't be removed. */
    suspend fun removeServer(serverId: String) {
        if (serverId == ServerScopedId.HOME) return
        dataStore.edit { prefs ->
            prefs[Keys.JELLYFIN_SERVERS_JSON] = json.encodeToString(readServers(prefs).filterNot { it.id == serverId })
            val profiles = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrNull()
            }
            if (profiles != null && profiles.any { serverId in it.serverCredentials }) {
                val updated = profiles.map { it.copy(serverCredentials = it.serverCredentials - serverId) }
                prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
                installActiveProfile(updated.firstOrNull { it.id == prefs[Keys.ACTIVE_PROFILE_ID] })
            }
            installServers(readServers(prefs))
        }
        resolvedServerUrls.remove(serverId)
    }

    /** Stores (or with null, forgets) [profileId]'s account on an extra server. */
    suspend fun setServerCredentials(profileId: String, serverId: String, credentials: ServerCredentials?) {
        if (serverId == ServerScopedId.HOME) return
        dataStore.edit { prefs ->
            val profiles = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrNull()
            } ?: return@edit
            val updated = profiles.map { profile ->
                if (profile.id != profileId) profile
                else profile.copy(
                    serverCredentials = if (credentials == null) profile.serverCredentials - serverId
                    else profile.serverCredentials + (serverId to credentials)
                )
            }
            prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
            if (prefs[Keys.ACTIVE_PROFILE_ID] == profileId) {
                installActiveProfile(updated.firstOrNull { it.id == profileId })
            }
        }
    }

    suspend fun setHomeServerName(name: String) {
        if (name.isBlank()) return
        dataStore.edit { prefs ->
            val current = readServers(prefs)
            if (current.firstOrNull()?.name == name) return@edit
            prefs[Keys.JELLYFIN_SERVERS_JSON] =
                json.encodeToString(current.map { if (it.isHome) it.copy(name = name) else it })
            installServers(readServers(prefs))
        }
    }

    /** Device-wide: build the playback profile from what this box / TV reports (default on). */
    @Volatile
    var cachedMatchDeviceCapabilities: Boolean = true
        private set

    val matchDeviceCapabilitiesFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        (prefs[Keys.MATCH_DEVICE_CAPS] != "false").also { cachedMatchDeviceCapabilities = it }
    }

    /** Device-wide: decode surround / lossless audio (AC3, E-AC3, DTS, TrueHD) locally via FFmpeg to PCM. */
    @Volatile
    var cachedSoftwareAudioDecoding: Boolean = true
        private set

    val softwareAudioDecodingFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        (prefs[Keys.SOFTWARE_AUDIO_DECODING] != "false").also { cachedSoftwareAudioDecoding = it }
    }

    /** Device-wide: show up as a "Play on" target for other Jellyfin apps (default on). */
    @Volatile
    var cachedAllowRemoteControl: Boolean = true
        private set

    val allowRemoteControlFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        (prefs[Keys.ALLOW_REMOTE_CONTROL] != "false").also { cachedAllowRemoteControl = it }
    }

    suspend fun setAllowRemoteControl(enabled: Boolean) {
        cachedAllowRemoteControl = enabled
        dataStore.edit { it[Keys.ALLOW_REMOTE_CONTROL] = enabled.toString() }
    }

    suspend fun setMatchDeviceCapabilities(enabled: Boolean) {
        cachedMatchDeviceCapabilities = enabled
        dataStore.edit { it[Keys.MATCH_DEVICE_CAPS] = enabled.toString() }
    }

    suspend fun setSoftwareAudioDecoding(enabled: Boolean) {
        cachedSoftwareAudioDecoding = enabled
        dataStore.edit { it[Keys.SOFTWARE_AUDIO_DECODING] = enabled.toString() }
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
        rebuildSelfSignedHosts()
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
        installActiveProfile(found)
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
        migrateToServerList()
        serverConfigFlow.first()
        serversFlow.first()
        matchDeviceCapabilitiesFlow.first()
        softwareAudioDecodingFlow.first()
        allowRemoteControlFlow.first()
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
            installServers(readServers(prefs))
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
                val keepLocalTracks = existing.hasLocalTrackPreferenceOverride
                profile.copy(
                    seerrCookie = effectiveCookie,
                    seerrUserId = profile.seerrUserId ?: existing.seerrUserId,
                    pinHash = existing.pinHash,
                    autoplayNextEpisode = existing.autoplayNextEpisode,
                    autoplayPreviews = existing.autoplayPreviews,
                    preferredAudioLang = if (keepLocalTracks) existing.preferredAudioLang else (profile.preferredAudioLang ?: existing.preferredAudioLang),
                    preferredSubtitleLang = if (keepLocalTracks) existing.preferredSubtitleLang else (profile.preferredSubtitleLang ?: existing.preferredSubtitleLang),
                    subtitleSelectionMode = if (keepLocalTracks) existing.subtitleSelectionMode else profile.subtitleSelectionMode,
                    playDefaultAudioTrack = if (keepLocalTracks) existing.playDefaultAudioTrack else profile.playDefaultAudioTrack,
                    hasLocalTrackPreferenceOverride = existing.hasLocalTrackPreferenceOverride,
                    subtitleFontScale = existing.subtitleFontScale,
                    subtitleBackgroundAlpha = existing.subtitleBackgroundAlpha,
                    subtitleTextColor = existing.subtitleTextColor,
                    subtitleEdgeStyle = existing.subtitleEdgeStyle,
                    subtitlePosition = existing.subtitlePosition,
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
                    bingeLimitEpisodes = existing.bingeLimitEpisodes,
                    mergeNextUpIntoContinueWatching = existing.mergeNextUpIntoContinueWatching,
                    screensaverTimeoutMinutes = existing.screensaverTimeoutMinutes,
                    cinemaIntrosEnabled = existing.cinemaIntrosEnabled,
                    themeSongsEnabled = existing.themeSongsEnabled,
                    defaultStartupTab = existing.defaultStartupTab,
                    pinnedLibraryIds = existing.pinnedLibraryIds,
                    hiddenHomeRowIds = existing.hiddenHomeRowIds,
                    homeRowOrder = existing.homeRowOrder,
                    metadataLanguage = existing.metadataLanguage,
                    uiLanguage = existing.uiLanguage,
                    // Re-signing in to the home server must not drop the accounts on other servers.
                    serverCredentials = existing.serverCredentials + profile.serverCredentials
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
        installActiveProfile(resolvedProfile)
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

    /**
     * Applies the server's track defaults (Jellyfin `/Users/{id}` configuration) to [profileId] in one
     * atomic edit, re-reading the stored profile so concurrent changes (a subtitle mode picked while
     * the request was in flight, a refreshed Seerr cookie) are never overwritten. Skipped once the
     * user has chosen track preferences locally.
     */
    suspend fun applyServerTrackDefaults(
        profileId: String,
        audioLang: String?,
        subtitleLang: String?,
        subtitleMode: com.seerflix.tv.core.model.SubtitleSelectionMode,
        playDefaultAudioTrack: Boolean
    ) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrNull()
            } ?: return@edit
            var changed = false
            val updated = current.map {
                if (it.id != profileId || it.hasLocalTrackPreferenceOverride) return@map it
                val synced = it.copy(
                    preferredAudioLang = audioLang?.takeIf { l -> l.isNotBlank() } ?: it.preferredAudioLang,
                    preferredSubtitleLang = subtitleLang?.takeIf { l -> l.isNotBlank() } ?: it.preferredSubtitleLang,
                    subtitleSelectionMode = subtitleMode,
                    playDefaultAudioTrack = playDefaultAudioTrack
                )
                if (synced != it) changed = true
                synced
            }
            if (changed) prefs[Keys.PROFILES_JSON] = json.encodeToString(updated)
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

    suspend fun updateSubtitleStyle(
        textColor: com.seerflix.tv.core.model.SubtitleTextColor? = null,
        edgeStyle: com.seerflix.tv.core.model.SubtitleEdgeStyle? = null,
        position: com.seerflix.tv.core.model.SubtitlePosition? = null
    ) {
        dataStore.edit { prefs ->
            val activeId = prefs[Keys.ACTIVE_PROFILE_ID] ?: return@edit
            val current = prefs[Keys.PROFILES_JSON]?.let {
                runCatching { json.decodeFromString<List<UserProfile>>(it) }.getOrDefault(emptyList())
            } ?: emptyList()
            val updated = current.map {
                if (it.id == activeId) {
                    it.copy(
                        subtitleTextColor = textColor ?: it.subtitleTextColor,
                        subtitleEdgeStyle = edgeStyle ?: it.subtitleEdgeStyle,
                        subtitlePosition = position ?: it.subtitlePosition
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
        assSubtitleMode: com.seerflix.tv.core.model.AssSubtitleMode? = null,
        subtitleSelectionMode: com.seerflix.tv.core.model.SubtitleSelectionMode? = null,
        bingeLimitEpisodes: Int? = null,
        mergeNextUpIntoContinueWatching: Boolean? = null,
        screensaverTimeoutMinutes: Int? = null,
        cinemaIntrosEnabled: Boolean? = null,
        themeSongsEnabled: Boolean? = null
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
                        assSubtitleMode = assSubtitleMode ?: it.assSubtitleMode,
                        subtitleSelectionMode = subtitleSelectionMode ?: it.subtitleSelectionMode,
                        hasLocalTrackPreferenceOverride = if (subtitleSelectionMode != null) true else it.hasLocalTrackPreferenceOverride,
                        bingeLimitEpisodes = bingeLimitEpisodes ?: it.bingeLimitEpisodes,
                        mergeNextUpIntoContinueWatching = mergeNextUpIntoContinueWatching ?: it.mergeNextUpIntoContinueWatching,
                        screensaverTimeoutMinutes = screensaverTimeoutMinutes ?: it.screensaverTimeoutMinutes,
                        cinemaIntrosEnabled = cinemaIntrosEnabled ?: it.cinemaIntrosEnabled,
                        themeSongsEnabled = themeSongsEnabled ?: it.themeSongsEnabled
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

    /**
     * Every request id the ready check saw last time for [owner] (profile + Seerr server); null
     * before its first run for that owner. Another profile or server sees a different set of
     * requests, so comparing against a set saved for someone else would flood "ready" alerts.
     */
    suspend fun getSeenRequestIds(owner: String): Set<Int>? {
        val data = dataStore.data.first()
        if (data[Keys.SEEN_REQUESTS_OWNER] != owner) return null
        val raw = data[Keys.SEEN_REQUESTS_JSON] ?: return null
        return runCatching { json.decodeFromString<Set<Int>>(raw) }.getOrNull()
    }

    suspend fun saveSeenRequestIds(owner: String, ids: Set<Int>) {
        dataStore.edit { prefs ->
            prefs[Keys.SEEN_REQUESTS_OWNER] = owner
            prefs[Keys.SEEN_REQUESTS_JSON] = json.encodeToString(ids)
        }
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
                installActiveProfile(null)
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
                installActiveProfile(found)
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
                installActiveProfile(null)
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
        private const val SCHEMA_MULTI_SERVER = "2"

        /** Lower-cased host of a server URL, or null when it doesn't parse. */
        fun hostOf(url: String): String? {
            if (url.isBlank()) return null
            return runCatching { java.net.URI(normalizeUrl(url)).host }.getOrNull()
                ?.lowercase()?.takeIf { it.isNotBlank() }
        }

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
            when {
                trimmed.isEmpty() -> Unit
                // TV keyboards often auto-capitalise ("Http://…"); OkHttp rejects "http://Http://…".
                trimmed.startsWith("http://", ignoreCase = true) ->
                    trimmed = "http://" + trimmed.substring("http://".length)
                trimmed.startsWith("https://", ignoreCase = true) ->
                    trimmed = "https://" + trimmed.substring("https://".length)
                else -> trimmed = "http://$trimmed"
            }
            return trimmed
        }
    }
}
