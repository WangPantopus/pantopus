package app.pantopus.android.ui.screens.root

import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Typed bottom-bar destination. Exposes both the NavController `path` used
 * by the NavHost and the label / icon that render in [PantopusBottomBar].
 *
 * Listed in display order — new tabs must keep the ordering stable so the
 * bar doesn't shuffle between releases.
 */
sealed class PantopusRoute(
    val path: String,
    val label: String,
    val icon: PantopusIcon,
) {
    /** Primary hub — the personalised landing surface (Prompt P7). */
    data object Hub : PantopusRoute(path = "root/hub", label = "Hub", icon = PantopusIcon.Home)

    /** Nearby discovery — gigs / people / businesses in your radius. */
    data object Nearby : PantopusRoute(path = "root/nearby", label = "Nearby", icon = PantopusIcon.Map)

    /** Mailbox drawer list. */
    data object Inbox : PantopusRoute(path = "root/inbox", label = "Inbox", icon = PantopusIcon.Inbox)

    /** Account & settings. */
    data object You : PantopusRoute(path = "root/you", label = "You", icon = PantopusIcon.User)

    companion object {
        /** Bottom-bar destinations in display order. */
        val entries: List<PantopusRoute> = listOf(Hub, Nearby, Inbox, You)

        /** Lookup a route by its `path`. Returns null for unknown paths. */
        fun fromPath(path: String?): PantopusRoute? = entries.firstOrNull { it.path == path }
    }
}
