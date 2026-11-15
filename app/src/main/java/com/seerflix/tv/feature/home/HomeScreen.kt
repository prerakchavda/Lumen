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
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.focus.FocusDirection
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
    else -> label
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
            onOpenLiveTvMenu = viewModel::openLiveTvMenu,
            onDismissLiveTvMenu = viewModel::dismissLiveTvMenu,
            onRecordProgram = viewModel::recordProgram,
            onCancelRecording = viewModel::cancelRecording,
            onCancelSeriesRecording = viewModel::cancelSeriesRecording
        )
    )
}

/** Library grid + DVR callbacks, bundled so they don't have to be threaded one by one. */
@androidx.compose.runtime.Immutable
data class HomeExtraActions(
    val onBrowseAll: (MediaType) -> Unit = {},
    val onOpenLiveTvMenu: (LiveTvChannel) -> Unit = {},
    val onDismissLiveTvMenu: () -> Unit = {},
    val onRecordProgram: (programId: String, series: Boolean) -> Unit = { _, _ -> },
    val onCancelRecording: (timerId: String) -> Unit = {},
    val onCancelSeriesRecording: (seriesTimerId: String) -> Unit = {}
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

    // Cached: a new Brush every recomposition invalidates the full-screen background draw.
    val canvasBg = remember(spec.preset, colors) {
        if (spec.preset == UiPreset.DISNEY_PLUS) {
            Brush.verticalGradient(listOf(colors.backgroundGlow.copy(alpha = 0.45f), colors.background))
        } else {
            SolidColor(colors.background)
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
                    hasActiveSyncPlay = state.activeSyncPlayGroup != null,
                    hasUpdate = state.updateInfo != null,
                    profileName = state.profile?.name ?: strings.homeProfileFallback,
                    strings = strings,
                    activeTabFocusRequester = navFocusRequester,
                    onSelectTab = onSelectTab,
                    onOpenSearch = onOpenSearch,
                    onOpenRequests = onOpenRequests,
                    onOpenAppearance = onOpenAppearance,
                    onOpenCustomizeHome = onOpenCustomizeHome,
                    onOpenSyncPlay = onOpenSyncPlay,
                    onOpenUpdate = onOpenUpdateDialog,
                    onSwitchProfile = onSwitchProfile,
                    onOpenSettings = onOpenSettings,
                    onRefresh = onRefresh
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
                    hasActiveSyncPlay = state.activeSyncPlayGroup != null,
                    hasUpdate = state.updateInfo != null,
                    profileName = state.profile?.name ?: strings.homeProfileFallback,
                    strings = strings,
                    activeTabFocusRequester = navFocusRequester,
                    onSelectTab = onSelectTab,
                    onOpenSearch = onOpenSearch,
                    onOpenRequests = onOpenRequests,
                    onOpenAppearance = onOpenAppearance,
                    onOpenCustomizeHome = onOpenCustomizeHome,
                    onOpenSyncPlay = onOpenSyncPlay,
                    onOpenUpdate = onOpenUpdateDialog,
                    onSwitchProfile = onSwitchProfile,
                    onOpenSettings = onOpenSettings,
                    onRefresh = onRefresh,
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
                    hasActiveSyncPlay = state.activeSyncPlayGroup != null,
                    hasUpdate = state.updateInfo != null,
                    profileName = state.profile?.name ?: strings.homeProfileFallback,
                    strings = strings,
                    activeTabFocusRequester = navFocusRequester,
                    onSelectTab = onSelectTab,
                    onOpenSearch = onOpenSearch,
                    onOpenRequests = onOpenRequests,
                    onOpenAppearance = onOpenAppearance,
                    onOpenCustomizeHome = onOpenCustomizeHome,
                    onOpenSyncPlay = onOpenSyncPlay,
                    onOpenUpdate = onOpenUpdateDialog,
                    onSwitchProfile = onSwitchProfile,
                    onOpenSettings = onOpenSettings,
                    onRefresh = onRefresh
                )

                HomeMainCanvas(
                    state = state,
                    onOpenDetails = onOpenDetails,
                    onPlayDirect = onPlayDirect,
                    onRefresh = onRefresh,
                    onTogglePreviewMute = onTogglePreviewMute,
                    onItemFocused = onItemFocused,
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
            onOpenItemDetails = { item ->
                onDismissCollectionDetails()
                onOpenDetails(item)
            },
            onRequestItem = onRequestCollectionItem,
            onRequestAllMissing = onRequestAllMissingInCollection,
            onDismiss = onDismissCollectionDetails
        )
    }

    state.liveTvMenuChannel?.let { channel ->
        LiveTvProgramDialog(
            channel = channel,
            onWatch = {
                extraActions.onDismissLiveTvMenu()
                onPlayDirect(channel.id)
            },
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
            onDismiss = onDismissUpdateDialog
        )
    }
}

private val NETFLIX_RAIL_COLLAPSED_WIDTH = 68.dp
private val NETFLIX_RAIL_EXPANDED_WIDTH = 216.dp

/**
 * D-pad scrolling for the vertical Home list.
 *
 * - Rows: the focused card is parked at a fixed line ([PIVOT_FRACTION] of the viewport), like the
 *   Android TV platform pivot, so every Down/Up press scrolls by exactly one row (Netflix-style)
 *   instead of the list creeping only when focus hits the bottom edge.
 * - Top of the page: anything that is fully visible with the list scrolled to the very top (hero
 *   CTA buttons, filter chips) snaps the list back to the top. With a plain pivot the hero buttons
 *   sit below the pivot line, so focusing them used to scroll the billboard half off-screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Stable
private class HomeListBringIntoViewSpec(private val listState: LazyListState) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        if (listState.firstVisibleItemIndex == 0) {
            val scrolled = listState.firstVisibleItemScrollOffset.toFloat()
            val absoluteBottom = offset + scrolled + size
            if (absoluteBottom <= containerSize * TOP_SNAP_FRACTION) return -scrolled
        }
        val pivot = containerSize * PIVOT_FRACTION
        // Too tall to fit below the pivot: align its bottom with the viewport end instead.
        val target = if (size <= containerSize && containerSize - pivot < size) containerSize - size else pivot
        return offset - target
    }

    private companion object {
        const val PIVOT_FRACTION = 0.3f
        const val TOP_SNAP_FRACTION = 0.92f
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeMainCanvas(
    state: HomeUiState,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onPlayDirect: (String) -> Unit,
    onRefresh: () -> Unit,
    onTogglePreviewMute: () -> Unit,
    onItemFocused: (UnifiedMediaItem) -> Unit,
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

    // --- D-pad focus bookkeeping -------------------------------------------------------------
    // Key of the last focused card; saveable so it survives navigating to Details and back.
    // Held as a raw State (not delegated) and only written from focus callbacks so focus moves
    // don't recompose this whole canvas.
    val lastFocusedCardKey = rememberSaveable { mutableStateOf<String?>(null) }
    // Non-null only when Home re-enters composition with a saved focus target (Back from Details/Player).
    val restoreFocusKey = remember { Snapshot.withoutReadObservation { lastFocusedCardKey.value } }
    val restoreFocusRequester = remember { FocusRequester() }
    val heroFocusRequester = remember { FocusRequester() }
    var initialFocusHandled by rememberSaveable { mutableStateOf(false) }
    var contentHasFocus by remember { mutableStateOf(false) }
    val onCardFocused = remember<(String) -> Unit> { { cardKey -> lastFocusedCardKey.value = cardKey } }

    // Restore focus to the card the user left from instead of dumping it on the hero / nothing.
    // Coming back with no saved card (e.g. you were on the hero, or the player was opened from a
    // Watch Next/deep link) also lands on the hero instead of the first nav item.
    LaunchedEffect(Unit) {
        if (restoreFocusKey == null && !initialFocusHandled) return@LaunchedEffect
        withFrameNanos { }
        val restored = restoreFocusKey != null &&
            runCatching { restoreFocusRequester.requestFocus(FocusDirection.Enter) }.getOrDefault(false)
        if (!restored) {
            runCatching { heroFocusRequester.requestFocus(FocusDirection.Enter) }
        }
    }
    // One-shot initial focus once the first load settles, so the remote works immediately on launch.
    // (Previously HeroBillboard grabbed focus every time it re-entered composition, which yanked
    // focus back to "Play" while scrolling up through rows and after returning from Details.)
    LaunchedEffect(state.isLoading) {
        if (state.isLoading || initialFocusHandled || restoreFocusKey != null) return@LaunchedEffect
        initialFocusHandled = true
        withFrameNanos { }
        if (runCatching { heroFocusRequester.requestFocus() }.isFailure && state.errorMessage == null) {
            onBackToNav()
        }
    }
    // Back from content returns to the nav first; only a Back from the nav exits the app.
    if (hasBackDispatcher) {
        BackHandler(enabled = contentHasFocus) { onBackToNav() }
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
                    onCancelScheduled = { rec ->
                        if (rec.isSeriesRule) extraActions.onCancelSeriesRecording(rec.id)
                        else extraActions.onCancelRecording(rec.id)
                    }
                )
            }
            state.activeTab == HomeTab.MY_LIST && state.rows.isEmpty() -> {
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
                val listState = rememberLazyListState()
                if (hasBackDispatcher) {
                    // Registered after the outer handler, so it takes precedence while the list is shown.
                    BackHandler(enabled = contentHasFocus) {
                        onBackToNav()
                        scope.launch { listState.scrollToItem(0) }
                    }
                }
                // Duplicate row ids would crash LazyColumn ("Key was already used").
                val rows = remember(state.rows) { state.rows.distinctBy { it.id } }
                // The Apple TV glass bar floats over the list; without a hero the filter bar sat under it.
                val listTopPadding = if (spec.navStyle == NavLayoutStyle.TOP_GLASS_PILL && state.featuredItem == null) 92.dp else 0.dp
                // Platform spec (pivot on Android TV) for the horizontal rows; the vertical list gets
                // the Home-specific spec below. Captured before it is overridden for the LazyColumn.
                val rowBringIntoViewSpec = LocalBringIntoViewSpec.current
                val homeBringIntoViewSpec = remember(listState) { HomeListBringIntoViewSpec(listState) }
                CompositionLocalProvider(LocalBringIntoViewSpec provides homeBringIntoViewSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
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
                                // requester matches this key, so restore falls back to the hero).
                                modifier = Modifier.onFocusChanged { if (it.hasFocus) onCardFocused("hero_billboard") }
                            )
                        }
                    }

                    if (state.readyAlerts.isNotEmpty()) {
                        item(key = "ready_alerts_banner", contentType = "alerts") {
                            ReadyRequestAlertsBanner(
                                alerts = state.readyAlerts,
                                onPlayAlert = onPlayReadyAlert,
                                onDismissAlert = onDismissReadyAlert
                            )
                        }
                    }

                    if (state.activeTab != HomeTab.MY_LIST) {
                        item(key = "availability_filter_bar", contentType = "filter_bar") {
                            AvailabilityFilterBar(
                                selectedFilter = state.availabilityFilter,
                                isSeerrConfigured = state.isSeerrConfigured,
                                activeSyncPlayName = state.activeSyncPlayGroup?.groupName,
                                isKidsMode = state.profile?.kidsMode == true,
                                onSelectFilter = onSelectAvailabilityFilter
                            )
                        }
                    }

                    if (state.activeTab == HomeTab.SHOWS || state.activeTab == HomeTab.MOVIES) {
                        item(key = "genre_filter_bar", contentType = "genre_bar") {
                            GenreFilterBar(
                                selectedGenre = state.selectedGenre,
                                onSelectGenre = onSelectGenre,
                                bringIntoViewSpec = rowBringIntoViewSpec,
                                browseAllLabel = if (state.activeTab == HomeTab.MOVIES) {
                                    strings.libraryAllMovies
                                } else {
                                    strings.libraryAllShows
                                },
                                onBrowseAll = {
                                    extraActions.onBrowseAll(
                                        if (state.activeTab == HomeTab.MOVIES) MediaType.MOVIE else MediaType.SERIES
                                    )
                                }
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
                                        strings.homeNothingYetHint
                                    },
                                    color = colors.textSecondary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    items(
                        items = rows,
                        key = { it.id },
                        contentType = { it.style }
                    ) { row ->
                        CatalogRowSection(
                            row = row,
                            rowBringIntoViewSpec = rowBringIntoViewSpec,
                            restoreFocusKey = restoreFocusKey,
                            restoreFocusRequester = restoreFocusRequester,
                            onCardFocused = onCardFocused,
                            onItemFocus = onItemFocused,
                            onItemClick = { item ->
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

@Composable
private fun AppleTvTopGlassBar(
    activeTab: HomeTab,
    activeDownloadCount: Int,
    isSeerrConfigured: Boolean,
    hasActiveSyncPlay: Boolean,
    hasUpdate: Boolean,
    profileName: String,
    strings: UiStrings,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCustomizeHome: () -> Unit,
    onOpenSyncPlay: () -> Unit,
    onOpenUpdate: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    activeTabFocusRequester: FocusRequester? = null
) {
    val colors = SeerflixTokens.colors
    Row(
        modifier = modifier.fillMaxWidth(),
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
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "tv+",
                color = colors.accent,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = "Lumen",
                color = colors.white,
                fontSize = 13.sp,
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
            HomeTab.entries.forEach { tab ->
                TopNavTabPill(
                    label = tab.localizedLabel(strings),
                    selected = activeTab == tab,
                    isGlass = true,
                    onClick = { onSelectTab(tab) },
                    modifier = activeTabModifier(tab == activeTab, activeTabFocusRequester)
                )
            }
        }

        // Right utility glass pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            TopBarUtilityIcon(
                Icons.Filled.Groups,
                strings.actionWatchTogether,
                highlighted = hasActiveSyncPlay,
                onClick = onOpenSyncPlay
            )
            TopBarUtilityIcon(Icons.Filled.Tune, strings.actionCustomizeHome, onClick = onOpenCustomizeHome)
            TopBarUtilityIcon(Icons.Filled.Palette, strings.homeAppearance, onClick = onOpenAppearance)
            if (hasUpdate) {
                TopBarUtilityIcon(Icons.Filled.SystemUpdate, strings.homeUpdate, highlighted = true, onClick = onOpenUpdate)
            }
            TopBarUtilityIcon(Icons.Filled.Refresh, strings.commonRefresh, onClick = onRefresh)
            TopBarUtilityIcon(Icons.Filled.Person, profileName, onClick = onSwitchProfile)
            TopBarUtilityIcon(Icons.Filled.Settings, strings.navSettings, onClick = onOpenSettings)
        }
    }
}

@Composable
private fun DisneyTopHeaderBar(
    activeTab: HomeTab,
    activeDownloadCount: Int,
    isSeerrConfigured: Boolean,
    hasActiveSyncPlay: Boolean,
    hasUpdate: Boolean,
    profileName: String,
    strings: UiStrings,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCustomizeHome: () -> Unit,
    onOpenSyncPlay: () -> Unit,
    onOpenUpdate: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    activeTabFocusRequester: FocusRequester? = null
) {
    val colors = SeerflixTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .background(colors.navBarBackground)
            .border(1.dp, colors.navBarBorder.copy(alpha = 0.45f))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "LUMEN+",
                color = colors.white,
                fontSize = 16.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HomeTab.entries.forEach { tab ->
                    TopNavTabPill(
                        label = tab.localizedLabel(strings),
                        selected = activeTab == tab,
                        isGlass = false,
                        onClick = { onSelectTab(tab) },
                        modifier = activeTabModifier(tab == activeTab, activeTabFocusRequester)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
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
            TopBarUtilityIcon(
                Icons.Filled.Groups,
                strings.actionWatchTogether,
                highlighted = hasActiveSyncPlay,
                onClick = onOpenSyncPlay
            )
            TopBarUtilityIcon(Icons.Filled.Tune, strings.actionCustomizeHome, onClick = onOpenCustomizeHome)
            TopBarUtilityIcon(Icons.Filled.Palette, strings.homeAppearance, onClick = onOpenAppearance)
            if (hasUpdate) {
                TopBarUtilityIcon(Icons.Filled.SystemUpdate, strings.homeUpdate, highlighted = true, onClick = onOpenUpdate)
            }
            TopBarUtilityIcon(Icons.Filled.Refresh, strings.commonRefresh, onClick = onRefresh)
            TopBarUtilityIcon(Icons.Filled.Person, profileName, onClick = onSwitchProfile)
            TopBarUtilityIcon(Icons.Filled.Settings, strings.navSettings, onClick = onOpenSettings)
        }
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
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            color = if (selected && isGlass) colors.deepBlack else colors.white,
            fontSize = 11.sp,
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
            .size(29.dp)
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
            modifier = Modifier.size(14.dp)
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
    activeSyncPlayName: String? = null,
    isKidsMode: Boolean = false,
    onSelectFilter: (AvailabilityFilter) -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = spec.contentStartPaddingDp.dp, end = 28.dp, top = 10.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = if (isSeerrConfigured) {
                AvailabilityFilter.entries
            } else {
                listOf(AvailabilityFilter.ALL, AvailabilityFilter.WATCH_NOW)
            }
            options.forEach { filter -> key(filter) {
                var focused by remember { mutableStateOf(false) }
                val selected = selectedFilter == filter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            when {
                                selected && filter == AvailabilityFilter.REQUESTABLE -> colors.accent
                                selected -> colors.white
                                focused -> colors.secondaryButtonFocusedBg
                                else -> colors.surface
                            }
                        )
                        .then(
                            if (focused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(20.dp))
                            else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        )
                        .onFocusChanged { focused = it.isFocused || it.hasFocus }
                        .clickable { onSelectFilter(filter) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = filter.localized(strings),
                        color = if (selected && filter != AvailabilityFilter.REQUESTABLE) colors.deepBlack else colors.white,
                        fontSize = 12.sp,
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!isSeerrConfigured) {
                StatusTagChip(strings.homeJellyfinOnlyMode, colors.downloadingBlue)
            }
            if (isKidsMode) {
                StatusTagChip(strings.homeKidsModeActive, colors.matchGreen)
            }
            if (activeSyncPlayName != null) {
                StatusTagChip(strings.homeSyncPlayChip.format(activeSyncPlayName), colors.accent)
            }
        }
    }
}

@Composable
private fun StatusTagChip(label: String, tint: Color) {
    val colors = SeerflixTokens.colors
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tint.copy(alpha = 0.2f))
            .border(1.dp, tint.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = colors.white,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenreFilterBar(
    selectedGenre: GenreFilterOption,
    onSelectGenre: (GenreFilterOption) -> Unit,
    bringIntoViewSpec: BringIntoViewSpec? = null,
    browseAllLabel: String? = null,
    onBrowseAll: (() -> Unit)? = null
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    WithBringIntoViewSpec(bringIntoViewSpec) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = spec.contentStartPaddingDp.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (onBrowseAll != null && browseAllLabel != null) {
            item(key = "browse_all", contentType = "browse_all_chip") {
                var focused by remember { mutableStateOf(false) }
                val shape = RoundedCornerShape(20.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(shape)
                        .background(if (focused) colors.white else colors.accent)
                        .onFocusChanged { focused = it.isFocused || it.hasFocus }
                        .clickable { onBrowseAll() }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.GridView,
                        contentDescription = null,
                        tint = if (focused) colors.deepBlack else colors.white,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = browseAllLabel,
                        color = if (focused) colors.deepBlack else colors.white,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        items(GenreFilterOption.ALL_GENRES, key = { it.id }, contentType = { "genre_chip" }) { genre ->
            var focused by remember { mutableStateOf(false) }
            val selected = selectedGenre.id == genre.id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when {
                            selected -> colors.white
                            focused -> colors.secondaryButtonFocusedBg
                            else -> colors.surface
                        }
                    )
                    .then(
                        if (focused) Modifier.border(2.dp, colors.accent, RoundedCornerShape(20.dp))
                        else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                    )
                    .onFocusChanged { focused = it.isFocused || it.hasFocus }
                    .clickable { onSelectGenre(genre) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = genre.localizedLabel(strings),
                    color = if (selected) colors.deepBlack else colors.white,
                    fontSize = 13.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
                )
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
            ContextActionRow(
                icon = Icons.Filled.PlayArrow,
                label = strings.actionResume,
                onClick = onResume,
                modifier = Modifier.focusRequester(firstActionFocusRequester)
            )
            ContextActionRow(Icons.Filled.Info, strings.actionMoreInfo, onViewDetails)
            ContextActionRow(Icons.Filled.DeleteOutline, strings.homeRemoveFromContinue, onRemove)
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) colors.surfaceElevated else colors.surface)
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
    rowBringIntoViewSpec: BringIntoViewSpec? = null
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
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
            .padding(top = 18.dp)
    ) {
        Text(
            text = row.title,
            color = colors.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = horizontalPad, end = horizontalPad, bottom = 10.dp)
        )

        WithBringIntoViewSpec(rowBringIntoViewSpec) {
        LazyRow(
            state = rowListState,
            contentPadding = PaddingValues(horizontal = horizontalPad),
            horizontalArrangement = Arrangement.spacedBy(if (spec.preset == UiPreset.APPLE_TV) 18.dp else 14.dp)
        ) {
            when (row.style) {
                RowStyle.CONTINUE_WATCHING -> {
                    items(
                        items = rowItems,
                        key = { "${row.id}_${it.id}" },
                        contentType = { "cw_card" }
                    ) { item ->
                        val cardKey = "${row.id}_${item.id}"
                        ContinueWatchingCard(
                            item = item,
                            onFocus = {
                                onCardFocused(cardKey)
                                onItemFocus(item)
                            },
                            onClick = { onItemClick(item) },
                            onOptionsMenu = { onContinueWatchingOptions(item) },
                            modifier = focusRestoreModifier(cardKey, restoreFocusKey, restoreFocusRequester)
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
                            modifier = focusRestoreModifier(cardKey, restoreFocusKey, restoreFocusRequester)
                        )
                    }
                }
                else -> {
                    items(
                        items = rowItems,
                        key = { "${row.id}_${it.id}" },
                        contentType = { "poster_card" }
                    ) { item ->
                        val cardKey = "${row.id}_${item.id}"
                        NetflixPosterCard(
                            item = item,
                            onFocus = {
                                onCardFocused(cardKey)
                                onItemFocus(item)
                            },
                            onClick = { onItemClick(item) },
                            onOptionsMenu = { onPosterOptions(item) },
                            modifier = focusRestoreModifier(cardKey, restoreFocusKey, restoreFocusRequester)
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

@Composable
private fun NetflixNavRail(
    activeTab: HomeTab,
    activeDownloadCount: Int,
    isSeerrConfigured: Boolean,
    hasActiveSyncPlay: Boolean,
    hasUpdate: Boolean,
    profileName: String,
    strings: UiStrings,
    activeTabFocusRequester: FocusRequester? = null,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenCustomizeHome: () -> Unit,
    onOpenSyncPlay: () -> Unit,
    onOpenUpdate: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
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
            .verticalScroll(rememberScrollState())
            .padding(vertical = 14.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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

            NavRailItem(
                icon = Icons.Filled.Search,
                label = strings.navSearch,
                expanded = expanded,
                selected = false,
                onClick = onOpenSearch
            )
            NavRailItem(
                icon = Icons.Filled.Home,
                label = strings.navHome,
                expanded = expanded,
                selected = activeTab == HomeTab.HOME,
                onClick = { onSelectTab(HomeTab.HOME) },
                modifier = activeTabModifier(activeTab == HomeTab.HOME, activeTabFocusRequester)
            )
            NavRailItem(
                icon = Icons.Filled.NewReleases,
                label = strings.navNewAndPopular,
                expanded = expanded,
                selected = activeTab == HomeTab.NEW_AND_POPULAR,
                onClick = { onSelectTab(HomeTab.NEW_AND_POPULAR) },
                modifier = activeTabModifier(activeTab == HomeTab.NEW_AND_POPULAR, activeTabFocusRequester)
            )
            NavRailItem(
                icon = Icons.Filled.Tv,
                label = strings.navShows,
                expanded = expanded,
                selected = activeTab == HomeTab.SHOWS,
                onClick = { onSelectTab(HomeTab.SHOWS) },
                modifier = activeTabModifier(activeTab == HomeTab.SHOWS, activeTabFocusRequester)
            )
            NavRailItem(
                icon = Icons.Filled.Movie,
                label = strings.navMovies,
                expanded = expanded,
                selected = activeTab == HomeTab.MOVIES,
                onClick = { onSelectTab(HomeTab.MOVIES) },
                modifier = activeTabModifier(activeTab == HomeTab.MOVIES, activeTabFocusRequester)
            )
            NavRailItem(
                icon = Icons.Filled.VideoLibrary,
                label = strings.navCollections,
                expanded = expanded,
                selected = activeTab == HomeTab.COLLECTIONS,
                onClick = { onSelectTab(HomeTab.COLLECTIONS) },
                modifier = activeTabModifier(activeTab == HomeTab.COLLECTIONS, activeTabFocusRequester)
            )
            NavRailItem(
                icon = Icons.Filled.LiveTv,
                label = strings.navLiveTv,
                expanded = expanded,
                selected = activeTab == HomeTab.LIVE_TV,
                onClick = { onSelectTab(HomeTab.LIVE_TV) },
                modifier = activeTabModifier(activeTab == HomeTab.LIVE_TV, activeTabFocusRequester)
            )
            NavRailItem(
                icon = Icons.Filled.Bookmark,
                label = strings.navMyList,
                expanded = expanded,
                selected = activeTab == HomeTab.MY_LIST,
                onClick = { onSelectTab(HomeTab.MY_LIST) },
                modifier = activeTabModifier(activeTab == HomeTab.MY_LIST, activeTabFocusRequester)
            )
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

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            NavRailItem(
                icon = Icons.Filled.Groups,
                label = strings.actionWatchTogether,
                expanded = expanded,
                selected = hasActiveSyncPlay,
                onClick = onOpenSyncPlay
            )
            NavRailItem(
                icon = Icons.Filled.Tune,
                label = strings.actionCustomizeHome,
                expanded = expanded,
                selected = false,
                onClick = onOpenCustomizeHome
            )
            NavRailItem(
                icon = Icons.Filled.Palette,
                label = strings.homeAppearance,
                expanded = expanded,
                selected = false,
                onClick = onOpenAppearance
            )
            if (hasUpdate) {
                NavRailItem(
                    icon = Icons.Filled.SystemUpdate,
                    label = strings.homeAppUpdate,
                    expanded = expanded,
                    selected = true,
                    onClick = onOpenUpdate
                )
            }
            NavRailItem(
                icon = Icons.Filled.Refresh,
                label = strings.commonRefresh,
                expanded = expanded,
                selected = false,
                onClick = onRefresh
            )
            NavRailItem(
                icon = Icons.Filled.Person,
                label = profileName,
                expanded = expanded,
                selected = false,
                onClick = onSwitchProfile
            )
            NavRailItem(
                icon = Icons.Filled.Settings,
                label = strings.navSettings,
                expanded = expanded,
                selected = false,
                onClick = onOpenSettings
            )
        }
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

@Composable
private fun NavRailItem(
    icon: ImageVector,
    label: String,
    expanded: Boolean,
    selected: Boolean,
    badgeCount: Int = 0,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    focused -> colors.surfaceElevated
                    selected -> colors.surface
                    else -> Color.Transparent
                }
            )
            .then(
                if (focused) Modifier.border(1.5.dp, colors.focusBorder, RoundedCornerShape(6.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(if (selected) colors.accent else Color.Transparent)
        )
        Spacer(Modifier.width(8.dp))
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (focused || selected) colors.white else colors.textSecondary,
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(colors.downloadingBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.coerceAtMost(9).toString(),
                        color = colors.white,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        if (expanded) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = label,
                color = if (focused || selected) colors.white else colors.textSecondary,
                fontSize = 14.sp,
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
                                    text = col.completionLabel,
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

@Composable
private fun CollectionDetailsDialog(
    collection: CollectionSummary,
    isLoading: Boolean,
    onPlayItem: (UnifiedMediaItem) -> Unit,
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
                        text = collection.completionLabel,
                        color = colors.matchGreen,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
    onOpenChannelMenu: (LiveTvChannel) -> Unit = {},
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

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 36.dp, end = 36.dp, top = topPadding, bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "live_tv_header") {
            Column {
                Text(
                    text = strings.liveGuideTitle,
                    color = colors.white,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = strings.liveChannelCount.format(channels.size),
                    color = colors.textSecondary,
                    fontSize = 13.sp
                )
            }
        }

        items(channels, key = { it.id }, contentType = { "channel" }) { ch ->
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
                    // OK tunes in; holding OK opens record / cancel options for the programmes.
                    .combinedClickable(
                        onClick = { onTuneChannel(ch.id) },
                        onLongClick = { onOpenChannelMenu(ch) }
                    )
                    .padding(14.dp)
            ) {
                // Channel number + logo
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(180.dp)
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
                    Text(
                        text = ch.name,
                        color = colors.white,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                                text = rowTitle,
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
                            text = strings.syncPlayViewers.format(activeGroup.participants.size.coerceAtLeast(1), activeGroup.state),
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
                                    text = strings.syncPlayParticipants.format(grp.participants.size, grp.state),
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
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogShape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val strings = AppStrings.current
    val closeFocusRequester = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { closeFocusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(500.dp)
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
            Text(
                text = strings.updateReleaseLink.format(updateInfo.htmlUrl),
                color = colors.matchGreen,
                fontSize = 12.sp
            )
            SmallTextButton(
                text = strings.commonClose,
                isPrimary = true,
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().focusRequester(closeFocusRequester)
            )
        }
    }
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

/** Hold-OK menu on a Live TV channel: watch, record this / next programme, record or cancel the series. */
@Composable
private fun LiveTvProgramDialog(
    channel: LiveTvChannel,
    onWatch: () -> Unit,
    onRecord: (programId: String, series: Boolean) -> Unit,
    onCancel: (timerId: String) -> Unit,
    onCancelSeries: (seriesTimerId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80L)
        runCatching { firstFocus.requestFocus() }
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(RoundedCornerShape(spec.dialogCornerRadiusDp.dp))
                .background(colors.dialogSurface)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(channel.name, color = colors.white, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(strings.dvrProgramOptions, color = colors.textSecondary, fontSize = 13.sp)
            DvrMenuButton(strings.dvrWatchLive, onWatch, Modifier.focusRequester(firstFocus))
            listOfNotNull(channel.currentProgram, channel.nextProgram).forEach { program ->
                Text(
                    "${program.title}${if (program.timeRangeLabel.isNotBlank()) "  •  ${program.timeRangeLabel}" else ""}",
                    color = colors.white,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp)
                )
                val timerId = program.timerId
                if (!timerId.isNullOrBlank()) {
                    DvrMenuButton(strings.dvrCancelRecording, { onCancel(timerId) })
                } else {
                    DvrMenuButton(strings.dvrRecord, { onRecord(program.id, false) })
                }
                val seriesTimerId = program.seriesTimerId
                if (!seriesTimerId.isNullOrBlank()) {
                    DvrMenuButton(strings.dvrCancelSeries, { onCancelSeries(seriesTimerId) })
                } else if (program.isSeries) {
                    DvrMenuButton(strings.dvrRecordSeries, { onRecord(program.id, true) })
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
