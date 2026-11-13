package com.seerflix.tv.core.network

import org.junit.Assert.assertEquals
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
}
