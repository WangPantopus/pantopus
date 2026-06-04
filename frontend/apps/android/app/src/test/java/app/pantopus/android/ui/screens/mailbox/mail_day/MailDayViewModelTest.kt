@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mail_day

import app.pantopus.android.data.api.models.mailbox.v2.LogEventResponse
import app.pantopus.android.data.api.models.mailbox.v2.PendingItemDto
import app.pantopus.android.data.api.models.mailbox.v2.PendingMailDto
import app.pantopus.android.data.api.models.mailbox.v2.PendingResponse
import app.pantopus.android.data.api.models.mailbox.v2.ResolveRoutingRequest
import app.pantopus.android.data.api.models.mailbox.v2.ResolveRoutingResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * A13.16 — coverage for the live My Mail Day view-model. The triage list
 * now comes from `GET /api/mailbox/v2/pending`; accepting a suggestion
 * optimistically moves the card to the reviewed list and calls
 * `POST /resolve` (rolling back on failure); finishing the day logs
 * `POST /event`. Counts are derived client-side.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MailDayViewModelTest {
    private val repository: MailboxRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pendingItem(
        id: String,
        name: String = "Alex Rivera",
        confidence: Double = 0.9,
    ) = PendingItemDto(
        mailId = id,
        recipientNameRaw = name,
        bestMatchUserId = "u_$id",
        bestMatchConfidence = confidence,
        mail =
            PendingMailDto(
                subject = "Subject $id",
                senderDisplay = "Sender $id",
                category = "personal",
                mailObjectType = "envelope",
            ),
    )

    private fun vmWithPending(vararg items: PendingItemDto): MailDayViewModel {
        coEvery { repository.pending() } returns NetworkResult.Success(PendingResponse(items.toList()))
        coEvery { repository.resolve(any()) } returns NetworkResult.Success(ResolveRoutingResponse("Routing resolved", "personal"))
        coEvery { repository.logEvent(any(), any(), any()) } returns NetworkResult.Success(LogEventResponse(logged = true))
        return MailDayViewModel(repository)
    }

    @Test
    fun loadProjectsTriageListFromPending() =
        runTest {
            val vm = vmWithPending(pendingItem("m1"), pendingItem("m2"))
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Populated, got $state", state is MailDayUiState.Populated)
            val content = (state as MailDayUiState.Populated).content
            assertEquals(2, content.unreviewed.size)
            assertEquals(0, content.reviewed.size)
            assertEquals("m1", content.unreviewed[0].id)
            assertEquals(2, vm.total)
            assertEquals(0, vm.done)
            assertEquals(2, vm.remaining)
            assertFalse("Finish day is disabled while pieces pending", vm.canFinishDay)
        }

    @Test
    fun loadProjectsEmptyWhenNothingPending() =
        runTest {
            val vm = vmWithPending()
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Empty, got $state", state is MailDayUiState.Empty)
            val content = (state as MailDayUiState.Empty).content
            assertEquals(0, content.unreviewed.size)
            // Recap / nudges / streak have no source on the live frame.
            assertNull(content.yesterdayRecap)
            assertTrue(content.setupNudges.isEmpty())
        }

    @Test
    fun loadSurfacesErrorOnFailure() =
        runTest {
            coEvery { repository.pending() } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = MailDayViewModel(repository)
            vm.load()
            assertTrue("Expected Error, got ${vm.state.value}", vm.state.value is MailDayUiState.Error)
        }

    @Test
    fun acceptSuggestionMovesToReviewedAndResolves() =
        runTest {
            val vm = vmWithPending(pendingItem("m1"), pendingItem("m2"))
            vm.load()
            val target = (vm.state.value as MailDayUiState.Populated).content.unreviewed[0]
            vm.acceptSuggestion(target.id)
            val updated = vm.state.value as MailDayUiState.Populated
            assertEquals(1, updated.content.unreviewed.size)
            assertEquals(1, updated.content.reviewed.size)
            assertEquals(
                target.id,
                updated.content.reviewed
                    .first()
                    .id,
            )
            assertEquals(
                ReviewedMailAction.Routed,
                updated.content.reviewed
                    .first()
                    .action,
            )
            assertEquals(
                5,
                updated.content.reviewed
                    .first()
                    .undoCountdown,
            )
            coVerify { repository.resolve(ResolveRoutingRequest(mailId = target.id, drawer = "personal")) }
        }

    @Test
    fun acceptSuggestionRollsBackWhenResolveFails() =
        runTest {
            val vm = vmWithPending(pendingItem("m1"), pendingItem("m2"))
            coEvery { repository.resolve(any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            vm.load()
            val target = (vm.state.value as MailDayUiState.Populated).content.unreviewed[0]
            vm.acceptSuggestion(target.id)
            val rolledBack = vm.state.value as MailDayUiState.Populated
            assertEquals(2, rolledBack.content.unreviewed.size)
            assertEquals(0, rolledBack.content.reviewed.size)
        }

    @Test
    fun tickUndoDecrementsLatestAfterAccept() =
        runTest {
            val vm = vmWithPending(pendingItem("m1"), pendingItem("m2"))
            vm.load()
            val target = (vm.state.value as MailDayUiState.Populated).content.unreviewed[0]
            vm.acceptSuggestion(target.id)
            vm.tickUndo()
            val state = vm.state.value as MailDayUiState.Populated
            assertEquals(
                4,
                state.content.reviewed
                    .first()
                    .undoCountdown,
            )
        }

    @Test
    fun tickUndoClearsAtZero() =
        runTest {
            val vm = vmWithPending(pendingItem("m1"), pendingItem("m2"))
            vm.load()
            val target = (vm.state.value as MailDayUiState.Populated).content.unreviewed[0]
            vm.acceptSuggestion(target.id)
            repeat(5) { vm.tickUndo() }
            val state = vm.state.value as MailDayUiState.Populated
            assertNull(
                "Should clear once seconds hit 0",
                state.content.reviewed
                    .first()
                    .undoCountdown,
            )
        }

    @Test
    fun tickUndoNoOpOnEmpty() =
        runTest {
            val vm = vmWithPending()
            vm.load()
            vm.tickUndo() // should not crash
            assertTrue(vm.state.value is MailDayUiState.Empty)
        }

    @Test
    fun canFinishDayOnceEverythingReviewed() =
        runTest {
            val vm = vmWithPending(pendingItem("m1"))
            vm.load()
            assertFalse(vm.canFinishDay)
            val target = (vm.state.value as MailDayUiState.Populated).content.unreviewed[0]
            vm.acceptSuggestion(target.id)
            assertTrue(vm.canFinishDay)
        }

    @Test
    fun finishDayLogsTelemetryEvent() =
        runTest {
            val vm = vmWithPending(pendingItem("m1"))
            vm.load()
            val target = (vm.state.value as MailDayUiState.Populated).content.unreviewed[0]
            vm.acceptSuggestion(target.id)
            vm.finishDay()
            coVerify { repository.logEvent(eventType = "mailday_finished", mailId = any(), metadata = any()) }
        }

    @Test
    fun requestScanInvokesConfiguredCallback() =
        runTest {
            val vm = vmWithPending()
            vm.load()
            var scanCalls = 0
            vm.configure(onScanRequested = { scanCalls++ })
            vm.requestScan()
            vm.requestScan()
            assertEquals(2, scanCalls)
        }
}
