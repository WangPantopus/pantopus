@file:Suppress("PackageNaming")

package app.pantopus.android.data.support_trains

import app.pantopus.android.data.api.models.support_trains.AddSupportTrainSlotBody
import app.pantopus.android.data.api.models.support_trains.CreateSupportTrainBody
import app.pantopus.android.data.api.models.support_trains.CreateSupportTrainResponse
import app.pantopus.android.data.api.models.support_trains.SupportTrainDetailDto
import app.pantopus.android.data.api.models.support_trains.SupportTrainReservationsResponse
import app.pantopus.android.data.api.models.support_trains.SupportTrainUpdateBody
import app.pantopus.android.data.api.models.support_trains.SupportTrainsListResponse
import app.pantopus.android.data.api.models.support_trains.SupportTrainsNearbyResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.SupportTrainsApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps `/api/support-trains/[*]` calls in the [NetworkResult] taxonomy. */
@Singleton
class SupportTrainsRepository
    @Inject
    constructor(
        private val api: SupportTrainsApi,
    ) {
        /**
         * `GET /api/support-trains/me/support-trains` — list trains
         * I participate in (organizer or helper). Route
         * `backend/routes/supportTrains.js:445`.
         */
        suspend fun mine(
            role: String? = null,
            status: String? = null,
            limit: Int = 20,
            offset: Int = 0,
        ): NetworkResult<SupportTrainsListResponse> =
            safeApiCall {
                api.mine(role = role, status = status, limit = limit, offset = offset)
            }

        /**
         * `GET /api/support-trains/nearby` — nearby feed.
         * Route `backend/routes/supportTrains.js:570`.
         */
        suspend fun nearby(
            latitude: Double,
            longitude: Double,
            radiusMeters: Double? = null,
            limit: Int = 40,
        ): NetworkResult<SupportTrainsNearbyResponse> =
            safeApiCall {
                api.nearby(latitude = latitude, longitude = longitude, radiusMeters = radiusMeters, limit = limit)
            }

        /**
         * `GET /api/support-trains/:id/reservations` — organizer-only
         * reservations feed. Route
         * `backend/routes/supportTrains.js:3306`.
         */
        suspend fun reservations(supportTrainId: String): NetworkResult<SupportTrainReservationsResponse> =
            safeApiCall { api.reservations(supportTrainId) }

        /**
         * `POST /api/support-trains/` — create draft. Route
         * `backend/routes/supportTrains.js:639`. P2.6.
         */
        suspend fun create(body: CreateSupportTrainBody): NetworkResult<CreateSupportTrainResponse> = safeApiCall { api.create(body) }

        /**
         * `POST /api/support-trains/:id/slots` — append one custom slot.
         * Route `backend/routes/supportTrains.js:921`. P2.6.
         */
        suspend fun addSlot(
            supportTrainId: String,
            body: AddSupportTrainSlotBody,
        ): NetworkResult<Unit> =
            safeApiCall {
                api.addSlot(supportTrainId, body).close()
            }

        /**
         * `POST /api/support-trains/:id/publish` — publish a draft.
         * Route `backend/routes/supportTrains.js:1236`. P2.6.
         */
        suspend fun publish(supportTrainId: String): NetworkResult<Unit> =
            safeApiCall {
                api.publish(supportTrainId).close()
            }

        /**
         * `GET /api/support-trains/:id` — participant-facing detail (A10.9
         * Detail / A13.13 Manage). Route
         * `backend/routes/supportTrains.js:3444`.
         */
        suspend fun detail(supportTrainId: String): NetworkResult<SupportTrainDetailDto> = safeApiCall { api.detail(supportTrainId) }

        /**
         * `POST /api/support-trains/:id/updates` — broadcast an update.
         * Route `backend/routes/supportTrains.js:1581`.
         */
        suspend fun postUpdate(
            supportTrainId: String,
            body: SupportTrainUpdateBody,
        ): NetworkResult<Unit> =
            safeApiCall {
                api.postUpdate(supportTrainId, body).close()
            }

        /**
         * `POST /api/support-trains/:id/complete` — mark the train
         * completed. Route `backend/routes/supportTrains.js:1508`.
         */
        suspend fun complete(supportTrainId: String): NetworkResult<Unit> =
            safeApiCall {
                api.complete(supportTrainId).close()
            }
    }
