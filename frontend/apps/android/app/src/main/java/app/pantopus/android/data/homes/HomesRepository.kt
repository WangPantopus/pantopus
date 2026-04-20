package app.pantopus.android.data.homes

import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.HomesApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [HomesApi] that returns the typed [NetworkResult]
 * taxonomy. ViewModels depend on this rather than Retrofit directly so
 * they can expose a single error surface to the UI.
 */
@Singleton
class HomesRepository
    @Inject
    constructor(
        private val api: HomesApi,
    ) {
        /** `GET /api/homes/my-homes`. */
        suspend fun myHomes(): NetworkResult<MyHomesResponse> = safeApiCall { api.myHomes() }

        /** `GET /api/homes/:id`. */
        suspend fun detail(id: String) = safeApiCall { api.detail(id) }

        /** `GET /api/homes/:id/public-profile`. */
        suspend fun publicProfile(id: String) = safeApiCall { api.publicProfile(id) }
    }
