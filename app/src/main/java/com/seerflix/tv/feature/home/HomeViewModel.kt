package com.seerflix.tv.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.seerflix.tv.core.data.AppUpdateChecker
import com.seerflix.tv.core.data.AppUpdateInstallState
import com.seerflix.tv.core.data.AppUpdateInstaller
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.HomeContentCache
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.i18n.stringsForLanguage
import com.seerflix.tv.core.model.AppUpdateInfo
import com.seerflix.tv.core.model.AvailabilityFilter
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.ContentRatingHelper
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.CollectionSummary
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.HomeTabId
import com.seerflix.tv.core.model.JellyfinLibraryView
import com.seerflix.tv.core.model.LiveTvChannel
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.ReadyRequestAlert
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.ServerConfig
import com.seerflix.tv.core.model.SyncPlayGroup
import com.seerflix.tv.core.model.UiPreset
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.core.model.UserProfile
import com.seerflix.tv.core.network.MediaClient
import com.seerflix.tv.core.network.SessionPreferences
import com.seerflix.tv.core.player.ResolvedPreviewStream
import com.seerflix.tv.core.player.SyncPlayManager
import com.seerflix.tv.core.network.JellyfinSocketClient
import com.seerflix.tv.core.network.ServerEvent
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterIsInstance
import com.seerflix.tv.core.player.YouTubeTrailerResolver
import com.seerflix.tv.core.tv.TvChannelSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File
import javax.inject.Inject

enum class HomeTab(val label: String, val filter: MediaType?) {
    HOME("Home", null),
    SHOWS("TV Shows", MediaType.SERIES),
    MOVIES("Movies", MediaType.MOVIE),
    COLLECTIONS("Collections", null),
    LIVE_TV("Live TV", null),
    NEW_AND_POPULAR("New & Popular", null),
    MY_LIST("My List", null)
}

/** Tabs that show the All / Watch Now / Requestable filter (merged into their chip row). */
val HomeTab.showsAvailabilityFilter: Boolean
    get() = this == HomeTab.SHOWS || this == HomeTab.MOVIES || this == HomeTab.NEW_AND_POPULAR

fun HomeTabId.toHomeTab(): HomeTab = when (this) {
    HomeTabId.HOME -> HomeTab.HOME
    HomeTabId.SHOWS -> HomeTab.SHOWS
    HomeTabId.MOVIES -> HomeTab.MOVIES
    HomeTabId.COLLECTIONS -> HomeTab.COLLECTIONS
    HomeTabId.LIVE_TV -> HomeTab.LIVE_TV
    HomeTabId.NEW_AND_POPULAR -> HomeTab.NEW_AND_POPULAR
    HomeTabId.MY_LIST -> HomeTab.MY_LIST
}

fun HomeTab.toHomeTabId(): HomeTabId = when (this) {
    HomeTab.HOME -> HomeTabId.HOME
    HomeTab.SHOWS -> HomeTabId.SHOWS
    HomeTab.MOVIES -> HomeTabId.MOVIES
    HomeTab.COLLECTIONS -> HomeTabId.COLLECTIONS
    HomeTab.LIVE_TV -> HomeTabId.LIVE_TV
    HomeTab.NEW_AND_POPULAR -> HomeTabId.NEW_AND_POPULAR
    HomeTab.MY_LIST -> HomeTabId.MY_LIST
}

@Immutable
data class HomeUiState(
    val activeTab: HomeTab = HomeTab.HOME,
    val selectedGenre: GenreFilterOption = GenreFilterOption.ALL_GENRES.first(),
    val availabilityFilter: AvailabilityFilter = AvailabilityFilter.ALL,
    val profile: UserProfile? = null,
    val isSeerrConfigured: Boolean = true,
    val rows: List<CatalogRow> = emptyList(),
    val collections: List<CollectionSummary> = emptyList(),
    val liveTvChannels: List<LiveTvChannel> = emptyList(),
    val liveTvRecordings: List<UnifiedMediaItem> = emptyList(),
    val liveTvScheduled: List<com.seerflix.tv.core.model.ScheduledRecording> = emptyList(),
    /** Channel whose programme options (record / cancel) are open. */
    val liveTvMenuChannel: LiveTvChannel? = null,
    /** Specific EPG programme selected in the 2D time-grid (null = show current + next). */
    val liveTvMenuProgram: com.seerflix.tv.core.model.LiveTvProgram? = null,
    /** "single" / "series" / "cancelled" / "error:<msg>" after a DVR change, shown briefly. */
    val dvrFeedback: String? = null,
    val libraryViews: List<JellyfinLibraryView> = emptyList(),
    val playlists: List<com.seerflix.tv.core.model.PlaylistSummary> = emptyList(),
    val activePlaylistDetails: com.seerflix.tv.core.model.PlaylistSummary? = null,
    val playlistDetailsLoading: Boolean = false,
    val showLibrariesDialog: Boolean = false,
    val readyAlerts: List<ReadyRequestAlert> = emptyList(),
    val syncPlayGroups: List<SyncPlayGroup> = emptyList(),
    val activeSyncPlayGroup: SyncPlayGroup? = null,
    val updateInfo: AppUpdateInfo? = null,
    val updateInstallState: AppUpdateInstallState = AppUpdateInstallState.Idle,
    val activeCollectionDetails: CollectionSummary? = null,
    val collectionDetailsLoading: Boolean = false,
    val featuredItem: UnifiedMediaItem? = null,
    val previewStream: ResolvedPreviewStream? = null,
    val isPreviewMuted: Boolean = true,
    val activeDownloadCount: Int = 0,
    val has4kMovieServer: Boolean = true,
    val has4kTvServer: Boolean = true,
    val continueWatchingMenuTarget: UnifiedMediaItem? = null,
    val posterMenuTarget: UnifiedMediaItem? = null,
    val showAppearanceDialog: Boolean = false,
    val showCustomizeHomeDialog: Boolean = false,
    val showSyncPlayDialog: Boolean = false,
    val showUpdateDialog: Boolean = false,
    val actionFeedbackMessage: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null
)

@OptIn(FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val catalogRepository: UnifiedCatalogRepository,
    private val trailerResolver: YouTubeTrailerResolver,
    private val tvChannelSyncManager: TvChannelSyncManager,
    private val syncPlayManager: SyncPlayManager,
    private val appUpdateChecker: AppUpdateChecker,
    private val appUpdateInstaller: AppUpdateInstaller,
    private val prefs: SessionPreferences,
    /** Honours the self-signed-certificate toggle; used by the hero trailer preview player. */
    @MediaClient val previewHttpClient: OkHttpClient,
    /** Last good rows per (profile, tab, genre, filter): painted instantly, then revalidated. */
    private val homeContentCache: HomeContentCache,
    socketClient: JellyfinSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    /** UI strings for the active profile's language, read when a message is produced. */
    private val strings: UiStrings
        get() = stringsForLanguage(prefs.cachedUiLanguage)

    @Volatile private var focusPreviewJob: Job? = null
    /** The item the hero is showing or about to show (the target of [focusPreviewJob]). */
    @Volatile private var focusTargetId: String? = null
    /** False while Home is scrolled into the rows (the hero, and so its trailer, is off screen). */
    @Volatile private var heroVisible = true

    fun setHeroVisible(visible: Boolean) {
        if (heroVisible == visible) return
        heroVisible = visible
        if (visible) {
            // Back at the top: resolve the trailer that was skipped while scrolled away.
            val state = _uiState.value
            val featured = state.featuredItem ?: return
            if (state.previewStream == null && state.profile?.autoplayPreviews == true) {
                onItemFocused(featured, force = true)
            }
        }
    }
    private var feedbackJob: Job? = null
    private var catalogJob: Job? = null
    private var collectionJob: Job? = null
    private var auxiliaryJob: Job? = null
    private var appUpdateJob: Job? = null

    /**
     * Cache key of the row content currently in [HomeUiState.rows] plus the rows as they were
     * loaded. In-place edits (My List, watched, Continue Watching) produce a different list, which
     * is written back when the user leaves that view so its cached copy doesn't resurrect them.
     */
    @Volatile
    private var displayedContent: Pair<HomeContentCache.Key, List<CatalogRow>>? = null

    /** Monotonic token so a superseded catalog load can never overwrite a newer one. */
    @Volatile
    private var catalogGeneration = 0

    /** Tab whose content is currently held in [HomeUiState.rows]/collections/live TV. */
    @Volatile
    private var lastLoadedTab: HomeTab? = null
    /** When the current tab's data last came from the network ([android.os.SystemClock.elapsedRealtime]). */
    private var lastNetworkLoadAtMs: Long = 0L

    /** True while Home is the screen on top (resumed). */
    @Volatile
    private var isScreenVisible = false

    /** The server reported a library change while Home was hidden: refresh when it comes back. */
    @Volatile
    private var pendingLibraryRefresh = false

    init {
        // A title deleted from its Details page disappears from Home right away.
        viewModelScope.launch {
            catalogRepository.deletedItemIds.collect { deleted ->
                if (deleted.isEmpty()) return@collect
                val before = _uiState.value.featuredItem
                val after = _uiState.updateAndGet { state ->
                    val rows = state.rows.withoutDeleted()
                    val featuredGone = state.featuredItem?.isDeletedBy(deleted) == true
                    state.copy(
                        rows = rows,
                        featuredItem = if (!featuredGone) state.featuredItem
                            else rows.firstOrNull()?.items?.firstOrNull(),
                        // The deleted title's trailer must not keep playing under the new hero.
                        previewStream = if (featuredGone) null else state.previewStream
                    )
                }.featuredItem
                if (after != null && after.id != before?.id) onItemFocused(after, force = true)
            }
        }
        // Live updates pushed by the server: new / removed titles (debounced, a library scan
        // sends bursts) and watch state changed on another device.
        viewModelScope.launch {
            socketClient.events
                .filterIsInstance<ServerEvent.LibraryChanged>()
                .debounce(LIBRARY_CHANGE_DEBOUNCE_MS)
                .collect {
                    if (isScreenVisible) refreshInBackground() else pendingLibraryRefresh = true
                }
        }
        // Another server was added, removed or signed in to: its titles join / leave the rows.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(prefs.serversFlow, authRepository.activeProfileFlow) { servers, profile ->
                servers.filter { !it.isHome && profile?.serverCredentials?.containsKey(it.id) == true }.map { it.id }
            }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    if (isScreenVisible) refreshInBackground() else pendingLibraryRefresh = true
                }
        }
        viewModelScope.launch {
            socketClient.events
                .filterIsInstance<ServerEvent.UserDataChanged>()
                .debounce(USER_DATA_DEBOUNCE_MS)
                .collect { if (isScreenVisible) refreshContinueWatching() }
        }
        viewModelScope.launch {
            authRepository.activeProfileFlow.collect { active ->
                if (active == null) return@collect
                val previous = _uiState.value.profile
                _uiState.update { it.copy(profile = active) }
                // The launcher's Watch Next row is shared by every profile: "Ready" promotions made
                // while an adult profile was active must not stay there for this one.
                if (active.isRestricted && (previous == null || previous.id != active.id ||
                        previous.effectiveRatingLimit != active.effectiveRatingLimit || previous.kidsMode != active.kidsMode)
                ) {
                    // removeReadyPromotions() swallows provider errors itself.
                    viewModelScope.launch {
                        tvChannelSyncManager.removeReadyPromotions { jfId ->
                            catalogRepository.isLibraryTitleAllowed(jfId, active)
                        }
                    }
                }
                // Home stays on the back stack while the profile is edited (Manage Profiles,
                // Settings): a new rating limit / Kids mode / row layout / language must not keep
                // showing rows built under the old rules.
                if (previous != null && previous.homeContentSignature() != active.homeContentSignature()) {
                    val ratingChanged = previous.effectiveRatingLimit != active.effectiveRatingLimit ||
                        previous.kidsMode != active.kidsMode || previous.id != active.id
                    if (ratingChanged) {
                        lastLoadedTab = null
                        displayedContent = null
                        _uiState.update { it.copy(rows = emptyList(), featuredItem = null, previewStream = null) }
                    }
                    val s = _uiState.value
                    loadCatalog(s.activeTab, s.selectedGenre, s.availabilityFilter)
                }
            }
        }
        viewModelScope.launch {
            prefs.serverConfigFlow.collect { cfg ->
                _uiState.update { it.copy(isSeerrConfigured = cfg.isSeerrConfigured) }
            }
        }
        viewModelScope.launch {
            prefs.readyAlertsFlow.collect { alerts ->
                // Alerts are stored per device, not per profile, and carry no rating: a Kids /
                // rating-limited profile would see (and could play) an adult's fulfilled request.
                _uiState.update { it.copy(readyAlerts = if (it.profile?.isRestricted == true) emptyList() else alerts) }
            }
        }
        viewModelScope.launch {
            syncPlayManager.availableGroups.collect { groups ->
                _uiState.update { it.copy(syncPlayGroups = groups) }
            }
        }
        viewModelScope.launch {
            syncPlayManager.activeGroup.collect { active ->
                _uiState.update { it.copy(activeSyncPlayGroup = active) }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            val initialProfile = authRepository.activeProfileFlow.first()
            val startTab = initialProfile?.defaultStartupTab?.toHomeTab() ?: HomeTab.HOME
            loadCatalog(startTab, GenreFilterOption.ALL_GENRES.first(), AvailabilityFilter.ALL)
        }
        viewModelScope.launch(Dispatchers.IO) {
            val upd = runCatching { appUpdateChecker.checkForUpdate() }.getOrNull()
            if (upd != null) {
                _uiState.update { it.copy(updateInfo = upd) }
            } else {
                // Up to date (e.g. right after installing an update): reclaim cached APK space.
                runCatching { appUpdateInstaller.cleanupCachedApks() }
            }
        }
    }

    fun openAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = true) }
    }

    fun dismissAppearanceDialog() {
        _uiState.update { it.copy(showAppearanceDialog = false) }
    }

    fun selectUiPreset(preset: UiPreset) {
        _uiState.update { it.copy(showAppearanceDialog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.updateActiveUiPreset(preset)
            showActionFeedback(strings.homePresetSwitched.format(preset.displayName))
        }
    }

    fun openCustomizeHomeDialog() {
        _uiState.update { it.copy(showCustomizeHomeDialog = true) }
        val userId = _uiState.value.profile?.jellyfinUserId ?: return
        if (_uiState.value.libraryViews.isEmpty()) {
            viewModelScope.launch(Dispatchers.IO) {
                val views = catalogRepository.loadUserLibraryViews(userId)
                _uiState.update { it.copy(libraryViews = views) }
            }
        }
    }

    fun dismissCustomizeHomeDialog() {
        _uiState.update { it.copy(showCustomizeHomeDialog = false) }
    }

    fun openLibrariesDialog() {
        _uiState.update { it.copy(showLibrariesDialog = true) }
        val userId = _uiState.value.profile?.jellyfinUserId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val views = catalogRepository.loadUserLibraryViews(userId)
            _uiState.update { it.copy(libraryViews = views) }
        }
    }

    fun dismissLibrariesDialog() {
        _uiState.update { it.copy(showLibrariesDialog = false) }
    }

    private var playlistJob: Job? = null

    fun openPlaylist(playlist: com.seerflix.tv.core.model.PlaylistSummary) {
        val profile = _uiState.value.profile ?: return
        playlistJob?.cancel()
        _uiState.update { it.copy(activePlaylistDetails = playlist, playlistDetailsLoading = true) }
        playlistJob = viewModelScope.launch(Dispatchers.IO) {
            val items = catalogRepository.loadPlaylistItems(profile.jellyfinUserId, playlist.id, profile)
            _uiState.update {
                it.copy(
                    activePlaylistDetails = playlist.copy(items = items, itemCount = items.size),
                    playlistDetailsLoading = false
                )
            }
        }
    }

    fun dismissPlaylistDetails() {
        playlistJob?.cancel()
        _uiState.update { it.copy(activePlaylistDetails = null, playlistDetailsLoading = false) }
    }

    fun removePlaylistItem(playlistId: String, entryId: String) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.removeFromPlaylist(profile.jellyfinUserId, playlistId, listOf(entryId))
                .onSuccess {
                    // Read and write inside update{}: two removals finishing together must not
                    // bring the first removed item back.
                    _uiState.update { s ->
                        val active = s.activePlaylistDetails?.takeIf { it.id == playlistId } ?: return@update s
                        val updatedItems = active.items.filterNot { it.playlistItemId == entryId }
                        s.copy(
                            activePlaylistDetails = active.copy(items = updatedItems, itemCount = updatedItems.size),
                            playlists = s.playlists.map {
                                if (it.id == playlistId) it.copy(itemCount = updatedItems.size) else it
                            }
                        )
                    }
                    showActionFeedback(strings.playlistRemovedFeedback)
                }
        }
    }

    fun saveHomeCustomization(
        pinnedLibraryIds: List<String>,
        hiddenRowIds: List<String>,
        rowOrder: List<String>,
        defaultStartupTab: HomeTabId
    ) {
        val current = _uiState.value.profile ?: return
        _uiState.update { it.copy(showCustomizeHomeDialog = false) }
        viewModelScope.launch(Dispatchers.IO) {
            authRepository.updateProfile(current.id) {
                it.copy(
                    pinnedLibraryIds = pinnedLibraryIds,
                    hiddenHomeRowIds = hiddenRowIds,
                    homeRowOrder = rowOrder,
                    defaultStartupTab = defaultStartupTab
                )
            }
            showActionFeedback(strings.homeLayoutSaved)
            // The active-profile collector reloads the rows when the layout actually changed.
        }
    }

    fun openSyncPlayDialog() {
        _uiState.update { it.copy(showSyncPlayDialog = true) }
        refreshSyncPlayGroups()
    }

    fun dismissSyncPlayDialog() {
        _uiState.update { it.copy(showSyncPlayDialog = false) }
    }

    fun refreshSyncPlayGroups() {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.refreshGroups()
        }
    }

    fun createSyncPlayGroup(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.createGroup(name)
                .onSuccess { grp ->
                    showActionFeedback(strings.syncPlayStarted.format(grp?.groupName ?: name))
                }
                .onFailure { err ->
                    val s = strings
                    showActionFeedback(s.syncPlayUnavailable.format(err.message ?: s.syncPlayCheckJellyfin))
                }
        }
    }

    fun joinSyncPlayGroup(groupId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.joinGroup(groupId)
                .onSuccess { grp ->
                    val s = strings
                    showActionFeedback(s.syncPlayJoined.format(grp?.groupName ?: s.syncPlayRoomFallback))
                }
                .onFailure { err ->
                    val s = strings
                    showActionFeedback(s.syncPlayJoinFailed.format(err.message ?: s.commonError))
                }
        }
    }

    fun leaveSyncPlayGroup() {
        viewModelScope.launch(Dispatchers.IO) {
            syncPlayManager.leaveGroup()
                .onSuccess { showActionFeedback(strings.syncPlayLeft) }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    val s = strings
                    showActionFeedback(s.syncPlayLeaveFailed.format(err.message ?: s.commonError))
                }
        }
    }

    fun openUpdateDialog() {
        _uiState.update { it.copy(showUpdateDialog = true) }
    }

    fun dismissUpdateDialog() {
        val currentState = _uiState.value.updateInstallState
        if (currentState is AppUpdateInstallState.Downloading) {
            appUpdateJob?.cancel()
            appUpdateJob = null
        }
        _uiState.update {
            it.copy(
                showUpdateDialog = false,
                updateInstallState = when (currentState) {
                    is AppUpdateInstallState.Downloading,
                    is AppUpdateInstallState.Failed -> AppUpdateInstallState.Idle
                    else -> currentState
                }
            )
        }
    }

    fun startAppUpdate() {
        val currentState = _uiState.value.updateInstallState
        if (currentState is AppUpdateInstallState.Downloading) return

        val existingPath = when (currentState) {
            is AppUpdateInstallState.ReadyToInstall -> currentState.apkPath
            is AppUpdateInstallState.NeedsInstallPermission -> currentState.apkPath
            else -> null
        }
        if (existingPath != null && File(existingPath).isFile) {
            triggerInstallOrPermission(existingPath)
            return
        }

        val info = _uiState.value.updateInfo ?: return
        appUpdateJob?.cancel()
        _uiState.update { it.copy(updateInstallState = AppUpdateInstallState.Downloading(0L, -1L)) }
        appUpdateJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val apkFile = appUpdateInstaller.downloadApk(info) { bytesRead, totalBytes ->
                    _uiState.update {
                        it.copy(
                            updateInstallState = AppUpdateInstallState.Downloading(
                                bytesRead = bytesRead,
                                totalBytes = totalBytes
                            )
                        )
                    }
                }
                withContext(Dispatchers.Main) {
                    triggerInstallOrPermission(apkFile.absolutePath)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        updateInstallState = AppUpdateInstallState.Failed(
                            e.message?.takeIf { msg -> msg.isNotBlank() } ?: strings.updateDownloadFailed
                        )
                    )
                }
            }
        }
    }

    fun cancelAppUpdate() {
        appUpdateJob?.cancel()
        appUpdateJob = null
        _uiState.update { it.copy(updateInstallState = AppUpdateInstallState.Idle) }
    }

    fun onResumeAppUpdateDialog() {
        val state = _uiState.value.updateInstallState
        if (state is AppUpdateInstallState.NeedsInstallPermission &&
            appUpdateInstaller.canRequestPackageInstalls()
        ) {
            triggerInstallOrPermission(state.apkPath)
        }
    }

    private fun triggerInstallOrPermission(apkPath: String) {
        if (appUpdateInstaller.canRequestPackageInstalls()) {
            if (appUpdateInstaller.launchInstaller(apkPath)) {
                _uiState.update { it.copy(updateInstallState = AppUpdateInstallState.ReadyToInstall(apkPath)) }
            } else {
                _uiState.update {
                    it.copy(updateInstallState = AppUpdateInstallState.Failed(strings.updateInstallerUnavailable))
                }
            }
            return
        }
        if (appUpdateInstaller.openUnknownAppSourcesSettings()) {
            _uiState.update { it.copy(updateInstallState = AppUpdateInstallState.NeedsInstallPermission(apkPath)) }
        } else if (appUpdateInstaller.launchInstaller(apkPath)) {
            // Some Android TV / Fire TV boxes omit ACTION_MANAGE_UNKNOWN_APP_SOURCES and prompt
            // for unknown-sources permission inline when the package installer launches.
            _uiState.update { it.copy(updateInstallState = AppUpdateInstallState.ReadyToInstall(apkPath)) }
        } else {
            _uiState.update { it.copy(updateInstallState = AppUpdateInstallState.NeedsInstallPermission(apkPath)) }
        }
    }

    fun dismissReadyAlert(requestId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.dismissReadyAlert(requestId)
        }
    }

    fun resolveReadyAlertPlayId(alert: ReadyRequestAlert): String? {
        return alert.jellyfinId ?: catalogRepository.findJellyfinIdForTmdb(alert.tmdbId, alert.mediaType)
    }

    fun selectTab(tab: HomeTab) {
        val state = _uiState.value
        if (state.activeTab == tab) {
            // Re-selecting the tab that is already loading: don't restart the same request.
            if (state.isLoading && catalogJob?.isActive == true) return
            val alreadyLoaded = lastLoadedTab == tab && when (tab) {
                HomeTab.COLLECTIONS -> state.collections.isNotEmpty()
                HomeTab.LIVE_TV -> state.liveTvChannels.isNotEmpty() || state.liveTvRecordings.isNotEmpty()
                else -> state.rows.isNotEmpty()
            }
            if (alreadyLoaded) return
        }
        // Home / My List have no filter bar any more, so they always show everything; otherwise a
        // "Requestable" picked on Movies would silently keep filtering Home with no visible control.
        val filter = if (tab.showsAvailabilityFilter) state.availabilityFilter else AvailabilityFilter.ALL
        loadCatalog(tab, GenreFilterOption.ALL_GENRES.first(), filter)
    }

    fun selectGenre(genre: GenreFilterOption) {
        val currentTab = _uiState.value.activeTab
        if (currentTab != HomeTab.SHOWS && currentTab != HomeTab.MOVIES) return
        if (_uiState.value.selectedGenre.id == genre.id) return
        loadCatalog(currentTab, genre, _uiState.value.availabilityFilter)
    }

    fun selectAvailabilityFilter(filter: AvailabilityFilter) {
        if (_uiState.value.availabilityFilter == filter) return
        loadCatalog(_uiState.value.activeTab, _uiState.value.selectedGenre, filter)
    }

    fun refresh() {
        loadCatalog(
            tab = _uiState.value.activeTab,
            genre = _uiState.value.selectedGenre,
            availabilityFilter = _uiState.value.availabilityFilter,
            forceRefresh = true
        )
    }

    private var continueWatchingJob: Job? = null

    // ---- DVR ----

    fun openLiveTvMenu(channel: LiveTvChannel, program: com.seerflix.tv.core.model.LiveTvProgram? = null) {
        _uiState.update { it.copy(liveTvMenuChannel = channel, liveTvMenuProgram = program) }
    }

    fun dismissLiveTvMenu() {
        _uiState.update { it.copy(liveTvMenuChannel = null, liveTvMenuProgram = null) }
    }

    fun toggleLiveTvChannelFavorite(channel: LiveTvChannel) {
        val userId = _uiState.value.profile?.jellyfinUserId?.takeIf { it.isNotBlank() } ?: return
        val newFav = !channel.isFavorite
        fun applyFavorite(favorite: Boolean) = _uiState.update { state ->
            // Not re-sorted here: the focused row would jump elsewhere in the guide. The server's
            // favourites-first order applies on the next load.
            val updatedChannels = state.liveTvChannels.map {
                if (it.id == channel.id) it.copy(isFavorite = favorite) else it
            }
            val updatedMenu = state.liveTvMenuChannel?.let {
                if (it.id == channel.id) it.copy(isFavorite = favorite) else it
            }
            state.copy(liveTvChannels = updatedChannels, liveTvMenuChannel = updatedMenu)
        }
        applyFavorite(newFav)
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.toggleLiveTvChannelFavorite(userId, channel.id, newFav)
                .onFailure { applyFavorite(!newFav) }
        }
    }

    /** Record one programme, or with [series] every airing of it, applying [options]. */
    fun recordProgram(
        programId: String,
        series: Boolean,
        options: com.seerflix.tv.core.model.DvrTimerOptions = com.seerflix.tv.core.model.DvrTimerOptions()
    ) = changeRecording(
        successMessage = if (series) "series" else "single"
    ) { catalogRepository.scheduleRecording(programId, series, options) }

    fun cancelRecording(timerId: String) = changeRecording("cancelled") { catalogRepository.cancelRecording(timerId) }

    fun cancelSeriesRecording(seriesTimerId: String) =
        changeRecording("cancelled") { catalogRepository.cancelSeriesRecording(seriesTimerId) }

    private var dvrFeedbackClearJob: Job? = null

    private fun changeRecording(successMessage: String, action: suspend () -> Unit) {
        _uiState.update { it.copy(liveTvMenuChannel = null, liveTvMenuProgram = null) }
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching { action() }
            _uiState.update { it.copy(dvrFeedback = if (result.isSuccess) successMessage else "error:${result.exceptionOrNull()?.message.orEmpty()}") }
            // Pick up the new REC badges / scheduled list without a spinner.
            if (_uiState.value.activeTab == HomeTab.LIVE_TV) refreshInBackground()
            // A newer message restarts the timer instead of being cleared by the older one.
            dvrFeedbackClearJob?.cancel()
            dvrFeedbackClearJob = viewModelScope.launch {
                delay(3500L)
                _uiState.update { it.copy(dvrFeedback = null) }
            }
        }
    }

    fun onScreenResumed() {
        isScreenVisible = true
        val liveTvStale = _uiState.value.activeTab == HomeTab.LIVE_TV &&
            android.os.SystemClock.elapsedRealtime() - lastNetworkLoadAtMs > LIVE_TV_STALE_MS
        if (pendingLibraryRefresh || liveTvStale) {
            // Live TV: "On now" / the guide move on while the user is watching a channel.
            pendingLibraryRefresh = false
            refreshInBackground()
        } else {
            refreshContinueWatching()
        }
    }

    fun onScreenPaused() {
        isScreenVisible = false
    }

    /** Fresh rows swapped in under the user: no spinner, hero and focus stay where they are. */
    private fun refreshInBackground() {
        val state = _uiState.value
        if (state.isLoading || catalogJob?.isActive == true || lastLoadedTab != state.activeTab) return
        loadCatalog(
            tab = state.activeTab,
            genre = state.selectedGenre,
            availabilityFilter = state.availabilityFilter,
            forceRefresh = true,
            background = true
        )
    }

    /**
     * Called whenever Home becomes visible again (e.g. back from the player). Only swaps the
     * Continue Watching row in place so focus and the rest of the page stay put.
     */
    fun refreshContinueWatching() {
        val state = _uiState.value
        val tab = state.activeTab
        if ((tab != HomeTab.HOME && tab != HomeTab.MOVIES && tab != HomeTab.SHOWS) ||
            state.availabilityFilter != AvailabilityFilter.ALL ||
            // A genre page has no Continue Watching row; don't inject an unfiltered one.
            state.selectedGenre.id != GenreFilterOption.ALL_GENRES.first().id
        ) return
        if (lastLoadedTab != tab || state.isLoading || catalogJob?.isActive == true) return
        if (continueWatchingJob?.isActive == true) return
        val profile = state.profile ?: return
        continueWatchingJob = viewModelScope.launch(Dispatchers.IO) {
            val (allCw, allNextUp) = try {
                catalogRepository.loadContinueWatchingAndNextUp(profile.jellyfinUserId, profile)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@launch
            }
            val cwItems = when (tab) {
                HomeTab.MOVIES -> allCw.filter { it.mediaType == MediaType.MOVIE }
                HomeTab.SHOWS -> allCw.filter { it.mediaType != MediaType.MOVIE }
                else -> allCw
            }
            val nextUpItems = if (tab == HomeTab.MOVIES) emptyList() else allNextUp
            val cwStrings = strings
            val cwTitle = profile.name.trim().takeIf { it.isNotEmpty() }
                ?.let { cwStrings.homeContinueWatchingFor.format(it) }
                ?: cwStrings.rowContinueWatching
            val hiddenRows = profile.hiddenHomeRowIds.toSet()
            _uiState.update { s ->
                // The page may have changed to a filtered / genre view (or another tab) meanwhile.
                if (s.activeTab != tab || s.isLoading || lastLoadedTab != tab ||
                    s.availabilityFilter != AvailabilityFilter.ALL ||
                    s.selectedGenre.id != GenreFilterOption.ALL_GENRES.first().id
                ) return@update s
                var updated = s.rows.toMutableList()
                val cwIdx = updated.indexOfFirst { it.id == "continue_watching" }
                when {
                    cwIdx >= 0 && (cwItems.isEmpty() || "continue_watching" in hiddenRows) -> updated.removeAt(cwIdx)
                    cwIdx >= 0 -> updated[cwIdx] = updated[cwIdx].copy(title = cwTitle, items = cwItems)
                    cwItems.isNotEmpty() && "continue_watching" !in hiddenRows -> {
                        val insertAt = if (updated.firstOrNull()?.id == "ready_to_watch") 1 else 0
                        updated.add(
                            insertAt,
                            CatalogRow(
                                id = "continue_watching",
                                title = cwTitle,
                                style = RowStyle.CONTINUE_WATCHING,
                                items = cwItems
                            )
                        )
                    }
                }
                val nuIdx = updated.indexOfFirst { it.id == "next_up" }
                when {
                    nuIdx >= 0 && (nextUpItems.isEmpty() || "next_up" in hiddenRows) -> updated.removeAt(nuIdx)
                    nuIdx >= 0 -> updated[nuIdx] = updated[nuIdx].copy(items = nextUpItems)
                    nextUpItems.isNotEmpty() && "next_up" !in hiddenRows -> {
                        val afterCw = updated.indexOfFirst { it.id == "continue_watching" }
                        val insertAt = if (afterCw >= 0) afterCw + 1 else if (updated.firstOrNull()?.id == "ready_to_watch") 1 else 0
                        updated.add(
                            insertAt,
                            CatalogRow(
                                id = "next_up",
                                title = "Next Up",
                                style = RowStyle.CONTINUE_WATCHING,
                                items = nextUpItems
                            )
                        )
                    }
                }
                if (updated == s.rows) s else s.copy(rows = updated)
            }
            // Keep the launcher's Watch Next row in step with the progress just made (it was
            // only refreshed on a full Home load).
            if (tab == HomeTab.HOME) {
                val rows = _uiState.value.rows
                val channelItems = rows.firstOrNull { it.id == "trending_now" }?.items
                    ?: rows.firstOrNull { it.id != "continue_watching" }?.items.orEmpty()
                com.seerflix.tv.core.player.runCatchingCancellable {
                    tvChannelSyncManager.syncHomeChannels(cwItems, channelItems, readyPromotionFilter())
                }
            }
        }
    }

    fun togglePreviewMute() {
        _uiState.update { it.copy(isPreviewMuted = !it.isPreviewMuted) }
    }

    fun toggleFeaturedMyList() {
        val featured = _uiState.value.featuredItem ?: return
        toggleMyListForItem(featured)
    }

    fun togglePosterMyList(item: UnifiedMediaItem) {
        _uiState.update { it.copy(posterMenuTarget = null) }
        toggleMyListForItem(item)
    }

    fun togglePosterWatched(item: UnifiedMediaItem) {
        val profile = _uiState.value.profile ?: return
        val jfId = item.jellyfinId ?: return
        val nextWatched = !item.isWatched
        _uiState.update { it.copy(posterMenuTarget = null) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.markWatched(profile.jellyfinUserId, jfId, nextWatched)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedFeatured = if (state.featuredItem?.id == item.id) {
                            state.featuredItem.copy(isWatched = nextWatched)
                        } else state.featuredItem
                        val updatedRows = state.rows.map { row ->
                            row.copy(
                                items = row.items.map {
                                    if (it.id == item.id) it.copy(isWatched = nextWatched) else it
                                }
                            )
                        }
                        state.copy(featuredItem = updatedFeatured, rows = updatedRows)
                    }
                    val s = strings
                    showActionFeedback(
                        if (nextWatched) s.homeMarkedWatched.format(item.title)
                        else s.homeMarkedUnwatched.format(item.title)
                    )
                }
                .onFailure { err ->
                    val s = strings
                    showActionFeedback(s.watchedUpdateFailed.format(err.message ?: s.commonError))
                }
        }
    }

    private fun toggleMyListForItem(target: UnifiedMediaItem) {
        val profile = _uiState.value.profile ?: return
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.toggleMyList(profile.jellyfinUserId, target)
                .onSuccess { inList ->
                    _uiState.update { state ->
                        val updatedFeatured = if (state.featuredItem?.id == target.id) {
                            state.featuredItem.copy(isInMyList = inList)
                        } else state.featuredItem

                        val updatedRows = state.rows.map { row ->
                            if (row.id == "my_list" || row.id == "my_list_page") {
                                val nextItems = if (inList) {
                                    (listOf(target.copy(isInMyList = true)) + row.items).distinctBy { it.id }
                                } else {
                                    row.items.filterNot { it.id == target.id }
                                }
                                row.copy(items = nextItems)
                            } else if (!inList && (row.id == "my_list_shows" || row.id == "my_list_movies")) {
                                // My List tab split rows must also drop the removed title.
                                row.copy(items = row.items.filterNot { it.id == target.id })
                            } else {
                                row.copy(
                                    items = row.items.map {
                                        if (it.id == target.id) it.copy(isInMyList = inList) else it
                                    }
                                )
                            }
                        }.filter { it.items.isNotEmpty() }

                        state.copy(featuredItem = updatedFeatured, rows = updatedRows)
                    }
                    val s = strings
                    showActionFeedback(
                        if (inList) s.homeAddedToMyList.format(target.title)
                        else s.homeRemovedFromMyList.format(target.title)
                    )
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    val s = strings
                    showActionFeedback(s.myListUpdateFailed.format(err.message ?: s.commonError))
                }
        }
    }

    fun openContinueWatchingOptions(item: UnifiedMediaItem) {
        _uiState.update { it.copy(continueWatchingMenuTarget = item) }
    }

    fun dismissContinueWatchingOptions() {
        _uiState.update { it.copy(continueWatchingMenuTarget = null) }
    }

    fun openPosterOptions(item: UnifiedMediaItem) {
        _uiState.update { it.copy(posterMenuTarget = item) }
    }

    fun dismissPosterOptions() {
        _uiState.update { it.copy(posterMenuTarget = null) }
    }

    fun dismissActionFeedback() {
        feedbackJob?.cancel()
        _uiState.update { it.copy(actionFeedbackMessage = null) }
    }

    fun quickRequestItem(item: UnifiedMediaItem, is4k: Boolean) {
        val tmdbId = item.tmdbId ?: return
        _uiState.update { it.copy(posterMenuTarget = null) }
        val profile = _uiState.value.profile
        if (!_uiState.value.isSeerrConfigured) {
            showActionFeedback(strings.requestNeedsSeerr)
            return
        }
        if (profile != null && !profile.canSubmitRequests) {
            showActionFeedback(strings.requestsLockedKids)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val seasons = if (item.mediaType == MediaType.SERIES) {
                item.seasons.map { it.seasonNumber }.filter { it > 0 }.ifEmpty { null }
            } else null

            catalogRepository.submitRequest(
                tmdbId = tmdbId,
                mediaType = item.mediaType,
                is4k = is4k,
                seasons = seasons
            ).onSuccess {
                _uiState.update { state ->
                    val applyStatus: (UnifiedMediaItem) -> UnifiedMediaItem = { current ->
                        if (current.id == item.id || (current.tmdbId == tmdbId && current.mediaType == item.mediaType)) {
                            if (is4k) current.copy(uhdStatus = AvailabilityTier.PENDING)
                            else current.copy(hdStatus = AvailabilityTier.PENDING)
                        } else current
                    }
                    state.copy(
                        featuredItem = state.featuredItem?.let(applyStatus),
                        rows = state.rows.map { row -> row.copy(items = row.items.map(applyStatus)) },
                        activeDownloadCount = state.activeDownloadCount + 1
                    )
                }
                showActionFeedback(strings.requestedTitleQualityFeedback.format(item.title, if (is4k) "4K" else "HD"))
            }.onFailure { err ->
                val s = strings
                showActionFeedback(s.requestFailed.format(err.message ?: s.commonUnknownError))
            }
        }
    }

    private fun showActionFeedback(message: String) {
        feedbackJob?.cancel()
        _uiState.update { it.copy(actionFeedbackMessage = message) }
        feedbackJob = viewModelScope.launch {
            delay(3500L)
            _uiState.update { it.copy(actionFeedbackMessage = null) }
        }
    }

    fun removeFromContinueWatching(item: UnifiedMediaItem) {
        val profile = _uiState.value.profile ?: return
        val jfId = item.jellyfinId ?: return
        _uiState.update { it.copy(continueWatchingMenuTarget = null) }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.removeFromContinueWatching(profile.jellyfinUserId, jfId)
                .onSuccess {
                    _uiState.update { state ->
                        val nextRows = state.rows.mapNotNull { row ->
                            if (row.id == "continue_watching") {
                                val filtered = row.items.filterNot { it.id == item.id }
                                if (filtered.isEmpty()) null else row.copy(items = filtered)
                            } else row
                        }
                        state.copy(rows = nextRows)
                    }
                }
                .onFailure { err ->
                    if (err is CancellationException) throw err
                    val s = strings
                    showActionFeedback(s.homeContinueRemoveFailed.format(err.message ?: s.commonError))
                }
        }
    }

    fun openCollection(boxSetId: String, title: String, tmdbCollectionId: Int? = null) {
        val profile = _uiState.value.profile ?: return
        _uiState.update {
            it.copy(
                activeCollectionDetails = CollectionSummary(
                    id = boxSetId,
                    tmdbCollectionId = tmdbCollectionId,
                    name = title
                ),
                collectionDetailsLoading = true
            )
        }
        collectionJob?.cancel()
        collectionJob = viewModelScope.launch(Dispatchers.IO) {
            val details = try {
                catalogRepository.loadBoxSetDetails(
                    userId = profile.jellyfinUserId,
                    boxSetId = boxSetId,
                    fallbackTitle = title,
                    tmdbCollectionIdHint = tmdbCollectionId
                )?.let { col ->
                    // Same parental filter as every row: a collection can mix ratings.
                    val allowed = ContentRatingHelper.filterForProfile(col.items, profile)
                    col.copy(items = allowed, ownedCount = allowed.count { it.isPlayable }, totalCount = col.totalCount?.let { allowed.size })
                }
            } catch (e: CancellationException) {
                ensureActive() // rethrow if the dialog was dismissed / superseded
                null
            } catch (e: Exception) {
                // Never leave the dialog spinning forever (or crash the app) on a network error.
                null
            }
            _uiState.update { state ->
                if (state.activeCollectionDetails?.id != boxSetId) state
                else state.copy(
                    activeCollectionDetails = details ?: state.activeCollectionDetails,
                    collectionDetailsLoading = false
                )
            }
        }
    }

    fun dismissCollectionDetails() {
        collectionJob?.cancel()
        _uiState.update { it.copy(activeCollectionDetails = null, collectionDetailsLoading = false) }
    }

    fun requestCollectionItem(item: UnifiedMediaItem, is4k: Boolean = false) {
        val tmdbId = item.tmdbId ?: return
        val profile = _uiState.value.profile
        if (!_uiState.value.isSeerrConfigured) {
            showActionFeedback(strings.requestNeedsSeerr)
            return
        }
        if (profile != null && !profile.canSubmitRequests) {
            showActionFeedback(strings.requestsLockedKids)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            catalogRepository.submitRequest(tmdbId = tmdbId, mediaType = item.mediaType, is4k = is4k)
                .onSuccess {
                    _uiState.update { state ->
                        val updatedCol = state.activeCollectionDetails?.let { col ->
                            col.copy(
                                items = col.items.map { part ->
                                    if (part.tmdbId == tmdbId) {
                                        if (is4k) part.copy(uhdStatus = AvailabilityTier.PENDING)
                                        else part.copy(hdStatus = AvailabilityTier.PENDING)
                                    } else part
                                }
                            )
                        }
                        state.copy(
                            activeCollectionDetails = updatedCol,
                            activeDownloadCount = state.activeDownloadCount + 1
                        )
                    }
                    showActionFeedback(strings.requestedTitleFeedback.format(item.title))
                }
                .onFailure { err ->
                    val s = strings
                    showActionFeedback(s.requestFailed.format(err.message ?: s.commonUnknownError))
                }
        }
    }

    private var requestAllJob: Job? = null

    fun requestAllMissingInCollection(is4k: Boolean = false) {
        val profile = _uiState.value.profile
        if (!_uiState.value.isSeerrConfigured) {
            showActionFeedback(strings.requestNeedsSeerr)
            return
        }
        if (profile != null && !profile.canSubmitRequests) {
            showActionFeedback(strings.requestsLockedKids)
            return
        }
        val col = _uiState.value.activeCollectionDetails ?: return
        val missing = col.items.filter { !it.isPlayable && it.canRequestHd && it.tmdbId != null }
        if (missing.isEmpty()) return
        // A second OK while the batch runs would request every title again.
        if (requestAllJob?.isActive == true) return
        requestAllJob = viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            val requestedTmdbIds = mutableSetOf<Int>()
            missing.forEach { part ->
                val tmdb = part.tmdbId ?: return@forEach
                catalogRepository.submitRequest(tmdbId = tmdb, mediaType = part.mediaType, is4k = is4k)
                    .onSuccess {
                        count++
                        requestedTmdbIds += tmdb
                    }
            }
            if (requestedTmdbIds.isNotEmpty()) {
                _uiState.update { state ->
                    val updatedCol = state.activeCollectionDetails?.let { cur ->
                        cur.copy(
                            items = cur.items.map { part ->
                                if (part.tmdbId in requestedTmdbIds) {
                                    if (is4k) part.copy(uhdStatus = AvailabilityTier.PENDING)
                                    else part.copy(hdStatus = AvailabilityTier.PENDING)
                                } else part
                            }
                        )
                    }
                    state.copy(
                        activeCollectionDetails = updatedCol,
                        activeDownloadCount = state.activeDownloadCount + count
                    )
                }
            }
            val s = strings
            showActionFeedback(
                when {
                    count == 1 -> s.requestedMissingInOne.format(col.name)
                    count > 1 -> s.requestedMissingInMany.format(count, col.name)
                    else -> s.requestsSubmitFailed
                }
            )
        }
    }

    @Synchronized
    fun onItemFocused(item: UnifiedMediaItem, force: Boolean = false) {
        if (!force) {
            if (focusTargetId == item.id) return
            if (_uiState.value.featuredItem?.id == item.id) {
                // Back on the featured card (A -> B -> A) before B's delayed switch fired:
                // cancel it, or the hero would change to B while A is focused.
                focusPreviewJob?.cancel()
                focusTargetId = item.id
                return
            }
        }
        focusTargetId = item.id
        focusPreviewJob?.cancel()
        focusPreviewJob = viewModelScope.launch(Dispatchers.IO) {
            if (!force) {
                delay(550L)
            }
            _uiState.update { it.copy(featuredItem = item, previewStream = null) }

            if (item.boxSetId != null) return@launch
            val profile = _uiState.value.profile ?: return@launch

            val needsMetadataEnrichment = item.overview.isBlank() ||
                (item.backdropUrl == null && item.posterUrl == null)
            val needsTrailerEnrichment = heroVisible && profile.autoplayPreviews &&
                item.youtubeTrailerKey == null &&
                item.localTrailerItemId == null

            val mergedFeatured: UnifiedMediaItem? = if (needsMetadataEnrichment || needsTrailerEnrichment) {
                val enriched = catalogRepository.loadFullDetails(
                    userId = profile.jellyfinUserId,
                    tmdbId = item.tmdbId,
                    jellyfinId = item.jellyfinId,
                    mediaType = item.mediaType
                ).getOrNull()

                enriched?.copy(
                    id = item.id,
                    jellyfinId = if (item.mediaType == MediaType.EPISODE && !item.jellyfinId.isNullOrBlank()) {
                        item.jellyfinId
                    } else {
                        enriched.jellyfinId ?: item.jellyfinId
                    },
                    episodeLabel = item.episodeLabel ?: enriched.episodeLabel,
                    resumePositionTicks = if (item.resumePositionTicks > 0L) item.resumePositionTicks else enriched.resumePositionTicks
                )?.also { merged ->
                    _uiState.update { state ->
                        if (state.featuredItem?.id == item.id) {
                            state.copy(featuredItem = merged)
                        } else state
                    }
                }
            } else {
                item
            }

            if (!profile.autoplayPreviews || !heroVisible) return@launch

            delay(1400L)
            if (!heroVisible) return@launch
            val target = mergedFeatured ?: item
            val stream = trailerResolver.resolvePreview(
                localTrailerId = target.localTrailerItemId,
                youtubeKey = target.youtubeTrailerKey
            )
            if (stream != null) {
                _uiState.update { state ->
                    if (state.featuredItem?.id == item.id) {
                        state.copy(previewStream = stream)
                    } else state
                }
            }
        }
    }

    private fun loadCatalog(
        tab: HomeTab,
        genre: GenreFilterOption,
        availabilityFilter: AvailabilityFilter,
        forceRefresh: Boolean = false,
        /** Server-pushed refresh: keep the current page on screen until the new rows arrive. */
        background: Boolean = false
    ) {
        // Main-confined: it is also called from IO (init, Customize Home, DVR changes), and the
        // job / generation bookkeeping below must not interleave with a tab press on Main.
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            viewModelScope.launch(Dispatchers.Main.immediate) {
                loadCatalog(tab, genre, availabilityFilter, forceRefresh, background)
            }
            return
        }
        // Cancel any in-flight load so rapid tab/filter switching can't race and let a stale
        // (slower) response overwrite the rows of the tab the user actually selected.
        catalogJob?.cancel()
        // A Continue Watching refresh in flight belongs to the page being left.
        continueWatchingJob?.cancel()
        val generation = ++catalogGeneration
        // Snapshot in-place edits (My List, watched, removed CW) of the page being left NOW: the
        // job below clears the rows on a tab switch before it could read them.
        val editedContent = displayedContent?.let { (key, original) ->
            _uiState.value.rows.takeIf { it !== original && it.isNotEmpty() }?.let { key to it }
        }
        // Switching tabs: the previous tab's rows must not sit under the new tab while it loads
        // (they'd be focusable, then swapped out from under the focused card).
        val clearPreviousTab = !background && lastLoadedTab != null && lastLoadedTab != tab
        if (clearPreviousTab) lastLoadedTab = null
        catalogJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    activeTab = tab,
                    selectedGenre = genre,
                    availabilityFilter = availabilityFilter,
                    rows = if (clearPreviousTab) emptyList() else it.rows,
                    featuredItem = if (clearPreviousTab) null else it.featuredItem,
                    isLoading = if (background) it.isLoading else true,
                    errorMessage = null,
                    previewStream = if (background) it.previewStream else null
                )
            }
            val profile = authRepository.activeProfileFlow.first()
            if (profile == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = strings.commonNoActiveProfile) }
                return@launch
            }
            val serverConfig = prefs.serverConfigFlow.first()
            _uiState.update { it.copy(profile = profile, isSeerrConfigured = prefs.isSeerrConfigured) }

            // Download badge / 4K capability / library views: never block the rows on them.
            refreshAuxiliaryState(profile)

            // Stale-while-revalidate: paint the last good content for this exact view right away
            // (no spinner), then swap in the fresh rows below. A user-initiated Refresh skips it.
            val cacheKey = homeCacheKeyFor(profile, tab, genre, availabilityFilter, serverConfig)
            editedContent?.let { (key, rows) ->
                if (displayedContent?.first == key) displayedContent = key to rows
                runCatching { homeContentCache.write(key, rows, currentJellyfinBase()) }
            }
            var showingCachedContent = false
            if (cacheKey != null && !forceRefresh && !background) {
                val cachedRows = try {
                    homeContentCache.read(cacheKey, currentJellyfinBase())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                    ?.withoutDeleted()
                ensureActive()
                if (!cachedRows.isNullOrEmpty() && generation == catalogGeneration) {
                    val cachedFeatured = pickInitialFeatured(cachedRows)
                    lastLoadedTab = tab
                    displayedContent = cacheKey to cachedRows
                    _uiState.update {
                        it.copy(rows = cachedRows, featuredItem = cachedFeatured, isLoading = false)
                    }
                    showingCachedContent = true
                    if (cachedFeatured != null) onItemFocused(cachedFeatured, force = true)
                }
            }

            val result: Result<CatalogLoadBundle> = try {
                Result.success(coroutineScope {
                    val collectionsDeferred = async {
                        // Collections carry no rating of their own, so restricted profiles don't get
                        // the hub at all (an adult saga's poster and name would otherwise show).
                        if (tab == HomeTab.COLLECTIONS && !profile.isRestricted) {
                            catalogRepository.loadBoxSetCollections(profile.jellyfinUserId)
                        } else emptyList()
                    }
                    val liveTvChannelsDeferred = async {
                        if (tab == HomeTab.LIVE_TV) {
                            catalogRepository.loadLiveTvChannelsAndPrograms(profile.jellyfinUserId)
                        } else emptyList()
                    }
                    val liveTvScheduledDeferred = async {
                        if (tab == HomeTab.LIVE_TV) catalogRepository.loadScheduledRecordings() else emptyList()
                    }
                    val liveTvRecordingsDeferred = async {
                        if (tab == HomeTab.LIVE_TV) {
                            // Recordings are library items like any other: same rating rules.
                            com.seerflix.tv.core.model.ContentRatingHelper.filterForProfile(
                                catalogRepository.loadLiveTvRecordings(profile.jellyfinUserId),
                                profile
                            )
                        } else emptyList()
                    }
                    val playlistsDeferred = async {
                        if (tab == HomeTab.MY_LIST) {
                            catalogRepository.loadPlaylists(profile.jellyfinUserId)
                        } else emptyList()
                    }
                    val rowsDeferred = async {
                        when (tab) {
                            HomeTab.COLLECTIONS, HomeTab.LIVE_TV -> emptyList()
                            HomeTab.NEW_AND_POPULAR -> catalogRepository.loadNewAndPopular(
                                userId = profile.jellyfinUserId,
                                availabilityFilter = availabilityFilter,
                                activeProfile = profile,
                                forceRefresh = forceRefresh
                            )
                            HomeTab.MY_LIST -> {
                                val listItems = catalogRepository.loadMyList(profile.jellyfinUserId)
                                if (listItems.isEmpty()) {
                                    emptyList()
                                } else {
                                    val movies = listItems.filter { it.mediaType == MediaType.MOVIE }
                                    val shows = listItems.filter { it.mediaType != MediaType.MOVIE }
                                    buildList {
                                        add(
                                            CatalogRow(
                                                id = "my_list_page",
                                                title = "My List",
                                                style = RowStyle.POSTER,
                                                items = listItems
                                            )
                                        )
                                        if (shows.isNotEmpty() && movies.isNotEmpty()) {
                                            add(CatalogRow("my_list_shows", "Saved TV Shows", RowStyle.POSTER, shows))
                                            add(CatalogRow("my_list_movies", "Saved Movies", RowStyle.POSTER, movies))
                                        }
                                    }
                                }
                            }
                            HomeTab.HOME, HomeTab.SHOWS, HomeTab.MOVIES -> {
                                val activeGenre = if (genre.movieGenreId == null && genre.tvGenreId == null) null else genre
                                catalogRepository.loadHomeRows(
                                    userId = profile.jellyfinUserId,
                                    filterType = tab.filter,
                                    genreFilter = activeGenre,
                                    availabilityFilter = availabilityFilter,
                                    activeProfile = profile,
                                    forceRefresh = forceRefresh
                                )
                            }
                        }
                    }

                    // The library index can lag a deletion: never bring a deleted title back.
                    val rows = rowsDeferred.await().withoutDeleted()
                    CatalogLoadBundle(
                        rows = rows,
                        collections = collectionsDeferred.await(),
                        playlists = playlistsDeferred.await(),
                        liveChannels = liveTvChannelsDeferred.await(),
                        liveRecordings = liveTvRecordingsDeferred.await(),
                        liveScheduled = liveTvScheduledDeferred.await(),
                        featured = pickInitialFeatured(rows)
                    )
                })
            } catch (e: CancellationException) {
                // Superseded by a newer load (or ViewModel cleared): rethrow, never surface as an
                // error. A stray CancellationException from a child (e.g. a timeout) while this job
                // is still active is a real failure and must not leave isLoading stuck at true.
                ensureActive()
                Result.failure(e)
            } catch (e: Exception) {
                Result.failure(e)
            }
            ensureActive()
            if (generation != catalogGeneration) return@launch

            result.onSuccess { bundle ->
                lastLoadedTab = tab
                lastNetworkLoadAtMs = android.os.SystemClock.elapsedRealtime()
                // Background swap over cached content: keep the hero (and its enrichment / preview)
                // when the title the user is looking at is still on the page, so nothing jumps.
                val currentFeaturedId = _uiState.value.featuredItem?.id
                val keepFeatured = (showingCachedContent || background) && currentFeaturedId != null &&
                    bundle.rows.any { row -> row.items.any { it.id == currentFeaturedId } }
                _uiState.update {
                    it.copy(
                        rows = bundle.rows,
                        collections = bundle.collections,
                        playlists = if (tab == HomeTab.MY_LIST) bundle.playlists else it.playlists,
                        liveTvChannels = bundle.liveChannels,
                        liveTvRecordings = bundle.liveRecordings,
                        liveTvScheduled = bundle.liveScheduled,
                        featuredItem = if (keepFeatured) it.featuredItem else bundle.featured,
                        isLoading = false
                    )
                }
                displayedContent = cacheKey?.let { it to bundle.rows }
                if (!keepFeatured && bundle.featured != null) {
                    onItemFocused(bundle.featured, force = true)
                }
                if (cacheKey != null && bundle.rows.isNotEmpty()) {
                    launch(Dispatchers.IO) {
                        runCatching { homeContentCache.write(cacheKey, bundle.rows, currentJellyfinBase()) }
                    }
                }
                if (tab == HomeTab.HOME && availabilityFilter == AvailabilityFilter.ALL) {
                    launch(Dispatchers.IO) {
                        val continueItems = bundle.rows.firstOrNull { it.id == "continue_watching" }?.items.orEmpty()
                        val channelItems = bundle.rows.firstOrNull { it.id == "trending_now" }?.items
                            ?: bundle.rows.firstOrNull { it.id != "continue_watching" }?.items.orEmpty()
                        tvChannelSyncManager.syncHomeChannels(continueItems, channelItems, readyPromotionFilter())
                    }
                }
            }.onFailure { err ->
                val message = UnifiedCatalogRepository.formatHttpError(err, strings.homeLoadFailed)
                val keepsCurrentContent = lastLoadedTab == tab
                _uiState.update {
                    if (keepsCurrentContent) {
                        // Refresh of the visible tab failed: keep what's on screen.
                        it.copy(isLoading = false, errorMessage = message)
                    } else {
                        // The content on screen belongs to another tab; clear it so the
                        // error + Retry state is shown instead of mislabelled stale rows.
                        displayedContent = null
                        it.copy(
                            isLoading = false,
                            errorMessage = message,
                            rows = emptyList(),
                            collections = emptyList(),
                            liveTvChannels = emptyList(),
                            liveTvRecordings = emptyList(),
                            featuredItem = null,
                            previewStream = null
                        )
                    }
                }
                if (keepsCurrentContent && !background) {
                    // The full-screen error UI only appears when nothing is loaded, so tell the
                    // user a background refresh failed instead of failing silently.
                    showActionFeedback(message)
                }
            }
        }
    }

    /**
     * Nav-badge download count, 4K-server capability and library views. Loaded beside the rows
     * instead of gating them (the old request-hub load did up to 50 Seerr detail calls first).
     */
    private fun refreshAuxiliaryState(profile: UserProfile) {
        if (auxiliaryJob?.isActive == true) return
        auxiliaryJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                coroutineScope {
                    val downloadsDeferred = async {
                        if (prefs.isSeerrConfigured) catalogRepository.loadActiveDownloadCount() else 0
                    }
                    val has4kMovieDeferred = async { catalogRepository.has4kServerConfigured(MediaType.MOVIE) }
                    val has4kTvDeferred = async { catalogRepository.has4kServerConfigured(MediaType.SERIES) }
                    val libraryViewsDeferred = async {
                        if (_uiState.value.libraryViews.isEmpty()) {
                            catalogRepository.loadUserLibraryViews(profile.jellyfinUserId)
                        } else _uiState.value.libraryViews
                    }
                    val downloads = downloadsDeferred.await()
                    val has4kMovie = has4kMovieDeferred.await()
                    val has4kTv = has4kTvDeferred.await()
                    val views = libraryViewsDeferred.await()
                    _uiState.update {
                        it.copy(
                            activeDownloadCount = downloads,
                            has4kMovieServer = has4kMovie,
                            has4kTvServer = has4kTv,
                            libraryViews = views
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Leave the previous values in place.
            }
        }
    }

    /** Drops titles deleted this session (cached rows and a lagging index would show them). */
    private fun List<CatalogRow>.withoutDeleted(): List<CatalogRow> {
        val deleted = catalogRepository.deletedItemIds.value
        if (deleted.isEmpty()) return this
        return map { row ->
            val kept = row.items.filterNot { it.isDeletedBy(deleted) }
            if (kept.size == row.items.size) row else row.copy(items = kept)
        }.filter { it.items.isNotEmpty() }
    }

    /** The title itself was deleted, or the series it is an episode of (Continue Watching / Next Up). */
    private fun UnifiedMediaItem.isDeletedBy(deleted: Set<String>): Boolean =
        jellyfinId in deleted || (seriesId != null && seriesId in deleted)

    /** Launcher "Ready" promotions the active profile may see (Watch Next is shared by every profile). */
    private fun readyPromotionFilter(): (String) -> Boolean {
        val profile = _uiState.value.profile
        return { jfId -> catalogRepository.isLibraryTitleAllowed(jfId, profile) }
    }

    private fun pickInitialFeatured(rows: List<CatalogRow>): UnifiedMediaItem? =
        rows.firstOrNull { it.id != "continue_watching" }?.items?.firstOrNull()
            ?: rows.firstOrNull()?.items?.firstOrNull()

    private fun currentJellyfinBase(): String =
        prefs.cachedResolvedJellyfinUrl.ifBlank { prefs.cachedJellyfinUrl }

    /** Only the row-based tabs are cached; Collections / Live TV load as before. */
    private fun homeCacheKeyFor(
        profile: UserProfile,
        tab: HomeTab,
        genre: GenreFilterOption,
        availabilityFilter: AvailabilityFilter,
        serverConfig: ServerConfig
    ): HomeContentCache.Key? {
        if (tab == HomeTab.COLLECTIONS || tab == HomeTab.LIVE_TV) return null
        return HomeContentCache.keyFor(
            profile = profile,
            tab = tab.name,
            genreId = genre.id,
            availability = availabilityFilter.name,
            seerrConfigured = serverConfig.isSeerrConfigured,
            // The extra servers this profile is signed in to are part of what Home shows.
            jellyfinServer = serverConfig.jellyfinUrl.ifBlank { serverConfig.jellyfinLocalUrl } +
                prefs.extraServers.filter { prefs.userIdFor(it.id) != null }.joinToString("") { "+${it.id}" },
            seerrServer = serverConfig.seerrUrl.ifBlank { serverConfig.seerrLocalUrl },
            metadataLanguage = profile.metadataLanguage.ifBlank { "en" }
        )
    }

    private data class CatalogLoadBundle(
        val rows: List<CatalogRow>,
        val collections: List<CollectionSummary>,
        val playlists: List<com.seerflix.tv.core.model.PlaylistSummary> = emptyList(),
        val liveChannels: List<LiveTvChannel>,
        val liveRecordings: List<UnifiedMediaItem>,
        val liveScheduled: List<com.seerflix.tv.core.model.ScheduledRecording> = emptyList(),
        val featured: UnifiedMediaItem?
    )
}

private const val LIBRARY_CHANGE_DEBOUNCE_MS = 8_000L
private const val USER_DATA_DEBOUNCE_MS = 3_000L
private const val LIVE_TV_STALE_MS = 5 * 60_000L

/** The profile fields that change what Home shows. */
private fun UserProfile.homeContentSignature(): List<Any?> = listOf(
    id, jellyfinUserId, effectiveRatingLimit, kidsMode, allowKidsRequests, hiddenHomeRowIds, homeRowOrder,
    pinnedLibraryIds, mergeNextUpIntoContinueWatching, metadataLanguage
)
