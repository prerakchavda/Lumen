package com.seerflix.tv.core.data

import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CastMember
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.MediaRequestSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.network.JellyfinApi
import com.seerflix.tv.core.network.JellyfinItemDto
import com.seerflix.tv.core.network.SeerrApi
import com.seerflix.tv.core.network.SeerrCreateRequestBody
import com.seerflix.tv.core.network.SeerrMediaInfoDto
import com.seerflix.tv.core.network.SeerrMediaResultDto
import com.seerflix.tv.core.network.SeerrRelatedVideoDto
import com.seerflix.tv.core.network.SessionPreferences
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

    suspend fun refreshLibraryIndex(userId: String) {
        val items = runCatching {
            jellyfinApi.getLibraryItems(userId = userId, limit = 1000).items
        }.getOrDefault(emptyList())

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

    suspend fun loadHomeRows(userId: String, filterType: MediaType? = null): List<CatalogRow> = coroutineScope {
        refreshLibraryIndex(userId)

        val resumeDeferred = async {
            runCatching { jellyfinApi.getResumeItems(userId = userId, limit = 15).items }.getOrDefault(emptyList())
        }
        val latestDeferred = async {
            runCatching { jellyfinApi.getLatestItems(userId = userId, limit = 20) }.getOrDefault(emptyList())
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
            .filter { filterType == null || (filterType == MediaType.MOVIE && it.mediaType == MediaType.MOVIE) || (filterType == MediaType.SERIES && it.mediaType != MediaType.MOVIE) }
        if (resumeItems.isNotEmpty()) {
            rows += CatalogRow(
                id = "continue_watching",
                title = "Continue Watching",
                style = RowStyle.CONTINUE_WATCHING,
                items = resumeItems
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

            if (resolvedTmdbId != null) {
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

    suspend fun searchUnified(userId: String, query: String): List<UnifiedMediaItem> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()
        val seerrDeferred = async {
            runCatching { seerrApi.search(query).results }.getOrDefault(emptyList())
        }
        val jfDeferred = async {
            runCatching { jellyfinApi.searchItems(userId, query).items }.getOrDefault(emptyList())
        }

        val seerrItems = seerrDeferred.await().mapNotNull { res ->
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

        seerrItems + jfExtras
    }

    suspend fun has4kServerConfigured(mediaType: MediaType): Boolean = runCatching {
        val servers = if (mediaType == MediaType.MOVIE) {
            seerrApi.getRadarrServers()
        } else {
            seerrApi.getSonarrServers()
        }
        servers.any { it.is4k }
    }.getOrDefault(true) // Default true if user lacks admin settings permission so 4K button remains accessible

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
            backdropUrl = if (dto.backdropImageTags.isNotEmpty() || type == MediaType.EPISODE) {
                MediaImageUrls.jellyfinBackdrop(base, backdropItemId)
            } else {
                MediaImageUrls.jellyfinPrimary(base, backdropItemId, 1280)
            },
            clearLogoUrl = MediaImageUrls.jellyfinLogo(base, posterItemId),
            hdStatus = AvailabilityTier.AVAILABLE,
            resumePositionTicks = dto.userData?.playbackPositionTicks ?: 0L,
            runtimeTicks = dto.runTimeTicks ?: 0L,
            isWatched = dto.userData?.played == true,
            episodeLabel = epLabel,
            seriesName = dto.seriesName,
            youtubeTrailerKey = extractYouTubeKey(dto.remoteTrailers.firstOrNull()?.url),
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
            isWatched = matchedJf?.userData?.played == true
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
            backdropUrl = MediaImageUrls.tmdbBackdrop(seerr.backdropPath)
                ?: jf?.let { MediaImageUrls.jellyfinBackdrop(jfBase, it.id) },
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
            youtubeTrailerKey = pickBestYouTubeTrailer(seerr.relatedVideos)
                ?: extractYouTubeKey(jf?.remoteTrailers?.firstOrNull()?.url),
            localTrailerItemId = localTrailerId,
            cast = seerr.credits?.cast?.take(12)?.map {
                CastMember(
                    name = it.name,
                    role = it.character.orEmpty(),
                    imageUrl = MediaImageUrls.tmdbProfile(it.profilePath)
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
            backdropUrl = MediaImageUrls.tmdbBackdrop(seerr.backdropPath)
                ?: jf?.let { MediaImageUrls.jellyfinBackdrop(jfBase, it.id) },
            clearLogoUrl = jf?.let { MediaImageUrls.jellyfinLogo(jfBase, it.id) },
            hdStatus = hdStatus,
            uhdStatus = uhdStatus,
            hdDownload = DownloadProgressParser.parse(info?.downloadStatus.orEmpty(), is4k = false),
            uhdDownload = DownloadProgressParser.parse(info?.downloadStatus4k.orEmpty(), is4k = true),
            hdRequestId = info?.requests?.firstOrNull { !it.is4k }?.id,
            uhdRequestId = info?.requests?.firstOrNull { it.is4k }?.id,
            youtubeTrailerKey = pickBestYouTubeTrailer(seerr.relatedVideos)
                ?: extractYouTubeKey(jf?.remoteTrailers?.firstOrNull()?.url),
            localTrailerItemId = localTrailerId,
            seasons = seasons,
            cast = seerr.credits?.cast?.take(12)?.map {
                CastMember(
                    name = it.name,
                    role = it.character.orEmpty(),
                    imageUrl = MediaImageUrls.tmdbProfile(it.profilePath)
                )
            }.orEmpty()
        )
    }

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
        com.seerflix.tv.core.player.YouTubeUrlParser.extractVideoKey(url)
}
