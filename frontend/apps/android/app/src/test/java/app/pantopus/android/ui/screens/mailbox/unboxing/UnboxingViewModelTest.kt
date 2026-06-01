@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.unboxing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A17.14 — JVM unit tests for the Unboxing flow projection. No Robolectric:
 * the view-model projects the deterministic fixture and flips in-memory
 * state synchronously.
 */
class UnboxingViewModelTest {
    @Test
    fun `starts in capture with the sample fixture`() {
        val vm = UnboxingViewModel()
        val state = vm.state.value
        assertTrue(state is UnboxingUiState.Capture)
        assertEquals("Breville Barista Express", state.content.productTitle)
        assertEquals(4, state.content.shots.size)
    }

    @Test
    fun `capture appends the next labeled shot, cycling the canonical sequence`() {
        val vm = UnboxingViewModel()
        val before = vm.state.value.content.shots.size
        vm.capture()
        val after = vm.state.value.content.shots
        assertEquals(before + 1, after.size)
        assertEquals(UnboxingSampleData.captureSequence[before % 4].label, after.last().label)
    }

    @Test
    fun `confirm files the item, undo returns to capture`() {
        val vm = UnboxingViewModel()
        vm.confirm()
        assertTrue(vm.state.value is UnboxingUiState.Filed)
        vm.undo()
        assertTrue(vm.state.value is UnboxingUiState.Capture)
    }

    @Test
    fun `scanNext re-arms the four-shot sequence and notifies the host`() {
        val vm = UnboxingViewModel()
        var handed = false
        vm.configure(onScanNext = { handed = true }, onOpenDrawer = {})
        // Grow the strip, file, then scan next — the sequence resets to four.
        vm.capture()
        vm.confirm()
        vm.scanNext()
        val state = vm.state.value
        assertTrue(state is UnboxingUiState.Capture)
        assertEquals(4, state.content.shots.size)
        assertTrue(handed)
    }

    @Test
    fun `openDrawer notifies the host`() {
        val vm = UnboxingViewModel()
        var opened = false
        vm.configure(onScanNext = {}, onOpenDrawer = { opened = true })
        vm.openDrawer()
        assertTrue(opened)
    }
}
