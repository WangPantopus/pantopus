package app.pantopus.android.data.businesses

import app.pantopus.android.data.api.models.businesses.BusinessDetailResponse
import app.pantopus.android.data.api.models.businesses.BusinessPublicResponse
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
    }
