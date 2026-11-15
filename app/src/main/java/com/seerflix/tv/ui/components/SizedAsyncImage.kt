package com.seerflix.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import coil3.memory.MemoryCache
import coil3.request.ImageRequest
import coil3.size.Precision
import com.seerflix.tv.core.data.MediaImageUrls

/**
 * [AsyncImage] for fixed-size artwork slots (cards, hero, thumbnails).
 *
 * - Asks Jellyfin / TMDB for a server-side resized variant that matches the slot
 *   ([MediaImageUrls.forDisplayWidth]) instead of the one-size-fits-all repository URL.
 * - Passes the slot size to Coil up front, so decoding happens at display size and a memory-cache
 *   hit can be served in the first frame without waiting for layout constraints.
 * - [keepPreviousWhileLoading] shows the previously loaded image (from the memory cache) while the
 *   next one loads, instead of flashing the background — used by the hero, whose artwork swaps on
 *   every featured-item change. No crossfade layer is involved, so the swap costs a single redraw.
 */
@Composable
fun SizedAsyncImage(
    url: String?,
    contentDescription: String?,
    width: Dp,
    height: Dp,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    maxRequestWidthPx: Int = 1280,
    keepPreviousWhileLoading: Boolean = false,
    /** Called when the artwork can't be loaded (404, decode error, null URL) so callers can show a fallback. */
    onError: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }.coerceAtLeast(1)
    val heightPx = with(density) { height.roundToPx() }.coerceAtLeast(1)
    // Plain holder (not snapshot state): updating it must not trigger recomposition / a new request.
    val lastLoadedKey = remember { arrayOfNulls<MemoryCache.Key>(1) }
    val request = remember(url, widthPx, heightPx, maxRequestWidthPx, keepPreviousWhileLoading) {
        ImageRequest.Builder(context)
            .data(MediaImageUrls.forDisplayWidth(url, widthPx, maxRequestWidthPx))
            .size(widthPx, heightPx)
            .precision(Precision.INEXACT)
            .apply {
                val previous = lastLoadedKey[0]
                if (keepPreviousWhileLoading && previous != null) placeholderMemoryCacheKey(previous)
            }
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        alignment = alignment,
        onSuccess = if (keepPreviousWhileLoading) {
            { state -> lastLoadedKey[0] = state.result.memoryCacheKey }
        } else {
            null
        },
        onError = if (onError != null) {
            { _ -> onError() }
        } else {
            null
        }
    )
}
