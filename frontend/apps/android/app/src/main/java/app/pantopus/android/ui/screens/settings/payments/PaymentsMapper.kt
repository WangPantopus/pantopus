@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.payments

import app.pantopus.android.data.api.models.payments.PaymentHistoryEntryDto
import app.pantopus.android.data.api.models.payments.PaymentMethodDto
import java.text.NumberFormat
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Projects backend payment DTOs into the A14.6 render models, and builds the
 * live Payments frame: the saved-methods card, the Stripe Connect entry
 * point, and the real transaction history from `GET api/payments/history`.
 * Balances are never fabricated — Wallet owns the earnings-in surface.
 * Mirrors iOS `PaymentsViewModel`.
 */
@Suppress("TooManyFunctions")
object PaymentsMapper {
    private val emptyActivity =
        PaymentsActivity.Empty(
            title = "No transactions yet",
            body = "Hires and sales will appear here.",
        )

    fun liveFrame(
        methods: List<PaymentMethod>,
        activity: PaymentsActivity = emptyActivity,
    ): PaymentsLoaded =
        PaymentsLoaded(
            balance = null,
            methods = methods,
            payouts = notConnectedPayouts,
            activity = activity,
            canCloseAccount = false,
            footerCaption = "Payments are processed securely by Stripe.",
        )

    /**
     * Project `GET api/payments/history` rows onto the Activity card. An
     * empty feed keeps the genuine empty state.
     */
    fun activity(entries: List<PaymentHistoryEntryDto>): PaymentsActivity =
        if (entries.isEmpty()) emptyActivity else PaymentsActivity.Transactions(entries.map(::transaction))

    /**
     * One history row → one Activity row. Mirrors RN `HistoryTab`: payouts and
     * debits read as money out, credits as money in, tips get the star.
     */
    fun transaction(entry: PaymentHistoryEntryDto): PaymentsTransaction {
        val isPayout = entry.entryType == "payout"
        val isTip = entry.paymentType == "tip"
        val isOutgoing =
            isPayout ||
                entry.direction?.lowercase(Locale.US) == "debit" ||
                (entry.direction == null && entry.isSender == true)
        val kind =
            when {
                isTip -> PaymentsTransaction.Kind.Tip
                isPayout -> PaymentsTransaction.Kind.Payout
                isOutgoing -> PaymentsTransaction.Kind.Sent
                else -> PaymentsTransaction.Kind.Received
            }
        return PaymentsTransaction(
            id = entry.id,
            kind = kind,
            title = transactionTitle(entry, isPayout),
            meta = transactionMeta(entry, isPayout, isOutgoing),
            amount = (if (isOutgoing) "-" else "+") + centsToCurrency(entry.amountCents),
            isOutgoing = isOutgoing,
        )
    }

    private fun transactionTitle(
        entry: PaymentHistoryEntryDto,
        isPayout: Boolean,
    ): String {
        if (isPayout) {
            entry.destinationLast4?.takeIf { it.isNotEmpty() }?.let { return "Payout to bank ••••$it" }
            return entry.description ?: "Payout"
        }
        entry.gig?.title?.takeIf { it.isNotEmpty() }?.let { return it }
        entry.description?.takeIf { it.isNotEmpty() }?.let { return it }
        entry.paymentType?.takeIf { it.isNotEmpty() }?.let { return humanised(it) }
        return "Payment"
    }

    private fun transactionMeta(
        entry: PaymentHistoryEntryDto,
        isPayout: Boolean,
        isOutgoing: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        shortDate(entry.createdAt)?.let { parts += it }
        entry.status?.takeIf { it.isNotEmpty() }?.let { parts += humanised(it) }
        if (!isPayout) {
            val counterparty = if (isOutgoing) entry.payee?.displayName else entry.payer?.displayName
            if (counterparty != null) {
                parts += if (isOutgoing) "to $counterparty" else "from $counterparty"
            }
        }
        return parts.joinToString(" · ")
    }

    /** `gig_payment` → `Gig payment`, `authorize_pending` → `Authorize pending`. */
    private fun humanised(raw: String): String = raw.replace('_', ' ').replaceFirstChar(Char::uppercase)

    private val centsFormat: NumberFormat =
        NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
            isGroupingUsed = true
        }

    /**
     * Integer cents → `"$1,284.50"`. Formatting only — the server's amount is
     * never re-derived or rounded.
     */
    fun centsToCurrency(cents: Int): String = "$" + centsFormat.format(kotlin.math.abs(cents) / 100.0)

    private val shortDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.US)

    private fun shortDate(raw: String?): String? {
        if (raw.isNullOrEmpty()) return null
        val instant =
            runCatching { Instant.parse(raw) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(raw).toInstant() }.getOrNull()
                ?: return null
        return shortDateFormat.format(instant.atZone(ZoneId.systemDefault()))
    }

    fun toUiMethod(dto: PaymentMethodDto): PaymentMethod {
        val isBank =
            dto.paymentMethodType == "us_bank_account" ||
                (dto.cardBrand == null && dto.bankLast4 != null)
        val last4 = if (isBank) dto.bankLast4 ?: "••••" else dto.cardLast4 ?: "••••"
        val name = if (isBank) dto.bankName ?: "Bank account" else cardName(dto.cardBrand)
        val subtext =
            when {
                isBank -> dto.bankAccountType?.let { "${it.replaceFirstChar(Char::uppercase)} account" }
                dto.cardExpMonth != null && dto.cardExpYear != null ->
                    "Expires %02d/%02d".format(dto.cardExpMonth, dto.cardExpYear % 100)
                else -> null
            }
        return PaymentMethod(
            id = dto.id,
            brand = if (isBank) PaymentMethodBrand.Bank else brandOf(dto.cardBrand),
            label = "$name •• $last4",
            subtext = subtext,
            chip = if (dto.isDefault) PaymentMethodChip("Default", PaymentsChipTone.Primary) else null,
        )
    }

    private fun brandOf(cardBrand: String?): PaymentMethodBrand =
        when (cardBrand?.lowercase()) {
            "visa" -> PaymentMethodBrand.Visa
            "mastercard" -> PaymentMethodBrand.Mastercard
            "amex", "american_express" -> PaymentMethodBrand.Amex
            else -> PaymentMethodBrand.Card
        }

    private fun cardName(cardBrand: String?): String =
        when (val brand = cardBrand?.lowercase()) {
            "visa" -> "Visa"
            "mastercard" -> "Mastercard"
            "amex", "american_express" -> "Amex"
            null, "" -> "Card"
            else -> brand.replaceFirstChar(Char::uppercase)
        }

    private val notConnectedPayouts =
        PaymentsPayouts(
            stripe =
                PaymentsPayoutRow(
                    id = "payouts.stripe",
                    leadingBrand = PaymentMethodBrand.Stripe,
                    label = "Stripe Connect",
                    subtext = "Receive payments from neighbors",
                    trailing = PaymentsRowTrailing.CtaChip("Connect", PaymentsChipTone.Primary),
                ),
            payoutMethod =
                PaymentsPayoutRow(
                    id = "payouts.method",
                    label = "Payout method",
                    subtext = "Add after connecting Stripe",
                    trailing = PaymentsRowTrailing.GatedDash,
                ),
            payoutSchedule = null,
            taxInfo =
                PaymentsPayoutRow(
                    id = "payouts.tax",
                    label = "Tax info",
                    subtext = "W-9 collected during setup",
                    trailing = PaymentsRowTrailing.GatedDash,
                ),
            helper = "Required before you can post paid tasks or sell on Marketplace.",
        )
}

/** Optimistic transform: mark [id] as the sole default-chipped method. */
fun PaymentsLoaded.markingDefault(id: String): PaymentsLoaded =
    copy(
        methods =
            methods.map { method ->
                method.copy(
                    chip = if (method.id == id) PaymentMethodChip("Default", PaymentsChipTone.Primary) else null,
                )
            },
    )

/** Optimistic transform: drop the method with [id]. */
fun PaymentsLoaded.removingMethod(id: String): PaymentsLoaded = copy(methods = methods.filterNot { it.id == id })
