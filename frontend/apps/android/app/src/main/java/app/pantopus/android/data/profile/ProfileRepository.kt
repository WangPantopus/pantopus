package app.pantopus.android.data.profile

import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.models.users.ProfileResponse
import app.pantopus.android.data.api.models.users.UserStatsDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.UsersApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the user-profile routes in the [NetworkResult] taxonomy. */
@Singleton
class ProfileRepository
    @Inject
    constructor(
        private val api: UsersApi,
    ) {
        /** `GET /api/users/id/:id` — route `backend/routes/users.js:2041`. */
        suspend fun publicProfile(id: String): NetworkResult<PublicProfileDto> = safeApiCall { api.publicProfile(id) }

        /** `GET /api/users/profile` — route `backend/routes/users.js:1962`. */
        suspend fun ownProfile(): NetworkResult<ProfileResponse> = safeApiCall { api.profile() }

        /** `GET /api/users/:id/stats` — route `backend/routes/users.js:2787`. */
        suspend fun stats(userId: String): NetworkResult<UserStatsDto> = safeApiCall { api.stats(userId) }
    }
