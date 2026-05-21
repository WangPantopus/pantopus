@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.audience_profile.compose_broadcast

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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * A.7 (A22.2) — Behavioral coverage for the Compose Broadcast VM, mirroring
 * the iOS `ComposeBroadcastViewModelTests`: derived composeState
 * (empty / composing / scheduled / sending / error), live counter,
 * over-limit guard, audience + media + send reset, the unsaved-draft
 * indicator, and the first-run CTA copy.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ComposeBroadcastViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun buildVm(personaId: String = "p1"): ComposeBroadcastViewModel =
        ComposeBroadcastViewModel(SavedStateHandle(mapOf(COMPOSE_BROADCAST_PERSONA_ID_KEY to personaId)))

    @Test
    fun `initial state is empty`() {
        val vm = buildVm()
        assertEquals(ComposeBroadcastState.Empty, vm.state.value.composeState())
        assertFalse(vm.state.value.canSend)
        assertEquals(0, vm.state.value.characterCount)
        assertFalse(vm.state.value.isDirty)
    }

    @Test
    fun `typing transitions to composing and counts live`() {
        val vm = buildVm()
        vm.updateBody("Hello beacons")
        assertEquals(ComposeBroadcastState.Composing(vm.state.value.draft), vm.state.value.composeState())
        assertEquals(13, vm.state.value.characterCount)
        assertTrue(vm.state.value.canSend)
        assertTrue(vm.state.value.isDirty)
    }

    @Test
    fun `over character limit blocks send`() {
        val vm = buildVm()
        vm.updateBody("a".repeat(1001))
        assertTrue(vm.state.value.isOverLimit)
        assertFalse(vm.state.value.canSend)
    }

    @Test
    fun `media without body still allows send`() {
        val vm = buildVm()
        vm.attachMedia(ComposeMediaPreview(id = "m", kind = ComposeMediaPreview.Kind.Image, caption = "boule.jpg"))
        assertFalse(vm.state.value.draft.isEmpty)
        assertTrue(vm.state.value.canSend)
        vm.removeMedia()
        assertNull(vm.state.value.draft.media)
        assertEquals(ComposeBroadcastState.Empty, vm.state.value.composeState())
    }

    @Test
    fun `set audience updates draft and reach`() {
        val vm = buildVm()
        assertEquals(BroadcastAudience.AllBeacons, vm.state.value.draft.audience)
        vm.setAudience(BroadcastAudience.BronzePlus)
        assertEquals(BroadcastAudience.BronzePlus, vm.state.value.draft.audience)
        assertEquals(518, vm.state.value.reach(BroadcastAudience.BronzePlus))
    }

    @Test
    fun `schedule and send now toggle state`() {
        val vm = buildVm()
        vm.updateBody("Loaf drop at 4")
        vm.schedule(ComposeBroadcastSampleData.SCHEDULED_AT_MILLIS)
        val scheduled = vm.state.value.composeState()
        assertTrue(scheduled is ComposeBroadcastState.Scheduled)
        assertEquals(ComposeBroadcastSampleData.SCHEDULED_AT_MILLIS, vm.state.value.scheduledAtMillis)
        vm.sendNow()
        assertNull(vm.state.value.scheduledAtMillis)
        assertEquals(ComposeBroadcastState.Composing(vm.state.value.draft), vm.state.value.composeState())
    }

    @Test
    fun `save draft clears unsaved indicator`() {
        val vm = buildVm()
        vm.updateBody("draft")
        assertTrue(vm.state.value.isDirty)
        vm.saveDraft()
        assertFalse(vm.state.value.isDirty)
        vm.updateBody("draft and more")
        assertTrue(vm.state.value.isDirty)
    }

    @Test
    fun `send passes through sending then resets`() =
        runTest(dispatcher) {
            val vm = buildVm()
            vm.updateBody("Going live")
            vm.send()
            // StandardTestDispatcher defers the launched body, so the
            // synchronous phase flip is observable here.
            assertEquals(ComposeBroadcastState.Sending, vm.state.value.composeState())
            advanceUntilIdle()
            assertEquals(ComposeBroadcastState.Empty, vm.state.value.composeState())
        }

    @Test
    fun `send success calls onSent and keeps audience`() =
        runTest(dispatcher) {
            val vm = buildVm()
            var sent = false
            vm.setAudience(BroadcastAudience.SilverPlus)
            vm.updateBody("Q&A recording is up")
            vm.send(onSent = { sent = true })
            advanceUntilIdle()
            assertTrue(sent)
            assertEquals(ComposeBroadcastState.Empty, vm.state.value.composeState())
            assertEquals(BroadcastAudience.SilverPlus, vm.state.value.draft.audience)
            assertFalse(vm.state.value.isDirty)
        }

    @Test
    fun `send failure surfaces error and preserves draft`() =
        runTest(dispatcher) {
            val vm = buildVm()
            vm.performSend = { _, _ -> throw RuntimeException("Network down") }
            vm.updateBody("keep me")
            vm.send()
            advanceUntilIdle()
            assertEquals(ComposeBroadcastState.Error("Network down"), vm.state.value.composeState())
            assertEquals("keep me", vm.state.value.draft.body)
            vm.retry()
            assertEquals(
                ComposeBroadcastState.Composing(ComposeBroadcastDraft(body = "keep me")),
                vm.state.value.composeState(),
            )
        }

    @Test
    fun `editing clears prior send error`() =
        runTest(dispatcher) {
            val vm = buildVm()
            vm.performSend = { _, _ -> throw RuntimeException("Oops") }
            vm.updateBody("first")
            vm.send()
            advanceUntilIdle()
            assertEquals(ComposeBroadcastState.Error("Oops"), vm.state.value.composeState())
            vm.updateBody("first edited")
            assertEquals(ComposeBroadcastState.Composing(vm.state.value.draft), vm.state.value.composeState())
        }

    @Test
    fun `primary action title reflects first run`() {
        val withRecents = ComposeBroadcastSampleData.populated()
        assertEquals("Send broadcast", withRecents.primaryActionTitle)
        val firstRun = ComposeBroadcastSampleData.empty()
        assertEquals("Send your first broadcast", firstRun.primaryActionTitle)
    }

    @Test
    fun `sample data provides at least three recent broadcasts with stats`() {
        val recents = ComposeBroadcastSampleData.recentBroadcasts
        assertTrue(recents.size >= 3)
        recents.forEach {
            assertTrue(it.reach.isNotEmpty())
            assertTrue(it.read.isNotEmpty())
            assertTrue(it.reactions.isNotEmpty())
            assertTrue(it.replies.isNotEmpty())
        }
    }
}
