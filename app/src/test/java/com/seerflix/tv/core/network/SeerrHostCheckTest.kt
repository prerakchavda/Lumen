package com.seerflix.tv.core.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrHostCheckTest {
    private val candidates = listOf("https://seerr.example.com", "http://192.168.1.50:5055/")

    @Test
    fun `configured hosts get the key and cookie`() {
        assertTrue(isConfiguredSeerrHost("https://seerr.example.com/api/v1/request".toHttpUrl(), candidates))
        assertTrue(isConfiguredSeerrHost("http://192.168.1.50:5055/api/v1/status".toHttpUrl(), candidates))
        assertTrue(isConfiguredSeerrHost("https://SEERR.example.com/".toHttpUrl(), candidates))
    }

    @Test
    fun `redirect targets on other hosts, ports or schemes do not`() {
        assertFalse(isConfiguredSeerrHost("https://sso.example.com/login".toHttpUrl(), candidates))
        assertFalse(isConfiguredSeerrHost("http://seerr.example.com/".toHttpUrl(), candidates))
        assertFalse(isConfiguredSeerrHost("http://192.168.1.50:8080/".toHttpUrl(), candidates))
        assertFalse(isConfiguredSeerrHost("https://seerr.example.com/".toHttpUrl(), emptyList()))
    }
}
