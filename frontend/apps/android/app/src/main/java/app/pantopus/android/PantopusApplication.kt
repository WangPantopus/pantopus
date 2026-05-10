package app.pantopus.android

import android.app.Application
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.observability.Observability
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * Application class. Initialises Hilt DI, logging, Sentry, and the
 * process-wide Coil image cache (P13 perf budget).
 *
 * Registered in AndroidManifest.xml via `android:name=".PantopusApplication"`.
 */
@HiltAndroidApp
class PantopusApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var observability: Observability

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Sentry's Timber integration handles error forwarding in release builds —
        // see Observability.start(). No extra tree needed.
        observability.start(this)
        Analytics.bind(observability)
    }

    /**
     * Coil image-loader factory. Caches:
     *   - 15 % of available app memory (~30–60 MB on a Pixel 6)
     *   - 2 % of available disk under `cacheDir/image_cache` (~100 MB)
     *
     * Sized per the P13 budget (`docs/perf_budgets.md`). One process-wide
     * loader keeps avatar / discovery / mailbox imagery from re-decoding
     * during fast scrolls.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(IMAGE_CACHE_MEMORY_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "image_cache"))
                    .maxSizePercent(IMAGE_CACHE_DISK_PERCENT)
                    .build()
            }
            .crossfade(true)
            .build()

    private companion object {
        const val IMAGE_CACHE_MEMORY_PERCENT = 0.15
        const val IMAGE_CACHE_DISK_PERCENT = 0.02
    }
}
