@file:Suppress("PackageNaming", "FunctionNaming", "MagicNumber")

package app.pantopus.android.ui.screens.mailbox.earn

import app.pantopus.android.data.api.models.mailbox.v2.EarnBalanceDto
import app.pantopus.android.data.api.models.mailbox.v2.EarnBalanceResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A10.11 — Covers the Earn VM:
 *   - initial loading state,
 *   - load() maps the live `/earn/balance` sums onto the populated hero
 *     (active earner) vs. the empty new-earner frame (all-zero balance),
 *   - failure surfaces the error state,
 *   - the shape of the populated + ways-to-earn sample fixtures (the
 *     Stripe-phase placeholder slots).
 *
 * Mirrors the iOS `EarnViewModelTests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EarnViewModelTest {
    private val repository: MailboxRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun balance(
        total: Double,
        available: Double,
        pending: Double,
    ) = EarnBalanceResponse(EarnBalanceDto(total = total, available = available, pending = pending))

    @Test
    fun initial_state_is_loading() {
        coEvery { repository.earnBalance() } returns NetworkResult.Success(balance(0.0, 0.0, 0.0))
        val vm = EarnViewModel(repository)
        assertEquals(EarnUiState.Loading, vm.state.value)
    }

    @Test
    fun load_resolves_to_populated_for_active_earner() =
        runTest {
            coEvery { repository.earnBalance() } returns NetworkResult.Success(balance(372.40, 312.40, 60.00))
            val vm = EarnViewModel(repository)
            vm.load()
            val state = vm.state.value
            assertTrue("expected Populated, got $state", state is EarnUiState.Populated)
            val content = (state as EarnUiState.Populated).content
            // Live balance is spliced over the placeholder frame.
            assertEquals("312.40", content.available)
            assertEquals("\$60.00", content.pending)
        }

    @Test
    fun load_resolves_to_empty_for_new_earner() =
        runTest {
            coEvery { repository.earnBalance() } returns NetworkResult.Success(balance(0.0, 0.0, 0.0))
            val vm = EarnViewModel(repository)
            vm.load()
            val state = vm.state.value
            assertTrue("expected Empty, got $state", state is EarnUiState.Empty)
            val ways = (state as EarnUiState.Empty).waysToEarn
            // The new-earner frame still carries the shared `Ways to earn` rows.
            assertEquals(3, ways.size)
            assertEquals(EarnWayKind.Browse, ways.first().kind)
        }

    @Test
    fun load_surfaces_error_on_failure() =
        runTest {
            coEvery { repository.earnBalance() } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = EarnViewModel(repository)
            vm.load()
            assertTrue("expected Error, got ${vm.state.value}", vm.state.value is EarnUiState.Error)
        }

    @Test
    fun populated_fixture_shape() {
        val content = EarnSampleData.populated
        assertEquals(4, content.earnings.size)
        assertEquals("74%", content.weeklyGoal.ringLabel)
        assertEquals("\$52 to go", content.weeklyGoal.headline)
        assertEquals("YTD earnings \$4,920 · 1099 available mid-Jan", content.taxDocs.bodyText)
        assertEquals("Every Friday · cleared balance", content.autoCashOut.detail)
        assertEquals("Today", content.earnings[0].day)
    }

    @Test
    fun ways_to_earn_shape() {
        val ways = EarnSampleData.waysToEarn
        assertEquals(
            listOf(EarnWayKind.Browse, EarnWayKind.Refer, EarnWayKind.Offer),
            ways.map { it.kind },
        )
        // Only the first row is featured (the tinted Browse launcher).
        assertTrue(ways[0].featured)
        assertFalse(ways[1].featured)
        assertFalse(ways[2].featured)
        assertEquals(
            listOf(EarnAccent.Primary, EarnAccent.Home, EarnAccent.Business),
            ways.map { it.accent },
        )
    }

    @Test
    fun exactly_one_pending_earning() {
        val pending = EarnSampleData.populated.earnings.filter { it.status is EarnStatus.Pending }
        assertEquals(1, pending.size)
        assertEquals("60.00", pending.first().amount)
        assertEquals("Dec 3", (pending.first().status as EarnStatus.Pending).clearsLabel)
    }

    @Test
    fun earning_categories_are_money_in_subset() {
        val cats =
            EarnSampleData.populated.earnings
                .map { it.category }
                .toSet()
        // Earn is money-in only — no bank / fee rows (those are Wallet's).
        assertEquals(
            setOf(
                EarnCategory.Cleaning,
                EarnCategory.PetCare,
                EarnCategory.Handyman,
                EarnCategory.ChildCare,
            ),
            cats,
        )
    }
}
