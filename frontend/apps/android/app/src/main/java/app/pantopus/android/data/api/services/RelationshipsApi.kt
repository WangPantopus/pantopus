package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.relationships.ConnectionRequestBody
import app.pantopus.android.data.api.models.relationships.ConnectionRequestResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * `/api/relationships/[*]` — trust-graph endpoints from
 * `backend/routes/relationships.js`. Mounted at `/api/relationships`
 * (see `backend/app.js:347`).
 */
interface RelationshipsApi {
    /**
     * `POST /api/relationships/requests` — send a connection request
     * to another user. Route `backend/routes/relationships.js:67`.
     */
    @POST("api/relationships/requests")
    suspend fun sendRequest(
        @Body body: ConnectionRequestBody,
    ): ConnectionRequestResponse

    /**
     * `POST /api/relationships/:id/accept` — accept an inbound request.
     * Route `backend/routes/relationships.js:217`.
     */
    @POST("api/relationships/{id}/accept")
    suspend fun accept(
        @Path("id") id: String,
    ): Unit

    /**
     * `POST /api/relationships/:id/reject` — decline an inbound request.
     * Route `backend/routes/relationships.js:295`.
     */
    @POST("api/relationships/{id}/reject")
    suspend fun reject(
        @Path("id") id: String,
    ): Unit
}
