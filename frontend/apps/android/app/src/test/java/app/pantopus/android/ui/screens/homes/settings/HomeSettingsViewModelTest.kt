@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.settings

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P5.1 / A14.1 — projection tests for the per-home Settings index.
 * Locks the audit's required slot inventory (5 groups, row ids per
 * group) and the destructive row swap between the established and
 * newly-claimed frames.
 */
class HomeSettingsViewModelTest {
    private fun makeVm(homeId: String) =
        HomeSettingsViewModel(
            savedStateHandle = SavedStateHandle(mapOf(HOME_SETTINGS_HOME_ID_KEY to homeId)),
        )

    @Test
    fun populated_frame_produces_five_groups() {
        val vm = makeVm("home-1")
        vm.load()
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        assertEquals(
            listOf("homeIdentity", "access", "members", "notifications", "windDown"),
            groups.map { it.id },
        )
    }

    @Test
    fun populated_frame_row_inventory_matches_audit() {
        val vm = makeVm("home-1")
        vm.load()
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val byGroup = groups.associate { it.id to it.rows.map { row -> row.id } }
        assertEquals(listOf("address", "propertyDetails", "photos", "documents"), byGroup["homeIdentity"])
        assertEquals(listOf("accessCodes", "trustedNeighbors", "privacy"), byGroup["access"])
        assertEquals(listOf("people", "inviteLink"), byGroup["members"])
        assertEquals(listOf("homeNotifications"), byGroup["notifications"])
        assertEquals(listOf("leaveHome"), byGroup["windDown"])
    }

    @Test
    fun populated_address_carries_success_chip() {
        val vm = makeVm("home-1")
        vm.load()
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val address = groups.first { it.id == "homeIdentity" }.rows.first { it.id == "address" }
        val control = address.control as RowControl.ChipStatus
        assertEquals("Verified", control.label)
        assertEquals(RowControl.ChipTone.Success, control.tone)
        assertTrue(control.includesChevron)
    }

    @Test
    fun pending_frame_swaps_destructive_to_cancel_claim() {
        val vm = makeVm("pending-claim-2")
        vm.load()
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val destructive = groups.last().rows.first()
        assertEquals("cancelClaim", destructive.id)
        assertEquals("Cancel claim", destructive.label)
        assertTrue(destructive.destructive)
    }

    @Test
    fun pending_address_carries_warning_verifying_chip() {
        val vm = makeVm("pending-2")
        vm.load()
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val address = groups.first { it.id == "homeIdentity" }.rows.first { it.id == "address" }
        val control = address.control as RowControl.ChipStatus
        assertEquals("Verifying", control.label)
        assertEquals(RowControl.ChipTone.Warning, control.tone)
    }

    @Test
    fun pending_subs_read_not_set_or_available_after_verification() {
        val vm = makeVm("pending-2")
        vm.load()
        val groups = (vm.state.value as GroupedListUiState.Loaded).groups
        val propertyDetails = groups.first { it.id == "homeIdentity" }.rows.first { it.id == "propertyDetails" }
        assertEquals("Not set", propertyDetails.subtext)
        val trustedNeighbors = groups.first { it.id == "access" }.rows.first { it.id == "trustedNeighbors" }
        assertEquals("Available after verification", trustedNeighbors.subtext)
    }

    @Test
    fun tap_privacy_row_routes_to_security() {
        val vm = makeVm("home-1")
        vm.load()
        vm.onRow("privacy")
        assertEquals(HomeSettingsRoute.Security, vm.navigation.value)
    }

    @Test
    fun frame_inference_follows_home_id_prefix() {
        assertEquals(HomeSettingsSampleData.Frame.Populated, HomeSettingsSampleData.frameForHomeId("home-abc"))
        assertEquals(HomeSettingsSampleData.Frame.Pending, HomeSettingsSampleData.frameForHomeId("pending-xyz"))
    }
}
