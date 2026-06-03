package app.pantopus.android.data.membership

import app.pantopus.android.data.api.models.membership.PersonaMembershipResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.MembershipApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps `/api/personas/:id/membership` reads + cancel in [NetworkResult]. */
@Singleton
class MembershipRepository
    @Inject
    constructor(
        private val api: MembershipApi,
    ) {
        suspend fun membership(personaId: String): NetworkResult<PersonaMembershipResponse> =
            safeApiCall { api.membership(personaId) }

        /** Single-tap cancel — no charge (Phase-1 safe). */
        suspend fun cancel(personaId: String): NetworkResult<PersonaMembershipResponse> =
            safeApiCall { api.cancel(personaId) }
    }
