package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.reviews.CreateReviewBody
import app.pantopus.android.data.api.models.reviews.CreateReviewResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Reviews endpoints from `backend/routes/reviews.js`. Mounted at
 * `/api/reviews` (see `backend/app.js`). Used by P3.4 Leave Review.
 */
interface ReviewsApi {
    /**
     * `POST /api/reviews` — create a review for a completed gig. Route
     * `backend/routes/reviews.js:35`. Backend rejects with 400 / 403 /
     * 409 when the caller isn't authorised or has already reviewed
     * this gig.
     */
    @POST("api/reviews")
    suspend fun create(
        @Body body: CreateReviewBody,
    ): CreateReviewResponse
}
