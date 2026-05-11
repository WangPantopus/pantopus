@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.data.api.models.mailbox.v2

import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MailboxCategoryPayloadTest {
    @Test fun coupon_decodes_required_fields() {
        val payload =
            mapOf(
                "headline" to "30% OFF",
                "subcopy" to "at any participating Whole Foods",
                "code" to "PANTO30",
                "expires_at" to "2026-05-31",
                "merchant" to "Whole Foods Market",
                "min_spend" to "$25",
                "fine_print" to "One per customer.",
            )
        val resolved = MailboxCategoryPayload.resolve(MailItemCategory.Coupon, payload)
        assertTrue(resolved is MailboxCategoryPayload.Coupon)
        val coupon = (resolved as MailboxCategoryPayload.Coupon).detail
        assertEquals("30% OFF", coupon.headline)
        assertEquals("PANTO30", coupon.code)
        assertEquals("$25", coupon.minimumSpend)
    }

    @Test fun coupon_missing_headline_falls_back_to_other() {
        val payload = mapOf("code" to "X")
        assertEquals(
            MailboxCategoryPayload.Other,
            MailboxCategoryPayload.resolve(MailItemCategory.Coupon, payload),
        )
    }

    @Test fun booklet_decodes_pages_and_count() {
        val payload =
            mapOf(
                "pages" to listOf("https://x/p1.png", "https://x/p2.png"),
                "summary" to "Spring catalog",
                "page_count" to 24,
            )
        val resolved = MailboxCategoryPayload.resolve(MailItemCategory.Booklet, payload)
        assertTrue(resolved is MailboxCategoryPayload.Booklet)
        val booklet = (resolved as MailboxCategoryPayload.Booklet).detail
        assertEquals(2, booklet.pages.size)
        assertEquals(24, booklet.pageCount)
    }

    @Test fun booklet_no_pages_falls_back() {
        val payload = mapOf("summary" to "no pages")
        assertEquals(
            MailboxCategoryPayload.Other,
            MailboxCategoryPayload.resolve(MailItemCategory.Booklet, payload),
        )
    }

    @Test fun certified_decodes_chain_and_completion_state() {
        val payload =
            mapOf(
                "reference_number" to "CRT-2026-0091",
                "document_type" to "Court summons",
                "acknowledge_by" to "2026-05-25",
                "chain" to
                    listOf(
                        mapOf("id" to "sent", "label" to "Sent", "occurred_at" to "2026-05-08"),
                        mapOf("id" to "delivered", "label" to "Delivered", "occurred_at" to "2026-05-10"),
                        mapOf("id" to "ack", "label" to "Acknowledged"),
                    ),
                "notice_body" to "You are summoned.",
                "terms_url" to "https://x/terms",
                "is_acknowledged" to false,
            )
        val resolved = MailboxCategoryPayload.resolve(MailItemCategory.Certified, payload)
        assertTrue(resolved is MailboxCategoryPayload.Certified)
        val cert = (resolved as MailboxCategoryPayload.Certified).detail
        assertEquals("CRT-2026-0091", cert.referenceNumber)
        assertEquals(3, cert.chain.size)
        assertTrue(cert.chain[0].isComplete)
        assertTrue(cert.chain[1].isComplete)
        assertFalse(cert.chain[2].isComplete) // no occurred_at
        assertNull(cert.chain[2].occurredAt)
    }

    @Test fun non_p18_category_returns_other() {
        val payload = mapOf("anything" to "goes")
        assertEquals(
            MailboxCategoryPayload.Other,
            MailboxCategoryPayload.resolve(MailItemCategory.Bill, payload),
        )
    }
}
