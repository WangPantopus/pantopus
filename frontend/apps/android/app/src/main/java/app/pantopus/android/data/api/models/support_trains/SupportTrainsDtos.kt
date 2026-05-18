package app.pantopus.android.data.api.models.support_trains

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `GET /api/support-trains/me/support-trains` envelope. See
 * `backend/routes/supportTrains.js:475–565`.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainsListResponse(
    @Json(name = "support_trains") val supportTrains: List<SupportTrainListItemDto> = emptyList(),
    val total: Int? = null,
    val limit: Int? = null,
    val offset: Int? = null,
)

/**
 * `GET /api/support-trains/nearby` envelope. See
 * `backend/routes/supportTrains.js:570+`.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainsNearbyResponse(
    @Json(name = "support_trains") val supportTrains: List<SupportTrainListItemDto> = emptyList(),
)

/**
 * One Support Train row, as rendered in My-trains / Nearby.
 *
 * Backend wire shape (verified at `supportTrains.js:534–558`):
 * ```
 * { id, title, status, published_at, created_at, my_role }
 * ```
 *
 * All additional fields below are **populated by the Nearby RPC only**
 * — they stay `null` for My-trains rows until a backend prep PR adds
 * `slots_filled`, `slots_total`, `support_train_type`, `starts_on`,
 * `ends_on` and `recipient_name` to the My-trains projection. Both
 * feeds decode through this single shape and the VM degrades
 * gracefully (generic archetype tile, no slot bar) when fields are
 * absent.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainListItemDto(
    val id: String,
    val title: String? = null,
    val status: String? = null,
    @Json(name = "published_at") val publishedAt: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    /** `organizer` / `co_organizer` / `helper` — My-trains feed only. */
    @Json(name = "my_role") val myRole: String? = null,
    // ── Nearby-RPC fields (follow-up backend-prep extends My-trains) ──
    @Json(name = "support_train_type") val supportTrainType: String? = null,
    @Json(name = "starts_on") val startsOn: String? = null,
    @Json(name = "ends_on") val endsOn: String? = null,
    @Json(name = "slots_filled") val slotsFilled: Int? = null,
    @Json(name = "slots_total") val slotsTotal: Int? = null,
    @Json(name = "distance_meters") val distanceMeters: Double? = null,
    @Json(name = "recipient_name") val recipientName: String? = null,
)

/**
 * `GET /api/support-trains/:id/reservations` envelope. See
 * `backend/routes/supportTrains.js:3306+`.
 */
@JsonClass(generateAdapter = true)
data class SupportTrainReservationsResponse(
    val reservations: List<SupportTrainReservationDto> = emptyList(),
)

/**
 * One helper reservation row, as rendered on Review-signups.
 *
 * Backend wire shape (verified at `supportTrains.js:3349–3357`):
 * ```
 * { id, slot_id, user_id, guest_name, guest_email, status,
 *   contribution_mode, dish_title, restaurant_name,
 *   estimated_arrival_at, note_to_recipient, private_note_to_organizer,
 *   created_at, updated_at, canceled_at,
 *   User: { id, username, name, profile_picture_url } }
 * ```
 *
 * Diet flag / conflict marker / explicit edited-at / helper
 * relationship — the four UI conveniences in the design — are **not**
 * projected by the current handler. A follow-up backend prep adds:
 *  - `User.is_verified` + `User.relationship_to_recipient`
 *  - `diet_flag` / `diet_ok` (joined from `SupportTrainRecipientProfile`)
 *  - `conflict_with` (denormalised from slot-availability service)
 *
 * Until then the row falls back gracefully: relationship chip hides,
 * diet flag is omitted, conflict strip never fires, "Edited" is
 * computed client-side via [wasEdited].
 */
@JsonClass(generateAdapter = true)
data class SupportTrainReservationDto(
    val id: String,
    @Json(name = "slot_id") val slotId: String? = null,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "guest_name") val guestName: String? = null,
    val status: String? = null,
    @Json(name = "contribution_mode") val contributionMode: String? = null,
    @Json(name = "dish_title") val dishTitle: String? = null,
    @Json(name = "restaurant_name") val restaurantName: String? = null,
    @Json(name = "estimated_arrival_at") val estimatedArrivalAt: String? = null,
    @Json(name = "note_to_recipient") val noteToRecipient: String? = null,
    @Json(name = "private_note_to_organizer") val privateNoteToOrganizer: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
    @Json(name = "canceled_at") val canceledAt: String? = null,
    /** Backend nests the helper as `User` (capitalised). */
    @Json(name = "User") val helper: SupportTrainHelperDto? = null,
) {
    /** True when `updatedAt > createdAt` — projects the "Edited" chip. */
    val wasEdited: Boolean
        get() = !updatedAt.isNullOrBlank() && updatedAt != createdAt

    /** Best-effort display name: `helper.name` → `username` → `guestName` → "Helper". */
    val displayName: String
        get() = helper?.name ?: helper?.username ?: guestName ?: "Helper"
}

@JsonClass(generateAdapter = true)
data class SupportTrainHelperDto(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
)
