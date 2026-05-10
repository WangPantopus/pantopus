package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.users.ProfileResponse
import app.pantopus.android.data.api.models.users.ProfileUpdateRequest
import app.pantopus.android.data.api.models.users.ProfileUpdateResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/** User profile routes from `backend/routes/users.js`. */
interface UsersApi {
    /** `GET /api/users/profile` — route `backend/routes/users.js:1427`. */
    @GET("api/users/profile")
    suspend fun profile(): ProfileResponse

    /** `PATCH /api/users/profile` — route `backend/routes/users.js:1503`. */
    @PATCH("api/users/profile")
    suspend fun updateProfile(
        @Body body: ProfileUpdateRequest,
    ): ProfileUpdateResponse
}
