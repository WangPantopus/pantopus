package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.notifications.NotificationActionEcho
import app.pantopus.android.data.api.models.notifications.NotificationUnreadCountResponse
import app.pantopus.android.data.api.models.notifications.NotificationsListResponse
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** T4.1 notifications routes from `backend/routes/notifications.js`. */
interface NotificationsApi {
    /** `GET /api/notifications` — route `backend/routes/notifications.js:84`. */
    @GET("api/notifications")
    suspend fun list(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("unread") unreadOnly: Boolean? = null,
    ): NotificationsListResponse

    /**
     * `GET /api/notifications/unread-count` — route
     * `backend/routes/notifications.js:160`. Drives the bell badge.
     */
    @GET("api/notifications/unread-count")
    suspend fun unreadCount(): NotificationUnreadCountResponse

    /**
     * `PATCH /api/notifications/:id/read` — route
     * `backend/routes/notifications.js:330`. Marks one row as read.
     */
    @PATCH("api/notifications/{id}/read")
    suspend fun markRead(
        @Path("id") id: String,
    ): NotificationActionEcho

    /**
     * `POST /api/notifications/read-all` — route
     * `backend/routes/notifications.js:361`. Sweeps every unread row
     * for the current user.
     */
    @POST("api/notifications/read-all")
    suspend fun markAllRead(): NotificationActionEcho
}
