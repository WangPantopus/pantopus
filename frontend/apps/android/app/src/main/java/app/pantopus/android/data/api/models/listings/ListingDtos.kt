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
 * One row from the marketplace endpoints. Mirrors `normalizeListingRow`
 * in `backend/services/marketplace/marketplaceService.js:82`.
 */
@JsonClass(generateAdapter = true)
data class ListingDto(
    val id: String,
    @Json(name = "user_id") val userId: String? = null,
    val title: String? = null,
    val description: String? = null,
    val price: Double? = null,
    @Json(name = "is_free") val isFree: Boolean? = null,
    val category: String? = null,
    val condition: String? = null,
    val status: String? = null,
    @Json(name = "media_urls") val mediaUrls: List<String>? = null,
    @Json(name = "first_image") val firstImage: String? = null,
    val layer: String? = null,
    @Json(name = "listing_type") val listingType: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "location_name") val locationName: String? = null,
    @Json(name = "distance_meters") val distanceMeters: Double? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "userHasSaved") val userHasSaved: Boolean? = null,
    @Json(name = "approx_location") val approxLocation: ListingApproxLocation? = null,
)

/** Pagination cursor returned by `/browse` and `/nearby`. */
@JsonClass(generateAdapter = true)
data class ListingsPagination(
    val limit: Int? = null,
    val offset: Int? = null,
    val hasMore: Boolean? = null,
    @Json(name = "next_cursor") val nextCursor: String? = null,
)

/** Envelope from `/api/listings/browse`. */
@JsonClass(generateAdapter = true)
data class ListingsBrowseResponse(
    val listings: List<ListingDto>,
    val pagination: ListingsPagination? = null,
    @Json(name = "nearest_activity_center") val nearestActivityCenter: NearestActivityCenter? = null,
)

/** Envelope from `/api/listings/nearby`. */
@JsonClass(generateAdapter = true)
data class ListingsNearbyResponse(
    val listings: List<ListingDto>,
    val pagination: ListingsPagination? = null,
)

/** Envelope from `/api/listings/in-bounds`. */
@JsonClass(generateAdapter = true)
data class ListingsInBoundsResponse(
    val listings: List<ListingDto>,
    @Json(name = "nearest_activity_center") val nearestActivityCenter: NearestActivityCenter? = null,
)

/** Backend recenter hint for empty viewports. */
@JsonClass(generateAdapter = true)
data class NearestActivityCenter(
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/** Envelope from `/api/listings/categories`. */
@JsonClass(generateAdapter = true)
data class ListingsCategoriesResponse(
    val categories: List<String>,
    val conditions: List<String>,
)

/** Save / unsave envelope from `POST /api/listings/:id/save`. */
@JsonClass(generateAdapter = true)
data class ListingSaveResponse(
    val message: String? = null,
    val saved: Boolean? = null,
)
