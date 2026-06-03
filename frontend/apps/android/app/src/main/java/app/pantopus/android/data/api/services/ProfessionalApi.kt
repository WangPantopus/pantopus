package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.professional.ProfessionalProfileResponse
import app.pantopus.android.data.api.models.professional.ProfessionalProfileUpdateRequest
import app.pantopus.android.data.api.models.professional.ProfessionalVerificationStatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/** Professional-profile routes from `backend/routes/professional.js`. */
interface ProfessionalApi {
    /** `GET /api/professional/profile/me` — route `professional.js:164`. */
    @GET("api/professional/profile/me")
    suspend fun profileMe(): ProfessionalProfileResponse

    /** `PATCH /api/professional/profile/me` — route `professional.js:190`. */
    @PATCH("api/professional/profile/me")
    suspend fun updateProfileMe(
        @Body body: ProfessionalProfileUpdateRequest,
    ): ProfessionalProfileResponse

    /** `GET /api/professional/verification/status` — route `professional.js:372`. */
    @GET("api/professional/verification/status")
    suspend fun verificationStatus(): ProfessionalVerificationStatusResponse
}
