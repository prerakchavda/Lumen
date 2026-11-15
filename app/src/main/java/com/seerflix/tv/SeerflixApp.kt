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
import coil3.bitmapFactoryMaxParallelism
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.allowRgb565
import coil3.request.crossfade
import com.seerflix.tv.core.network.MediaClient
import com.seerflix.tv.core.tv.RequestReadyWorker
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
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
                // Percent of the app's heap class (~192-256 MB on 2 GB Google TV sticks) => ~35-45 MB,
                // i.e. a few hundred display-size RGB_565 posters.
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
            .components {
                // Route image loads through the client that honours the "trust self-signed
                // certificates" setting so Jellyfin artwork loads on self-signed HTTPS servers.
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = {
                            EntryPointAccessors.fromApplication(
                                context.applicationContext,
                                ImageClientEntryPoint::class.java
                            ).mediaClient()
                        }
                    )
                )
            }
            // Opaque JPEG posters/backdrops decode as RGB_565 (half the memory of ARGB_8888); on
            // API 29+ Coil maps this to ImageDecoder's low-RAM policy, which still yields
            // hardware bitmaps, so there is no texture upload on the RenderThread while scrolling.
            .allowRgb565(true)
            // Row cards swap artwork constantly; a crossfade would add an extra animated layer per card.
            .crossfade(false)
            // Default is 4 concurrent decodes. On a quad-core A55 stick that starves the UI thread and
            // RenderThread while a new row scrolls in; 2 keeps D-pad scrolling smooth and display-size
            // decodes are small enough that throughput is barely affected.
            .bitmapFactoryMaxParallelism(2)
            .build()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ImageClientEntryPoint {
        @MediaClient
        fun mediaClient(): OkHttpClient
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

