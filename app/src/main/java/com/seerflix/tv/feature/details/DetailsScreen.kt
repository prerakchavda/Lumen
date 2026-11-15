package com.seerflix.tv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.CastMember
import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.ExtrasItem
import com.seerflix.tv.core.model.MediaSourceOption
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.AvailabilityBadge
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlayItem: (String, String?) -> Unit,
    onPlayTrailer: (title: String, youtubeKey: String?, localTrailerId: String?) -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onSearchActor: (name: String, personId: Int?) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
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
            onPlayTrailer(cur?.title ?: "Trailer", key, cur?.localTrailerItemId)
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
        onRequestAllMissingInCollection = { viewModel.requestAllMissingInCollection() }
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
    onRetry: () -> Unit = {}
) {
    val colors = SeerflixTokens.colors
    // Lives outside the LazyColumn so scrolling the header away and back (or the seed -> full data
    // swap) never re-runs the initial focus request and yanks focus back to Play.
    val focusMemory = remember { DetailsFocusMemory() }
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
                    Text(state.errorMessage ?: "Title not found", color = colors.accent, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    // A transient network error used to dead-end here with only "Go Back".
                    // (Parental-control blocks get only "Go Back"; retrying can't help.)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (!state.isBlockedByParentalControls) {
                            HeroPillButton(
                                text = "Retry",
                                icon = Icons.Filled.Refresh,
                                isPrimary = true,
                                onClick = onRetry,
                                modifier = Modifier.focusRequester(errorBackFocusRequester)
                            )
                        }
                        HeroPillButton(
                            text = "Go Back",
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 56.dp)
                ) {
                    // Header Backdrop + Metadata + Actions + Live Download Progress
                    item(key = "details_header") {
                        DetailsHeaderSection(
                            item = item,
                            hasPlayableEpisode = hasPlayableEpisode,
                            isRefining = state.isRefining,
                            focusMemory = focusMemory,
                            isSeerrConfigured = state.isSeerrConfigured,
                            canSubmitRequests = state.canSubmitRequests,
                            feedbackBanner = state.feedbackBanner,
                            onBack = onBack,
                            onPlay = {
                                // Series: pass the series id so the playback manager resumes the
                                // in-progress / next-up episode. The old code played the first
                                // episode of whichever season tab happened to be selected (S1E1 by
                                // default), ignoring where the viewer actually left off.
                                val playableId = if (item.mediaType == MediaType.SERIES) {
                                    item.jellyfinId ?: episodes.firstOrNull { it.isAvailableInJellyfin }?.id
                                } else {
                                    item.jellyfinId
                                }
                                val sourceId = if (item.mediaType == MediaType.SERIES) null else selectedSourceId
                                playableId?.let { id -> onPlayItem(id, sourceId) }
                            },
                            onRequestClick = onOpenRequestModal,
                            onWatchTrailer = {
                                onPlayTrailer("${item.title} • Trailer", item.youtubeTrailerKey, item.localTrailerItemId)
                            },
                            onToggleMyList = onToggleMyList,
                            onToggleReminder = onToggleReminder,
                            onToggleWatched = onToggleWatched,
                            onOpenExternalPlayer = onOpenExternalPlayer,
                            onOpenReportIssue = onOpenReportIssue,
                            onRemoveFromContinueWatching = onRemoveFromContinueWatching,
                            onCancelRequest = onCancelRequest
                        )
                    }

                    // Seed only: seasons / cast / versions / recommendations aren't known yet.
                    if (state.isRefining) {
                        item(key = "refining_skeleton") {
                            DetailsSectionsSkeleton(isSeries = item.mediaType == MediaType.SERIES)
                        }
                    }

                    // Multi-Version / Edition Picker
                    if (item.mediaVersions.size > 1) {
                        item(key = "media_versions") {
                            MediaVersionsSection(
                                versions = item.mediaVersions,
                                selectedId = selectedSourceId ?: item.mediaVersions.firstOrNull()?.id,
                                onSelectMediaSource = onSelectMediaSource
                            )
                        }
                    }

                    // TV Show Seasons & Episodes
                    if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                        item(key = "seasons_bar") {
                            Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
                                Text(
                                    text = "Seasons & Episodes",
                                    color = colors.white,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(12.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                            onPlayItem(ep.id, null)
                                        } else {
                                            onOpenRequestModal()
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Extras & Special Features (Jellyfin SpecialFeatures)
                    if (item.extras.isNotEmpty()) {
                        item(key = "extras_and_specials") {
                            ExtrasSection(
                                extras = item.extras,
                                fallbackBackdropUrl = item.backdropUrl ?: item.posterUrl,
                                onPlayItem = onPlayItem
                            )
                        }
                    }

                    // Trailers & More
                    if (item.relatedVideos.isNotEmpty()) {
                        item(key = "trailers_and_more") {
                            RelatedVideosSection(
                                videos = item.relatedVideos,
                                fallbackBackdropUrl = item.backdropUrl ?: item.posterUrl,
                                onPlayTrailer = onPlayTrailer
                            )
                        }
                    }

                    // Cast Row (clickable for Actor Filmography search)
                    if (item.cast.isNotEmpty()) {
                        item(key = "cast_section") {
                            CastSection(cast = item.cast, onSearchActor = onSearchActor)
                        }
                    }

                    // Collection / Saga Completion Row
                    state.collectionSaga?.takeIf { it.items.size > 1 }?.let { saga ->
                        item(key = "collection_saga") {
                            val missingCount = remember(saga.items) {
                                saga.items.count { !it.isPlayable && it.canRequestHd }
                            }
                            val sagaFirstFocusRequester = remember { FocusRequester() }
                            Column(modifier = Modifier.padding(top = 28.dp)) {
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
                                            text = "Part of ${saga.name}",
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
                                                text = saga.completionLabel,
                                                color = colors.matchGreen,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }

                                    if (missingCount > 0 && state.isSeerrConfigured && state.canSubmitRequests) {
                                        HeroPillButton(
                                            text = "Request Missing ($missingCount)",
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

                    // More Like This
                    if (state.recommendations.isNotEmpty()) {
                        item(key = "more_like_this") {
                            Column(modifier = Modifier.padding(top = 28.dp)) {
                                Text(
                                    text = "More Like This",
                                    color = colors.white,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 48.dp),
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
        Text(
            text = AppStrings.current.labelVersions,
            color = colors.white,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
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
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = AppStrings.current.labelExtras,
            color = colors.white,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)
        )
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
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = "Trailers & More",
            color = colors.white,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)
        )
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
    onSearchActor: (name: String, personId: Int?) -> Unit
) {
    val colors = SeerflixTokens.colors
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(
            text = "Cast",
            color = colors.white,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)
        )
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
                        .clickable { onSearchActor(member.name, member.personId) }
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
                contentDescription = "Play Trailer",
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
                contentDescription = "Play Extra",
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
            Text("${mins}m", color = colors.textMuted, fontSize = 11.sp)
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
    onPlay: () -> Unit,
    onRequestClick: () -> Unit,
    onWatchTrailer: () -> Unit,
    onToggleMyList: () -> Unit,
    onToggleReminder: () -> Unit,
    onToggleWatched: () -> Unit,
    onOpenExternalPlayer: () -> Unit,
    onOpenReportIssue: () -> Unit,
    onRemoveFromContinueWatching: () -> Unit,
    onCancelRequest: (Int) -> Unit
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
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = colors.white, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Back", color = colors.white, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
                AvailabilityBadge(item = item)
                if (isRefining) {
                    // The card's per-tier status may be stale/incomplete; wait for the real one.
                    SkeletonBlock(96.dp, 20.dp, corner = 4.dp)
                    SkeletonBlock(96.dp, 20.dp, corner = 4.dp)
                } else {
                    TierPill("HD", item.hdStatus)
                    TierPill("4K", item.uhdStatus)
                }
                item.mediaBadges.take(4).forEach { badge ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(colors.surface.copy(alpha = 0.78f))
                            .border(1.dp, colors.badgeBorder, RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(text = badge, color = colors.white, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item.matchPercent?.let {
                    Text("$it% Match", color = colors.matchGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                item.releaseYear?.let {
                    Text(it, color = colors.textSecondary, fontSize = 14.sp)
                }
                item.maturityRating?.let {
                    Box(
                        modifier = Modifier
                            .border(1.dp, colors.textMuted, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(it, color = colors.textSecondary, fontSize = 11.sp)
                    }
                }
                item.formattedRuntime?.let {
                    Text(it, color = colors.textSecondary, fontSize = 14.sp)
                }
                if (item.genres.isNotEmpty()) {
                    Text(item.genres.take(3).joinToString(" • "), color = colors.textSecondary, fontSize = 13.sp)
                }
            }

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
                    if (runCatching { primaryActionFocusRequester.requestFocus() }.isSuccess) {
                        focusMemory.initialFocusDone = true
                        return@LaunchedEffect
                    }
                }
            }
            val visibleActionKeys = buildSet {
                if (canPlay) { add("act_play"); add("act_ext_player") }
                if (showReqButton) add("act_req")
                if (showMyList) add("act_mylist")
                if (showWatched) add("act_watched")
                if (showRemind) add("act_remind")
                if (showTrailer) add("act_trailer")
                if (showIssue) add("act_issue")
                if (showRmProgress) add("act_rm_progress")
                if (showCancelReq) add("act_cancel_req")
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

            // A scrollable Row instead of a LazyRow: with ~10 buttons on a 960dp-wide TV screen the
            // LazyRow disposed the off-screen Play button, so focus requests on it silently failed.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
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
                    HeroPillButton(
                        text = strings.actionExternalPlayer,
                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                        isPrimary = false,
                        onClick = onOpenExternalPlayer,
                        modifier = Modifier.trackActionFocus("act_ext_player")
                    )
                }

                if (showReqButton) {
                    val reqModifier = (if (!canPlay) Modifier.focusRequester(primaryActionFocusRequester) else Modifier)
                        .trackActionFocus("act_req")
                    if (canSubmitRequests) {
                        HeroPillButton(
                            text = if (canPlay) "Request 4K / More" else "Request HD / 4K",
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
                        text = if (item.isInMyList) "In My List" else "My List",
                        icon = if (item.isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                        isPrimary = false,
                        onClick = onToggleMyList,
                        modifier = (if (!canPlay && !showReqButton) Modifier.focusRequester(primaryActionFocusRequester) else Modifier)
                            .trackActionFocus("act_mylist")
                    )
                }

                if (isRefining) {
                    // Request / My List / Watched / ... take these slots once availability is known.
                    SkeletonPill(if (canPlay) 120.dp else 170.dp)
                    SkeletonPill(120.dp)
                    SkeletonPill(150.dp)
                }

                if (showWatched) {
                    HeroPillButton(
                        text = if (item.isWatched) strings.actionMarkUnwatched else strings.actionMarkWatched,
                        icon = if (item.isWatched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        isPrimary = false,
                        onClick = onToggleWatched,
                        modifier = Modifier.trackActionFocus("act_watched")
                    )
                }

                if (showRemind) {
                    HeroPillButton(
                        text = if (item.isReminderSet) "Reminder Set" else "Remind Me",
                        icon = if (item.isReminderSet) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                        isPrimary = false,
                        onClick = onToggleReminder,
                        modifier = Modifier.trackActionFocus("act_remind")
                    )
                }

                if (showTrailer) {
                    HeroPillButton(
                        text = strings.actionTrailer,
                        icon = Icons.Filled.Theaters,
                        isPrimary = false,
                        onClick = onWatchTrailer,
                        modifier = Modifier.trackActionFocus("act_trailer")
                    )
                }

                if (showIssue) {
                    HeroPillButton(
                        text = strings.actionReportIssue,
                        icon = Icons.Filled.Flag,
                        isPrimary = false,
                        onClick = onOpenReportIssue,
                        modifier = Modifier.trackActionFocus("act_issue")
                    )
                }

                if (showRmProgress) {
                    HeroPillButton(
                        text = "Remove Progress",
                        icon = Icons.Filled.DeleteOutline,
                        isPrimary = false,
                        onClick = onRemoveFromContinueWatching,
                        modifier = Modifier.trackActionFocus("act_rm_progress")
                    )
                }

                if (showCancelReq && pendingReqId != null) {
                    HeroPillButton(
                        text = "Cancel Request",
                        icon = Icons.Filled.Close,
                        isPrimary = false,
                        onClick = { onCancelRequest(pendingReqId) },
                        modifier = Modifier.trackActionFocus("act_cancel_req")
                    )
                }
            }
        }
    }
}

@Composable
private fun TierPill(tierName: String, status: AvailabilityTier) {
    val colors = SeerflixTokens.colors
    val (label, color) = when (status) {
        AvailabilityTier.AVAILABLE -> "$tierName Available" to colors.matchGreen
        AvailabilityTier.PARTIALLY_AVAILABLE -> "$tierName Partial" to colors.pendingAmber
        AvailabilityTier.PROCESSING -> "$tierName Downloading" to colors.downloadingBlue
        AvailabilityTier.PENDING -> "$tierName Requested" to colors.pendingAmber
        else -> "$tierName Not in Library" to colors.textMuted
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

@Composable
private fun EpisodeRowCard(
    episode: EpisodeSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
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
            .clickable(onClick = onClick)
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
                    contentDescription = "Play",
                    tint = colors.white,
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(17.dp))
                        .background(colors.deepBlack.copy(alpha = 0.65f))
                        .padding(4.dp)
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
                        text = "${episode.runtimeMinutes}m",
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
                        Text("Not on Server", color = colors.pendingAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
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
        mutableStateOf(selectedHdServer?.profiles?.firstOrNull()?.id)
    }
    var selectedHdRoot by remember(selectedHdServer) {
        mutableStateOf(selectedHdServer?.rootFolders?.firstOrNull())
    }

    var selectedUhdServer by remember(uhdServers) {
        mutableStateOf(uhdServers.firstOrNull { it.isDefault } ?: uhdServers.firstOrNull())
    }
    var selectedUhdProfileId by remember(selectedUhdServer) {
        mutableStateOf(selectedUhdServer?.profiles?.firstOrNull()?.id)
    }
    var selectedUhdRoot by remember(selectedUhdServer) {
        mutableStateOf(selectedUhdServer?.rootFolders?.firstOrNull())
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
                text = "Request \"${item.title}\"",
                color = colors.white,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Choose quality tier, Radarr/Sonarr server, profile & root folder:",
                color = colors.textSecondary,
                fontSize = 13.sp
            )

            // HD Option
            QualityToggleRow(
                title = "HD (1080p)",
                subtitle = when {
                    !isSeries && item.hasHd -> "Already in library"
                    !hdRequestable -> "Already requested"
                    else -> selectedHdServer?.name ?: "Standard Radarr / Sonarr profile"
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
                    title = "4K Ultra HD (2160p)",
                    subtitle = when {
                        !isSeries && item.has4k -> "Already in library"
                        !uhdRequestable -> "Already requested"
                        else -> selectedUhdServer?.name ?: "4K Radarr / Sonarr profile"
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
                Text("Loading Radarr / Sonarr profiles...", color = colors.textMuted, fontSize = 12.sp)
            }

            if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                Text(
                    text = "Select Seasons (${selectedSeasons.size} selected):",
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
                    text = if (isSubmitting) "Sending..." else "Send Request",
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (servers.size > 1) {
            Text("Server", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
            Text("Quality Profile", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
            Text("Root Folder", color = colors.textMuted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
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
            Text("Report Issue", color = colors.white, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(title, color = colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SeerrIssueType.entries, key = { it.name }) { type ->
                    SeasonTabChip(
                        label = type.label,
                        isSelected = type == selectedType,
                        isAvailable = false,
                        onClick = { selectedType = type }
                    )
                }
            }

            OutlinedTextField(
                value = message,
                onValueChange = { message = it },
                label = { Text("Describe the playback or subtitle issue (optional)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.white,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.badgeBorder
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                HeroPillButton(
                    text = if (isSubmitting) "Reporting..." else "Submit Issue",
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

/** Holds which header action button currently has focus; deliberately not snapshot state. */
private class ActionFocusTracker {
    var focusedKey: String? = null
}

/** Whether the screen has already placed its initial focus; deliberately not snapshot state. */
private class DetailsFocusMemory {
    var initialFocusDone: Boolean = false
}
