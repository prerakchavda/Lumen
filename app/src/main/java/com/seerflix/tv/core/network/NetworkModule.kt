package com.seerflix.tv.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.seerflix.tv.BuildConfig
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

private class FailoverBaseUrlInterceptor(
    private val candidatesProvider: () -> List<String>,
    private val onResolvedUrl: (String) -> Unit
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val original = chain.request()
        val candidates = candidatesProvider()
        if (candidates.isEmpty()) {
            throw IOException("Server URL is not configured")
        }

        var lastError: IOException? = null
        for ((idx, rawBase) in candidates.withIndex()) {
            val targetBase = rawBase.toHttpUrlOrNull() ?: continue
            val updated = rewriteRequestUrl(original, targetBase)
            try {
                val response = chain.proceed(updated)
                onResolvedUrl(rawBase)
                return response
            } catch (io: IOException) {
                lastError = io
                // A cancelled call must not fan out to the next candidate URL.
                if (idx == candidates.lastIndex || chain.call().isCanceled()) throw io
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

private class JellyfinAuthInterceptor(
    private val prefs: SessionPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        // Never send the previously active profile's token on sign-in calls: a stale/revoked
        // token must not leak into (or interfere with) a fresh authentication for another user.
        val path = chain.request().url.encodedPath
        val isSignInCall = path.endsWith("/AuthenticateByName", ignoreCase = true) ||
            path.endsWith("/AuthenticateWithQuickConnect", ignoreCase = true)
        val token = if (isSignInCall) null else prefs.cachedToken
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

private class SeerrAuthInterceptor(
    private val prefs: SessionPreferences
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val builder = chain.request().newBuilder()
            .header("Accept", "application/json")
        val apiKey = prefs.cachedSeerrApiKey
        if (apiKey.isNotBlank()) {
            builder.header("X-Api-Key", apiKey)
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

    val delegatingTm = object : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            if (!prefs.cachedTrustSelfSigned) platformTm().checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            if (!prefs.cachedTrustSelfSigned) platformTm().checkServerTrusted(chain, authType)
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
        if (prefs.cachedTrustSelfSigned) true else defaultVerifier.verify(hostname, session)
    }
    return this
}

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
                    candidatesProvider = { prefs.jellyfinUrlCandidates },
                    onResolvedUrl = { prefs.cachedResolvedJellyfinUrl = it }
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
                    onResolvedUrl = { prefs.cachedResolvedSeerrUrl = it }
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
        @GeneralClient general: OkHttpClient,
        prefs: SessionPreferences
    ): OkHttpClient = general.newBuilder()
        .readTimeout(30, TimeUnit.SECONDS)
        .applyDynamicSelfSignedTrust(prefs)
        .build()

    @Provides
    @Singleton
    fun provideJellyfinApi(
        @JellyfinClient okHttpClient: OkHttpClient,
        json: Json
    ): JellyfinApi = Retrofit.Builder()
        .baseUrl("http://jellyfin.local/")
        .client(okHttpClient)
        .addConverterFactory(UnitConverterFactory)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(JellyfinApi::class.java)

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
}
