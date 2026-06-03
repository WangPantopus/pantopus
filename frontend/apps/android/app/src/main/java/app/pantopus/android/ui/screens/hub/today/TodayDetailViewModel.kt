@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.hub.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.hub.HubRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * A10.3 / P1-F — backs the full-screen Hub "Today" briefing.
 *
 * The production path decodes `GET /api/hub/today` via [HubRepository] and
 * projects the orchestrated payload onto [TodayDetailContent]. Today always
 * has weather data, so the state machine is loading / populated / alert /
 * error (no empty); a `CONTEXT_UNAVAILABLE` payload (`today == null`) maps to
 * Error. Previews / snapshots / tests seed deterministic [TodaySampleData]
 * through [setFixture], which bypasses the network.
 */
@HiltViewModel
class TodayDetailViewModel
    @Inject
    constructor(
        private val repository: HubRepository,
    ) : ViewModel() {
        private var fixture: TodayDetailContent? = null

        private val _state = MutableStateFlow<TodayDetailUiState>(TodayDetailUiState.Loading)
        val state: StateFlow<TodayDetailUiState> = _state.asStateFlow()

        fun load() {
            val seed = fixture
            if (seed != null) {
                _state.value =
                    if (seed.isAlert) TodayDetailUiState.Alert(seed) else TodayDetailUiState.Populated(seed)
                return
            }
            _state.value = TodayDetailUiState.Loading
            viewModelScope.launch {
                when (val result = repository.todayDetail()) {
                    is NetworkResult.Success -> {
                        val payload = result.data
                        _state.value =
                            if (!payload.isRenderable) {
                                TodayDetailUiState.Error("Today's briefing isn't available right now.")
                            } else {
                                val content = TodayDetailMapper.fromPayload(payload)
                                if (content.isAlert) {
                                    TodayDetailUiState.Alert(content)
                                } else {
                                    TodayDetailUiState.Populated(content)
                                }
                            }
                    }
                    is NetworkResult.Failure -> {
                        _state.value = TodayDetailUiState.Error(result.error.message)
                    }
                }
            }
        }

        fun refresh() = load()

        /** Test/preview seam — swap the stub fixture before calling [load]. */
        fun setFixture(content: TodayDetailContent) {
            this.fixture = content
        }
    }
