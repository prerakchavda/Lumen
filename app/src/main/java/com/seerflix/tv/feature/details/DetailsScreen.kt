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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Theaters
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.DownloadProgressInfo
import com.seerflix.tv.core.model.EpisodeSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.RelatedVideoItem
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.AvailabilityBadge
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.theme.NetflixColors

@Composable
fun DetailsScreen(
    onBack: () -> Unit,
    onPlayItem: (String) -> Unit,
    onPlayTrailer: (title: String, youtubeKey: String?, localTrailerId: String?) -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onSearchActor: (name: String, personId: Int?) -> Unit,
    viewModel: DetailsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()

    DetailsContent(
        state = state,
        onBack = onBack,
        onPlayItem = onPlayItem,
        onOpenDetails = onOpenDetails,
        onOpenRequestModal = viewModel::openRequestModal,
        onCloseRequestModal = viewModel::closeRequestModal,
        onCancelRequest = viewModel::cancelRequest,
        onSelectSeason = viewModel::selectSeason,
        onSubmitRequest = viewModel::submitRequest,
        onWatchTrailer = { key ->
            onPlayTrailer(state.item?.title ?: "Trailer", key, state.item?.localTrailerItemId)
        },
        onPlayTrailer = onPlayTrailer,
        onToggleMyList = viewModel::toggleMyList,
        onToggleReminder = viewModel::toggleReminder,
        onRemoveFromContinueWatching = viewModel::removeFromContinueWatching,
        onSearchActor = onSearchActor
    )
}

@Composable
fun DetailsContent(
    state: DetailsUiState,
    onBack: () -> Unit,
    onPlayItem: (String) -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onOpenRequestModal: () -> Unit,
    onCloseRequestModal: () -> Unit,
    onCancelRequest: (Int) -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSubmitRequest: (requestHd: Boolean, request4k: Boolean, seasons: List<Int>?) -> Unit,
    onWatchTrailer: (String) -> Unit,
    onPlayTrailer: (title: String, youtubeKey: String?, localTrailerId: String?) -> Unit = { _, k, _ -> k?.let(onWatchTrailer) },
    onToggleMyList: () -> Unit = {},
    onToggleReminder: () -> Unit = {},
    onRemoveFromContinueWatching: () -> Unit = {},
    onSearchActor: (name: String, personId: Int?) -> Unit = { _, _ -> }
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixColors.Background)
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    color = NetflixColors.NetflixRed,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            state.errorMessage != null || state.item == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(state.errorMessage ?: "Title not found", color = NetflixColors.NetflixRed, fontSize = 16.sp)
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
                            feedbackBanner = state.feedbackBanner,
                            onBack = onBack,
                            onPlay = {
                                val playableId = if (item.mediaType == MediaType.SERIES) {
                                    state.episodes.firstOrNull { it.isAvailableInJellyfin }?.id ?: item.jellyfinId
                                } else {
                                    item.jellyfinId
                                }
                                playableId?.let(onPlayItem)
                            },
                            onRequestClick = onOpenRequestModal,
                            onWatchTrailer = {
                                onPlayTrailer("${item.title} • Trailer", item.youtubeTrailerKey, item.localTrailerItemId)
                            },
                            onToggleMyList = onToggleMyList,
                            onToggleReminder = onToggleReminder,
                            onRemoveFromContinueWatching = onRemoveFromContinueWatching,
                            onCancelRequest = onCancelRequest
                        )
                    }

                    // TV Show Seasons & Episodes
                    if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                        item(key = "seasons_bar") {
                            Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 12.dp)) {
                                Text(
                                    text = "Seasons & Episodes",
                                    color = NetflixColors.White,
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
                                    CircularProgressIndicator(color = NetflixColors.NetflixRed, modifier = Modifier.size(28.dp))
                                }
                            }
                        } else {
                            items(state.episodes, key = { it.id }) { ep ->
                                EpisodeRowCard(
                                    episode = ep,
                                    onClick = {
                                        if (ep.isAvailableInJellyfin) {
                                            onPlayItem(ep.id)
                                        } else {
                                            onOpenRequestModal()
                                        }
                                    },
                                    modifier = Modifier.padding(horizontal = 48.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Trailers & More
                    if (item.relatedVideos.isNotEmpty()) {
                        item(key = "trailers_and_more") {
                            Column(modifier = Modifier.padding(top = 24.dp)) {
                                Text(
                                    text = "Trailers & More",
                                    color = NetflixColors.White,
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
                                    color = NetflixColors.White,
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
                                                .background(if (castFocused) Color(0xFF242424) else Color.Transparent)
                                                .then(
                                                    if (castFocused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(8.dp))
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
                                                    .background(NetflixColors.SurfaceElevated)
                                            )
                                            Spacer(Modifier.height(6.dp))
                                            Text(
                                                text = member.name,
                                                color = if (castFocused) NetflixColors.White else NetflixColors.TextPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (member.role.isNotBlank()) {
                                                Text(
                                                    text = member.role,
                                                    color = NetflixColors.TextMuted,
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

                    // More Like This
                    if (state.recommendations.isNotEmpty()) {
                        item(key = "more_like_this") {
                            Column(modifier = Modifier.padding(top = 28.dp)) {
                                Text(
                                    text = "More Like This",
                                    color = NetflixColors.White,
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
            isSubmitting = state.isSubmittingRequest,
            onDismiss = onCloseRequestModal,
            onSubmit = onSubmitRequest
        )
    }
}

@Composable
private fun RelatedVideoCard(
    video: RelatedVideoItem,
    fallbackBackdropUrl: String?,
    onClick: () -> Unit
) {
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
                .clip(RoundedCornerShape(6.dp))
                .background(NetflixColors.SurfaceElevated)
                .then(
                    if (focused) Modifier.border(2.5.dp, NetflixColors.White, RoundedCornerShape(6.dp))
                    else Modifier.border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(6.dp))
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
                tint = NetflixColors.White,
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xAA000000))
                    .padding(6.dp)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xBB0A0A0A))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(video.type, color = NetflixColors.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = video.title,
            color = if (focused) NetflixColors.White else NetflixColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailsHeaderSection(
    item: UnifiedMediaItem,
    episodes: List<EpisodeSummary>,
    feedbackBanner: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onRequestClick: () -> Unit,
    onWatchTrailer: () -> Unit,
    onToggleMyList: () -> Unit,
    onToggleReminder: () -> Unit,
    onRemoveFromContinueWatching: () -> Unit,
    onCancelRequest: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(385.dp)
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
                            0.0f to Color(0xFA141414),
                            0.45f to Color(0xD8141414),
                            1.0f to Color(0x33141414)
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
                            0.0f to Color(0x660A0A0A),
                            0.60f to Color.Transparent,
                            1.0f to NetflixColors.Background
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
                .clip(RoundedCornerShape(6.dp))
                .background(if (backFocused) Color(0xCC333333) else Color(0x88141414))
                .then(if (backFocused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(6.dp)) else Modifier)
                .onFocusChanged { backFocused = it.isFocused }
                .focusable()
                .clickable(onClick = onBack)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NetflixColors.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Back", color = NetflixColors.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 220.dp, bottom = 20.dp)
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
                            .background(Color(0xAA1F1F1F))
                            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(4.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Text(text = badge, color = NetflixColors.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
                    color = NetflixColors.White,
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
                    Text("$it% Match", color = NetflixColors.MatchGreen, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                item.releaseYear?.let {
                    Text(it, color = NetflixColors.TextSecondary, fontSize = 14.sp)
                }
                item.maturityRating?.let {
                    Box(
                        modifier = Modifier
                            .border(1.dp, NetflixColors.TextMuted, RoundedCornerShape(2.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(it, color = NetflixColors.TextSecondary, fontSize = 11.sp)
                    }
                }
                item.formattedRuntime?.let {
                    Text(it, color = NetflixColors.TextSecondary, fontSize = 14.sp)
                }
                if (item.genres.isNotEmpty()) {
                    Text(item.genres.take(3).joinToString(" • "), color = NetflixColors.TextSecondary, fontSize = 13.sp)
                }
            }

            if (item.overview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = item.overview,
                    color = NetflixColors.TextPrimary,
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
                Text(msg, color = NetflixColors.MatchGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val canPlay = item.isPlayable || episodes.any { it.isAvailableInJellyfin }
                if (canPlay) {
                    HeroPillButton(
                        text = if (item.resumePositionTicks > 0L) "Resume" else "Play",
                        icon = Icons.Filled.PlayArrow,
                        isPrimary = true,
                        onClick = onPlay
                    )
                }

                if (item.tmdbId != null && (item.canRequestHd || item.canRequest4k || item.mediaType == MediaType.SERIES)) {
                    HeroPillButton(
                        text = if (canPlay) "Request 4K / More" else "Request HD / 4K",
                        icon = Icons.Filled.Add,
                        isPrimary = !canPlay,
                        onClick = onRequestClick
                    )
                }

                HeroPillButton(
                    text = if (item.isInMyList) "In My List" else "My List",
                    icon = if (item.isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                    isPrimary = false,
                    onClick = onToggleMyList
                )

                if (!canPlay && item.overallAvailability == AvailabilityTier.UNKNOWN && item.tmdbId != null) {
                    HeroPillButton(
                        text = if (item.isReminderSet) "Reminder Set" else "Remind Me",
                        icon = if (item.isReminderSet) Icons.Filled.NotificationsActive else Icons.Filled.Notifications,
                        isPrimary = false,
                        onClick = onToggleReminder
                    )
                }

                if (!item.youtubeTrailerKey.isNullOrBlank() || !item.localTrailerItemId.isNullOrBlank()) {
                    HeroPillButton(
                        text = "Trailer",
                        icon = Icons.Filled.Theaters,
                        isPrimary = false,
                        onClick = onWatchTrailer
                    )
                }

                if (item.resumePositionTicks > 0L && item.jellyfinId != null) {
                    HeroPillButton(
                        text = "Remove Progress",
                        icon = Icons.Filled.DeleteOutline,
                        isPrimary = false,
                        onClick = onRemoveFromContinueWatching
                    )
                }

                val pendingReqId = item.uhdRequestId ?: item.hdRequestId
                if (pendingReqId != null && !canPlay && item.overallAvailability.isRequestedOrDownloading) {
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

@Composable
private fun TierPill(tierName: String, status: AvailabilityTier) {
    val (label, color) = when (status) {
        AvailabilityTier.AVAILABLE -> "$tierName Available" to NetflixColors.MatchGreen
        AvailabilityTier.PARTIALLY_AVAILABLE -> "$tierName Partial" to NetflixColors.PendingAmber
        AvailabilityTier.PROCESSING -> "$tierName Downloading" to NetflixColors.DownloadingBlue
        AvailabilityTier.PENDING -> "$tierName Requested" to NetflixColors.PendingAmber
        else -> "$tierName Not in Library" to NetflixColors.TextMuted
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xAA1F1F1F))
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DownloadProgressCard(dl: DownloadProgressInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xCC1E1E1E))
            .border(1.dp, NetflixColors.DownloadingBlue.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dl.label,
                color = NetflixColors.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            dl.title?.let {
                Text(
                    text = it,
                    color = NetflixColors.TextSecondary,
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
                    .background(NetflixColors.NetflixRed)
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
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    isSelected -> NetflixColors.White
                    focused -> Color(0xFF3A3A3A)
                    else -> Color(0xFF232323)
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, NetflixColors.NetflixRed, RoundedCornerShape(6.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) NetflixColors.DeepBlack else NetflixColors.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        if (isAvailable) {
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = if (isSelected) NetflixColors.DeepBlack else NetflixColors.MatchGreen,
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
    var focused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF2A2A2A) else Color(0xFF1A1A1A))
            .then(
                if (focused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(8.dp))
                else Modifier.border(1.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Text(
            text = "${episode.episodeNumber}",
            color = NetflixColors.TextSecondary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp)
        )

        Box(
            modifier = Modifier
                .width(168.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(NetflixColors.SurfaceElevated)
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
                    tint = NetflixColors.White,
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(17.dp))
                        .background(Color(0x99000000))
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
                            .background(NetflixColors.NetflixRed)
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
                    color = NetflixColors.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (episode.runtimeMinutes > 0) {
                    Text(
                        text = "${episode.runtimeMinutes}m",
                        color = NetflixColors.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                if (!episode.isAvailableInJellyfin) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF333333))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("Not on Server", color = NetflixColors.PendingAmber, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (episode.overview.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = episode.overview,
                    color = NetflixColors.TextSecondary,
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
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (requestHd: Boolean, request4k: Boolean, seasons: List<Int>?) -> Unit
) {
    var requestHd by remember { mutableStateOf(!item.hasHd) }
    var request4k by remember { mutableStateOf(item.hasHd && !item.has4k && has4kServer) }

    val selectedSeasons = remember {
        mutableStateListOf<Int>().apply {
            val missing = item.seasons.filter { !it.isPlayable }.map { it.seasonNumber }
            if (missing.isNotEmpty()) addAll(missing) else addAll(item.seasons.map { it.seasonNumber })
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(500.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Request \"${item.title}\"",
                color = NetflixColors.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = "Choose quality tier(s) to send to Seerr:",
                color = NetflixColors.TextSecondary,
                fontSize = 14.sp
            )

            // HD Option
            QualityToggleRow(
                title = "HD (1080p)",
                subtitle = if (item.hasHd && item.mediaType == MediaType.MOVIE) "Already in library" else "Standard Radarr / Sonarr profile",
                checked = requestHd,
                enabled = item.mediaType == MediaType.SERIES || !item.hasHd,
                onToggle = { requestHd = !requestHd }
            )

            // 4K Option
            if (has4kServer) {
                QualityToggleRow(
                    title = "4K Ultra HD (2160p)",
                    subtitle = if (item.has4k && item.mediaType == MediaType.MOVIE) "Already in library" else "4K Radarr / Sonarr profile",
                    checked = request4k,
                    enabled = item.mediaType == MediaType.SERIES || !item.has4k,
                    onToggle = { request4k = !request4k }
                )
            }

            if (item.mediaType == MediaType.SERIES && item.seasons.isNotEmpty()) {
                Text(
                    text = "Select Seasons (${selectedSeasons.size} selected):",
                    color = NetflixColors.TextPrimary,
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
                            onSubmit(requestHd, request4k, seasonsList)
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
private fun QualityToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (focused) Color(0xFF2D2D2D) else Color(0xFF222222))
            .then(if (focused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(8.dp)) else Modifier)
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
                checkedColor = NetflixColors.NetflixRed,
                uncheckedColor = NetflixColors.TextSecondary
            )
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = if (enabled) NetflixColors.White else NetflixColors.TextMuted, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = NetflixColors.TextSecondary, fontSize = 12.sp)
        }
    }
}
