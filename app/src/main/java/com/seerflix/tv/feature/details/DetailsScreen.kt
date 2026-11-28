package com.seerflix.tv.feature.details

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.i18n.UiStrings
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CastMember
import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.ExtrasItem
import com.seerflix.tv.core.model.MediaSourceOption
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.PlaylistSummary
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.TrackOption
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.feature.player.PlayerTrackSelection
import com.seerflix.tv.core.player.QueueEntry
import com.seerflix.tv.ui.components.AvailabilityBadge
import com.seerflix.tv.ui.components.TitleMetaLine
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

@androidx.compose.runtime.Immutable
data class DetailsExtraActions(
    val onPlayWithOptions: (itemId: String, mediaSourceId: String?, fromStart: Boolean, audioIndex: Int?, subtitleIndex: Int?) -> Unit = { _, _, _, _, _ -> },
    val onPlayQueue: (entries: List<QueueEntry>, shuffle: Boolean) -> Unit = { _, _ -> },
    val onSearchPerson: (name: String, personId: Int?, jellyfinPersonId: String?) -> Unit = { _, _, _ -> },
    val onOpenPlaylistDialog: () -> Unit = {},
    val onDismissPlaylistDialog: () -> Unit = {},
    val onAddToPlaylist: (playlistId: String, playlistName: String) -> Unit = { _, _ -> },
    val onCreatePlaylist: (name: String) -> Unit = {},
    val onOpenMediaInfo: () -> Unit = {},
    val onDismissMediaInfo: () -> Unit = {},
    val onOpenTrackPicker: () -> Unit = {},
    val onDismissTrackPicker: () -> Unit = {},
    val onOpenDeleteConfirm: () -> Unit = {},
    val onDismissDeleteConfirm: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onOpenEpisodeMenu: (EpisodeSummary) -> Unit = {},
    val onDismissEpisodeMenu: () -> Unit = {},
    val onToggleEpisodeWatched: (EpisodeSummary) -> Unit = {},
    val onToggleSeasonWatched: () -> Unit = {}
)

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlayItem: (String, String?) -> Unit,
    onPlayTrailer: (title: String, youtubeKey: String?, localTrailerId: String?) -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onSearchActor: (name: String, personId: Int?) -> Unit,
    onPlayWithOptions: (itemId: String, mediaSourceId: String?, fromStart: Boolean, audioIndex: Int?, subtitleIndex: Int?) -> Unit = { id, src, _, _, _ ->
        onPlayItem(id, src)
    },
    onPlayQueue: (List<QueueEntry>, Boolean) -> Unit = { _, _ -> },
    onSearchPerson: (name: String, personId: Int?, jellyfinPersonId: String?) -> Unit = { name, pid, _ ->
        onSearchActor(name, pid)
    },
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val strings = AppStrings.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(state.itemDeleted) {
        if (state.itemDeleted) onBack()
    }
    LaunchedEffect(lifecycleOwner, viewModel) {
        // Pause the 15s request/download polling while the player (or anything else) covers this
        // screen, and refresh resume/watched state when coming back.
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.onScreenVisible()
            try {
                awaitCancellation()
            } finally {
                viewModel.onScreenHidden()
            }
        }
    }

    ThemeSongPlayerEffect(
        themeSongUrl = state.themeSongUrl,
        mediaHttpClient = viewModel.mediaHttpClient,
        lifecycleOwner = lifecycleOwner
    )

    DetailsContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::loadAll,
        onPlayItem = onPlayItem,
        onOpenDetails = onOpenDetails,
        onOpenRequestModal = viewModel::openRequestModal,
        onCloseRequestModal = viewModel::closeRequestModal,
        onCancelRequest = viewModel::cancelRequest,
        onSelectSeason = viewModel::selectSeason,
        onSelectMediaSource = viewModel::selectMediaSource,
        onSubmitRequest = { hd, uhd, seasons, hdSrv, hdProf, hdRoot, uhdSrv, uhdProf, uhdRoot ->
            viewModel.submitRequest(hd, uhd, seasons, hdSrv, hdProf, hdRoot, uhdSrv, uhdProf, uhdRoot)
        },
        onWatchTrailer = { key ->
            val cur = viewModel.uiState.value.item
            onPlayTrailer(cur?.title ?: strings.actionTrailer, key, cur?.localTrailerItemId)
        },
        onPlayTrailer = onPlayTrailer,
        onToggleMyList = viewModel::toggleMyList,
        onToggleReminder = viewModel::toggleReminder,
        onToggleWatched = viewModel::toggleWatched,
        onOpenExternalPlayer = { viewModel.openExternalPlayer(context) },
        onOpenReportIssue = viewModel::openReportIssueModal,
        onCloseReportIssue = viewModel::closeReportIssueModal,
        onSubmitIssue = viewModel::submitIssue,
        onRemoveFromContinueWatching = viewModel::removeFromContinueWatching,
        onSearchActor = onSearchActor,
        onRequestCollectionItem = { part -> viewModel.requestCollectionItem(part) },
        onRequestAllMissingInCollection = { viewModel.requestAllMissingInCollection() },
        extraActions = DetailsExtraActions(
            onPlayWithOptions = onPlayWithOptions,
            onPlayQueue = onPlayQueue,
            onSearchPerson = onSearchPerson,
            onOpenPlaylistDialog = viewModel::openPlaylistDialog,
            onDismissPlaylistDialog = viewModel::dismissPlaylistDialog,
            onAddToPlaylist = viewModel::addToExistingPlaylist,
            onCreatePlaylist = viewModel::createAndAddToPlaylist,
            onOpenMediaInfo = viewModel::openMediaInfoDialog,
            onDismissMediaInfo = viewModel::dismissMediaInfoDialog,
            onOpenTrackPicker = viewModel::openTrackPickerDialog,
            onDismissTrackPicker = viewModel::dismissTrackPickerDialog,
            onOpenDeleteConfirm = viewModel::openDeleteConfirmDialog,
            onDismissDeleteConfirm = viewModel::dismissDeleteConfirmDialog,
            onConfirmDelete = viewModel::confirmDeleteItem,
            onOpenEpisodeMenu = viewModel::openEpisodeMenu,
            onDismissEpisodeMenu = viewModel::dismissEpisodeMenu,
            onToggleEpisodeWatched = viewModel::toggleEpisodeWatched,
            onToggleSeasonWatched = viewModel::toggleCurrentSeasonWatched
        )
    )
}

@Composable
fun DetailsContent(
    state: DetailsUiState,
    onBack: () -> Unit,
    onPlayItem: (String, String?) -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onOpenRequestModal: () -> Unit,
    onCloseRequestModal: () -> Unit,
    onCancelRequest: (Int) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectMediaSource: (String) -> Unit = {},
    onSubmitRequest: (
        requestHd: Boolean,
        request4k: Boolean,
        seasons: List<Int>?,
        hdServerId: Int?,
        hdProfileId: Int?,
        hdRootFolder: String?,
        uhdServerId: Int?,
        uhdProfileId: Int?,
        uhdRootFolder: String?
    ) -> Unit = { hd, uhd, seasons, _, _, _, _, _, _ -> },
    onWatchTrailer: (String) -> Unit,
    onPlayTrailer: (title: String, youtubeKey: String?, localTrailerId: String?) -> Unit = { _, k, _ -> k?.let(onWatchTrailer) },
    onToggleMyList: () -> Unit = {},
    onToggleReminder: () -> Unit = {},
    onToggleWatched: () -> Unit = {},
    onOpenExternalPlayer: () -> Unit = {},
    onOpenReportIssue: () -> Unit = {},
    onCloseReportIssue: () -> Unit = {},
    onSubmitIssue: (SeerrIssueType, String) -> Unit = { _, _ -> },
    onRemoveFromContinueWatching: () -> Unit = {},
    onSearchActor: (name: String, personId: Int?) -> Unit = { _, _ -> },
    onRequestCollectionItem: (UnifiedMediaItem) -> Unit = {},
    onRequestAllMissingInCollection: () -> Unit = {},
    onRetry: () -> Unit = {},
    extraActions: DetailsExtraActions = DetailsExtraActions(
        onPlayWithOptions = { id, src, _, _, _ -> onPlayItem(id, src) },
        onSearchPerson = { name, pid, _ -> onSearchActor(name, pid) }
    )
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    // Lives outside the LazyColumn so scrolling the header away and back (or the seed -> full data
    // swap) never re-runs the initial focus request and yanks focus back to Play.
    // Saveable: coming back from the player recreates this screen, and with a plain remember the
    // restored scroll position (often deep in the episode list) was left with nothing focused.
    val focusMemory = rememberSaveable(saver = DetailsFocusMemory.Saver) { DetailsFocusMemory() }
    // Re-entering the screen without an episode to return to: let the header focus Play again.
    // (Runs once per entry into composition, before the header's focus effect is launched.)
    @Suppress("UNUSED_VARIABLE")
    val focusMemoryPrepared = remember(focusMemory) {
        if (focusMemory.lastPlayedEpisodeId == null) focusMemory.initialFocusDone = false
        true
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        when {
            state.isLoading -> {
                // No seed (deep link, Requests, restricted profile with an unrated card): a static
                // header skeleton instead of a spinner, so the page doesn't "pop" when data lands.
                DetailsLoadingSkeleton()
            }
            state.errorMessage != null || state.item == null -> {
                val errorBackFocusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    delay(60L)
                    runCatching { errorBackFocusRequester.requestFocus() }
                }
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage ?: strings.detailsTitleNotFound, color = colors.accent, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    // A transient network error used to dead-end here with only "Go Back".
                    // (Parental-control blocks get only "Go Back"; retrying can't help.)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!state.isBlockedByParentalControls) {
                            HeroPillButton(
                                text = strings.commonRetry,
                                icon = Icons.Filled.Refresh,
                                isPrimary = true,
                                onClick = onRetry,
                                modifier = Modifier.focusRequester(errorBackFocusRequester)
                            )
                        }
                        HeroPillButton(
                            text = strings.commonGoBack,
                            icon = if (state.isBlockedByParentalControls) Icons.Filled.Lock else Icons.AutoMirrored.Filled.ArrowBack,
                            isPrimary = state.isBlockedByParentalControls,
                            onClick = onBack,
                            modifier = if (state.isBlockedByParentalControls) {
                                Modifier.focusRequester(errorBackFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                    }
                }
            }
            else -> {
                val item = state.item!!
                val episodes = state.episodes
                val selectedSourceId = state.selectedMediaSourceId
                val hasPlayableEpisode = remember(episodes) { episodes.any { it.isAvailableInJellyfin } }
                val listState = rememberLazyListState()
                val returnEpisodeFocusRequester = remember { FocusRequester() }
                // Back from the player: land on the episode that was playing. If it can't take
                // focus (list changed, episodes still loading), go to the top so the header's
                // initial-focus effect puts the remote on Play instead of on nothing.
                LaunchedEffect(Unit) {
                    if (!focusMemory.initialFocusDone || focusMemory.lastPlayedEpisodeId == null) return@LaunchedEffect
                    repeat(10) {
                        delay(80L)
                        if (runCatching { returnEpisodeFocusRequester.requestFocus() }.getOrDefault(false)) {
                            return@LaunchedEffect
                        }
                    }
                    focusMemory.lastPlayedEpisodeId = null
                    focusMemory.initialFocusDone = false
                    runCatching { listState.scrollToItem(0) }
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 56.dp)
                ) {
                    // Header Backdrop + Metadata + Actions + Live Download Progress
                    item(key = "details_header") {
                        val resolvePlayTarget: () -> Pair<String, String?>? = {
                            val playableId = if (item.mediaType == MediaType.SERIES) {
                                item.jellyfinId ?: episodes.firstOrNull { it.isAvailableInJellyfin }?.id
                            } else {
                                item.jellyfinId
                            }
                            val sourceId = if (item.mediaType == MediaType.SERIES) null else selectedSourceId
                            val versionItemId = sourceId?.let { id ->
                                item.mediaVersions.firstOrNull { it.id == id }?.itemId
                            }
                            (versionItemId ?: playableId)?.let { id -> id to sourceId }
                        }
                        DetailsHeaderSection(
                            item = item,
                            hasPlayableEpisode = hasPlayableEpisode,
                            isRefining = state.isRefining,
                            focusMemory = focusMemory,
                            isSeerrConfigured = state.isSeerrConfigured,
                            canSubmitRequests = state.canSubmitRequests,
                            canDeleteItems = !state.isRestrictedProfile,
                            feedbackBanner = state.feedbackBanner,
                            onBack = onBack,
                            onPlay = {
                                resolvePlayTarget()?.let { (id, src) -> onPlayItem(id, src) }
                            },
                            onPlayFromBeginning = {
                                resolvePlayTarget()?.let { (id, src) ->
                                    extraActions.onPlayWithOptions(id, src, true, null, null)
                                }
                            },
                            onRequestClick = onOpenRequestModal,
                            onWatchTrailer = {
                                onPlayTrailer("${item.title} • ${strings.actionTrailer}", item.youtubeTrailerKey, item.localTrailerItemId)
                            },
                            onToggleMyList = onToggleMyList,
                            onToggleReminder = onToggleReminder,
                            onToggleWatched = onToggleWatched,
                            onOpenExternalPlayer = onOpenExternalPlayer,
                            onOpenReportIssue = onOpenReportIssue,
                            onRemoveFromContinueWatching = onRemoveFromContinueWatching,
                            onCancelRequest = onCancelRequest,
                            onOpenPlaylistDialog = extraActions.onOpenPlaylistDialog,
                            onOpenMediaInfo = extraActions.onOpenMediaInfo,
                            onOpenTrackPicker = {
                                // The picker lists the first version's streams; for another version
                                // its indexes would point into a different file, so play that
                                // version without the picker instead.
                                if (selectedSourceId == null || selectedSourceId == item.mediaVersions.firstOrNull()?.id) {
                                    extraActions.onOpenTrackPicker()
                                } else {
                                    resolvePlayTarget()?.let { (id, src) -> onPlayItem(id, src) }
                                }
                            },
                            onOpenDeleteConfirm = extraActions.onOpenDeleteConfirm
                        )
                    }

                    // Seed only: seasons / cast / versions / recommendations aren't known yet.
                    if (state.isRefining) {
                        item(key = "refining_skeleton") {
                            DetailsSectionsSkeleton(isSeries = item.mediaType == MediaType.SERIES)
                        }
                    }

                    // Below the header, sections sit behind tabs instead of one long stacked page.
                    val sections = buildList {
                        if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) add(DetailsSection.EPISODES)
                        if (item.mediaVersions.size > 1) add(DetailsSection.VERSIONS)
                        if (item.extras.isNotEmpty()) add(DetailsSection.EXTRAS)
                        if (item.relatedVideos.isNotEmpty()) add(DetailsSection.TRAILERS)
                        if (item.cast.isNotEmpty()) add(DetailsSection.CAST)
                        if ((state.collectionSaga?.items?.size ?: 0) > 1) add(DetailsSection.COLLECTION)
                        if (state.recommendations.isNotEmpty()) add(DetailsSection.MORE_LIKE_THIS)
                    }
                    val selected = sections.firstOrNull { it == focusMemory.section } ?: sections.firstOrNull()
                    if (!state.isRefining && sections.isNotEmpty()) {
                        item(key = "section_tabs") {
                            DetailsSectionTabs(
                                sections = sections,
                                selected = selected,
                                onSelect = { focusMemory.section = it }
                            )
                        }
                    }

                    when (selected) {
                        null -> Unit
                        DetailsSection.VERSIONS -> item(key = "media_versions") {
                            MediaVersionsSection(
                                versions = item.mediaVersions,
                                selectedId = selectedSourceId ?: item.mediaVersions.firstOrNull()?.id,
                                onSelectMediaSource = onSelectMediaSource
                            )
                        }
                        DetailsSection.EPISODES -> {
                            item(key = "seasons_bar") {
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    // Index-qualified key: Jellyfin-only seasons without an IndexNumber
                                    // all map to seasonNumber 1, and a duplicate key crashes the row.
                                    itemsIndexed(item.seasons, key = { idx, s -> "season_${idx}_${s.seasonNumber}" }) { _, season ->
                                        SeasonTabChip(
                                            label = season.name,
                                            isSelected = season.seasonNumber == state.selectedSeasonNumber,
                                            isAvailable = season.isPlayable,
                                            onClick = { onSelectSeason(season.seasonNumber) }
                                        )
                                    }
                                }
                            }

                            val playableSeasonEps = episodes.filter { it.isAvailableInJellyfin }
                            if (!state.episodesLoading && playableSeasonEps.isNotEmpty()) {
                                item(key = "season_actions") {
                                    val allWatched = playableSeasonEps.all { it.isWatched }
                                    val queueStrings = AppStrings.current
                                    val seasonQueue = remember(playableSeasonEps, queueStrings) {
                                        playableSeasonEps.map { ep ->
                                            QueueEntry(
                                                itemId = ep.id,
                                                title = queueStrings.episodeCodeTitle.format(ep.seasonNumber, ep.episodeNumber, ep.name)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.padding(horizontal = 48.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        HeroPillButton(
                                            text = strings.actionPlayAll,
                                            icon = Icons.Filled.PlayArrow,
                                            isPrimary = false,
                                            onClick = { extraActions.onPlayQueue(seasonQueue, false) }
                                        )
                                        HeroPillButton(
                                            text = strings.actionShuffle,
                                            icon = Icons.Filled.Shuffle,
                                            isPrimary = false,
                                            onClick = { extraActions.onPlayQueue(seasonQueue, true) }
                                        )
                                        HeroPillButton(
                                            text = if (allWatched) strings.actionMarkSeasonUnwatched else strings.actionMarkSeasonWatched,
                                            icon = if (allWatched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            isPrimary = false,
                                            onClick = extraActions.onToggleSeasonWatched
                                        )
                                    }
                                }
                            }

                            if (state.episodesLoading) {
                                // Static placeholders (no endlessly spinning indicator on a weak SoC).
                                items(EPISODE_SKELETON_KEYS, key = { it }) {
                                    EpisodeRowSkeleton(modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp))
                                }
                            } else {
                                items(state.episodes, key = { it.id }) { ep ->
                                    EpisodeRowCard(
                                        episode = ep,
                                        onClick = {
                                            if (ep.isAvailableInJellyfin) {
                                                focusMemory.lastPlayedEpisodeId = ep.id
                                                onPlayItem(ep.id, null)
                                            } else {
                                                onOpenRequestModal()
                                            }
                                        },
                                        onLongClick = if (ep.isAvailableInJellyfin) {
                                            { extraActions.onOpenEpisodeMenu(ep) }
                                        } else null,
                                        modifier = Modifier
                                            .then(
                                                if (ep.id == focusMemory.lastPlayedEpisodeId) {
                                                    Modifier.focusRequester(returnEpisodeFocusRequester)
                                                } else Modifier
                                            )
                                            .padding(horizontal = 48.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                        DetailsSection.EXTRAS -> item(key = "extras_and_specials") {
                            ExtrasSection(
                                extras = item.extras,
                                fallbackBackdropUrl = item.backdropUrl ?: item.posterUrl,
                                onPlayItem = onPlayItem
                            )
                        }
                        DetailsSection.TRAILERS -> item(key = "trailers_and_more") {
                            RelatedVideosSection(
                                videos = item.relatedVideos,
                                fallbackBackdropUrl = item.backdropUrl ?: item.posterUrl,
                                onPlayTrailer = onPlayTrailer
                            )
                        }
                        DetailsSection.CAST -> item(key = "cast_section") {
                            CastSection(
                                cast = item.cast,
                                onSearchPerson = extraActions.onSearchPerson
                            )
                        }
                        DetailsSection.COLLECTION -> state.collectionSaga?.let { saga ->
                            item(key = "collection_saga") {
                                val missingCount = remember(saga.items) {
                                    saga.items.count { !it.isPlayable && it.canRequestHd }
                                }
                                val sagaFirstFocusRequester = remember { FocusRequester() }
                                Column(modifier = Modifier.padding(top = 4.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 48.dp, vertical = 10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Weighted so a long saga name ellipsizes instead of pushing the
                                        // completion badge and "Request Missing" button off-screen.
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            Text(
                                                text = strings.detailsPartOf.format(saga.name),
                                                color = colors.white,
                                                fontSize = 18.sp,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(colors.secondaryButtonBg)
                                                    .border(1.dp, colors.badgeBorder, RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                            ) {
                                                Text(
                                                    text = com.seerflix.tv.core.data.UpcomingDateFormatter.collectionCompletionLabel(saga.ownedCount, saga.totalCount, AppStrings.current),
                                                    color = colors.matchGreen,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }

                                        if (missingCount > 0 && state.isSeerrConfigured && state.canSubmitRequests) {
                                            HeroPillButton(
                                                text = strings.detailsRequestMissing.format(missingCount),
                                                icon = Icons.Filled.Add,
                                                isPrimary = false,
                                                onClick = {
                                                    onRequestAllMissingInCollection()
                                                    // The button disappears once nothing is missing;
                                                    // move focus into the saga row rather than losing it.
                                                    runCatching { sagaFirstFocusRequester.requestFocus() }
                                                }
                                            )
                                        }
                                    }

                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 48.dp),
                                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                                    ) {
                                        items(saga.items, key = { "saga_${it.id}" }) { part ->
                                            NetflixPosterCard(
                                                item = part,
                                                onFocus = {},
                                                onClick = { onOpenDetails(part) },
                                                onOptionsMenu = if (!part.isPlayable && part.canRequestHd && state.isSeerrConfigured && state.canSubmitRequests) {
                                                    { onRequestCollectionItem(part) }
                                                } else null,
                                                modifier = if (part.id == saga.items.firstOrNull()?.id) {
                                                    Modifier.focusRequester(sagaFirstFocusRequester)
                                                } else {
                                                    Modifier
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        DetailsSection.MORE_LIKE_THIS -> item(key = "more_like_this") {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                items(state.recommendations, key = { it.id }) { rec ->
                                    NetflixPosterCard(
                                        item = rec,
                                        onFocus = {},
                                        onClick = { onOpenDetails(rec) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.showRequestModal && state.item != null) {
        RequestQualityModal(
            item = state.item!!,
            has4kServer = state.has4kServer,
            arrServers = state.arrServers,
            arrServersLoading = state.arrServersLoading,
            isSubmitting = state.isSubmittingRequest,
            onDismiss = onCloseRequestModal,
            onSubmit = onSubmitRequest
        )
    }

    if (state.showReportIssueModal && state.item != null) {
        ReportIssueModal(
            title = state.item!!.title,
            isSubmitting = state.isReportingIssue,
            onDismiss = onCloseReportIssue,
            onSubmit = onSubmitIssue
        )
    }

    if (state.showPlaylistDialog && state.item != null) {
        AddToPlaylistDialog(
            itemTitle = state.item!!.title,
            playlists = state.playlists,
            onSelectPlaylist = extraActions.onAddToPlaylist,
            onCreatePlaylist = extraActions.onCreatePlaylist,
            onDismiss = extraActions.onDismissPlaylistDialog
        )
    }

    if (state.showMediaInfoDialog && state.item != null) {
        MediaInfoDialog(
            item = state.item!!,
            onDismiss = extraActions.onDismissMediaInfo
        )
    }

    if (state.showTrackPickerDialog && state.item != null) {
        val currentItem = state.item!!
        TrackPickerDialog(
            title = currentItem.title,
            audioTracks = currentItem.availableAudioTracks,
            subtitleTracks = currentItem.availableSubtitleTracks,
            onPlayWithSelection = { audioIdx, subIdx ->
                extraActions.onDismissTrackPicker()
                val sourceId = if (currentItem.mediaType == MediaType.SERIES) null else state.selectedMediaSourceId
                val versionItemId = sourceId?.let { id ->
                    currentItem.mediaVersions.firstOrNull { it.id == id }?.itemId
                }
                // The listed streams are the first version's: their indexes only apply to it.
                val tracksMatchVersion = sourceId == null || sourceId == currentItem.mediaVersions.firstOrNull()?.id
                (versionItemId ?: currentItem.jellyfinId)?.let { id ->
                    extraActions.onPlayWithOptions(
                        id,
                        sourceId,
                        false,
                        audioIdx.takeIf { tracksMatchVersion },
                        subIdx.takeIf { tracksMatchVersion }
                    )
                }
            },
            onDismiss = extraActions.onDismissTrackPicker
        )
    }

    if (state.showDeleteConfirmDialog && state.item != null && state.item!!.canDelete) {
        DeleteConfirmDialog(
            title = state.item!!.title,
            onConfirmDelete = extraActions.onConfirmDelete,
            onDismiss = extraActions.onDismissDeleteConfirm
        )
    }

    state.episodeMenuTarget?.let { ep ->
        EpisodeActionDialog(
            episode = ep,
            onPlay = {
                extraActions.onDismissEpisodeMenu()
                onPlayItem(ep.id, null)
            },
            onPlayFromBeginning = {
                extraActions.onDismissEpisodeMenu()
                extraActions.onPlayWithOptions(ep.id, null, true, null, null)
            },
            onToggleWatched = { extraActions.onToggleEpisodeWatched(ep) },
            onDismiss = extraActions.onDismissEpisodeMenu
        )
    }
}

// Sections below take only their own slice of the item. DetailsViewModel reuses unchanged list
// instances across refreshes, so a 15s poll tick that only moves a download bar skips these.

@Composable
private fun MediaVersionsSection(
    versions: List<MediaSourceOption>,
    selectedId: String?,
    onSelectMediaSource: (String) -> Unit
) {
    val colors = SeerflixTokens.colors
    Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(versions, key = { it.id }) { src ->
                SeasonTabChip(
                    label = src.displayLabel,
                    isSelected = src.id == selectedId,
                    isAvailable = true,
                    onClick = { onSelectMediaSource(src.id) }
                )
            }
        }
    }
}

@Composable
private fun ExtrasSection(
    extras: List<ExtrasItem>,
    fallbackBackdropUrl: String?,
    onPlayItem: (String, String?) -> Unit
) {
    val colors = SeerflixTokens.colors
    Column(modifier = Modifier.padding(top = 10.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(extras, key = { it.id }) { extra ->
                ExtrasFeatureCard(
                    extra = extra,
                    fallbackBackdropUrl = fallbackBackdropUrl,
                    onClick = { onPlayItem(extra.id, null) }
                )
            }
        }
    }
}

@Composable
private fun RelatedVideosSection(
    videos: List<RelatedVideoItem>,
    fallbackBackdropUrl: String?,
    onPlayTrailer: (title: String, youtubeKey: String?, localTrailerId: String?) -> Unit
) {
    val colors = SeerflixTokens.colors
    Column(modifier = Modifier.padding(top = 10.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            itemsIndexed(videos, key = { idx, v -> "video_${idx}_${v.youtubeKey}_${v.localTrailerItemId}" }) { _, video ->
                RelatedVideoCard(
                    video = video,
                    fallbackBackdropUrl = fallbackBackdropUrl,
                    onClick = {
                        onPlayTrailer(video.title, video.youtubeKey, video.localTrailerItemId)
                    }
                )
            }
        }
    }
}

@Composable
private fun CastSection(
    cast: List<CastMember>,
    onSearchPerson: (name: String, personId: Int?, jellyfinPersonId: String?) -> Unit
) {
    val colors = SeerflixTokens.colors
    Column(modifier = Modifier.padding(top = 10.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TMDB can list the same actor/role twice; name+role alone isn't unique.
            itemsIndexed(cast, key = { idx, m -> "cast_${idx}_${m.name}" }) { _, member ->
                var castFocused by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .width(108.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (castFocused) colors.surfaceElevated else Color.Transparent)
                        .then(
                            if (castFocused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .onFocusChanged { castFocused = it.isFocused }
                        .clickable { onSearchPerson(member.name, member.personId, member.jellyfinPersonId) }
                        .padding(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AsyncImage(
                        model = member.imageUrl,
                        contentDescription = member.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(42.dp))
                            .background(colors.surfaceElevated)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = member.name,
                        color = if (castFocused) colors.white else colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (member.role.isNotBlank()) {
                        Text(
                            text = member.role,
                            color = colors.textMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ---- Static skeletons (no shimmer: a continuously animating placeholder costs frames on a stick) ----

private val EPISODE_SKELETON_KEYS = listOf("eps_skeleton_0", "eps_skeleton_1", "eps_skeleton_2")

@Composable
private fun SkeletonBlock(
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp
) {
    Box(
        modifier = modifier
            .size(width, height)
            .clip(RoundedCornerShape(corner))
            .background(SeerflixTokens.colors.surfaceElevated.copy(alpha = 0.75f))
    )
}

/** Placeholder for action buttons whose state isn't known yet (non-focusable on purpose). */
@Composable
private fun SkeletonPill(width: Dp) {
    SkeletonBlock(width = width, height = 40.dp, corner = SeerflixTokens.spec.buttonCornerRadiusDp.dp)
}

/** Full-page placeholder used only when there is no seed to show. */
@Composable
private fun DetailsLoadingSkeleton() {
    // The seed usually lands within a frame or two (profile read); don't flash gray blocks first.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(150L)
        visible = true
    }
    if (!visible) return
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(400.dp)
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 48.dp, end = 160.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SkeletonBlock(96.dp, 20.dp, corner = 4.dp)
                    SkeletonBlock(96.dp, 20.dp, corner = 4.dp)
                }
                SkeletonBlock(360.dp, 44.dp)
                SkeletonBlock(240.dp, 16.dp)
                SkeletonBlock(520.dp, 14.dp)
                SkeletonBlock(460.dp, 14.dp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SkeletonPill(120.dp)
                    SkeletonPill(150.dp)
                    SkeletonPill(120.dp)
                }
            }
        }
    }
}

/** Below-the-header placeholder while the seed is showing. */
@Composable
private fun DetailsSectionsSkeleton(isSeries: Boolean) {
    Column(
        modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SkeletonBlock(180.dp, 20.dp)
        if (isSeries) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(4) { SkeletonPill(96.dp) }
            }
            EpisodeRowSkeleton()
            EpisodeRowSkeleton()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(7) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.width(108.dp).padding(6.dp)
                    ) {
                        SkeletonBlock(84.dp, 84.dp, corner = 42.dp)
                        SkeletonBlock(72.dp, 10.dp, corner = 3.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRowSkeleton(modifier: Modifier = Modifier) {
    val colors = SeerflixTokens.colors
    val corner = RoundedCornerShape(SeerflixTokens.spec.cardCornerRadiusDp.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(corner)
            .background(colors.surface)
            .padding(12.dp)
    ) {
        Spacer(Modifier.width(36.dp))
        SkeletonBlock(168.dp, 94.dp)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SkeletonBlock(220.dp, 16.dp)
            SkeletonBlock(420.dp, 12.dp)
            SkeletonBlock(360.dp, 12.dp)
        }
    }
}

@Composable
private fun RelatedVideoCard(
    video: RelatedVideoItem,
    fallbackBackdropUrl: String?,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(corner)
                .background(colors.surfaceElevated)
                .then(
                    if (focused) Modifier.border(2.5.dp, colors.focusBorder, corner)
                    else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.45f), corner)
                )
        ) {
            AsyncImage(
                model = video.thumbnailUrl ?: fallbackBackdropUrl,
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = AppStrings.current.detailsPlayTrailer,
                tint = colors.white,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.deepBlack.copy(alpha = 0.72f))
                    .padding(6.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.deepBlack.copy(alpha = 0.78f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(video.type, color = colors.white, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = video.title,
            color = if (focused) colors.white else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ExtrasFeatureCard(
    extra: ExtrasItem,
    fallbackBackdropUrl: String?,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(220.dp)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(corner)
                .background(colors.surfaceElevated)
                .then(
                    if (focused) Modifier.border(2.5.dp, colors.focusBorder, corner)
                    else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.45f), corner)
                )
        ) {
            AsyncImage(
                model = extra.thumbnailUrl ?: fallbackBackdropUrl,
                contentDescription = extra.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = AppStrings.current.detailsPlayExtra,
                tint = colors.white,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.deepBlack.copy(alpha = 0.72f))
                    .padding(6.dp)
            )
            extra.extraType?.takeIf { it.isNotBlank() }?.let { et ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.deepBlack.copy(alpha = 0.78f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(et, color = colors.white, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = extra.name,
            color = if (focused) colors.white else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        extra.runtimeMinutes?.takeIf { it > 0 }?.let { mins ->
            Text(AppStrings.current.commonMinutesShort.format(mins), color = colors.textMuted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun DetailsHeaderSection(
    item: UnifiedMediaItem,
    hasPlayableEpisode: Boolean,
    isRefining: Boolean,
    focusMemory: DetailsFocusMemory,
    isSeerrConfigured: Boolean,
    canSubmitRequests: Boolean,
    feedbackBanner: String?,
    onBack: () -> Unit,
    canDeleteItems: Boolean = true,
    onPlay: () -> Unit,
    onPlayFromBeginning: () -> Unit = {},
    onRequestClick: () -> Unit,
    onWatchTrailer: () -> Unit,
    onToggleMyList: () -> Unit,
    onToggleReminder: () -> Unit,
    onToggleWatched: () -> Unit,
    onOpenExternalPlayer: () -> Unit,
    onOpenReportIssue: () -> Unit,
    onRemoveFromContinueWatching: () -> Unit,
    onCancelRequest: (Int) -> Unit,
    onOpenPlaylistDialog: () -> Unit = {},
    onOpenMediaInfo: () -> Unit = {},
    onOpenTrackPicker: () -> Unit = {},
    onOpenDeleteConfirm: () -> Unit = {}
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val backCorner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
    ) {
        AsyncImage(
            model = item.backdropUrl ?: item.posterUrl,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to colors.background.copy(alpha = 0.98f),
                            0.45f to colors.background.copy(alpha = 0.85f),
                            1.0f to colors.background.copy(alpha = 0.2f)
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to colors.deepBlack.copy(alpha = 0.4f),
                            0.60f to Color.Transparent,
                            1.0f to colors.background
                        )
                    )
                )
        )

        // Back button top-left
        var backFocused by remember { mutableStateOf(false) }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 48.dp, top = 28.dp)
                .clip(backCorner)
                .background(if (backFocused) colors.secondaryButtonFocusedBg else colors.secondaryButtonBg)
                .then(if (backFocused) Modifier.border(2.dp, colors.focusBorder, backCorner) else Modifier)
                .onFocusChanged { backFocused = it.isFocused }
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.commonBack, tint = colors.white, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(strings.commonBack, color = colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 160.dp, bottom = 20.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isRefining) {
                    // The card's per-tier status may be stale/incomplete; wait for the real one.
                    SkeletonBlock(96.dp, 20.dp, corner = 4.dp)
                    SkeletonBlock(96.dp, 20.dp, corner = 4.dp)
                } else {
                    // One status per tier. A tier that is downloading is shown by its progress
                    // card below instead (it used to appear as a badge, a pill AND the card).
                    if (item.hdDownload == null && item.hdStatus != AvailabilityTier.UNKNOWN) TierPill("HD", item.hdStatus)
                    if (item.uhdDownload == null && item.uhdStatus != AvailabilityTier.UNKNOWN) TierPill("4K", item.uhdStatus)
                }
            }

            Spacer(Modifier.height(10.dp))

            if (!item.clearLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = item.clearLogoUrl,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .height(72.dp)
                        .fillMaxWidth(0.6f)
                )
            } else {
                Text(
                    text = item.title,
                    color = colors.white,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            // Rating, year, maturity, runtime, genres, format badges — same line as the banner.
            TitleMetaLine(
                item = item,
                fontSize = 14.sp,
                maxBadges = 3,
                extraText = item.genres.take(3)
            )

            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = item.overview,
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Live Seerr Download Progress & ETA Bars (HD & 4K independently!)
            item.hdDownload?.let { dl ->
                Spacer(Modifier.height(10.dp))
                DownloadProgressCard(dl)
            }
            item.uhdDownload?.let { dl ->
                Spacer(Modifier.height(8.dp))
                DownloadProgressCard(dl)
            }

            feedbackBanner?.let { msg ->
                Spacer(Modifier.height(8.dp))
                Text(msg, color = colors.matchGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            val primaryActionFocusRequester = remember { FocusRequester() }
            // Play works from the seed alone (a jellyfinId is enough; for a series the playback
            // manager resolves next-up). Everything that depends on real availability / user data
            // (request, My List, watched, reminder, issue, cancel) waits for the full load.
            val canPlay = item.isPlayable || hasPlayableEpisode
            val actionsReady = !isRefining
            val showReqButton = actionsReady && isSeerrConfigured && item.tmdbId != null && (item.canRequestHd || item.canRequest4k || item.mediaType == MediaType.SERIES)
            val showMyList = actionsReady
            val showWatched = actionsReady && item.jellyfinId != null
            val showRemind = actionsReady && !canPlay && item.overallAvailability == AvailabilityTier.UNKNOWN && item.tmdbId != null
            val showTrailer = actionsReady && (!item.youtubeTrailerKey.isNullOrBlank() || !item.localTrailerItemId.isNullOrBlank())
            val showIssue = actionsReady && isSeerrConfigured && canPlay && item.tmdbId != null
            val showRmProgress = actionsReady && item.resumePositionTicks > 0L && item.jellyfinId != null
            val pendingReqId = item.uhdRequestId ?: item.hdRequestId
            val showCancelReq = actionsReady && pendingReqId != null && !canPlay && item.overallAvailability.isRequestedOrDownloading
            // Which button carries primaryActionFocusRequester (mirrors the modifiers below);
            // null for the seed of a not-yet-playable title, where nothing is actionable yet.
            val primaryKey: String? = when {
                canPlay -> "act_play"
                showReqButton -> "act_req"
                showMyList -> "act_mylist"
                else -> null
            }

            // Several actions remove their own button (Remove Progress, Cancel Request, Request once
            // fulfilled, or a live-poll status change). When the focused button vanishes the remote
            // is left with nothing focused, so hand focus back to the primary action. A plain var
            // (not state) tracks the focused key so focus callbacks don't trigger recomposition.
            val focusTracker = remember { ActionFocusTracker() }
            // Initial focus: once, as soon as a primary action exists (seed or full data). Keyed on
            // the primary button rather than item.id, so the seed -> full swap (whose id can differ)
            // and later poll updates never pull focus back after the user has moved it.
            LaunchedEffect(primaryKey) {
                if (primaryKey == null || focusMemory.initialFocusDone) return@LaunchedEffect
                repeat(5) { attempt ->
                    delay(if (attempt == 0) 60L else 50L)
                    if (focusMemory.initialFocusDone) return@LaunchedEffect
                    if (focusTracker.focusedKey != null || backFocused) {
                        // The user already navigated somewhere in the header; leave focus there.
                        focusMemory.initialFocusDone = true
                        return@LaunchedEffect
                    }
                    // Throws until the requester's node is attached; retry briefly.
                    // requestFocus() returns false (no longer throws) while the node isn't attached.
                    if (runCatching { primaryActionFocusRequester.requestFocus() }.getOrDefault(false)) {
                        focusMemory.initialFocusDone = true
                        return@LaunchedEffect
                    }
                }
            }
            val hasMoreActions = actionsReady && (canPlay || showWatched || showRemind || showTrailer ||
                showIssue || showRmProgress || showCancelReq)
            val visibleActionKeys = buildSet {
                if (canPlay) add("act_play")
                if (showReqButton) add("act_req")
                if (showMyList) add("act_mylist")
                if (hasMoreActions) add("act_more")
            }
            val lostFocusKey = focusTracker.focusedKey?.takeIf { it !in visibleActionKeys }
            LaunchedEffect(lostFocusKey) {
                if (lostFocusKey != null) {
                    focusTracker.focusedKey = null
                    runCatching { primaryActionFocusRequester.requestFocus() }
                }
            }
            fun Modifier.trackActionFocus(key: String): Modifier = onFocusChanged { fs ->
                if (fs.isFocused) {
                    focusTracker.focusedKey = key
                } else if (focusTracker.focusedKey == key) {
                    focusTracker.focusedKey = null
                }
            }
            var showMoreMenu by remember { mutableStateOf(false) }
            val moreFocusRequester = remember { FocusRequester() }

            // Three main actions (Play / Request / My List) and a "More" menu for the rest, so the
            // row always fits on screen instead of scrolling buttons off the right edge.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (canPlay) {
                    HeroPillButton(
                        text = if (item.resumePositionTicks > 0L) strings.actionResume else strings.actionPlay,
                        icon = Icons.Filled.PlayArrow,
                        isPrimary = true,
                        onClick = onPlay,
                        modifier = Modifier
                            .focusRequester(primaryActionFocusRequester)
                            .trackActionFocus("act_play")
                    )
                }

                if (showReqButton) {
                    val reqModifier = (if (!canPlay) Modifier.focusRequester(primaryActionFocusRequester) else Modifier)
                        .trackActionFocus("act_req")
                    if (canSubmitRequests) {
                        HeroPillButton(
                            text = if (canPlay) strings.detailsRequest4kMore else strings.detailsRequestHd4k,
                            icon = Icons.Filled.Add,
                            isPrimary = !canPlay,
                            onClick = onRequestClick,
                            modifier = reqModifier
                        )
                    } else {
                        HeroPillButton(
                            text = strings.labelKidsLocked,
                            icon = Icons.Filled.Lock,
                            isPrimary = false,
                            onClick = onRequestClick,
                            modifier = reqModifier
                        )
                    }
                }

                if (showMyList) {
                    HeroPillButton(
                        text = if (item.isInMyList) strings.detailsInMyList else strings.navMyList,
                        icon = if (item.isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                        isPrimary = false,
                        onClick = onToggleMyList,
                        modifier = (if (!canPlay && !showReqButton) Modifier.focusRequester(primaryActionFocusRequester) else Modifier)
                            .trackActionFocus("act_mylist")
                    )
                }

                if (isRefining) {
                    // Request / My List / More take these slots once availability is known.
                    SkeletonPill(if (canPlay) 120.dp else 170.dp)
                    SkeletonPill(120.dp)
                }

                if (hasMoreActions) {
                    HeroPillButton(
                        text = strings.detailsMore,
                        icon = Icons.Filled.MoreHoriz,
                        isPrimary = false,
                        onClick = { showMoreMenu = true },
                        modifier = Modifier
                            .focusRequester(moreFocusRequester)
                            .trackActionFocus("act_more")
                    )
                }
            }

            if (showMoreMenu) {
                val close: (() -> Unit) -> () -> Unit = { action ->
                    {
                        showMoreMenu = false
                        action()
                    }
                }
                val showTrackPicker = canPlay && (item.availableAudioTracks.size > 1 || item.availableSubtitleTracks.isNotEmpty())
                val showMediaInfo = item.jellyfinId != null && (item.mediaStreamSpecs.isNotEmpty() || !item.mediaFilePath.isNullOrBlank() || !item.mediaContainer.isNullOrBlank())
                val showAddToPlaylist = item.jellyfinId != null
                DetailsMoreMenu(
                    title = item.title,
                    actions = buildList {
                        if (canPlay && item.resumePositionTicks > 0L) {
                            add(MoreAction(Icons.Filled.Replay, strings.actionPlayFromBeginning, close(onPlayFromBeginning)))
                        }
                        if (showTrackPicker) {
                            add(MoreAction(Icons.Filled.Subtitles, strings.actionTrackPicker, close(onOpenTrackPicker)))
                        }
                        if (showTrailer) add(MoreAction(Icons.Filled.Theaters, strings.actionTrailer, close(onWatchTrailer)))
                        if (canPlay) add(MoreAction(Icons.AutoMirrored.Filled.OpenInNew, strings.actionExternalPlayer, close(onOpenExternalPlayer)))
                        if (showAddToPlaylist) {
                            add(MoreAction(Icons.AutoMirrored.Filled.PlaylistAdd, strings.actionAddToPlaylist, close(onOpenPlaylistDialog)))
                        }
                        if (showWatched) add(
                            MoreAction(
                                if (item.isWatched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                if (item.isWatched) strings.actionMarkUnwatched else strings.actionMarkWatched,
                                close(onToggleWatched)
                            )
                        )
                        if (showRemind) add(
                            MoreAction(
                                if (item.isReminderSet) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                                if (item.isReminderSet) strings.detailsReminderSet else strings.detailsRemindMe,
                                close(onToggleReminder)
                            )
                        )
                        if (showRmProgress) add(MoreAction(Icons.Filled.DeleteOutline, strings.detailsRemoveProgress, close(onRemoveFromContinueWatching)))
                        if (showMediaInfo) {
                            add(MoreAction(Icons.Filled.Info, strings.actionMediaInfo, close(onOpenMediaInfo)))
                        }
                        if (showIssue) add(MoreAction(Icons.Filled.Flag, strings.actionReportIssue, close(onOpenReportIssue)))
                        if (showCancelReq && pendingReqId != null) {
                            add(MoreAction(Icons.Filled.Close, strings.detailsCancelRequest, close { onCancelRequest(pendingReqId) }))
                        }
                        if (canDeleteItems && item.canDelete && item.jellyfinId != null) {
                            add(MoreAction(Icons.Filled.DeleteOutline, strings.actionDeleteItem, close(onOpenDeleteConfirm)))
                        }
                    },
                    onDismiss = {
                        showMoreMenu = false
                        runCatching { moreFocusRequester.requestFocus() }
                    }
                )
            }
        }
    }
}

private class MoreAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** The Details "More" menu: every secondary action in one focus-trapped list. */
@Composable
private fun DetailsMoreMenu(
    title: String,
    actions: List<MoreAction>,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val shape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(30L)
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(400.dp)
                .heightIn(max = 520.dp)
                .clip(shape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, shape)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                title,
                color = colors.white,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            actions.forEachIndexed { index, action ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = (if (index == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (focused) colors.white else colors.surface)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable(onClick = action.onClick)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    val fg = if (focused) colors.deepBlack else colors.white
                    Icon(action.icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(action.label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun TierPill(tierName: String, status: AvailabilityTier) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    val (label, color) = when (status) {
        AvailabilityTier.AVAILABLE -> strings.detailsTierAvailable.format(tierName) to colors.matchGreen
        AvailabilityTier.PARTIALLY_AVAILABLE -> strings.detailsTierPartial.format(tierName) to colors.pendingAmber
        AvailabilityTier.PROCESSING -> strings.detailsTierDownloading.format(tierName) to colors.downloadingBlue
        AvailabilityTier.PENDING -> strings.detailsTierRequested.format(tierName) to colors.pendingAmber
        else -> strings.detailsTierNotInLibrary.format(tierName) to colors.textMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surface.copy(alpha = 0.78f))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DownloadProgressCard(dl: DownloadProgressInfo) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .clip(corner)
            .background(colors.surface.copy(alpha = 0.88f))
            .border(1.dp, colors.downloadingBlue.copy(alpha = 0.6f), corner)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dl.label,
                color = colors.white,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            dl.title?.let {
                Text(
                    text = it,
                    color = colors.textSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(180.dp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x44FFFFFF))
        ) {
            val frac = if (dl.sizeBytes > 0L) (dl.percentage / 100f).coerceIn(0.02f, 1f) else 0.35f
            Box(
                modifier = Modifier
                    .fillMaxWidth(frac)
                    .fillMaxHeight()
                    .background(colors.progressFill)
            )
        }
    }
}

@Composable
private fun SeasonTabChip(
    label: String,
    isSelected: Boolean,
    isAvailable: Boolean,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.buttonCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(corner)
            .background(
                when {
                    isSelected -> colors.white
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, colors.accent, corner)
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) colors.deepBlack else colors.white,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (isAvailable) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (isSelected) colors.deepBlack else colors.matchGreen,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EpisodeRowCard(
    episode: EpisodeSummary,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(corner)
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .then(
                if (focused) Modifier.border(2.dp, colors.focusBorder, corner)
                else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.4f), corner)
            )
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(12.dp)
    ) {
        Text(
            text = "${episode.episodeNumber}",
            color = colors.textSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )

        Box(
            modifier = Modifier
                .width(168.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.surfaceElevated)
        ) {
            AsyncImage(
                model = episode.stillUrl,
                contentDescription = episode.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (episode.isAvailableInJellyfin) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = strings.actionPlay,
                    tint = colors.white,
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(17.dp))
                        .background(colors.deepBlack.copy(alpha = 0.65f))
                        .padding(4.dp)
                )
            }
            if (episode.isWatched) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = strings.actionMarkWatched,
                    tint = colors.matchGreen,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.deepBlack.copy(alpha = 0.8f))
                        .padding(3.dp)
                )
            }
            if (episode.progressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.BottomCenter)
                        .background(Color(0x66FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(episode.progressFraction)
                            .fillMaxHeight()
                            .background(colors.progressFill)
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = episode.name,
                    color = colors.white,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.runtimeMinutes > 0) {
                    Text(
                        text = strings.commonMinutesShort.format(episode.runtimeMinutes),
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
                if (!episode.isAvailableInJellyfin) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(strings.detailsNotOnServer, color = colors.pendingAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                } else if (focused && onLongClick != null) {
                    Text(
                        text = strings.cardHoldOk,
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }
            }
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episode.overview,
                    color = colors.textSecondary,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RequestQualityModal(
    item: UnifiedMediaItem,
    has4kServer: Boolean,
    arrServers: List<ArrServerOption>,
    arrServersLoading: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (
        requestHd: Boolean,
        request4k: Boolean,
        seasons: List<Int>?,
        hdServerId: Int?,
        hdProfileId: Int?,
        hdRootFolder: String?,
        uhdServerId: Int?,
        uhdProfileId: Int?,
        uhdRootFolder: String?
    ) -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)

    // Movies: a tier that is already requested/downloading (PENDING/PROCESSING) isn't "in library"
    // yet, so the old !hasHd check pre-ticked and allowed re-requesting it (Seerr then 409s).
    // Only offer tiers Seerr will accept. Series keep per-season requests enabled.
    val isSeries = item.mediaType == MediaType.SERIES
    val hdRequestable = if (isSeries) true else item.canRequestHd
    val uhdRequestable = if (isSeries) true else item.canRequest4k
    var requestHd by remember { mutableStateOf(if (isSeries) !item.hasHd else item.canRequestHd) }
    var request4k by remember {
        mutableStateOf(
            if (isSeries) item.hasHd && !item.has4k && has4kServer
            else !item.canRequestHd && item.canRequest4k && has4kServer
        )
    }

    val hdServers = remember(arrServers) { arrServers.filter { !it.is4k } }
    val uhdServers = remember(arrServers) { arrServers.filter { it.is4k } }

    var selectedHdServer by remember(hdServers) {
        mutableStateOf(hdServers.firstOrNull { it.isDefault } ?: hdServers.firstOrNull())
    }
    var selectedHdProfileId by remember(selectedHdServer) {
        mutableStateOf(selectedHdServer?.initialProfileId)
    }
    var selectedHdRoot by remember(selectedHdServer) {
        mutableStateOf(selectedHdServer?.initialRootFolder)
    }

    var selectedUhdServer by remember(uhdServers) {
        mutableStateOf(uhdServers.firstOrNull { it.isDefault } ?: uhdServers.firstOrNull())
    }
    var selectedUhdProfileId by remember(selectedUhdServer) {
        mutableStateOf(selectedUhdServer?.initialProfileId)
    }
    var selectedUhdRoot by remember(selectedUhdServer) {
        mutableStateOf(selectedUhdServer?.initialRootFolder)
    }

    val selectedSeasons = remember {
        mutableStateListOf<Int>().apply {
            val missing = item.seasons.filter { !it.isPlayable }.map { it.seasonNumber }
            if (missing.isNotEmpty()) addAll(missing) else addAll(item.seasons.map { it.seasonNumber })
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(560.dp)
                .clip(dialogCorner)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogCorner)
                .verticalScroll(rememberScrollState())
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = strings.detailsRequestTitle.format(item.title),
                color = colors.white,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = strings.detailsRequestIntro,
                color = colors.textSecondary,
                fontSize = 13.sp
            )

            // HD Option
            QualityToggleRow(
                title = strings.detailsQualityHd,
                subtitle = when {
                    !isSeries && item.hasHd -> strings.detailsAlreadyInLibrary
                    !hdRequestable -> strings.detailsAlreadyRequested
                    else -> selectedHdServer?.name ?: strings.detailsStandardProfile
                },
                checked = requestHd && hdRequestable,
                enabled = hdRequestable,
                onToggle = { requestHd = !requestHd }
            )

            if (requestHd && hdServers.isNotEmpty()) {
                ArrServerOverrideSection(
                    servers = hdServers,
                    selectedServer = selectedHdServer,
                    selectedProfileId = selectedHdProfileId,
                    selectedRootFolder = selectedHdRoot,
                    onSelectServer = { selectedHdServer = it },
                    onSelectProfile = { selectedHdProfileId = it },
                    onSelectRoot = { selectedHdRoot = it }
                )
            }

            // 4K Option
            if (has4kServer) {
                QualityToggleRow(
                    title = strings.detailsQuality4k,
                    subtitle = when {
                        !isSeries && item.has4k -> strings.detailsAlreadyInLibrary
                        !uhdRequestable -> strings.detailsAlreadyRequested
                        else -> selectedUhdServer?.name ?: strings.details4kProfile
                    },
                    checked = request4k && uhdRequestable,
                    enabled = uhdRequestable,
                    onToggle = { request4k = !request4k }
                )

                if (request4k && uhdServers.isNotEmpty()) {
                    ArrServerOverrideSection(
                        servers = uhdServers,
                        selectedServer = selectedUhdServer,
                        selectedProfileId = selectedUhdProfileId,
                        selectedRootFolder = selectedUhdRoot,
                        onSelectServer = { selectedUhdServer = it },
                        onSelectProfile = { selectedUhdProfileId = it },
                        onSelectRoot = { selectedUhdRoot = it }
                    )
                }
            }

            if (arrServersLoading) {
                Text(strings.detailsLoadingProfiles, color = colors.textMuted, fontSize = 12.sp)
            }

            if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                Text(
                    text = strings.detailsSelectSeasons.format(selectedSeasons.size),
                    color = colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(item.seasons, key = { idx, season -> "req_season_${idx}_${season.seasonNumber}" }) { _, s ->
                        val isPicked = s.seasonNumber in selectedSeasons
                        SeasonTabChip(
                            label = "S${s.seasonNumber}",
                            isSelected = isPicked,
                            isAvailable = s.isPlayable,
                            onClick = {
                                if (isPicked) selectedSeasons.remove(s.seasonNumber)
                                else selectedSeasons.add(s.seasonNumber)
                            }
                        )
                    }
                }
            }

            val sendRequestFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) {
                delay(60L)
                runCatching { sendRequestFocusRequester.requestFocus() }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HeroPillButton(
                    text = if (isSubmitting) strings.commonSending else strings.detailsSendRequest,
                    icon = Icons.Filled.Check,
                    isPrimary = true,
                    onClick = {
                        // has4kServer can flip to false after the modal opened (it loads async and
                        // defaults to true): never submit a hidden 4K tier.
                        val effectiveHd = requestHd && hdRequestable
                        val effective4k = request4k && uhdRequestable && has4kServer
                        // An empty season selection would be sent as "no seasons", which the
                        // repository expands to *every* season. Require at least one.
                        val seasonsOk = !isSeries || item.seasons.isEmpty() || selectedSeasons.isNotEmpty()
                        if (!isSubmitting && (effectiveHd || effective4k) && seasonsOk) {
                            val seasonsList = if (isSeries) selectedSeasons.toList() else null
                            onSubmit(
                                effectiveHd,
                                effective4k,
                                seasonsList,
                                selectedHdServer?.id,
                                selectedHdProfileId,
                                selectedHdRoot,
                                selectedUhdServer?.id,
                                selectedUhdProfileId,
                                selectedUhdRoot
                            )
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(sendRequestFocusRequester)
                )
                HeroPillButton(
                    text = AppStrings.current.actionCancel,
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ArrServerOverrideSection(
    servers: List<ArrServerOption>,
    selectedServer: ArrServerOption?,
    selectedProfileId: Int?,
    selectedRootFolder: String?,
    onSelectServer: (ArrServerOption) -> Unit,
    onSelectProfile: (Int) -> Unit,
    onSelectRoot: (String) -> Unit
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (servers.size > 1) {
            Text(strings.detailsServer, color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(servers, key = { "srv_${it.id}" }) { srv ->
                    SeasonTabChip(
                        label = srv.name,
                        isSelected = srv.id == selectedServer?.id,
                        isAvailable = srv.isDefault,
                        onClick = { onSelectServer(srv) }
                    )
                }
            }
        }
        val profiles = selectedServer?.profiles.orEmpty()
        if (profiles.isNotEmpty()) {
            Text(strings.detailsQualityProfile, color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(profiles, key = { "prof_${it.id}" }) { prof ->
                    SeasonTabChip(
                        label = prof.name,
                        isSelected = prof.id == selectedProfileId,
                        isAvailable = false,
                        onClick = { onSelectProfile(prof.id) }
                    )
                }
            }
        }
        val roots = selectedServer?.rootFolders.orEmpty()
        if (roots.isNotEmpty()) {
            Text(strings.detailsRootFolder, color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roots, key = { "root_$it" }) { root ->
                    SeasonTabChip(
                        label = root,
                        isSelected = root == selectedRootFolder,
                        isAvailable = false,
                        onClick = { onSelectRoot(root) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportIssueModal(
    title: String,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (SeerrIssueType, String) -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val dialogCorner = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val strings = AppStrings.current
    var selectedType by remember { mutableStateOf(SeerrIssueType.VIDEO) }
    var message by remember { mutableStateOf("") }
    val submitIssueFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60L)
        runCatching { submitIssueFocusRequester.requestFocus() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .clip(dialogCorner)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, dialogCorner)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(strings.actionReportIssue, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(title, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SeerrIssueType.entries, key = { it.name }) { type ->
                    SeasonTabChip(
                        label = type.localized(strings),
                        isSelected = type == selectedType,
                        isAvailable = false,
                        onClick = { selectedType = type }
                    )
                }
            }

            com.seerflix.tv.ui.components.TvTextFieldContainer(
                modifier = Modifier.fillMaxWidth(),
                focusRingColor = colors.accent
            ) { fieldModifier ->
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    label = { Text(strings.detailsIssueHint) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.white,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.badgeBorder
                    ),
                    modifier = fieldModifier.fillMaxWidth()
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HeroPillButton(
                    text = if (isSubmitting) strings.detailsReporting else strings.detailsSubmitIssue,
                    icon = Icons.Filled.Flag,
                    isPrimary = true,
                    onClick = { if (!isSubmitting) onSubmit(selectedType, message) },
                    modifier = Modifier.weight(1f).focusRequester(submitIssueFocusRequester)
                )
                HeroPillButton(
                    text = AppStrings.current.actionCancel,
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QualityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(corner)
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, corner) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = CheckboxDefaults.colors(
                checkedColor = colors.accent,
                uncheckedColor = colors.textSecondary
            )
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = if (enabled) colors.white else colors.textMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = colors.textSecondary, fontSize = 12.sp)
        }
    }
}

private fun SeerrIssueType.localized(s: UiStrings): String = when (this) {
    SeerrIssueType.VIDEO -> s.issueVideo
    SeerrIssueType.AUDIO -> s.issueAudio
    SeerrIssueType.SUBTITLE -> s.issueSubtitle
    SeerrIssueType.OTHER -> s.issueOther
}

/** Holds which header action button currently has focus; deliberately not snapshot state. */
private class ActionFocusTracker {
    var focusedKey: String? = null
}

/** Whether the screen has already placed its initial focus; deliberately not snapshot state. */
private class DetailsFocusMemory {
    var initialFocusDone: Boolean = false
    /** Selected section tab; state so switching tabs recomposes the list. */
    var section: DetailsSection? by mutableStateOf(null)
    /** The episode last started from the list, so Back from the player lands on it. */
    var lastPlayedEpisodeId: String? = null

    companion object {
        val Saver: androidx.compose.runtime.saveable.Saver<DetailsFocusMemory, Any> =
            androidx.compose.runtime.saveable.listSaver(
                save = { listOf(it.initialFocusDone, it.section?.name.orEmpty(), it.lastPlayedEpisodeId.orEmpty()) },
                restore = { saved ->
                    DetailsFocusMemory().apply {
                        initialFocusDone = saved[0] as Boolean
                        section = (saved[1] as String).takeIf { it.isNotEmpty() }
                            ?.let { name -> DetailsSection.entries.firstOrNull { it.name == name } }
                        lastPlayedEpisodeId = (saved[2] as String).takeIf { it.isNotEmpty() }
                    }
                }
            )
    }
}

private enum class DetailsSection {
    EPISODES, VERSIONS, EXTRAS, TRAILERS, CAST, COLLECTION, MORE_LIKE_THIS
}

private fun DetailsSection.label(s: UiStrings): String = when (this) {
    DetailsSection.EPISODES -> s.detailsTabEpisodes
    DetailsSection.VERSIONS -> s.labelVersions
    DetailsSection.EXTRAS -> s.labelExtras
    DetailsSection.TRAILERS -> s.detailsTrailersMore
    DetailsSection.CAST -> s.detailsCast
    DetailsSection.COLLECTION -> s.detailsTabCollection
    DetailsSection.MORE_LIKE_THIS -> s.detailsMoreLikeThis
}

/**
 * Tabs for the sections under the header. A tab is chosen as soon as it is focused, so moving
 * along the tabs previews each section and Down goes straight into it.
 */
@Composable
private fun DetailsSectionTabs(
    sections: List<DetailsSection>,
    selected: DetailsSection?,
    onSelect: (DetailsSection) -> Unit
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    if (sections.size == 1) {
        // A single section needs no tabs: just its heading.
        Text(
            text = sections.first().label(strings),
            color = colors.white,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 14.dp)
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 8.dp)
    ) {
        items(sections, key = { it.name }) { section ->
            var focused by remember { mutableStateOf(false) }
            val isSelected = section == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (focused) colors.white else Color.Transparent)
                    .onFocusChanged {
                        focused = it.isFocused
                        if (it.isFocused) onSelect(section)
                    }
                    .clickable { onSelect(section) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = section.label(strings),
                    color = when {
                        focused -> colors.deepBlack
                        isSelected -> colors.white
                        else -> colors.textSecondary
                    },
                    fontSize = 16.sp,
                    fontWeight = if (isSelected || focused) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected && !focused) colors.accent else Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun AddToPlaylistDialog(
    itemTitle: String,
    playlists: List<PlaylistSummary>,
    onSelectPlaylist: (playlistId: String, playlistName: String) -> Unit,
    onCreatePlaylist: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val shape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    var newName by remember { mutableStateOf("") }
    val firstFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        // Re-run when the playlists arrive: the first row replaces the field as the focus target.
        LaunchedEffect(playlists.isEmpty()) {
            delay(40L)
            runCatching { firstFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(460.dp)
                .heightIn(max = 520.dp)
                .clip(shape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, shape)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(strings.actionAddToPlaylist, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(itemTitle, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            playlists.forEachIndexed { idx, pl ->
                var focused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = (if (idx == 0) Modifier.focusRequester(firstFocus) else Modifier)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (focused) colors.white else colors.surface)
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onSelectPlaylist(pl.id, pl.name) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = pl.name,
                        color = if (focused) colors.deepBlack else colors.white,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = strings.playlistItemCount.format(pl.itemCount),
                        color = if (focused) colors.deepBlack.copy(alpha = 0.7f) else colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(strings.playlistCreateNew, color = colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            // TV-safe field: the D-pad passes over it; OK opens the keyboard.
            com.seerflix.tv.ui.components.TvTextFieldContainer(
                modifier = Modifier.fillMaxWidth(),
                focusRingColor = colors.accent,
                containerFocusRequester = if (playlists.isEmpty()) firstFocus else null
            ) { fieldModifier ->
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(strings.playlistNameHint, color = colors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.white,
                        unfocusedTextColor = colors.white,
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.badgeBorder
                    ),
                    modifier = fieldModifier.fillMaxWidth()
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPillButton(
                    text = strings.playlistCreateNew,
                    icon = Icons.Filled.Add,
                    isPrimary = true,
                    onClick = { if (newName.isNotBlank()) onCreatePlaylist(newName) }
                )
                HeroPillButton(
                    text = strings.actionCancel,
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun MediaInfoDialog(
    item: UnifiedMediaItem,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val shape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    val closeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(40L)
            runCatching { closeFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(620.dp)
                .heightIn(max = 540.dp)
                .clip(shape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, shape)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(strings.mediaInfoDialogTitle, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(item.title, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            item.mediaFilePath?.takeIf { it.isNotBlank() }?.let { path ->
                MediaInfoRow(strings.mediaInfoFile, path)
            }
            item.mediaContainer?.takeIf { it.isNotBlank() }?.let { container ->
                MediaInfoRow(strings.mediaInfoContainer, container.uppercase())
            }
            item.mediaFileSizeBytes?.takeIf { it > 0L }?.let { bytes ->
                val gb = bytes / (1024.0 * 1024.0 * 1024.0)
                val formatted = if (gb >= 1.0) String.format(java.util.Locale.US, "%.2f GB", gb)
                else String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
                MediaInfoRow(strings.mediaInfoSize, formatted)
            }
            item.mediaBitrate?.takeIf { it > 0L }?.let { bps ->
                MediaInfoRow(strings.mediaInfoBitrate, String.format(java.util.Locale.US, "%.1f Mbps", bps / 1_000_000.0))
            }
            if (item.mediaStreamSpecs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(strings.mediaInfoStreams, color = colors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                item.mediaStreamSpecs.forEach { s ->
                    val details = listOfNotNull(
                        s.codec,
                        s.resolution,
                        s.hdrFormat,
                        s.bitDepth?.let { "${it}-bit" },
                        s.channels?.let {
                            when (it) {
                                8 -> "7.1"
                                6 -> "5.1"
                                2 -> "2.0"
                                else -> "${it}ch"
                            }
                        },
                        s.language,
                        s.bitrate?.takeIf { it > 0L }?.let {
                            if (it >= 1_000_000L) String.format(java.util.Locale.US, "%.1f Mbps", it / 1_000_000.0)
                            else "${it / 1000L} kbps"
                        },
                        if (s.isForced) strings.playerForcedBadge else null
                    ).distinct().joinToString(" • ")
                    // Jellyfin's MediaStream.Type; unknown types (EmbeddedImage, Data, ...) stay as sent.
                    val typeLabel = when (s.type.lowercase()) {
                        "video" -> strings.mediaInfoStreamVideo
                        "audio" -> strings.mediaInfoStreamAudio
                        "subtitle" -> strings.mediaInfoStreamSubtitle
                        else -> s.type
                    }
                    MediaInfoRow(typeLabel, details.ifBlank { s.title })
                }
            }
            Spacer(Modifier.height(10.dp))
            HeroPillButton(
                text = strings.commonClose,
                icon = Icons.Filled.Close,
                isPrimary = true,
                onClick = onDismiss,
                modifier = Modifier.focusRequester(closeFocus)
            )
        }
    }
}

@Composable
private fun MediaInfoRow(label: String, value: String) {
    val colors = SeerflixTokens.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = label,
            color = colors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(90.dp)
        )
        Text(
            text = value,
            color = colors.white,
            fontSize = 12.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TrackPickerDialog(
    title: String,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onPlayWithSelection: (audioIndex: Int?, subtitleIndex: Int?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val shape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    // null = "Default": nothing explicit is sent and the profile's preferred audio language /
    // subtitle mode decide in the player. Only a track the user actually picks is sent.
    var selectedAudio by remember { mutableStateOf<Int?>(null) }
    // null = "Default", SUBTITLE_OFF = explicitly off, otherwise the picked stream index.
    var selectedSub by remember { mutableStateOf<Int?>(null) }
    val playFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(40L)
            runCatching { playFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(520.dp)
                .heightIn(max = 540.dp)
                .clip(shape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.badgeBorder, shape)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(strings.trackPickerTitle, color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(title, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            if (audioTracks.isNotEmpty()) {
                Text(strings.playerAudioTracks, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                TrackPickerOptionRow(
                    label = strings.commonDefault,
                    selected = selectedAudio == null,
                    onClick = { selectedAudio = null }
                )
                audioTracks.forEach { track ->
                    TrackPickerOptionRow(
                        label = listOfNotNull(
                            track.language?.uppercase(),
                            track.label.takeIf { it.isNotBlank() },
                            track.codec?.uppercase()
                        ).distinct().joinToString(" • "),
                        selected = selectedAudio == track.index,
                        onClick = { selectedAudio = track.index }
                    )
                }
            }

            if (subtitleTracks.isNotEmpty()) {
                Text(strings.settingsSubtitlesSection, color = colors.accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                TrackPickerOptionRow(
                    label = strings.commonDefault,
                    selected = selectedSub == null,
                    onClick = { selectedSub = null }
                )
                TrackPickerOptionRow(
                    label = strings.optionOff,
                    selected = selectedSub == PlayerTrackSelection.SUBTITLE_OFF,
                    onClick = { selectedSub = PlayerTrackSelection.SUBTITLE_OFF }
                )
                subtitleTracks.forEach { track ->
                    TrackPickerOptionRow(
                        label = listOfNotNull(
                            track.language?.uppercase(),
                            track.label.takeIf { it.isNotBlank() },
                            track.codec?.uppercase(),
                            if (track.isForced) strings.playerForcedBadge else null
                        ).distinct().joinToString(" • "),
                        selected = selectedSub == track.index,
                        onClick = { selectedSub = track.index }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPillButton(
                    text = strings.trackPickerPlayWithSelection,
                    icon = Icons.Filled.PlayArrow,
                    isPrimary = true,
                    onClick = { onPlayWithSelection(selectedAudio, selectedSub) },
                    modifier = Modifier.focusRequester(playFocus)
                )
                HeroPillButton(
                    text = strings.actionCancel,
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun TrackPickerOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) colors.white else if (selected) colors.surfaceElevated else colors.surface)
            .border(1.dp, if (selected) colors.accent else Color.Transparent, RoundedCornerShape(6.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = label,
            color = if (focused) colors.deepBlack else colors.white,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = if (focused) colors.deepBlack else colors.accent, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    title: String,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val shape = RoundedCornerShape(spec.dialogCornerRadiusDp.dp)
    var stepTwo by remember { mutableStateOf(false) }
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(Unit) {
            delay(40L)
            runCatching { cancelFocus.requestFocus() }
        }
        Column(
            modifier = Modifier
                .width(480.dp)
                .clip(shape)
                .background(colors.dialogSurface)
                .border(1.dp, colors.accent, shape)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(strings.deleteConfirmTitle.format(title), color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(strings.deleteConfirmBody, color = colors.textSecondary, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroPillButton(
                    text = strings.actionCancel,
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(cancelFocus)
                )
                HeroPillButton(
                    text = if (stepTwo) strings.deleteConfirmStep2 else strings.deleteConfirmStep1,
                    icon = Icons.Filled.DeleteOutline,
                    isPrimary = true,
                    onClick = {
                        if (!stepTwo) stepTwo = true else onConfirmDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun EpisodeActionDialog(
    episode: EpisodeSummary,
    onPlay: () -> Unit,
    onPlayFromBeginning: () -> Unit,
    onToggleWatched: () -> Unit,
    onDismiss: () -> Unit
) {
    val strings = AppStrings.current
    DetailsMoreMenu(
        title = strings.episodeCodeTitle.format(episode.seasonNumber, episode.episodeNumber, episode.name),
        actions = buildList {
            add(MoreAction(Icons.Filled.PlayArrow, if (episode.resumePositionTicks > 0L) strings.actionResume else strings.actionPlay, onPlay))
            if (episode.resumePositionTicks > 0L) {
                add(MoreAction(Icons.Filled.Replay, strings.actionPlayFromBeginning, onPlayFromBeginning))
            }
            add(
                MoreAction(
                    if (episode.isWatched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    if (episode.isWatched) strings.actionMarkUnwatched else strings.actionMarkWatched,
                    onToggleWatched
                )
            )
        },
        onDismiss = onDismiss
    )
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun ThemeSongPlayerEffect(
    themeSongUrl: String?,
    mediaHttpClient: okhttp3.OkHttpClient,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current
    val screensaverVisible = com.seerflix.tv.ui.components.LocalScreensaverVisible.current
    val playerHolder = remember { arrayOfNulls<androidx.media3.exoplayer.ExoPlayer>(1) }
    androidx.compose.runtime.DisposableEffect(themeSongUrl, lifecycleOwner) {
        if (themeSongUrl.isNullOrBlank()) {
            onDispose { }
        } else {
            val dataSourceFactory = androidx.media3.datasource.okhttp.OkHttpDataSource.Factory(mediaHttpClient)
            val mediaSourceFactory = androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
            val player = androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setMediaSourceFactory(mediaSourceFactory)
                .build()
                .apply {
                    volume = 0.25f
                    // Played once per visit, like a title card: a looping song on a TV left on a
                    // Details page (or under the screensaver) gets old fast.
                    repeatMode = androidx.media3.common.Player.REPEAT_MODE_OFF
                    setMediaItem(androidx.media3.common.MediaItem.fromUri(themeSongUrl))
                    playWhenReady = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                    prepare()
                }
            playerHolder[0] = player
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> runCatching {
                        if (player.playbackState != androidx.media3.common.Player.STATE_ENDED) player.playWhenReady = true
                    }
                    Lifecycle.Event.ON_PAUSE -> runCatching { player.playWhenReady = false }
                    // Covered by the player or another screen: free the decoder and audio track.
                    Lifecycle.Event.ON_STOP -> runCatching { player.stop() }
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                playerHolder[0] = null
                runCatching {
                    player.stop()
                    player.release()
                }
            }
        }
    }
    LaunchedEffect(screensaverVisible) {
        val player = playerHolder[0] ?: return@LaunchedEffect
        if (screensaverVisible) {
            runCatching { player.playWhenReady = false }
        } else if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            player.playbackState == androidx.media3.common.Player.STATE_READY
        ) {
            runCatching { player.playWhenReady = true }
        }
    }
}

