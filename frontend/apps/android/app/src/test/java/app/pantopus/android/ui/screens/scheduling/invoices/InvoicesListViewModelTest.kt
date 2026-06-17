@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.invoices

import app.pantopus.android.data.api.models.scheduling.GetInvoicesResponse
import app.pantopus.android.data.api.models.scheduling.InvoiceDto
import app.pantopus.android.data.api.models.scheduling.PaymentStatusResponse
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingFeatureFlags
import app.pantopus.android.data.scheduling.SchedulingRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InvoicesListViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo: SchedulingRepository = mockk(relaxed = true)
    private val auth: AuthRepository = mockk()
    private val errors = SchedulingErrorDecoder(Moshi.Builder().build())
    private val flags = SchedulingFeatureFlags().apply { environment = "local" }

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { auth.state } returns MutableStateFlow(AuthRepository.State.SignedIn(user()))
        coEvery { repo.getPaymentsStatus(any()) } returns
            NetworkResult.Success(PaymentStatusResponse(applicable = true, connected = true))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun user() = UserDto(id = "biz-1", email = "a@b.com", displayName = "A", avatarUrl = null)

    private fun vm() = InvoicesListViewModel(repo, auth, errors, flags)

    private fun invoice(
        id: String,
        cents: Int,
    ) = InvoiceDto(
        id = id,
        recipientUserId = "cust-9",
        totalCents = cents,
        currency = "USD",
        createdAt = "2026-06-11T12:00:00Z",
    )

    @Test
    fun `paid flag off shows coming soon`() =
        runTest(dispatcher) {
            flags.environment = "production"
            val model = vm()
            model.start()
            advanceUntilIdle()
            assertTrue(model.state.value is InvoicesListUiState.ComingSoon)
        }

    @Test
    fun `gate when no invoices and payments not connected`() =
        runTest(dispatcher) {
            coEvery { repo.getInvoices(any()) } returns NetworkResult.Success(GetInvoicesResponse(emptyList()))
            coEvery { repo.getPaymentsStatus(any()) } returns
                NetworkResult.Success(PaymentStatusResponse(applicable = true, connected = false))
            val model = vm()
            model.start()
            advanceUntilIdle()
            assertTrue(model.state.value is InvoicesListUiState.Gate)
        }

    @Test
    fun `empty when connected and no invoices`() =
        runTest(dispatcher) {
            coEvery { repo.getInvoices(any()) } returns NetworkResult.Success(GetInvoicesResponse(emptyList()))
            val model = vm()
            model.start()
            advanceUntilIdle()
            assertTrue(model.state.value is InvoicesListUiState.Empty)
        }

    @Test
    fun `loaded sums totals and groups by day`() =
        runTest(dispatcher) {
            coEvery { repo.getInvoices(any()) } returns
                NetworkResult.Success(GetInvoicesResponse(listOf(invoice("abc123x", 22000), invoice("def456y", 9600))))
            val model = vm()
            model.start()
            advanceUntilIdle()
            val loaded = model.state.value as InvoicesListUiState.Loaded
            assertEquals(1, loaded.sections.size)
            assertEquals(2, loaded.sections.first().invoices.size)
            assertEquals("$316.00", loaded.totalLabel)
            assertEquals("2", loaded.countLabel)
        }

    @Test
    fun `reference is derived from the invoice id`() =
        runTest(dispatcher) {
            val model = vm()
            assertEquals("INV-ABC123", model.reference(invoice("abc123def", 100)))
        }
}
