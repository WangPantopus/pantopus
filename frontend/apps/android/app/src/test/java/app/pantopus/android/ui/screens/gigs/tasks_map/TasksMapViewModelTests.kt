@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.gigs.tasks_map

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.gigs.GigsSort
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapPinState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A11.1 Tasks map view-model — load → populated / empty, the live category
 * filter, pin↔card selection, and client-side sort. Mirrors iOS
 * `TasksMapViewModelTests`.
 */
class TasksMapViewModelTests {
    private fun vm(category: String? = null): TasksMapViewModel =
        TasksMapViewModel(
            SavedStateHandle(if (category != null) mapOf("category" to category) else emptyMap()),
        )

    private fun items(vm: TasksMapViewModel): List<TaskMapItem>? = (vm.state.value as? TasksMapUiState.Populated)?.items

    @Test
    fun load_produces_populated_with_default_selection() {
        val vm = vm()
        vm.load()
        assertEquals(TasksMapSampleData.items.size, items(vm)?.size)
        // Closest-first by default → the 0.2 mi handyman task leads + pulses.
        assertEquals("handyman-1", vm.selectedId.value)
        assertEquals(items(vm)?.first()?.id, vm.selectedId.value)
    }

    @Test
    fun category_filter_narrows_visible_items() {
        val vm = vm()
        vm.load()
        vm.selectCategory(GigsCategory.Cleaning)
        val visible = items(vm)
        assertEquals(2, visible?.size)
        assertTrue(visible?.all { it.category == GigsCategory.Cleaning } == true)
        assertEquals(visible?.first()?.id, vm.selectedId.value)
    }

    @Test
    fun category_with_no_matches_produces_empty() {
        val vm = vm()
        vm.load()
        vm.selectCategory(GigsCategory.Tech) // no tech task in the seed
        assertTrue(vm.state.value is TasksMapUiState.Empty)
        assertNull(vm.selectedId.value)
    }

    @Test
    fun widen_from_empty_restores_populated() {
        val vm = vm()
        vm.load()
        vm.selectCategory(GigsCategory.Tech)
        assertTrue(vm.state.value is TasksMapUiState.Empty)
        vm.selectCategory(GigsCategory.All) // "Widen search"
        assertEquals(TasksMapSampleData.items.size, items(vm)?.size)
    }

    @Test
    fun select_updates_selected_id() {
        val vm = vm()
        vm.load()
        vm.select("cleaning-1")
        assertEquals("cleaning-1", vm.selectedId.value)
    }

    @Test
    fun sort_fewest_bids_orders_ascending() {
        val vm = vm()
        vm.load()
        vm.selectSort(GigsSort.FewestBids)
        val bids = items(vm)?.map { it.bidCount } ?: emptyList()
        assertEquals(bids.sorted(), bids)
    }

    @Test
    fun sort_highest_pay_leads_with_priciest() {
        val vm = vm()
        vm.load()
        vm.selectSort(GigsSort.HighestPay)
        assertEquals("cleaning-1", items(vm)?.first()?.id) // $180
    }

    @Test
    fun initial_category_from_saved_state_applied_on_load() {
        val vm = vm("petcare")
        vm.load()
        val visible = items(vm)
        assertEquals(2, visible?.size)
        assertTrue(visible?.all { it.category == GigsCategory.PetCare } == true)
        assertEquals(GigsCategory.PetCare, vm.activeCategory.value)
    }

    @Test
    fun pins_carry_pending_state_for_moving_and_tutoring() {
        val vm = vm()
        vm.load()
        val pins = items(vm)?.map { it.toPin() } ?: emptyList()
        assertEquals(TasksMapSampleData.items.size, pins.size)
        assertEquals(2, pins.count { it.state == MapPinState.Pending })
    }
}
