package app.pantopus.android.data.auth

import app.cash.turbine.test
import app.pantopus.android.data.api.ApiService
import app.pantopus.android.data.api.models.AuthResponse
import app.pantopus.android.data.api.models.LoginRequest
import app.pantopus.android.data.api.models.UserDto
import app.pantopus.android.data.observability.Observability
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {

    private val user = UserDto(id = "u_1", email = "a@b.com", displayName = "A", avatarUrl = null)
    private val authResponse = AuthResponse(accessToken = "at", refreshToken = "rt", user = user)

    private fun buildRepo(
        api: ApiService = mockk(relaxed = true),
        storage: TokenStorage = mockk(relaxed = true),
        obs: Observability = mockk(relaxed = true)
    ) = AuthRepository(api, storage, obs)

    @Test
    fun `signIn success persists tokens, identifies user, flips to SignedIn`() = runTest {
        val api = mockk<ApiService>()
        val storage = mockk<TokenStorage>(relaxed = true)
        val obs = mockk<Observability>(relaxed = true)

        coEvery { api.login(LoginRequest("a@b.com", "hunter22")) } returns authResponse

        val repo = buildRepo(api, storage, obs)

        repo.state.test {
            assertEquals(AuthRepository.State.Unknown, awaitItem())

            val result = repo.signIn("a@b.com", "hunter22")
            assertTrue(result.isSuccess)
            assertEquals(AuthRepository.State.SignedIn(user), awaitItem())
        }

        coVerify { storage.save(accessToken = "at", refreshToken = "rt", userId = "u_1") }
        coVerify { obs.identify(userId = "u_1", email = "a@b.com") }
        coVerify { obs.track("auth.signed_in", any()) }
    }

    @Test
    fun `signIn failure captures error and keeps state Unknown`() = runTest {
        val api = mockk<ApiService>()
        val obs = mockk<Observability>(relaxed = true)
        coEvery { api.login(any()) } throws IllegalStateException("boom")

        val repo = buildRepo(api = api, obs = obs)
        val result = repo.signIn("a@b.com", "hunter22")

        assertTrue(result.isFailure)
        coVerify { obs.capture(match<Throwable> { it.message == "boom" }) }
    }

    @Test
    fun `signOut clears storage and identity`() = runTest {
        val storage = mockk<TokenStorage>(relaxed = true)
        val obs = mockk<Observability>(relaxed = true)

        val repo = buildRepo(storage = storage, obs = obs)
        repo.signOut()

        coVerify { storage.clear() }
        coVerify { obs.identify(userId = null, email = null) }
        coVerify { obs.track("auth.signed_out", any()) }
        assertEquals(AuthRepository.State.SignedOut, repo.state.value)
    }

    @Test
    fun `restore with no token goes to SignedOut`() = runTest {
        val storage = mockk<TokenStorage>(relaxed = true)
        coEvery { storage.accessToken() } returns null

        val repo = buildRepo(storage = storage)
        repo.restore()

        assertEquals(AuthRepository.State.SignedOut, repo.state.value)
    }

    @Test
    fun `restore with valid token hydrates user`() = runTest {
        val api = mockk<ApiService>()
        val storage = mockk<TokenStorage>(relaxed = true)
        val obs = mockk<Observability>(relaxed = true)

        coEvery { storage.accessToken() } returns "at"
        coEvery { api.me() } returns user

        val repo = buildRepo(api, storage, obs)
        repo.restore()

        assertEquals(AuthRepository.State.SignedIn(user), repo.state.value)
        coVerify { obs.identify(userId = "u_1", email = "a@b.com") }
    }

    @Test
    fun `restore with invalid token clears storage and signs out`() = runTest {
        val api = mockk<ApiService>()
        val storage = mockk<TokenStorage>(relaxed = true)

        coEvery { storage.accessToken() } returns "expired"
        coEvery { api.me() } throws RuntimeException("401")

        val repo = buildRepo(api = api, storage = storage)
        repo.restore()

        coVerify { storage.clear() }
        assertEquals(AuthRepository.State.SignedOut, repo.state.value)
    }
}
