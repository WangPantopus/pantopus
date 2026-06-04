@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.settings.payments

import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult

/**
 * Shared Stripe PaymentSheet helpers reused across Phase 3 (3A–3D). Builds
 * the [PaymentSheet.Configuration] from the server-supplied customer +
 * ephemeral key, and maps Stripe's [PaymentSheetResult] into the SDK-free
 * [AddCardOutcome] the view-model consumes.
 *
 * We never build card-entry UI — PaymentSheet collects the card, handles
 * SCA/3-D Secure, and reports the result.
 */
object StripePaymentSheets {
    private const val MERCHANT_DISPLAY_NAME = "Pantopus"

    fun configuration(
        customerId: String,
        ephemeralKey: String,
    ): PaymentSheet.Configuration =
        PaymentSheet.Configuration(
            merchantDisplayName = MERCHANT_DISPLAY_NAME,
            customer =
                PaymentSheet.CustomerConfiguration(
                    id = customerId,
                    ephemeralKeySecret = ephemeralKey,
                ),
        )

    fun outcome(result: PaymentSheetResult): AddCardOutcome =
        when (result) {
            is PaymentSheetResult.Completed -> AddCardOutcome.Completed
            is PaymentSheetResult.Canceled -> AddCardOutcome.Canceled
            is PaymentSheetResult.Failed -> AddCardOutcome.Failed(result.error.message)
        }
}
