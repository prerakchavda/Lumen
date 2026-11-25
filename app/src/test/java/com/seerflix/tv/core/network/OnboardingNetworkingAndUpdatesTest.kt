package com.seerflix.tv.core.network

import com.seerflix.tv.core.data.AppUpdateChecker
import com.seerflix.tv.core.data.AppUpdateInstaller
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class OnboardingNetworkingAndUpdatesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `JellyfinDiscoveryClient parses full and fallback UDP discovery payloads`() {
        val client = JellyfinDiscoveryClient(json)

        val full = client.parseDiscoveryResponse(
            """{"Id":"srv1","Name":"Living Room JF","Address":"http://192.168.1.42:8096/"}"""
        )
        assertNotNull(full)
        assertEquals("srv1", full!!.id)
        assertEquals("Living Room JF", full.name)
        assertEquals("http://192.168.1.42:8096", full.address)

        val fallback = client.parseDiscoveryResponse(
            """{"Id":"srv2","Name":"NAS"}""",
            fallbackHost = "192.168.1.99"
        )
        assertEquals("http://192.168.1.99:8096", fallback?.address)

        assertNull(client.parseDiscoveryResponse("not-json"))
    }

    @Test
    fun `SetupCompanionServer parseFormBody decodes URLs and self-signed toggle`() {
        val body = "jellyfinUrl=https%3A%2F%2Fjf.example.com%2F&jellyfinLocalUrl=http%3A%2F%2F192.168.1.10%3A8096&seerrUrl=&seerrLocalUrl=http%3A%2F%2F192.168.1.10%3A5055&trustSelfSigned=on"
        val parsed = SetupCompanionServer.parseFormBody(body)

        assertEquals("https://jf.example.com/", parsed.jellyfinUrl)
        assertEquals("http://192.168.1.10:8096", parsed.jellyfinLocalUrl)
        assertEquals("", parsed.seerrUrl)
        assertEquals("http://192.168.1.10:5055", parsed.seerrLocalUrl)
        assertTrue(parsed.trustSelfSigned)
    }

    @Test
    fun `SetupCompanionServer only serves paths under its random access token`() {
        val token = SetupCompanionServer.generateAccessToken()
        assertEquals(24, token.length)
        assertFalse(token == SetupCompanionServer.generateAccessToken())

        assertTrue(SetupCompanionServer.isAuthorizedPath("/$token/", token))
        assertTrue(SetupCompanionServer.isAuthorizedPath("/$token/save", token))
        assertTrue(SetupCompanionServer.isAuthorizedPath("/$token", token))
        assertFalse(SetupCompanionServer.isAuthorizedPath("/", token))
        assertFalse(SetupCompanionServer.isAuthorizedPath("/save", token))
        assertFalse(SetupCompanionServer.isAuthorizedPath("/${token}x/save", token))
        assertFalse(SetupCompanionServer.isAuthorizedPath("/anything", ""))
    }

    @Test
    fun `Jellyfin authorization header uses MediaBrowser scheme and only adds Token when present`() {
        val anonymous = buildJellyfinAuthHeader("dev-1", null)
        assertTrue(anonymous.startsWith("MediaBrowser Client=\"Lumen\""))
        assertTrue(anonymous.contains("DeviceId=\"dev-1\""))
        assertFalse(anonymous.contains("Token="))

        val authed = buildJellyfinAuthHeader("dev-1", "abc123")
        assertTrue(authed.endsWith(", Token=\"abc123\""))
    }

    @Test
    fun `AppUpdateChecker compares semantic versions and parses GitHub release APK asset`() {
        assertTrue(AppUpdateChecker.isNewerVersion("0.1.0-beta", "v0.1.1"))
        assertTrue(AppUpdateChecker.isNewerVersion("0.1.0-beta", "v0.2.0"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.0.0", "v1.2.0"))
        assertTrue(AppUpdateChecker.isNewerVersion("1.2.0", "2.0.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("1.2.0", "v1.2.0"))
        assertFalse(AppUpdateChecker.isNewerVersion("2.0.0", "v1.9.9"))

        val checker = AppUpdateChecker(OkHttpClient(), json)
        val releaseJson = """
            {
              "tag_name": "v1.4.0",
              "body": "Added ASS subtitles and Live TV EPG",
              "html_url": "https://github.com/prerakchavda/Lumen/releases/tag/v1.4.0",
              "prerelease": false,
              "draft": false,
              "assets": [
                {"name": "lumen-tv-v1.4.0.apk", "browser_download_url": "https://github.com/prerakchavda/Lumen/releases/download/v1.4.0/lumen-tv-v1.4.0.apk"}
              ]
            }
        """.trimIndent()

        val update = checker.parseRelease(releaseJson, "1.0.0")
        assertNotNull(update)
        assertEquals("v1.4.0", update!!.latestVersion)
        assertTrue(update.downloadUrl!!.endsWith(".apk"))
        assertNull(checker.parseRelease(releaseJson, "1.4.0"))
    }

    @Test
    fun `AppUpdateInstaller validates release URLs, filenames, and APK magic header`() {
        assertTrue(AppUpdateInstaller.isTrustedReleaseUrl("https://github.com/prerakchavda/Lumen/releases/download/v0.10.0/lumen-tv-v0.10.0.apk"))
        assertTrue(AppUpdateInstaller.isTrustedReleaseUrl("https://objects.githubusercontent.com/github-production-release-asset-123/lumen.apk"))
        assertFalse(AppUpdateInstaller.isTrustedReleaseUrl("http://github.com/prerakchavda/Lumen/releases/download/v0.10.0/lumen.apk"))
        assertFalse(AppUpdateInstaller.isTrustedReleaseUrl("https://evil-github.com/lumen.apk"))
        assertFalse(AppUpdateInstaller.isTrustedReleaseUrl("https://example.com/lumen.apk"))

        assertEquals("lumen-update-v0.10.0.apk", AppUpdateInstaller.apkFileNameForVersion("v0.10.0"))
        assertEquals("lumen-update-v0.10.0.apk", AppUpdateInstaller.apkFileNameForVersion("../../v0.10.0"))
        assertEquals("lumen-update-latest.apk", AppUpdateInstaller.apkFileNameForVersion("   "))

        val validApk = File(tempFolder.root, "valid.apk").apply {
            writeBytes(byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x14, 0x00))
        }
        val htmlPage = File(tempFolder.root, "error.apk").apply {
            writeText("<html>502 Bad Gateway</html>")
        }
        assertTrue(AppUpdateInstaller.hasZipApkMagic(validApk))
        assertFalse(AppUpdateInstaller.hasZipApkMagic(htmlPage))
    }

    @Test
    fun `AppUpdateInstaller downloads, verifies, caches, and cleans up stale APKs`() = runTest {
        val updatesDir = tempFolder.newFolder("updates")
        val staleFile = File(updatesDir, "lumen-update-v0.7.0.apk").apply { writeText("old") }
        val payload = ByteArray(100_000) { i ->
            when (i) {
                0 -> 0x50
                1 -> 0x4B
                2 -> 0x03
                3 -> 0x04
                else -> (i % 251).toByte()
            }
        }

        var networkCalls = 0
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                networkCalls++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(payload.toResponseBody("application/vnd.android.package-archive".toMediaType()))
                    .build()
            }
            .build()

        val progressEvents = mutableListOf<Pair<Long, Long>>()
        val url = "https://github.com/prerakchavda/Lumen/releases/download/v0.10.0/lumen-tv-v0.10.0.apk"

        val downloaded = AppUpdateInstaller.downloadApkToFile(
            client = client,
            rawUrl = url,
            targetDir = updatesDir,
            version = "v0.10.0"
        ) { read, total -> progressEvents += read to total }

        assertEquals(1, networkCalls)
        assertFalse(staleFile.exists())
        assertTrue(downloaded.isFile)
        assertEquals(payload.size.toLong(), downloaded.length())
        assertTrue(progressEvents.last() == payload.size.toLong() to payload.size.toLong())
        assertFalse(File(updatesDir, "${downloaded.name}.part").exists())

        // Re-running for the same version reuses the verified cached APK without re-downloading.
        val cached = AppUpdateInstaller.downloadApkToFile(
            client = client,
            rawUrl = url,
            targetDir = updatesDir,
            version = "v0.10.0"
        )
        assertEquals(1, networkCalls)
        assertEquals(downloaded.absolutePath, cached.absolutePath)
    }

    @Test
    fun `AppUpdateInstaller rejects non-APK response bodies and removes partial files`() = runTest {
        val updatesDir = tempFolder.newFolder("updates_invalid")
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("<html>Not an APK</html>".toResponseBody("text/html".toMediaType()))
                    .build()
            }
            .build()

        var thrown: IOException? = null
        try {
            AppUpdateInstaller.downloadApkToFile(
                client = client,
                rawUrl = "https://github.com/prerakchavda/Lumen/releases/download/v0.10.0/lumen.apk",
                targetDir = updatesDir,
                version = "v0.10.0"
            )
        } catch (e: IOException) {
            thrown = e
        }

        assertNotNull(thrown)
        assertTrue(updatesDir.listFiles().orEmpty().isEmpty())
    }
}
