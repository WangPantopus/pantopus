package app.pantopus.android.data.support_trains

import app.pantopus.android.data.api.models.support_trains.SupportTrainReservationsResponse
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
    }
