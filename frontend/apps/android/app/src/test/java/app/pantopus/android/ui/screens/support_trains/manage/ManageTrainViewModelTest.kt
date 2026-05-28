@file:Suppress("LongMethod", "PackageNaming")

package app.pantopus.android.ui.screens.support_trains.manage

import androidx.lifecycle.SavedStateHandle
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

/**
 * P4.3 / A13.13 — Manage train ViewModel. Covers the same state machine
 * as the iOS `ManageTrainViewModelTests` so the two platforms stay in
 * lock-step:
 *  - active fixture loads with seeded draft + audience + push state
 *  - character cap clamps oversize input
 *  - empty / whitespace draft disables Send
 *  - audience selector accepts known ids, rejects unknown
 *  - sendUpdate clears the draft and flashes the helper-count toast
 *  - show / hide / confirm close-train sheet
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ManageTrainViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun savedState(trainId: String = ManageTrainSampleData.TRAIN_ID): SavedStateHandle =
        SavedStateHandle(mapOf(ManageTrainViewModel.TRAIN_ID_KEY to trainId))

    private fun makeVm(): ManageTrainViewModel = ManageTrainViewModel(savedState())

    @Test
    fun load_projects_active_fixture() =
        runTest {
            val vm = makeVm()
            val state = vm.state.value
            val loaded = state.state as ManageTrainState.Loaded
            assertEquals("Meals for the Murphy family", loaded.content.title)
            assertTrue(loaded.content.isActive)
            assertEquals("18/21", loaded.content.slotFillValue)
            assertEquals("12", loaded.content.helpersValue)
            assertEquals("9d", loaded.content.daysLeftValue)
            assertEquals("1", loaded.content.dropoutValue)
            assertEquals(
                listOf("edit-dates", "invite", "analytics"),
                loaded.content.organizeRows.map { it.id },
            )
            assertEquals("close", loaded.content.closeRow.id)
            assertTrue(loaded.content.closeRow.isDestructive)
        }

    @Test
    fun initial_draft_mirrors_content() =
        runTest {
            val vm = makeVm()
            val state = vm.state.value
            assertFalse("Active fixture seeds a typed draft", state.draftMessage.isEmpty())
            assertEquals("all", state.selectedAudienceId)
            assertTrue(state.pushToPhones)
            assertTrue(state.canSendUpdate)
        }

    @Test
    fun character_cap_clamps_oversize_input() =
        runTest {
            val vm = makeVm()
            val oversized = "x".repeat(ManageTrainUiState.MAX_MESSAGE_CHARS + 50)
            vm.updateDraftMessage(oversized)
            assertEquals(ManageTrainUiState.MAX_MESSAGE_CHARS, vm.state.value.draftMessage.length)
            assertEquals(ManageTrainUiState.MAX_MESSAGE_CHARS, vm.state.value.characterCount)
        }

    @Test
    fun empty_draft_disables_send() =
        runTest {
            val vm = makeVm()
            vm.updateDraftMessage("   \n  ")
            assertFalse(
                "Whitespace-only draft is not sendable",
                vm.state.value.canSendUpdate,
            )
        }

    @Test
    fun select_audience_updates_selection() =
        runTest {
            val vm = makeVm()
            vm.selectAudience("upcoming")
            assertEquals("upcoming", vm.state.value.selectedAudienceId)
        }

    @Test
    fun select_audience_rejects_unknown_id() =
        runTest {
            val vm = makeVm()
            vm.selectAudience("does-not-exist")
            assertEquals(
                "Unknown ids are dropped",
                "all",
                vm.state.value.selectedAudienceId,
            )
        }

    @Test
    fun send_update_clears_draft_and_flashes_toast() =
        runTest {
            val vm = makeVm()
            assertNull(vm.state.value.toast)
            vm.sendUpdate()
            assertEquals("", vm.state.value.draftMessage)
            assertNotNull(vm.state.value.toast)
            assertTrue(vm.state.value.toast!!.contains("12"))
        }

    @Test
    fun show_and_hide_close_sheet() =
        runTest {
            val vm = makeVm()
            assertEquals(ManageTrainSheetMode.HIDDEN, vm.state.value.sheetMode)
            vm.showCloseSheet()
            assertEquals(ManageTrainSheetMode.CLOSING, vm.state.value.sheetMode)
            vm.hideCloseSheet()
            assertEquals(ManageTrainSheetMode.HIDDEN, vm.state.value.sheetMode)
        }

    @Test
    fun confirm_close_flips_train_and_fires_toast() =
        runTest {
            val vm = makeVm()
            vm.showCloseSheet()
            vm.confirmClose()
            assertEquals(ManageTrainSheetMode.CLOSED, vm.state.value.sheetMode)
            val loaded = vm.state.value.state as ManageTrainState.Loaded
            assertFalse(
                "Confirm close flips the train chip to Closed",
                loaded.content.isActive,
            )
            assertNotNull(vm.state.value.toast)
        }

    @Test
    fun sample_data_active_fixture_matches_design_copy() {
        val content = ManageTrainSampleData.active
        assertEquals("18", content.close.mealsDelivered)
        assertEquals("12", content.close.neighborsHelped)
        assertEquals("12d", content.close.coverageDays)
        assertTrue(content.close.recipientQuote.contains("Theo"))
        assertEquals("All helpers", content.audienceChips.first().label)
        assertEquals("12", content.audienceChips.first().count)
    }
}
