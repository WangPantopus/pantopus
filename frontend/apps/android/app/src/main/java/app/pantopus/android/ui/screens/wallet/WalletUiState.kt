@file:Suppress("PackageNaming", "MatchingDeclarationName", "LongParameterList")

package app.pantopus.android.ui.screens.wallet

/**
 * A10.10 — render payloads for the Wallet screen. Pure value types so
 * the view-model can be fed deterministic stub data ([WalletSampleData])
 * and every state snapshots reproducibly. Colour is a semantic
 * [ActivityCategory] enum; the screen maps cases → `PantopusColors`.
 */

/** Activity-row category — drives the per-row icon tile colour + glyph. */
enum class ActivityCategory {
    Cleaning,
    ChildCare,
    Handyman,
    PetCare,
    Bank,
    Fee,
}

/** Direction of money flow for an activity row. */
enum class ActivityDirection {
    In,
    Out,
}

/**
 * Clearing status for a single row. The view renders a trailing label
 * ("Cleared" / "On hold" / "Payout" / "Fee") and optionally an amber
 * "Pending" chip beside the description for [Pending].
 */
sealed interface ActivityStatus {
    /** Earned and cleared — counts toward the available balance. */
    data object Available : ActivityStatus

    /**
     * Earned but still in escrow. [clearsLabel] is the user-facing
     * "clears Dec 4" sub-line copy.
     */
    data class Pending(val clearsLabel: String) : ActivityStatus

    /** Already-settled outbound payout or fee. */
    data object Complete : ActivityStatus
}

/** Single transaction row inside the Recent activity card. */
data class WalletActivityItem(
    val id: String,
    val day: String,
    val dateLabel: String,
    val description: String,
    val counterparty: String,
    val category: ActivityCategory,
    val direction: ActivityDirection,
    val status: ActivityStatus,
    /**
     * Pre-formatted amount string without the leading sign or "$".
     * Example `"140.00"` — the row renders `+$140.00` / `−$2.40` based
     * on [direction].
     */
    val amount: String,
    /**
     * `true` for the service-fee row — switches the trailing label to
     * "Fee" and uses the neutral fee category tint.
     */
    val isFee: Boolean = false,
)

/**
 * Payout-method card payload. The view renders a debit-card-shaped
 * `CHASE` tile plus the meta line; [warn] = `true` flips the card to
 * the amber re-verify state.
 */
data class WalletPayoutMethod(
    val bankLabel: String,
    val last4: String,
    /**
     * Body line rendered under the bank label. In the default state
     * the view prepends the green `Zap` flash icon; in the warn state
     * it prepends the amber `AlertCircle`.
     */
    val bodyText: String,
    val warn: Boolean,
)

/**
 * Tax-docs row payload. [ready] lights up the home-green icon tile +
 * `New` chip + "1099-NEC ready" body. Otherwise the row renders the
 * neutral grey YTD line.
 */
data class WalletTaxDocs(
    val ready: Boolean,
    val bodyText: String,
)

/**
 * Hold-state payload — populated only in the [Hold] state. Drives the
 * amber top banner above the BalanceHero and the locked Withdraw CTA
 * footnote at the bottom.
 */
data class WalletHoldState(
    val bannerHeadline: String,
    val bannerBody: String,
    /** Compact one-line summary surfaced inside the BalanceHero's
     *  inset amber strip ("Re-verify your bank to release funds."). */
    val heroBannerHeadline: String,
    val heroBannerBody: String,
    /** Centred footnote under the locked Withdraw CTA. */
    val withdrawFootnote: String,
)

/** Top-level Wallet render payload. */
data class WalletContent(
    /** Pre-formatted available balance — e.g. `"847.50"`. */
    val available: String,
    val pending: String,
    val pendingMeta: String,
    val monthValue: String,
    val monthMeta: String,
    val activity: List<WalletActivityItem>,
    val payoutMethod: WalletPayoutMethod,
    val taxDocs: WalletTaxDocs,
    /** Populated only in the [Hold] state. */
    val holdState: WalletHoldState? = null,
) {
    val isOnHold: Boolean get() = holdState != null
}

/**
 * Four-state machine: loading / populated / hold / error. Matches iOS
 * `WalletViewModel.State`.
 */
sealed interface WalletUiState {
    data object Loading : WalletUiState

    data class Populated(val content: WalletContent) : WalletUiState

    data class Hold(val content: WalletContent) : WalletUiState

    data class Error(val message: String) : WalletUiState
}
