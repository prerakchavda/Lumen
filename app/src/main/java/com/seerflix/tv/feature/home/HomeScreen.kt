package com.seerflix.tv.feature.home

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.seerflix.tv.core.model.CatalogRow
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.RowStyle
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.ContinueWatchingCard
import com.seerflix.tv.ui.components.HeroBillboard
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.components.Top10PosterCard
import com.seerflix.tv.ui.theme.NetflixColors

@Composable
fun HomeScreen(
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    onPlayDirect: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
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
        onRefresh = viewModel::refresh,
        onTogglePreviewMute = viewModel::togglePreviewMute,
        onToggleFeaturedMyList = viewModel::toggleFeaturedMyList,
        onItemFocused = viewModel::onItemFocused,
        onOpenContinueWatchingOptions = viewModel::openContinueWatchingOptions,
        onDismissContinueWatchingOptions = viewModel::dismissContinueWatchingOptions,
        onRemoveFromContinueWatching = viewModel::removeFromContinueWatching
    )
}

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
    onToggleFeaturedMyList: () -> Unit = {},
    onOpenContinueWatchingOptions: (UnifiedMediaItem) -> Unit = {},
    onDismissContinueWatchingOptions: () -> Unit = {},
    onRemoveFromContinueWatching: (UnifiedMediaItem) -> Unit = {}
) {
    var railExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixColors.Background)
    ) {
        // Collapsible Left Navigation Rail
        NetflixNavRail(
            expanded = railExpanded,
            activeTab = state.activeTab,
            activeDownloadCount = state.activeDownloadCount,
            profileName = state.profile?.name ?: "Profile",
            onRailFocusChange = { railExpanded = it },
            onSelectTab = onSelectTab,
            onOpenSearch = onOpenSearch,
            onOpenRequests = onOpenRequests,
            onSwitchProfile = onSwitchProfile,
            onOpenSettings = onOpenSettings,
            onRefresh = onRefresh
        )

        // Main Content Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(NetflixColors.Background)
        ) {
            when {
                state.isLoading && state.rows.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(color = NetflixColors.NetflixRed)
                        Spacer(Modifier.height(14.dp))
                        Text("Loading your Netflix-style hub…", color = NetflixColors.TextSecondary, fontSize = 14.sp)
                    }
                }
                state.errorMessage != null && state.rows.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(state.errorMessage!!, color = NetflixColors.NetflixRed, fontSize = 15.sp)
                        Spacer(Modifier.height(14.dp))
                        NavRailItem(
                            icon = Icons.Filled.Refresh,
                            label = "Retry",
                            expanded = true,
                            selected = false,
                            onClick = onRefresh
                        )
                    }
                }
                state.activeTab == HomeTab.MY_LIST && state.rows.isEmpty() -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Bookmark,
                            contentDescription = null,
                            tint = NetflixColors.TextMuted,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(14.dp))
                        Text("Your My List is empty", color = NetflixColors.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Add movies and shows from the Hero banner or Details screen to find them here.",
                            color = NetflixColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 48.dp)
                    ) {
                        state.featuredItem?.let { hero ->
                            item(key = "hero_billboard") {
                                HeroBillboard(
                                    item = hero,
                                    previewStream = state.previewStream,
                                    isMuted = state.isPreviewMuted,
                                    onToggleMute = onTogglePreviewMute,
                                    onPrimaryAction = {
                                        if (hero.isPlayable && hero.jellyfinId != null) {
                                            onPlayDirect(hero.jellyfinId)
                                        } else {
                                            onOpenDetails(hero)
                                        }
                                    },
                                    onMoreInfo = { onOpenDetails(hero) },
                                    onToggleMyList = onToggleFeaturedMyList
                                )
                            }
                        }

                        if (state.activeTab == HomeTab.SHOWS || state.activeTab == HomeTab.MOVIES) {
                            item(key = "genre_filter_bar") {
                                GenreFilterBar(
                                    selectedGenre = state.selectedGenre,
                                    onSelectGenre = onSelectGenre
                                )
                            }
                        }

                        items(state.rows, key = { it.id }) { row ->
                            CatalogRowSection(
                                row = row,
                                onItemFocus = onItemFocused,
                                onItemClick = { item ->
                                    if (row.style == RowStyle.CONTINUE_WATCHING && item.jellyfinId != null) {
                                        onPlayDirect(item.jellyfinId)
                                    } else {
                                        onOpenDetails(item)
                                    }
                                },
                                onContinueWatchingOptions = onOpenContinueWatchingOptions
                            )
                        }
                    }
                }
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
}

@Composable
private fun GenreFilterBar(
    selectedGenre: GenreFilterOption,
    onSelectGenre: (GenreFilterOption) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(GenreFilterOption.ALL_GENRES, key = { it.id }) { genre ->
            var focused by remember { mutableStateOf(false) }
            val selected = selectedGenre.id == genre.id
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        when {
                            selected -> NetflixColors.White
                            focused -> Color(0xFF3E3E3E)
                            else -> Color(0xFF232323)
                        }
                    )
                    .then(
                        if (focused) Modifier.border(2.dp, NetflixColors.NetflixRed, RoundedCornerShape(20.dp))
                        else Modifier.border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(20.dp))
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .clickable { onSelectGenre(genre) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = genre.label,
                    color = if (selected) NetflixColors.DeepBlack else NetflixColors.White,
                    fontSize = 13.sp,
                    fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
                )
            }
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
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1A1A1A))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(10.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.title,
                color = NetflixColors.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
            item.episodeLabel?.let {
                Text(it, color = NetflixColors.TextSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            ContextActionRow(Icons.Filled.PlayArrow, "Resume Playing", onResume)
            ContextActionRow(Icons.Filled.Info, "More Details", onViewDetails)
            ContextActionRow(Icons.Filled.DeleteOutline, "Remove from Continue Watching", onRemove)
        }
    }
}

@Composable
private fun ContextActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) Color(0xFF3A3A3A) else Color(0xFF242424))
            .then(if (focused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(6.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = NetflixColors.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = NetflixColors.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CatalogRowSection(
    row: CatalogRow,
    onItemFocus: (UnifiedMediaItem) -> Unit,
    onItemClick: (UnifiedMediaItem) -> Unit,
    onContinueWatchingOptions: (UnifiedMediaItem) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp)
    ) {
        Text(
            text = row.title,
            color = NetflixColors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, bottom = 10.dp)
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (row.style) {
                RowStyle.CONTINUE_WATCHING -> {
                    items(row.items, key = { "${row.id}_${it.id}" }) { item ->
                        ContinueWatchingCard(
                            item = item,
                            onFocus = { onItemFocus(item) },
                            onClick = { onItemClick(item) },
                            onOptionsMenu = { onContinueWatchingOptions(item) }
                        )
                    }
                }
                RowStyle.TOP_10 -> {
                    itemsIndexed(row.items, key = { idx, it -> "${row.id}_${idx}_${it.id}" }) { idx, item ->
                        Top10PosterCard(
                            rank = idx + 1,
                            item = item,
                            onFocus = { onItemFocus(item) },
                            onClick = { onItemClick(item) }
                        )
                    }
                }
                else -> {
                    items(row.items, key = { "${row.id}_${it.id}" }) { item ->
                        NetflixPosterCard(
                            item = item,
                            onFocus = { onItemFocus(item) },
                            onClick = { onItemClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NetflixNavRail(
    expanded: Boolean,
    activeTab: HomeTab,
    activeDownloadCount: Int,
    profileName: String,
    onRailFocusChange: (Boolean) -> Unit,
    onSelectTab: (HomeTab) -> Unit,
    onOpenSearch: () -> Unit,
    onOpenRequests: () -> Unit,
    onSwitchProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(if (expanded) 208.dp else 68.dp)
            .animateContentSize()
            .background(NetflixColors.RailBackground)
            .onFocusChanged { focusState ->
                onRailFocusChange(focusState.hasFocus)
            }
            .padding(vertical = 20.dp, horizontal = 10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // Netflix-inspired brand mark
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "S",
                    color = NetflixColors.NetflixRed,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                if (expanded) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "SEERFLIX",
                        color = NetflixColors.NetflixRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    )
                }
            }

            NavRailItem(
                icon = Icons.Filled.Search,
                label = "Search",
                expanded = expanded,
                selected = false,
                onClick = onOpenSearch
            )
            NavRailItem(
                icon = Icons.Filled.Home,
                label = "Home",
                expanded = expanded,
                selected = activeTab == HomeTab.HOME,
                onClick = { onSelectTab(HomeTab.HOME) }
            )
            NavRailItem(
                icon = Icons.Filled.NewReleases,
                label = "New & Popular",
                expanded = expanded,
                selected = activeTab == HomeTab.NEW_AND_POPULAR,
                onClick = { onSelectTab(HomeTab.NEW_AND_POPULAR) }
            )
            NavRailItem(
                icon = Icons.Filled.Tv,
                label = "TV Shows",
                expanded = expanded,
                selected = activeTab == HomeTab.SHOWS,
                onClick = { onSelectTab(HomeTab.SHOWS) }
            )
            NavRailItem(
                icon = Icons.Filled.Movie,
                label = "Movies",
                expanded = expanded,
                selected = activeTab == HomeTab.MOVIES,
                onClick = { onSelectTab(HomeTab.MOVIES) }
            )
            NavRailItem(
                icon = Icons.Filled.Bookmark,
                label = "My List",
                expanded = expanded,
                selected = activeTab == HomeTab.MY_LIST,
                onClick = { onSelectTab(HomeTab.MY_LIST) }
            )
            NavRailItem(
                icon = Icons.Filled.CloudDownload,
                label = "Requests",
                expanded = expanded,
                selected = false,
                badgeCount = activeDownloadCount,
                onClick = onOpenRequests
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            NavRailItem(
                icon = Icons.Filled.Refresh,
                label = "Refresh",
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
                label = "Servers",
                expanded = expanded,
                selected = false,
                onClick = onOpenSettings
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
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                when {
                    focused -> Color(0xFF2D2D2D)
                    selected -> Color(0xFF1F1F1F)
                    else -> Color.Transparent
                }
            )
            .then(
                if (focused) Modifier.border(1.5.dp, NetflixColors.White, RoundedCornerShape(6.dp))
                else Modifier
            )
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(if (selected) NetflixColors.NetflixRed else Color.Transparent)
        )
        Spacer(Modifier.width(8.dp))
        Box {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (focused || selected) NetflixColors.White else NetflixColors.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
            if (badgeCount > 0) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(NetflixColors.DownloadingBlue),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = badgeCount.coerceAtMost(9).toString(),
                        color = NetflixColors.White,
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
                color = if (focused || selected) NetflixColors.White else NetflixColors.TextSecondary,
                fontSize = 14.sp,
                fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

