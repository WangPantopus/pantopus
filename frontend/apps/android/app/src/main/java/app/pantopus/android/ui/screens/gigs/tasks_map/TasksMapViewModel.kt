@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.gigs.tasks_map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.gigs.GigsSort
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapAnchor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * A11.1 Tasks map view-model. No backend — seeds from [TasksMapSampleData]
 * and applies the live category filter + sort the design's chips and
 * sheet-header sort control drive. Owns the pin↔card selection link.
 *
 * Mirrors iOS `TasksMapViewModel`.
 */
@HiltViewModel
class TasksMapViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val seed: List<TaskMapItem> = TasksMapSampleData.items

        /** "You are here" anchor handed to the shell. */
        val anchor: MapAnchor? = TasksMapSampleData.anchor

        private val _state = MutableStateFlow<TasksMapUiState>(TasksMapUiState.Loading)
        val state: StateFlow<TasksMapUiState> = _state.asStateFlow()

        // Match by exact category key, defaulting to All for a missing /
        // unknown arg — mirrors iOS `GigsCategory(rawValue:) ?? .all`.
        // (`fromBackendKey` falls back to Handyman, which would wrongly
        // pre-filter the map.)
        private val _activeCategory =
            MutableStateFlow(
                GigsCategory.entries.firstOrNull { it.key == savedStateHandle.get<String>(CATEGORY_KEY) }
                    ?: GigsCategory.All,
            )
        val activeCategory: StateFlow<GigsCategory> = _activeCategory.asStateFlow()

        private val _activeSort = MutableStateFlow(GigsSort.Closest)
        val activeSort: StateFlow<GigsSort> = _activeSort.asStateFlow()

        private val _selectedId = MutableStateFlow<String?>(null)
        val selectedId: StateFlow<String?> = _selectedId.asStateFlow()

        fun load() = recompute()

        fun refresh() = recompute()

        fun selectCategory(category: GigsCategory) {
            if (category == _activeCategory.value) return
            _activeCategory.value = category
            recompute()
        }

        fun selectSort(sort: GigsSort) {
            if (sort == _activeSort.value) return
            _activeSort.value = sort
            recompute()
        }

        /** Pin↔card link — the shell fires this on pin tap; the screen also
         * snaps the sheet to Standard so the matching card surfaces. */
        fun select(id: String) {
            _selectedId.value = id
        }

        /**
         * Recompute the visible window. Empty either because the area has no
         * tasks or the active filter excludes them — both render the in-sheet
         * empty hero.
         */
        private fun recompute() {
            val visible = filteredSorted()
            if (visible.isEmpty()) {
                _selectedId.value = null
                _state.value = TasksMapUiState.Empty
                return
            }
            // Keep the selection if it survives the filter, else pick the
            // first visible task so exactly one pin pulses (design default).
            if (_selectedId.value == null || visible.none { it.id == _selectedId.value }) {
                _selectedId.value = visible.first().id
            }
            _state.value = TasksMapUiState.Populated(visible)
        }

        private fun filteredSorted(): List<TaskMapItem> {
            val filtered =
                seed.filter { _activeCategory.value == GigsCategory.All || it.category == _activeCategory.value }
            return when (_activeSort.value) {
                GigsSort.Newest -> filtered // seed is authored newest-first
                GigsSort.Closest -> filtered.sortedBy { distanceMiles(it.distanceLabel) }
                GigsSort.HighestPay -> filtered.sortedByDescending { priceValue(it.price) }
                GigsSort.FewestBids -> filtered.sortedBy { it.bidCount }
            }
        }

        companion object {
            const val CATEGORY_KEY = "category"

            private fun distanceMiles(label: String): Double = label.substringBefore(" ").toDoubleOrNull() ?: Double.MAX_VALUE

            private fun priceValue(price: String): Double = price.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
        }
    }
