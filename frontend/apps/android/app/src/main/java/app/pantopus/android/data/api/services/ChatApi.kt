package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.chats.ChatStatsResponse
import app.pantopus.android.data.api.models.chats.UnifiedConversationsResponse
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Chat endpoints under `/api/chat/*` from `backend/routes/chats.js`.
 * Mounted at `/api/chat` (see `backend/app.js:325`).
 */
interface ChatApi {
    /**
     * `GET /api/chat/unified-conversations` — person-grouped chat list.
     * Route `backend/routes/chats.js:2211`.
     */
    @GET("api/chat/unified-conversations")
    suspend fun unifiedConversations(
        @Query("limit") limit: Int = 100,
    ): UnifiedConversationsResponse

    /**
     * `GET /api/chat/stats` — lightweight badge counts.
     * Route `backend/routes/chats.js:2140`.
     */
    @GET("api/chat/stats")
    suspend fun stats(): ChatStatsResponse
}
