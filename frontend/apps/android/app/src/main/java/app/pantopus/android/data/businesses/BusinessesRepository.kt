package app.pantopus.android.data.businesses

import app.pantopus.android.data.api.models.businesses.BusinessDashboardResponse
import app.pantopus.android.data.api.models.businesses.BusinessDetailResponse
import app.pantopus.android.data.api.models.businesses.BusinessFollowResponse
import app.pantopus.android.data.api.models.businesses.BusinessInsightsResponse
import app.pantopus.android.data.api.models.businesses.BusinessOwnerReviewsResponse
import app.pantopus.android.data.api.models.businesses.BusinessPublicResponse
import app.pantopus.android.data.api.models.businesses.BusinessReviewRespondRequest
import app.pantopus.android.data.api.models.businesses.MyBusinessesResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.BusinessesApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the `/api/businesses` owner / staff endpoints in the
 *  [NetworkResult] taxonomy. */
@Singleton
open class BusinessesRepository
    @Inject
    constructor(
        private val api: BusinessesApi,
    ) {
        /** T6.3f / P14 — backs My businesses. Owner + staff seats. */
        open suspend fun myBusinesses(): NetworkResult<MyBusinessesResponse> = safeApiCall { api.myBusinesses() }

        /** P1.6 — backs the Business Profile detail fetch. */
        open suspend fun business(businessId: String): NetworkResult<BusinessDetailResponse> = safeApiCall { api.business(businessId) }

        /** P1.6 — best-effort public payload used to fold hours + catalog
         *  into the Business Profile screen. Callers expect this to
         *  fail silently for unpublished businesses. */
        open suspend fun publicBusiness(username: String): NetworkResult<BusinessPublicResponse> =
            safeApiCall { api.publicBusiness(username) }

        /** P1-C — owner-scoped dashboard (publish state + onboarding). */
        open suspend fun dashboard(businessId: String): NetworkResult<BusinessDashboardResponse> = safeApiCall { api.dashboard(businessId) }

        /** P1-C — owner analytics behind the "This week" tiles. */
        open suspend fun insights(
            businessId: String,
            period: String = "30d",
        ): NetworkResult<BusinessInsightsResponse> = safeApiCall { api.insights(businessId, period) }

        /** P1-C — owner reviews list behind the reply composer. */
        open suspend fun reviews(businessId: String): NetworkResult<BusinessOwnerReviewsResponse> = safeApiCall { api.reviews(businessId) }

        /** P1-C — save / update the owner's reply on a review. */
        open suspend fun respondToReview(
            businessId: String,
            reviewId: String,
            response: String,
        ): NetworkResult<Unit> = safeApiCall { api.respondToReview(businessId, reviewId, BusinessReviewRespondRequest(response)) }

        /** P1.6 — save/follow a public business profile. */
        open suspend fun followBusiness(businessId: String): NetworkResult<BusinessFollowResponse> = safeApiCall { api.follow(businessId) }
    }
