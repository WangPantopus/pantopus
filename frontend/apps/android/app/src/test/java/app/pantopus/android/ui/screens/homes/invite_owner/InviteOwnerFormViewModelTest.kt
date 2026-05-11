@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.invite_owner

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.InviteOwnerRequest
import app.pantopus.android.data.api.models.homes.InviteOwnerResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
class InviteOwnerFormViewModelTest {
    private val repo: HomesRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(currentEmail: String = "me@example.com"): InviteOwnerFormViewModel =
        InviteOwnerFormViewModel(
            repo = repo,
            savedStateHandle =
                SavedStateHandle(
                    mapOf(
                        INVITE_OWNER_HOME_ID_KEY to "home-1",
                        INVITE_OWNER_CURRENT_EMAIL_KEY to currentEmail,
                    ),
                ),
        )

    @Test fun initial_state_is_clean_and_invalid() {
        val vm = makeVm()
        val state = vm.state.value
        assertFalse(state.isDirty)
        assertFalse(state.isValid)
    }

    @Test fun email_validation_rejects_garbage() {
        val vm = makeVm()
        vm.update(InviteOwnerField.Email, "not-an-email")
        val state = vm.state.value
        assertNotNull(state.fields[InviteOwnerField.Email]?.error)
        assertFalse(state.isValid)
    }

    @Test fun email_rejects_self_invite_case_insensitive() {
        val vm = makeVm(currentEmail = "Alex@example.com")
        vm.update(InviteOwnerField.Email, "alex@example.com")
        val state = vm.state.value
        assertEquals("You can't invite yourself.", state.fields[InviteOwnerField.Email]?.error)
    }

    @Test fun phone_optional_passes_when_empty_and_e164() {
        val vm = makeVm()
        vm.update(InviteOwnerField.Email, "x@y.com")
        vm.update(InviteOwnerField.Phone, "")
        assertNull(vm.state.value.fields[InviteOwnerField.Phone]?.error)
        assertTrue(vm.state.value.isValid)

        vm.update(InviteOwnerField.Phone, "555-1212")
        assertNotNull(vm.state.value.fields[InviteOwnerField.Phone]?.error)
        assertFalse(vm.state.value.isValid)

        vm.update(InviteOwnerField.Phone, "+15555550123")
        assertNull(vm.state.value.fields[InviteOwnerField.Phone]?.error)
        assertTrue(vm.state.value.isValid)
    }

    @Test fun submit_happy_path_dismisses_form() =
        runTest {
            coEvery { repo.inviteOwner("home-1", any()) } returns
                NetworkResult.Success(InviteOwnerResponse(message = "ok", claimId = "c-1"))
            val vm = makeVm()
            vm.update(InviteOwnerField.Email, "alex@pantopus.app")
            vm.submit()
            // The VM holds the toast for 1.5s before flipping the dismiss
            // flag so the success overlay renders. Skip past that delay
            // in virtual time before asserting the dismiss latch.
            assertEquals("Invite sent.", vm.state.value.toast?.text)
            advanceTimeBy(1_600)
            runCurrent()
            assertTrue(vm.state.value.shouldDismiss)
            coVerify(exactly = 1) { repo.inviteOwner("home-1", any()) }
        }

    @Test fun submit_409_maps_to_inline_email_error() =
        runTest {
            coEvery { repo.inviteOwner("home-1", any()) } returns
                NetworkResult.Failure(
                    NetworkError.ClientError(409, "An ownership claim is already active for this home."),
                )
            val vm = makeVm()
            vm.update(InviteOwnerField.Email, "alex@pantopus.app")
            vm.submit()
            val state = vm.state.value
            assertEquals(
                "An ownership claim is already active for this home.",
                state.fields[InviteOwnerField.Email]?.error,
            )
        }

    @Test fun submit_with_invalid_email_skips_network() =
        runTest {
            val vm = makeVm()
            vm.update(InviteOwnerField.Email, "garbage")
            vm.submit()
            coVerify(exactly = 0) { repo.inviteOwner(any(), any()) }
        }

    @Test fun build_request_omits_empty_phone() =
        runTest {
            val captured = slot<InviteOwnerRequest>()
            coEvery { repo.inviteOwner("home-1", capture(captured)) } returns
                NetworkResult.Success(InviteOwnerResponse(message = "ok", claimId = "c-1"))
            val vm = makeVm()
            vm.update(InviteOwnerField.Email, "alex@pantopus.app")
            vm.submit()
            assertEquals("alex@pantopus.app", captured.captured.email)
            assertNull(captured.captured.phone)
            assertEquals(false, captured.captured.fastTrack)
        }
}
