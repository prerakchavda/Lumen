package com.seerflix.tv.core.network

import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.ServerHealth
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.model.ServerStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.HttpException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One server as the active profile sees it: the API instance routed to that server and the
 * profile's user id there. Ids going to [api] must be raw ([raw]); ids coming back are scoped
 * ([scope] / [scopedTo]) before they reach the rest of the app.
 */
class ServerSession(
    val server: JellyfinServer,
    val api: JellyfinApi,
    val userId: String
) {
    val id: String get() = server.id
    val isHome: Boolean get() = server.isHome

    fun scope(rawId: String): String = ServerScopedId.scope(id, rawId)
    fun raw(scopedId: String): String = ServerScopedId.rawOf(scopedId)
    fun owns(scopedId: String?): Boolean = ServerScopedId.serverOf(scopedId) == id
}

/**
 * Which copies of a title are the same thing on different servers. Built from the merged library
 * index; [canonical] is the copy shown and played first (the home server's when it has one).
 */
class CrossServerLinks(
    private val primaryOf: Map<String, String> = emptyMap(),
    private val copies: Map<String, List<String>> = emptyMap()
) {
    fun canonical(id: String): String = primaryOf[id] ?: id

    /** Every copy of [id] (itself included), canonical first. */
    fun copiesOf(id: String): List<String> = copies[canonical(id)] ?: listOf(id)

    fun hasCopies(id: String): Boolean = (copies[canonical(id)]?.size ?: 1) > 1

    val isEmpty: Boolean get() = copies.isEmpty()

    companion object {
        val EMPTY = CrossServerLinks()
    }
}

/**
 * The Jellyfin servers the app talks to: a routed [JellyfinApi] per server, the active profile's
 * session on each, and how each extra server is doing ([statusFlow]).
 *
 * The home server is always the injected [homeApi]; everything that is home-only (Seerr, Live TV,
 * SyncPlay, remote control, log upload) keeps using that instance directly and never comes here.
 */
@Singleton
class JellyfinServers internal constructor(
    private val prefs: SessionPreferences,
    private val homeApi: JellyfinApi,
    private val apiFactory: (serverId: String) -> JellyfinApi,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Inject
    constructor(
        prefs: SessionPreferences,
        homeApi: JellyfinApi,
        @JellyfinClient okHttpClient: OkHttpClient,
        json: Json
    ) : this(prefs, homeApi, { serverId -> buildJellyfinApi(okHttpClient, json, serverId) })

    private val apis = ConcurrentHashMap<String, JellyfinApi>()
    private val retryAfterMs = ConcurrentHashMap<String, Long>()
    private val health = MutableStateFlow<Map<String, ServerHealth>>(emptyMap())

    /** Same-title links from the latest merged library index (set by the catalog repository). */
    @Volatile
    var links: CrossServerLinks = CrossServerLinks.EMPTY

    /** Changes whenever the server list or the active profile's accounts change. */
    val revision: Int get() = prefs.serversRevision

    fun api(serverId: String): JellyfinApi =
        if (serverId == ServerScopedId.HOME) homeApi else apis.getOrPut(serverId) { apiFactory(serverId) }

    fun server(serverId: String): JellyfinServer? = prefs.serverById(serverId)

    fun serverName(serverId: String): String = server(serverId)?.displayName.orEmpty()

    /** Base URL currently in use for [serverId] (image and stream URLs). */
    fun baseUrl(serverId: String): String = prefs.resolvedUrlFor(serverId).trim().trimEnd('/')

    fun baseUrlForItem(scopedId: String): String = baseUrl(ServerScopedId.serverOf(scopedId))

    fun token(serverId: String): String = prefs.tokenFor(serverId).orEmpty()

    val hasExtraServers: Boolean get() = prefs.extraServers.isNotEmpty()

    /** The home session for [userId] (the active profile's home user id). */
    fun home(userId: String): ServerSession = ServerSession(
        server = prefs.cachedServers.firstOrNull { it.isHome } ?: JellyfinServer(id = ServerScopedId.HOME, isHome = true),
        api = homeApi,
        userId = userId
    )

    /** Extra servers the active profile is signed in to, whatever their current health. */
    fun signedInExtras(): List<ServerSession> = prefs.extraServers.mapNotNull { server ->
        val userId = prefs.userIdFor(server.id) ?: return@mapNotNull null
        ServerSession(server, api(server.id), userId)
    }

    /** Extra servers worth calling right now: signed in, token not rejected, not backing off. */
    fun availableExtras(): List<ServerSession> = signedInExtras().filter { isAvailable(it.id) }

    /**
     * The session that owns [scopedId]. [homeUserId] is used for home items; for an extra server
     * the profile's account there is used, and null is returned when it has none.
     */
    fun sessionForItem(scopedId: String, homeUserId: String): ServerSession? =
        session(ServerScopedId.serverOf(scopedId), homeUserId)

    fun session(serverId: String, homeUserId: String): ServerSession? {
        if (serverId == ServerScopedId.HOME) return home(homeUserId)
        val server = prefs.serverById(serverId) ?: return null
        val userId = prefs.userIdFor(serverId) ?: return null
        return ServerSession(server, api(serverId), userId)
    }

    /** False while [serverId] needs a new sign-in or is being left alone after a failure. */
    fun isAvailable(serverId: String): Boolean {
        if (serverId == ServerScopedId.HOME) return true
        if (health.value[serverId] == ServerHealth.AUTH_REQUIRED) return false
        val retryAt = retryAfterMs[serverId] ?: return true
        return clock() >= retryAt
    }

    fun markOnline(serverId: String) {
        retryAfterMs.remove(serverId)
        setHealth(serverId, ServerHealth.ONLINE)
    }

    fun markUnreachable(serverId: String) {
        retryAfterMs[serverId] = clock() + UNREACHABLE_BACKOFF_MS
        setHealth(serverId, ServerHealth.UNREACHABLE)
    }

    fun markAuthRequired(serverId: String) = setHealth(serverId, ServerHealth.AUTH_REQUIRED)

    /** Forget what we knew about [serverId] (after a fresh sign-in, a retry, or its removal). */
    fun resetHealth(serverId: String) {
        retryAfterMs.remove(serverId)
        health.update { it - serverId }
    }

    private fun setHealth(serverId: String, value: ServerHealth) {
        if (serverId == ServerScopedId.HOME) return
        health.update { if (it[serverId] == value) it else it + (serverId to value) }
    }

    /**
     * Runs [block] against one extra server with a time budget. Returns null (and records why) when
     * the server is slow, offline or rejects the token; the caller carries on with what it has.
     */
    suspend fun <T : Any> callExtra(
        session: ServerSession,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend (ServerSession) -> T
    ): T? {
        return try {
            val result = withTimeoutOrNull(timeoutMs) { block(session) }
            if (result == null) markUnreachable(session.id) else markOnline(session.id)
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // The server answered. Only a rejected token changes its status; any other HTTP error
            // (a missing item, a server-side failure) is that one call's problem.
            if (e.code() == 401) markAuthRequired(session.id) else markOnline(session.id)
            null
        } catch (e: java.io.IOException) {
            markUnreachable(session.id)
            null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Runs [block] on every available extra server in parallel and returns the answers that came
     * back in time, in server order. Never throws for a server-side problem.
     */
    suspend fun <T : Any> fromExtras(
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
        block: suspend (ServerSession) -> T
    ): List<Pair<ServerSession, T>> {
        val sessions = availableExtras()
        if (sessions.isEmpty()) return emptyList()
        return coroutineScope {
            sessions.map { session -> async { session to callExtra(session, timeoutMs, block) } }
                .awaitAll()
                .mapNotNull { (session, result) -> result?.let { session to it } }
        }
    }

    /** Every extra server with its current state for the active profile (Settings, Home indicator). */
    val statusFlow: Flow<List<ServerStatus>> =
        combine(prefs.serversFlow, prefs.activeProfileFlow, health) { servers, profile, healthMap ->
            servers.filterNot { it.isHome }.map { server ->
                val creds = profile?.serverCredentials?.get(server.id)
                ServerStatus(
                    server = server,
                    health = when {
                        creds == null -> ServerHealth.NOT_SIGNED_IN
                        else -> healthMap[server.id] ?: ServerHealth.ONLINE
                    },
                    userName = creds?.userName.orEmpty()
                )
            }
        }.distinctUntilChanged()

    companion object {
        /** Budget for an extra server's part of a screen load. */
        const val DEFAULT_TIMEOUT_MS = 4_000L

        /** How long a slow / offline server is left alone before it is tried again. */
        const val UNREACHABLE_BACKOFF_MS = 45_000L
    }
}

/** Scopes every id in this item to [serverId] (a no-op for the home server). */
fun JellyfinItemDto.scopedTo(serverId: String): JellyfinItemDto {
    if (serverId == ServerScopedId.HOME) return this
    return copy(
        id = ServerScopedId.scope(serverId, id),
        seriesId = seriesId?.let { ServerScopedId.scope(serverId, it) },
        seasonId = seasonId?.let { ServerScopedId.scope(serverId, it) },
        channelId = channelId?.let { ServerScopedId.scope(serverId, it) },
        people = if (people.isEmpty()) people else people.map { p ->
            p.copy(id = p.id?.let { ServerScopedId.scope(serverId, it) })
        }
    )
}

/** The item with the raw ids its own server knows it by (inverse of [scopedTo]). */
fun JellyfinItemDto.unscoped(): JellyfinItemDto {
    if (ServerScopedId.isHome(id)) return this
    return copy(
        id = ServerScopedId.rawOf(id),
        seriesId = seriesId?.let { ServerScopedId.rawOf(it) },
        seasonId = seasonId?.let { ServerScopedId.rawOf(it) },
        channelId = channelId?.let { ServerScopedId.rawOf(it) }
    )
}

fun List<JellyfinItemDto>.scopedTo(serverId: String): List<JellyfinItemDto> =
    if (serverId == ServerScopedId.HOME) this else map { it.scopedTo(serverId) }
