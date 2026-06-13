package app.pantopus.android.data.ai

import app.pantopus.android.data.api.models.ai.AIDraftListingVisionRequest
import app.pantopus.android.data.api.models.ai.AIListingVisionResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.AIApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the single-turn `/api/ai/draft/[*]` routes in the [NetworkResult] taxonomy. */
@Singleton
class AIDraftRepository
    @Inject
    constructor(
        private val api: AIApi,
    ) {
        /** A12.9 Snap & Sell — vision draft from photo data URLs (max 5). */
        suspend fun draftListingVision(request: AIDraftListingVisionRequest): NetworkResult<AIListingVisionResponse> =
            safeApiCall { api.draftListingVision(request) }
    }
