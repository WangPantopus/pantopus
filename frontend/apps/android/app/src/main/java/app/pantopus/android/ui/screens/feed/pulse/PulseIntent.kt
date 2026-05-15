@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.feed.pulse

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Six-way classification for Pulse posts. Drives the chip-row filter,
 * the per-card colored chip, the reaction-verb set, and the compose
 * FAB's pre-fill. `All` is a chip-row-only sentinel; real posts always
 * resolve to one of the other five.
 */
enum class PulseIntent(
    val key: String,
    val label: String,
    val cardChipLabel: String,
) {
    All(key = "all", label = "All", cardChipLabel = ""),
    Ask(key = "ask", label = "Ask", cardChipLabel = "Ask"),
    Recommend(key = "recommend", label = "Recommend", cardChipLabel = "Rec"),
    Event(key = "event", label = "Event", cardChipLabel = "Event"),
    Lost(key = "lost", label = "Lost & Found", cardChipLabel = "Lost"),
    Announce(key = "announce", label = "Announce", cardChipLabel = "Announce"),
    ;

    /** Backend `post_type` filter for `/api/posts/feed`. `All` is `null`. */
    val postType: String?
        get() =
            when (this) {
                All -> null
                Ask -> "ask_local"
                Recommend -> "recommendation"
                Event -> "event"
                Lost -> "lost_found"
                Announce -> "local_update"
            }

    /** Icon used inside the per-card intent chip. */
    val icon: PantopusIcon
        get() =
            when (this) {
                All -> PantopusIcon.Info
                Ask -> PantopusIcon.HelpCircle
                Recommend -> PantopusIcon.ThumbsUp
                Event -> PantopusIcon.Calendar
                Lost -> PantopusIcon.Search
                Announce -> PantopusIcon.Megaphone
            }

    companion object {
        fun fromKey(key: String): PulseIntent = entries.firstOrNull { it.key == key } ?: All

        /**
         * Resolve a backend `post_type` to a UI intent. Unknown values
         * fall through to `Announce` (most generic) so the card still
         * renders a meaningful indicator.
         */
        fun fromPostType(postType: String?): PulseIntent =
            when (postType ?: "") {
                "ask_local", "ask" -> Ask
                "recommendation", "recommend" -> Recommend
                "event" -> Event
                "lost_found" -> Lost
                "local_update", "announcement", "heads_up", "neighborhood_win" -> Announce
                else -> Announce
            }
    }
}

/**
 * One reaction kind shown in the bottom strip of a post card. The
 * backend only persists `like` (helpful); the other counts are
 * display-only and intent-shaped to match the design.
 */
@Immutable
data class PulseReaction(
    val kind: Kind,
    val icon: PantopusIcon,
    val label: String,
    val count: Int,
    val isInteractive: Boolean,
) {
    enum class Kind(val key: String) {
        Helpful("helpful"),
        Heart("heart"),
        Going("going"),
        Seen("seen"),
        Shared("shared"),
    }
}

/**
 * Returns the reaction strip the design specifies for this intent.
 * The first kind is wired to `POST /:id/like`; the rest are
 * display-only counts.
 */
fun PulseIntent.reactionTemplate(
    helpfulCount: Int,
    secondaryCount: Int = 0,
): List<PulseReaction> =
    when (this) {
        PulseIntent.Ask ->
            listOf(
                PulseReaction(PulseReaction.Kind.Helpful, PantopusIcon.Lightbulb, "helpful", helpfulCount, true),
                PulseReaction(PulseReaction.Kind.Heart, PantopusIcon.Heart, "", secondaryCount, false),
            )
        PulseIntent.Recommend ->
            listOf(
                PulseReaction(PulseReaction.Kind.Helpful, PantopusIcon.Heart, "", helpfulCount, true),
                PulseReaction(PulseReaction.Kind.Heart, PantopusIcon.Lightbulb, "helpful", secondaryCount, false),
            )
        PulseIntent.Event ->
            listOf(
                PulseReaction(PulseReaction.Kind.Going, PantopusIcon.Check, "going", helpfulCount, true),
                PulseReaction(PulseReaction.Kind.Heart, PantopusIcon.Heart, "", secondaryCount, false),
            )
        PulseIntent.Lost ->
            listOf(
                PulseReaction(PulseReaction.Kind.Seen, PantopusIcon.Eye, "seen", helpfulCount, true),
                PulseReaction(PulseReaction.Kind.Shared, PantopusIcon.Share, "shared", secondaryCount, false),
            )
        PulseIntent.Announce, PulseIntent.All ->
            listOf(
                PulseReaction(PulseReaction.Kind.Helpful, PantopusIcon.Lightbulb, "helpful", helpfulCount, true),
                PulseReaction(PulseReaction.Kind.Heart, PantopusIcon.Heart, "", secondaryCount, false),
            )
    }
