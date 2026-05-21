@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.mailbox.mailbox_map

import androidx.lifecycle.ViewModel
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapListHybridDetent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar
import javax.inject.Inject

/**
 * A11.4 Mailbox map view-model. No backend — [load] surfaces the seeded
 * spots (mirroring the fetch shape so the four render states still
 * apply). Owns the sheet detent, the active category-chip filter, and
 * the pin↔detail selection link. Mirrors the iOS `MailboxMapViewModel`.
 *
 * The production path is the no-arg [Inject] constructor; the internal
 * secondary constructor is the test / preview seam (mirrors the iOS
 * `init(spots:seededState:todayWeekday:)`).
 */
@HiltViewModel
class MailboxMapViewModel
    @Inject
    constructor() : ViewModel() {
        private var allSpots: List<MailboxSpot> = MailboxMapSampleData.spots
        private var seededState: MailboxMapUiState? = null

        /**
         * Current weekday (`Calendar` convention, 1 = Sun … 7 = Sat) used
         * to highlight the week-hour strip. Injected so previews + tests
         * stay deterministic.
         */
        var todayWeekday: Int = defaultWeekday()
            private set

        /** Test / preview seam. */
        internal constructor(
            spots: List<MailboxSpot> = MailboxMapSampleData.spots,
            seededState: MailboxMapUiState? = null,
            todayWeekday: Int = defaultWeekday(),
        ) : this() {
            this.allSpots = spots
            this.seededState = seededState
            this.todayWeekday = todayWeekday
        }

        private val _state = MutableStateFlow<MailboxMapUiState>(MailboxMapUiState.Loading)

        /** Current render state. Mutated through [load] / [select] / [backToList]. */
        val state: StateFlow<MailboxMapUiState> = _state.asStateFlow()

        private val _detent = MutableStateFlow(MapListHybridDetent.Standard)

        /** Sheet detent for the populated rail (A11 archetype contract). */
        val detent: StateFlow<MapListHybridDetent> = _detent.asStateFlow()

        private val _activeKind = MutableStateFlow<MailboxSpotKind?>(null)

        /** Active category chip; `null` is the "All" sentinel. */
        val activeKind: StateFlow<MailboxSpotKind?> = _activeKind.asStateFlow()

        /**
         * Surface the seeded spots. Stand-in for the network fetch the
         * archetype would otherwise drive.
         */
        fun load() {
            val seeded = seededState
            if (seeded != null) {
                _state.value = seeded
                return
            }
            _state.value = MailboxMapUiState.Populated(filtered(allSpots))
        }

        fun refresh() = load()

        /**
         * Tap a pin / rail card → pin-detail. The full spot list rides
         * along so the context strip can keep drawing dimmed pins. The
         * category filter is left untouched — the selected frame's chip
         * highlight follows the spot's kind purely in the view, so "Back
         * to list" restores whatever filter the user had.
         */
        fun select(id: String) {
            val spot = allSpots.firstOrNull { it.id == id } ?: return
            _state.value = MailboxMapUiState.Selected(spot = spot, spots = allSpots)
        }

        /** "Back to list" → restore the populated rail under the current filter. */
        fun backToList() {
            _state.value = MailboxMapUiState.Populated(filtered(allSpots))
        }

        /**
         * Category-chip tap. Applies the filter and surfaces the populated
         * rail — also the way "back to list" works when a chip is tapped
         * from an open detail panel.
         */
        fun selectKind(kind: MailboxSpotKind?) {
            _activeKind.value = kind
            _state.value = MailboxMapUiState.Populated(filtered(allSpots))
        }

        fun setDetent(detent: MapListHybridDetent) {
            _detent.value = detent
        }

        private fun filtered(spots: List<MailboxSpot>): List<MailboxSpot> {
            val kind = _activeKind.value ?: return spots
            return spots.filter { it.kind == kind }
        }

        private companion object {
            fun defaultWeekday(): Int = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        }
    }
