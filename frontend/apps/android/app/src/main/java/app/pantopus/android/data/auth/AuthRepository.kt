package app.pantopus.android.data.auth

import app.pantopus.android.data.api.ApiService
import app.pantopus.android.data.api.models.auth.AuthenticatedUser
import app.pantopus.android.data.api.models.auth.LoginRequest
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.models.users.UserProfile
import app.pantopus.android.data.observability.Observability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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
            data class SignedIn(val user: UserDto) : State
        }

        private val _state = MutableStateFlow<State>(State.Unknown)
        val state: StateFlow<State> = _state.asStateFlow()

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

        /** Clear local tokens and flip state to signed-out. */
        suspend fun signOut() {
            tokenStorage.clear()
            observability.identify(userId = null)
            observability.track("auth.signed_out")
            _state.value = State.SignedOut
        }
    }

/** Projection of [AuthenticatedUser] → the compact [UserDto] used in session state. */
private fun AuthenticatedUser.toSessionUser(): UserDto =
    UserDto(
        id = id,
        email = email,
        displayName = name.takeIf { it.isNotEmpty() },
        avatarUrl = null,
    )

/** Projection of [UserProfile] → the compact [UserDto] used in session state. */
private fun UserProfile.toSessionUser(): UserDto =
    UserDto(
        id = id,
        email = email,
        displayName = name.takeIf { it.isNotEmpty() },
        avatarUrl = avatarUrl ?: profilePictureUrl,
    )
