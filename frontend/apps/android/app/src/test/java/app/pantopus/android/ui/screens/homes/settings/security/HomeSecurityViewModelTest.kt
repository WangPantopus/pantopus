@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.settings.security

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5.1 / A14.2 — projection tests for the per-home Security toggles.
 * Locks the audit's required shape (3 groups × 3 toggles = 9) plus
 * the helper-line copy contract — the strings here MUST stay in
 * sync with the iOS `HomeSecurityViewModel` helpers so that
 * iOS+Android parity holds.
 */
class HomeSecurityViewModelTest {
    private fun makeVm() =
        HomeSecurityViewModel(
            savedStateHandle = SavedStateHandle(mapOf(HOME_SECURITY_HOME_ID_KEY to "home-1")),
        )

    @Test
    fun balanced_variant_has_five_toggles_on() {
        val vm = makeVm()
        vm.setVariant(HomeSecurityViewModel.Variant.Balanced)
        assertEquals(5, vm.toggles.values.count { it })
    }

    @Test
    fun strict_variant_has_nine_toggles_on() {
        val vm = makeVm()
        vm.setVariant(HomeSecurityViewModel.Variant.Strict)
        assertEquals(9, vm.toggles.size)
        assertTrue(vm.toggles.values.all { it })
    }

    @Test
    fun group_shape_matches_audit() {
        val vm = makeVm()
        vm.load()
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        assertEquals(listOf("accessControl", "privacy", "documents"), groups.map { it.id })
        for (group in groups) {
            assertEquals(3, group.rows.size)
            for (row in group.rows) {
                assertTrue("Row ${row.id} should be a toggle", row.control is RowControl.Toggle)
            }
        }
    }

    @Test
    fun balanced_helpers_use_mixed_state_copy() {
        val vm = makeVm()
        vm.setVariant(HomeSecurityViewModel.Variant.Balanced)
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val helpers = groups.associate { it.id to it.helper }
        assertEquals(
            "Guest approval is on, so guests need an owner-tap to enter.",
            helpers["accessControl"],
        )
        assertEquals(
            "Visible to verified neighbors only. Address used for deliveries.",
            helpers["privacy"],
        )
        assertEquals(
            "Docs unlock with Face ID. Previews still appear in chat.",
            helpers["documents"],
        )
    }

    @Test
    fun strict_helpers_shift_to_consequence_language() {
        val vm = makeVm()
        vm.setVariant(HomeSecurityViewModel.Variant.Strict)
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val helpers = groups.associate { it.id to it.helper }
        assertEquals(
            "All guest activity requires your explicit approval. Names and street precision are hidden from outsiders.",
            helpers["accessControl"],
        )
        assertEquals(
            "Hidden from the neighborhood map, previews suppressed. Outsiders only see your home name.",
            helpers["privacy"],
        )
        assertEquals(
            "All docs require Face ID. Previews stay blurred everywhere, including notifications.",
            helpers["documents"],
        )
    }

    @Test
    fun guest_approval_off_shows_tighten() {
        val vm = makeVm()
        vm.setVariant(HomeSecurityViewModel.Variant.Balanced)
        vm.onToggle(HomeSecurityToggles.GUEST_APPROVAL, false)
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val helper = groups.first { it.id == "accessControl" }.helper
        assertEquals(
            "Guest approval is off — anyone with a code is in. Tighten this if you're away.",
            helper,
        )
    }

    @Test
    fun toggle_flip_updates_state() {
        val vm = makeVm()
        vm.setVariant(HomeSecurityViewModel.Variant.Balanced)
        vm.onToggle(HomeSecurityToggles.ADDRESS_PRECISION, true)
        assertEquals(true, vm.toggles[HomeSecurityToggles.ADDRESS_PRECISION])
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val row = groups.flatMap { it.rows }.first { it.id == HomeSecurityToggles.ADDRESS_PRECISION }
        val control = row.control as RowControl.Toggle
        assertTrue(control.isOn)
    }
}
