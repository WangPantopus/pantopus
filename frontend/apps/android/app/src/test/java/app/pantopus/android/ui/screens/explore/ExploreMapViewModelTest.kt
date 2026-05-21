@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.explore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Covers A11.2 Explore local projection: filters, clustering, empty recovery, and selection. */
class ExploreMapViewModelTest {
    private fun loaded(vm: ExploreMapViewModel): ExploreMapUiState.Loaded = vm.state.value as ExploreMapUiState.Loaded

    @Test
    fun populated_load_projects47Entities_withThreeActiveFilters() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Populated)
        val loaded = loaded(vm)
        assertEquals(47, loaded.entities.size)
        assertFalse(loaded.isEmpty)
        assertEquals(3, vm.filters.value.activeCount)
    }

    @Test
    fun populated_markers_containAtLeastOneCluster() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Populated)
        assertTrue(loaded(vm).markers.any { it is ExploreMarker.Cluster })
    }

    @Test
    fun empty_load_isEmpty() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Empty)
        assertTrue(loaded(vm).isEmpty)
        assertEquals(3, vm.filters.value.activeCount)
    }

    @Test
    fun clearFilters_fromEmpty_revealsAllEntities() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Empty)
        vm.clearFilters()
        assertEquals(6, loaded(vm).entities.size)
        assertEquals(0, vm.filters.value.activeCount)
        assertNull(vm.activeKind.value)
    }

    @Test
    fun widenArea_fromEmpty_surfacesFartherNeighbors() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Empty)
        vm.widenArea()
        val entities = loaded(vm).entities
        assertEquals(3, entities.size)
        assertTrue(entities.all { it.verified && it.openNow })
    }

    @Test
    fun selectKind_filtersToSingleKind() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Populated)
        vm.selectKind(ExploreKind.Spot)
        val entities = loaded(vm).entities
        assertTrue(entities.isNotEmpty())
        assertTrue(entities.all { it.kind == ExploreKind.Spot })
    }

    @Test
    fun applyFilters_narrowsAndUpdatesActiveCount() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Populated)
        vm.applyFilters(
            ExploreFilterCriteria(
                kinds = setOf(ExploreKind.Task),
                distanceUpper = 1f,
                verifiedOnly = true,
                openNow = true,
            ),
        )
        assertTrue(loaded(vm).entities.all { it.kind == ExploreKind.Task })
        assertEquals(4, vm.filters.value.activeCount)
    }

    @Test
    fun selectEntity_setsSelectedId() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Populated)
        val first = loaded(vm).entities.first()
        vm.selectEntity(first.id)
        assertEquals(first.id, loaded(vm).selectedId)
    }

    @Test
    fun errorScenario_rendersError() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Error)
        assertTrue(vm.state.value is ExploreMapUiState.Error)
    }

    @Test
    fun loadingScenario_staysLoading() {
        val vm = ExploreMapViewModel()
        vm.load(ExploreScenario.Loading)
        assertTrue(vm.state.value is ExploreMapUiState.Loading)
    }

    @Test
    fun filterCriteria_sectionsRoundTrip() {
        val original =
            ExploreFilterCriteria(
                kinds = setOf(ExploreKind.Task, ExploreKind.Spot),
                distanceUpper = 1f,
                verifiedOnly = true,
                openNow = false,
            )
        assertEquals(original, ExploreFilterCriteria.fromSections(original.toSections()))
    }

    @Test
    fun filterCriteria_activeCount_emptyAndAllAreInactive() {
        assertEquals(0, ExploreFilterCriteria().activeCount)
        val allKinds = ExploreFilterCriteria(kinds = ExploreKind.entries.toSet())
        assertFalse(allKinds.isKindActive)
        assertEquals(0, allKinds.activeCount)
    }

    @Test
    fun filterCriteria_matches_honoursEveryDimension() {
        val entity =
            ExploreEntity(
                id = "x",
                kind = ExploreKind.Task,
                state = ExploreEntityState.Confirmed,
                latitude = 0.0,
                longitude = 0.0,
                title = "Task",
                metaLead = "$1",
                distanceLabel = "0.2 mi",
                distanceMiles = 0.2,
                badge = null,
                verified = false,
                openNow = true,
            )
        assertTrue(ExploreFilterCriteria().matches(entity))
        assertFalse(ExploreFilterCriteria(kinds = setOf(ExploreKind.Spot)).matches(entity))
        assertFalse(ExploreFilterCriteria(distanceUpper = 0.1f).matches(entity))
        assertFalse(ExploreFilterCriteria(verifiedOnly = true).matches(entity))
    }
}
