package app.pantopus.android.data.api.models.support_trains

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `GET /api/support-trains/me/support-trains` envelope.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainsListResponse(
    @Json(name = "support_trains") val supportTrains: List<SupportTrainListItemDto> = emptyList(),
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

/**
 * `GET /api/support-trains/nearby` envelope.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainsNearbyResponse(
    @Json(name = "support_trains") val supportTrains: List<SupportTrainListItemDto> = emptyList(),
)

/**
 * One Support Train row, as rendered in My-trains / Nearby. Backend keeps
 * wire-level snake_case columns; we map at the decoder boundary.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainListItemDto(
    val id: String,
    val title: String? = null,
    val status: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    /** `organizer` / `helper` — present on the My-trains feed only. */
    @Json(name = "my_role") val myRole: String? = null,
    @Json(name = "support_train_type") val supportTrainType: String? = null,
    @Json(name = "starts_on") val startsOn: String? = null,
    @Json(name = "ends_on") val endsOn: String? = null,
    @Json(name = "slots_filled") val slotsFilled: Int? = null,
    @Json(name = "slots_total") val slotsTotal: Int? = null,
    @Json(name = "distance_meters") val distanceMeters: Double? = null,
    @Json(name = "recipient_name") val recipientName: String? = null,
)

/**
 * `GET /api/support-trains/:id/reservations` envelope.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainReservationsResponse(
    val reservations: List<SupportTrainReservationDto> = emptyList(),
)

/**
 * One helper reservation row, as rendered on Review-signups.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainReservationDto(
    val id: String,
    @Json(name = "slot_id") val slotId: String? = null,
    val status: String? = null,
    val note: String? = null,
    @Json(name = "diet_flag") val dietFlag: String? = null,
    @Json(name = "diet_ok") val dietOk: Boolean? = null,
    @Json(name = "drop_window") val dropWindow: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "edited_at") val editedAt: String? = null,
    @Json(name = "conflict_with") val conflictWith: String? = null,
    val helper: SupportTrainHelperDto? = null,
    val slot: SupportTrainSlotDto? = null,
)

@JsonClass(generateAdapter = true)
data class SupportTrainHelperDto(
    val id: String,
    val username: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    @Json(name = "is_verified") val isVerified: Boolean? = null,
    val relationship: String? = null,
)

@JsonClass(generateAdapter = true)
data class SupportTrainSlotDto(
    val id: String,
    val date: String? = null,
    @Json(name = "drop_window") val dropWindow: String? = null,
)
