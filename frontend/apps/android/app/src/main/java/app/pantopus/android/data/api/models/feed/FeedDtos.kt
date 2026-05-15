package app.pantopus.android.data.api.models.feed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One row in `GET /api/posts/feed` (route `backend/routes/posts.js:1449`).
 * The backend serializer projects every `Post` column the feed needs; we
 * keep the fields the Pulse UI actually reads.
 */
@JsonClass(generateAdapter = true)
data class FeedPost(
    val id: String,
    @Json(name = "user_id") val userId: String,
    val title: String? = null,
    val content: String? = null,
    @Json(name = "post_type") val postType: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "like_count") val likeCount: Int = 0,
    @Json(name = "comment_count") val commentCount: Int = 0,
    @Json(name = "userHasLiked") val userHasLiked: Boolean = false,
    @Json(name = "location_name") val locationName: String? = null,
    @Json(name = "event_date") val eventDate: String? = null,
    @Json(name = "event_venue") val eventVenue: String? = null,
    @Json(name = "lost_found_type") val lostFoundType: String? = null,
    val creator: FeedPostCreator? = null,
)

/**
 * Author projection on a feed post. Mirrors the iOS `PostCreatorDTO`
 * shape and SAFE_CREATOR_SELECT + identity-author attachment.
 */
@JsonClass(generateAdapter = true)
data class FeedPostCreator(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @Json(name = "first_name") val firstName: String? = null,
    @Json(name = "last_name") val lastName: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
    val city: String? = null,
    val state: String? = null,
    @Json(name = "account_type") val accountType: String? = null,
) {
    /** Best-effort display name across the populated fields. */
    fun displayName(): String {
        if (!name.isNullOrEmpty()) return name
        val combined = listOfNotNull(firstName, lastName).filter { it.isNotEmpty() }.joinToString(" ")
        if (combined.isNotEmpty()) return combined
        if (!username.isNullOrEmpty()) return "@$username"
        return "Pantopus user"
    }
}

/** Paging envelope returned by `GET /api/posts/feed`. */
@JsonClass(generateAdapter = true)
data class FeedPagination(
    val nextCursor: String? = null,
    val hasMore: Boolean? = null,
)

/** `GET /api/posts/feed` response envelope. */
@JsonClass(generateAdapter = true)
data class FeedResponse(
    val posts: List<FeedPost>,
    val pagination: FeedPagination? = null,
)

/** Push-token registration request (kept from legacy ApiService). */
@JsonClass(generateAdapter = true)
data class RegisterPushTokenRequest(
    val token: String,
    val platform: String,
)
