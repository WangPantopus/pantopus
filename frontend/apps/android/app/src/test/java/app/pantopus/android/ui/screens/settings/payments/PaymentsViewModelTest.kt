@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.payments

import app.cash.turbine.test
import app.pantopus.android.data.api.models.payments.AddCardSheetParamsDto
import app.pantopus.android.data.api.models.payments.PaymentMethodAckResponse
import app.pantopus.android.data.api.models.payments.PaymentMethodDto
import app.pantopus.android.data.api.models.payments.PaymentMethodsResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.payments.PaymentsRepository
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors iOS `PaymentsViewModelTests`. Covers the fixture projections
 * (seeded) plus the Phase 3 (3A) live path: methods load, add-card
 * refresh, optimistic set-default / remove, and error handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PaymentsViewModelTest {
    private lateinit var repository: PaymentsRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun vm() = PaymentsViewModel(repository)

    private fun cardDto(
        id: String,
        brand: String,
        last4: String,
        isDefault: Boolean,
    ) = PaymentMethodDto(
        id = id,
        paymentMethodType = "card",
        cardBrand = brand,
        cardLast4 = last4,
        cardExpMonth = 3,
        cardExpYear = 2027,
        isDefault = isDefault,
    )

    // MARK: - Fixture projections

    @Test
    fun load_populated_projects_all_sections() =
        runTest {
            val vm = vm()
            vm.seed(PaymentsSeed.Populated)
            vm.load()
            val content = (vm.state.value as PaymentsUiState.Loaded).content

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
            val vm = vm()
            vm.seed(PaymentsSeed.Empty)
            vm.load()
            val content = (vm.state.value as PaymentsUiState.Loaded).content

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
        assertTrue(vm().state.value is PaymentsUiState.Loading)
    }

    // MARK: - Live path (Phase 3 / 3A)

    @Test
    fun live_load_projects_real_methods() =
        runTest {
            coEvery { repository.paymentMethods() } returns
                NetworkResult.Success(
                    PaymentMethodsResponse(
                        listOf(
                            cardDto("pm_1", "visa", "4242", isDefault = true),
                            cardDto("pm_2", "mastercard", "4444", isDefault = false),
                        ),
                    ),
                )
            val vm = vm()
            vm.load()
            val content = (vm.state.value as PaymentsUiState.Loaded).content

            assertEquals(2, content.methods.size)
            assertEquals(PaymentMethodBrand.Visa, content.methods[0].brand)
            assertEquals("Visa •• 4242", content.methods[0].label)
            assertEquals("Expires 03/27", content.methods[0].subtext)
            assertEquals("Default", content.methods[0].chip?.label)
            assertNull("Only the default method carries a chip", content.methods[1].chip)
            // Live frame never fabricates a balance — Payouts/Connect land in 3C.
            assertNull(content.balance)
            assertTrue(content.payouts.stripe.trailing is PaymentsRowTrailing.CtaChip)
        }

    @Test
    fun live_load_failure_is_error() =
        runTest {
            coEvery { repository.paymentMethods() } returns NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = vm()
            vm.load()
            assertTrue(vm.state.value is PaymentsUiState.Error)
        }

    @Test
    fun set_default_optimistic_then_reconcile() =
        runTest {
            val initial =
                PaymentMethodsResponse(
                    listOf(
                        cardDto("pm_1", "visa", "4242", isDefault = true),
                        cardDto("pm_2", "mastercard", "4444", isDefault = false),
                    ),
                )
            val reordered =
                PaymentMethodsResponse(
                    listOf(
                        cardDto("pm_2", "mastercard", "4444", isDefault = true),
                        cardDto("pm_1", "visa", "4242", isDefault = false),
                    ),
                )
            coEvery { repository.paymentMethods() } returnsMany
                listOf(NetworkResult.Success(initial), NetworkResult.Success(reordered))
            coEvery { repository.setDefault("pm_2") } returns NetworkResult.Success(PaymentMethodAckResponse("ok"))

            val vm = vm()
            vm.load()
            vm.setDefault("pm_2")

            val content = (vm.state.value as PaymentsUiState.Loaded).content
            val default = content.methods.first { it.chip?.tone == PaymentsChipTone.Primary }
            assertEquals("pm_2", default.id)
        }

    @Test
    fun set_default_failure_reverts_and_emits_message() =
        runTest {
            coEvery { repository.paymentMethods() } returns
                NetworkResult.Success(
                    PaymentMethodsResponse(
                        listOf(
                            cardDto("pm_1", "visa", "4242", isDefault = true),
                            cardDto("pm_2", "mastercard", "4444", isDefault = false),
                        ),
                    ),
                )
            coEvery { repository.setDefault("pm_2") } returns NetworkResult.Failure(NetworkError.Server(500, "boom"))

            val vm = vm()
            vm.load()
            vm.events.test {
                vm.setDefault("pm_2")
                assertTrue(awaitItem() is PaymentsEvent.ShowMessage)
                cancelAndIgnoreRemainingEvents()
            }
            val content = (vm.state.value as PaymentsUiState.Loaded).content
            // Reverted: pm_1 is still the default.
            assertEquals("pm_1", content.methods.first { it.chip?.tone == PaymentsChipTone.Primary }.id)
        }

    @Test
    fun remove_optimistic_then_reconcile() =
        runTest {
            val initial =
                PaymentMethodsResponse(
                    listOf(
                        cardDto("pm_1", "visa", "4242", isDefault = true),
                        cardDto("pm_2", "mastercard", "4444", isDefault = false),
                    ),
                )
            val afterRemoval =
                PaymentMethodsResponse(listOf(cardDto("pm_1", "visa", "4242", isDefault = true)))
            coEvery { repository.paymentMethods() } returnsMany
                listOf(NetworkResult.Success(initial), NetworkResult.Success(afterRemoval))
            coEvery { repository.removeMethod("pm_2") } returns NetworkResult.Success(PaymentMethodAckResponse("ok"))

            val vm = vm()
            vm.load()
            vm.removeMethod("pm_2")

            val content = (vm.state.value as PaymentsUiState.Loaded).content
            assertEquals(1, content.methods.size)
            assertEquals("pm_1", content.methods.first().id)
        }

    @Test
    fun tap_add_method_emits_present_sheet_event() =
        runTest {
            coEvery { repository.paymentMethods() } returns NetworkResult.Success(PaymentMethodsResponse(emptyList()))
            coEvery { repository.addCardSheetParams() } returns
                NetworkResult.Success(
                    AddCardSheetParamsDto(
                        setupIntent = "seti_secret",
                        ephemeralKey = "ek_test",
                        customer = "cus_1",
                        publishableKey = "pk_test",
                    ),
                )
            val vm = vm()
            vm.load()
            vm.events.test {
                vm.tapAddMethod()
                val event = awaitItem()
                assertTrue(event is PaymentsEvent.PresentAddCardSheet)
                assertEquals("seti_secret", (event as PaymentsEvent.PresentAddCardSheet).params.setupIntent)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun add_card_completed_refreshes_methods() =
        runTest {
            coEvery { repository.paymentMethods() } returnsMany
                listOf(
                    NetworkResult.Success(PaymentMethodsResponse(emptyList())),
                    NetworkResult.Success(PaymentMethodsResponse(listOf(cardDto("pm_1", "visa", "4242", isDefault = true)))),
                )
            val vm = vm()
            vm.load()
            assertTrue((vm.state.value as PaymentsUiState.Loaded).content.methods.isEmpty())

            vm.onAddCardOutcome(AddCardOutcome.Completed)

            assertEquals(1, (vm.state.value as PaymentsUiState.Loaded).content.methods.size)
        }
}
