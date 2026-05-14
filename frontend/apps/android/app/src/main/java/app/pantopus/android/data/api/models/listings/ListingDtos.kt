@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.listings

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Privacy-safe coarse location surfaced on listings map responses. */
@JsonClass(generateAdapter = true)
data class ListingApproxLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val label: String? = null,
)

/**
 * Minimal projection of one listing row from
 * `GET /api/listings/in-bounds`. T2.4 needs only enough to drop a pin
 * and render a card — full listing shape lands in T2.5.
 */
@JsonClass(generateAdapter = true)
data class ListingDto(
    val id: String,
    val title: String? = null,
    val category: String? = null,
    val layer: String? = null,
    val price: Double? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "approx_location") val approxLocation: ListingApproxLocation? = null,
)

/** Backend recenter hint for empty viewports. */
@JsonClass(generateAdapter = true)
data class NearestActivityCenter(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Envelope from `GET /api/listings/in-bounds`. */
@JsonClass(generateAdapter = true)
data class ListingsInBoundsResponse(
    val listings: List<ListingDto>,
    @Json(name = "nearest_activity_center") val nearestActivityCenter: NearestActivityCenter? = null,
)
