package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.common.JsonValue
import app.pantopus.android.data.api.models.homes.CheckAddressRequest
import app.pantopus.android.data.api.models.homes.CheckAddressResponse
import app.pantopus.android.data.api.models.homes.CreateHomeRequest
import app.pantopus.android.data.api.models.homes.CreateHomeResponse
import app.pantopus.android.data.api.models.homes.HomeDetailResponse
import app.pantopus.android.data.api.models.homes.HomePublicProfileResponse
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.homes.PropertySuggestionsRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** Home routes from `backend/routes/home.js`. */
interface HomesApi {
    /** `GET /api/homes/my-homes` — route `backend/routes/home.js:1464`. */
    @GET("api/homes/my-homes")
    suspend fun myHomes(): MyHomesResponse

    /** `GET /api/homes/:id` — route `backend/routes/home.js:2891`. */
    @GET("api/homes/{id}")
    suspend fun detail(
        @Path("id") id: String,
    ): HomeDetailResponse

    /** `GET /api/homes/:id/public-profile` — route `backend/routes/home.js:2439`. */
    @GET("api/homes/{id}/public-profile")
    suspend fun publicProfile(
        @Path("id") id: String,
    ): HomePublicProfileResponse

    /** `POST /api/homes` — route `backend/routes/home.js:677`. */
    @POST("api/homes")
    suspend fun create(
        @Body body: CreateHomeRequest,
    ): CreateHomeResponse

    /**
     * `POST /api/homes/property-suggestions` — route `backend/routes/home.js:540`.
     * Returns an ATTOM-provided bundle whose shape is provider-defined.
     */
    @POST("api/homes/property-suggestions")
    suspend fun propertySuggestions(
        @Body body: PropertySuggestionsRequest,
    ): JsonValue

    /** `POST /api/homes/check-address` — route `backend/routes/home.js:555`. */
    @POST("api/homes/check-address")
    suspend fun checkAddress(
        @Body body: CheckAddressRequest,
    ): CheckAddressResponse
}
