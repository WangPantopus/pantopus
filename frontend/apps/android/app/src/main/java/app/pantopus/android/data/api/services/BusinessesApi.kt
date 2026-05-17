package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.businesses.MyBusinessesResponse
import retrofit2.http.GET

/**
 * Owner / staff endpoints under `/api/businesses/*`. Distinct from
 * [BusinessDiscoveryApi] which covers public search / nearby.
 *
 * T6.3f / P14 — `myBusinesses()` backs the My businesses screen.
 */
interface BusinessesApi {
    /**
     * `GET /api/businesses/my-businesses` — every business the current
     * user owns or staffs (via BusinessSeat or legacy BusinessTeam).
     * Route `backend/routes/businesses.js:682`.
     */
    @GET("api/businesses/my-businesses")
    suspend fun myBusinesses(): MyBusinessesResponse
}
