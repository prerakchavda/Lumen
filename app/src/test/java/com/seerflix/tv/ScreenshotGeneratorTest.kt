package com.seerflix.tv

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import coil3.annotation.ExperimentalCoilApi
import coil3.asImage
import coil3.compose.AsyncImagePreviewHandler
import coil3.compose.LocalAsyncImagePreviewHandler
import com.android.resources.Density
import com.android.resources.NightMode
import com.android.resources.ScreenOrientation
import com.android.resources.UiMode
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CastMember
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.MediaRequestSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.PlaybackSessionData
import com.seerflix.tv.core.model.QualityPreset
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.SeasonSummary
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.feature.details.DetailsContent
import com.seerflix.tv.feature.details.DetailsUiState
import com.seerflix.tv.feature.home.HomeContent
import com.seerflix.tv.feature.home.HomeTab
import com.seerflix.tv.feature.home.HomeUiState
import com.seerflix.tv.feature.player.EpisodesDrawerOverlay
import com.seerflix.tv.feature.player.NextEpisodeOverlayCard
import com.seerflix.tv.feature.player.StreamInfoOverlay
import com.seerflix.tv.feature.profiles.PinEntryCard
import com.seerflix.tv.feature.profiles.ProfilePickerContent
import com.seerflix.tv.feature.profiles.ProfilePickerUiState
import com.seerflix.tv.feature.requests.RequestFilter
import com.seerflix.tv.feature.requests.RequestsContent
import com.seerflix.tv.feature.requests.RequestsUiState
import com.seerflix.tv.feature.search.SearchContent
import com.seerflix.tv.feature.search.SearchUiState
import com.seerflix.tv.feature.setup.SetupContent
import com.seerflix.tv.feature.setup.SetupUiState
import com.seerflix.tv.ui.theme.NetflixColors
import com.seerflix.tv.ui.theme.SeerflixTheme
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalCoilApi::class)
class ScreenshotGeneratorTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig(
            screenWidth = 1920,
            screenHeight = 1080,
            xdpi = 320,
            ydpi = 320,
            orientation = ScreenOrientation.LANDSCAPE,
            uiMode = UiMode.TELEVISION,
            nightMode = NightMode.NIGHT,
            density = Density.XHIGH
        ),
        theme = "android:Theme.DeviceDefault.NoActionBar",
        appCompatEnabled = false
    )

    private val artworkPreviewHandler = AsyncImagePreviewHandler { request ->
        renderSyntheticArtwork(request.data.toString()).asImage()
    }

    @Test
    fun home() {
        val dune = sampleDune()
        val state = HomeUiState(
            activeTab = HomeTab.HOME,
            profile = UserProfile("1", "Prerak", "1", "token", avatarColorHex = 0xFFE50914),
            featuredItem = dune,
            activeDownloadCount = 2,
            isLoading = false,
            rows = listOf(
                CatalogRow(
                    id = "continue_watching",
                    title = "Continue Watching for Prerak",
                    style = RowStyle.CONTINUE_WATCHING,
                    items = listOf(
                        UnifiedMediaItem(
                            id = "cw_sev",
                            tmdbId = 95396,
                            jellyfinId = "jf_sev_ep",
                            mediaType = MediaType.SERIES,
                            title = "Severance",
                            episodeLabel = "S2 • E4",
                            backdropUrl = "art://backdrop/severance/SEVERANCE/0xFF0E3A4C/0xFF1CC8B8",
                            runtimeTicks = 30_000_000_000L,
                            resumePositionTicks = 18_600_000_000L,
                            hdStatus = AvailabilityTier.AVAILABLE,
                            uhdStatus = AvailabilityTier.AVAILABLE
                        ),
                        UnifiedMediaItem(
                            id = "cw_tlou",
                            tmdbId = 100088,
                            jellyfinId = "jf_tlou_ep",
                            mediaType = MediaType.SERIES,
                            title = "The Last of Us",
                            episodeLabel = "S2 • E1",
                            backdropUrl = "art://backdrop/tlou/THE LAST OF US/0xFF283518/0xFFD99B38",
                            runtimeTicks = 36_000_000_000L,
                            resumePositionTicks = 12_600_000_000L,
                            hdStatus = AvailabilityTier.AVAILABLE
                        ),
                        UnifiedMediaItem(
                            id = "cw_arc",
                            tmdbId = 94605,
                            jellyfinId = "jf_arc_ep",
                            mediaType = MediaType.SERIES,
                            title = "Arcane",
                            episodeLabel = "S2 • E7",
                            backdropUrl = "art://backdrop/arcane/ARCANE/0xFF20133A/0xFFE91E63",
                            runtimeTicks = 24_000_000_000L,
                            resumePositionTicks = 19_200_000_000L,
                            hdStatus = AvailabilityTier.AVAILABLE,
                            uhdStatus = AvailabilityTier.AVAILABLE
                        ),
                        UnifiedMediaItem(
                            id = "cw_andor",
                            tmdbId = 83867,
                            jellyfinId = "jf_andor_ep",
                            mediaType = MediaType.SERIES,
                            title = "Andor",
                            episodeLabel = "S1 • E9",
                            backdropUrl = "art://backdrop/andor/ANDOR/0xFF1F242D/0xFFFF7043",
                            runtimeTicks = 28_000_000_000L,
                            resumePositionTicks = 8_000_000_000L,
                            hdStatus = AvailabilityTier.AVAILABLE
                        )
                    )
                ),
                CatalogRow(
                    id = "top_10_movies",
                    title = "Top 10 Movies on Seerflix Today",
                    style = RowStyle.TOP_10,
                    items = listOf(
                        dune,
                        samplePosterItem(
                            "m2", "Interstellar", "art://poster/interstellar/INTERSTELLAR/0xFF0E1B38/0xFFF4B942",
                            uhd = AvailabilityTier.AVAILABLE, hd = AvailabilityTier.AVAILABLE, jfId = "jf2"
                        ),
                        samplePosterItem(
                            "m3", "The Brutalist", "art://poster/brutalist/THE BRUTALIST/0xFF292421/0xFFE07A5F",
                            hd = AvailabilityTier.PROCESSING,
                            hdDl = DownloadProgressInfo(false, "downloading", "2160p", 100L, 11L, 89f, "~2m left", null)
                        ),
                        samplePosterItem(
                            "m4", "Blade Runner 2049", "art://poster/br2049/BLADE RUNNER/0xFF3E1810/0xFFFF9800",
                            uhd = AvailabilityTier.AVAILABLE, hd = AvailabilityTier.AVAILABLE, jfId = "jf4"
                        ),
                        samplePosterItem(
                            "m5", "Nosferatu", "art://poster/nosferatu/NOSFERATU/0xFF121824/0xFF8FA8C8",
                            hd = AvailabilityTier.PENDING
                        )
                    )
                ),
                CatalogRow(
                    id = "trending",
                    title = "Trending Now",
                    style = RowStyle.POSTER,
                    items = listOf(
                        samplePosterItem("t1", "Severance", "art://poster/sev/SEVERANCE/0xFF0E3A4C/0xFF1CC8B8", hd = AvailabilityTier.AVAILABLE, uhd = AvailabilityTier.AVAILABLE, jfId = "jft1"),
                        samplePosterItem("t2", "Cyberpunk: Edgerunners", "art://poster/cp/EDGERUNNERS/0xFF1E133D/0xFF00E5FF", hd = AvailabilityTier.AVAILABLE, jfId = "jft2"),
                        samplePosterItem("t3", "Daredevil: Born Again", "art://poster/dd/DAREDEVIL/0xFF3D0A0E/0xFFE50914", hd = AvailabilityTier.PENDING),
                        samplePosterItem("t4", "Shōgun", "art://poster/shogun/SHOGUN/0xFF2B1B17/0xFFD4AF37", hd = AvailabilityTier.AVAILABLE, uhd = AvailabilityTier.AVAILABLE, jfId = "jft4"),
                        samplePosterItem("t5", "Fallout", "art://poster/fallout/FALLOUT/0xFF1B2E3C/0xFFFFD54F", hd = AvailabilityTier.AVAILABLE, jfId = "jft5"),
                        samplePosterItem("t6", "Mickey 17", "art://poster/mickey/MICKEY 17/0xFF1A2230/0xFF64B5F6", hd = AvailabilityTier.UNKNOWN)
                    )
                )
            )
        )

        snapshotScreen("home") {
            HomeContent(
                state = state,
                onOpenDetails = {},
                onPlayDirect = {},
                onOpenSearch = {},
                onOpenRequests = {},
                onSwitchProfile = {},
                onOpenSettings = {},
                onSelectTab = {},
                onRefresh = {},
                onTogglePreviewMute = {},
                onItemFocused = {}
            )
        }
    }

    @Test
    fun details() {
        val severance = UnifiedMediaItem(
            id = "tmdb_tv_95396",
            tmdbId = 95396,
            jellyfinId = "jf_sev_series",
            mediaType = MediaType.SERIES,
            title = "Severance",
            overview = "Mark leads a team of office workers whose memories have been surgically divided between their work and personal lives. When a mysterious colleague appears outside of work, it begins a journey to discover the truth.",
            releaseYear = "2022",
            runtimeMinutes = 50,
            maturityRating = "TV-MA",
            voteAverage = 8.8,
            genres = listOf("Sci-Fi", "Mystery", "Drama"),
            backdropUrl = "art://backdrop/severance/SEVERANCE/0xFF0B2B38/0xFF26D0CE",
            posterUrl = "art://poster/sev/SEVERANCE/0xFF0E3A4C/0xFF1CC8B8",
            hdStatus = AvailabilityTier.AVAILABLE,
            uhdStatus = AvailabilityTier.PROCESSING,
            uhdDownload = DownloadProgressInfo(
                is4k = true,
                status = "downloading",
                title = "Severance.S02E05.2160p.WEB-DL.DV",
                sizeBytes = 6_400_000_000L,
                sizeLeftBytes = 2_304_000_000L,
                percentage = 64f,
                etaText = "~6m left",
                estimatedCompletionIso = null
            ),
            uhdRequestId = 42,
            isInMyList = true,
            mediaBadges = listOf("4K", "Dolby Vision", "Dolby Atmos", "CC"),
            youtubeTrailerKey = "xEQP4VVuyrY",
            relatedVideos = listOf(
                RelatedVideoItem(
                    title = "Official Trailer",
                    type = "Trailer",
                    youtubeKey = "xEQP4VVuyrY",
                    thumbnailUrl = "art://still/tr1/TRAILER/0xFF0E3A4C/0xFF1CC8B8"
                ),
                RelatedVideoItem(
                    title = "Inside Lumon Featurette",
                    type = "Featurette",
                    youtubeKey = "feat1",
                    thumbnailUrl = "art://still/tr2/FEATURETTE/0xFF20133A/0xFFE91E63"
                )
            ),
            seasons = listOf(
                SeasonSummary(1, "Season 1", 9, hdStatus = AvailabilityTier.AVAILABLE, uhdStatus = AvailabilityTier.AVAILABLE, jellyfinSeasonId = "s1"),
                SeasonSummary(2, "Season 2", 10, hdStatus = AvailabilityTier.PARTIALLY_AVAILABLE, uhdStatus = AvailabilityTier.PROCESSING, jellyfinSeasonId = "s2")
            ),
            cast = listOf(
                CastMember("Adam Scott", "Mark Scout", "art://cast/adam/AS/0xFF253746/0xFF78909C", personId = 36801),
                CastMember("Britt Lower", "Helly Riggs", "art://cast/britt/BL/0xFF3A1C2A/0xFFF06292", personId = 1529460),
                CastMember("Zach Cherry", "Dylan George", "art://cast/zach/ZC/0xFF1E3224/0xFF66BB6A", personId = 1718281),
                CastMember("Tramell Tillman", "Seth Milchick", "art://cast/tramell/TT/0xFF2C2538/0xFF9575CD", personId = 1835993)
            )
        )

        val state = DetailsUiState(
            item = severance,
            selectedSeasonNumber = 2,
            isLoading = false,
            has4kServer = true,
            episodes = listOf(
                EpisodeSummary(
                    id = "ep1",
                    name = "Hello, Ms. Cobel",
                    overview = "Mark returns to Lumon under startlingly different circumstances as secrets from the outside world surface.",
                    seasonNumber = 2,
                    episodeNumber = 1,
                    runtimeTicks = 30_000_000_000L,
                    resumePositionTicks = 30_000_000_000L,
                    stillUrl = "art://still/ep1/S2E1/0xFF103742/0xFF4DD0E1",
                    isAvailableInJellyfin = true,
                    isWatched = true
                ),
                EpisodeSummary(
                    id = "ep2",
                    name = "Woe's Hollow",
                    overview = "The MDR team embarks on an outdoor team-building retreat in a snow-covered wilderness.",
                    seasonNumber = 2,
                    episodeNumber = 4,
                    runtimeTicks = 31_200_000_000L,
                    resumePositionTicks = 19_000_000_000L,
                    stillUrl = "art://still/ep4/S2E4/0xFF1D2939/0xFF90CAF9",
                    isAvailableInJellyfin = true,
                    isWatched = false
                )
            )
        )

        snapshotScreen("details") {
            DetailsContent(
                state = state,
                onBack = {},
                onPlayItem = {},
                onOpenDetails = {},
                onOpenRequestModal = {},
                onCloseRequestModal = {},
                onCancelRequest = {},
                onSelectSeason = {},
                onSubmitRequest = { _, _, _ -> },
                onWatchTrailer = {}
            )
        }
    }

    @Test
    fun requests() {
        val state = RequestsUiState(
            filter = RequestFilter.ALL,
            isLoading = false,
            allRequests = listOf(
                MediaRequestSummary(
                    requestId = 101,
                    tmdbId = 693134,
                    mediaType = MediaType.MOVIE,
                    title = "Dune: Part Two",
                    posterUrl = "art://poster/dune2/DUNE 2/0xFF5D2906/0xFFFF9800",
                    backdropUrl = null,
                    is4k = true,
                    requestStatus = 2,
                    mediaStatus = AvailabilityTier.PROCESSING,
                    requestedBy = "Prerak",
                    createdAt = "2026-11-13",
                    downloadProgress = DownloadProgressInfo(
                        is4k = true,
                        status = "downloading",
                        title = "Dune.Part.Two.2024.2160p.UHD",
                        sizeBytes = 24_000_000_000L,
                        sizeLeftBytes = 8_640_000_000L,
                        percentage = 64f,
                        etaText = "~6m left",
                        estimatedCompletionIso = null
                    )
                ),
                MediaRequestSummary(
                    requestId = 102,
                    tmdbId = 549509,
                    mediaType = MediaType.MOVIE,
                    title = "The Brutalist",
                    posterUrl = "art://poster/brutalist/BRUTALIST/0xFF292421/0xFFE07A5F",
                    backdropUrl = null,
                    is4k = false,
                    requestStatus = 2,
                    mediaStatus = AvailabilityTier.PROCESSING,
                    requestedBy = "Alex",
                    createdAt = "2026-11-13",
                    downloadProgress = DownloadProgressInfo(
                        is4k = false,
                        status = "downloading",
                        title = "The.Brutalist.2024.1080p",
                        sizeBytes = 12_000_000_000L,
                        sizeLeftBytes = 1_320_000_000L,
                        percentage = 89f,
                        etaText = "~2m left",
                        estimatedCompletionIso = null
                    )
                ),
                MediaRequestSummary(
                    requestId = 103,
                    tmdbId = 202555,
                    mediaType = MediaType.SERIES,
                    title = "Daredevil: Born Again",
                    posterUrl = "art://poster/dd/DAREDEVIL/0xFF3D0A0E/0xFFE50914",
                    backdropUrl = null,
                    is4k = true,
                    requestStatus = 2,
                    mediaStatus = AvailabilityTier.PENDING,
                    requestedBy = "Prerak",
                    createdAt = "2026-11-13",
                    downloadProgress = null
                ),
                MediaRequestSummary(
                    requestId = 104,
                    tmdbId = 95396,
                    mediaType = MediaType.SERIES,
                    title = "Severance",
                    posterUrl = "art://poster/sev/SEVERANCE/0xFF0E3A4C/0xFF1CC8B8",
                    backdropUrl = null,
                    is4k = true,
                    requestStatus = 2,
                    mediaStatus = AvailabilityTier.AVAILABLE,
                    requestedBy = "Prerak",
                    createdAt = "2026-11-12",
                    downloadProgress = null
                )
            )
        )

        snapshotScreen("requests") {
            RequestsContent(
                state = state,
                onBack = {},
                onRefresh = {},
                onSelectFilter = {},
                onOpenRequestDetails = { _, _ -> }
            )
        }
    }

    @Test
    fun profiles() {
        val state = ProfilePickerUiState(
            profiles = listOf(
                UserProfile("1", "Prerak", "1", "t1", avatarColorHex = 0xFFE50914),
                UserProfile("2", "Alex", "2", "t2", avatarColorHex = 0xFF0071EB, pinHash = "locked"),
                UserProfile("3", "Sam", "3", "t3", avatarColorHex = 0xFF2EA44F),
                UserProfile("4", "Guest", "4", "t4", avatarColorHex = 0xFFD97706)
            )
        )

        snapshotScreen("profiles") {
            ProfilePickerContent(
                state = state,
                onOpenSettings = {},
                onSelectProfile = {},
                onOpenAddProfile = {},
                onCloseAuthDialog = {},
                onUpdateUsername = {},
                onUpdatePassword = {},
                onSignInWithPassword = {},
                onToggleAuthMode = {}
            )
        }
    }

    @Test
    fun profilesManage() {
        val state = ProfilePickerUiState(
            isManageMode = true,
            profiles = listOf(
                UserProfile("1", "Prerak", "1", "t1", avatarColorHex = 0xFFE50914),
                UserProfile("2", "Alex", "2", "t2", avatarColorHex = 0xFF0071EB, pinHash = "locked"),
                UserProfile("3", "Sam", "3", "t3", avatarColorHex = 0xFF2EA44F),
                UserProfile("4", "Guest", "4", "t4", avatarColorHex = 0xFFD97706)
            )
        )

        snapshotScreen("profiles_manage") {
            ProfilePickerContent(
                state = state,
                onOpenSettings = {},
                onSelectProfile = {},
                onOpenAddProfile = {},
                onCloseAuthDialog = {},
                onUpdateUsername = {},
                onUpdatePassword = {},
                onSignInWithPassword = {},
                onToggleAuthMode = {}
            )
        }
    }

    @Test
    fun profilePinCard() {
        snapshotScreen("profile_pin_card") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixColors.Background),
                contentAlignment = Alignment.Center
            ) {
                PinEntryCard(
                    profileName = "Alex",
                    pinInput = "12",
                    errorMessage = null,
                    onDigit = {},
                    onBackspace = {},
                    onClear = {},
                    onDismiss = {}
                )
            }
        }
    }

    @Test
    fun search() {
        val state = SearchUiState(
            query = "Dune",
            results = listOf(
                sampleDune(),
                samplePosterItem(
                    "m2", "Interstellar", "art://poster/interstellar/INTERSTELLAR/0xFF0E1B38/0xFFF4B942",
                    uhd = AvailabilityTier.AVAILABLE, hd = AvailabilityTier.AVAILABLE, jfId = "jf2"
                ),
                samplePosterItem(
                    "m3", "Arrival", "art://poster/arrival/ARRIVAL/0xFF1F2A38/0xFF90CAF9",
                    hd = AvailabilityTier.AVAILABLE, jfId = "jf3"
                ),
                samplePosterItem(
                    "m4", "Blade Runner 2049", "art://poster/br2049/BLADE RUNNER/0xFF3E1810/0xFFFF9800",
                    uhd = AvailabilityTier.AVAILABLE, hd = AvailabilityTier.AVAILABLE, jfId = "jf4"
                )
            ),
            personResults = listOf(
                PersonSearchResult(1, "Denis Villeneuve", "Directing", "art://cast/denis/DV/0xFF253746/0xFF78909C"),
                PersonSearchResult(2, "Rebecca Ferguson", "Acting", "art://cast/rebecca/RF/0xFF3A1C2A/0xFFF06292"),
                PersonSearchResult(3, "Timothée Chalamet", "Acting", "art://cast/timothee/TC/0xFF1E3224/0xFF66BB6A")
            )
        )

        snapshotScreen("search") {
            SearchContent(
                state = state,
                onBack = {},
                onQueryChange = {},
                onVoiceSearch = {},
                onSelectGenre = {},
                onSelectPerson = {},
                onClearFilter = {},
                onOpenDetails = {}
            )
        }
    }

    @Test
    fun playerOverlays() {
        val session = PlaybackSessionData(
            itemId = "ep2",
            title = "Severance",
            subtitle = "S2:E4 • Woe's Hollow",
            streamUrl = "http://example/stream",
            isHls = false,
            mediaSourceId = "src",
            playSessionId = "sess",
            resumeTicks = 0L,
            runtimeTicks = 30_000_000_000L,
            videoCodec = "HEVC",
            audioCodec = "EAC3 5.1 Atmos",
            resolutionLabel = "4K UHD",
            hdrFormat = "Dolby Vision"
        )
        val seasons = listOf(
            SeasonSummary(1, "Season 1", 9, hdStatus = AvailabilityTier.AVAILABLE, jellyfinSeasonId = "s1"),
            SeasonSummary(2, "Season 2", 10, hdStatus = AvailabilityTier.PARTIALLY_AVAILABLE, jellyfinSeasonId = "s2")
        )
        val episodes = listOf(
            EpisodeSummary(
                id = "ep1",
                name = "Hello, Ms. Cobel",
                overview = "Mark returns to Lumon under startlingly different circumstances.",
                seasonNumber = 2,
                episodeNumber = 1,
                runtimeTicks = 30_000_000_000L,
                resumePositionTicks = 30_000_000_000L,
                stillUrl = "art://still/ep1/S2E1/0xFF103742/0xFF4DD0E1",
                isAvailableInJellyfin = true,
                isWatched = true
            ),
            EpisodeSummary(
                id = "ep2",
                name = "Woe's Hollow",
                overview = "The MDR team embarks on an outdoor team-building retreat.",
                seasonNumber = 2,
                episodeNumber = 4,
                runtimeTicks = 31_200_000_000L,
                resumePositionTicks = 19_000_000_000L,
                stillUrl = "art://still/ep4/S2E4/0xFF1D2939/0xFF90CAF9",
                isAvailableInJellyfin = true,
                isWatched = false
            )
        )

        snapshotScreen("player_overlays") {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(NetflixColors.Background)
            ) {
                StreamInfoOverlay(
                    session = session,
                    selectedQuality = QualityPreset.AUTO,
                    playbackSpeed = 1.0f,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(32.dp)
                )
                NextEpisodeOverlayCard(
                    nextEpisodeTitle = "S2:E5 • Trojan's Horse",
                    autoplayEnabled = true,
                    countdownSeconds = 4,
                    onPlayNow = {},
                    onWatchCredits = {},
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(32.dp)
                )
                EpisodesDrawerOverlay(
                    seriesTitle = "Severance",
                    seasons = seasons,
                    selectedSeason = 2,
                    episodes = episodes,
                    isLoading = false,
                    currentEpisodeId = "ep2",
                    onSelectSeason = {},
                    onSelectEpisode = {},
                    onClose = {}
                )
            }
        }
    }

    @Test
    fun setup() {
        val state = SetupUiState(
            jellyfinUrl = "http://192.168.1.100:8096",
            seerrUrl = "http://192.168.1.100:5055",
            isTesting = false,
            statusMessage = "Connected to Home Jellyfin & Seerr (v3.0.1)"
        )

        snapshotScreen("setup") {
            SetupContent(
                state = state,
                onJellyfinUrlChange = {},
                onSeerrUrlChange = {},
                onConnectClick = {}
            )
        }
    }

    @Test
    fun homeTop10() {
        val state = HomeUiState(
            activeTab = HomeTab.MOVIES,
            profile = UserProfile("1", "Prerak", "1", "token", avatarColorHex = 0xFFE50914),
            featuredItem = sampleDune(),
            activeDownloadCount = 2,
            isLoading = false,
            rows = listOf(
                CatalogRow(
                    id = "top_10_movies",
                    title = "Top 10 Movies on Seerflix Today",
                    style = RowStyle.TOP_10,
                    items = listOf(
                        sampleDune(),
                        samplePosterItem(
                            "m2", "Interstellar", "art://poster/interstellar/INTERSTELLAR/0xFF0E1B38/0xFFF4B942",
                            uhd = AvailabilityTier.AVAILABLE, hd = AvailabilityTier.AVAILABLE, jfId = "jf2"
                        ),
                        samplePosterItem(
                            "m3", "The Brutalist", "art://poster/brutalist/THE BRUTALIST/0xFF292421/0xFFE07A5F",
                            hd = AvailabilityTier.PROCESSING,
                            hdDl = DownloadProgressInfo(false, "downloading", "1080p", 100L, 11L, 89f, "~2m left", null)
                        ),
                        samplePosterItem(
                            "m4", "Blade Runner 2049", "art://poster/br2049/BLADE RUNNER/0xFF3E1810/0xFFFF9800",
                            uhd = AvailabilityTier.AVAILABLE, hd = AvailabilityTier.AVAILABLE, jfId = "jf4"
                        ),
                        samplePosterItem(
                            "m5", "Nosferatu", "art://poster/nosferatu/NOSFERATU/0xFF121824/0xFF8FA8C8",
                            hd = AvailabilityTier.PENDING
                        )
                    )
                )
            )
        )

        snapshotScreen("home_top10") {
            HomeContent(
                state = state,
                onOpenDetails = {},
                onPlayDirect = {},
                onOpenSearch = {},
                onOpenRequests = {},
                onSwitchProfile = {},
                onOpenSettings = {},
                onSelectTab = {},
                onRefresh = {},
                onTogglePreviewMute = {},
                onItemFocused = {}
            )
        }
    }

    @Test
    fun collections() {
        val state = HomeUiState(
            activeTab = HomeTab.COLLECTIONS,
            profile = UserProfile("1", "Prerak", "1", "token", avatarColorHex = 0xFFE50914),
            activeDownloadCount = 2,
            isLoading = false,
            collections = listOf(
                CollectionSummary(
                    id = "box_dune",
                    tmdbCollectionId = 726871,
                    name = "Dune Collection",
                    backdropUrl = "art://backdrop/dune/DUNE COLLECTION/0xFF5D2906/0xFFFF9800",
                    ownedCount = 2,
                    totalCount = 3
                ),
                CollectionSummary(
                    id = "box_lotr",
                    tmdbCollectionId = 119,
                    name = "The Lord of the Rings",
                    backdropUrl = "art://backdrop/lotr/MIDDLE EARTH/0xFF1D3524/0xFFD4AF37",
                    ownedCount = 3,
                    totalCount = 3
                ),
                CollectionSummary(
                    id = "box_sv",
                    tmdbCollectionId = 573436,
                    name = "Spider-Verse Collection",
                    backdropUrl = "art://backdrop/spiderverse/SPIDER-VERSE/0xFF2B0A2B/0xFFE50914",
                    ownedCount = 2,
                    totalCount = 3
                ),
                CollectionSummary(
                    id = "box_jw",
                    tmdbCollectionId = 404609,
                    name = "John Wick Collection",
                    backdropUrl = "art://backdrop/johnwick/JOHN WICK/0xFF14182B/0xFF00E5FF",
                    ownedCount = 4,
                    totalCount = 4
                ),
                CollectionSummary(
                    id = "box_br",
                    tmdbCollectionId = 422837,
                    name = "Blade Runner Collection",
                    backdropUrl = "art://backdrop/bladerunner/BLADE RUNNER/0xFF3E1810/0xFFFF9800",
                    ownedCount = 2,
                    totalCount = 2
                ),
                CollectionSummary(
                    id = "box_alien",
                    tmdbCollectionId = 8091,
                    name = "Alien Anthology",
                    backdropUrl = "art://backdrop/alien/ALIEN/0xFF0D2329/0xFF66BB6A",
                    ownedCount = 5,
                    totalCount = 7
                )
            )
        )

        snapshotScreen("collections") {
            HomeContent(
                state = state,
                onOpenDetails = {},
                onPlayDirect = {},
                onOpenSearch = {},
                onOpenRequests = {},
                onSwitchProfile = {},
                onOpenSettings = {},
                onSelectTab = {},
                onRefresh = {},
                onTogglePreviewMute = {},
                onItemFocused = {}
            )
        }
    }

    private fun snapshotScreen(
        name: String,
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        paparazzi.snapshot(name = name) {
            CompositionLocalProvider(
                LocalInspectionMode provides true,
                LocalAsyncImagePreviewHandler provides artworkPreviewHandler
            ) {
                SeerflixTheme {
                    content()
                }
            }
        }
    }

    private fun sampleDune() = UnifiedMediaItem(
        id = "tmdb_movie_693134",
        tmdbId = 693134,
        jellyfinId = "jf_dune_2",
        mediaType = MediaType.MOVIE,
        title = "Dune: Part Two",
        overview = "Paul Atreides unites with Chani and the Fremen while seeking revenge against the conspirators who destroyed his family.",
        releaseYear = "2024",
        runtimeMinutes = 166,
        maturityRating = "PG-13",
        voteAverage = 8.4,
        genres = listOf("Sci-Fi", "Adventure"),
        posterUrl = "art://poster/dune2/DUNE PART TWO/0xFF5D2906/0xFFFF9800",
        backdropUrl = "art://backdrop/dune2/DUNE PART TWO/0xFF4A1E08/0xFFFFA726",
        hdStatus = AvailabilityTier.AVAILABLE,
        uhdStatus = AvailabilityTier.AVAILABLE,
        isInMyList = true,
        mediaBadges = listOf("4K", "Dolby Vision", "Dolby Atmos", "CC"),
        resumePositionTicks = 42_000_000_000L,
        runtimeTicks = 99_600_000_000L
    )

    private fun samplePosterItem(
        id: String,
        title: String,
        posterUrl: String,
        hd: AvailabilityTier = AvailabilityTier.UNKNOWN,
        uhd: AvailabilityTier = AvailabilityTier.UNKNOWN,
        jfId: String? = null,
        hdDl: DownloadProgressInfo? = null
    ) = UnifiedMediaItem(
        id = id,
        tmdbId = abs(id.hashCode()),
        jellyfinId = jfId,
        mediaType = MediaType.MOVIE,
        title = title,
        posterUrl = posterUrl,
        hdStatus = hd,
        uhdStatus = uhd,
        hdDownload = hdDl
    )

    private fun renderSyntheticArtwork(uri: String): Bitmap {
        val parts = uri.removePrefix("art://").split("/")
        val kind = parts.getOrElse(0) { "poster" }
        val label = parts.getOrElse(2) { "SEERFLIX" }
        val primary = parts.getOrNull(3)?.removePrefix("0x")?.toLongOrNull(16)?.toInt() ?: 0xFF1F2937.toInt()
        val accent = parts.getOrNull(4)?.removePrefix("0x")?.toLongOrNull(16)?.toInt() ?: 0xFFE50914.toInt()

        val (w, h) = when (kind) {
            "backdrop" -> 960 to 540
            "still" -> 480 to 270
            "cast" -> 220 to 220
            else -> 360 to 540
        }

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Rich atmospheric gradient background
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, w.toFloat(), h.toFloat(),
                intArrayOf(0xFF0A0A0C.toInt(), primary, 0xFF0E0E12.toInt()),
                floatArrayOf(0f, 0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        // Dramatic lighting orb / horizon glow
        val glowX = if (kind == "backdrop") w * 0.72f else w * 0.52f
        val glowY = if (kind == "backdrop") h * 0.36f else h * 0.38f
        val glowRadius = maxOf(w, h) * 0.55f
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                glowX, glowY, glowRadius,
                intArrayOf(
                    (accent and 0x00FFFFFF) or 0xAA000000.toInt(),
                    (primary and 0x00FFFFFF) or 0x44000000,
                    0x00000000
                ),
                floatArrayOf(0f, 0.45f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(glowX, glowY, glowRadius, glowPaint)

        // Stylized silhouette shapes for depth
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x33000000
        }
        if (kind == "backdrop") {
            canvas.drawOval(RectF(w * 0.48f, h * 0.58f, w * 1.15f, h * 1.25f), shapePaint)
            canvas.drawOval(RectF(w * 0.32f, h * 0.68f, w * 0.85f, h * 1.2f), shapePaint)
        } else if (kind == "cast") {
            // Stylized portrait silhouette
            val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF }
            canvas.drawCircle(w * 0.5f, h * 0.38f, w * 0.17f, headPaint)
            canvas.drawOval(RectF(w * 0.24f, h * 0.60f, w * 0.76f, h * 1.05f), headPaint)
        } else if (kind == "still") {
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x22FFFFFF
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(10f, 10f, w - 10f, h - 10f, framePaint)
        } else {
            // Cinematic poster graphic & title treatment
            val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0x22FFFFFF
                style = Paint.Style.STROKE
                strokeWidth = 3f
            }
            canvas.drawRect(14f, 14f, w - 14f, h - 14f, framePaint)

            val displayLabel = label.take(14)
            val scaleFactor = if (displayLabel.length > 10) 0.082f else 0.105f
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xEEFFFFFF.toInt()
                textSize = (w * scaleFactor).coerceAtLeast(20f)
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
                letterSpacing = 0.06f
            }
            canvas.drawText(displayLabel, w * 0.5f, h * 0.76f, titlePaint)

            val accentLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent
                strokeWidth = 4f
            }
            canvas.drawLine(w * 0.28f, h * 0.81f, w * 0.72f, h * 0.81f, accentLine)
        }

        return bitmap
    }
}
