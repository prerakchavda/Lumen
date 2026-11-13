package com.seerflix.tv.feature.search

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.seerflix.tv.core.model.GenreFilterOption
import com.seerflix.tv.core.model.PersonSearchResult
import com.seerflix.tv.core.model.UnifiedMediaItem
import com.seerflix.tv.ui.components.NetflixPosterCard
import com.seerflix.tv.ui.theme.NetflixColors

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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixColors.Background)
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
                placeholder = { Text("Search movies, TV shows, or cast (Library + Seerr)...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = NetflixColors.TextSecondary) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = NetflixColors.White,
                    unfocusedTextColor = NetflixColors.TextPrimary,
                    focusedBorderColor = NetflixColors.NetflixRed,
                    unfocusedBorderColor = Color(0x44FFFFFF)
                ),
                modifier = Modifier.weight(1f)
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
                                selected -> NetflixColors.White
                                focused -> Color(0xFF3E3E3E)
                                else -> Color(0xFF232323)
                            }
                        )
                        .then(
                            if (focused) Modifier.border(2.dp, NetflixColors.NetflixRed, RoundedCornerShape(18.dp))
                            else Modifier.border(1.dp, Color(0x28FFFFFF), RoundedCornerShape(18.dp))
                        )
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .clickable { onSelectGenre(genre) }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = genre.label,
                        color = if (selected) NetflixColors.DeepBlack else NetflixColors.White,
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
                    color = NetflixColors.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                var clearFocused by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (clearFocused) Color(0xFF3E3E3E) else Color(0xFF262626))
                        .then(if (clearFocused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(16.dp)) else Modifier)
                        .onFocusChanged { clearFocused = it.isFocused }
                        .focusable()
                        .clickable(onClick = onClearFilter)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = NetflixColors.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clear", color = NetflixColors.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val hasActiveFilter = state.activePersonName != null || state.selectedGenre != null
            when {
                state.isSearching -> {
                    CircularProgressIndicator(
                        color = NetflixColors.NetflixRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                !hasActiveFilter && state.query.trim().length < 2 -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = "Top Searches",
                            color = NetflixColors.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        if (state.isLoadingSuggestions && state.trendingItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = NetflixColors.NetflixRed)
                            }
                        } else if (state.trendingItems.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Type a movie, TV show, or actor name to search.",
                                    color = NetflixColors.TextSecondary,
                                    fontSize = 15.sp
                                )
                            }
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 148.dp),
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
                        color = NetflixColors.TextSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.personResults.isNotEmpty()) {
                            Text(
                                text = "People",
                                color = NetflixColors.White,
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
                                            .background(if (focused) Color(0xFF282828) else Color.Transparent)
                                            .then(if (focused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(8.dp)) else Modifier)
                                            .onFocusChanged { focused = it.isFocused }
                                            .focusable()
                                            .clickable { onSelectPerson(person) }
                                            .padding(6.dp)
                                    ) {
                                        AsyncImage(
                                            model = person.imageUrl,
                                            contentDescription = person.name,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(32.dp))
                                                .background(NetflixColors.SurfaceElevated)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = person.name,
                                            color = if (focused) NetflixColors.White else NetflixColors.TextPrimary,
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
                                columns = GridCells.Adaptive(minSize = 148.dp),
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
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(if (focused) Color(0xFF3A3A3A) else Color(0xFF232323))
            .then(if (focused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(22.dp)) else Modifier)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = NetflixColors.White)
    }
}

