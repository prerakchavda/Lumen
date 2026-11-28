package com.seerflix.tv.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.seerflix.tv.BuildConfig
import com.seerflix.tv.core.model.ServerScopedId
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class JellyfinClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class SeerrClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeneralClient

/**
 * Plain client (no base-URL rewriting, no auth headers) that honours the user's
 * "trust self-signed certificates" toggle. Use it for absolute media URLs that may point at
 * the user's own server (Jellyfin images, streams, external subtitle files).
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class MediaClient

/** Which Jellyfin server a request is for; set by the routing interceptor for the ones after it. */
internal data class JellyfinServerTag(val serverId: String)

/** Placeholder base URL of the Retrofit instance for [serverId]; the host names the server. */
internal fun jellyfinPlaceholderBaseUrl(serverId: String): String =
    if (serverId == ServerScopedId.HOME) "http://$JELLYFIN_PLACEHOLDER_HOST/"
    else "http://$serverId.$JELLYFIN_PLACEHOLDER_HOST/"

/** Inverse of [jellyfinPlaceholderBaseUrl]: the server a placeholder host stands for. */
internal fun serverIdForPlaceholderHost(host: String): String {
    val suffix = ".$JELLYFIN_PLACEHOLDER_HOST"
    return if (host.endsWith(suffix, ignoreCase = true)) host.substring(0, host.length - suffix.length)
    else ServerScopedId.HOME
}

private const val JELLYFIN_PLACEHOLDER_HOST = "jellyfin.local"

/** Extra servers get tighter timeouts than the home server so a slow one can't hold up a screen. */
private const val EXTRA_SERVER_CONNECT_TIMEOUT_S = 5
private const val EXTRA_SERVER_READ_TIMEOUT_S = 20

/**
 * Rewrites the placeholder base URL to the real one, trying each candidate in turn (LAN, then
 * remote). [candidatesProvider] / [onResolvedUrl] receive the id of the server the request is for:
 * always [ServerScopedId.HOME] unless [resolveServerFromHost] is set (the Jellyfin client), in which
 * case it is read from the placeholder host of the per-server Retrofit instance.
 */
/**
 * Whether a failed attempt may be repeated on the next candidate URL: always for reads, and for
 * writes only when the request provably never reached a server (it failed while connecting).
 */
internal fun isSafeToRetryElsewhere(method: String, error: IOException): Boolean {
    if (method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)) return true
    return when (error) {
        is java.net.ConnectException,
        is java.net.UnknownHostException,
        is java.net.NoRouteToHostException,
        is javax.net.ssl.SSLHandshakeException -> true
        is java.net.SocketTimeoutException -> error.message.orEmpty().contains("connect", ignoreCase = true)
        else -> false
    }
}

internal class FailoverBaseUrlInterceptor(
    private val resolveServerFromHost: Boolean = false,
    private val candidatesProvider: (serverId: String) -> List<String>,
    private val onResolvedUrl: (serverId: String, url: String) -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val serverId = if (resolveServerFromHost) serverIdForPlaceholderHost(original.url.host) else ServerScopedId.HOME
        val candidates = candidatesProvider(serverId)
        if (candidates.isEmpty()) {
            throw IOException("Server URL is not configured")
        }
        val isExtraServer = serverId != ServerScopedId.HOME
        val effectiveChain = if (isExtraServer) {
            chain.withConnectTimeout(EXTRA_SERVER_CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
                .withReadTimeout(EXTRA_SERVER_READ_TIMEOUT_S, TimeUnit.SECONDS)
        } else chain

        var lastError: IOException? = null
        for ((idx, rawBase) in candidates.withIndex()) {
            val targetBase = rawBase.toHttpUrlOrNull() ?: continue
            val updated = rewriteRequestUrl(original, targetBase).newBuilder()
                .tag(JellyfinServerTag::class.java, JellyfinServerTag(serverId))
                .build()
            try {
                val response = effectiveChain.proceed(updated)
                onResolvedUrl(serverId, rawBase)
                return response
            } catch (io: IOException) {
                lastError = io
                // A cancelled call must not fan out to the next candidate URL.
                if (idx == candidates.lastIndex || chain.call().isCanceled()) throw io
                // A POST that may already have reached the server (e.g. a Seerr request that
                // timed out waiting on Sonarr) must not be sent again to the next address: the
                // user then gets "already requested" for a request that worked.
                if (!isSafeToRetryElsewhere(original.method, io)) throw io
            }
        }
        throw lastError ?: IOException("Unable to reach server URL")
    }

    private fun rewriteRequestUrl(original: okhttp3.Request, targetBase: HttpUrl): okhttp3.Request {
        val baseSegments = targetBase.pathSegments.filter { it.isNotEmpty() }
        val newUrlBuilder = original.url.newBuilder()
            .scheme(targetBase.scheme)
            .host(targetBase.host)
            .port(targetBase.port)

        if (baseSegments.isNotEmpty()) {
            val existingSegments = original.url.pathSegments.filter { it.isNotEmpty() }
            newUrlBuilder.encodedPath("/")
            (baseSegments + existingSegments).forEach { seg ->
                newUrlBuilder.addPathSegment(seg)
            }
        }

        return original.newBuilder()
            .url(newUrlBuilder.build())
            .build()
    }
}

internal class JellyfinAuthInterceptor(
    private val prefs: SessionPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        // Never send the previously active profile's token on sign-in calls: a stale/revoked
        // token must not leak into (or interfere with) a fresh authentication for another user.
        val path = chain.request().url.encodedPath
        val isSignInCall = path.endsWith("/AuthenticateByName", ignoreCase = true) ||
            path.endsWith("/AuthenticateWithQuickConnect", ignoreCase = true) ||
            // Starting / polling Quick Connect is part of signing in too: a token that the server
            // has revoked must not get in the way of signing in again.
            path.endsWith("/QuickConnect/Initiate", ignoreCase = true) ||
            path.endsWith("/QuickConnect/Connect", ignoreCase = true)
        // Each server only ever sees its own token (the routing interceptor tagged the request).
        val serverId = chain.request().tag(JellyfinServerTag::class.java)?.serverId ?: ServerScopedId.HOME
        // Public endpoints (also used to test a URL typed into Server settings, which may be a
        // typo or someone else's host) never get the signed-in profile's token.
        val isPublicProbe = path.endsWith("/System/Info/Public", ignoreCase = true) ||
            path.endsWith("/Users/Public", ignoreCase = true) ||
            path.endsWith("/System/Ping", ignoreCase = true)
        val token = if (isSignInCall || isPublicProbe) null else prefs.tokenFor(serverId)
        val authHeader = buildJellyfinAuthHeader(prefs.cachedDeviceId, token)
        val request = chain.request().newBuilder()
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}

/**
 * Standard Jellyfin client authorization header value
 * (`MediaBrowser Client="…", Device="…", DeviceId="…", Version="…", Token="…"`).
 */
internal fun buildJellyfinAuthHeader(deviceId: String, token: String?): String = buildString {
    append("""MediaBrowser Client="Lumen", Device="Google TV", DeviceId="$deviceId", Version="${BuildConfig.VERSION_NAME}"""")
    if (!token.isNullOrBlank()) {
        append(""", Token="$token"""")
    }
}

internal sealed interface SeerrKeyMode {
    /** No API key: the profile's own Seerr session cookie (if any) authenticates the call. */
    data object NONE : SeerrKeyMode
    /** The API key alone, which Seerr treats as its admin (user 1). */
    data object ADMIN : SeerrKeyMode
    /** The API key acting as this Seerr user (`X-API-User`), with that user's permissions. */
    data class AsUser(val seerrUserId: Int) : SeerrKeyMode
}

/**
 * Which identity a Seerr call goes out as. Seerr lets the API key override the session cookie, so
 * sending it alone made every profile (Kids included) the Seerr admin: auto-approved requests,
 * no quotas, and everyone's requests shown as the admin's.
 */
internal fun seerrKeyMode(
    marker: String?,
    hasApiKey: Boolean,
    seerrUserId: Int?,
    profileRestricted: Boolean
): SeerrKeyMode = when {
    !hasApiKey || marker == "1" -> SeerrKeyMode.NONE
    marker == "0" -> SeerrKeyMode.ADMIN
    seerrUserId != null -> SeerrKeyMode.AsUser(seerrUserId)
    // Not linked to a Seerr account yet: unrestricted profiles keep working through the key
    // (the behaviour before 1.0); Kids / rating-limited profiles never act as the admin.
    profileRestricted -> SeerrKeyMode.NONE
    else -> SeerrKeyMode.ADMIN
}

private class SeerrAuthInterceptor(
    private val prefs: SessionPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val marker = original.header(SEERR_SESSION_ONLY_HEADER)
        val builder = original.newBuilder()
            .removeHeader(SEERR_SESSION_ONLY_HEADER)
            .header("Accept", "application/json")
        // A pasted key with an invisible / non-ASCII character makes OkHttp throw from header(),
        // which kills the process on an async call. Skip it; Seerr then answers 401 instead.
        val apiKey = prefs.cachedSeerrApiKey.trim().takeIf { key -> key.all { it in ' '..'~' } }.orEmpty()
        when (val mode = seerrKeyMode(marker, apiKey.isNotBlank(), prefs.cachedSeerrUserId, prefs.cachedProfileRestricted)) {
            SeerrKeyMode.NONE -> Unit
            SeerrKeyMode.ADMIN -> builder.header("X-Api-Key", apiKey)
            is SeerrKeyMode.AsUser -> builder
                .header("X-Api-Key", apiKey)
                .header("X-API-User", mode.seerrUserId.toString())
        }
        return chain.proceed(builder.build())
    }
}

private class SeerrSessionCookieJar(
    private val prefs: SessionPreferences
) : CookieJar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val sid = cookies.firstOrNull { it.name == "connect.sid" } ?: return
        val encoded = "${sid.name}=${sid.value}"
        prefs.setImmediateSeerrCookie(encoded)
        // Sign-in responses belong to the profile being created; AuthRepository persists them via
        // upsertAndActivateProfile. Writing them to the *currently active* profile here would hand
        // the previous profile the new user's Seerr session.
        if (url.encodedPath.contains("/api/v1/auth/", ignoreCase = true)) return
        scope.launch {
            runCatching { prefs.updateActiveSeerrCookie(encoded) }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        // The status probe also tests URLs typed into Server settings: don't hand them a session.
        if (url.encodedPath.endsWith("/api/v1/status")) return emptyList()
        val saved = prefs.cachedSeerrCookie ?: return emptyList()
        val parts = saved.split("=", limit = 2)
        if (parts.size != 2) return emptyList()
        return listOf(
            Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .domain(url.host)
                .path("/")
                .build()
        )
    }
}

private object UnitConverterFactory : retrofit2.Converter.Factory() {
    override fun responseBodyConverter(
        type: java.lang.reflect.Type,
        annotations: Array<out Annotation>,
        retrofit: Retrofit
    ): retrofit2.Converter<okhttp3.ResponseBody, *>? {
        return if (type == Unit::class.java) {
            retrofit2.Converter<okhttp3.ResponseBody, Unit> { body ->
                body.close()
                Unit
            }
        } else null
    }
}

private fun OkHttpClient.Builder.applyDynamicSelfSignedTrust(prefs: SessionPreferences): OkHttpClient.Builder {
    val systemDefaultTm: javax.net.ssl.X509TrustManager? = runCatching {
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as java.security.KeyStore?)
        tmf.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().firstOrNull()
    }.getOrNull()

    // If the platform trust manager could not be loaded, fail closed instead of silently
    // trusting every certificate while the self-signed toggle is OFF.
    fun platformTm(): javax.net.ssl.X509TrustManager = systemDefaultTm
        ?: throw java.security.cert.CertificateException("System trust store unavailable")

    // "Trust self-signed certificates" is per server, but a trust manager is not told which host
    // it is validating. So while at least one server has the toggle on, the trust manager lets the
    // handshake through and the hostname verifier (which does know the host) decides: a host with
    // the toggle is accepted as-is, every other host gets the full platform chain check plus the
    // normal hostname check there. With no toggle on anywhere, nothing changes: the platform trust
    // manager validates every handshake as usual.
    val deferredToVerifier = java.util.concurrent.atomic.AtomicBoolean(false)

    val delegatingTm = object : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            platformTm().checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            if (prefs.anySelfSignedTrusted) {
                deferredToVerifier.set(true)
            } else {
                platformTm().checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
            systemDefaultTm?.acceptedIssuers ?: emptyArray()
    }

    val sslContext = javax.net.ssl.SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<javax.net.ssl.TrustManager>(delegatingTm), java.security.SecureRandom())
    }
    sslSocketFactory(sslContext.socketFactory, delegatingTm)
    val defaultVerifier = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
    hostnameVerifier { hostname, session ->
        when {
            prefs.trustsSelfSignedHost(hostname) -> true
            // Once any handshake has been waved through, the chain is (re)checked here for every
            // host without the toggle, so a concurrent toggle change can never skip validation.
            deferredToVerifier.get() -> {
                val chainOk = runCatching {
                    val chain = session.peerCertificates
                        .filterIsInstance<java.security.cert.X509Certificate>()
                        .toTypedArray()
                    platformTm().checkServerTrusted(chain, chain.firstOrNull()?.publicKey?.algorithm ?: "RSA")
                }.isSuccess
                chainOk && defaultVerifier.verify(hostname, session)
            }
            else -> defaultVerifier.verify(hostname, session)
        }
    }
    return this
}

/** One Retrofit [JellyfinApi] bound to [serverId] through its placeholder base URL. */
internal fun buildJellyfinApi(okHttpClient: OkHttpClient, json: Json, serverId: String): JellyfinApi =
    Retrofit.Builder()
        .baseUrl(jellyfinPlaceholderBaseUrl(serverId))
        .client(okHttpClient)
        .addConverterFactory(UnitConverterFactory)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(JellyfinApi::class.java)

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        // Request bodies must carry values even when they equal the Kotlin default
        // (e.g. PlaybackPositionTicks = 0 to clear resume, the DeviceProfile's DirectPlay /
        // Transcoding profiles), otherwise kotlinx drops them and the server sees `{}`.
        encodeDefaults = true
        // ...but omit nulls rather than sending `"serverId": null` style fields.
        explicitNulls = false
    }

    private fun loggingInterceptor(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        redactHeader("Authorization")
        redactHeader("X-Api-Key")
        redactHeader("Cookie")
        redactHeader("Set-Cookie")
    }

    @Provides
    @Singleton
    @JellyfinClient
    fun provideJellyfinOkHttp(prefs: SessionPreferences): OkHttpClient {
        val logging = loggingInterceptor()
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .applyDynamicSelfSignedTrust(prefs)
            .addInterceptor(
                FailoverBaseUrlInterceptor(
                    resolveServerFromHost = true,
                    candidatesProvider = { serverId -> prefs.urlCandidatesFor(serverId) },
                    onResolvedUrl = { serverId, url -> prefs.onResolvedServerUrl(serverId, url) }
                )
            )
            .addInterceptor(JellyfinAuthInterceptor(prefs))
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @SeerrClient
    fun provideSeerrOkHttp(prefs: SessionPreferences): OkHttpClient {
        val logging = loggingInterceptor()
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .applyDynamicSelfSignedTrust(prefs)
            .cookieJar(SeerrSessionCookieJar(prefs))
            .addInterceptor(
                FailoverBaseUrlInterceptor(
                    candidatesProvider = { prefs.seerrUrlCandidates },
                    onResolvedUrl = { _, url -> prefs.cachedResolvedSeerrUrl = url }
                )
            )
            .addInterceptor(SeerrAuthInterceptor(prefs))
            .addInterceptor(logging)
            .build()
    }

    @Provides
    @Singleton
    @GeneralClient
    fun provideGeneralOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    @MediaClient
    fun provideMediaOkHttp(
        @JellyfinClient jellyfin: OkHttpClient
    ): OkHttpClient = jellyfin.newBuilder()
        // Derived from the Jellyfin API client so streams, subtitles and artwork reuse its
        // connection pool *and* its TLS socket factory: OkHttp only reuses a pooled connection
        // when the SSL config is the same instance, so a separately-built client paid a fresh
        // TCP+TLS handshake before the first video bytes. No URL rewriting / auth headers here.
        .apply {
            interceptors().clear()
            networkInterceptors().clear()
        }
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideJellyfinApi(
        @JellyfinClient okHttpClient: OkHttpClient,
        json: Json
    ): JellyfinApi = buildJellyfinApi(okHttpClient, json, ServerScopedId.HOME)

    @Provides
    @Singleton
    fun provideSeerrApi(
        @SeerrClient okHttpClient: OkHttpClient,
        json: Json
    ): SeerrApi = Retrofit.Builder()
        .baseUrl("http://seerr.local/")
        .client(okHttpClient)
        .addConverterFactory(UnitConverterFactory)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SeerrApi::class.java)

    /**
     * Public Trakt charts. Only the app's Trakt client id is sent (no user account); when the
     * build has no id, [com.seerflix.tv.core.data.TraktCharts] never calls this.
     */
    @Provides
    @Singleton
    fun provideTraktApi(
        @GeneralClient general: OkHttpClient,
        json: Json
    ): TraktApi = Retrofit.Builder()
        .baseUrl("https://api.trakt.tv/")
        .client(
            general.newBuilder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain.request().newBuilder()
                            .header("Content-Type", "application/json")
                            .header("trakt-api-version", "2")
                            .header("trakt-api-key", BuildConfig.TRAKT_CLIENT_ID)
                            .header("User-Agent", "Lumen/${BuildConfig.VERSION_NAME}")
                            .build()
                    )
                }
                .build()
        )
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(TraktApi::class.java)
}
