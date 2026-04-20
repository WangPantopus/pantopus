package app.pantopus.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber

/**
 * Application class. Initialises Hilt DI and logging.
 *
 * Registered in AndroidManifest.xml via `android:name=".PantopusApplication"`.
 */
@HiltAndroidApp
class PantopusApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else {
            // Release builds: plant a Timber tree that forwards to Crashlytics
            // or whatever crash/log collector is wired up. No-op for now.
        }
    }
}
