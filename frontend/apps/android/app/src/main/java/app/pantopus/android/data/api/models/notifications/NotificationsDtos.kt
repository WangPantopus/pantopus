package app.pantopus.android.data.api.models.notifications

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Single row from `/api/notifications`. */
@JsonClass(generateAdapter = true)
data class NotificationDto(
    val id: String,
    @Json(name = "user_id") val userId: String?,
    val type: String?,
    val title: String?,
    val body: String?,
    val icon: String?,
    /**
     * Backend-emitted deep link path, e.g. `/post/abc-123`,
     * `/homes/h_1/dashboard`. DeepLinkRouter parses this.
     */
    val link: String?,
    @Json(name = "is_read") val isRead: Boolean?,
    @Json(name = "created_at") val createdAt: String?,
    val context: String? = null,
)

/** `GET /api/notifications` envelope — route `backend/routes/notifications.js:84`. */
@JsonClass(generateAdapter = true)
data class NotificationsListResponse(
    val notifications: List<NotificationDto> = emptyList(),
    val unreadCount: Int? = null,
    val hasMore: Boolean? = null,
)

/** `GET /api/notifications/unread-count` envelope — route `backend/routes/notifications.js:160`. */
@JsonClass(generateAdapter = true)
data class NotificationUnreadCountResponse(
    val count: Int,
)

/** Echo of a write call (`/read` / `/read-all`). Both `ok` and `count` are optional on success. */
@JsonClass(generateAdapter = true)
data class NotificationActionEcho(
    val ok: Boolean? = null,
    val count: Int? = null,
)
