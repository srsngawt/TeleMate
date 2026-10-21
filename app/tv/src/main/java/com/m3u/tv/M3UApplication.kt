package com.m3u.tv

import android.app.Application
import android.graphics.Bitmap
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.m3u.data.worker.ExtensionPluginBootstrapWorker
import com.m3u.data.worker.PersistedUriPermissionCleanupWorker
import com.m3u.data.worker.ProviderCredentialRecoveryWorker
import com.m3u.data.worker.ProviderSessionCleanupWorker
import com.m3u.data.worker.initializePersistedUriPermissionLeases
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class M3UApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        initializePersistedUriPermissionLeases(this)
        PersistedUriPermissionCleanupWorker.enqueueRecovery(
            WorkManager.getInstance(this)
        )
        ProviderCredentialRecoveryWorker.enqueue(WorkManager.getInstance(this))
        ProviderSessionCleanupWorker.enqueue(
            workManager = WorkManager.getInstance(this),
        )
        ExtensionPluginBootstrapWorker.enqueue(WorkManager.getInstance(this))
    }

    override val workManagerConfiguration: Configuration by lazy {
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .bitmapConfig(Bitmap.Config.RGB_565)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.15)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .crossfade(false)
            .build()
    }
}
