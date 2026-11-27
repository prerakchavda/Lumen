package com.seerflix.tv.core.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import com.seerflix.tv.BuildConfig
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.player.DeviceCapabilityProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a crash report to disk when the app dies, so it can be offered for upload on the next
 * launch. Installed first thing in Application.onCreate; the previous handler still runs, so the
 * system "app has stopped" behaviour is unchanged.
 */
object CrashRecorder {
    private const val DIR = "diagnostics"
    private const val FILE = "last_crash.txt"

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(appContext, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun crashFile(context: Context): File = File(File(context.filesDir, DIR), FILE)

    fun pendingCrash(context: Context): String? =
        crashFile(context).takeIf { it.exists() }?.let { runCatching { it.readText() }.getOrNull() }

    fun clear(context: Context) {
        crashFile(context).delete()
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val file = crashFile(context)
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendLine("Lumen crash report")
                appendLine("Time: ${timestamp()}")
                appendLine("Thread: ${thread.name}")
                appendLine(deviceHeader())
                appendLine()
                append(trace)
            }
        )
    }

    internal fun timestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(Date())

    internal fun deviceHeader(): String =
        "App: Lumen ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}"
}

sealed interface LogUploadResult {
    data class Uploaded(val fileName: String) : LogUploadResult
    /** The server has "Allow client log upload" switched off (Dashboard > General). */
    data object DisabledOnServer : LogUploadResult
    data class Failed(val message: String) : LogUploadResult
}

/**
 * Collects a diagnostics bundle (device, what it can play, recent app log, last crash) and stores
 * it on the user's own Jellyfin server, where it shows up under Dashboard > Logs. Nothing is sent
 * anywhere else.
 */
@Singleton
class DiagnosticsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jellyfinApi: JellyfinApi,
    private val capabilityProvider: DeviceCapabilityProvider
) {
    fun pendingCrash(): String? = CrashRecorder.pendingCrash(context)

    fun dismissCrash() = CrashRecorder.clear(context)

    suspend fun buildReport(includeCrash: Boolean = true): String = withContext(Dispatchers.IO) {
        buildString {
            appendLine("Lumen diagnostics")
            appendLine("Time: ${CrashRecorder.timestamp()}")
            appendLine(CrashRecorder.deviceHeader())
            appendLine("Playback capabilities: ${runCatching { capabilityProvider.current().summary() }.getOrDefault("unknown")}")
            appendLine()
            if (includeCrash) {
                pendingCrash()?.let {
                    appendLine("===== Last crash =====")
                    // Exception messages can carry URLs with api_key / tokens; same redaction as the log.
                    appendLine(redact(it))
                    appendLine()
                }
            }
            appendLine("===== Recent app log =====")
            append(readOwnLog())
        }
    }

    /** Uploads [report] (or a fresh one) to the server and, on success, clears the stored crash. */
    suspend fun upload(report: String? = null): LogUploadResult = withContext(Dispatchers.IO) {
        val body = (report ?: buildReport()).toRequestBody("text/plain; charset=utf-8".toMediaType())
        try {
            val response = jellyfinApi.uploadClientLog(body)
            dismissCrash()
            LogUploadResult.Uploaded(response.fileName.ifBlank { "client log" })
        } catch (e: HttpException) {
            if (e.code() == 403) LogUploadResult.DisabledOnServer
            else LogUploadResult.Failed("Server returned HTTP ${e.code()}")
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUploadResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** The last lines this process wrote to logcat (an app may read its own log without permissions). */
    private fun readOwnLog(): String = runCatching {
        val proc = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "-t", LOG_LINES.toString(), "--pid=${Process.myPid()}"
        ).redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        proc.waitFor(5, TimeUnit.SECONDS)
        redact(text)
    }.getOrElse { "(log unavailable: ${it.message})" }

    companion object {
        private const val LOG_LINES = 1500

        private val TOKEN_PATTERNS = listOf(
            Regex("(api_key=)[^&\\s\"]+", RegexOption.IGNORE_CASE),
            Regex("(Token=\")[^\"]+", RegexOption.IGNORE_CASE),
            Regex("(X-Api-Key:\\s*)\\S+", RegexOption.IGNORE_CASE),
            Regex("(connect\\.sid=)[^;\\s]+", RegexOption.IGNORE_CASE)
        )

        /** Strips access tokens / cookies from log text before it leaves the device. */
        fun redact(text: String): String =
            TOKEN_PATTERNS.fold(text) { acc, re -> re.replace(acc) { m -> m.groupValues[1] + "REDACTED" } }
    }
}
