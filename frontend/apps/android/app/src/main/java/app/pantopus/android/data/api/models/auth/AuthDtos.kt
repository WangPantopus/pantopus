package app.pantopus.android.data.api.models.auth

import com.squareup.moshi.JsonClass

/**
 * `POST /api/users/login` request body. Route: `backend/routes/users.js:955`.
 */
@JsonClass(generateAdapter = true)
data class LoginRequest(
    val email: String,
    val password: String,
)

/**
 * `POST /api/users/login` response. Tokens are omitted when the server is
 * in cookie-transport mode. Route: `backend/routes/users.js:955`.
 */
@JsonClass(generateAdapter = true)
data class LoginResponse(
    val message: String?,
    val accessToken: String?,
    val refreshToken: String?,
    /** Seconds until the access token expires. */
    val expiresIn: Long?,
    /** Absolute expiry (Unix epoch, seconds). */
    val expiresAt: Long?,
    val user: AuthenticatedUser,
)

/**
 * `POST /api/users/refresh` request. The server can also read the refresh
 * token from the `pantopus_refresh` cookie. Route:
 * `backend/routes/users.js:1370`.
 */
@JsonClass(generateAdapter = true)
data class RefreshRequest(
    val refreshToken: String?,
)

/**
 * `POST /api/users/refresh` response. Route: `backend/routes/users.js:1370`.
 */
@JsonClass(generateAdapter = true)
data class RefreshResponse(
    val ok: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val expiresIn: Long?,
    val expiresAt: Long?,
)

/**
 * User payload embedded in [LoginResponse]. Mirrors
 * `sanitizeUserForAuthResponse` at `backend/routes/users.js:955`.
 */
@JsonClass(generateAdapter = true)
data class AuthenticatedUser(
    val id: String,
    val email: String,
    val username: String,
    val name: String,
    val firstName: String,
    val middleName: String?,
    val lastName: String,
    val phoneNumber: String?,
    val address: String?,
    val city: String?,
    val state: String?,
    val zipcode: String?,
    val accountType: String,
    val role: String,
    val verified: Boolean,
    val createdAt: String,
)
