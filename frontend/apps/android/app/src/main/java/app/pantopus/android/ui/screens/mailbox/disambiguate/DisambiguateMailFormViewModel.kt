@file:Suppress("MagicNumber", "PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.mailbox.disambiguate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.mailbox.v2.ResolveRoutingRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.screens.homes.invite_owner.ToastPayload
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg keys for the disambiguate form. */
const val DISAMBIGUATE_MAIL_ID_KEY = "mailId"
const val DISAMBIGUATE_OCR_TEXT_KEY = "ocrText"
const val DISAMBIGUATE_CONFIDENCE_KEY = "confidence"
const val DISAMBIGUATE_ENVELOPE_URL_KEY = "envelopeUrl"

/** One of the three drawer destinations the backend accepts. */
enum class MailRecipientChoice(
    val drawer: String,
    val title: String,
    val subtitle: String,
    val identity: IdentityPillar,
) {
    Personal(
        drawer = "personal",
        title = "Just for me",
        subtitle = "Routes to your personal drawer",
        identity = IdentityPillar.Personal,
    ),
    Home(
        drawer = "home",
        title = "My home household",
        subtitle = "Routes to the shared home drawer",
        identity = IdentityPillar.Home,
    ),
    Business(
        drawer = "business",
        title = "My business inbox",
        subtitle = "Routes to the business team drawer",
        identity = IdentityPillar.Business,
    ),
}

/** Aggregate UI state for the Disambiguate form. */
data class DisambiguateUiState(
    val ocrRecipient: String = "",
    val confidence: Double = 0.0,
    val envelopeUrl: String? = null,
    val selected: MailRecipientChoice? = null,
    val aliasNotes: String = "",
    val isSubmitting: Boolean = false,
    val toast: ToastPayload? = null,
    val shouldDismiss: Boolean = false,
) {
    /** Char-count error when the alias would exceed the schema limit. */
    val aliasError: String?
        get() = if (aliasNotes.trim().length > 255) {
            "Notes must be 255 characters or fewer."
        } else {
            null
        }

    val canSubmit: Boolean get() = selected != null && aliasError == null && !isSubmitting
}

/**
 * POSTs `/api/mailbox/v2/resolve`
 * (`backend/routes/mailboxV2.js:555`).
 *
 * Backend accepts a `drawer` enum (`personal | home | business`) plus
 * an optional alias. The Form design drew "candidate recipients" with
 * avatars; we surface the three drawers as the three rows. See PR
 * description for the discrepancy.
 */
@HiltViewModel
class DisambiguateMailFormViewModel
    @Inject
    constructor(
        private val repo: MailboxRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mailId: String =
            requireNotNull(savedStateHandle[DISAMBIGUATE_MAIL_ID_KEY]) {
                "DisambiguateMailFormViewModel requires a '$DISAMBIGUATE_MAIL_ID_KEY' nav arg."
            }

        private val _state =
            MutableStateFlow(
                DisambiguateUiState(
                    ocrRecipient = savedStateHandle[DISAMBIGUATE_OCR_TEXT_KEY] ?: "",
                    confidence = savedStateHandle[DISAMBIGUATE_CONFIDENCE_KEY] ?: 0.0,
                    envelopeUrl = savedStateHandle[DISAMBIGUATE_ENVELOPE_URL_KEY],
                ),
            )
        val state: StateFlow<DisambiguateUiState> = _state.asStateFlow()

        fun select(choice: MailRecipientChoice) {
            _state.update { it.copy(selected = choice) }
        }

        fun setAliasNotes(value: String) {
            _state.update { it.copy(aliasNotes = value) }
        }

        fun dismissToast() {
            _state.update { it.copy(toast = null) }
        }

        fun acknowledgeDismiss() {
            _state.update { it.copy(shouldDismiss = false) }
        }

        fun submit() {
            val current = _state.value
            val choice = current.selected ?: run {
                _state.update {
                    it.copy(toast = ToastPayload("Pick a destination first.", isError = true))
                }
                return
            }
            current.aliasError?.let { msg ->
                _state.update { it.copy(toast = ToastPayload(msg, isError = true)) }
                return
            }
            _state.update { it.copy(isSubmitting = true) }
            val trimmedAlias = current.aliasNotes.trim()
            val request =
                ResolveRoutingRequest(
                    mailId = mailId,
                    drawer = choice.drawer,
                    addAlias = if (trimmedAlias.isEmpty()) null else true,
                    aliasString = trimmedAlias.ifEmpty { null },
                )
            viewModelScope.launch {
                when (val result = repo.resolve(request)) {
                    is NetworkResult.Success ->
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                toast =
                                    ToastPayload(
                                        "Mail routed to ${choice.title.lowercase()}.",
                                        isError = false,
                                    ),
                                shouldDismiss = true,
                            )
                        }
                    is NetworkResult.Failure ->
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                toast =
                                    ToastPayload(
                                        result.error.message ?: "Couldn't route this mail.",
                                        isError = true,
                                    ),
                            )
                        }
                }
            }
        }
    }
