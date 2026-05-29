@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.feed

import app.pantopus.android.ui.theme.PantopusIcon

/**
 * The A03 feed archetype renders two surfaces from one screen: Pulse (the
 * public neighborhood feed, `surface=place`) and Beacon Updates (broadcasts
 * from verified beacons the user follows, `surface=personas`).
 * Design ref: docs/designs/A03 — feed-frames.jsx (A03.1) + beacons-frames.jsx
 * (A03.2). They share chrome, chip row, card recipe, FAB, and tab bar; only
 * the title, backend surface, verified-floor, and empty state differ.
 */
enum class FeedSurface(
    val title: String,
    /** Backend `surface` query value sent on `/api/posts/feed`. */
    val backendSurface: String,
    /**
     * Beacons are verified people / businesses / civic accounts, so every
     * author on that surface carries the verified check disc (A03.2).
     */
    val authorsAlwaysVerified: Boolean,
) {
    /** A03.1 — public neighborhood feed (`surface=place`). */
    Pulse(title = "Pulse", backendSurface = "place", authorsAlwaysVerified = false),

    /** A03.2 — beacon broadcasts (`surface=personas`). */
    Beacons(title = "Beacon Updates", backendSurface = "personas", authorsAlwaysVerified = true),
    ;

    /**
     * Build the empty-state descriptor for this surface.
     *
     * @param scopeLabel Active neighborhood (Pulse footer). `null` hides the
     *   Pulse footer chip.
     * @param followCount Beacons followed (Beacons footer).
     */
    fun emptyContent(
        scopeLabel: String?,
        followCount: Int,
    ): FeedEmptyContent =
        when (this) {
            Pulse ->
                FeedEmptyContent(
                    icon = PantopusIcon.Radio,
                    headline = "No posts yet",
                    body = "Be the first to share. Ask a question, recommend a spot, or announce something local.",
                    ctaLabel = "Create post",
                    ctaIcon = PantopusIcon.Pencil,
                    footerIcon = PantopusIcon.MapPin,
                    footerLead = "Showing posts within ",
                    footerEmphasis = scopeLabel,
                    footerTrail = " · change in filter",
                )
            Beacons ->
                FeedEmptyContent(
                    icon = PantopusIcon.Rss,
                    headline = "Follow a beacon to see updates here",
                    body =
                        "Beacons are verified people, businesses, and civic accounts you can follow. " +
                            "Their posts land in this feed only.",
                    ctaLabel = "Discover beacons",
                    ctaIcon = PantopusIcon.Compass,
                    footerIcon = PantopusIcon.Users,
                    footerLead = "You follow ",
                    footerEmphasis = "$followCount beacons",
                    footerTrail = " · suggestions nearby",
                )
        }
}

/**
 * Render descriptor for a feed empty state. The footer chip reads
 * [footerLead] + bold [footerEmphasis] + [footerTrail]; it is hidden when
 * [footerEmphasis] is `null`.
 */
data class FeedEmptyContent(
    val icon: PantopusIcon,
    val headline: String,
    val body: String,
    val ctaLabel: String,
    val ctaIcon: PantopusIcon,
    val footerIcon: PantopusIcon,
    val footerLead: String,
    val footerEmphasis: String?,
    val footerTrail: String,
)
