package app.pantopus.android.data.api.models.payments

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for the Stripe payment-methods surface (`backend/routes/pays.js`,
 * mounted at `/api/payments`). Phase 3 (3A) wires the Settings → Payments
 * methods card. Connect / payout / checkout DTOs land with 3B/3C/3D.
 */

// GET /api/payments/methods — backend/routes/pays.js:701
@JsonClass(generateAdapter = true)
data class PaymentMethodsResponse(
    val paymentMethods: List<PaymentMethodDto> = emptyList(),
)

/**
 * One row from the `PaymentMethod` table. Columns are snake_case; a card
 * row carries `card_*`, a bank row carries `bank_*`.
 */
@JsonClass(generateAdapter = true)
data class PaymentMethodDto(
    val id: String,
    @Json(name = "payment_method_type") val paymentMethodType: String? = null,
    @Json(name = "card_brand") val cardBrand: String? = null,
    @Json(name = "card_last4") val cardLast4: String? = null,
    @Json(name = "card_exp_month") val cardExpMonth: Int? = null,
    @Json(name = "card_exp_year") val cardExpYear: Int? = null,
    @Json(name = "bank_name") val bankName: String? = null,
    @Json(name = "bank_last4") val bankLast4: String? = null,
    @Json(name = "bank_account_type") val bankAccountType: String? = null,
    @Json(name = "is_default") val isDefault: Boolean = false,
)

/**
 * POST /api/payments/payment-sheet-add-card — backend/routes/pays.js:1095.
 * SetupIntent params for the mobile PaymentSheet "add a card" flow. Keys
 * are already camelCase server-side.
 */
@JsonClass(generateAdapter = true)
data class AddCardSheetParamsDto(
    val setupIntent: String,
    val ephemeralKey: String,
    val customer: String,
    val publishableKey: String? = null,
)

/** Generic `{ message }` ack returned by set-default / remove. */
@JsonClass(generateAdapter = true)
data class PaymentMethodAckResponse(
    val message: String? = null,
)

/**
 * Body for `POST /api/payments/intent` (Block 3B checkout). The server
 * computes/validates the amount and creates the PaymentIntent for the
 * referenced order. Keys are camelCase to match `createPaymentSchema`.
 */
@JsonClass(generateAdapter = true)
data class CreatePaymentIntentRequest(
    val payeeId: String,
    val amount: Int,
    val gigId: String? = null,
    val listingId: String? = null,
    val offerId: String? = null,
    val description: String? = null,
)

/**
 * Response from `POST /api/payments/intent` — the params the mobile
 * PaymentSheet needs to present a charge. `customer` + `ephemeralKey` are
 * best-effort (the sheet still works card-only without them). The shape is a
 * superset of the gig bid-accept payment payload so the same checkout flow can
 * present either. Keys are camelCase server-side.
 */
@JsonClass(generateAdapter = true)
data class PaymentIntentSheetParamsDto(
    val clientSecret: String? = null,
    val paymentIntentId: String? = null,
    val customer: String? = null,
    val ephemeralKey: String? = null,
    val publishableKey: String? = null,
    val isSetupIntent: Boolean? = null,
)
