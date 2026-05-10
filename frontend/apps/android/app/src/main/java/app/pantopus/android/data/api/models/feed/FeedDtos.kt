package app.pantopus.android.data.api.models.feed

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

/** Pre-existing feed post (not in Prompt P3 scope). */
@JsonClass(generateAdapter = true)
data class FeedPost(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "author_name") val authorName: String?,
    val content: String,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "like_count") val likeCount: Int,
)

/** Pre-existing feed response envelope. */
@JsonClass(generateAdapter = true)
data class FeedResponse(
    val posts: List<FeedPost>,
    @Json(name = "next_cursor") val nextCursor: String?,
)

/** Push-token registration request (unchanged from the existing endpoint). */
@JsonClass(generateAdapter = true)
data class RegisterPushTokenRequest(
    val token: String,
    val platform: String,
)
