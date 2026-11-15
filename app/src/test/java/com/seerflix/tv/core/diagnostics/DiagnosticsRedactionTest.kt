package com.seerflix.tv.core.diagnostics

import com.seerflix.tv.feature.library.LibraryViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsRedactionTest {

    @Test
    fun `access tokens, auth headers and cookies are removed from logs`() {
        val log = """
            GET http://10.0.2.2:8096/Videos/1/stream?Static=true&api_key=abc123secret&MediaSourceId=1
            Authorization: MediaBrowser Client="Lumen", DeviceId="d", Token="tok-987"
            X-Api-Key: seerrKEY42
            Cookie: connect.sid=s%3Asession; theme=dark
        """.trimIndent()
        val out = DiagnosticsRepository.redact(log)
        assertFalse(out.contains("abc123secret"))
        assertFalse(out.contains("tok-987"))
        assertFalse(out.contains("seerrKEY42"))
        assertFalse(out.contains("s%3Asession"))
        assertTrue(out.contains("api_key=REDACTED&MediaSourceId=1"))
        assertTrue(out.contains("theme=dark"))
    }

    @Test
    fun `letter jump maps to prefix or before-A queries`() {
        assertEquals(null to null, LibraryViewModel.letterQuery(null))
        assertEquals("M" to null, LibraryViewModel.letterQuery("M"))
        assertEquals(null to "A", LibraryViewModel.letterQuery("#"))
        assertEquals(27, LibraryViewModel.LETTERS.size)
    }
}
