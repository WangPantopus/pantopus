@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.gigs

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
    @Json(name = "scheduled_start") val scheduledStart: String? = null,
    @Json(name = "payment_status") val paymentStatus: String? = null,
    @Json(name = "engagement_mode") val engagementMode: String? = null,
    @Json(name = "schedule_type") val scheduleType: String? = null,
    @Json(name = "pay_type") val payType: String? = null,
    @Json(name = "task_archetype") val taskArchetype: String? = null,
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
