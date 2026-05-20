@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.businesses

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Lightweight business "user" projection emitted alongside every
 * [BusinessMembership] row. The backend joins `User`
 * (account_type='business') plus `city, state` for the locality body
 * (T6.3f added city/state to the SELECT).
 */
@JsonClass(generateAdapter = true)
data class BusinessUserDto(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
    @Json(name = "account_type") val accountType: String? = null,
    val city: String? = null,
    val state: String? = null,
)

/**
 * Optional `BusinessProfile` join — present when the business has
 * onboarded a profile (categories, logo, description). Always null for
 * freshly-created businesses with no profile yet.
 */
@JsonClass(generateAdapter = true)
data class BusinessProfileDto(
    @Json(name = "business_user_id") val businessUserId: String? = null,
    @Json(name = "business_type") val businessType: String? = null,
    val categories: List<String>? = null,
    @Json(name = "is_published") val isPublished: Boolean? = null,
    @Json(name = "logo_file_id") val logoFileId: String? = null,
    @Json(name = "banner_file_id") val bannerFileId: String? = null,
    val description: String? = null,
)

/**
 * One row from `/api/businesses/my-businesses` — the membership +
 * business projection used by the My businesses screen. Route:
 * `backend/routes/businesses.js:682`.
 */
@JsonClass(generateAdapter = true)
data class BusinessMembership(
    val id: String,
    @Json(name = "role_base") val roleBase: String? = null,
    val title: String? = null,
    @Json(name = "joined_at") val joinedAt: String? = null,
    @Json(name = "business_user_id") val businessUserId: String,
    val business: BusinessUserDto,
    val profile: BusinessProfileDto? = null,
)

/** `GET /api/businesses/my-businesses` envelope. */
@JsonClass(generateAdapter = true)
data class MyBusinessesResponse(
    val businesses: List<BusinessMembership>,
)

// ---------------------------------------------------------------------------
// P1.6 — Business Profile screen DTOs
// ---------------------------------------------------------------------------

/**
 * Full Business "User" row returned by `GET /api/businesses/:businessId`.
 * The backend `select('*')` projects every column; we decode only the
 * fields the Business Profile screen renders.
 */
@JsonClass(generateAdapter = true)
data class BusinessUserDetailDto(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    val email: String? = null,
    val bio: String? = null,
    val tagline: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
    @Json(name = "cover_photo_url") val coverPhotoUrl: String? = null,
    @Json(name = "account_type") val accountType: String? = null,
    val city: String? = null,
    val state: String? = null,
    val verified: Boolean? = null,
    @Json(name = "average_rating") val averageRating: Double? = null,
    @Json(name = "review_count") val reviewCount: Int? = null,
    @Json(name = "followers_count") val followersCount: Int? = null,
    @Json(name = "gigs_completed") val gigsCompleted: Int? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)

/**
 * Geo point projection. The backend's `parsePostGISPoint` normalises
 * PostGIS data into `{ lat, lng }`.
 */
@JsonClass(generateAdapter = true)
data class BusinessGeoPoint(
    val lat: Double,
    val lng: Double,
)

/** A single `BusinessLocation` row. */
@JsonClass(generateAdapter = true)
data class BusinessLocationDto(
    val id: String,
    val label: String? = null,
    @Json(name = "is_primary") val isPrimary: Boolean? = null,
    val address: String? = null,
    val address2: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipcode: String? = null,
    val country: String? = null,
    val location: BusinessGeoPoint? = null,
    val phone: String? = null,
    val email: String? = null,
    val timezone: String? = null,
)

/**
 * Full `BusinessProfile` row returned by `GET /api/businesses/:businessId`
 * (and folded in by the public payload). Only fields the iOS screen
 * actually renders are decoded.
 */
@JsonClass(generateAdapter = true)
data class BusinessProfileDetailDto(
    @Json(name = "business_user_id") val businessUserId: String? = null,
    @Json(name = "business_type") val businessType: String? = null,
    val categories: List<String>? = null,
    val description: String? = null,
    @Json(name = "logo_file_id") val logoFileId: String? = null,
    @Json(name = "banner_file_id") val bannerFileId: String? = null,
    @Json(name = "public_email") val publicEmail: String? = null,
    @Json(name = "public_phone") val publicPhone: String? = null,
    val website: String? = null,
    @Json(name = "founded_year") val foundedYear: Int? = null,
    @Json(name = "employee_count") val employeeCount: String? = null,
    @Json(name = "service_area") val serviceArea: String? = null,
    @Json(name = "founding_badge") val foundingBadge: Boolean? = null,
    @Json(name = "is_published") val isPublished: Boolean? = null,
    @Json(name = "verification_status") val verificationStatus: String? = null,
    @Json(name = "primary_location") val primaryLocation: BusinessLocationDto? = null,
)

/**
 * `access` sub-object on the detail response. Tells the caller whether
 * the viewer owns / staffs the business — P1.6 only uses `isOwner` to
 * suppress the Save affordance for self-views.
 */
@JsonClass(generateAdapter = true)
data class BusinessAccessDto(
    val hasAccess: Boolean = false,
    val isOwner: Boolean = false,
    @Json(name = "role_base") val roleBase: String? = null,
)

/**
 * `GET /api/businesses/:businessId` envelope — route
 * `backend/routes/businesses.js:912`.
 */
@JsonClass(generateAdapter = true)
data class BusinessDetailResponse(
    val business: BusinessUserDetailDto,
    val profile: BusinessProfileDetailDto? = null,
    val locations: List<BusinessLocationDto> = emptyList(),
    val access: BusinessAccessDto? = null,
)

/**
 * One `BusinessHours` row returned in `/public/:username`. `day_of_week`
 * is `0..6` (Sunday=0). Closed days have `is_closed = true` and `null`
 * open/close strings.
 */
@JsonClass(generateAdapter = true)
data class BusinessHoursDto(
    val id: String? = null,
    @Json(name = "location_id") val locationId: String? = null,
    @Json(name = "day_of_week") val dayOfWeek: Int,
    @Json(name = "open_time") val openTime: String? = null,
    @Json(name = "close_time") val closeTime: String? = null,
    @Json(name = "is_closed") val isClosed: Boolean? = null,
)

/**
 * `BusinessCatalogItem` row. Used by the Services tab. Donation /
 * product items pass through too — the renderer just shows the kind
 * label and the price (or "Variable" when prices aren't set).
 */
@JsonClass(generateAdapter = true)
data class BusinessCatalogItemDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val kind: String? = null,
    @Json(name = "price_cents") val priceCents: Int? = null,
    @Json(name = "price_max_cents") val priceMaxCents: Int? = null,
    @Json(name = "price_unit") val priceUnit: String? = null,
    val currency: String? = null,
    @Json(name = "image_url") val imageUrl: String? = null,
    @Json(name = "is_featured") val isFeatured: Boolean? = null,
)

/**
 * `GET /api/businesses/public/:username` response (subset). Only the
 * fields the Business Profile screen reads are decoded; the response is
 * far larger (pages, blocks, founding slot, …).
 * Route `backend/routes/businesses.js:3277`.
 */
@JsonClass(generateAdapter = true)
data class BusinessPublicResponse(
    val hours: List<BusinessHoursDto> = emptyList(),
    val catalog: List<BusinessCatalogItemDto> = emptyList(),
)
