package com.seerflix.tv.feature.search

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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NetflixColors.Background)
            .padding(horizontal = 48.dp, vertical = 28.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            var backFocused by remember { mutableStateOf(false) }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (backFocused) Color(0xFF3A3A3A) else Color(0xFF232323))
                    .then(if (backFocused) Modifier.border(2.dp, NetflixColors.White, RoundedCornerShape(22.dp)) else Modifier)
                    .onFocusChanged { backFocused = it.isFocused }
                    .focusable()
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NetflixColors.White)
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text("Search movies & TV shows (Library + Seerr)...") },
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
        }

        Spacer(Modifier.height(24.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isSearching -> {
                    CircularProgressIndicator(
                        color = NetflixColors.NetflixRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.query.length < 2 -> {
                    Text(
                        text = "Type a movie or TV show title to search your Jellyfin library and Seerr catalog simultaneously.",
                        color = NetflixColors.TextSecondary,
                        fontSize = 15.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.results.isEmpty() -> {
                    Text(
                        text = "No results found for \"${state.query}\"",
                        color = NetflixColors.TextSecondary,
                        fontSize = 16.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
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
