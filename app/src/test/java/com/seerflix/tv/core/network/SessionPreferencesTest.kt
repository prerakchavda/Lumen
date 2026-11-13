package com.seerflix.tv.core.network

import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UserProfile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SessionPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun `normalizeUrl trims whitespace and trailing slashes`() {
        assertEquals("http://192.168.1.50:8096", SessionPreferences.normalizeUrl("  http://192.168.1.50:8096/  "))
        assertEquals("https://seerr.example.com", SessionPreferences.normalizeUrl("https://seerr.example.com///"))
    }

    @Test
    fun `normalizeUrl prepends http when scheme missing`() {
        assertEquals("http://192.168.1.50:5055", SessionPreferences.normalizeUrl("192.168.1.50:5055/"))
    }

    @Test
    fun `normalizeUrl returns empty string when blank`() {
        assertEquals("", SessionPreferences.normalizeUrl("   "))
    }

    @Test
    fun `hashPin is deterministic SHA-256 hex and verifyPin checks match`() {
        val hash1 = SessionPreferences.hashPin("1234")
        val hash2 = SessionPreferences.hashPin("1234")
        val otherHash = SessionPreferences.hashPin("9999")

        assertEquals(64, hash1.length)
        assertEquals(hash1, hash2)
        assertNotEquals(hash1, otherHash)

        assertTrue(SessionPreferences.verifyPin(hash1, "1234"))
        assertFalse(SessionPreferences.verifyPin(hash1, "4321"))
        assertTrue(SessionPreferences.verifyPin(null, "anything"))
        assertTrue(SessionPreferences.verifyPin("", "0000"))
    }

    @Test
    fun `reminderKey distinguishes media type and tmdb id`() {
        assertEquals("MOVIE_693134", SessionPreferences.reminderKey(693134, MediaType.MOVIE))
        assertEquals("SERIES_95396", SessionPreferences.reminderKey(95396, MediaType.SERIES))
    }

    @Test
    fun `legacy UserProfile JSON without uiPreset deserializes to NETFLIX`() {
        val legacyJson = """
            {
              "id": "u1",
              "name": "Prerak",
              "jellyfinUserId": "jf1",
              "jellyfinToken": "tok1",
              "avatarColorHex": 15010068
            }
        """.trimIndent()

        val decoded = json.decodeFromString<UserProfile>(legacyJson)
        assertEquals(UiPreset.NETFLIX, decoded.uiPreset)
    }

    @Test
    fun `updateActiveUiPreset updates only the active profile in DataStore`() = runTest {
        val prefs = SessionPreferences(File(tempFolder.root, "prefs_isolation.preferences_pb"), json)
        val alice = UserProfile("1", "Alice", "jf1", "t1")
        val bob = UserProfile("2", "Bob", "jf2", "t2")

        prefs.upsertAndActivateProfile(alice)
        prefs.upsertAndActivateProfile(bob)
        assertEquals("2", prefs.activeProfileFlow.first()?.id)

        prefs.updateActiveUiPreset(UiPreset.APPLE_TV)

        assertEquals(UiPreset.APPLE_TV, prefs.activeProfileFlow.first()?.uiPreset)
        val allAfterBobUpdate = prefs.profilesFlow.first().associateBy { it.id }
        assertEquals(UiPreset.NETFLIX, allAfterBobUpdate["1"]?.uiPreset)
        assertEquals(UiPreset.APPLE_TV, allAfterBobUpdate["2"]?.uiPreset)

        prefs.setActiveProfile("1")
        prefs.updateActiveUiPreset(UiPreset.DISNEY_PLUS)

        val allAfterAliceUpdate = prefs.profilesFlow.first().associateBy { it.id }
        assertEquals(UiPreset.DISNEY_PLUS, allAfterAliceUpdate["1"]?.uiPreset)
        assertEquals(UiPreset.APPLE_TV, allAfterAliceUpdate["2"]?.uiPreset)
    }

    @Test
    fun `upsertAndActivateProfile preserves existing uiPreset across re-login`() = runTest {
        val prefs = SessionPreferences(File(tempFolder.root, "prefs_preserve.preferences_pb"), json)
        val initial = UserProfile("1", "Alice", "jf1", "old_token")
        prefs.upsertAndActivateProfile(initial)
        prefs.updateActiveUiPreset(UiPreset.DISNEY_PLUS)

        val relogin = UserProfile("1", "Alice", "jf1", "new_token", uiPreset = UiPreset.NETFLIX)
        prefs.upsertAndActivateProfile(relogin)

        val active = prefs.activeProfileFlow.first()
        assertEquals("new_token", active?.jellyfinToken)
        assertEquals(UiPreset.DISNEY_PLUS, active?.uiPreset)
    }
}
