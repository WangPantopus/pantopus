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
