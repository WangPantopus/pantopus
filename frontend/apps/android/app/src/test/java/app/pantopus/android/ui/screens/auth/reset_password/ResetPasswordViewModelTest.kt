@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.auth.reset_password

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.auth.AuthError
import app.pantopus.android.data.auth.AuthRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
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

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun buildVm(
        token: String,
        repo: AuthRepository = mockk(relaxed = true),
    ): ResetPasswordViewModel = ResetPasswordViewModel(repo, SavedStateHandle(mapOf(ResetPasswordViewModel.TOKEN_KEY to token)))

    @Test
    fun `token is captured from SavedStateHandle`() {
        val vm = buildVm(token = "deep-link-hashed")
        assertEquals("deep-link-hashed", vm.uiState.value.token)
    }

    @Test
    fun `canSubmit requires strength + match + token`() {
        val vm = buildVm(token = "tok")
        assertFalse(vm.uiState.value.canSubmit)
        vm.onPasswordChange("weak")
        vm.onConfirmPasswordChange("weak")
        assertFalse(vm.uiState.value.canSubmit)
        vm.onPasswordChange("strongpass1")
        vm.onConfirmPasswordChange("different1")
        assertFalse(vm.uiState.value.canSubmit)
        vm.onConfirmPasswordChange("strongpass1")
        assertTrue(vm.uiState.value.canSubmit)
    }

    @Test
    fun `canSubmit false when token missing`() {
        val vm = buildVm(token = "")
        vm.onPasswordChange("strongpass1")
        vm.onConfirmPasswordChange("strongpass1")
        assertFalse(vm.uiState.value.canSubmit)
    }

    @Test
    fun `submit calls AuthRepository resetPassword with the token + new password`() =
        runTest {
            val repo = mockk<AuthRepository>(relaxed = true)
            val tokenSlot = slot<String>()
            val passwordSlot = slot<String>()
            coEvery { repo.resetPassword(capture(tokenSlot), capture(passwordSlot)) } returns Unit

            val vm = buildVm(token = "deep-tok", repo = repo)
            vm.onPasswordChange("strongpass1")
            vm.onConfirmPasswordChange("strongpass1")
            vm.submit()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.phase is ResetPasswordViewModel.Phase.Reset)
            assertEquals("deep-tok", tokenSlot.captured)
            assertEquals("strongpass1", passwordSlot.captured)
            assertNull(state.errorMessage)
        }

    @Test
    fun `submit rolls back loading state on error`() =
        runTest {
            val repo = mockk<AuthRepository>(relaxed = true)
            coEvery { repo.resetPassword(any(), any()) } throws AuthError.ServerError("Invalid or expired reset token")

            val vm = buildVm(token = "stale", repo = repo)
            vm.onPasswordChange("strongpass1")
            vm.onConfirmPasswordChange("strongpass1")
            vm.submit()
            advanceUntilIdle()

            val state = vm.uiState.value
            assertTrue(state.phase is ResetPasswordViewModel.Phase.Form)
            assertEquals(
                "Invalid or expired reset token",
                (state.errorMessage as AuthError.ServerError).detail,
            )
            assertFalse(state.isLoading)
        }

    @Test
    fun `submit blocked when invalid does not hit network`() =
        runTest {
            val repo = mockk<AuthRepository>(relaxed = true)
            val vm = buildVm(token = "tok", repo = repo)
            // No password — gate blocks submission.
            vm.submit()
            advanceUntilIdle()
            coVerify(exactly = 0) { repo.resetPassword(any(), any()) }
        }
}
