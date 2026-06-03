package app.pantopus.android.data.professional

import app.pantopus.android.data.api.models.professional.ProfessionalProfileResponse
import app.pantopus.android.data.api.models.professional.ProfessionalProfileUpdateRequest
import app.pantopus.android.data.api.models.professional.ProfessionalVerificationStatusResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.ProfessionalApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps [ProfessionalApi] in the [NetworkResult] taxonomy. */
@Singleton
class ProfessionalRepository
    @Inject
    constructor(
        private val api: ProfessionalApi,
    ) {
        /** `GET /api/professional/profile/me`. */
        suspend fun profileMe(): NetworkResult<ProfessionalProfileResponse> = safeApiCall { api.profileMe() }

        /** `GET /api/professional/verification/status`. */
        suspend fun verificationStatus(): NetworkResult<ProfessionalVerificationStatusResponse> =
            safeApiCall { api.verificationStatus() }

        /** `PATCH /api/professional/profile/me`. */
        suspend fun updateProfileMe(
            body: ProfessionalProfileUpdateRequest,
        ): NetworkResult<ProfessionalProfileResponse> = safeApiCall { api.updateProfileMe(body) }
    }
