package com.seerflix.tv.core.network

import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.ServerCredentials
import com.seerflix.tv.core.model.ServerHealth
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Every Jellyfin call must reach the server it is meant for, with that server's own token, and
 * each server keeps its own LAN -> remote failover. Uses the real interceptors and Retrofit
 * instances over a transport that never touches the network.
 */
class ServerRoutingTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = NetworkModule.provideJson()
    private val friendId = "s1a2b3"

    private lateinit var prefs: SessionPreferences
    private lateinit var client: OkHttpClient

    /** What actually went "on the wire", in order. */
    private val sent = CopyOnWriteArrayList<Request>()
    private val unreachableHosts = CopyOnWriteArrayList<String>()
    private val unauthorizedHosts = CopyOnWriteArrayList<String>()
    /** Hosts that accept the request but never answer (read timeout after it was sent). */
    private val readTimeoutHosts = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() = runBlocking {
        prefs = SessionPreferences(File(tempFolder.root, "routing.preferences_pb"), json)
        prefs.saveServerUrls(
            jellyfinUrl = "https://jf.example.com",
            seerrUrl = "",
            jellyfinLocalUrl = "http://192.168.1.50:8096"
        )
        prefs.upsertServer(
            JellyfinServer(
                id = friendId,
                name = "Friend's Jellyfin",
                localUrl = "http://10.0.0.5:8096",
                // A server published under a sub-path keeps it.
                remoteUrl = "https://friend.example.net/jellyfin"
            )
        )
        prefs.upsertAndActivateProfile(
            UserProfile(
                id = "home-user",
                name = "Prerak",
                jellyfinUserId = "home-user",
                jellyfinToken = "home-token",
                serverCredentials = mapOf(friendId to ServerCredentials("friend-user", "friend-token", "prerak"))
            )
        )
        client = OkHttpClient.Builder()
            .addInterceptor(
                FailoverBaseUrlInterceptor(
                    resolveServerFromHost = true,
                    candidatesProvider = { serverId -> prefs.urlCandidatesFor(serverId) },
                    onResolvedUrl = { serverId, url -> prefs.onResolvedServerUrl(serverId, url) }
                )
            )
            .addInterceptor(JellyfinAuthInterceptor(prefs))
            .addInterceptor { chain ->
                val request = chain.request()
                sent += request
                if (request.url.host in unreachableHosts) throw java.net.ConnectException("unreachable: ${request.url.host}")
                if (request.url.host in readTimeoutHosts) throw java.net.SocketTimeoutException("timeout")
                val unauthorized = request.url.host in unauthorizedHosts
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (unauthorized) 401 else 200)
                    .message(if (unauthorized) "Unauthorized" else "OK")
                    .body("""{"ServerName":"${request.url.host}","Id":"id-${request.url.host}"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
    }

    private fun api(serverId: String) = buildJellyfinApi(client, json, serverId)

    private fun Request.token(): String? =
        Regex("Token=\"([^\"]+)\"").find(header("Authorization").orEmpty())?.groupValues?.get(1)

    @Test
    fun `placeholder hosts map to server ids and back`() {
        assertEquals(ServerScopedId.HOME, serverIdForPlaceholderHost("jellyfin.local"))
        assertEquals(friendId, serverIdForPlaceholderHost("$friendId.jellyfin.local"))
        assertEquals(
            friendId,
            serverIdForPlaceholderHost(java.net.URI(jellyfinPlaceholderBaseUrl(friendId)).host)
        )
        assertEquals(
            ServerScopedId.HOME,
            serverIdForPlaceholderHost(java.net.URI(jellyfinPlaceholderBaseUrl(ServerScopedId.HOME)).host)
        )
    }

    @Test
    fun `home calls go to the home server with the home token`() = runBlocking {
        val info = api(ServerScopedId.HOME).getPublicSystemInfo()

        assertEquals("192.168.1.50", info.serverName)
        val request = sent.single()
        assertEquals("http://192.168.1.50:8096/System/Info/Public", request.url.toString())
        assertEquals("home-token", request.token())
    }

    @Test
    fun `calls for another server go to that server with its own token`() = runBlocking {
        api(friendId).getResumeItems(userId = "friend-user")

        val request = sent.single()
        assertEquals("10.0.0.5", request.url.host)
        assertEquals("/Users/friend-user/Items/Resume", request.url.encodedPath)
        assertEquals("friend-token", request.token())
        // The home server's token never leaves for another server.
        assertFalse(sent.any { it.token() == "home-token" })
    }

    @Test
    fun `a POST that may have reached the server is not resent to the next address`() {
        readTimeoutHosts += "10.0.0.5"
        val post = Request.Builder()
            .url(jellyfinPlaceholderBaseUrl(friendId).trimEnd('/') + "/Sessions/Playing")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(post).execute().close()
            fail("expected the read timeout to surface")
        } catch (_: IOException) {
        }
        // Sent once: resending could duplicate a request / timer / playlist on the server.
        assertEquals(listOf("10.0.0.5"), sent.map { it.url.host })

        // A GET is safe to retry on the remote address.
        sent.clear()
        runBlocking { api(friendId).getPublicSystemInfo() }
        assertEquals(listOf("10.0.0.5", "friend.example.net"), sent.map { it.url.host })
    }

    @Test
    fun `each server fails over from LAN to remote on its own`() = runBlocking {
        unreachableHosts += "10.0.0.5"

        api(friendId).getPublicSystemInfo()
        api(ServerScopedId.HOME).getPublicSystemInfo()

        assertEquals(
            listOf(
                "http://10.0.0.5:8096/System/Info/Public",
                // Remote address, sub-path kept.
                "https://friend.example.net/jellyfin/System/Info/Public",
                // The home server is unaffected and still on its LAN address.
                "http://192.168.1.50:8096/System/Info/Public"
            ),
            sent.map { it.url.toString() }
        )
        assertEquals(listOf("friend-token", "friend-token", "home-token"), sent.map { it.token() })

        // The address that worked is remembered per server (used for image / stream URLs too).
        assertEquals("https://friend.example.net/jellyfin", prefs.resolvedUrlFor(friendId))
        assertEquals("http://192.168.1.50:8096", prefs.resolvedUrlFor(ServerScopedId.HOME))
        sent.clear()
        api(friendId).getPublicSystemInfo()
        assertEquals("friend.example.net", sent.first().url.host)
    }

    @Test
    fun `an offline extra server fails on its own and the home server keeps answering`() = runBlocking {
        unreachableHosts += listOf("10.0.0.5", "friend.example.net")

        try {
            api(friendId).getPublicSystemInfo()
            fail("expected the friend's server to be unreachable")
        } catch (expected: IOException) {
            // both of its addresses were tried
            assertEquals(listOf("10.0.0.5", "friend.example.net"), sent.map { it.url.host })
        }
        assertEquals("192.168.1.50", api(ServerScopedId.HOME).getPublicSystemInfo().serverName)
    }

    @Test
    fun `sign-in calls carry no token and a server that is not configured is rejected`() = runBlocking {
        runCatching {
            api(friendId).authenticateByName(JellyfinAuthRequest(username = "prerak", password = "secret"))
        }
        assertNull(sent.single().token())
        assertTrue(sent.single().header("Authorization").orEmpty().contains("DeviceId="))

        sent.clear()
        try {
            api("sunknown").getPublicSystemInfo()
            fail("expected an unknown server to be rejected")
        } catch (expected: IOException) {
            assertTrue(sent.isEmpty())
        }
    }

    @Test
    fun `a server being added is reachable as a draft before anything is saved`() = runBlocking {
        prefs.setDraftServer(JellyfinServer(id = "sdraft", remoteUrl = "https://new.example.org"))

        api("sdraft").getPublicSystemInfo()

        assertEquals("https://new.example.org/System/Info/Public", sent.single().url.toString())
        assertNull("no account yet, so no token", sent.single().token())
        assertEquals(listOf(ServerScopedId.HOME, friendId), prefs.serversFlow.first().map { it.id })
    }

    @Test
    fun `the registry hands out one session per server and tracks slow or signed-out servers`() = runBlocking {
        var now = 1_000_000L
        val homeApi = api(ServerScopedId.HOME)
        val servers = JellyfinServers(prefs, homeApi, { serverId -> api(serverId) }, clock = { now })

        assertSame(homeApi, servers.api(ServerScopedId.HOME))
        assertSame(homeApi, servers.home("home-user").api)
        val friend = servers.availableExtras().single()
        assertEquals(friendId, friend.id)
        assertEquals("friend-user", friend.userId)
        assertEquals("s1a2b3~item1", friend.scope("item1"))
        assertEquals("item1", friend.raw("s1a2b3~item1"))
        assertEquals(friendId, servers.sessionForItem("s1a2b3~item1", "home-user")?.id)
        assertEquals(ServerScopedId.HOME, servers.sessionForItem("item1", "home-user")?.id)

        // Answers: healthy.
        assertEquals("10.0.0.5", servers.callExtra(friend) { it.api.getPublicSystemInfo() }?.serverName)
        assertEquals(ServerHealth.ONLINE, servers.statusFlow.first().single().health)

        // Offline: the call yields nothing, the server is left alone for a while, then retried.
        unreachableHosts += listOf("10.0.0.5", "friend.example.net")
        assertNull(servers.callExtra(friend) { it.api.getPublicSystemInfo() })
        assertEquals(ServerHealth.UNREACHABLE, servers.statusFlow.first().single().health)
        assertTrue(servers.availableExtras().isEmpty())
        assertTrue(servers.fromExtras { it.api.getPublicSystemInfo() }.isEmpty())
        now += JellyfinServers.UNREACHABLE_BACKOFF_MS + 1
        assertEquals(1, servers.availableExtras().size)

        // Back, but the token was revoked: sign-in needed, and it is not called again.
        unreachableHosts.clear()
        unauthorizedHosts += "10.0.0.5"
        assertNull(servers.callExtra(friend) { it.api.getResumeItems(userId = it.userId) })
        assertEquals(ServerHealth.AUTH_REQUIRED, servers.statusFlow.first().single().health)
        assertTrue(servers.availableExtras().isEmpty())

        // After signing in again it is usable straight away.
        unauthorizedHosts.clear()
        servers.resetHealth(friendId)
        assertEquals(1, servers.fromExtras { it.api.getPublicSystemInfo() }.size)

        // A profile without an account on that server simply doesn't use it.
        prefs.upsertAndActivateProfile(UserProfile("kid", "Kid", "kid", "kid-token"))
        assertTrue(servers.signedInExtras().isEmpty())
        assertEquals(ServerHealth.NOT_SIGNED_IN, servers.statusFlow.first().single().health)
    }

    @Test
    fun `a slow extra server is cut off at its time budget`() = runBlocking {
        val servers = JellyfinServers(prefs, api(ServerScopedId.HOME), { serverId -> api(serverId) })
        val friend = servers.availableExtras().single()

        val started = System.currentTimeMillis()
        val result = servers.callExtra(friend, timeoutMs = 150L) {
            kotlinx.coroutines.delay(10_000L)
            "never"
        }

        assertNull(result)
        assertTrue("returned at the budget, not after the slow call", System.currentTimeMillis() - started < 5_000L)
        assertEquals(ServerHealth.UNREACHABLE, servers.statusFlow.first().single().health)
    }
}
