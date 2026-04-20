package app.pantopus.android

import app.cash.turbine.test
import app.pantopus.android.data.api.models.UserDto
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.ui.screens.auth.LoginViewModel
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setup() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `canSubmit requires valid email and 6+ char password`() = runTest {
        val repo = mockk<AuthRepository>(relaxed = true) {
            coEvery { state } returns MutableStateFlow(AuthRepository.State.SignedOut)
        }
        val vm = LoginViewModel(repo)
        vm.uiState.test {
            assertFalse(awaitItem().canSubmit) // initial
            vm.onEmailChange("foo@bar.com")
            awaitItem() // email update
            vm.onPasswordChange("secret123")
            assertTrue(awaitItem().canSubmit)
        }
    }
}
