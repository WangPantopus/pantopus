package app.pantopus.android.data.api.models.users

import app.pantopus.android.data.api.models.common.JsonValue
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Compact session-identity projection. Build via the companion helpers
 * from the richer login / profile responses.
 */
@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    @Json(name = "display_name") val displayName: String?,
    @Json(name = "avatar_url") val avatarUrl: String?,
)

/**
 * Social-link bundle inside [UserProfile]. Route:
 * `backend/routes/users.js:1427`.
 */
@JsonClass(generateAdapter = true)
data class SocialLinks(
    val website: String?,
    val linkedin: String?,
    val twitter: String?,
    val instagram: String?,
    val facebook: String?,
)

/**
 * `GET /api/users/profile` user envelope — route
 * `backend/routes/users.js:1427`. Shape fields whose upstream type is
 * provider-dependent (residency, inviteProgress) are decoded as untyped.
 */
@JsonClass(generateAdapter = true)
data class UserProfile(
    val id: String,
    val email: String,
    val username: String,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val name: String,
    val phoneNumber: String?,
    val dateOfBirth: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val zipcode: String?,
    val accountType: String,
    val role: String,
    val verified: Boolean,
    val residency: JsonValue?,
    @Json(name = "avatar_url") val avatarUrl: String?,
    @Json(name = "profile_picture_url") val profilePictureUrl: String?,
    val profilePicture: String?,
    val bio: String?,
    val tagline: String?,
    val socialLinks: SocialLinks?,
    val skills: List<String>?,
    @Json(name = "followers_count") val followersCount: Int?,
    @Json(name = "average_rating") val averageRating: Double?,
    @Json(name = "gigs_posted") val gigsPosted: Int?,
    @Json(name = "gigs_completed") val gigsCompleted: Int?,
    val profileVisibility: String?,
    val createdAt: String,
    val updatedAt: String,
)

/** Envelope for `GET /api/users/profile`. Route: `backend/routes/users.js:1427`. */
@JsonClass(generateAdapter = true)
data class ProfileResponse(
    val user: UserProfile,
    /** Shape varies by invite service — decoded lazily. */
    @Json(name = "invite_progress") val inviteProgress: JsonValue?,
)

/**
 * `PATCH /api/users/profile` request. Every field optional — unspecified
 * keys are left untouched server-side. Route:
 * `backend/routes/users.js:1503`.
 */
@JsonClass(generateAdapter = true)
data class ProfileUpdateRequest(
    val firstName: String? = null,
    val middleName: String? = null,
    val lastName: String? = null,
    val phoneNumber: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipcode: String? = null,
    val dateOfBirth: String? = null,
    val bio: String? = null,
    val tagline: String? = null,
    val profileVisibility: String? = null,
    val website: String? = null,
    val linkedin: String? = null,
    val twitter: String? = null,
    val instagram: String? = null,
    val facebook: String? = null,
)

/** Envelope for `PATCH /api/users/profile`. Route: `backend/routes/users.js:1503`. */
@JsonClass(generateAdapter = true)
data class ProfileUpdateResponse(
    val message: String,
    val user: UserProfile,
)
