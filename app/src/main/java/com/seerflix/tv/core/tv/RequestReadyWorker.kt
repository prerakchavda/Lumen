package com.seerflix.tv.core.tv

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.seerflix.tv.core.data.UnifiedCatalogRepository
import com.seerflix.tv.core.model.AvailabilityTier
import com.seerflix.tv.core.model.ReadyRequestAlert
import com.seerflix.tv.core.network.SessionPreferences
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

class RequestReadyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun preferences(): SessionPreferences
        fun catalogRepository(): UnifiedCatalogRepository
        fun tvChannelSyncManager(): TvChannelSyncManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            WorkerEntryPoint::class.java
        )
        val prefs = entryPoint.preferences()
        prefs.warmup()
        if (!prefs.isSeerrConfigured) return Result.success()

        val activeProfile = prefs.activeProfileFlow.first() ?: return Result.success()
        val repo = entryPoint.catalogRepository()
        val tvSync = entryPoint.tvChannelSyncManager()

        // With the profile: an index rebuilt without it carries no rating cap.
        repo.refreshLibraryIndex(activeProfile.jellyfinUserId, activeProfile = activeProfile)
        val requests = repo.loadRequestsHub().getOrNull() ?: return Result.success()

        val previouslyTracked = prefs.getTrackedPendingRequestIds()
        val previouslySeen = prefs.getSeenRequestIds()
        val currentlyPendingOrProcessing = requests
            .filter { it.mediaStatus == AvailabilityTier.PENDING || it.mediaStatus == AvailabilityTier.PROCESSING }
            .map { it.requestId }
            .toSet()

        // Pending at the last check and in the library now, or requested *and* fulfilled since
        // the last check (a movie that downloads in minutes never shows up as pending here).
        val newlyAvailable = requests.filter { req ->
            req.mediaStatus.isInLibrary && (
                req.requestId in previouslyTracked ||
                    (previouslySeen != null && req.requestId !in previouslySeen)
                )
        }

        if (newlyAvailable.isNotEmpty()) {
            val alerts = newlyAvailable.map { req ->
                val jfId = repo.findJellyfinIdForTmdb(req.tmdbId, req.mediaType)
                ReadyRequestAlert(
                    requestId = req.requestId,
                    tmdbId = req.tmdbId,
                    jellyfinId = jfId,
                    mediaType = req.mediaType,
                    title = req.title,
                    posterUrl = req.posterUrl,
                    timestampMs = System.currentTimeMillis()
                )
            }
            prefs.addReadyRequestAlerts(alerts)
            // The launcher's Watch Next row is shared by every profile: never put another
            // member's request there while a restricted (Kids / rating-limited) profile is active.
            // A PIN-locked profile's titles stay off it too (the launcher needs no PIN).
            if (!activeProfile.isRestricted && !activeProfile.hasPin) {
                alerts.forEach { tvSync.promoteNewReleaseToWatchNext(it) }
            }
        }

        prefs.saveTrackedPendingRequestIds(currentlyPendingOrProcessing)
        // Remembered across runs (not just this page) so requests sliding into the 50-item window
        // from further back aren't mistaken for new ones.
        prefs.saveSeenRequestIds(((previouslySeen ?: emptySet()) + requests.map { it.requestId }).takeLastIds())
        return Result.success()
    }

    private fun Set<Int>.takeLastIds(max: Int = 2_000): Set<Int> =
        if (size <= max) this else sortedDescending().take(max).toSet()

    companion object {
        const val WORK_NAME = "seerflix_request_ready_poll"
    }
}
