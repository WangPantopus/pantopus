package app.pantopus.android.data.auth

import app.cash.turbine.test
import app.pantopus.android.data.api.ApiService
import app.pantopus.android.data.api.models.auth.AuthMessageResponse
import app.pantopus.android.data.api.models.auth.AuthenticatedUser
import app.pantopus.android.data.api.models.auth.ForgotPasswordRequest
import app.pantopus.android.data.api.models.auth.LoginRequest
import app.pantopus.android.data.api.models.auth.LoginResponse
import app.pantopus.android.data.api.models.auth.RefreshRequest
import app.pantopus.android.data.api.models.auth.RefreshResponse
import app.pantopus.android.data.api.models.auth.RegisterRequest
import app.pantopus.android.data.api.models.auth.RegisterResponse
import app.pantopus.android.data.api.models.auth.ResendVerificationRequest
import app.pantopus.android.data.api.models.auth.ResetPasswordRequest
import app.pantopus.android.data.api.models.auth.VerifyEmailRequest
import app.pantopus.android.data.api.models.auth.VerifyEmailResponse
import app.pantopus.android.data.api.models.users.ProfileResponse
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.models.users.UserProfile
import app.pantopus.android.data.api.services.AuthApi
import app.pantopus.android.data.observability.Observability
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

@OptIn(ExperimentalCoroutinesApi::class)
class AuthRepositoryTest {
    private val sessionUser =
        UserDto(
            id = "u_1",
            email = "a@b.com",
            displayName = "Alice Doe",
            avatarUrl = null,
            username = "alice",
        )

    private val authUser =
        AuthenticatedUser(
            id = "u_1",
            email = "a@b.com",
            username = "alice",
            name = "Alice Doe",
            firstName = "Alice",
            middleName = null,
            lastName = "Doe",
            phoneNumber = null,
            address = null,
            city = null,
            state = null,
            zipcode = null,
            accountType = "personal",
            role = "member",
            verified = true,
            createdAt = "2025-01-01T00:00:00Z",
        )

    private val loginResponse =
        LoginResponse(
            message = "ok",
            accessToken = "at",
            refreshToken = "rt",
            expiresIn = 3600,
            expiresAt = 1_800_000_000,
            user = authUser,
        )

    private val profile =
        UserProfile(
            id = "u_1",
            email = "a@b.com",
            username = "alice",
            firstName = "Alice",
            middleName = null,
            lastName = "Doe",
            name = "Alice Doe",
            phoneNumber = null,
            dateOfBirth = null,
            address = null,
            city = null,
            state = null,
            zipcode = null,
            accountType = "personal",
            role = "member",
            verified = true,
            residency = null,
            avatarUrl = null,
            profilePictureUrl = null,
            profilePicture = null,
            bio = null,
            tagline = null,
            socialLinks = null,
            skills = null,
            followersCount = null,
            averageRating = null,
            gigsPosted = null,
            gigsCompleted = null,
            profileVisibility = null,
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2025-01-01T00:00:00Z",
        )

    private fun buildRepo(
        api: ApiService = mockk(relaxed = true),
        authApi: AuthApi = mockk(relaxed = true),
        storage: TokenStorage = mockk(relaxed = true),
        obs: Observability = mockk(relaxed = true),
    ) = AuthRepository(api, authApi, storage, obs)

    private fun httpException(
        code: Int,
        body: String,
    ): HttpException =
        HttpException(
            Response.error<Any>(code, body.toResponseBody("application/json".toMediaTypeOrNull())),
        )

    @Test
    fun `signIn success persists tokens, identifies user, flips to SignedIn`() =
        runTest {
            val api = mockk<ApiService>()
            val storage = mockk<TokenStorage>(relaxed = true)
            val obs = mockk<Observability>(relaxed = true)

            coEvery { api.login(LoginRequest("a@b.com", "hunter22")) } returns loginResponse

            val repo = buildRepo(api = api, storage = storage, obs = obs)

            repo.state.test {
                assertEquals(AuthRepository.State.Unknown, awaitItem())

                val result = repo.signIn("a@b.com", "hunter22")
                assertTrue(result.isSuccess)
                assertEquals(AuthRepository.State.SignedIn(sessionUser), awaitItem())
            }

            coVerify { storage.save(accessToken = "at", refreshToken = "rt", userId = "u_1") }
            coVerify { obs.identify(userId = "u_1", email = "a@b.com") }
            coVerify { obs.track("auth.signed_in", any()) }
        }

    @Test
    fun `signIn failure captures error and keeps state Unknown`() =
        runTest {
            val api = mockk<ApiService>()
            val obs = mockk<Observability>(relaxed = true)
            coEvery { api.login(any()) } throws IllegalStateException("boom")

            val repo = buildRepo(api = api, obs = obs)
            val result = repo.signIn("a@b.com", "hunter22")

            assertTrue(result.isFailure)
            coVerify { obs.capture(match<Throwable> { it.message == "boom" }) }
        }

    @Test
    fun `signOut clears storage and identity`() =
        runTest {
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
    fun `restore with no token goes to SignedOut`() =
        runTest {
            val storage = mockk<TokenStorage>(relaxed = true)
            coEvery { storage.accessToken() } returns null

            val repo = buildRepo(storage = storage)
            repo.restore()

            assertEquals(AuthRepository.State.SignedOut, repo.state.value)
        }

    @Test
    fun `restore with valid token hydrates user`() =
        runTest {
            val api = mockk<ApiService>()
            val storage = mockk<TokenStorage>(relaxed = true)
            val obs = mockk<Observability>(relaxed = true)

            coEvery { storage.accessToken() } returns "at"
            coEvery { api.me() } returns ProfileResponse(user = profile, inviteProgress = null)

            val repo = buildRepo(api = api, storage = storage, obs = obs)
            repo.restore()

            assertEquals(AuthRepository.State.SignedIn(sessionUser), repo.state.value)
            coVerify { obs.identify(userId = "u_1", email = "a@b.com") }
        }

    @Test
    fun `restore with invalid token clears storage and signs out`() =
        runTest {
            val api = mockk<ApiService>()
            val storage = mockk<TokenStorage>(relaxed = true)

            coEvery { storage.accessToken() } returns "expired"
            coEvery { api.me() } throws RuntimeException("401")

            val repo = buildRepo(api = api, storage = storage)
            repo.restore()

            coVerify { storage.clear() }
            assertEquals(AuthRepository.State.SignedOut, repo.state.value)
        }

    // ───────────────────────── T6.1a additions ─────────────────────────

    @Test
    fun `signUp success returns SignUpResult with requiresEmailVerification`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val response =
                RegisterResponse(
                    message = "Registration successful.",
                    requiresEmailVerification = true,
                    user = authUser.copy(id = "u_new", verified = false),
                )
            coEvery { authApi.register(any()) } returns response

            val repo = buildRepo(authApi = authApi)
            val result =
                repo.signUp(
                    email = "new@example.com",
                    password = "strongpass123",
                    phoneNumber = null,
                    username = "newuser",
                    firstName = "New",
                    middleName = null,
                    lastName = "User",
                    dateOfBirth = null,
                    address = null,
                    city = null,
                    state = null,
                    zipcode = null,
                    accountType = AccountType.Personal,
                    inviteCode = null,
                )

            assertEquals("u_new", result.user.id)
            assertTrue(result.requiresEmailVerification)
            // Does not flip session state.
            assertEquals(AuthRepository.State.Unknown, repo.state.value)
            coVerify {
                authApi.register(
                    match<RegisterRequest> { it.accountType == "individual" && it.email == "new@example.com" },
                )
            }
        }

    @Test
    fun `signUp 400 with already registered maps to EmailAlreadyExists`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.register(any()) } throws
                httpException(400, "{\"error\":\"Email already registered\"}")

            val repo = buildRepo(authApi = authApi)
            try {
                repo.signUp(
                    email = "taken@example.com",
                    password = "strongpass123",
                    phoneNumber = null,
                    username = "u",
                    firstName = "T",
                    middleName = null,
                    lastName = "U",
                    dateOfBirth = null,
                    address = null,
                    city = null,
                    state = null,
                    zipcode = null,
                    accountType = AccountType.Personal,
                    inviteCode = null,
                )
                throw AssertionError("Expected throw")
            } catch (e: AuthError) {
                assertEquals(AuthError.EmailAlreadyExists, e)
            }
        }

    @Test
    fun `forgotPassword success returns Unit`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.forgotPassword(any()) } returns AuthMessageResponse(message = "ok")

            val repo = buildRepo(authApi = authApi)
            repo.forgotPassword("a@b.com")

            coVerify { authApi.forgotPassword(ForgotPasswordRequest(email = "a@b.com")) }
        }

    @Test
    fun `forgotPassword 5xx maps to ServerError`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.forgotPassword(any()) } throws
                httpException(503, "{\"error\":\"unavailable\"}")

            val repo = buildRepo(authApi = authApi)
            try {
                repo.forgotPassword("a@b.com")
                throw AssertionError("Expected throw")
            } catch (e: AuthError) {
                assertTrue("Expected ServerError, got $e", e is AuthError.ServerError)
            }
        }

    @Test
    fun `resetPassword 400 invalid token maps to ServerError`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.resetPassword(any()) } throws
                httpException(400, "{\"error\":\"Invalid or expired reset token\"}")

            val repo = buildRepo(authApi = authApi)
            try {
                repo.resetPassword("stale", "newstrong123")
                throw AssertionError("Expected throw")
            } catch (e: AuthError) {
                assertTrue("Expected ServerError, got $e", e is AuthError.ServerError)
                assertEquals("Invalid or expired reset token", (e as AuthError.ServerError).detail)
            }
        }

    @Test
    fun `resetPassword success calls API with token and newPassword`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.resetPassword(any()) } returns AuthMessageResponse(message = "ok")

            val repo = buildRepo(authApi = authApi)
            repo.resetPassword("hash", "newstrong123")

            coVerify { authApi.resetPassword(ResetPasswordRequest(token = "hash", newPassword = "newstrong123")) }
        }

    @Test
    fun `verifyEmail success`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.verifyEmail(any()) } returns
                VerifyEmailResponse(message = "ok", verified = true)

            val repo = buildRepo(authApi = authApi)
            repo.verifyEmail("hashed-token")

            coVerify { authApi.verifyEmail(VerifyEmailRequest(tokenHash = "hashed-token")) }
        }

    @Test
    fun `verifyEmail 400 maps to ServerError`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.verifyEmail(any()) } throws
                httpException(400, "{\"error\":\"Invalid or expired verification link/code\"}")

            val repo = buildRepo(authApi = authApi)
            try {
                repo.verifyEmail("bad")
                throw AssertionError("Expected throw")
            } catch (e: AuthError) {
                assertTrue("Expected ServerError, got $e", e is AuthError.ServerError)
            }
        }

    @Test
    fun `resendVerification success`() =
        runTest {
            val authApi = mockk<AuthApi>()
            coEvery { authApi.resendVerification(any()) } returns AuthMessageResponse(message = "ok")

            val repo = buildRepo(authApi = authApi)
            repo.resendVerification("a@b.com")

            coVerify { authApi.resendVerification(ResendVerificationRequest(email = "a@b.com")) }
        }

    @Test
    fun `refreshSession success rotates tokens without touching userId`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val storage = mockk<TokenStorage>(relaxed = true)
            coEvery { storage.refreshToken() } returns "rt-current"
            coEvery { authApi.refresh(any()) } returns
                RefreshResponse(
                    ok = true,
                    accessToken = "new-at",
                    refreshToken = "new-rt",
                    expiresIn = 3600,
                    expiresAt = 1_800_000_000,
                )

            val repo = buildRepo(authApi = authApi, storage = storage)
            repo.refreshSession()

            coVerify { authApi.refresh(RefreshRequest(refreshToken = "rt-current")) }
            coVerify { storage.updateTokens(accessToken = "new-at", refreshToken = "new-rt") }
        }

    @Test
    fun `refreshSession failure signs out and rethrows`() =
        runTest {
            val authApi = mockk<AuthApi>()
            val storage = mockk<TokenStorage>(relaxed = true)
            coEvery { storage.refreshToken() } returns "stale"
            coEvery { authApi.refresh(any()) } throws
                httpException(401, "{\"error\":\"Session expired\"}")

            val repo = buildRepo(authApi = authApi, storage = storage)
            try {
                repo.refreshSession()
                throw AssertionError("Expected throw")
            } catch (e: AuthError) {
                assertEquals(AuthError.InvalidCredentials, e)
            }
            assertEquals(AuthRepository.State.SignedOut, repo.state.value)
            coVerify { storage.clear() }
        }
}
