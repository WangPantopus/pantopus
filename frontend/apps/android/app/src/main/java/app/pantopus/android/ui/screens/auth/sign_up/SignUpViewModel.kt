@file:Suppress("PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.auth.sign_up

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.auth.AccountType
import app.pantopus.android.data.auth.AuthError
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.ui.screens.auth.AuthValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Two-segment Personal / Business picker choice. */
enum class SignUpAccountTypeChoice {
    Personal,
    Business,
    ;

    val label: String
        get() =
            when (this) {
                Personal -> "Personal"
                Business -> "Business"
            }

    val asAccountType: AccountType
        get() =
            when (this) {
                Personal -> AccountType.Personal
                Business -> AccountType.Business
            }
}

/** Fields tracked by the signup form. */
enum class SignUpField {
    Email,
    Password,
    ConfirmPassword,
    Username,
    FirstName,
    MiddleName,
    LastName,
    DateOfBirth,
    PhoneNumber,
    Address,
    City,
    State,
    Zipcode,
    InviteCode,
}

/**
 * Form values + lifecycle for the Create-account surface. Mirrors iOS
 * `SignUpViewModel` 1:1 — same field set, same validators (`AuthValidation`),
 * same success / failure model.
 */
@HiltViewModel
class SignUpViewModel
    @Inject
    constructor(
        private val authRepository: AuthRepository,
    ) : ViewModel() {
        data class UiState(
            val email: String = "",
            val password: String = "",
            val confirmPassword: String = "",
            val username: String = "",
            val firstName: String = "",
            val middleName: String = "",
            val lastName: String = "",
            val dateOfBirth: LocalDate? = null,
            val phoneNumber: String = "",
            val address: String = "",
            val city: String = "",
            val state: String = "",
            val zipcode: String = "",
            val accountType: SignUpAccountTypeChoice = SignUpAccountTypeChoice.Personal,
            val inviteCode: String = "",
            val agreedToTerms: Boolean = false,
            val fieldErrors: Map<SignUpField, String> = emptyMap(),
            val hasAttemptedSubmit: Boolean = false,
            val isSubmitting: Boolean = false,
            val topLevelError: AuthError? = null,
            val didSucceed: Boolean = false,
        ) {
            val passwordStrength: Int get() = AuthValidation.passwordStrength(password)

            val passwordStrengthLabel: String get() =
                when (passwordStrength) {
                    1 -> "Weak"
                    2 -> "Fair"
                    3 -> "Strong"
                    else -> "—"
                }

            val isValid: Boolean get() =
                agreedToTerms && SignUpField.values().all { validate(it) == null }

            fun validate(field: SignUpField): String? =
                when (field) {
                    SignUpField.Email -> AuthValidation.email(email)
                    SignUpField.Password -> AuthValidation.password(password)
                    SignUpField.ConfirmPassword ->
                        when {
                            confirmPassword.isEmpty() -> "Confirm your password."
                            confirmPassword != password -> "Passwords don't match."
                            else -> null
                        }
                    SignUpField.Username -> AuthValidation.username(username)
                    SignUpField.FirstName ->
                        if (firstName.trim().isEmpty()) "First name is required." else null
                    SignUpField.LastName ->
                        if (lastName.trim().isEmpty()) "Last name is required." else null
                    SignUpField.MiddleName -> null
                    SignUpField.DateOfBirth -> AuthValidation.dateOfBirth(dateOfBirth)
                    SignUpField.PhoneNumber -> AuthValidation.phoneOptional(phoneNumber)
                    SignUpField.Address -> {
                        val trimmed = address.trim()
                        when {
                            trimmed.isEmpty() -> "Address is required."
                            trimmed.length < 5 -> "Address must be at least 5 characters."
                            else -> null
                        }
                    }
                    SignUpField.City -> {
                        val trimmed = city.trim()
                        when {
                            trimmed.isEmpty() -> "City is required."
                            trimmed.length < 2 -> "City must be at least 2 characters."
                            else -> null
                        }
                    }
                    SignUpField.State -> {
                        val trimmed = state.trim()
                        when {
                            trimmed.isEmpty() -> "State is required."
                            trimmed.length < 2 -> "State must be at least 2 characters."
                            else -> null
                        }
                    }
                    SignUpField.Zipcode -> {
                        val trimmed = zipcode.trim()
                        when {
                            trimmed.isEmpty() -> "ZIP is required."
                            trimmed.length < 3 -> "ZIP must be at least 3 characters."
                            else -> null
                        }
                    }
                    SignUpField.InviteCode -> null
                }

            fun validateAll(): Map<SignUpField, String> =
                buildMap {
                    SignUpField.values().forEach { field ->
                        validate(field)?.let { put(field, it) }
                    }
                }
        }

        private val _uiState = MutableStateFlow(UiState())
        val uiState: StateFlow<UiState> = _uiState.asStateFlow()

        private fun update(transform: (UiState) -> UiState) {
            _uiState.update(transform)
        }

        // Field setters used by the screen.
        fun onEmailChange(value: String) = update { it.copy(email = value, fieldErrors = it.fieldErrors - SignUpField.Email, topLevelError = null) }

        fun onPasswordChange(value: String) = update { it.copy(password = value, fieldErrors = it.fieldErrors - SignUpField.Password) }

        fun onConfirmPasswordChange(value: String) = update { it.copy(confirmPassword = value, fieldErrors = it.fieldErrors - SignUpField.ConfirmPassword) }

        fun onUsernameChange(value: String) = update { it.copy(username = value, fieldErrors = it.fieldErrors - SignUpField.Username) }

        fun onFirstNameChange(value: String) = update { it.copy(firstName = value, fieldErrors = it.fieldErrors - SignUpField.FirstName) }

        fun onMiddleNameChange(value: String) = update { it.copy(middleName = value) }

        fun onLastNameChange(value: String) = update { it.copy(lastName = value, fieldErrors = it.fieldErrors - SignUpField.LastName) }

        fun onDateOfBirthChange(value: LocalDate?) = update { it.copy(dateOfBirth = value, fieldErrors = it.fieldErrors - SignUpField.DateOfBirth) }

        fun onPhoneChange(value: String) = update { it.copy(phoneNumber = value, fieldErrors = it.fieldErrors - SignUpField.PhoneNumber) }

        fun onAddressChange(value: String) = update { it.copy(address = value, fieldErrors = it.fieldErrors - SignUpField.Address) }

        fun onCityChange(value: String) = update { it.copy(city = value, fieldErrors = it.fieldErrors - SignUpField.City) }

        fun onStateChange(value: String) = update { it.copy(state = value, fieldErrors = it.fieldErrors - SignUpField.State) }

        fun onZipcodeChange(value: String) = update { it.copy(zipcode = value, fieldErrors = it.fieldErrors - SignUpField.Zipcode) }

        fun onAccountTypeChange(value: SignUpAccountTypeChoice) = update { it.copy(accountType = value) }

        fun onInviteCodeChange(value: String) = update { it.copy(inviteCode = value) }

        fun onTermsToggle() = update { it.copy(agreedToTerms = !it.agreedToTerms) }

        fun clearTopLevelError() = update { it.copy(topLevelError = null) }

        fun acknowledgeSuccess() = update { it.copy(didSucceed = false) }

        /**
         * Runs validation, then submits to `AuthRepository.signUp`. On
         * success flips `didSucceed = true` so the screen can route to the
         * verify-email surface (per the Q4 + backend-gap analysis in
         * `docs/mobile/auth-backend-contracts.md`).
         */
        fun submit() {
            val snapshot = _uiState.value
            val errors = snapshot.validateAll()
            update { it.copy(fieldErrors = errors, topLevelError = null, hasAttemptedSubmit = true) }
            if (errors.isNotEmpty() || !snapshot.agreedToTerms) return

            update { it.copy(isSubmitting = true) }
            viewModelScope.launch {
                try {
                    authRepository.signUp(
                        email = snapshot.email.trim().lowercase(),
                        password = snapshot.password,
                        phoneNumber = snapshot.phoneNumber.ifBlank { null },
                        username = snapshot.username.trim().lowercase(),
                        firstName = snapshot.firstName.trim(),
                        middleName = snapshot.middleName.ifBlank { null }?.trim(),
                        lastName = snapshot.lastName.trim(),
                        dateOfBirth = snapshot.dateOfBirth?.format(DateTimeFormatter.ISO_LOCAL_DATE),
                        address = snapshot.address.trim(),
                        city = snapshot.city.trim(),
                        state = snapshot.state.trim(),
                        zipcode = snapshot.zipcode.trim(),
                        accountType = snapshot.accountType.asAccountType,
                        inviteCode = snapshot.inviteCode.ifBlank { null }?.trim(),
                    )
                    update { it.copy(isSubmitting = false, didSucceed = true) }
                } catch (e: AuthError) {
                    update { it.copy(isSubmitting = false, topLevelError = e) }
                } catch (e: Throwable) {
                    update { it.copy(isSubmitting = false, topLevelError = AuthError.Unknown) }
                }
            }
        }
    }

