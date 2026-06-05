@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.contentdetail

import app.pantopus.android.data.api.models.payments.PaymentIntentSheetParamsDto

/**
 * Block 3B — checkout types for the invoice "Pay" CTA. The view-model creates
 * a PaymentIntent (`POST /api/payments/intent`) and emits an
 * [InvoiceDetailEvent.PresentCheckout]; the screen presents Stripe's
 * PaymentSheet (it needs the Activity's `ActivityResultRegistry`) and reports
 * the outcome back. We never mark the invoice paid here — the VM re-reads
 * server state on success (webhooks reconcile the `Payment`).
 */

/**
 * What the buyer is paying for. The server owns the real amount and payee; the
 * client passes only the order reference.
 */
data class CheckoutRequest(
    val gigId: String? = null,
    val listingId: String? = null,
    val offerId: String? = null,
    val description: String? = null,
)

/** Where the "Pay" CTA currently sits, so the screen can surface the right toast. */
sealed interface InvoicePaymentStatus {
    data object Idle : InvoicePaymentStatus

    data object Paying : InvoicePaymentStatus

    data object Paid : InvoicePaymentStatus

    data object Canceled : InvoicePaymentStatus

    data class Declined(val message: String) : InvoicePaymentStatus
}

/** One-shot effects the [InvoiceDetailViewModel] asks the screen to perform. */
sealed interface InvoiceDetailEvent {
    /** Present Stripe PaymentSheet for the created PaymentIntent. */
    data class PresentCheckout(val params: PaymentIntentSheetParamsDto) : InvoiceDetailEvent
}
