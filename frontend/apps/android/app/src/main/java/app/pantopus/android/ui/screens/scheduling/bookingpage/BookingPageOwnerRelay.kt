@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.bookingpage

import app.pantopus.android.data.scheduling.SchedulingOwner
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stream-local relay that passes the resolved [SchedulingOwner] from the
 * navigation entry-point into the arg-less Booking-page screens
 * (BookingPageManage, PublicPagePreview).
 *
 * The navigation graph (RootTabScreen) sets [pendingOwner] before navigating;
 * each ViewModel's `init` calls [consume] once and clears it. A null value
 * (deep-link / process-death / first-party Personal navigation that doesn't
 * explicitly set an owner) falls back to [SchedulingOwner.Personal].
 */
@Singleton
class BookingPageOwnerRelay
    @Inject
    constructor() {
        /** Owner context for the next pushed booking-page screen. */
        var pendingOwner: SchedulingOwner? = null

        /** Read-and-clear the pending owner. Returns null if no owner was set. */
        fun consume(): SchedulingOwner? {
            val value = pendingOwner
            pendingOwner = null
            return value
        }
    }
