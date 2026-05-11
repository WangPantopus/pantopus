package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.homes.FileUploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * Multipart file upload endpoint. Backed by `POST /api/files/upload` —
 * `backend/routes/files.js:781`. The server accepts a single `file`
 * part plus arbitrary string form fields (e.g. `file_type`,
 * `visibility`) and returns `{ message, file: { id, url } }`.
 */
interface FilesApi {
    /**
     * Upload one binary file plus optional form fields. The server's
     * 413/415 errors flow back as Retrofit HttpException and are mapped
     * by `NetworkResult` callers.
     */
    @Multipart
    @POST("api/files/upload")
    suspend fun upload(
        @Part file: MultipartBody.Part,
        @Part("file_type") fileType: RequestBody,
        @Part("visibility") visibility: RequestBody,
    ): FileUploadResponse
}
