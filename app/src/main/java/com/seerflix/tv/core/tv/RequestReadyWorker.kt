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

        repo.refreshLibraryIndex(activeProfile.jellyfinUserId)
        val requests = repo.loadRequestsHub().getOrNull() ?: return Result.success()

        val previouslyTracked = prefs.getTrackedPendingRequestIds()
        val currentlyPendingOrProcessing = requests
            .filter { it.mediaStatus == AvailabilityTier.PENDING || it.mediaStatus == AvailabilityTier.PROCESSING }
            .map { it.requestId }
            .toSet()

        val newlyAvailable = requests.filter { req ->
            req.mediaStatus.isInLibrary && req.requestId in previouslyTracked
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
            alerts.forEach { tvSync.promoteNewReleaseToWatchNext(it) }
        }

        prefs.saveTrackedPendingRequestIds(currentlyPendingOrProcessing)
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "seerflix_request_ready_poll"
    }
}
