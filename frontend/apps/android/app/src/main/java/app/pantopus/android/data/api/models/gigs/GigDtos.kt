@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.gigs

import app.pantopus.android.data.api.models.payments.PaymentIntentSheetParamsDto
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Privacy-safe coarse location surfaced on map / in-bounds responses. */
@JsonClass(generateAdapter = true)
data class GigApproxLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val label: String? = null,
)

/** Creator projection from the backend `User` join. */
@JsonClass(generateAdapter = true)
data class GigCreator(
    val id: String? = null,
    val username: String? = null,
    val name: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
    val verified: Boolean? = null,
)

/**
 * One row from `GET /api/gigs` / `GET /api/gigs/nearby`. Mirrors the
 * GIG_LIST projection from `backend/routes/gigs.js`.
 */
@JsonClass(generateAdapter = true)
data class GigDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val price: Double? = null,
    val category: String? = null,
    val status: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val deadline: String? = null,
    @Json(name = "is_urgent") val isUrgent: Boolean? = null,
    val tags: List<String>? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "accepted_by") val acceptedBy: String? = null,
    @Json(name = "accepted_at") val acceptedAt: String? = null,
    // Set when the poster confirms completion — gates the Block 3D tip affordance.
    @Json(name = "owner_confirmed_at") val ownerConfirmedAt: String? = null,
    @Json(name = "scheduled_start") val scheduledStart: String? = null,
    @Json(name = "payment_status") val paymentStatus: String? = null,
    @Json(name = "engagement_mode") val engagementMode: String? = null,
    @Json(name = "schedule_type") val scheduleType: String? = null,
    @Json(name = "pay_type") val payType: String? = null,
    @Json(name = "task_archetype") val taskArchetype: String? = null,
    // Explicit V2 ("Magic Task") discriminator. `true` → rich V2 surface;
    // `null`/`false` → sparse V1 legacy layout. Backend may omit on legacy gigs.
    @Json(name = "is_v2") val isV2: Boolean? = null,
    @Json(name = "pickup_address") val pickupAddress: String? = null,
    @Json(name = "dropoff_address") val dropoffAddress: String? = null,
    @Json(name = "bid_count") val bidCount: Int? = null,
    @Json(name = "saved_by_user") val savedByUser: Boolean? = null,
    @Json(name = "distance_miles") val distanceMiles: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "approx_location") val approxLocation: GigApproxLocation? = null,
    @Json(name = "User") val creator: GigCreator? = null,
)

/** Envelope from `/api/gigs`. */
@JsonClass(generateAdapter = true)
data class GigsListResponse(
    val gigs: List<GigDto>,
    val total: Int? = null,
    val radiusMeters: Int? = null,
)

/** Save / unsave envelope from `POST /api/gigs/:id/save`. */
@JsonClass(generateAdapter = true)
data class GigSaveResponse(
    val message: String? = null,
    val saved: Boolean? = null,
)

/** Backend recenter hint for empty map viewports. */
@JsonClass(generateAdapter = true)
data class GigsNearestActivityCenter(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Envelope from `GET /api/gigs/in-bounds`. */
@JsonClass(generateAdapter = true)
data class GigsInBoundsResponse(
    val gigs: List<GigDto>,
    @Json(name = "nearest_activity_center") val nearestActivityCenter: GigsNearestActivityCenter? = null,
)

/** Envelope from `GET /api/gigs/:id`. */
@JsonClass(generateAdapter = true)
data class GigDetailResponse(
    val gig: GigDto,
)

/** One bid on a gig. */
@JsonClass(generateAdapter = true)
data class GigBidDto(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "bid_amount") val bidAmount: Double? = null,
    val amount: Double? = null,
    val status: String? = null,
    val message: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "User") val bidder: GigCreator? = null,
)

/** Envelope from `GET /api/gigs/:gigId/bids`. */
@JsonClass(generateAdapter = true)
data class GigBidsResponse(
    val bids: List<GigBidDto>,
)

/** `POST /api/gigs/:gigId/bids` body. */
@JsonClass(generateAdapter = true)
data class PlaceBidBody(
    @Json(name = "bid_amount") val bidAmount: Double,
    val message: String? = null,
    @Json(name = "proposed_time") val proposedTime: String? = null,
)

/** `POST /api/gigs/:gigId/bids` envelope. */
@JsonClass(generateAdapter = true)
data class PlaceBidResponse(
    val bid: GigBidDto? = null,
    val message: String? = null,
)

/**
 * Response from `POST /api/gigs/:gigId/bids/:bidId/accept`.
 * Paid gigs return PaymentSheet params and stay in `pending_payment`
 * until `finalize-accept` succeeds; free gigs may already be accepted.
 */
@JsonClass(generateAdapter = true)
data class GigBidAcceptResponse(
    val bid: GigBidDto? = null,
    val message: String? = null,
    val requiresPaymentSetup: Boolean? = null,
    val isSetupIntent: Boolean? = null,
    val payment: PaymentPayload? = null,
    val publishableKey: String? = null,
    val clientSecret: String? = null,
    val paymentId: String? = null,
    val setupIntentId: String? = null,
    val paymentIntentId: String? = null,
    val ephemeralKey: String? = null,
    val customer: String? = null,
    val customerId: String? = null,
) {
    fun sheetParams(): PaymentIntentSheetParamsDto =
        PaymentIntentSheetParamsDto(
            clientSecret = clientSecret ?: payment?.clientSecret,
            paymentIntentId = paymentIntentId ?: payment?.paymentIntentId,
            customer = customer ?: customerId,
            ephemeralKey = ephemeralKey,
            publishableKey = publishableKey,
            isSetupIntent = isSetupIntent,
        )

    @JsonClass(generateAdapter = true)
    data class PaymentPayload(
        val clientSecret: String? = null,
        val paymentId: String? = null,
        val setupIntentId: String? = null,
        val paymentIntentId: String? = null,
    )
}

/**
 * Body for `POST /api/gigs/:gigId/mark-completed`. The Delivery Proof
 * sheet sends the optional `note` plus `photos` (proof-of-delivery URLs
 * uploaded first via `POST /api/files/upload`); the backend stores them
 * as `completion_photos`. `checklist` is omitted.
 */
@JsonClass(generateAdapter = true)
data class MarkCompletedBody(
    val note: String? = null,
    val photos: List<String>? = null,
)

/** Response envelope from the mark-completed endpoint. */
@JsonClass(generateAdapter = true)
data class MarkCompletedResponse(
    val message: String? = null,
)

/**
 * Body for `POST /api/gigs`. Mirrors the subset of the backend's
 * `createGigSchema` the Post-a-Task wizard surfaces
 * (`backend/routes/gigs.js:425`). Optional fields are nullable so Moshi
 * omits them from the JSON when unset.
 */
@JsonClass(generateAdapter = true)
data class CreateGigBody(
    val title: String,
    val description: String,
    val category: String? = null,
    val price: Double,
    @Json(name = "pay_type") val payType: String? = null,
    @Json(name = "schedule_type") val scheduleType: String? = null,
    @Json(name = "scheduled_start") val scheduledStart: String? = null,
    @Json(name = "task_format") val taskFormat: String? = null,
    val attachments: List<String>? = null,
    // E.1 — composer picker-sheet fields. Optional, so Moshi omits them
    // from the JSON when unset.
    val deadline: String? = null,
    @Json(name = "cancellation_policy") val cancellationPolicy: String? = null,
    @Json(name = "is_urgent") val isUrgent: Boolean? = null,
    val tags: List<String>? = null,
    val location: CreateGigLocation,
)

/**
 * Nested `location` object the backend requires
 * (`backend/routes/gigs.js:521`).
 */
@JsonClass(generateAdapter = true)
data class CreateGigLocation(
    val mode: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val city: String? = null,
    val state: String? = null,
    val zip: String? = null,
    val homeId: String? = null,
)

/** Envelope from `POST /api/gigs`. */
@JsonClass(generateAdapter = true)
data class CreateGigResponse(
    val gig: GigDto,
    val message: String? = null,
)
