package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.auth.LoginRequest
import app.pantopus.android.data.api.models.auth.LoginResponse
import app.pantopus.android.data.api.models.auth.RefreshRequest
import app.pantopus.android.data.api.models.auth.RefreshResponse
import retrofit2.http.Body
import retrofit2.http.POST

/** Auth routes from `backend/routes/users.js` (login / refresh). */
interface AuthApi {
    /** `POST /api/users/login` — route `backend/routes/users.js:955`. */
    @POST("api/users/login")
    suspend fun login(
        @Body body: LoginRequest,
    ): LoginResponse

    /** `POST /api/users/refresh` — route `backend/routes/users.js:1370`. */
    @POST("api/users/refresh")
    suspend fun refresh(
        @Body body: RefreshRequest,
    ): RefreshResponse
}
