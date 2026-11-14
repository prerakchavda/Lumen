package com.seerflix.tv.feature.requests

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.MediaRequestSummary
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.ui.components.HeroPillButton
import com.seerflix.tv.ui.theme.SeerflixTokens

@Composable
fun RequestsScreen(
    onBack: () -> Unit,
    onOpenRequestDetails: (tmdbId: Int, mediaType: MediaType) -> Unit,
    viewModel: RequestsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    RequestsContent(
        state = state,
        onBack = onBack,
        onRefresh = { viewModel.refresh() },
        onSelectFilter = viewModel::selectFilter,
        onOpenRequestDetails = onOpenRequestDetails,
        onApproveRequest = viewModel::approveRequest,
        onDeclineRequest = viewModel::declineRequest
    )
}

@Composable
fun RequestsContent(
    state: RequestsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSelectFilter: (RequestFilter) -> Unit,
    onOpenRequestDetails: (tmdbId: Int, mediaType: MediaType) -> Unit,
    onApproveRequest: (Int) -> Unit = {},
    onDeclineRequest: (Int) -> Unit = {}
) {
    val colors = SeerflixTokens.colors
    val strings = AppStrings.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                FocusIconButton(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Column {
                    Text(
                        text = "${strings.navRequests} & Downloads",
                        color = colors.white,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (state.isSeerrConfigured) {
                            "Live status synced with Seerr v3+ • Auto-refreshes every 15s"
                        } else {
                            "Jellyfin-Only Mode • Add a Seerr URL in Server Settings to enable requests"
                        },
                        color = colors.textSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            FocusIconButton(Icons.Filled.Refresh, "Refresh", onRefresh)
        }

        state.actionMessage?.let { msg ->
            Spacer(Modifier.height(10.dp))
            Text(msg, color = colors.matchGreen, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(18.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(RequestFilter.entries) { filter ->
                FilterChip(
                    label = filter.label,
                    selected = state.filter == filter,
                    onClick = { onSelectFilter(filter) }
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                !state.isSeerrConfigured -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Seerr Not Connected (Jellyfin-Only Mode)",
                            color = colors.white,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Add a Seerr URL in Server Setup to browse, submit, approve, or track media requests.",
                            color = colors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                state.isLoading -> {
                    CircularProgressIndicator(color = colors.accent, modifier = Modifier.align(Alignment.Center))
                }
                state.errorMessage != null -> {
                    Text(state.errorMessage!!, color = colors.accent, modifier = Modifier.align(Alignment.Center))
                }
                state.visibleRequests.isEmpty() -> {
                    Text(
                        text = "No requests in this view yet.",
                        color = colors.textSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(state.visibleRequests, key = { it.requestId }) { req ->
                            RequestItemCard(
                                req = req,
                                isAdmin = state.isAdmin,
                                isBusy = state.busyRequestId == req.requestId,
                                onApprove = { onApproveRequest(req.requestId) },
                                onDecline = { onDeclineRequest(req.requestId) },
                                onClick = {
                                    if (req.tmdbId > 0) {
                                        onOpenRequestDetails(req.tmdbId, req.mediaType)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestItemCard(
    req: MediaRequestSummary,
    isAdmin: Boolean,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val strings = AppStrings.current
    val corner = RoundedCornerShape(spec.cardCornerRadiusDp.dp)
    var focused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(corner)
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .then(
                if (focused) Modifier.border(2.dp, colors.focusBorder, corner)
                else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.4f), corner)
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        AsyncImage(
            model = req.posterUrl ?: req.backdropUrl,
            contentDescription = req.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(64.dp)
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.surfaceElevated)
        )

        Spacer(Modifier.width(18.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = req.title,
                    color = colors.white,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (req.is4k) colors.pendingAmber.copy(alpha = 0.22f) else colors.surfaceElevated)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (req.is4k) "4K" else "HD",
                        color = if (req.is4k) colors.pendingAmber else colors.white,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = "Requested by ${req.requestedBy} • ${if (req.mediaType == MediaType.MOVIE) "Movie" else "TV Series"}",
                color = colors.textSecondary,
                fontSize = 12.sp
            )

            Spacer(Modifier.height(8.dp))

            val statusColor = when {
                req.downloadProgress != null || req.mediaStatus == AvailabilityTier.PROCESSING -> colors.downloadingBlue
                req.mediaStatus.isInLibrary -> colors.matchGreen
                req.requestStatus == 3 -> colors.accent
                else -> colors.pendingAmber
            }

            Text(
                text = req.statusBadgeText,
                color = statusColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            req.downloadProgress?.let { dl ->
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x44FFFFFF))
                ) {
                    val frac = if (dl.sizeBytes > 0L) (dl.percentage / 100f).coerceIn(0.03f, 1f) else 0.3f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .background(colors.progressFill)
                    )
                }
            }
        }

        if (isAdmin && req.requestStatus == 1) {
            Spacer(Modifier.width(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroPillButton(
                    text = if (isBusy) "..." else strings.actionApprove,
                    icon = Icons.Filled.Check,
                    isPrimary = true,
                    onClick = { if (!isBusy) onApprove() }
                )
                HeroPillButton(
                    text = strings.actionDecline,
                    icon = Icons.Filled.Close,
                    isPrimary = false,
                    onClick = { if (!isBusy) onDecline() }
                )
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(
                when {
                    selected -> colors.white
                    focused -> colors.surfaceElevated
                    else -> colors.surface
                }
            )
            .then(if (focused) Modifier.border(2.dp, colors.accent, RoundedCornerShape(20.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            color = if (selected) colors.deepBlack else colors.white,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FocusIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (focused) colors.surfaceElevated else colors.surface)
            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(22.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = colors.white)
    }
}
