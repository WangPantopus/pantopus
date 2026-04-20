package app.pantopus.android.data.auth

import app.pantopus.android.data.api.ApiService
import app.pantopus.android.data.api.models.LoginRequest
import app.pantopus.android.data.api.models.UserDto
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
class AuthRepository @Inject constructor(
    private val api: ApiService,
    private val tokenStorage: TokenStorage,
    private val observability: Observability
) {
    sealed interface State {
        data object Unknown : State
        data object SignedOut : State
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
            val user = api.me()
            observability.identify(userId = user.id, email = user.email)
            _state.value = State.SignedIn(user)
        } catch (t: Throwable) {
            tokenStorage.clear()
            _state.value = State.SignedOut
        }
    }

    suspend fun signIn(email: String, password: String): Result<UserDto> = runCatching {
        val response = api.login(LoginRequest(email = email, password = password))
        tokenStorage.save(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            userId = response.user.id
        )
        observability.identify(userId = response.user.id, email = response.user.email)
        observability.track("auth.signed_in")
        _state.value = State.SignedIn(response.user)
        response.user
    }.onFailure { t ->
        if (t !is kotlin.coroutines.cancellation.CancellationException) observability.capture(t)
    }

    suspend fun signOut() {
        tokenStorage.clear()
        observability.identify(userId = null)
        observability.track("auth.signed_out")
        _state.value = State.SignedOut
    }
}
