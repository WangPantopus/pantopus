@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling._shared

import androidx.compose.runtime.Composable
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Price formatting for `price_cents`/`currency` + the paid-surface gate.
 *
 * Paid surfaces (priced event types, packages, invoices, payouts, Stripe
 * checkout) call [PaidGate] with `SchedulingFeatureFlags.paidSchedulingEnabled`
 * so they vanish in production until payout settlement ships.
 */
object MoneyAndFlag {
    /**
     * Format minor units to a localized currency string. `null`/`0` → "Free".
     * Falls back to "<amount> <code>" if the currency code is unknown.
     */
    fun formatPrice(
        priceCents: Int?,
        currency: String? = DEFAULT_CURRENCY,
    ): String {
        if (priceCents == null || priceCents <= 0) return "Free"
        val code = currency?.takeIf { it.isNotBlank() }?.uppercase() ?: DEFAULT_CURRENCY
        val amount = priceCents / CENTS_PER_UNIT
        return runCatching {
            NumberFormat.getCurrencyInstance(Locale.US).apply {
                this.currency = Currency.getInstance(code)
            }.format(amount)
        }.getOrElse { "%.2f %s".format(amount, code) }
    }

    private const val DEFAULT_CURRENCY = "USD"
    private const val CENTS_PER_UNIT = 100.0
}

/**
 * Renders [content] only when paid scheduling is [enabled]; otherwise renders
 * [fallback] (nothing by default). The caller passes
 * `SchedulingFeatureFlags.paidSchedulingEnabled`.
 */
@Composable
fun PaidGate(
    enabled: Boolean,
    fallback: @Composable () -> Unit = {},
    content: @Composable () -> Unit,
) {
    if (enabled) content() else fallback()
}
