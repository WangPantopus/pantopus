@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.gigs

import app.pantopus.android.data.api.models.payments.PaymentIntentSheetParamsDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Phase 5 — gig detail & lifecycle DTOs: counter-offers, instant accept,
 * worker acknowledgement, no-show, moderation reports, and the owner
 * cancellation fee preview.
 */

/** Body for `POST /api/gigs/:gigId/bids/:bidId/counter` (`backend/routes/gigs.js:5097`). */
@JsonClass(generateAdapter = true)
data class CounterBidBody(
    val amount: Double,
    val message: String? = null,
)

/**
 * Envelope shared by the bid lifecycle mutations: counter (`{ bid }`),
 * counter accept / decline (`{ bid }`), and reject (`{ message }`).
 */
@JsonClass(generateAdapter = true)
data class GigBidMutationResponse(
    val bid: GigBidDto? = null,
    val message: String? = null,
)

/**
 * Response from `POST /api/gigs/:gigId/instant-accept`
 * (`backend/routes/gigsV2.js:64`). Paid gigs initialise the poster's
 * payment up-front; `payment.clientSecret` is present when the backend
 * wants a PaymentSheet pass before work can start.
 */
@JsonClass(generateAdapter = true)
data class GigInstantAcceptResponse(
    val message: String? = null,
    val gig: GigDto? = null,
    val paymentRequired: Boolean? = null,
    val requiresPaymentSetup: Boolean? = null,
    val isSetupIntent: Boolean? = null,
    val payment: GigBidAcceptResponse.PaymentPayload? = null,
    val publishableKey: String? = null,
    val ephemeralKey: String? = null,
    val customer: String? = null,
) {
    fun sheetParams(): PaymentIntentSheetParamsDto =
        PaymentIntentSheetParamsDto(
            clientSecret = payment?.clientSecret,
            paymentIntentId = payment?.paymentIntentId,
            customer = customer,
            ephemeralKey = ephemeralKey,
            publishableKey = publishableKey,
            isSetupIntent = isSetupIntent,
        )
}

/**
 * Body for `POST /api/gigs/:gigId/worker-ack`
 * (`backend/routes/gigs.js:5838`). `status` is `starting_now` or
 * `running_late`; `eta_minutes` (1..480) only applies to the latter.
 */
@JsonClass(generateAdapter = true)
data class WorkerAckBody(
    val status: String,
    @Json(name = "eta_minutes") val etaMinutes: Int? = null,
    val note: String? = null,
)

/** Response from the worker-ack endpoint. */
@JsonClass(generateAdapter = true)
data class WorkerAckResponse(
    val success: Boolean? = null,
    @Json(name = "worker_ack_status") val workerAckStatus: String? = null,
    val message: String? = null,
)

/**
 * Response from `GET /api/gigs/:gigId/no-show-check`
 * (`backend/routes/gigs.js:7720`). Gate for the "Report no-show"
 * affordance; the extra fields explain the verdict.
 */
@JsonClass(generateAdapter = true)
data class NoShowCheckResponse(
    @Json(name = "can_report") val canReport: Boolean? = null,
    val reason: String? = null,
    @Json(name = "expected_start") val expectedStart: String? = null,
    @Json(name = "can_report_after") val canReportAfter: String? = null,
    @Json(name = "minutes_overdue") val minutesOverdue: Int? = null,
    @Json(name = "hours_since_accept") val hoursSinceAccept: Int? = null,
)

/**
 * Body for `POST /api/gigs/:gigId/report-no-show`
 * (`backend/routes/gigs.js:7572`). Cancels the gig with a no-show
 * incident; `evidence_urls` are optional uploaded proof links.
 */
@JsonClass(generateAdapter = true)
data class ReportNoShowBody(
    val description: String? = null,
    @Json(name = "evidence_urls") val evidenceUrls: List<String>? = null,
)

/** Response from the report-no-show endpoint (`{ message, gig }`). */
@JsonClass(generateAdapter = true)
data class ReportNoShowResponse(
    val message: String? = null,
    val gig: GigDto? = null,
)

/**
 * Body for `POST /api/gigs/:gigId/report` (`backend/routes/gigs.js:3110`).
 * `reason` must be one of the [GigReportReason] wire values; `details`
 * is free text up to 1000 chars.
 */
@JsonClass(generateAdapter = true)
data class ReportGigBody(
    val reason: String,
    val details: String? = null,
)

/** Response from the gig report endpoint. */
@JsonClass(generateAdapter = true)
data class ReportGigResponse(
    val message: String? = null,
)

/** Allowed `reason` values for `reportGigSchema` (`backend/routes/gigs.js:690`). */
enum class GigReportReason(val wireValue: String, val label: String) {
    Spam("spam", "Spam or scam"),
    Harassment("harassment", "Harassment"),
    Inappropriate("inappropriate", "Inappropriate content"),
    Misinformation("misinformation", "Misinformation"),
    Safety("safety", "Safety concern"),
    Other("other", "Something else"),
}

/**
 * Response from `GET /api/gigs/:gigId/cancellation-preview`
 * (`backend/routes/gigs.js:6354`). Mirrors `computeCancellationInfo`
 * (`backend/routes/gigs.js:569`) plus the policy descriptors.
 */
@JsonClass(generateAdapter = true)
data class CancellationPreviewResponse(
    val zone: Int? = null,
    @Json(name = "zone_label") val zoneLabel: String? = null,
    val fee: Double? = null,
    @Json(name = "fee_pct") val feePct: Double? = null,
    @Json(name = "in_grace") val inGrace: Boolean? = null,
    val policy: String? = null,
    @Json(name = "policy_label") val policyLabel: String? = null,
    @Json(name = "policy_description") val policyDescription: String? = null,
    @Json(name = "can_reschedule") val canReschedule: Boolean? = null,
)
