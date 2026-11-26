package com.seerflix.tv.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.components.TabularNumbers
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val strings = AppStrings.current
    val colors = SeerflixTokens.colors
    val gridState = rememberLazyGridState()
    val firstItemFocus = remember { FocusRequester() }
    val filterButtonFocus = remember { FocusRequester() }
    val selectedLetterFocus = remember { FocusRequester() }
    var showFilterPanel by remember { mutableStateOf(false) }
    BackHandler {
        if (!viewModel.navigateUpFolder()) onBack()
    }

    // Land in the grid as soon as the first page is in; until then the Sort & Filter button holds focus.
    var focusedFirstPage by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L)
        runCatching { filterButtonFocus.requestFocus() }
    }
    LaunchedEffect(state.items.isNotEmpty()) {
        if (state.items.isNotEmpty() && !focusedFirstPage) {
            focusedFirstPage = true
            delay(60L)
            runCatching { firstItemFocus.requestFocus() }
        }
    }

    val sortLabels = listOf(
        LibrarySort.NAME to strings.sortName,
        LibrarySort.DATE_ADDED to strings.sortDateAdded,
        LibrarySort.RELEASED to strings.sortReleased,
        LibrarySort.RATING to strings.sortRating,
        LibrarySort.RANDOM to strings.sortRandom
    )
    val filterLabels = listOf(
        LibraryFilter.ALL to strings.libraryFilterAll,
        LibraryFilter.UNWATCHED to strings.libraryFilterUnwatched,
        LibraryFilter.WATCHED to strings.libraryFilterWatched,
        LibraryFilter.FAVORITES to strings.libraryFilterFavorites
    )
    // What the button says: the current sort, plus every filter that is narrowing the grid.
    val summary = listOfNotNull(
        sortLabels.firstOrNull { it.first == state.sort }?.second,
        filterLabels.firstOrNull { it.first == state.filter && it.first != LibraryFilter.ALL }?.second,
        state.genre,
        state.letter
    ).joinToString(" · ")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp, end = 48.dp)
        ) {
            Text(
                text = state.displayTitle,
                color = colors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            Spacer(Modifier.width(14.dp))
            if (!state.isLoading) {
                Text(
                    strings.libraryTitleCount.format(state.totalCount),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    style = TabularNumbers
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.canToggleFolderMode) {
                SortFilterButton(
                    label = if (state.folderMode) strings.libraryFolderView else strings.libraryGridView,
                    summary = "",
                    onClick = viewModel::toggleFolderMode
                )
                Spacer(Modifier.width(10.dp))
            }
            SortFilterButton(
                label = strings.librarySortFilter,
                summary = summary,
                onClick = { showFilterPanel = true },
                modifier = Modifier.focusRequester(filterButtonFocus)
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                when {
                    state.isLoading -> CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    state.errorMessage != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text(state.errorMessage.orEmpty(), color = colors.textSecondary, fontSize = 16.sp)
                        Spacer(Modifier.height(14.dp))
                        val retryFocus = remember { FocusRequester() }
                        LaunchedEffect(Unit) {
                            delay(60L)
                            runCatching { retryFocus.requestFocus() }
                        }
                        SortFilterButton(
                            label = strings.commonRetry,
                            summary = "",
                            onClick = viewModel::retry,
                            modifier = Modifier.focusRequester(retryFocus)
                        )
                    }
                    state.items.isEmpty() -> Text(
                        strings.libraryEmpty,
                        color = colors.textSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    else -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 128.dp),
                        contentPadding = PaddingValues(start = 48.dp, end = 12.dp, top = 16.dp, bottom = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.items, key = { _, item -> item.id }, contentType = { _, _ -> "poster" }) { index, item ->
                            NetflixPosterCard(
                                item = item,
                                onFocus = {
                                    if (index >= state.items.size - LOAD_MORE_THRESHOLD) viewModel.loadMore()
                                },
                                onClick = {
                                    if (item.isFolder && !item.jellyfinId.isNullOrBlank()) {
                                        viewModel.enterFolder(item.jellyfinId, item.title)
                                    } else {
                                        onOpenDetails(item)
                                    }
                                },
                                forcePortrait = true,
                                modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                            )
                        }
                        if (state.loadMoreFailed) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "load_more_failed") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    SortFilterButton(label = strings.commonRetry, summary = "", onClick = viewModel::retry)
                                }
                            }
                        }
                        if (state.isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "loading_more") {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = colors.accent)
                                }
                            }
                        }
                    }
                }
            }

            // A-Z strip: press Right from the grid's last column. Entering it lands on the
            // current letter; picking one narrows the grid to titles starting with it.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier
                    .width(40.dp)
                    .fillMaxHeight()
                    .padding(top = 12.dp, bottom = 16.dp, end = 8.dp)
                    .focusProperties { enter = { selectedLetterFocus } }
                    .focusGroup()
            ) {
                LetterButton(
                    label = "•",
                    selected = state.letter == null,
                    onClick = { viewModel.setLetter(null) },
                    modifier = if (state.letter == null) Modifier.focusRequester(selectedLetterFocus) else Modifier
                )
                LibraryViewModel.LETTERS.forEach { letter ->
                    LetterButton(
                        label = letter,
                        selected = state.letter == letter,
                        onClick = { viewModel.setLetter(letter) },
                        modifier = if (state.letter == letter) Modifier.focusRequester(selectedLetterFocus) else Modifier
                    )
                }
            }
        }
    }

    if (showFilterPanel) {
        SortFilterPanel(
            title = strings.librarySortFilter,
            sortHeader = strings.librarySortBy,
            showHeader = strings.libraryShow,
            genreHeader = strings.libraryGenre,
            sortOptions = sortLabels,
            selectedSort = state.sort,
            onSelectSort = viewModel::setSort,
            filterOptions = filterLabels,
            selectedFilter = state.filter,
            onSelectFilter = viewModel::setFilter,
            allGenresLabel = strings.libraryAllGenres,
            genres = state.genres,
            selectedGenre = state.genre,
            onSelectGenre = viewModel::setGenre,
            onDismiss = {
                showFilterPanel = false
                runCatching { filterButtonFocus.requestFocus() }
            }
        )
    }
}

private const val LOAD_MORE_THRESHOLD = 18

@Composable
private fun SortFilterButton(
    label: String,
    summary: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(22.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(shape)
            .background(if (focused) colors.white else colors.surface)
            .border(1.dp, if (focused) colors.white else colors.badgeBorder.copy(alpha = 0.5f), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        val fg = if (focused) colors.deepBlack else colors.white
        Icon(Icons.Filled.Tune, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        if (summary.isNotEmpty()) {
            Spacer(Modifier.width(10.dp))
            Text(
                summary,
                color = if (focused) colors.deepBlack.copy(alpha = 0.7f) else colors.textSecondary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 260.dp)
            )
        }
    }
}

@Composable
private fun LetterButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 28.dp, height = 15.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                when {
                    focused -> colors.white
                    selected -> colors.surfaceElevated
                    else -> colors.background
                }
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            color = when {
                focused -> colors.deepBlack
                selected -> colors.white
                else -> colors.textMuted
            },
            fontSize = 10.sp,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** Right-hand panel with every sort and filter option in one scrolling list. */
@Composable
private fun <S, F> SortFilterPanel(
    title: String,
    sortHeader: String,
    showHeader: String,
    genreHeader: String,
    sortOptions: List<Pair<S, String>>,
    selectedSort: S,
    onSelectSort: (S) -> Unit,
    filterOptions: List<Pair<F, String>>,
    selectedFilter: F,
    onSelectFilter: (F) -> Unit,
    allGenresLabel: String,
    genres: List<String>,
    selectedGenre: String?,
    onSelectGenre: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = SeerflixTokens.colors
    val firstFocus = remember { FocusRequester() }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // A Dialog window wraps its content by default; make it a full-height sheet on the right.
        val dialogWindow = (androidx.compose.ui.platform.LocalView.current.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        LaunchedEffect(dialogWindow) {
            dialogWindow?.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            runCatching { firstFocus.requestFocus() }
        }
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(340.dp)
                    .fillMaxHeight()
                    .background(colors.dialogSurface)
                    .padding(top = 28.dp)
            ) {
                Text(
                    title,
                    color = colors.white,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    item(key = "h_sort") { PanelHeader(sortHeader) }
                    items(sortOptions, key = { "s_${it.second}" }) { (sort, label) ->
                        PanelOption(
                            label = label,
                            selected = sort == selectedSort,
                            onClick = { onSelectSort(sort) },
                            modifier = if (sort == selectedSort) Modifier.focusRequester(firstFocus) else Modifier
                        )
                    }
                    item(key = "h_show") { PanelHeader(showHeader) }
                    items(filterOptions, key = { "f_${it.second}" }) { (filter, label) ->
                        PanelOption(label, filter == selectedFilter, { onSelectFilter(filter) })
                    }
                    if (genres.isNotEmpty()) {
                        item(key = "h_genre") { PanelHeader(genreHeader) }
                        item(key = "g_all") {
                            PanelOption(allGenresLabel, selectedGenre == null, { onSelectGenre(null) })
                        }
                        items(genres, key = { "g_$it" }) { genre ->
                            PanelOption(genre, genre == selectedGenre, { onSelectGenre(genre) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(text: String) {
    val colors = SeerflixTokens.colors
    Text(
        text.uppercase(),
        color = colors.textMuted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun PanelOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(8.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(shape)
            .background(if (focused) colors.white else androidx.compose.ui.graphics.Color.Transparent)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        val fg = if (focused) colors.deepBlack else colors.white
        Text(
            label,
            color = fg,
            fontSize = 15.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = if (focused) colors.deepBlack else colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}
