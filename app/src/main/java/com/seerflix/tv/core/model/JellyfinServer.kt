package com.seerflix.tv.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * One Jellyfin server the app is connected to. Exactly one is the [isHome] server (the one set up
 * first; Seerr, Live TV, SyncPlay and remote control stay tied to it); any others are "extra"
 * servers whose libraries are merged into the same UI.
 */
@Immutable
@Serializable
data class JellyfinServer(
    /** Stable id; [ServerScopedId.HOME] for the home server, a short random id for the others. */
    val id: String,
    val name: String = "",
    val localUrl: String = "",
    val remoteUrl: String = "",
    val trustSelfSignedCerts: Boolean = false,
    /** Cap for streams from this server in bits per second; null = no cap (direct play as-is). */
    val maxStreamingBitrateBps: Long? = null,
    val isHome: Boolean = false
) {
    /** LAN first, then remote: the order the failover interceptor tries them in. */
    val candidates: List<String>
        get() = listOf(localUrl, remoteUrl)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

    val displayName: String
        get() = name.ifBlank { if (isHome) "Home server" else "Jellyfin server" }

    /** Ties cached data (library index) to the server it was fetched from. */
    val cacheKey: String
        get() = remoteUrl.ifBlank { localUrl }.trim().trimEnd('/')

    companion object {
        /** Default cap for a newly added (usually remote) server. */
        const val DEFAULT_EXTRA_SERVER_BITRATE_BPS = 20_000_000L

        /** Choices offered in Settings; null = no limit. */
        val BITRATE_CHOICES: List<Long?> = listOf(
            4_000_000L, 8_000_000L, 12_000_000L, 20_000_000L, 40_000_000L, 80_000_000L, null
        )

        fun bitrateLabel(bps: Long?): String =
            if (bps == null) "No limit" else "${bps / 1_000_000L} Mbps"
    }
}

/**
 * What a profile needs to talk to one extra server: the user id and access token Jellyfin returned
 * at sign-in. Passwords are never stored.
 */
@Immutable
@Serializable
data class ServerCredentials(
    val userId: String,
    val accessToken: String,
    val userName: String = ""
)

/**
 * Item ids are only unique within one Jellyfin server (they are derived from the file path, so two
 * servers can even hand out the same id). Every id that leaves the network layer is therefore
 * *server scoped*: ids from the home server stay exactly as Jellyfin returned them, ids from any
 * other server are prefixed with that server's id (`<serverId>~<itemId>`). The scoped id is what
 * models, navigation arguments, caches and deep links carry; the network layer splits it again to
 * pick the server and send the raw id.
 */
object ServerScopedId {
    const val HOME = "home"
    private const val SEPARATOR = '~'

    fun scope(serverId: String, rawId: String): String =
        if (serverId == HOME || rawId.isEmpty() || rawId.indexOf(SEPARATOR) >= 0) rawId
        else "$serverId$SEPARATOR$rawId"

    fun serverOf(id: String?): String {
        if (id == null) return HOME
        val idx = id.indexOf(SEPARATOR)
        return if (idx <= 0) HOME else id.substring(0, idx)
    }

    fun rawOf(id: String): String {
        val idx = id.indexOf(SEPARATOR)
        return if (idx <= 0) id else id.substring(idx + 1)
    }

    fun isHome(id: String?): Boolean = serverOf(id) == HOME
}

/** How an extra server is doing right now; drives the quiet Home indicator and Settings. */
enum class ServerHealth {
    ONLINE,
    /** Slow or unreachable: skipped for a short while so Home stays quick. */
    UNREACHABLE,
    /** The server rejected the stored token (revoked / password changed): sign in again. */
    AUTH_REQUIRED,
    /** The active profile has no account on this server yet. */
    NOT_SIGNED_IN
}

@Immutable
data class ServerStatus(
    val server: JellyfinServer,
    val health: ServerHealth,
    val userName: String = ""
)
