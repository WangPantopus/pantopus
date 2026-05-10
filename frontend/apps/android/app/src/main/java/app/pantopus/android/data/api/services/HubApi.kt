package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.hub.HubDiscoveryResponse
import app.pantopus.android.data.api.models.hub.HubResponse
import app.pantopus.android.data.api.models.hub.HubTodayResponse
import retrofit2.http.GET
import retrofit2.http.Query

/** Hub routes from `backend/routes/hub.js`. */
interface HubApi {
    /** `GET /api/hub` — route `backend/routes/hub.js:24`. */
    @GET("api/hub")
    suspend fun overview(): HubResponse

    /** `GET /api/hub/today` — route `backend/routes/hub.js:596`. */
    @GET("api/hub/today")
    suspend fun today(): HubTodayResponse

    /** `GET /api/hub/discovery` — route `backend/routes/hub.js:720`. */
    @GET("api/hub/discovery")
    suspend fun discovery(
        @Query("filter") filter: String,
        @Query("lat") lat: Double? = null,
        @Query("lng") lng: Double? = null,
        @Query("limit") limit: Int = 10,
    ): HubDiscoveryResponse
}
