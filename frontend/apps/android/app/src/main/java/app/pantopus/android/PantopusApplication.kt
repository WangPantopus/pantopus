package app.pantopus.android

import android.app.Application
import app.pantopus.android.data.observability.Observability
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application class. Initialises Hilt DI, logging, and Sentry.
 *
 * Registered in AndroidManifest.xml via `android:name=".PantopusApplication"`.
 */
@HiltAndroidApp
class PantopusApplication : Application() {
    @Inject lateinit var observability: Observability

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // Sentry's Timber integration handles error forwarding in release builds —
        // see Observability.start(). No extra tree needed.
        observability.start(this)
    }
}
