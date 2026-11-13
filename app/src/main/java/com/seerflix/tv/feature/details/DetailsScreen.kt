package com.seerflix.tv.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.ArrServerOption
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.ExtrasItem
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.SeerrIssueType
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.AvailabilityBadge
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.theme.SeerflixTokens

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

    DetailsContent(
        state = state,
        onBack = onBack,
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
            onPlayTrailer(state.item?.title ?: "Trailer", key, state.item?.localTrailerItemId)
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
    onRequestAllMissingInCollection: () -> Unit = {}
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            state.errorMessage != null || state.item == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage ?: "Title not found", color = colors.accent, fontSize = 16.sp)
                    Spacer(Modifier.height(16.dp))
                    HeroPillButton("Go Back", Icons.AutoMirrored.Filled.ArrowBack, isPrimary = true, onClick = onBack)
                }
            }
            else -> {
                val item = state.item!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 56.dp)
                ) {
                    // Header Backdrop + Metadata + Actions + Live Download Progress
                    item(key = "details_header") {
                        DetailsHeaderSection(
                            item = item,
                            episodes = state.episodes,
                            isSeerrConfigured = state.isSeerrConfigured,
                            canSubmitRequests = state.canSubmitRequests,
                            feedbackBanner = state.feedbackBanner,
                            onBack = onBack,
                            onPlay = {
                                val playableId = if (item.mediaType == MediaType.SERIES) {
                                    state.episodes.firstOrNull { it.isAvailableInJellyfin }?.id ?: item.jellyfinId
                                } else {
                                    item.jellyfinId
                                }
                                playableId?.let { id -> onPlayItem(id, state.selectedMediaSourceId) }
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

                    // Multi-Version / Edition Picker
                    if (item.mediaVersions.size > 1) {
                        item(key = "media_versions") {
                            Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)) {
                                Text(
                                    text = strings.labelVersions,
                                    color = colors.white,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(item.mediaVersions, key = { it.id }) { src ->
                                        SeasonTabChip(
                                            label = src.displayLabel,
                                            isSelected = src.id == (state.selectedMediaSourceId ?: item.mediaVersions.firstOrNull()?.id),
                                            isAvailable = true,
                                            onClick = { onSelectMediaSource(src.id) }
                                        )
                                    }
                                }
                            }
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
                                    items(item.seasons, key = { it.seasonNumber }) { season ->
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
                            item(key = "eps_loading") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.size(28.dp))
                                }
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
                            Column(modifier = Modifier.padding(top = 24.dp)) {
                                Text(
                                    text = strings.labelExtras,
                                    color = colors.white,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)
                                )
                                LazyRow(
                                    contentPadding = PaddingValues(horizontal = 48.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    items(item.extras, key = { it.id }) { extra ->
                                        ExtrasFeatureCard(
                                            extra = extra,
                                            fallbackBackdropUrl = item.backdropUrl ?: item.posterUrl,
                                            onClick = { onPlayItem(extra.id, null) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Trailers & More
                    if (item.relatedVideos.isNotEmpty()) {
                        item(key = "trailers_and_more") {
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
                                    items(item.relatedVideos, key = { "${it.title}_${it.youtubeKey}_${it.localTrailerItemId}" }) { video ->
                                        RelatedVideoCard(
                                            video = video,
                                            fallbackBackdropUrl = item.backdropUrl ?: item.posterUrl,
                                            onClick = {
                                                onPlayTrailer(video.title, video.youtubeKey, video.localTrailerItemId)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Cast Row (clickable for Actor Filmography search)
                    if (item.cast.isNotEmpty()) {
                        item(key = "cast_section") {
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
                                    items(item.cast, key = { "${it.name}_${it.role}" }) { member ->
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
                                                .focusable()
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
                    }

                    // Collection / Saga Completion Row
                    state.collectionSaga?.takeIf { it.items.size > 1 }?.let { saga ->
                        item(key = "collection_saga") {
                            val missingCount = remember(saga.items) {
                                saga.items.count { !it.isPlayable && it.canRequestHd }
                            }
                            Column(modifier = Modifier.padding(top = 28.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 48.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(
                                            text = "Part of ${saga.name}",
                                            color = colors.white,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
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
                                            onClick = onRequestAllMissingInCollection
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
                                            } else null
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
            .focusable()
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
            .focusable()
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
    episodes: List<EpisodeSummary>,
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
                .focusable()
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
                TierPill("HD", item.hdStatus)
                TierPill("4K", item.uhdStatus)
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

            LazyRow(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val canPlay = item.isPlayable || episodes.any { it.isAvailableInJellyfin }
                if (canPlay) {
                    item(key = "act_play") {
                        HeroPillButton(
                            text = if (item.resumePositionTicks > 0L) strings.actionResume else strings.actionPlay,
                            icon = Icons.Filled.PlayArrow,
                            isPrimary = true,
                            onClick = onPlay
                        )
                    }
                    item(key = "act_ext_player") {
                        HeroPillButton(
                            text = strings.actionExternalPlayer,
                            icon = Icons.AutoMirrored.Filled.OpenInNew,
                            isPrimary = false,
                            onClick = onOpenExternalPlayer
                        )
                    }
                }

                if (isSeerrConfigured && item.tmdbId != null && (item.canRequestHd || item.canRequest4k || item.mediaType == MediaType.SERIES)) {
                    item(key = "act_req") {
                        if (canSubmitRequests) {
                            HeroPillButton(
                                text = if (canPlay) "Request 4K / More" else "Request HD / 4K",
                                icon = Icons.Filled.Add,
                                isPrimary = !canPlay,
                                onClick = onRequestClick
                            )
                        } else {
                            HeroPillButton(
                                text = strings.labelKidsLocked,
                                icon = Icons.Filled.Lock,
                                isPrimary = false,
                                onClick = onRequestClick
                            )
                        }
                    }
                }

                item(key = "act_mylist") {
                    HeroPillButton(
                        text = if (item.isInMyList) "In My List" else "My List",
                        icon = if (item.isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                        isPrimary = false,
                        onClick = onToggleMyList
                    )
                }

                if (item.jellyfinId != null) {
                    item(key = "act_watched") {
                        HeroPillButton(
                            text = if (item.isWatched) strings.actionMarkUnwatched else strings.actionMarkWatched,
                            icon = if (item.isWatched) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            isPrimary = false,
                            onClick = onToggleWatched
                        )
                    }
                }

                if (!canPlay && item.overallAvailability == AvailabilityTier.UNKNOWN && item.tmdbId != null) {
                    item(key = "act_remind") {
                        HeroPillButton(
                            text = if (item.isReminderSet) "Reminder Set" else "Remind Me",
                            icon = if (item.isReminderSet) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                            isPrimary = false,
                            onClick = onToggleReminder
                        )
                    }
                }

                if (!item.youtubeTrailerKey.isNullOrBlank() || !item.localTrailerItemId.isNullOrBlank()) {
                    item(key = "act_trailer") {
                        HeroPillButton(
                            text = strings.actionTrailer,
                            icon = Icons.Filled.Theaters,
                            isPrimary = false,
                            onClick = onWatchTrailer
                        )
                    }
                }

                if (isSeerrConfigured && canPlay && item.tmdbId != null) {
                    item(key = "act_issue") {
                        HeroPillButton(
                            text = strings.actionReportIssue,
                            icon = Icons.Filled.Flag,
                            isPrimary = false,
                            onClick = onOpenReportIssue
                        )
                    }
                }

                if (item.resumePositionTicks > 0L && item.jellyfinId != null) {
                    item(key = "act_rm_progress") {
                        HeroPillButton(
                            text = "Remove Progress",
                            icon = Icons.Filled.DeleteOutline,
                            isPrimary = false,
                            onClick = onRemoveFromContinueWatching
                        )
                    }
                }

                val pendingReqId = item.uhdRequestId ?: item.hdRequestId
                if (pendingReqId != null && !canPlay && item.overallAvailability.isRequestedOrDownloading) {
                    item(key = "act_cancel_req") {
                        HeroPillButton(
                            text = "Cancel Request",
                            icon = Icons.Filled.Close,
                            isPrimary = false,
                            onClick = { onCancelRequest(pendingReqId) }
                        )
                    }
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
            .focusable()
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
            .focusable()
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

    var requestHd by remember { mutableStateOf(!item.hasHd) }
    var request4k by remember { mutableStateOf(item.hasHd && !item.has4k && has4kServer) }

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
                subtitle = if (item.hasHd && item.mediaType == MediaType.MOVIE) "Already in library" else (selectedHdServer?.name ?: "Standard Radarr / Sonarr profile"),
                checked = requestHd,
                enabled = item.mediaType == MediaType.SERIES || !item.hasHd,
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
                    subtitle = if (item.has4k && item.mediaType == MediaType.MOVIE) "Already in library" else (selectedUhdServer?.name ?: "4K Radarr / Sonarr profile"),
                    checked = request4k,
                    enabled = item.mediaType == MediaType.SERIES || !item.has4k,
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
                    items(item.seasons, key = { it.seasonNumber }) { s ->
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HeroPillButton(
                    text = if (isSubmitting) "Sending..." else "Send Request",
                    icon = Icons.Filled.Check,
                    isPrimary = true,
                    onClick = {
                        if (!isSubmitting && (requestHd || request4k)) {
                            val seasonsList = if (item.mediaType == MediaType.SERIES) selectedSeasons.toList() else null
                            onSubmit(
                                requestHd,
                                request4k,
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
                    modifier = Modifier.weight(1f)
                )
                HeroPillButton(
                    text = "Cancel",
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
                    modifier = Modifier.weight(1f)
                )
                HeroPillButton(
                    text = "Cancel",
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
            .focusable(enabled = enabled)
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = if (enabled) { { onToggle() } } else null,
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
