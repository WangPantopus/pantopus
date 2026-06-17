@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.automations

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.scheduling.PreviewTemplateRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling._shared.pillar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TEST_NOTE_MS = 2600L

/**
 * Stream A16 — H7 Message Preview. Renders the resolved message per channel
 * before saving. Drafts come inline from an editor (subject/body/channel).
 * Variables are filled by `POST /message-templates/preview` (sample values),
 * with a local interpolation fallback so the mock always renders. There is no
 * send-test endpoint yet, so "Send test" surfaces a coming-soon note honestly.
 */
@HiltViewModel
class MessagePreviewViewModel
    @Inject
    constructor(
        private val repo: SchedulingRepository,
    ) : ViewModel() {
        private val owner: SchedulingOwner = SchedulingOwner.Personal
        val pillar: SchedulingPillar = owner.pillar()

        /** Channel tab order per the design (Push first). */
        val channelOrder: List<WorkflowChannel> =
            listOf(WorkflowChannel.Push, WorkflowChannel.Email, WorkflowChannel.InApp, WorkflowChannel.Sms)

        private val _state = MutableStateFlow<MessagePreviewUiState>(MessagePreviewUiState.Loading)
        val state: StateFlow<MessagePreviewUiState> = _state.asStateFlow()

        private val _activeChannel = MutableStateFlow(WorkflowChannel.Email)
        val activeChannel: StateFlow<WorkflowChannel> = _activeChannel.asStateFlow()

        private val _testNote = MutableStateFlow<String?>(null)
        val testNote: StateFlow<String?> = _testNote.asStateFlow()

        private var lastKey: String? = null

        fun start(
            subject: String?,
            body: String,
            channel: WorkflowChannel,
        ) {
            val key = "$channel|${subject.orEmpty()}|$body"
            if (key == lastKey && _state.value is MessagePreviewUiState.Loaded) return
            lastKey = key
            _activeChannel.value = channel
            _state.value = MessagePreviewUiState.Loading
            viewModelScope.launch {
                val samples = TemplateVariableCatalog.sampleValues
                val result = repo.previewMessageTemplate(owner, PreviewTemplateRequest(body = body, subject = subject, variables = samples))
                _state.value =
                    when (result) {
                        is NetworkResult.Success ->
                            MessagePreviewUiState.Loaded(
                                filledSubject =
                                    result.data.subject?.let { interpolateTemplate(it, samples) }
                                        ?: subject?.let { interpolateTemplate(it, samples) },
                                filledBody = result.data.body ?: interpolateTemplate(body, samples),
                            )
                        is NetworkResult.Failure ->
                            MessagePreviewUiState.Loaded(
                                filledSubject = subject?.let { interpolateTemplate(it, samples) },
                                filledBody = interpolateTemplate(body, samples),
                            )
                    }
            }
        }

        fun selectChannel(index: Int) {
            _activeChannel.value = channelOrder.getOrElse(index) { WorkflowChannel.Push }
        }

        /** No send-test endpoint exists yet — surface a calm coming-soon note. */
        fun sendTest() {
            _testNote.value = "Test sends are coming soon. Save your message to use it."
            viewModelScope.launch {
                delay(TEST_NOTE_MS)
                _testNote.value = null
            }
        }
    }

@Immutable
sealed interface MessagePreviewUiState {
    data object Loading : MessagePreviewUiState

    data class Loaded(
        val filledSubject: String?,
        val filledBody: String,
    ) : MessagePreviewUiState
}
