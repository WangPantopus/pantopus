@file:Suppress("MagicNumber")

package app.pantopus.android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.auth.AuthError
import app.pantopus.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

@HiltViewModel
class LoginViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        data class UiState(
            val email: String = "",
            val password: String = "",
            val isLoading: Boolean = false,
            /**
             * Typed error so the redesigned banner can render its own
             * headline + body. `null` when no error is currently surfaced.
             */
            val errorMessage: AuthError? = null,
        ) {
            val canSubmit: Boolean
                get() = !isLoading && AuthValidation.email(email) == null && password.length >= 6
        }

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }

        fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }

        fun clearError() = _uiState.update { it.copy(errorMessage = null) }

        fun signIn() {
            val snapshot = _uiState.value
            if (!snapshot.canSubmit) return
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                authRepository
                    .signIn(snapshot.email.trim().lowercase(), snapshot.password)
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = mapLoginError(e)) }
                    }.onSuccess {
                        _uiState.update { it.copy(isLoading = false) }
                    }
            }
        }

        /**
         * `AuthRepository.signIn` returns a generic `Result<UserDto>` — we
         * map the raw failure to a typed [AuthError] here so the screen
         * banner can render headline + body. Mirrors the iOS
         * `AuthManager.mapSignInError`.
         */
        private fun mapLoginError(error: Throwable): AuthError =
            when {
                error is AuthError -> error
                error is HttpException && error.code() == 401 -> AuthError.InvalidCredentials
                error is HttpException && error.code() == 403 ->
                    AuthError.ServerError(
                        "Please verify your email before signing in.",
                    )
                error is HttpException && error.code() == 429 -> AuthError.RateLimited
                error is HttpException && error.code() in 500..599 ->
                    AuthError.ServerError("Server error ${error.code()}.")
                error is IOException -> AuthError.NetworkError
                else -> AuthError.Unknown
            }
    }
