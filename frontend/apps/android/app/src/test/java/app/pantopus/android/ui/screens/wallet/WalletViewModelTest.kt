@file:Suppress("PackageNaming", "FunctionNaming")

package app.pantopus.android.ui.screens.wallet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A10.10 — Covers the Wallet VM:
 *   - initial loading state,
 *   - load() selects populated vs. hold from the content,
 *   - the shape of the populated + hold sample fixtures.
 */
class WalletViewModelTest {
    @Test
    fun initial_state_is_loading() {
        val vm = WalletViewModel()
        assertEquals(WalletUiState.Loading, vm.state.value)
    }

    @Test
    fun load_resolves_to_populated_for_populated_fixture() {
        val vm = WalletViewModel()
        vm.setFixture(WalletSampleData.populated)
        vm.load()
        val state = vm.state.value
        assertTrue("expected Populated, got $state", state is WalletUiState.Populated)
        val content = (state as WalletUiState.Populated).content
        assertEquals("847.50", content.available)
        assertFalse(content.isOnHold)
        assertFalse(content.payoutMethod.warn)
        assertFalse(content.taxDocs.ready)
    }

    @Test
    fun load_resolves_to_hold_when_state_present() {
        val vm = WalletViewModel()
        vm.setFixture(WalletSampleData.onHold)
        vm.load()
        val state = vm.state.value
        assertTrue("expected Hold, got $state", state is WalletUiState.Hold)
        val content = (state as WalletUiState.Hold).content
        assertNotNull(content.holdState)
        assertTrue(content.payoutMethod.warn)
        assertTrue(content.taxDocs.ready)
        assertEquals("Bank verification expired", content.holdState?.bannerHeadline)
    }

    @Test
    fun populated_fixture_shape() {
        val content = WalletSampleData.populated
        assertEquals(7, content.activity.size)
        assertEquals("7421", content.payoutMethod.last4)
        assertEquals("Instant payout · 1–3 minutes", content.payoutMethod.bodyText)
        assertEquals("\$1,284.50", content.monthValue)
        assertTrue(content.monthMeta.contains("22%"))
        // First two rows fall on "Today" — same-day grouping renders one header.
        assertEquals("Today", content.activity[0].day)
        assertEquals("Today", content.activity[1].day)
        assertEquals("Yesterday", content.activity[2].day)
    }

    @Test
    fun hold_fixture_shape() {
        val content = WalletSampleData.onHold
        assertNotNull(content.holdState)
        assertEquals(4, content.activity.size)
        assertEquals("Verification expired Nov 30", content.payoutMethod.bodyText)
        assertTrue(content.taxDocs.bodyText.contains("1099-NEC"))
        assertEquals(
            "Re-verify your bank above to unlock payouts.",
            content.holdState?.withdrawFootnote,
        )
    }

    @Test
    fun activity_categories_cover_audit_palette() {
        val cats = WalletSampleData.populated.activity.map { it.category }.toSet()
        // Audit calls out every category present in the populated frame:
        // cleaning · child-care · handyman · pet-care · bank · fee.
        assertEquals(
            setOf(
                ActivityCategory.Cleaning,
                ActivityCategory.ChildCare,
                ActivityCategory.Handyman,
                ActivityCategory.PetCare,
                ActivityCategory.Bank,
                ActivityCategory.Fee,
            ),
            cats,
        )
    }

    @Test
    fun fee_row_flagged_and_outbound() {
        val fee = WalletSampleData.populated.activity.first { it.isFee }
        assertEquals(ActivityDirection.Out, fee.direction)
        assertEquals(ActivityCategory.Fee, fee.category)
    }

    @Test
    fun bank_row_is_outbound_payout() {
        val bankRow = WalletSampleData.populated.activity.first { it.category == ActivityCategory.Bank }
        assertEquals(ActivityDirection.Out, bankRow.direction)
        assertEquals("Withdrawal", bankRow.description)
        assertTrue(bankRow.counterparty.contains("7421"))
    }
}
