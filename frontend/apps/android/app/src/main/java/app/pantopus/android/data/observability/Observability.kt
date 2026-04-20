package app.pantopus.android.data.observability

import android.content.Context
import app.pantopus.android.BuildConfig
import io.sentry.Breadcrumb
import io.sentry.Sentry
import io.sentry.SentryLevel
import io.sentry.android.core.SentryAndroid
import io.sentry.android.timber.SentryTimberIntegration
import io.sentry.protocol.User
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for crash reporting, error capture, and analytics.
 *
 * Call [start] from [app.pantopus.android.PantopusApplication.onCreate]; every other
 * piece of the app goes through this class so the underlying backend (Sentry, Firebase, …)
 * can be swapped without touching call sites.
 *
 * DSN comes from `BuildConfig.SENTRY_DSN` — if empty, Sentry is not initialised and
 * everything no-ops (useful for local dev and CI).
 */
@Singleton
class Observability
    @Inject
    constructor() {
        private var started = false

        fun start(context: Context) {
            if (started) return
            started = true

            val dsn = BuildConfig.SENTRY_DSN
            if (dsn.isBlank()) {
                Timber.i("Sentry DSN not set — crash reporting disabled")
                return
            }

            SentryAndroid.init(context) { options ->
                options.dsn = dsn
                options.environment = BuildConfig.PANTOPUS_ENV
                options.release = "app.pantopus.android@${BuildConfig.VERSION_NAME}+${BuildConfig.VERSION_CODE}"
                options.isEnableAutoSessionTracking = true
                options.tracesSampleRate = if (BuildConfig.PANTOPUS_ENV == "production") 0.1 else 1.0
                options.isAttachScreenshot = false
                options.isAttachViewHierarchy = false
                options.isSendDefaultPii = false
                // Route Timber.e/w into Sentry breadcrumbs + events automatically.
                options.addIntegration(
                    SentryTimberIntegration(
                        minEventLevel = SentryLevel.ERROR,
                        minBreadcrumbLevel = SentryLevel.INFO,
                    ),
                )
            }
            Timber.i("Sentry started (env=${BuildConfig.PANTOPUS_ENV})")
        }

        fun capture(throwable: Throwable) {
            Timber.e(throwable)
            if (started) Sentry.captureException(throwable)
        }

        fun capture(
            message: String,
            level: SentryLevel = SentryLevel.WARNING,
        ) {
            Timber.log(level.toTimberPriority(), message)
            if (started) Sentry.captureMessage(message, level)
        }

        fun identify(
            userId: String?,
            email: String? = null,
        ) {
            if (!started) return
            if (userId == null) {
                Sentry.setUser(null)
            } else {
                Sentry.setUser(
                    User().apply {
                        this.id = userId
                        this.email = email
                    },
                )
            }
        }

        /** Lightweight analytics — structured log + Sentry breadcrumb. */
        fun track(
            name: String,
            properties: Map<String, String> = emptyMap(),
        ) {
            Timber.i("analytics event=$name props=$properties")
            if (!started) return
            val crumb =
                Breadcrumb().apply {
                    category = "analytics"
                    message = name
                    level = SentryLevel.INFO
                    properties.forEach { (k, v) -> setData(k, v) }
                }
            Sentry.addBreadcrumb(crumb)
        }
    }

private fun SentryLevel.toTimberPriority(): Int =
    when (this) {
        SentryLevel.DEBUG -> android.util.Log.DEBUG
        SentryLevel.INFO -> android.util.Log.INFO
        SentryLevel.WARNING -> android.util.Log.WARN
        SentryLevel.ERROR, SentryLevel.FATAL -> android.util.Log.ERROR
    }
