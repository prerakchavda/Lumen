package com.seerflix.tv.core.data

import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
import com.seerflix.tv.core.model.ArrQualityProfile
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CastMember
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.ContentRatingHelper
import com.seerflix.tv.core.model.ContentRatingLimit
import com.seerflix.tv.core.model.DvrTimerOptions
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.ExtrasItem
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.JellyfinLibraryView
import com.seerflix.tv.core.model.JellyfinServer
import com.seerflix.tv.core.model.MediaSourceOption
import com.seerflix.tv.core.model.ServerScopedId
import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.LiveTvProgram
import com.seerflix.tv.core.model.LibraryPage
import com.seerflix.tv.core.model.ScheduledRecording
import com.seerflix.tv.core.model.ScreensaverSlide
import com.seerflix.tv.core.model.MediaRequestSummary
import com.seerflix.tv.core.model.MediaStreamSpec
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.PlaylistSummary
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.TrackOption
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.seerflix.tv.core.network.CrossServerLinks
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinCreatePlaylistRequest
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinServers
import com.seerflix.tv.core.network.ServerSession
import com.seerflix.tv.core.network.scopedTo
import com.seerflix.tv.core.network.JellyfinMediaStreamDto
import com.seerflix.tv.core.network.JellyfinUpdateUserDataRequest
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrCreateIssueBody
import com.seerflix.tv.core.network.SeerrCreateRequestBody
import com.seerflix.tv.core.network.SeerrMediaInfoDto
import com.seerflix.tv.core.network.SeerrMediaResultDto
import com.seerflix.tv.core.network.SeerrMovieDetailsDto
import com.seerflix.tv.core.network.SeerrRelatedVideoDto
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.player.YouTubeUrlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedCatalogRepository @Inject constructor(
    private val jellyfinApi: JellyfinApi,
    private val seerrApi: SeerrApi,
    private val prefs: SessionPreferences,
    /** Disk copy of the library index; null (unit tests) keeps the index in memory only. */
    private val indexStore: LibraryIndexStore? = null,
    /** Trakt "most watched this week" charts; null (unit tests) = Seerr popularity only. */
    private val traktCharts: TraktCharts? = null,
    /**
     * The extra Jellyfin servers whose libraries are merged in; null (unit tests) or no extra
     * server configured = the home server only, exactly as before. [jellyfinApi] is always the
     * home server.
     */
    private val servers: JellyfinServers? = null,
    private val copies: CrossServerCopies? = null,
    /** "Connect Trakt" recommendations; null (unit tests) or not connected = no Trakt row. */
    private val traktAccount: TraktAccount? = null
) {
    init {
        // formatHttpError() is static; let it follow the active profile's language.
        uiLanguageSource = { prefs.cachedUiLanguage.ifBlank { "en" } }
    }

    /**
     * Snapshot of one Jellyfin user's owned Movies/Series plus the lookup maps derived from it.
     * Swapped atomically so readers never see a half-built index.
     */
    private class LibraryIndex(
        val userId: String,
        val items: List<JellyfinItemDto>,
        val fetchedAtMs: Long,
        /** false = placeholder after a failed first fetch for this user (not a usable copy). */
        val isUsable: Boolean,
        /** Which items are the same title on another server ([items] lists each title once). */
        val links: CrossServerLinks = CrossServerLinks.EMPTY
    ) {
        val movies: Map<Int, JellyfinItemDto>
        val series: Map<Int, JellyfinItemDto>
        val seriesTmdbByJellyfinId: Map<String, Int>
        /** Owned series by Jellyfin id, every server's copy included (episode rating/genre fallback). */
        val seriesById: Map<String, JellyfinItemDto>
        val byId: Map<String, JellyfinItemDto> by lazy(LazyThreadSafetyMode.PUBLICATION) { items.associateBy { it.id } }

        init {
            val m = HashMap<Int, JellyfinItemDto>(items.size)
            val s = HashMap<Int, JellyfinItemDto>(items.size)
            val byId = HashMap<String, JellyfinItemDto>()
            items.forEach { dto ->
                if (dto.type.equals("Series", ignoreCase = true)) {
                    links.copiesOf(dto.id).forEach { copyId -> byId.putIfAbsent(copyId, dto) }
                }
                val tmdb = dto.providerIds["Tmdb"]?.toIntOrNull()
                    ?: dto.providerIds["tmdb"]?.toIntOrNull()
                if (tmdb != null) {
                    when {
                        dto.type.equals("Movie", ignoreCase = true) -> m[tmdb] = dto
                        dto.type.equals("Series", ignoreCase = true) -> s[tmdb] = dto
                    }
                }
            }
            val bySeriesId = HashMap<String, Int>(s.size)
            s.forEach { (tmdb, dto) ->
                // Every copy of the series resolves to the same TMDB id.
                links.copiesOf(dto.id).forEach { copyId -> bySeriesId.putIfAbsent(copyId, tmdb) }
            }
            movies = m
            series = s
            seriesTmdbByJellyfinId = bySeriesId
            seriesById = byId
        }
    }

    @Volatile
    private var libraryIndex: LibraryIndex? = null

    /** The Jellyfin user the app most recently asked the index for; fetches for others never install. */
    @Volatile
    private var requestedIndexUserId: String? = null

    /**
     * The index, unless it belongs to another Jellyfin user than the one last asked for (right
     * after a profile switch, before the new user's copy is in): never match against another
     * user's library.
     */
    private val currentIndex: LibraryIndex?
        get() = libraryIndex?.takeIf { idx -> requestedIndexUserId.let { it == null || it == idx.userId } }

    private val tmdbToJellyfinMovie: Map<Int, JellyfinItemDto>
        get() = currentIndex?.movies.orEmpty()
    private val tmdbToJellyfinSeries: Map<Int, JellyfinItemDto>
        get() = currentIndex?.series.orEmpty()
    private val allLibraryItems: List<JellyfinItemDto>
        get() = currentIndex?.items.orEmpty()

    /** Index fetches outlive the (cancellable) caller that started them: others may be awaiting. */
    private val indexScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexLock = Any()
    private var indexFetch: Pair<String, Deferred<LibraryIndex?>>? = null
    private var backgroundIndexRefresh: Job? = null
    private val indexDiskMutex = Mutex()

    private data class CachedDetailsEntry(
        val item: UnifiedMediaItem,
        val timestampMs: Long
    )
    private val detailsCache = object : LinkedHashMap<String, CachedDetailsEntry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedDetailsEntry>?): Boolean {
            return size > 64
        }
    }

    private var cachedHas4kMovie: Boolean? = null
    private var cachedHas4kTv: Boolean? = null
    private var last4kCheckTimestampMs: Long = 0L

    @Synchronized
    private fun getCachedDetails(key: String): UnifiedMediaItem? {
        val entry = detailsCache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestampMs > 120_000L) {
            detailsCache.remove(key)
            return null
        }
        return entry.item
    }

    @Synchronized
    private fun putCachedDetails(key: String, item: UnifiedMediaItem) {
        detailsCache[key] = CachedDetailsEntry(item, System.currentTimeMillis())
    }

    @Synchronized
    private fun invalidateDetailsCache() {
        detailsCache.clear()
    }

    private val isSeerrEnabled: Boolean
        get() = prefs.isSeerrConfigured

    private val metaLang: String
        get() = prefs.cachedMetadataLanguage.ifBlank { "en" }

    private val watchRegion: String
        get() = java.util.Locale.getDefault().country
            .takeIf { it.length == 2 && it.all(Char::isLetter) }
            ?.uppercase(java.util.Locale.US)
            ?: "US"

    private val jfBaseUrl: String
        get() = prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }

    fun findJellyfinIdForTmdb(tmdbId: Int, mediaType: MediaType): String? {
        return when (mediaType) {
            MediaType.MOVIE -> tmdbToJellyfinMovie[tmdbId]?.id
            MediaType.SERIES, MediaType.EPISODE -> tmdbToJellyfinSeries[tmdbId]?.id
        }
    }

    /**
     * Whether [profile] may see the owned movie / series [jellyfinId], with the same rating check
     * as every row (e.g. a launcher "Ready" promotion made while another profile was active).
     * A restricted profile is not allowed a title the current library index doesn't know.
     */
    fun isLibraryTitleAllowed(jellyfinId: String, profile: UserProfile?): Boolean {
        if (profile == null || !profile.isRestricted) return true
        val dto = currentIndex?.items?.firstOrNull { it.id == jellyfinId } ?: return false
        return ContentRatingHelper.isAllowed(dto.officialRating, dto.genres, profile)
    }

    /**
     * The indexed parent series of an episode whose own rating or genres are missing: Jellyfin keeps
     * them on the series, and an unrated episode would be hidden from every restricted profile.
     */
    private fun seriesForRatingFallback(dto: JellyfinItemDto): JellyfinItemDto? {
        if (!dto.officialRating.isNullOrBlank() && dto.genres.isNotEmpty()) return null
        val seriesId = dto.seriesId?.takeIf { it.isNotBlank() } ?: return null
        return currentIndex?.seriesById?.get(seriesId)
    }

    /** TMDB id of an owned series given its Jellyfin item id (episodes only carry their own TMDB id). */
    private fun seriesTmdbIdFor(seriesId: String?): Int? {
        if (seriesId.isNullOrBlank()) return null
        return currentIndex?.seriesTmdbByJellyfinId?.get(seriesId)
    }

    /**
     * Makes sure [userId]'s library index is younger than the TTL, fetching it (and waiting) when it
     * is stale or missing; [forceRefresh] always refetches. Used by background pollers (request-ready
     * worker, Details download polling) that need an up-to-date view of what is in the library.
     * Screen loaders use [ensureLibraryIndex] instead, which serves a stale copy immediately and
     * revalidates in the background.
     *
     * The index is per Jellyfin user and NOT rating filtered, so [activeProfile] does not affect it;
     * the parameter is kept for source compatibility.
     */
    @Suppress("UNUSED_PARAMETER")
    suspend fun refreshLibraryIndex(
        userId: String,
        activeProfile: UserProfile? = null,
        forceRefresh: Boolean = false
    ) {
        ensureLibraryIndex(userId, if (forceRefresh) IndexPolicy.FORCE else IndexPolicy.BLOCK_IF_STALE)
    }

    private enum class IndexPolicy {
        /** Serve any usable copy now and refresh in the background when stale; block only with no copy. */
        STALE_WHILE_REVALIDATE,
        /** Block until a copy younger than the TTL exists. */
        BLOCK_IF_STALE,
        /** Always refetch (user pressed Refresh). */
        FORCE
    }

    // The index is deliberately NOT filtered by MaxOfficialRating: it is used to match Seerr
    // results to owned items. If restricted titles were dropped here, an R-rated movie that is
    // in the library would surface in discovery rows as an unrated, "requestable" Seerr item and
    // slip past the parental filter. Rating filtering is applied client-side to every row
    // (ContentRatingHelper) using the matched item's OfficialRating instead.
    private suspend fun ensureLibraryIndex(
        userId: String,
        policy: IndexPolicy = IndexPolicy.STALE_WHILE_REVALIDATE
    ) {
        requestedIndexUserId = userId
        val serverWithoutIndex = syncIndexWithServers(userId)
        if (policy == IndexPolicy.FORCE) {
            startIndexFetch(userId).await()
            return
        }
        val current = currentOrDiskIndex(userId)
        if (current == null) {
            // No copy at all for this user (first run, or a profile switch with no disk copy).
            startIndexFetch(userId).await()
            return
        }
        if (serverWithoutIndex && syncIndexWithServers(userId)) {
            // A server was just added (or signed in to) and nothing of it is cached yet: fetch
            // only the missing shares (not the whole home library again), and at most once per
            // cooldown per server, so a server whose library query keeps failing can't make every
            // screen load wait on it.
            fetchMissingExtraParts(userId)
        }
        if (System.currentTimeMillis() - current.fetchedAtMs < LIBRARY_INDEX_TTL_MS) return
        if (policy == IndexPolicy.BLOCK_IF_STALE) {
            startIndexFetch(userId).await()
        } else {
            scheduleBackgroundIndexRefresh(userId)
        }
    }

    /** One server's share of the index; its item ids are already server scoped. */
    private class IndexPart(val ownerKey: String, val items: List<JellyfinItemDto>, val fetchedAtMs: Long)

    /** The home server's share of the current index, kept so the other shares can be re-merged. */
    @Volatile
    private var homeIndexPart: IndexPart? = null
    private val extraIndexParts = java.util.concurrent.ConcurrentHashMap<String, IndexPart>()

    /** The extra servers the current index was merged for (ids + accounts). */
    @Volatile
    private var indexServersSignature: String = ""

    private fun extraPartKey(userId: String, session: ServerSession) = "$userId@${session.id}:${session.userId}"
    private fun extraStoreUser(userId: String, session: ServerSession) = "$userId@${session.id}"
    private fun extraStoreServer(session: ServerSession) = "${session.server.cacheKey}|${session.userId}"

    /**
     * The index for [userId]: the home server's titles plus those of every extra server that is
     * usable right now, each title once (see [CrossServerMerge]). A server that is offline or needs
     * a new sign-in is left out, so nothing that can't be played is offered.
     */
    private fun buildIndex(userId: String, home: IndexPart): LibraryIndex {
        val extraParts = servers?.availableExtras().orEmpty().mapNotNull { session ->
            extraIndexParts[session.id]?.takeIf { it.ownerKey == extraPartKey(userId, session) }?.items
        }
        if (extraParts.isEmpty()) return LibraryIndex(userId, home.items, home.fetchedAtMs, isUsable = true)
        val merged = CrossServerMerge.mergeLibrary(listOf(home.items) + extraParts)
        return LibraryIndex(userId, merged.items, home.fetchedAtMs, isUsable = true, links = merged.links)
    }

    /** Call with [indexLock] held. */
    private fun installIndex(index: LibraryIndex) {
        libraryIndex = index
        servers?.links = index.links
    }

    /**
     * Keeps the index in step with the extra servers: when one was added, removed, went offline or
     * came back, the cached shares are re-merged right away. Returns true when a usable server has
     * no share yet (so a fetch is needed before its titles can show up).
     */
    private fun syncIndexWithServers(userId: String): Boolean {
        val srv = servers ?: return false
        val sessions = srv.availableExtras()
        val signature = sessions.joinToString(",") { "${it.id}:${it.userId}" }
        if (signature != indexServersSignature) {
            synchronized(indexLock) {
                indexServersSignature = signature
                extraIndexParts.keys.retainAll(srv.signedInExtras().map { it.id }.toSet())
                val home = homeIndexPart
                val cur = libraryIndex
                if (home != null && home.ownerKey == userId && cur != null && cur.userId == userId && cur.isUsable) {
                    installIndex(buildIndex(userId, home))
                }
            }
        }
        return sessions.any { extraIndexParts[it.id]?.ownerKey != extraPartKey(userId, it) }
    }

    /** The usable in-memory index for [userId], else its disk copy (installed as current), else null. */
    private suspend fun currentOrDiskIndex(userId: String): LibraryIndex? {
        libraryIndex?.takeIf { it.userId == userId && it.isUsable }?.let { return it }
        val store = indexStore ?: return null
        return indexDiskMutex.withLock {
            libraryIndex?.takeIf { it.userId == userId && it.isUsable }?.let { return@withLock it }
            val snapshot = withContext(Dispatchers.IO) { store.read(userId, indexServerKey) }
                ?: return@withLock null
            val home = IndexPart(userId, snapshot.items.map { it.toItemDto() }, snapshot.fetchedAtMs)
            loadExtraPartsFromDisk(userId, store)
            synchronized(indexLock) {
                val loaded = buildIndex(userId, home)
                val cur = libraryIndex
                // Never replace a network copy for this user that landed meanwhile.
                if (requestedIndexUserId == userId && (cur == null || cur.userId != userId || !cur.isUsable)) {
                    homeIndexPart = home
                    installIndex(loaded)
                }
                loaded
            }
        }
    }

    /** Disk copies of the extra servers' shares, so a cold start shows the merged library at once. */
    private suspend fun loadExtraPartsFromDisk(userId: String, store: LibraryIndexStore) {
        val sessions = servers?.signedInExtras().orEmpty()
        for (session in sessions) {
            val key = extraPartKey(userId, session)
            if (extraIndexParts[session.id]?.ownerKey == key) continue
            val snapshot = withContext(Dispatchers.IO) {
                store.read(extraStoreUser(userId, session), extraStoreServer(session))
            } ?: continue
            extraIndexParts.putIfAbsent(
                session.id,
                IndexPart(key, snapshot.items.map { it.toItemDto().scopedTo(session.id) }, snapshot.fetchedAtMs)
            )
        }
    }

    /** Single in-flight fetch per user; concurrent callers share (and await) it. */
    private fun startIndexFetch(userId: String): Deferred<LibraryIndex?> = synchronized(indexLock) {
        val existing = indexFetch
        if (existing != null && existing.first == userId && existing.second.isActive) {
            return existing.second
        }
        val deferred = indexScope.async { fetchAndInstallIndex(userId) }
        indexFetch = userId to deferred
        deferred
    }

    private fun scheduleBackgroundIndexRefresh(userId: String) {
        synchronized(indexLock) {
            if (backgroundIndexRefresh?.isActive == true) return
            val existing = indexFetch
            if (existing != null && existing.first == userId && existing.second.isActive) return
            backgroundIndexRefresh = indexScope.launch {
                // Let the screen's own (small) requests go first on a slow device.
                delay(BACKGROUND_INDEX_REFRESH_DELAY_MS)
                if (requestedIndexUserId != userId) return@launch
                val cur = libraryIndex
                val refreshedMeanwhile = cur != null && cur.userId == userId && cur.isUsable &&
                    System.currentTimeMillis() - cur.fetchedAtMs < LIBRARY_INDEX_TTL_MS
                if (!refreshedMeanwhile) startIndexFetch(userId).await()
            }
        }
    }

    /** Every owned Movie / Series of one server's user, trimmed to what the index keeps. */
    private suspend fun fetchIndexEntries(api: JellyfinApi, apiUserId: String): List<LibraryIndexEntry> {
        val collected = ArrayList<LibraryIndexEntry>()
        var startIndex = 0
        while (collected.size < MAX_LIBRARY_INDEX_ITEMS) {
            val page = api.getLibraryItems(
                userId = apiUserId,
                fields = LIBRARY_INDEX_FIELDS,
                limit = LIBRARY_INDEX_PAGE_SIZE,
                startIndex = startIndex.takeIf { it > 0 },
                // Only "has a backdrop" is read; skip Primary/Logo/Thumb tags and their blurhashes.
                enableImages = true,
                imageTypeLimit = 1,
                enableImageTypes = "Backdrop",
                enableUserData = true,
                enableTotalRecordCount = false
            )
            // Trim each page right away so full DTOs never pile up for a big library.
            page.items.mapTo(collected) { it.toIndexEntry() }
            startIndex += page.items.size
            // Only a short page means "done". TotalRecordCount can't be trusted here: with
            // EnableTotalRecordCount=false Jellyfin reports it as the page size (1000), which
            // stopped paging after the newest 1000 items and dropped the rest of the library.
            if (page.items.isEmpty() || page.items.size < LIBRARY_INDEX_PAGE_SIZE) {
                break
            }
        }
        return collected
    }

    private suspend fun fetchAndInstallIndex(userId: String): LibraryIndex? {
        // The extra servers are fetched alongside the home server, each within its own time limit.
        val extraFetches = servers?.availableExtras().orEmpty().map { session ->
            indexScope.async { fetchExtraIndexPart(userId, session) }
        }
        val entries = runCatchingCancellable { fetchIndexEntries(jellyfinApi, userId) }.getOrNull()

        val now = System.currentTimeMillis()
        if (entries == null) {
            // Never keep serving another user's index after a profile switch, even if this fetch failed.
            synchronized(indexLock) {
                val cur = libraryIndex
                if (requestedIndexUserId == userId && (cur == null || cur.userId != userId)) {
                    homeIndexPart = null
                    installIndex(LibraryIndex(userId, emptyList(), now, isUsable = false))
                }
            }
            return null
        }
        val home = IndexPart(userId, entries.map { it.toItemDto() }, now)
        // Give the other servers a moment so the first merged view is complete, but never hold the
        // home library back for a slow one: its share is merged in when it arrives.
        if (extraFetches.isNotEmpty()) {
            withTimeoutOrNull(EXTRA_INDEX_WAIT_MS) { extraFetches.awaitAll() }
        }
        val fresh = synchronized(indexLock) {
            val built = buildIndex(userId, home)
            if (requestedIndexUserId == userId || libraryIndex?.userId == userId) {
                homeIndexPart = home
                installIndex(built)
            }
            built
        }
        indexStore?.write(userId, indexServerKey, now, entries)
        extraFetches.filterNot { it.isCompleted }.forEach { pending ->
            indexScope.launch { if (pending.await()) remergeIndex(userId) }
        }
        return fresh
    }

    /** Fetches one extra server's share; false when the server didn't answer in time. */
    private suspend fun fetchExtraIndexPart(userId: String, session: ServerSession): Boolean {
        val srv = servers ?: return false
        val entries = srv.callExtra(session, EXTRA_INDEX_FETCH_TIMEOUT_MS) { s ->
            fetchIndexEntries(s.api, s.userId)
        } ?: return false
        val now = System.currentTimeMillis()
        extraIndexParts[session.id] = IndexPart(
            extraPartKey(userId, session),
            entries.map { it.toItemDto().scopedTo(session.id) },
            now
        )
        indexStore?.write(extraStoreUser(userId, session), extraStoreServer(session), now, entries)
        return true
    }

    /** Last attempt to fetch an extra server's missing share, by server id. */
    private val extraPartAttemptAtMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private suspend fun fetchMissingExtraParts(userId: String) {
        val now = System.currentTimeMillis()
        val missing = servers?.availableExtras().orEmpty().filter { session ->
            extraIndexParts[session.id]?.ownerKey != extraPartKey(userId, session) &&
                now - (extraPartAttemptAtMs[session.id] ?: 0L) > EXTRA_PART_RETRY_COOLDOWN_MS
        }
        if (missing.isEmpty()) return
        missing.forEach { extraPartAttemptAtMs[it.id] = now }
        val fetches = missing.map { session -> indexScope.async { fetchExtraIndexPart(userId, session) } }
        // Same wait as a full fetch; a slower share is merged in when it arrives.
        val results = withTimeoutOrNull(EXTRA_INDEX_WAIT_MS) { fetches.awaitAll() }
        if (results?.any { it } == true || (results == null && fetches.any { it.isCompleted })) {
            remergeIndex(userId)
        }
        fetches.filterNot { it.isCompleted }.forEach { pending ->
            indexScope.launch { if (pending.await()) remergeIndex(userId) }
        }
    }

    /** Rebuilds the current index from the cached shares (a late share arrived). */
    private fun remergeIndex(userId: String) {
        synchronized(indexLock) {
            val home = homeIndexPart ?: return
            val cur = libraryIndex ?: return
            if (home.ownerKey == userId && cur.userId == userId && cur.isUsable) {
                installIndex(buildIndex(userId, home))
            }
        }
    }

    /** Ties a disk copy of the index to the configured Jellyfin server. */
    private val indexServerKey: String
        get() = prefs.cachedJellyfinUrl.trim().trimEnd('/')

    // ---------------------------------------------------------------------------------------
    // Several servers. Everything below works on server scoped ids (see ServerScopedId): the
    // home server's are plain, another server's carry its id, and each call is sent to the
    // server that owns the id with the raw id.
    // ---------------------------------------------------------------------------------------

    private fun homeSession(userId: String): ServerSession = ServerSession(HOME_SERVER, jellyfinApi, userId)

    /** The session that owns [scopedId]; null when the profile has no account on that server. */
    private fun sessionFor(scopedId: String, userId: String): ServerSession? =
        if (ServerScopedId.isHome(scopedId)) homeSession(userId) else servers?.sessionForItem(scopedId, userId)

    private fun requireSession(scopedId: String, userId: String): ServerSession =
        sessionFor(scopedId, userId) ?: error("Not signed in to the server this title is on")

    private val links: CrossServerLinks
        get() = libraryIndex?.links ?: CrossServerLinks.EMPTY

    private val hasExtraServers: Boolean
        get() = servers?.availableExtras()?.isNotEmpty() == true

    /** Best-effort call on [session]: null when it fails (an extra server is also time-boxed). */
    private suspend fun <T : Any> onServer(
        session: ServerSession,
        timeoutMs: Long = DETAILS_EXTRA_TIMEOUT_MS,
        block: suspend (ServerSession) -> T
    ): T? =
        if (session.isHome) runCatchingCancellable { block(session) }.getOrNull()
        else servers?.callExtra(session, timeoutMs, block)

    /**
     * [onServer] for a secondary part of a screen (a row, extra credits): an extra server that is
     * offline or backing off is skipped right away, any other is time-boxed like the merged rows.
     */
    private suspend fun <T : Any> onServerForRow(
        session: ServerSession,
        block: suspend (ServerSession) -> T
    ): T? {
        if (!session.isHome && servers?.isAvailable(session.id) == false) return null
        return onServer(session, ROW_EXTRA_TIMEOUT_MS, block)
    }

    /**
     * A list every server contributes to. With no extra server this is exactly [home] (result or
     * failure). Otherwise the extra servers are asked in parallel within [timeoutMs]; whatever came
     * back in time is merged with the home list (one entry per title, most recent watch state),
     * and a server that didn't answer is simply left out.
     */
    private suspend fun mergedItems(
        order: CrossServerMerge.Order = CrossServerMerge.Order.SERVER,
        timeoutMs: Long = ROW_EXTRA_TIMEOUT_MS,
        extra: suspend (ServerSession) -> List<JellyfinItemDto>,
        home: suspend () -> List<JellyfinItemDto>
    ): List<JellyfinItemDto> {
        val srv = servers
        if (srv == null || srv.availableExtras().isEmpty()) return home()
        return coroutineScope {
            val extrasDeferred = async { srv.fromExtras(timeoutMs) { s -> extra(s).scopedTo(s.id) } }
            val homeResult = runCatchingCancellable { home() }
            val extraLists = extrasDeferred.await().map { it.second }
            if (homeResult.isFailure && extraLists.all { it.isEmpty() }) homeResult.getOrThrow()
            CrossServerMerge.mergeItems(listOf(homeResult.getOrDefault(emptyList())) + extraLists, links, order)
        }
    }

    private suspend fun resumeDtos(userId: String, limit: Int): List<JellyfinItemDto> = mergedItems(
        order = CrossServerMerge.Order.LAST_PLAYED,
        extra = { s -> s.api.getResumeItems(userId = s.userId, limit = limit).items },
        home = { jellyfinApi.getResumeItems(userId = userId, limit = limit).items }
    ).take(limit)

    private suspend fun nextUpDtos(userId: String, limit: Int): List<JellyfinItemDto> = mergedItems(
        // Next Up episodes are unplayed (no play date), so LAST_PLAYED kept the home list first and
        // take(limit) cut every other server's entries.
        order = CrossServerMerge.Order.INTERLEAVE,
        extra = { s -> s.api.getNextUp(userId = s.userId, limit = limit).items },
        home = { jellyfinApi.getNextUp(userId = userId, limit = limit).items }
    ).take(limit)

    private suspend fun latestDtos(userId: String, limit: Int): List<JellyfinItemDto> {
        // DateCreated is only needed to interleave several servers' lists.
        if (!hasExtraServers) return jellyfinApi.getLatestItems(userId = userId, limit = limit)
        return mergedItems(
            order = CrossServerMerge.Order.DATE_CREATED,
            extra = { s -> s.api.getLatestItems(userId = s.userId, limit = limit, fields = LATEST_FIELDS_MERGED) },
            home = { jellyfinApi.getLatestItems(userId = userId, limit = limit, fields = LATEST_FIELDS_MERGED) }
        ).take(limit)
    }

    private suspend fun playedDtos(userId: String, limit: Int): List<JellyfinItemDto> = mergedItems(
        order = CrossServerMerge.Order.LAST_PLAYED,
        extra = { s -> s.api.getPlayedItems(userId = s.userId, limit = limit).items },
        home = { jellyfinApi.getPlayedItems(userId = userId, limit = limit).items }
    ).take(limit)

    private suspend fun favoriteDtos(userId: String): List<JellyfinItemDto> = mergedItems(
        extra = { s -> s.api.getFavoriteItems(userId = s.userId).items },
        home = { jellyfinApi.getFavoriteItems(userId = userId).items }
    )

    private suspend fun searchDtos(userId: String, query: String): List<JellyfinItemDto> = mergedItems(
        extra = { s -> s.api.searchItems(s.userId, query).items },
        home = { jellyfinApi.searchItems(userId, query).items }
    )

    private suspend fun setFavorite(userId: String, scopedId: String, favorite: Boolean) {
        val session = requireSession(scopedId, userId)
        if (favorite) session.api.addFavorite(session.userId, session.raw(scopedId))
        else session.api.removeFavorite(session.userId, session.raw(scopedId))
    }

    /** The same title / episode on the other servers (empty with a single server). */
    private suspend fun otherCopies(userId: String, scopedId: String): List<ItemCopy> =
        copies?.othersOf(scopedId, userId).orEmpty()

    /**
     * Parental gate for playback: whatever opened the player (Watch Next, a held deep link, a
     * collection dialog, "Play on" from a phone), a restricted profile may only play titles within
     * its rating limit. Episodes are judged by their series' rating, which is where Jellyfin keeps
     * it. Live TV channels have no rating and stay governed by the Live TV tab. Unrestricted
     * profiles skip the lookup entirely.
     */
    suspend fun isPlayableForProfile(itemId: String, profile: UserProfile?): Boolean {
        if (profile == null || profile.effectiveRatingLimit == ContentRatingLimit.ALL && !profile.kidsMode) return true
        val item = runCatchingCancellable { fetchItem(profile.jellyfinUserId, itemId) }.getOrNull() ?: return false
        if (item.type.equals("TvChannel", ignoreCase = true) || item.type.equals("Program", ignoreCase = true)) return true
        val series = item.seriesId?.takeIf { item.officialRating.isNullOrBlank() || item.genres.isEmpty() }?.let { sid ->
            runCatchingCancellable { fetchItem(profile.jellyfinUserId, sid) }.getOrNull()
        }
        val probe = UnifiedMediaItem(
            id = itemId,
            mediaType = MediaType.MOVIE,
            title = item.name,
            maturityRating = item.officialRating?.takeIf { it.isNotBlank() } ?: series?.officialRating,
            genres = item.genres.ifEmpty { series?.genres.orEmpty() }
        )
        return ContentRatingHelper.isAllowed(probe, profile)
    }

    /** One item from the server that owns it, ids scoped; null when it can't be loaded. */
    private suspend fun fetchItem(userId: String, scopedId: String): JellyfinItemDto? {
        val session = sessionFor(scopedId, userId) ?: return null
        return onServer(session) { s -> s.api.getItemDetails(s.userId, s.raw(scopedId)) }?.scopedTo(session.id)
    }

    /** Seasons of a series from every server that has it, merged by season number. */
    private suspend fun seasonsAcrossServers(userId: String, seriesId: String): List<JellyfinItemDto> = coroutineScope {
        val perServer = links.copiesOf(seriesId).map { copyId ->
            async {
                val session = sessionFor(copyId, userId) ?: return@async emptyList()
                onServer(session) { s -> s.api.getSeasons(s.raw(copyId), s.userId).items }
                    ?.scopedTo(session.id).orEmpty()
            }
        }.awaitAll()
        if (perServer.size == 1) perServer.first() else CrossServerMerge.mergeSeasons(perServer).map { it.item }
    }

    /**
     * Episodes of one season from every server that has the series, merged by episode number: an
     * episode missing on one server is filled in from another.
     */
    private suspend fun episodesAcrossServers(
        userId: String,
        seriesId: String,
        seasonId: String?,
        seasonNumber: Int
    ): List<JellyfinItemDto> = coroutineScope {
        val seriesCopies = links.copiesOf(seriesId)
        val perServer = seriesCopies.map { copyId ->
            async {
                val session = sessionFor(copyId, userId) ?: return@async emptyList()
                // A season id only means something on the server it came from; the other copies of
                // the series are asked for the season by its number.
                val ownSeasonId = seasonId?.takeIf { session.owns(it) }
                onServer(session) { s ->
                    // Without a season id, ask by number even on a single server: an unfiltered
                    // getEpisodes fetched every episode of the series just to keep one season.
                    if (ownSeasonId != null) {
                        s.api.getEpisodes(
                            seriesId = s.raw(copyId),
                            userId = s.userId,
                            seasonId = s.raw(ownSeasonId)
                        ).items
                    } else {
                        s.api.getEpisodesBySeasonNumber(
                            seriesId = s.raw(copyId),
                            userId = s.userId,
                            season = seasonNumber
                        ).items.filter { it.parentIndexNumber == seasonNumber }
                    }
                }?.scopedTo(session.id).orEmpty()
            }
        }.awaitAll()
        if (perServer.size == 1) perServer.first() else CrossServerMerge.mergeEpisodes(perServer).map { it.item }
    }

    /**
     * The Versions list for [item]: its own files, then the copies on other servers, each labelled
     * with its server. Media source ids are server scoped like item ids, so two servers' files can
     * never be mixed up.
     */
    private fun buildMediaVersions(item: JellyfinItemDto?, others: List<ItemCopy>): List<MediaSourceOption> {
        if (item == null) return emptyList()
        val ownServerId = ServerScopedId.serverOf(item.id)
        // Name the server whenever it isn't obvious: there is another copy, or this one isn't at home.
        val ownServerName = if (others.isNotEmpty() || ownServerId != ServerScopedId.HOME) {
            servers?.serverName(ownServerId)?.takeIf { it.isNotBlank() }
        } else null
        return crossServerVersions(ownServerId, ownServerName, item.mediaSources, others)
    }

    // Image URLs are built against the server that owns the item ([homeBase] is the home server's
    // base URL, already resolved by the caller).
    private fun imageBase(scopedId: String, homeBase: String): String =
        if (ServerScopedId.isHome(scopedId)) homeBase else servers?.baseUrlForItem(scopedId).orEmpty()

    private fun primaryImage(homeBase: String, scopedId: String, maxWidth: Int = 340): String? =
        MediaImageUrls.jellyfinPrimary(imageBase(scopedId, homeBase), ServerScopedId.rawOf(scopedId), maxWidth)

    private fun backdropImage(homeBase: String, scopedId: String): String? =
        MediaImageUrls.jellyfinBackdrop(imageBase(scopedId, homeBase), ServerScopedId.rawOf(scopedId))

    private fun logoImage(homeBase: String, scopedId: String): String? =
        MediaImageUrls.jellyfinLogo(imageBase(scopedId, homeBase), ServerScopedId.rawOf(scopedId))

    /**
     * The index carries no Overview (it is most of the payload). Rows built from index items
     * back-fill it for their first [max] items with one small by-id request.
     */
    private suspend fun withOverviews(
        userId: String,
        dtos: List<JellyfinItemDto>,
        max: Int = OVERVIEW_BACKFILL_LIMIT
    ): List<JellyfinItemDto> {
        val missingIds = dtos.asSequence()
            .filter { it.overview == null }
            .map { it.id }
            .distinct()
            .take(max)
            .toList()
        if (missingIds.isEmpty()) return dtos
        // One by-id request per server that owns some of the items.
        val overviews = coroutineScope {
            missingIds.groupBy { ServerScopedId.serverOf(it) }.map { (_, ids) ->
                async {
                    val session = sessionFor(ids.first(), userId) ?: return@async emptyMap()
                    onServer(session, ROW_EXTRA_TIMEOUT_MS) { s ->
                        s.api.getLibraryItems(
                            userId = s.userId,
                            fields = "Overview",
                            limit = ids.size,
                            ids = ids.joinToString(",") { s.raw(it) },
                            enableImages = false,
                            enableUserData = false,
                            enableTotalRecordCount = false
                        ).items.mapNotNull { dto -> dto.overview?.let { s.scope(dto.id) to it } }.toMap()
                    }.orEmpty()
                }
            }.awaitAll().fold(emptyMap<String, String>()) { acc, part -> if (acc.isEmpty()) part else acc + part }
        }
        if (overviews.isEmpty()) return dtos
        return dtos.map { dto -> overviews[dto.id]?.let { dto.copy(overview = it) } ?: dto }
    }

    /**
     * Rating gate on a raw index item, applied before a row built from the index is capped: the
     * parental filter on the finished row would otherwise leave a restricted profile with the few
     * allowed titles that happened to fall inside the cap.
     */
    private fun JellyfinItemDto.allowedFor(profile: UserProfile?): Boolean =
        ContentRatingHelper.isAllowed(officialRating, genres, profile)

    private fun matchesGenreLabel(dto: JellyfinItemDto, genre: GenreFilterOption): Boolean {
        val needle = genre.label.substringBefore(" ")
        return dto.genres.any { g -> g.contains(needle, ignoreCase = true) }
    }

    /** Same MediaType mapping as [mapJellyfinItem], without building the whole item. */
    private fun jellyfinMediaType(dto: JellyfinItemDto): MediaType = when {
        dto.type.equals("Movie", ignoreCase = true) -> MediaType.MOVIE
        dto.type.equals("Episode", ignoreCase = true) -> MediaType.EPISODE
        else -> MediaType.SERIES
    }

    suspend fun markWatched(userId: String, itemId: String, watched: Boolean): Result<Unit> = runCatchingCancellable {
        val session = requireSession(itemId, userId)
        if (watched) {
            session.api.markPlayed(session.userId, session.raw(itemId))
        } else {
            session.api.markUnplayed(session.userId, session.raw(itemId))
            // The watch state shown is the most recently played copy's, so a copy on another
            // server that is still marked played would bring the tick straight back.
            otherCopies(userId, itemId).forEach { copy ->
                runCatchingCancellable { copy.session.api.markUnplayed(copy.session.userId, copy.session.raw(copy.item.id)) }
            }
        }
        invalidateDetailsCache()
    }

    /** Marks a whole season (or its available episodes if no Jellyfin season id is present) watched/unwatched. */
    suspend fun markSeasonWatched(
        userId: String,
        jellyfinSeasonId: String?,
        episodeIds: List<String>,
        watched: Boolean
    ): Result<Unit> = runCatchingCancellable {
        // A series merged across servers can list episodes that only exist on another server; the
        // season id only covers its own server, so mark those episodes one by one instead.
        val seasonServer = ServerScopedId.serverOf(jellyfinSeasonId)
        val seasonCoversAll = !jellyfinSeasonId.isNullOrBlank() &&
            episodeIds.all { ServerScopedId.serverOf(it) == seasonServer }
        if (seasonCoversAll) {
            markWatched(userId, jellyfinSeasonId!!, watched).getOrThrow()
            // The shown state is the most recently played copy's, so the same season still played
            // on another server would bring every tick back: clear it there too.
            if (!watched) markSeasonUnplayedOnOtherServers(userId, jellyfinSeasonId)
        } else {
            coroutineScope {
                episodeIds.distinct()
                    .map { epId -> async { markWatched(userId, epId, watched).getOrThrow() } }
                    .awaitAll()
            }
        }
    }

    /** Best effort: marks the season numbered like [seasonId] unplayed on every other copy of its series. */
    private suspend fun markSeasonUnplayedOnOtherServers(userId: String, seasonId: String) {
        if (links.isEmpty) return // a single server: nothing to keep in step
        val season = fetchItem(userId, seasonId) ?: return
        val seriesId = season.seriesId ?: return
        val number = season.indexNumber ?: return
        val seasonServer = ServerScopedId.serverOf(seasonId)
        val others = links.copiesOf(seriesId).filter { ServerScopedId.serverOf(it) != seasonServer }
        if (others.isEmpty()) return
        coroutineScope {
            others.map { copyId ->
                async {
                    val session = sessionFor(copyId, userId) ?: return@async
                    onServerForRow(session) { s ->
                        val match = s.api.getSeasons(s.raw(copyId), s.userId).items
                            .firstOrNull { it.indexNumber == number } ?: return@onServerForRow Unit
                        s.api.markUnplayed(s.userId, match.id)
                        Unit
                    }
                }
            }.awaitAll()
        }
    }

    /** Deletes an item from Jellyfin (only surfaced in UI when Jellyfin reports CanDelete == true). */
    suspend fun deleteItem(userId: String, itemId: String): Result<Unit> = runCatchingCancellable {
        val session = requireSession(itemId, userId)
        session.api.deleteItem(session.raw(itemId))
        invalidateDetailsCache()
        _deletedItemIds.update { it + itemId }
    }

    private val _deletedItemIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    /** Titles deleted from the server in this session: screens still showing them drop the cards. */
    val deletedItemIds: kotlinx.coroutines.flow.StateFlow<Set<String>> = _deletedItemIds

    /**
     * Loads all video-capable Jellyfin libraries across the home server and any extra connected
     * servers. Explicitly excludes non-video libraries (music, photos, books) to keep Lumen video-focused.
     */
    suspend fun loadUserLibraryViews(userId: String): List<JellyfinLibraryView> = coroutineScope {
        val extrasDeferred = async {
            servers?.fromExtras(ROW_EXTRA_TIMEOUT_MS) { s ->
                s.api.getUserViews(s.userId).items
                    .filter { isVideoCapableLibrary(it.collectionType) }
                    .map { dto ->
                        JellyfinLibraryView(
                            id = s.scope(dto.id),
                            name = dto.name,
                            collectionType = dto.collectionType,
                            serverName = s.server.name.takeIf { it.isNotBlank() }
                        )
                    }
            }.orEmpty().flatMap { it.second }
        }
        val homeViews = runCatchingCancellable {
            jellyfinApi.getUserViews(userId).items
                .filter { isVideoCapableLibrary(it.collectionType) }
                .map { dto ->
                    JellyfinLibraryView(
                        id = dto.id,
                        name = dto.name,
                        collectionType = dto.collectionType
                    )
                }
        }.getOrDefault(emptyList())
        val extraViews = extrasDeferred.await()
        if (extraViews.isEmpty()) {
            homeViews
        } else {
            val homeName = prefs.cachedServers.firstOrNull { it.isHome }?.name?.takeIf { it.isNotBlank() }
            homeViews.map { it.copy(serverName = homeName) } + extraViews
        }
    }

    // ---- Playlists (Phase 2) ----

    suspend fun loadPlaylists(userId: String): List<PlaylistSummary> = coroutineScope {
        val dtos = runCatchingCancellable {
            jellyfinApi.getPlaylists(userId = userId).items
        }.getOrDefault(emptyList())
        val base = jfBaseUrl
        dtos.map { dto ->
            PlaylistSummary(
                id = dto.id,
                name = dto.name,
                itemCount = dto.childCount ?: 0,
                posterUrl = jellyfinPrimaryUrl(base, dto),
                backdropUrl = jellyfinBackdropUrl(base, dto),
                overview = dto.overview.orEmpty()
            )
        }
    }

    suspend fun loadPlaylistItems(
        userId: String,
        playlistId: String,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> {
        val session = requireSession(playlistId, userId)
        // Time-boxed on another server: a slow or offline one mustn't hang the screen.
        val items = onServer(session) { s ->
            s.api.getPlaylistItems(
                playlistId = s.raw(playlistId),
                userId = s.userId
            ).items
        }.orEmpty().scopedTo(session.id).map { mapJellyfinItem(it) }
        return ContentRatingHelper.filterForProfile(items, activeProfile)
    }

    suspend fun createPlaylist(
        userId: String,
        name: String,
        initialItemIds: List<String> = emptyList()
    ): Result<String> = runCatchingCancellable {
        // Playlists live on the home server: use each title's home copy.
        val rawIds = initialItemIds
            .mapNotNull { id -> links.copiesOf(id).firstOrNull { ServerScopedId.isHome(it) } }
            .map { ServerScopedId.rawOf(it) }
            .distinct()
        if (initialItemIds.isNotEmpty() && rawIds.isEmpty()) throw PlaylistServerMismatchException()
        val resp = jellyfinApi.createPlaylist(
            JellyfinCreatePlaylistRequest(
                name = name.trim(),
                ids = rawIds,
                userId = userId,
                mediaType = "Video"
            )
        )
        resp.id
    }

    suspend fun addToPlaylist(
        userId: String,
        playlistId: String,
        itemIds: List<String>
    ): Result<Unit> = runCatchingCancellable {
        val session = requireSession(playlistId, userId)
        // An id from another server means nothing to the playlist's server: use that server's
        // copy of the title, and fail loudly instead of a silent no-op when it has none.
        val rawIds = itemIds
            .mapNotNull { id -> links.copiesOf(id).firstOrNull { session.owns(it) } }
            .map { session.raw(it) }
            .filter { it.isNotBlank() }
            .distinct()
        if (rawIds.isEmpty()) {
            if (itemIds.isNotEmpty()) throw PlaylistServerMismatchException()
            return@runCatchingCancellable
        }
        session.api.addItemsToPlaylist(
            playlistId = session.raw(playlistId),
            ids = rawIds.joinToString(","),
            userId = session.userId
        )
    }

    suspend fun removeFromPlaylist(
        userId: String,
        playlistId: String,
        entryIds: List<String>
    ): Result<Unit> = runCatchingCancellable {
        val session = requireSession(playlistId, userId)
        val cleanIds = entryIds.filter { it.isNotBlank() }
        if (cleanIds.isEmpty()) return@runCatchingCancellable
        session.api.removeItemsFromPlaylist(
            playlistId = session.raw(playlistId),
            entryIds = cleanIds.joinToString(",")
        )
    }

    suspend fun toggleMyList(userId: String, item: UnifiedMediaItem): Result<Boolean> = runCatchingCancellable {
        invalidateDetailsCache()
        if (item.jellyfinId != null) {
            val newState = !item.isInMyList
            setFavorite(userId, item.jellyfinId, newState)
            if (!newState) {
                // A favourite on any server keeps the title in My List: clear the other copies too.
                otherCopies(userId, item.jellyfinId).forEach { copy ->
                    runCatchingCancellable { setFavorite(userId, copy.item.id, false) }
                }
            }
            item.tmdbId?.let { tmdb ->
                prefs.removeFromLocalMyList(userId, tmdb, item.mediaType)
            }
            newState
        } else {
            prefs.toggleLocalMyList(userId, item)
        }
    }

    suspend fun toggleReminder(userId: String, item: UnifiedMediaItem): Boolean {
        val tmdbId = item.tmdbId ?: return false
        invalidateDetailsCache()
        return prefs.toggleReminder(userId, tmdbId, item.mediaType)
    }

    suspend fun removeFromContinueWatching(userId: String, itemId: String): Result<Unit> = runCatchingCancellable {
        clearResumePosition(requireSession(itemId, userId), itemId)
        // In progress on another server too? Clear it there as well, or it comes right back.
        otherCopies(userId, itemId).forEach { copy ->
            runCatchingCancellable { clearResumePosition(copy.session, copy.item.id) }
        }
        invalidateDetailsCache()
    }

    private suspend fun clearResumePosition(session: ServerSession, scopedId: String) {
        val raw = session.raw(scopedId)
        val updated = runCatchingCancellable {
            session.api.updateUserData(
                userId = session.userId,
                itemId = raw,
                body = JellyfinUpdateUserDataRequest(playbackPositionTicks = 0L)
            )
        }
        if (updated.isFailure) {
            session.api.markPlayed(session.userId, raw)
            session.api.markUnplayed(session.userId, raw)
        }
    }

    suspend fun loadMyList(userId: String): List<UnifiedMediaItem> = coroutineScope {
        val jfFavsDeferred = async {
            runCatchingCancellable { favoriteDtos(userId) }.getOrDefault(emptyList())
        }
        ensureLibraryIndex(userId)
        val localEntries = prefs.getLocalMyList(userId)
        val remainingLocal = mutableListOf<UnifiedMediaItem>()
        val promoted = mutableListOf<UnifiedMediaItem>()

        for (entry in localEntries) {
            val matchedJf = if (entry.mediaType == MediaType.MOVIE) {
                tmdbToJellyfinMovie[entry.tmdbId]
            } else {
                tmdbToJellyfinSeries[entry.tmdbId]
            }
            if (matchedJf != null) {
                // Auto-promote local My List item to Jellyfin Favorites now that it's in the library.
                // Only drop the local entry once the server accepted it, or a transient failure
                // would silently lose the title from My List.
                val promotedOk = runCatchingCancellable { setFavorite(userId, matchedJf.id, true) }.isSuccess
                if (promotedOk) {
                    prefs.removeFromLocalMyList(userId, entry.tmdbId, entry.mediaType)
                    // The favourites fetch may have started before this promotion landed.
                    promoted += mapJellyfinItem(matchedJf).copy(isInMyList = true)
                } else {
                    remainingLocal += entry.toUnifiedMediaItem()
                }
            } else {
                remainingLocal += entry.toUnifiedMediaItem()
            }
        }

        val fetchedFavs = jfFavsDeferred.await().map { mapJellyfinItem(it).copy(isInMyList = true) }
        val fetchedIds = fetchedFavs.mapTo(HashSet()) { it.id }
        val jfFavs = fetchedFavs + promoted.filter { it.id !in fetchedIds }
        val seenTmdb = jfFavs.mapNotNull { it.tmdbId?.let { id -> "${it.mediaType}_$id" } }.toSet()
        val dedupedLocal = remainingLocal.filterNot {
            it.tmdbId != null && "${it.mediaType}_${it.tmdbId}" in seenTmdb
        }
        jfFavs + dedupedLocal
    }

    /**
     * Lightweight re-fetch of Continue Watching + Next Up items, used when Home comes back into
     * view after playback or on `ServerEvent.UserDataChanged`.
     *
     * Returns `(continueWatchingItems, separateNextUpItems)`. When
     * [UserProfile.mergeNextUpIntoContinueWatching] is true (default), deduplicated Next Up
     * episodes are appended to `continueWatchingItems` and `separateNextUpItems` is empty.
     */
    suspend fun loadContinueWatchingAndNextUp(
        userId: String,
        activeProfile: UserProfile?
    ): Pair<List<UnifiedMediaItem>, List<UnifiedMediaItem>> = coroutineScope {
        val resumeDeferred = async {
            runCatchingCancellable { resumeDtos(userId, limit = 15) }.getOrDefault(emptyList())
        }
        val nextUpDeferred = async {
            runCatchingCancellable { nextUpDtos(userId, limit = 15) }.getOrDefault(emptyList())
        }
        // Episodes are rated through their series in the index (see seriesForRatingFallback).
        ensureLibraryIndex(userId)
        val rawResume = resumeDeferred.await()
        val rawNextUp = CrossServerMerge.dedupeNextUpAgainstResume(
            resumeItems = rawResume,
            nextUpItems = nextUpDeferred.await(),
            links = links
        )
        val resumeItems = ContentRatingHelper.filterForProfile(rawResume.map { mapJellyfinItem(it) }, activeProfile)
        val nextUpItems = ContentRatingHelper.filterForProfile(
            rawNextUp.map { mapJellyfinItem(it, isNextUp = true) },
            activeProfile
        )
        val mergeNextUp = activeProfile?.mergeNextUpIntoContinueWatching ?: true
        if (mergeNextUp) {
            (resumeItems + nextUpItems) to emptyList()
        } else {
            resumeItems to nextUpItems
        }
    }

    /** Backwards-compatible wrapper returning the Continue Watching list (including merged Next Up when enabled). */
    suspend fun loadContinueWatchingItems(userId: String, activeProfile: UserProfile?): List<UnifiedMediaItem> =
        loadContinueWatchingAndNextUp(userId, activeProfile).first

    suspend fun loadHomeRows(
        userId: String,
        filterType: MediaType? = null,
        genreFilter: GenreFilterOption? = null,
        availabilityFilter: AvailabilityFilter = AvailabilityFilter.ALL,
        today: LocalDate = LocalDate.now(),
        activeProfile: UserProfile? = null,
        forceRefresh: Boolean = false
    ): List<CatalogRow> = coroutineScope {
        val indexPolicy = if (forceRefresh) IndexPolicy.FORCE else IndexPolicy.STALE_WHILE_REVALIDATE

        // If a specific genre chip is selected on TV Shows / Movies, return genre-focused Top 10 + discovery rows
        if (genreFilter != null && (genreFilter.movieGenreId != null || genreFilter.tvGenreId != null)) {
            ensureLibraryIndex(userId, indexPolicy)
            return@coroutineScope loadGenreFilteredRows(
                userId = userId,
                filterType = filterType,
                genre = genreFilter,
                availabilityFilter = availabilityFilter,
                activeProfile = activeProfile,
                today = today
            )
        }

        val seerrActive = isSeerrEnabled
        // Raw fetches that don't read the library index start first, so a blocking index fetch
        // (first run / Refresh) overlaps them instead of delaying every request.
        val resumeDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { resumeDtos(userId, limit = 15) }.getOrDefault(emptyList())
        }
        val nextUpDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE || filterType == MediaType.MOVIE) emptyList()
            else runCatchingCancellable { nextUpDtos(userId, limit = 15) }.getOrDefault(emptyList())
        }
        val latestDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { latestDtos(userId, limit = 20) }.getOrDefault(emptyList())
        }
        val playedDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { playedDtos(userId, limit = 20) }.getOrDefault(emptyList())
        }
        val trendingDeferred = async {
            if (!seerrActive) emptyList()
            else runCatchingCancellable { seerrApi.getTrending(language = metaLang).results }.getOrDefault(emptyList())
        }
        val collectionsDeferred = async {
            if ((filterType == null || filterType == MediaType.MOVIE) && availabilityFilter != AvailabilityFilter.REQUESTABLE) {
                loadBoxSetCollections(userId)
            } else emptyList()
        }
        val upcomingTvDeferred = async {
            if (seerrActive && filterType != MediaType.MOVIE) {
                runCatchingCancellable { seerrApi.getUpcomingTv(language = metaLang).results }.getOrDefault(emptyList())
            } else emptyList()
        }
        val pinnedLibraryRowsDeferred = async {
            val pinnedIds = activeProfile?.pinnedLibraryIds.orEmpty()
            if (pinnedIds.isEmpty() || availabilityFilter == AvailabilityFilter.REQUESTABLE) {
                emptyList()
            } else {
                val views = loadUserLibraryViews(userId).associateBy { it.id }
                val maxRating = activeProfile?.effectiveRatingLimit?.jellyfinMaxOfficialRating
                pinnedIds.map { libId ->
                    async {
                        val view = views[libId]
                        val session = sessionFor(libId, userId) ?: return@async null
                        val includeTypes = includeTypesForCollectionType(view?.collectionType, folderMode = false)
                        // A library on another server must not hold up Home (or wait on a dead server).
                        val libItems = onServerForRow(session) { s ->
                            s.api.getLibraryItems(
                                userId = s.userId,
                                parentId = s.raw(libId),
                                includeItemTypes = includeTypes,
                                limit = 30,
                                maxOfficialRating = maxRating
                            ).items
                        }.orEmpty().scopedTo(session.id).map { mapJellyfinItem(it) }
                            .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
                        if (libItems.isEmpty()) null
                        else CatalogRow(
                            id = "lib_$libId",
                            title = view?.displayName ?: "Library",
                            style = RowStyle.POSTER,
                            items = libItems
                        )
                    }
                }.mapNotNull { it.await() }
            }
        }

        ensureLibraryIndex(userId, indexPolicy)

        val myListDeferred = async {
            runCatchingCancellable { loadMyList(userId) }.getOrDefault(emptyList())
        }
        val resumeItemsDeferred = async {
            val rawResume = resumeDeferred.await()
            val mappedResume = rawResume
                .map { mapJellyfinItem(it) }
                .filter {
                    (filterType == null ||
                        (filterType == MediaType.MOVIE && it.mediaType == MediaType.MOVIE) ||
                        (filterType == MediaType.SERIES && it.mediaType != MediaType.MOVIE)) &&
                        availabilityFilter.matches(it)
                }
            mappedResume
        }
        val nextUpItemsDeferred = async {
            if (filterType == MediaType.MOVIE) return@async emptyList<UnifiedMediaItem>()
            val rawResume = resumeDeferred.await()
            val deduped = CrossServerMerge.dedupeNextUpAgainstResume(
                resumeItems = rawResume,
                nextUpItems = nextUpDeferred.await(),
                links = links
            )
            deduped.map { mapJellyfinItem(it, isNextUp = true) }
                .filter { availabilityFilter.matches(it) }
        }
        // Personalized discovery: seeds from the whole recent history (not just the last title),
        // merged into "Top Picks for <profile>", several "Because you watched" rows, library
        // rows, a person row and the household row.
        val taste = runCatchingCancellable { tasteFor(userId) }.getOrDefault(TasteData())
        val discoveryDeferred = async {
            runCatchingCancellable {
                buildPersonalDiscovery(
                    userId = userId,
                    watchingRaw = resumeDeferred.await() + nextUpDeferred.await(),
                    playedRaw = playedDeferred.await(),
                    favorites = myListDeferred.await(),
                    taste = taste,
                    activeProfile = activeProfile
                )
            }.getOrNull()
        }
        val becauseRowsDeferred = async {
            val discovery = discoveryDeferred.await() ?: return@async emptyList<CatalogRow>()
            becauseYouWatchedRows(discovery, taste, filterType, availabilityFilter)
        }
        val libraryDiscoveryDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) return@async emptyMap<String, List<UnifiedMediaItem>>()
            val affinity = discoveryDeferred.await()?.affinity.orEmpty()
            runCatchingCancellable { libraryDiscoveryRows(userId, filterType, affinity, today, taste) }
                .getOrDefault(emptyMap())
                .mapValues { (_, items) -> items.filter(availabilityFilter::matches) }
        }
        val personRowDeferred = async {
            if (filterType != null || availabilityFilter == AvailabilityFilter.REQUESTABLE) return@async null
            val discovery = discoveryDeferred.await() ?: return@async null
            runCatchingCancellable { favoritePersonRow(userId, discovery, filterType, activeProfile) }.getOrNull()
        }
        val traktRecsDeferred = async {
            runCatchingCancellable { traktRecommendedItems(userId, filterType, today) }.getOrDefault(emptyList())
                .filter { availabilityFilter.matches(it) && !it.isWatched }
        }
        val householdDeferred = async {
            if (filterType != null || availabilityFilter == AvailabilityFilter.REQUESTABLE) return@async emptyList()
            val nowMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + DAY_MS
            runCatchingCancellable { householdPopularItems(userId, activeProfile, nowMs) }.getOrDefault(emptyList())
        }
        val topMoviesNowDeferred = async {
            if (seerrActive && (filterType == null || filterType == MediaType.MOVIE)) {
                fetchTop10MoviesOutNow(today, availabilityFilter)
            } else emptyList()
        }
        val topTvAiringNowDeferred = async {
            if (seerrActive && (filterType == null || filterType == MediaType.SERIES)) {
                fetchTop10TvAiringNow(today, availabilityFilter)
            } else emptyList()
        }
        val homeReleaseMoviesDeferred = async {
            if (seerrActive && (filterType == null || filterType == MediaType.MOVIE)) {
                loadHomeReleaseMovieRows(today, availabilityFilter)
            } else HomeReleaseMovieRows.EMPTY
        }
        val topNewTvPremieresDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchTop10NewSeriesPremieres(today, availabilityFilter)
            } else emptyList()
        }

        // Home curated editorial rows
        val gemsForYouDeferred = async {
            if (filterType != null) emptyList()
            else if (seerrActive) fetchGemsForYou(today, availabilityFilter)
            else if (availabilityFilter != AvailabilityFilter.REQUESTABLE) {
                val topRated = allLibraryItems
                    .filter { (it.communityRating ?: 0.0) >= 7.4 && it.allowedFor(activeProfile) }
                    .sortedByDescending { it.communityRating ?: 0.0 }
                    .take(20)
                withOverviews(userId, topRated).map { mapJellyfinItem(it) }.filter(availabilityFilter::matches)
            } else emptyList()
        }
        val awardWinningTvDeferred = async {
            if (seerrActive && filterType == null) {
                fetchCuratedTv(
                    cacheTag = "award_tv",
                    today = today,
                    availabilityFilter = availabilityFilter,
                    sortBy = "vote_average.desc",
                    voteCountGte = 250,
                    voteAverageGte = 7.6
                )
            } else emptyList()
        }
        val seasonalSpec = seasonalRowSpec(today)
        val seasonalDeferred = async {
            if (seerrActive && filterType == null) {
                fetchSeasonalRow(seasonalSpec, today, availabilityFilter)
            } else emptyList()
        }

        // Movies curated editorial & genre poster rows
        val needAGoodLaughDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies(
                    cacheTag = "good_laugh",
                    today = today,
                    availabilityFilter = availabilityFilter,
                    genre = "35",
                    sortBy = "vote_average.desc",
                    voteCountGte = 300,
                    voteAverageGte = 6.8
                )
            } else emptyList()
        }
        val animationCriticsDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies(
                    cacheTag = "anim_critics",
                    today = today,
                    availabilityFilter = availabilityFilter,
                    genre = "16",
                    sortBy = "vote_average.desc",
                    voteCountGte = 250,
                    voteAverageGte = 7.4
                )
            } else emptyList()
        }
        val blockbusterMoviesDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies(
                    cacheTag = "blockbusters",
                    today = today,
                    availabilityFilter = availabilityFilter,
                    sortBy = "popularity.desc",
                    voteCountGte = 1500
                )
            } else emptyList()
        }
        val movieComediesDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies("m_comedy", today, availabilityFilter, genre = "35")
            } else emptyList()
        }
        val movieActionDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies("m_action", today, availabilityFilter, genre = "28")
            } else emptyList()
        }
        val movieHorrorDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies("m_horror", today, availabilityFilter, genre = "27")
            } else emptyList()
        }
        val movieDramaDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies("m_drama", today, availabilityFilter, genre = "18")
            } else emptyList()
        }
        val movieFamilyAnimationDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies("m_family_anim", today, availabilityFilter, genre = "16,10751")
            } else emptyList()
        }
        val movieSciFiDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchCuratedMovies("m_scifi", today, availabilityFilter, genre = "878")
            } else emptyList()
        }

        // TV Shows curated editorial & genre poster rows
        val awardWinningTvDramasDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchCuratedTv(
                    cacheTag = "award_tv_dramas",
                    today = today,
                    availabilityFilter = availabilityFilter,
                    genre = "18",
                    sortBy = "vote_average.desc",
                    voteCountGte = 200,
                    voteAverageGte = 7.6
                )
            } else emptyList()
        }
        val thirtyMinuteHitsDeferred = async {
            if (filterType != MediaType.SERIES) emptyList()
            else if (seerrActive) {
                fetchCuratedTv(
                    cacheTag = "tv_30min",
                    today = today,
                    availabilityFilter = availabilityFilter,
                    genre = "35",
                    sortBy = "popularity.desc",
                    voteCountGte = 80,
                    withRuntimeLte = 35
                )
            } else if (availabilityFilter != AvailabilityFilter.REQUESTABLE) {
                val quickShows = allLibraryItems.filter { dto ->
                    dto.type.equals("Series", ignoreCase = true) &&
                        (dto.runTimeTicks ?: 0L) in 1L..(35L * 600_000_000L) &&
                        dto.allowedFor(activeProfile)
                }.take(20)
                withOverviews(userId, quickShows).map { mapJellyfinItem(it) }.filter(availabilityFilter::matches)
            } else emptyList()
        }
        val imaginativeTvDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchCuratedTv("tv_imaginative", today, availabilityFilter, genre = "10765")
            } else emptyList()
        }
        val familiarTvFavoritesDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchCuratedTv(
                    cacheTag = "tv_familiar",
                    today = today,
                    availabilityFilter = availabilityFilter,
                    sortBy = "popularity.desc",
                    voteCountGte = 500
                )
            } else emptyList()
        }
        val tvComediesDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchCuratedTv("tv_comedy", today, availabilityFilter, genre = "35")
            } else emptyList()
        }
        val tvRealityDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchCuratedTv("tv_reality", today, availabilityFilter, genre = "10764", voteCountGte = 5)
            } else emptyList()
        }
        val tvCrimeDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchCuratedTv("tv_crime", today, availabilityFilter, genre = "80")
            } else emptyList()
        }

        val readyToWatchDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else loadReadyToWatchAlerts(userId)
        }
        val airingThisWeekDeferred = async {
            if (filterType != MediaType.MOVIE && availabilityFilter != AvailabilityFilter.REQUESTABLE) {
                loadAiringThisWeek(userId, today)
            } else emptyList()
        }
        // In Jellyfin-Only Mode, surface Movies, TV Series, and genre rows from the library
        val jellyfinLibraryRowsDeferred = async {
            if (seerrActive || availabilityFilter == AvailabilityFilter.REQUESTABLE) {
                return@async emptyList<CatalogRow>()
            }
            val wantMovies = filterType == null || filterType == MediaType.MOVIE
            val wantSeries = filterType == null || filterType == MediaType.SERIES
            val index = allLibraryItems
            val movieDtos = if (wantMovies) {
                index.asSequence().filter { it.type.equals("Movie", ignoreCase = true) && it.allowedFor(activeProfile) }.take(25).toList()
            } else emptyList()
            val seriesDtos = if (wantSeries) {
                index.asSequence().filter { it.type.equals("Series", ignoreCase = true) && it.allowedFor(activeProfile) }.take(25).toList()
            } else emptyList()
            val genreRows = runCatchingCancellable {
                libraryGenreRows(userId, filterType, discoveryDeferred.await()?.affinity.orEmpty())
            }.getOrDefault(emptyList())
            val withText = withOverviews(userId, movieDtos + seriesDtos)
            val movies = withText.take(movieDtos.size).map { mapJellyfinItem(it) }.filter(availabilityFilter::matches)
            val series = withText.drop(movieDtos.size).map { mapJellyfinItem(it) }.filter(availabilityFilter::matches)
            buildList {
                if (movies.isNotEmpty()) {
                    add(CatalogRow(id = "jellyfin_movies", title = "Movies in Your Library", style = RowStyle.POSTER, items = movies))
                }
                if (series.isNotEmpty()) {
                    add(CatalogRow(id = "jellyfin_series", title = "TV Shows in Your Library", style = RowStyle.POSTER, items = series))
                }
                addAll(genreRows)
            }
        }

        val rows = mutableListOf<CatalogRow>()
        val regionSuffix = regionTodaySuffix(watchRegion)
        val cwTitle = continueWatchingTitle(activeProfile)

        val readyToWatch = readyToWatchDeferred.await()
            .filter {
                (filterType == null || it.mediaType == filterType) &&
                    availabilityFilter.matches(it)
            }
        if (readyToWatch.isNotEmpty()) {
            rows += CatalogRow(
                id = "ready_to_watch",
                title = "Ready to Watch • Your Reminders",
                style = RowStyle.POSTER,
                items = readyToWatch
            )
        }

        val resumeItems = resumeItemsDeferred.await()
        val nextUpItems = nextUpItemsDeferred.await()
        val mergeNextUp = activeProfile?.mergeNextUpIntoContinueWatching ?: true
        val continueWatchingItems = if (mergeNextUp) resumeItems + nextUpItems else resumeItems
        val separateNextUpItems = if (mergeNextUp) emptyList() else nextUpItems
        val myListItems = myListDeferred.await()
            .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
        val top10MoviesNow = topMoviesNowDeferred.await()
        val top10TvNow = topTvAiringNowDeferred.await()
        val homeReleaseMovies = homeReleaseMoviesDeferred.await()
        val discovery = discoveryDeferred.await()
        val becauseRows = becauseRowsDeferred.await()
        val libraryRows = libraryDiscoveryDeferred.await()
        val airingThisWeek = airingThisWeekDeferred.await().filter(availabilityFilter::matches)
        val latest = latestDeferred.await()
            .map { mapJellyfinItem(it) }
            .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
        val newPremieres = topNewTvPremieresDeferred.await()

        val trendingRaw = trendingDeferred.await()
            .mapNotNull { res ->
                val type = when (res.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> null
                } ?: return@mapNotNull null
                if (filterType != null && type != filterType) return@mapNotNull null
                mapSeerrResult(res, type, today)
            }
            .filter { isReleasedOnOrBefore(it.releaseDate, today) }
            .filter(availabilityFilter::matches)
        val trending = filterMoviesAvailableAtHome(trendingRaw, today)

        // "Top Picks for <profile>": the merged multi-title ranking first, topped up with what's
        // trending when the history is still thin.
        val personalPicks = discovery?.topPicks.orEmpty()
            .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
        val todaysTopPicks = buildList {
            addAll(personalPicks.take(20))
            if (size < 12) {
                addAll(trending)
                if (filterType == MediaType.MOVIE) addAll(top10MoviesNow)
                if (filterType == MediaType.SERIES) addAll(top10TvNow)
            }
        }.filterNot { taste.isNotInterested(it) || taste.ratingFor(it) == TasteRating.DISLIKE }
            .distinctBy { "${it.mediaType}_${it.tmdbId ?: it.id}" }
            .take(20)
        val profileFirstName = activeProfile?.name?.trim()?.takeIf { it.isNotEmpty() }
        val topPicksTitle = if (personalPicks.size >= MIN_DISCOVERY_ROW_ITEMS && profileFirstName != null) {
            TOP_PICKS_FOR_PREFIX + profileFirstName
        } else "Today's Top Picks for You"
        fun libraryRow(id: String, title: String): CatalogRow? =
            libraryRows[id]?.takeIf { it.isNotEmpty() }?.let { CatalogRow(id, title, RowStyle.POSTER, it) }

        when (filterType) {
            null -> {
                // ---- HOME TAB ----
                if (continueWatchingItems.isNotEmpty()) {
                    rows += CatalogRow("continue_watching", cwTitle, RowStyle.CONTINUE_WATCHING, continueWatchingItems)
                }
                if (separateNextUpItems.isNotEmpty()) {
                    rows += CatalogRow("next_up", "Next Up", RowStyle.CONTINUE_WATCHING, separateNextUpItems)
                }
                val gems = gemsForYouDeferred.await()
                if (gems.isNotEmpty()) {
                    rows += CatalogRow("gems_for_you", "Gems for You", RowStyle.POSTER, gems)
                }
                if (todaysTopPicks.isNotEmpty()) {
                    rows += CatalogRow("todays_top_picks", topPicksTitle, RowStyle.POSTER, todaysTopPicks)
                }
                becauseRows.getOrNull(0)?.let { rows += it }
                val traktRecs = traktRecsDeferred.await()
                if (traktRecs.size >= MIN_DISCOVERY_ROW_ITEMS) {
                    rows += CatalogRow("trakt_recommended", "Recommended for You on Trakt", RowStyle.POSTER, traktRecs)
                }
                val awardTv = awardWinningTvDeferred.await()
                if (awardTv.isNotEmpty()) {
                    rows += CatalogRow("award_winning_tv", "Award-Winning TV Shows", RowStyle.POSTER, awardTv)
                }
                if (top10TvNow.isNotEmpty()) {
                    rows += CatalogRow("top10_tv_now", "Top 10 TV Shows $regionSuffix", RowStyle.TOP_10, top10TvNow)
                }
                if (top10MoviesNow.isNotEmpty()) {
                    rows += CatalogRow("top10_movies_now", "Top 10 Movies $regionSuffix", RowStyle.TOP_10, top10MoviesNow)
                }
                libraryRow("unwatched_in_library", "Unwatched in Your Library")?.let { rows += it }
                if (myListItems.isNotEmpty()) {
                    rows += CatalogRow("my_list", "My List", RowStyle.POSTER, myListItems)
                }
                rows += pinnedLibraryRowsDeferred.await()
                becauseRows.getOrNull(1)?.let { rows += it }
                val household = householdDeferred.await()
                if (household.size >= MIN_PERSON_ROW_ITEMS) {
                    rows += CatalogRow("popular_household", "Popular in Your Household", RowStyle.POSTER, household)
                }
                personRowDeferred.await()?.let { rows += it }
                val seasonal = seasonalDeferred.await()
                if (seasonal.isNotEmpty()) {
                    rows += CatalogRow(seasonalSpec.id, seasonalSpec.title, RowStyle.POSTER, seasonal)
                }
                libraryRow("hidden_gems", "Hidden Gems on Your Server")?.let { rows += it }
                if (homeReleaseMovies.newOnDigital.isNotEmpty()) {
                    rows += CatalogRow("new_on_digital", "New on Digital & Streaming", RowStyle.POSTER, homeReleaseMovies.newOnDigital)
                }
                if (latest.isNotEmpty()) {
                    rows += CatalogRow("recently_added", "Recently Added", RowStyle.POSTER, latest)
                }
                becauseRows.getOrNull(2)?.let { rows += it }
                libraryRow("forgotten_in_library", "Added a While Ago, Never Started")?.let { rows += it }
                libraryRow("short_tonight", "Short on Time? Movies Under 100 Minutes")?.let { rows += it }
                rows += jellyfinLibraryRowsDeferred.await()
            }

            MediaType.MOVIE -> {
                // ---- MOVIES TAB ----
                if (todaysTopPicks.isNotEmpty()) {
                    rows += CatalogRow("todays_top_picks", topPicksTitle, RowStyle.POSTER, todaysTopPicks)
                }
                if (top10MoviesNow.isNotEmpty()) {
                    rows += CatalogRow("top10_movies_now", "Top 10 Movies $regionSuffix", RowStyle.TOP_10, top10MoviesNow)
                }
                if (resumeItems.isNotEmpty()) {
                    rows += CatalogRow("continue_watching", cwTitle, RowStyle.CONTINUE_WATCHING, resumeItems)
                }
                becauseRows.getOrNull(0)?.let { rows += it }
                libraryRow("unwatched_in_library", "Unwatched in Your Library")?.let { rows += it }
                traktRecsDeferred.await().takeIf { it.size >= MIN_DISCOVERY_ROW_ITEMS }?.let {
                    rows += CatalogRow("trakt_recommended", "Recommended for You on Trakt", RowStyle.POSTER, it)
                }
                val goodLaugh = needAGoodLaughDeferred.await()
                if (goodLaugh.isNotEmpty()) {
                    rows += CatalogRow("need_a_good_laugh", "Need a Good Laugh?", RowStyle.POSTER, goodLaugh)
                }
                val animCritics = animationCriticsDeferred.await()
                if (animCritics.isNotEmpty()) {
                    rows += CatalogRow("animation_critics_love", "Animation Critics Love", RowStyle.POSTER, animCritics)
                }
                val blockbusters = blockbusterMoviesDeferred.await()
                if (blockbusters.isNotEmpty()) {
                    rows += CatalogRow("blockbuster_movies", "Blockbuster Movies", RowStyle.POSTER, blockbusters)
                }
                val comedies = movieComediesDeferred.await()
                if (comedies.isNotEmpty()) {
                    rows += CatalogRow("movie_comedies", "Comedies", RowStyle.POSTER, comedies)
                }
                val action = movieActionDeferred.await()
                if (action.isNotEmpty()) {
                    rows += CatalogRow("movie_action_adventure", "Action & Adventure Movies", RowStyle.POSTER, action)
                }
                val horror = movieHorrorDeferred.await()
                if (horror.isNotEmpty()) {
                    rows += CatalogRow("movie_horror", "Horror Movies", RowStyle.POSTER, horror)
                }
                val drama = movieDramaDeferred.await()
                if (drama.isNotEmpty()) {
                    rows += CatalogRow("movie_drama", "Drama Movies", RowStyle.POSTER, drama)
                }
                val familyAnim = movieFamilyAnimationDeferred.await()
                if (familyAnim.isNotEmpty()) {
                    rows += CatalogRow("movie_family_animation", "Family Animation", RowStyle.POSTER, familyAnim)
                }
                val sciFi = movieSciFiDeferred.await()
                if (sciFi.isNotEmpty()) {
                    rows += CatalogRow("movie_scifi", "Sci-Fi Movies", RowStyle.POSTER, sciFi)
                }
                becauseRows.drop(1).forEach { rows += it }
                libraryRow("short_tonight", "Short on Time? Movies Under 100 Minutes")?.let { rows += it }
                libraryRow("hidden_gems", "Hidden Gems on Your Server")?.let { rows += it }
                libraryRow("forgotten_in_library", "Added a While Ago, Never Started")?.let { rows += it }
                if (myListItems.isNotEmpty()) {
                    rows += CatalogRow("my_list", "My List", RowStyle.POSTER, myListItems)
                }
                rows += pinnedLibraryRowsDeferred.await()
                if (homeReleaseMovies.newOnDigital.isNotEmpty()) {
                    rows += CatalogRow("new_on_digital", "New on Digital & Streaming", RowStyle.POSTER, homeReleaseMovies.newOnDigital)
                }
                if (latest.isNotEmpty()) {
                    rows += CatalogRow("recently_added", "Recently Added", RowStyle.POSTER, latest)
                }
                rows += jellyfinLibraryRowsDeferred.await()
            }

            MediaType.SERIES, MediaType.EPISODE -> {
                // ---- TV SHOWS TAB ----
                if (todaysTopPicks.isNotEmpty()) {
                    rows += CatalogRow("todays_top_picks", topPicksTitle, RowStyle.POSTER, todaysTopPicks)
                }
                becauseRows.getOrNull(0)?.let { rows += it }
                libraryRow("unwatched_in_library", "Unwatched in Your Library")?.let { rows += it }
                traktRecsDeferred.await().takeIf { it.size >= MIN_DISCOVERY_ROW_ITEMS }?.let {
                    rows += CatalogRow("trakt_recommended", "Recommended for You on Trakt", RowStyle.POSTER, it)
                }
                val awardDramas = awardWinningTvDramasDeferred.await()
                if (awardDramas.isNotEmpty()) {
                    rows += CatalogRow("award_winning_tv_dramas", "Award-Winning TV Dramas", RowStyle.POSTER, awardDramas)
                }
                val quickHits = thirtyMinuteHitsDeferred.await()
                if (quickHits.isNotEmpty()) {
                    rows += CatalogRow("thirty_minute_hits", "In a Hurry? Try These 30-Minute Hits", RowStyle.POSTER, quickHits)
                }
                val imaginative = imaginativeTvDeferred.await()
                if (imaginative.isNotEmpty()) {
                    rows += CatalogRow("imaginative_tv", "Imaginative TV Shows", RowStyle.POSTER, imaginative)
                }
                val familiar = familiarTvFavoritesDeferred.await()
                if (familiar.isNotEmpty()) {
                    rows += CatalogRow("familiar_tv_favorites", "Familiar TV Favorites", RowStyle.POSTER, familiar)
                }
                if (continueWatchingItems.isNotEmpty()) {
                    rows += CatalogRow("continue_watching", cwTitle, RowStyle.CONTINUE_WATCHING, continueWatchingItems)
                }
                if (separateNextUpItems.isNotEmpty()) {
                    rows += CatalogRow("next_up", "Next Up", RowStyle.CONTINUE_WATCHING, separateNextUpItems)
                }
                if (top10TvNow.isNotEmpty()) {
                    rows += CatalogRow("top10_tv_now", "Top 10 TV Shows $regionSuffix", RowStyle.TOP_10, top10TvNow)
                }
                val tvComedies = tvComediesDeferred.await()
                if (tvComedies.isNotEmpty()) {
                    rows += CatalogRow("tv_comedies", "TV Comedies", RowStyle.POSTER, tvComedies)
                }
                val newOnLumen = (latest + newPremieres).distinctBy { "${it.mediaType}_${it.tmdbId ?: it.id}" }
                if (newOnLumen.isNotEmpty()) {
                    rows += CatalogRow("recently_added", "New on Lumen", RowStyle.POSTER, newOnLumen)
                }
                val tvReality = tvRealityDeferred.await()
                if (tvReality.isNotEmpty()) {
                    rows += CatalogRow("tv_reality", "Reality TV", RowStyle.POSTER, tvReality)
                }
                val tvCrime = tvCrimeDeferred.await()
                if (tvCrime.isNotEmpty()) {
                    rows += CatalogRow("tv_crime", "Crime TV Shows", RowStyle.POSTER, tvCrime)
                }
                becauseRows.drop(1).forEach { rows += it }
                libraryRow("hidden_gems", "Hidden Gems on Your Server")?.let { rows += it }
                libraryRow("forgotten_in_library", "Added a While Ago, Never Started")?.let { rows += it }
                if (myListItems.isNotEmpty()) {
                    rows += CatalogRow("my_list", "My List", RowStyle.POSTER, myListItems)
                }
                rows += pinnedLibraryRowsDeferred.await()
                rows += jellyfinLibraryRowsDeferred.await()
            }
        }

        val collections = collectionsDeferred.await()
        if (collections.isNotEmpty()) {
            rows += CatalogRow(
                id = "collections",
                title = "Collections",
                style = RowStyle.POSTER,
                items = collections.map { col -> col.toPosterCardItem() }
            )
        }

        if (airingThisWeek.isNotEmpty()) {
            rows += CatalogRow(
                id = "airing_this_week",
                title = "New Episodes Coming This Week",
                style = RowStyle.POSTER,
                items = airingThisWeek
            )
        }

        if ((filterType == null || filterType == MediaType.MOVIE) && homeReleaseMovies.comingToStreamingDvd.isNotEmpty()) {
            rows += CatalogRow(
                id = "coming_to_streaming_dvd",
                title = "Coming Soon to Streaming & DVD",
                style = RowStyle.POSTER,
                items = homeReleaseMovies.comingToStreamingDvd
            )
        }

        if (filterType == null || filterType == MediaType.SERIES) {
            val upcomingTv = upcomingTvDeferred.await()
                .map { mapSeerrResult(it, MediaType.SERIES, today) }
                .distinctBy { "${it.mediaType}_${it.tmdbId}" }
                .filter(availabilityFilter::matches)
            if (upcomingTv.isNotEmpty()) {
                rows += CatalogRow(
                    id = "coming_soon_tv",
                    title = "Coming Soon • TV Shows",
                    style = RowStyle.POSTER,
                    items = upcomingTv
                )
            }
        }

        val watchAgain = playedDeferred.await()
            .map { mapJellyfinItem(it) }
            .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
        if (watchAgain.isNotEmpty()) {
            rows += CatalogRow(
                id = "watch_it_again",
                title = "Watch It Again",
                style = RowStyle.POSTER,
                items = watchAgain
            )
        }

        val decorated = decorateNetflixBadges(
            rows = rows,
            top10Movies = top10MoviesNow,
            top10Tv = top10TvNow,
            latestItems = latest,
            newOnDigital = homeReleaseMovies.newOnDigital,
            airingThisWeek = airingThisWeek,
            newSeriesPremieres = newPremieres
        )
        val polished = DiscoveryEngine.dedupeAcrossRows(DiscoveryEngine.filterDisliked(decorated, taste))
        applyRowCustomizationAndParentalFilters(polished, activeProfile, taste.rowOpens)
    }

    private fun applyRowCustomizationAndParentalFilters(
        rows: List<CatalogRow>,
        activeProfile: UserProfile?,
        rowOpens: Map<String, Int> = emptyMap()
    ): List<CatalogRow> {
        val hiddenIds = activeProfile?.hiddenHomeRowIds?.toSet().orEmpty()
        val filtered = rows
            .filterNot { it.id in hiddenIds }
            .mapNotNull { row ->
                val safeItems = ContentRatingHelper.filterForProfile(row.items, activeProfile)
                if (safeItems.isEmpty()) null else row.copy(items = safeItems)
            }
        val order = activeProfile?.homeRowOrder.orEmpty()
        if (order.isEmpty()) {
            // No hand-made order: rows this profile actually uses drift up (Netflix-style).
            return if (activeProfile?.personalizedRowOrder != false) {
                DiscoveryEngine.orderByEngagement(filtered, rowOpens)
            } else filtered
        }
        val byId = filtered.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        val remaining = filtered.filterNot { it.id in order }
        return ordered + remaining
    }

    private suspend fun loadGenreFilteredRows(
        userId: String,
        filterType: MediaType?,
        genre: GenreFilterOption,
        availabilityFilter: AvailabilityFilter,
        activeProfile: UserProfile? = null,
        today: LocalDate = LocalDate.now()
    ): List<CatalogRow> = coroutineScope {
        val seerrActive = isSeerrEnabled
        val movieGenreDeferred = async {
            if (seerrActive && filterType != MediaType.SERIES && genre.movieGenreId != null) {
                fetchGenrePool(availabilityFilter) { page ->
                    discoverReleasedByGenre(MediaType.MOVIE, genre.movieGenreId, page, today)
                }
            } else emptyList()
        }
        val tvGenreDeferred = async {
            if (seerrActive && filterType != MediaType.MOVIE && genre.tvGenreId != null) {
                fetchGenrePool(availabilityFilter) { page ->
                    discoverReleasedByGenre(MediaType.SERIES, genre.tvGenreId, page, today)
                }
            } else emptyList()
        }
        val libraryMatchesDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) return@async emptyList()
            // Match on the raw index first: mapping every library item just to test its genre was
            // the expensive part on a large library.
            // Capped: a broad genre ("Drama") on a big library matched thousands of titles, all
            // mapped into one horizontal row (and only the first 60 got overviews anyway).
            val matchingDtos = allLibraryItems.asSequence().filter { dto ->
                (filterType == null || jellyfinMediaType(dto) == filterType) && matchesGenreLabel(dto, genre) &&
                    dto.allowedFor(activeProfile)
            }.take(OVERVIEW_BACKFILL_LIMIT).toList()
            withOverviews(userId, matchingDtos)
                .map { mapJellyfinItem(it) }
                .filter(availabilityFilter::matches)
        }

        val rows = mutableListOf<CatalogRow>()
        if (availabilityFilter != AvailabilityFilter.REQUESTABLE) {
            val libraryMatches = libraryMatchesDeferred.await()
            if (libraryMatches.isNotEmpty()) {
                rows += CatalogRow(
                    id = "genre_library_${genre.id}",
                    title = "In Your Library • ${genre.label}",
                    style = RowStyle.POSTER,
                    items = libraryMatches
                )
            }
        }

        val movies = movieGenreDeferred.await()
        if (movies.isNotEmpty()) {
            rows += CatalogRow(
                id = "genre_top10_movies_${genre.id}",
                title = "Top 10 ${genre.label} Movies",
                style = RowStyle.TOP_10,
                items = movies.take(10)
            )
            val moreMovies = movies.drop(10)
            if (moreMovies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "genre_more_movies_${genre.id}",
                    title = "More ${genre.label} Movies",
                    style = RowStyle.POSTER,
                    items = moreMovies
                )
            }
        }

        val tv = tvGenreDeferred.await()
        if (tv.isNotEmpty()) {
            rows += CatalogRow(
                id = "genre_top10_tv_${genre.id}",
                title = "Top 10 ${genre.label} TV Shows",
                style = RowStyle.TOP_10,
                items = tv.take(10)
            )
            val moreTv = tv.drop(10)
            if (moreTv.isNotEmpty()) {
                rows += CatalogRow(
                    id = "genre_more_tv_${genre.id}",
                    title = "More ${genre.label} TV Shows",
                    style = RowStyle.POSTER,
                    items = moreTv
                )
            }
        }
        applyRowCustomizationAndParentalFilters(rows, activeProfile)
    }

    suspend fun loadNewAndPopular(
        userId: String,
        availabilityFilter: AvailabilityFilter = AvailabilityFilter.ALL,
        today: LocalDate = LocalDate.now(),
        activeProfile: UserProfile? = null,
        forceRefresh: Boolean = false
    ): List<CatalogRow> = coroutineScope {
        val seerrActive = isSeerrEnabled
        // Index-independent raw fetches first (they overlap a blocking index fetch, if any).
        val trendingDeferred = async {
            if (seerrActive) runCatchingCancellable { seerrApi.getTrending(language = metaLang).results }.getOrDefault(emptyList())
            else emptyList()
        }
        val upcomingTvDeferred = async {
            if (seerrActive) runCatchingCancellable { seerrApi.getUpcomingTv(language = metaLang).results }.getOrDefault(emptyList())
            else emptyList()
        }
        val latestDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { latestDtos(userId, limit = 24) }.getOrDefault(emptyList())
        }

        ensureLibraryIndex(userId, if (forceRefresh) IndexPolicy.FORCE else IndexPolicy.STALE_WHILE_REVALIDATE)

        val moviesNowDeferred = async {
            if (seerrActive) fetchTop10MoviesOutNow(today, availabilityFilter) else emptyList()
        }
        val tvNowDeferred = async {
            if (seerrActive) fetchTop10TvAiringNow(today, availabilityFilter) else emptyList()
        }
        val homeReleaseMoviesDeferred = async {
            if (seerrActive) loadHomeReleaseMovieRows(today, availabilityFilter) else HomeReleaseMovieRows.EMPTY
        }
        val streamingRowsDeferred = StreamingService.entries.map { service ->
            service to async {
                if (seerrActive) fetchTop10ForStreamingService(service, null, today, availabilityFilter)
                else emptyList()
            }
        }
        val airingThisWeekDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else loadAiringThisWeek(userId, today)
        }

        val regionSuffix = regionTodaySuffix(watchRegion)
        val moviesNow = moviesNowDeferred.await()
        val tvNow = tvNowDeferred.await()
        val homeReleaseMovies = homeReleaseMoviesDeferred.await()
        val airingThisWeek = airingThisWeekDeferred.await().filter(availabilityFilter::matches)
        val latest = latestDeferred.await().map { mapJellyfinItem(it) }.filter(availabilityFilter::matches)

        val rows = buildList {
            if (moviesNow.isNotEmpty()) {
                add(CatalogRow("np_top10_movies_now", "Top 10 Movies $regionSuffix", RowStyle.TOP_10, moviesNow))
            }
            if (tvNow.isNotEmpty()) {
                add(CatalogRow("np_top10_tv_now", "Top 10 TV Shows $regionSuffix", RowStyle.TOP_10, tvNow))
            }
            if (homeReleaseMovies.newOnDigital.isNotEmpty()) {
                add(CatalogRow("np_new_on_digital", "New on Digital & Streaming", RowStyle.POSTER, homeReleaseMovies.newOnDigital))
            }
            streamingRowsDeferred.forEach { (service, deferred) ->
                val items = deferred.await()
                if (items.isNotEmpty()) {
                    add(CatalogRow(service.newAndPopularRowId, streamingRowTitle(service, null), RowStyle.TOP_10, items))
                }
            }
            val trendingRaw = trendingDeferred.await().mapNotNull { res ->
                val type = when (res.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> null
                } ?: return@mapNotNull null
                mapSeerrResult(res, type, today)
            }
                .filter { isReleasedOnOrBefore(it.releaseDate, today) }
                .filter(availabilityFilter::matches)
            val trending = filterMoviesAvailableAtHome(trendingRaw, today)
            // Unreleased / theater-only titles stay out of the numbered Trending chart.
            if (trending.isNotEmpty()) {
                add(CatalogRow("np_trending", "Trending Now", RowStyle.TOP_10, trending.take(10)))
            }
            if (airingThisWeek.isNotEmpty()) {
                add(CatalogRow("np_airing_this_week", "New Episodes Coming This Week", RowStyle.POSTER, airingThisWeek))
            }
            if (homeReleaseMovies.comingToStreamingDvd.isNotEmpty()) {
                add(CatalogRow("np_coming_streaming_dvd", "Coming Soon to Streaming & DVD", RowStyle.POSTER, homeReleaseMovies.comingToStreamingDvd))
            }
            val upcomingTv = upcomingTvDeferred.await()
                .map { mapSeerrResult(it, MediaType.SERIES, today) }
                .filter(availabilityFilter::matches)
            if (upcomingTv.isNotEmpty()) {
                add(CatalogRow("np_coming_tv", "Coming Soon • TV Shows", RowStyle.POSTER, upcomingTv))
            }
            if (latest.isNotEmpty()) {
                add(CatalogRow("np_latest", "Just Added to Your Server", RowStyle.POSTER, latest))
            }
        }
        val decorated = decorateNetflixBadges(
            rows = rows,
            top10Movies = moviesNow,
            top10Tv = tvNow,
            latestItems = latest,
            newOnDigital = homeReleaseMovies.newOnDigital,
            airingThisWeek = airingThisWeek,
            newSeriesPremieres = emptyList()
        )
        applyRowCustomizationAndParentalFilters(decorated, activeProfile)
    }

    /**
     * "Top 10 Movies Out Right Now": Trakt's most-watched movies this week when a Trakt client id is
     * built in, otherwise (or to fill gaps) TMDB popularity among movies released in the last 90 days.
     * Unreleased and theater-only titles (no Digital/Physical release yet) are filtered out.
     */
    private suspend fun fetchTop10MoviesOutNow(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> {
        val chart = traktCharts?.movies(today)
        if (!chart.isNullOrEmpty()) {
            val fromChart = resolveChart(chart, MediaType.MOVIE, today, availabilityFilter)
            if (fromChart.size >= TOP_N) return fromChart
            return (fromChart + seerrMoviesOutNow(today, availabilityFilter))
                .distinctBy { "${it.mediaType}_${it.tmdbId}" }
                .take(TOP_N)
        }
        return seerrMoviesOutNow(today, availabilityFilter)
    }

    private suspend fun seerrMoviesOutNow(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> {
        val windowStart = today.minusDays(90).toString()
        val windowEnd = today.toString()
        val windowed = fetchTop10Matching(availabilityFilter) { page ->
            val raw = runCatchingCancellable {
                seerrApi.getDiscoverMovies(
                    page = page,
                    language = metaLang,
                    sortBy = "popularity.desc",
                    primaryReleaseDateGte = windowStart,
                    primaryReleaseDateLte = windowEnd,
                    voteCountGte = 10
                ).results
            }.getOrDefault(emptyList())
                .filter { isReleasedOnOrBefore(it.releaseDate, today) }
                .map { mapSeerrResult(it, MediaType.MOVIE, today) }
            filterMoviesAvailableAtHome(raw, today)
        }
        if (windowed.isNotEmpty()) return windowed

        // Fallback if Seerr server ignores date bounds
        return fetchTop10Matching(availabilityFilter) { page ->
            val raw = runCatchingCancellable { seerrApi.getDiscoverMovies(page = page, language = metaLang).results }
                .getOrDefault(emptyList())
                .filter { isReleasedOnOrBefore(it.releaseDate, today) }
                .map { mapSeerrResult(it, MediaType.MOVIE, today) }
            filterMoviesAvailableAtHome(raw, today)
        }
    }

    /**
     * "Top 10 TV Shows Airing Now": Trakt's most-watched still-running shows this week, otherwise
     * (or to fill gaps) TMDB popularity among shows TMDB marks "Returning Series".
     */
    private suspend fun fetchTop10TvAiringNow(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> {
        val chart = traktCharts?.shows()
        if (!chart.isNullOrEmpty()) {
            val fromChart = resolveChart(chart, MediaType.SERIES, today, availabilityFilter)
            if (fromChart.size >= TOP_N) return fromChart
            return (fromChart + seerrTvAiringNow(today, availabilityFilter))
                .distinctBy { "${it.mediaType}_${it.tmdbId}" }
                .take(TOP_N)
        }
        return seerrTvAiringNow(today, availabilityFilter)
    }

    private suspend fun seerrTvAiringNow(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> = fetchTop10Matching(availabilityFilter) { page ->
        // Seerr's "upcoming TV" list is premieres from today on, so it can't answer "airing now";
        // TMDB's Returning Series status can (older Seerr versions ignore `status`, which still
        // leaves popular, already-premiered shows).
        runCatchingCancellable {
            seerrApi.getDiscoverTv(
                page = page,
                language = metaLang,
                sortBy = "popularity.desc",
                firstAirDateLte = today.toString(),
                status = TMDB_STATUS_RETURNING
            ).results
        }.getOrDefault(emptyList())
            .filter { isReleasedOnOrBefore(it.firstAirDate, today) }
            .map { mapSeerrResult(it, MediaType.SERIES, today) }
    }

    private fun streamingRowTitle(service: StreamingService, filterType: MediaType?): String = when (filterType) {
        MediaType.MOVIE -> "Top 10 Movies on ${service.displayName}"
        MediaType.SERIES -> "Top 10 TV Shows on ${service.displayName}"
        else -> "Top 10 on ${service.displayName}"
    }

    private class StreamingChartCacheEntry(val atMs: Long, val results: List<SeerrMediaResultDto>)

    private val streamingProviderCache = java.util.concurrent.ConcurrentHashMap<String, StreamingChartCacheEntry>()

    private suspend fun fetchStreamingProviderPage(
        service: StreamingService,
        type: MediaType,
        page: Int,
        today: LocalDate
    ): List<SeerrMediaResultDto> {
        val region = watchRegion
        val cacheKey = "${service.id}_${type.name}_${page}_${region}_$metaLang"
        val now = System.currentTimeMillis()
        streamingProviderCache[cacheKey]?.takeIf { now - it.atMs in 0 until CHART_CARD_TTL_MS }?.let {
            return it.results
        }
        val fetched = runCatchingCancellable {
            if (type == MediaType.MOVIE) {
                seerrApi.getDiscoverMovies(
                    page = page,
                    language = metaLang,
                    sortBy = "popularity.desc",
                    primaryReleaseDateLte = today.toString(),
                    voteCountGte = 10,
                    watchRegion = region,
                    watchProviders = service.providerIds
                ).results
            } else {
                seerrApi.getDiscoverTv(
                    page = page,
                    language = metaLang,
                    sortBy = "popularity.desc",
                    firstAirDateLte = today.toString(),
                    voteCountGte = 10,
                    watchRegion = region,
                    watchProviders = service.providerIds
                ).results
            }
        }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            putBounded(streamingProviderCache, cacheKey, StreamingChartCacheEntry(now, fetched)) { it.atMs }
        }
        return fetched
    }

    /**
     * Top 10 titles currently streaming on [service] (via Seerr/TMDB watch providers for the local
     * region). Every title returned by TMDB watch providers is already released & streaming.
     */
    private suspend fun fetchTop10ForStreamingService(
        service: StreamingService,
        filterType: MediaType?,
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> {
        if (filterType == MediaType.MOVIE) {
            return fetchTop10Matching(availabilityFilter) { page ->
                fetchStreamingProviderPage(service, MediaType.MOVIE, page, today)
                    .filter { isReleasedOnOrBefore(it.releaseDate, today) }
                    .map { mapSeerrResult(it, MediaType.MOVIE, today) }
            }
        }
        if (filterType == MediaType.SERIES) {
            return fetchTop10Matching(availabilityFilter) { page ->
                fetchStreamingProviderPage(service, MediaType.SERIES, page, today)
                    .filter { isReleasedOnOrBefore(it.firstAirDate, today) }
                    .map { mapSeerrResult(it, MediaType.SERIES, today) }
            }
        }

        // Combined Home hub row: fetch Movies & TV in parallel and rank into one Top 10.
        return coroutineScope {
            val maxPages = if (availabilityFilter == AvailabilityFilter.ALL) 1 else 2
            val moviesDeferred = async {
                (1..maxPages).flatMap { page ->
                    fetchStreamingProviderPage(service, MediaType.MOVIE, page, today)
                }
                    .filter { isReleasedOnOrBefore(it.releaseDate, today) }
                    .map { dto -> dto.popularity to mapSeerrResult(dto, MediaType.MOVIE, today) }
                    .filter { (_, item) -> availabilityFilter.matches(item) }
            }
            val tvDeferred = async {
                (1..maxPages).flatMap { page ->
                    fetchStreamingProviderPage(service, MediaType.SERIES, page, today)
                }
                    .filter { isReleasedOnOrBefore(it.firstAirDate, today) }
                    .map { dto -> dto.popularity to mapSeerrResult(dto, MediaType.SERIES, today) }
                    .filter { (_, item) -> availabilityFilter.matches(item) }
            }
            val movies = moviesDeferred.await()
            val tv = tvDeferred.await()
            val hasPopularity = (movies + tv).any { it.first != null }
            val merged = if (hasPopularity) {
                (movies + tv)
                    .sortedByDescending { it.first ?: 0.0 }
                    .map { it.second }
            } else {
                // Interleave movies and TV evenly when popularity scores are omitted.
                buildList {
                    val maxLen = maxOf(movies.size, tv.size)
                    for (i in 0 until maxLen) {
                        if (i < tv.size) add(tv[i].second)
                        if (i < movies.size) add(movies[i].second)
                    }
                }
            }
            merged
                .distinctBy { "${it.mediaType}_${it.tmdbId}_${it.id}" }
                .take(TOP_N)
        }
    }

    private data class HomeReleaseMovieRows(
        val newOnDigital: List<UnifiedMediaItem>,
        val comingToStreamingDvd: List<UnifiedMediaItem>
    ) {
        companion object {
            val EMPTY = HomeReleaseMovieRows(emptyList(), emptyList())
        }
    }

    /**
     * Builds "New on Digital & Streaming" (home release within the last 45 days) and
     * "Coming Soon to Streaming & DVD" (home release in the next 90 days) from TMDB's typed
     * Digital (4) / Physical (5) release dates — avoiding theatrical-only upcoming movies.
     */
    private suspend fun loadHomeReleaseMovieRows(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): HomeReleaseMovieRows = coroutineScope {
        val candidateStart = today.minusDays(150).toString()
        val candidateEnd = today.plusDays(45).toString()
        val candidates = (1..2).flatMap { page ->
            runCatchingCancellable {
                seerrApi.getDiscoverMovies(
                    page = page,
                    language = metaLang,
                    sortBy = "popularity.desc",
                    primaryReleaseDateGte = candidateStart,
                    primaryReleaseDateLte = candidateEnd,
                    voteCountGte = 10
                ).results
            }.getOrDefault(emptyList())
        }
            .distinctBy { it.id }
            .take(HOME_RELEASE_CANDIDATE_LIMIT)

        if (candidates.isEmpty()) return@coroutineScope HomeReleaseMovieRows.EMPTY

        val region = watchRegion
        val newCutoff = today.minusDays(NEW_ON_DIGITAL_WINDOW_DAYS)
        val comingCutoff = today.plusDays(COMING_TO_HOME_WINDOW_DAYS)

        val resolved = candidates.map { dto ->
            async {
                val details = movieReleaseDetails(dto.id) ?: return@async null
                val homeRelease = MovieReleaseClassifier.homeReleaseDate(details, region) ?: return@async null
                Triple(dto, details, homeRelease)
            }
        }.mapNotNull { it.await() }

        val newOnDigital = resolved
            .filter { (_, _, hr) -> !hr.date.isAfter(today) && !hr.date.isBefore(newCutoff) }
            .sortedWith(compareByDescending<Triple<SeerrMediaResultDto, SeerrMovieDetailsDto, HomeReleaseDate>> { it.third.date }
                .thenByDescending { it.first.popularity ?: 0.0 })
            .map { (dto, details, hr) ->
                val base = mapSeerrResult(dto, MediaType.MOVIE, today)
                base.copy(
                    releaseDate = hr.date.toString(),
                    upcomingBadgeLabel = null,
                    maturityRating = base.maturityRating ?: details.certification(region)
                )
            }
            .filter(availabilityFilter::matches)
            .take(20)

        val comingToStreamingDvd = resolved
            .filter { (_, _, hr) -> hr.date.isAfter(today) && !hr.date.isAfter(comingCutoff) }
            .sortedBy { it.third.date }
            .map { (dto, details, hr) ->
                val base = mapSeerrResult(dto, MediaType.MOVIE, today)
                base.copy(
                    releaseDate = hr.date.toString(),
                    upcomingBadgeLabel = MovieReleaseClassifier.formatUpcomingHomeBadge(hr, today),
                    maturityRating = base.maturityRating ?: details.certification(region)
                )
            }
            .filter(availabilityFilter::matches)
            .take(20)

        HomeReleaseMovieRows(
            newOnDigital = newOnDigital,
            comingToStreamingDvd = comingToStreamingDvd
        )
    }

    /**
     * Excludes theatrical-only and unreleased movies from home-watchable rows (Top 10 / Trending).
     * Movies already playable in Jellyfin pass immediately without a Seerr lookup; TV items always pass.
     */
    private suspend fun filterMoviesAvailableAtHome(
        items: List<UnifiedMediaItem>,
        today: LocalDate
    ): List<UnifiedMediaItem> {
        if (items.isEmpty()) return emptyList()
        val region = watchRegion
        return coroutineScope {
            items.map { item ->
                async {
                    if (item.mediaType != MediaType.MOVIE || item.isPlayable) return@async item
                    val tmdbId = item.tmdbId ?: return@async item
                    val details = movieReleaseDetails(tmdbId)
                    if (details == null || MovieReleaseClassifier.isAvailableAtHome(details, today, region, item.releaseDate)) {
                        item
                    } else null
                }
            }.mapNotNull { it.await() }
        }
    }

    /**
     * Turns a ranked chart into cards via Seerr details (artwork, availability, rating), keeping chart
     * order. Looks up a dozen at a time in parallel and stops as soon as [TOP_N] cards pass the
     * release-date, home-availability, and availability filters, so the usual case is a single parallel batch.
     */
    private suspend fun resolveChart(
        chart: List<ChartEntry>,
        type: MediaType,
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> {
        val collected = mutableListOf<UnifiedMediaItem>()
        val region = watchRegion
        for (batch in chart.distinctBy { it.tmdbId }.chunked(CHART_BATCH).take(CHART_MAX_BATCHES)) {
            val resolved = coroutineScope {
                batch.map { entry -> async { chartCardWithDetails(entry.tmdbId, type, today) } }.map { it.await() }
            }
            resolved.asSequence()
                .filterNotNull()
                .filter { (card, movieDetails) ->
                    if (type == MediaType.MOVIE && !card.isPlayable && movieDetails != null) {
                        MovieReleaseClassifier.isAvailableAtHome(movieDetails, today, region, card.releaseDate)
                    } else {
                        isReleasedOnOrBefore(card.releaseDate, today)
                    }
                }
                .map { it.first }
                .filter(availabilityFilter::matches)
                .forEach { if (collected.size < TOP_N) collected += it }
            if (collected.size >= TOP_N) break
        }
        return collected
    }

    private class ChartCardCacheEntry(
        val atMs: Long,
        val result: SeerrMediaResultDto,
        val rating: String?,
        val movieDetails: SeerrMovieDetailsDto? = null
    )

    /** Short-lived: charts are re-resolved on every Home load, but availability can change. */
    private val chartCardCache = java.util.concurrent.ConcurrentHashMap<String, ChartCardCacheEntry>()

    /**
     * In-flight detail lookups by cache key. On a cold Home load the trending filter, the Top 10
     * rows and the release-date rows ask for the same popular titles at the same time; they now
     * share one request instead of each fetching `/movie/{id}`.
     */
    private val chartCardInFlight =
        java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<ChartCardLookup>>()

    /** [abandoned]: the caller that was fetching it was cancelled; a waiter fetches it itself. */
    private class ChartCardLookup(val entry: ChartCardCacheEntry?, val abandoned: Boolean = false)

    private suspend fun chartCardEntry(tmdbId: Int, type: MediaType): ChartCardCacheEntry? {
        val key = "${type.name}_${tmdbId}_$metaLang"
        val now = System.currentTimeMillis()
        chartCardCache[key]?.takeIf { now - it.atMs in 0 until CHART_CARD_TTL_MS }?.let { return it }
        val mine = kotlinx.coroutines.CompletableDeferred<ChartCardLookup>()
        val existing = chartCardInFlight.putIfAbsent(key, mine)
        if (existing != null) {
            val shared = existing.await()
            if (!shared.abandoned) return shared.entry
            return runCatchingCancellable { fetchChartCardEntry(tmdbId, type, metaLang) }.getOrNull()
                ?.also { putBounded(chartCardCache, key, it) { e -> e.atMs } }
        }
        // This caller fetches; concurrent callers for the same title wait for its result.
        var lookup = ChartCardLookup(null, abandoned = true)
        try {
            val entry = runCatchingCancellable { fetchChartCardEntry(tmdbId, type, metaLang) }.getOrNull()
                ?.also { putBounded(chartCardCache, key, it) { e -> e.atMs } }
            lookup = ChartCardLookup(entry)
            return entry
        } finally {
            chartCardInFlight.remove(key, mine)
            mine.complete(lookup)
        }
    }

    private suspend fun fetchChartCardEntry(tmdbId: Int, type: MediaType, lang: String): ChartCardCacheEntry {
        val now = System.currentTimeMillis()
        return if (type == MediaType.MOVIE) {
            val d = seerrApi.getMovieDetails(tmdbId, language = lang)
            ChartCardCacheEntry(
                now,
                SeerrMediaResultDto(
                    id = d.id, mediaType = "movie", title = d.title, overview = d.overview,
                    releaseDate = d.releaseDate, posterPath = d.posterPath, backdropPath = d.backdropPath,
                    voteAverage = d.voteAverage, adult = d.adult, mediaInfo = d.mediaInfo
                ),
                d.certification(watchRegion),
                d
            )
        } else {
            val d = seerrApi.getTvDetails(tmdbId, language = lang)
            ChartCardCacheEntry(
                now,
                SeerrMediaResultDto(
                    id = d.id, mediaType = "tv", name = d.name, overview = d.overview,
                    firstAirDate = d.firstAirDate, posterPath = d.posterPath, backdropPath = d.backdropPath,
                    voteAverage = d.voteAverage, mediaInfo = d.mediaInfo
                ),
                d.certification(watchRegion)
            )
        }
    }

    /**
     * The TTL is only checked on read, so these caches would otherwise grow for the life of the
     * process (curated keys include the date). Past [SEERR_CACHE_MAX_ENTRIES], expired entries
     * are dropped, then the oldest ones.
     */
    private fun <V> putBounded(
        cache: java.util.concurrent.ConcurrentHashMap<String, V>,
        key: String,
        value: V,
        atMs: (V) -> Long
    ) {
        cache[key] = value
        if (cache.size <= SEERR_CACHE_MAX_ENTRIES) return
        val now = System.currentTimeMillis()
        cache.entries.removeIf { now - atMs(it.value) !in 0 until CHART_CARD_TTL_MS }
        if (cache.size > SEERR_CACHE_MAX_ENTRIES) {
            cache.entries.sortedBy { atMs(it.value) }
                .take(cache.size - SEERR_CACHE_MAX_ENTRIES)
                .forEach { cache.remove(it.key, it.value) }
        }
    }

    private suspend fun movieReleaseDetails(tmdbId: Int): SeerrMovieDetailsDto? =
        chartCardEntry(tmdbId, MediaType.MOVIE)?.movieDetails

    private suspend fun chartCardWithDetails(
        tmdbId: Int,
        type: MediaType,
        today: LocalDate
    ): Pair<UnifiedMediaItem, SeerrMovieDetailsDto?>? {
        val entry = chartCardEntry(tmdbId, type) ?: return null
        val card = mapSeerrResult(entry.result, type, today)
        // Certification lets Kids / rating-limited profiles filter chart titles they don't own.
        val ratedCard = if (card.maturityRating == null && entry.rating != null) card.copy(maturityRating = entry.rating) else card
        return ratedCard to entry.movieDetails
    }

    private suspend fun fetchTop10NewSeriesPremieres(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> {
        val windowStart = today.minusDays(60).toString()
        val windowEnd = today.toString()
        return fetchTop10Matching(availabilityFilter) { page ->
            runCatchingCancellable {
                seerrApi.getDiscoverTv(
                    page = page,
                    language = metaLang,
                    sortBy = "popularity.desc",
                    firstAirDateGte = windowStart,
                    firstAirDateLte = windowEnd
                ).results
            }.getOrDefault(emptyList())
                .filter { isReleasedOnOrBefore(it.firstAirDate, today) }
                .map { mapSeerrResult(it, MediaType.SERIES, today) }
        }
    }

    private class CuratedDiscoverCacheEntry(val atMs: Long, val results: List<SeerrMediaResultDto>)

    private val curatedDiscoverCache = java.util.concurrent.ConcurrentHashMap<String, CuratedDiscoverCacheEntry>()

    private suspend fun fetchCuratedMovies(
        cacheTag: String,
        today: LocalDate,
        availabilityFilter: AvailabilityFilter,
        genre: String? = null,
        sortBy: String = "popularity.desc",
        voteCountGte: Int = GENRE_MIN_VOTES_MOVIE,
        voteAverageGte: Double? = null,
        withRuntimeLte: Int? = null
    ): List<UnifiedMediaItem> = fetchCuratedPool(availabilityFilter) { page ->
        val key = "m_${cacheTag}_${page}_${today}_$metaLang"
        val now = System.currentTimeMillis()
        val raw = curatedDiscoverCache[key]?.takeIf { now - it.atMs in 0 until CHART_CARD_TTL_MS }?.results
            ?: runCatchingCancellable {
                seerrApi.getDiscoverMovies(
                    page = page,
                    language = metaLang,
                    sortBy = sortBy,
                    genre = genre,
                    primaryReleaseDateLte = today.toString(),
                    voteAverageGte = voteAverageGte,
                    voteCountGte = voteCountGte,
                    withRuntimeLte = withRuntimeLte
                ).results
            }.getOrDefault(emptyList()).also {
                if (it.isNotEmpty()) putBounded(curatedDiscoverCache, key, CuratedDiscoverCacheEntry(now, it)) { e -> e.atMs }
            }
        raw.filter { isReleasedOnOrBefore(it.releaseDate, today) }
            .map { mapSeerrResult(it, MediaType.MOVIE, today) }
    }

    private suspend fun fetchCuratedTv(
        cacheTag: String,
        today: LocalDate,
        availabilityFilter: AvailabilityFilter,
        genre: String? = null,
        sortBy: String = "popularity.desc",
        voteCountGte: Int = GENRE_MIN_VOTES_TV,
        voteAverageGte: Double? = null,
        withRuntimeLte: Int? = null
    ): List<UnifiedMediaItem> = fetchCuratedPool(availabilityFilter) { page ->
        val key = "tv_${cacheTag}_${page}_${today}_$metaLang"
        val now = System.currentTimeMillis()
        val raw = curatedDiscoverCache[key]?.takeIf { now - it.atMs in 0 until CHART_CARD_TTL_MS }?.results
            ?: runCatchingCancellable {
                seerrApi.getDiscoverTv(
                    page = page,
                    language = metaLang,
                    sortBy = sortBy,
                    genre = genre,
                    firstAirDateLte = today.toString(),
                    voteAverageGte = voteAverageGte,
                    voteCountGte = voteCountGte,
                    withRuntimeLte = withRuntimeLte
                ).results
            }.getOrDefault(emptyList()).also {
                if (it.isNotEmpty()) putBounded(curatedDiscoverCache, key, CuratedDiscoverCacheEntry(now, it)) { e -> e.atMs }
            }
        raw.filter { isReleasedOnOrBefore(it.firstAirDate, today) }
            .map { mapSeerrResult(it, MediaType.SERIES, today) }
    }

    private suspend fun fetchGemsForYou(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> = coroutineScope {
        val moviesDeferred = async {
            fetchCuratedMovies(
                cacheTag = "gems_movies",
                today = today,
                availabilityFilter = availabilityFilter,
                sortBy = "vote_average.desc",
                voteCountGte = 200,
                voteAverageGte = 7.6
            )
        }
        val tvDeferred = async {
            fetchCuratedTv(
                cacheTag = "gems_tv",
                today = today,
                availabilityFilter = availabilityFilter,
                sortBy = "vote_average.desc",
                voteCountGte = 150,
                voteAverageGte = 7.7
            )
        }
        val movies = moviesDeferred.await()
        val tv = tvDeferred.await()
        buildList {
            val maxLen = maxOf(movies.size, tv.size)
            for (i in 0 until maxLen) {
                if (i < tv.size) add(tv[i])
                if (i < movies.size) add(movies[i])
            }
        }.distinctBy { "${it.mediaType}_${it.tmdbId ?: it.id}" }.take(CURATED_ROW_SIZE)
    }

    private data class SeasonalRowSpec(
        val id: String,
        val title: String,
        val movieGenre: String?,
        val tvGenre: String?,
        val sortBy: String = "popularity.desc",
        val voteCountGte: Int = 100,
        val voteAverageGte: Double? = null
    )

    private fun seasonalRowSpec(today: LocalDate): SeasonalRowSpec = when (today.monthValue) {
        2 -> SeasonalRowSpec("seasonal_picks", "Date Night & Romance", movieGenre = "10749", tvGenre = "10749")
        6, 7, 8 -> SeasonalRowSpec("seasonal_picks", "Summer Blockbusters", movieGenre = "28,12", tvGenre = "10759")
        10 -> SeasonalRowSpec("seasonal_picks", "Spooky Season Picks", movieGenre = "27", tvGenre = "9648")
        11 -> SeasonalRowSpec("seasonal_picks", "Cozy Autumn & Thanksgiving Picks", movieGenre = "35,10751", tvGenre = "35")
        12 -> SeasonalRowSpec("seasonal_picks", "Holiday Favorites", movieGenre = "10751", tvGenre = "10751")
        else -> SeasonalRowSpec(
            id = "seasonal_picks",
            title = "Critically Acclaimed Films",
            movieGenre = null,
            tvGenre = null,
            sortBy = "vote_average.desc",
            voteCountGte = 400,
            voteAverageGte = 7.6
        )
    }

    private suspend fun fetchSeasonalRow(
        spec: SeasonalRowSpec,
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> = coroutineScope {
        val moviesDeferred = async {
            fetchCuratedMovies(
                cacheTag = "seasonal_m_${today.monthValue}",
                today = today,
                availabilityFilter = availabilityFilter,
                genre = spec.movieGenre,
                sortBy = spec.sortBy,
                voteCountGte = spec.voteCountGte,
                voteAverageGte = spec.voteAverageGte
            )
        }
        val tvDeferred = async {
            if (spec.tvGenre == null) emptyList()
            else fetchCuratedTv(
                cacheTag = "seasonal_tv_${today.monthValue}",
                today = today,
                availabilityFilter = availabilityFilter,
                genre = spec.tvGenre,
                sortBy = spec.sortBy,
                voteCountGte = spec.voteCountGte / 2,
                voteAverageGte = spec.voteAverageGte
            )
        }
        val movies = moviesDeferred.await()
        val tv = tvDeferred.await()
        if (tv.isEmpty()) return@coroutineScope movies.take(CURATED_ROW_SIZE)
        buildList {
            val maxLen = maxOf(movies.size, tv.size)
            for (i in 0 until maxLen) {
                if (i < movies.size) add(movies[i])
                if (i < tv.size) add(tv[i])
            }
        }.distinctBy { "${it.mediaType}_${it.tmdbId ?: it.id}" }.take(CURATED_ROW_SIZE)
    }

    private suspend fun fetchCuratedPool(
        availabilityFilter: AvailabilityFilter,
        fetchPage: suspend (Int) -> List<UnifiedMediaItem>
    ): List<UnifiedMediaItem> {
        val maxPages = if (availabilityFilter == AvailabilityFilter.ALL) 1 else 2
        val collected = mutableListOf<UnifiedMediaItem>()
        val seenKeys = mutableSetOf<String>()
        for (page in 1..maxPages) {
            val raw = fetchPage(page)
            if (raw.isEmpty()) break
            raw.filter(availabilityFilter::matches).forEach { item ->
                val key = "${item.mediaType}_${item.tmdbId}_${item.id}"
                if (seenKeys.add(key)) {
                    collected += item
                }
            }
            if (collected.size >= CURATED_ROW_SIZE) break
        }
        return collected.take(CURATED_ROW_SIZE)
    }

    private fun decorateNetflixBadges(
        rows: List<CatalogRow>,
        top10Movies: List<UnifiedMediaItem>,
        top10Tv: List<UnifiedMediaItem>,
        latestItems: List<UnifiedMediaItem>,
        newOnDigital: List<UnifiedMediaItem>,
        airingThisWeek: List<UnifiedMediaItem>,
        newSeriesPremieres: List<UnifiedMediaItem>
    ): List<CatalogRow> {
        val top10MovieTmdb = top10Movies.mapNotNull { it.tmdbId }.toSet()
        val top10TvTmdb = top10Tv.mapNotNull { it.tmdbId }.toSet()

        fun UnifiedMediaItem.matchKeys(): List<String> = buildList(2) {
            tmdbId?.let { add("${mediaType.name}_$it") }
            jellyfinId?.let { add("jf_$it") }
        }

        val newEpisodeKeys = airingThisWeek.flatMapTo(HashSet()) { it.matchKeys() }
        val newSeasonKeys = buildSet {
            newSeriesPremieres.forEach { addAll(it.matchKeys()) }
            latestItems.filter { it.mediaType == MediaType.SERIES }.forEach { addAll(it.matchKeys()) }
        }
        val recentlyAddedKeys = buildSet {
            latestItems.filter { it.mediaType == MediaType.MOVIE }.forEach { addAll(it.matchKeys()) }
            newOnDigital.forEach { addAll(it.matchKeys()) }
        }

        return rows.map { row ->
            if (row.style == RowStyle.CONTINUE_WATCHING) return@map row
            row.copy(
                items = row.items.map { item ->
                    if (item.boxSetId != null) return@map item
                    val inTop10 = when (item.mediaType) {
                        MediaType.MOVIE -> item.tmdbId != null && item.tmdbId in top10MovieTmdb
                        MediaType.SERIES -> item.tmdbId != null && item.tmdbId in top10TvTmdb
                        MediaType.EPISODE -> false
                    }
                    val badge = if (!item.upcomingBadgeLabel.isNullOrBlank()) {
                        item.netflixBadgeLabel
                    } else {
                        val keys = item.matchKeys()
                        when {
                            keys.any { it in newEpisodeKeys } -> "New Episode"
                            keys.any { it in newSeasonKeys } -> "New Season"
                            keys.any { it in recentlyAddedKeys } -> "Recently Added"
                            else -> item.netflixBadgeLabel
                        }
                    }
                    if (inTop10 == item.isTop10 && badge == item.netflixBadgeLabel) item
                    else item.copy(isTop10 = inTop10, netflixBadgeLabel = badge)
                }
            )
        }
    }

    private fun regionTodaySuffix(region: String): String = when (region.uppercase(java.util.Locale.US)) {
        "US" -> "in the U.S. Today"
        "GB", "UK" -> "in the UK Today"
        "CA" -> "in Canada Today"
        "AU" -> "in Australia Today"
        "DE" -> "in Germany Today"
        "FR" -> "in France Today"
        "ES" -> "in Spain Today"
        "JP" -> "in Japan Today"
        else -> "Today"
    }

    private fun continueWatchingTitle(activeProfile: UserProfile?): String {
        val name = activeProfile?.name?.trim().orEmpty()
        return if (name.isNotEmpty()) "Continue Watching for $name" else "Continue Watching"
    }

    /**
     * Most popular titles in a genre that are already out. Seerr's `/discover/{movies,tv}/genre/{id}`
     * has no date bound (an unreleased blockbuster with a big marketing push tops it), so this uses
     * the main discover list with the same genre, a release date of today or earlier and a minimum
     * vote count.
     */
    private suspend fun discoverReleasedByGenre(
        type: MediaType,
        genreId: Int,
        page: Int,
        today: LocalDate
    ): List<UnifiedMediaItem> = runCatchingCancellable {
        if (type == MediaType.MOVIE) {
            seerrApi.getDiscoverMovies(
                page = page,
                language = metaLang,
                sortBy = "popularity.desc",
                genre = genreId.toString(),
                primaryReleaseDateLte = today.toString(),
                voteCountGte = GENRE_MIN_VOTES_MOVIE
            ).results
                .filter { isReleasedOnOrBefore(it.releaseDate, today) }
                .map { mapSeerrResult(it, MediaType.MOVIE, today) }
        } else {
            seerrApi.getDiscoverTv(
                page = page,
                language = metaLang,
                sortBy = "popularity.desc",
                genre = genreId.toString(),
                firstAirDateLte = today.toString(),
                voteCountGte = GENRE_MIN_VOTES_TV
            ).results
                .filter { isReleasedOnOrBefore(it.firstAirDate, today) }
                .map { mapSeerrResult(it, MediaType.SERIES, today) }
        }
    }.getOrDefault(emptyList())

    private suspend fun fetchTop10Matching(
        availabilityFilter: AvailabilityFilter,
        fetchPage: suspend (Int) -> List<UnifiedMediaItem>
    ): List<UnifiedMediaItem> {
        val maxPages = if (availabilityFilter == AvailabilityFilter.ALL) 1 else 3
        val collected = mutableListOf<UnifiedMediaItem>()
        val seenKeys = mutableSetOf<String>()
        for (page in 1..maxPages) {
            val raw = fetchPage(page)
            if (raw.isEmpty()) break
            raw.filter(availabilityFilter::matches).forEach { item ->
                val key = "${item.mediaType}_${item.tmdbId}_${item.id}"
                if (seenKeys.add(key)) {
                    collected += item
                }
            }
            if (collected.size >= 10) break
        }
        return collected.take(10)
    }

    private suspend fun fetchGenrePool(
        availabilityFilter: AvailabilityFilter,
        fetchPage: suspend (Int) -> List<UnifiedMediaItem>
    ): List<UnifiedMediaItem> {
        val maxPages = if (availabilityFilter == AvailabilityFilter.ALL) 1 else 3
        val collected = mutableListOf<UnifiedMediaItem>()
        val seenKeys = mutableSetOf<String>()
        for (page in 1..maxPages) {
            val raw = fetchPage(page)
            if (raw.isEmpty()) break
            raw.filter(availabilityFilter::matches).forEach { item ->
                val key = "${item.mediaType}_${item.tmdbId}_${item.id}"
                if (seenKeys.add(key)) {
                    collected += item
                }
            }
            if (collected.size >= 20) break
        }
        return collected
    }

    private fun isReleasedOnOrBefore(dateStr: String?, cutoff: LocalDate): Boolean {
        // No (or an unreadable) date means TMDB doesn't know when it comes out: not released.
        val prefix = dateStr?.trim()?.take(10)
        if (prefix.isNullOrBlank()) return false
        val parsed = runCatchingCancellable { LocalDate.parse(prefix) }.getOrNull() ?: return false
        return !parsed.isAfter(cutoff)
    }

    suspend fun loadTrendingTitles(
        userId: String,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> = coroutineScope {
        ensureLibraryIndex(userId)
        if (!isSeerrEnabled) {
            return@coroutineScope ContentRatingHelper.filterForProfile(
                withOverviews(
                    userId,
                    allLibraryItems.asSequence().filter { it.allowedFor(activeProfile) }.take(20).toList()
                ).map { mapJellyfinItem(it) },
                activeProfile
            )
        }
        val raw = runCatchingCancellable { seerrApi.getTrending(language = metaLang).results }
            .getOrDefault(emptyList())
            .mapNotNull { res ->
                val type = when (res.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> null
                } ?: return@mapNotNull null
                mapSeerrResult(res, type)
            }
        ContentRatingHelper.filterForProfile(raw, activeProfile)
    }

    suspend fun loadByGenre(
        userId: String,
        genre: GenreFilterOption,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> = coroutineScope {
        ensureLibraryIndex(userId)
        if (!isSeerrEnabled) {
            val matches = withOverviews(userId, allLibraryItems.filter { matchesGenreLabel(it, genre) })
                .map { mapJellyfinItem(it) }
            return@coroutineScope ContentRatingHelper.filterForProfile(matches, activeProfile)
        }
        val today = LocalDate.now()
        val movieDeferred = async {
            genre.movieGenreId?.let { id -> discoverReleasedByGenre(MediaType.MOVIE, id, 1, today) }.orEmpty()
        }
        val tvDeferred = async {
            genre.tvGenreId?.let { id -> discoverReleasedByGenre(MediaType.SERIES, id, 1, today) }.orEmpty()
        }
        val movies = movieDeferred.await()
        val tv = tvDeferred.await()
        ContentRatingHelper.filterForProfile(
            (movies + tv).distinctBy { "${it.mediaType}_${it.tmdbId}" },
            activeProfile
        )
    }

    suspend fun loadFullDetails(
        userId: String,
        tmdbId: Int?,
        jellyfinId: String?,
        mediaType: MediaType,
        forceRefresh: Boolean = false
    ): Result<UnifiedMediaItem> = runCatchingCancellable {
        val cacheKey = "${userId}_${mediaType.name}_${tmdbId ?: ""}_${jellyfinId ?: ""}"
        // forceRefresh: live status polling must not be served the 120 s cached copy.
        if (!forceRefresh) getCachedDetails(cacheKey)?.let { return@runCatchingCancellable it }

        coroutineScope {
            // A cold deep link / voice search can land here before anything loaded the index, and an
            // owned title would then look requestable. Overlaps the Jellyfin/Seerr requests below.
            val indexReady = async { runCatchingCancellable { ensureLibraryIndex(userId) } }
            val resolvedType = if (mediaType == MediaType.EPISODE) MediaType.SERIES else mediaType
            // When the TMDB id is already known, start the Seerr lookup now instead of after the
            // Jellyfin round trip(s) below; they're independent.
            val earlySeerrTmdbId = if (mediaType != MediaType.EPISODE && isSeerrEnabled) tmdbId else null
            val earlySeerrMovie = if (earlySeerrTmdbId != null && resolvedType == MediaType.MOVIE) {
                async { runCatchingCancellable { seerrApi.getMovieDetails(earlySeerrTmdbId, language = metaLang) } }
            } else null
            val earlySeerrTv = if (earlySeerrTmdbId != null && resolvedType == MediaType.SERIES) {
                async { runCatchingCancellable { seerrApi.getTvDetails(earlySeerrTmdbId, language = metaLang) } }
            } else null
            // A title that is on several servers is opened as its preferred copy (the home
            // server's when it has one); if that server can't be reached, the copy that was asked for.
            val preferredId = jellyfinId?.let { links.canonical(it) }
            var jfItem: JellyfinItemDto? = preferredId?.let { id -> fetchItem(userId, id) }
                ?: jellyfinId?.takeIf { it != preferredId }?.let { id -> fetchItem(userId, id) }
            if (jfItem?.type.equals("Episode", ignoreCase = true) && !jfItem?.seriesId.isNullOrBlank()) {
                val seriesId = jfItem!!.seriesId!!
                val preferredSeriesId = links.canonical(seriesId)
                val parentSeries = fetchItem(userId, preferredSeriesId)
                    ?: seriesId.takeIf { it != preferredSeriesId }?.let { fetchItem(userId, it) }
                if (parentSeries != null) {
                    jfItem = parentSeries
                }
            }
            val resolvedTmdbId = (if (mediaType == MediaType.EPISODE) null else tmdbId)
                ?: jfItem?.providerIds?.get("Tmdb")?.toIntOrNull()
                ?: jfItem?.providerIds?.get("tmdb")?.toIntOrNull()
                ?: tmdbId

            // (links.canonical above may still see no index: it then simply opens the requested copy.)
            indexReady.await()
            if (jfItem == null && resolvedTmdbId != null) {
                jfItem = if (resolvedType == MediaType.MOVIE) {
                    tmdbToJellyfinMovie[resolvedTmdbId]
                } else {
                    tmdbToJellyfinSeries[resolvedTmdbId]
                }
            }

            // The same title on the other servers: extra versions, and possibly newer watch state.
            val otherCopiesDeferred = async {
                jfItem?.let { item -> copies?.others(item, userId, itemFields = "MediaSources") }.orEmpty()
            }

            val extrasDeferred = async {
                jfItem?.id?.let { id ->
                    sessionFor(id, userId)?.let { session ->
                        onServer(session) { s -> s.api.getSpecialFeatures(s.raw(id), s.userId) }?.scopedTo(session.id)
                    }
                }.orEmpty().map { extra ->
                    ExtrasItem(
                        id = extra.id,
                        name = extra.name,
                        overview = extra.overview.orEmpty(),
                        extraType = extra.extraType,
                        runtimeMinutes = extra.runTimeTicks?.let { (it / 600_000_000L).toInt() },
                        thumbnailUrl = primaryImage(jfBaseUrl, extra.id, 480)
                    )
                }
            }

            val localTrailerDeferred = async {
                jfItem?.id?.let { id ->
                    sessionFor(id, userId)?.let { session ->
                        onServer(session) { s -> s.api.getLocalTrailers(s.raw(id), s.userId) }
                            ?.firstOrNull()?.id?.let { session.scope(it) }
                    }
                }
            }

            // Seasons only depend on the resolved Jellyfin series; fetch them alongside Seerr.
            val jfSeasonsDeferred = async {
                val id = jfItem?.id
                if (resolvedType == MediaType.SERIES && id != null) {
                    seasonsAcrossServers(userId, id)
                } else emptyList()
            }

            // Watch state (resume position, played, favourite) follows the most recently played copy.
            val otherCopies = otherCopiesDeferred.await()
            if (otherCopies.isNotEmpty()) {
                jfItem = jfItem?.let { item ->
                    item.copy(
                        userData = CrossServerMerge.mergeUserData(
                            listOf(item.userData) + otherCopies.map { it.item.userData }
                        )
                    )
                }
            }

            val seerrMergedItem: UnifiedMediaItem? = if (resolvedTmdbId != null && isSeerrEnabled) {
                if (resolvedType == MediaType.MOVIE) {
                    val seerrMovieResult = if (earlySeerrMovie != null && earlySeerrTmdbId == resolvedTmdbId) {
                        earlySeerrMovie.await()
                    } else runCatchingCancellable {
                        seerrApi.getMovieDetails(resolvedTmdbId, language = metaLang)
                    }
                    val seerrMovie = seerrMovieResult.getOrNull()
                    if (seerrMovie != null) {
                        mergeMovieDetails(seerrMovie, jfItem, localTrailerDeferred.await())
                    } else if (jfItem == null) {
                        throw IllegalStateException(
                            formatHttpError(
                                seerrMovieResult.exceptionOrNull() ?: IllegalStateException("Failed to load movie details"),
                                "Failed to load movie details"
                            )
                        )
                    } else {
                        null
                    }
                } else {
                    val seerrTvResult = if (earlySeerrTv != null && earlySeerrTmdbId == resolvedTmdbId) {
                        earlySeerrTv.await()
                    } else runCatchingCancellable {
                        seerrApi.getTvDetails(resolvedTmdbId, language = metaLang)
                    }
                    val seerrTv = seerrTvResult.getOrNull()
                    if (seerrTv != null) {
                        val jfSeasons = jfSeasonsDeferred.await()
                        mergeTvDetails(seerrTv, jfItem, jfSeasons, localTrailerDeferred.await())
                    } else if (jfItem == null) {
                        throw IllegalStateException(
                            formatHttpError(
                                seerrTvResult.exceptionOrNull() ?: IllegalStateException("Failed to load series details"),
                                "Failed to load series details"
                            )
                        )
                    } else {
                        null
                    }
                }
            } else null

            val baseItem = seerrMergedItem ?: if (jfItem != null) {
                val mapped = mapJellyfinItem(jfItem)
                val localTrailerId = localTrailerDeferred.await()
                if (resolvedType == MediaType.SERIES) {
                    val jfSeasons = jfSeasonsDeferred.await()
                    val mappedSeasons = jfSeasons.map { s ->
                        SeasonSummary(
                            seasonNumber = s.indexNumber ?: 1,
                            name = s.name,
                            episodeCount = s.childCount ?: 0,
                            overview = s.overview,
                            posterUrl = primaryImage(jfBaseUrl, s.id),
                            hdStatus = AvailabilityTier.AVAILABLE,
                            jellyfinSeasonId = s.id
                        )
                    }.sortedBy { it.seasonNumber }
                    mapped.copy(seasons = mappedSeasons, localTrailerItemId = localTrailerId)
                } else {
                    mapped.copy(localTrailerItemId = localTrailerId)
                }
            } else {
                error("Missing both TMDB ID and Jellyfin ID")
            }

            val isInMyList = if (jfItem != null) {
                jfItem.userData?.isFavorite == true
            } else if (baseItem.tmdbId != null) {
                prefs.isInLocalMyList(userId, baseItem.tmdbId, baseItem.mediaType)
            } else {
                false
            }
            val isReminderSet = baseItem.tmdbId?.let {
                prefs.isReminderSet(userId, it, baseItem.mediaType)
            } ?: false

            val mediaVersions = buildMediaVersions(jfItem, otherCopies)
            val primarySource = jfItem?.mediaSources?.firstOrNull()
            val streams = primarySource?.mediaStreams?.takeIf { it.isNotEmpty() } ?: jfItem?.mediaStreams.orEmpty()
            val mediaStreamSpecs = streams.map { s ->
                val res = if (s.type.equals("Video", ignoreCase = true) && s.width != null && s.height != null) {
                    "${s.width}x${s.height}"
                } else null
                val hdr = if (s.type.equals("Video", ignoreCase = true)) {
                    when {
                        s.videoRangeType?.contains("DOVI", ignoreCase = true) == true -> "Dolby Vision"
                        s.videoRangeType?.contains("HDR10Plus", ignoreCase = true) == true -> "HDR10+"
                        s.videoRangeType?.contains("HLG", ignoreCase = true) == true -> "HLG"
                        s.videoRange?.equals("HDR", ignoreCase = true) == true -> "HDR10"
                        else -> s.videoRange?.takeIf { !it.equals("SDR", ignoreCase = true) }
                    }
                } else null
                MediaStreamSpec(
                    type = s.type,
                    title = s.displayTitle ?: s.title ?: s.codec?.uppercase() ?: s.type,
                    codec = s.codec?.uppercase(),
                    language = s.language?.uppercase(),
                    channels = s.channels,
                    resolution = res,
                    hdrFormat = hdr,
                    bitrate = s.bitRate,
                    bitDepth = s.bitDepth,
                    isDefault = s.isDefault,
                    isForced = s.isForced
                )
            }
            val audioTracks = streams
                .filter { it.type.equals("Audio", ignoreCase = true) }
                .map { s ->
                    TrackOption(
                        index = s.index,
                        label = s.displayTitle ?: s.title ?: s.language?.uppercase() ?: "Audio ${s.index}",
                        language = s.language,
                        codec = s.codec,
                        isDefault = s.isDefault,
                        isForced = s.isForced,
                        isHearingImpaired = s.isHearingImpaired,
                        channels = s.channels,
                        bitRate = s.bitRate,
                        sampleRate = s.sampleRate
                    )
                }
            val subtitleTracks = streams
                .filter { it.type.equals("Subtitle", ignoreCase = true) }
                .map { s ->
                    TrackOption(
                        index = s.index,
                        label = s.displayTitle ?: s.title ?: s.language?.uppercase() ?: "Subtitle ${s.index}",
                        language = s.language,
                        codec = s.codec,
                        isExternal = s.isExternal,
                        isDefault = s.isDefault,
                        isForced = s.isForced,
                        isHearingImpaired = s.isHearingImpaired
                    )
                }
            val jfPersonByName = jfItem?.people.orEmpty()
                .mapNotNull { p ->
                    val rawPid = p.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val scopedPid = ServerScopedId.scope(ServerScopedId.serverOf(jfItem!!.id), rawPid)
                    p.name.trim().lowercase() to scopedPid
                }.toMap()
            val enrichedCast = baseItem.cast.map { member ->
                if (member.jellyfinPersonId != null) member
                else member.copy(jellyfinPersonId = jfPersonByName[member.name.trim().lowercase()])
            }

            earlySeerrMovie?.cancel()
            earlySeerrTv?.cancel()
            baseItem.copy(
                isInMyList = isInMyList,
                isReminderSet = isReminderSet,
                mediaVersions = mediaVersions,
                extras = extrasDeferred.await(),
                cast = enrichedCast,
                canDelete = jfItem?.canDelete == true,
                mediaFilePath = primarySource?.path ?: jfItem?.path,
                mediaContainer = primarySource?.container ?: jfItem?.container,
                mediaFileSizeBytes = primarySource?.size,
                mediaBitrate = primarySource?.bitrate,
                mediaStreamSpecs = mediaStreamSpecs,
                availableAudioTracks = audioTracks,
                availableSubtitleTracks = subtitleTracks
            ).also { resultItem ->
                putCachedDetails(cacheKey, resultItem)
            }
        }
    }

    suspend fun loadSeasonEpisodes(
        userId: String,
        tmdbId: Int?,
        jellyfinSeriesId: String?,
        jellyfinSeasonId: String?,
        seasonNumber: Int
    ): List<EpisodeSummary> = coroutineScope {
        val jfEpisodesDeferred = async {
            if (jellyfinSeriesId != null) {
                episodesAcrossServers(userId, jellyfinSeriesId, jellyfinSeasonId, seasonNumber)
            } else emptyList()
        }
        val seerrSeasonDeferred = async {
            if (isSeerrEnabled && tmdbId != null) {
                runCatchingCancellable { seerrApi.getTvSeasonDetails(tmdbId, seasonNumber, language = metaLang) }.getOrNull()
            } else null
        }

        val jfEpisodes = jfEpisodesDeferred.await()
        val jfByEpNum = jfEpisodes.associateBy { it.indexNumber ?: -1 }
        val seerrEps = seerrSeasonDeferred.await()?.episodes.orEmpty()
        val jfBase = jfBaseUrl
        fun fromJellyfin(jf: JellyfinItemDto) = EpisodeSummary(
            id = jf.id,
            name = jf.name,
            overview = jf.overview.orEmpty(),
            seasonNumber = jf.parentIndexNumber ?: seasonNumber,
            episodeNumber = jf.indexNumber ?: 0,
            runtimeTicks = jf.runTimeTicks ?: 0L,
            resumePositionTicks = jf.userData?.playbackPositionTicks ?: 0L,
            stillUrl = primaryImage(jfBase, jf.id, 480),
            isAvailableInJellyfin = true,
            isWatched = jf.userData?.played == true
        )

        if (seerrEps.isNotEmpty()) {
            val seerrNumbers = seerrEps.mapTo(HashSet()) { it.episodeNumber }
            val fromSeerr = seerrEps.map { ep ->
                val jf = jfByEpNum[ep.episodeNumber]
                EpisodeSummary(
                    id = jf?.id ?: "tmdb_${tmdbId}_s${seasonNumber}_e${ep.episodeNumber}",
                    name = jf?.name ?: ep.name ?: "Episode ${ep.episodeNumber}",
                    overview = jf?.overview ?: ep.overview.orEmpty(),
                    seasonNumber = seasonNumber,
                    episodeNumber = ep.episodeNumber,
                    runtimeTicks = jf?.runTimeTicks ?: 0L,
                    resumePositionTicks = jf?.userData?.playbackPositionTicks ?: 0L,
                    stillUrl = jf?.let { primaryImage(jfBase, it.id, 480) }
                        ?: MediaImageUrls.tmdbBackdrop(ep.stillPath),
                    isAvailableInJellyfin = jf != null,
                    isWatched = jf?.userData?.played == true
                )
            }
            // Episodes TMDB doesn't list (extras, specials numbered differently) are still on the
            // server and playable: keep them instead of hiding them behind Seerr's list.
            val jellyfinOnly = jfEpisodes.filter { it.indexNumber == null || it.indexNumber !in seerrNumbers }
            if (jellyfinOnly.isEmpty()) fromSeerr
            else (fromSeerr + jellyfinOnly.map(::fromJellyfin)).sortedBy { it.episodeNumber }
        } else {
            jfEpisodes.map(::fromJellyfin)
        }
    }

    suspend fun loadRecommendations(
        userId: String,
        tmdbId: Int?,
        jellyfinId: String?,
        mediaType: MediaType,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> = coroutineScope {
        // Seerr results are matched to owned titles through the index (see loadFullDetails).
        val indexReady = async { runCatchingCancellable { ensureLibraryIndex(userId) } }
        if (isSeerrEnabled && tmdbId != null) {
            val results = runCatchingCancellable {
                if (mediaType == MediaType.MOVIE) {
                    seerrApi.getMovieRecommendations(tmdbId, language = metaLang).results
                } else {
                    seerrApi.getTvRecommendations(tmdbId, language = metaLang).results
                }
            }.getOrDefault(emptyList())
            if (results.isNotEmpty()) {
                indexReady.await()
                return@coroutineScope ContentRatingHelper.filterForProfile(
                    results.take(15).map { mapSeerrResult(it, mediaType) },
                    activeProfile
                )
            }
        }
        if (jellyfinId != null) {
            val similarDtos = sessionFor(jellyfinId, userId)?.let { session ->
                onServer(session) { s -> s.api.getSimilarItems(s.raw(jellyfinId), s.userId).items }
                    ?.scopedTo(session.id)
            }.orEmpty()
            indexReady.await()
            return@coroutineScope ContentRatingHelper.filterForProfile(similarDtos.map { mapJellyfinItem(it) }, activeProfile)
        }
        emptyList()
    }

    suspend fun searchUnified(
        userId: String,
        query: String,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> =
        searchUnifiedWithPeople(userId, query, activeProfile).first

    suspend fun searchUnifiedWithPeople(
        userId: String,
        query: String,
        activeProfile: UserProfile? = null
    ): Pair<List<UnifiedMediaItem>, List<PersonSearchResult>> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList<UnifiedMediaItem>() to emptyList()
        // Seerr results are matched to owned titles through the index (see loadFullDetails).
        val indexReady = async { runCatchingCancellable { ensureLibraryIndex(userId) } }
        val seerrDeferred = async {
            if (isSeerrEnabled) runCatchingCancellable { seerrApi.search(query, language = metaLang).results }.getOrDefault(emptyList())
            else emptyList()
        }
        val jfDeferred = async {
            runCatchingCancellable { searchDtos(userId, query) }.getOrDefault(emptyList())
        }
        val jfPersonsDeferred = async {
            if (isSeerrEnabled) emptyList()
            else runCatchingCancellable {
                mergedItems(
                    extra = { s -> s.api.getPersons(searchTerm = query, limit = 15).items },
                    home = { jellyfinApi.getPersons(searchTerm = query, limit = 15).items }
                )
            }.getOrDefault(emptyList())
        }

        val rawSeerr = seerrDeferred.await()
        val seerrPeople = rawSeerr
            .filter { it.mediaType == "person" }
            .map { person ->
                PersonSearchResult(
                    id = person.id,
                    name = person.name ?: person.title ?: "Person",
                    department = person.knownForDepartment,
                    imageUrl = MediaImageUrls.tmdbProfile(person.profilePath)
                )
            }

        val people = if (seerrPeople.isNotEmpty()) {
            seerrPeople
        } else {
            val base = jfBaseUrl
            val jfPersons = if (!isSeerrEnabled) {
                jfPersonsDeferred.await()
            } else {
                // Even if Seerr is enabled, fall back to Jellyfin /Persons when Seerr returned no people.
                runCatchingCancellable {
                    mergedItems(
                        extra = { s -> s.api.getPersons(searchTerm = query, limit = 15).items },
                        home = { jellyfinApi.getPersons(searchTerm = query, limit = 15).items }
                    )
                }.getOrDefault(emptyList())
            }
            jfPersons
                .distinctBy { it.name.trim().lowercase() }
                .map { dto ->
                    PersonSearchResult(
                        id = dto.id.hashCode().let { if (it == 0) 1 else kotlin.math.abs(it) },
                        name = dto.name.ifBlank { "Person" },
                        department = dto.type.takeIf { it.isNotBlank() && !it.equals("Person", ignoreCase = true) },
                        imageUrl = jellyfinPrimaryUrl(base, dto, maxWidth = 240),
                        jellyfinPersonId = dto.id
                    )
                }
        }

        indexReady.await()
        val seerrItems = rawSeerr.mapNotNull { res ->
            val type = when (res.mediaType) {
                "movie" -> MediaType.MOVIE
                "tv" -> MediaType.SERIES
                else -> null
            } ?: return@mapNotNull null
            mapSeerrResult(res, type)
        }
        // TMDB ids are per media type: a movie and a show (or an episode) can share a number.
        val seenTmdb = seerrItems.mapNotNull { item -> item.tmdbId?.let { "${item.mediaType}_$it" } }.toSet()
        val jfExtras = jfDeferred.await()
            .map { mapJellyfinItem(it) }
            .filter { it.tmdbId == null || "${it.mediaType}_${it.tmdbId}" !in seenTmdb }

        ContentRatingHelper.filterForProfile(seerrItems + jfExtras, activeProfile) to people
    }

    suspend fun loadPersonCredits(
        userId: String,
        personId: Int? = null,
        jellyfinPersonId: String? = null,
        personName: String? = null,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> = coroutineScope {
        // 1. Prefer Seerr / TMDB credits when Seerr is configured and we have a numeric TMDB person ID
        //    (full filmography, requestable). The Jellyfin person id is only the fallback below.
        if (isSeerrEnabled && personId != null && personId > 0) {
            ensureLibraryIndex(userId)
            val credits = runCatchingCancellable { seerrApi.getPersonCredits(personId, language = metaLang) }.getOrNull()
            if (credits != null) {
                val combined = (credits.cast + credits.crew)
                    .mapNotNull { res ->
                        val type = when (res.mediaType) {
                            "movie" -> MediaType.MOVIE
                            "tv" -> MediaType.SERIES
                            else -> null
                        } ?: return@mapNotNull null
                        mapSeerrResult(res, type)
                    }
                    .distinctBy { "${it.mediaType}_${it.tmdbId}" }
                if (combined.isNotEmpty()) {
                    return@coroutineScope ContentRatingHelper.filterForProfile(combined, activeProfile)
                }
            }
        }

        // 2. Jellyfin-native Person filmography via /Users/{userId}/Items?PersonIds=...
        val resolvedJfPersonId = jellyfinPersonId?.takeIf { it.isNotBlank() }
            ?: personName?.takeIf { it.isNotBlank() }?.let { name ->
                runCatchingCancellable {
                    jellyfinApi.getPersons(searchTerm = name, limit = 5).items
                        .firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                        ?: jellyfinApi.getPersons(searchTerm = name, limit = 1).items.firstOrNull()?.id
                }.getOrNull()
            }
            ?: return@coroutineScope emptyList()

        val owningSession = sessionFor(resolvedJfPersonId, userId) ?: homeSession(userId)
        val owningItemsDeferred = async {
            onServer(owningSession, ROW_EXTRA_TIMEOUT_MS) { s ->
                s.api.getItemsByPerson(userId = s.userId, personIds = s.raw(resolvedJfPersonId)).items
            }.orEmpty().scopedTo(owningSession.id)
        }
        // If multiple servers are connected and we know the person's name, also pull matching credits from other servers.
        val otherServerItemsDeferred = async {
            val name = personName?.takeIf { it.isNotBlank() } ?: return@async emptyList<List<JellyfinItemDto>>()
            val extras = servers?.availableExtras().orEmpty()
            if (extras.isEmpty()) return@async emptyList()
            val otherSessions = buildList {
                if (!owningSession.isHome) add(homeSession(userId))
                extras.filter { it.id != owningSession.id }.forEach(::add)
            }
            otherSessions.map { session ->
                async {
                    // Extra credits only: a slow / offline server is time-boxed or skipped.
                    onServerForRow(session) { s ->
                        val pid = s.api.getPersons(searchTerm = name, limit = 5).items
                            .firstOrNull { it.name.equals(name, ignoreCase = true) }?.id
                            ?: return@onServerForRow emptyList()
                        s.api.getItemsByPerson(userId = s.userId, personIds = pid).items
                    }.orEmpty().scopedTo(session.id)
                }
            }.awaitAll()
        }
        val mergedDtos = CrossServerMerge.mergeItems(
            perServer = listOf(owningItemsDeferred.await()) + otherServerItemsDeferred.await(),
            links = links,
            order = CrossServerMerge.Order.SERVER
        )
        val mapped = mergedDtos.map { mapJellyfinItem(it) }.distinctBy { it.id }
        ContentRatingHelper.filterForProfile(mapped, activeProfile)
    }

    suspend fun has4kServerConfigured(mediaType: MediaType): Boolean = runCatchingCancellable {
        if (!isSeerrEnabled) return@runCatchingCancellable false
        val now = System.currentTimeMillis()
        if (now - last4kCheckTimestampMs < 300_000L) {
            val cached = if (mediaType == MediaType.MOVIE) cachedHas4kMovie else cachedHas4kTv
            if (cached != null) return@runCatchingCancellable cached
        }
        val servers = if (mediaType == MediaType.MOVIE) {
            seerrApi.getRadarrServers()
        } else {
            seerrApi.getSonarrServers()
        }
        val has4k = servers.any { it.is4k }
        if (mediaType == MediaType.MOVIE) {
            cachedHas4kMovie = has4k
        } else {
            cachedHas4kTv = has4k
        }
        last4kCheckTimestampMs = now
        has4k
    }.getOrDefault(false)

    suspend fun loadArrServers(mediaType: MediaType): List<ArrServerOption> = coroutineScope {
        if (!isSeerrEnabled) return@coroutineScope emptyList()
        val servers = runCatchingCancellable {
            if (mediaType == MediaType.MOVIE) seerrApi.getRadarrServers() else seerrApi.getSonarrServers()
        }.getOrDefault(emptyList())

        servers.map { srv ->
            async {
                val detail = runCatchingCancellable {
                    if (mediaType == MediaType.MOVIE) {
                        seerrApi.getRadarrServerDetails(srv.id)
                    } else {
                        seerrApi.getSonarrServerDetails(srv.id)
                    }
                }.getOrNull()
                val profiles = (detail?.profiles?.takeIf { it.isNotEmpty() } ?: srv.profiles)
                    .map { ArrQualityProfile(id = it.id, name = it.name.ifBlank { "Profile ${it.id}" }) }
                val roots = (detail?.rootFolders?.takeIf { it.isNotEmpty() } ?: srv.rootFolders)
                    .mapNotNull { it.path.takeIf { p -> p.isNotBlank() } }
                    .ifEmpty { listOfNotNull(srv.activeDirectory?.takeIf { it.isNotBlank() }) }
                ArrServerOption(
                    id = srv.id,
                    name = srv.name ?: if (srv.is4k) "4K Server" else "HD Server",
                    is4k = srv.is4k,
                    isDefault = srv.isDefault,
                    profiles = profiles,
                    rootFolders = roots
                )
            }
        }.map { it.await() }
    }

    suspend fun submitRequest(
        tmdbId: Int,
        mediaType: MediaType,
        is4k: Boolean,
        seasons: List<Int>? = null,
        serverId: Int? = null,
        profileId: Int? = null,
        rootFolder: String? = null
    ): Result<Unit> = runCatchingCancellable {
        check(isSeerrEnabled) { "Seerr is not configured (Jellyfin-Only Mode)" }
        invalidateDetailsCache()
        val resolvedSeasons = if (mediaType == MediaType.SERIES) {
            // A failed lookup fails the request: silently asking for season 1 only looked like success.
            seasons?.takeIf { it.isNotEmpty() } ?: seerrApi.getTvDetails(tmdbId, language = metaLang).seasons
                .map { it.seasonNumber }
                .filter { it > 0 }
                .ifEmpty { listOf(1) }
        } else null

        seerrApi.createRequest(
            SeerrCreateRequestBody(
                mediaType = if (mediaType == MediaType.MOVIE) "movie" else "tv",
                mediaId = tmdbId,
                is4k = is4k,
                seasons = resolvedSeasons,
                serverId = serverId,
                profileId = profileId,
                rootFolder = rootFolder
            )
        )
        Unit
    }.recoverCatching { throw IllegalStateException(formatHttpError(it, "Request failed"), it) }

    suspend fun approveRequest(requestId: Int): Result<Unit> = runCatchingCancellable {
        check(isSeerrEnabled) { "Seerr is not configured" }
        seerrApi.approveRequest(requestId)
        invalidateDetailsCache()
    }.recoverCatching { throw IllegalStateException(formatHttpError(it, "Approve failed"), it) }

    suspend fun declineRequest(requestId: Int): Result<Unit> = runCatchingCancellable {
        check(isSeerrEnabled) { "Seerr is not configured" }
        seerrApi.declineRequest(requestId)
        invalidateDetailsCache()
    }.recoverCatching { throw IllegalStateException(formatHttpError(it, "Decline failed"), it) }

    suspend fun cancelRequest(requestId: Int): Result<Unit> = runCatchingCancellable {
        check(isSeerrEnabled) { "Seerr is not configured" }
        seerrApi.deleteRequest(requestId)
        invalidateDetailsCache()
    }.recoverCatching { throw IllegalStateException(formatHttpError(it, "Cancel failed"), it) }

    suspend fun reportIssue(
        tmdbId: Int,
        mediaType: MediaType,
        issueType: SeerrIssueType,
        message: String
    ): Result<Unit> = runCatchingCancellable {
        check(isSeerrEnabled) { "Seerr is not configured" }
        val seerrMediaId = if (mediaType == MediaType.MOVIE) {
            seerrApi.getMovieDetails(tmdbId, language = metaLang).mediaInfo?.id
        } else {
            seerrApi.getTvDetails(tmdbId, language = metaLang).mediaInfo?.id
        } ?: error("This item does not have an active Seerr media record yet")

        seerrApi.createIssue(
            SeerrCreateIssueBody(
                issueType = issueType.code,
                message = message.trim().ifBlank { issueType.label },
                mediaId = seerrMediaId
            )
        )
    }.recoverCatching { throw IllegalStateException(formatHttpError(it, "Failed to report issue"), it) }

    /**
     * Number of requests that are pending/processing or downloading (Home's nav badge). Same rule as
     * counting [loadRequestsHub] results, but from the single requests page: the request's `media`
     * already carries status + downloadStatus, so the per-request title/poster detail lookups
     * (up to 50 extra Seerr calls) are skipped.
     */
    suspend fun loadActiveDownloadCount(): Int {
        if (!isSeerrEnabled) return 0
        val page = runCatchingCancellable {
            seerrApi.getRequests(take = 50, skip = 0, filter = "all", sort = "added")
        }.getOrNull() ?: return 0
        return page.results.count { req ->
            val downloads = if (req.is4k) req.media?.downloadStatus4k else req.media?.downloadStatus
            DownloadProgressParser.parse(downloads.orEmpty(), is4k = req.is4k) != null ||
                AvailabilityTier.fromSeerrCode(
                    if (req.is4k) req.media?.status4k else req.media?.status
                ).isRequestedOrDownloading
        }
    }

    /** A request's media status only, from the single requests page (no per-title lookups). */
    data class RequestStatus(val requestId: Int, val mediaStatus: AvailabilityTier)

    /**
     * The status of the latest requests with one Seerr call. The background "request is ready"
     * check uses this first and only loads titles / posters (and the library index) for the few
     * requests that actually became available.
     */
    suspend fun loadRequestStatuses(): Result<List<RequestStatus>> = runCatchingCancellable {
        if (!isSeerrEnabled) return@runCatchingCancellable emptyList()
        seerrApi.getRequests(take = 50, skip = 0, filter = "all", sort = "added").results.map { req ->
            RequestStatus(
                requestId = req.id,
                mediaStatus = AvailabilityTier.fromSeerrCode(if (req.is4k) req.media?.status4k else req.media?.status)
            )
        }
    }

    /** [onlyRequestIds]: look up titles / posters for just these requests (null = all). */
    suspend fun loadRequestsHub(onlyRequestIds: Set<Int>? = null): Result<List<MediaRequestSummary>> = runCatchingCancellable {
        if (!isSeerrEnabled) return@runCatchingCancellable emptyList()
        val page = seerrApi.getRequests(take = 50, skip = 0, filter = "all", sort = "added")
        val wanted = if (onlyRequestIds == null) page.results else page.results.filter { it.id in onlyRequestIds }
        coroutineScope {
            wanted.map { req ->
                async {
                    val tmdbId = req.media?.tmdbId ?: 0
                    val isMovie = when (req.media?.mediaType?.lowercase()) {
                        "movie" -> true
                        "tv" -> false
                        else -> req.seasons.isEmpty() && (req.media?.tvdbId == null)
                    }
                    val type = if (isMovie) MediaType.MOVIE else MediaType.SERIES
                    var title = "Request #${req.id}"
                    var posterPath: String? = null
                    var backdropPath: String? = null
                    var downloadProgress = if (req.is4k) {
                        DownloadProgressParser.parse(req.media?.downloadStatus4k.orEmpty(), is4k = true)
                    } else {
                        DownloadProgressParser.parse(req.media?.downloadStatus.orEmpty(), is4k = false)
                    }

                    if (tmdbId > 0) {
                        if (type == MediaType.MOVIE) {
                            runCatchingCancellable { seerrApi.getMovieDetails(tmdbId, language = metaLang) }.getOrNull()?.let { m ->
                                title = m.title
                                posterPath = m.posterPath
                                backdropPath = m.backdropPath
                                if (downloadProgress == null) {
                                    val list = if (req.is4k) m.mediaInfo?.downloadStatus4k else m.mediaInfo?.downloadStatus
                                    downloadProgress = DownloadProgressParser.parse(list.orEmpty(), req.is4k)
                                }
                            }
                        } else {
                            runCatchingCancellable { seerrApi.getTvDetails(tmdbId, language = metaLang) }.getOrNull()?.let { tv ->
                                title = tv.name
                                posterPath = tv.posterPath
                                backdropPath = tv.backdropPath
                                if (downloadProgress == null) {
                                    val list = if (req.is4k) tv.mediaInfo?.downloadStatus4k else tv.mediaInfo?.downloadStatus
                                    downloadProgress = DownloadProgressParser.parse(list.orEmpty(), req.is4k)
                                }
                            }
                        }
                    }

                    val mediaStatus = AvailabilityTier.fromSeerrCode(
                        if (req.is4k) req.media?.status4k else req.media?.status
                    )

                    MediaRequestSummary(
                        requestId = req.id,
                        tmdbId = tmdbId,
                        mediaType = type,
                        title = title,
                        posterUrl = MediaImageUrls.tmdbPoster(posterPath),
                        backdropUrl = MediaImageUrls.tmdbBackdrop(backdropPath),
                        is4k = req.is4k,
                        requestStatus = req.status,
                        mediaStatus = mediaStatus,
                        requestedBy = req.requestedBy?.displayName
                            ?: req.requestedBy?.jellyfinUsername
                            ?: req.requestedBy?.username
                            ?: "User",
                        createdAt = req.createdAt.orEmpty(),
                        downloadProgress = downloadProgress,
                        requestedByJellyfinUsername = req.requestedBy?.jellyfinUsername
                    )
                }
            }.map { it.await() }
        }
    }.recoverCatching { throw IllegalStateException(formatHttpError(it, "Failed to load requests"), it) }

    suspend fun loadLiveTvChannelsAndPrograms(userId: String): List<LiveTvChannel> = coroutineScope {
        val nowInstant = Instant.now()
        val minEndIso = DateTimeFormatter.ISO_INSTANT.format(nowInstant)
        val maxStartIso = DateTimeFormatter.ISO_INSTANT.format(nowInstant.plusSeconds(36L * 3600L))
        val channelsDeferred = async {
            runCatchingCancellable {
                jellyfinApi.getLiveTvChannels(userId = userId, enableFavoriteSorting = true).items
            }.getOrDefault(emptyList())
        }
        // One request is sorted by start time across *all* channels, so a single page of a
        // few hundred programmes only covers the first few hours on a large lineup. Page
        // through the whole 36-hour window. The cap grows with the lineup (a fixed cap left
        // the later hours empty on every channel of a big guide) but stays bounded, so a huge
        // IPTV guide can't run forever or exhaust memory.
        suspend fun page(startIndex: Int, withTotal: Boolean) = runCatchingCancellable {
            jellyfinApi.getLiveTvPrograms(
                userId = userId,
                hasAired = false,
                limit = LIVE_TV_PROGRAM_PAGE,
                startIndex = startIndex.takeIf { it > 0 },
                minEndDate = minEndIso,
                maxStartDate = maxStartIso,
                sortBy = "StartDate",
                enableTotalRecordCount = withTotal
            )
        }.getOrNull()
        // The first page (it brings the total) goes out alongside the channel list.
        val firstPageDeferred = async { page(0, withTotal = true) }
        val channels = channelsDeferred.await()
        val programs = run {
            val first = firstPageDeferred.await() ?: return@run emptyList<JellyfinItemDto>()
            val collected = first.items.toMutableList()
            if (first.items.size >= LIVE_TV_PROGRAM_PAGE) {
                val cap = liveTvProgramCap(channels.size)
                val end = minOf(first.totalRecordCount, cap)
                if (end > first.items.size) {
                    // The rest in parallel, a few requests at a time.
                    val gate = Semaphore(LIVE_TV_PROGRAM_PARALLEL_PAGES)
                    (first.items.size until end step LIVE_TV_PROGRAM_PAGE)
                        .map { start -> async { gate.withPermit { page(start, withTotal = false)?.items.orEmpty() } } }
                        .awaitAll()
                        .forEach { collected += it }
                } else {
                    // No usable total: page on one by one, as before.
                    var startIndex = first.items.size
                    while (startIndex < cap) {
                        val items = page(startIndex, withTotal = false)?.items ?: break
                        collected += items
                        if (items.size < LIVE_TV_PROGRAM_PAGE) break
                        startIndex += items.size
                    }
                }
            }
            collected.distinctBy { it.id }
        }
        val programsByChannel = programs.groupBy { it.channelId }
        val base = jfBaseUrl
        val nowMs = nowInstant.toEpochMilli()
        // Fallback label only: the guide formats start/end itself with the TV's 12/24-hour
        // setting (this layer has no Context). Localized short time, never a fixed "HH:mm".
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withLocale(Locale.forLanguageTag(prefs.cachedUiLanguage.ifBlank { "en" }))
            .withZone(ZoneId.systemDefault())

        channels.map { ch ->
            val progList = programsByChannel[ch.id].orEmpty().map { p ->
                val startMs = parseIsoMillis(p.startDate)
                val endMs = parseIsoMillis(p.endDate)
                val range = if (startMs > 0L && endMs > 0L) {
                    "${timeFmt.format(Instant.ofEpochMilli(startMs))} - ${timeFmt.format(Instant.ofEpochMilli(endMs))}"
                } else ""
                LiveTvProgram(
                    id = p.id,
                    title = p.name,
                    overview = p.overview,
                    startMs = startMs,
                    endMs = endMs,
                    timeRangeLabel = range,
                    isSeries = p.isSeries,
                    timerId = p.timerId,
                    seriesTimerId = p.seriesTimerId,
                    isMovie = p.isMovie,
                    isSports = p.isSports,
                    isNews = p.isNews,
                    isKids = p.isKids
                )
            }.sortedBy { it.startMs }

            val embeddedCurrent = ch.currentProgram?.let { cp ->
                val sMs = parseIsoMillis(cp.startDate)
                val eMs = parseIsoMillis(cp.endDate)
                val range = if (sMs > 0L && eMs > 0L) {
                    "${timeFmt.format(Instant.ofEpochMilli(sMs))} - ${timeFmt.format(Instant.ofEpochMilli(eMs))}"
                } else ""
                LiveTvProgram(
                    id = cp.id,
                    title = cp.name ?: "Live Broadcast",
                    overview = cp.overview,
                    startMs = sMs,
                    endMs = eMs,
                    timeRangeLabel = range,
                    isSeries = cp.isSeries,
                    timerId = cp.timerId,
                    seriesTimerId = cp.seriesTimerId,
                    isMovie = cp.isMovie,
                    isSports = cp.isSports,
                    isNews = cp.isNews,
                    isKids = cp.isKids
                )
            }
            val current = embeddedCurrent
                ?: progList.firstOrNull { nowMs in it.startMs..it.endMs }
                ?: progList.firstOrNull()
            val next = progList.firstOrNull { it.startMs > (current?.startMs ?: nowMs) }
            val mergedPrograms = buildList {
                if (current != null && progList.none { it.id == current.id }) add(current)
                addAll(progList)
            }.sortedBy { it.startMs }

            LiveTvChannel(
                id = ch.id,
                number = ch.channelNumber,
                name = ch.name,
                logoUrl = primaryImage(base, ch.id, 300),
                currentProgram = current,
                nextProgram = next,
                programs = mergedPrograms,
                isFavorite = ch.userData?.isFavorite == true
            )
        }
    }

    suspend fun toggleLiveTvChannelFavorite(userId: String, channelId: String, favorite: Boolean): Result<Unit> =
        runCatchingCancellable {
            val rawId = ServerScopedId.rawOf(channelId)
            if (favorite) jellyfinApi.addFavorite(userId, rawId)
            else jellyfinApi.removeFavorite(userId, rawId)
        }

    /** Upcoming one-off recordings plus series rules, soonest first (series rules last). */
    suspend fun loadScheduledRecordings(): List<ScheduledRecording> = coroutineScope {
        val timersDeferred = async {
            runCatchingCancellable { jellyfinApi.getLiveTvTimers().items }.getOrDefault(emptyList())
        }
        val seriesDeferred = async {
            runCatchingCancellable { jellyfinApi.getLiveTvSeriesTimers().items }.getOrDefault(emptyList())
        }
        // Fallback label only: the guide formats [ScheduledRecording.startMs] itself with the TV's
        // 12/24-hour setting and the app language.
        val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .withLocale(Locale.forLanguageTag(prefs.cachedUiLanguage.ifBlank { "en" }))
            .withZone(ZoneId.systemDefault())
        val single = timersDeferred.await().map { t ->
            val start = parseIsoMillis(t.startDate)
            ScheduledRecording(
                id = t.id,
                title = t.name.orEmpty().ifBlank { "Recording" },
                channelName = t.channelName,
                startMs = start,
                timeLabel = if (start > 0L) fmt.format(Instant.ofEpochMilli(start)) else "",
                isSeriesRule = false
            )
        }.sortedBy { it.startMs }
        val series = seriesDeferred.await().map { t ->
            ScheduledRecording(
                id = t.id,
                title = t.name.orEmpty().ifBlank { "Series" },
                channelName = if (t.recordAnyChannel == true) null else t.channelName,
                startMs = Long.MAX_VALUE,
                timeLabel = "",
                isSeriesRule = true
            )
        }
        single + series
    }

    /**
     * Schedules [programId] (or, with [series], every airing of its series) using the server's
     * defaults patched with the user's [DvrTimerOptions] (padding, new-only, any-channel).
     */
    suspend fun scheduleRecording(
        programId: String,
        series: Boolean,
        options: DvrTimerOptions = DvrTimerOptions()
    ) {
        val defaults = jellyfinApi.getLiveTvTimerDefaults(programId)
        val payload = patchTimerDefaults(defaults, series, options)
        if (series) jellyfinApi.createLiveTvSeriesTimer(payload) else jellyfinApi.createLiveTvTimer(payload)
    }

    suspend fun cancelRecording(timerId: String) = jellyfinApi.cancelLiveTvTimer(timerId)

    suspend fun cancelSeriesRecording(seriesTimerId: String) = jellyfinApi.cancelLiveTvSeriesTimer(seriesTimerId)

    /**
     * Resolves an ambient theme song audio URL for [itemId] (via Jellyfin's `/Items/{id}/ThemeSongs`),
     * returning `null` if the item has no theme song or the server doesn't expose one.
     */
    suspend fun loadThemeSongUrl(userId: String, itemId: String): String? {
        val session = sessionFor(itemId, userId) ?: return null
        val resp = runCatchingCancellable {
            session.api.getThemeSongs(itemId = session.raw(itemId), userId = session.userId)
        }.getOrNull() ?: return null
        val song = resp.items.firstOrNull { it.id.isNotBlank() } ?: return null
        val base = (if (session.isHome) jfBaseUrl else servers?.baseUrlForItem(itemId).orEmpty()).trimEnd('/')
        if (base.isBlank()) return null
        val token = if (session.isHome) prefs.cachedToken.orEmpty() else servers?.token(session.id).orEmpty()
        return "$base/Audio/${song.id}/universal?UserId=${session.userId}&api_key=$token&Container=mp3,aac,opus,flac&AudioCodec=aac"
    }

    /**
     * Returns high-res backdrop slides from the user's Jellyfin library for the OLED screensaver,
     * filtered by [ContentRatingHelper] so restricted / Kids profiles only ever see age-appropriate fanart.
     */
    suspend fun loadScreensaverBackdrops(userId: String, activeProfile: UserProfile?): List<ScreensaverSlide> {
        val limit = activeProfile?.effectiveRatingLimit ?: ContentRatingLimit.ALL
        val items = runCatchingCancellable {
            jellyfinApi.getLibraryItems(
                userId = userId,
                limit = 60,
                sortBy = "Random",
                maxOfficialRating = limit.jellyfinMaxOfficialRating
            ).items
        }.getOrDefault(emptyList())
        val base = jfBaseUrl
        return items.asSequence()
            // Same rules as every other row (rating rank *and* the Kids-mode genre exclusions).
            .filter { dto ->
                limit == ContentRatingLimit.ALL || ContentRatingHelper.isAllowed(mapJellyfinItem(dto), activeProfile)
            }
            .filter { dto -> dto.backdropImageTags.isNotEmpty() || dto.imageTags.containsKey("Backdrop") }
            .mapNotNull { dto ->
                val url = backdropImage(base, dto.id) ?: return@mapNotNull null
                val year = dto.productionYear?.toString()
                ScreensaverSlide(
                    title = dto.name,
                    subtitle = year,
                    backdropUrl = url
                )
            }
            .distinctBy { it.backdropUrl }
            .take(30)
            .toList()
            .shuffled()
    }

    /** One page of a library for the A-Z grid; rating-limited like every other row. */
    suspend fun browseLibrary(
        userId: String,
        parentId: String?,
        includeItemTypes: String,
        sortBy: String,
        sortOrder: String,
        filters: String?,
        genre: String?,
        nameStartsWith: String?,
        nameLessThan: String?,
        maxOfficialRating: String?,
        startIndex: Int,
        limit: Int,
        recursive: Boolean = true
    ): LibraryPage {
        // An individual library (parentId) routes directly to the server that owns it (home or extra);
        // the global All Movies / All Shows grids interleave every connected server's titles.
        val extras = if (parentId == null && recursive) servers?.availableExtras().orEmpty() else emptyList()
        if (extras.isNotEmpty()) {
            return browseMerged(
                userId, extras, includeItemTypes, sortBy, sortOrder, filters, genre,
                nameStartsWith, nameLessThan, maxOfficialRating, startIndex, limit
            )
        }
        val session = if (parentId != null) requireSession(parentId, userId) else homeSession(userId)
        val resp = session.api.browseItems(
            userId = session.userId,
            parentId = parentId?.let { session.raw(it) },
            includeItemTypes = includeItemTypes,
            recursive = recursive,
            sortBy = sortBy,
            sortOrder = sortOrder,
            filters = filters,
            genres = genre,
            nameStartsWith = nameStartsWith,
            nameLessThan = nameLessThan,
            maxOfficialRating = maxOfficialRating,
            startIndex = startIndex,
            limit = limit
        )
        return LibraryPage(resp.items.scopedTo(session.id).map { mapJellyfinItem(it) }, resp.totalRecordCount)
    }

    suspend fun loadGenres(userId: String, parentId: String?, includeItemTypes: String): List<String> = coroutineScope {
        if (parentId != null) {
            val session = sessionFor(parentId, userId) ?: return@coroutineScope emptyList()
            // Time-boxed on another server, like the merged lookup below.
            return@coroutineScope onServer(session, ROW_EXTRA_TIMEOUT_MS) { s ->
                s.api.getGenres(
                    userId = s.userId,
                    parentId = s.raw(parentId),
                    includeItemTypes = includeItemTypes
                ).items.map { it.name }.filter { it.isNotBlank() }
            }.orEmpty()
        }
        val extraDeferred = async {
            servers?.fromExtras(ROW_EXTRA_TIMEOUT_MS) { s ->
                s.api.getGenres(userId = s.userId, includeItemTypes = includeItemTypes).items.map { it.name }
            }.orEmpty().flatMap { it.second }
        }
        val home = runCatchingCancellable {
            jellyfinApi.getGenres(userId = userId, parentId = null, includeItemTypes = includeItemTypes)
                .items.map { it.name }.filter { it.isNotBlank() }
        }.getOrDefault(emptyList())
        val extra = extraDeferred.await().filter { it.isNotBlank() }
        if (extra.isEmpty()) home
        else (home + extra).distinctBy { it.lowercase() }.sortedBy { it.lowercase() }
    }

    private class BrowseStream(val session: ServerSession) {
        val buffer = ArrayDeque<JellyfinItemDto>()
        var nextStart = 0
        var total = 0
        var exhausted = false
    }

    /** Where an interleaved library grid is up to: one paging cursor per server. */
    private class MergedBrowse(val signature: String, val streams: List<BrowseStream>) {
        var emitted = 0
        var duplicates = 0
        var turn = 0
        val seen = HashSet<String>()
    }

    private var mergedBrowse: MergedBrowse? = null
    private val browseMutex = Mutex()

    /**
     * One page of a grid that spans several servers. Each server pages through its own sorted
     * list; the pages are interleaved in the same order, and a title that was already listed from
     * another server is skipped. A server that stops answering just ends its part of the grid.
     */
    private suspend fun browseMerged(
        userId: String,
        extras: List<ServerSession>,
        includeItemTypes: String,
        sortBy: String,
        sortOrder: String,
        filters: String?,
        genre: String?,
        nameStartsWith: String?,
        nameLessThan: String?,
        maxOfficialRating: String?,
        startIndex: Int,
        limit: Int
    ): LibraryPage = browseMutex.withLock {
        val signature = listOf(
            userId, includeItemTypes, sortBy, sortOrder, filters, genre, nameStartsWith, nameLessThan,
            maxOfficialRating, extras.joinToString(",") { "${it.id}:${it.userId}" }
        ).joinToString("|")
        var state = mergedBrowse
        if (state == null || state.signature != signature || state.emitted != startIndex) {
            state = MergedBrowse(signature, listOf(BrowseStream(homeSession(userId))) + extras.map { BrowseStream(it) })
            mergedBrowse = state
        }
        val comparator = CrossServerMerge.browseComparator(sortBy, sortOrder.equals("Descending", ignoreCase = true))
        val pageLinks = links

        suspend fun refill(stream: BrowseStream) {
            if (stream.exhausted || stream.buffer.isNotEmpty()) return
            suspend fun page(s: ServerSession) = s.api.browseItems(
                userId = s.userId,
                includeItemTypes = includeItemTypes,
                sortBy = sortBy,
                sortOrder = sortOrder,
                filters = filters,
                genres = genre,
                nameStartsWith = nameStartsWith,
                nameLessThan = nameLessThan,
                maxOfficialRating = maxOfficialRating,
                startIndex = stream.nextStart,
                limit = limit,
                fields = BROWSE_FIELDS_MERGED
            )
            // The home server failing is an error, as before; another server failing ends its part.
            val resp = if (stream.session.isHome) page(stream.session)
            else servers?.callExtra(stream.session, ROW_EXTRA_TIMEOUT_MS) { page(it) }
            if (resp == null) {
                stream.exhausted = true
                return
            }
            stream.total = resp.totalRecordCount
            stream.nextStart += resp.items.size
            stream.buffer.addAll(resp.items.scopedTo(stream.session.id))
            if (resp.items.size < limit || stream.nextStart >= resp.totalRecordCount) stream.exhausted = true
        }

        suspend fun next(): JellyfinItemDto? {
            while (true) {
                coroutineScope { state.streams.map { async { refill(it) } }.awaitAll() }
                val ready = state.streams.filter { it.buffer.isNotEmpty() }
                if (ready.isEmpty()) return null
                val pick = if (comparator != null) {
                    ready.minWithOrNull { a, b -> comparator.compare(a.buffer.first(), b.buffer.first()) }!!
                } else {
                    // No comparable order (Random): take turns.
                    ready[state.turn++ % ready.size]
                }
                val dto = pick.buffer.removeFirst()
                val keys = listOf("id:${pageLinks.canonical(dto.id)}") + CrossServerMerge.providerKeys(dto)
                if (keys.any { it in state.seen }) {
                    state.duplicates++
                    continue
                }
                state.seen.addAll(keys)
                return dto
            }
        }

        // The grid normally asks for consecutive pages; after anything else, catch up from the top.
        while (state.emitted < startIndex) {
            next() ?: break
            state.emitted++
        }
        val items = ArrayList<JellyfinItemDto>(limit)
        while (items.size < limit) {
            items += next() ?: break
            state.emitted++
        }
        val finished = state.streams.all { it.exhausted && it.buffer.isEmpty() }
        val total = if (finished) state.emitted
        else maxOf(state.emitted + 1, state.streams.sumOf { it.total } - state.duplicates)
        LibraryPage(items.map { mapJellyfinItem(it) }, total)
    }

    suspend fun loadLiveTvRecordings(userId: String): List<UnifiedMediaItem> {
        return runCatchingCancellable {
            jellyfinApi.getLiveTvRecordings(userId).items.map { mapJellyfinItem(it) }
        }.getOrDefault(emptyList())
    }

    private fun parseIsoMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatchingCancellable { Instant.parse(iso).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(iso).toInstant().toEpochMilli() }
            .getOrDefault(0L)
    }

    /** Appends the image tag so Jellyfin serves the image with long-lived cache headers. */
    private fun withImageTag(url: String?, tag: String?): String? =
        if (url == null || tag.isNullOrBlank()) url else "$url&tag=$tag"

    private fun jellyfinPrimaryUrl(base: String, dto: JellyfinItemDto, maxWidth: Int = 340): String? =
        withImageTag(primaryImage(base, dto.id, maxWidth), dto.imageTags["Primary"])

    /** Only when the item actually has a backdrop; null otherwise. */
    private fun jellyfinBackdropUrl(base: String, dto: JellyfinItemDto): String? {
        val tag = dto.backdropImageTags.firstOrNull() ?: return null
        return withImageTag(backdropImage(base, dto.id), tag)
    }

    /** Only when the item actually has a logo: a guessed URL 404s and hides the hero's title text. */
    private fun jellyfinLogoUrl(base: String, dto: JellyfinItemDto): String? {
        val tag = dto.imageTags["Logo"]?.takeIf { it.isNotBlank() } ?: return null
        return withImageTag(logoImage(base, dto.id), tag)
    }

    private fun mapJellyfinItem(dto: JellyfinItemDto, isNextUp: Boolean = false): UnifiedMediaItem {
        val base = jfBaseUrl
        val isFolder = dto.type.equals("Folder", ignoreCase = true) ||
            dto.type.equals("CollectionFolder", ignoreCase = true) ||
            dto.type.equals("UserView", ignoreCase = true)
        val type = when {
            dto.type.equals("Movie", ignoreCase = true) ||
                dto.type.equals("Video", ignoreCase = true) ||
                dto.type.equals("MusicVideo", ignoreCase = true) ||
                dto.type.equals("Recording", ignoreCase = true) -> MediaType.MOVIE
            dto.type.equals("Episode", ignoreCase = true) -> MediaType.EPISODE
            else -> MediaType.SERIES
        }
        val tmdb = dto.providerIds["Tmdb"]?.toIntOrNull() ?: dto.providerIds["tmdb"]?.toIntOrNull()
        val epLabel = if (type == MediaType.EPISODE && dto.parentIndexNumber != null && dto.indexNumber != null) {
            "S${dto.parentIndexNumber}:E${dto.indexNumber}"
        } else null

        // Episodes are presented with their series' artwork. The DTO doesn't carry the parent's image
        // tags, so the series poster/backdrop URLs are untagged and no logo is guessed (a 404'ing
        // logo hides the hero title; hero enrichment loads the series and fills in a real one).
        val usesSeriesArt = type == MediaType.EPISODE && !dto.seriesId.isNullOrBlank()
        val posterItemId = if (usesSeriesArt) dto.seriesId!! else dto.id
        val posterUrl = if (usesSeriesArt) {
            primaryImage(base, posterItemId)
        } else {
            jellyfinPrimaryUrl(base, dto)
        }
        val backdropUrl = when {
            usesSeriesArt -> backdropImage(base, posterItemId)
            dto.backdropImageTags.isNotEmpty() -> jellyfinBackdropUrl(base, dto)
            type == MediaType.EPISODE -> backdropImage(base, dto.id)
            else -> jellyfinPrimaryUrl(base, dto, maxWidth = 1280)
        }
        val clearLogoUrl = if (usesSeriesArt) null else jellyfinLogoUrl(base, dto)
        val badges = extractMediaBadges(dto.mediaStreams)
        val ytKey = extractYouTubeKey(dto.remoteTrailers.firstOrNull()?.url)
        val related = buildRelatedVideos(
            seerrVideos = emptyList(),
            localTrailerId = null,
            jfRemoteTrailers = dto.remoteTrailers.mapNotNull { t ->
                val k = extractYouTubeKey(t.url) ?: return@mapNotNull null
                RelatedVideoItem(
                    title = t.name ?: "Trailer",
                    type = "Trailer",
                    youtubeKey = k,
                    thumbnailUrl = "https://img.youtube.com/vi/$k/hqdefault.jpg"
                )
            },
            backdropUrl = backdropUrl
        )
        val itemServerId = ServerScopedId.serverOf(dto.id)
        val parentSeries = seriesForRatingFallback(dto)

        return UnifiedMediaItem(
            id = "jf_${dto.id}",
            tmdbId = tmdb,
            jellyfinId = dto.id,
            imdbId = dto.providerIds["Imdb"],
            tvdbId = dto.providerIds["Tvdb"]?.toIntOrNull(),
            mediaType = type,
            title = if (type == MediaType.EPISODE && !dto.seriesName.isNullOrBlank()) {
                "${dto.seriesName} • ${dto.name}"
            } else dto.name,
            overview = dto.overview.orEmpty(),
            tagline = dto.taglines.firstOrNull(),
            releaseYear = dto.productionYear?.toString(),
            runtimeMinutes = dto.runTimeTicks?.let { (it / 600_000_000L).toInt() },
            maturityRating = dto.officialRating?.takeIf { it.isNotBlank() } ?: parentSeries?.officialRating,
            voteAverage = dto.communityRating,
            genres = dto.genres.ifEmpty { parentSeries?.genres.orEmpty() },
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            clearLogoUrl = clearLogoUrl,
            hdStatus = AvailabilityTier.AVAILABLE,
            resumePositionTicks = dto.userData?.playbackPositionTicks ?: 0L,
            runtimeTicks = dto.runTimeTicks ?: 0L,
            isWatched = dto.userData?.played == true,
            isInMyList = dto.userData?.isFavorite == true,
            mediaBadges = badges,
            episodeLabel = epLabel,
            seriesName = dto.seriesName,
            seriesId = dto.seriesId,
            releaseDate = dto.premiereDate,
            youtubeTrailerKey = ytKey,
            relatedVideos = related,
            cast = dto.people.take(12).map { p ->
                val scopedPid = p.id?.takeIf { it.isNotBlank() }?.let { ServerScopedId.scope(itemServerId, it) }
                CastMember(
                    name = p.name,
                    role = p.role.orEmpty(),
                    imageUrl = scopedPid?.let { primaryImage(base, it, 200) },
                    jellyfinPersonId = scopedPid
                )
            },
            isNextUp = isNextUp,
            isFolder = isFolder,
            playlistItemId = dto.playlistItemId,
            canDelete = dto.canDelete == true
        )
    }

    suspend fun loadReadyToWatchAlerts(userId: String): List<UnifiedMediaItem> {
        ensureLibraryIndex(userId)
        val reminderKeys = prefs.getReminders(userId)
        if (reminderKeys.isEmpty()) return emptyList()
        val matchedDtos = reminderKeys.mapNotNull { key ->
            val sep = key.lastIndexOf('_')
            if (sep <= 0) return@mapNotNull null
            val type = runCatchingCancellable { MediaType.valueOf(key.substring(0, sep)) }.getOrNull() ?: return@mapNotNull null
            val tmdbId = key.substring(sep + 1).toIntOrNull() ?: return@mapNotNull null
            when (type) {
                MediaType.MOVIE -> tmdbToJellyfinMovie[tmdbId]
                MediaType.SERIES, MediaType.EPISODE -> tmdbToJellyfinSeries[tmdbId]
            }
        }
        if (matchedDtos.isEmpty()) return emptyList()
        return withOverviews(userId, matchedDtos).map { matchedDto ->
            mapJellyfinItem(matchedDto).copy(
                isReminderSet = true,
                upcomingBadgeLabel = "Now in Library"
            )
        }
    }

    suspend fun loadAiringThisWeek(
        userId: String,
        today: LocalDate = LocalDate.now()
    ): List<UnifiedMediaItem> {
        val base = jfBaseUrl
        val upcomingDtos = runCatchingCancellable {
            jellyfinApi.getUpcomingEpisodes(userId = userId).items
        }.getOrDefault(emptyList())
        return upcomingDtos.map { dto ->
            val epCode = if (dto.parentIndexNumber != null && dto.indexNumber != null) {
                "S${dto.parentIndexNumber} E${dto.indexNumber}"
            } else null
            val badge = UpcomingDateFormatter.formatComingSoonBadge(
                releaseDateIso = dto.premiereDate,
                episodeLabel = epCode,
                today = today
            )
            val seriesItemId = dto.seriesId ?: dto.id
            // These are Episode DTOs: their ProviderIds["Tmdb"] is the episode id, not the show's,
            // and this card is presented (and opened) as the SERIES. Resolve via the series instead.
            val tmdbId = seriesTmdbIdFor(dto.seriesId)
            // Rated like the series it opens: without a rating every card was hidden from restricted profiles.
            val series = seriesForRatingFallback(dto)
            UnifiedMediaItem(
                id = "jf_upcoming_${dto.id}",
                tmdbId = tmdbId,
                jellyfinId = dto.seriesId,
                mediaType = MediaType.SERIES,
                title = dto.seriesName ?: dto.name,
                maturityRating = dto.officialRating?.takeIf { it.isNotBlank() } ?: series?.officialRating,
                genres = dto.genres.ifEmpty { series?.genres.orEmpty() },
                overview = buildString {
                    if (!dto.seriesName.isNullOrBlank() && dto.name.isNotBlank()) {
                        append(dto.name)
                        if (!dto.overview.isNullOrBlank()) append("\n")
                    }
                    append(dto.overview.orEmpty())
                }.trim(),
                posterUrl = primaryImage(base, seriesItemId),
                backdropUrl = backdropImage(base, seriesItemId),
                hdStatus = if (dto.seriesId != null) AvailabilityTier.AVAILABLE else AvailabilityTier.UNKNOWN,
                episodeLabel = epCode,
                seriesName = dto.seriesName,
                releaseDate = dto.premiereDate,
                nextAirDate = dto.premiereDate,
                upcomingBadgeLabel = badge
            )
        }
    }

    suspend fun loadBoxSetCollections(userId: String): List<CollectionSummary> {
        val base = jfBaseUrl
        val merged = hasExtraServers
        val boxSets = runCatchingCancellable {
            mergedItems(
                extra = { s -> s.api.getCollections(userId = s.userId).items },
                home = { jellyfinApi.getCollections(userId = userId).items }
            )
        }.getOrDefault(emptyList())
        return boxSets.map { dto ->
            val tmdbColId = dto.providerIds["TmdbCollection"]?.toIntOrNull()
                ?: dto.providerIds["Tmdb"]?.toIntOrNull()
                ?: dto.providerIds["tmdb"]?.toIntOrNull()
            val backdrop = jellyfinBackdropUrl(base, dto) ?: jellyfinPrimaryUrl(base, dto, maxWidth = 1280)
            CollectionSummary(
                id = dto.id,
                tmdbCollectionId = tmdbColId,
                name = dto.name,
                overview = dto.overview.orEmpty(),
                posterUrl = jellyfinPrimaryUrl(base, dto),
                backdropUrl = backdrop,
                ownedCount = dto.childCount ?: 0,
                totalCount = null
            )
        }.let { all ->
            // The same collection on two servers is listed once (the home server's comes first).
            if (!merged) all
            else all.distinctBy { c -> c.tmdbCollectionId?.let { "tmdb:$it" } ?: "name:${c.name.trim().lowercase()}" }
        }
    }

    suspend fun loadBoxSetDetails(
        userId: String,
        boxSetId: String,
        fallbackTitle: String = "Collection",
        tmdbCollectionIdHint: Int? = null
    ): CollectionSummary? {
        ensureLibraryIndex(userId)
        val ownedDtos = sessionFor(boxSetId, userId)?.let { session ->
            onServer(session) { s -> s.api.getCollectionItems(userId = s.userId, parentId = s.raw(boxSetId)).items }
                ?.scopedTo(session.id)
        }.orEmpty()
        val ownedItems = ownedDtos
            .map { mapJellyfinItem(it) }
            .sortedBy { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: it.releaseYear ?: "9999" }

        val resolvedColId = if (isSeerrEnabled) {
            tmdbCollectionIdHint ?: ownedItems
                .firstOrNull { it.mediaType == MediaType.MOVIE && it.tmdbId != null }
                ?.tmdbId
                ?.let { movieTmdb ->
                    runCatchingCancellable { seerrApi.getMovieDetails(movieTmdb, language = metaLang).resolvedCollection?.id }.getOrNull()
                }
        } else null

        if (resolvedColId != null) {
            val saga = loadCollectionSaga(userId, resolvedColId)
            if (saga != null && saga.items.isNotEmpty()) {
                val ownedByTmdb = ownedItems.filter { it.tmdbId != null }.associateBy { it.tmdbId!! }
                val sagaTmdbIds = saga.items.mapNotNull { it.tmdbId }.toSet()
                val mergedSaga = saga.items.map { part ->
                    val owned = part.tmdbId?.let { ownedByTmdb[it] }
                    owned ?: part
                }
                val extras = ownedItems.filter { it.tmdbId == null || it.tmdbId !in sagaTmdbIds }
                val combined = (mergedSaga + extras)
                    .sortedBy { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: it.releaseYear ?: "9999" }
                return saga.copy(
                    id = boxSetId,
                    name = fallbackTitle.ifBlank { saga.name },
                    ownedCount = combined.count { it.isPlayable },
                    totalCount = combined.size,
                    items = combined
                )
            }
        }

        if (ownedItems.isEmpty()) return null
        return CollectionSummary(
            id = boxSetId,
            tmdbCollectionId = resolvedColId,
            name = fallbackTitle,
            overview = ownedItems.firstOrNull()?.overview.orEmpty(),
            posterUrl = ownedItems.firstOrNull()?.posterUrl,
            backdropUrl = ownedItems.firstOrNull()?.backdropUrl,
            ownedCount = ownedItems.size,
            totalCount = null,
            items = ownedItems
        )
    }

    suspend fun loadCollectionSaga(
        userId: String,
        collectionId: Int,
        activeProfile: UserProfile? = null
    ): CollectionSummary? {
        if (!isSeerrEnabled) return null
        ensureLibraryIndex(userId)
        val dto = runCatchingCancellable { seerrApi.getCollectionDetails(collectionId, language = metaLang) }.getOrNull() ?: return null
        val items = ContentRatingHelper.filterForProfile(
            dto.parts
                .map { mapSeerrResult(it, MediaType.MOVIE) }
                .sortedBy { it.releaseDate?.takeIf { d -> d.isNotBlank() } ?: it.releaseYear ?: "9999" },
            activeProfile
        )
        val ownedCount = items.count { it.isPlayable }
        return CollectionSummary(
            id = "tmdb_col_${dto.id}",
            tmdbCollectionId = dto.id,
            name = dto.name,
            overview = dto.overview.orEmpty(),
            posterUrl = MediaImageUrls.tmdbPoster(dto.posterPath) ?: items.firstOrNull()?.posterUrl,
            backdropUrl = MediaImageUrls.tmdbBackdrop(dto.backdropPath) ?: items.firstOrNull()?.backdropUrl,
            ownedCount = ownedCount,
            totalCount = items.size,
            items = items
        )
    }

    fun CollectionSummary.toPosterCardItem(): UnifiedMediaItem = UnifiedMediaItem(
        id = "boxset_$id",
        mediaType = MediaType.MOVIE,
        title = name,
        overview = overview,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        hdStatus = AvailabilityTier.AVAILABLE,
        upcomingBadgeLabel = completionLabel,
        collectionId = tmdbCollectionId,
        collectionName = name,
        boxSetId = id
    )

    private fun mapSeerrResult(
        res: SeerrMediaResultDto,
        type: MediaType,
        today: LocalDate = LocalDate.now()
    ): UnifiedMediaItem {
        val jfBase = jfBaseUrl
        val matchedJf = if (type == MediaType.MOVIE) tmdbToJellyfinMovie[res.id] else tmdbToJellyfinSeries[res.id]
        val mediaInfo = res.mediaInfo
        val hdStatus = if (matchedJf != null) {
            AvailabilityTier.AVAILABLE
        } else {
            AvailabilityTier.fromSeerrCode(mediaInfo?.status)
        }
        val uhdStatus = AvailabilityTier.fromSeerrCode(mediaInfo?.status4k)
        val rawReleaseDate = (res.releaseDate ?: res.firstAirDate)?.takeIf { it.isNotBlank() }
        val upcomingBadge = UpcomingDateFormatter.formatComingSoonBadge(rawReleaseDate, today = today)

        return UnifiedMediaItem(
            id = "tmdb_${type.name}_${res.id}",
            tmdbId = res.id,
            jellyfinId = matchedJf?.id ?: mediaInfo?.jellyfinMediaId ?: mediaInfo?.jellyfinMediaId4k,
            mediaType = type,
            title = res.title ?: res.name ?: res.originalTitle ?: res.originalName ?: "Untitled",
            overview = res.overview.orEmpty(),
            releaseYear = rawReleaseDate?.take(4),
            releaseDate = rawReleaseDate,
            upcomingBadgeLabel = upcomingBadge,
            // Seerr list results carry no certification; use the owned copy's rating when we have
            // one so parental filtering can see it.
            maturityRating = matchedJf?.officialRating,
            voteAverage = res.voteAverage,
            genres = matchedJf?.genres?.takeIf { it.isNotEmpty() }
                ?: res.genreIds.mapNotNull { TMDB_GENRE_NAMES[it] },
            posterUrl = MediaImageUrls.tmdbPoster(res.posterPath)
                ?: matchedJf?.let { jellyfinPrimaryUrl(jfBase, it) },
            backdropUrl = MediaImageUrls.tmdbBackdrop(res.backdropPath)
                ?: matchedJf?.let { jellyfinBackdropUrl(jfBase, it) },
            clearLogoUrl = matchedJf?.let { jellyfinLogoUrl(jfBase, it) },
            hdStatus = hdStatus,
            uhdStatus = uhdStatus,
            hdDownload = DownloadProgressParser.parse(mediaInfo?.downloadStatus.orEmpty(), is4k = false),
            uhdDownload = DownloadProgressParser.parse(mediaInfo?.downloadStatus4k.orEmpty(), is4k = true),
            resumePositionTicks = matchedJf?.userData?.playbackPositionTicks ?: 0L,
            runtimeTicks = matchedJf?.runTimeTicks ?: 0L,
            isWatched = matchedJf?.userData?.played == true,
            isInMyList = matchedJf?.userData?.isFavorite == true,
            mediaBadges = matchedJf?.let { extractMediaBadges(it.mediaStreams) }.orEmpty(),
            isAdult = res.adult
        )
    }

    private fun mergeMovieDetails(
        seerr: com.seerflix.tv.core.network.SeerrMovieDetailsDto,
        jf: JellyfinItemDto?,
        localTrailerId: String?
    ): UnifiedMediaItem {
        val jfBase = jfBaseUrl
        val info = seerr.mediaInfo
        val hdStatus = if (jf != null) AvailabilityTier.AVAILABLE else AvailabilityTier.fromSeerrCode(info?.status)
        val uhdStatus = AvailabilityTier.fromSeerrCode(info?.status4k)
        val backdropUrl = MediaImageUrls.tmdbBackdrop(seerr.backdropPath)
            ?: jf?.let { jellyfinBackdropUrl(jfBase, it) }
        val collection = seerr.resolvedCollection

        return UnifiedMediaItem(
            id = "tmdb_MOVIE_${seerr.id}",
            tmdbId = seerr.id,
            jellyfinId = jf?.id ?: info?.jellyfinMediaId ?: info?.jellyfinMediaId4k,
            imdbId = seerr.externalIds?.imdbId,
            mediaType = MediaType.MOVIE,
            title = seerr.title,
            overview = seerr.overview.orEmpty(),
            tagline = seerr.tagline,
            releaseYear = seerr.releaseDate?.take(4),
            releaseDate = seerr.releaseDate,
            collectionId = collection?.id,
            collectionName = collection?.name,
            runtimeMinutes = seerr.runtime ?: jf?.runTimeTicks?.let { (it / 600_000_000L).toInt() },
            maturityRating = jf?.officialRating ?: seerr.certification(),
            voteAverage = seerr.voteAverage,
            genres = seerr.genres.map { it.name },
            posterUrl = MediaImageUrls.tmdbPoster(seerr.posterPath)
                ?: jf?.let { jellyfinPrimaryUrl(jfBase, it) },
            backdropUrl = backdropUrl,
            clearLogoUrl = jf?.let { jellyfinLogoUrl(jfBase, it) },
            hdStatus = hdStatus,
            uhdStatus = uhdStatus,
            hdDownload = DownloadProgressParser.parse(info?.downloadStatus.orEmpty(), is4k = false),
            uhdDownload = DownloadProgressParser.parse(info?.downloadStatus4k.orEmpty(), is4k = true),
            hdRequestId = info?.requests?.firstOrNull { !it.is4k }?.id,
            uhdRequestId = info?.requests?.firstOrNull { it.is4k }?.id,
            resumePositionTicks = jf?.userData?.playbackPositionTicks ?: 0L,
            runtimeTicks = jf?.runTimeTicks ?: 0L,
            isWatched = jf?.userData?.played == true,
            isInMyList = jf?.userData?.isFavorite == true,
            mediaBadges = jf?.let { extractMediaBadges(it.mediaStreams) }.orEmpty(),
            youtubeTrailerKey = pickBestYouTubeTrailer(seerr.relatedVideos)
                ?: extractYouTubeKey(jf?.remoteTrailers?.firstOrNull()?.url),
            localTrailerItemId = localTrailerId,
            relatedVideos = buildRelatedVideos(seerr.relatedVideos, localTrailerId, emptyList(), backdropUrl),
            cast = seerr.credits?.cast?.take(12)?.map {
                CastMember(
                    name = it.name,
                    role = it.character.orEmpty(),
                    imageUrl = MediaImageUrls.tmdbProfile(it.profilePath),
                    personId = it.id
                )
            }.orEmpty(),
            isAdult = seerr.adult
        )
    }

    private fun mergeTvDetails(
        seerr: com.seerflix.tv.core.network.SeerrTvDetailsDto,
        jf: JellyfinItemDto?,
        jfSeasons: List<JellyfinItemDto>,
        localTrailerId: String?
    ): UnifiedMediaItem {
        val jfBase = jfBaseUrl
        val info: SeerrMediaInfoDto? = seerr.mediaInfo
        val hdStatus = if (jf != null) {
            val seerrHd = AvailabilityTier.fromSeerrCode(info?.status)
            if (seerrHd == AvailabilityTier.PARTIALLY_AVAILABLE) seerrHd else AvailabilityTier.AVAILABLE
        } else {
            AvailabilityTier.fromSeerrCode(info?.status)
        }
        val uhdStatus = AvailabilityTier.fromSeerrCode(info?.status4k)

        val jfSeasonByNum = jfSeasons.associateBy { it.indexNumber ?: -1 }
        val seerrSeasonStatus = info?.seasons?.associateBy { it.seasonNumber }.orEmpty()

        val seasons = seerr.seasons
            .filter { it.seasonNumber > 0 }
            .map { s ->
                val jfS = jfSeasonByNum[s.seasonNumber]
                val sStatus = seerrSeasonStatus[s.seasonNumber]
                SeasonSummary(
                    seasonNumber = s.seasonNumber,
                    name = s.name ?: "Season ${s.seasonNumber}",
                    episodeCount = s.episodeCount ?: s.episodes.size,
                    overview = s.overview,
                    posterUrl = MediaImageUrls.tmdbPoster(s.posterPath),
                    hdStatus = if (jfS != null) AvailabilityTier.AVAILABLE else AvailabilityTier.fromSeerrCode(sStatus?.status),
                    uhdStatus = AvailabilityTier.fromSeerrCode(sStatus?.status4k),
                    jellyfinSeasonId = jfS?.id
                )
            }
            .let { fromSeerr ->
                // Seasons only the server has (Specials, extra seasons TMDB doesn't list) stay visible.
                val listed = fromSeerr.mapTo(HashSet()) { it.seasonNumber }
                val jellyfinOnly = jfSeasons.filter { it.indexNumber != null && it.indexNumber !in listed }
                if (jellyfinOnly.isEmpty()) fromSeerr
                else (fromSeerr + jellyfinOnly.map { s ->
                    SeasonSummary(
                        seasonNumber = s.indexNumber!!,
                        name = s.name,
                        episodeCount = s.childCount ?: 0,
                        overview = s.overview,
                        posterUrl = primaryImage(jfBase, s.id),
                        hdStatus = AvailabilityTier.AVAILABLE,
                        jellyfinSeasonId = s.id
                    )
                }).sortedBy { it.seasonNumber }
            }
        val backdropUrl = MediaImageUrls.tmdbBackdrop(seerr.backdropPath)
            ?: jf?.let { jellyfinBackdropUrl(jfBase, it) }

        return UnifiedMediaItem(
            id = "tmdb_SERIES_${seerr.id}",
            tmdbId = seerr.id,
            jellyfinId = jf?.id ?: info?.jellyfinMediaId ?: info?.jellyfinMediaId4k,
            imdbId = seerr.externalIds?.imdbId,
            tvdbId = seerr.externalIds?.tvdbId,
            mediaType = MediaType.SERIES,
            title = seerr.name,
            overview = seerr.overview.orEmpty(),
            tagline = seerr.tagline,
            releaseYear = seerr.firstAirDate?.take(4),
            runtimeMinutes = seerr.episodeRunTime.firstOrNull(),
            maturityRating = jf?.officialRating ?: seerr.certification(),
            voteAverage = seerr.voteAverage,
            genres = seerr.genres.map { it.name },
            posterUrl = MediaImageUrls.tmdbPoster(seerr.posterPath)
                ?: jf?.let { jellyfinPrimaryUrl(jfBase, it) },
            backdropUrl = backdropUrl,
            clearLogoUrl = jf?.let { jellyfinLogoUrl(jfBase, it) },
            hdStatus = hdStatus,
            uhdStatus = uhdStatus,
            hdDownload = DownloadProgressParser.parse(info?.downloadStatus.orEmpty(), is4k = false),
            uhdDownload = DownloadProgressParser.parse(info?.downloadStatus4k.orEmpty(), is4k = true),
            hdRequestId = info?.requests?.firstOrNull { !it.is4k }?.id,
            uhdRequestId = info?.requests?.firstOrNull { it.is4k }?.id,
            isInMyList = jf?.userData?.isFavorite == true,
            mediaBadges = jf?.let { extractMediaBadges(it.mediaStreams) }.orEmpty(),
            youtubeTrailerKey = pickBestYouTubeTrailer(seerr.relatedVideos)
                ?: extractYouTubeKey(jf?.remoteTrailers?.firstOrNull()?.url),
            localTrailerItemId = localTrailerId,
            relatedVideos = buildRelatedVideos(seerr.relatedVideos, localTrailerId, emptyList(), backdropUrl),
            seasons = seasons,
            cast = seerr.credits?.cast?.take(12)?.map {
                CastMember(
                    name = it.name,
                    role = it.character.orEmpty(),
                    imageUrl = MediaImageUrls.tmdbProfile(it.profilePath),
                    personId = it.id
                )
            }.orEmpty()
        )
    }

    private fun buildRelatedVideos(
        seerrVideos: List<SeerrRelatedVideoDto>,
        localTrailerId: String?,
        jfRemoteTrailers: List<RelatedVideoItem>,
        backdropUrl: String?
    ): List<RelatedVideoItem> = buildList {
        if (!localTrailerId.isNullOrBlank()) {
            add(
                RelatedVideoItem(
                    title = "Server Trailer",
                    type = "Trailer",
                    localTrailerItemId = localTrailerId,
                    thumbnailUrl = backdropUrl
                )
            )
        }
        seerrVideos.forEach { v ->
            val isYt = v.site.equals("YouTube", ignoreCase = true) ||
                v.url?.contains("youtube.com", ignoreCase = true) == true ||
                v.url?.contains("youtu.be", ignoreCase = true) == true
            val key = if (isYt) (v.key ?: extractYouTubeKey(v.url)) else null
            if (!key.isNullOrBlank()) {
                add(
                    RelatedVideoItem(
                        title = v.name ?: (v.type ?: "Video"),
                        type = v.type ?: "Trailer",
                        youtubeKey = key,
                        thumbnailUrl = "https://img.youtube.com/vi/$key/hqdefault.jpg"
                    )
                )
            }
        }
        if (isEmpty()) {
            addAll(jfRemoteTrailers)
        }
    }.distinctBy { it.localTrailerItemId ?: it.youtubeKey ?: it.title }

    private fun pickBestYouTubeTrailer(videos: List<SeerrRelatedVideoDto>): String? {
        val ytVideos = videos.filter {
            it.site.equals("YouTube", ignoreCase = true) ||
                it.url?.contains("youtube.com", ignoreCase = true) == true ||
                it.url?.contains("youtu.be", ignoreCase = true) == true
        }
        val preferred = ytVideos.firstOrNull { it.type.equals("Trailer", ignoreCase = true) }
            ?: ytVideos.firstOrNull { it.type.equals("Teaser", ignoreCase = true) }
            ?: ytVideos.firstOrNull()
        return preferred?.key ?: extractYouTubeKey(preferred?.url)
    }

    private fun extractYouTubeKey(url: String?): String? =
        YouTubeUrlParser.extractVideoKey(url)

    // ---------------------------------------------------------------------------------------
    // Personalized discovery (0.9.8): ratings, multi-title picks, library rows, people and
    // household rows. Scoring lives in DiscoveryEngine; this is the I/O around it.
    // ---------------------------------------------------------------------------------------

    private val indexById: Map<String, JellyfinItemDto>
        get() = libraryIndex?.byId.orEmpty()

    /**
     * The profile's taste: ratings given in Lumen, plus thumbs set on the server (UserData.Likes,
     * e.g. from Lumen on another TV) for titles not rated here.
     */
    suspend fun tasteFor(userId: String): TasteData {
        val local = prefs.getTaste(userId)
        val serverRated = allLibraryItems.mapNotNull { dto ->
            val likes = dto.userData?.likes ?: return@mapNotNull null
            TasteEntry(
                mediaType = DiscoveryEngine.titleType(jellyfinMediaType(dto)),
                tmdbId = dto.providerIds["Tmdb"]?.toIntOrNull() ?: dto.providerIds["tmdb"]?.toIntOrNull(),
                jellyfinId = dto.id,
                title = dto.name,
                genres = dto.genres,
                rating = if (likes) TasteRating.LIKE else TasteRating.DISLIKE
            )
        }
        if (serverRated.isEmpty()) return local
        val localKeys = local.ratings.flatMap { it.keys }.toSet()
        val extra = serverRated.filter { e -> e.keys.none { it in localKeys } }
        // Local ratings stay last (= most recent) so they lead the seeds.
        return if (extra.isEmpty()) local else local.copy(ratings = extra + local.ratings)
    }

    /** The rating Lumen shows for [item] (local first, then the server's thumbs). */
    suspend fun ratingFor(userId: String, item: UnifiedMediaItem): TasteRating? =
        tasteFor(userId).ratingFor(withLibraryId(item))

    /** Fills in the owned copy's Jellyfin id for a Seerr result, so both ids carry the rating. */
    private fun withLibraryId(item: UnifiedMediaItem): UnifiedMediaItem {
        if (item.jellyfinId != null || item.tmdbId == null) return item
        val jfId = findJellyfinIdForTmdb(item.tmdbId, DiscoveryEngine.titleType(item.mediaType)) ?: return item
        return item.copy(jellyfinId = jfId)
    }

    /**
     * Thumbs down / up / love for a title (null clears). Saved for the profile and, for titles in
     * the library, mirrored to Jellyfin's Likes so other Lumen TVs see it too.
     */
    suspend fun rateTitle(userId: String, item: UnifiedMediaItem, rating: TasteRating?): Result<TasteData> =
        runCatchingCancellable {
            val target = withLibraryId(item)
            val updated = prefs.updateTaste(userId) { it.withRating(target, rating, System.currentTimeMillis()) }
            target.jellyfinId?.let { jfId ->
                sessionFor(jfId, userId)?.let { session ->
                    runCatchingCancellable {
                        if (rating == null) session.api.deleteUserItemRating(session.userId, session.raw(jfId))
                        else session.api.updateUserItemRating(
                            session.userId,
                            session.raw(jfId),
                            likes = rating != TasteRating.DISLIKE
                        )
                    }
                }
            }
            updated
        }

    /** "Not interested": the title stops appearing in recommendation and discovery rows. */
    suspend fun setNotInterested(userId: String, item: UnifiedMediaItem, notInterested: Boolean): Result<TasteData> =
        runCatchingCancellable {
            val target = withLibraryId(item)
            prefs.updateTaste(userId) { it.withNotInterested(target, notInterested, System.currentTimeMillis()) }
        }

    /** Counts a title opened from a Home row, so rows the profile uses move up the page. */
    suspend fun recordRowOpen(userId: String, rowId: String) {
        runCatchingCancellable { prefs.updateTaste(userId) { it.withRowOpened(rowId) } }
    }

    /** Forgets the profile's ratings, "Not interested" list and row history. */
    suspend fun resetTaste(userId: String): Result<Unit> = runCatchingCancellable {
        prefs.updateTaste(userId) { TasteData() }
        Unit
    }

    /** History as titles: an episode becomes its series (from the index when owned). */
    private fun historyTitles(dtos: List<JellyfinItemDto>): List<UnifiedMediaItem> {
        val byId = indexById
        return dtos.mapNotNull { dto ->
            when {
                dto.type.equals("Episode", ignoreCase = true) -> {
                    val sid = dto.seriesId ?: return@mapNotNull null
                    byId[sid]?.let { mapJellyfinItem(it) } ?: UnifiedMediaItem(
                        id = sid,
                        jellyfinId = sid,
                        tmdbId = seriesTmdbIdFor(sid),
                        mediaType = MediaType.SERIES,
                        title = dto.seriesName ?: dto.name,
                        genres = dto.genres
                    )
                }
                dto.type.equals("Movie", ignoreCase = true) || dto.type.equals("Series", ignoreCase = true) ->
                    mapJellyfinItem(byId[dto.id]?.let { idx -> dto.copy(genres = dto.genres.ifEmpty { idx.genres }) } ?: dto)
                else -> null
            }
        }.distinctBy { DiscoveryEngine.identity(it) }
    }

    /** Per-title recommendation lists are stable for a while; tab switches reuse them. */
    private val seedRecsCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, List<UnifiedMediaItem>>>()

    /** A cached recommendation with today's library state (owned copy, watched, progress). */
    private fun refreshFromIndex(rec: UnifiedMediaItem): UnifiedMediaItem {
        val tmdb = rec.tmdbId ?: return rec
        val owned = (if (rec.mediaType == MediaType.MOVIE) tmdbToJellyfinMovie[tmdb] else tmdbToJellyfinSeries[tmdb])
            ?: return rec
        return rec.copy(
            jellyfinId = rec.jellyfinId ?: owned.id,
            isWatched = owned.userData?.played == true,
            resumePositionTicks = owned.userData?.playbackPositionTicks ?: rec.resumePositionTicks
        )
    }

    internal class PersonalDiscovery(
        val seeds: List<DiscoveryEngine.Seed>,
        val affinity: Map<String, Double>,
        val perSeed: List<Pair<DiscoveryEngine.Seed, List<UnifiedMediaItem>>>,
        val topPicks: List<UnifiedMediaItem>,
        /** Titles the profile is watching or finished: never recommended back. */
        val historyIds: Set<String>
    )

    /**
     * Seeds from the whole recent history (watching, finished, favorites, thumbs up), the
     * recommendations of the strongest few, and one merged "Top Picks" ranking.
     */
    private suspend fun buildPersonalDiscovery(
        userId: String,
        watchingRaw: List<JellyfinItemDto>,
        playedRaw: List<JellyfinItemDto>,
        favorites: List<UnifiedMediaItem>,
        taste: TasteData,
        activeProfile: UserProfile?
    ): PersonalDiscovery = coroutineScope {
        val watching = historyTitles(watchingRaw)
        val played = historyTitles(playedRaw)
        val seeds = DiscoveryEngine.buildSeeds(watching, played, favorites, taste)
        val affinity = DiscoveryEngine.genreAffinity(seeds, taste)
        val perSeed = seeds.take(SEEDS_TO_EXPAND).map { seed ->
            async {
                val type = DiscoveryEngine.titleType(seed.item.mediaType)
                val tmdb = seed.item.tmdbId
                    ?: seed.item.jellyfinId?.let { if (type == MediaType.SERIES) seriesTmdbIdFor(it) else null }
                val cacheKey = "$userId|$type|${tmdb ?: seed.item.jellyfinId}|$metaLang|${activeProfile?.effectiveRatingLimit}"
                val now = System.currentTimeMillis()
                val cached = seedRecsCache[cacheKey]?.takeIf { now - it.first in 0 until SEED_RECS_TTL_MS }?.second
                val recs = cached ?: runCatchingCancellable {
                    loadRecommendations(
                        userId = userId,
                        tmdbId = tmdb,
                        jellyfinId = seed.item.jellyfinId,
                        mediaType = type,
                        activeProfile = activeProfile
                    )
                }.getOrNull()?.also { if (it.isNotEmpty()) seedRecsCache[cacheKey] = now to it }.orEmpty()
                // Availability and watch state come from the current index, not the cached copy.
                seed to recs.map { rec -> refreshFromIndex(rec) }
            }
        }.awaitAll()
        val historyIds = (watching + played).map { DiscoveryEngine.identity(it) }.toSet()
        val topPicks = DiscoveryEngine.rankRecommendations(perSeed, affinity, taste, exclude = historyIds, limit = 30)
        PersonalDiscovery(seeds, affinity, perSeed, topPicks, historyIds)
    }

    /**
     * Up to three "Because you watched / liked <title>" rows, each from a different seed, each
     * with enough titles left after dropping what's watched, disliked or already a seed.
     */
    private fun becauseYouWatchedRows(
        discovery: PersonalDiscovery,
        taste: TasteData,
        filterType: MediaType?,
        availabilityFilter: AvailabilityFilter
    ): List<CatalogRow> {
        val seedIds = discovery.seeds.map { DiscoveryEngine.identity(it.item) }.toSet()
        val rows = mutableListOf<CatalogRow>()
        for ((seed, recs) in discovery.perSeed) {
            if (rows.size >= MAX_BECAUSE_ROWS) break
            val items = recs.filter { rec ->
                (filterType == null || rec.mediaType == filterType) &&
                    availabilityFilter.matches(rec) &&
                    !rec.isWatched &&
                    DiscoveryEngine.identity(rec) !in seedIds &&
                    DiscoveryEngine.identity(rec) !in discovery.historyIds &&
                    !taste.isNotInterested(rec) &&
                    taste.ratingFor(rec) != TasteRating.DISLIKE
            }
            if (items.size < MIN_DISCOVERY_ROW_ITEMS) continue
            val prefix = when (seed.reason) {
                DiscoveryEngine.SeedReason.LOVED, DiscoveryEngine.SeedReason.LIKED -> BECAUSE_YOU_LIKED_PREFIX
                else -> BECAUSE_YOU_WATCHED_PREFIX
            }
            val id = if (rows.isEmpty()) "because_you_watched" else "because_you_watched_${rows.size + 1}"
            rows += CatalogRow(id, prefix + (seed.item.seriesName ?: seed.item.title), RowStyle.POSTER, items)
        }
        return rows
    }

    /**
     * Rows straight from the library index (no Seerr needed): unwatched best matches, hidden gems,
     * titles added a while ago and never started, and short movies. One overview back-fill for all.
     */
    private suspend fun libraryDiscoveryRows(
        userId: String,
        filterType: MediaType?,
        affinity: Map<String, Double>,
        today: LocalDate,
        taste: TasteData
    ): Map<String, List<UnifiedMediaItem>> {
        val index = allLibraryItems
        if (index.isEmpty()) return emptyMap()
        val nowMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val raw = linkedMapOf(
            "unwatched_in_library" to DiscoveryEngine.unwatchedInLibrary(index, affinity, filterType),
            "hidden_gems" to DiscoveryEngine.hiddenGems(index, affinity, today.year, filterType),
            "forgotten_in_library" to DiscoveryEngine.forgottenInLibrary(index, affinity, nowMs, filterType)
        )
        if (filterType == null || filterType == MediaType.MOVIE) {
            raw["short_tonight"] = DiscoveryEngine.shortTonight(index, affinity)
        }
        val union = raw.values.flatten().distinctBy { it.id }
        if (union.isEmpty()) return emptyMap()
        val withText = withOverviews(userId, union, max = union.size.coerceAtMost(LIBRARY_ROWS_OVERVIEW_LIMIT))
            .associateBy { it.id }
        return raw.mapValues { (_, dtos) ->
            dtos.map { mapJellyfinItem(withText[it.id] ?: it) }
                .filterNot { taste.isNotInterested(it) || taste.ratingFor(it) == TasteRating.DISLIKE }
        }.filterValues { it.size >= MIN_DISCOVERY_ROW_ITEMS }
    }

    /**
     * Jellyfin-only Home: the profile's favourite library genres as rows (instead of the first
     * titles of the index), unwatched and best rated first.
     */
    private suspend fun libraryGenreRows(
        userId: String,
        filterType: MediaType?,
        affinity: Map<String, Double>
    ): List<CatalogRow> {
        val index = allLibraryItems
        val genres = DiscoveryEngine.topLibraryGenres(index, affinity, filterType)
        if (genres.isEmpty()) return emptyList()
        val raw = genres.map { g -> g to DiscoveryEngine.libraryGenreRow(index, g, filterType) }
        val union = raw.flatMap { it.second }.distinctBy { it.id }
        val withText = withOverviews(userId, union, max = union.size.coerceAtMost(LIBRARY_ROWS_OVERVIEW_LIMIT))
            .associateBy { it.id }
        return raw.mapNotNull { (genre, dtos) ->
            val items = dtos.map { mapJellyfinItem(withText[it.id] ?: it) }
            if (items.size < MIN_DISCOVERY_ROW_ITEMS) null
            else CatalogRow(
                id = "library_genre_" + DiscoveryEngine.normalizeGenre(genre).replace(Regex("[^a-z0-9]+"), "_"),
                title = genre,
                style = RowStyle.POSTER,
                items = items
            )
        }
    }

    /** "Directed by / Starring <person>": the person behind several of the profile's titles. */
    private suspend fun favoritePersonRow(
        userId: String,
        discovery: PersonalDiscovery,
        filterType: MediaType?,
        activeProfile: UserProfile?
    ): CatalogRow? = coroutineScope {
        val seeds = discovery.seeds.filter { it.item.jellyfinId != null }.take(10)
        if (seeds.size < 2) return@coroutineScope null
        val ids = seeds.mapNotNull { it.item.jellyfinId }
        val peopleById = ids.groupBy { ServerScopedId.serverOf(it) }.map { (_, group) ->
            async {
                val session = sessionFor(group.first(), userId) ?: return@async emptyMap()
                onServer(session, ROW_EXTRA_TIMEOUT_MS) { s ->
                    s.api.getLibraryItems(
                        userId = s.userId,
                        fields = "People",
                        limit = group.size,
                        ids = group.joinToString(",") { s.raw(it) },
                        enableImages = false,
                        enableUserData = false,
                        enableTotalRecordCount = false
                    ).items.associate { dto ->
                        s.scope(dto.id) to dto.people.map { p -> p.copy(id = p.id?.let(s::scope)) }
                    }
                }.orEmpty()
            }
        }.awaitAll().fold(emptyMap<String, List<com.seerflix.tv.core.network.JellyfinPersonDto>>()) { a, b -> a + b }
        val pick = DiscoveryEngine.favoritePerson(
            seeds.mapNotNull { seed -> peopleById[seed.item.jellyfinId]?.let { seed.weight to it } }
        ) ?: return@coroutineScope null
        val seedIds = discovery.seeds.map { DiscoveryEngine.identity(it.item) }.toSet()
        val items = runCatchingCancellable {
            loadPersonCredits(userId, jellyfinPersonId = pick.id, personName = pick.name, activeProfile = activeProfile)
        }.getOrDefault(emptyList())
            .filter { (filterType == null || it.mediaType == filterType) && DiscoveryEngine.identity(it) !in seedIds }
            .sortedBy { it.isWatched }
            .take(20)
        if (items.size < MIN_PERSON_ROW_ITEMS) return@coroutineScope null
        val prefix = if (pick.isDirector) DIRECTED_BY_PREFIX else STARRING_PREFIX
        CatalogRow("more_from_person", prefix + pick.name, RowStyle.POSTER, items)
    }

    /**
     * The connected Trakt member's recommendations (movies and shows interleaved), as library
     * titles when owned, else as Seerr cards (requestable). Empty when Trakt isn't connected.
     */
    private suspend fun traktRecommendedItems(
        userId: String,
        filterType: MediaType?,
        today: LocalDate
    ): List<UnifiedMediaItem> = coroutineScope {
        val account = traktAccount ?: return@coroutineScope emptyList()
        if (!account.isAvailable) return@coroutineScope emptyList()
        val types = when (filterType) {
            null -> listOf(MediaType.MOVIE, MediaType.SERIES)
            MediaType.MOVIE -> listOf(MediaType.MOVIE)
            else -> listOf(MediaType.SERIES)
        }
        val perType = types.map { t -> async { account.recommendations(userId, t).map { t to it } } }.awaitAll()
        val interleaved = buildList {
            val max = perType.maxOfOrNull { it.size } ?: 0
            for (i in 0 until max) perType.forEach { list -> list.getOrNull(i)?.let(::add) }
        }.take(TRAKT_ROW_LIMIT)
        if (interleaved.isEmpty()) return@coroutineScope emptyList()
        interleaved.map { (type, tmdb) ->
            async {
                val owned = if (type == MediaType.MOVIE) tmdbToJellyfinMovie[tmdb] else tmdbToJellyfinSeries[tmdb]
                when {
                    owned != null -> mapJellyfinItem(owned)
                    isSeerrEnabled -> chartCardWithDetails(tmdb, type, today)?.first
                    else -> null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * "Popular in Your Household": what other people watched lately. Administrators see every
     * user on the server; everyone else sees the other profiles on this TV (PIN-locked profiles
     * are left out). Only titles this profile hasn't finished, and only from the library.
     */
    private suspend fun householdPopularItems(
        userId: String,
        activeProfile: UserProfile?,
        nowMs: Long
    ): List<UnifiedMediaItem> = coroutineScope {
        val fromServer = if (activeProfile?.isAdmin == true) {
            runCatchingCancellable { jellyfinApi.getUsers() }.getOrNull()
                ?.filter { it.id != userId }
                ?.take(MAX_HOUSEHOLD_USERS)
                ?.map { u ->
                    async {
                        runCatchingCancellable { jellyfinApi.getRecentlyPlayedForUser(null, u.id).items }
                            .getOrDefault(emptyList())
                    }
                }?.awaitAll()
        } else null
        val perUser = fromServer ?: run {
            val others = runCatchingCancellable { prefs.profilesFlow.first() }.getOrDefault(emptyList())
                .filter { it.jellyfinUserId.isNotBlank() && it.jellyfinUserId != userId }
                .filter { it.jellyfinToken.isNotBlank() && !it.hasPin }
                .distinctBy { it.jellyfinUserId }
                .take(MAX_HOUSEHOLD_USERS)
            others.map { p ->
                async {
                    runCatchingCancellable { jellyfinApi.getRecentlyPlayedForUser(p.jellyfinToken, p.jellyfinUserId).items }
                        .getOrDefault(emptyList())
                }
            }.awaitAll()
        }
        if (perUser.all { it.isEmpty() }) return@coroutineScope emptyList()
        val byId = indexById
        val dtos = DiscoveryEngine.householdPopular(perUser, nowMs)
            .mapNotNull { byId[it.titleId] }
            .filterNot { it.userData?.played == true }
        withOverviews(userId, dtos).map { mapJellyfinItem(it) }
    }

    companion object {
        private const val LIBRARY_INDEX_PAGE_SIZE = 1000
        private const val MAX_LIBRARY_INDEX_ITEMS = 20_000
        private const val LIBRARY_INDEX_TTL_MS = 180_000L
        private const val BACKGROUND_INDEX_REFRESH_DELAY_MS = 4_000L
        private const val OVERVIEW_BACKFILL_LIMIT = 60
        private const val LIBRARY_ROWS_OVERVIEW_LIMIT = 90
        private const val SEEDS_TO_EXPAND = 6
        private const val MAX_BECAUSE_ROWS = 3
        private const val MIN_DISCOVERY_ROW_ITEMS = 5
        private const val MIN_PERSON_ROW_ITEMS = 4
        private const val MAX_HOUSEHOLD_USERS = 12
        private const val TRAKT_ROW_LIMIT = 24
        private const val SEED_RECS_TTL_MS = 30L * 60 * 1000
        private const val DAY_MS = 86_400_000L
        internal const val BECAUSE_YOU_WATCHED_PREFIX = "Because you watched "
        internal const val BECAUSE_YOU_LIKED_PREFIX = "Because you liked "
        internal const val TOP_PICKS_FOR_PREFIX = "Top Picks for "
        internal const val DIRECTED_BY_PREFIX = "Directed by "
        internal const val STARRING_PREFIX = "Starring "
        /** Live TV guide: programmes per request, and the most fetched for the 36-hour window. */
        private const val LIVE_TV_PROGRAM_PAGE = 1000
        /** At least this many programmes, whatever the lineup (the old fixed cap). */
        private const val LIVE_TV_PROGRAM_MIN = 12_000
        /**
         * Budget per channel for 36 hours: 72 half-hour slots, doubled for lineups with short
         * programmes (news bulletins, kids' shorts).
         */
        private const val LIVE_TV_PROGRAMS_PER_CHANNEL = 150
        /** Hard ceiling, so a huge IPTV guide stays within memory. */
        private const val LIVE_TV_PROGRAM_MAX = 50_000
        /** Guide pages fetched at once. */
        private const val LIVE_TV_PROGRAM_PARALLEL_PAGES = 4

        /** How many guide programmes to fetch for a lineup of [channelCount] channels. */
        internal fun liveTvProgramCap(channelCount: Int): Int =
            (channelCount.coerceAtLeast(0).toLong() * LIVE_TV_PROGRAMS_PER_CHANNEL)
                .coerceIn(LIVE_TV_PROGRAM_MIN.toLong(), LIVE_TV_PROGRAM_MAX.toLong())
                .toInt()

        // Several servers
        private val HOME_SERVER = JellyfinServer(id = ServerScopedId.HOME, isHome = true)
        /** How long a row / grid / search waits for an extra server before going on without it. */
        private const val ROW_EXTRA_TIMEOUT_MS = 3_500L
        private const val EXTRA_PART_RETRY_COOLDOWN_MS = 10 * 60_000L
        private const val SEERR_CACHE_MAX_ENTRIES = 400
        private const val DETAILS_EXTRA_TIMEOUT_MS = 5_000L
        /** How long a blocking index load waits for the extra servers' shares. */
        private const val EXTRA_INDEX_WAIT_MS = 6_000L
        /** Whole-library fetch of an extra server (runs in the background past the wait above). */
        private const val EXTRA_INDEX_FETCH_TIMEOUT_MS = 45_000L
        private const val LATEST_FIELDS_MERGED =
            "PrimaryImageAspectRatio,Overview,Genres,ProviderIds,UserData,RunTimeTicks,RemoteTrailers,MediaStreams,DateCreated"
        private const val BROWSE_FIELDS_MERGED =
            "ProviderIds,ProductionYear,OfficialRating,CommunityRating,UserData,PrimaryImageAspectRatio,Genres,RunTimeTicks,SortName,DateCreated"

        // Top 10 charts & curated rows
        private const val TOP_N = 10
        private const val CURATED_ROW_SIZE = 20
        /** Chart titles looked up in parallel per round; 12 leaves room for a couple of misses. */
        private const val CHART_BATCH = 12
        private const val CHART_MAX_BATCHES = 3
        private const val CHART_CARD_TTL_MS = 10L * 60 * 1000
        private const val TMDB_STATUS_RETURNING = "0"
        private const val GENRE_MIN_VOTES_MOVIE = 25
        private const val GENRE_MIN_VOTES_TV = 10
        private const val HOME_RELEASE_CANDIDATE_LIMIT = 28
        private const val NEW_ON_DIGITAL_WINDOW_DAYS = 45L
        private const val COMING_TO_HOME_WINDOW_DAYS = 90L

        private val TMDB_GENRE_NAMES = mapOf(
            12 to "Adventure",
            14 to "Fantasy",
            16 to "Animation",
            18 to "Drama",
            27 to "Horror",
            28 to "Action",
            35 to "Comedy",
            36 to "History",
            37 to "Western",
            53 to "Thriller",
            80 to "Crime",
            99 to "Documentary",
            878 to "Sci-Fi",
            9648 to "Mystery",
            10402 to "Music",
            10749 to "Romance",
            10751 to "Family",
            10752 to "War",
            10759 to "Action & Adventure",
            10762 to "Kids",
            10763 to "News",
            10764 to "Reality",
            10765 to "Sci-Fi & Fantasy",
            10766 to "Soap",
            10767 to "Talk",
            10768 to "War & Politics"
        )

        /**
         * Everything index consumers read besides UserData (EnableUserData) and Overview (back-filled
         * per row by withOverviews): TMDB/IMDB/TVDB ids for matching, genres for genre rows, and
         * runtime/year/ratings for cards and the parental filter.
         */
        private const val LIBRARY_INDEX_FIELDS =
            "ProviderIds,Genres,RunTimeTicks,ProductionYear,OfficialRating,CommunityRating,DateCreated"

        fun extractMediaBadges(streams: List<JellyfinMediaStreamDto>): List<String> {
            if (streams.isEmpty()) return emptyList()
            val badges = linkedSetOf<String>()

            val videoStreams = streams.filter { it.type.equals("Video", ignoreCase = true) }
            val audioStreams = streams.filter { it.type.equals("Audio", ignoreCase = true) }
            val subtitleStreams = streams.filter { it.type.equals("Subtitle", ignoreCase = true) }

            val primaryVideo = videoStreams.firstOrNull()
            if (primaryVideo != null) {
                if ((primaryVideo.width ?: 0) >= 3800 || (primaryVideo.height ?: 0) >= 2000) {
                    badges += "4K"
                } else if ((primaryVideo.width ?: 0) >= 1900 || (primaryVideo.height ?: 0) >= 1000) {
                    badges += "HD"
                }
                val rangeType = primaryVideo.videoRangeType.orEmpty()
                val range = primaryVideo.videoRange.orEmpty()
                when {
                    rangeType.contains("DOVI", ignoreCase = true) ||
                        rangeType.contains("Dolby", ignoreCase = true) -> badges += "Dolby Vision"
                    rangeType.contains("HDR10Plus", ignoreCase = true) -> badges += "HDR10+"
                    rangeType.contains("HDR10", ignoreCase = true) -> badges += "HDR10"
                    range.equals("HDR", ignoreCase = true) || rangeType.equals("HLG", ignoreCase = true) ->
                        badges += "HDR"
                }
            }

            val hasAtmos = audioStreams.any {
                it.audioSpatialFormat.orEmpty().contains("Atmos", ignoreCase = true) ||
                    it.displayTitle.orEmpty().contains("Atmos", ignoreCase = true)
            }
            if (hasAtmos) {
                badges += "Dolby Atmos"
            } else {
                val maxChannels = audioStreams.maxOfOrNull { it.channels ?: 0 } ?: 0
                if (maxChannels >= 8) badges += "7.1"
                else if (maxChannels >= 6) badges += "5.1"
            }

            if (subtitleStreams.any { it.isHearingImpaired || it.displayTitle.orEmpty().contains("SDH", ignoreCase = true) }) {
                badges += "CC"
            } else if (subtitleStreams.isNotEmpty()) {
                badges += "Subtitles"
            }

            val hasAudioDesc = audioStreams.any {
                val descText = "${it.title.orEmpty()} ${it.displayTitle.orEmpty()}"
                descText.contains("Audio Description", ignoreCase = true) ||
                    descText.contains("Descriptive", ignoreCase = true)
            }
            if (hasAudioDesc) {
                badges += "AD"
            }

            return badges.toList()
        }

        /** Non-video Jellyfin collectionTypes excluded to keep Lumen strictly video-focused. */
        private val VIDEO_LIBRARY_EXCLUDED_TYPES = setOf(
            "music", "photos", "books", "audiobooks", "boxsets", "playlists", "livetv", "trailers"
        )

        fun isVideoCapableLibrary(collectionType: String?): Boolean {
            val normalized = collectionType?.trim()?.lowercase() ?: return true
            return normalized !in VIDEO_LIBRARY_EXCLUDED_TYPES
        }

        fun includeTypesForCollectionType(collectionType: String?, folderMode: Boolean = false): String {
            return when (collectionType?.trim()?.lowercase()) {
                "movies" -> if (folderMode) "Movie,Video,Folder" else "Movie"
                "tvshows" -> if (folderMode) "Series,Folder" else "Series"
                "homevideos", "musicvideos" -> if (folderMode) "Video,Movie,MusicVideo,Folder" else "Video,Movie,MusicVideo"
                else -> if (folderMode) "Video,Movie,Series,MusicVideo,Folder" else "Video,Movie,Series,MusicVideo"
            }
        }

        fun patchTimerDefaults(
            defaults: JsonObject,
            series: Boolean,
            options: DvrTimerOptions
        ): JsonObject {
            val map = defaults.toMutableMap()
            options.prePaddingSeconds?.let { map["PrePaddingSeconds"] = JsonPrimitive(it.coerceAtLeast(0)) }
            options.postPaddingSeconds?.let { map["PostPaddingSeconds"] = JsonPrimitive(it.coerceAtLeast(0)) }
            if (series) {
                options.recordNewOnly?.let { map["RecordNewOnly"] = JsonPrimitive(it) }
                options.recordAnyChannel?.let { map["RecordAnyChannel"] = JsonPrimitive(it) }
            }
            return JsonObject(map)
        }

        /**
         * The active profile's UI language for [formatHttpError], which callers use statically.
         * Set by the (singleton) repository from [SessionPreferences.cachedUiLanguage]; English
         * until then.
         */
        @Volatile
        internal var uiLanguageSource: () -> String = { "en" }

        fun formatHttpError(
            err: Throwable,
            fallback: String = "Operation failed",
            strings: UiStrings = stringsForLanguage(uiLanguageSource())
        ): String {
            val raw = err.message?.trim().orEmpty()
            val codeMatch = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE).find(raw)
            val code = codeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            return when (code) {
                401, 403 -> strings.errorHttpAuthRequired.format(code)
                404 -> strings.errorHttpNotFound.format(404)
                409 -> strings.errorHttpAlreadyRequested.format(409)
                500, 502, 503, 504 -> strings.errorHttpServer.format(code)
                else -> if (raw.isNotBlank()) raw else fallback
            }
        }
    }
}

/** The title isn't on the server that holds the playlist (playlists live on the home server). */
class PlaylistServerMismatchException : IllegalStateException("Title is not on the playlist's server")

/**
 * Like [runCatching], but never swallows coroutine cancellation: a cancelled caller must not get a
 * `Result.failure(CancellationException)` back (which would surface as a bogus error / empty state).
 */
internal inline fun <R> runCatchingCancellable(block: () -> R): Result<R> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
