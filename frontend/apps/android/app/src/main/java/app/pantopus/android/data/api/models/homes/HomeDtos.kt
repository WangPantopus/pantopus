package app.pantopus.android.data.api.models.homes

import app.pantopus.android.data.api.models.common.JsonValue
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Stable fields from a Home row. Additional columns not listed here are
 * ignored by the JSON adapter. Route citations live on the response
 * envelopes below.
 */
@JsonClass(generateAdapter = true)
data class HomeDto(
    val id: String,
    val name: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val zipcode: String?,
    @Json(name = "home_type") val homeType: String?,
    val visibility: String?,
    val description: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
)

/**
 * Occupancy badge emitted per-home in `my-homes`. Route:
 * `backend/routes/home.js:1464`.
 */
@JsonClass(generateAdapter = true)
data class HomeOccupancy(
    val id: String,
    val role: String,
    @Json(name = "role_base") val roleBase: String,
    @Json(name = "is_active") val isActive: Boolean,
    @Json(name = "start_at") val startAt: String?,
    @Json(name = "end_at") val endAt: String?,
    @Json(name = "verification_status") val verificationStatus: String,
)

/**
 * `GET /api/homes/my-homes` entry. The home-columns fields are mixed in at
 * the top level with the badge fields below; we decode the home and the
 * badges as sibling properties (`@Json` tags align field-by-field).
 * Route: `backend/routes/home.js:1464`.
 */
@JsonClass(generateAdapter = true)
data class MyHome(
    val id: String,
    val name: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val zipcode: String?,
    @Json(name = "home_type") val homeType: String?,
    val visibility: String?,
    val description: String?,
    @Json(name = "created_at") val createdAt: String?,
    @Json(name = "updated_at") val updatedAt: String?,
    val occupancy: HomeOccupancy?,
    @Json(name = "ownership_status") val ownershipStatus: String?,
    @Json(name = "verification_tier") val verificationTier: String?,
    @Json(name = "is_primary_owner") val isPrimaryOwner: Boolean?,
    @Json(name = "pending_claim_id") val pendingClaimId: String?,
)

/** `GET /api/homes/my-homes` envelope — route `backend/routes/home.js:1464`. */
@JsonClass(generateAdapter = true)
data class MyHomesResponse(
    val homes: List<MyHome>,
    val message: String?,
)

/** `GET /api/homes/:id` envelope — route `backend/routes/home.js:2891`. */
@JsonClass(generateAdapter = true)
data class HomeDetailResponse(
    val home: HomeDetail,
)

@JsonClass(generateAdapter = true)
data class HomeDetail(
    val id: String,
    val name: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val zipcode: String?,
    @Json(name = "home_type") val homeType: String?,
    val visibility: String?,
    val description: String?,
    @Json(name = "created_at") val createdAt: String?,
    val owner: HomeUserRef?,
    val occupants: List<HomeOccupant> = emptyList(),
    val location: HomeLocation?,
    val isOwner: Boolean = false,
    val isPendingOwner: Boolean = false,
    val pendingClaimId: String?,
    val isOccupant: Boolean = false,
    val owners: List<HomeOwnershipRef> = emptyList(),
    @Json(name = "can_delete_home") val canDeleteHome: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeUserRef(
    val id: String,
    val username: String,
    val name: String,
)

@JsonClass(generateAdapter = true)
data class HomeOccupant(
    @Json(name = "user_id") val userId: String,
    @Json(name = "created_at") val createdAt: String,
    val user: HomeUserRef,
)

@JsonClass(generateAdapter = true)
data class HomeLocation(
    val longitude: Double,
    val latitude: Double,
)

@JsonClass(generateAdapter = true)
data class HomeOwnershipRef(
    val id: String,
    @Json(name = "subject_type") val subjectType: String,
    @Json(name = "subject_id") val subjectId: String,
    @Json(name = "owner_status") val ownerStatus: String,
    @Json(name = "is_primary_owner") val isPrimaryOwner: Boolean,
    @Json(name = "verification_tier") val verificationTier: String,
)

/** `GET /api/homes/:id/public-profile` envelope — route `backend/routes/home.js:2439`. */
@JsonClass(generateAdapter = true)
data class HomePublicProfileResponse(
    val home: HomePublicProfile,
)

@JsonClass(generateAdapter = true)
data class HomePublicProfile(
    val id: String,
    val name: String?,
    val address: String,
    val city: String,
    val state: String,
    val zipcode: String,
    @Json(name = "home_type") val homeType: String?,
    val visibility: String,
    val description: String?,
    @Json(name = "created_at") val createdAt: String,
    val hasVerifiedOwner: Boolean,
    val verifiedOwner: VerifiedOwner?,
    val userMembershipStatus: String,
    val userResidencyClaim: ResidencyClaim?,
    val memberCount: Int,
    val nearbyGigs: Int,
) {
    @JsonClass(generateAdapter = true)
    data class VerifiedOwner(
        val id: String,
        val username: String,
        val name: String,
        @Json(name = "first_name") val firstName: String,
        @Json(name = "last_name") val lastName: String,
        @Json(name = "profile_picture_url") val profilePictureUrl: String?,
    )

    @JsonClass(generateAdapter = true)
    data class ResidencyClaim(
        val id: String,
        val status: String,
        @Json(name = "created_at") val createdAt: String,
    )
}

/**
 * `POST /api/homes` request. Route: `backend/routes/home.js:677`. Supports
 * the commonly-used fields; callers pass any ATTOM payload through
 * [attomPropertyDetail] — its schema is provider-defined.
 */
@JsonClass(generateAdapter = true)
data class CreateHomeRequest(
    val address: String,
    @Json(name = "unit_number") val unitNumber: String? = null,
    val city: String,
    val state: String,
    @Json(name = "zip_code") val zipCode: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @Json(name = "home_type") val homeType: String? = null,
    val visibility: String? = null,
    val name: String? = null,
    val description: String? = null,
    @Json(name = "attom_property_detail") val attomPropertyDetail: JsonValue? = null,
)

/** `POST /api/homes` response — route `backend/routes/home.js:677`. */
@JsonClass(generateAdapter = true)
data class CreateHomeResponse(
    val message: String,
    val home: HomeDto,
    @Json(name = "requires_verification") val requiresVerification: Boolean,
    @Json(name = "verification_type") val verificationType: String?,
    val role: String,
)

/**
 * `POST /api/homes/property-suggestions` request. Route:
 * `backend/routes/home.js:540`.
 */
@JsonClass(generateAdapter = true)
data class PropertySuggestionsRequest(
    val address: String,
    @Json(name = "unit_number") val unitNumber: String? = null,
    val city: String,
    val state: String,
    @Json(name = "zip_code") val zipCode: String,
    @Json(name = "address_id") val addressId: String? = null,
    val classification: String? = null,
)

/**
 * `POST /api/homes/check-address` request. Route:
 * `backend/routes/home.js:555`.
 */
@JsonClass(generateAdapter = true)
data class CheckAddressRequest(
    @Json(name = "address_id") val addressId: String? = null,
    val address: String,
    @Json(name = "unit_number") val unitNumber: String? = null,
    val city: String,
    val state: String,
    @Json(name = "zip_code") val zipCode: String,
    val country: String? = null,
)

/** `POST /api/homes/check-address` response. */
@JsonClass(generateAdapter = true)
data class CheckAddressResponse(
    val exists: Boolean,
    val homeCount: Int,
    val hasVerifiedMembers: Boolean,
    @Json(name = "verdict_status") val verdictStatus: String?,
)
