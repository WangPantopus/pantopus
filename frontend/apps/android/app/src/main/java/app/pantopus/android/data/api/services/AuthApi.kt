package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.auth.AuthMessageResponse
import app.pantopus.android.data.api.models.auth.ForgotPasswordRequest
import app.pantopus.android.data.api.models.auth.LoginRequest
import app.pantopus.android.data.api.models.auth.LoginResponse
import app.pantopus.android.data.api.models.auth.RefreshRequest
import app.pantopus.android.data.api.models.auth.RefreshResponse
import app.pantopus.android.data.api.models.auth.RegisterRequest
import app.pantopus.android.data.api.models.auth.RegisterResponse
import app.pantopus.android.data.api.models.auth.ResendVerificationRequest
import app.pantopus.android.data.api.models.auth.ResetPasswordRequest
import app.pantopus.android.data.api.models.auth.VerifyEmailRequest
import app.pantopus.android.data.api.models.auth.VerifyEmailResponse
import retrofit2.http.Body
import retrofit2.http.POST

/** Auth routes from `backend/routes/users.js`. */
interface AuthApi {
    /** `POST /api/users/login` — route `backend/routes/users.js:1492`. */
    @POST("api/users/login")
    suspend fun login(
        @Body body: LoginRequest,
    ): LoginResponse

    /** `POST /api/users/register` — route `backend/routes/users.js:1177`. */
    @POST("api/users/register")
    suspend fun register(
        @Body body: RegisterRequest,
    ): RegisterResponse

    /** `POST /api/users/refresh` — route `backend/routes/users.js:1910`. */
    @POST("api/users/refresh")
    suspend fun refresh(
        @Body body: RefreshRequest,
    ): RefreshResponse

    /** `POST /api/users/forgot-password` — route `backend/routes/users.js:3197`. */
    @POST("api/users/forgot-password")
    suspend fun forgotPassword(
        @Body body: ForgotPasswordRequest,
    ): AuthMessageResponse

    /** `POST /api/users/reset-password` — route `backend/routes/users.js:3247`. */
    @POST("api/users/reset-password")
    suspend fun resetPassword(
        @Body body: ResetPasswordRequest,
    ): AuthMessageResponse

    /** `POST /api/users/verify-email` — route `backend/routes/users.js:3115`. */
    @POST("api/users/verify-email")
    suspend fun verifyEmail(
        @Body body: VerifyEmailRequest,
    ): VerifyEmailResponse

    /** `POST /api/users/resend-verification` — route `backend/routes/users.js:3049`. */
    @POST("api/users/resend-verification")
    suspend fun resendVerification(
        @Body body: ResendVerificationRequest,
    ): AuthMessageResponse
}
