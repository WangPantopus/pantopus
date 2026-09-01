package app.pantopus.android.ui.screens.compose.placepicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.geo.GeoPlace
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.location.UserCoordinate
import app.pantopus.android.data.place.PlaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Render state for the place-picker sheet. */
sealed interface PlacePickerUiState {
    data object Loading : PlacePickerUiState

    /** Nearby POIs + the enclosing locality from the device fix. */
    data class Loaded(
        val nearby: List<GeoPlace>,
        val locality: GeoPlace?,
    ) : PlacePickerUiState

    data class SearchResults(val places: List<GeoPlace>) : PlacePickerUiState

    data object Empty : PlacePickerUiState

    data class Error(val message: String) : PlacePickerUiState
}

/**
 * Backs [PlacePickerSheet] — the Instagram-style venue picker shared by
 * the Pulse and Beacon composers. [load] resolves a device fix and fetches
 * `GET /api/geo/places/nearby`; typing searches
 * `GET /api/geo/places/search` (debounced, proximity-biased when a fix is
 * known). No fix ⇒ search-only mode with no NEARBY section. Mirrors the
 * iOS `PlacePickerViewModel`.
 */
@HiltViewModel
class PlacePickerViewModel
    @Inject
    constructor(
        private val repo: PlaceRepository,
        private val locationProvider: LocationProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow<PlacePickerUiState>(PlacePickerUiState.Loading)
        val state: StateFlow<PlacePickerUiState> = _state.asStateFlow()

        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        /** True when no device fix is available — the sheet hides NEARBY. */
        private val _searchOnly = MutableStateFlow(false)
        val searchOnly: StateFlow<Boolean> = _searchOnly.asStateFlow()

        /** Device fix, kept for search proximity biasing. */
        private var deviceCoordinate: UserCoordinate? = null

        /**
         * Last successful nearby payload — null until a load (or
         * search-only entry) completes, restored when the search field
         * clears. Mirrors the iOS `lastNearby` cache.
         */
        private var lastNearby: NearbyPayload? = null
        private var searchJob: Job? = null

        private data class NearbyPayload(
            val nearby: List<GeoPlace>,
            val locality: GeoPlace?,
        )

        /**
         * Resolve a device fix and fetch the NEARBY section. Called on
         * sheet open (after the runtime permission flow, which lives in
         * the composable layer — [locationProvider] only checks).
         *
         * The GPS fix + fetch can take seconds: when the user typed a
         * live query meanwhile, the completion caches the payload for
         * later restore but never clobbers their on-screen search
         * results (or error) — same guard as the iOS `load()`.
         */
        fun load() {
            if (!hasActiveQuery()) {
                searchJob?.cancel()
                _state.value = PlacePickerUiState.Loading
            }
            viewModelScope.launch {
                val fix = locationProvider.requestCurrent(timeoutMillis = GPS_TIMEOUT_MS)
                deviceCoordinate = fix
                if (fix == null) {
                    enterSearchOnlyMode()
                    return@launch
                }
                _searchOnly.value = false
                when (val result = repo.geoNearbyPlaces(fix.latitude, fix.longitude)) {
                    is NetworkResult.Success -> {
                        lastNearby = NearbyPayload(result.data.places, result.data.locality)
                        if (!hasActiveQuery()) showNearby()
                    }
                    is NetworkResult.Failure ->
                        if (!hasActiveQuery()) {
                            _state.value =
                                PlacePickerUiState.Error(
                                    result.error.message.ifBlank { "Couldn't load nearby places." },
                                )
                        }
                }
            }
        }

        /**
         * Permission denied (or no fix) — search still works. Maps to
         * `Loaded(emptyList(), null)` like iOS's `.loaded([], nil)`; the
         * sheet renders the search-only hint from [searchOnly].
         */
        fun enterSearchOnlyMode() {
            _searchOnly.value = true
            lastNearby = NearbyPayload(emptyList(), null)
            if (!hasActiveQuery()) {
                _state.value = PlacePickerUiState.Loaded(nearby = emptyList(), locality = null)
            }
        }

        fun onQueryChange(value: String) {
            _query.value = value
            searchJob?.cancel()
            val q = value.trim()
            if (q.length < MIN_QUERY_LENGTH) {
                // Restore nearby only when a payload was cached — a failed
                // load's Error card (and its Retry) must survive short
                // queries (mirrors iOS's `lastNearby` nullable guard).
                showNearby()
                return
            }
            searchJob =
                viewModelScope.launch {
                    delay(SEARCH_DEBOUNCE_MS)
                    val coordinate = deviceCoordinate
                    when (val result = repo.geoSearchPlaces(q, coordinate?.latitude, coordinate?.longitude)) {
                        is NetworkResult.Success ->
                            _state.value =
                                if (result.data.places.isEmpty()) {
                                    PlacePickerUiState.Empty
                                } else {
                                    PlacePickerUiState.SearchResults(result.data.places)
                                }
                        is NetworkResult.Failure ->
                            _state.value =
                                PlacePickerUiState.Error(
                                    result.error.message.ifBlank { "Couldn't search places." },
                                )
                    }
                }
        }

        /** Error-state CTA — re-run the search or the nearby load. */
        fun retry() {
            if (_query.value.trim().length >= MIN_QUERY_LENGTH) {
                onQueryChange(_query.value)
            } else {
                load()
            }
        }

        /** True while the query is long enough to own the list area. */
        private fun hasActiveQuery(): Boolean = _query.value.trim().length >= MIN_QUERY_LENGTH

        private fun showNearby() {
            val cached = lastNearby ?: return
            // Loaded even when empty — the sheet renders "No places
            // nearby" from the loaded state, matching iOS.
            _state.value = PlacePickerUiState.Loaded(nearby = cached.nearby, locality = cached.locality)
        }

        private companion object {
            /** Best-effort fix; the sheet degrades to search-only past this. */
            const val GPS_TIMEOUT_MS = 4_000L

            // Mirrors PlaceLaunchViewModel: one request per pause, not per key.
            const val SEARCH_DEBOUNCE_MS = 220L
            const val MIN_QUERY_LENGTH = 2
        }
    }
