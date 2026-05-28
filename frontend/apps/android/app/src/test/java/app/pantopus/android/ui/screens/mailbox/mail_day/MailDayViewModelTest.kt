@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mail_day

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
 * A13.16 — state-projection coverage for the My Mail Day view-model.
 * Mirrors `MailDayViewModelTests` (iOS): asserts the populated and
 * empty frames project off the sample fixture cleanly, the counter
 * derivations add up, and the 5-second undo countdown ticks down
 * through `tickUndo`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MailDayViewModelTest {
    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(variant: String = "populated"): MailDayViewModel =
        MailDayViewModel(SavedStateHandle(mapOf(MAIL_DAY_VARIANT_KEY to variant)))

    @Test
    fun loadProjectsPopulatedFrame() =
        runTest {
            val vm = makeVm("populated")
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Populated, got $state", state is MailDayUiState.Populated)
            val content = (state as MailDayUiState.Populated).content
            assertEquals(2, content.unreviewed.size)
            assertEquals(6, content.reviewed.size)
            assertEquals(8, vm.total)
            assertEquals(6, vm.done)
            assertEquals(2, vm.remaining)
            assertEquals(4, vm.routedCount)
            assertEquals(1, vm.junkedCount)
            assertEquals(1, vm.returnedCount)
            assertFalse("Finish day is disabled while 2 pieces pending", vm.canFinishDay)
        }

    @Test
    fun populatedLatestRowCarriesUndoCountdown() =
        runTest {
            val vm = makeVm("populated")
            vm.load()
            val state = vm.state.value as MailDayUiState.Populated
            assertEquals(5, state.content.reviewed.first().undoCountdown)
            assertNull(
                "Only the latest reviewed row should carry the countdown",
                state.content.reviewed.drop(1).firstOrNull()?.undoCountdown,
            )
        }

    @Test
    fun tickUndoDecrementsLatest() =
        runTest {
            val vm = makeVm("populated")
            vm.load()
            vm.tickUndo()
            val state = vm.state.value as MailDayUiState.Populated
            assertEquals(4, state.content.reviewed.first().undoCountdown)
        }

    @Test
    fun tickUndoClearsAtZero() =
        runTest {
            val vm = makeVm("populated")
            vm.load()
            repeat(5) { vm.tickUndo() }
            val state = vm.state.value as MailDayUiState.Populated
            assertNull("Should clear once seconds hit 0", state.content.reviewed.first().undoCountdown)
        }

    @Test
    fun tickUndoNoOpOnEmpty() =
        runTest {
            val vm = makeVm("empty")
            vm.load()
            vm.tickUndo() // should not crash
            assertTrue(vm.state.value is MailDayUiState.Empty)
        }

    @Test
    fun acceptSuggestionMovesUnreviewedToReviewed() =
        runTest {
            val vm = makeVm("populated")
            vm.load()
            val initial = vm.state.value as MailDayUiState.Populated
            val target = initial.content.unreviewed[0]
            vm.acceptSuggestion(target.id)
            val updated = vm.state.value as MailDayUiState.Populated
            assertEquals(1, updated.content.unreviewed.size)
            assertEquals(7, updated.content.reviewed.size)
            assertEquals(target.id, updated.content.reviewed.first().id)
            assertEquals(ReviewedMailAction.Routed, updated.content.reviewed.first().action)
            assertEquals(5, updated.content.reviewed.first().undoCountdown)
            assertTrue(
                "Older rows lose their countdown when a newer action arrives",
                updated.content.reviewed.drop(1).all { it.undoCountdown == null },
            )
        }

    @Test
    fun emptyProjectsRecapAndNudges() =
        runTest {
            val vm = makeVm("empty")
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Empty, got $state", state is MailDayUiState.Empty)
            val content = (state as MailDayUiState.Empty).content
            assertEquals(0, content.unreviewed.size)
            assertEquals(0, content.reviewed.size)
            assertEquals(12, content.streakDays)
            assertEquals("9h ago", content.lastScanLabel)
            assertNotNull("yesterdayRecap should populate on empty frame", content.yesterdayRecap)
            assertEquals(4, content.yesterdayRecap?.segments?.size)
            assertEquals(2, content.setupNudges.size)
        }

    @Test
    fun requestScanInvokesConfiguredCallback() =
        runTest {
            val vm = makeVm("empty")
            vm.load()
            var scanCalls = 0
            vm.configure(onScanRequested = { scanCalls++ })
            vm.requestScan()
            vm.requestScan()
            assertEquals(2, scanCalls)
        }
}
