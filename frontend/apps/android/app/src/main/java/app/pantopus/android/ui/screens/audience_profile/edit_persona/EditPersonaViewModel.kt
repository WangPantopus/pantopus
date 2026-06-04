@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.audience_profile.edit_persona

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.audience.PersonaSummaryDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.audience.AudienceProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

/**
 * Nav-arg key for the persona id read off the back-stack handle. Matches the
 * `ChildRoutes.EDIT_PERSONA` route template (`personas/{personaId}/edit`).
 */
const val EDIT_PERSONA_PERSONA_ID_KEY = "personaId"

/**
 * A13.12 — Backs the creator-facing Edit persona editor. The persona id selects
 * the frame: the sourdough fixture id loads the SETUP (mid-setup draft) frame,
 * everything else loads the LIVE (published) frame.
 *
 * BLOCK 3E wires the load path to `GET /api/mailbox/.../personas/me`
 * ([AudienceProfileRepository.me]) and overlays the identity fields the
 * serializer exposes (handle / display name / bio / follower + post counts)
 * onto the sample frame. The editor's richer configuration — tiers, Stripe
 * Connect state, broadcast cap, quiet hours, analytics, category policy, setup
 * checklist — has no source in the persona serializer (and `updatePersonaSchema`
 * accepts only handle/display_name/links/category/audience), so those stay
 * sampled. The iOS counterpart is still fully sample-only ("backend removed"),
 * making this the parity-leading platform.
 *
 * The production seam is the [Inject] constructor; the test path passes a
 * repository whose `me()` fails, so [load] falls back to the pure sample frame
 * the existing unit + snapshot tests assert.
 */
@HiltViewModel
class EditPersonaViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: AudienceProfileRepository,
    ) : ViewModel() {
        private val personaId: String =
            savedStateHandle.get<String>(EDIT_PERSONA_PERSONA_ID_KEY).orEmpty()

        private val _state = MutableStateFlow<EditPersonaUiState>(EditPersonaUiState.Loading)
        val state: StateFlow<EditPersonaUiState> = _state.asStateFlow()

        fun load() {
            _state.value = EditPersonaUiState.Loading
            viewModelScope.launch {
                val base = baseFrame()
                _state.value =
                    when (val result = repository.me()) {
                        is NetworkResult.Success -> overlay(base, result.data.persona)
                        is NetworkResult.Failure -> base
                    }
            }
        }

        /** The sample frame the persona id selects, before any live overlay. */
        private fun baseFrame(): EditPersonaUiState =
            if (personaId == EditPersonaSampleData.setup.personaId) {
                EditPersonaUiState.Setup(
                    content = EditPersonaSampleData.setup,
                    stepsDone = EditPersonaSampleData.SETUP_STEPS_DONE,
                    stepsTotal = EditPersonaSampleData.SETUP_STEPS_TOTAL,
                )
            } else {
                EditPersonaUiState.Live(EditPersonaSampleData.live)
            }

        /** Overlay the live identity fields onto the sample frame (no-op if null). */
        private fun overlay(
            base: EditPersonaUiState,
            persona: PersonaSummaryDto?,
        ): EditPersonaUiState {
            if (persona == null) return base
            return when (base) {
                is EditPersonaUiState.Live -> EditPersonaUiState.Live(merge(base.content, persona))
                is EditPersonaUiState.Setup -> base.copy(content = merge(base.content, persona))
                else -> base
            }
        }

        private fun merge(
            content: EditPersonaContent,
            persona: PersonaSummaryDto,
        ): EditPersonaContent =
            content.copy(
                handle = persona.handle ?: content.handle,
                displayName = persona.displayName ?: content.displayName,
                bio = persona.bio ?: content.bio,
                followers = persona.followerCount?.let { String.format(Locale.US, "%,d", it) } ?: content.followers,
                posts = persona.postCount?.toString() ?: content.posts,
            )
    }
