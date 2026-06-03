@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.gigs.tasks_map

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigsInBoundsResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.gigs.GigsSort
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapPinState
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A11.1 Tasks map view-model — live `GET /api/gigs/in-bounds` fetch +
 * projection, plus the client-side category filter, pin↔card selection,
 * and sort. Mirrors iOS `TasksMapViewModelTests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksMapViewModelTests {
    private val repo: GigsRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    // Anchor is TasksMapSampleData.anchor (40.7484, -73.9857); coords are
    // placed so handyman-1 is the closest task.
    private fun gig(
        id: String,
        category: String,
        price: Double,
        lat: Double,
        lon: Double,
        bidCount: Int,
        status: String = "open",
        payType: String? = null,
    ): GigDto =
        GigDto(
            id = id,
            title = "Task $id",
            price = price,
            category = category,
            status = status,
            payType = payType,
            bidCount = bidCount,
            latitude = lat,
            longitude = lon,
        )

    private val sampleGigs =
        listOf(
            gig("handyman-1", "handyman", 60.0, 40.749, -73.988, bidCount = 4),
            gig("cleaning-1", "cleaning", 180.0, 40.745, -73.982, bidCount = 1),
            gig("cleaning-2", "cleaning", 90.0, 40.752, -73.990, bidCount = 2),
            gig("petcare-1", "petcare", 22.0, 40.747, -73.984, bidCount = 0, payType = "per_walk"),
            gig("petcare-2", "petcare", 25.0, 40.750, -73.987, bidCount = 3, payType = "per_walk"),
            gig("moving-1", "moving", 80.0, 40.744, -73.983, bidCount = 5, status = "assigned"),
            gig("tutoring-1", "tutoring", 40.0, 40.753, -73.991, bidCount = 6, status = "in_progress"),
        )

    private fun vm(
        category: String? = null,
        gigs: List<GigDto> = sampleGigs,
        result: NetworkResult<GigsInBoundsResponse> = NetworkResult.Success(GigsInBoundsResponse(gigs)),
    ): TasksMapViewModel {
        coEvery { repo.inBounds(any(), any(), any(), any(), any()) } returns result
        return TasksMapViewModel(
            repo,
            SavedStateHandle(if (category != null) mapOf("category" to category) else emptyMap()),
        )
    }

    private fun items(vm: TasksMapViewModel): List<TaskMapItem>? = (vm.state.value as? TasksMapUiState.Populated)?.items

    @Test
    fun load_fetches_in_bounds_and_projects_with_closest_selection() =
        runTest {
            val vm = vm()
            vm.load()
            assertEquals(sampleGigs.size, items(vm)?.size)
            // Closest-first by default → handyman-1 leads + pulses.
            assertEquals("handyman-1", vm.selectedId.value)
            assertEquals(items(vm)?.first()?.id, vm.selectedId.value)
            assertEquals("$60", items(vm)?.first()?.price)
            assertEquals("$22/walk", items(vm)?.firstOrNull { it.id == "petcare-1" }?.price)
        }

    @Test
    fun load_drops_gigs_without_coordinates() =
        runTest {
            val noCoords = GigDto(id = "x", title = "No coords", category = "cleaning", status = "open")
            val vm = vm(gigs = listOf(sampleGigs.first(), noCoords))
            vm.load()
            assertEquals(listOf("handyman-1"), items(vm)?.map { it.id })
        }

    @Test
    fun load_empty_result_produces_empty() =
        runTest {
            val vm = vm(gigs = emptyList())
            vm.load()
            assertTrue(vm.state.value is TasksMapUiState.Empty)
            assertNull(vm.selectedId.value)
        }

    @Test
    fun load_server_error_produces_error() =
        runTest {
            val vm = vm(result = NetworkResult.Failure(NetworkError.Server(500, null)))
            vm.load()
            assertTrue(vm.state.value is TasksMapUiState.Error)
        }

    @Test
    fun category_filter_narrows_visible_items() =
        runTest {
            val vm = vm()
            vm.load()
            vm.selectCategory(GigsCategory.Cleaning)
            val visible = items(vm)
            assertEquals(2, visible?.size)
            assertTrue(visible?.all { it.category == GigsCategory.Cleaning } == true)
            assertEquals(visible?.first()?.id, vm.selectedId.value)
        }

    @Test
    fun category_with_no_matches_produces_empty() =
        runTest {
            val vm = vm()
            vm.load()
            vm.selectCategory(GigsCategory.Tech) // no tech task fetched
            assertTrue(vm.state.value is TasksMapUiState.Empty)
            assertNull(vm.selectedId.value)
        }

    @Test
    fun widen_from_empty_restores_populated() =
        runTest {
            val vm = vm()
            vm.load()
            vm.selectCategory(GigsCategory.Tech)
            assertTrue(vm.state.value is TasksMapUiState.Empty)
            vm.selectCategory(GigsCategory.All) // "Widen search"
            assertEquals(sampleGigs.size, items(vm)?.size)
        }

    @Test
    fun select_updates_selected_id() =
        runTest {
            val vm = vm()
            vm.load()
            vm.select("cleaning-1")
            assertEquals("cleaning-1", vm.selectedId.value)
        }

    @Test
    fun sort_fewest_bids_orders_ascending() =
        runTest {
            val vm = vm()
            vm.load()
            vm.selectSort(GigsSort.FewestBids)
            val bids = items(vm)?.map { it.bidCount } ?: emptyList()
            assertEquals(bids.sorted(), bids)
        }

    @Test
    fun sort_highest_pay_leads_with_priciest() =
        runTest {
            val vm = vm()
            vm.load()
            vm.selectSort(GigsSort.HighestPay)
            assertEquals("cleaning-1", items(vm)?.first()?.id) // $180
        }

    @Test
    fun initial_category_from_saved_state_applied_on_load() =
        runTest {
            val vm = vm(category = "petcare")
            vm.load()
            val visible = items(vm)
            assertEquals(2, visible?.size)
            assertTrue(visible?.all { it.category == GigsCategory.PetCare } == true)
            assertEquals(GigsCategory.PetCare, vm.activeCategory.value)
        }

    @Test
    fun pins_carry_pending_state_for_non_open_gigs() =
        runTest {
            val vm = vm()
            vm.load()
            val pins = items(vm)?.map { it.toPin() } ?: emptyList()
            assertEquals(sampleGigs.size, pins.size)
            // moving-1 (assigned) + tutoring-1 (in_progress) → pending.
            assertEquals(2, pins.count { it.state == MapPinState.Pending })
        }
}
