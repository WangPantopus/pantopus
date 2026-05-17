@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.password

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.account.AccountRepository
import app.pantopus.android.data.api.models.settings.PasswordUpdateBody
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * P8 / T6.2c — Settings → Password.
 *
 * Three-field form: current password (only when the account already
 * has a password — discovered via `GET /api/users/auth-methods`),
 * new password, confirm new password. Submit calls
 * `POST /api/users/password` (users.js:1771).
 *
 * Validation:
 * - current required when `hasPassword == true`
 * - new ≥ [MIN_LENGTH] chars
 * - new ≠ current (server also enforces; we catch early for clarity)
 * - confirm == new
 */
@HiltViewModel
class PasswordChangeViewModel
    @Inject
    constructor(
        private val account: AccountRepository,
    ) : ViewModel() {
        sealed interface FormState {
            data object Loading : FormState

            data object Ready : FormState

            data class Error(val message: String) : FormState
        }

        enum class FieldKey { Current, New, Confirm }

        private val _formState = MutableStateFlow<FormState>(FormState.Loading)
        val formState: StateFlow<FormState> = _formState.asStateFlow()

        private val _hasPassword = MutableStateFlow(true)
        val hasPassword: StateFlow<Boolean> = _hasPassword.asStateFlow()

        private val _isSaving = MutableStateFlow(false)
        val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

        private val _fields =
            MutableStateFlow(
                mapOf(
                    FieldKey.Current to FormFieldState(id = "current"),
                    FieldKey.New to FormFieldState(id = "new"),
                    FieldKey.Confirm to FormFieldState(id = "confirm"),
                ),
            )
        val fields: StateFlow<Map<FieldKey, FormFieldState>> = _fields.asStateFlow()

        private val _toast = MutableStateFlow<String?>(null)
        val toast: StateFlow<String?> = _toast.asStateFlow()

        private val _shouldDismiss = MutableStateFlow(false)
        val shouldDismiss: StateFlow<Boolean> = _shouldDismiss.asStateFlow()

        val requiresCurrent: Boolean get() = _hasPassword.value

        val isDirty: Boolean
            get() {
                val map = _fields.value
                val newValue = map[FieldKey.New]?.value.orEmpty()
                val confirmValue = map[FieldKey.Confirm]?.value.orEmpty()
                val currentValue = map[FieldKey.Current]?.value.orEmpty()
                return newValue.isNotEmpty() || confirmValue.isNotEmpty() ||
                    (requiresCurrent && currentValue.isNotEmpty())
            }

        val isValid: Boolean
            get() {
                val map = _fields.value
                val newValue = map[FieldKey.New]?.value.orEmpty()
                val confirmValue = map[FieldKey.Confirm]?.value.orEmpty()
                val currentValue = map[FieldKey.Current]?.value.orEmpty()
                if (newValue.length < MIN_LENGTH) return false
                if (confirmValue != newValue) return false
                if (requiresCurrent && currentValue.isEmpty()) return false
                if (requiresCurrent && currentValue == newValue) return false
                return true
            }

        fun load() {
            _formState.value = FormState.Loading
            viewModelScope.launch {
                when (val result = account.authMethods()) {
                    is NetworkResult.Success -> _hasPassword.value = result.data.hasPassword ?: true
                    is NetworkResult.Failure -> _hasPassword.value = true
                }
                _formState.value = FormState.Ready
            }
        }

        fun update(
            key: FieldKey,
            value: String,
        ) {
            val previous = _fields.value[key] ?: FormFieldState(id = key.name.lowercase())
            val withNewValue = previous.copy(value = value, touched = true)
            val withError = withNewValue.copy(error = inlineError(key, value))
            val next = _fields.value.toMutableMap()
            next[key] = withError
            if (key == FieldKey.New) {
                next[FieldKey.Confirm]?.let { confirm ->
                    if (confirm.touched) {
                        next[FieldKey.Confirm] =
                            confirm.copy(error = inlineErrorWith(FieldKey.Confirm, confirm.value, value))
                    }
                }
            }
            _fields.value = next
        }

        fun save() {
            if (!isValid || _isSaving.value) return
            val map = _fields.value
            val currentValue = map[FieldKey.Current]?.value.orEmpty()
            val newValue = map[FieldKey.New]?.value.orEmpty()
            _isSaving.value = true
            viewModelScope.launch {
                val body =
                    PasswordUpdateBody(
                        currentPassword = if (requiresCurrent) currentValue else null,
                        newPassword = newValue,
                    )
                when (val result = account.updatePassword(body)) {
                    is NetworkResult.Success -> {
                        _toast.value = "Password updated"
                        _shouldDismiss.value = true
                    }
                    is NetworkResult.Failure -> mapServerError(result.error)
                }
                _isSaving.value = false
            }
        }

        fun acknowledgeDismiss() {
            _shouldDismiss.value = false
        }

        private fun inlineError(
            key: FieldKey,
            value: String,
        ): String? =
            when (key) {
                FieldKey.Current ->
                    if (requiresCurrent && value.isEmpty()) "Required" else null
                FieldKey.New -> {
                    val current = _fields.value[FieldKey.Current]?.value.orEmpty()
                    when {
                        value.length < MIN_LENGTH -> "At least $MIN_LENGTH characters"
                        requiresCurrent && current.isNotEmpty() && value == current ->
                            "Choose something different from your current password"
                        else -> null
                    }
                }
                FieldKey.Confirm -> {
                    val newValue = _fields.value[FieldKey.New]?.value.orEmpty()
                    if (value != newValue) "Doesn't match" else null
                }
            }

        private fun inlineErrorWith(
            key: FieldKey,
            value: String,
            newValue: String,
        ): String? {
            if (key != FieldKey.Confirm) return inlineError(key, value)
            return if (value != newValue) "Doesn't match" else null
        }

        private fun markError(
            key: FieldKey,
            message: String,
        ) {
            val map = _fields.value.toMutableMap()
            val previous = map[key] ?: FormFieldState(id = key.name.lowercase())
            map[key] = previous.copy(error = message)
            _fields.value = map
        }

        private fun mapServerError(error: NetworkError) {
            when (error) {
                is NetworkError.Unauthorized -> markError(FieldKey.Current, "Current password is incorrect")
                is NetworkError.ClientError -> markError(FieldKey.New, error.body ?: "Couldn't update your password")
                is NetworkError.NotFound -> markError(FieldKey.New, "We couldn't find your account. Try signing back in.")
                else -> markError(FieldKey.New, "Couldn't update your password. Try again.")
            }
        }

        companion object {
            const val MIN_LENGTH: Int = 8
        }
    }
