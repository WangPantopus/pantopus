@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.businesses.create_business

import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
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

@OptIn(ExperimentalCoroutinesApi::class)
class CreateBusinessWizardViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): CreateBusinessWizardViewModel = CreateBusinessWizardViewModel()

    @Test
    fun initial_state_is_pick_category_with_home_default() {
        val vm = makeVm()
        assertEquals(CreateBusinessStep.PickCategory, vm.state.value.currentStep)
        assertEquals(BusinessCategory.Home, vm.state.value.selectedCategory)
        assertEquals("Continue", vm.chrome.primaryCtaLabel)
        assertTrue(vm.chrome.primaryCtaEnabled)
        assertFalse("Default home selection should not be dirty.", vm.chrome.dirty)
        assertEquals(
            WizardProgressLabel.StepOf(current = 1, total = 4),
            vm.chrome.progressLabel,
        )
        assertEquals(WizardLeadingControl.Close, vm.chrome.leading)
    }

    @Test
    fun selecting_non_default_category_marks_dirty() {
        val vm = makeVm()
        vm.selectCategory(BusinessCategory.Tech)
        assertEquals(BusinessCategory.Tech, vm.state.value.selectedCategory)
        assertTrue("Picking a non-default tile must mark the wizard dirty.", vm.chrome.dirty)
    }

    @Test
    fun typing_search_query_marks_dirty() {
        val vm = makeVm()
        vm.setSearchText("tutor")
        assertTrue(vm.state.value.isSearchActive)
        assertTrue(vm.chrome.dirty)
    }

    @Test
    fun search_hits_are_filtered_and_capped() {
        val vm = makeVm()
        vm.setSearchText("tutor")
        val hits = vm.state.value.searchHits
        assertEquals(3, hits.size)
        assertEquals("tutoring-core", hits.first().id)
        assertEquals(BusinessCategory.Personal, hits.first().category)
        assertTrue(hits.all { it.label.lowercase().contains("tutor") })
    }

    @Test
    fun empty_search_yields_no_hits() {
        val vm = makeVm()
        vm.setSearchText("   ")
        assertTrue(vm.state.value.searchHits.isEmpty())
    }

    @Test
    fun selecting_search_hit_selects_category_and_clears_query() {
        val vm = makeVm()
        vm.setSearchText("tutor")
        val hit = vm.state.value.searchHits.first()
        vm.selectSearchHit(hit)
        assertEquals(hit.category, vm.state.value.selectedCategory)
        assertEquals("", vm.state.value.searchText)
        assertFalse(vm.state.value.isSearchActive)
    }

    @Test
    fun primary_from_pick_category_advances_to_legal_info() {
        val vm = makeVm()
        vm.onPrimary()
        assertEquals(CreateBusinessStep.LegalInfo, vm.state.value.currentStep)
        assertEquals(
            WizardProgressLabel.StepOf(current = 2, total = 4),
            vm.chrome.progressLabel,
        )
        assertEquals("Next", vm.chrome.primaryCtaLabel)
    }

    @Test
    fun primary_chains_through_legal_profile_confirm() {
        val vm = makeVm()
        vm.onPrimary() // → legal
        vm.onPrimary() // → profile
        assertEquals(CreateBusinessStep.Profile, vm.state.value.currentStep)
        assertEquals(
            WizardProgressLabel.StepOf(current = 3, total = 4),
            vm.chrome.progressLabel,
        )
        vm.onPrimary() // → confirm
        assertEquals(CreateBusinessStep.Confirm, vm.state.value.currentStep)
        assertEquals("Confirm", vm.chrome.primaryCtaLabel)
    }

    @Test
    fun back_from_legal_info_returns_to_pick_category() {
        val vm = makeVm()
        vm.onPrimary()
        vm.onLeading()
        assertEquals(CreateBusinessStep.PickCategory, vm.state.value.currentStep)
    }

    @Test
    fun close_on_pick_category_dispatches_dismiss() {
        val vm = makeVm()
        vm.onLeading()
        assertEquals(CreateBusinessOutboundEvent.Dismiss, vm.pendingEvent.value)
    }

    @Test
    fun custom_category_submit_stays_on_pick_category_with_backend_error() =
        runTest {
            val vm = makeVm()
            vm.setSearchText("alpaca grooming")
            vm.submitCustomCategory()
            assertEquals(BusinessCategory.Home, vm.state.value.selectedCategory)
            assertEquals(CreateBusinessStep.PickCategory, vm.state.value.currentStep)
            assertEquals("alpaca grooming", vm.state.value.searchText)
            assertEquals(
                "Custom categories are not accepted by the backend yet.",
                vm.state.value.submitError,
            )
            assertFalse(vm.state.value.isSubmittingCustom)
        }

    @Test
    fun custom_category_submit_noop_on_empty_query() {
        val vm = makeVm()
        vm.setSearchText("   ")
        vm.submitCustomCategory()
        assertEquals(CreateBusinessStep.PickCategory, vm.state.value.currentStep)
    }

    @Test
    fun what_you_get_only_visible_for_home_services() {
        val vm = makeVm()
        assertFalse("Default .Home should show the strip.", vm.state.value.whatYouGetItems.isEmpty())
        vm.selectCategory(BusinessCategory.Tech)
        assertTrue(
            "Other categories don't have a payload yet.",
            vm.state.value.whatYouGetItems.isEmpty(),
        )
    }

    @Test
    fun pending_event_acknowledge_clears_to_null() {
        val vm = makeVm()
        vm.onLeading()
        assertNotNull(vm.pendingEvent.value)
        vm.acknowledgeEvent()
        assertNull(vm.pendingEvent.value)
    }
}
