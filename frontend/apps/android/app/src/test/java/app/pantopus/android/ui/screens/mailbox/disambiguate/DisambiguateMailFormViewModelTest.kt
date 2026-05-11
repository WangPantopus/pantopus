@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.disambiguate

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.mailbox.v2.ResolveRoutingRequest
import app.pantopus.android.data.api.models.mailbox.v2.ResolveRoutingResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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

@OptIn(ExperimentalCoroutinesApi::class)
class DisambiguateMailFormViewModelTest {
    private val repo: MailboxRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): DisambiguateMailFormViewModel =
        DisambiguateMailFormViewModel(
            repo = repo,
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        DISAMBIGUATE_MAIL_ID_KEY to "mail-1",
                        DISAMBIGUATE_OCR_TEXT_KEY to "MS. ALEX",
                        DISAMBIGUATE_CONFIDENCE_KEY to 0.85,
                    ),
                ),
        )

    @Test fun initial_state_blocks_submit() {
        val vm = makeVm()
        val state = vm.state.value
        assertNull(state.selected)
        assertFalse(state.canSubmit)
    }

    @Test fun selecting_choice_enables_submit() {
        val vm = makeVm()
        vm.select(MailRecipientChoice.Home)
        val state = vm.state.value
        assertEquals(MailRecipientChoice.Home, state.selected)
        assertTrue(state.canSubmit)
    }

    @Test fun alias_over_255_chars_blocks_submit() {
        val vm = makeVm()
        vm.select(MailRecipientChoice.Personal)
        vm.setAliasNotes("x".repeat(256))
        assertNotNull(vm.state.value.aliasError)
        assertFalse(vm.state.value.canSubmit)
    }

    @Test fun submit_happy_path_sends_drawer_and_alias() =
        runTest {
            val captured = slot<ResolveRoutingRequest>()
            coEvery { repo.resolve(io.mockk.capture(captured)) } returns
                NetworkResult.Success(ResolveRoutingResponse(message = "ok", drawer = "home"))
            val vm = makeVm()
            vm.select(MailRecipientChoice.Home)
            vm.setAliasNotes("Mom")
            vm.submit()
            assertEquals("home", captured.captured.drawer)
            assertEquals("mail-1", captured.captured.mailId)
            assertEquals(true, captured.captured.addAlias)
            assertEquals("Mom", captured.captured.aliasString)
            assertTrue(vm.state.value.shouldDismiss)
        }

    @Test fun submit_with_empty_alias_omits_alias_fields() =
        runTest {
            val captured = slot<ResolveRoutingRequest>()
            coEvery { repo.resolve(io.mockk.capture(captured)) } returns
                NetworkResult.Success(ResolveRoutingResponse(message = "ok", drawer = "personal"))
            val vm = makeVm()
            vm.select(MailRecipientChoice.Personal)
            vm.submit()
            assertNull(captured.captured.addAlias)
            assertNull(captured.captured.aliasString)
        }

    @Test fun submit_failure_surfaces_error_toast() =
        runTest {
            coEvery { repo.resolve(any()) } returns
                NetworkResult.Failure(NetworkError.NotFound)
            val vm = makeVm()
            vm.select(MailRecipientChoice.Home)
            vm.submit()
            assertEquals(true, vm.state.value.toast?.isError)
        }

    @Test fun submit_without_selection_does_not_call_backend() =
        runTest {
            val vm = makeVm()
            vm.submit()
            coVerify(exactly = 0) { repo.resolve(any()) }
            assertEquals(true, vm.state.value.toast?.isError)
        }
}
