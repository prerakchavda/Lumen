package com.seerflix.tv.core.tv

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.BaseColumns
import com.seerflix.tv.core.data.AuthRepository
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

class SeerflixSearchProvider : ContentProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SearchProviderEntryPoint {
        fun authRepository(): AuthRepository
        fun catalogRepository(): UnifiedCatalogRepository
    }

    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val cursor = MatrixCursor(COLUMNS)
        val rawQuery = selectionArgs?.firstOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.takeIf { it != SearchManager.SUGGEST_URI_PATH_QUERY }
            ?: return cursor

        val ctx = context ?: return cursor
        val results = runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                ctx.applicationContext,
                SearchProviderEntryPoint::class.java
            )
            runBlocking {
                withTimeoutOrNull(3_500L) {
                    val authRepository = entryPoint.authRepository()
                    // Cold process (system search): make sure server URLs/token are loaded first.
                    authRepository.warmup()
                    val profile = authRepository.activeProfileFlow.first()
                        ?: return@withTimeoutOrNull emptyList()
                    // A PIN-locked profile's titles must not show up in the system search without
                    // the PIN (the app itself opens on the profile picker for it).
                    if (profile.hasPin) return@withTimeoutOrNull emptyList()
                    // Global search must honour the active profile's parental controls.
                    entryPoint.catalogRepository().searchUnified(
                        userId = profile.jellyfinUserId,
                        query = rawQuery.trim(),
                        activeProfile = profile
                    )
                }.orEmpty()
            }
        }.getOrDefault(emptyList())

        results.take(15).forEachIndexed { idx, item ->
            val subtitle = buildString {
                append(if (item.mediaType.name == "MOVIE") "Movie" else "TV Series")
                item.releaseYear?.let { append(" • $it") }
                if (item.isPlayable) append(" • Available")
            }
            val deepLink = TvChannelSyncManager.buildDetailsDeepLink(item).toString()
            cursor.addRow(
                arrayOf<Any?>(
                    idx.toLong(),
                    item.title,
                    subtitle,
                    item.posterUrl ?: item.backdropUrl,
                    Intent.ACTION_VIEW,
                    deepLink
                )
            )
        }
        return cursor
    }

    override fun getType(uri: Uri): String = SearchManager.SUGGEST_MIME_TYPE

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    companion object {
        const val AUTHORITY = "com.seerflix.tv.search"

        private val COLUMNS = arrayOf(
            BaseColumns._ID,
            SearchManager.SUGGEST_COLUMN_TEXT_1,
            SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE,
            SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA
        )
    }
}
