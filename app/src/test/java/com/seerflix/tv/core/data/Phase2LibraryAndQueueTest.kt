package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.ContentRatingLimit
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ServerConfig
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.CrossServerLinks
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinCreatePlaylistResponse
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinItemsResponse
import com.seerflix.tv.core.network.JellyfinUserDataDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.PlaybackQueueManager
import com.seerflix.tv.core.player.QueueEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy
import kotlin.random.Random

class Phase2LibraryAndQueueTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `dedupeNextUpAgainstResume drops episodes whose series is already in Resume across servers`() {
        val links = CrossServerLinks(
            primaryOf = mapOf("s2:series-A" to "series-A"),
            copies = mapOf("series-A" to listOf("series-A", "s2:series-A"))
        )
        val resume = listOf(
            JellyfinItemDto(
                id = "ep-1",
                name = "Episode 4",
                type = "Episode",
                seriesId = "series-A",
                parentIndexNumber = 2,
                indexNumber = 4,
                userData = JellyfinUserDataDto(playbackPositionTicks = 500_000L)
            )
        )
        val nextUp = listOf(
            // Same series on extra server -> must be dropped
            JellyfinItemDto(
                id = "s2:ep-5",
                name = "Episode 5",
                type = "Episode",
                seriesId = "s2:series-A",
                parentIndexNumber = 2,
                indexNumber = 5
            ),
            // Different series -> kept
            JellyfinItemDto(
                id = "ep-b2",
                name = "Pilot Part 2",
                type = "Episode",
                seriesId = "series-B",
                parentIndexNumber = 1,
                indexNumber = 2
            ),
            // Duplicate of series-B -> dropped
            JellyfinItemDto(
                id = "ep-b3",
                name = "Episode 3",
                type = "Episode",
                seriesId = "series-B",
                parentIndexNumber = 1,
                indexNumber = 3
            )
        )

        val deduped = CrossServerMerge.dedupeNextUpAgainstResume(resume, nextUp, links)
        assertEquals(1, deduped.size)
        assertEquals("ep-b2", deduped.first().id)
    }

    @Test
    fun `video library filter excludes music, photos, and books while keeping video libraries`() {
        assertTrue(UnifiedCatalogRepository.isVideoCapableLibrary("movies"))
        assertTrue(UnifiedCatalogRepository.isVideoCapableLibrary("tvshows"))
        assertTrue(UnifiedCatalogRepository.isVideoCapableLibrary("homevideos"))
        assertTrue(UnifiedCatalogRepository.isVideoCapableLibrary("musicvideos"))
        assertTrue(UnifiedCatalogRepository.isVideoCapableLibrary(null)) // mixed video

        assertFalse(UnifiedCatalogRepository.isVideoCapableLibrary("music"))
        assertFalse(UnifiedCatalogRepository.isVideoCapableLibrary("photos"))
        assertFalse(UnifiedCatalogRepository.isVideoCapableLibrary("books"))
        assertFalse(UnifiedCatalogRepository.isVideoCapableLibrary("audiobooks"))

        assertEquals("Movie", UnifiedCatalogRepository.includeTypesForCollectionType("movies", folderMode = false))
        assertEquals("Movie,Video,Folder", UnifiedCatalogRepository.includeTypesForCollectionType("movies", folderMode = true))
        assertEquals("Series", UnifiedCatalogRepository.includeTypesForCollectionType("tvshows", folderMode = false))
        assertEquals("Video,Movie,MusicVideo", UnifiedCatalogRepository.includeTypesForCollectionType("homevideos", folderMode = false))
        assertEquals("Video,Movie,Series,MusicVideo,Folder", UnifiedCatalogRepository.includeTypesForCollectionType(null, folderMode = true))
    }

    @Test
    fun `PlaybackQueueManager handles Play All, Shuffle, PlayNext, advanceNext and stepPrevious`() {
        val qm = PlaybackQueueManager()
        val items = listOf(
            QueueEntry("i1", "One"),
            QueueEntry("i2", "Two"),
            QueueEntry("i3", "Three")
        )
        val first = qm.setQueue(items, startIndex = 0, shuffle = false)
        assertEquals("i1", first?.itemId)
        assertEquals("1 / 3", qm.state.value.positionLabel)
        assertTrue(qm.state.value.hasNext)
        assertFalse(qm.state.value.hasPrevious)

        // Remote PlayNext inserts right after current item (index 0 -> inserted at index 1)
        qm.enqueueNext(listOf(QueueEntry("i99", "Inserted Next")))
        assertEquals(4, qm.state.value.items.size)
        assertEquals("i99", qm.advanceNext()?.itemId)
        assertEquals("2 / 4", qm.state.value.positionLabel)
        assertTrue(qm.state.value.hasPrevious)

        assertEquals("i1", qm.stepPrevious()?.itemId)
        assertEquals("i99", qm.advanceNext()?.itemId)
        assertEquals("i2", qm.advanceNext()?.itemId)
        assertEquals("i3", qm.advanceNext()?.itemId)
        assertNull(qm.advanceNext())

        // Shuffle keeps all elements and starts at index 0
        val shuffledFirst = qm.setQueue(items, shuffle = true, random = Random(42))
        assertTrue(qm.state.value.isShuffled)
        assertEquals(3, qm.state.value.items.size)
        assertEquals(items.map { it.itemId }.toSet(), qm.state.value.items.map { it.itemId }.toSet())
        assertEquals(qm.state.value.items.first().itemId, shuffledFirst?.itemId)

        // syncToItem clears queue when an unrelated item is played directly
        qm.syncToItem("unrelated-item")
        assertFalse(qm.state.value.isActive)
    }

    @Test
    fun `Jellyfin-only mode searches Persons and resolves PersonIds filmography without Seerr`() = runBlocking {
        val deletedIds = mutableListOf<String>()
        val createdPlaylists = mutableListOf<String>()
        val resumeDtos = listOf(
            JellyfinItemDto(id = "ep-1", name = "Ep 1", type = "Episode", seriesId = "ser-1", seriesName = "Show A", parentIndexNumber = 1, indexNumber = 1, officialRating = "TV-PG")
        )
        val nextUpDtos = listOf(
            JellyfinItemDto(id = "ep-2", name = "Ep 2", type = "Episode", seriesId = "ser-1", seriesName = "Show A", parentIndexNumber = 1, indexNumber = 2, officialRating = "TV-PG"),
            JellyfinItemDto(id = "ep-9", name = "Ep 5", type = "Episode", seriesId = "ser-2", seriesName = "Show B", parentIndexNumber = 2, indexNumber = 5, officialRating = "TV-PG")
        )
        val personDtos = listOf(
            JellyfinItemDto(id = "person-keanu", name = "Keanu Reeves", type = "Person")
        )
        val personCredits = listOf(
            JellyfinItemDto(id = "mov-matrix", name = "The Matrix", type = "Movie", officialRating = "PG-13")
        )

        val jfApi = Proxy.newProxyInstance(
            JellyfinApi::class.java.classLoader,
            arrayOf(JellyfinApi::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getResumeItems" -> JellyfinItemsResponse(resumeDtos, resumeDtos.size)
                "getNextUp" -> JellyfinItemsResponse(nextUpDtos, nextUpDtos.size)
                "getPersons" -> JellyfinItemsResponse(personDtos, personDtos.size)
                "getItemsByPerson" -> JellyfinItemsResponse(personCredits, personCredits.size)
                "searchItems" -> JellyfinItemsResponse()
                "getPlaylists" -> JellyfinItemsResponse(listOf(JellyfinItemDto(id = "pl-1", name = "Weekend", type = "Playlist", childCount = 3)), 1)
                "createPlaylist" -> {
                    createdPlaylists += "ok"
                    JellyfinCreatePlaylistResponse("pl-new")
                }
                "deleteItem" -> {
                    deletedIds += (args?.firstOrNull() as? String).orEmpty()
                    Unit
                }
                "getUserViews" -> JellyfinItemsResponse(
                    listOf(
                        JellyfinItemDto(id = "v1", name = "Movies", collectionType = "movies"),
                        JellyfinItemDto(id = "v2", name = "Music", collectionType = "music"),
                        JellyfinItemDto(id = "v3", name = "Home Videos", collectionType = "homevideos"),
                        JellyfinItemDto(id = "v4", name = "Photos", collectionType = "photos")
                    ),
                    4
                )
                else -> JellyfinItemsResponse()
            }
        } as JellyfinApi

        val seerrApi = Proxy.newProxyInstance(
            SeerrApi::class.java.classLoader,
            arrayOf(SeerrApi::class.java)
        ) { _, method, _ ->
            error("Seerr must not be called in Jellyfin-only mode (called ${method.name})")
        } as SeerrApi

        val prefs = SessionPreferences(
            testFile = tempFolder.newFile("phase2_test.preferences_pb"),
            json = Json { ignoreUnknownKeys = true }
        )
        prefs.saveServerUrls(jellyfinUrl = "https://jf.example", seerrUrl = "")
        val repo = UnifiedCatalogRepository(jfApi, seerrApi, prefs)
        val profile = UserProfile(
            id = "u1",
            name = "Alex",
            jellyfinUserId = "u1",
            jellyfinToken = "tok",
            mergeNextUpIntoContinueWatching = true
        )

        // 1. Video-only libraries exclude Music and Photos
        val views = repo.loadUserLibraryViews("u1")
        assertEquals(listOf("Movies", "Home Videos"), views.map { it.name })

        // 2. Continue Watching + Next Up merges and deduplicates ser-1 while keeping ser-2 with isNextUp=true
        val (mergedCw, splitNextUp) = repo.loadContinueWatchingAndNextUp("u1", profile)
        assertEquals(2, mergedCw.size)
        assertEquals("jf_ep-1", mergedCw[0].id)
        assertFalse(mergedCw[0].isNextUp)
        assertEquals("jf_ep-9", mergedCw[1].id)
        assertTrue(mergedCw[1].isNextUp)
        assertTrue(splitNextUp.isEmpty())

        // Separate Next Up mode
        val (onlyCw, separateNu) = repo.loadContinueWatchingAndNextUp("u1", profile.copy(mergeNextUpIntoContinueWatching = false))
        assertEquals(1, onlyCw.size)
        assertEquals(1, separateNu.size)
        assertEquals("jf_ep-9", separateNu.first().id)

        // 3. Jellyfin-native People Search & Credits without Seerr
        val (_, people) = repo.searchUnifiedWithPeople("u1", "Keanu", profile)
        assertEquals(1, people.size)
        assertEquals("person-keanu", people.first().jellyfinPersonId)

        val credits = repo.loadPersonCredits("u1", jellyfinPersonId = "person-keanu", personName = "Keanu Reeves", activeProfile = profile)
        assertEquals(1, credits.size)
        assertEquals("The Matrix", credits.first().title)
        assertEquals(MediaType.MOVIE, credits.first().mediaType)

        // 4. Playlists & Delete Item
        val playlists = repo.loadPlaylists("u1")
        assertEquals(1, playlists.size)
        assertEquals("Weekend", playlists.first().name)
        assertTrue(repo.createPlaylist("u1", "Favorites", listOf("mov-matrix")).isSuccess)
        assertEquals(1, createdPlaylists.size)

        assertTrue(repo.deleteItem("u1", "mov-matrix").isSuccess)
        assertEquals(listOf("mov-matrix"), deletedIds)
    }
}
