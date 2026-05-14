package app.pantopus.android.data.api.services

import app.pantopus.android.data.api.models.chats.ChatMessagesResponse
import app.pantopus.android.data.api.models.chats.ChatStatsResponse
import app.pantopus.android.data.api.models.chats.ReactToChatMessageBody
import app.pantopus.android.data.api.models.chats.ReactToChatMessageResponse
import app.pantopus.android.data.api.models.chats.SendChatMessageBody
import app.pantopus.android.data.api.models.chats.SendChatMessageResponse
import app.pantopus.android.data.api.models.chats.UnifiedConversationsResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Chat endpoints under `/api/chat/*` from `backend/routes/chats.js`.
 * Mounted at `/api/chat` (see `backend/app.js:325`).
 */
interface ChatApi {
    /** `GET /api/chat/unified-conversations`. */
    @GET("api/chat/unified-conversations")
    suspend fun unifiedConversations(
        @Query("limit") limit: Int = 100,
    ): UnifiedConversationsResponse

    /** `GET /api/chat/stats`. */
    @GET("api/chat/stats")
    suspend fun stats(): ChatStatsResponse

    /** `GET /api/chat/rooms/:roomId/messages` — route `chats.js:1340`. */
    @GET("api/chat/rooms/{roomId}/messages")
    suspend fun roomMessages(
        @Path("roomId") roomId: String,
        @Query("limit") limit: Int = 60,
        @Query("before") before: String? = null,
        @Query("after") after: String? = null,
    ): ChatMessagesResponse

    /** `GET /api/chat/conversations/:otherUserId/messages` — route `chats.js:1157`. */
    @GET("api/chat/conversations/{otherUserId}/messages")
    suspend fun conversationMessages(
        @Path("otherUserId") otherUserId: String,
        @Query("limit") limit: Int = 60,
        @Query("before") before: String? = null,
        @Query("after") after: String? = null,
    ): ChatMessagesResponse

    /** `POST /api/chat/messages` — route `chats.js:1438`. */
    @POST("api/chat/messages")
    suspend fun sendMessage(
        @Body body: SendChatMessageBody,
    ): SendChatMessageResponse

    /** `POST /api/chat/messages/:id/react` — route `chats.js:2558`. */
    @POST("api/chat/messages/{id}/react")
    suspend fun reactToMessage(
        @Path("id") id: String,
        @Body body: ReactToChatMessageBody,
    ): ReactToChatMessageResponse

    /** `POST /api/chat/rooms/:roomId/read` — route `chats.js:1953`. */
    @POST("api/chat/rooms/{roomId}/read")
    suspend fun markRoomRead(
        @Path("roomId") roomId: String,
    ): Unit

    /** `POST /api/chat/conversations/:otherUserId/read` — route `chats.js:1265`. */
    @POST("api/chat/conversations/{otherUserId}/read")
    suspend fun markConversationRead(
        @Path("otherUserId") otherUserId: String,
    ): Unit
}
