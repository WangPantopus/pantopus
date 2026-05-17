package app.pantopus.android.data.homes

import app.pantopus.android.data.api.models.homes.CheckAddressRequest
import app.pantopus.android.data.api.models.homes.CreateBillRequest
import app.pantopus.android.data.api.models.homes.CreateHomeRequest
import app.pantopus.android.data.api.models.homes.CreateMaintenanceRequest
import app.pantopus.android.data.api.models.homes.CreatePackageRequest
import app.pantopus.android.data.api.models.homes.FileUploadResponse
import app.pantopus.android.data.api.models.homes.GetBillSplitsResponse
import app.pantopus.android.data.api.models.homes.GetHomeBillsResponse
import app.pantopus.android.data.api.models.homes.GetHomeMaintenanceResponse
import app.pantopus.android.data.api.models.homes.GetHomePackagesResponse
import app.pantopus.android.data.api.models.homes.HomeBillResponse
import app.pantopus.android.data.api.models.homes.HomeMaintenanceResponse
import app.pantopus.android.data.api.models.homes.HomePackageResponse
import app.pantopus.android.data.api.models.homes.InviteOwnerRequest
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.homes.MyOwnershipClaimsResponse
import app.pantopus.android.data.api.models.homes.PropertySuggestionsRequest
import app.pantopus.android.data.api.models.homes.SubmitClaimRequest
import app.pantopus.android.data.api.models.homes.SubmitClaimResponse
import app.pantopus.android.data.api.models.homes.UpdateBillRequest
import app.pantopus.android.data.api.models.homes.UpdateMaintenanceRequest
import app.pantopus.android.data.api.models.homes.UpdatePackageRequest
import app.pantopus.android.data.api.models.homes.UploadEvidenceRequest
import app.pantopus.android.data.api.models.homes.UploadEvidenceResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.FilesApi
import app.pantopus.android.data.api.services.HomesApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [HomesApi] that returns the typed [NetworkResult]
 * taxonomy. ViewModels depend on this rather than Retrofit directly so
 * they can expose a single error surface to the UI.
 */
@Suppress("TooManyFunctions")
@Singleton
open class HomesRepository
    @Inject
    constructor(
        private val api: HomesApi,
        private val filesApi: FilesApi,
    ) {
        /** `GET /api/homes/my-homes`. */
        open suspend fun myHomes(): NetworkResult<MyHomesResponse> = safeApiCall { api.myHomes() }

        /** `GET /api/homes/:id`. */
        open suspend fun detail(id: String) = safeApiCall { api.detail(id) }

        /** `GET /api/homes/:id/public-profile`. */
        open suspend fun publicProfile(id: String) = safeApiCall { api.publicProfile(id) }

        /** `POST /api/homes/property-suggestions`. */
        open suspend fun propertySuggestions(request: PropertySuggestionsRequest) = safeApiCall { api.propertySuggestions(request) }

        /** `POST /api/homes/check-address`. */
        open suspend fun checkAddress(request: CheckAddressRequest) = safeApiCall { api.checkAddress(request) }

        /** `POST /api/homes`. */
        open suspend fun create(request: CreateHomeRequest) = safeApiCall { api.create(request) }

        /** `POST /api/homes/:id/owners/invite`. */
        open suspend fun inviteOwner(
            homeId: String,
            request: InviteOwnerRequest,
        ) = safeApiCall { api.inviteOwner(homeId, request) }

        /** `POST /api/homes/:id/ownership-claims`. */
        open suspend fun submitClaim(
            homeId: String,
            request: SubmitClaimRequest,
        ): NetworkResult<SubmitClaimResponse> = safeApiCall { api.submitClaim(homeId, request) }

        /** `POST /api/homes/:id/ownership-claims/:claimId/evidence`. */
        open suspend fun uploadEvidence(
            homeId: String,
            claimId: String,
            request: UploadEvidenceRequest,
        ): NetworkResult<UploadEvidenceResponse> = safeApiCall { api.uploadEvidence(homeId, claimId, request) }

        /** `GET /api/homes/my-ownership-claims`. */
        open suspend fun myOwnershipClaims(): NetworkResult<MyOwnershipClaimsResponse> = safeApiCall { api.myOwnershipClaims() }

        /** `GET /api/homes/:id/bills`. */
        open suspend fun getHomeBills(
            homeId: String,
            status: String? = null,
        ): NetworkResult<GetHomeBillsResponse> = safeApiCall { api.getHomeBills(homeId, status) }

        /** `POST /api/homes/:id/bills`. */
        open suspend fun createHomeBill(
            homeId: String,
            request: CreateBillRequest,
        ): NetworkResult<HomeBillResponse> = safeApiCall { api.createHomeBill(homeId, request) }

        /** `PUT /api/homes/:id/bills/:billId`. */
        open suspend fun updateHomeBill(
            homeId: String,
            billId: String,
            request: UpdateBillRequest,
        ): NetworkResult<HomeBillResponse> = safeApiCall { api.updateHomeBill(homeId, billId, request) }

        /** `GET /api/homes/:id/bills/:billId/splits`. */
        open suspend fun getHomeBillSplits(
            homeId: String,
            billId: String,
        ): NetworkResult<GetBillSplitsResponse> = safeApiCall { api.getHomeBillSplits(homeId, billId) }

        /** `GET /api/homes/:id/packages`. */
        open suspend fun getHomePackages(
            homeId: String,
            status: String? = null,
        ): NetworkResult<GetHomePackagesResponse> = safeApiCall { api.getHomePackages(homeId, status) }

        /** `POST /api/homes/:id/packages`. */
        open suspend fun createHomePackage(
            homeId: String,
            request: CreatePackageRequest,
        ): NetworkResult<HomePackageResponse> = safeApiCall { api.createHomePackage(homeId, request) }

        /** `PUT /api/homes/:id/packages/:packageId`. */
        open suspend fun updateHomePackage(
            homeId: String,
            packageId: String,
            request: UpdatePackageRequest,
        ): NetworkResult<HomePackageResponse> = safeApiCall { api.updateHomePackage(homeId, packageId, request) }

        // ─── Maintenance (T6.3b / P10) ─────────────────────────

        /** `GET /api/homes/:id/maintenance`. */
        open suspend fun getHomeMaintenance(
            homeId: String,
            status: String? = null,
        ): NetworkResult<GetHomeMaintenanceResponse> = safeApiCall { api.getHomeMaintenance(homeId, status) }

        /** `POST /api/homes/:id/maintenance`. */
        open suspend fun createHomeMaintenance(
            homeId: String,
            request: CreateMaintenanceRequest,
        ): NetworkResult<HomeMaintenanceResponse> = safeApiCall { api.createHomeMaintenance(homeId, request) }

        /** `PUT /api/homes/:id/maintenance/:taskId`. */
        open suspend fun updateHomeMaintenance(
            homeId: String,
            taskId: String,
            request: UpdateMaintenanceRequest,
        ): NetworkResult<HomeMaintenanceResponse> = safeApiCall { api.updateHomeMaintenance(homeId, taskId, request) }

        /** `DELETE /api/homes/:id/maintenance/:taskId`. */
        open suspend fun deleteHomeMaintenance(
            homeId: String,
            taskId: String,
        ): NetworkResult<Unit> =
            safeApiCall {
                val response = api.deleteHomeMaintenance(homeId, taskId)
                if (!response.isSuccessful) {
                    throw retrofit2.HttpException(response)
                }
                Unit
            }

        /**
         * Upload one binary file to `POST /api/files/upload` and return
         * the resulting URL. ViewModels pass that URL as `storage_ref`
         * on the subsequent evidence call. `fileType="claim_evidence"`
         * / `visibility="private"` keeps the artefact off public
         * surfaces.
         */
        open suspend fun uploadFile(
            filename: String,
            mimeType: String,
            bytes: ByteArray,
        ): NetworkResult<FileUploadResponse> =
            safeApiCall {
                val filePart =
                    MultipartBody.Part.createFormData(
                        name = "file",
                        filename = filename,
                        body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                    )
                val plain = "text/plain".toMediaTypeOrNull()
                filesApi.upload(
                    file = filePart,
                    fileType = "claim_evidence".toRequestBody(plain),
                    visibility = "private".toRequestBody(plain),
                )
            }
    }
