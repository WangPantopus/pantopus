package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.settings.PrivacyBlocksResponse
import app.pantopus.android.data.api.models.settings.PrivacySettingsResponse
import app.pantopus.android.data.api.models.settings.PrivacySettingsUpdate
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH

/** Privacy endpoints from `backend/routes/privacy.js`. */
interface PrivacyApi {
    /** `GET /api/privacy/settings` — route `backend/routes/privacy.js:50`. */
    @GET("api/privacy/settings")
    suspend fun settings(): PrivacySettingsResponse

    /** `PATCH /api/privacy/settings` — partial update.
     *  Route `backend/routes/privacy.js:95`. */
    @PATCH("api/privacy/settings")
    suspend fun updateSettings(
        @Body body: PrivacySettingsUpdate,
    ): PrivacySettingsResponse

    /** `GET /api/privacy/blocks` — route `backend/routes/privacy.js:154`. */
    @GET("api/privacy/blocks")
    suspend fun blocks(): PrivacyBlocksResponse
}
