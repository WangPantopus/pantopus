@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.bills

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.BillDto
import app.pantopus.android.data.api.models.homes.GetHomeBillsResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowTrailing
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
import java.math.BigDecimal
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BillsListViewModelTest {
    private val repo: HomesRepository = mockk()

    /** Fixed clock so chip derivation and subtitle formatting are
     *  deterministic — 2026-05-15T12:00:00Z. */
    private val fixedNow: Instant = Instant.parse("2026-05-15T12:00:00Z")

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeBill(
        id: String,
        status: String = "pending",
        dueDate: String? = "2026-05-20T00:00:00Z",
        paidAt: String? = null,
        provider: String? = "ConEd",
        amount: BigDecimal = BigDecimal("142.80"),
    ) = BillDto(
        id = id,
        homeId = "home-1",
        billType = "electric",
        providerName = provider,
        amount = amount,
        dueDate = dueDate,
        status = status,
        paidAt = paidAt,
    )

    private fun makeVm(): BillsListViewModel =
        BillsListViewModel(
            repo = repo,
            savedStateHandle = SavedStateHandle(mapOf(BILLS_HOME_ID_KEY to "home-1")),
            clock = { fixedNow },
        )

    // ─── Four states ───────────────────────────────────────────

    @Test fun empty_response_renders_empty_state() =
        runTest {
            coEvery { repo.getHomeBills(any(), any()) } returns
                NetworkResult.Success(GetHomeBillsResponse(bills = emptyList()))
            val vm = makeVm()
            vm.load()
            val state = vm.state.value
            assertTrue(state is ListOfRowsUiState.Empty)
            assertEquals("No bills yet", (state as ListOfRowsUiState.Empty).headline)
            assertEquals("Add a bill", state.ctaTitle)
        }

    @Test fun failure_renders_error_state() =
        runTest {
            coEvery { repo.getHomeBills(any(), any()) } returns
                NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is ListOfRowsUiState.Error)
        }

    @Test fun loaded_response_maps_to_amountWithChip_trailing() =
        runTest {
            coEvery { repo.getHomeBills(any(), any()) } returns
                NetworkResult.Success(
                    GetHomeBillsResponse(bills = listOf(makeBill(id = "b1"))),
                )
            val vm = makeVm()
            vm.load()
            val state = vm.state.value as ListOfRowsUiState.Loaded
            val row = state.sections[0].rows[0]
            assertEquals("b1", row.id)
            assertEquals("ConEd", row.title)
            val trailing = row.trailing
            assertTrue(trailing is RowTrailing.AmountWithChip)
            trailing as RowTrailing.AmountWithChip
            assertEquals("$142.80", trailing.amount)
            assertTrue(trailing.chipText.startsWith("Due "))
        }

    // ─── Chip status derivation ───────────────────────────────

    @Test fun chipStatus_paid_wins_over_due_date() {
        val bill = makeBill(id = "b", status = "paid", dueDate = "2030-01-01T00:00:00Z")
        assertEquals(BillChipStatus.Paid, BillsListViewModel.chipStatus(bill, fixedNow))
    }

    @Test fun chipStatus_overdue_when_due_past_and_not_paid() {
        val bill = makeBill(id = "b", dueDate = "2026-05-01T00:00:00Z")
        assertEquals(BillChipStatus.Overdue, BillsListViewModel.chipStatus(bill, fixedNow))
    }

    @Test fun chipStatus_scheduled_respects_status_field() {
        val bill = makeBill(id = "b", status = "scheduled")
        assertEquals(BillChipStatus.Scheduled, BillsListViewModel.chipStatus(bill, fixedNow))
    }

    @Test fun chipStatus_due_for_future_date() {
        val bill = makeBill(id = "b")
        assertEquals(BillChipStatus.Due, BillsListViewModel.chipStatus(bill, fixedNow))
    }

    // ─── Per-status projection ────────────────────────────────

    @Test fun project_paid_subtitle_and_chip() {
        val projection =
            BillsListViewModel.project(
                makeBill(
                    id = "b",
                    status = "paid",
                    dueDate = "2026-05-08T00:00:00Z",
                    paidAt = "2026-05-08T00:00:00Z",
                ),
                fixedNow,
            )
        assertEquals("Paid", projection.chipText)
        assertEquals(StatusChipVariant.Success, projection.chipVariant)
        assertEquals("Paid May 8", projection.subtitle)
    }

    @Test fun project_overdue_subtitle_and_chip() {
        val projection =
            BillsListViewModel.project(
                makeBill(id = "b", dueDate = "2026-05-05T00:00:00Z"),
                fixedNow,
            )
        assertEquals("Overdue", projection.chipText)
        assertEquals(StatusChipVariant.ErrorVariant, projection.chipVariant)
        assertEquals("Due May 5", projection.subtitle)
    }

    @Test fun project_scheduled_subtitle_and_chip() {
        val projection =
            BillsListViewModel.project(
                makeBill(id = "b", status = "scheduled", dueDate = "2026-05-18T00:00:00Z"),
                fixedNow,
            )
        assertEquals("Scheduled", projection.chipText)
        assertEquals(StatusChipVariant.Personal, projection.chipVariant)
        assertEquals("Auto-pay May 18", projection.subtitle)
    }

    @Test fun project_due_subtitle_and_chip() {
        val projection =
            BillsListViewModel.project(
                makeBill(id = "b", dueDate = "2026-05-20T00:00:00Z"),
                fixedNow,
            )
        assertEquals("Due May 20", projection.chipText)
        assertEquals(StatusChipVariant.Warning, projection.chipVariant)
        assertEquals("May 20", projection.subtitle)
    }

    // ─── Tab filtering ────────────────────────────────────────

    @Test fun upcoming_tab_filters_to_due_overdue_scheduled() =
        runTest {
            coEvery { repo.getHomeBills(any(), any()) } returns
                NetworkResult.Success(GetHomeBillsResponse(bills = mixedBills()))
            val vm = makeVm()
            vm.load()
            vm.selectTab(BillsTab.Upcoming.id)
            val state = vm.state.value as ListOfRowsUiState.Loaded
            val ids = state.sections.flatMap { it.rows }.map { it.id }.toSet()
            assertEquals(setOf("b-due", "b-overdue", "b-scheduled"), ids)
        }

    @Test fun paid_tab_filters_to_only_paid() =
        runTest {
            coEvery { repo.getHomeBills(any(), any()) } returns
                NetworkResult.Success(GetHomeBillsResponse(bills = mixedBills()))
            val vm = makeVm()
            vm.load()
            vm.selectTab(BillsTab.Paid.id)
            val state = vm.state.value as ListOfRowsUiState.Loaded
            val ids = state.sections.flatMap { it.rows }.map { it.id }
            assertEquals(listOf("b-paid"), ids)
        }

    @Test fun all_tab_excludes_cancelled() =
        runTest {
            coEvery { repo.getHomeBills(any(), any()) } returns
                NetworkResult.Success(GetHomeBillsResponse(bills = mixedBills()))
            val vm = makeVm()
            vm.load()
            vm.selectTab(BillsTab.All.id)
            val state = vm.state.value as ListOfRowsUiState.Loaded
            val ids = state.sections.flatMap { it.rows }.map { it.id }.toSet()
            assertEquals(setOf("b-due", "b-overdue", "b-scheduled", "b-paid"), ids)
            assertTrue("b-cancelled" !in ids)
        }

    private fun mixedBills(): List<BillDto> =
        listOf(
            makeBill(id = "b-due", dueDate = "2026-05-20T00:00:00Z"),
            makeBill(id = "b-overdue", dueDate = "2026-05-01T00:00:00Z"),
            makeBill(id = "b-scheduled", status = "scheduled", dueDate = "2026-05-25T00:00:00Z"),
            makeBill(
                id = "b-paid",
                status = "paid",
                dueDate = "2026-05-08T00:00:00Z",
                paidAt = "2026-05-08T00:00:00Z",
            ),
            makeBill(id = "b-cancelled", status = "cancelled", dueDate = "2026-05-08T00:00:00Z"),
        )
}
