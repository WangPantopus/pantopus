package app.pantopus.android.data.chats

import app.pantopus.android.data.api.models.chats.ChatStatsResponse
import app.pantopus.android.data.api.models.chats.UnifiedConversationsResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.api.net.safeApiCall
import app.pantopus.android.data.api.services.ChatApi
import javax.inject.Inject
import javax.inject.Singleton

/** Wraps the chat endpoints in the [NetworkResult] taxonomy. */
@Singleton
class ChatRepository
    @Inject
    constructor(
        private val api: ChatApi,
    ) {
        /** `GET /api/chat/unified-conversations`. */
        suspend fun unifiedConversations(limit: Int = 100): NetworkResult<UnifiedConversationsResponse> =
            safeApiCall { api.unifiedConversations(limit) }

        /** `GET /api/chat/stats`. */
        suspend fun stats(): NetworkResult<ChatStatsResponse> = safeApiCall { api.stats() }
    }
