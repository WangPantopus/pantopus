@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.support_trains.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.support_trains.SupportTrainsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A10.9 — VM for the participant-facing Support Train detail screen.
 * Distinct from the organizer-only `ReviewSignupsViewModel`. The
 * detail payload is not yet projected by the backend's
 * `GET /api/support-trains/:id`, so the VM resolves from a
 * deterministic stub ([SupportTrainDetailSampleData]) and chooses the
 * `populated` vs `fullyCovered` variant by inspecting the `trainId`.
 *
 * The state machine matches the iOS [SupportTrainDetailViewModel]:
 * `Loading / Loaded / Error`. Fully-covered is **not** empty — it's a
 * celebrated loaded variant.
 */
@HiltViewModel
class SupportTrainDetailViewModel
    @Inject
    constructor(
        private val repo: SupportTrainsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            const val SUPPORT_TRAIN_ID_KEY = "supportTrainDetailId"
        }

        private val trainId: String =
            savedStateHandle.get<String>(SUPPORT_TRAIN_ID_KEY) ?: "sample-populated"

        private val _state =
            MutableStateFlow<SupportTrainDetailUiState>(SupportTrainDetailUiState.Loading)
        val state: StateFlow<SupportTrainDetailUiState> = _state.asStateFlow()

        /**
         * Optional offline override (previews / QA / tests). When null — the
         * production default — `load()` fetches `GET /api/support-trains/:id`
         * and projects it via [SupportTrainDetailProjection]. Set it to
         * [::defaultResolve] to drive the sample fixtures without a backend.
         */
        var resolve: ((String) -> SupportTrainDetailContent?)? = null

        fun load() {
            viewModelScope.launch {
                _state.value = SupportTrainDetailUiState.Loading
                val override = resolve
                if (override != null) {
                    val content = override(trainId)
                    _state.value =
                        if (content != null) {
                            SupportTrainDetailUiState.Loaded(content)
                        } else {
                            SupportTrainDetailUiState.Error("Couldn't load this support train.")
                        }
                    return@launch
                }
                _state.value =
                    when (val result = repo.detail(trainId)) {
                        is NetworkResult.Success ->
                            SupportTrainDetailUiState.Loaded(SupportTrainDetailProjection.project(result.data))
                        is NetworkResult.Failure ->
                            SupportTrainDetailUiState.Error(result.error.message)
                    }
            }
        }

        fun refresh() {
            load()
        }

        /**
         * Test-friendly seeding hook. Used by previews + chrome tests
         * to exercise loading / error deterministically. Hilt callers
         * never invoke this.
         */
        fun seed(state: SupportTrainDetailUiState) {
            _state.value = state
        }
    }

/**
 * Pure resolver used both as the default VM strategy and directly
 * from previews + tests. Returns the fully-covered fixture when the
 * trainId contains "covered" or "full", otherwise the populated one.
 */
fun defaultResolve(trainId: String): SupportTrainDetailContent {
    val lowered = trainId.lowercase()
    return if ("covered" in lowered || "full" in lowered) {
        SupportTrainDetailSampleData.fullyCovered
    } else {
        SupportTrainDetailSampleData.populated
    }
}
