package com.seerflix.tv.core.player

import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinItemsResponse
import com.seerflix.tv.core.network.JellyfinMediaSegmentDto
import com.seerflix.tv.core.network.JellyfinMediaSegmentsResponse
import com.seerflix.tv.core.network.JellyfinMediaSourceDto
import com.seerflix.tv.core.network.JellyfinMediaStreamDto
import com.seerflix.tv.core.network.JellyfinPlaybackInfoResponse
import com.seerflix.tv.core.network.JellyfinUserDataDto
import com.seerflix.tv.core.network.SessionPreferences
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy
import java.util.Collections

class PlaybackPreparationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var prefs: SessionPreferences
    private val calls: MutableList<String> = Collections.synchronizedList(mutableListOf())

    private val episode = JellyfinItemDto(
        id = "ep3",
        name = "Third",
        type = "Episode",
        seriesId = "series1",
        seriesName = "Show",
        seasonId = "season1",
        parentIndexNumber = 1,
        indexNumber = 3,
        userData = JellyfinUserDataDto(playbackPositionTicks = 0L)
    )
    private val source = JellyfinMediaSourceDto(
        id = "src1",
        container = "mkv",
        supportsDirectPlay = true,
        mediaStreams = listOf(
            JellyfinMediaStreamDto(index = 0, type = "Video", codec = "hevc", width = 3840, height = 2160, realFrameRate = 23.976f)
        )
    )

    /** Episodes returned for `Shows/{id}/Episodes?StartItemId=...`. */
    private var episodesPage: List<JellyfinItemDto> = emptyList()
    private var resumeItems: List<JellyfinItemDto> = emptyList()
    private val itemsById = mutableMapOf<String, JellyfinItemDto>()

    @Before
    fun setUp() {
        prefs = SessionPreferences(
            testFile = tempFolder.newFile("playback_prep.preferences_pb"),
            json = Json { ignoreUnknownKeys = true }
        )
        itemsById[episode.id] = episode
    }

    /** Proxy-based fake: suspend funs that return without suspending just return their value. */
    private fun fakeApi(): JellyfinApi = Proxy.newProxyInstance(
        JellyfinApi::class.java.classLoader,
        arrayOf(JellyfinApi::class.java)
    ) { _, method, args ->
        when (method.name) {
            "getItemDetails" -> {
                val id = args[1] as String
                calls += "details:$id:${args[2]}"
                itemsById[id] ?: error("unknown item $id")
            }
            "getPlaybackInfo" -> {
                calls += "playbackInfo:${args[0]}"
                JellyfinPlaybackInfoResponse(mediaSources = listOf(source), playSessionId = "ps-${args[0]}")
            }
            "getMediaSegments" -> {
                calls += "segments:${args[0]}"
                JellyfinMediaSegmentsResponse(
                    items = listOf(JellyfinMediaSegmentDto(type = "Intro", startTicks = 10L, endTicks = 20L))
                )
            }
            "getEpisodesLite" -> {
                calls += "episodesLite:start=${args[3]}:limit=${args[4]}:season=${args[2]}"
                JellyfinItemsResponse(items = episodesPage)
            }
            "getResumeItemsLite" -> {
                calls += "resumeLite:parent=${args[1]}"
                JellyfinItemsResponse(items = resumeItems)
            }
            "getEpisodes", "getResumeItems" -> error("heavy ${method.name} must not be used by playback")
            "toString" -> "FakeJellyfinApi"
            "hashCode" -> 0
            "equals" -> false
            else -> error("unexpected call ${method.name}")
        }
    } as JellyfinApi

    @Test
    fun `episode session returns without extras and extras resolve next episode via StartItemId page`() = runBlocking {
        val special = JellyfinItemDto(id = "sp1", name = "Special", type = "Episode", parentIndexNumber = 0, indexNumber = 1)
        val next = JellyfinItemDto(id = "ep4", name = "Fourth", type = "Episode", parentIndexNumber = 1, indexNumber = 4)
        episodesPage = listOf(episode, special, next)
        val manager = JellyfinPlaybackManager(fakeApi(), prefs)

        val prepared = manager.preparePlaybackWithExtras(extrasScope = this, userId = "u", itemId = "ep3").getOrThrow()

        val session = prepared.session
        assertEquals("ep3", session.itemId)
        assertEquals("ps-ep3", session.playSessionId)
        // Not part of the initial session any more: they arrive via the deferreds.
        assertNull(session.nextEpisodeId)
        assertNull(session.introStartTicks)
        assertEquals(23.976f, prepared.videoFrameRate!!, 0.001f)

        val segments = prepared.segments!!.await()
        assertEquals(10L, segments!!.introStartTicks)
        assertEquals(20L, segments.introEndTicks)
        val nextLookup = prepared.nextEpisode!!.await()
        assertEquals("ep4", nextLookup!!.next!!.id)
        assertEquals("S1:E4 • Fourth", nextLookup.next!!.title)

        assertTrue(calls.contains("details:ep3:${JellyfinPlaybackManager.PLAYBACK_ITEM_FIELDS}"))
        assertTrue(calls.contains("playbackInfo:ep3"))
        assertTrue(calls.contains("episodesLite:start=ep3:limit=${JellyfinPlaybackManager.NEXT_EPISODE_LOOKAHEAD}:season=null"))
    }

    @Test
    fun `preparePlayback without extras scope does not fetch segments or next episode`() = runBlocking {
        val manager = JellyfinPlaybackManager(fakeApi(), prefs)
        val session = manager.preparePlayback(userId = "u", itemId = "ep3").getOrThrow()
        assertEquals("ep3", session.itemId)
        assertFalse(calls.any { it.startsWith("segments") || it.startsWith("episodesLite") })
    }

    @Test
    fun `series id resolves the in-progress episode and requests its PlaybackInfo`() = runBlocking {
        val series = JellyfinItemDto(id = "series1", name = "Show", type = "Series")
        val inProgress = JellyfinItemDto(
            id = "ep2",
            name = "Second",
            type = "Episode",
            seriesId = "series1",
            parentIndexNumber = 1,
            indexNumber = 2,
            userData = JellyfinUserDataDto(playbackPositionTicks = 5_000_000L)
        )
        itemsById[series.id] = series
        itemsById[inProgress.id] = inProgress
        resumeItems = listOf(inProgress)
        episodesPage = emptyList()
        val manager = JellyfinPlaybackManager(fakeApi(), prefs)

        val prepared = manager.preparePlaybackWithExtras(extrasScope = this, userId = "u", itemId = "series1").getOrThrow()

        assertEquals("ep2", prepared.session.itemId)
        assertEquals("ps-ep2", prepared.session.playSessionId)
        assertEquals(5_000_000L, prepared.session.resumeTicks)
        assertTrue(calls.contains("resumeLite:parent=series1"))
        assertTrue(calls.contains("playbackInfo:ep2"))
        // StartItemId page didn't contain the current episode -> legacy season fallback, still no next.
        assertNotNull(prepared.nextEpisode!!.await())
        assertNull(prepared.nextEpisode!!.await()!!.next)
    }

    @Test
    fun `pickNextEpisode skips specials unless the current episode is a special`() {
        val regular = JellyfinItemDto(id = "a", type = "Episode", parentIndexNumber = 2, indexNumber = 5)
        val special = JellyfinItemDto(id = "s", type = "Episode", parentIndexNumber = 0, indexNumber = 1)
        val nextRegular = JellyfinItemDto(id = "b", type = "Episode", parentIndexNumber = 3, indexNumber = 1)
        assertEquals("b", JellyfinPlaybackManager.pickNextEpisode(regular, listOf(special, nextRegular))?.id)

        val special2 = JellyfinItemDto(id = "s2", type = "Episode", parentIndexNumber = 0, indexNumber = 2)
        assertEquals("s2", JellyfinPlaybackManager.pickNextEpisode(special, listOf(nextRegular, special2))?.id)
        assertNull(JellyfinPlaybackManager.pickNextEpisode(regular, emptyList()))
    }

    @Test
    fun `isInBandTextSubtitle only skips side-loading for embedded text subs of direct-played containers`() {
        assertTrue(JellyfinPlaybackManager.isInBandTextSubtitle("mkv", "subrip", isExternal = false, isHls = false))
        assertTrue(JellyfinPlaybackManager.isInBandTextSubtitle("mkv", "ASS", isExternal = false, isHls = false))
        assertTrue(JellyfinPlaybackManager.isInBandTextSubtitle("mov,mp4,m4a,3gp,3g2,mj2", "mov_text", isExternal = false, isHls = false))
        // Sidecar files, HLS transcodes and unsupported combinations keep being side-loaded.
        assertFalse(JellyfinPlaybackManager.isInBandTextSubtitle("mkv", "subrip", isExternal = true, isHls = false))
        assertFalse(JellyfinPlaybackManager.isInBandTextSubtitle("mkv", "subrip", isExternal = false, isHls = true))
        assertFalse(JellyfinPlaybackManager.isInBandTextSubtitle("mp4", "subrip", isExternal = false, isHls = false))
        assertFalse(JellyfinPlaybackManager.isInBandTextSubtitle("avi", "subrip", isExternal = false, isHls = false))
        assertFalse(JellyfinPlaybackManager.isInBandTextSubtitle(null, "subrip", isExternal = false, isHls = false))
    }

    @Test
    fun `PlaybackSegments maps intro recap and credits markers`() {
        val segments = PlaybackSegments.from(
            listOf(
                JellyfinMediaSegmentDto(type = "Recap", startTicks = 0L, endTicks = 5L),
                JellyfinMediaSegmentDto(type = "Intro", startTicks = 10L, endTicks = 20L),
                JellyfinMediaSegmentDto(type = "Outro", startTicks = 900L, endTicks = 1000L)
            )
        )
        assertEquals(0L, segments.recapStartTicks)
        assertEquals(5L, segments.recapEndTicks)
        assertEquals(10L, segments.introStartTicks)
        assertEquals(900L, segments.creditsStartTicks)
    }

    @Test
    fun `targetBufferBytes scales with heap and is clamped`() {
        val mb = 1024L * 1024L
        assertEquals(PlayerTuning.MIN_TARGET_BUFFER_BYTES, PlayerTuning.targetBufferBytes(64 * mb))
        assertEquals((192 * mb * 0.35).toInt(), PlayerTuning.targetBufferBytes(192 * mb))
        assertEquals(PlayerTuning.MAX_TARGET_BUFFER_BYTES, PlayerTuning.targetBufferBytes(512 * mb))
    }

    @Test
    fun `AssCueIndex finds overlapping cues like a linear filter`() {
        val cues = listOf(
            AssCue(startMs = 0L, endMs = 10_000L, text = "long sign"),
            AssCue(startMs = 1_000L, endMs = 2_000L, text = "a"),
            AssCue(startMs = 1_500L, endMs = 3_000L, text = "b"),
            AssCue(startMs = 5_000L, endMs = 6_000L, text = "c"),
            AssCue(startMs = 20_000L, endMs = 21_000L, text = "d")
        )
        val index = AssCueIndex(cues.shuffled(java.util.Random(7)))
        for (t in listOf(-1L, 0L, 999L, 1_000L, 1_700L, 2_000L, 4_000L, 5_500L, 10_001L, 20_500L, 30_000L)) {
            val expected = cues.filter { t in it.startMs..it.endMs }.map { it.text }.sorted()
            assertEquals("t=$t", expected, index.activeAt(t).map { it.text }.sorted())
        }
    }
}
