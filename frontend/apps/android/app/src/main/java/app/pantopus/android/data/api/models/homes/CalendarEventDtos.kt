@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.homes

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One row from `GET /api/homes/:id/events` — route
 * `backend/routes/home.js:4793`.
 */
@JsonClass(generateAdapter = true)
data class CalendarEventDto(
    val id: String,
    @Json(name = "home_id") val homeId: String,
    @Json(name = "event_type") val eventType: String,
    val title: String,
    val description: String? = null,
    /** ISO-8601 timestamp. */
    @Json(name = "start_at") val startAt: String,
    /** ISO-8601 timestamp. Null for all-day / point-in-time events. */
    @Json(name = "end_at") val endAt: String? = null,
    @Json(name = "location_notes") val locationNotes: String? = null,
    /** iCal RRULE string ("FREQ=WEEKLY"), null for one-off events. */
    @Json(name = "recurrence_rule") val recurrenceRule: String? = null,
    /** Backend stores an array of user-ids (assigned household members). */
    @Json(name = "assigned_to") val assignedTo: List<String>? = null,
    @Json(name = "alerts_enabled") val alertsEnabled: Boolean? = null,
    @Json(name = "created_by") val createdBy: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "updated_at") val updatedAt: String? = null,
)

/** Envelope for `GET /api/homes/:id/events`. */
@JsonClass(generateAdapter = true)
data class GetHomeEventsResponse(
    val events: List<CalendarEventDto> = emptyList(),
)

/** Body for `POST /api/homes/:id/events` — route
 *  `backend/routes/home.js:4827`. */
@JsonClass(generateAdapter = true)
data class CreateHomeEventRequest(
    @Json(name = "event_type") val eventType: String,
    val title: String,
    @Json(name = "start_at") val startAt: String,
    val description: String? = null,
    @Json(name = "end_at") val endAt: String? = null,
    @Json(name = "location_notes") val locationNotes: String? = null,
    @Json(name = "recurrence_rule") val recurrenceRule: String? = null,
    @Json(name = "assigned_to") val assignedTo: List<String>? = null,
    @Json(name = "alerts_enabled") val alertsEnabled: Boolean? = null,
)

/** Envelope for `POST /api/homes/:id/events` and `PUT …/:eventId`. */
@JsonClass(generateAdapter = true)
data class HomeEventResponse(
    val event: CalendarEventDto,
)
