package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.ArrQualityProfile
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CastMember
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.ContentRatingHelper
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.ExtrasItem
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.JellyfinLibraryView
import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.LiveTvProgram
import com.seerflix.tv.core.model.MediaRequestSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinMediaStreamDto
import com.seerflix.tv.core.network.JellyfinUpdateUserDataRequest
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrCreateIssueBody
import com.seerflix.tv.core.network.SeerrCreateRequestBody
import com.seerflix.tv.core.network.SeerrMediaInfoDto
import com.seerflix.tv.core.network.SeerrMediaResultDto
import com.seerflix.tv.core.network.SeerrRelatedVideoDto
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.JellyfinPlaybackManager
import com.seerflix.tv.core.player.YouTubeUrlParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UnifiedCatalogRepository @Inject constructor(
    private val jellyfinApi: JellyfinApi,
    private val seerrApi: SeerrApi,
    private val prefs: SessionPreferences
) {
    private var tmdbToJellyfinMovie: Map<Int, JellyfinItemDto> = emptyMap()
    private var tmdbToJellyfinSeries: Map<Int, JellyfinItemDto> = emptyMap()
    private var allLibraryItems: List<JellyfinItemDto> = emptyList()
    private var lastIndexUserId: String? = null
    private var lastIndexMaxRating: String? = null
    private var lastIndexTimestampMs: Long = 0L

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

    private val jfBaseUrl: String
        get() = prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }

    fun findJellyfinIdForTmdb(tmdbId: Int, mediaType: MediaType): String? {
        return when (mediaType) {
            MediaType.MOVIE -> tmdbToJellyfinMovie[tmdbId]?.id
            MediaType.SERIES, MediaType.EPISODE -> tmdbToJellyfinSeries[tmdbId]?.id
        }
    }

    /** TMDB id of an owned series given its Jellyfin item id (episodes only carry their own TMDB id). */
    private fun seriesTmdbIdFor(seriesId: String?): Int? {
        if (seriesId.isNullOrBlank()) return null
        return tmdbToJellyfinSeries.entries.firstOrNull { it.value.id == seriesId }?.key
    }

    suspend fun refreshLibraryIndex(
        userId: String,
        activeProfile: UserProfile? = null,
        forceRefresh: Boolean = false
    ) {
        val maxRating = activeProfile?.effectiveRatingLimit?.jellyfinMaxOfficialRating
        val now = System.currentTimeMillis()
        if (!forceRefresh &&
            allLibraryItems.isNotEmpty() &&
            lastIndexUserId == userId &&
            lastIndexMaxRating == maxRating &&
            now - lastIndexTimestampMs < 180_000L
        ) {
            return
        }

        // The index is deliberately NOT filtered by MaxOfficialRating: it is used to match Seerr
        // results to owned items. If restricted titles were dropped here, an R-rated movie that is
        // in the library would surface in discovery rows as an unrated, "requestable" Seerr item and
        // slip past the parental filter. Rating filtering is applied client-side to every row
        // (ContentRatingHelper) using the matched item's OfficialRating instead.
        val items = runCatchingCancellable {
            val collected = mutableListOf<JellyfinItemDto>()
            var startIndex = 0
            while (collected.size < MAX_LIBRARY_INDEX_ITEMS) {
                val page = jellyfinApi.getLibraryItems(
                    userId = userId,
                    fields = "ProviderIds,Overview,Genres,RunTimeTicks,UserData,ProductionYear,OfficialRating,CommunityRating",
                    limit = LIBRARY_INDEX_PAGE_SIZE,
                    startIndex = startIndex.takeIf { it > 0 }
                )
                collected += page.items
                startIndex += page.items.size
                if (page.items.size < LIBRARY_INDEX_PAGE_SIZE ||
                    (page.totalRecordCount in 1..startIndex)
                ) {
                    break
                }
            }
            collected.toList()
        }.getOrDefault(emptyList())

        // Never keep serving another user's index after a profile switch, even if this fetch failed.
        if (items.isNotEmpty() || allLibraryItems.isEmpty() || lastIndexUserId != userId) {
            allLibraryItems = items
            val movies = HashMap<Int, JellyfinItemDto>(items.size)
            val series = HashMap<Int, JellyfinItemDto>(items.size)
            items.forEach { dto ->
                val tmdb = dto.providerIds["Tmdb"]?.toIntOrNull()
                    ?: dto.providerIds["tmdb"]?.toIntOrNull()
                if (tmdb != null) {
                    when {
                        dto.type.equals("Movie", ignoreCase = true) -> movies[tmdb] = dto
                        dto.type.equals("Series", ignoreCase = true) -> series[tmdb] = dto
                    }
                }
            }
            tmdbToJellyfinMovie = movies
            tmdbToJellyfinSeries = series
            lastIndexUserId = userId
            lastIndexMaxRating = maxRating
            lastIndexTimestampMs = now
        }
    }

    suspend fun markWatched(userId: String, itemId: String, watched: Boolean): Result<Unit> = runCatchingCancellable {
        if (watched) {
            jellyfinApi.markPlayed(userId, itemId)
        } else {
            jellyfinApi.markUnplayed(userId, itemId)
        }
        invalidateDetailsCache()
    }

    suspend fun loadUserLibraryViews(userId: String): List<JellyfinLibraryView> {
        return runCatchingCancellable {
            jellyfinApi.getUserViews(userId).items.map { dto ->
                JellyfinLibraryView(
                    id = dto.id,
                    name = dto.name,
                    collectionType = dto.collectionType
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun toggleMyList(userId: String, item: UnifiedMediaItem): Result<Boolean> = runCatchingCancellable {
        invalidateDetailsCache()
        if (item.jellyfinId != null) {
            val newState = !item.isInMyList
            if (newState) {
                jellyfinApi.addFavorite(userId, item.jellyfinId)
            } else {
                jellyfinApi.removeFavorite(userId, item.jellyfinId)
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
        val updated = runCatchingCancellable {
            jellyfinApi.updateUserData(
                userId = userId,
                itemId = itemId,
                body = JellyfinUpdateUserDataRequest(playbackPositionTicks = 0L)
            )
        }
        if (updated.isFailure) {
            jellyfinApi.markPlayed(userId, itemId)
            jellyfinApi.markUnplayed(userId, itemId)
        }
        invalidateDetailsCache()
    }

    suspend fun loadMyList(userId: String): List<UnifiedMediaItem> = coroutineScope {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId)
        }
        val jfFavsDeferred = async {
            runCatchingCancellable { jellyfinApi.getFavoriteItems(userId = userId).items }.getOrDefault(emptyList())
        }
        val localEntries = prefs.getLocalMyList(userId)
        val remainingLocal = mutableListOf<UnifiedMediaItem>()

        for (entry in localEntries) {
            val matchedJf = if (entry.mediaType == MediaType.MOVIE) {
                tmdbToJellyfinMovie[entry.tmdbId]
            } else {
                tmdbToJellyfinSeries[entry.tmdbId]
            }
            if (matchedJf != null) {
                // Auto-promote local My List item to Jellyfin Favorites now that it's in the library
                runCatchingCancellable { jellyfinApi.addFavorite(userId, matchedJf.id) }
                prefs.removeFromLocalMyList(userId, entry.tmdbId, entry.mediaType)
            } else {
                remainingLocal += entry.toUnifiedMediaItem()
            }
        }

        val jfFavs = jfFavsDeferred.await().map { mapJellyfinItem(it).copy(isInMyList = true) }
        val seenTmdb = jfFavs.mapNotNull { it.tmdbId?.let { id -> "${it.mediaType}_$id" } }.toSet()
        val dedupedLocal = remainingLocal.filterNot {
            it.tmdbId != null && "${it.mediaType}_${it.tmdbId}" in seenTmdb
        }
        jfFavs + dedupedLocal
    }

    /**
     * Lightweight re-fetch of only the Continue Watching items, used when Home comes back into
     * view after playback so progress / newly started titles show up without a full reload.
     */
    suspend fun loadContinueWatchingItems(userId: String, activeProfile: UserProfile?): List<UnifiedMediaItem> {
        val items = jellyfinApi.getResumeItems(userId = userId, limit = 15).items.map { mapJellyfinItem(it) }
        return ContentRatingHelper.filterForProfile(items, activeProfile)
    }

    suspend fun loadHomeRows(
        userId: String,
        filterType: MediaType? = null,
        genreFilter: GenreFilterOption? = null,
        availabilityFilter: AvailabilityFilter = AvailabilityFilter.ALL,
        today: LocalDate = LocalDate.now(),
        activeProfile: UserProfile? = null,
        forceRefresh: Boolean = false
    ): List<CatalogRow> = coroutineScope {
        refreshLibraryIndex(userId, activeProfile, forceRefresh = forceRefresh)

        // If a specific genre chip is selected on TV Shows / Movies, return genre-focused Top 10 + discovery rows
        if (genreFilter != null && (genreFilter.movieGenreId != null || genreFilter.tvGenreId != null)) {
            return@coroutineScope loadGenreFilteredRows(
                userId = userId,
                filterType = filterType,
                genre = genreFilter,
                availabilityFilter = availabilityFilter,
                activeProfile = activeProfile
            )
        }

        val seerrActive = isSeerrEnabled
        val resumeDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { jellyfinApi.getResumeItems(userId = userId, limit = 15).items }.getOrDefault(emptyList())
        }
        val latestDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { jellyfinApi.getLatestItems(userId = userId, limit = 20) }.getOrDefault(emptyList())
        }
        val myListDeferred = async {
            runCatchingCancellable { loadMyList(userId) }.getOrDefault(emptyList())
        }
        val playedDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { jellyfinApi.getPlayedItems(userId = userId, limit = 20).items }.getOrDefault(emptyList())
        }
        val trendingDeferred = async {
            if (!seerrActive) emptyList()
            else runCatchingCancellable { seerrApi.getTrending(language = metaLang).results }.getOrDefault(emptyList())
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
        val topNewTvPremieresDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchTop10NewSeriesPremieres(today, availabilityFilter)
            } else emptyList()
        }
        val topComedyMoviesDeferred = async {
            if (seerrActive && (filterType == null || filterType == MediaType.MOVIE)) {
                fetchTop10MoviesByGenre(35, availabilityFilter)
            } else emptyList()
        }
        val topActionMoviesDeferred = async {
            if (seerrActive && (filterType == null || filterType == MediaType.MOVIE)) {
                fetchTop10MoviesByGenre(28, availabilityFilter)
            } else emptyList()
        }
        val topHorrorMoviesDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchTop10MoviesByGenre(27, availabilityFilter)
            } else emptyList()
        }
        val topSciFiMoviesDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchTop10MoviesByGenre(878, availabilityFilter)
            } else emptyList()
        }
        val topAnimatedMoviesDeferred = async {
            if (seerrActive && filterType == MediaType.MOVIE) {
                fetchTop10MoviesByGenre(16, availabilityFilter)
            } else emptyList()
        }
        val topSciFiTvDeferred = async {
            if (seerrActive && (filterType == null || filterType == MediaType.SERIES)) {
                fetchTop10TvByGenre(10765, availabilityFilter)
            } else emptyList()
        }
        val topComedyTvDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchTop10TvByGenre(35, availabilityFilter)
            } else emptyList()
        }
        val topDramaTvDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchTop10TvByGenre(18, availabilityFilter)
            } else emptyList()
        }
        val topCrimeTvDeferred = async {
            if (seerrActive && filterType == MediaType.SERIES) {
                fetchTop10TvByGenre(80, availabilityFilter)
            } else emptyList()
        }
        val readyToWatchDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else loadReadyToWatchAlerts(userId)
        }
        val collectionsDeferred = async {
            if ((filterType == null || filterType == MediaType.MOVIE) && availabilityFilter != AvailabilityFilter.REQUESTABLE) {
                loadBoxSetCollections(userId)
            } else emptyList()
        }
        val airingThisWeekDeferred = async {
            if (filterType != MediaType.MOVIE && availabilityFilter != AvailabilityFilter.REQUESTABLE) {
                loadAiringThisWeek(userId, today)
            } else emptyList()
        }
        val upcomingMoviesDeferred = async {
            if (seerrActive && filterType != MediaType.SERIES) {
                runCatchingCancellable { seerrApi.getUpcomingMovies(language = metaLang).results }.getOrDefault(emptyList())
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
                pinnedIds.mapNotNull { libId ->
                    val view = views[libId]
                    val libItems = runCatchingCancellable {
                        jellyfinApi.getLibraryItems(
                            userId = userId,
                            parentId = libId,
                            limit = 30,
                            maxOfficialRating = maxRating
                        ).items.map { mapJellyfinItem(it) }
                    }.getOrDefault(emptyList())
                        .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
                    if (libItems.isEmpty()) null
                    else CatalogRow(
                        id = "lib_$libId",
                        title = view?.name ?: "Library",
                        style = RowStyle.POSTER,
                        items = libItems
                    )
                }
            }
        }

        val rows = mutableListOf<CatalogRow>()

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

        val resumeRaw = resumeDeferred.await()
        val resumeItems = resumeRaw
            .map { mapJellyfinItem(it) }
            .filter {
                (filterType == null ||
                    (filterType == MediaType.MOVIE && it.mediaType == MediaType.MOVIE) ||
                    (filterType == MediaType.SERIES && it.mediaType != MediaType.MOVIE)) &&
                    availabilityFilter.matches(it)
            }
        if (resumeItems.isNotEmpty()) {
            rows += CatalogRow(
                id = "continue_watching",
                title = "Continue Watching",
                style = RowStyle.CONTINUE_WATCHING,
                items = resumeItems
            )
        }

        val myListItems = myListDeferred.await()
            .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
        if (myListItems.isNotEmpty()) {
            rows += CatalogRow(
                id = "my_list",
                title = "My List",
                style = RowStyle.POSTER,
                items = myListItems
            )
        }

        rows += pinnedLibraryRowsDeferred.await()

        if (filterType == null || filterType == MediaType.MOVIE) {
            val top10MoviesNow = topMoviesNowDeferred.await()
            if (top10MoviesNow.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_movies_now",
                    title = "Top 10 Movies Out Right Now",
                    style = RowStyle.TOP_10,
                    items = top10MoviesNow
                )
            }
        }

        if (filterType == null || filterType == MediaType.SERIES) {
            val top10TvNow = topTvAiringNowDeferred.await()
            if (top10TvNow.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_tv_now",
                    title = "Top 10 TV Shows Airing Now",
                    style = RowStyle.TOP_10,
                    items = top10TvNow
                )
            }
        }

        if (filterType == MediaType.SERIES) {
            val newPremieres = topNewTvPremieresDeferred.await()
            if (newPremieres.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_new_tv_premieres",
                    title = "Top 10 New Series Premieres",
                    style = RowStyle.TOP_10,
                    items = newPremieres
                )
            }
        }

        // Netflix-style Genre Top 10 rows
        if (filterType == null || filterType == MediaType.MOVIE) {
            val comedyMovies = topComedyMoviesDeferred.await()
            if (comedyMovies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_comedy_movies",
                    title = "Top 10 Comedy Movies",
                    style = RowStyle.TOP_10,
                    items = comedyMovies
                )
            }
        }

        if (filterType == null || filterType == MediaType.SERIES) {
            val sciFiTv = topSciFiTvDeferred.await()
            if (sciFiTv.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_scifi_tv",
                    title = "Top 10 Sci-Fi & Fantasy Series",
                    style = RowStyle.TOP_10,
                    items = sciFiTv
                )
            }
        }

        val trending = trendingDeferred.await()
            .mapNotNull { res ->
                val type = when (res.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> null
                } ?: return@mapNotNull null
                if (filterType != null && type != filterType) return@mapNotNull null
                mapSeerrResult(res, type)
            }
            .filter(availabilityFilter::matches)
        if (trending.isNotEmpty()) {
            rows += CatalogRow(
                id = "trending_now",
                title = "Trending Now",
                style = RowStyle.POSTER,
                items = trending
            )
        }

        // Personalized "Because you watched <RecentTitle>" row
        val playedRaw = playedDeferred.await()
        val recentSeed = resumeItems.firstOrNull { it.tmdbId != null || it.jellyfinId != null }
            ?: playedRaw.firstOrNull()?.let { mapJellyfinItem(it) }
        if (recentSeed != null) {
            val seedIsEpisode = recentSeed.mediaType == MediaType.EPISODE
            val seedType = if (seedIsEpisode) MediaType.SERIES else recentSeed.mediaType
            val seedName = recentSeed.seriesName ?: recentSeed.title
            // An episode's ProviderIds hold the *episode's* TMDB id; recommendations must be keyed
            // on the parent series (both the Seerr TV id and the Jellyfin item id).
            val seedSeriesId = if (seedIsEpisode) {
                (resumeRaw + playedRaw).firstOrNull { it.id == recentSeed.jellyfinId }?.seriesId
            } else null
            val seedTmdbId = if (seedIsEpisode) seriesTmdbIdFor(seedSeriesId) else recentSeed.tmdbId
            val seedJellyfinId = if (seedIsEpisode) seedSeriesId else recentSeed.jellyfinId
            val personalized = runCatchingCancellable {
                loadRecommendations(
                    userId = userId,
                    tmdbId = seedTmdbId,
                    jellyfinId = seedJellyfinId,
                    mediaType = seedType,
                    activeProfile = activeProfile
                )
            }.getOrDefault(emptyList())
                .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
            if (personalized.isNotEmpty()) {
                rows += CatalogRow(
                    id = "because_you_watched",
                    title = "Because you watched $seedName",
                    style = RowStyle.POSTER,
                    items = personalized
                )
            }
        }

        if (filterType == null || filterType == MediaType.MOVIE) {
            val actionMovies = topActionMoviesDeferred.await()
            if (actionMovies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_action_movies",
                    title = "Top 10 Action & Thriller Movies",
                    style = RowStyle.TOP_10,
                    items = actionMovies
                )
            }
        }

        if (filterType == MediaType.MOVIE) {
            val horrorMovies = topHorrorMoviesDeferred.await()
            if (horrorMovies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_horror_movies",
                    title = "Top 10 Horror & Mystery Movies",
                    style = RowStyle.TOP_10,
                    items = horrorMovies
                )
            }
            val sciFiMovies = topSciFiMoviesDeferred.await()
            if (sciFiMovies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_scifi_movies",
                    title = "Top 10 Sci-Fi Movies",
                    style = RowStyle.TOP_10,
                    items = sciFiMovies
                )
            }
            val animatedMovies = topAnimatedMoviesDeferred.await()
            if (animatedMovies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_animated_movies",
                    title = "Top 10 Animated Movies",
                    style = RowStyle.TOP_10,
                    items = animatedMovies
                )
            }
        }

        if (filterType == MediaType.SERIES) {
            val comedyTv = topComedyTvDeferred.await()
            if (comedyTv.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_comedy_tv",
                    title = "Top 10 Comedy Series",
                    style = RowStyle.TOP_10,
                    items = comedyTv
                )
            }
            val dramaTv = topDramaTvDeferred.await()
            if (dramaTv.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_drama_tv",
                    title = "Top 10 Drama Series",
                    style = RowStyle.TOP_10,
                    items = dramaTv
                )
            }
            val crimeTv = topCrimeTvDeferred.await()
            if (crimeTv.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_crime_tv",
                    title = "Top 10 Crime Series",
                    style = RowStyle.TOP_10,
                    items = crimeTv
                )
            }
        }

        val latest = latestDeferred.await()
            .map { mapJellyfinItem(it) }
            .filter { (filterType == null || it.mediaType == filterType) && availabilityFilter.matches(it) }
        if (latest.isNotEmpty()) {
            rows += CatalogRow(
                id = "recently_added",
                title = "Recently Added to Your Server",
                style = RowStyle.POSTER,
                items = latest
            )
        }

        // In Jellyfin-Only Mode (or when user has a large library), surface Movies & TV Series library rows
        if (!seerrActive && availabilityFilter != AvailabilityFilter.REQUESTABLE) {
            val libraryMovies = allLibraryItems
                .filter { it.type.equals("Movie", ignoreCase = true) }
                .map { mapJellyfinItem(it) }
                .filter(availabilityFilter::matches)
                .take(25)
            if ((filterType == null || filterType == MediaType.MOVIE) && libraryMovies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "jellyfin_movies",
                    title = "Movies in Your Library",
                    style = RowStyle.POSTER,
                    items = libraryMovies
                )
            }
            val librarySeries = allLibraryItems
                .filter { it.type.equals("Series", ignoreCase = true) }
                .map { mapJellyfinItem(it) }
                .filter(availabilityFilter::matches)
                .take(25)
            if ((filterType == null || filterType == MediaType.SERIES) && librarySeries.isNotEmpty()) {
                rows += CatalogRow(
                    id = "jellyfin_series",
                    title = "TV Shows in Your Library",
                    style = RowStyle.POSTER,
                    items = librarySeries
                )
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

        val airingThisWeek = airingThisWeekDeferred.await()
            .filter(availabilityFilter::matches)
        if (airingThisWeek.isNotEmpty()) {
            rows += CatalogRow(
                id = "airing_this_week",
                title = "New Episodes Coming This Week",
                style = RowStyle.POSTER,
                items = airingThisWeek
            )
        }

        val upcoming = buildList {
            if (filterType == null || filterType == MediaType.MOVIE) {
                addAll(upcomingMoviesDeferred.await().map { mapSeerrResult(it, MediaType.MOVIE, today) })
            }
            if (filterType == null || filterType == MediaType.SERIES) {
                addAll(upcomingTvDeferred.await().map { mapSeerrResult(it, MediaType.SERIES, today) })
            }
        }
            .distinctBy { "${it.mediaType}_${it.tmdbId}" }
            .filter(availabilityFilter::matches)
        if (upcoming.isNotEmpty()) {
            rows += CatalogRow(
                id = "new_upcoming",
                title = "New & Coming Soon",
                style = RowStyle.POSTER,
                items = upcoming
            )
        }

        val watchAgain = playedRaw
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

        applyRowCustomizationAndParentalFilters(rows, activeProfile)
    }

    private fun applyRowCustomizationAndParentalFilters(
        rows: List<CatalogRow>,
        activeProfile: UserProfile?
    ): List<CatalogRow> {
        val hiddenIds = activeProfile?.hiddenHomeRowIds?.toSet().orEmpty()
        val filtered = rows
            .filterNot { it.id in hiddenIds }
            .mapNotNull { row ->
                val safeItems = ContentRatingHelper.filterForProfile(row.items, activeProfile)
                if (safeItems.isEmpty()) null else row.copy(items = safeItems)
            }
        val order = activeProfile?.homeRowOrder.orEmpty()
        if (order.isEmpty()) return filtered
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
        activeProfile: UserProfile? = null
    ): List<CatalogRow> = coroutineScope {
        val seerrActive = isSeerrEnabled
        val movieGenreDeferred = async {
            if (seerrActive && filterType != MediaType.SERIES && genre.movieGenreId != null) {
                fetchGenrePool(availabilityFilter) { page ->
                    runCatchingCancellable { seerrApi.getMoviesByGenre(genre.movieGenreId, page = page, language = metaLang).results }
                        .getOrDefault(emptyList())
                        .map { mapSeerrResult(it, MediaType.MOVIE) }
                }
            } else emptyList()
        }
        val tvGenreDeferred = async {
            if (seerrActive && filterType != MediaType.MOVIE && genre.tvGenreId != null) {
                fetchGenrePool(availabilityFilter) { page ->
                    runCatchingCancellable { seerrApi.getTvByGenre(genre.tvGenreId, page = page, language = metaLang).results }
                        .getOrDefault(emptyList())
                        .map { mapSeerrResult(it, MediaType.SERIES) }
                }
            } else emptyList()
        }

        val rows = mutableListOf<CatalogRow>()
        if (availabilityFilter != AvailabilityFilter.REQUESTABLE) {
            val libraryMatches = allLibraryItems
                .map { mapJellyfinItem(it) }
                .filter { item ->
                    (filterType == null || item.mediaType == filterType) &&
                        availabilityFilter.matches(item) &&
                        item.genres.any { g ->
                            g.contains(genre.label.substringBefore(" "), ignoreCase = true)
                        }
                }
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
        refreshLibraryIndex(userId, activeProfile, forceRefresh = forceRefresh)
        val seerrActive = isSeerrEnabled
        val moviesNowDeferred = async {
            if (seerrActive) fetchTop10MoviesOutNow(today, availabilityFilter) else emptyList()
        }
        val tvNowDeferred = async {
            if (seerrActive) fetchTop10TvAiringNow(today, availabilityFilter) else emptyList()
        }
        val trendingDeferred = async {
            if (seerrActive) runCatchingCancellable { seerrApi.getTrending(language = metaLang).results }.getOrDefault(emptyList())
            else emptyList()
        }
        val airingThisWeekDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else loadAiringThisWeek(userId, today)
        }
        val upcomingMoviesDeferred = async {
            if (seerrActive) runCatchingCancellable { seerrApi.getUpcomingMovies(language = metaLang).results }.getOrDefault(emptyList())
            else emptyList()
        }
        val upcomingTvDeferred = async {
            if (seerrActive) runCatchingCancellable { seerrApi.getUpcomingTv(language = metaLang).results }.getOrDefault(emptyList())
            else emptyList()
        }
        val latestDeferred = async {
            if (availabilityFilter == AvailabilityFilter.REQUESTABLE) emptyList()
            else runCatchingCancellable { jellyfinApi.getLatestItems(userId = userId, limit = 24) }.getOrDefault(emptyList())
        }

        val rows = buildList {
            val moviesNow = moviesNowDeferred.await()
            if (moviesNow.isNotEmpty()) {
                add(CatalogRow("np_top10_movies_now", "Top 10 Movies Out Right Now", RowStyle.TOP_10, moviesNow))
            }
            val tvNow = tvNowDeferred.await()
            if (tvNow.isNotEmpty()) {
                add(CatalogRow("np_top10_tv_now", "Top 10 TV Shows Airing Now", RowStyle.TOP_10, tvNow))
            }
            val trending = trendingDeferred.await().mapNotNull { res ->
                val type = when (res.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> null
                } ?: return@mapNotNull null
                mapSeerrResult(res, type, today)
            }.filter(availabilityFilter::matches)
            if (trending.isNotEmpty()) {
                add(CatalogRow("np_trending", "Worth the Wait & Trending", RowStyle.TOP_10, trending.take(10)))
            }
            val airingThisWeek = airingThisWeekDeferred.await()
                .filter(availabilityFilter::matches)
            if (airingThisWeek.isNotEmpty()) {
                add(CatalogRow("np_airing_this_week", "New Episodes Coming This Week", RowStyle.POSTER, airingThisWeek))
            }
            val upcomingMovies = upcomingMoviesDeferred.await()
                .map { mapSeerrResult(it, MediaType.MOVIE, today) }
                .filter(availabilityFilter::matches)
            if (upcomingMovies.isNotEmpty()) {
                add(CatalogRow("np_coming_movies", "Coming Soon • Movies", RowStyle.POSTER, upcomingMovies))
            }
            val upcomingTv = upcomingTvDeferred.await()
                .map { mapSeerrResult(it, MediaType.SERIES, today) }
                .filter(availabilityFilter::matches)
            if (upcomingTv.isNotEmpty()) {
                add(CatalogRow("np_coming_tv", "Coming Soon • TV Shows", RowStyle.POSTER, upcomingTv))
            }
            val latest = latestDeferred.await()
                .map { mapJellyfinItem(it) }
                .filter(availabilityFilter::matches)
            if (latest.isNotEmpty()) {
                add(CatalogRow("np_latest", "Just Added to Your Server", RowStyle.POSTER, latest))
            }
        }
        applyRowCustomizationAndParentalFilters(rows, activeProfile)
    }

    private suspend fun fetchTop10MoviesOutNow(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> {
        val windowStart = today.minusDays(60).toString()
        val windowEnd = today.toString()
        val windowed = fetchTop10Matching(availabilityFilter) { page ->
            runCatchingCancellable {
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
                .map { mapSeerrResult(it, MediaType.MOVIE) }
        }
        if (windowed.isNotEmpty()) return windowed

        // Fallback if Seerr server ignores date bounds
        return fetchTop10Matching(availabilityFilter) { page ->
            runCatchingCancellable { seerrApi.getDiscoverMovies(page = page, language = metaLang).results }
                .getOrDefault(emptyList())
                .filter { isReleasedOnOrBefore(it.releaseDate, today) }
                .map { mapSeerrResult(it, MediaType.MOVIE) }
        }
    }

    private suspend fun fetchTop10TvAiringNow(
        today: LocalDate,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> = coroutineScope {
        val airingDeferred = async {
            fetchTop10Matching(availabilityFilter) { page ->
                runCatchingCancellable { seerrApi.getUpcomingTv(page = page, language = metaLang).results }
                    .getOrDefault(emptyList())
                    .filter { isReleasedOnOrBefore(it.firstAirDate, today) }
                    .map { mapSeerrResult(it, MediaType.SERIES) }
            }
        }
        val airing = airingDeferred.await()
        if (airing.size >= 10) return@coroutineScope airing.take(10)

        // Supplement with currently popular TV shows released on or before today
        val fallbackPool = fetchTop10Matching(availabilityFilter) { page ->
            runCatchingCancellable {
                seerrApi.getDiscoverTv(
                    page = page,
                    language = metaLang,
                    sortBy = "popularity.desc",
                    firstAirDateLte = today.toString()
                ).results
            }.getOrDefault(emptyList())
                .filter { isReleasedOnOrBefore(it.firstAirDate, today) }
                .map { mapSeerrResult(it, MediaType.SERIES) }
        }
        (airing + fallbackPool)
            .distinctBy { "${it.mediaType}_${it.tmdbId}_${it.id}" }
            .take(10)
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
                .map { mapSeerrResult(it, MediaType.SERIES) }
        }
    }

    private suspend fun fetchTop10MoviesByGenre(
        genreId: Int,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> = fetchTop10Matching(availabilityFilter) { page ->
        runCatchingCancellable { seerrApi.getMoviesByGenre(genreId, page = page, language = metaLang).results }
            .getOrDefault(emptyList())
            .map { mapSeerrResult(it, MediaType.MOVIE) }
    }

    private suspend fun fetchTop10TvByGenre(
        genreId: Int,
        availabilityFilter: AvailabilityFilter
    ): List<UnifiedMediaItem> = fetchTop10Matching(availabilityFilter) { page ->
        runCatchingCancellable { seerrApi.getTvByGenre(genreId, page = page, language = metaLang).results }
            .getOrDefault(emptyList())
            .map { mapSeerrResult(it, MediaType.SERIES) }
    }

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
        val prefix = dateStr?.trim()?.take(10)
        if (prefix.isNullOrBlank()) return true
        val parsed = runCatchingCancellable { LocalDate.parse(prefix) }.getOrNull() ?: return true
        return !parsed.isAfter(cutoff)
    }

    suspend fun loadTrendingTitles(
        userId: String,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> = coroutineScope {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId, activeProfile)
        }
        if (!isSeerrEnabled) {
            return@coroutineScope ContentRatingHelper.filterForProfile(
                allLibraryItems.take(20).map { mapJellyfinItem(it) },
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
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId, activeProfile)
        }
        if (!isSeerrEnabled) {
            val matches = allLibraryItems
                .map { mapJellyfinItem(it) }
                .filter { item ->
                    item.genres.any { g ->
                        g.contains(genre.label.substringBefore(" "), ignoreCase = true)
                    }
                }
            return@coroutineScope ContentRatingHelper.filterForProfile(matches, activeProfile)
        }
        val movieDeferred = async {
            genre.movieGenreId?.let { id ->
                runCatchingCancellable { seerrApi.getMoviesByGenre(id, language = metaLang).results }.getOrDefault(emptyList())
            }.orEmpty()
        }
        val tvDeferred = async {
            genre.tvGenreId?.let { id ->
                runCatchingCancellable { seerrApi.getTvByGenre(id, language = metaLang).results }.getOrDefault(emptyList())
            }.orEmpty()
        }
        val movies = movieDeferred.await().map { mapSeerrResult(it, MediaType.MOVIE) }
        val tv = tvDeferred.await().map { mapSeerrResult(it, MediaType.SERIES) }
        ContentRatingHelper.filterForProfile(
            (movies + tv).distinctBy { "${it.mediaType}_${it.tmdbId}" },
            activeProfile
        )
    }

    suspend fun loadFullDetails(
        userId: String,
        tmdbId: Int?,
        jellyfinId: String?,
        mediaType: MediaType
    ): Result<UnifiedMediaItem> = runCatchingCancellable {
        val cacheKey = "${userId}_${mediaType.name}_${tmdbId ?: ""}_${jellyfinId ?: ""}"
        getCachedDetails(cacheKey)?.let { return@runCatchingCancellable it }

        coroutineScope {
            val resolvedType = if (mediaType == MediaType.EPISODE) MediaType.SERIES else mediaType
            var jfItem: JellyfinItemDto? = jellyfinId?.let { id ->
                runCatchingCancellable { jellyfinApi.getItemDetails(userId, id) }.getOrNull()
            }
            if (jfItem?.type.equals("Episode", ignoreCase = true) && !jfItem?.seriesId.isNullOrBlank()) {
                val parentSeries = runCatchingCancellable { jellyfinApi.getItemDetails(userId, jfItem!!.seriesId!!) }.getOrNull()
                if (parentSeries != null) {
                    jfItem = parentSeries
                }
            }
            val resolvedTmdbId = (if (mediaType == MediaType.EPISODE) null else tmdbId)
                ?: jfItem?.providerIds?.get("Tmdb")?.toIntOrNull()
                ?: jfItem?.providerIds?.get("tmdb")?.toIntOrNull()
                ?: tmdbId

            if (jfItem == null && resolvedTmdbId != null) {
                jfItem = if (resolvedType == MediaType.MOVIE) {
                    tmdbToJellyfinMovie[resolvedTmdbId]
                } else {
                    tmdbToJellyfinSeries[resolvedTmdbId]
                }
            }

            val extrasDeferred = async {
                jfItem?.id?.let { id ->
                    runCatchingCancellable { jellyfinApi.getSpecialFeatures(id, userId) }.getOrDefault(emptyList())
                }.orEmpty().map { extra ->
                    ExtrasItem(
                        id = extra.id,
                        name = extra.name,
                        overview = extra.overview.orEmpty(),
                        extraType = extra.extraType,
                        runtimeMinutes = extra.runTimeTicks?.let { (it / 600_000_000L).toInt() },
                        thumbnailUrl = MediaImageUrls.jellyfinPrimary(jfBaseUrl, extra.id, 480)
                    )
                }
            }

            val localTrailerDeferred = async {
                jfItem?.id?.let { id ->
                    runCatchingCancellable { jellyfinApi.getLocalTrailers(id, userId).firstOrNull()?.id }.getOrNull()
                }
            }

            val seerrMergedItem: UnifiedMediaItem? = if (resolvedTmdbId != null && isSeerrEnabled) {
                if (resolvedType == MediaType.MOVIE) {
                    val seerrMovieResult = runCatchingCancellable {
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
                    val seerrTvResult = runCatchingCancellable {
                        seerrApi.getTvDetails(resolvedTmdbId, language = metaLang)
                    }
                    val seerrTv = seerrTvResult.getOrNull()
                    if (seerrTv != null) {
                        val jfSeasons = jfItem?.id?.let { id ->
                            runCatchingCancellable { jellyfinApi.getSeasons(id, userId).items }.getOrDefault(emptyList())
                        }.orEmpty()
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
                    val jfSeasons = runCatchingCancellable { jellyfinApi.getSeasons(jfItem.id, userId).items }.getOrDefault(emptyList())
                    val mappedSeasons = jfSeasons.map { s ->
                        SeasonSummary(
                            seasonNumber = s.indexNumber ?: 1,
                            name = s.name,
                            episodeCount = s.childCount ?: 0,
                            overview = s.overview,
                            posterUrl = MediaImageUrls.jellyfinPrimary(jfBaseUrl, s.id),
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

            val mediaVersions = jfItem?.mediaSources
                ?.map { JellyfinPlaybackManager.mapMediaSourceOption(it) }
                .orEmpty()

            baseItem.copy(
                isInMyList = isInMyList,
                isReminderSet = isReminderSet,
                mediaVersions = mediaVersions,
                extras = extrasDeferred.await()
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
                runCatchingCancellable {
                    jellyfinApi.getEpisodes(
                        seriesId = jellyfinSeriesId,
                        userId = userId,
                        seasonId = jellyfinSeasonId
                    ).items.filter { it.parentIndexNumber == seasonNumber || jellyfinSeasonId != null }
                }.getOrDefault(emptyList())
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

        if (seerrEps.isNotEmpty()) {
            seerrEps.map { ep ->
                val jf = jfByEpNum[ep.episodeNumber]
                EpisodeSummary(
                    id = jf?.id ?: "tmdb_${tmdbId}_s${seasonNumber}_e${ep.episodeNumber}",
                    name = jf?.name ?: ep.name ?: "Episode ${ep.episodeNumber}",
                    overview = jf?.overview ?: ep.overview.orEmpty(),
                    seasonNumber = seasonNumber,
                    episodeNumber = ep.episodeNumber,
                    runtimeTicks = jf?.runTimeTicks ?: 0L,
                    resumePositionTicks = jf?.userData?.playbackPositionTicks ?: 0L,
                    stillUrl = jf?.let { MediaImageUrls.jellyfinPrimary(jfBase, it.id, 480) }
                        ?: MediaImageUrls.tmdbBackdrop(ep.stillPath),
                    isAvailableInJellyfin = jf != null,
                    isWatched = jf?.userData?.played == true
                )
            }
        } else {
            jfEpisodes.map { jf ->
                EpisodeSummary(
                    id = jf.id,
                    name = jf.name,
                    overview = jf.overview.orEmpty(),
                    seasonNumber = jf.parentIndexNumber ?: seasonNumber,
                    episodeNumber = jf.indexNumber ?: 0,
                    runtimeTicks = jf.runTimeTicks ?: 0L,
                    resumePositionTicks = jf.userData?.playbackPositionTicks ?: 0L,
                    stillUrl = MediaImageUrls.jellyfinPrimary(jfBase, jf.id, 480),
                    isAvailableInJellyfin = true,
                    isWatched = jf.userData?.played == true
                )
            }
        }
    }

    suspend fun loadRecommendations(
        userId: String,
        tmdbId: Int?,
        jellyfinId: String?,
        mediaType: MediaType,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> {
        if (isSeerrEnabled && tmdbId != null) {
            val results = runCatchingCancellable {
                if (mediaType == MediaType.MOVIE) {
                    seerrApi.getMovieRecommendations(tmdbId, language = metaLang).results
                } else {
                    seerrApi.getTvRecommendations(tmdbId, language = metaLang).results
                }
            }.getOrDefault(emptyList())
            if (results.isNotEmpty()) {
                return ContentRatingHelper.filterForProfile(
                    results.take(15).map { mapSeerrResult(it, mediaType) },
                    activeProfile
                )
            }
        }
        if (jellyfinId != null) {
            val similar = runCatchingCancellable {
                jellyfinApi.getSimilarItems(jellyfinId, userId).items.map { mapJellyfinItem(it) }
            }.getOrDefault(emptyList())
            return ContentRatingHelper.filterForProfile(similar, activeProfile)
        }
        return emptyList()
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
        val seerrDeferred = async {
            if (isSeerrEnabled) runCatchingCancellable { seerrApi.search(query, language = metaLang).results }.getOrDefault(emptyList())
            else emptyList()
        }
        val jfDeferred = async {
            runCatchingCancellable { jellyfinApi.searchItems(userId, query).items }.getOrDefault(emptyList())
        }

        val rawSeerr = seerrDeferred.await()
        val people = rawSeerr
            .filter { it.mediaType == "person" }
            .map { person ->
                PersonSearchResult(
                    id = person.id,
                    name = person.name ?: person.title ?: "Person",
                    department = person.knownForDepartment,
                    imageUrl = MediaImageUrls.tmdbProfile(person.profilePath)
                )
            }

        val seerrItems = rawSeerr.mapNotNull { res ->
            val type = when (res.mediaType) {
                "movie" -> MediaType.MOVIE
                "tv" -> MediaType.SERIES
                else -> null
            } ?: return@mapNotNull null
            mapSeerrResult(res, type)
        }
        val seenTmdb = seerrItems.mapNotNull { it.tmdbId }.toSet()
        val jfExtras = jfDeferred.await()
            .map { mapJellyfinItem(it) }
            .filter { it.tmdbId == null || it.tmdbId !in seenTmdb }

        ContentRatingHelper.filterForProfile(seerrItems + jfExtras, activeProfile) to people
    }

    suspend fun loadPersonCredits(
        userId: String,
        personId: Int,
        activeProfile: UserProfile? = null
    ): List<UnifiedMediaItem> = coroutineScope {
        if (!isSeerrEnabled) return@coroutineScope emptyList()
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId, activeProfile)
        }
        val credits = runCatchingCancellable { seerrApi.getPersonCredits(personId, language = metaLang) }.getOrNull()
            ?: return@coroutineScope emptyList()
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
        ContentRatingHelper.filterForProfile(combined, activeProfile)
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
            seasons?.takeIf { it.isNotEmpty() } ?: runCatchingCancellable {
                seerrApi.getTvDetails(tmdbId, language = metaLang).seasons
                    .map { it.seasonNumber }
                    .filter { it > 0 }
                    .takeIf { it.isNotEmpty() }
            }.getOrNull() ?: listOf(1)
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

    suspend fun loadRequestsHub(): Result<List<MediaRequestSummary>> = runCatchingCancellable {
        if (!isSeerrEnabled) return@runCatchingCancellable emptyList()
        val page = seerrApi.getRequests(take = 50, skip = 0, filter = "all", sort = "added")
        coroutineScope {
            page.results.map { req ->
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
                        downloadProgress = downloadProgress
                    )
                }
            }.map { it.await() }
        }
    }.recoverCatching { throw IllegalStateException(formatHttpError(it, "Failed to load requests"), it) }

    suspend fun loadLiveTvChannelsAndPrograms(userId: String): List<LiveTvChannel> = coroutineScope {
        val channelsDeferred = async {
            runCatchingCancellable { jellyfinApi.getLiveTvChannels(userId).items }.getOrDefault(emptyList())
        }
        val programsDeferred = async {
            runCatchingCancellable { jellyfinApi.getLiveTvPrograms(userId).items }.getOrDefault(emptyList())
        }
        val channels = channelsDeferred.await()
        val programsByChannel = programsDeferred.await().groupBy { it.channelId }
        val base = jfBaseUrl
        val nowMs = System.currentTimeMillis()
        val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())

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
                    isSeries = p.isSeries
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
                    isSeries = cp.isSeries
                )
            }
            val current = embeddedCurrent
                ?: progList.firstOrNull { nowMs in it.startMs..it.endMs }
                ?: progList.firstOrNull()
            val next = progList.firstOrNull { it.startMs > (current?.startMs ?: nowMs) }

            LiveTvChannel(
                id = ch.id,
                number = ch.channelNumber,
                name = ch.name,
                logoUrl = MediaImageUrls.jellyfinPrimary(base, ch.id, 300),
                currentProgram = current,
                nextProgram = next
            )
        }
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

    private fun mapJellyfinItem(dto: JellyfinItemDto): UnifiedMediaItem {
        val base = jfBaseUrl
        val type = when {
            dto.type.equals("Movie", ignoreCase = true) -> MediaType.MOVIE
            dto.type.equals("Episode", ignoreCase = true) -> MediaType.EPISODE
            else -> MediaType.SERIES
        }
        val tmdb = dto.providerIds["Tmdb"]?.toIntOrNull() ?: dto.providerIds["tmdb"]?.toIntOrNull()
        val epLabel = if (type == MediaType.EPISODE && dto.parentIndexNumber != null && dto.indexNumber != null) {
            "S${dto.parentIndexNumber}:E${dto.indexNumber}"
        } else null

        val posterItemId = if (type == MediaType.EPISODE && !dto.seriesId.isNullOrBlank()) dto.seriesId else dto.id
        val backdropItemId = if (type == MediaType.EPISODE && !dto.seriesId.isNullOrBlank()) dto.seriesId else dto.id
        val backdropUrl = if (dto.backdropImageTags.isNotEmpty() || type == MediaType.EPISODE) {
            MediaImageUrls.jellyfinBackdrop(base, backdropItemId)
        } else {
            MediaImageUrls.jellyfinPrimary(base, backdropItemId, 1280)
        }
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
            maturityRating = dto.officialRating,
            voteAverage = dto.communityRating,
            genres = dto.genres,
            posterUrl = MediaImageUrls.jellyfinPrimary(base, posterItemId),
            backdropUrl = backdropUrl,
            clearLogoUrl = MediaImageUrls.jellyfinLogo(base, posterItemId),
            hdStatus = AvailabilityTier.AVAILABLE,
            resumePositionTicks = dto.userData?.playbackPositionTicks ?: 0L,
            runtimeTicks = dto.runTimeTicks ?: 0L,
            isWatched = dto.userData?.played == true,
            isInMyList = dto.userData?.isFavorite == true,
            mediaBadges = badges,
            episodeLabel = epLabel,
            seriesName = dto.seriesName,
            releaseDate = dto.premiereDate,
            youtubeTrailerKey = ytKey,
            relatedVideos = related,
            cast = dto.people.take(12).map { p ->
                CastMember(
                    name = p.name,
                    role = p.role.orEmpty(),
                    imageUrl = p.id?.let { MediaImageUrls.jellyfinPrimary(base, it, 200) }
                )
            }
        )
    }

    suspend fun loadReadyToWatchAlerts(userId: String): List<UnifiedMediaItem> {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId)
        }
        val reminderKeys = prefs.getReminders(userId)
        if (reminderKeys.isEmpty()) return emptyList()
        return reminderKeys.mapNotNull { key ->
            val sep = key.lastIndexOf('_')
            if (sep <= 0) return@mapNotNull null
            val type = runCatchingCancellable { MediaType.valueOf(key.substring(0, sep)) }.getOrNull() ?: return@mapNotNull null
            val tmdbId = key.substring(sep + 1).toIntOrNull() ?: return@mapNotNull null
            val matchedDto = when (type) {
                MediaType.MOVIE -> tmdbToJellyfinMovie[tmdbId]
                MediaType.SERIES, MediaType.EPISODE -> tmdbToJellyfinSeries[tmdbId]
            } ?: return@mapNotNull null
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
            UnifiedMediaItem(
                id = "jf_upcoming_${dto.id}",
                tmdbId = tmdbId,
                jellyfinId = dto.seriesId,
                mediaType = MediaType.SERIES,
                title = dto.seriesName ?: dto.name,
                overview = buildString {
                    if (!dto.seriesName.isNullOrBlank() && dto.name.isNotBlank()) {
                        append(dto.name)
                        if (!dto.overview.isNullOrBlank()) append("\n")
                    }
                    append(dto.overview.orEmpty())
                }.trim(),
                posterUrl = MediaImageUrls.jellyfinPrimary(base, seriesItemId),
                backdropUrl = MediaImageUrls.jellyfinBackdrop(base, seriesItemId),
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
        val boxSets = runCatchingCancellable {
            jellyfinApi.getCollections(userId = userId).items
        }.getOrDefault(emptyList())
        return boxSets.map { dto ->
            val tmdbColId = dto.providerIds["TmdbCollection"]?.toIntOrNull()
                ?: dto.providerIds["Tmdb"]?.toIntOrNull()
                ?: dto.providerIds["tmdb"]?.toIntOrNull()
            val backdrop = if (dto.backdropImageTags.isNotEmpty()) {
                MediaImageUrls.jellyfinBackdrop(base, dto.id)
            } else {
                MediaImageUrls.jellyfinPrimary(base, dto.id, 1280)
            }
            CollectionSummary(
                id = dto.id,
                tmdbCollectionId = tmdbColId,
                name = dto.name,
                overview = dto.overview.orEmpty(),
                posterUrl = MediaImageUrls.jellyfinPrimary(base, dto.id),
                backdropUrl = backdrop,
                ownedCount = dto.childCount ?: 0,
                totalCount = null
            )
        }
    }

    suspend fun loadBoxSetDetails(
        userId: String,
        boxSetId: String,
        fallbackTitle: String = "Collection",
        tmdbCollectionIdHint: Int? = null
    ): CollectionSummary? {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId)
        }
        val ownedDtos = runCatchingCancellable {
            jellyfinApi.getCollectionItems(userId = userId, parentId = boxSetId).items
        }.getOrDefault(emptyList())
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
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId, activeProfile)
        }
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
            posterUrl = MediaImageUrls.tmdbPoster(res.posterPath)
                ?: matchedJf?.let { MediaImageUrls.jellyfinPrimary(jfBase, it.id) },
            backdropUrl = MediaImageUrls.tmdbBackdrop(res.backdropPath)
                ?: matchedJf?.let { MediaImageUrls.jellyfinBackdrop(jfBase, it.id) },
            clearLogoUrl = matchedJf?.let { MediaImageUrls.jellyfinLogo(jfBase, it.id) },
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
            ?: jf?.let { MediaImageUrls.jellyfinBackdrop(jfBase, it.id) }
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
                ?: jf?.let { MediaImageUrls.jellyfinPrimary(jfBase, it.id) },
            backdropUrl = backdropUrl,
            clearLogoUrl = jf?.let { MediaImageUrls.jellyfinLogo(jfBase, it.id) },
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
        val backdropUrl = MediaImageUrls.tmdbBackdrop(seerr.backdropPath)
            ?: jf?.let { MediaImageUrls.jellyfinBackdrop(jfBase, it.id) }

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
                ?: jf?.let { MediaImageUrls.jellyfinPrimary(jfBase, it.id) },
            backdropUrl = backdropUrl,
            clearLogoUrl = jf?.let { MediaImageUrls.jellyfinLogo(jfBase, it.id) },
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

    companion object {
        private const val LIBRARY_INDEX_PAGE_SIZE = 1000
        private const val MAX_LIBRARY_INDEX_ITEMS = 20_000

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

        fun formatHttpError(err: Throwable, fallback: String = "Operation failed"): String {
            val raw = err.message?.trim().orEmpty()
            val codeMatch = Regex("""HTTP\s+(\d{3})""", RegexOption.IGNORE_CASE).find(raw)
            val code = codeMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
            return when (code) {
                401, 403 -> "Authentication required (HTTP $code) — re-sign in to your profile or add a Seerr API Key in Server Settings"
                404 -> "Media item not found on server (HTTP 404)"
                409 -> "This item has already been requested (HTTP 409)"
                500, 502, 503, 504 -> "Server error (HTTP $code) — check that Jellyfin and Seerr are healthy"
                else -> if (raw.isNotBlank()) raw else fallback
            }
        }
    }
}

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
