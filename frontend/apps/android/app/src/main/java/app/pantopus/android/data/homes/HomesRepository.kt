package app.pantopus.android.data.homes

import app.pantopus.android.data.api.models.homes.CheckAddressRequest
import app.pantopus.android.data.api.models.homes.CreateHomeRequest
import app.pantopus.android.data.api.models.homes.InviteOwnerRequest
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.homes.PropertySuggestionsRequest
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
open class HomesRepository
    @Inject
    constructor(
        private val api: HomesApi,
    ) {
        /** `GET /api/homes/my-homes`. */
        open suspend fun myHomes(): NetworkResult<MyHomesResponse> = safeApiCall { api.myHomes() }

        /** `GET /api/homes/:id`. */
        open suspend fun detail(id: String) = safeApiCall { api.detail(id) }

        /** `GET /api/homes/:id/public-profile`. */
        open suspend fun publicProfile(id: String) = safeApiCall { api.publicProfile(id) }

        /** `POST /api/homes/property-suggestions`. */
        open suspend fun propertySuggestions(request: PropertySuggestionsRequest) = safeApiCall { api.propertySuggestions(request) }

        /** `POST /api/homes/check-address`. */
        open suspend fun checkAddress(request: CheckAddressRequest) = safeApiCall { api.checkAddress(request) }

        /** `POST /api/homes`. */
        open suspend fun create(request: CreateHomeRequest) = safeApiCall { api.create(request) }

        /** `POST /api/homes/:id/owners/invite`. */
        open suspend fun inviteOwner(
            homeId: String,
            request: InviteOwnerRequest,
        ) = safeApiCall { api.inviteOwner(homeId, request) }
    }
