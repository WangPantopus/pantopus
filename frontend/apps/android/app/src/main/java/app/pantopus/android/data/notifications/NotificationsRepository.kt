package app.pantopus.android.data.notifications

import app.pantopus.android.data.api.models.notifications.NotificationActionEcho
import app.pantopus.android.data.api.models.notifications.NotificationUnreadCountResponse
import app.pantopus.android.data.api.models.notifications.NotificationsListResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.NotificationsApi
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper around [NotificationsApi] returning the typed
 * [NetworkResult] taxonomy. Optimistic mark-read / read-all bookkeeping
 * lives in the VM; the repository just relays.
 */
@Singleton
class NotificationsRepository
    @Inject
    constructor(
        private val api: NotificationsApi,
    ) {
        suspend fun list(
            limit: Int,
            offset: Int,
            unreadOnly: Boolean? = null,
        ): NetworkResult<NotificationsListResponse> = safeApiCall { api.list(limit = limit, offset = offset, unreadOnly = unreadOnly) }

        suspend fun unreadCount(): NetworkResult<NotificationUnreadCountResponse> = safeApiCall { api.unreadCount() }

        suspend fun markRead(id: String): NetworkResult<NotificationActionEcho> = safeApiCall { api.markRead(id) }

        suspend fun markAllRead(): NetworkResult<NotificationActionEcho> = safeApiCall { api.markAllRead() }
    }
