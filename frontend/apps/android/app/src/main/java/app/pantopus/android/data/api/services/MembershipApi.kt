package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.membership.PersonaMembershipResponse
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * A10.8 fan-side membership. Backs the membership manage screen. Backend
 * keeps the persona / membership names on the wire; the UI renames at the VM
 * boundary. Mounted at `/api/personas/:id/membership` (`backend/app.js:367`).
 */
interface MembershipApi {
    /** `GET /api/personas/:id/membership` — the calling fan's own membership.
     *  Route `backend/routes/personaMembership.js:108`. */
    @GET("api/personas/{id}/membership")
    suspend fun membership(
        @Path("id") personaId: String,
    ): PersonaMembershipResponse

    /** `POST /api/personas/:id/membership/cancel` — no-charge cancel (free
     *  cancels immediately, paid flips `cancel_at_period_end`). Route
     *  `backend/routes/personaMembership.js:204`. */
    @POST("api/personas/{id}/membership/cancel")
    suspend fun cancel(
        @Path("id") personaId: String,
    ): PersonaMembershipResponse
}
