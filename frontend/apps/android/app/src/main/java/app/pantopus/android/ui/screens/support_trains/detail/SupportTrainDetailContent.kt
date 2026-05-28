@file:Suppress("PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.support_trains.detail

import app.pantopus.android.ui.components.SlotCalendarDay

/**
 * A10.9 — Render payloads for the participant-facing Support Train
 * detail screen. Pure value types so the view-model can be fed
 * deterministic stub data ([SupportTrainDetailSampleData]) and every
 * state snapshots reproducibly. Colour is expressed as a semantic
 * [SupportTrainKind]; the screen maps it onto `PantopusColors` so the
 * model stays free of UI types.
 *
 * Two designed variants share this model:
 *   - `populated`     12 / 21 slots covered · 9 open · `signUp` dock.
 *   - `fullyCovered`  21 / 21 covered · celebration banner at top ·
 *                     split `Send a card` / `Join as backup` dock.
 */

/**
 * Per-archetype palette. Drives the type-dates card icon tile + the
 * recipient avatar gradient. Mirrors the iOS `SupportTrainKind` so
 * the list-feed projection lands the same accent on both platforms.
 */
enum class SupportTrainKind { Meals, Rides, Childcare, Petcare, Errands, Visits, Generic }

/** Identity tag for the recipient card (drives the chip + verified disc + avatar gradient). */
enum class RecipientIdentityTag { Home, Personal, Business }

/** Semantic palette swatch for contributor / author discs. */
enum class ContributorTone { Warning, Primary, Business, Success, Error, Personal }

/** Sticky bottom-dock variant. */
sealed interface SupportTrainDock {
    data class SignUp(val label: String) : SupportTrainDock

    data object SendCardAndBackup : SupportTrainDock
}

/** Slot-row presentation state. */
enum class SlotRowState { Open, Covered }

/** A bubble inside the contributor strip (4 avatars + +N overflow). */
data class ContributorBubble(
    val id: String,
    val initials: String,
    val tone: ContributorTone,
)

/** Helper attribution on a covered slot row. */
data class SlotRowAuthor(
    val initials: String,
    val displayName: String,
    val tone: ContributorTone,
)

/** "For" overline + recipient card payload. */
data class RecipientCardContent(
    val initials: String,
    val householdName: String,
    val identityTag: RecipientIdentityTag,
    val verified: Boolean,
    val address: String,
    val proximity: String?,
    val quote: String,
    val quoteAttribution: String?,
)

/** "The train" overline + type-dates card payload. */
data class TypeDatesCardContent(
    val kind: SupportTrainKind,
    val title: String,
    val dateRange: String,
    val daysLeft: Int,
    val slotsFilled: Int,
    val slotsTotal: Int,
    val contributors: List<ContributorBubble>,
    val extraCount: Int,
) {
    val isFullyCovered: Boolean get() = slotsTotal > 0 && slotsFilled >= slotsTotal

    val percentCovered: Int
        get() = if (slotsTotal <= 0) 0 else Math.round(slotsFilled.toFloat() * 100f / slotsTotal.toFloat())
}

/** One slot row, used by both open + covered + mine variants. */
data class SlotRowContent(
    val id: String,
    val dayLabel: String,
    val dateLabel: String,
    val state: SlotRowState,
    val author: SlotRowAuthor? = null,
    val title: String,
    val subtitle: String? = null,
    val mine: Boolean = false,
)

/** Organizer footer pinned at the bottom of the body. */
data class HostedByFooter(
    val organizerInitials: String,
    val organizerDisplayName: String,
    val neighborHint: String?,
)

/**
 * One stack of slot rows ("Open slots near you", "Already on the
 * train", "Your commitment", "Next up"). Optional action label
 * surfaces as a trailing `See all N` button.
 */
data class SlotSection(
    val id: String,
    val overline: String,
    val actionLabel: String? = null,
    val rows: List<SlotRowContent>,
)

/** Celebration banner shown above the body in the fully-covered variant. */
data class CelebrationBanner(
    val title: String,
    val body: String,
)

/** Full render payload for the participant-facing Support Train detail. */
data class SupportTrainDetailContent(
    val trainId: String,
    val recipient: RecipientCardContent,
    val typeDates: TypeDatesCardContent,
    /** 28 days in row-major order (week 0 Mon…Sun … week 3 Mon…Sun). */
    val calendarDays: List<SlotCalendarDay>,
    val sections: List<SlotSection>,
    val hostedBy: HostedByFooter,
    val dock: SupportTrainDock,
    val celebrationBanner: CelebrationBanner? = null,
) {
    val isFullyCovered: Boolean get() = typeDates.isFullyCovered
}

/**
 * Mirrors the iOS [SupportTrainDetailViewModel.State] enum. Four-state
 * contract: loading / loaded / error. Fully-covered is **not** empty
 * — it's a celebrated loaded variant — so the state machine has no
 * `Empty` case.
 */
sealed interface SupportTrainDetailUiState {
    data object Loading : SupportTrainDetailUiState

    data class Loaded(val content: SupportTrainDetailContent) : SupportTrainDetailUiState

    data class Error(val message: String) : SupportTrainDetailUiState
}
