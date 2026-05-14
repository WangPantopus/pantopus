@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.chats

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One topic attached to a person-conversation in
 * `GET /api/chat/unified-conversations`.
 */
@JsonClass(generateAdapter = true)
data class ChatTopic(
    val id: String? = null,
    @Json(name = "topic_type") val topicType: String? = null,
    val title: String? = null,
)

/**
 * Inline identity object emitted by the local-identity serializer on a
 * `_type: "conversation"` row.
 */
@JsonClass(generateAdapter = true)
data class ChatOtherIdentity(
    val id: String? = null,
    @Json(name = "display_name") val displayName: String? = null,
    @Json(name = "avatarUrl") val avatarUrl: String? = null,
    @Json(name = "identity_kind") val identityKind: String? = null,
    val verified: Boolean? = null,
)

/**
 * One row in the unified-conversations response. Hetero by `_type`:
 * `conversation` (person-grouped DM/gig) or `room` (group/home). The
 * fields not relevant to a variant decode as `null`.
 *
 * Route: `backend/routes/chats.js:2211`.
 */
@JsonClass(generateAdapter = true)
data class UnifiedConversationDto(
    @Json(name = "_type") val type: String? = "conversation",
    // conversation-only keys
    @Json(name = "other_participant_id") val otherParticipantId: String? = null,
    @Json(name = "other_participant_name") val otherParticipantName: String? = null,
    @Json(name = "other_participant_avatar") val otherParticipantAvatar: String? = null,
    @Json(name = "other_participant_identity") val otherParticipantIdentity: ChatOtherIdentity? = null,
    @Json(name = "room_ids") val roomIds: List<String>? = null,
    val topics: List<ChatTopic>? = null,
    // room-only keys
    val id: String? = null,
    @Json(name = "room_type") val roomType: String? = null,
    @Json(name = "room_name") val roomName: String? = null,
    @Json(name = "gig_id") val gigId: String? = null,
    @Json(name = "home_id") val homeId: String? = null,
    // shared
    @Json(name = "total_unread") val totalUnread: Int = 0,
    @Json(name = "last_message_at") val lastMessageAt: String? = null,
    @Json(name = "last_message_preview") val lastMessagePreview: String? = null,
)

/** `GET /api/chat/unified-conversations` envelope. */
@JsonClass(generateAdapter = true)
data class UnifiedConversationsResponse(
    val conversations: List<UnifiedConversationDto>,
    val total: Int? = null,
    val totalUnread: Int? = null,
)

/** Stats payload from `GET /api/chat/stats`. */
@JsonClass(generateAdapter = true)
data class ChatStats(
    @Json(name = "total_chats") val totalChats: Int = 0,
    @Json(name = "total_messages") val totalMessages: Int = 0,
    @Json(name = "total_unread") val totalUnread: Int = 0,
    @Json(name = "direct_chats") val directChats: Int = 0,
    @Json(name = "gig_chats") val gigChats: Int = 0,
    @Json(name = "home_chats") val homeChats: Int = 0,
)

/** `GET /api/chat/stats` envelope. */
@JsonClass(generateAdapter = true)
data class ChatStatsResponse(
    val stats: ChatStats,
)

// MARK: - Messages (T2.2)

/** Sender projection embedded in a `ChatMessage` row. */
@JsonClass(generateAdapter = true)
data class ChatMessageSender(
    val id: String,
    val username: String? = null,
    val name: String? = null,
    @Json(name = "profile_picture_url") val profilePictureUrl: String? = null,
)

/** One row from `/messages`. */
@JsonClass(generateAdapter = true)
data class ChatMessageDto(
    val id: String,
    @Json(name = "room_id") val roomId: String,
    @Json(name = "user_id") val userId: String? = null,
    @Json(name = "message_text") val messageText: String? = null,
    @Json(name = "message_type") val messageType: String = "text",
    val metadata: Map<String, Any>? = null,
    @Json(name = "reply_to_id") val replyToId: String? = null,
    @Json(name = "client_message_id") val clientMessageId: String? = null,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "edited_at") val editedAt: String? = null,
    @Json(name = "deleted_at") val deletedAt: String? = null,
    @Json(name = "delivered_at") val deliveredAt: String? = null,
    @Json(name = "read_at") val readAt: String? = null,
    val sender: ChatMessageSender? = null,
)

/** `GET /messages` envelope. */
@JsonClass(generateAdapter = true)
data class ChatMessagesResponse(
    val messages: List<ChatMessageDto>,
    val hasMore: Boolean? = null,
    val roomIds: List<String>? = null,
)

/** `POST /messages` body. */
@JsonClass(generateAdapter = true)
data class SendChatMessageBody(
    val roomId: String,
    val messageText: String? = null,
    val messageType: String = "text",
    val clientMessageId: String? = null,
    val replyToId: String? = null,
)

/** `POST /messages` envelope. */
@JsonClass(generateAdapter = true)
data class SendChatMessageResponse(
    val message: ChatMessageDto,
)

/** `POST /messages/:id/react` body. */
@JsonClass(generateAdapter = true)
data class ReactToChatMessageBody(
    val reaction: String,
)

/** `POST /messages/:id/react` envelope. */
@JsonClass(generateAdapter = true)
data class ReactToChatMessageResponse(
    val messageId: String? = null,
    val reaction: String? = null,
    val counts: Map<String, Int>? = null,
)
