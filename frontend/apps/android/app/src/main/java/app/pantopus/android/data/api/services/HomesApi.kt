package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.common.JsonValue
import app.pantopus.android.data.api.models.homes.CheckAddressRequest
import app.pantopus.android.data.api.models.homes.CreateBillRequest
import app.pantopus.android.data.api.models.homes.GetBillSplitsResponse
import app.pantopus.android.data.api.models.homes.GetHomeBillsResponse
import app.pantopus.android.data.api.models.homes.HomeBillResponse
import app.pantopus.android.data.api.models.homes.UpdateBillRequest
import app.pantopus.android.data.api.models.homes.CheckAddressResponse
import app.pantopus.android.data.api.models.homes.CreateHomeRequest
import app.pantopus.android.data.api.models.homes.CreateHomeResponse
import app.pantopus.android.data.api.models.homes.HomeDetailResponse
import app.pantopus.android.data.api.models.homes.HomePublicProfileResponse
import app.pantopus.android.data.api.models.homes.InviteOwnerRequest
import app.pantopus.android.data.api.models.homes.InviteOwnerResponse
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.homes.MyOwnershipClaimsResponse
import app.pantopus.android.data.api.models.homes.PropertySuggestionsRequest
import app.pantopus.android.data.api.models.homes.SubmitClaimRequest
import app.pantopus.android.data.api.models.homes.SubmitClaimResponse
import app.pantopus.android.data.api.models.homes.UploadEvidenceRequest
import app.pantopus.android.data.api.models.homes.UploadEvidenceResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

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

    /**
     * `POST /api/homes/:id/owners/invite` — route
     * `backend/routes/homeOwnership.js:1376`.
     */
    @POST("api/homes/{id}/owners/invite")
    suspend fun inviteOwner(
        @Path("id") homeId: String,
        @Body body: InviteOwnerRequest,
    ): InviteOwnerResponse

    /**
     * `POST /api/homes/:id/ownership-claims` — route
     * `backend/routes/homeOwnership.js:251`.
     */
    @POST("api/homes/{id}/ownership-claims")
    suspend fun submitClaim(
        @Path("id") homeId: String,
        @Body body: SubmitClaimRequest,
    ): SubmitClaimResponse

    /**
     * `POST /api/homes/:id/ownership-claims/:claimId/evidence` — route
     * `backend/routes/homeOwnership.js:886`. Body is JSON; real bytes
     * are pushed through `/api/files/upload` first and the resulting
     * URL is sent here as `storage_ref`.
     */
    @POST("api/homes/{id}/ownership-claims/{claimId}/evidence")
    suspend fun uploadEvidence(
        @Path("id") homeId: String,
        @Path("claimId") claimId: String,
        @Body body: UploadEvidenceRequest,
    ): UploadEvidenceResponse

    /**
     * `GET /api/homes/my-ownership-claims` — route
     * `backend/routes/homeOwnership.js:217`.
     */
    @GET("api/homes/my-ownership-claims")
    suspend fun myOwnershipClaims(): MyOwnershipClaimsResponse

    /** `GET /api/homes/:id/bills` — route `backend/routes/home.js:4506`. */
    @GET("api/homes/{id}/bills")
    suspend fun getHomeBills(
        @Path("id") homeId: String,
        @Query("status") status: String? = null,
    ): GetHomeBillsResponse

    /** `POST /api/homes/:id/bills` — route `backend/routes/home.js:4539`. */
    @POST("api/homes/{id}/bills")
    suspend fun createHomeBill(
        @Path("id") homeId: String,
        @Body body: CreateBillRequest,
    ): HomeBillResponse

    /** `PUT /api/homes/:id/bills/:billId` — route `backend/routes/home.js:4585`. */
    @PUT("api/homes/{id}/bills/{billId}")
    suspend fun updateHomeBill(
        @Path("id") homeId: String,
        @Path("billId") billId: String,
        @Body body: UpdateBillRequest,
    ): HomeBillResponse

    /** `GET /api/homes/:id/bills/:billId/splits` — route
     *  `backend/routes/home.js:4627`. Backend has no POST/PATCH/DELETE
     *  for splits today; the detail view treats them as read-only. */
    @GET("api/homes/{id}/bills/{billId}/splits")
    suspend fun getHomeBillSplits(
        @Path("id") homeId: String,
        @Path("billId") billId: String,
    ): GetBillSplitsResponse
}
