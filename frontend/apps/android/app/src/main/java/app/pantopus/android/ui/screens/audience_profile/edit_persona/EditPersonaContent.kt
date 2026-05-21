@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.audience_profile.edit_persona

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * A13.12 — Edit persona. Render models for the creator-facing persona editor
 * (the underlying editor for a Public Profile / persona, distinct from the
 * T3.3 Audience Profile management hub). Mirrors the iOS
 * `EditPersonaContent.swift` shape so cross-platform parity holds.
 *
 * Persona accent: sky / `primary600`, flat. The design source renders a
 * fuchsia gradient hero, but there is no fuchsia/persona token in the design
 * system (only Personal / Home / Business pillars), and every shipped persona
 * surface (Audience Profile, Broadcast detail, Membership) uses the sky
 * primary. We mirror that — no new tokens, no gradient.
 */

/** Which frame the editor renders. */
enum class EditPersonaVariant { Live, Setup }

/** Live availability of the chosen handle — drives the inline status pill. */
enum class PersonaHandleStatus { Reserved, Available, Taken }

/** One content-category chip in the policy block. */
@Immutable
data class PersonaCategoryChip(
    val label: String,
    val icon: PantopusIcon,
)

/** One row in the SETUP checklist hero. */
@Immutable
data class PersonaChecklistStep(
    val id: String,
    val label: String,
    val done: Boolean,
    val isNext: Boolean = false,
)

/**
 * A creator-side tier card. Deliberately named to disambiguate from the
 * fan-side `MembershipTier` in A10.8 — this is the *configuration* card a
 * creator edits, not the membership a fan holds.
 */
@Immutable
data class PersonaTierCard(
    val id: String,
    val name: String,
    val kind: Kind,
    val priceLabel: String? = null,
    val period: String? = null,
    val blurb: String,
    val perks: List<String> = emptyList(),
    val stripeState: StripeState = StripeState.None,
    val isFresh: Boolean = false,
) {
    enum class Kind { Free, Paid, PaidLocked }

    enum class StripeState { None, Ready, NeedsStripe }
}

/** Stripe Connect onboarding state for the persona. */
sealed interface PersonaStripeState {
    data class Connected(val account: String) : PersonaStripeState

    data object NotConnected : PersonaStripeState
}

/**
 * Posting-cap segmented options. Order + labels mirror the design's
 * `CapSelector` (1/wk · 3/wk · Daily · Unlimited).
 */
enum class PersonaCapOption(val label: String) {
    Weekly1("1/wk"),
    Weekly3("3/wk"),
    Daily("Daily"),
    Unlimited("Unlimited"),
}

/**
 * Composed editor content shared by both frames. Frame-specific emphasis
 * (live hero stats vs. setup checklist) is selected by the enclosing
 * [EditPersonaUiState] case; unused fields simply aren't rendered.
 */
@Immutable
data class EditPersonaContent(
    val personaId: String,
    val handle: String,
    val displayName: String,
    val bio: String,
    val bioCharCount: String,
    val handleStatus: PersonaHandleStatus,
    val handleNote: String? = null,
    // Live hero stat strip.
    val followers: String = "",
    val posts: String = "",
    val rating: String = "",
    val liveBadge: String = "Live",
    // Setup checklist hero.
    val checklist: List<PersonaChecklistStep> = emptyList(),
    val checklistSummary: String = "",
    // Category policy.
    val categoriesAllow: List<PersonaCategoryChip>,
    val categoriesAllowSub: String,
    val categoriesOff: List<PersonaCategoryChip>,
    val categoriesOffSub: String,
    val policyNote: String? = null,
    // Tiers.
    val stripe: PersonaStripeState,
    val tiers: List<PersonaTierCard>,
    val canAddTier: Boolean,
    // Broadcast.
    val cap: PersonaCapOption,
    val quietHoursOn: Boolean,
    val quietHoursRange: String,
    // Share.
    val shareUrl: String,
    val shareIsPublic: Boolean,
    // Analytics.
    val analyticsOn: Boolean,
    val analyticsScope: List<String>,
) {
    /** `@handle` for the top-bar subtitle + share row. */
    val atHandle: String get() = "@$handle"
}

/**
 * Top-level editor state. `Live` and `Setup` carry the same content shape;
 * `Setup` adds the checklist progress counters consumed by the hero's
 * progress bar.
 */
sealed interface EditPersonaUiState {
    data object Loading : EditPersonaUiState

    data class Live(val content: EditPersonaContent) : EditPersonaUiState

    data class Setup(
        val content: EditPersonaContent,
        val stepsDone: Int,
        val stepsTotal: Int,
    ) : EditPersonaUiState

    data class Error(val message: String) : EditPersonaUiState
}
