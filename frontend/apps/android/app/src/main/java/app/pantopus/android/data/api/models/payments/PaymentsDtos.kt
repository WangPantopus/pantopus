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
