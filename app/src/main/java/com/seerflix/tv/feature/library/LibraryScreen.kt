package com.seerflix.tv.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.seerflix.tv.core.i18n.AppStrings
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.theme.SeerflixTokens
import kotlinx.coroutines.delay

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
    val sortFocus = remember { FocusRequester() }
    BackHandler(onBack = onBack)

    // Focus the first title once the first page is in; until then the sort chips hold focus.
    var focusedFirstPage by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(60L)
        runCatching { sortFocus.requestFocus() }
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(top = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(horizontal = 48.dp)
        ) {
            Text(state.title, color = colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(16.dp))
            if (!state.isLoading) {
                Text(
                    strings.libraryTitleCount.format(state.totalCount),
                    color = colors.textSecondary,
                    fontSize = 15.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }

        // Sort + watched filter
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sortLabels, key = { "sort_${it.first.name}" }) { (sort, label) ->
                FilterChip(
                    label = label,
                    selected = state.sort == sort,
                    onClick = { viewModel.setSort(sort) },
                    modifier = if (sort == LibrarySort.NAME) Modifier.focusRequester(sortFocus) else Modifier
                )
            }
            item(key = "divider") { Spacer(Modifier.width(18.dp)) }
            items(filterLabels, key = { "filter_${it.first.name}" }) { (filter, label) ->
                FilterChip(label, state.filter == filter, { viewModel.setFilter(filter) })
            }
        }

        // Genres
        if (state.genres.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "genre_all") {
                    FilterChip(strings.libraryAllGenres, state.genre == null, { viewModel.setGenre(null) }, small = true)
                }
                items(state.genres, key = { "genre_$it" }) { genre ->
                    FilterChip(genre, state.genre == genre, { viewModel.setGenre(genre) }, small = true)
                }
            }
        }

        // A-Z jump (prefix filter); only meaningful for name order but useful with any sort.
        LazyRow(
            contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item(key = "letter_all") {
                FilterChip(strings.libraryFilterAll, state.letter == null, { viewModel.setLetter(null) }, small = true)
            }
            items(LibraryViewModel.LETTERS, key = { "letter_$it" }) { letter ->
                FilterChip(letter, state.letter == letter, { viewModel.setLetter(letter) }, small = true)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> CircularProgressIndicator(
                    color = colors.accent,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.errorMessage != null -> Text(
                    state.errorMessage.orEmpty(),
                    color = colors.textSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                state.items.isEmpty() -> Text(
                    strings.libraryEmpty,
                    color = colors.textSecondary,
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
                else -> LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 128.dp),
                    contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }, contentType = { _, _ -> "poster" }) { index, item ->
                        NetflixPosterCard(
                            item = item,
                            onFocus = {
                                if (index >= state.items.size - LOAD_MORE_THRESHOLD) viewModel.loadMore()
                            },
                            onClick = { onOpenDetails(item) },
                            forcePortrait = true,
                            modifier = if (index == 0) Modifier.focusRequester(firstItemFocus) else Modifier
                        )
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
    }
}

private const val LOAD_MORE_THRESHOLD = 18

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false
) {
    val colors = SeerflixTokens.colors
    val shape = RoundedCornerShape(20.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .background(
                when {
                    selected -> colors.white
                    focused -> colors.secondaryButtonFocusedBg
                    else -> colors.surface
                },
                shape
            )
            .border(if (focused) 2.dp else 1.dp, if (focused) colors.accent else colors.badgeBorder.copy(alpha = 0.45f), shape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(horizontal = if (small) 11.dp else 16.dp, vertical = if (small) 5.dp else 8.dp)
    ) {
        Text(
            label,
            color = if (selected) colors.deepBlack else Color.White,
            fontSize = if (small) 12.sp else 14.sp,
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
        )
    }
}
