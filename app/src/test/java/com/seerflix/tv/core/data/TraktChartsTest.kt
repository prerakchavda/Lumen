package com.seerflix.tv.core.data

import com.seerflix.tv.core.network.TraktApi
import com.seerflix.tv.core.network.TraktIdsDto
import com.seerflix.tv.core.network.TraktTitleDto
import com.seerflix.tv.core.network.TraktWatchedMovieDto
import com.seerflix.tv.core.network.TraktWatchedShowDto
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDate

class TraktChartsTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }
    private val today = LocalDate.of(2026, 11, 15)

    private class CountingApi : TraktApi {
        var calls = 0
        var fail = false
        var ids = listOf(1, 2, 3)
        override suspend fun watchedMovies(period: String, limit: Int, years: String?): List<TraktWatchedMovieDto> {
            calls++
            if (fail) throw java.io.IOException("offline")
            return ids.map { TraktWatchedMovieDto(movie = TraktTitleDto("M$it", 2026, TraktIdsDto(tmdb = it))) } +
                // Entries without a TMDB id can't be shown and are dropped.
                TraktWatchedMovieDto(movie = TraktTitleDto("No TMDB", 2026, TraktIdsDto(trakt = 9)))
        }
        override suspend fun watchedShows(period: String, limit: Int, status: String?) = emptyList<TraktWatchedShowDto>()
    }

    @Test
    fun `chart is reused while fresh, refetched after six hours, and kept on disk`() = runTest {
        val dir = tempFolder.newFolder()
        var now = 1_000_000L
        val api = CountingApi()
        val charts = TraktCharts(api, { dir }, json) { now }

        assertEquals(listOf(1, 2, 3), charts.movies(today)!!.map { it.tmdbId })
        now += TraktCharts.FRESH_MS - 1
        charts.movies(today)
        assertEquals(1, api.calls)

        // A new instance (app restart) reads the disk copy instead of calling Trakt.
        val restarted = TraktCharts(api, { dir }, json) { now }
        assertEquals(listOf(1, 2, 3), restarted.movies(today)!!.map { it.tmdbId })
        assertEquals(1, api.calls)

        now += 2
        api.ids = listOf(4, 5)
        assertEquals(listOf(4, 5), charts.movies(today)!!.map { it.tmdbId })
        assertEquals(2, api.calls)
    }

    @Test
    fun `stale copy covers a trakt outage for up to three days`() = runTest {
        var now = 0L
        val api = CountingApi()
        val charts = TraktCharts(api, { tempFolder.newFolder() }, json) { now }
        charts.movies(today)

        api.fail = true
        now = TraktCharts.FRESH_MS + 1
        assertEquals(listOf(1, 2, 3), charts.movies(today)!!.map { it.tmdbId })
        now = TraktCharts.MAX_STALE_MS + 1
        assertNull(charts.movies(today))
    }

    @Test
    fun `no client id means no trakt calls`() = runTest {
        val charts = TraktCharts(null, { tempFolder.newFolder() }, json)
        assertNull(charts.movies(today))
        assertNull(charts.shows())
    }
}
