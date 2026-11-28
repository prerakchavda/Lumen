package com.seerflix.tv.core.data

import android.util.Log
import com.seerflix.tv.BuildConfig
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.network.TraktApi
import com.seerflix.tv.core.network.TraktDeviceCodeDto
import com.seerflix.tv.core.network.TraktDeviceCodeRequest
import com.seerflix.tv.core.network.TraktDeviceTokenRequest
import com.seerflix.tv.core.network.TraktRefreshRequest
import com.seerflix.tv.core.network.TraktTokenDto
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Connect Trakt": TV sign-in with a short code (no typing a password with the remote), and the
 * member's own Trakt recommendations, which become a Home row. Each Jellyfin user connects their
 * own account. Unavailable (and hidden) when the build has no Trakt client id + secret.
 */
@Singleton
class TraktAccount internal constructor(
    private val api: TraktApi?,
    private val prefs: SessionPreferences,
    private val clientId: String,
    private val clientSecret: String,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Inject
    constructor(api: TraktApi, prefs: SessionPreferences) : this(
        api = api,
        prefs = prefs,
        clientId = BuildConfig.TRAKT_CLIENT_ID,
        clientSecret = BuildConfig.TRAKT_CLIENT_SECRET
    )

    val isAvailable: Boolean
        get() = api != null && clientId.isNotBlank() && clientSecret.isNotBlank()

    sealed interface SignInResult {
        data object Connected : SignInResult
        data object Expired : SignInResult
        data object Denied : SignInResult
        data class Failed(val message: String) : SignInResult
    }

    suspend fun isConnected(userId: String): Boolean = prefs.getTraktToken(userId) != null

    /** Step 1: a code to show on screen with the activation URL. */
    suspend fun startSignIn(): Result<TraktDeviceCodeDto> = runCatchingCancellable {
        check(isAvailable) { "Trakt isn't set up in this build" }
        api!!.deviceCode(TraktDeviceCodeRequest(clientId))
    }

    /** Step 2: waits (polling at Trakt's pace) until the code is approved, denied or expires. */
    suspend fun awaitSignIn(userId: String, code: TraktDeviceCodeDto): SignInResult {
        val api = api ?: return SignInResult.Failed("Trakt isn't set up in this build")
        var intervalMs = code.interval.coerceAtLeast(1) * 1000L
        val deadline = clock() + code.expiresIn * 1000L
        while (clock() < deadline) {
            delay(intervalMs)
            val response = runCatchingCancellable {
                api.deviceToken(TraktDeviceTokenRequest(code.deviceCode, clientId, clientSecret))
            }.getOrElse { return SignInResult.Failed(it.message ?: "network error") }
            when (response.code()) {
                200 -> {
                    val token = response.body() ?: return SignInResult.Failed("empty response")
                    prefs.saveTraktToken(userId, token.withCreatedAt(clock()))
                    recCache.keys.removeAll { it.startsWith("$userId|") }
                    return SignInResult.Connected
                }
                400 -> Unit // still waiting for the user
                429 -> intervalMs += 1000L
                410 -> return SignInResult.Expired
                418 -> return SignInResult.Denied
                else -> return SignInResult.Failed("HTTP ${response.code()}")
            }
        }
        return SignInResult.Expired
    }

    suspend fun disconnect(userId: String) {
        prefs.saveTraktToken(userId, null)
        recCache.keys.removeAll { it.startsWith("$userId|") }
    }

    private class CachedRecs(val atMs: Long, val ids: List<Int>)
    private val recCache = ConcurrentHashMap<String, CachedRecs>()
    private val refreshLock = Mutex()

    /**
     * The member's recommended TMDB ids for [type] (movies or shows), cached for a few hours.
     * Empty when not connected or Trakt can't be reached.
     */
    suspend fun recommendations(userId: String, type: MediaType): List<Int> {
        if (!isAvailable) return emptyList()
        val key = "$userId|${if (type == MediaType.MOVIE) "m" else "s"}"
        val now = clock()
        recCache[key]?.takeIf { now - it.atMs in 0 until CACHE_MS }?.let { return it.ids }
        val token = validToken(userId) ?: return emptyList()
        val bearer = "Bearer ${token.accessToken}"
        val ids = runCatchingCancellable {
            val titles = if (type == MediaType.MOVIE) api!!.recommendedMovies(bearer) else api!!.recommendedShows(bearer)
            titles.mapNotNull { it.ids.tmdb }.distinct()
        }.onFailure { e -> runCatching { Log.w(TAG, "Trakt recommendations failed: ${e.message}") } }
            .getOrNull() ?: return recCache[key]?.ids.orEmpty()
        recCache[key] = CachedRecs(now, ids)
        return ids
    }

    private suspend fun validToken(userId: String): TraktTokenDto? = refreshLock.withLock {
        val token = prefs.getTraktToken(userId) ?: return@withLock null
        if (!token.isExpired(clock() / 1000)) return@withLock token
        val refreshed = runCatchingCancellable {
            api!!.refreshToken(TraktRefreshRequest(token.refreshToken, clientId, clientSecret))
        }.getOrNull()?.withCreatedAt(clock())
        if (refreshed != null && refreshed.accessToken.isNotBlank()) {
            prefs.saveTraktToken(userId, refreshed)
            refreshed
        } else null
    }

    private fun TraktTokenDto.withCreatedAt(nowMs: Long) =
        if (createdAt > 0) this else copy(createdAt = nowMs / 1000)

    companion object {
        private const val TAG = "TraktAccount"
        private const val CACHE_MS = 4L * 60 * 60 * 1000
    }
}
