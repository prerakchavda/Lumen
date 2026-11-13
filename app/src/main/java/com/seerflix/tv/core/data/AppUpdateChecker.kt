package com.seerflix.tv.core.data

import com.seerflix.tv.BuildConfig
import com.seerflix.tv.core.model.AppUpdateInfo
import com.seerflix.tv.core.network.GeneralClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class GitHubAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = ""
)

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val assets: List<GitHubAssetDto> = emptyList(),
    val prerelease: Boolean = false,
    val draft: Boolean = false
)

@Singleton
class AppUpdateChecker @Inject constructor(
    @GeneralClient private val okHttpClient: OkHttpClient,
    private val json: Json
) {
    suspend fun checkForUpdate(
        currentVersion: String = BuildConfig.VERSION_NAME
    ): AppUpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                parseRelease(body, currentVersion)
            }
        }.getOrNull()
    }

    fun parseRelease(rawJson: String, currentVersion: String): AppUpdateInfo? {
        val dto = runCatching {
            json.decodeFromString<GitHubReleaseDto>(rawJson)
        }.getOrNull() ?: return null

        if (dto.draft || dto.prerelease) return null
        val latest = dto.tagName.trim()
        if (latest.isBlank() || !isNewerVersion(currentVersion, latest)) return null

        val apkUrl = dto.assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
            ?.browserDownloadUrl
        return AppUpdateInfo(
            latestVersion = latest,
            releaseNotes = dto.body?.trim().orEmpty(),
            downloadUrl = apkUrl,
            htmlUrl = dto.htmlUrl.ifBlank { FALLBACK_RELEASES_URL }
        )
    }

    companion object {
        private const val LATEST_RELEASE_URL =
            "https://api.github.com/repos/prerakchavda/seerflix-tv/releases/latest"
        private const val FALLBACK_RELEASES_URL =
            "https://github.com/prerakchavda/seerflix-tv/releases"

        fun isNewerVersion(current: String, latest: String): Boolean {
            val curParts = parseVersionParts(current)
            val latParts = parseVersionParts(latest)
            val maxLen = maxOf(curParts.size, latParts.size)
            for (i in 0 until maxLen) {
                val c = curParts.getOrElse(i) { 0 }
                val l = latParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            return false
        }

        private fun parseVersionParts(raw: String): List<Int> {
            return raw.trim()
                .removePrefix("v")
                .removePrefix("V")
                .substringBefore("-")
                .split(".")
                .mapNotNull { it.trim().toIntOrNull() }
        }
    }
}
