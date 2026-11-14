package com.seerflix.tv

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.seerflix.tv.core.tv.RequestReadyWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit

@HiltAndroidApp
class SeerflixApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        scheduleRequestReadyWorker()
    }

    override fun newImageLoader(context: Context): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.18)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache"))
                    .maxSizeBytes(80L * 1024 * 1024)
                    .build()
            }
            .allowRgb565(true)
            .crossfade(false)
            .build()
    }

    private fun scheduleRequestReadyWorker() {
        runCatching {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val work = PeriodicWorkRequestBuilder<RequestReadyWorker>(4, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                RequestReadyWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                work
            )
        }
    }
}

