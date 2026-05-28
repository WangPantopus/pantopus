@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.owners.transfer

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A13.4 — Behavioural unit tests for the Transfer Ownership form
 * view-model. Mirrors iOS `TransferOwnershipViewModelTests` so both
 * platforms march through the same state machine.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransferOwnershipViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeViewModel(): TransferOwnershipViewModel =
        TransferOwnershipViewModel(SavedStateHandle(mapOf(TRANSFER_HOME_ID_KEY to "preview")))

    @Test
    fun initial_state_not_ready_to_commit() {
        val vm = makeViewModel()
        val state = vm.state.value
        assertFalse(state.isReadyToCommit)
        assertEquals(ConfirmSheetPhase.Hidden, state.sheetPhase)
        assertEquals(TransferOwnershipSampleData.DEFAULT_AMOUNT, state.amount)
        assertFalse(state.confirmationMatches)
    }

    @Test
    fun typing_confirmation_phrase_arms_cta() {
        val vm = makeViewModel()
        vm.updateConfirmation("TRANSFER")
        val state = vm.state.value
        assertTrue(state.confirmationMatches)
        assertTrue(state.isReadyToCommit)
    }

    @Test
    fun lowercase_does_not_match() {
        val vm = makeViewModel()
        vm.updateConfirmation("transfer")
        assertFalse(vm.state.value.confirmationMatches)
        assertFalse(vm.state.value.isReadyToCommit)
    }

    @Test
    fun amount_is_clamped_to_user_stake() {
        val vm = makeViewModel()
        vm.updateAmount(120)
        assertEquals(vm.state.value.maxAmount, vm.state.value.amount)
        vm.updateAmount(0)
        assertEquals(vm.state.value.sliderRange.first, vm.state.value.amount)
    }

    @Test
    fun preset_jumps_amount() {
        val vm = makeViewModel()
        vm.selectPreset(33)
        assertEquals(33, vm.state.value.amount)
    }

    @Test
    fun after_segments_reflect_live_amount() {
        val vm = makeViewModel()
        vm.updateAmount(25)
        val after = vm.state.value.afterSegments
        val you = after.first { it.id == TransferOwnershipSampleData.currentUser.id }
        val maya = after.first { it.id == vm.state.value.recipient.id }
        assertEquals(35, you.percent)
        assertEquals(-25, you.delta)
        assertEquals(25, maya.percent)
        assertEquals(25, maya.delta)
        assertTrue(maya.isNew)
    }

    @Test
    fun present_confirm_sheet_only_when_ready() {
        val vm = makeViewModel()
        vm.presentConfirmSheet()
        assertEquals(ConfirmSheetPhase.Hidden, vm.state.value.sheetPhase)
        vm.updateConfirmation("TRANSFER")
        vm.presentConfirmSheet()
        assertEquals(ConfirmSheetPhase.Visible, vm.state.value.sheetPhase)
    }

    @Test
    fun dismiss_confirm_sheet_resets() {
        val vm = makeViewModel()
        vm.updateConfirmation("TRANSFER")
        vm.presentConfirmSheet()
        vm.dismissConfirmSheet()
        assertEquals(ConfirmSheetPhase.Hidden, vm.state.value.sheetPhase)
    }

    @Test
    fun request_biometric_marks_authenticating() {
        val vm = makeViewModel()
        vm.updateConfirmation("TRANSFER")
        vm.presentConfirmSheet()
        vm.requestBiometric()
        assertEquals(ConfirmSheetPhase.Authenticating, vm.state.value.sheetPhase)
    }

    @Test
    fun biometric_failure_keeps_sheet_open_with_error() {
        val vm = makeViewModel()
        vm.updateConfirmation("TRANSFER")
        vm.presentConfirmSheet()
        vm.requestBiometric()
        vm.handleBiometricResult(success = false, errorMessage = "Try again")
        assertEquals(ConfirmSheetPhase.Visible, vm.state.value.sheetPhase)
        assertEquals("Try again", vm.state.value.biometricErrorMessage)
        assertFalse(vm.state.value.shouldDismiss)
    }

    @Test
    fun successful_biometric_commits_and_dismisses() =
        runTest(dispatcher) {
            val vm = makeViewModel()
            vm.updateConfirmation("TRANSFER")
            vm.presentConfirmSheet()
            vm.requestBiometric()
            vm.handleBiometricResult(success = true)
            advanceUntilIdle()
            assertEquals(ConfirmSheetPhase.Dismissing, vm.state.value.sheetPhase)
            assertTrue(vm.state.value.shouldDismiss)
            assertNotNull(vm.state.value.toast)
            assertFalse(vm.state.value.toast!!.isError)
        }

    @Test
    fun cta_label_uses_recipient_first_name() {
        val vm = makeViewModel()
        vm.updateAmount(33)
        assertEquals("Transfer 33% to Maya", vm.state.value.ctaLabel)
    }

    @Test
    fun warning_copy_names_other_owners() {
        val vm = makeViewModel()
        val copy = vm.state.value.warningCopy
        assertTrue(copy.contains("Mateo and Jin"))
        assertTrue(copy.contains("Maya"))
    }

    @Test
    fun dirty_picks_up_amount_changes() {
        val vm = makeViewModel()
        assertFalse(vm.state.value.isDirty)
        vm.updateAmount(10)
        assertTrue(vm.state.value.isDirty)
    }
}
