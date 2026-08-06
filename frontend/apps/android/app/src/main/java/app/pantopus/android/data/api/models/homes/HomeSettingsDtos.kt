@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.homes

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** `POST /api/homes/:id/move-out` — route `backend/routes/home.js:3563`. */
@JsonClass(generateAdapter = true)
data class MoveOutResponse(
    val message: String,
    @Json(name = "homeId") val homeId: String? = null,
)

/** `DELETE /api/homes/:id/ownership-claims/:claimId`. */
@JsonClass(generateAdapter = true)
data class DeleteOwnershipClaimResponse(
    val ok: Boolean = true,
    val deleted: Boolean = true,
)

// No property-correction DTOs: the backend exposes no correction endpoint,
// and `PropertyCorrectionScreen` says so rather than faking a submit.
