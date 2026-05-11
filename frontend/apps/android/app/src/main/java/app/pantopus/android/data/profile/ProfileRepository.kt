package app.pantopus.android.data.profile

import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.UsersApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the public-profile route in the [NetworkResult] taxonomy. */
@Singleton
class ProfileRepository
    @Inject
    constructor(
        private val api: UsersApi,
    ) {
        /** `GET /api/users/id/:id` — route `backend/routes/users.js:2041`. */
        suspend fun publicProfile(id: String): NetworkResult<PublicProfileDto> = safeApiCall { api.publicProfile(id) }
    }
