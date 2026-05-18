@file:Suppress("MagicNumber")

package app.pantopus.android.data.auth

import app.pantopus.android.data.api.ApiService
import app.pantopus.android.data.api.models.auth.AuthErrorBody
import app.pantopus.android.data.api.models.auth.AuthenticatedUser
import app.pantopus.android.data.api.models.auth.ForgotPasswordRequest
import app.pantopus.android.data.api.models.auth.LoginRequest
import app.pantopus.android.data.api.models.auth.RefreshRequest
import app.pantopus.android.data.api.models.auth.RegisterRequest
import app.pantopus.android.data.api.models.auth.ResendVerificationRequest
import app.pantopus.android.data.api.models.auth.ResetPasswordRequest
import app.pantopus.android.data.api.models.auth.VerifyEmailRequest
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.models.users.UserProfile
import app.pantopus.android.data.api.services.AuthApi
import app.pantopus.android.data.observability.Observability
import com.squareup.moshi.Moshi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Account type chosen at registration. Maps to the backend `account_type`
 * column. Persisted as `"individual"` (personal) or `"business"` per
 * `registerSchema` at `backend/routes/users.js:723`.
 */
enum class AccountType(val backendValue: String) {
    Personal("individual"),
    Business("business"),
}

/**
 * Typed error surface for the auth flows. Mapping rules mirror iOS
 * `AuthError`:
 *
 * - [InvalidCredentials] — login 401 (wrong email/password).
 * - [EmailAlreadyExists] — register 400 whose error message references
 *   "Email already registered" or "already registered".
 * - [WeakPassword] — register 400 whose error message references the
 *   password length policy.
 * - [NetworkError] — transport-level failure: offline, timeout, DNS.
 * - [RateLimited] — 429 from any auth endpoint.
 * - [ServerError] — 5xx or otherwise unrecoverable server reply; carries
 *   the backend's `error` field for diagnostics.
 * - [Unknown] — any other failure.
 */
sealed class AuthError(
    override val message: String,
    override val cause: Throwable? = null,
) : Throwable(message, cause) {
    data object InvalidCredentials : AuthError("Invalid email or password.")

    data object EmailAlreadyExists : AuthError("An account with this email already exists.")

    data object WeakPassword : AuthError("Choose a stronger password.")

    data object NetworkError : AuthError("Can't reach Pantopus. Check your connection.")

    data object RateLimited : AuthError("Too many attempts. Try again in a moment.")

    data class ServerError(
        val detail: String,
    ) : AuthError(detail)

    data object Unknown : AuthError("Something went wrong. Please try again.")
}

/**
 * Result of a successful `signUp` call. Carries the created user plus the
 * verify-email gating flag (Q4 soft-gate decision).
 */
data class SignUpResult(
    val user: AuthenticatedUser,
    val requiresEmailVerification: Boolean,
)

/**
 * Session state + login / logout orchestration.
 *
 * Exposes [state] as a StateFlow so any ViewModel can collect it. Tokens are
 * persisted via [TokenStorage]; see its docstring for the encryption caveat.
 */
@Singleton
class AuthRepository
    @Inject
    constructor(
        private val api: ApiService,
        private val authApi: AuthApi,
        private val tokenStorage: TokenStorage,
        private val observability: Observability,
    ) {
        /** Session state for the current user. */
        sealed interface State {
            /** Initial state before [restore] runs. */
            data object Unknown : State

            /** No session token; user must sign in. */
            data object SignedOut : State

            /** Session restored or freshly signed in. */
            data class SignedIn(
                val user: UserDto,
            ) : State
        }

        private val _state = MutableStateFlow<State>(State.Unknown)
        val state: StateFlow<State> = _state.asStateFlow()

        private val errorBodyAdapter = Moshi.Builder().build().adapter(AuthErrorBody::class.java)

        /** Called once at app start to hydrate session from persisted tokens. */
        suspend fun restore() {
            val token = tokenStorage.accessToken()
            if (token == null) {
                _state.value = State.SignedOut
                return
            }
            try {
                val profile = api.me().user
                val user = profile.toSessionUser()
                observability.identify(userId = user.id, email = user.email)
                _state.value = State.SignedIn(user)
            } catch (t: Throwable) {
                tokenStorage.clear()
                _state.value = State.SignedOut
            }
        }

        /** Sign the user in against `POST /api/users/login`. */
        suspend fun signIn(
            email: String,
            password: String,
        ): Result<UserDto> =
            runCatching {
                val response = api.login(LoginRequest(email = email, password = password))
                val user = response.user.toSessionUser()
                tokenStorage.save(
                    accessToken = response.accessToken.orEmpty(),
                    refreshToken = response.refreshToken,
                    userId = response.user.id,
                )
                observability.identify(userId = user.id, email = user.email)
                observability.track("auth.signed_in")
                _state.value = State.SignedIn(user)
                user
            }.onFailure { t ->
                if (t !is kotlin.coroutines.cancellation.CancellationException) observability.capture(t)
            }

        /**
         * `POST /api/users/register` (route `backend/routes/users.js:1177`).
         *
         * Does not sign the user in — caller routes to verify-email or to
         * login per the Q4 soft-gate decision. Throws [AuthError] on failure.
         */
        @Suppress("LongParameterList")
        suspend fun signUp(
            email: String,
            password: String,
            phoneNumber: String?,
            username: String,
            firstName: String,
            middleName: String?,
            lastName: String,
            dateOfBirth: String?,
            address: String?,
            city: String?,
            state: String?,
            zipcode: String?,
            accountType: AccountType,
            inviteCode: String?,
        ): SignUpResult {
            try {
                val response =
                    authApi.register(
                        RegisterRequest(
                            email = email,
                            password = password,
                            phoneNumber = phoneNumber,
                            username = username,
                            firstName = firstName,
                            middleName = middleName,
                            lastName = lastName,
                            dateOfBirth = dateOfBirth,
                            address = address,
                            city = city,
                            state = state,
                            zipcode = zipcode,
                            accountType = accountType.backendValue,
                            inviteCode = inviteCode,
                        ),
                    )
                observability.track("auth.signed_up")
                return SignUpResult(
                    user = response.user,
                    requiresEmailVerification = response.requiresEmailVerification ?: true,
                )
            } catch (t: Throwable) {
                throw mapRegisterError(t)
            }
        }

        /**
         * `POST /api/users/forgot-password` (route `backend/routes/users.js:3197`).
         * Backend always replies 200 with a generic message to prevent
         * email enumeration.
         */
        suspend fun forgotPassword(email: String) {
            try {
                authApi.forgotPassword(ForgotPasswordRequest(email = email))
                observability.track("auth.forgot_password_requested")
            } catch (t: Throwable) {
                throw mapGenericError(t)
            }
        }

        /**
         * `POST /api/users/reset-password` (route `backend/routes/users.js:3247`).
         * `token` is the hashed recovery token carried by the email link.
         */
        suspend fun resetPassword(
            token: String,
            newPassword: String,
        ) {
            try {
                authApi.resetPassword(ResetPasswordRequest(token = token, newPassword = newPassword))
                observability.track("auth.password_reset")
            } catch (t: Throwable) {
                throw mapResetPasswordError(t)
            }
        }

        /**
         * `POST /api/users/verify-email` (route `backend/routes/users.js:3115`).
         * Sends the hashed Supabase OTP. Backend revokes the just-issued
         * session — verifying does NOT sign the user in.
         */
        suspend fun verifyEmail(token: String) {
            try {
                authApi.verifyEmail(VerifyEmailRequest(tokenHash = token))
                observability.track("auth.email_verified")
            } catch (t: Throwable) {
                throw mapVerifyEmailError(t)
            }
        }

        /**
         * `POST /api/users/resend-verification` (route `backend/routes/users.js:3049`).
         * Like forgot-password, always 200 with a generic message.
         */
        suspend fun resendVerification(email: String) {
            try {
                authApi.resendVerification(ResendVerificationRequest(email = email))
                observability.track("auth.verification_resent")
            } catch (t: Throwable) {
                throw mapGenericError(t)
            }
        }

        /**
         * `POST /api/users/refresh` (route `backend/routes/users.js:1910`).
         * On success, rotates the stored access (and optionally refresh)
         * token in place. The stored userId is not touched. On failure,
         * signs out and rethrows.
         */
        suspend fun refreshSession() {
            val stored = tokenStorage.refreshToken()
            try {
                val response = authApi.refresh(RefreshRequest(refreshToken = stored))
                if (!response.accessToken.isNullOrEmpty()) {
                    tokenStorage.updateTokens(
                        accessToken = response.accessToken,
                        refreshToken = response.refreshToken,
                    )
                }
            } catch (t: Throwable) {
                signOut()
                throw mapGenericError(t)
            }
        }

        /** Clear local tokens and flip state to signed-out. */
        suspend fun signOut() {
            tokenStorage.clear()
            observability.identify(userId = null)
            observability.track("auth.signed_out")
            _state.value = State.SignedOut
        }

        // MARK: - Error mapping

        private fun mapRegisterError(t: Throwable): AuthError {
            return when (t) {
                is IOException -> AuthError.NetworkError
                is HttpException -> {
                    val status = t.code()
                    if (status == 429) return AuthError.RateLimited
                    val raw = t.response()?.errorBody()?.string().orEmpty()
                    val message = extractMessage(raw) ?: raw
                    when {
                        message.contains("already registered", ignoreCase = true) ||
                            message.contains("Email already", ignoreCase = true) -> AuthError.EmailAlreadyExists
                        message.contains("password", ignoreCase = true) -> AuthError.WeakPassword
                        status >= 500 -> AuthError.ServerError(message.ifBlank { "Server error $status." })
                        else -> AuthError.ServerError(message.ifBlank { "Request failed ($status)." })
                    }
                }
                else -> AuthError.Unknown
            }
        }

        private fun mapResetPasswordError(t: Throwable): AuthError {
            return when (t) {
                is IOException -> AuthError.NetworkError
                is HttpException -> {
                    val status = t.code()
                    if (status == 429) return AuthError.RateLimited
                    val raw = t.response()?.errorBody()?.string().orEmpty()
                    val message = extractMessage(raw) ?: raw
                    when {
                        message.contains("password", ignoreCase = true) &&
                            !message.contains("Invalid or expired", ignoreCase = true) -> AuthError.WeakPassword
                        status >= 500 -> AuthError.ServerError(message.ifBlank { "Server error $status." })
                        else -> AuthError.ServerError(message.ifBlank { "Request failed ($status)." })
                    }
                }
                else -> AuthError.Unknown
            }
        }

        private fun mapVerifyEmailError(t: Throwable): AuthError {
            return when (t) {
                is IOException -> AuthError.NetworkError
                is HttpException -> {
                    val status = t.code()
                    if (status == 429) return AuthError.RateLimited
                    val raw = t.response()?.errorBody()?.string().orEmpty()
                    val message = extractMessage(raw) ?: raw
                    if (status >= 500) {
                        AuthError.ServerError(message.ifBlank { "Server error $status." })
                    } else {
                        AuthError.ServerError(message.ifBlank { "Request failed ($status)." })
                    }
                }
                else -> AuthError.Unknown
            }
        }

        private fun mapGenericError(t: Throwable): AuthError {
            return when (t) {
                is IOException -> AuthError.NetworkError
                is HttpException -> {
                    val status = t.code()
                    val raw = t.response()?.errorBody()?.string().orEmpty()
                    val message = extractMessage(raw) ?: raw
                    when {
                        status == 401 -> AuthError.InvalidCredentials
                        status == 429 -> AuthError.RateLimited
                        status >= 500 -> AuthError.ServerError(message.ifBlank { "Server error $status." })
                        else -> AuthError.ServerError(message.ifBlank { "Request failed ($status)." })
                    }
                }
                else -> AuthError.Unknown
            }
        }

        private fun extractMessage(body: String): String? {
            if (body.isBlank()) return null
            return runCatching { errorBodyAdapter.fromJson(body)?.error }.getOrNull()
        }
    }

/** Projection of [AuthenticatedUser] → the compact [UserDto] used in session state. */
private fun AuthenticatedUser.toSessionUser(): UserDto =
    UserDto(
        id = id,
        email = email,
        displayName = name.takeIf { it.isNotEmpty() },
        avatarUrl = null,
        isAdmin = role == "admin",
    )

/** Projection of [UserProfile] → the compact [UserDto] used in session state. */
private fun UserProfile.toSessionUser(): UserDto =
    UserDto(
        id = id,
        email = email,
        displayName = name.takeIf { it.isNotEmpty() },
        avatarUrl = avatarUrl ?: profilePictureUrl,
        isAdmin = role == "admin",
    )
