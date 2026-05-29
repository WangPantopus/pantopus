@file:Suppress("MagicNumber", "PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.settings

import app.pantopus.android.ui.components.ChannelGlyph
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
 * P7.5 / A14.5 — the reshaped notification matrix. Covers the populated
 * + paused frames, the channel-header / triad projection, the locked
 * Emergency-push chip, optimistic chip + pause toggles, and the
 * helper-line parity contract (mirrored on iOS).
 */
class NotificationSettingsViewModelTest {
    @Test fun populated_produces_master_plus_five_categories() {
        val vm = NotificationSettingsViewModel()
        val groups = vm.loadedGroups()
        assertEquals(
            listOf("master", "tasks", "pulse", "marketplace", "homeMailbox", "accountSecurity"),
            groups.map { it.id },
        )
        assertNull(vm.banner.value)
        assertFalse(vm.dimmed.value)
    }

    @Test fun category_cards_carry_channel_header_and_triad_rows() {
        val categories = NotificationSettingsViewModel().loadedGroups().filter { it.id != "master" }
        assertEquals(5, categories.size)
        categories.forEach { category ->
            assertTrue("${category.id} should show the P/E/S header", category.showsChannelHeader)
            category.rows.forEach {
                assertTrue("${it.id} should be a channelTriad row", it.control is RowControl.ChannelTriad)
            }
        }
    }

    @Test fun master_card_is_toggle_and_chip_chevron() {
        val groups = NotificationSettingsViewModel().loadedGroups()
        assertEquals(false, groups.first { it.id == "master" }.showsChannelHeader)
        assertEquals(RowControl.Toggle(false), groups.row(NotificationCatalog.PAUSE_ALL)?.control)
        assertTrue(groups.row(NotificationCatalog.QUIET_HOURS)?.control is RowControl.ChipStatus)
    }

    @Test fun seed_patterns_match_design() {
        val groups = NotificationSettingsViewModel().loadedGroups()
        assertPattern(groups.row("tasks.bids"), p = true, e = false, s = false)
        assertPattern(groups.row("tasks.messages"), p = true, e = true, s = false)
        assertPattern(groups.row("tasks.receipts"), p = false, e = true, s = false)
        assertPattern(groups.row("pulse.lostFound"), p = false, e = false, s = false)
        assertPattern(groups.row("marketplace.offers"), p = true, e = true, s = false)
        assertPattern(groups.row("account.billing"), p = false, e = true, s = false)
    }

    @Test fun emergency_keeps_push_locked() {
        val control = NotificationSettingsViewModel().loadedGroups().row(NotificationCatalog.EMERGENCY)?.control
        assertTrue(control is RowControl.ChannelTriad)
        control as RowControl.ChannelTriad
        assertTrue(control.p)
        assertTrue(control.e)
        assertTrue(control.s)
        assertEquals(setOf(ChannelGlyph.P), control.locked)
    }

    @Test fun toggle_channel_flips_local_state() {
        val vm = NotificationSettingsViewModel()
        vm.load()
        vm.onToggleChannel("tasks.receipts", ChannelGlyph.P, isOn = true)
        assertPattern(vm.groups().row("tasks.receipts"), p = true, e = true, s = false)
    }

    @Test fun locked_channel_cannot_be_toggled_off() {
        val vm = NotificationSettingsViewModel()
        vm.load()
        vm.onToggleChannel(NotificationCatalog.EMERGENCY, ChannelGlyph.P, isOn = false)
        val control = vm.groups().row(NotificationCatalog.EMERGENCY)?.control as RowControl.ChannelTriad
        assertTrue("locked push can't be turned off", control.p)
        assertEquals(setOf(ChannelGlyph.P), control.locked)
    }

    @Test fun pause_all_swaps_master_for_banner_and_dims() {
        val vm = NotificationSettingsViewModel()
        vm.load()
        vm.onToggle(NotificationCatalog.PAUSE_ALL, isOn = true)
        val groups = vm.groups()
        assertFalse("Master card is replaced by the banner", groups.any { it.id == "master" })
        assertEquals("tasks", groups.first().id)
        assertTrue(vm.dimmed.value)
        val banner = vm.banner.value
        assertNotNull(banner)
        assertEquals("Paused for 2 hours", banner!!.title)
        assertEquals("Resumes 11:42 AM · Emergency alerts still come through", banner.subtitle)
        assertEquals("Resume", banner.actionLabel)
        assertEquals(PantopusIcon.BellOff, banner.icon)
    }

    @Test fun paused_variant_boots_paused() {
        val vm = NotificationSettingsViewModel()
        vm.setVariant(NotificationSettingsViewModel.Variant.Paused)
        assertTrue(vm.dimmed.value)
        assertNotNull(vm.banner.value)
        assertEquals(
            listOf("tasks", "pulse", "marketplace", "homeMailbox", "accountSecurity"),
            vm.groups().map { it.id },
        )
    }

    @Test fun resume_restores_master() {
        val vm = NotificationSettingsViewModel()
        vm.setVariant(NotificationSettingsViewModel.Variant.Paused)
        vm.onTapBanner()
        assertNull(vm.banner.value)
        assertFalse(vm.dimmed.value)
        assertEquals("master", vm.groups().first().id)
    }

    @Test fun footer_legend() {
        assertEquals("P · Push   E · Email   S · SMS", NotificationSettingsViewModel().footerCaption)
    }

    @Test fun helper_copy_matches_design() {
        val groups = NotificationSettingsViewModel().loadedGroups()

        fun helper(id: String) = groups.first { it.id == id }.helper
        assertEquals(
            "Pause all silences every channel except emergency alerts. Quiet hours just delays them.",
            helper("master"),
        )
        assertEquals(
            "Push only for things that need a fast reply. Receipts go to email so they're searchable.",
            helper("tasks"),
        )
        assertEquals("Pulse is quiet by default. Mentions break through, browsing doesn't.", helper("pulse"))
        assertNull("Marketplace card has no helper line in the design", helper("marketplace"))
        assertEquals("Emergency alerts can't be muted on push.", helper("homeMailbox"))
        assertEquals("Security alerts always come through. You can choose how.", helper("accountSecurity"))
    }

    // MARK: - Helpers

    private fun assertPattern(
        row: GroupedListRow?,
        p: Boolean,
        e: Boolean,
        s: Boolean,
    ) {
        val control = row?.control
        assertTrue("expected channelTriad for ${row?.id}", control is RowControl.ChannelTriad)
        control as RowControl.ChannelTriad
        assertEquals("push for ${row?.id}", p, control.p)
        assertEquals("email for ${row?.id}", e, control.e)
        assertEquals("sms for ${row?.id}", s, control.s)
    }

    private fun NotificationSettingsViewModel.loadedGroups(): List<GroupedListGroup> {
        load()
        return groups()
    }

    private fun NotificationSettingsViewModel.groups(): List<GroupedListGroup> = (state.value as GroupedListUiState.Loaded).groups

    private fun List<GroupedListGroup>.row(id: String): GroupedListRow? = flatMap { it.rows }.firstOrNull { it.id == id }
}
