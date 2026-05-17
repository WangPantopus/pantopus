package app.pantopus.android.data.businesses

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
        open suspend fun myBusinesses(): NetworkResult<MyBusinessesResponse> =
            safeApiCall { api.myBusinesses() }
    }
