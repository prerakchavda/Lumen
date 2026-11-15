package com.seerflix.tv.core.network

import com.seerflix.tv.core.data.AppUpdateChecker
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingNetworkingAndUpdatesTest {

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
              "html_url": "https://github.com/prerakchavda/seerflix-tv/releases/tag/v1.4.0",
              "prerelease": false,
              "draft": false,
              "assets": [
                {"name": "seerflix-tv-release.apk", "browser_download_url": "https://github.com/prerakchavda/seerflix-tv/releases/download/v1.4.0/seerflix-tv-release.apk"}
              ]
            }
        """.trimIndent()

        val update = checker.parseRelease(releaseJson, "1.0.0")
        assertNotNull(update)
        assertEquals("v1.4.0", update!!.latestVersion)
        assertTrue(update.downloadUrl!!.endsWith(".apk"))
        assertNull(checker.parseRelease(releaseJson, "1.4.0"))
    }
}
