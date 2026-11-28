package com.seerflix.tv.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.delay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListPrefetchStrategy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.SwitchAccount
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.seerflix.tv.core.data.AppUpdateInstallState
import com.seerflix.tv.core.data.StreamingService
import com.seerflix.tv.core.data.UpcomingDateFormatter
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.model.AppUpdateInfo
import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.HomeTabId
import com.seerflix.tv.core.model.JellyfinLibraryView
import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.ScheduledRecording
import androidx.compose.material.icons.filled.GridView
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ReadyRequestAlert
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.SyncPlayGroup
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.ui.components.ContinueWatchingCard
import com.seerflix.tv.ui.components.HeroBillboard
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.components.SizedAsyncImage
import com.seerflix.tv.ui.components.Top10PosterCard
import com.seerflix.tv.ui.components.TitleMetaLine
import com.seerflix.tv.ui.components.UiPresetPickerDialog
import com.seerflix.tv.ui.theme.NavLayoutStyle
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

private fun AvailabilityFilter.localized(s: UiStrings): String = when (this) {
    AvailabilityFilter.ALL -> s.libraryFilterAll
    AvailabilityFilter.WATCH_NOW -> s.homeFilterWatchNow
    AvailabilityFilter.REQUESTABLE -> s.homeFilterRequestable
}

private fun GenreFilterOption.localizedLabel(s: UiStrings): String = when (id) {
    "all" -> s.genreAll
    "action" -> s.genreAction
    "comedy" -> s.genreComedy
    "scifi" -> s.genreSciFi
    "horror" -> s.genreHorrorMystery
    "drama" -> s.genreDrama
    "crime" -> s.genreCrime
    "thriller" -> s.genreThriller
    "documentary" -> s.genreDocumentary
    "animation" -> s.genreAnimation
    "family" -> s.genreFamily
    "fantasy" -> s.genreFantasy
    "romance" -> s.genreRomance
    "reality" -> s.genreReality
    "kids" -> s.genreKids
    "history" -> s.genreHistory
    "music" -> s.genreMusic
    else -> label
}

/**
 * The catalog (UnifiedCatalogRepository) builds Home row titles in English. Resolve the shown
 * title from the row id instead; ids that carry no known meaning (server library names,
 * collections, anything new) keep the catalog's title.
 */
private fun localizedHomeRowTitle(id: String, title: String, s: UiStrings): String {
    genreRowTitle(id, s)?.let { return it }
    // Personalized rows (0.9.5): several "Because you ..." rows and library genre rows.
    if (id.startsWith("because_you_watched")) {
        return when {
            title.startsWith(BECAUSE_YOU_LIKED_PREFIX) -> s.rowBecauseYouLiked.format(title.removePrefix(BECAUSE_YOU_LIKED_PREFIX))
            title.startsWith(BECAUSE_YOU_WATCHED_PREFIX) -> s.rowBecauseYouWatched.format(title.removePrefix(BECAUSE_YOU_WATCHED_PREFIX))
            else -> s.rowBecauseYouWatchedGeneric
        }
    }
    if (id.startsWith("library_genre_")) return s.rowGenreInLibrary.format(title)
    StreamingService.entries.firstOrNull { it.newAndPopularRowId == id }?.let {
        return s.rowTop10OnService.format(it.displayName)
    }
    return when (id) {
        "continue_watching" -> when {
            title.startsWith(CONTINUE_WATCHING_FOR_PREFIX) ->
                s.homeContinueWatchingFor.format(title.removePrefix(CONTINUE_WATCHING_FOR_PREFIX))
            title == "Continue Watching" -> s.rowContinueWatching
            else -> title // already localized by HomeViewModel
        }
        "next_up" -> s.rowNextUp
        "ready_to_watch" -> s.rowReadyToWatch
        "gems_for_you" -> s.rowGemsForYou
        "todays_top_picks" ->
            if (title.startsWith(TOP_PICKS_FOR_PREFIX)) s.rowTopPicksFor.format(title.removePrefix(TOP_PICKS_FOR_PREFIX))
            else s.rowTodaysTopPicks
        "unwatched_in_library" -> s.rowUnwatchedInLibrary
        "hidden_gems" -> s.rowHiddenGems
        "forgotten_in_library" -> s.rowForgottenInLibrary
        "short_tonight" -> s.rowShortTonight
        "popular_household" -> s.rowPopularHousehold
        "trakt_recommended" -> s.rowTraktRecommended
        "more_from_person" -> when {
            title.startsWith(DIRECTED_BY_PREFIX) -> s.rowDirectedBy.format(title.removePrefix(DIRECTED_BY_PREFIX))
            title.startsWith(STARRING_PREFIX) -> s.rowStarring.format(title.removePrefix(STARRING_PREFIX))
            else -> title
        }
        "award_winning_tv" -> s.rowAwardWinningTv
        "top10_tv_now", "np_top10_tv_now" ->
            regionalTop10Title(title, "Top 10 TV Shows", s.rowTop10TvToday, s.rowTop10TvIn, s)
        "top10_movies_now", "np_top10_movies_now" ->
            regionalTop10Title(title, "Top 10 Movies", s.rowTop10MoviesToday, s.rowTop10MoviesIn, s)
        "my_list", "my_list_page" -> s.navMyList
        "my_list_shows" -> s.rowSavedShows
        "my_list_movies" -> s.rowSavedMovies
        "seasonal_picks" -> when (title) {
            "Date Night & Romance" -> s.rowSeasonalRomance
            "Summer Blockbusters" -> s.rowSeasonalSummer
            "Spooky Season Picks" -> s.rowSeasonalSpooky
            "Cozy Autumn & Thanksgiving Picks" -> s.rowSeasonalAutumn
            "Holiday Favorites" -> s.rowSeasonalHoliday
            "Critically Acclaimed Films" -> s.rowSeasonalAcclaimed
            else -> s.rowSeasonalPicksGeneric
        }
        "new_on_digital", "np_new_on_digital" -> s.rowNewOnDigital
        // The TV tab reuses this id for its "New on Lumen" row.
        "recently_added" -> if (title == "New on Lumen") s.rowNewOnLumen else s.rowRecentlyAdded
        "need_a_good_laugh" -> s.rowNeedALaugh
        "animation_critics_love" -> s.rowAnimationCriticsLove
        "blockbuster_movies" -> s.rowBlockbusterMovies
        "movie_comedies" -> s.rowMovieComedies
        "movie_action_adventure" -> s.rowMovieActionAdventure
        "movie_horror" -> s.rowMovieHorror
        "movie_drama" -> s.rowMovieDrama
        "movie_family_animation" -> s.rowMovieFamilyAnimation
        "movie_scifi" -> s.rowMovieSciFi
        "award_winning_tv_dramas" -> s.rowAwardWinningTvDramas
        "thirty_minute_hits" -> s.rowThirtyMinuteHits
        "imaginative_tv" -> s.rowImaginativeTv
        "familiar_tv_favorites" -> s.rowFamiliarTvFavorites
        "tv_comedies" -> s.rowTvComedies
        "tv_reality" -> s.genreReality
        "tv_crime" -> s.rowTvCrime
        "collections" -> s.navCollections
        "airing_this_week", "np_airing_this_week" -> s.rowAiringThisWeek
        "coming_to_streaming_dvd", "np_coming_streaming_dvd" -> s.rowComingToStreamingDvd
        "coming_soon_tv", "np_coming_tv" -> s.rowComingSoonTv
        "watch_it_again" -> s.rowWatchItAgain
        "jellyfin_movies" -> s.rowLibraryMovies
        "jellyfin_series" -> s.rowLibraryShows
        "np_trending" -> s.rowTrendingNow
        "np_latest" -> s.rowJustAddedToServer
        else -> title
    }
}

/** SyncPlay group state as Jellyfin / SyncPlayManager report it ("Playing", "Paused", ...), localized for display. */
private fun syncPlayStateLabel(state: String, s: UiStrings): String = when (state.lowercase()) {
    "playing" -> s.syncPlayStatePlaying
    "paused" -> s.syncPlayStatePaused
    "idle" -> s.syncPlayStateIdle
    "waiting" -> s.syncPlayStateWaiting
    else -> state
}

private const val CONTINUE_WATCHING_FOR_PREFIX = "Continue Watching for "
private const val BECAUSE_YOU_WATCHED_PREFIX = "Because you watched "
private const val BECAUSE_YOU_LIKED_PREFIX = "Because you liked "
private const val TOP_PICKS_FOR_PREFIX = "Top Picks for "
private const val DIRECTED_BY_PREFIX = "Directed by "
private const val STARRING_PREFIX = "Starring "

/** Per-genre rows: `genre_<kind>_<genreId>`, with the genre label localized. */
private fun genreRowTitle(id: String, s: UiStrings): String? {
    val (prefix, format) = listOf(
        "genre_library_" to s.rowGenreInLibrary,
        "genre_top10_movies_" to s.rowGenreTop10Movies,
        "genre_more_movies_" to s.rowGenreMoreMovies,
        "genre_top10_tv_" to s.rowGenreTop10Tv,
        "genre_more_tv_" to s.rowGenreMoreTv
    ).firstOrNull { id.startsWith(it.first) } ?: return null
    val genreId = id.removePrefix(prefix)
    val genre = GenreFilterOption.ALL_GENRES.firstOrNull { it.id == genreId } ?: return null
    return format.format(genre.localizedLabel(s))
}

/** English region suffixes the catalog appends to Top 10 titles, by ISO country code. */
private val TOP10_REGION_SUFFIXES = mapOf(
    "in the U.S. Today" to "US",
    "in the UK Today" to "GB",
    "in Canada Today" to "CA",
    "in Australia Today" to "AU",
    "in Germany Today" to "DE",
    "in France Today" to "FR",
    "in Spain Today" to "ES",
    "in Japan Today" to "JP"
)

/** "Top 10 Movies in the UK Today" -> the localized title with the country name in the UI language. */
private fun regionalTop10Title(
    title: String,
    englishPrefix: String,
    todayTitle: String,
    regionFormat: String,
    s: UiStrings
): String {
    // Not a catalog title (e.g. a hidden row in Customize Home, named from its id).
    if (!title.startsWith(englishPrefix)) return todayTitle
    val suffix = title.removePrefix(englishPrefix).trim()
    if (suffix == "Today") return todayTitle
    val region = TOP10_REGION_SUFFIXES[suffix] ?: return title
    // English keeps the catalog's own wording ("in the U.S.").
    if (s.languageCode == "en") return title
    val country = runCatching {
        java.util.Locale.Builder().setRegion(region).build()
            .getDisplayCountry(java.util.Locale.forLanguageTag(s.languageCode))
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: return title
    return regionFormat.format(country)
}

@Composable
private fun HomeTab.localizedLabel(strings: UiStrings): String = when (this) {
    HomeTab.HOME -> strings.navHome
    HomeTab.SHOWS -> strings.navShows
    HomeTab.MOVIES -> strings.navMovies
    HomeTab.COLLECTIONS -> strings.navCollections
    HomeTab.LIVE_TV -> strings.navLiveTv
    HomeTab.NEW_AND_POPULAR -> strings.navNewAndPopular
    HomeTab.MY_LIST -> strings.navMyList
}

@Composable
fun HomeScreen(
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onPlayDirect: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLibrary: (MediaType) -> Unit = {},
    onOpenLibraryView: (JellyfinLibraryView) -> Unit = {},
    onPlayQueue: (List<com.seerflix.tv.core.player.QueueEntry>, Boolean) -> Unit = { _, _ -> },
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    // Back from the player/details: pick up new Continue Watching progress (no-op on first load).
    LifecycleResumeEffect(viewModel) {
        viewModel.onScreenResumed()
        onPauseOrDispose { viewModel.onScreenPaused() }
    }
    HomeContent(
        state = state,
        onOpenDetails = onOpenDetails,
        onPlayDirect = onPlayDirect,
        onOpenSearch = onOpenSearch,
        onOpenRequests = onOpenRequests,
        onSwitchProfile = onSwitchProfile,
        onOpenSettings = onOpenSettings,
        onSelectTab = viewModel::selectTab,
        onSelectGenre = viewModel::selectGenre,
        onSelectAvailabilityFilter = viewModel::selectAvailabilityFilter,
        onRefresh = viewModel::refresh,
        onTogglePreviewMute = viewModel::togglePreviewMute,
        onToggleFeaturedMyList = viewModel::toggleFeaturedMyList,
        onItemFocused = viewModel::onItemFocused,
        onOpenContinueWatchingOptions = viewModel::openContinueWatchingOptions,
        onDismissContinueWatchingOptions = viewModel::dismissContinueWatchingOptions,
        onRemoveFromContinueWatching = viewModel::removeFromContinueWatching,
        onOpenPosterOptions = viewModel::openPosterOptions,
        onDismissPosterOptions = viewModel::dismissPosterOptions,
        onQuickRequest = viewModel::quickRequestItem,
        onTogglePosterMyList = viewModel::togglePosterMyList,
        onTogglePosterWatched = viewModel::togglePosterWatched,
        onOpenCollection = viewModel::openCollection,
        onDismissCollectionDetails = viewModel::dismissCollectionDetails,
        onRequestCollectionItem = { item -> viewModel.requestCollectionItem(item) },
        onRequestAllMissingInCollection = { viewModel.requestAllMissingInCollection() },
        onOpenAppearance = viewModel::openAppearanceDialog,
        onDismissAppearance = viewModel::dismissAppearanceDialog,
        onSelectUiPreset = viewModel::selectUiPreset,
        onOpenCustomizeHome = viewModel::openCustomizeHomeDialog,
        onDismissCustomizeHome = viewModel::dismissCustomizeHomeDialog,
        onSaveHomeCustomization = viewModel::saveHomeCustomization,
        onOpenSyncPlay = viewModel::openSyncPlayDialog,
        onDismissSyncPlay = viewModel::dismissSyncPlayDialog,
        onCreateSyncPlay = viewModel::createSyncPlayGroup,
        onJoinSyncPlay = viewModel::joinSyncPlayGroup,
        onLeaveSyncPlay = viewModel::leaveSyncPlayGroup,
        onRefreshSyncPlay = viewModel::refreshSyncPlayGroups,
        onPlayReadyAlert = { alert ->
            val jfId = viewModel.resolveReadyAlertPlayId(alert)
            if (jfId != null) {
                viewModel.dismissReadyAlert(alert.requestId)
                onPlayDirect(jfId)
            } else {
                // Library index hasn't picked the title up yet: open Details instead of
                // silently ignoring the "Watch Now" press.
                onOpenDetails(
                    UnifiedMediaItem(
                        id = "tmdb_${alert.tmdbId}",
                        tmdbId = alert.tmdbId,
                        mediaType = alert.mediaType,
                        title = alert.title,
                        posterUrl = alert.posterUrl
                    )
                )
            }
        },
        onDismissReadyAlert = viewModel::dismissReadyAlert,
        onOpenUpdateDialog = viewModel::openUpdateDialog,
        onDismissUpdateDialog = viewModel::dismissUpdateDialog,
        previewHttpClient = viewModel.previewHttpClient,
        extraActions = HomeExtraActions(
            onBrowseAll = onOpenLibrary,
            onOpenLibraryView = onOpenLibraryView,
            onOpenLibraries = viewModel::openLibrariesDialog,
            onDismissLibraries = viewModel::dismissLibrariesDialog,
            onOpenPlaylist = viewModel::openPlaylist,
            onDismissPlaylist = viewModel::dismissPlaylistDetails,
            onRemovePlaylistItem = viewModel::removePlaylistItem,
            onPlayQueue = onPlayQueue,
            onOpenLiveTvMenu = viewModel::openLiveTvMenu,
            onDismissLiveTvMenu = viewModel::dismissLiveTvMenu,
            onToggleLiveTvFavorite = viewModel::toggleLiveTvChannelFavorite,
            onRecordProgram = viewModel::recordProgram,
            onCancelRecording = viewModel::cancelRecording,
            onCancelSeriesRecording = viewModel::cancelSeriesRecording,
            onStartAppUpdate = viewModel::startAppUpdate,
            onCancelAppUpdate = viewModel::cancelAppUpdate,
            onResumeAppUpdateDialog = viewModel::onResumeAppUpdateDialog,
            onRateTitle = viewModel::ratePosterTitle,
            onNotInterested = viewModel::markPosterNotInterested,
            onRowItemOpened = viewModel::onRowItemOpened
        )
    )
}

/** Library grid, playlists, queue, DVR, and in-app updater callbacks, bundled so they don't have to be threaded one by one. */
@androidx.compose.runtime.Immutable
data class HomeExtraActions(
    val onBrowseAll: (MediaType) -> Unit = {},
    val onOpenLibraryView: (JellyfinLibraryView) -> Unit = {},
    val onOpenLibraries: () -> Unit = {},
    val onDismissLibraries: () -> Unit = {},
    val onOpenPlaylist: (com.seerflix.tv.core.model.PlaylistSummary) -> Unit = {},
    val onDismissPlaylist: () -> Unit = {},
    val onRemovePlaylistItem: (playlistId: String, entryId: String) -> Unit = { _, _ -> },
    val onPlayQueue: (entries: List<com.seerflix.tv.core.player.QueueEntry>, shuffle: Boolean) -> Unit = { _, _ -> },
    val onOpenLiveTvMenu: (LiveTvChannel, com.seerflix.tv.core.model.LiveTvProgram?) -> Unit = { _, _ -> },
    val onDismissLiveTvMenu: () -> Unit = {},
    val onToggleLiveTvFavorite: (LiveTvChannel) -> Unit = {},
    val onRecordProgram: (programId: String, series: Boolean, options: com.seerflix.tv.core.model.DvrTimerOptions) -> Unit = { _, _, _ -> },
    val onCancelRecording: (timerId: String) -> Unit = {},
    val onCancelSeriesRecording: (seriesTimerId: String) -> Unit = {},
    val onStartAppUpdate: () -> Unit = {},
    val onCancelAppUpdate: () -> Unit = {},
    val onResumeAppUpdateDialog: () -> Unit = {},
    /** Thumbs down / up / love from the poster menu (null clears). */
    val onRateTitle: (UnifiedMediaItem, com.seerflix.tv.core.data.TasteRating?) -> Unit = { _, _ -> },
    val onNotInterested: (UnifiedMediaItem) -> Unit = {},
    /** A title was opened from this Home row (rows the profile uses move up). */
    val onRowItemOpened: (rowId: String) -> Unit = {}
)

@Composable
fun HomeContent(
    state: HomeUiState,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onPlayDirect: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onRefresh: () -> Unit,
    onTogglePreviewMute: () -> Unit,
    onItemFocused: (UnifiedMediaItem) -> Unit,
    onSelectGenre: (GenreFilterOption) -> Unit = {},
    onSelectAvailabilityFilter: (AvailabilityFilter) -> Unit = {},
    onToggleFeaturedMyList: () -> Unit = {},
    onOpenContinueWatchingOptions: (UnifiedMediaItem) -> Unit = {},
    onDismissContinueWatchingOptions: () -> Unit = {},
    onRemoveFromContinueWatching: (UnifiedMediaItem) -> Unit = {},
    onOpenPosterOptions: (UnifiedMediaItem) -> Unit = {},
    onDismissPosterOptions: () -> Unit = {},
    onQuickRequest: (UnifiedMediaItem, Boolean) -> Unit = { _, _ -> },
    onTogglePosterMyList: (UnifiedMediaItem) -> Unit = {},
    onTogglePosterWatched: (UnifiedMediaItem) -> Unit = {},
    onOpenCollection: (boxSetId: String, title: String, tmdbCollectionId: Int?) -> Unit = { _, _, _ -> },
    onDismissCollectionDetails: () -> Unit = {},
    onRequestCollectionItem: (UnifiedMediaItem) -> Unit = {},
    onRequestAllMissingInCollection: () -> Unit = {},
    onOpenAppearance: () -> Unit = {},
    onDismissAppearance: () -> Unit = {},
    onSelectUiPreset: (UiPreset) -> Unit = {},
    onOpenCustomizeHome: () -> Unit = {},
    onDismissCustomizeHome: () -> Unit = {},
    onSaveHomeCustomization: (List<String>, List<String>, List<String>, HomeTabId) -> Unit = { _, _, _, _ -> },
    onOpenSyncPlay: () -> Unit = {},
    onDismissSyncPlay: () -> Unit = {},
    onCreateSyncPlay: (String) -> Unit = {},
    onJoinSyncPlay: (String) -> Unit = {},
    onLeaveSyncPlay: () -> Unit = {},
    onRefreshSyncPlay: () -> Unit = {},
    onPlayReadyAlert: (ReadyRequestAlert) -> Unit = {},
    onDismissReadyAlert: (Int) -> Unit = {},
    onOpenUpdateDialog: () -> Unit = {},
    onDismissUpdateDialog: () -> Unit = {},
    previewHttpClient: OkHttpClient? = null,
    extraActions: HomeExtraActions = HomeExtraActions()
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    // Attached to the active tab in whichever nav (rail / pill bar / header) the preset uses,
    // so Back from content can hand focus to the navigation before the app exits.
    val navFocusRequester = remember { FocusRequester() }
    val onBackToNav: () -> Unit = remember(navFocusRequester) {
        { runCatching { navFocusRequester.requestFocus() } }
    }

    val profileName = state.profile?.name ?: strings.homeProfileFallback
    // Blue dot on the profile entry when something in its menu needs attention.
    val profileHasAlert = state.updateInfo != null || state.activeSyncPlayGroup != null
    var showProfileMenu by remember { mutableStateOf(false) }

    // Cached: a new Brush every recomposition invalidates the full-screen background draw.
    val canvasBg = remember(spec.preset, colors) {
        when (spec.preset) {
            UiPreset.DISNEY_PLUS ->
                Brush.verticalGradient(listOf(colors.backgroundGlow.copy(alpha = 0.45f), colors.background))
            UiPreset.NETFLIX ->
                Brush.verticalGradient(
                    0.0f to colors.backgroundGlow.copy(alpha = 0.42f),
                    0.38f to colors.background,
                    1.0f to colors.background
                )
            else -> SolidColor(colors.background)
        }
    }

    when (spec.navStyle) {
        NavLayoutStyle.LEFT_RAIL -> {
            // The rail OVERLAYS the content instead of sitting next to it in a Row: expanding it
            // (68 -> 216 dp on focus) used to re-measure and re-layout every visible row and the hero.
            // Content is permanently inset by the collapsed width, so the collapsed look is identical.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasBg)
            ) {
                HomeMainCanvas(
                    state = state,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onRefresh = onRefresh,
                    onTogglePreviewMute = onTogglePreviewMute,
                    onItemFocused = onItemFocused,
                    onSelectTab = onSelectTab,
                    onSelectGenre = onSelectGenre,
                    onSelectAvailabilityFilter = onSelectAvailabilityFilter,
                    onToggleFeaturedMyList = onToggleFeaturedMyList,
                    onOpenContinueWatchingOptions = onOpenContinueWatchingOptions,
                    onOpenPosterOptions = onOpenPosterOptions,
                    onOpenCollection = onOpenCollection,
                    onPlayReadyAlert = onPlayReadyAlert,
                    onDismissReadyAlert = onDismissReadyAlert,
                    onBackToNav = onBackToNav,
                    extraActions = extraActions,
                    previewHttpClient = previewHttpClient,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = NETFLIX_RAIL_COLLAPSED_WIDTH)
                )

                NetflixNavRail(
                    activeTab = state.activeTab,
                    activeDownloadCount = state.activeDownloadCount,
                    isSeerrConfigured = state.isSeerrConfigured,
                    profileName = profileName,
                    profileHasAlert = profileHasAlert,
                    strings = strings,
                    activeTabFocusRequester = navFocusRequester,
                    onSelectTab = onSelectTab,
                    onOpenSearch = onOpenSearch,
                    onOpenRequests = onOpenRequests,
                    onOpenProfileMenu = { showProfileMenu = true }
                )
            }
        }
        NavLayoutStyle.TOP_GLASS_PILL -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasBg)
            ) {
                HomeMainCanvas(
                    state = state,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onRefresh = onRefresh,
                    onTogglePreviewMute = onTogglePreviewMute,
                    onItemFocused = onItemFocused,
                    onSelectTab = onSelectTab,
                    onSelectGenre = onSelectGenre,
                    onSelectAvailabilityFilter = onSelectAvailabilityFilter,
                    onToggleFeaturedMyList = onToggleFeaturedMyList,
                    onOpenContinueWatchingOptions = onOpenContinueWatchingOptions,
                    onOpenPosterOptions = onOpenPosterOptions,
                    onOpenCollection = onOpenCollection,
                    onPlayReadyAlert = onPlayReadyAlert,
                    onDismissReadyAlert = onDismissReadyAlert,
                    onBackToNav = onBackToNav,
                    extraActions = extraActions,
                    previewHttpClient = previewHttpClient,
                    modifier = Modifier.fillMaxSize()
                )

                AppleTvTopGlassBar(
                    activeTab = state.activeTab,
                    activeDownloadCount = state.activeDownloadCount,
                    isSeerrConfigured = state.isSeerrConfigured,
                    profileName = profileName,
                    profileHasAlert = profileHasAlert,
                    strings = strings,
                    activeTabFocusRequester = navFocusRequester,
                    onSelectTab = onSelectTab,
                    onOpenSearch = onOpenSearch,
                    onOpenRequests = onOpenRequests,
                    onOpenProfileMenu = { showProfileMenu = true },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp, start = 16.dp, end = 16.dp)
                )
            }
        }
        NavLayoutStyle.TOP_HEADER_BAR -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(canvasBg)
            ) {
                DisneyTopHeaderBar(
                    activeTab = state.activeTab,
                    activeDownloadCount = state.activeDownloadCount,
                    isSeerrConfigured = state.isSeerrConfigured,
                    profileName = profileName,
                    profileHasAlert = profileHasAlert,
                    strings = strings,
                    activeTabFocusRequester = navFocusRequester,
                    onSelectTab = onSelectTab,
                    onOpenSearch = onOpenSearch,
                    onOpenRequests = onOpenRequests,
                    onOpenProfileMenu = { showProfileMenu = true }
                )

                HomeMainCanvas(
                    state = state,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onRefresh = onRefresh,
                    onTogglePreviewMute = onTogglePreviewMute,
                    onItemFocused = onItemFocused,
                    onSelectTab = onSelectTab,
                    onSelectGenre = onSelectGenre,
                    onSelectAvailabilityFilter = onSelectAvailabilityFilter,
                    onToggleFeaturedMyList = onToggleFeaturedMyList,
                    onOpenContinueWatchingOptions = onOpenContinueWatchingOptions,
                    onOpenPosterOptions = onOpenPosterOptions,
                    onOpenCollection = onOpenCollection,
                    onPlayReadyAlert = onPlayReadyAlert,
                    onDismissReadyAlert = onDismissReadyAlert,
                    onBackToNav = onBackToNav,
                    extraActions = extraActions,
                    previewHttpClient = previewHttpClient,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
    }

    if (showProfileMenu) {
        ProfileMenuDialog(
            profileName = profileName,
            isKidsMode = state.profile?.kidsMode == true,
            activeSyncPlayName = state.activeSyncPlayGroup?.groupName,
            hasUpdate = state.updateInfo != null,
            onOpenLibraries = extraActions.onOpenLibraries,
            onOpenSettings = onOpenSettings,
            onSwitchProfile = onSwitchProfile,
            onOpenSyncPlay = onOpenSyncPlay,
            onOpenCustomizeHome = onOpenCustomizeHome,
            onOpenAppearance = onOpenAppearance,
            onOpenUpdate = onOpenUpdateDialog,
            onRefresh = onRefresh,
            onDismiss = { showProfileMenu = false }
        )
    }

    state.continueWatchingMenuTarget?.let { target ->
        ContinueWatchingOptionsDialog(
            item = target,
            onResume = {
                onDismissContinueWatchingOptions()
                target.jellyfinId?.let(onPlayDirect)
            },
            onViewDetails = {
                onDismissContinueWatchingOptions()
                onOpenDetails(target)
            },
            onRemove = { onRemoveFromContinueWatching(target) },
            onDismiss = onDismissContinueWatchingOptions
        )
    }

    state.posterMenuTarget?.let { target ->
        if (target.boxSetId == null) {
            val has4k = if (target.mediaType == MediaType.MOVIE) state.has4kMovieServer else state.has4kTvServer
            val canRequest = state.isSeerrConfigured && (state.profile?.canSubmitRequests != false)
            PosterOptionsDialog(
                item = target,
                has4kServer = has4k,
                canSubmitRequests = canRequest,
                isKidsLocked = state.isSeerrConfigured && state.profile?.canSubmitRequests == false,
                onPlay = {
                    onDismissPosterOptions()
                    target.jellyfinId?.let(onPlayDirect)
                },
                onRequestHd = { onQuickRequest(target, false) },
                onRequest4k = { onQuickRequest(target, true) },
                onToggleMyList = { onTogglePosterMyList(target) },
                onToggleWatched = { onTogglePosterWatched(target) },
                onViewDetails = {
                    onDismissPosterOptions()
                    onOpenDetails(target)
                },
                onRate = { rating -> extraActions.onRateTitle(target, rating) },
                onNotInterested = { extraActions.onNotInterested(target) },
                onDismiss = onDismissPosterOptions
            )
        }
    }

    state.activeCollectionDetails?.let { collection ->
        CollectionDetailsDialog(
            collection = collection,
            isLoading = state.collectionDetailsLoading,
            onPlayItem = { item ->
                onDismissCollectionDetails()
                item.jellyfinId?.let(onPlayDirect)
            },
            onPlayQueue = { entries, shuffle ->
                onDismissCollectionDetails()
                extraActions.onPlayQueue(entries, shuffle)
            },
            onOpenItemDetails = { item ->
                onDismissCollectionDetails()
                onOpenDetails(item)
            },
            onRequestItem = onRequestCollectionItem,
            onRequestAllMissing = onRequestAllMissingInCollection,
            onDismiss = onDismissCollectionDetails
        )
    }

    state.activePlaylistDetails?.let { playlist ->
        PlaylistDetailsDialog(
            playlist = playlist,
            isLoading = state.playlistDetailsLoading,
            onPlayItem = { item ->
                extraActions.onDismissPlaylist()
                item.jellyfinId?.let(onPlayDirect)
            },
            onPlayQueue = { entries, shuffle ->
                extraActions.onDismissPlaylist()
                extraActions.onPlayQueue(entries, shuffle)
            },
            onRemoveItem = { entryId ->
                extraActions.onRemovePlaylistItem(playlist.id, entryId)
            },
            onDismiss = extraActions.onDismissPlaylist
        )
    }

    if (state.showLibrariesDialog) {
        LibrariesPickerDialog(
            libraries = state.libraryViews,
            onSelectLibrary = { lib ->
                extraActions.onDismissLibraries()
                extraActions.onOpenLibraryView(lib)
            },
            onDismiss = extraActions.onDismissLibraries
        )
    }

    state.liveTvMenuChannel?.let { channel ->
        LiveTvProgramDialog(
            channel = channel,
            selectedProgram = state.liveTvMenuProgram,
            onWatch = {
                extraActions.onDismissLiveTvMenu()
                onPlayDirect(channel.id)
            },
            onToggleFavorite = { extraActions.onToggleLiveTvFavorite(channel) },
            onRecord = extraActions.onRecordProgram,
            onCancel = extraActions.onCancelRecording,
            onCancelSeries = extraActions.onCancelSeriesRecording,
            onDismiss = extraActions.onDismissLiveTvMenu
        )
    }

    if (state.showAppearanceDialog) {
        UiPresetPickerDialog(
            profileName = state.profile?.name ?: strings.homeProfileFallback,
            currentPreset = state.profile?.uiPreset ?: spec.preset,
            onSelectPreset = onSelectUiPreset,
            onDismiss = onDismissAppearance
        )
    }

    if (state.showCustomizeHomeDialog && state.profile != null) {
        CustomizeHomeDialog(
            profile = state.profile,
            libraries = state.libraryViews,
            currentRows = state.rows,
            onSave = onSaveHomeCustomization,
            onDismiss = onDismissCustomizeHome
        )
    }

    if (state.showSyncPlayDialog) {
        SyncPlayDialog(
            groups = state.syncPlayGroups,
            activeGroup = state.activeSyncPlayGroup,
            onCreateGroup = onCreateSyncPlay,
            onJoinGroup = onJoinSyncPlay,
            onLeaveGroup = onLeaveSyncPlay,
            onRefresh = onRefreshSyncPlay,
            onDismiss = onDismissSyncPlay
        )
    }

    if (state.showUpdateDialog && state.updateInfo != null) {
        AppUpdateDialog(
            updateInfo = state.updateInfo,
            installState = state.updateInstallState,
            onStartUpdate = extraActions.onStartAppUpdate,
            onCancelUpdate = extraActions.onCancelAppUpdate,
            onResume = extraActions.onResumeAppUpdateDialog,
            onDismiss = onDismissUpdateDialog
        )
    }
}

private val NETFLIX_RAIL_COLLAPSED_WIDTH = 68.dp
private val NETFLIX_RAIL_EXPANDED_WIDTH = 216.dp

/**
 * D-pad scrolling for the vertical Home list.
 *
 * - Top of the page: anything that is fully visible with the list scrolled to the very top (hero
 *   buttons, the first row) keeps the list at the top, so on launch you see the banner AND the
 *   whole first row.
 * - Further down, the focused row is parked just below the compact info header ([pivotPx]), so
 *   every Down/Up press scrolls by exactly one row (Netflix-style).
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
private class HomeListBringIntoViewSpec(
    private val listState: LazyListState,
    private val pivotPx: Float
) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (listState.firstVisibleItemIndex == 0) {
            val scrolled = listState.firstVisibleItemScrollOffset.toFloat()
            val absoluteBottom = offset + scrolled + size
            if (absoluteBottom <= containerSize) return -scrolled
        }
        val pivot = pivotPx.coerceAtMost(containerSize * 0.5f)
        // Too tall to fit below the pivot: align its bottom with the viewport end instead.
        val target = if (size <= containerSize && containerSize - pivot < size) containerSize - size else pivot
        return offset - target
    }
}

/** Height of the info header that replaces the banner once you scroll into the rows. */
private val COMPACT_HEADER_HEIGHT = 150.dp

/** Row title + spacing above a row's cards (see CatalogRowSection). */
private val ROW_TITLE_ALLOWANCE = 44.dp

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun HomeMainCanvas(
    state: HomeUiState,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onPlayDirect: (String) -> Unit,
    onRefresh: () -> Unit,
    onTogglePreviewMute: () -> Unit,
    onItemFocused: (UnifiedMediaItem) -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onSelectGenre: (GenreFilterOption) -> Unit,
    onSelectAvailabilityFilter: (AvailabilityFilter) -> Unit,
    onToggleFeaturedMyList: () -> Unit,
    onOpenContinueWatchingOptions: (UnifiedMediaItem) -> Unit,
    onOpenPosterOptions: (UnifiedMediaItem) -> Unit,
    onOpenCollection: (boxSetId: String, title: String, tmdbCollectionId: Int?) -> Unit,
    onPlayReadyAlert: (ReadyRequestAlert) -> Unit,
    onDismissReadyAlert: (Int) -> Unit,
    onBackToNav: () -> Unit = {},
    previewHttpClient: OkHttpClient? = null,
    extraActions: HomeExtraActions = HomeExtraActions(),
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val scope = rememberCoroutineScope()
    val hasBackDispatcher = LocalOnBackPressedDispatcherOwner.current != null

    // Duplicate row ids would crash LazyColumn ("Key was already used").
    val rows = remember(state.rows) { state.rows.distinctBy { it.id } }

    // --- D-pad focus bookkeeping -------------------------------------------------------------
    // Key of the last focused card; saveable so it survives navigating to Details and back.
    // Held as a raw State (not delegated) and only written from focus callbacks so focus moves
    // don't recompose this whole canvas.
    val lastFocusedCardKey = rememberSaveable { mutableStateOf<String?>(null) }
    // Non-null only when Home re-enters composition with a saved focus target (Back from Details/Player).
    val restoreFocusKey = remember { Snapshot.withoutReadObservation { lastFocusedCardKey.value } }
    val restoreFocusRequester = remember { FocusRequester() }
    val heroFocusRequester = remember { FocusRequester() }
    // The first row (entered at its first card): where focus starts on launch.
    val firstCardFocusRequester = remember { FocusRequester() }
    var initialFocusHandled by rememberSaveable { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    val onCardFocused = remember<(String) -> Unit> { { cardKey -> lastFocusedCardKey.value = cardKey } }

    // Hoisted out of the list branch so it survives a visit to Collections / Live TV.
    val listState = rememberLazyListState()

    val focusTop: suspend () -> Unit = {
        withFrameNanos { }
        withFrameNanos { }
        // The banner's Play button: it is not inside a recycled row, so it is always the real
        // top of the page. (Targeting the first row after scrolling back up could land mid-row.)
        val ok = runCatching { heroFocusRequester.requestFocus() }.getOrDefault(false)
        if (!ok) runCatching { firstCardFocusRequester.requestFocus() }
    }

    // Restore focus to the card the user left from instead of dumping it on the hero / nothing.
    // Coming back with no saved card (e.g. the player was opened from a Watch Next/deep link)
    // lands on the first row.
    LaunchedEffect(Unit) {
        if (restoreFocusKey == null && !initialFocusHandled) return@LaunchedEffect
        withFrameNanos { }
        val restored = restoreFocusKey != null &&
            runCatching { restoreFocusRequester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
        if (!restored) focusTop()
    }
    // One-shot initial focus once the first load settles: the first card of the first row, so the
    // whole first row is on screen and Down/Right work immediately.
    LaunchedEffect(state.isLoading) {
        if (state.isLoading || initialFocusHandled || restoreFocusKey != null) return@LaunchedEffect
        initialFocusHandled = true
        withFrameNanos { }
        val ok = runCatching { firstCardFocusRequester.requestFocus() }.getOrDefault(false) ||
            runCatching { heroFocusRequester.requestFocus() }.getOrDefault(false)
        if (!ok && state.errorMessage == null) onBackToNav()
    }

    // --- Per-tab scroll memory ------------------------------------------------------------
    val tabScroll = remember { mutableMapOf<HomeTab, Pair<Int, Int>>() }
    var shownTab by remember { mutableStateOf(state.activeTab) }
    var pendingRestoreTab by remember { mutableStateOf<HomeTab?>(null) }
    LaunchedEffect(state.activeTab) {
        if (shownTab == state.activeTab) return@LaunchedEffect
        tabScroll[shownTab] = listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        shownTab = state.activeTab
        runCatching { listState.scrollToItem(0) }
        pendingRestoreTab = state.activeTab
    }
    LaunchedEffect(pendingRestoreTab, rows.isNotEmpty()) {
        val tab = pendingRestoreTab ?: return@LaunchedEffect
        if (rows.isEmpty()) return@LaunchedEffect
        pendingRestoreTab = null
        val (index, offset) = tabScroll[tab] ?: return@LaunchedEffect
        withFrameNanos { }
        runCatching { listState.scrollToItem(index, offset) }
    }

    // Back: Collections -> Movies; deep in the rows -> back to the top of the page;
    // at the top -> the nav. Only a Back from the nav exits the app.
    if (hasBackDispatcher) {
        BackHandler(enabled = contentHasFocus) {
            when {
                state.activeTab == HomeTab.COLLECTIONS -> {
                    onSelectTab(HomeTab.MOVIES)
                    onBackToNav()
                }
                listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0 -> {
                    scope.launch {
                        listState.scrollToItem(0)
                        focusTop()
                    }
                }
                else -> onBackToNav()
            }
        }
    }

    val hasContentForTab = when (state.activeTab) {
        HomeTab.COLLECTIONS -> state.collections.isNotEmpty()
        HomeTab.LIVE_TV -> state.liveTvChannels.isNotEmpty() || state.liveTvRecordings.isNotEmpty()
        else -> state.rows.isNotEmpty()
    }

    Box(modifier = modifier.onFocusChanged { contentHasFocus = it.hasFocus }) {
        when {
            // Per-tab check: switching to Collections / Live TV used to flash their
            // "nothing found" empty state while the request was still in flight.
            state.isLoading && !hasContentForTab -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = strings.homeLoadingHub.format(spec.preset.displayName),
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
            state.errorMessage != null && !hasContentForTab -> {
                val retryFocusRequester = remember { FocusRequester() }
                LaunchedEffect(state.errorMessage) {
                    // Nothing else is focusable here; make sure the remote lands on Retry.
                    withFrameNanos { }
                    runCatching { retryFocusRequester.requestFocus() }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.errorMessage.orEmpty(),
                        color = colors.accent,
                        fontSize = 15.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(14.dp))
                    NavRailItem(
                        icon = Icons.Filled.Refresh,
                        label = strings.commonRetry,
                        expanded = true,
                        selected = false,
                        onClick = onRefresh,
                        modifier = Modifier
                            .width(160.dp)
                            .focusRequester(retryFocusRequester)
                    )
                }
            }
            state.activeTab == HomeTab.COLLECTIONS -> {
                CollectionsHubContent(
                    collections = state.collections,
                    onOpenCollection = { col ->
                        onOpenCollection(col.id, col.name, col.tmdbCollectionId)
                    }
                )
            }
            state.activeTab == HomeTab.LIVE_TV -> {
                LiveTvGuideContent(
                    channels = state.liveTvChannels,
                    recordings = state.liveTvRecordings,
                    scheduled = state.liveTvScheduled,
                    dvrFeedback = state.dvrFeedback,
                    onTuneChannel = onPlayDirect,
                    onOpenRecording = onOpenDetails,
                    onOpenChannelMenu = extraActions.onOpenLiveTvMenu,
                    onToggleChannelFavorite = extraActions.onToggleLiveTvFavorite,
                    onCancelScheduled = { rec ->
                        if (rec.isSeriesRule) extraActions.onCancelSeriesRecording(rec.id)
                        else extraActions.onCancelRecording(rec.id)
                    }
                )
            }
            state.activeTab == HomeTab.MY_LIST && state.rows.isEmpty() && state.playlists.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Bookmark,
                        contentDescription = null,
                        tint = colors.textMuted,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(strings.homeMyListEmpty, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = strings.homeMyListEmptyHint,
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            }
            else -> {
                // The Apple TV glass bar floats over the list; without a hero the filter bar sat under it.
                val topInset = if (spec.navStyle == NavLayoutStyle.TOP_GLASS_PILL) 70.dp else 0.dp
                val listTopPadding = if (state.featuredItem == null) topInset + 22.dp else 0.dp
                // Platform spec (pivot on Android TV) for the horizontal rows; the vertical list gets
                // the Home-specific spec below. Captured before it is overridden for the LazyColumn.
                val rowBringIntoViewSpec = LocalBringIntoViewSpec.current
                val density = androidx.compose.ui.platform.LocalDensity.current
                // The pivot positions the focused CARD; leave room above it for the row's title.
                val pivotPx = with(density) { (topInset + COMPACT_HEADER_HEIGHT + ROW_TITLE_ALLOWANCE).toPx() }
                val homeBringIntoViewSpec = remember(listState, pivotPx) { HomeListBringIntoViewSpec(listState, pivotPx) }
                val isMoviesOrShows = state.activeTab == HomeTab.SHOWS || state.activeTab == HomeTab.MOVIES
                CompositionLocalProvider(LocalBringIntoViewSpec provides homeBringIntoViewSpec) {
                LazyColumn(
                    state = listState,
                    // Coming back from the nav lands on the row (and card) you left, not on
                    // whatever happens to be level with the nav item.
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer(),
                    contentPadding = PaddingValues(top = listTopPadding, bottom = 48.dp)
                ) {
                    state.featuredItem?.let { hero ->
                        item(key = "hero_billboard", contentType = "hero") {
                            HeroBillboard(
                                item = hero,
                                previewStream = state.previewStream,
                                isMuted = state.isPreviewMuted,
                                primaryFocusRequester = heroFocusRequester,
                                previewHttpClient = previewHttpClient,
                                onToggleMute = onTogglePreviewMute,
                                onPrimaryAction = {
                                    if (hero.boxSetId != null) {
                                        onOpenCollection(hero.boxSetId, hero.title, hero.collectionId)
                                    } else if (hero.isPlayable && hero.jellyfinId != null) {
                                        onPlayDirect(hero.jellyfinId)
                                    } else {
                                        onOpenDetails(hero)
                                    }
                                },
                                onMoreInfo = {
                                    if (hero.boxSetId != null) {
                                        onOpenCollection(hero.boxSetId, hero.title, hero.collectionId)
                                    } else {
                                        onOpenDetails(hero)
                                    }
                                },
                                onToggleMyList = onToggleFeaturedMyList,
                                // Remember the hero too, so Back from Details lands here (no card
                                // requester matches this key, so restore falls back to the first row).
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) onCardFocused("hero_billboard") }
                            )
                        }
                    }

                    if (state.readyAlerts.isNotEmpty() && state.profile?.isRestricted != true) {
                        item(key = "ready_alerts_banner", contentType = "alerts") {
                            ReadyRequestAlertsBanner(
                                alerts = state.readyAlerts,
                                onPlayAlert = onPlayReadyAlert,
                                onDismissAlert = onDismissReadyAlert
                            )
                        }
                    }

                    if (state.activeTab == HomeTab.MY_LIST && state.playlists.isNotEmpty()) {
                        item(key = "playlists_row", contentType = "playlists") {
                            PlaylistsRowSection(
                                playlists = state.playlists,
                                rowBringIntoViewSpec = rowBringIntoViewSpec,
                                onOpenPlaylist = extraActions.onOpenPlaylist
                            )
                        }
                    }

                    if (isMoviesOrShows) {
                        // Clean Netflix-style control row: Categories picker pill + browse-all, Libraries, Collections, availability filter.
                        item(key = "genre_filter_bar", contentType = "genre_bar") {
                            val tabMediaType = if (state.activeTab == HomeTab.MOVIES) MediaType.MOVIE else MediaType.SERIES
                            GenreFilterBar(
                                mediaType = tabMediaType,
                                selectedGenre = state.selectedGenre,
                                onSelectGenre = onSelectGenre,
                                bringIntoViewSpec = rowBringIntoViewSpec,
                                browseAllLabel = if (state.activeTab == HomeTab.MOVIES) {
                                    strings.libraryAllMovies
                                } else {
                                    strings.libraryAllShows
                                },
                                onBrowseAll = { extraActions.onBrowseAll(tabMediaType) },
                                onOpenLibraries = if (state.libraryViews.isNotEmpty()) extraActions.onOpenLibraries else null,
                                onOpenCollections = if (state.activeTab == HomeTab.MOVIES && state.profile?.isRestricted != true) {
                                    { onSelectTab(HomeTab.COLLECTIONS) }
                                } else null,
                                selectedFilter = state.availabilityFilter,
                                isSeerrConfigured = state.isSeerrConfigured,
                                onSelectFilter = onSelectAvailabilityFilter
                            )
                        }
                    } else if (state.activeTab.showsAvailabilityFilter) {
                        item(key = "availability_filter_bar", contentType = "filter_bar") {
                            AvailabilityFilterBar(
                                selectedFilter = state.availabilityFilter,
                                isSeerrConfigured = state.isSeerrConfigured,
                                onSelectFilter = onSelectAvailabilityFilter
                            )
                        }
                    }

                    if (rows.isEmpty() && !state.isLoading) {
                        item(key = "empty_filtered_state", contentType = "empty") {
                            val isFiltered = state.availabilityFilter != AvailabilityFilter.ALL ||
                                state.selectedGenre.id != GenreFilterOption.ALL_GENRES.first().id
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 48.dp, vertical = 48.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (isFiltered) {
                                        strings.homeNoTitlesMatch.format(state.availabilityFilter.localized(strings))
                                    } else {
                                        strings.homeNothingYet
                                    },
                                    color = colors.white,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = if (isFiltered) {
                                        strings.homeNoTitlesMatchHint
                                    } else {
                                        // Restricted profiles: say why, rather than "your libraries are empty".
                                        if (state.profile?.isRestricted == true) strings.homeNothingForProfileHint
                                        else strings.homeNothingYetHint
                                    },
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    itemsIndexed(
                        items = rows,
                        key = { _, row -> row.id },
                        contentType = { _, row -> row.style }
                    ) { index, row ->
                        CatalogRowSection(
                            row = row,
                            rowBringIntoViewSpec = rowBringIntoViewSpec,
                            restoreFocusKey = restoreFocusKey,
                            restoreFocusRequester = restoreFocusRequester,
                            firstCardFocusRequester = if (index == 0) firstCardFocusRequester else null,
                            onCardFocused = onCardFocused,
                            onItemFocus = onItemFocused,
                            onItemClick = { item ->
                                extraActions.onRowItemOpened(row.id)
                                if (item.boxSetId != null) {
                                    onOpenCollection(item.boxSetId, item.title, item.collectionId)
                                } else if (row.style == RowStyle.CONTINUE_WATCHING && item.jellyfinId != null) {
                                    onPlayDirect(item.jellyfinId)
                                } else {
                                    onOpenDetails(item)
                                }
                            },
                            onContinueWatchingOptions = onOpenContinueWatchingOptions,
                            onPosterOptions = onOpenPosterOptions
                        )
                    }
                }
                }

                // Once the banner has scrolled away, a compact header keeps the focused title's
                // name, details and synopsis on screen above the rows.
                val showCompactHeader by remember(listState) {
                    derivedStateOf { listState.firstVisibleItemIndex > 0 }
                }
                val headerItem = state.featuredItem
                if (showCompactHeader && headerItem != null) {
                    CompactInfoHeader(
                        item = headerItem,
                        topInset = topInset,
                        modifier = Modifier.align(Alignment.TopStart)
                    )
                }
            }
        }

        state.actionFeedbackMessage?.let { message ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.dialogSurface)
                    .border(1.dp, colors.badgeBorder, RoundedCornerShape(8.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message,
                    color = colors.white,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The banner, shrunk: title, one metadata line and a two-line synopsis of the focused title over a
 * faded backdrop. Not focusable — it only describes what is focused in the rows below.
 */
@Composable
private fun CompactInfoHeader(
    item: UnifiedMediaItem,
    topInset: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp
    // Reaches a little past the pivot line so the previous row's card titles don't peek out
    // between the header and the focused row's title.
    val height = topInset + COMPACT_HEADER_HEIGHT + 14.dp
    val bg = colors.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(bg)
    ) {
        SizedAsyncImage(
            url = item.backdropUrl ?: item.posterUrl,
            contentDescription = null,
            width = screenWidth * 0.55f,
            height = height,
            maxRequestWidthPx = 960,
            keepPreviousWhileLoading = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .width(screenWidth * 0.55f)
                .fillMaxHeight()
        )
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val horizontal = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to bg,
                            0.45f to bg,
                            0.75f to bg.copy(alpha = 0.55f),
                            1.0f to bg.copy(alpha = 0.15f)
                        )
                    )
                    val bottom = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, bg),
                        startY = size.height * 0.55f,
                        endY = size.height
                    )
                    onDrawBehind {
                        drawRect(horizontal)
                        drawRect(bottom)
                    }
                }
        )
        Column(
            modifier = Modifier
                .padding(start = spec.contentStartPaddingDp.dp, top = topInset + 18.dp)
                .fillMaxWidth(0.58f)
        ) {
            Text(
                text = item.title,
                color = colors.white,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            TitleMetaLine(item = item)
            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.overview,
                    color = colors.textPrimary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun AppleTvTopGlassBar(
    activeTab: HomeTab,
    activeDownloadCount: Int,
    isSeerrConfigured: Boolean,
    profileName: String,
    profileHasAlert: Boolean,
    strings: UiStrings,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenProfileMenu: () -> Unit,
    modifier: Modifier = Modifier,
    activeTabFocusRequester: FocusRequester? = null
) {
    val colors = SeerflixTokens.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Up from the content lands on the tab you are on.
            .focusProperties { enter = { activeTabFocusRequester ?: FocusRequester.Default } }
            .focusGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left brand capsule
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(colors.navBarBackground)
                .border(1.dp, colors.navBarBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Text(
                text = "tv+",
                color = colors.accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Lumen",
                color = colors.white,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Center floating glass pill tabs
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(colors.navBarBackground)
                .border(1.dp, colors.navBarBorder, RoundedCornerShape(28.dp))
                .padding(horizontal = 4.dp, vertical = 3.dp)
        ) {
            NAV_DESTINATIONS.forEach { tab ->
                val isActive = tab.isActiveFor(activeTab)
                TopNavTabPill(
                    label = tab.navLabel(strings),
                    selected = isActive,
                    isGlass = true,
                    onClick = { onSelectTab(tab) },
                    modifier = activeTabModifier(isActive, activeTabFocusRequester)
                )
            }
        }

        TopBarUtilities(
            activeDownloadCount = activeDownloadCount,
            isSeerrConfigured = isSeerrConfigured,
            profileName = profileName,
            profileHasAlert = profileHasAlert,
            strings = strings,
            onOpenSearch = onOpenSearch,
            onOpenRequests = onOpenRequests,
            onOpenProfileMenu = onOpenProfileMenu
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DisneyTopHeaderBar(
    activeTab: HomeTab,
    activeDownloadCount: Int,
    isSeerrConfigured: Boolean,
    profileName: String,
    profileHasAlert: Boolean,
    strings: UiStrings,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenProfileMenu: () -> Unit,
    activeTabFocusRequester: FocusRequester? = null
) {
    val colors = SeerflixTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(colors.navBarBackground)
            .border(1.dp, colors.navBarBorder.copy(alpha = 0.45f))
            .focusProperties { enter = { activeTabFocusRequester ?: FocusRequester.Default } }
            .focusGroup()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "LUMEN+",
                color = colors.white,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NAV_DESTINATIONS.forEach { tab ->
                    val isActive = tab.isActiveFor(activeTab)
                    TopNavTabPill(
                        label = tab.navLabel(strings),
                        selected = isActive,
                        isGlass = false,
                        onClick = { onSelectTab(tab) },
                        modifier = activeTabModifier(isActive, activeTabFocusRequester)
                    )
                }
            }
        }

        TopBarUtilities(
            activeDownloadCount = activeDownloadCount,
            isSeerrConfigured = isSeerrConfigured,
            profileName = profileName,
            profileHasAlert = profileHasAlert,
            strings = strings,
            onOpenSearch = onOpenSearch,
            onOpenRequests = onOpenRequests,
            onOpenProfileMenu = onOpenProfileMenu
        )
    }
}

/** Search, Requests and the profile avatar — the only buttons on the right of the top bars. */
@Composable
private fun TopBarUtilities(
    activeDownloadCount: Int,
    isSeerrConfigured: Boolean,
    profileName: String,
    profileHasAlert: Boolean,
    strings: UiStrings,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TopBarUtilityIcon(Icons.Filled.Search, strings.navSearch, onClick = onOpenSearch)
        if (isSeerrConfigured) {
            TopBarUtilityIcon(
                Icons.Filled.CloudDownload,
                strings.navRequests,
                badgeCount = activeDownloadCount,
                onClick = onOpenRequests
            )
        }
        TopBarProfileButton(
            profileName = profileName,
            showDot = profileHasAlert,
            onClick = onOpenProfileMenu
        )
    }
}

@Composable
private fun TopBarProfileButton(profileName: String, showDot: Boolean, onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .border(if (focused) 2.dp else 0.dp, if (focused) colors.focusBorder else Color.Transparent, CircleShape)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(2.dp)
    ) {
        ProfileInitialAvatar(name = profileName, size = 30.dp, showDot = showDot)
    }
}

private fun activeTabModifier(isActive: Boolean, requester: FocusRequester?): Modifier =
    if (isActive && requester != null) Modifier.focusRequester(requester) else Modifier

private fun focusRestoreModifier(cardKey: String, restoreKey: String?, requester: FocusRequester?): Modifier =
    if (requester != null && restoreKey != null && cardKey == restoreKey) Modifier.focusRequester(requester) else Modifier

@Composable
private fun TopNavTabPill(
    label: String,
    selected: Boolean,
    isGlass: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    val corner = RoundedCornerShape(if (isGlass) 22.dp else 8.dp)

    Box(
        modifier = modifier
            .clip(corner)
            .background(
                when {
                    selected && isGlass -> colors.white
                    selected -> colors.accent
                    focused -> colors.secondaryButtonFocusedBg
                    else -> Color.Transparent
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, colors.focusBorder, corner)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            color = if (selected && isGlass) colors.deepBlack else colors.white,
            fontSize = 13.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun TopBarUtilityIcon(
    icon: ImageVector,
    label: String,
    badgeCount: Int = 0,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(
                when {
                    focused -> colors.secondaryButtonFocusedBg
                    highlighted -> colors.accent.copy(alpha = 0.25f)
                    else -> colors.navBarBackground
                }
            )
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = when {
                    focused -> colors.focusBorder
                    highlighted -> colors.accent
                    else -> colors.navBarBorder
                },
                shape = CircleShape
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (focused || highlighted) colors.white else colors.textPrimary,
            modifier = Modifier.size(17.dp)
        )
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(colors.downloadingBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = badgeCount.coerceAtMost(9).toString(),
                    color = colors.white,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun AvailabilityFilterBar(
    selectedFilter: AvailabilityFilter,
    isSeerrConfigured: Boolean = true,
    onSelectFilter: (AvailabilityFilter) -> Unit
) {
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spec.contentStartPaddingDp.dp, end = 28.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val options = if (isSeerrConfigured) {
            AvailabilityFilter.entries
        } else {
            listOf(AvailabilityFilter.ALL, AvailabilityFilter.WATCH_NOW)
        }
        options.forEach { filter ->
            key(filter) {
                FilterChip(
                    label = filter.localized(strings),
                    selected = selectedFilter == filter,
                    onClick = { onSelectFilter(filter) }
                )
            }
        }
    }
}

/** One chip style for every filter on Home: white when focused, outlined when selected. */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    emphasized: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(20.dp)
    val bg = when {
        focused -> colors.white
        selected -> colors.surfaceElevated
        emphasized -> colors.accent
        else -> colors.surface
    }
    val fg = if (focused) colors.deepBlack else colors.white
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(bg)
            .then(
                when {
                    focused -> Modifier
                    selected -> Modifier.border(1.5.dp, colors.white, shape)
                    else -> Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.5f), shape)
                }
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        } else if (selected && trailingIcon == null) {
            Icon(imageVector = Icons.Filled.Check, contentDescription = null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            color = fg,
            fontSize = 13.sp,
            fontWeight = if (selected || focused || emphasized) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(4.dp))
            Icon(imageVector = trailingIcon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenreFilterBar(
    mediaType: MediaType?,
    selectedGenre: GenreFilterOption,
    onSelectGenre: (GenreFilterOption) -> Unit,
    bringIntoViewSpec: BringIntoViewSpec? = null,
    browseAllLabel: String? = null,
    onBrowseAll: (() -> Unit)? = null,
    onOpenLibraries: (() -> Unit)? = null,
    onOpenCollections: (() -> Unit)? = null,
    selectedFilter: AvailabilityFilter = AvailabilityFilter.ALL,
    isSeerrConfigured: Boolean = false,
    onSelectFilter: (AvailabilityFilter) -> Unit = {}
) {
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    var showCategoriesDialog by remember { mutableStateOf(false) }
    val isGenreActive = selectedGenre.id != GenreFilterOption.ALL_GENRES.first().id
    val categoriesLabel = if (isGenreActive) selectedGenre.localizedLabel(strings) else strings.homeCategories

    WithBringIntoViewSpec(bringIntoViewSpec) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = spec.contentStartPaddingDp.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item(key = "categories_picker", contentType = "action_chip") {
            FilterChip(
                label = categoriesLabel,
                selected = isGenreActive,
                trailingIcon = Icons.Filled.KeyboardArrowDown,
                onClick = { showCategoriesDialog = true }
            )
        }
        if (onBrowseAll != null && browseAllLabel != null) {
            item(key = "browse_all", contentType = "action_chip") {
                FilterChip(
                    label = browseAllLabel,
                    selected = false,
                    emphasized = true,
                    icon = Icons.Filled.GridView,
                    onClick = onBrowseAll
                )
            }
        }
        if (onOpenLibraries != null) {
            item(key = "libraries", contentType = "action_chip") {
                FilterChip(
                    label = strings.actionLibraries,
                    selected = false,
                    icon = Icons.Filled.Folder,
                    onClick = onOpenLibraries
                )
            }
        }
        if (onOpenCollections != null) {
            item(key = "collections", contentType = "action_chip") {
                FilterChip(
                    label = strings.navCollections,
                    selected = false,
                    icon = Icons.Filled.VideoLibrary,
                    onClick = onOpenCollections
                )
            }
        }
        // Availability is one chip that cycles All -> Watch Now -> Requestable, instead of its own row.
        item(key = "availability", contentType = "action_chip") {
            val options = if (isSeerrConfigured) {
                AvailabilityFilter.entries
            } else {
                listOf(AvailabilityFilter.ALL, AvailabilityFilter.WATCH_NOW)
            }
            FilterChip(
                label = if (selectedFilter == AvailabilityFilter.ALL) strings.homeFilterAllTitles else selectedFilter.localized(strings),
                selected = selectedFilter != AvailabilityFilter.ALL,
                icon = Icons.Filled.FilterList,
                onClick = {
                    val next = options[(options.indexOf(selectedFilter) + 1) % options.size]
                    onSelectFilter(next)
                }
            )
        }
    }
    }

    if (showCategoriesDialog) {
        CategoriesPickerDialog(
            mediaType = mediaType,
            selectedGenre = selectedGenre,
            onSelect = { genre ->
                showCategoriesDialog = false
                onSelectGenre(genre)
            },
            onDismiss = { showCategoriesDialog = false }
        )
    }
}

/** Netflix-style translucent category picker overlay for Movies and TV Shows. */
@Composable
private fun CategoriesPickerDialog(
    mediaType: MediaType?,
    selectedGenre: GenreFilterOption,
    onSelect: (GenreFilterOption) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val categories = remember(mediaType) { GenreFilterOption.forMediaType(mediaType) }
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val selectedFocusRequester = remember { FocusRequester() }
    val selectedIndex = remember(categories, selectedGenre.id) {
        categories.indexOfFirst { it.id == selectedGenre.id }.coerceAtLeast(0)
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (selectedIndex - 2).coerceAtLeast(0))

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { selectedFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 520.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = strings.homeCategoriesTitle,
                    color = colors.white,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (mediaType == MediaType.MOVIE) strings.navMovies else strings.navShows,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(Modifier.height(14.dp))
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(categories, key = { _, g -> g.id }) { idx, genre ->
                    val isSelected = genre.id == selectedGenre.id
                    var focused by remember { mutableStateOf(false) }
                    val itemShape = RoundedCornerShape(8.dp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (idx == selectedIndex) Modifier.focusRequester(selectedFocusRequester) else Modifier)
                            .clip(itemShape)
                            .background(
                                when {
                                    focused -> colors.white
                                    isSelected -> colors.surfaceElevated
                                    else -> Color.Transparent
                                }
                            )
                            .then(
                                if (isSelected && !focused) Modifier.border(1.dp, colors.badgeBorder, itemShape)
                                else Modifier
                            )
                            .onFocusChanged { focused = it.isFocused || it.hasFocus }
                            .clickable { onSelect(genre) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = genre.localizedLabel(strings),
                            color = if (focused) colors.deepBlack else colors.white,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = if (focused) colors.deepBlack else colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Re-provides the platform [BringIntoViewSpec] for horizontal rows nested in the Home list. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WithBringIntoViewSpec(spec: BringIntoViewSpec?, content: @Composable () -> Unit) {
    if (spec == null) {
        content()
    } else {
        CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
    }
}

@Composable
private fun PosterOptionsDialog(
    item: UnifiedMediaItem,
    has4kServer: Boolean,
    canSubmitRequests: Boolean,
    isKidsLocked: Boolean,
    onPlay: () -> Unit,
    onRequestHd: () -> Unit,
    onRequest4k: () -> Unit,
    onToggleMyList: () -> Unit,
    onToggleWatched: () -> Unit,
    onViewDetails: () -> Unit,
    onRate: (com.seerflix.tv.core.data.TasteRating?) -> Unit = {},
    onNotInterested: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val firstActionFocusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        // Must run inside the dialog's own composition: from the parent it fires before the
        // dialog window's content is attached and silently fails, leaving the dialog unfocused.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstActionFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(400.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                color = colors.white,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildList {
                item.releaseYear?.let(::add)
                add(if (item.mediaType == MediaType.MOVIE) strings.commonMovie else strings.commonTvSeries)
                when {
                    item.isPlayable -> add(strings.commonInLibrary)
                    item.overallAvailability.isRequestedOrDownloading -> add(strings.homeRequestedDownloading)
                    item.canRequestHd -> add(strings.homeAvailableToRequest)
                }
            }.joinToString(" • ")
            Text(subtitle, color = colors.textSecondary, fontSize = 13.sp)
            Spacer(Modifier.height(4.dp))
            var assignedFirst = false
            fun firstModifier(): Modifier = if (!assignedFirst) {
                assignedFirst = true
                Modifier.focusRequester(firstActionFocusRequester)
            } else {
                Modifier
            }
            if (item.isPlayable && item.jellyfinId != null) {
                ContextActionRow(Icons.Filled.PlayArrow, strings.actionPlay, onPlay, modifier = firstModifier())
                ContextActionRow(
                    icon = if (item.isWatched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    label = if (item.isWatched) strings.actionMarkUnwatched else strings.actionMarkWatched,
                    onClick = onToggleWatched
                )
            }
            if (item.canRequestHd && canSubmitRequests) {
                ContextActionRow(Icons.Filled.CloudDownload, strings.actionRequestHd, onRequestHd, modifier = firstModifier())
            }
            if (item.canRequest4k && has4kServer && canSubmitRequests) {
                ContextActionRow(Icons.Filled.Hd, strings.actionRequest4k, onRequest4k, modifier = firstModifier())
            }
            if (isKidsLocked && (item.canRequestHd || item.canRequest4k)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(strings.labelKidsLocked, color = colors.textMuted, fontSize = 12.sp)
                }
            }
            ContextActionRow(
                icon = if (item.isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                label = if (item.isInMyList) strings.homeRemoveFromMyList else strings.homeAddToMyList,
                onClick = onToggleMyList,
                modifier = firstModifier()
            )
            ContextActionRow(Icons.Filled.Info, strings.homeDetailsCastTrailers, onViewDetails)
            // Netflix-style ratings: they steer Top Picks and "Because you watched".
            ContextActionRow(Icons.Filled.ThumbUp, strings.rateLike, { onRate(com.seerflix.tv.core.data.TasteRating.LIKE) })
            ContextActionRow(Icons.Filled.Favorite, strings.rateLove, { onRate(com.seerflix.tv.core.data.TasteRating.LOVE) })
            ContextActionRow(Icons.Filled.ThumbDown, strings.rateNotForMe, { onRate(com.seerflix.tv.core.data.TasteRating.DISLIKE) })
            ContextActionRow(Icons.Filled.Block, strings.actionNotInterested, onNotInterested)
        }
    }
}

@Composable
private fun ContinueWatchingOptionsDialog(
    item: UnifiedMediaItem,
    onResume: () -> Unit,
    onViewDetails: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val strings = AppStrings.current
    val firstActionFocusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstActionFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                color = colors.white,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.episodeLabel?.let {
                Text(it, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            // An unstarted Next Up episode has nothing to resume or remove (clearing a position
            // that is already 0 doesn't stick: Jellyfin lists it again on the next refresh).
            val isUnstartedNextUp = item.isNextUp && item.resumePositionTicks <= 0L
            ContextActionRow(
                icon = Icons.Filled.PlayArrow,
                label = if (isUnstartedNextUp) strings.actionPlay else strings.actionResume,
                onClick = onResume,
                modifier = Modifier.focusRequester(firstActionFocusRequester)
            )
            ContextActionRow(Icons.Filled.Info, strings.actionMoreInfo, onViewDetails)
            if (!isUnstartedNextUp) {
                ContextActionRow(Icons.Filled.DeleteOutline, strings.homeRemoveFromContinue, onRemove)
            }
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    focused -> colors.surfaceElevated
                    highlighted -> colors.accent.copy(alpha = 0.22f)
                    else -> colors.surface
                }
            )
            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.white, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            color = colors.white,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CatalogRowSection(
    row: CatalogRow,
    onItemFocus: (UnifiedMediaItem) -> Unit,
    onItemClick: (UnifiedMediaItem) -> Unit,
    onContinueWatchingOptions: (UnifiedMediaItem) -> Unit,
    onPosterOptions: (UnifiedMediaItem) -> Unit,
    restoreFocusKey: String? = null,
    restoreFocusRequester: FocusRequester? = null,
    onCardFocused: (String) -> Unit = {},
    rowBringIntoViewSpec: BringIntoViewSpec? = null,
    firstCardFocusRequester: FocusRequester? = null
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    @Suppress("UNUSED_PARAMETER")
    fun cardModifier(cardKey: String, index: Int): Modifier =
        focusRestoreModifier(cardKey, restoreFocusKey, restoreFocusRequester)
    val horizontalPad = spec.contentStartPaddingDp.dp
    // When the Home list prefetches this row ahead of a vertical scroll, compose a full screen
    // width of cards (default is 2), so the row doesn't compose 4-5 cards in the frame it scrolls in.
    val rowListState = rememberLazyListState(
        prefetchStrategy = remember { LazyListPrefetchStrategy(nestedPrefetchItemCount = ROW_NESTED_PREFETCH_COUNT) }
    )
    // The same title can legitimately be returned twice for a row (e.g. merged Jellyfin + Seerr
    // results); duplicate LazyRow keys crash with "Key ... was already used".
    val rowItems = remember(row.items) { row.items.distinctBy { it.id } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        val strings = AppStrings.current
        Text(
            text = localizedHomeRowTitle(row.id, row.title, strings),
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = horizontalPad, end = horizontalPad, bottom = 8.dp)
        )

        WithBringIntoViewSpec(rowBringIntoViewSpec) {
        LazyRow(
            state = rowListState,
            // Up/Down into a row you've visited lands on the card you left in it. The first row's
            // requester targets the row itself (not a card, whose node may be recycled), so
            // focusing it enters the row at its remembered card, or the first one.
            modifier = (if (firstCardFocusRequester != null) Modifier.focusRequester(firstCardFocusRequester) else Modifier)
                .focusRestorer(),
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(if (spec.preset == UiPreset.APPLE_TV) 18.dp else 14.dp)
        ) {
            when (row.style) {
                RowStyle.CONTINUE_WATCHING -> {
                    itemsIndexed(
                        items = rowItems,
                        key = { _, it -> "${row.id}_${it.id}" },
                        contentType = { _, _ -> "cw_card" }
                    ) { idx, item ->
                        val cardKey = "${row.id}_${item.id}"
                        ContinueWatchingCard(
                            item = item,
                            onFocus = {
                                onCardFocused(cardKey)
                                onItemFocus(item)
                            },
                            onClick = { onItemClick(item) },
                            onOptionsMenu = { onContinueWatchingOptions(item) },
                            modifier = cardModifier(cardKey, idx)
                        )
                    }
                }
                RowStyle.TOP_10 -> {
                    itemsIndexed(
                        items = rowItems,
                        key = { _, it -> "${row.id}_${it.id}" },
                        contentType = { _, _ -> "top10_card" }
                    ) { idx, item ->
                        val cardKey = "${row.id}_${item.id}"
                        Top10PosterCard(
                            rank = idx + 1,
                            item = item,
                            onFocus = {
                                onCardFocused(cardKey)
                                onItemFocus(item)
                            },
                            onClick = { onItemClick(item) },
                            onOptionsMenu = { onPosterOptions(item) },
                            modifier = cardModifier(cardKey, idx)
                        )
                    }
                }
                else -> {
                    itemsIndexed(
                        items = rowItems,
                        key = { _, it -> "${row.id}_${it.id}" },
                        contentType = { _, _ -> "poster_card" }
                    ) { idx, item ->
                        val cardKey = "${row.id}_${item.id}"
                        NetflixPosterCard(
                            item = item,
                            onFocus = {
                                onCardFocused(cardKey)
                                onItemFocus(item)
                            },
                            onClick = { onItemClick(item) },
                            onOptionsMenu = { onPosterOptions(item) },
                            modifier = cardModifier(cardKey, idx)
                        )
                    }
                }
            }
        }
        }
    }
}

/** ~one 1080p screen width of the narrowest (Netflix portrait) cards plus a partial one. */
private const val ROW_NESTED_PREFETCH_COUNT = 7

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NetflixNavRail(
    activeTab: HomeTab,
    activeDownloadCount: Int,
    isSeerrConfigured: Boolean,
    profileName: String,
    profileHasAlert: Boolean,
    strings: UiStrings,
    activeTabFocusRequester: FocusRequester? = null,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenProfileMenu: () -> Unit
) {
    val colors = SeerflixTokens.colors
    // Expanded state lives here (not in HomeContent) so a rail focus change recomposes only the rail.
    var expanded by remember { mutableStateOf(false) }
    // The rail now overlays the content, so its translucent nav colour let rows and chips show
    // through the labels; use the same colour flattened onto the app background (fully opaque).
    val railBackground = remember(colors.navBarBackground, colors.background) {
        colors.navBarBackground.compositeOver(colors.background)
    }
    val expandedScrim = remember(railBackground) {
        Brush.horizontalGradient(listOf(railBackground, Color.Transparent))
    }
    Row(modifier = Modifier.fillMaxHeight()) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (expanded) NETFLIX_RAIL_EXPANDED_WIDTH else NETFLIX_RAIL_COLLAPSED_WIDTH)
            .background(railBackground)
            .onFocusChanged { focusState ->
                if (expanded != focusState.hasFocus) expanded = focusState.hasFocus
            }
            // Pressing Left from anywhere in the content lands on the tab you are on, not on
            // whichever icon happens to be level with the focused card.
            .focusProperties {
                enter = { activeTabFocusRequester ?: FocusRequester.Default }
            }
            .focusGroup()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "L",
                    color = colors.accent,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black
                )
                if (expanded) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "LUMEN",
                        color = colors.accent,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            NavRailItem(
                icon = Icons.Filled.Search,
                label = strings.navSearch,
                expanded = expanded,
                selected = false,
                onClick = onOpenSearch
            )
            NAV_DESTINATIONS.forEach { tab ->
                val isActive = tab.isActiveFor(activeTab)
                NavRailItem(
                    icon = tab.navIcon(),
                    label = tab.navLabel(strings),
                    expanded = expanded,
                    selected = isActive,
                    onClick = { onSelectTab(tab) },
                    modifier = activeTabModifier(isActive, activeTabFocusRequester)
                )
            }
            if (isSeerrConfigured) {
                NavRailItem(
                    icon = Icons.Filled.CloudDownload,
                    label = strings.navRequests,
                    expanded = expanded,
                    selected = false,
                    badgeCount = activeDownloadCount,
                    onClick = onOpenRequests
                )
            }
        }

        // Everything that is an action rather than a place (Watch Together, Customize, Appearance,
        // Refresh, Update, Settings, Switch Profile) lives in the profile menu.
        NavRailItem(
            icon = Icons.Filled.AccountCircle,
            label = profileName,
            expanded = expanded,
            selected = false,
            showDot = profileHasAlert,
            onClick = onOpenProfileMenu
        )
    }
    if (expanded) {
        // The expanded rail floats over the rows; a short fade keeps its edge readable.
        Spacer(
            modifier = Modifier
                .fillMaxHeight()
                .width(96.dp)
                .background(expandedScrim)
        )
    }
    }
}

/** Places in the nav, in order. Collections lives inside Movies (chip on the Movies tab). */
private val NAV_DESTINATIONS = listOf(
    HomeTab.HOME,
    HomeTab.NEW_AND_POPULAR,
    HomeTab.SHOWS,
    HomeTab.MOVIES,
    HomeTab.LIVE_TV,
    HomeTab.MY_LIST
)

/** Collections is reached from Movies, so Movies stays highlighted while it is open. */
private fun HomeTab.isActiveFor(active: HomeTab): Boolean =
    this == active || (this == HomeTab.MOVIES && active == HomeTab.COLLECTIONS)

private fun HomeTab.navIcon(): ImageVector = when (this) {
    HomeTab.HOME -> Icons.Filled.Home
    HomeTab.NEW_AND_POPULAR -> Icons.Filled.Whatshot
    HomeTab.SHOWS -> Icons.Filled.Tv
    HomeTab.MOVIES -> Icons.Filled.Movie
    HomeTab.COLLECTIONS -> Icons.Filled.VideoLibrary
    HomeTab.LIVE_TV -> Icons.Filled.LiveTv
    HomeTab.MY_LIST -> Icons.Filled.Bookmark
}

private fun HomeTab.navLabel(strings: UiStrings): String = when (this) {
    HomeTab.HOME -> strings.navHome
    HomeTab.SHOWS -> strings.navShows
    HomeTab.MOVIES -> strings.navMovies
    HomeTab.COLLECTIONS -> strings.navCollections
    HomeTab.LIVE_TV -> strings.navLiveTv
    HomeTab.NEW_AND_POPULAR -> strings.navNewAndPopular
    HomeTab.MY_LIST -> strings.navMyList
}

/**
 * The profile menu: one place for everything in the nav that is an action rather than a
 * destination, shared by all three presets.
 */
@Composable
private fun ProfileMenuDialog(
    profileName: String,
    isKidsMode: Boolean,
    activeSyncPlayName: String?,
    hasUpdate: Boolean,
    onOpenLibraries: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSyncPlay: () -> Unit,
    onOpenCustomizeHome: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenUpdate: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val firstActionFocusRequester = remember { FocusRequester() }
    fun run(action: () -> Unit): () -> Unit = {
        onDismiss()
        action()
    }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstActionFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileInitialAvatar(name = profileName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = profileName,
                        color = colors.white,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val status = listOfNotNull(
                        strings.homeKidsModeActive.takeIf { isKidsMode },
                        activeSyncPlayName?.let { strings.homeSyncPlayChip.format(it) }
                    ).joinToString(" · ")
                    if (status.isNotEmpty()) {
                        Text(status, color = colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            if (hasUpdate) {
                ContextActionRow(
                    Icons.Filled.SystemUpdate,
                    strings.homeAppUpdate,
                    run(onOpenUpdate),
                    modifier = Modifier.focusRequester(firstActionFocusRequester),
                    highlighted = true
                )
            }
            ContextActionRow(
                Icons.Filled.SwitchAccount,
                strings.profileMenuSwitchProfile,
                run(onSwitchProfile),
                modifier = if (hasUpdate) Modifier else Modifier.focusRequester(firstActionFocusRequester)
            )
            ContextActionRow(Icons.Filled.Folder, strings.actionLibraries, run(onOpenLibraries))
            ContextActionRow(
                Icons.Filled.Groups,
                activeSyncPlayName?.let { strings.homeSyncPlayChip.format(it) } ?: strings.actionWatchTogether,
                run(onOpenSyncPlay),
                highlighted = activeSyncPlayName != null
            )
            ContextActionRow(Icons.Filled.Tune, strings.actionCustomizeHome, run(onOpenCustomizeHome))
            ContextActionRow(Icons.Filled.Palette, strings.homeAppearance, run(onOpenAppearance))
            ContextActionRow(Icons.Filled.Refresh, strings.commonRefresh, run(onRefresh))
            ContextActionRow(Icons.Filled.Settings, strings.navSettings, run(onOpenSettings))
        }
    }
}

/** Round avatar with the profile's first letter, used by the nav and the profile menu. */
@Composable
private fun ProfileInitialAvatar(name: String, size: androidx.compose.ui.unit.Dp, showDot: Boolean = false) {
    val colors = SeerflixTokens.colors
    Box(modifier = Modifier.size(size)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(colors.accent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase().ifEmpty { "?" },
                color = colors.white,
                fontSize = (size.value * 0.45f).sp,
                fontWeight = FontWeight.Bold
            )
        }
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(size * 0.3f)
                    .clip(CircleShape)
                    .background(colors.downloadingBlue)
                    .border(1.5.dp, colors.background, CircleShape)
            )
        }
    }
}

@Composable
private fun NavRailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    badgeCount: Int = 0,
    showDot: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> colors.white
                    selected -> colors.surface
                    else -> Color.Transparent
                }
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(if (selected && !focused) colors.accent else Color.Transparent)
        )
        Spacer(Modifier.width(8.dp))
        val tint = when {
            focused -> colors.deepBlack
            selected -> colors.white
            else -> colors.textSecondary
        }
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .offset(x = 5.dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(colors.downloadingBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (badgeCount > 9) "9+" else badgeCount.toString(),
                        color = colors.white,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else if (showDot) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(colors.downloadingBlue)
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                color = tint,
                fontSize = 15.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CollectionsHubContent(
    collections: List<CollectionSummary>,
    onOpenCollection: (CollectionSummary) -> Unit
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    val spec = SeerflixTokens.spec
    val topPadding = if (spec.navStyle == NavLayoutStyle.TOP_GLASS_PILL) 92.dp else 28.dp
    val cardCorner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)

    if (collections.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.VideoLibrary,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(strings.homeNoCollections, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings.homeNoCollectionsHint,
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    val rows = remember(collections) { collections.chunked(4) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = topPadding, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item(key = "collections_header", contentType = "header") {
            Column {
                Text(
                    text = strings.navCollections,
                    color = colors.white,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.homeCollectionsSubtitle,
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        items(rows, contentType = { "collection_row" }) { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                chunk.forEach { col ->
                    var focused by remember { mutableStateOf(false) }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(cardCorner)
                            .background(if (focused) colors.surfaceElevated else colors.surface)
                            .then(
                                if (focused) Modifier.border(2.dp, colors.focusBorder, cardCorner)
                                else Modifier.border(1.dp, colors.badgeBorder, cardCorner)
                            )
                            .onFocusChanged { focused = it.isFocused || it.hasFocus }
                            .clickable { onOpenCollection(col) }
                            .padding(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape((spec.cardCornerRadiusDp - 2).coerceAtLeast(4).dp))
                                .background(colors.surfaceElevated)
                        ) {
                            SizedAsyncImage(
                                url = col.backdropUrl ?: col.posterUrl,
                                contentDescription = col.name,
                                // ~quarter of the canvas width; exact size isn't needed for Crop.
                                width = 240.dp,
                                height = 135.dp,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.navBarBackground)
                                    .padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = UpcomingDateFormatter.collectionCompletionLabel(col.ownedCount, col.totalCount, AppStrings.current),
                                    color = colors.white,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = col.name,
                            color = colors.white,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                repeat(4 - chunk.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsRowSection(
    playlists: List<com.seerflix.tv.core.model.PlaylistSummary>,
    rowBringIntoViewSpec: BringIntoViewSpec?,
    onOpenPlaylist: (com.seerflix.tv.core.model.PlaylistSummary) -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    val horizontalPad = spec.contentStartPaddingDp.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
    ) {
        Text(
            text = strings.playlistsSectionTitle,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = horizontalPad, end = horizontalPad, bottom = 8.dp)
        )
        WithBringIntoViewSpec(rowBringIntoViewSpec) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = horizontalPad),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    var focused by remember { mutableStateOf(false) }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .width(240.dp)
                            .clip(corner)
                            .background(if (focused) colors.surfaceElevated else colors.surface)
                            .border(
                                if (focused) 2.dp else 1.dp,
                                if (focused) colors.focusBorder else colors.badgeBorder,
                                corner
                            )
                            .onFocusChanged { focused = it.isFocused || it.hasFocus }
                            .clickable { onOpenPlaylist(pl) }
                            .padding(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.accent.copy(alpha = 0.22f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlaylistPlay,
                                contentDescription = null,
                                tint = colors.accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = pl.name,
                                color = colors.white,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = strings.playlistItemCount.format(pl.itemCount),
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistDetailsDialog(
    playlist: com.seerflix.tv.core.model.PlaylistSummary,
    isLoading: Boolean,
    onPlayItem: (UnifiedMediaItem) -> Unit,
    onPlayQueue: (List<com.seerflix.tv.core.player.QueueEntry>, Boolean) -> Unit,
    onRemoveItem: (entryId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val closeFocusRequester = remember { FocusRequester() }
    val playableEntries = remember(playlist.items) {
        playlist.items.mapNotNull { item ->
            item.jellyfinId?.let { com.seerflix.tv.core.player.QueueEntry(itemId = it, title = item.title) }
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { closeFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(680.dp)
                .clip(dialogCorner)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogCorner)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        color = colors.white,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = strings.playlistItemCount.format(playlist.items.size.coerceAtLeast(playlist.itemCount)),
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (playableEntries.isNotEmpty()) {
                        SmallTextButton(
                            text = strings.actionPlayAll,
                            isPrimary = true,
                            onClick = { onPlayQueue(playableEntries, false) }
                        )
                        SmallTextButton(
                            text = strings.actionShuffle,
                            isPrimary = false,
                            onClick = { onPlayQueue(playableEntries, true) }
                        )
                    }
                    var closeFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .focusRequester(closeFocusRequester)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (closeFocused) colors.secondaryButtonFocusedBg else colors.secondaryButtonBg)
                            .then(if (closeFocused) Modifier.border(2.dp, colors.focusBorder, CircleShape) else Modifier)
                            .onFocusChanged { closeFocused = it.isFocused || it.hasFocus }
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = strings.commonClose, tint = colors.white, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (isLoading && playlist.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (playlist.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text(strings.playlistsEmpty, color = colors.textSecondary, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(playlist.items, key = { idx, item -> "${item.playlistItemId ?: item.id}_$idx" }) { _, item ->
                        var rowFocused by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (rowFocused) colors.surfaceElevated else colors.surface)
                                .border(
                                    if (rowFocused) 2.dp else 1.dp,
                                    if (rowFocused) colors.focusBorder else colors.badgeBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { rowFocused = it.isFocused || it.hasFocus }
                                .clickable { onPlayItem(item) }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    color = colors.white,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sub = listOfNotNull(item.releaseYear, item.formattedRuntime).joinToString(" · ")
                                if (sub.isNotBlank()) {
                                    Text(sub, color = colors.textSecondary, fontSize = 11.sp)
                                }
                            }
                            item.playlistItemId?.takeIf { it.isNotBlank() }?.let { entryId ->
                                Spacer(Modifier.width(8.dp))
                                SmallIconAction(
                                    icon = Icons.Filled.DeleteOutline,
                                    label = strings.actionRemoveFromPlaylist,
                                    onClick = { onRemoveItem(entryId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrariesPickerDialog(
    libraries: List<JellyfinLibraryView>,
    onSelectLibrary: (JellyfinLibraryView) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(libraries.isNotEmpty()) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(460.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = strings.librariesDialogTitle,
                color = colors.white,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            if (libraries.isEmpty()) {
                Text(strings.librariesEmpty, color = colors.textSecondary, fontSize = 14.sp)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(libraries, key = { _, lib -> lib.id }) { idx, lib ->
                        val icon = when (lib.collectionType?.lowercase()) {
                            "movies" -> Icons.Filled.Movie
                            "tvshows" -> Icons.Filled.Tv
                            else -> Icons.Filled.Folder
                        }
                        ContextActionRow(
                            icon = icon,
                            label = lib.displayName,
                            onClick = { onSelectLibrary(lib) },
                            modifier = if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CollectionDetailsDialog(
    collection: CollectionSummary,
    isLoading: Boolean,
    onPlayItem: (UnifiedMediaItem) -> Unit,
    onPlayQueue: (List<com.seerflix.tv.core.player.QueueEntry>, Boolean) -> Unit = { _, _ -> },
    onOpenItemDetails: (UnifiedMediaItem) -> Unit,
    onRequestItem: (UnifiedMediaItem) -> Unit,
    onRequestAllMissing: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val buttonCorner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)
    val parts = remember(collection.items) { collection.items.distinctBy { it.id } }
    val playableEntries = remember(parts) {
        parts.mapNotNull { item ->
            item.jellyfinId?.let { com.seerflix.tv.core.player.QueueEntry(itemId = it, title = item.title) }
        }
    }
    val missingCount = remember(parts) {
        parts.count { !it.isPlayable && it.canRequestHd }
    }
    val closeFocusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        // Without an initial focus target the dialog opened with nothing focused.
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { closeFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(680.dp)
                .clip(dialogCorner)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogCorner)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = collection.name,
                        color = colors.white,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = UpcomingDateFormatter.collectionCompletionLabel(collection.ownedCount, collection.totalCount, AppStrings.current),
                        color = colors.matchGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (playableEntries.size > 1) {
                        SmallTextButton(
                            text = strings.actionPlayAll,
                            isPrimary = true,
                            onClick = { onPlayQueue(playableEntries, false) }
                        )
                        SmallTextButton(
                            text = strings.actionShuffle,
                            isPrimary = false,
                            onClick = { onPlayQueue(playableEntries, true) }
                        )
                    }
                    if (missingCount > 0) {
                        var reqAllFocused by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(buttonCorner)
                                .background(if (reqAllFocused) colors.accent else colors.accentDark)
                                .then(if (reqAllFocused) Modifier.border(2.dp, colors.focusBorder, buttonCorner) else Modifier)
                                .onFocusChanged { reqAllFocused = it.isFocused || it.hasFocus }
                                .clickable(onClick = onRequestAllMissing)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null, tint = colors.white, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(strings.detailsRequestMissing.format(missingCount), color = colors.white, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    var closeFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .focusRequester(closeFocusRequester)
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(if (closeFocused) colors.secondaryButtonFocusedBg else colors.secondaryButtonBg)
                            .then(if (closeFocused) Modifier.border(2.dp, colors.focusBorder, CircleShape) else Modifier)
                            .onFocusChanged { closeFocused = it.isFocused || it.hasFocus }
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = strings.commonClose, tint = colors.white, modifier = Modifier.size(18.dp))
                    }
                }
            }

            if (collection.overview.isNotBlank()) {
                Text(
                    text = collection.overview,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isLoading && parts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (parts.isEmpty()) {
                // Load finished (or failed) with nothing to list: say so instead of a blank box.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = strings.homeCollectionEmpty,
                        color = colors.textSecondary,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(parts, key = { it.id }) { part ->
                        var rowFocused by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (rowFocused) colors.surfaceElevated else colors.surface)
                                .then(if (rowFocused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(8.dp)) else Modifier)
                                .onFocusChanged { rowFocused = it.isFocused || it.hasFocus }
                                .clickable {
                                    when {
                                        part.isPlayable -> onPlayItem(part)
                                        part.canRequestHd -> onRequestItem(part)
                                        else -> onOpenItemDetails(part)
                                    }
                                }
                                .padding(10.dp)
                        ) {
                            SizedAsyncImage(
                                url = part.posterUrl ?: part.backdropUrl,
                                contentDescription = part.title,
                                width = 56.dp,
                                height = 84.dp,
                                modifier = Modifier
                                    .width(56.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.surfaceElevated)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = part.title,
                                    color = colors.white,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = listOfNotNull(
                                        part.releaseYear,
                                        when {
                                            part.isPlayable -> strings.commonInLibrary
                                            part.overallAvailability == AvailabilityTier.PROCESSING -> strings.commonDownloading
                                            part.overallAvailability == AvailabilityTier.PENDING -> strings.commonRequested
                                            part.canRequestHd -> strings.homeMissingFromLibrary
                                            else -> null
                                        }
                                    ).joinToString(" • "),
                                    color = if (part.isPlayable) colors.matchGreen else colors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            val actionLabel = when {
                                part.isPlayable -> strings.actionPlay
                                part.overallAvailability.isRequestedOrDownloading -> strings.commonRequested
                                part.canRequestHd -> "+ " + strings.actionRequestMore
                                else -> strings.homeDetails
                            }
                            Box(
                                modifier = Modifier
                                    .clip(buttonCorner)
                                    .background(
                                        when {
                                            part.isPlayable -> colors.primaryButtonBg
                                            part.canRequestHd -> colors.accent
                                            else -> colors.secondaryButtonBg
                                        }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = actionLabel,
                                    color = if (part.isPlayable) colors.primaryButtonFg else colors.white,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadyRequestAlertsBanner(
    alerts: List<ReadyRequestAlert>,
    onPlayAlert: (ReadyRequestAlert) -> Unit,
    onDismissAlert: (Int) -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val horizontalPad = spec.contentStartPaddingDp.dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = horizontalPad, end = 28.dp, top = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        alerts.take(2).forEach { alert ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.matchGreen.copy(alpha = 0.14f))
                    .border(1.dp, colors.matchGreen.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = colors.matchGreen,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = strings.homeJustArrived.format(alert.title),
                            color = colors.white,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = strings.homeJustArrivedBody,
                            color = colors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    var playFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (playFocused) colors.white else colors.matchGreen)
                            .then(if (playFocused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(6.dp)) else Modifier)
                            .onFocusChanged { playFocused = it.isFocused || it.hasFocus }
                            .clickable { onPlayAlert(alert) }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = strings.homeFilterWatchNow,
                            color = colors.deepBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    var dismissFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (dismissFocused) colors.surfaceElevated else colors.surface)
                            .then(if (dismissFocused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(6.dp)) else Modifier)
                            .onFocusChanged { dismissFocused = it.isFocused || it.hasFocus }
                            .clickable { onDismissAlert(alert.requestId) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = strings.homeDismiss,
                            color = colors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun LiveTvGuideContent(
    channels: List<LiveTvChannel>,
    recordings: List<UnifiedMediaItem>,
    onTuneChannel: (String) -> Unit,
    onOpenRecording: (UnifiedMediaItem) -> Unit,
    scheduled: List<ScheduledRecording> = emptyList(),
    dvrFeedback: String? = null,
    onOpenChannelMenu: (LiveTvChannel, com.seerflix.tv.core.model.LiveTvProgram?) -> Unit = { _, _ -> },
    onToggleChannelFavorite: (LiveTvChannel) -> Unit = {},
    onCancelScheduled: (ScheduledRecording) -> Unit = {}
) {
    val strings = AppStrings.current
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val topPadding = if (spec.navStyle == NavLayoutStyle.TOP_GLASS_PILL) 92.dp else 28.dp
    val cardCorner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)

    if (channels.isEmpty() && recordings.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(14.dp))
                Text(strings.liveNoChannels, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = strings.liveNoChannelsHint,
                    color = colors.textSecondary,
                    fontSize = 14.sp
                )
            }
        }
        return
    }

    var isGridMode by rememberSaveable { mutableStateOf(false) }
    var selectedFilter by rememberSaveable { mutableStateOf(com.seerflix.tv.core.model.LiveTvFilter.ALL) }
    // "Now" moves on while the guide is open: tick every minute so the now-marker, the current
    // programme highlight and the first window follow the clock.
    val nowMs by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    val baseWindowStartMs = com.seerflix.tv.core.data.LiveTvEpgHelper.floorToHalfHour(nowMs)
    var windowOffsetHours by rememberSaveable { mutableStateOf(0) }
    val windowStartMs = baseWindowStartMs + windowOffsetHours * 3_600_000L
    val timeSlots = remember(windowStartMs) { com.seerflix.tv.core.data.LiveTvEpgHelper.timeSlots(windowStartMs) }
    val epgContext = androidx.compose.ui.platform.LocalContext.current
    // App language + the TV's 12/24-hour setting (not a hard-coded "HH:mm" / device locale).
    val slotTimeFmt = remember(strings.languageCode) {
        val locale = java.util.Locale.forLanguageTag(strings.languageCode)
        val skeleton = if (android.text.format.DateFormat.is24HourFormat(epgContext)) "Hm" else "hm"
        java.time.format.DateTimeFormatter
            .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, skeleton), locale)
            .withZone(java.time.ZoneId.systemDefault())
    }
    val dayFmt = remember(strings.languageCode) {
        val locale = java.util.Locale.forLanguageTag(strings.languageCode)
        java.time.format.DateTimeFormatter
            .ofPattern(android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEdMMM"), locale)
            .withZone(java.time.ZoneId.systemDefault())
    }
    // Rows shown under the current filter stay until the filter changes: un-favouriting the
    // focused channel under "Favorites" must not pull the row (and focus) out from under the user.
    val shownUnderFilter = remember(selectedFilter) { mutableSetOf<String>() }
    val filteredChannels = remember(channels, selectedFilter) {
        channels.filter { selectedFilter.matches(it) || it.id in shownUnderFilter }
            .also { list -> list.forEach { shownUnderFilter += it.id } }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = topPadding, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "live_tv_header") {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = strings.liveGuideTitle,
                            color = colors.white,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = strings.liveChannelCount.format(filteredChannels.size),
                            color = colors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveTvChipButton(
                            label = strings.liveViewList,
                            icon = Icons.Filled.ViewList,
                            selected = !isGridMode,
                            onClick = { isGridMode = false }
                        )
                        LiveTvChipButton(
                            label = strings.liveViewGrid,
                            icon = Icons.Filled.GridView,
                            selected = isGridMode,
                            onClick = { isGridMode = true }
                        )
                    }
                }
                // Category & Favorites filter chips
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(com.seerflix.tv.core.model.LiveTvFilter.entries, key = { it.name }) { filter ->
                        val label = when (filter) {
                            com.seerflix.tv.core.model.LiveTvFilter.ALL -> strings.liveFilterAll
                            com.seerflix.tv.core.model.LiveTvFilter.FAVORITES -> strings.liveFilterFavorites
                            com.seerflix.tv.core.model.LiveTvFilter.MOVIES -> strings.liveFilterMovies
                            com.seerflix.tv.core.model.LiveTvFilter.SPORTS -> strings.liveFilterSports
                            com.seerflix.tv.core.model.LiveTvFilter.NEWS -> strings.liveFilterNews
                            com.seerflix.tv.core.model.LiveTvFilter.KIDS -> strings.liveFilterKids
                        }
                        LiveTvChipButton(
                            label = label,
                            icon = if (filter == com.seerflix.tv.core.model.LiveTvFilter.FAVORITES) Icons.Filled.Star else null,
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter }
                        )
                    }
                }
            }
        }

        if (filteredChannels.isEmpty()) {
            item(key = "live_tv_filtered_empty") {
                Text(
                    text = strings.liveFilterEmpty,
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        } else if (isGridMode) {
            // ---- 2D TIME-GRID EPG ----
            item(key = "epg_time_header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = dayFmt.format(java.time.Instant.ofEpochMilli(windowStartMs)),
                            color = colors.white,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(185.dp)
                        )
                        LiveTvChipButton(
                            label = strings.liveGridEarlier,
                            selected = false,
                            onClick = { windowOffsetHours = (windowOffsetHours - 2).coerceAtLeast(0) }
                        )
                        LiveTvChipButton(
                            label = strings.liveGridNow,
                            selected = windowOffsetHours == 0,
                            onClick = { windowOffsetHours = 0 }
                        )
                        LiveTvChipButton(
                            label = strings.liveGridLater,
                            selected = false,
                            onClick = { windowOffsetHours = (windowOffsetHours + 2).coerceAtMost(32) }
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.surfaceElevated.copy(alpha = 0.7f))
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(Modifier.width(185.dp))
                        timeSlots.forEach { slotMs ->
                            Text(
                                text = slotTimeFmt.format(java.time.Instant.ofEpochMilli(slotMs)),
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            items(filteredChannels, key = { "grid_${it.id}" }, contentType = { "epg_row" }) { ch ->
                val cells = remember(ch, windowStartMs, nowMs) {
                    com.seerflix.tv.core.data.LiveTvEpgHelper.cellsForWindow(ch, windowStartMs, nowMs)
                }
                var chFocused by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Sticky-style Channel header cell (OK = watch live, Long-OK = options/favorite)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .width(185.dp)
                            .height(64.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (chFocused) colors.surfaceElevated else colors.surface)
                            .border(
                                if (chFocused) 2.dp else 1.dp,
                                if (chFocused) colors.focusBorder else colors.badgeBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .onFocusChanged { chFocused = it.isFocused || it.hasFocus }
                            .combinedClickable(
                                onClick = { onTuneChannel(ch.id) },
                                onLongClick = { onOpenChannelMenu(ch, ch.currentProgram) }
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        SizedAsyncImage(
                            url = ch.logoUrl,
                            contentDescription = ch.name,
                            width = 28.dp,
                            height = 28.dp,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (!ch.number.isNullOrBlank()) {
                                    Text(
                                        text = ch.number,
                                        color = colors.accent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                if (ch.isFavorite) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            Text(
                                text = ch.name,
                                color = colors.white,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Proportional horizontal program cells for the 3-hour window
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        cells.forEach { cell ->
                            var cellFocused by remember { mutableStateOf(false) }
                            val prog = cell.program
                            val bg = when {
                                cellFocused -> colors.white
                                cell.isCurrent -> colors.accent.copy(alpha = 0.24f)
                                prog != null -> colors.surface
                                else -> colors.surface.copy(alpha = 0.45f)
                            }
                            val fg = if (cellFocused) colors.deepBlack else colors.white
                            val subFg = if (cellFocused) colors.deepBlack.copy(alpha = 0.72f) else colors.textSecondary
                            Column(
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .weight(cell.weightFraction)
                                    .height(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(
                                        if (cellFocused) 2.dp else 1.dp,
                                        when {
                                            cellFocused -> colors.accent
                                            cell.isCurrent -> colors.accent.copy(alpha = 0.65f)
                                            else -> colors.badgeBorder
                                        },
                                        RoundedCornerShape(8.dp)
                                    )
                                    .onFocusChanged { cellFocused = it.isFocused || it.hasFocus }
                                    .combinedClickable(
                                        onClick = {
                                            if (prog != null) onOpenChannelMenu(ch, prog)
                                            else onTuneChannel(ch.id)
                                        },
                                        onLongClick = { onTuneChannel(ch.id) }
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (prog?.isRecording == true) RecBadge(strings.dvrRecordingBadge)
                                    Text(
                                        text = prog?.title ?: strings.liveNoProgramData,
                                        color = if (prog != null) fg else colors.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = if (cell.isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (prog != null && prog.timeRangeLabel.isNotBlank()) {
                                    Text(
                                        text = prog.timeRangeLabel,
                                        color = subFg,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ---- LIST VIEW ----
            items(filteredChannels, key = { it.id }, contentType = { "channel" }) { ch ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardCorner)
                        .background(if (focused) colors.surfaceElevated else colors.surface)
                        .border(
                            if (focused) 2.dp else 1.dp,
                            if (focused) colors.focusBorder else colors.badgeBorder,
                            cardCorner
                        )
                        .onFocusChanged { focused = it.isFocused || it.hasFocus }
                        // OK tunes in; holding OK opens record / cancel / favorite options.
                        .combinedClickable(
                            onClick = { onTuneChannel(ch.id) },
                            onLongClick = { onOpenChannelMenu(ch, null) }
                        )
                        .padding(14.dp)
                ) {
                    // Channel number + logo + favorite indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.width(196.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.accent.copy(alpha = 0.2f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = ch.number?.ifBlank { "CH" } ?: "CH",
                                color = colors.accent,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        SizedAsyncImage(
                            url = ch.logoUrl,
                            contentDescription = ch.name,
                            width = 38.dp,
                            height = 38.dp,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (ch.isFavorite) {
                                    Icon(
                                        imageVector = Icons.Filled.Star,
                                        contentDescription = null,
                                        tint = Color(0xFFFFD54F),
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                                Text(
                                    text = ch.name,
                                    color = colors.white,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(18.dp))

                    // Now Playing & Progress
                    Column(modifier = Modifier.weight(1.2f)) {
                        val cur = ch.currentProgram
                        if (cur?.isRecording == true) RecBadge(strings.dvrRecordingBadge)
                        Text(
                            text = cur?.title ?: strings.liveStream,
                            color = colors.white,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (cur != null && cur.timeRangeLabel.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = strings.liveOnNow.format(cur.timeRangeLabel),
                                color = colors.matchGreen,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            val progress = cur.progressFraction()
                            if (progress > 0f) {
                                Spacer(Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(colors.badgeBorder)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress)
                                            .fillMaxHeight()
                                            .background(colors.accent)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.width(18.dp))

                    // Up Next
                    Column(modifier = Modifier.weight(0.9f)) {
                        ch.nextProgram?.let { nxt ->
                            if (nxt.isRecording) RecBadge(strings.dvrRecordingBadge)
                            Text(
                                text = strings.liveUpNext,
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = nxt.title,
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (nxt.timeRangeLabel.isNotBlank()) {
                                Text(nxt.timeRangeLabel, color = colors.textMuted, fontSize = 11.sp)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (focused) colors.primaryButtonBg else colors.secondaryButtonBg)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = strings.liveTuneIn,
                            color = if (focused) colors.primaryButtonFg else colors.white,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (dvrFeedback != null) {
            item(key = "dvr_feedback") {
                val text = when {
                    dvrFeedback == "single" -> strings.dvrRecordingSet
                    dvrFeedback == "series" -> strings.dvrSeriesSet
                    dvrFeedback == "cancelled" -> strings.dvrCancelled
                    else -> strings.dvrFailed.format(dvrFeedback.removePrefix("error:"))
                }
                Text(text, color = colors.matchGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }

        item(key = "dvr_scheduled_header") {
            Text(
                text = strings.dvrScheduled,
                color = colors.white,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        if (scheduled.isEmpty()) {
            item(key = "dvr_scheduled_empty") {
                Text(strings.dvrNoneScheduled, color = colors.textSecondary, fontSize = 14.sp)
            }
        } else {
            items(scheduled, key = { "timer_${it.id}" }, contentType = { "timer" }) { rec ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(cardCorner)
                        .background(if (focused) colors.surfaceElevated else colors.surface)
                        .border(if (focused) 2.dp else 1.dp, if (focused) colors.focusBorder else colors.badgeBorder, cardCorner)
                        .onFocusChanged { focused = it.isFocused || it.hasFocus }
                        .clickable { onCancelScheduled(rec) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    RecBadge(strings.dvrRecordingBadge)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(rec.title, color = colors.white, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(
                            rec.timeLabel.takeIf { it.isNotBlank() },
                            rec.channelName,
                            strings.dvrRecordSeries.takeIf { rec.isSeriesRule }
                        ).joinToString(" • ")
                        if (sub.isNotBlank()) Text(sub, color = colors.textSecondary, fontSize = 12.sp)
                    }
                    if (focused) {
                        Text(
                            if (rec.isSeriesRule) strings.dvrCancelSeries else strings.dvrCancelRecording,
                            color = colors.accent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (recordings.isNotEmpty()) {
            item(key = "dvr_recordings_row") {
                CatalogRowSection(
                    row = CatalogRow(
                        id = "dvr_recordings",
                        title = strings.liveDvrRecordings,
                        style = RowStyle.POSTER,
                        items = recordings
                    ),
                    onItemFocus = {},
                    onItemClick = onOpenRecording,
                    onContinueWatchingOptions = {},
                    onPosterOptions = {}
                )
            }
        }
    }
}

@Composable
private fun CustomizeHomeDialog(
    profile: UserProfile,
    libraries: List<JellyfinLibraryView>,
    currentRows: List<CatalogRow>,
    onSave: (List<String>, List<String>, List<String>, HomeTabId) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)

    val pinnedLibs = remember(profile.id) {
        mutableStateListOf<String>().apply { addAll(profile.pinnedLibraryIds) }
    }
    val hiddenRows = remember(profile.id) {
        mutableStateListOf<String>().apply { addAll(profile.hiddenHomeRowIds) }
    }
    val orderedRows = remember(profile.id, currentRows) {
        val visible = currentRows.distinctBy { it.id }.map { it.id to it.title }
        // Hidden rows are filtered out of `currentRows` by the repository, so without this they
        // vanished from the dialog and could never be shown again.
        val visibleIds = visible.map { it.first }.toSet()
        val hiddenOnly = profile.hiddenHomeRowIds.distinct()
            .filterNot { it in visibleIds }
            .map { id -> id to id.split('_').joinToString(" ") { part -> part.replaceFirstChar { c -> c.uppercase() } } }
        val known = visible + hiddenOnly
        val base = if (profile.homeRowOrder.isNotEmpty()) {
            val map = known.toMap()
            profile.homeRowOrder.mapNotNull { id -> map[id]?.let { id to it } } +
                known.filterNot { it.first in profile.homeRowOrder }
        } else {
            known
        }
        mutableStateListOf<Pair<String, String>>().apply { addAll(base) }
    }
    var startupTab by remember(profile.id) { mutableStateOf(profile.defaultStartupTab) }
    val strings = AppStrings.current

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(680.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.customizeTitle.format(profile.name),
                color = colors.white,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            if (libraries.isNotEmpty()) {
                Text(
                    text = strings.customizePinLibraries,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(libraries, key = { it.id }) { lib ->
                        val pinned = lib.id in pinnedLibs
                        var focused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        pinned -> colors.accent
                                        focused -> colors.surfaceElevated
                                        else -> colors.surface
                                    }
                                )
                                .border(
                                    1.dp,
                                    if (focused) colors.focusBorder else colors.badgeBorder,
                                    RoundedCornerShape(8.dp)
                                )
                                .onFocusChanged { focused = it.isFocused || it.hasFocus }
                                .clickable {
                                    if (pinned) pinnedLibs.remove(lib.id) else pinnedLibs.add(lib.id)
                                }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = (if (pinned) "✓ " else "+ ") + lib.name,
                                color = colors.white,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Text(
                text = strings.customizeStartupTab,
                color = colors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(HomeTabId.entries) { tab ->
                    val selected = startupTab == tab
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (selected) colors.white else if (focused) colors.surfaceElevated else colors.surface)
                            .border(1.dp, if (focused) colors.focusBorder else colors.badgeBorder, RoundedCornerShape(6.dp))
                            .onFocusChanged { focused = it.isFocused || it.hasFocus }
                            .clickable { startupTab = tab }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = tab.toHomeTab().localizedLabel(strings),
                            color = if (selected) colors.deepBlack else colors.white,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (orderedRows.isNotEmpty()) {
                Text(
                    text = strings.customizeHideReorder,
                    color = colors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(orderedRows, key = { _, item -> item.first }) { idx, (rowId, rowTitle) ->
                        val hidden = rowId in hiddenRows
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = localizedHomeRowTitle(rowId, rowTitle, strings),
                                color = if (hidden) colors.textMuted else colors.white,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            SmallIconAction(
                                icon = if (hidden) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                label = if (hidden) strings.customizeShow else strings.customizeHide,
                                onClick = {
                                    if (hidden) hiddenRows.remove(rowId) else hiddenRows.add(rowId)
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            SmallIconAction(
                                icon = Icons.Filled.ArrowUpward,
                                label = strings.customizeUp,
                                enabled = idx > 0,
                                onClick = {
                                    val moved = orderedRows.removeAt(idx)
                                    orderedRows.add(idx - 1, moved)
                                }
                            )
                            Spacer(Modifier.width(6.dp))
                            SmallIconAction(
                                icon = Icons.Filled.ArrowDownward,
                                label = strings.customizeDown,
                                enabled = idx < orderedRows.lastIndex,
                                onClick = {
                                    val moved = orderedRows.removeAt(idx)
                                    orderedRows.add(idx + 1, moved)
                                }
                            )
                        }
                    }
                }
            }

            val saveLayoutFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                withFrameNanos { }
                runCatching { saveLayoutFocusRequester.requestFocus() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallTextButton(
                    text = strings.customizeSave,
                    isPrimary = true,
                    onClick = {
                        onSave(
                            pinnedLibs.toList(),
                            hiddenRows.toList(),
                            orderedRows.map { it.first },
                            startupTab
                        )
                    },
                    modifier = Modifier.weight(1f).focusRequester(saveLayoutFocusRequester)
                )
                SmallTextButton(
                    text = strings.actionCancel,
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SyncPlayDialog(
    groups: List<SyncPlayGroup>,
    activeGroup: SyncPlayGroup?,
    onCreateGroup: (String) -> Unit,
    onJoinGroup: (String) -> Unit,
    onLeaveGroup: () -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val strings = AppStrings.current
    val defaultRoomName = strings.syncPlayDefaultRoom
    var roomName by remember { mutableStateOf(defaultRoomName) }
    val createRoomFocusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { createRoomFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(strings.syncPlayTitle, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                SmallIconAction(icon = Icons.Filled.Refresh, label = strings.commonRefresh, onClick = onRefresh)
            }

            if (activeGroup != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.matchGreen.copy(alpha = 0.16f))
                        .border(1.dp, colors.matchGreen, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(strings.syncPlayActiveRoom.format(activeGroup.groupName), color = colors.white, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = strings.syncPlayViewers.format(activeGroup.participants.size.coerceAtLeast(1), syncPlayStateLabel(activeGroup.state, strings)),
                            color = colors.matchGreen,
                            fontSize = 12.sp
                        )
                    }
                    SmallTextButton(text = strings.syncPlayLeave, isPrimary = false, onClick = onLeaveGroup)
                }
            }

            OutlinedTextField(
                value = roomName,
                onValueChange = { roomName = it },
                label = { Text(strings.syncPlayNewName) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.white,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.badgeBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            SmallTextButton(
                text = strings.syncPlayCreate,
                isPrimary = true,
                onClick = { onCreateGroup(roomName) },
                modifier = Modifier.fillMaxWidth().focusRequester(createRoomFocusRequester)
            )

            Text(strings.syncPlayRooms, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            if (groups.isEmpty()) {
                Text(strings.syncPlayNoRooms, color = colors.textMuted, fontSize = 12.sp)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { it.groupId }) { grp ->
                        val isCurrent = activeGroup?.groupId == grp.groupId
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.surface)
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(grp.groupName, color = colors.white, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    text = strings.syncPlayParticipants.format(grp.participants.size, syncPlayStateLabel(grp.state, strings)),
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                            if (!isCurrent) {
                                SmallTextButton(text = strings.syncPlayJoin, isPrimary = true, onClick = { onJoinGroup(grp.groupId) })
                            }
                        }
                    }
                }
            }

            SmallTextButton(text = strings.commonClose, isPrimary = false, onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun AppUpdateDialog(
    updateInfo: AppUpdateInfo,
    installState: AppUpdateInstallState,
    onStartUpdate: () -> Unit,
    onCancelUpdate: () -> Unit,
    onResume: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val strings = AppStrings.current
    val primaryFocusRequester = remember { FocusRequester() }
    val secondaryFocusRequester = remember { FocusRequester() }
    val hasDownloadUrl = !updateInfo.downloadUrl.isNullOrBlank()
    val isDownloading = installState is AppUpdateInstallState.Downloading

    // When returning from TV's "Install unknown apps" settings screen, automatically resume install.
    LifecycleResumeEffect(Unit) {
        onResume()
        onPauseOrDispose { }
    }

    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(isDownloading, hasDownloadUrl) {
            withFrameNanos { }
            val target = if (!isDownloading && hasDownloadUrl) primaryFocusRequester else secondaryFocusRequester
            runCatching { target.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(520.dp)
                .clip(dialogShape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogShape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = colors.matchGreen, modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = strings.updateAvailable.format(updateInfo.latestVersion),
                    color = colors.white,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            if (updateInfo.releaseNotes.isNotBlank()) {
                Text(
                    text = updateInfo.releaseNotes,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis
                )
            }

            when (installState) {
                is AppUpdateInstallState.Downloading -> {
                    val progress = installState.progress
                    val statusText = if (progress != null && installState.totalBytes > 0L) {
                        val pct = (progress * 100f).toInt().coerceIn(0, 100)
                        strings.updateDownloadingPercent.format(
                            pct,
                            formatUpdateMegabytes(installState.bytesRead),
                            formatUpdateMegabytes(installState.totalBytes)
                        )
                    } else {
                        strings.updateDownloading
                    }
                    Text(
                        text = statusText,
                        color = colors.downloadingBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.downloadingBlue,
                            trackColor = colors.surface
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = colors.downloadingBlue,
                            trackColor = colors.surface
                        )
                    }
                }
                is AppUpdateInstallState.NeedsInstallPermission -> {
                    Text(
                        text = strings.updatePermissionHint,
                        color = colors.pendingAmber,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                is AppUpdateInstallState.ReadyToInstall -> {
                    Text(
                        text = strings.updateReadyHint,
                        color = colors.matchGreen,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                is AppUpdateInstallState.Failed -> {
                    Text(
                        text = strings.updateFailedPrefix.format(installState.message),
                        color = colors.accent,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
                AppUpdateInstallState.Idle -> Unit
            }

            Text(
                text = strings.updateReleaseLink.format(updateInfo.htmlUrl),
                color = colors.textMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!isDownloading && hasDownloadUrl) {
                    val primaryLabel = when (installState) {
                        is AppUpdateInstallState.NeedsInstallPermission -> strings.updateAllowPermission
                        is AppUpdateInstallState.ReadyToInstall -> strings.updateInstallNow
                        is AppUpdateInstallState.Failed -> strings.updateRetry
                        else -> strings.updateDownloadAndInstall
                    }
                    SmallTextButton(
                        text = primaryLabel,
                        isPrimary = true,
                        onClick = onStartUpdate,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(primaryFocusRequester)
                    )
                }
                SmallTextButton(
                    text = if (isDownloading) strings.actionCancel else strings.commonClose,
                    isPrimary = !hasDownloadUrl || isDownloading,
                    onClick = if (isDownloading) onCancelUpdate else onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(secondaryFocusRequester)
                )
            }
        }
    }
}

private fun formatUpdateMegabytes(bytes: Long): String {
    val mb = bytes.coerceAtLeast(0L) / (1024f * 1024f)
    return java.util.Locale.US.let { String.format(it, "%.1f MB", mb) }
}

@Composable
private fun SmallIconAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) colors.surfaceElevated else colors.dialogSurface)
            .border(1.dp, if (focused) colors.focusBorder else colors.badgeBorder, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            // Stay focusable when disabled: moving a row to the top disabled its focused "Up"
            // button, which dropped D-pad focus out of the dialog entirely.
            .clickable { if (enabled) onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) colors.white else colors.textMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SmallTextButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isPrimary && focused -> colors.accentDark
                    isPrimary -> colors.accent
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.focusBorder else colors.badgeBorder,
                RoundedCornerShape(6.dp)
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = colors.white, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecBadge(label: String) {
    Box(
        modifier = Modifier
            .padding(bottom = 3.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFFD32F2F))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(label, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun LiveTvChipButton(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(50)
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(shape)
            .background(
                when {
                    focused -> colors.white
                    selected -> colors.accent
                    else -> colors.surface
                }
            )
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) colors.accent else colors.badgeBorder,
                shape
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (focused) colors.deepBlack else colors.white,
                modifier = Modifier.size(14.dp)
            )
        }
        Text(
            text = label,
            color = if (focused) colors.deepBlack else colors.white,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

private val DVR_PRE_PADDING_MINUTES = listOf(0, 2, 5, 10)
private val DVR_POST_PADDING_MINUTES = listOf(0, 5, 10, 15, 30)

/** Hold-OK / EPG program menu: watch live, favorite channel, and configure/schedule/cancel DVR timers. */
@Composable
private fun LiveTvProgramDialog(
    channel: LiveTvChannel,
    selectedProgram: com.seerflix.tv.core.model.LiveTvProgram? = null,
    onWatch: () -> Unit,
    onToggleFavorite: () -> Unit = {},
    onRecord: (programId: String, series: Boolean, options: com.seerflix.tv.core.model.DvrTimerOptions) -> Unit,
    onCancel: (timerId: String) -> Unit,
    onCancelSeries: (seriesTimerId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val firstFocus = remember { FocusRequester() }
    // null = keep the server's recording defaults (Dashboard → Live TV).
    var prePaddingMin by remember { mutableStateOf<Int?>(null) }
    var postPaddingMin by remember { mutableStateOf<Int?>(null) }
    var recordNewOnly by remember { mutableStateOf<Boolean?>(null) }
    var recordAnyChannel by remember { mutableStateOf<Boolean?>(null) }
    fun toggleLabel(value: Boolean?) = when (value) {
        null -> strings.commonDefault
        true -> strings.optionOn
        false -> strings.optionOff
    }
    // Default → On → Off → Default
    fun nextToggle(value: Boolean?): Boolean? = when (value) {
        null -> true
        true -> false
        false -> null
    }

    val programs = remember(channel, selectedProgram) {
        if (selectedProgram != null) listOf(selectedProgram)
        else listOfNotNull(channel.currentProgram, channel.nextProgram)
    }
    val hasSeriesOption = remember(programs) { programs.any { it.isSeries && it.seriesTimerId.isNullOrBlank() } }
    val hasRecordableProgram = remember(programs) { programs.any { it.timerId.isNullOrBlank() || (it.isSeries && it.seriesTimerId.isNullOrBlank()) } }

    fun currentOptions() = com.seerflix.tv.core.model.DvrTimerOptions(
        prePaddingSeconds = prePaddingMin?.let { it * 60 },
        postPaddingSeconds = postPaddingMin?.let { it * 60 },
        recordNewOnly = recordNewOnly,
        recordAnyChannel = recordAnyChannel
    )

    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { firstFocus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(600.dp)
                .clip(RoundedCornerShape(spec.dialogCornerRadiusDp.dp))
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, RoundedCornerShape(spec.dialogCornerRadiusDp.dp))
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(channel.name, color = colors.white, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (channel.isFavorite) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(18.dp))
                }
            }
            Text(strings.dvrProgramOptions, color = colors.textSecondary, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                DvrMenuButton(strings.dvrWatchLive, onWatch, Modifier.weight(1f).focusRequester(firstFocus))
                DvrMenuButton(
                    label = if (channel.isFavorite) strings.liveFavoriteRemove else strings.liveFavoriteAdd,
                    onClick = onToggleFavorite,
                    modifier = Modifier.weight(1f)
                )
            }

            if (hasRecordableProgram) {
                // Pre-padding selector
                Text(strings.dvrPrePadding, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveTvChipButton(
                        label = strings.commonDefault,
                        selected = prePaddingMin == null,
                        onClick = { prePaddingMin = null }
                    )
                    DVR_PRE_PADDING_MINUTES.forEach { mins ->
                        LiveTvChipButton(
                            label = strings.dvrPaddingMinutes.format(mins),
                            selected = prePaddingMin == mins,
                            onClick = { prePaddingMin = mins }
                        )
                    }
                }
                // Post-padding selector
                Text(strings.dvrPostPadding, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LiveTvChipButton(
                        label = strings.commonDefault,
                        selected = postPaddingMin == null,
                        onClick = { postPaddingMin = null }
                    )
                    DVR_POST_PADDING_MINUTES.forEach { mins ->
                        LiveTvChipButton(
                            label = strings.dvrPaddingMinutes.format(mins),
                            selected = postPaddingMin == mins,
                            onClick = { postPaddingMin = mins }
                        )
                    }
                }
                if (hasSeriesOption) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiveTvChipButton(
                            label = "${strings.dvrRecordNewOnly}: ${toggleLabel(recordNewOnly)}",
                            selected = recordNewOnly == true,
                            onClick = { recordNewOnly = nextToggle(recordNewOnly) }
                        )
                        LiveTvChipButton(
                            label = "${strings.dvrRecordAnyChannel}: ${toggleLabel(recordAnyChannel)}",
                            selected = recordAnyChannel == true,
                            onClick = { recordAnyChannel = nextToggle(recordAnyChannel) }
                        )
                    }
                }
            }

            programs.forEach { program ->
                Text(
                    "${program.title}${if (program.timeRangeLabel.isNotBlank()) "  •  ${program.timeRangeLabel}" else ""}",
                    color = colors.white,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 6.dp)
                )
                if (!program.overview.isNullOrBlank()) {
                    Text(
                        text = program.overview,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val timerId = program.timerId
                if (!timerId.isNullOrBlank()) {
                    DvrMenuButton(strings.dvrCancelRecording, { onCancel(timerId) })
                } else {
                    DvrMenuButton(strings.dvrRecord, { onRecord(program.id, false, currentOptions()) })
                }
                val seriesTimerId = program.seriesTimerId
                if (!seriesTimerId.isNullOrBlank()) {
                    DvrMenuButton(strings.dvrCancelSeries, { onCancelSeries(seriesTimerId) })
                } else if (program.isSeries) {
                    DvrMenuButton(strings.dvrRecordSeries, { onRecord(program.id, true, currentOptions()) })
                }
            }
        }
    }
}

@Composable
private fun DvrMenuButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(SeerflixTokens.spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (focused) colors.white else colors.secondaryButtonBg)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = if (focused) colors.deepBlack else colors.white, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}
