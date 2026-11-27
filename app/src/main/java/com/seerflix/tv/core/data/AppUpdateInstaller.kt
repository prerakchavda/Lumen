package com.seerflix.tv.core.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Immutable
import androidx.core.content.FileProvider
import com.seerflix.tv.core.model.AppUpdateInfo
import com.seerflix.tv.core.network.GeneralClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Immutable
sealed interface AppUpdateInstallState {
    data object Idle : AppUpdateInstallState

    data class Downloading(
        val bytesRead: Long,
        val totalBytes: Long
    ) : AppUpdateInstallState {
        val progress: Float?
            get() = if (totalBytes > 0L) (bytesRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
    }

    data class NeedsInstallPermission(val apkPath: String) : AppUpdateInstallState

    data class ReadyToInstall(val apkPath: String) : AppUpdateInstallState

    data class Failed(val message: String) : AppUpdateInstallState
}

/**
 * Downloads a GitHub release APK into app-private cache (`cacheDir/updates/`) and hands it to the
 * Android TV package installer via [FileProvider]. Because updates install in-place, the new APK
 * must share the installed app's signing certificate.
 */
@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    @GeneralClient private val okHttpClient: OkHttpClient
) {
    private val updatesDir: File
        get() = File(context.cacheDir, UPDATES_SUBDIR)

    private val downloadClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun downloadApk(
        updateInfo: AppUpdateInfo,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
    ): File = withContext(Dispatchers.IO) {
        val url = updateInfo.downloadUrl?.trim().orEmpty()
        if (url.isBlank()) {
            throw IOException("Release does not include an APK asset.")
        }
        downloadApkToFile(
            client = downloadClient,
            rawUrl = url,
            targetDir = updatesDir,
            version = updateInfo.latestVersion,
            onProgress = onProgress
        )
    }

    fun canRequestPackageInstalls(): Boolean {
        return runCatching { context.packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    }

    /**
     * Opens Android TV's "Install unknown apps" toggle for Lumen. Returns false when the device's
     * Settings app doesn't expose that activity (some TV boxes prompt inline from the installer
     * instead).
     */
    fun openUnknownAppSourcesSettings(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** Launches the system package installer for [apkPath] via [FileProvider]. */
    fun launchInstaller(apkPath: String): Boolean {
        val apkFile = File(apkPath)
        if (!hasZipApkMagic(apkFile)) return false
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    /** Removes cached update APKs once the app is up to date (saves storage on 8 GB TV sticks). */
    fun cleanupCachedApks() {
        val dir = updatesDir
        if (!dir.isDirectory) return
        dir.listFiles()?.forEach { file ->
            if (file.isFile) runCatching { file.delete() }
        }
    }

    companion object {
        private const val UPDATES_SUBDIR = "updates"
        private const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        private const val PROGRESS_STEP_BYTES = 65_536L

        fun isTrustedReleaseUrl(rawUrl: String): Boolean {
            val parsed = rawUrl.trim().toHttpUrlOrNull() ?: return false
            if (!parsed.isHttps) return false
            val host = parsed.host.lowercase()
            return host == "github.com" ||
                host == "githubusercontent.com" ||
                host.endsWith(".githubusercontent.com")
        }

        fun apkFileNameForVersion(version: String): String {
            val clean = version.trim()
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
                .trim('_', '.')
            val safe = clean.ifBlank { "latest" }.take(40)
            return "lumen-update-$safe.apk"
        }

        /** APKs are ZIP archives; verifying the local file header catches HTML error pages. */
        fun hasZipApkMagic(file: File): Boolean {
            if (!file.isFile || file.length() < 4L) return false
            val header = ByteArray(4)
            val read = runCatching {
                file.inputStream().use { it.read(header) }
            }.getOrDefault(0)
            return read == 4 &&
                header[0] == 0x50.toByte() &&
                header[1] == 0x4B.toByte() &&
                header[2] == 0x03.toByte() &&
                header[3] == 0x04.toByte()
        }

        internal suspend fun downloadApkToFile(
            client: OkHttpClient,
            rawUrl: String,
            targetDir: File,
            version: String,
            onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> }
        ): File {
            if (!isTrustedReleaseUrl(rawUrl)) {
                throw IOException("Untrusted update download URL.")
            }
            if (!targetDir.exists() && !targetDir.mkdirs()) {
                throw IOException("Could not create update cache directory.")
            }

            val targetFile = File(targetDir, apkFileNameForVersion(version))
            // Reuse a complete, verified APK if the user already downloaded this exact version.
            if (hasZipApkMagic(targetFile)) {
                val len = targetFile.length()
                onProgress(len, len)
                return targetFile
            }

            // Remove stale files from earlier versions or interrupted downloads before writing.
            targetDir.listFiles()?.forEach { existing ->
                if (existing.isFile) runCatching { existing.delete() }
            }

            val partFile = File(targetDir, "${targetFile.name}.part")
            val request = Request.Builder()
                .url(rawUrl)
                .header("Accept", "application/octet-stream")
                .build()
            val call = client.newCall(request)
            // Fire as soon as the job starts cancelling (not when it completes, which a coroutine
            // blocked inside execute()/read() never would), so Cancel aborts a stalled download.
            @OptIn(kotlinx.coroutines.InternalCoroutinesApi::class)
            val cancelHandle = coroutineContext[Job]?.invokeOnCompletion(onCancelling = true, invokeImmediately = true) { cause ->
                if (cause != null) call.cancel()
            }

            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download failed (HTTP ${response.code}).")
                    }
                    val finalUrl = response.request.url.toString()
                    if (!isTrustedReleaseUrl(finalUrl)) {
                        throw IOException("Update redirected to an untrusted host.")
                    }
                    val body = response.body ?: throw IOException("Empty download response.")
                    val totalBytes = body.contentLength()
                    onProgress(0L, totalBytes)

                    val buffer = ByteArray(16 * 1024)
                    var bytesRead = 0L
                    var lastReported = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(partFile).use { output ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val count = input.read(buffer)
                                if (count == -1) break
                                output.write(buffer, 0, count)
                                bytesRead += count
                                if (bytesRead - lastReported >= PROGRESS_STEP_BYTES) {
                                    lastReported = bytesRead
                                    onProgress(bytesRead, totalBytes)
                                }
                            }
                            output.fd.sync()
                        }
                    }

                    if (totalBytes > 0L && bytesRead < totalBytes) {
                        throw IOException("Download was interrupted before completing.")
                    }
                    if (!hasZipApkMagic(partFile)) {
                        throw IOException("Downloaded file is not a valid APK.")
                    }

                    if (targetFile.exists()) targetFile.delete()
                    if (!partFile.renameTo(targetFile)) {
                        partFile.copyTo(targetFile, overwrite = true)
                        partFile.delete()
                    }
                    onProgress(bytesRead, if (totalBytes > 0L) totalBytes else bytesRead)
                    return targetFile
                }
            } catch (t: Throwable) {
                runCatching { partFile.delete() }
                throw t
            } finally {
                cancelHandle?.dispose()
            }
        }
    }
}
