package app.pantopus.android.data.api.models.reviews

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Body for `POST /api/reviews` — route `backend/routes/reviews.js:35`.
 *
 * Backend constraints: gig must be `completed`, reviewer must be the
 * gig owner or the accepted worker, and only one review per gig per
 * reviewer is allowed. `rating` is `1..5`.
 */
@JsonClass(generateAdapter = true)
data class CreateReviewBody(
    @Json(name = "gig_id") val gigId: String,
    @Json(name = "reviewee_id") val revieweeId: String,
    val rating: Int,
    val comment: String? = null,
)

/**
 * Response envelope for `POST /api/reviews`. The backend wraps the new
 * row inside `{ review: {...} }`, but the screen only needs to know
 * the call succeeded — so this DTO carries the minimum shape.
 */
@JsonClass(generateAdapter = true)
data class CreateReviewResponse(
    val id: String? = null,
    @Json(name = "gig_id") val gigId: String? = null,
    val rating: Int? = null,
)
