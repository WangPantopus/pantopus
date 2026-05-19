package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.businesses.BusinessDetailResponse
import app.pantopus.android.data.api.models.businesses.BusinessPublicResponse
import app.pantopus.android.data.api.models.businesses.MyBusinessesResponse
import retrofit2.http.GET
import retrofit2.http.Path

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
}
