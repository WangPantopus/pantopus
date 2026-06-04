@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.settings.payments

import androidx.compose.runtime.Immutable

/**
 * Render models for A14.6 Payments (Settings → Payments). This is the
 * payments-OUT surface — cards on file · Stripe Connect setup · payout
 * routing — distinct from A10.10 Wallet which surfaces earnings-IN.
 * Mirrors `docs/designs/A14/payments-frames.jsx`: balance hero + three
 * grouped cards (Payment methods · Payouts · Activity) + an optional
 * destructive Close-account card.
 */

/** Top-level state for the Payments screen. */
sealed interface PaymentsUiState {
    data object Loading : PaymentsUiState

    data class Loaded(val content: PaymentsLoaded) : PaymentsUiState

    data class Error(val message: String) : PaymentsUiState
}

/** Loaded projection of the Payments screen. */
@Immutable
data class PaymentsLoaded(
    /** Hero card — `null` on the empty-account frame (nothing to surface). */
    val balance: PaymentsBalance?,
    /**
     * Saved payment methods (cards / wallets / bank). Empty list → the
     * methods card renders an inline empty hero above the Add row.
     */
    val methods: List<PaymentMethod>,
    /** Stripe Connect row + payout method row + tax row. */
    val payouts: PaymentsPayouts,
    /**
     * Populated has 3 stat rows (lifetime · YTD · last payout); empty
     * collapses to one muted "No transactions yet" row.
     */
    val activity: PaymentsActivity,
    /** Gates the "Close payment account" destructive card. */
    val canCloseAccount: Boolean,
    /** Monospaced footer caption rendered below the destructive card. */
    val footerCaption: String,
)

/** Balance hero — A14.6's compact `BalanceHero` payout variant. */
@Immutable
data class PaymentsBalance(
    val overline: String,
    val amount: String,
    val nextPayoutLabel: String,
    val frequencyPill: String,
)

/** One saved payment method row. */
@Immutable
data class PaymentMethod(
    val id: String,
    val brand: PaymentMethodBrand,
    val label: String,
    val subtext: String? = null,
    val chip: PaymentMethodChip? = null,
)

/** Brand badge variants for `PaymentMethodRow`. */
enum class PaymentMethodBrand {
    Visa,
    Mastercard,
    Amex,
    ApplePay,
    Bank,
    Stripe,

    /**
     * Generic card mark for brands without a bespoke badge (Discover, JCB,
     * Diners, UnionPay, …) so real saved cards always render.
     */
    Card,
}

/** Small status chip rendered before the trailing chevron. */
@Immutable
data class PaymentMethodChip(
    val label: String,
    val tone: PaymentsChipTone,
)

/** Chip color tones — mirrors `RowControl.ChipTone`. */
enum class PaymentsChipTone {
    Primary,
    Success,
    Neutral,
}

/** Payouts card content (Stripe row + 2–3 sibling rows). */
@Immutable
data class PaymentsPayouts(
    val stripe: PaymentsPayoutRow,
    val payoutMethod: PaymentsPayoutRow,
    val payoutSchedule: PaymentsPayoutRow? = null,
    val taxInfo: PaymentsPayoutRow,
    val helper: String? = null,
)

/** One row inside the Payouts card. */
@Immutable
data class PaymentsPayoutRow(
    val id: String,
    val leadingBrand: PaymentMethodBrand? = null,
    val label: String,
    val subtext: String? = null,
    val trailing: PaymentsRowTrailing,
)

/** Trailing affordance vocabulary for `PaymentsPayoutRow`. */
sealed interface PaymentsRowTrailing {
    data object Chevron : PaymentsRowTrailing

    @Immutable
    data class ChipChevron(val label: String, val tone: PaymentsChipTone) : PaymentsRowTrailing

    @Immutable
    data class CtaChip(val label: String, val tone: PaymentsChipTone) : PaymentsRowTrailing

    /** Lock glyph + em-dash — empty frame's payout-method / tax rows. */
    data object GatedDash : PaymentsRowTrailing
}

/** Activity card content. */
sealed interface PaymentsActivity {
    @Immutable
    data class Stats(val rows: List<PaymentsActivityStat>) : PaymentsActivity

    @Immutable
    data class Empty(val title: String, val body: String) : PaymentsActivity
}

/** One row inside the activity card. */
@Immutable
data class PaymentsActivityStat(
    val id: String,
    val label: String,
    val subtext: String? = null,
)
