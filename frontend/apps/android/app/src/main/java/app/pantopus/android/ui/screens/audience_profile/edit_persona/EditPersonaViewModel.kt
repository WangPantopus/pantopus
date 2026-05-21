@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.audience_profile.edit_persona

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Nav-arg key for the persona id read off the back-stack handle. Matches the
 * `ChildRoutes.EDIT_PERSONA` route template (`personas/{personaId}/edit`).
 */
const val EDIT_PERSONA_PERSONA_ID_KEY = "personaId"

/**
 * A13.12 — Backs the creator-facing Edit persona editor. The backend has been
 * removed from the repo, so `load()` projects a deterministic fixture
 * ([EditPersonaSampleData]) instead of fetching. The persona id selects the
 * frame: the sourdough fixture id loads the SETUP (mid-setup draft) frame,
 * everything else loads the LIVE (published) frame.
 */
@HiltViewModel
class EditPersonaViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val personaId: String =
            savedStateHandle.get<String>(EDIT_PERSONA_PERSONA_ID_KEY).orEmpty()

        private val _state = MutableStateFlow<EditPersonaUiState>(EditPersonaUiState.Loading)
        val state: StateFlow<EditPersonaUiState> = _state.asStateFlow()

        fun load() {
            _state.value = EditPersonaUiState.Loading
            viewModelScope.launch {
                _state.value =
                    if (personaId == EditPersonaSampleData.setup.personaId) {
                        EditPersonaUiState.Setup(
                            content = EditPersonaSampleData.setup,
                            stepsDone = EditPersonaSampleData.SETUP_STEPS_DONE,
                            stepsTotal = EditPersonaSampleData.SETUP_STEPS_TOTAL,
                        )
                    } else {
                        EditPersonaUiState.Live(EditPersonaSampleData.live)
                    }
            }
        }
    }
