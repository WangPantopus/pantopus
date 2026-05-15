package app.pantopus.android.data.relationships

import app.pantopus.android.data.api.models.relationships.ConnectionRequestBody
import app.pantopus.android.data.api.models.relationships.ConnectionRequestResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.RelationshipsApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps `/api/relationships/[*]` calls in the [NetworkResult] taxonomy. */
@Singleton
class RelationshipsRepository
    @Inject
    constructor(
        private val api: RelationshipsApi,
    ) {
        /**
         * `POST /api/relationships/requests` — send a connection request.
         * Route `backend/routes/relationships.js:67`.
         */
        suspend fun sendRequest(
            addresseeId: String,
            message: String? = null,
        ): NetworkResult<ConnectionRequestResponse> =
            safeApiCall {
                api.sendRequest(ConnectionRequestBody(addresseeId = addresseeId, message = message))
            }
    }
