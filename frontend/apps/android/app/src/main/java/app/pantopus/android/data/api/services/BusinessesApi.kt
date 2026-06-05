package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.businesses.BusinessDashboardResponse
import app.pantopus.android.data.api.models.businesses.BusinessDetailResponse
import app.pantopus.android.data.api.models.businesses.BusinessFollowResponse
import app.pantopus.android.data.api.models.businesses.BusinessInsightsResponse
import app.pantopus.android.data.api.models.businesses.BusinessOwnerReviewsResponse
import app.pantopus.android.data.api.models.businesses.BusinessPublicResponse
import app.pantopus.android.data.api.models.businesses.BusinessReviewRespondRequest
import app.pantopus.android.data.api.models.businesses.MyBusinessesResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Owner / staff endpoints under the `/api/businesses/` namespace. Distinct from
 * [BusinessDiscoveryApi] which covers public search / nearby.
 *
 * T6.3f / P14 — `myBusinesses()` backs the My businesses screen.
 * P1.6 — `business()` + `publicBusiness()` back the Business Profile screen.
 */
interface BusinessesApi {
    /**
     * `GET /api/businesses/my-businesses` — every business the current
     * user owns or staffs (via BusinessSeat or legacy BusinessTeam).
     * Route `backend/routes/businesses.js:682`.
     */
    @GET("api/businesses/my-businesses")
    suspend fun myBusinesses(): MyBusinessesResponse

    /**
     * `GET /api/businesses/:businessId` — authenticated detail fetch.
     * Returns `business + profile + locations + access`. The `access`
     * block tells the caller whether the viewer owns / staffs the
     * business; for a non-owning viewer the rest of the response still
     * renders. Route `backend/routes/businesses.js:912`.
     */
    @GET("api/businesses/{businessId}")
    suspend fun business(
        @Path("businessId") businessId: String,
    ): BusinessDetailResponse

    /**
     * `GET /api/businesses/public/:username` — unauthenticated public
     * view. Used by the Business Profile screen to fold in `hours` +
     * `catalog` once the username is known from the detail fetch. 404s
     * for unpublished businesses; the repository absorbs that silently.
     * Route `backend/routes/businesses.js:3277`.
     */
    @GET("api/businesses/public/{username}")
    suspend fun publicBusiness(
        @Path("username") username: String,
    ): BusinessPublicResponse

    /**
     * `GET /api/businesses/:businessId/dashboard` — the owner-scoped fetch:
     * publish state, edit recency, and the onboarding checklist behind the
     * owner dashboard's profile-strength card. 403s for a viewer with no
     * access. Route `backend/routes/businesses.js:979`.
     */
    @GET("api/businesses/{businessId}/dashboard")
    suspend fun dashboard(
        @Path("businessId") businessId: String,
    ): BusinessDashboardResponse

    /**
     * `GET /api/businesses/:businessId/insights` — owner analytics (views /
     * followers / reviews + week-over-week trends) behind the dashboard's
     * "This week" tiles. `period` is `7d | 30d | 90d`. Route
     * `backend/routes/businesses.js:3915`.
     */
    @GET("api/businesses/{businessId}/insights")
    suspend fun insights(
        @Path("businessId") businessId: String,
        @Query("period") period: String = "30d",
    ): BusinessInsightsResponse

    /**
     * `GET /api/businesses/:businessId/reviews` — owner reviews list (enriched
     * with reviewer + gig + any published owner response) behind the reply
     * composer. Route `backend/routes/businesses.js:3441`.
     */
    @GET("api/businesses/{businessId}/reviews")
    suspend fun reviews(
        @Path("businessId") businessId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
    ): BusinessOwnerReviewsResponse

    /**
     * `POST /api/businesses/:businessId/reviews/:reviewId/respond` — save or
     * update the owner's reply on a review. Route
     * `backend/routes/businesses.js:3552`.
     */
    @POST("api/businesses/{businessId}/reviews/{reviewId}/respond")
    suspend fun respondToReview(
        @Path("businessId") businessId: String,
        @Path("reviewId") reviewId: String,
        @Body body: BusinessReviewRespondRequest,
    )

    /**
     * `POST /api/businesses/:businessId/follow` — save/follow a public
     * business. Route `backend/routes/businesses.js:3621`.
     */
    @POST("api/businesses/{businessId}/follow")
    suspend fun follow(
        @Path("businessId") businessId: String,
    ): BusinessFollowResponse
}
