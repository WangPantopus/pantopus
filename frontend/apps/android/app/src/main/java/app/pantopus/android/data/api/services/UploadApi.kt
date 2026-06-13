package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.chats.AIMediaUploadResponse
import app.pantopus.android.data.api.models.chats.ChatMediaUploadResponse
import app.pantopus.android.data.api.models.listings.ListingMediaUploadResponse
import app.pantopus.android.data.api.models.posts.PostMediaUploadResponse
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

/**
 * Multipart upload endpoints under `api/upload`.
 * Post media uses `POST /api/upload/post-media/:postId` —
 * `backend/routes/upload.js:934`.
 */
interface UploadApi {
    /** Attach up to nine images/videos to an existing post. */
    @Multipart
    @POST("api/upload/post-media/{postId}")
    suspend fun uploadPostMedia(
        @Path("postId") postId: String,
        @Part files: @JvmSuppressWildcards List<MultipartBody.Part>,
    ): PostMediaUploadResponse

    /** Upload up to five chat attachments for a room. */
    @Multipart
    @POST("api/upload/chat-media/{roomId}")
    suspend fun uploadChatMedia(
        @Path("roomId") roomId: String,
        @Part files: @JvmSuppressWildcards List<MultipartBody.Part>,
    ): ChatMediaUploadResponse

    /** Upload images for AI assistant chat. */
    @Multipart
    @POST("api/upload/ai-media")
    suspend fun uploadAIMedia(
        @Part files: @JvmSuppressWildcards List<MultipartBody.Part>,
    ): AIMediaUploadResponse

    /**
     * Attach photos to an existing listing (Snap & Sell post-create
     * upload). Route `backend/routes/upload.js:1049`.
     */
    @Multipart
    @POST("api/upload/listing-media/{listingId}")
    suspend fun uploadListingMedia(
        @Path("listingId") listingId: String,
        @Part files: @JvmSuppressWildcards List<MultipartBody.Part>,
    ): ListingMediaUploadResponse
}
