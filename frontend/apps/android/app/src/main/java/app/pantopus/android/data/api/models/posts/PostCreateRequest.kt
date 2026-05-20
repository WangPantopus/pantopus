package app.pantopus.android.data.api.models.posts

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `POST /api/posts` request body. Mirrors `createPostSchema` at
 * `backend/routes/posts.js:196-300`. Every optional field is nullable
 * so Moshi can drop it from the wire when the compose form doesn't
 * surface it — the backend's Joi schema rejects unexpected `null`s on
 * several keys, so we send `null` only where it's explicitly accepted.
 */
@JsonClass(generateAdapter = true)
data class PostCreateRequest(
    val content: String,
    val title: String? = null,
    @Json(name = "postType") val postType: String,
    val visibility: String,
    @Json(name = "postAs") val postAs: String,
    @Json(name = "mediaUrls") val mediaUrls: List<String>? = null,
    // Event-specific
    @Json(name = "eventDate") val eventDate: String? = null,
    @Json(name = "eventVenue") val eventVenue: String? = null,
    // Lost & Found
    @Json(name = "lostFoundType") val lostFoundType: String? = null,
    // Recommend
    @Json(name = "businessName") val businessName: String? = null,
    // Ask category
    @Json(name = "serviceCategory") val serviceCategory: String? = null,
    // Announce audience
    val audience: String? = null,
    // v1.2 purpose tag
    val purpose: String? = null,
)

/** `POST /api/posts` response envelope — the backend echoes a thin ack. */
@JsonClass(generateAdapter = true)
data class PostCreateResponse(
    val message: String? = null,
    @Json(name = "post_id") val postId: String? = null,
)
