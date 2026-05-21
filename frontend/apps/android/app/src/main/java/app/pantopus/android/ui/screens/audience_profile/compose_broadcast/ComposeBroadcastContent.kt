@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.audience_profile.compose_broadcast

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.screens.identity_center.IdentityKind
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * A.7 (A22.2) Compose Broadcast — render-only models for the full-screen
 * broadcast composer pushed from the Audience Profile. Mirrors the iOS
 * `ComposeBroadcastContent.swift` shape so cross-platform parity tests can
 * compare projections one-to-one. No backend: seeded from
 * [ComposeBroadcastSampleData].
 */

/** Targeting for a broadcast — "All beacons" public reach down to tier locks. */
enum class BroadcastAudience(
    val key: String,
    val title: String,
    val icon: PantopusIcon,
    /** Persona tier rank for chip color via `tierColor(rank)`; null = public. */
    val tierRank: Int?,
) {
    AllBeacons("allBeacons", "All beacons", PantopusIcon.Globe, null),
    FollowersOnly("followersOnly", "Followers only", PantopusIcon.Users, 1),
    BronzePlus("bronzePlus", "Bronze+", PantopusIcon.Lock, 2),
    SilverPlus("silverPlus", "Silver+", PantopusIcon.Lock, 3),
    GoldOnly("goldOnly", "Gold only", PantopusIcon.Lock, 4),
    ;

    val isRestricted: Boolean get() = this != AllBeacons
}

/** One attached media item. Rendered as a tinted placeholder + caption. */
@Immutable
data class ComposeMediaPreview(
    val id: String,
    val kind: Kind,
    val caption: String?,
) {
    enum class Kind { Image, Video }
}

/** The mutable composer payload — what would be POSTed on send. */
@Immutable
data class ComposeBroadcastDraft(
    val body: String = "",
    val audience: BroadcastAudience = BroadcastAudience.AllBeacons,
    val media: ComposeMediaPreview? = null,
) {
    /** Nothing worth sending — empty body and no media. */
    val isEmpty: Boolean get() = body.isBlank() && media == null
}

/** The persona a broadcast is sent as — drives the composer PersonaRow. */
@Immutable
data class BroadcastPersona(
    val id: String,
    val handle: String,
    val displayName: String,
    val kind: IdentityKind,
    val avatarInitial: String,
)

/** One recent broadcast with inline analytics (pre-formatted strings). */
@Immutable
data class RecentBroadcastContent(
    val id: String,
    val timeLabel: String,
    val audience: BroadcastAudience,
    val body: String,
    val reach: String,
    val read: String,
    val readPct: String,
    val reactions: String,
    val replies: String,
    val hasMedia: Boolean,
)

/** Send lifecycle, kept separate from the editable draft. */
sealed interface ComposePhase {
    data object Idle : ComposePhase

    data object Sending : ComposePhase

    data class Error(val message: String) : ComposePhase
}

/**
 * The prompt's composer-state contract, derived from the live draft +
 * [ComposePhase]. Mirrors the iOS `ComposeBroadcastState` enum so parity
 * tests line up.
 */
sealed interface ComposeBroadcastState {
    data object Empty : ComposeBroadcastState

    data class Composing(val draft: ComposeBroadcastDraft) : ComposeBroadcastState

    data class Scheduled(val draft: ComposeBroadcastDraft, val sendAt: Long) : ComposeBroadcastState

    data object Sending : ComposeBroadcastState

    data class Error(val message: String) : ComposeBroadcastState
}

/**
 * Full UI state the screen renders from. The editor is always present;
 * `phase` + the draft drive [composeState] and the screen chrome.
 */
@Immutable
data class ComposeBroadcastUiState(
    val persona: BroadcastPersona,
    val recentBroadcasts: List<RecentBroadcastContent>,
    val draft: ComposeBroadcastDraft,
    val scheduledAtMillis: Long?,
    val scheduledLabel: String?,
    val phase: ComposePhase,
    val isDirty: Boolean,
    val maxCharacterCount: Int = 1_000,
    val audienceReach: Map<BroadcastAudience, Int> = emptyMap(),
) {
    val characterCount: Int get() = draft.body.length
    val isOverLimit: Boolean get() = characterCount > maxCharacterCount
    val hasRecentBroadcasts: Boolean get() = recentBroadcasts.isNotEmpty()
    val isSending: Boolean get() = phase == ComposePhase.Sending

    val canSend: Boolean
        get() = phase != ComposePhase.Sending && !draft.isEmpty && !isOverLimit

    val primaryActionTitle: String
        get() = if (hasRecentBroadcasts) "Send broadcast" else "Send your first broadcast"

    fun reach(audience: BroadcastAudience): Int? = audienceReach[audience]

    fun composeState(): ComposeBroadcastState =
        when (val current = phase) {
            ComposePhase.Sending -> ComposeBroadcastState.Sending
            is ComposePhase.Error -> ComposeBroadcastState.Error(current.message)
            ComposePhase.Idle ->
                when {
                    scheduledAtMillis != null -> ComposeBroadcastState.Scheduled(draft, scheduledAtMillis)
                    draft.isEmpty -> ComposeBroadcastState.Empty
                    else -> ComposeBroadcastState.Composing(draft)
                }
        }
}
