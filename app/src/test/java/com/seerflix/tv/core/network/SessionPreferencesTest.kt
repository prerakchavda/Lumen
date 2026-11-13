package com.seerflix.tv.core.network

import com.seerflix.tv.core.model.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionPreferencesTest {

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
}
