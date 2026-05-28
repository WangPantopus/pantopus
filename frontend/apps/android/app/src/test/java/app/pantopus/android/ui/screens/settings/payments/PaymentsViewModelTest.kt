@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.payments

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors iOS `PaymentsViewModelTests`. Asserts the populated and
 * empty fixtures project end-to-end through the view-model into
 * [PaymentsUiState.Loaded].
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_populated_projects_all_sections() =
        runTest {
            val vm = PaymentsViewModel()
            vm.seed(PaymentsSeed.Populated)
            vm.load()
            val loaded = vm.state.value as PaymentsUiState.Loaded
            val content = loaded.content

            assertNotNull(content.balance)
            assertEquals("Available to pay out", content.balance?.overline)
            assertEquals("124.50", content.balance?.amount)
            assertEquals("Weekly", content.balance?.frequencyPill)

            assertEquals(3, content.methods.size)
            assertEquals(PaymentMethodBrand.Visa, content.methods.first().brand)
            assertEquals("Default", content.methods.first().chip?.label)
            assertEquals(PaymentsChipTone.Primary, content.methods.first().chip?.tone)
            assertEquals(PaymentMethodBrand.ApplePay, content.methods.last().brand)

            val stripeTrailing = content.payouts.stripe.trailing as PaymentsRowTrailing.ChipChevron
            assertEquals("Connected", stripeTrailing.label)
            assertEquals(PaymentsChipTone.Success, stripeTrailing.tone)

            assertNotNull(content.payouts.payoutSchedule)
            assertEquals("Weekly · Mondays", content.payouts.payoutSchedule?.subtext)

            val taxTrailing = content.payouts.taxInfo.trailing as PaymentsRowTrailing.ChipChevron
            assertEquals("On file", taxTrailing.label)

            val activity = content.activity as PaymentsActivity.Stats
            assertEquals(3, activity.rows.size)
            assertEquals("Lifetime", activity.rows[0].label)
            assertEquals("Year to date", activity.rows[1].label)
            assertEquals("Last payout", activity.rows[2].label)

            assertTrue("Populated frame surfaces destructive card", content.canCloseAccount)
        }

    @Test
    fun load_empty_hides_hero_and_gates_payout_rows() =
        runTest {
            val vm = PaymentsViewModel()
            vm.seed(PaymentsSeed.Empty)
            vm.load()
            val loaded = vm.state.value as PaymentsUiState.Loaded
            val content = loaded.content

            assertNull("Empty frame omits the balance hero", content.balance)
            assertTrue(content.methods.isEmpty())
            assertNull("Schedule row gates behind Stripe Connect", content.payouts.payoutSchedule)

            val stripeTrailing = content.payouts.stripe.trailing as PaymentsRowTrailing.CtaChip
            assertEquals("Connect", stripeTrailing.label)
            assertEquals(PaymentsChipTone.Primary, stripeTrailing.tone)

            assertTrue(content.payouts.payoutMethod.trailing is PaymentsRowTrailing.GatedDash)
            assertTrue(content.payouts.taxInfo.trailing is PaymentsRowTrailing.GatedDash)

            val activity = content.activity as PaymentsActivity.Empty
            assertEquals("No transactions yet", activity.title)

            assertFalse("Empty frame hides the destructive card", content.canCloseAccount)
        }

    @Test
    fun initial_state_is_loading() {
        val vm = PaymentsViewModel()
        assertTrue(vm.state.value is PaymentsUiState.Loading)
    }
}
