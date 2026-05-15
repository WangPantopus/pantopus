@file:Suppress("PackageNaming", "LongMethod", "MagicNumber")

package app.pantopus.android.ui.screens.ceremonial_mail_open

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.mailbox.MailboxRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class CeremonialMailOpenViewModel
    @Inject
    constructor(
        private val repository: MailboxRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val mailId: String = savedStateHandle.get<String>(MAIL_ID_KEY) ?: ""

        private val _state = MutableStateFlow<CeremonialMailOpenUiState>(CeremonialMailOpenUiState.Loading)
        val state: StateFlow<CeremonialMailOpenUiState> = _state.asStateFlow()

        private val _isVoicePlaying = MutableStateFlow(false)
        val isVoicePlaying: StateFlow<Boolean> = _isVoicePlaying.asStateFlow()

        fun load() {
            if (mailId.isBlank()) {
                _state.value = CeremonialMailOpenUiState.Error("Missing mail id.")
                return
            }
            _state.value = CeremonialMailOpenUiState.Loading
            viewModelScope.launch {
                when (val result = repository.item(mailId)) {
                    is NetworkResult.Success -> {
                        val letter = project(result.data, mailId)
                        _state.value = CeremonialMailOpenUiState.Loaded(letter, CeremonialMailPhase.Sealed)
                    }
                    is NetworkResult.Failure ->
                        _state.value = CeremonialMailOpenUiState.Error("Couldn't load this letter.")
                }
            }
        }

        /** Step the seal-break ceremony forward. The view triggers
         *  this when the user taps the envelope. */
        fun startBreakingSeal() {
            val current = _state.value as? CeremonialMailOpenUiState.Loaded ?: return
            if (current.phase != CeremonialMailPhase.Sealed) return
            _state.value = current.copy(phase = CeremonialMailPhase.Breaking)
            viewModelScope.launch {
                delay(600)
                val now = _state.value as? CeremonialMailOpenUiState.Loaded ?: return@launch
                if (now.phase == CeremonialMailPhase.Breaking) {
                    _state.value = now.copy(phase = CeremonialMailPhase.Open)
                }
            }
        }

        fun openImmediately() {
            val current = _state.value as? CeremonialMailOpenUiState.Loaded ?: return
            _state.value = current.copy(phase = CeremonialMailPhase.Open)
        }

        fun enterReplying() {
            val current = _state.value as? CeremonialMailOpenUiState.Loaded ?: return
            _state.value = current.copy(phase = CeremonialMailPhase.Replying)
        }

        fun resetToOpen() {
            val current = _state.value as? CeremonialMailOpenUiState.Loaded ?: return
            _state.value = current.copy(phase = CeremonialMailPhase.Open)
        }

        fun toggleVoicePlayback() {
            _isVoicePlaying.value = !_isVoicePlaying.value
        }

        fun stopVoicePlayback() {
            _isVoicePlaying.value = false
        }

        companion object {
            const val MAIL_ID_KEY = "mailId"

            internal fun project(
                response: app.pantopus.android.data.api.models.mailbox.v2.MailboxV2ItemResponse,
                mailId: String,
            ): CeremonialMailLetter {
                val item = response.mail
                // The backend's `object_payload` arrives as an opaque
                // map — pull the ceremonial slots by key. The wider
                // payload is intentionally untyped at the repo layer
                // because each mail_type has a different shape.
                val payload =
                    runCatching {
                        item.objectPayload?.let { JSONObject(it.toString()) }
                    }.getOrNull()
                val stationery =
                    CeremonialMailStationeryTone.fromWire(payload?.optString("stationeryTheme")?.takeIf { it.isNotEmpty() })
                val ink = CeremonialMailInkTone.fromWire(payload?.optString("inkSelection")?.takeIf { it.isNotEmpty() })
                val seal = CeremonialMailSealTone.fromWire(payload?.optString("sealChoice")?.takeIf { it.isNotEmpty() })
                val voiceUri = payload?.optString("voicePostscriptUri")?.takeIf { it.isNotEmpty() }
                val body = item.content.orEmpty()
                val paragraphs =
                    body.split("\n\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                val sender =
                    CeremonialSenderCard(
                        displayName = item.senderDisplay ?: item.sender?.name ?: "Someone",
                        handle = item.sender?.username,
                        trustLabel = trustLabel(item.senderTrust),
                        avatarUrl = null,
                    )
                return CeremonialMailLetter(
                    mailId = mailId,
                    sender = sender,
                    category = "letter",
                    subject = item.subject ?: "A letter",
                    bodyParagraphs = if (paragraphs.isEmpty()) listOf(body) else paragraphs,
                    stationery = stationery,
                    ink = ink,
                    seal = seal,
                    voicePostscriptUri = voiceUri,
                    receivedAt = item.createdAt,
                    outcomeCtas = CeremonialMailLetter.defaultOutcomeCtas(),
                )
            }

            internal fun trustLabel(raw: String?): String? =
                when (raw) {
                    "verified_gov", "verified_utility", "verified_business" -> "Verified"
                    "pantopus_user" -> "Pantopus friend"
                    "partial" -> "Partial trust"
                    else -> null
                }
        }
    }
