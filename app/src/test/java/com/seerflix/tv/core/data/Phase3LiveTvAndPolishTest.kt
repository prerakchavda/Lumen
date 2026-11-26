package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.ContentRatingLimit
import com.seerflix.tv.core.model.DvrTimerOptions
import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.LiveTvFilter
import com.seerflix.tv.core.model.LiveTvProgram
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinItemsResponse
import com.seerflix.tv.core.network.JellyfinUserDataDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.CinemaIntroQueue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy

class Phase3LiveTvAndPolishTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `patchTimerDefaults applies pre and post padding and series flags while keeping server defaults`() {
        val defaults = JsonObject(
            mapOf(
                "ChannelId" to JsonPrimitive("ch-42"),
                "ProgramId" to JsonPrimitive("prog-7"),
                "PrePaddingSeconds" to JsonPrimitive(0),
                "PostPaddingSeconds" to JsonPrimitive(0)
            )
        )

        val single = UnifiedCatalogRepository.patchTimerDefaults(
            defaults = defaults,
            series = false,
            options = DvrTimerOptions(prePaddingSeconds = 120, postPaddingSeconds = 900, recordNewOnly = true, recordAnyChannel = true)
        )
        assertEquals("ch-42", single["ChannelId"]?.jsonPrimitive?.content)
        assertEquals(120, single["PrePaddingSeconds"]?.jsonPrimitive?.intOrNull)
        assertEquals(900, single["PostPaddingSeconds"]?.jsonPrimitive?.intOrNull)
        assertNull(single["RecordNewOnly"])
        assertNull(single["RecordAnyChannel"])

        val series = UnifiedCatalogRepository.patchTimerDefaults(
            defaults = defaults,
            series = true,
            options = DvrTimerOptions(prePaddingSeconds = -30, postPaddingSeconds = 600, recordNewOnly = true, recordAnyChannel = false)
        )
        assertEquals(0, series["PrePaddingSeconds"]?.jsonPrimitive?.intOrNull)
        assertEquals(600, series["PostPaddingSeconds"]?.jsonPrimitive?.intOrNull)
        assertEquals(true, series["RecordNewOnly"]?.jsonPrimitive?.booleanOrNull)
        assertEquals(false, series["RecordAnyChannel"]?.jsonPrimitive?.booleanOrNull)
    }

    @Test
    fun `LiveTvFilter matches Favorites, Movies, Sports, News, and Kids channels`() {
        val sportsChannel = LiveTvChannel(
            id = "c1",
            number = "10",
            name = "ESPN",
            logoUrl = null,
            isFavorite = true,
            currentProgram = LiveTvProgram(id = "p1", title = "Finals", isSports = true)
        )
        val kidsChannel = LiveTvChannel(
            id = "c2",
            number = "20",
            name = "Cartoon",
            logoUrl = null,
            isFavorite = false,
            programs = listOf(LiveTvProgram(id = "p2", title = "Bluey", isKids = true))
        )
        val movieNewsChannel = LiveTvChannel(
            id = "c3",
            number = "30",
            name = "Cinema News",
            logoUrl = null,
            currentProgram = LiveTvProgram(id = "p3", title = "Evening News", isNews = true),
            programs = listOf(
                LiveTvProgram(id = "p3", title = "Evening News", isNews = true),
                LiveTvProgram(id = "p4", title = "Late Movie", isMovie = true)
            )
        )

        val all = listOf(sportsChannel, kidsChannel, movieNewsChannel)
        assertEquals(3, all.count { LiveTvFilter.ALL.matches(it) })
        assertEquals(listOf("c1"), all.filter { LiveTvFilter.FAVORITES.matches(it) }.map { it.id })
        assertEquals(listOf("c1"), all.filter { LiveTvFilter.SPORTS.matches(it) }.map { it.id })
        assertEquals(listOf("c2"), all.filter { LiveTvFilter.KIDS.matches(it) }.map { it.id })
        assertEquals(listOf("c3"), all.filter { LiveTvFilter.NEWS.matches(it) }.map { it.id })
        assertEquals(listOf("c3"), all.filter { LiveTvFilter.MOVIES.matches(it) }.map { it.id })
    }

    @Test
    fun `LiveTvEpgHelper computes half-hour slots, clips program cells with gap fill, and wraps channel surfing`() {
        val base = LiveTvEpgHelper.floorToHalfHour(1_700_000_900_000L)
        assertEquals(0L, base % LiveTvEpgHelper.HALF_HOUR_MS)

        val slots = LiveTvEpgHelper.timeSlots(base)
        assertEquals(LiveTvEpgHelper.GRID_SLOTS, slots.size)
        assertEquals(base + LiveTvEpgHelper.HALF_HOUR_MS, slots[1])

        val ch = LiveTvChannel(
            id = "ch1",
            number = "1",
            name = "News 1",
            logoUrl = null,
            programs = listOf(
                // Starts 15m before window, ends 1h into window
                LiveTvProgram(id = "p1", title = "Morning", startMs = base - 15 * 60_000L, endMs = base + 60 * 60_000L),
                // 30m gap, then 1h program
                LiveTvProgram(id = "p2", title = "Noon", startMs = base + 90 * 60_000L, endMs = base + 150 * 60_000L)
            )
        )
        val cells = LiveTvEpgHelper.cellsForWindow(ch, windowStartMs = base, nowMs = base + 10 * 60_000L)
        // Expect: p1 (0..60m), gap (60..90m), p2 (90..150m), tail gap (150..180m)
        assertEquals(4, cells.size)
        assertEquals("p1", cells[0].program?.id)
        assertTrue(cells[0].isCurrent)
        assertNull(cells[1].program)
        assertEquals("p2", cells[2].program?.id)
        assertNull(cells[3].program)

        val channels = listOf(
            LiveTvChannel("c1", "1", "One", null),
            LiveTvChannel("c2", "2", "Two", null),
            LiveTvChannel("c3", "3", "Three", null)
        )
        assertEquals("c2", LiveTvEpgHelper.nextChannelId(channels, "c1", +1)?.id)
        assertEquals("c3", LiveTvEpgHelper.nextChannelId(channels, "c1", -1)?.id)
        assertEquals("c1", LiveTvEpgHelper.nextChannelId(channels, "c3", +1)?.id)
        assertNull(LiveTvEpgHelper.nextChannelId(listOf(channels.first()), "c1", +1))
    }

    @Test
    fun `CinemaIntroQueue only arms on unresumed movies and sequences intros then main movie cleanly`() {
        val queue = CinemaIntroQueue()
        val movieSession = PlaybackSessionData(
            itemId = "movie-1",
            title = "Inception",
            subtitle = "2010",
            streamUrl = "http://jf/v",
            isHls = false,
            mediaSourceId = "src-1",
            playSessionId = "ps-1",
            resumeTicks = 0L,
            runtimeTicks = 80_000_000_000L
        )
        assertTrue(queue.shouldQueryIntros(true, movieSession, null, false, false, false))
        assertFalse(queue.shouldQueryIntros(false, movieSession, null, false, false, false))
        assertFalse(queue.shouldQueryIntros(true, movieSession.copy(resumeTicks = 600_000_000L), null, false, false, false))
        assertFalse(queue.shouldQueryIntros(true, movieSession.copy(seriesId = "ser-1"), null, false, false, false))
        assertFalse(queue.shouldQueryIntros(true, movieSession.copy(isLiveTv = true), null, false, false, false))
        assertFalse(queue.shouldQueryIntros(true, movieSession, 12_000L, false, false, false))

        val main = CinemaIntroQueue.PendingMainItem("movie-1", "src-1", 1, 2, false)
        val step1 = queue.arm(listOf("intro-1", "intro-2"), main)
        assertTrue(step1 is CinemaIntroQueue.NextStep.PlayIntro)
        assertEquals("intro-1", (step1 as CinemaIntroQueue.NextStep.PlayIntro).introItemId)
        assertEquals(1, step1.index)
        assertEquals(2, step1.total)

        val step2 = queue.advance()
        assertTrue(step2 is CinemaIntroQueue.NextStep.PlayIntro)
        assertEquals("intro-2", (step2 as CinemaIntroQueue.NextStep.PlayIntro).introItemId)
        assertEquals(2, step2.index)

        val step3 = queue.advance()
        assertTrue(step3 is CinemaIntroQueue.NextStep.PlayMain)
        assertEquals("movie-1", (step3 as CinemaIntroQueue.NextStep.PlayMain).main.itemId)
        assertFalse(queue.hasPendingMain)

        // Skip immediately jumps over remaining intros to main movie
        queue.arm(listOf("intro-a", "intro-b", "intro-c"), main)
        val skipped = queue.skipToMain()
        assertTrue(skipped is CinemaIntroQueue.NextStep.PlayMain)
        assertEquals("movie-1", (skipped as CinemaIntroQueue.NextStep.PlayMain).main.itemId)
        assertFalse(queue.hasPendingMain)
    }

    @Test
    fun `repository resolves theme song URL, filters kids screensaver backdrops, and patches DVR timer payload`() = runBlocking {
        var postedSeriesTimer: JsonObject? = null
        val jfApi = Proxy.newProxyInstance(
            JellyfinApi::class.java.classLoader,
            arrayOf(JellyfinApi::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getThemeSongs" -> JellyfinItemsResponse(
                    listOf(JellyfinItemDto(id = "theme-99", name = "Main Theme", type = "Audio")),
                    1
                )
                "getLibraryItems" -> JellyfinItemsResponse(
                    listOf(
                        JellyfinItemDto(
                            id = "kid-movie",
                            name = "Toy Story",
                            type = "Movie",
                            officialRating = "G",
                            productionYear = 1995,
                            backdropImageTags = listOf("tag1")
                        ),
                        JellyfinItemDto(
                            id = "adult-movie",
                            name = "Deadpool",
                            type = "Movie",
                            officialRating = "R",
                            productionYear = 2016,
                            backdropImageTags = listOf("tag2")
                        )
                    ),
                    2
                )
                "getLiveTvTimerDefaults" -> JsonObject(mapOf("ProgramId" to JsonPrimitive("prog-1")))
                "createLiveTvSeriesTimer" -> {
                    postedSeriesTimer = args?.firstOrNull() as? JsonObject
                    Unit
                }
                "getLiveTvChannels" -> JellyfinItemsResponse(
                    listOf(
                        JellyfinItemDto(
                            id = "ch-1",
                            name = "Sports HD",
                            channelNumber = "101",
                            userData = JellyfinUserDataDto(isFavorite = true)
                        )
                    ),
                    1
                )
                "getLiveTvPrograms" -> JellyfinItemsResponse(
                    listOf(
                        JellyfinItemDto(
                            id = "pr-1",
                            name = "Championship Match",
                            channelId = "ch-1",
                            startDate = "2026-11-26T20:00:00Z",
                            endDate = "2026-11-26T22:00:00Z",
                            isSports = true
                        )
                    ),
                    1
                )
                else -> JellyfinItemsResponse()
            }
        } as JellyfinApi

        val seerrApi = Proxy.newProxyInstance(
            SeerrApi::class.java.classLoader,
            arrayOf(SeerrApi::class.java)
        ) { _, _, _ -> error("Seerr not used") } as SeerrApi

        val prefs = SessionPreferences(
            testFile = tempFolder.newFile("phase3_test.preferences_pb"),
            json = Json { ignoreUnknownKeys = true }
        )
        prefs.saveServerUrls(jellyfinUrl = "https://jf.example", seerrUrl = "")
        val kidsProfile = UserProfile(
            id = "u-kid",
            name = "Kids",
            jellyfinUserId = "u-kid",
            jellyfinToken = "secret-tok",
            maxContentRating = ContentRatingLimit.PG
        )
        prefs.upsertAndActivateProfile(kidsProfile)
        val repo = UnifiedCatalogRepository(jfApi, seerrApi, prefs)

        // 1. Theme song URL
        val themeUrl = repo.loadThemeSongUrl("u-kid", "ser-1")
        assertTrue(themeUrl!!.startsWith("https://jf.example/Audio/theme-99/universal?UserId=u-kid"))

        // 2. Kids screensaver backdrops exclude R-rated movies
        val slides = repo.loadScreensaverBackdrops("u-kid", kidsProfile)
        assertEquals(listOf("Toy Story"), slides.map { it.title })

        // 3. DVR timer patching sent to Jellyfin
        repo.scheduleRecording(
            programId = "prog-1",
            series = true,
            options = DvrTimerOptions(prePaddingSeconds = 300, postPaddingSeconds = 900, recordNewOnly = true, recordAnyChannel = true)
        )
        assertEquals(300, postedSeriesTimer?.get("PrePaddingSeconds")?.jsonPrimitive?.intOrNull)
        assertEquals(900, postedSeriesTimer?.get("PostPaddingSeconds")?.jsonPrimitive?.intOrNull)
        assertEquals(true, postedSeriesTimer?.get("RecordNewOnly")?.jsonPrimitive?.booleanOrNull)
        assertEquals(true, postedSeriesTimer?.get("RecordAnyChannel")?.jsonPrimitive?.booleanOrNull)

        // 4. Live TV channels populate favorite flag, category tags, and full program list
        val channels = repo.loadLiveTvChannelsAndPrograms("u-kid")
        assertEquals(1, channels.size)
        assertTrue(channels[0].isFavorite)
        assertEquals(1, channels[0].programs.size)
        assertTrue(channels[0].programs[0].isSports)
    }
}
