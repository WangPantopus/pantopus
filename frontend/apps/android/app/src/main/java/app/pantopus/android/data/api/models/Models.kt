package app.pantopus.android.data.api.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import java.time.Instant

/**
 * DTOs mirroring the backend's JSON shapes.
 *
 * Keep these in sync with `backend/routes/*`. Long-term plan: generate them
 * from the backend's OpenAPI spec — see the iOS counterpart `Models.swift`
 * and the migration note in the root README.
 */

// --- Auth ---------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String
)

@JsonClass(generateAdapter = true)
data class AuthResponse(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String?,
    val user: UserDto
)

@JsonClass(generateAdapter = true)
data class RefreshRequest(
    @Json(name = "refresh_token") val refreshToken: String
)

@JsonClass(generateAdapter = true)
data class RegisterPushTokenRequest(
    val token: String,
    val platform: String
)

// --- User ---------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class UserDto(
    val id: String,
    val email: String,
    @Json(name = "display_name") val displayName: String?,
    @Json(name = "avatar_url") val avatarUrl: String?
)

// --- Feed ---------------------------------------------------------------

@JsonClass(generateAdapter = true)
data class FeedPost(
    val id: String,
    @Json(name = "author_id") val authorId: String,
    @Json(name = "author_name") val authorName: String?,
    val content: String,
    @Json(name = "created_at") val createdAt: Instant,
    @Json(name = "like_count") val likeCount: Int
)

@JsonClass(generateAdapter = true)
data class FeedResponse(
    val posts: List<FeedPost>,
    @Json(name = "next_cursor") val nextCursor: String?
)
