package com.seerflix.tv.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.theme.CardPresentationStyle
import com.seerflix.tv.ui.theme.SeerflixTokens

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val voiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.onQueryChange(spoken)
            }
        }
    }

    SearchContent(
        state = state,
        onBack = onBack,
        onQueryChange = viewModel::onQueryChange,
        onVoiceSearch = {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Search movies, TV shows, or actors")
            }
            runCatching { voiceLauncher.launch(intent) }
        },
        onSelectGenre = viewModel::selectGenre,
        onSelectPerson = viewModel::selectPerson,
        onClearFilter = viewModel::clearFilter,
        onOpenDetails = onOpenDetails
    )
}

@Composable
fun SearchContent(
    state: SearchUiState,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onVoiceSearch: () -> Unit,
    onSelectGenre: (GenreFilterOption) -> Unit,
    onSelectPerson: (PersonSearchResult) -> Unit,
    onClearFilter: () -> Unit,
    onOpenDetails: (UnifiedMediaItem) -> Unit
) {
    val colors = SeerflixTokens.colors
    val spec = SeerflixTokens.spec
    val gridMinWidth = if (spec.cardStyle == CardPresentationStyle.WIDESCREEN_16_9) 224.dp else 148.dp
    val searchInputFocusRequester = remember { FocusRequester() }
    val clearFilterFocusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Set when the focused element is about to disappear (a person tile or the Clear chip), so
    // focus can be handed to a stable target on the next frame instead of being lost.
    var pendingFocusTarget by remember { mutableStateOf<FocusRequester?>(null) }
    LaunchedEffect(Unit) {
        // Arriving on an actor's filmography (from Details) shouldn't pop the on-screen keyboard
        // over the results; focus the filter chip instead of the text field.
        val target = if (state.activePersonName != null) clearFilterFocusRequester else searchInputFocusRequester
        runCatching { target.requestFocus() }
    }
    LaunchedEffect(pendingFocusTarget, state.activePersonName, state.selectedGenre) {
        val target = pendingFocusTarget ?: return@LaunchedEffect
        if (target == clearFilterFocusRequester && state.activePersonName == null && state.selectedGenre == null) {
            return@LaunchedEffect // Clear chip not composed yet
        }
        pendingFocusTarget = null
        runCatching { target.requestFocus() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SearchIconCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        if (state.isSeerrConfigured) "Search movies, TV shows, or cast (Library + Seerr)..."
                        else "Search movies & TV shows in your Jellyfin library..."
                    )
                },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = colors.textSecondary) },
                singleLine = true,
                // The IME's action key closes the keyboard and moves into the results instead of
                // doing nothing (default single-line behaviour), which left TV users stuck behind it.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    keyboardController?.hide()
                    focusManager.moveFocus(FocusDirection.Down)
                }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = colors.white,
                    unfocusedTextColor = colors.textPrimary,
                    focusedBorderColor = colors.accent,
                    unfocusedBorderColor = colors.badgeBorder
                ),
                modifier = Modifier.weight(1f).focusRequester(searchInputFocusRequester)
            )

            SearchIconCircleButton(
                icon = Icons.Filled.Mic,
                contentDescription = "Voice Search",
                onClick = onVoiceSearch
            )
        }

        Spacer(Modifier.height(14.dp))

        // Browse by Genre chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(GenreFilterOption.ALL_GENRES.drop(1), key = { it.id }) { genre ->
                var focused by remember { mutableStateOf(false) }
                val selected = state.selectedGenre?.id == genre.id
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            when {
                                selected -> colors.white
                                focused -> colors.surfaceElevated
                                else -> colors.surface
                            }
                        )
                        .then(
                            if (focused) Modifier.border(2.dp, colors.accent, RoundedCornerShape(18.dp))
                            else Modifier.border(1.dp, colors.badgeBorder.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .clickable { onSelectGenre(genre) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = genre.label,
                        color = if (selected) colors.deepBlack else colors.white,
                        fontSize = 12.sp,
                        fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Active Person or Genre header banner
        val activeFilterLabel = when {
            state.activePersonName != null -> "Filmography • ${state.activePersonName}"
            state.selectedGenre != null -> "Genre • ${state.selectedGenre.label}"
            else -> null
        }
        if (activeFilterLabel != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text(
                    text = activeFilterLabel,
                    color = colors.white,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                var clearFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .focusRequester(clearFilterFocusRequester)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (clearFocused) colors.surfaceElevated else colors.surface)
                        .then(if (clearFocused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(16.dp)) else Modifier)
                        .onFocusChanged { clearFocused = it.isFocused }
                        .clickable {
                            // This chip disappears once the filter is cleared.
                            onClearFilter()
                            pendingFocusTarget = searchInputFocusRequester
                        }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = colors.white, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", color = colors.white, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val hasActiveFilter = state.activePersonName != null || state.selectedGenre != null
            when {
                state.isSearching -> {
                    CircularProgressIndicator(
                        color = colors.accent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                !hasActiveFilter && state.query.trim().length < 2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Top Searches",
                            color = colors.white,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (state.isLoadingSuggestions && state.trendingItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.accent)
                            }
                        } else if (state.trendingItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Type a movie, TV show, or actor name to search.",
                                    color = colors.textSecondary,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = gridMinWidth),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                items(state.trendingItems, key = { "trending_${it.id}" }) { item ->
                                    NetflixPosterCard(
                                        item = item,
                                        onFocus = {},
                                        onClick = { onOpenDetails(item) }
                                    )
                                }
                            }
                        }
                    }
                }
                state.results.isEmpty() && state.personResults.isEmpty() -> {
                    Text(
                        text = if (hasActiveFilter) "No matching titles found." else "No results found for \"${state.query}\"",
                        color = colors.textSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.personResults.isNotEmpty()) {
                            Text(
                                text = "People",
                                color = colors.white,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 10.dp)
                            )
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.padding(bottom = 18.dp)
                            ) {
                                items(state.personResults, key = { it.id }) { person ->
                                    var focused by remember { mutableStateOf(false) }
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier
                                            .width(96.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (focused) colors.surfaceElevated else Color.Transparent)
                                            .then(if (focused) Modifier.border(2.dp, colors.focusBorder, RoundedCornerShape(8.dp)) else Modifier)
                                            .onFocusChanged { focused = it.isFocused }
                                            .clickable {
                                                // The People row is removed once a person is picked.
                                                onSelectPerson(person)
                                                pendingFocusTarget = clearFilterFocusRequester
                                            }
                                            .padding(6.dp)
                                    ) {
                                        AsyncImage(
                                            model = person.imageUrl,
                                            contentDescription = person.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(32.dp))
                                                .background(colors.surfaceElevated)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = person.name,
                                            color = if (focused) colors.white else colors.textPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        if (state.results.isNotEmpty()) {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = gridMinWidth),
                                contentPadding = PaddingValues(bottom = 32.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                items(state.results, key = { it.id }) { item ->
                                    NetflixPosterCard(
                                        item = item,
                                        onFocus = {},
                                        onClick = { onOpenDetails(item) }
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

@Composable
private fun SearchIconCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
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
        Icon(icon, contentDescription = contentDescription, tint = colors.white)
    }
}

