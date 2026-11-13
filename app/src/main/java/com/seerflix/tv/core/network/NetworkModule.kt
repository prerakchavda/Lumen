package com.seerflix.tv.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
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
                if (idx == candidates.lastIndex) throw io
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
        val token = prefs.cachedToken
        val deviceId = prefs.cachedDeviceId
        val authHeader = buildString {
            append("""MediaBrowser Client="Seerflix", Device="Google TV", DeviceId="$deviceId", Version="1.0.0"""")
            if (!token.isNullOrBlank()) {
                append(""", Token="$token"""")
            }
        }
        val request = chain.request().newBuilder()
            .header("X-Emby-Authorization", authHeader)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}

private class SeerrSessionCookieJar(
    private val prefs: SessionPreferences
) : CookieJar {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val sid = cookies.firstOrNull { it.name == "connect.sid" } ?: return
        val encoded = "${sid.name}=${sid.value}"
        scope.launch {
            prefs.updateActiveSeerrCookie(encoded)
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

private fun OkHttpClient.Builder.applyDynamicSelfSignedTrust(prefs: SessionPreferences): OkHttpClient.Builder {
    val systemDefaultTm: javax.net.ssl.X509TrustManager? = runCatching {
        val tmf = javax.net.ssl.TrustManagerFactory.getInstance(javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm())
        tmf.init(null as java.security.KeyStore?)
        tmf.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().firstOrNull()
    }.getOrNull()

    val delegatingTm = object : javax.net.ssl.X509TrustManager {
        override fun checkClientTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            if (!prefs.cachedTrustSelfSigned) systemDefaultTm?.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<java.security.cert.X509Certificate>, authType: String) {
            if (!prefs.cachedTrustSelfSigned) systemDefaultTm?.checkServerTrusted(chain, authType)
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
    }

    @Provides
    @Singleton
    @JellyfinClient
    fun provideJellyfinOkHttp(prefs: SessionPreferences): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
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
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
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
    fun provideJellyfinApi(
        @JellyfinClient okHttpClient: OkHttpClient,
        json: Json
    ): JellyfinApi = Retrofit.Builder()
        .baseUrl("http://jellyfin.local/")
        .client(okHttpClient)
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
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SeerrApi::class.java)
}
