package app.pantopus.android.data.api.models.relationships

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * `POST /api/relationships/requests` body. Matches the Joi validator at
 * `backend/routes/relationships.js:40-43` (snake_case keys).
 */
@JsonClass(generateAdapter = true)
data class ConnectionRequestBody(
    @Json(name = "addressee_id") val addresseeId: String,
    val message: String? = null,
)

/**
 * `POST /api/relationships/requests` response envelope. Backend returns
 * `{message, relationship}`; only `message` is consumed by the UI.
 */
@JsonClass(generateAdapter = true)
data class ConnectionRequestResponse(
    val message: String? = null,
)
