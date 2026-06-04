@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.mailbox.v2

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Mail-task DTOs for `GET /api/mailbox/v2/p3/tasks`
 * (`backend/routes/mailboxV2Phase3.js:831`). The handler returns
 * mail-linked `HomeTask` rows partitioned into `active` / `completed`,
 * each shaped to the keys below plus a joined `mail_preview` /
 * `mail_sender` from the related `Mail` row.
 *
 * Only the basic HomeTask fields exist here; the "elf" / sub-task / magic
 * enrichment the screen also renders is a separate `magicTask` concept and
 * has no source on this endpoint.
 */

/** Envelope for `GET /api/mailbox/v2/p3/tasks`. */
@JsonClass(generateAdapter = true)
data class P3TasksResponse(
    val active: List<P3TaskDto> = emptyList(),
    val completed: List<P3TaskDto> = emptyList(),
)

/** One mail-linked task (shaped `HomeTask` row). */
@JsonClass(generateAdapter = true)
data class P3TaskDto(
    val id: String,
    @Json(name = "home_id") val homeId: String? = null,
    @Json(name = "mail_id") val mailId: String? = null,
    val title: String? = null,
    val description: String? = null,
    @Json(name = "due_at") val dueAt: String? = null,
    val priority: String? = null,
    val status: String? = null,
    @Json(name = "assigned_to") val assignedTo: String? = null,
    @Json(name = "converted_to_gig_id") val convertedToGigId: String? = null,
    @Json(name = "created_at") val createdAt: String? = null,
    @Json(name = "mail_preview") val mailPreview: String? = null,
    @Json(name = "mail_sender") val mailSender: String? = null,
)
