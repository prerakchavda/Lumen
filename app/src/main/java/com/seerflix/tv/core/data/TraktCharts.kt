package com.seerflix.tv.core.data

import android.content.Context
import android.util.Log
import com.seerflix.tv.BuildConfig
import com.seerflix.tv.core.network.TraktApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** One ranked chart position: a TMDB id plus Trakt's title (for logs only; artwork comes from Seerr). */
@Serializable
data class ChartEntry(val tmdbId: Int, val title: String = "")

/**
 * Real "what people are watching" charts from Trakt, ranked by members who watched this week.
 *
 * Charts move daily at most, so each list is kept on disk for [FRESH_MS] and only re-downloaded
 * after that; Home therefore pays for one small Trakt request a few times a day, not per launch.
 * If Trakt can't be reached, a copy up to [MAX_STALE_MS] old is still served. Returns null when no
 * Trakt client id was built into the app or nothing usable is available, so callers fall back to
 * Seerr's TMDB popularity lists.
 */
@Singleton
class TraktCharts internal constructor(
    private val api: TraktApi?,
    private val dirProvider: () -> File?,
    private val json: Json,
    private val clock: () -> Long = System::currentTimeMillis
) {
    @Inject
    constructor(api: TraktApi, @ApplicationContext context: Context, json: Json) : this(
        api = api.takeIf { BuildConfig.TRAKT_CLIENT_ID.isNotBlank() },
        dirProvider = { File(context.cacheDir, DIR_NAME) },
        json = json
    )

    @Serializable
    private data class Snapshot(val fetchedAtMs: Long, val entries: List<ChartEntry>)

    private val memory = ConcurrentHashMap<String, Snapshot>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    val isConfigured: Boolean get() = api != null

    /** Movies from this year and last, most watched this week. */
    suspend fun movies(today: LocalDate): List<ChartEntry>? {
        val api = api ?: return null
        val years = "${today.year - 1}-${today.year}"
        return chart("movies_$years") {
            // Trakt documents `years` as "4 digit year or range of years"; if a server ever rejects
            // the range, ask without it and keep the same two years here.
            val rows = runCatchingCancellable {
                api.watchedMovies(period = "weekly", limit = FETCH_LIMIT, years = years)
            }.getOrElse {
                api.watchedMovies(period = "weekly", limit = FETCH_LIMIT * 2, years = null)
                    .filter { row -> (row.movie?.year ?: 0) >= today.year - 1 }
            }
            rows.mapNotNull { row -> row.movie?.let { m -> m.ids.tmdb?.let { ChartEntry(it, m.title.orEmpty()) } } }
        }
    }

    /** Shows that are still running (not ended or cancelled), most watched this week. */
    suspend fun shows(): List<ChartEntry>? {
        val api = api ?: return null
        return chart("shows_returning") {
            api.watchedShows(period = "weekly", limit = FETCH_LIMIT, status = "returning series")
                .mapNotNull { row -> row.show?.let { s -> s.ids.tmdb?.let { ChartEntry(it, s.title.orEmpty()) } } }
        }
    }

    private suspend fun chart(key: String, fetch: suspend () -> List<ChartEntry>): List<ChartEntry>? {
        // One download per chart even when Home and New & Popular ask at the same moment.
        return locks.getOrPut(key) { Mutex() }.withLock {
            val cached = memory[key] ?: readDisk(key)?.also { memory[key] = it }
            val now = clock()
            if (cached != null && now - cached.fetchedAtMs in 0 until FRESH_MS) return@withLock cached.entries

            val fresh = runCatchingCancellable { fetch() }
                // runCatching: android.util.Log is a stub in JVM unit tests.
                .onFailure { e -> runCatching { Log.w(TAG, "Trakt chart $key failed: ${e.message}") } }
                .getOrNull()
                ?.distinctBy { it.tmdbId }
                ?.takeIf { it.isNotEmpty() }
            if (fresh != null) {
                val snapshot = Snapshot(now, fresh)
                memory[key] = snapshot
                writeDisk(key, snapshot)
                return@withLock fresh
            }
            cached?.takeIf { now - it.fetchedAtMs in 0 until MAX_STALE_MS }?.entries
        }
    }

    private suspend fun readDisk(key: String): Snapshot? = withContext(Dispatchers.IO) {
        val file = dirProvider()?.let { File(it, "$key.json") } ?: return@withContext null
        if (!file.isFile) return@withContext null
        runCatchingCancellable { json.decodeFromString(Snapshot.serializer(), file.readText()) }.getOrNull()
    }

    private suspend fun writeDisk(key: String, snapshot: Snapshot) = withContext(Dispatchers.IO) {
        val dir = dirProvider() ?: return@withContext
        runCatchingCancellable {
            dir.mkdirs()
            val tmp = File(dir, "$key.json.tmp")
            tmp.writeText(json.encodeToString(Snapshot.serializer(), snapshot))
            if (!tmp.renameTo(File(dir, "$key.json"))) {
                File(dir, "$key.json").writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        private const val TAG = "TraktCharts"
        private const val DIR_NAME = "trakt_charts"
        private const val FETCH_LIMIT = 40
        internal const val FRESH_MS = 6L * 60 * 60 * 1000
        internal const val MAX_STALE_MS = 3L * 24 * 60 * 60 * 1000
    }
}
