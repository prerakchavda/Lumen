package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CastMember
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.MediaRequestSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.JellyfinMediaStreamDto
import com.seerflix.tv.core.network.JellyfinUpdateUserDataRequest
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrCreateRequestBody
import com.seerflix.tv.core.network.SeerrMediaInfoDto
import com.seerflix.tv.core.network.SeerrMediaResultDto
import com.seerflix.tv.core.network.SeerrRelatedVideoDto
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.YouTubeUrlParser
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

    suspend fun refreshLibraryIndex(userId: String) {
        val items = runCatching {
            jellyfinApi.getLibraryItems(userId = userId, limit = 1000).items
        }.getOrDefault(emptyList())

        allLibraryItems = items
        val movies = mutableMapOf<Int, JellyfinItemDto>()
        val series = mutableMapOf<Int, JellyfinItemDto>()
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
    }

    suspend fun toggleMyList(userId: String, item: UnifiedMediaItem): Result<Boolean> = runCatching {
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
        return prefs.toggleReminder(userId, tmdbId, item.mediaType)
    }

    suspend fun removeFromContinueWatching(userId: String, itemId: String): Result<Unit> = runCatching {
        val updated = runCatching {
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
    }

    suspend fun loadMyList(userId: String): List<UnifiedMediaItem> = coroutineScope {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId)
        }
        val jfFavsDeferred = async {
            runCatching { jellyfinApi.getFavoriteItems(userId = userId).items }.getOrDefault(emptyList())
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
                runCatching { jellyfinApi.addFavorite(userId, matchedJf.id) }
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

    suspend fun loadHomeRows(
        userId: String,
        filterType: MediaType? = null,
        genreFilter: GenreFilterOption? = null
    ): List<CatalogRow> = coroutineScope {
        refreshLibraryIndex(userId)

        // If a specific genre chip is selected on TV Shows / Movies, return genre-focused rows
        if (genreFilter != null && (genreFilter.movieGenreId != null || genreFilter.tvGenreId != null)) {
            return@coroutineScope loadGenreFilteredRows(userId, filterType, genreFilter)
        }

        val resumeDeferred = async {
            runCatching { jellyfinApi.getResumeItems(userId = userId, limit = 15).items }.getOrDefault(emptyList())
        }
        val latestDeferred = async {
            runCatching { jellyfinApi.getLatestItems(userId = userId, limit = 20) }.getOrDefault(emptyList())
        }
        val myListDeferred = async {
            runCatching { loadMyList(userId) }.getOrDefault(emptyList())
        }
        val playedDeferred = async {
            runCatching { jellyfinApi.getPlayedItems(userId = userId, limit = 20).items }.getOrDefault(emptyList())
        }
        val trendingDeferred = async {
            runCatching { seerrApi.getTrending().results }.getOrDefault(emptyList())
        }
        val topMoviesDeferred = async {
            runCatching { seerrApi.getDiscoverMovies().results }.getOrDefault(emptyList())
        }
        val topTvDeferred = async {
            runCatching { seerrApi.getDiscoverTv().results }.getOrDefault(emptyList())
        }
        val upcomingMoviesDeferred = async {
            runCatching { seerrApi.getUpcomingMovies().results }.getOrDefault(emptyList())
        }
        val upcomingTvDeferred = async {
            runCatching { seerrApi.getUpcomingTv().results }.getOrDefault(emptyList())
        }
        val actionMoviesDeferred = async {
            runCatching { seerrApi.getMoviesByGenre(28).results }.getOrDefault(emptyList())
        }
        val sciFiTvDeferred = async {
            runCatching { seerrApi.getTvByGenre(10765).results }.getOrDefault(emptyList())
        }

        val rows = mutableListOf<CatalogRow>()

        val resumeItems = resumeDeferred.await()
            .map { mapJellyfinItem(it) }
            .filter {
                filterType == null ||
                    (filterType == MediaType.MOVIE && it.mediaType == MediaType.MOVIE) ||
                    (filterType == MediaType.SERIES && it.mediaType != MediaType.MOVIE)
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
            .filter { filterType == null || it.mediaType == filterType }
        if (myListItems.isNotEmpty()) {
            rows += CatalogRow(
                id = "my_list",
                title = "My List",
                style = RowStyle.POSTER,
                items = myListItems
            )
        }

        if (filterType == null || filterType == MediaType.MOVIE) {
            val top10Movies = topMoviesDeferred.await()
                .take(10)
                .map { mapSeerrResult(it, MediaType.MOVIE) }
            if (top10Movies.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_movies",
                    title = "Top 10 Movies Today",
                    style = RowStyle.TOP_10,
                    items = top10Movies
                )
            }
        }

        if (filterType == null || filterType == MediaType.SERIES) {
            val top10Tv = topTvDeferred.await()
                .take(10)
                .map { mapSeerrResult(it, MediaType.SERIES) }
            if (top10Tv.isNotEmpty()) {
                rows += CatalogRow(
                    id = "top10_tv",
                    title = "Top 10 TV Shows Today",
                    style = RowStyle.TOP_10,
                    items = top10Tv
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
            val seedType = if (recentSeed.mediaType == MediaType.EPISODE) MediaType.SERIES else recentSeed.mediaType
            val seedName = recentSeed.seriesName ?: recentSeed.title
            val personalized = runCatching {
                loadRecommendations(
                    userId = userId,
                    tmdbId = recentSeed.tmdbId,
                    jellyfinId = recentSeed.jellyfinId,
                    mediaType = seedType
                )
            }.getOrDefault(emptyList())
                .filter { filterType == null || it.mediaType == filterType }
            if (personalized.isNotEmpty()) {
                rows += CatalogRow(
                    id = "because_you_watched",
                    title = "Because you watched $seedName",
                    style = RowStyle.POSTER,
                    items = personalized
                )
            }
        }

        val latest = latestDeferred.await()
            .map { mapJellyfinItem(it) }
            .filter { filterType == null || it.mediaType == filterType }
        if (latest.isNotEmpty()) {
            rows += CatalogRow(
                id = "recently_added",
                title = "Recently Added to Your Server",
                style = RowStyle.POSTER,
                items = latest
            )
        }

        val upcoming = buildList {
            if (filterType == null || filterType == MediaType.MOVIE) {
                addAll(upcomingMoviesDeferred.await().map { mapSeerrResult(it, MediaType.MOVIE) })
            }
            if (filterType == null || filterType == MediaType.SERIES) {
                addAll(upcomingTvDeferred.await().map { mapSeerrResult(it, MediaType.SERIES) })
            }
        }.distinctBy { "${it.mediaType}_${it.tmdbId}" }
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
            .filter { filterType == null || it.mediaType == filterType }
        if (watchAgain.isNotEmpty()) {
            rows += CatalogRow(
                id = "watch_it_again",
                title = "Watch It Again",
                style = RowStyle.POSTER,
                items = watchAgain
            )
        }

        if (filterType == null || filterType == MediaType.MOVIE) {
            val action = actionMoviesDeferred.await().map { mapSeerrResult(it, MediaType.MOVIE) }
            if (action.isNotEmpty()) {
                rows += CatalogRow(
                    id = "action_movies",
                    title = "Action & Thrillers",
                    style = RowStyle.POSTER,
                    items = action
                )
            }
        }

        if (filterType == null || filterType == MediaType.SERIES) {
            val sciFi = sciFiTvDeferred.await().map { mapSeerrResult(it, MediaType.SERIES) }
            if (sciFi.isNotEmpty()) {
                rows += CatalogRow(
                    id = "scifi_tv",
                    title = "Sci-Fi & Fantasy Series",
                    style = RowStyle.POSTER,
                    items = sciFi
                )
            }
        }

        rows
    }

    private suspend fun loadGenreFilteredRows(
        userId: String,
        filterType: MediaType?,
        genre: GenreFilterOption
    ): List<CatalogRow> = coroutineScope {
        val movieGenreDeferred = async {
            if (filterType != MediaType.SERIES && genre.movieGenreId != null) {
                runCatching { seerrApi.getMoviesByGenre(genre.movieGenreId).results }.getOrDefault(emptyList())
            } else emptyList()
        }
        val tvGenreDeferred = async {
            if (filterType != MediaType.MOVIE && genre.tvGenreId != null) {
                runCatching { seerrApi.getTvByGenre(genre.tvGenreId).results }.getOrDefault(emptyList())
            } else emptyList()
        }

        val rows = mutableListOf<CatalogRow>()
        val libraryMatches = allLibraryItems
            .map { mapJellyfinItem(it) }
            .filter { item ->
                (filterType == null || item.mediaType == filterType) &&
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

        val movies = movieGenreDeferred.await().map { mapSeerrResult(it, MediaType.MOVIE) }
        if (movies.isNotEmpty()) {
            rows += CatalogRow(
                id = "genre_movies_${genre.id}",
                title = "Popular ${genre.label} Movies",
                style = RowStyle.POSTER,
                items = movies
            )
        }

        val tv = tvGenreDeferred.await().map { mapSeerrResult(it, MediaType.SERIES) }
        if (tv.isNotEmpty()) {
            rows += CatalogRow(
                id = "genre_tv_${genre.id}",
                title = "Popular ${genre.label} TV Shows",
                style = RowStyle.POSTER,
                items = tv
            )
        }
        rows
    }

    suspend fun loadNewAndPopular(userId: String): List<CatalogRow> = coroutineScope {
        refreshLibraryIndex(userId)
        val trendingDeferred = async {
            runCatching { seerrApi.getTrending().results }.getOrDefault(emptyList())
        }
        val upcomingMoviesDeferred = async {
            runCatching { seerrApi.getUpcomingMovies().results }.getOrDefault(emptyList())
        }
        val upcomingTvDeferred = async {
            runCatching { seerrApi.getUpcomingTv().results }.getOrDefault(emptyList())
        }
        val latestDeferred = async {
            runCatching { jellyfinApi.getLatestItems(userId = userId, limit = 24) }.getOrDefault(emptyList())
        }

        buildList {
            val trending = trendingDeferred.await().mapNotNull { res ->
                val type = when (res.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> null
                } ?: return@mapNotNull null
                mapSeerrResult(res, type)
            }
            if (trending.isNotEmpty()) {
                add(CatalogRow("np_trending", "Worth the Wait & Trending", RowStyle.TOP_10, trending.take(10)))
            }
            val upcomingMovies = upcomingMoviesDeferred.await().map { mapSeerrResult(it, MediaType.MOVIE) }
            if (upcomingMovies.isNotEmpty()) {
                add(CatalogRow("np_coming_movies", "Coming Soon • Movies", RowStyle.POSTER, upcomingMovies))
            }
            val upcomingTv = upcomingTvDeferred.await().map { mapSeerrResult(it, MediaType.SERIES) }
            if (upcomingTv.isNotEmpty()) {
                add(CatalogRow("np_coming_tv", "Coming Soon • TV Shows", RowStyle.POSTER, upcomingTv))
            }
            val latest = latestDeferred.await().map { mapJellyfinItem(it) }
            if (latest.isNotEmpty()) {
                add(CatalogRow("np_latest", "Just Added to Your Server", RowStyle.POSTER, latest))
            }
        }
    }

    suspend fun loadTrendingTitles(userId: String): List<UnifiedMediaItem> = coroutineScope {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId)
        }
        runCatching { seerrApi.getTrending().results }
            .getOrDefault(emptyList())
            .mapNotNull { res ->
                val type = when (res.mediaType) {
                    "movie" -> MediaType.MOVIE
                    "tv" -> MediaType.SERIES
                    else -> null
                } ?: return@mapNotNull null
                mapSeerrResult(res, type)
            }
    }

    suspend fun loadByGenre(
        userId: String,
        genre: GenreFilterOption
    ): List<UnifiedMediaItem> = coroutineScope {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId)
        }
        val movieDeferred = async {
            genre.movieGenreId?.let { id ->
                runCatching { seerrApi.getMoviesByGenre(id).results }.getOrDefault(emptyList())
            }.orEmpty()
        }
        val tvDeferred = async {
            genre.tvGenreId?.let { id ->
                runCatching { seerrApi.getTvByGenre(id).results }.getOrDefault(emptyList())
            }.orEmpty()
        }
        val movies = movieDeferred.await().map { mapSeerrResult(it, MediaType.MOVIE) }
        val tv = tvDeferred.await().map { mapSeerrResult(it, MediaType.SERIES) }
        (movies + tv).distinctBy { "${it.mediaType}_${it.tmdbId}" }
    }

    suspend fun loadFullDetails(
        userId: String,
        tmdbId: Int?,
        jellyfinId: String?,
        mediaType: MediaType
    ): Result<UnifiedMediaItem> = runCatching {
        coroutineScope {
            val resolvedType = if (mediaType == MediaType.EPISODE) MediaType.SERIES else mediaType
            var jfItem: JellyfinItemDto? = jellyfinId?.let { id ->
                runCatching { jellyfinApi.getItemDetails(userId, id) }.getOrNull()
            }
            val resolvedTmdbId = tmdbId
                ?: jfItem?.providerIds?.get("Tmdb")?.toIntOrNull()
                ?: jfItem?.providerIds?.get("tmdb")?.toIntOrNull()

            if (jfItem == null && resolvedTmdbId != null) {
                jfItem = if (resolvedType == MediaType.MOVIE) {
                    tmdbToJellyfinMovie[resolvedTmdbId]
                } else {
                    tmdbToJellyfinSeries[resolvedTmdbId]
                }
            }

            val baseItem = if (resolvedTmdbId != null) {
                if (resolvedType == MediaType.MOVIE) {
                    val seerrMovie = seerrApi.getMovieDetails(resolvedTmdbId)
                    val localTrailerDeferred = async {
                        jfItem?.id?.let { id ->
                            runCatching { jellyfinApi.getLocalTrailers(id, userId).firstOrNull()?.id }.getOrNull()
                        }
                    }
                    mergeMovieDetails(seerrMovie, jfItem, localTrailerDeferred.await())
                } else {
                    val seerrTv = seerrApi.getTvDetails(resolvedTmdbId)
                    val jfSeasonsDeferred = async {
                        jfItem?.id?.let { id ->
                            runCatching { jellyfinApi.getSeasons(id, userId).items }.getOrDefault(emptyList())
                        }.orEmpty()
                    }
                    val localTrailerDeferred = async {
                        jfItem?.id?.let { id ->
                            runCatching { jellyfinApi.getLocalTrailers(id, userId).firstOrNull()?.id }.getOrNull()
                        }
                    }
                    mergeTvDetails(seerrTv, jfItem, jfSeasonsDeferred.await(), localTrailerDeferred.await())
                }
            } else if (jfItem != null) {
                mapJellyfinItem(jfItem)
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

            baseItem.copy(
                isInMyList = isInMyList,
                isReminderSet = isReminderSet
            )
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
                runCatching {
                    jellyfinApi.getEpisodes(
                        seriesId = jellyfinSeriesId,
                        userId = userId,
                        seasonId = jellyfinSeasonId
                    ).items.filter { it.parentIndexNumber == seasonNumber || jellyfinSeasonId != null }
                }.getOrDefault(emptyList())
            } else emptyList()
        }
        val seerrSeasonDeferred = async {
            if (tmdbId != null) {
                runCatching { seerrApi.getTvSeasonDetails(tmdbId, seasonNumber) }.getOrNull()
            } else null
        }

        val jfEpisodes = jfEpisodesDeferred.await()
        val jfByEpNum = jfEpisodes.associateBy { it.indexNumber ?: -1 }
        val seerrEps = seerrSeasonDeferred.await()?.episodes.orEmpty()
        val jfBase = prefs.cachedJellyfinUrl

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
        mediaType: MediaType
    ): List<UnifiedMediaItem> {
        if (tmdbId != null) {
            val results = runCatching {
                if (mediaType == MediaType.MOVIE) {
                    seerrApi.getMovieRecommendations(tmdbId).results
                } else {
                    seerrApi.getTvRecommendations(tmdbId).results
                }
            }.getOrDefault(emptyList())
            if (results.isNotEmpty()) {
                return results.take(15).map { mapSeerrResult(it, mediaType) }
            }
        }
        if (jellyfinId != null) {
            return runCatching {
                jellyfinApi.getSimilarItems(jellyfinId, userId).items.map { mapJellyfinItem(it) }
            }.getOrDefault(emptyList())
        }
        return emptyList()
    }

    suspend fun searchUnified(userId: String, query: String): List<UnifiedMediaItem> =
        searchUnifiedWithPeople(userId, query).first

    suspend fun searchUnifiedWithPeople(
        userId: String,
        query: String
    ): Pair<List<UnifiedMediaItem>, List<PersonSearchResult>> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList<UnifiedMediaItem>() to emptyList()
        val seerrDeferred = async {
            runCatching { seerrApi.search(query).results }.getOrDefault(emptyList())
        }
        val jfDeferred = async {
            runCatching { jellyfinApi.searchItems(userId, query).items }.getOrDefault(emptyList())
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

        (seerrItems + jfExtras) to people
    }

    suspend fun loadPersonCredits(userId: String, personId: Int): List<UnifiedMediaItem> = coroutineScope {
        if (tmdbToJellyfinMovie.isEmpty() && tmdbToJellyfinSeries.isEmpty()) {
            refreshLibraryIndex(userId)
        }
        val credits = runCatching { seerrApi.getPersonCredits(personId) }.getOrNull()
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
        combined
    }

    suspend fun has4kServerConfigured(mediaType: MediaType): Boolean = runCatching {
        val servers = if (mediaType == MediaType.MOVIE) {
            seerrApi.getRadarrServers()
        } else {
            seerrApi.getSonarrServers()
        }
        servers.any { it.is4k }
    }.getOrDefault(true)

    suspend fun submitRequest(
        tmdbId: Int,
        mediaType: MediaType,
        is4k: Boolean,
        seasons: List<Int>? = null
    ): Result<Unit> = runCatching {
        seerrApi.createRequest(
            SeerrCreateRequestBody(
                mediaType = if (mediaType == MediaType.MOVIE) "movie" else "tv",
                mediaId = tmdbId,
                is4k = is4k,
                seasons = if (mediaType == MediaType.SERIES) seasons else null
            )
        )
    }

    suspend fun cancelRequest(requestId: Int): Result<Unit> = runCatching {
        seerrApi.deleteRequest(requestId)
    }

    suspend fun loadRequestsHub(): Result<List<MediaRequestSummary>> = runCatching {
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
                            runCatching { seerrApi.getMovieDetails(tmdbId) }.getOrNull()?.let { m ->
                                title = m.title
                                posterPath = m.posterPath
                                backdropPath = m.backdropPath
                                if (downloadProgress == null) {
                                    val list = if (req.is4k) m.mediaInfo?.downloadStatus4k else m.mediaInfo?.downloadStatus
                                    downloadProgress = DownloadProgressParser.parse(list.orEmpty(), req.is4k)
                                }
                            }
                        } else {
                            runCatching { seerrApi.getTvDetails(tmdbId) }.getOrNull()?.let { tv ->
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
    }

    private fun mapJellyfinItem(dto: JellyfinItemDto): UnifiedMediaItem {
        val base = prefs.cachedJellyfinUrl
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

    private fun mapSeerrResult(res: SeerrMediaResultDto, type: MediaType): UnifiedMediaItem {
        val jfBase = prefs.cachedJellyfinUrl
        val matchedJf = if (type == MediaType.MOVIE) tmdbToJellyfinMovie[res.id] else tmdbToJellyfinSeries[res.id]
        val mediaInfo = res.mediaInfo
        val hdStatus = if (matchedJf != null) {
            AvailabilityTier.AVAILABLE
        } else {
            AvailabilityTier.fromSeerrCode(mediaInfo?.status)
        }
        val uhdStatus = AvailabilityTier.fromSeerrCode(mediaInfo?.status4k)

        return UnifiedMediaItem(
            id = "tmdb_${type.name}_${res.id}",
            tmdbId = res.id,
            jellyfinId = matchedJf?.id ?: mediaInfo?.jellyfinMediaId ?: mediaInfo?.jellyfinMediaId4k,
            mediaType = type,
            title = res.title ?: res.name ?: res.originalTitle ?: res.originalName ?: "Untitled",
            overview = res.overview.orEmpty(),
            releaseYear = (res.releaseDate ?: res.firstAirDate)?.take(4),
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
            mediaBadges = matchedJf?.let { extractMediaBadges(it.mediaStreams) }.orEmpty()
        )
    }

    private fun mergeMovieDetails(
        seerr: com.seerflix.tv.core.network.SeerrMovieDetailsDto,
        jf: JellyfinItemDto?,
        localTrailerId: String?
    ): UnifiedMediaItem {
        val jfBase = prefs.cachedJellyfinUrl
        val info = seerr.mediaInfo
        val hdStatus = if (jf != null) AvailabilityTier.AVAILABLE else AvailabilityTier.fromSeerrCode(info?.status)
        val uhdStatus = AvailabilityTier.fromSeerrCode(info?.status4k)
        val backdropUrl = MediaImageUrls.tmdbBackdrop(seerr.backdropPath)
            ?: jf?.let { MediaImageUrls.jellyfinBackdrop(jfBase, it.id) }

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
            runtimeMinutes = seerr.runtime ?: jf?.runTimeTicks?.let { (it / 600_000_000L).toInt() },
            maturityRating = jf?.officialRating,
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
            }.orEmpty()
        )
    }

    private fun mergeTvDetails(
        seerr: com.seerflix.tv.core.network.SeerrTvDetailsDto,
        jf: JellyfinItemDto?,
        jfSeasons: List<JellyfinItemDto>,
        localTrailerId: String?
    ): UnifiedMediaItem {
        val jfBase = prefs.cachedJellyfinUrl
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
            maturityRating = jf?.officialRating,
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
    }
}
