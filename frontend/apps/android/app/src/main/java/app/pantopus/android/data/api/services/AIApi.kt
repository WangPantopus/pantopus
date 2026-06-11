package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.ai.AIConversationsResponse
import retrofit2.http.GET

/**
 * AI assistant endpoints under `/api/ai/[*]` from `backend/routes/ai.js`.
 * The streaming chat itself rides raw SSE in [app.pantopus.android.data.ai.AIChatRepository];
 * this interface covers the plain JSON management routes.
 */
interface AIApi {
    /**
     * List the user's AI conversations, newest-updated first.
     * Route `backend/routes/ai.js:358`.
     */
    @GET("api/ai/conversations")
    suspend fun conversations(): AIConversationsResponse
}
