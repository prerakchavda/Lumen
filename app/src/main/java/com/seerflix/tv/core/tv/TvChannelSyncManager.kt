package com.seerflix.tv.core.tv

import android.content.Context
import android.database.Cursor
import android.net.Uri
import androidx.tvprovider.media.tv.Channel
import androidx.tvprovider.media.tv.PreviewProgram
import androidx.tvprovider.media.tv.TvContractCompat
import androidx.tvprovider.media.tv.WatchNextProgram
import com.seerflix.tv.core.model.MediaType
import com.seerflix.tv.core.model.UnifiedMediaItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvChannelSyncManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun syncHomeChannels(
        continueWatching: List<UnifiedMediaItem>,
        featuredRows: List<UnifiedMediaItem>
    ) = withContext(Dispatchers.IO) {
        runCatching {
            syncWatchNext(continueWatching)
        }
        runCatching {
            syncSeerflixChannel(featuredRows)
        }
    }

    suspend fun promoteNewReleaseToWatchNext(alert: com.seerflix.tv.core.model.ReadyRequestAlert) =
        withContext(Dispatchers.IO) {
            val jfId = alert.jellyfinId ?: return@withContext
            runCatching {
                val type = when (alert.mediaType) {
                    MediaType.MOVIE -> TvContractCompat.WatchNextPrograms.TYPE_MOVIE
                    MediaType.EPISODE -> TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                    MediaType.SERIES -> TvContractCompat.WatchNextPrograms.TYPE_TV_SERIES
                }
                val builder = WatchNextProgram.Builder()
                    .setType(type)
                    .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_NEW)
                    .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                    .setTitle(alert.title)
                    .setDescription("Requested title is now ready to watch")
                    .setIntentUri(buildPlayerDeepLink(jfId))
                    .setInternalProviderId("ready_$jfId")

                alert.posterUrl?.let { art ->
                    builder.setPosterArtUri(Uri.parse(art))
                    builder.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
                }
                context.contentResolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    builder.build().toContentValues()
                )
            }
        }

    private fun syncWatchNext(items: List<UnifiedMediaItem>) {
        val resolver = context.contentResolver
        val existingIds = mutableMapOf<String, Long>()

        resolver.query(
            TvContractCompat.WatchNextPrograms.CONTENT_URI,
            arrayOf(
                TvContractCompat.WatchNextPrograms._ID,
                TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID
            ),
            null,
            null,
            null
        )?.use { cursor: Cursor ->
            val idIdx = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms._ID)
            val provIdx = cursor.getColumnIndex(TvContractCompat.WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID)
            while (cursor.moveToNext()) {
                if (idIdx >= 0 && provIdx >= 0) {
                    val rowId = cursor.getLong(idIdx)
                    val provId = cursor.getString(provIdx)
                    if (!provId.isNullOrBlank()) {
                        existingIds[provId] = rowId
                    }
                }
            }
        }

        val currentProviderIds = mutableSetOf<String>()
        items.take(10).forEach { item ->
            val jfId = item.jellyfinId ?: return@forEach
            currentProviderIds += jfId
            val program = buildWatchNextProgram(item) ?: return@forEach
            val existingRowId = existingIds[jfId]
            if (existingRowId != null) {
                resolver.update(
                    TvContractCompat.buildWatchNextProgramUri(existingRowId),
                    program.toContentValues(),
                    null,
                    null
                )
            } else {
                resolver.insert(
                    TvContractCompat.WatchNextPrograms.CONTENT_URI,
                    program.toContentValues()
                )
            }
        }

        // Remove stale entries no longer in Continue Watching
        existingIds.forEach { (provId, rowId) ->
            if (provId !in currentProviderIds) {
                resolver.delete(TvContractCompat.buildWatchNextProgramUri(rowId), null, null)
            }
        }
    }

    private fun syncSeerflixChannel(items: List<UnifiedMediaItem>) {
        if (items.isEmpty()) return
        val resolver = context.contentResolver
        val channelId = getOrCreateSeerflixChannelId() ?: return

        // Replace channel preview programs cleanly
        resolver.delete(
            TvContractCompat.buildPreviewProgramsUriForChannel(channelId),
            null,
            null
        )

        items.take(15).forEachIndexed { index, item ->
            val program = buildPreviewProgram(channelId, item, items.size - index)
            resolver.insert(
                TvContractCompat.PreviewPrograms.CONTENT_URI,
                program.toContentValues()
            )
        }
    }

    private fun getOrCreateSeerflixChannelId(): Long? {
        val resolver = context.contentResolver
        resolver.query(
            TvContractCompat.Channels.CONTENT_URI,
            arrayOf(
                TvContractCompat.Channels._ID,
                TvContractCompat.Channels.COLUMN_DISPLAY_NAME
            ),
            null,
            null,
            null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(TvContractCompat.Channels._ID)
            val nameIdx = cursor.getColumnIndex(TvContractCompat.Channels.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                if (nameIdx >= 0 && cursor.getString(nameIdx) == CHANNEL_NAME && idIdx >= 0) {
                    return cursor.getLong(idIdx)
                }
            }
        }

        val channel = Channel.Builder()
            .setType(TvContractCompat.Channels.TYPE_PREVIEW)
            .setDisplayName(CHANNEL_NAME)
            .setAppLinkIntentUri(Uri.parse("seerflix://home"))
            .build()

        val uri = resolver.insert(TvContractCompat.Channels.CONTENT_URI, channel.toContentValues())
            ?: return null
        val channelId = uri.lastPathSegment?.toLongOrNull() ?: return null
        runCatching { TvContractCompat.requestChannelBrowsable(context, channelId) }
        return channelId
    }

    companion object {
        private const val CHANNEL_NAME = "Seerflix"

        fun buildPlayerDeepLink(jellyfinId: String): Uri =
            Uri.parse("seerflix://player/$jellyfinId")

        fun buildDetailsDeepLink(item: UnifiedMediaItem): Uri {
            val tmdb = item.tmdbId ?: 0
            val jf = item.jellyfinId ?: "none"
            return Uri.parse("seerflix://details/$tmdb/$jf/${item.mediaType.name}")
        }

        fun buildWatchNextProgram(item: UnifiedMediaItem): WatchNextProgram? {
            val jfId = item.jellyfinId ?: return null
            val type = when (item.mediaType) {
                MediaType.MOVIE -> TvContractCompat.WatchNextPrograms.TYPE_MOVIE
                MediaType.EPISODE -> TvContractCompat.WatchNextPrograms.TYPE_TV_EPISODE
                MediaType.SERIES -> TvContractCompat.WatchNextPrograms.TYPE_TV_SERIES
            }
            val builder = WatchNextProgram.Builder()
                .setType(type)
                .setWatchNextType(TvContractCompat.WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE)
                .setLastEngagementTimeUtcMillis(System.currentTimeMillis())
                .setTitle(item.title)
                .setDescription(item.overview.take(200))
                .setIntentUri(buildPlayerDeepLink(jfId))
                .setInternalProviderId(jfId)

            (item.backdropUrl ?: item.posterUrl)?.let { art ->
                builder.setPosterArtUri(Uri.parse(art))
                builder.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9)
            }
            if (item.runtimeTicks > 0L) {
                builder.setDurationMillis((item.runtimeTicks / 10_000L).toInt())
                builder.setLastPlaybackPositionMillis((item.resumePositionTicks / 10_000L).toInt())
            }
            return builder.build()
        }

        fun buildPreviewProgram(
            channelId: Long,
            item: UnifiedMediaItem,
            weight: Int
        ): PreviewProgram {
            val type = when (item.mediaType) {
                MediaType.MOVIE -> TvContractCompat.PreviewPrograms.TYPE_MOVIE
                MediaType.EPISODE -> TvContractCompat.PreviewPrograms.TYPE_TV_EPISODE
                MediaType.SERIES -> TvContractCompat.PreviewPrograms.TYPE_TV_SERIES
            }
            val builder = PreviewProgram.Builder()
                .setChannelId(channelId)
                .setType(type)
                .setTitle(item.title)
                .setDescription(item.overview.take(200))
                .setIntentUri(buildDetailsDeepLink(item))
                .setInternalProviderId(item.id)
                .setWeight(weight)

            (item.posterUrl ?: item.backdropUrl)?.let { art ->
                builder.setPosterArtUri(Uri.parse(art))
                builder.setPosterArtAspectRatio(TvContractCompat.PreviewPrograms.ASPECT_RATIO_2_3)
            }
            return builder.build()
        }
    }
}
