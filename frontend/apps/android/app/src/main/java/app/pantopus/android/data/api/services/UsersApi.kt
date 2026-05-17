package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.models.settings.AuthMethodsResponse
import app.pantopus.android.data.api.models.settings.PasswordUpdateBody
import app.pantopus.android.data.api.models.settings.ResendVerificationBody
import app.pantopus.android.data.api.models.users.ProfileResponse
import app.pantopus.android.data.api.models.users.ProfileUpdateRequest
import app.pantopus.android.data.api.models.users.ProfileUpdateResponse
import app.pantopus.android.data.api.models.users.UserStatsDto
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

/** User profile routes from `backend/routes/users.js`. */
interface UsersApi {
    /** `GET /api/users/profile` — route `backend/routes/users.js:1962`. */
    @GET("api/users/profile")
    suspend fun profile(): ProfileResponse

    /** `PATCH /api/users/profile` — route `backend/routes/users.js:2052`. */
    @PATCH("api/users/profile")
    suspend fun updateProfile(
        @Body body: ProfileUpdateRequest,
    ): ProfileUpdateResponse

    /** `GET /api/users/id/:id` — route `backend/routes/users.js:2041`. */
    @GET("api/users/id/{id}")
    suspend fun publicProfile(
        @Path("id") id: String,
    ): PublicProfileDto

    /** `GET /api/users/:id/stats` — route `backend/routes/users.js:2787`. */
    @GET("api/users/{id}/stats")
    suspend fun stats(
        @Path("id") id: String,
    ): UserStatsDto

    /** `GET /api/users/auth-methods` — route `backend/routes/users.js:1739`. */
    @GET("api/users/auth-methods")
    suspend fun authMethods(): AuthMethodsResponse

    /** `POST /api/users/password` — route `backend/routes/users.js:1771`.
     *  Rate-limited by `reauthLimiter`. */
    @POST("api/users/password")
    suspend fun updatePassword(
        @Body body: PasswordUpdateBody,
    )

    /** `POST /api/users/resend-verification` — route
     *  `backend/routes/users.js:3049`. Rate-limited by
     *  `resendVerificationLimiter`. */
    @POST("api/users/resend-verification")
    suspend fun resendVerification(
        @Body body: ResendVerificationBody,
    )
}
