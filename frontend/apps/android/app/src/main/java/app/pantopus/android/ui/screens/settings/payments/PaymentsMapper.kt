@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.payments

import app.pantopus.android.data.api.models.payments.PaymentMethodDto

/**
 * Projects backend payment DTOs into the A14.6 render models, and builds
 * the live Payments frame. Phase 3 (3A) wires the methods card only; the
 * balance hero / Payouts (Stripe Connect) / Activity sections render an
 * honest "not set up yet" scaffold until 3C wires them — we never
 * fabricate balances.
 */
object PaymentsMapper {
    fun liveFrame(methods: List<PaymentMethod>): PaymentsLoaded =
        PaymentsLoaded(
            balance = null,
            methods = methods,
            payouts = notConnectedPayouts,
            activity =
                PaymentsActivity.Empty(
                    title = "No transactions yet",
                    body = "Hires and sales will appear here.",
                ),
            canCloseAccount = false,
            footerCaption = "Payments are processed securely by Stripe.",
        )

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
                    subtext = "Available after Stripe connect",
                    trailing = PaymentsRowTrailing.GatedDash,
                ),
            payoutSchedule = null,
            taxInfo =
                PaymentsPayoutRow(
                    id = "payouts.tax",
                    label = "Tax info",
                    subtext = "Available after Stripe connect",
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
