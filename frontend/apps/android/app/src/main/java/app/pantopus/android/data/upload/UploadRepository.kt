package app.pantopus.android.data.upload

import app.pantopus.android.data.api.models.chats.AIMediaUploadResponse
import app.pantopus.android.data.api.models.chats.ChatMediaUploadResponse
import app.pantopus.android.data.api.models.listings.ListingMediaUploadResponse
import app.pantopus.android.data.api.models.posts.PostMediaUploadResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.UploadApi
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps post-media multipart uploads in the [NetworkResult] taxonomy. */
@Singleton
class UploadRepository
    @Inject
    constructor(
        private val uploadApi: UploadApi,
    ) {
        suspend fun uploadPostMedia(
            postId: String,
            photoBytes: List<ByteArray>,
        ): NetworkResult<PostMediaUploadResponse> =
            safeApiCall {
                val parts =
                    photoBytes.mapIndexed { index, bytes ->
                        val (filename, mimeType) = photoMimeInfo(bytes, index)
                        MultipartBody.Part.createFormData(
                            name = "files",
                            filename = filename,
                            body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
                        )
                    }
                uploadApi.uploadPostMedia(postId, parts)
            }

        suspend fun uploadChatMedia(
            roomId: String,
            files: List<UploadFile>,
        ): NetworkResult<ChatMediaUploadResponse> =
            safeApiCall {
                val parts =
                    files.map { file ->
                        MultipartBody.Part.createFormData(
                            name = "files",
                            filename = file.filename,
                            body = file.bytes.toRequestBody(file.mimeType.toMediaTypeOrNull()),
                        )
                    }
                uploadApi.uploadChatMedia(roomId, parts)
            }

        suspend fun uploadAIMedia(files: List<UploadFile>): NetworkResult<AIMediaUploadResponse> =
            safeApiCall {
                val parts =
                    files.map { file ->
                        MultipartBody.Part.createFormData(
                            name = "files",
                            filename = file.filename,
                            body = file.bytes.toRequestBody(file.mimeType.toMediaTypeOrNull()),
                        )
                    }
                uploadApi.uploadAIMedia(parts)
            }

        /** Snap & Sell — attach local photos to a just-created/edited listing. */
        suspend fun uploadListingMedia(
            listingId: String,
            files: List<UploadFile>,
        ): NetworkResult<ListingMediaUploadResponse> =
            safeApiCall {
                val parts =
                    files.map { file ->
                        MultipartBody.Part.createFormData(
                            name = "files",
                            filename = file.filename,
                            body = file.bytes.toRequestBody(file.mimeType.toMediaTypeOrNull()),
                        )
                    }
                uploadApi.uploadListingMedia(listingId, parts)
            }
    }

data class UploadFile(
    val filename: String,
    val mimeType: String,
    val bytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        this === other ||
            (
                other is UploadFile &&
                    filename == other.filename &&
                    mimeType == other.mimeType &&
                    bytes.contentEquals(other.bytes)
            )

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + mimeType.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

private fun photoMimeInfo(
    bytes: ByteArray,
    index: Int,
): Pair<String, String> {
    if (bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()) {
        return "photo-$index.jpg" to "image/jpeg"
    }
    if (bytes.size >= 8 &&
        bytes[0] == 0x89.toByte() &&
        bytes[1] == 0x50.toByte() &&
        bytes[2] == 0x4E.toByte() &&
        bytes[3] == 0x47.toByte()
    ) {
        return "photo-$index.png" to "image/png"
    }
    if (bytes.size >= 12 &&
        bytes[4] == 0x66.toByte() &&
        bytes[5] == 0x74.toByte() &&
        bytes[6] == 0x79.toByte() &&
        bytes[7] == 0x70.toByte()
    ) {
        return "photo-$index.heic" to "image/heic"
    }
    return "photo-$index.jpg" to "image/jpeg"
}
