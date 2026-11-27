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

        // One cheap call first: most runs find nothing new, and then neither the library index
        // nor 50 per-title Seerr lookups are needed.
        val statuses = repo.loadRequestStatuses().getOrNull() ?: return Result.success()

        val previouslyTracked = prefs.getTrackedPendingRequestIds()
        val currentlyPendingOrProcessing = statuses
            .filter { it.mediaStatus == AvailabilityTier.PENDING || it.mediaStatus == AvailabilityTier.PROCESSING }
            .map { it.requestId }
            .toSet()
        val newlyAvailableIds = statuses
            .filter { it.mediaStatus.isInLibrary && it.requestId in previouslyTracked }
            .map { it.requestId }
            .toSet()

        val newlyAvailable = if (newlyAvailableIds.isEmpty()) emptyList() else {
            // With the profile: an index rebuilt without it carries no rating cap.
            repo.refreshLibraryIndex(activeProfile.jellyfinUserId, activeProfile = activeProfile)
            repo.loadRequestsHub(onlyRequestIds = newlyAvailableIds).getOrNull()
                ?: return Result.success()
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
            if (!activeProfile.isRestricted) alerts.forEach { tvSync.promoteNewReleaseToWatchNext(it) }
        }

        prefs.saveTrackedPendingRequestIds(currentlyPendingOrProcessing)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "seerflix_request_ready_poll"
    }
}
