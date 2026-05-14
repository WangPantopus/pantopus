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
