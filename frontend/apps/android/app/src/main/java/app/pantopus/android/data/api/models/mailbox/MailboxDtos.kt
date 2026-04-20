package app.pantopus.android.data.api.models.mailbox

import app.pantopus.android.data.api.models.common.JsonArrayValue
import app.pantopus.android.data.api.models.common.JsonValue
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Core mail row — shared between the list and detail responses. Route
 * citations: `backend/routes/mailbox.js:1306` (list), `:1466` (detail).
 */
@JsonClass(generateAdapter = true)
data class MailItem(
    val id: String,
    @Json(name = "recipient_user_id") val recipientUserId: String?,
    @Json(name = "recipient_home_id") val recipientHomeId: String?,
    @Json(name = "delivery_target_type") val deliveryTargetType: String?,
    @Json(name = "delivery_target_id") val deliveryTargetId: String?,
    @Json(name = "address_home_id") val addressHomeId: String?,
    @Json(name = "attn_user_id") val attnUserId: String?,
    @Json(name = "attn_label") val attnLabel: String?,
    @Json(name = "delivery_visibility") val deliveryVisibility: String?,
    @Json(name = "mail_type") val mailType: String?,
    @Json(name = "display_title") val displayTitle: String?,
    @Json(name = "preview_text") val previewText: String?,
    @Json(name = "primary_action") val primaryAction: String?,
    @Json(name = "action_required") val actionRequired: Boolean?,
    @Json(name = "ack_required") val ackRequired: Boolean?,
    @Json(name = "ack_status") val ackStatus: String?,
    val type: String,
    val subject: String?,
    val content: String?,
    @Json(name = "sender_user_id") val senderUserId: String?,
    @Json(name = "sender_business_name") val senderBusinessName: String?,
    @Json(name = "sender_address") val senderAddress: String?,
    val viewed: Boolean = false,
    @Json(name = "viewed_at") val viewedAt: String?,
    val archived: Boolean = false,
    val starred: Boolean = false,
    @Json(name = "payout_amount") val payoutAmount: Double?,
    @Json(name = "payout_status") val payoutStatus: String?,
    val category: String?,
    val tags: List<String> = emptyList(),
    val priority: String = "normal",
    val attachments: List<String>?,
    @Json(name = "expires_at") val expiresAt: String?,
    @Json(name = "created_at") val createdAt: String,
)

/** `GET /api/mailbox` envelope — route `backend/routes/mailbox.js:1306`. */
@JsonClass(generateAdapter = true)
data class MailboxListResponse(
    val mail: List<MailItem>,
    val count: Int,
)

/** `GET /api/mailbox/:id` envelope — route `backend/routes/mailbox.js:1466`. */
@JsonClass(generateAdapter = true)
data class MailDetailResponse(
    val mail: MailDetail,
)

@JsonClass(generateAdapter = true)
data class MailDetail(
    val id: String,
    val type: String,
    @Json(name = "created_at") val createdAt: String,
    val sender: Sender?,
    val `object`: JsonValue?,
    @Json(name = "content_format") val contentFormat: String?,
    val links: JsonArrayValue = emptyList(),
) {
    @JsonClass(generateAdapter = true)
    data class Sender(
        val id: String,
        val username: String,
        val name: String,
    )
}

/** `PATCH /api/mailbox/:id/ack` response — route `backend/routes/mailbox.js:2702`. */
@JsonClass(generateAdapter = true)
data class AckResponse(
    val message: String,
    val ackStatus: String,
)
