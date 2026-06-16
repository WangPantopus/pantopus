@file:Suppress("PackageNaming")

package app.pantopus.android.data.api.models.scheduling

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Business invoices (gig-system reuse). `GET /invoices`, `GET /invoices/:id`,
 * `POST /invoices/:id/send` (business-only). `line_items` is a flexible array.
 */
@JsonClass(generateAdapter = true)
data class InvoiceDto(
    val id: String,
    @Json(name = "business_user_id") val businessUserId: String? = null,
    @Json(name = "recipient_user_id") val recipientUserId: String? = null,
    @Json(name = "total_cents") val totalCents: Int? = null,
    val currency: String? = null,
    @Json(name = "line_items") val lineItems: List<Map<String, Any?>>? = null,
    @Json(name = "created_at") val createdAt: String? = null,
)

/** `GET /invoices` — `{ invoices: [...] }`. */
@JsonClass(generateAdapter = true)
data class GetInvoicesResponse(
    val invoices: List<InvoiceDto> = emptyList(),
)

/** `GET /invoices/:id` — `{ invoice: … }`. */
@JsonClass(generateAdapter = true)
data class InvoiceResponse(
    val invoice: InvoiceDto,
)
