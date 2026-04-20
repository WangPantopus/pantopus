package app.pantopus.android.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.auth.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
            val errorMessage: String? = null,
        ) {
            val canSubmit: Boolean
                get() = !isLoading && email.contains('@') && password.length >= 6
        }

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        fun onEmailChange(value: String) = _uiState.update { it.copy(email = value, errorMessage = null) }

        fun onPasswordChange(value: String) = _uiState.update { it.copy(password = value, errorMessage = null) }

        fun signIn() {
            val snapshot = _uiState.value
            if (!snapshot.canSubmit) return
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            viewModelScope.launch {
                authRepository.signIn(snapshot.email, snapshot.password)
                    .onFailure { e ->
                        _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Sign-in failed") }
                    }
                    .onSuccess {
                        _uiState.update { it.copy(isLoading = false) }
                    }
            }
        }
    }
