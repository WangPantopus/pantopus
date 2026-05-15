package app.pantopus.android.data.chats

import app.pantopus.android.data.api.models.chats.ChatMessagesResponse
import app.pantopus.android.data.api.models.chats.ChatStatsResponse
import app.pantopus.android.data.api.models.chats.ReactToChatMessageBody
import app.pantopus.android.data.api.models.chats.ReactToChatMessageResponse
import app.pantopus.android.data.api.models.chats.SendChatMessageBody
import app.pantopus.android.data.api.models.chats.SendChatMessageResponse
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
        suspend fun unifiedConversations(limit: Int = 100): NetworkResult<UnifiedConversationsResponse> =
            safeApiCall { api.unifiedConversations(limit) }

        suspend fun stats(): NetworkResult<ChatStatsResponse> = safeApiCall { api.stats() }

        suspend fun roomMessages(
            roomId: String,
            before: String? = null,
            after: String? = null,
            limit: Int = 60,
        ): NetworkResult<ChatMessagesResponse> = safeApiCall { api.roomMessages(roomId, limit, before, after) }

        suspend fun conversationMessages(
            otherUserId: String,
            before: String? = null,
            after: String? = null,
            limit: Int = 60,
        ): NetworkResult<ChatMessagesResponse> = safeApiCall { api.conversationMessages(otherUserId, limit, before, after) }

        suspend fun sendMessage(body: SendChatMessageBody): NetworkResult<SendChatMessageResponse> = safeApiCall { api.sendMessage(body) }

        suspend fun reactToMessage(
            messageId: String,
            reaction: String,
        ): NetworkResult<ReactToChatMessageResponse> = safeApiCall { api.reactToMessage(messageId, ReactToChatMessageBody(reaction)) }

        suspend fun markRoomRead(roomId: String): NetworkResult<Unit> = safeApiCall { api.markRoomRead(roomId) }

        suspend fun markConversationRead(otherUserId: String): NetworkResult<Unit> = safeApiCall { api.markConversationRead(otherUserId) }
    }
