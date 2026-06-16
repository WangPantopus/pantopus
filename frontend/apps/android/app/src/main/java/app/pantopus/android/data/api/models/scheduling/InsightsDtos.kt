@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.scheduling

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Analytics. `GET /bookings/insights/no-shows?days` and
 * `GET /bookings/insights/team?days` (business-only). Rates are percentages;
 * revenue is in cents.
 */
@JsonClass(generateAdapter = true)
data class NoShowByEventType(
    @Json(name = "event_type_id") val eventTypeId: String? = null,
    val name: String? = null,
    val count: Int? = null,
    val rate: Double? = null,
)

@JsonClass(generateAdapter = true)
data class NoShowByHost(
    @Json(name = "user_id") val userId: String? = null,
    val name: String? = null,
    val count: Int? = null,
    val rate: Double? = null,
)

@JsonClass(generateAdapter = true)
data class NoShowRecent(
    @Json(name = "booking_id") val bookingId: String? = null,
    @Json(name = "invitee_name") val inviteeName: String? = null,
    @Json(name = "scheduled_at") val scheduledAt: String? = null,
    @Json(name = "no_show_at") val noShowAt: String? = null,
)

/** `GET /bookings/insights/no-shows`. */
@JsonClass(generateAdapter = true)
data class NoShowReportResponse(
    @Json(name = "noShowCount") val noShowCount: Int = 0,
    @Json(name = "noShowRate") val noShowRate: Double = 0.0,
    @Json(name = "byEventType") val byEventType: List<NoShowByEventType> = emptyList(),
    @Json(name = "byHost") val byHost: List<NoShowByHost> = emptyList(),
    val recent: List<NoShowRecent> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class TeamMemberPerformance(
    @Json(name = "user_id") val userId: String? = null,
    val name: String? = null,
    @Json(name = "bookingsCount") val bookingsCount: Int? = null,
    val revenue: Double? = null,
    @Json(name = "noShowRate") val noShowRate: Double? = null,
    @Json(name = "avgDuration") val avgDuration: Double? = null,
)

/** `GET /bookings/insights/team` (business-only). */
@JsonClass(generateAdapter = true)
data class TeamPerformanceResponse(
    @Json(name = "teamMembers") val teamMembers: List<TeamMemberPerformance> = emptyList(),
    @Json(name = "totalRevenue") val totalRevenue: Double? = null,
    @Json(name = "totalBookings") val totalBookings: Int? = null,
    @Json(name = "avgBookingValue") val avgBookingValue: Double? = null,
)
