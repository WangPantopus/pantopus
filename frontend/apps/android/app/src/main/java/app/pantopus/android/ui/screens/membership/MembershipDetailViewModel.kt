@file:Suppress("PackageNaming", "UnusedPrivateMember")

package app.pantopus.android.ui.screens.membership

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
 * `ChildRoutes.MEMBERSHIP_DETAIL` route template (`personas/{personaId}/...`).
 */
const val MEMBERSHIP_DETAIL_PERSONA_ID_KEY = "personaId"

/**
 * A10.8 — Backs the fan-side membership manage screen. The backend has been
 * removed from the repo, so `load()` projects a deterministic fixture
 * ([MembershipSampleData]) instead of fetching. "Give it a week" snoozes the
 * SLA banner in place via [dismissSlaAlert].
 */
@HiltViewModel
class MembershipDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        // Carried for parity with the eventual fetch (per-persona membership
        // projection). Read here so the route arg is wired end-to-end.
        private val personaId: String =
            savedStateHandle.get<String>(MEMBERSHIP_DETAIL_PERSONA_ID_KEY).orEmpty()

        private val _state = MutableStateFlow<MembershipDetailUiState>(MembershipDetailUiState.Loading)
        val state: StateFlow<MembershipDetailUiState> = _state.asStateFlow()

        fun load() {
            _state.value = MembershipDetailUiState.Loading
            viewModelScope.launch {
                val content = MembershipSampleData.populated
                _state.value =
                    if (content.slaAlert != null) {
                        MembershipDetailUiState.SlaMissed(content)
                    } else {
                        MembershipDetailUiState.Populated(content)
                    }
            }
        }

        /** "Give it a week" — drop the SLA banner and settle to the happy path. */
        fun dismissSlaAlert() {
            val current = _state.value
            if (current is MembershipDetailUiState.SlaMissed) {
                _state.value = MembershipDetailUiState.Populated(current.content.clearingSlaAlert())
            }
        }
    }
