package com.seerflix.tv.core.network

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ServerCredentials
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The 0.9 -> multi-server DataStore migration and the per-server account storage. */
class MultiServerPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false
    }

    /** Profiles exactly as 0.9 stored them: no per-server accounts, one with a PIN. */
    private val legacyProfilesJson = """
        [
          {"id":"jf-user-1","name":"Prerak","jellyfinUserId":"jf-user-1","jellyfinToken":"home-token-1",
           "avatarColorHex":15010068,"isAdmin":true,"pinHash":"${SessionPreferences.hashPin("4321")}",
           "uiPreset":"APPLE_TV","pinnedLibraryIds":["lib-a"],"hiddenHomeRowIds":["trending_now"],
           "seerrCookie":"connect.sid=abc"},
          {"id":"jf-user-2","name":"Kiddo","jellyfinUserId":"jf-user-2","jellyfinToken":"home-token-2",
           "kidsMode":true,"maxContentRating":"PG"}
        ]
    """.trimIndent()

    /** Writes a prefs file the way 0.9 left it (single server, no server list, no schema key). */
    private fun writeLegacyFile(name: String): File = runBlocking {
        val file = File(tempFolder.root, name)
        val job = Job()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
        store.edit { prefs ->
            prefs[stringPreferencesKey("jellyfin_url")] = "https://jf.example.com"
            prefs[stringPreferencesKey("jellyfin_local_url")] = "http://192.168.1.50:8096"
            prefs[stringPreferencesKey("seerr_url")] = "https://seerr.example.com"
            prefs[stringPreferencesKey("seerr_api_key")] = "seerr-key"
            prefs[stringPreferencesKey("trust_self_signed")] = "true"
            prefs[stringPreferencesKey("device_id")] = "device-123"
            prefs[stringPreferencesKey("profiles_json")] = legacyProfilesJson
            prefs[stringPreferencesKey("active_profile_id")] = "jf-user-1"
            prefs[stringPreferencesKey("local_my_list_jf-user-1")] =
                """[{"id":"tmdb_MOVIE_603","tmdbId":603,"mediaType":"MOVIE","title":"The Matrix"}]"""
            prefs[stringPreferencesKey("reminders_jf-user-1")] = """["SERIES_95396"]"""
        }
        // Only one DataStore may have the file open.
        job.cancelAndJoin()
        file
    }

    @Test
    fun `first launch after 0_9 turns the configured server into the home server and keeps everything else`() = runBlocking {
        val prefs = SessionPreferences(writeLegacyFile("legacy.preferences_pb"), json)

        prefs.warmup()

        // The existing server is the one and only (home) server.
        val servers = prefs.serversFlow.first()
        assertEquals(1, servers.size)
        val home = servers.single()
        assertEquals(ServerScopedId.HOME, home.id)
        assertTrue(home.isHome)
        assertEquals("https://jf.example.com", home.remoteUrl)
        assertEquals("http://192.168.1.50:8096", home.localUrl)
        assertTrue(home.trustSelfSignedCerts)
        assertNull("the home server keeps its current behaviour: no bitrate cap", home.maxStreamingBitrateBps)
        assertEquals(listOf("http://192.168.1.50:8096", "https://jf.example.com"), home.candidates)

        // Server config, Seerr and device id read exactly as before.
        val config = prefs.serverConfigFlow.first()
        assertEquals("https://jf.example.com", config.jellyfinUrl)
        assertEquals("http://192.168.1.50:8096", config.jellyfinLocalUrl)
        assertEquals("https://seerr.example.com", config.seerrUrl)
        assertEquals("seerr-key", config.seerrApiKey)
        assertEquals("device-123", config.deviceId)

        // Profiles survive untouched: tokens, PIN, look, home customisation, kids settings.
        val profiles = prefs.profilesFlow.first().associateBy { it.id }
        assertEquals(setOf("jf-user-1", "jf-user-2"), profiles.keys)
        val owner = profiles.getValue("jf-user-1")
        assertEquals("home-token-1", owner.jellyfinToken)
        assertTrue(SessionPreferences.verifyPin(owner.pinHash, "4321"))
        assertEquals("APPLE_TV", owner.uiPreset.name)
        assertEquals(listOf("lib-a"), owner.pinnedLibraryIds)
        assertEquals(listOf("trending_now"), owner.hiddenHomeRowIds)
        assertEquals("connect.sid=abc", owner.seerrCookie)
        assertTrue(owner.serverCredentials.isEmpty())
        val kid = profiles.getValue("jf-user-2")
        assertTrue(kid.kidsMode)
        assertEquals("PG", kid.maxContentRating.name)

        // The active profile and its home account are what the network layer uses.
        assertEquals("jf-user-1", prefs.activeProfileFlow.first()?.id)
        assertEquals("home-token-1", prefs.tokenFor(ServerScopedId.HOME))
        assertEquals("jf-user-1", prefs.userIdFor(ServerScopedId.HOME))
        assertEquals("home-token-1", owner.credentialsFor(ServerScopedId.HOME)?.accessToken)

        // My List and reminders are still there.
        assertEquals(listOf(603), prefs.getLocalMyList("jf-user-1").map { it.tmdbId })
        assertTrue(prefs.isReminderSet("jf-user-1", 95396, MediaType.SERIES))
    }

    @Test
    fun `migration runs once and never drops servers added afterwards`() = runBlocking {
        val first = SessionPreferences(writeLegacyFile("legacy_twice.preferences_pb"), json)
        first.warmup()
        first.upsertServer(friendServer)
        first.setServerCredentials("jf-user-1", friendServer.id, ServerCredentials("friend-user", "friend-token", "prerak"))
        // The next launch warms up again: nothing is migrated a second time.
        first.warmup()

        val servers = first.serversFlow.first()
        assertEquals(listOf(ServerScopedId.HOME, "s1a2b3"), servers.map { it.id })
        assertEquals("https://jf.example.com", servers.first().remoteUrl)
        assertEquals("friend-token", first.tokenFor("s1a2b3"))
    }

    @Test
    fun `each profile has its own account per server and only tokens are stored`() = runBlocking {
        val file = writeLegacyFile("accounts.preferences_pb")
        val prefs = SessionPreferences(file, json)
        prefs.warmup()

        prefs.upsertServer(friendServer)
        prefs.setServerCredentials("jf-user-1", friendServer.id, ServerCredentials("friend-user", "friend-token", "prerak"))

        // The active profile (jf-user-1) reaches the friend's server with that server's account...
        assertEquals("friend-token", prefs.tokenFor(friendServer.id))
        assertEquals("friend-user", prefs.userIdFor(friendServer.id))
        assertEquals("home-token-1", prefs.tokenFor(ServerScopedId.HOME))
        assertEquals(listOf("https://friend.example.net"), prefs.urlCandidatesFor(friendServer.id))
        assertEquals(20_000_000L, prefs.serverById(friendServer.id)?.maxStreamingBitrateBps)

        // ...the other profile has no account there.
        prefs.setActiveProfile("jf-user-2")
        assertNull(prefs.tokenFor(friendServer.id))
        assertNull(prefs.userIdFor(friendServer.id))
        assertEquals("home-token-2", prefs.tokenFor(ServerScopedId.HOME))

        // Signing in to the home server again keeps the friend's account (and the PIN).
        prefs.setActiveProfile("jf-user-1")
        prefs.upsertAndActivateProfile(UserProfile("jf-user-1", "Prerak", "jf-user-1", "home-token-NEW"))
        val owner = prefs.activeProfileFlow.first()!!
        assertEquals("home-token-NEW", owner.jellyfinToken)
        assertEquals("friend-token", owner.serverCredentials[friendServer.id]?.accessToken)
        assertTrue(SessionPreferences.verifyPin(owner.pinHash, "4321"))

        // Nothing password-like is ever written: the stored profile only carries ids and tokens.
        val stored = json.encodeToString(kotlinx.serialization.builtins.ListSerializer(UserProfile.serializer()), prefs.profilesFlow.first())
        assertFalse(stored.contains("password", ignoreCase = true))
        assertFalse(stored.contains("\"Pw\""))
    }

    @Test
    fun `removing a server forgets it and every profile's account on it`() = runBlocking {
        val prefs = SessionPreferences(writeLegacyFile("remove.preferences_pb"), json)
        prefs.warmup()
        prefs.upsertServer(friendServer)
        prefs.setServerCredentials("jf-user-1", friendServer.id, ServerCredentials("friend-user", "friend-token"))
        prefs.setServerCredentials("jf-user-2", friendServer.id, ServerCredentials("friend-kid", "friend-kid-token"))
        val revisionBefore = prefs.serversRevision

        prefs.removeServer(friendServer.id)

        assertEquals(listOf(ServerScopedId.HOME), prefs.serversFlow.first().map { it.id })
        assertTrue(prefs.profilesFlow.first().all { it.serverCredentials.isEmpty() })
        assertNull(prefs.tokenFor(friendServer.id))
        assertTrue(prefs.urlCandidatesFor(friendServer.id).isEmpty())
        assertTrue("the change is visible to the network layer", prefs.serversRevision > revisionBefore)

        // The home server can't be removed, and its profiles are intact.
        prefs.removeServer(ServerScopedId.HOME)
        assertEquals(1, prefs.serversFlow.first().size)
        assertEquals("home-token-1", prefs.tokenFor(ServerScopedId.HOME))
    }

    @Test
    fun `trust for self-signed certificates is per server host`() = runBlocking {
        val prefs = SessionPreferences(File(tempFolder.root, "trust.preferences_pb"), json)
        prefs.saveServerUrls(jellyfinUrl = "https://jf.example.com", seerrUrl = "", trustSelfSignedCerts = false)
        assertFalse(prefs.anySelfSignedTrusted)

        prefs.upsertServer(friendServer.copy(trustSelfSignedCerts = true))

        assertTrue(prefs.anySelfSignedTrusted)
        assertTrue(prefs.trustsSelfSignedHost("friend.example.net"))
        assertTrue(prefs.trustsSelfSignedHost("FRIEND.example.net"))
        assertFalse("the home server keeps normal certificate checks", prefs.trustsSelfSignedHost("jf.example.com"))
        assertFalse(prefs.trustsSelfSignedHost("image.tmdb.org"))

        // A draft (address being tested, not saved yet) is honoured the same way.
        prefs.setDraftServer(JellyfinServer(id = "sdraft", remoteUrl = "https://10.0.0.9:8920", trustSelfSignedCerts = true))
        assertTrue(prefs.trustsSelfSignedHost("10.0.0.9"))
        prefs.setDraftServer(null)
        assertFalse(prefs.trustsSelfSignedHost("10.0.0.9"))
    }

    @Test
    fun `My List keeps working for an item that only exists on another server`() = runBlocking {
        val prefs = SessionPreferences(File(tempFolder.root, "mylist.preferences_pb"), json)
        val item = UnifiedMediaItem(
            id = "tmdb_MOVIE_27205",
            tmdbId = 27205,
            jellyfinId = ServerScopedId.scope("s1a2b3", "abc123"),
            mediaType = MediaType.MOVIE,
            title = "Inception"
        )
        assertTrue(prefs.toggleLocalMyList("jf-user-1", item))
        assertNotNull(prefs.getLocalMyList("jf-user-1").singleOrNull { it.tmdbId == 27205 })
    }

    private val friendServer = JellyfinServer(
        id = "s1a2b3",
        name = "Friend's Jellyfin",
        remoteUrl = "https://friend.example.net/",
        maxStreamingBitrateBps = JellyfinServer.DEFAULT_EXTRA_SERVER_BITRATE_BPS
    )
}
