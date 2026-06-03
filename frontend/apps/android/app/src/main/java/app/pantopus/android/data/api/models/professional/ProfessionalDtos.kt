package app.pantopus.android.data.api.models.professional

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * DTOs for `backend/routes/professional.js`. The backend professional record
 * is thin (headline / categories / pricing / verification); the editor maps
 * the overlapping fields.
 */

// GET /api/professional/profile/me — backend/routes/professional.js:164
// `{ profile: … | null }`; null means professional mode is off.
@JsonClass(generateAdapter = true)
data class ProfessionalProfileResponse(
    val profile: ProfessionalProfileDto? = null,
)

@JsonClass(generateAdapter = true)
data class ProfessionalProfileDto(
    val headline: String? = null,
    val bio: String? = null,
    val categories: List<String>? = null,
    @Json(name = "service_area") val serviceArea: ProfessionalServiceAreaDto? = null,
    @Json(name = "is_public") val isPublic: Boolean? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
    @Json(name = "verification_tier") val verificationTier: Int? = null,
    @Json(name = "verification_status") val verificationStatus: String? = null,
)

@JsonClass(generateAdapter = true)
data class ProfessionalServiceAreaDto(
    val city: String? = null,
    val state: String? = null,
)

// GET /api/professional/verification/status — backend/routes/professional.js:372
@JsonClass(generateAdapter = true)
data class ProfessionalVerificationStatusResponse(
    val tier: Int? = null,
    val status: String? = null,
    @Json(name = "submitted_at") val submittedAt: String? = null,
    @Json(name = "completed_at") val completedAt: String? = null,
)

// PATCH /api/professional/profile/me (request) — only the safe fields are sent;
// `categories` is enum-constrained server-side so free-text skills aren't
// written here. Null fields are omitted by Moshi (serializeNulls off).
@JsonClass(generateAdapter = true)
data class ProfessionalProfileUpdateRequest(
    val headline: String? = null,
    val bio: String? = null,
    @Json(name = "is_public") val isPublic: Boolean? = null,
    @Json(name = "is_active") val isActive: Boolean? = null,
)
