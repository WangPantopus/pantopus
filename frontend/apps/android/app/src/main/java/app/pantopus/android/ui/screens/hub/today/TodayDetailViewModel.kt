@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.hub.today

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * A10.3 — backs the full-screen Hub "Today" briefing. Today always has
 * weather data, so the state machine is loading / populated / alert / error
 * (no empty). Backend has been removed from the repo, so the view-model is
 * fed deterministic stub content ([TodaySampleData]) rather than a network
 * call; `content.isAlert` selects the populated vs. alert state.
 */
@HiltViewModel
class TodayDetailViewModel
    @Inject
    constructor() : ViewModel() {
        private var content: TodayDetailContent = TodaySampleData.populated

        private val _state = MutableStateFlow<TodayDetailUiState>(TodayDetailUiState.Loading)
        val state: StateFlow<TodayDetailUiState> = _state.asStateFlow()

        fun load() {
            _state.value =
                if (content.isAlert) {
                    TodayDetailUiState.Alert(content)
                } else {
                    TodayDetailUiState.Populated(content)
                }
        }

        fun refresh() = load()

        /** Test/preview seam — swap the stub fixture before calling [load]. */
        fun setFixture(content: TodayDetailContent) {
            this.content = content
        }
    }
