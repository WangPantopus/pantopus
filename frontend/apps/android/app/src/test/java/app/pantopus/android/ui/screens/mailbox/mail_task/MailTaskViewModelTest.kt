@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mail_task

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A17.12 — exercises the Mail-task view-model: load seeds the right
 * frame, subtask taps persist + drive progress, mark-done / reopen flip
 * the frame, and the source / next-up taps resolve the right mail id.
 * Mirrors iOS `MailTaskViewModelTests`. Pure JVM — no Robolectric.
 */
class MailTaskViewModelTest {
    private fun loaded(vm: MailTaskViewModel): MailTaskContent {
        vm.load()
        return (vm.state.value as MailTaskUiState.Loaded).content
    }

    @Test
    fun load_activeSeed_isNotDone() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        val content = loaded(vm)
        assertFalse(content.isDone)
        assertEquals(1, content.finishedSteps)
        assertEquals(3, content.totalSteps)
    }

    @Test
    fun load_doneSeed_isDone_andFullProgress() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Done)
        val content = loaded(vm)
        assertTrue(content.isDone)
        assertEquals(content.totalSteps, content.finishedSteps)
        assertEquals(1f, content.progress, 0.0001f)
    }

    @Test
    fun toggleSubtask_persistsAndUpdatesProgress() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        loaded(vm)
        vm.toggleSubtask("photos")
        val content = (vm.state.value as MailTaskUiState.Loaded).content
        assertEquals(2, content.finishedSteps)
        assertTrue(content.subtasks.first { it.id == "photos" }.isDone)
    }

    @Test
    fun toggleSubtask_isReversible() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        loaded(vm)
        vm.toggleSubtask("draft") // was done → now undone
        val content = (vm.state.value as MailTaskUiState.Loaded).content
        assertEquals(0, content.finishedSteps)
    }

    @Test
    fun markDone_flipsFrameAndToasts() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        loaded(vm)
        vm.markDone()
        assertTrue(vm.isDone)
        assertEquals("Marked done", vm.toast.value)
    }

    @Test
    fun reopen_returnsToOpenFrame() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Done)
        loaded(vm)
        vm.reopen()
        assertFalse(vm.isDone)
        assertEquals("Task reopened", vm.toast.value)
    }

    @Test
    fun toggleSubtask_noopWhenDone() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Done)
        loaded(vm)
        vm.toggleSubtask("photos")
        val content = (vm.state.value as MailTaskUiState.Loaded).content
        assertEquals(content.totalSteps, content.finishedSteps)
    }

    @Test
    fun openSourceMail_resolvesSourceId() {
        var opened: String? = null
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        vm.configureNavigation(onOpenMail = { opened = it })
        loaded(vm)
        vm.openSourceMail()
        assertEquals("mail_412elm_hearing", opened)
    }

    @Test
    fun openNextUp_resolvesNextUpId() {
        var opened: String? = null
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Done)
        vm.configureNavigation(onOpenMail = { opened = it })
        loaded(vm)
        vm.openNextUp()
        assertEquals("mail_riverside_linen", opened)
    }

    @Test
    fun delegate_opensSheet() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        loaded(vm)
        vm.delegate()
        assertTrue(vm.showsDelegateSheet.value)
    }

    @Test
    fun snooze_toasts() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        loaded(vm)
        vm.snooze("evening")
        assertEquals("Snoozed · This evening", vm.toast.value)
    }

    @Test
    fun configureSeed_doneReseedsLoadedFrame() {
        val vm = MailTaskViewModel("t_1", MailTaskSeed.Active)
        loaded(vm)
        vm.configureSeed(MailTaskSeed.Done)
        assertTrue((vm.state.value as MailTaskUiState.Loaded).content.isDone)
    }
}
