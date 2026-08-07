package app.pantopus.android.data.homes

import app.pantopus.android.data.api.models.homes.ChangeMemberRoleRequest
import app.pantopus.android.data.api.models.homes.ChangeMemberRoleResponse
import app.pantopus.android.data.api.models.homes.DeleteHomeResponse
import app.pantopus.android.data.api.models.homes.HomeAccessDto
import app.pantopus.android.data.api.models.homes.HouseholdAccessRequestActionResponse
import app.pantopus.android.data.api.models.homes.HouseholdAccessRequestsResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.HomeAdminApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [HomeAdminApi] returning the typed [NetworkResult]
 * taxonomy so view-models expose a single error surface to the UI.
 */
@Singleton
open class HomeAdminRepository
    @Inject
    constructor(
        private val api: HomeAdminApi,
    ) {
        /** `DELETE /api/homes/:id`. */
        open suspend fun deleteHome(homeId: String): NetworkResult<DeleteHomeResponse> =
            safeApiCall { api.deleteHome(homeId) }

        /** `GET /api/homes/:id/me`. */
        open suspend fun myAccess(homeId: String): NetworkResult<HomeAccessDto> = safeApiCall { api.myAccess(homeId) }

        /** `POST /api/homes/:id/members/:userId/role`. */
        open suspend fun changeMemberRole(
            homeId: String,
            userId: String,
            roleBase: String,
        ): NetworkResult<ChangeMemberRoleResponse> =
            safeApiCall {
                api.changeMemberRole(homeId, userId, ChangeMemberRoleRequest(roleBase = roleBase))
            }

        /** `GET /api/homes/:id/household-access-requests?status=…`. */
        open suspend fun householdAccessRequests(
            homeId: String,
            status: String = "pending",
        ): NetworkResult<HouseholdAccessRequestsResponse> =
            safeApiCall { api.householdAccessRequests(homeId, status) }

        /** `POST …/household-access-requests/:requestId/approve`. */
        open suspend fun approveHouseholdAccessRequest(
            homeId: String,
            requestId: String,
        ): NetworkResult<HouseholdAccessRequestActionResponse> =
            safeApiCall { api.approveHouseholdAccessRequest(homeId, requestId) }

        /** `POST …/household-access-requests/:requestId/reject`. */
        open suspend fun rejectHouseholdAccessRequest(
            homeId: String,
            requestId: String,
        ): NetworkResult<HouseholdAccessRequestActionResponse> =
            safeApiCall { api.rejectHouseholdAccessRequest(homeId, requestId) }
    }
