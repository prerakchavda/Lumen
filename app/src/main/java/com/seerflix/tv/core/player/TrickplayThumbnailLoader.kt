package com.seerflix.tv.core.player

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.seerflix.tv.core.model.TrickplayCrop
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Loads seek-bar preview thumbnails from Jellyfin trickplay tile sheets.
 *
 * A tile sheet is a large JPEG (e.g. 10×10 thumbnails of 320×180 = 3200×1800, ~23 MB as an ARGB
 * bitmap). Instead of decoding the whole sheet and cropping it, the compressed sheet bytes are
 * cached and only the needed 320×180 region is decoded ([BitmapRegionDecoder], RGB_565) off the
 * main thread. Sheet downloads are not cancelled when the scrub position moves on, so fast
 * scrubbing still fills the cache and later positions in the same sheet are instant.
 */
class TrickplayThumbnailLoader(private val client: OkHttpClient) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<String, Deferred<ByteArray?>>()

    private val sheetCache = object : LruCache<String, ByteArray>(SHEET_CACHE_BYTES) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size
    }
    private val thumbCache = LruCache<String, ImageBitmap>(THUMB_CACHE_ENTRIES)

    /** Returns an already decoded thumbnail without any I/O, or null. */
    fun peek(crop: TrickplayCrop): ImageBitmap? = thumbCache.get(crop.key())

    suspend fun load(crop: TrickplayCrop): ImageBitmap? {
        val key = crop.key()
        thumbCache.get(key)?.let { return it }
        val bytes = sheetBytes(crop.tileUrl) ?: return null
        val bitmap = withContext(Dispatchers.Default) { decodeRegion(bytes, crop) } ?: return null
        thumbCache.put(key, bitmap)
        return bitmap
    }

    /** Starts downloading the sheet for [crop] (if not cached) without waiting for it. */
    fun prefetch(crop: TrickplayCrop) {
        if (sheetCache.get(crop.tileUrl) == null) fetchSheetAsync(crop.tileUrl)
    }

    fun close() {
        scope.cancel()
        inFlight.clear()
        thumbCache.evictAll()
        sheetCache.evictAll()
    }

    private suspend fun sheetBytes(url: String): ByteArray? =
        sheetCache.get(url) ?: fetchSheetAsync(url).await()

    private fun fetchSheetAsync(url: String): Deferred<ByteArray?> {
        inFlight[url]?.let { return it }
        // Lazy + putIfAbsent: an eagerly started download could finish (and run its remove) before
        // getOrPut stored it, leaving a completed Deferred in inFlight that is served forever.
        val created = scope.async(start = CoroutineStart.LAZY) {
            try {
                download(url)?.also { sheetCache.put(url, it) }
            } finally {
                inFlight.remove(url)
            }
        }
        val existing = inFlight.putIfAbsent(url, created)
        if (existing != null) {
            created.cancel()
            return existing
        }
        created.start()
        return created
    }

    private suspend fun download(url: String): ByteArray? = suspendCancellableCoroutine { cont ->
        val call = client.newCall(Request.Builder().url(url).build())
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                val bytes = response.use { resp ->
                    if (!resp.isSuccessful) null else runCatching { resp.body?.bytes() }.getOrNull()
                }
                if (cont.isActive) cont.resume(bytes)
            }
        })
    }

    private fun decodeRegion(bytes: ByteArray, crop: TrickplayCrop): ImageBitmap? = runCatching {
        val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
        } else {
            @Suppress("DEPRECATION")
            BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
        } ?: return@runCatching null
        try {
            val left = crop.x.coerceIn(0, (decoder.width - 1).coerceAtLeast(0))
            val top = crop.y.coerceIn(0, (decoder.height - 1).coerceAtLeast(0))
            val right = (left + crop.width).coerceAtMost(decoder.width)
            val bottom = (top + crop.height).coerceAtMost(decoder.height)
            if (right <= left || bottom <= top) return@runCatching null
            val options = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
            decoder.decodeRegion(Rect(left, top, right, bottom), options)?.asImageBitmap()
        } finally {
            decoder.recycle()
        }
    }.getOrNull()

    private fun TrickplayCrop.key(): String = "$tileUrl#$x,$y"

    private companion object {
        /** Compressed sheets are ~0.2–0.8 MB each; keep a handful around. */
        const val SHEET_CACHE_BYTES = 6 * 1024 * 1024

        /** ~115 KB each at 320×180 RGB_565. */
        const val THUMB_CACHE_ENTRIES = 48
    }
}
