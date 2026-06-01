@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.identity_center.view_as

import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.components.ViewerAudience
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * B5.2 (A18.5) — backs the "View as" identity preview. Mirrors iOS
 * `ViewAsViewModel.swift`. Holds the chosen [ViewerAudience] and the
 * resolved [ViewAsRender]; picking a chip re-resolves the entire render
 * through [ViewAsSampleData] (the sample privacy matrix that stands in for
 * backend per-field resolution), so banner tone, badges and field
 * redaction all flip in one shot.
 *
 * No network: the data is local sample content, so [load] resolves
 * immediately and there's no empty/error path.
 */
@HiltViewModel
class ViewAsViewModel
    @Inject
    constructor() : ViewModel() {
        private val _selected = MutableStateFlow(ViewerAudience.Connection)
        val selected: StateFlow<ViewerAudience> = _selected.asStateFlow()

        private val _state = MutableStateFlow<ViewAsUiState>(ViewAsUiState.Loading)
        val state: StateFlow<ViewAsUiState> = _state.asStateFlow()

        /** Resolve the initial render for the seeded audience. */
        fun load() {
            _state.value = resolve(_selected.value)
        }

        /** Switch the previewed audience and re-resolve the render in place. */
        fun select(viewer: ViewerAudience) {
            if (viewer == _selected.value) return
            _selected.value = viewer
            // Only re-emit once we've left the loading frame.
            if (_state.value is ViewAsUiState.Loaded) {
                _state.value = resolve(viewer)
            }
        }

        private fun resolve(viewer: ViewerAudience): ViewAsUiState.Loaded =
            ViewAsUiState.Loaded(selected = viewer, render = ViewAsSampleData.render(viewer))
    }
