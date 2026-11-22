package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.ServerCredentials
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.model.ServerStatus
import com.seerflix.tv.core.network.JellyfinAuthRequest
import com.seerflix.tv.core.network.JellyfinAuthResult
import com.seerflix.tv.core.network.JellyfinQuickConnectAuthRequest
import com.seerflix.tv.core.network.JellyfinQuickConnectState
import com.seerflix.tv.core.network.JellyfinServers
import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adding, signing in to and removing the extra Jellyfin servers whose libraries are merged into
 * the app. The home server is set up by [AuthRepository] and is never touched here.
 *
 * Only what Jellyfin returns at sign-in (user id + access token) is stored, per profile. The
 * password is used for the one sign-in request and then dropped.
 */
@Singleton
class ServerAccountRepository @Inject constructor(
    private val prefs: SessionPreferences,
    private val servers: JellyfinServers,
    private val catalog: UnifiedCatalogRepository
) {
    val statusFlow: Flow<List<ServerStatus>> = servers.statusFlow

    fun server(serverId: String): JellyfinServer? = prefs.serverById(serverId)

    /**
     * Checks that a Jellyfin server answers at the given address(es) and returns it as a draft:
     * reachable through the network layer, but not saved until a sign-in succeeds. An address that
     * is already configured returns that server instead of creating a second entry.
     */
    suspend fun testServer(
        remoteUrl: String,
        localUrl: String,
        trustSelfSignedCerts: Boolean
    ): Result<JellyfinServer> = runCatchingCancellable {
        val remote = SessionPreferences.normalizeUrl(remoteUrl)
        val local = SessionPreferences.normalizeUrl(localUrl)
        require(remote.isNotBlank() || local.isNotBlank()) { "Enter the server's address" }
        val wanted = setOf(remote, local).filter { it.isNotBlank() }.toSet()

        val home = prefs.cachedServers.firstOrNull { it.isHome }
        require(home == null || home.candidates.none { it in wanted }) {
            "That is your home server; it is already connected"
        }
        val existing = prefs.extraServers.firstOrNull { server -> server.candidates.any { it in wanted } }
        val draft = (existing ?: JellyfinServer(
            id = newServerId(),
            maxStreamingBitrateBps = JellyfinServer.DEFAULT_EXTRA_SERVER_BITRATE_BPS
        )).copy(
            remoteUrl = remote.ifBlank { local },
            localUrl = if (remote.isBlank()) "" else local,
            trustSelfSignedCerts = trustSelfSignedCerts
        )
        prefs.setDraftServer(draft)
        try {
            val info = servers.api(draft.id).getPublicSystemInfo()
            // The same Jellyfin server reached by another address is still the home server.
            val homeId = withTimeoutOrNull(HOME_CHECK_TIMEOUT_MS) {
                runCatchingCancellable { servers.api(ServerScopedId.HOME).getPublicSystemInfo().id }.getOrNull()
            }
            require(info.id.isNullOrBlank() || info.id != homeId) {
                "That is your home server; it is already connected"
            }
            val named = draft.copy(name = draft.name.ifBlank { info.serverName.orEmpty() })
            prefs.setDraftServer(named)
            named
        } catch (t: Throwable) {
            prefs.setDraftServer(null)
            throw t
        }
    }

    /** Forgets a draft that was never signed in to. */
    fun cancelDraft() = prefs.setDraftServer(null)

    /** Signs the active profile in to [server] with a username and password. */
    suspend fun signInWithPassword(
        server: JellyfinServer,
        username: String,
        password: String
    ): Result<Unit> = runCatchingCancellable {
        ensureReachable(server)
        val auth = servers.api(server.id).authenticateByName(
            JellyfinAuthRequest(username = username.trim(), password = password)
        )
        saveAccount(server, auth)
    }

    suspend fun startQuickConnect(server: JellyfinServer): Result<JellyfinQuickConnectState> = runCatchingCancellable {
        ensureReachable(server)
        servers.api(server.id).initiateQuickConnect()
    }

    /** True once the code was approved on the other server and the account is saved. */
    suspend fun completeQuickConnect(server: JellyfinServer, secret: String): Result<Boolean> = runCatchingCancellable {
        ensureReachable(server)
        val api = servers.api(server.id)
        if (!api.checkQuickConnect(secret).authenticated) return@runCatchingCancellable false
        saveAccount(server, api.authenticateWithQuickConnect(JellyfinQuickConnectAuthRequest(secret = secret)))
        true
    }

    /** Pulls the new server's library into the merged index so its titles are there right away. */
    suspend fun syncLibrary() {
        val profile = prefs.activeProfileFlow.first() ?: return
        runCatchingCancellable { catalog.refreshLibraryIndex(profile.jellyfinUserId, forceRefresh = true) }
    }

    suspend fun setBitrateCap(serverId: String, maxBitrateBps: Long?) {
        val server = prefs.extraServers.firstOrNull { it.id == serverId } ?: return
        prefs.upsertServer(server.copy(maxStreamingBitrateBps = maxBitrateBps))
    }

    /**
     * Removes an extra server from this TV for every profile. The active profile's session on it
     * is ended first (best effort) so the stored token stops working; nothing on the server's
     * library is touched.
     */
    suspend fun removeServer(serverId: String) {
        if (serverId == ServerScopedId.HOME) return
        if (prefs.tokenFor(serverId) != null) {
            withTimeoutOrNull(LOGOUT_TIMEOUT_MS) {
                runCatchingCancellable { servers.api(serverId).logout() }
            }
        }
        prefs.removeServer(serverId)
        servers.resetHealth(serverId)
        syncLibrary()
    }

    /** A saved server may be used as is; anything else must be the current draft. */
    private fun ensureReachable(server: JellyfinServer) {
        if (prefs.cachedServers.none { it.id == server.id }) prefs.setDraftServer(server)
    }

    private suspend fun saveAccount(server: JellyfinServer, auth: JellyfinAuthResult) {
        val profile = prefs.activeProfileFlow.first() ?: error("No active profile")
        prefs.upsertServer(server)
        prefs.setServerCredentials(
            profileId = profile.id,
            serverId = server.id,
            credentials = ServerCredentials(
                userId = auth.user.id,
                accessToken = auth.accessToken,
                userName = auth.user.name
            )
        )
        servers.resetHealth(server.id)
    }

    private fun newServerId(): String {
        val taken = prefs.cachedServers.map { it.id }.toSet()
        while (true) {
            val bytes = ByteArray(3).also { random.nextBytes(it) }
            val id = "s" + bytes.joinToString("") { "%02x".format(it) }
            if (id !in taken) return id
        }
    }

    companion object {
        private val random = SecureRandom()
        private const val HOME_CHECK_TIMEOUT_MS = 3_000L
        private const val LOGOUT_TIMEOUT_MS = 4_000L
    }
}
