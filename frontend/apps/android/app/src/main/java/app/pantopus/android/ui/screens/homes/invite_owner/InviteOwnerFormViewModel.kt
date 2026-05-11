@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.invite_owner

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.InviteOwnerRequest
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.ui.screens.shared.form.FormAggregate
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.screens.shared.form.FormValidator
import app.pantopus.android.ui.screens.shared.form.all
import app.pantopus.android.ui.screens.shared.form.e164Phone
import app.pantopus.android.ui.screens.shared.form.email
import app.pantopus.android.ui.screens.shared.form.emailNotMatching
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg keys for the Invite Owner form. */
const val INVITE_OWNER_HOME_ID_KEY = "homeId"
const val INVITE_OWNER_CURRENT_EMAIL_KEY = "currentUserEmail"

/** Stable identifiers for every editable Invite Owner field. */
enum class InviteOwnerField(val key: String) {
    Email("email"),
    Phone("phone"),
}

/** Aggregate UI state for the Invite Owner form. */
data class InviteOwnerUiState(
    val fields: Map<InviteOwnerField, FormFieldState> =
        InviteOwnerField.entries.associateWith { FormFieldState(id = it.key) },
    val isSaving: Boolean = false,
    val toast: ToastPayload? = null,
    val shouldDismiss: Boolean = false,
) {
    val aggregate: FormAggregate
        get() = FormAggregate.from(InviteOwnerField.entries.mapNotNull { fields[it] })

    val isValid: Boolean get() = aggregate.isValid
    val isDirty: Boolean get() = aggregate.isDirty
}

/** Tiny tone+text bundle the screen turns into a snackbar / toast. */
data class ToastPayload(
    val text: String,
    val isError: Boolean,
)

/**
 * POSTs `/api/homes/:id/owners/invite`
 * (`backend/routes/homeOwnership.js:1376`). See PR description for the
 * Role + Personal Note fields the design draws but the backend doesn't
 * accept.
 */
@HiltViewModel
class InviteOwnerFormViewModel
    @Inject
    constructor(
        private val repo: HomesRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val homeId: String =
            requireNotNull(savedStateHandle[INVITE_OWNER_HOME_ID_KEY]) {
                "InviteOwnerFormViewModel requires a '$INVITE_OWNER_HOME_ID_KEY' nav arg."
            }
        private val currentUserEmail: String =
            savedStateHandle[INVITE_OWNER_CURRENT_EMAIL_KEY] ?: ""

        private val _state = MutableStateFlow(InviteOwnerUiState())
        val state: StateFlow<InviteOwnerUiState> = _state.asStateFlow()

        fun update(
            field: InviteOwnerField,
            value: String,
        ) {
            _state.update { current ->
                val snapshot =
                    current.fields[field]?.copy(
                        value = value,
                        touched = true,
                        error = validator(field).validate(value),
                    ) ?: FormFieldState(id = field.key, value = value, touched = true)
                current.copy(fields = current.fields + (field to snapshot))
            }
        }

        fun dismissToast() {
            _state.update { it.copy(toast = null) }
        }

        fun acknowledgeDismiss() {
            _state.update { it.copy(shouldDismiss = false) }
        }

        /** Run all validators. Returns the first invalid field, if any. */
        fun validateAll(): InviteOwnerField? {
            var firstInvalid: InviteOwnerField? = null
            _state.update { current ->
                val updated =
                    current.fields.mapValues { (field, snapshot) ->
                        val message = validator(field).validate(snapshot.value)
                        if (firstInvalid == null && message != null) firstInvalid = field
                        snapshot.copy(error = message, touched = true)
                    }
                current.copy(fields = updated)
            }
            return firstInvalid
        }

        fun submit() {
            if (validateAll() != null) {
                _state.update {
                    it.copy(toast = ToastPayload("Fix the highlighted field.", isError = true))
                }
                return
            }
            _state.update { it.copy(isSaving = true) }
            val request = buildRequest()
            viewModelScope.launch {
                when (val result = repo.inviteOwner(homeId, request)) {
                    is NetworkResult.Success -> {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                toast = ToastPayload("Invite sent.", isError = false),
                            )
                        }
                        // Hold the success toast on screen briefly before
                        // dismissing the form so the overlay actually renders.
                        kotlinx.coroutines.delay(1_500)
                        _state.update { it.copy(shouldDismiss = true) }
                    }
                    is NetworkResult.Failure ->
                        _state.update { current ->
                            val (fieldError, toast) = mapInviteError(result.error)
                            val updatedFields =
                                if (fieldError != null) {
                                    val snapshot =
                                        current.fields[InviteOwnerField.Email]
                                            ?: FormFieldState(id = InviteOwnerField.Email.key)
                                    current.fields +
                                        (InviteOwnerField.Email to snapshot.copy(error = fieldError, touched = true))
                                } else {
                                    current.fields
                                }
                            current.copy(
                                isSaving = false,
                                fields = updatedFields,
                                toast = ToastPayload(toast, isError = true),
                            )
                        }
                }
            }
        }

        private fun buildRequest(): InviteOwnerRequest {
            val email = (_state.value.fields[InviteOwnerField.Email]?.value ?: "").trim()
            val phone = (_state.value.fields[InviteOwnerField.Phone]?.value ?: "").trim()
            return InviteOwnerRequest(
                email = email.ifEmpty { null },
                phone = phone.ifEmpty { null },
                userId = null,
                fastTrack = false,
            )
        }

        private fun validator(field: InviteOwnerField): FormValidator =
            when (field) {
                InviteOwnerField.Email ->
                    FormValidator.all(
                        listOf(FormValidator.email(), FormValidator.emailNotMatching(currentUserEmail)),
                    )
                InviteOwnerField.Phone -> FormValidator.e164Phone()
            }

        /** Map a backend 400/409 onto a friendly inline message. */
        private fun mapInviteError(error: NetworkError): Pair<String?, String> {
            val raw = (error.message ?: "").lowercase()
            return when {
                raw.contains("already active") ->
                    "An ownership claim is already active for this home." to
                        "An ownership claim is already active for this home."
                raw.contains("already an owner") ->
                    "Already an owner of this home." to
                        "Already an owner of this home."
                raw.contains("could not find") || raw.contains("create an account") ->
                    "We couldn't find a Pantopus account with that email." to
                        "We couldn't find a Pantopus account with that email."
                else -> null to (error.message ?: "Couldn't send invite.")
            }
        }
    }
