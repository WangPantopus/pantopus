@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.homes

import app.pantopus.android.data.api.models.common.JsonValue
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One row from `GET /api/homes/my-ownership-claims` —
 * `backend/routes/homeOwnership.js:217`. Backend masks the internal
 * state to a generic `status` string for the opaque-handshake contract.
 */
@JsonClass(generateAdapter = true)
data class OwnershipClaimDto(
    val id: String,
    @Json(name = "home_id") val homeId: String,
    @Json(name = "claim_type") val claimType: String,
    val method: String,
    val status: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "updated_at") val updatedAt: String,
)

/** Envelope for `GET /api/homes/my-ownership-claims`. */
@JsonClass(generateAdapter = true)
data class MyOwnershipClaimsResponse(
    val claims: List<OwnershipClaimDto>,
)

/**
 * Body for `POST /api/homes/:id/ownership-claims` —
 * `backend/routes/homeOwnership.js:251`. Mirrors `submitClaimSchema`
 * (`backend/routes/homeOwnership.js:33`). Backend rejects extra fields,
 * so the wizard's optional reviewer note is piped through evidence
 * metadata instead.
 */
@JsonClass(generateAdapter = true)
data class SubmitClaimRequest(
    @Json(name = "claim_type") val claimType: String = "owner",
    val method: String,
)

/** Inner claim envelope returned by the submit endpoint. */
@JsonClass(generateAdapter = true)
data class SubmitClaimEnvelope(
    val id: String? = null,
    val status: String,
)

/** Outer envelope for `POST /api/homes/:id/ownership-claims`. */
@JsonClass(generateAdapter = true)
data class SubmitClaimResponse(
    val message: String,
    val claim: SubmitClaimEnvelope,
    @Json(name = "next_step") val nextStep: String? = null,
)

/**
 * Body for `POST /api/homes/:id/ownership-claims/:claimId/evidence`
 * — `backend/routes/homeOwnership.js:886`. Mirrors
 * `uploadEvidenceSchema` (`backend/routes/homeOwnership.js:43`).
 * `storageRef` carries the URL produced by `/api/files/upload`.
 */
@JsonClass(generateAdapter = true)
data class UploadEvidenceRequest(
    @Json(name = "evidence_type") val evidenceType: String,
    val provider: String = "manual",
    @Json(name = "storage_ref") val storageRef: String? = null,
    val metadata: Map<String, String>? = null,
)

/** Response for the evidence endpoint. Both fields are loosely shaped. */
@JsonClass(generateAdapter = true)
data class UploadEvidenceResponse(
    val evidence: JsonValue,
    @Json(name = "verification_tier") val verificationTier: JsonValue? = null,
)

/** Response for `POST /api/files/upload` — `backend/routes/files.js:781`. */
@JsonClass(generateAdapter = true)
data class FileUploadResponse(
    val message: String,
    val file: FileRef,
) {
    @JsonClass(generateAdapter = true)
    data class FileRef(
        val id: String,
        val url: String,
    )
}
