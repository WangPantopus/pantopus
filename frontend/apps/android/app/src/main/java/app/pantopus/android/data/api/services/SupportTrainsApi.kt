package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.support_trains.AddSupportTrainSlotBody
import app.pantopus.android.data.api.models.support_trains.CreateSupportTrainBody
import app.pantopus.android.data.api.models.support_trains.CreateSupportTrainResponse
import app.pantopus.android.data.api.models.support_trains.SupportTrainDetailDto
import app.pantopus.android.data.api.models.support_trains.SupportTrainReservationsResponse
import app.pantopus.android.data.api.models.support_trains.SupportTrainUpdateBody
import app.pantopus.android.data.api.models.support_trains.SupportTrainsListResponse
import app.pantopus.android.data.api.models.support_trains.SupportTrainsNearbyResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * `/api/support-trains/[*]` — Support Trains (mutual-aid) endpoints
 * from `backend/routes/supportTrains.js`. The full surface area is 39
 * routes; this file wires the two list-feed endpoints powering the
 * My-trains / Nearby tabs and the organizer-only reservations feed
 * driving the Review-signups screen. Additional endpoints (slot
 * reservation, organizer mutations) are folded in additively as the
 * detail / wizard surfaces ship.
 */
interface SupportTrainsApi {
    /**
     * `GET /api/support-trains/me/support-trains` — list trains the
     * caller participates in (organizer or helper). Route
     * `backend/routes/supportTrains.js:445`.
     */
    @GET("api/support-trains/me/support-trains")
    suspend fun mine(
        @Query("role") role: String? = null,
        @Query("status") status: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
    ): SupportTrainsListResponse

    /**
     * `GET /api/support-trains/nearby` — list trains visible nearby
     * (default 25 mi radius). Route
     * `backend/routes/supportTrains.js:570`.
     */
    @GET("api/support-trains/nearby")
    suspend fun nearby(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radius_meters") radiusMeters: Double? = null,
        @Query("limit") limit: Int = 40,
    ): SupportTrainsNearbyResponse

    /**
     * `GET /api/support-trains/:id/reservations` — organizer-only feed
     * of pending / confirmed helper reservations. Route
     * `backend/routes/supportTrains.js:3306`.
     */
    @GET("api/support-trains/{id}/reservations")
    suspend fun reservations(
        @Path("id") supportTrainId: String,
    ): SupportTrainReservationsResponse

    /**
     * `POST /api/support-trains/` — create a new Support Train
     * (status `draft`). P2.6 — the Start-a-Support-Train wizard fires
     * this on launch, then [addSlot] for each generated slot, then
     * [publish]. Route `backend/routes/supportTrains.js:639`.
     */
    @POST("api/support-trains")
    suspend fun create(
        @Body body: CreateSupportTrainBody,
    ): CreateSupportTrainResponse

    /**
     * `POST /api/support-trains/:id/slots` — append one custom slot.
     * The wizard calls this once per generated slot. Route
     * `backend/routes/supportTrains.js:921`.
     */
    @POST("api/support-trains/{id}/slots")
    suspend fun addSlot(
        @Path("id") supportTrainId: String,
        @Body body: AddSupportTrainSlotBody,
    ): ResponseBody

    /**
     * `POST /api/support-trains/:id/publish` — flip the draft to
     * `published` so neighbors / connections can sign up. Fires last
     * in the wizard's launch sequence. Route
     * `backend/routes/supportTrains.js:1236`.
     */
    @POST("api/support-trains/{id}/publish")
    suspend fun publish(
        @Path("id") supportTrainId: String,
    ): ResponseBody

    /**
     * `GET /api/support-trains/:id` — participant-facing detail (A10.9).
     * Privacy-gated: slots / my-reservations / updates / organizers are
     * scoped to the viewer's role. Route
     * `backend/routes/supportTrains.js:3444`.
     *
     * PREFIX NOTE: this client family targets `/api/support-trains/{...}`
     * (matching the shipped list/create siblings), while the Express router
     * is mounted at `/api/activities/support-trains` (`backend/app.js:398`).
     * Kept consistent with the siblings; see the P1-E delivery notes.
     */
    @GET("api/support-trains/{id}")
    suspend fun detail(
        @Path("id") supportTrainId: String,
    ): SupportTrainDetailDto

    /**
     * `POST /api/support-trains/:id/updates` — broadcast an update to the
     * train's helpers (A13.13 Manage → Send update). Route
     * `backend/routes/supportTrains.js:1581`.
     */
    @POST("api/support-trains/{id}/updates")
    suspend fun postUpdate(
        @Path("id") supportTrainId: String,
        @Body body: SupportTrainUpdateBody,
    ): ResponseBody

    /**
     * `POST /api/support-trains/:id/complete` — mark the train completed
     * (A13.13 Manage → Close train). Route
     * `backend/routes/supportTrains.js:1508`.
     */
    @POST("api/support-trains/{id}/complete")
    suspend fun complete(
        @Path("id") supportTrainId: String,
    ): ResponseBody
}
