package app.pantopus.android.data.hub

import app.pantopus.android.data.api.models.hub.HubDiscoveryResponse
import app.pantopus.android.data.api.models.hub.HubResponse
import app.pantopus.android.data.api.models.hub.HubTodayResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.HubApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps [HubApi] in the [NetworkResult] taxonomy. */
@Singleton
class HubRepository
    @Inject
    constructor(
        private val api: HubApi,
    ) {
        /** `GET /api/hub`. */
        suspend fun overview(): NetworkResult<HubResponse> = safeApiCall { api.overview() }

        /** `GET /api/hub/today`. */
        suspend fun today(): NetworkResult<HubTodayResponse> = safeApiCall { api.today() }

        /** `GET /api/hub/discovery?filter=gigs&limit=10`. */
        suspend fun discovery(
            filter: String = "gigs",
            limit: Int = 10,
        ): NetworkResult<HubDiscoveryResponse> = safeApiCall { api.discovery(filter = filter, limit = limit) }
    }
