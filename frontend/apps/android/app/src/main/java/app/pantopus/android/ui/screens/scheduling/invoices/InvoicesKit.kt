@file:Suppress("PackageNaming", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.scheduling.invoices

import app.pantopus.android.data.api.models.scheduling.InvoiceDto
import app.pantopus.android.ui.screens.scheduling.packages.PackagesFormat

/**
 * Stream A15 — invoice helpers (G12/G13). Defensively parses the invoice's
 * `line_items` JSON (gig-system shape varies) into renderable rows, and groups
 * invoices by created day for the list. Mirrors iOS `InvoicesKit.swift`.
 *
 * Backend/Foundation note: the shared `InvoiceDto` exposes id / business_user_id
 * / recipient_user_id / total_cents / currency / line_items / created_at only.
 * The BusinessInvoice table additionally has status / subtotal_cents / fee_cents
 * / due_date / memo / paid_at — those aren't in the DTO, so the design's status
 * pills, status filters, subtotal+fee breakdown, due date, payment timeline,
 * sender note, and payer display names can't be rendered yet. Flagged as a
 * Foundation DTO gap (see the PR) rather than patched locally.
 */

/** A single renderable invoice line item parsed from the untyped `line_items`. */
data class InvoiceLineItem(
    val label: String,
    val quantity: Int?,
    val unitCents: Int?,
    val totalCents: Int?,
)

object InvoiceParsing {
    private val LABEL_KEYS = listOf("description", "name", "label", "title")
    private val QTY_KEYS = listOf("quantity", "qty")
    private val UNIT_KEYS = listOf("unit_amount_cents", "unit_cents", "unit_price_cents")
    private val TOTAL_KEYS =
        listOf("total_cents", "amount_cents", "line_total_cents", "total", "amount")

    /**
     * Parse `line_items` maps into rows. Tolerant of key naming and number
     * types (Moshi decodes untyped numbers as Double). Rows that carry no money
     * at all (metadata-only) are skipped.
     */
    fun lineItems(items: List<Map<String, Any?>>?): List<InvoiceLineItem> {
        if (items.isNullOrEmpty()) return emptyList()
        return items.mapNotNull { dict ->
            val unit = firstInt(dict, UNIT_KEYS)
            val total = firstInt(dict, TOTAL_KEYS)
            val hasMoneyKey = dict.keys.any { it.contains("amount") || it.contains("total") }
            if (unit == null && total == null && !hasMoneyKey) return@mapNotNull null
            InvoiceLineItem(
                label = firstString(dict, LABEL_KEYS) ?: "Item",
                quantity = firstInt(dict, QTY_KEYS),
                unitCents = unit,
                totalCents = total,
            )
        }
    }

    private fun firstString(
        dict: Map<String, Any?>,
        keys: List<String>,
    ): String? {
        for (key in keys) {
            val value = dict[key]
            if (value is String && value.isNotEmpty()) return value
        }
        return null
    }

    private fun firstInt(
        dict: Map<String, Any?>,
        keys: List<String>,
    ): Int? {
        for (key in keys) {
            when (val value = dict[key]) {
                is Int -> return value
                is Long -> return value.toInt()
                is Double -> return value.toInt()
                is String -> value.toDoubleOrNull()?.let { return it.toInt() }
                else -> Unit
            }
        }
        return null
    }
}

/** A day-grouped section of invoices (created_at order preserved within each day). */
data class InvoiceDaySection(
    val day: String,
    val invoices: List<InvoiceDto>,
)

object InvoiceGrouping {
    fun byDay(invoices: List<InvoiceDto>): List<InvoiceDaySection> {
        val order = mutableListOf<String>()
        val buckets = linkedMapOf<String, MutableList<InvoiceDto>>()
        invoices.forEach { invoice ->
            val day = PackagesFormat.dayString(invoice.createdAt) ?: "Earlier"
            if (buckets[day] == null) {
                buckets[day] = mutableListOf()
                order.add(day)
            }
            buckets.getValue(day).add(invoice)
        }
        return order.map { InvoiceDaySection(day = it, invoices = buckets.getValue(it)) }
    }
}
