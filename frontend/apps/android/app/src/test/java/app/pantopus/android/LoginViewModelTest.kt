package app.pantopus.android

import app.cash.turbine.test
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.ui.screens.auth.LoginViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val sampleUser = UserDto(id = "u_1", email = "a@b.com", displayName = "A", avatarUrl = null)

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun repoReturning(result: Result<UserDto>) =
        mockk<AuthRepository>(relaxed = true).apply {
            coEvery { state } returns MutableStateFlow(AuthRepository.State.SignedOut)
            coEvery { signIn(any(), any()) } returns result
        }

    @Test
    fun `canSubmit requires valid email and 6+ char password`() =
        runTest {
            val vm = LoginViewModel(repoReturning(Result.success(sampleUser)))
            vm.uiState.test {
                assertFalse(awaitItem().canSubmit)
                vm.onEmailChange("foo@bar.com")
                awaitItem()
                vm.onPasswordChange("secret123")
                assertTrue(awaitItem().canSubmit)
            }
        }

    @Test
    fun `signIn flips isLoading then clears on success`() =
        runTest {
            val repo = repoReturning(Result.success(sampleUser))
            val vm = LoginViewModel(repo)

            vm.onEmailChange("alice@example.com")
            vm.onPasswordChange("hunter22")
            vm.signIn()

            advanceUntilIdle()

            val final = vm.uiState.value
            assertFalse(final.isLoading)
            assertNull(final.errorMessage)
            coVerify { repo.signIn("alice@example.com", "hunter22") }
        }

    @Test
    fun `signIn sets errorMessage on failure`() =
        runTest {
            val repo = repoReturning(Result.failure(IllegalStateException("bad credentials")))
            val vm = LoginViewModel(repo)

            vm.onEmailChange("alice@example.com")
            vm.onPasswordChange("hunter22")
            vm.signIn()

            advanceUntilIdle()

            val final = vm.uiState.value
            assertFalse(final.isLoading)
            assertEquals("bad credentials", final.errorMessage)
        }

    @Test
    fun `signIn is a no-op when form invalid`() =
        runTest {
            val repo = repoReturning(Result.success(sampleUser))
            val vm = LoginViewModel(repo)

            vm.signIn() // email + password empty
            advanceUntilIdle()

            coVerify(exactly = 0) { repo.signIn(any(), any()) }
        }

    @Test
    fun `typing email after error clears errorMessage`() =
        runTest {
            val repo = repoReturning(Result.failure(RuntimeException("oops")))
            val vm = LoginViewModel(repo)

            vm.onEmailChange("a@b.com")
            vm.onPasswordChange("hunter22")
            vm.signIn()
            advanceUntilIdle()
            assertEquals("oops", vm.uiState.value.errorMessage)

            vm.onEmailChange("a@b.com ")
            assertNull(vm.uiState.value.errorMessage)
        }
}
