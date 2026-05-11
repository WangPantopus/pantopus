@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.mailbox.v2

import app.pantopus.android.data.api.models.common.JsonValue

/**
 * Booklet sub-payload decoded from `mail.object_payload` when
 * `mail_type == "booklet"`. Backend stores this as untyped JSON in S3.
 * [decodeFromObjectPayload] returns null when the payload doesn't list
 * any pages.
 */
data class BookletDetailDto(
    val pages: List<String>,
    val summary: String?,
    val pageCount: Int,
) {
    companion object {
        fun decodeFromObjectPayload(payload: JsonValue?): BookletDetailDto? {
            if (payload == null) return null
            val rawPages = payload["pages"] as? List<*> ?: emptyList<Any?>()
            val pages = rawPages.mapNotNull { it as? String }
            if (pages.isEmpty()) return null
            val pageCount =
                (payload["page_count"] as? Number)?.toInt() ?: pages.size
            return BookletDetailDto(
                pages = pages,
                summary = payload["summary"] as? String,
                pageCount = pageCount,
            )
        }
    }
}
