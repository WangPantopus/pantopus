@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.pantopus.android.data.api.models.payments.CreatePaymentIntentRequest
import app.pantopus.android.data.api.models.payments.PaymentIntentSheetParamsDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.payments.PaymentsRepository
import app.pantopus.android.ui.screens.settings.payments.CheckoutOutcome
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Mirrors iOS `InvoiceCheckoutTests`. Covers the Block 3B pay step: create the
 * PaymentIntent (`POST /api/payments/intent`) → present PaymentSheet → map the
 * outcome and re-read server state. The Stripe SDK is exercised in the screen,
 * so here we assert the VM's event emission + outcome handling.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InvoiceDetailViewModelTest {
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

    private fun vm(withCheckout: Boolean = false) =
        InvoiceDetailViewModel(
            savedStateHandle =
                SavedStateHandle(
                    buildMap {
                        put(InvoiceDetailViewModel.INVOICE_ID_KEY, "inv-1")
                        if (withCheckout) {
                            put(InvoiceDetailViewModel.LISTING_ID_KEY, "listing-1")
                            put(InvoiceDetailViewModel.OFFER_ID_KEY, "offer-1")
                        }
                    },
                ),
            paymentsRepository = repository,
        )

    private val sheetParams =
        PaymentIntentSheetParamsDto(
            clientSecret = "pi_secret_1",
            paymentIntentId = "pi_1",
            customer = "cus_1",
            ephemeralKey = "ek_1",
            publishableKey = "pk_test",
        )

    @Test
    fun load_projects_due_fixture() =
        runTest {
            val vm = vm()
            vm.load()
            val content = (vm.state.value as ContentDetailUiState.Loaded).content
            assertEquals(ContentDetailKind.Invoice, content.kind)
            assertEquals("Pay $642.85", content.dock.primary.label)
        }

    // checkout.paymentSheet — pay() creates the intent and asks the screen to present.
    @Test
    fun pay_success_emits_present_checkout_event() =
        runTest {
            coEvery { repository.createPaymentIntent(any()) } returns NetworkResult.Success(sheetParams)
            val vm = vm(withCheckout = true)
            vm.events.test {
                vm.pay()
                val event = awaitItem()
                assertTrue(event is InvoiceDetailEvent.PresentCheckout)
                assertEquals("pi_secret_1", (event as InvoiceDetailEvent.PresentCheckout).params.clientSecret)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(InvoicePaymentStatus.Paying, vm.paymentStatus.value)
        }

    @Test
    fun pay_passes_order_reference_to_backend() =
        runTest {
            val slot = mutableListOf<CreatePaymentIntentRequest>()
            coEvery { repository.createPaymentIntent(capture(slot)) } returns NetworkResult.Success(sheetParams)
            val vm = vm(withCheckout = true)
            vm.pay()
            assertEquals("listing-1", slot.first().listingId)
            assertEquals("offer-1", slot.first().offerId)
            assertEquals(null, slot.first().gigId)
        }

    @Test
    fun pay_without_order_reference_declines_without_intent() =
        runTest {
            val vm = vm()
            vm.pay()
            assertEquals(
                InvoicePaymentStatus.Declined("This invoice can't be paid yet."),
                vm.paymentStatus.value,
            )
        }

    // Intent creation fails → declined, no sheet.
    @Test
    fun pay_intent_failure_marks_declined() =
        runTest {
            coEvery { repository.createPaymentIntent(any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))
            val vm = vm(withCheckout = true)
            vm.pay()
            assertTrue(vm.paymentStatus.value is InvoicePaymentStatus.Declined)
        }

    // checkout.paySuccess — completed sheet re-reads server state.
    @Test
    fun outcome_paid_refreshes_and_marks_paid() =
        runTest {
            val vm = vm()
            vm.onCheckoutOutcome(CheckoutOutcome.Paid)
            assertEquals(InvoicePaymentStatus.Paid, vm.paymentStatus.value)
            assertTrue(vm.state.value is ContentDetailUiState.Loaded)
        }

    // checkout.cancel
    @Test
    fun outcome_canceled_marks_canceled() =
        runTest {
            val vm = vm()
            vm.onCheckoutOutcome(CheckoutOutcome.Canceled)
            assertEquals(InvoicePaymentStatus.Canceled, vm.paymentStatus.value)
        }

    // checkout.payDeclined
    @Test
    fun outcome_declined_surfaces_message() =
        runTest {
            val vm = vm()
            vm.onCheckoutOutcome(CheckoutOutcome.Declined("Your card was declined."))
            assertEquals(
                InvoicePaymentStatus.Declined("Your card was declined."),
                vm.paymentStatus.value,
            )
        }
}
