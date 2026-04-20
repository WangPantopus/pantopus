package app.pantopus.android.data.api.models.mailbox.v2

import app.pantopus.android.data.api.models.common.JsonArrayValue
import app.pantopus.android.data.api.models.common.JsonValue
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** `GET /api/mailbox/v2/drawers` — route `backend/routes/mailboxV2.js:214`. */
@JsonClass(generateAdapter = true)
data class DrawerListResponse(
    val drawers: List<Drawer>,
)

@JsonClass(generateAdapter = true)
data class Drawer(
    val drawer: String,
    @Json(name = "display_name") val displayName: String,
    val icon: String,
    @Json(name = "unread_count") val unreadCount: Int,
    @Json(name = "urgent_count") val urgentCount: Int,
    @Json(name = "last_item_at") val lastItemAt: String?,
)

/** `GET /api/mailbox/v2/drawer/:drawer` — route `backend/routes/mailboxV2.js:280`. */
@JsonClass(generateAdapter = true)
data class DrawerItemsResponse(
    val mail: List<DrawerMail>,
    val total: Int,
    val drawer: String,
)

@JsonClass(generateAdapter = true)
data class DrawerMail(
    val id: String,
    val type: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "display_title") val displayTitle: String?,
    @Json(name = "preview_text") val previewText: String?,
    @Json(name = "ack_required") val ackRequired: Boolean?,
    @Json(name = "ack_status") val ackStatus: String?,
    val viewed: Boolean = false,
    val starred: Boolean = false,
    val archived: Boolean = false,
    val sender: SenderRef?,
    @Json(name = "sender_display") val senderDisplay: String,
    @Json(name = "sender_trust") val senderTrust: String,
    val `package`: JsonValue?,
) {
    @JsonClass(generateAdapter = true)
    data class SenderRef(
        val name: String?,
        val username: String?,
    )
}

/** `GET /api/mailbox/v2/item/:id` — route `backend/routes/mailboxV2.js:366`. */
@JsonClass(generateAdapter = true)
data class MailboxV2ItemResponse(
    val mail: MailboxV2Item,
)

@JsonClass(generateAdapter = true)
data class MailboxV2Item(
    val id: String,
    val type: String,
    @Json(name = "created_at") val createdAt: String,
    @Json(name = "display_title") val displayTitle: String?,
    @Json(name = "preview_text") val previewText: String?,
    val sender: DrawerMail.SenderRef?,
    @Json(name = "sender_display") val senderDisplay: String,
    @Json(name = "sender_trust") val senderTrust: String,
    val `package`: JsonValue?,
    val packageInfo: JsonValue?,
    val packageTimeline: JsonArrayValue = emptyList(),
    val objectPayload: JsonValue?,
)

/** `POST /api/mailbox/v2/item/:id/action` — route `backend/routes/mailboxV2.js:459`. */
@JsonClass(generateAdapter = true)
data class MailboxItemActionRequest(
    /** One of: pay, sign, forward, file, shred, remind, split, acknowledge, share_household, create_task, dispute. */
    val action: String,
)

@JsonClass(generateAdapter = true)
data class MailboxItemActionResponse(
    val message: String,
    val action: String,
)

/**
 * `GET /api/mailbox/v2/package/:mailId` — route `backend/routes/mailboxV2.js:634`.
 * Package + timeline columns evolve server-side; they're exposed as JSON
 * maps rather than invented types.
 */
@JsonClass(generateAdapter = true)
data class PackageDetailResponse(
    val `package`: JsonValue,
    val timeline: JsonArrayValue,
    val sender: Sender?,
) {
    @JsonClass(generateAdapter = true)
    data class Sender(
        val display: String,
        val trust: String,
    )
}

/** `PATCH /api/mailbox/v2/package/:mailId/status` — route `backend/routes/mailboxV2.js:670`. */
@JsonClass(generateAdapter = true)
data class PackageStatusUpdateRequest(
    /** One of: pre_receipt, in_transit, out_for_delivery, delivered, exception. */
    val status: String,
    val location: String? = null,
    val photoUrl: String? = null,
    val deliveryLocationNote: String? = null,
)

@JsonClass(generateAdapter = true)
data class PackageStatusUpdateResponse(
    val message: String,
    val status: String,
    val previousStatus: String,
)
