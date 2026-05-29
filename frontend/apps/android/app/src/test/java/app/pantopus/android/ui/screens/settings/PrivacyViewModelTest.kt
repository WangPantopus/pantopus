@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.settings

import app.pantopus.android.ui.components.FuzzStop
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListBanner
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListGroup
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListRow
import app.pantopus.android.ui.screens.shared.grouped_list.GroupedListUiState
import app.pantopus.android.ui.screens.shared.grouped_list.RowControl
import app.pantopus.android.ui.theme.PantopusIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P7.6 / A14.7 — the reshaped Privacy matrix. Covers the defaults +
 * stealth frames, the RadioCard / fuzz / activity / data projection,
 * the stealth banner, optimistic radio / toggle / fuzz mutations, and
 * the helper-line parity contract (mirrored on iOS).
 */
class PrivacyViewModelTest {
    @Test fun populated_produces_six_groups_in_design_order() {
        val groups = PrivacySettingsViewModel().loadedGroups()
        assertEquals(
            listOf("visibility", "address", "fuzz", "activity", "data", "delete"),
            groups.map { it.id },
        )
        val vm = PrivacySettingsViewModel().apply { load() }
        assertNull(vm.banner.value)
    }

    @Test fun visibility_and_address_are_four_option_radio_cards() {
        val groups = PrivacySettingsViewModel().loadedGroups()
        val visibility = groups.group("visibility")
        val address = groups.group("address")
        assertEquals(4, visibility?.rows?.size)
        assertEquals(4, address?.rows?.size)
        assertEquals("visibility.verified", selectedRadioId(visibility))
        assertEquals("address.street", selectedRadioId(address))
        visibility?.rows?.forEach { assertTrue("${it.id} radio", it.control is RowControl.Radio) }
    }

    @Test fun fuzz_group_defaults_to_block_default() {
        val fuzz = PrivacySettingsViewModel().loadedGroups().group("fuzz")
        assertEquals(FuzzStop.BlockDefault, fuzz?.fuzz?.stop)
        assertEquals("How exact your task and listing pins appear on the map.", fuzz?.fuzz?.leadIn)
        assertTrue(fuzz?.rows?.isEmpty() ?: false)
    }

    @Test fun activity_has_four_toggles_all_on() {
        val activity = PrivacySettingsViewModel().loadedGroups().group("activity")
        assertEquals(listOf("online", "recent", "nearby", "ratings"), activity?.rows?.map { it.id })
        activity?.rows?.forEach {
            val control = it.control
            assertTrue("${it.id} toggle", control is RowControl.Toggle && control.isOn)
        }
    }

    @Test fun data_rows_carry_leading_icons_and_delete_is_destructive() {
        val groups = PrivacySettingsViewModel().loadedGroups()
        val data = groups.group("data")
        assertEquals(PantopusIcon.Download, data?.rows?.first { it.id == "downloadData" }?.leadingIcon)
        assertEquals(PantopusIcon.FileText, data?.rows?.first { it.id == "whatWeCollect" }?.leadingIcon)
        val delete = groups.group("delete")?.rows?.first()
        assertEquals("deleteAccount", delete?.id)
        assertTrue(delete?.destructive ?: false)
    }

    @Test fun select_radio_updates_selection() {
        val vm = PrivacySettingsViewModel()
        vm.load()
        vm.onRadio("visibility.connections")
        assertEquals("visibility.connections", selectedRadioId(vm.groups().group("visibility")))
    }

    @Test fun toggle_activity_flips_local_state() {
        val vm = PrivacySettingsViewModel()
        vm.load()
        vm.onToggle("online", isOn = false)
        val control = vm.groups().group("activity")?.rows?.first { it.id == "online" }?.control
        assertTrue(control is RowControl.Toggle && !control.isOn)
    }

    @Test fun set_fuzz_updates_stop() {
        val vm = PrivacySettingsViewModel()
        vm.load()
        vm.onSetFuzz(PrivacyCatalog.FUZZ, FuzzStop.Exact)
        assertEquals(FuzzStop.Exact, vm.groups().group("fuzz")?.fuzz?.stop)
    }

    @Test fun stealth_shows_banner_and_strictest_controls() {
        val vm = PrivacySettingsViewModel()
        vm.setVariant(PrivacySettingsViewModel.Variant.Stealth)
        val groups = vm.groups()
        val banner = vm.banner.value
        assertNotNull(banner)
        assertEquals("Stealth mode is on", banner?.title)
        assertEquals("Your profile is hidden from search. Existing connections still see you.", banner?.subtitle)
        assertEquals(PantopusIcon.EyeOff, banner?.icon)
        assertEquals(GroupedListBanner.Style.Stealth, banner?.style)
        assertEquals("visibility.hidden", selectedRadioId(groups.group("visibility")))
        assertEquals("address.hidden", selectedRadioId(groups.group("address")))
        assertEquals(FuzzStop.Neighborhood, groups.group("fuzz")?.fuzz?.stop)
        groups.group("activity")?.rows?.forEach {
            val control = it.control
            if (control is RowControl.Toggle) assertFalse("${it.id} off", control.isOn)
        }
        assertEquals("Stealth · auto-applied May 26, 2026", vm.footerCaption)
    }

    @Test fun footer_default() {
        assertEquals("Last updated · Mar 12, 2024", PrivacySettingsViewModel().footerCaption)
    }

    @Test fun helper_copy_matches_design() {
        val populated = PrivacySettingsViewModel().loadedGroups()
        assertEquals(
            "Verified neighbors can find you and start a conversation.",
            populated.group("visibility")?.helper,
        )
        assertEquals(
            "Street name shows on your profile; full address only to people you hire or sell to.",
            populated.group("address")?.helper,
        )
        assertEquals(
            "Pins drop within a block of you. Exact address only shared after a task is accepted.",
            populated.group("fuzz")?.helper,
        )
        assertNull("Activity card has no helper", populated.group("activity")?.helper)

        val stealthVm = PrivacySettingsViewModel().apply { setVariant(PrivacySettingsViewModel.Variant.Stealth) }
        val stealth = stealthVm.groups()
        assertEquals(
            "Hidden — your profile won't show in search or recommendations.",
            stealth.group("visibility")?.helper,
        )
        assertEquals(
            "Address hidden everywhere. Deliveries still route correctly.",
            stealth.group("address")?.helper,
        )
        assertEquals(
            "Pins fuzz to your neighborhood — buyers see only \"Park Slope\", never your block.",
            stealth.group("fuzz")?.helper,
        )
    }

    // MARK: - Helpers

    private fun PrivacySettingsViewModel.loadedGroups(): List<GroupedListGroup> {
        load()
        return groups()
    }

    private fun PrivacySettingsViewModel.groups(): List<GroupedListGroup> =
        (state.value as GroupedListUiState.Loaded).groups

    private fun List<GroupedListGroup>.group(id: String): GroupedListGroup? = firstOrNull { it.id == id }

    private fun selectedRadioId(group: GroupedListGroup?): String? =
        group?.rows?.firstOrNull { row ->
            val control = row.control
            control is RowControl.Radio && control.isSelected
        }?.id
}
