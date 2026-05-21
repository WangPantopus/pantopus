package app.pantopus.android.ui.util

/**
 * P6.6 — shared invite copy used by "Share ...", "Invite ...", and
 * "Find people" surfaces.
 */
object InviteLinks {
    /**
     * Public download landing page — single source of truth. Swap for the
     * real App Store / Play Store smart-link when it ships.
     */
    const val DOWNLOAD_URL = "https://pantopus.app"

    const val INVITE_MESSAGE =
        "Join me on Pantopus — your neighborhood for trusted home help, " +
            "local gigs, and your whole household in one place. $DOWNLOAD_URL"
}
