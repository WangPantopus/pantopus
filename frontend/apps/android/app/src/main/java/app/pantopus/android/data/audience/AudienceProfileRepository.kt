package app.pantopus.android.data.audience

import app.pantopus.android.data.api.models.audience.AudienceListResponse
import app.pantopus.android.data.api.models.audience.MembershipStatsResponse
import app.pantopus.android.data.api.models.audience.PersonaMeResponse
import app.pantopus.android.data.api.models.audience.PersonaPostsResponse
import app.pantopus.android.data.api.models.audience.PersonaThreadsResponse
import app.pantopus.android.data.api.models.audience.PersonaTiersResponse
import app.pantopus.android.data.api.models.audience.PublishUpdateBody
import app.pantopus.android.data.api.models.audience.PublishUpdateResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.AudienceProfileApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps `/api/personas/[*]` and `/api/broadcast/[*]` in [NetworkResult]. */
@Singleton
class AudienceProfileRepository
    @Inject
    constructor(
        private val api: AudienceProfileApi,
    ) {
        suspend fun me(): NetworkResult<PersonaMeResponse> = safeApiCall { api.me() }

        suspend fun audience(): NetworkResult<AudienceListResponse> = safeApiCall { api.audience() }

        suspend fun posts(handle: String): NetworkResult<PersonaPostsResponse> = safeApiCall { api.posts(handle) }

        suspend fun tiers(handle: String): NetworkResult<PersonaTiersResponse> = safeApiCall { api.tiers(handle) }

        suspend fun membershipStats(personaId: String): NetworkResult<MembershipStatsResponse> =
            safeApiCall { api.membershipStats(personaId) }

        suspend fun threads(personaId: String): NetworkResult<PersonaThreadsResponse> = safeApiCall { api.threads(personaId) }

        suspend fun publishUpdate(
            channelId: String,
            body: PublishUpdateBody,
        ): NetworkResult<PublishUpdateResponse> = safeApiCall { api.publishUpdate(channelId, body) }
    }
