package app.pantopus.android.ui.screens.compose.pulse

/**
 * Purpose-driven post types for step 2. Maps to backend `purpose` +
 * `postType` — aligned with `@pantopus/ui-utils` PURPOSE_TO_POST_TYPE.
 */
enum class PulseComposePurpose(
    val key: String,
    val label: String,
    val postType: String,
    val apiPurpose: String,
) {
    Ask("ask", "Ask", "ask_local", "ask"),
    HeadsUp("heads_up", "Heads Up", "alert", "heads_up"),
    Recommend("recommend", "Recommend", "recommendation", "recommend"),
    LostFound("lost_found", "Lost & Found", "lost_found", "lost_found"),
    LocalUpdate("local_update", "Local Update", "local_update", "local_update"),
    NeighborhoodWin("neighborhood_win", "Neighborhood Win", "neighborhood_win", "neighborhood_win"),
    VisitorGuide("visitor_guide", "Visitor Guide", "visitor_guide", "visitor_guide"),
    Event("event", "Event", "event", "event"),
    Deal("deal", "Deal", "deal", "deal"),
    ;

    val legacyIntent: PulseComposeIntent
        get() =
            when (this) {
                Ask -> PulseComposeIntent.Ask
                Recommend -> PulseComposeIntent.Recommend
                Event -> PulseComposeIntent.Event
                LostFound -> PulseComposeIntent.Lost
                HeadsUp, LocalUpdate, NeighborhoodWin, VisitorGuide, Deal -> PulseComposeIntent.Announce
            }

    companion object {
        fun allowedFor(target: PulsePostingTarget): List<PulseComposePurpose> {
            if (target.isNetworkTarget) return emptyList()

            val placeTypes =
                setOf(
                    "ask_local", "recommendation", "event", "lost_found", "alert", "deal",
                    "local_update", "neighborhood_win", "visitor_guide",
                )
            val businessTypes = setOf("event", "deal", "local_update")
            val allowedPostTypes =
                when (target) {
                    is PulsePostingTarget.Business -> businessTypes
                    else -> placeTypes
                }

            return entries.filter { allowedPostTypes.contains(it.postType) }
        }
    }
}
