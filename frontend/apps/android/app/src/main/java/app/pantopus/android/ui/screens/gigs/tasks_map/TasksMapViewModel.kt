@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.gigs.tasks_map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.location.UserCoordinate
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.gigs.GigsSort
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapAnchor
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapPinState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A11.1 Tasks map view-model. `load()` hits `GET /api/gigs/in-bounds`
 * (`GigsRepository.inBounds`) for a ~1.3 km viewport around the anchor —
 * the same map endpoint the generic Nearby map uses — and projects the
 * gigs into pin↔card [TaskMapItem]s. The live category filter + sort run
 * client-side on the fetched set. Owns the pin↔card selection link.
 *
 * Mirrors iOS `TasksMapViewModel`.
 */
@HiltViewModel
class TasksMapViewModel
    @Inject
    constructor(
        private val repo: GigsRepository,
        private val location: LocationProvider,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val _anchor =
            MutableStateFlow<MapAnchor?>(
                location.cachedCoordinate()?.toMapAnchor(),
            )

        /** "You are here" anchor handed to the shell. */
        val anchor: StateFlow<MapAnchor?> = _anchor.asStateFlow()

        /** The task set currently driving the map — fetched from in-bounds. */
        private var items: List<TaskMapItem> = emptyList()

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

        fun load() {
            viewModelScope.launch {
                resolveLocation(refresh = false)
                fetchInBounds()
            }
        }

        fun refresh() = load()

        /** Re-resolve GPS and re-fetch the viewport around the updated anchor. */
        fun locate() {
            viewModelScope.launch {
                resolveLocation(refresh = true)
                fetchInBounds()
            }
        }

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

        private suspend fun resolveLocation(refresh: Boolean) {
            val coordinate =
                when {
                    refresh -> location.requestCurrent()
                    _anchor.value != null -> location.cachedCoordinate() ?: location.requestCurrent()
                    else -> location.requestCurrent()
                }
            coordinate?.let { _anchor.value = it.toMapAnchor() }
        }

        /**
         * Build a ~1.3 km square viewport around the anchor and hit
         * `GET /api/gigs/in-bounds` for *all* categories in view. The
         * category chips are an instant client-side filter (`recompute`), so
         * an initial category scope can widen back to "All" without a
         * re-fetch.
         */
        private suspend fun fetchInBounds() {
            _state.value = TasksMapUiState.Loading
            val center = _anchor.value ?: MapAnchor(FALLBACK_LAT, FALLBACK_LON)
            val result =
                repo.inBounds(
                    minLat = center.latitude - HALF_DEG_LAT,
                    minLon = center.longitude - HALF_DEG_LON,
                    maxLat = center.latitude + HALF_DEG_LAT,
                    maxLon = center.longitude + HALF_DEG_LON,
                )
            when (result) {
                is NetworkResult.Success -> {
                    items = result.data.gigs.mapNotNull { project(it, center) }
                    recompute()
                }
                is NetworkResult.Failure -> {
                    _state.value = TasksMapUiState.Error(result.error.message)
                }
            }
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
                items.filter { _activeCategory.value == GigsCategory.All || it.category == _activeCategory.value }
            return when (_activeSort.value) {
                GigsSort.Newest -> filtered // backend returns newest-first
                GigsSort.Closest -> filtered.sortedBy { distanceMiles(it.distanceLabel) }
                GigsSort.HighestPay -> filtered.sortedByDescending { priceValue(it.price) }
                GigsSort.FewestBids -> filtered.sortedBy { it.bidCount }
            }
        }

        companion object {
            const val CATEGORY_KEY = "category"

            private const val HALF_DEG_LAT = 0.012 // ~1.3 km
            private const val HALF_DEG_LON = 0.016 // ~1.3 km at 40°
            private const val FALLBACK_LAT = 40.7484
            private const val FALLBACK_LON = -73.9857
            private const val EARTH_RADIUS_MILES = 3958.8

            /**
             * `GigDto` → pin↔card [TaskMapItem]. Drops gigs without
             * coordinates. Distance is computed client-side because
             * `in-bounds` doesn't project `distance_miles`.
             */
            fun project(
                gig: GigDto,
                anchor: MapAnchor,
            ): TaskMapItem? {
                val lat = gig.latitude ?: gig.approxLocation?.latitude ?: return null
                val lon = gig.longitude ?: gig.approxLocation?.longitude ?: return null
                val miles = haversineMiles(anchor.latitude, anchor.longitude, lat, lon)
                return TaskMapItem(
                    id = gig.id,
                    category = GigsCategory.fromBackendKey(gig.category),
                    state = if (gig.status == "open" || gig.status == null) MapPinState.Confirmed else MapPinState.Pending,
                    latitude = lat,
                    longitude = lon,
                    title = gig.title,
                    price = priceLabel(gig.price, gig.payType),
                    distanceLabel = String.format(Locale.US, "%.1f mi", miles),
                    bidCount = gig.bidCount ?: 0,
                )
            }

            private fun priceLabel(
                price: Double?,
                payType: String?,
            ): String {
                if (price == null) return "—"
                val formatted = if (price % 1.0 == 0.0) "$${price.toInt()}" else String.format(Locale.US, "$%.2f", price)
                return when (payType) {
                    "hourly" -> "$formatted/hr"
                    "per_session" -> "$formatted/session"
                    "per_walk" -> "$formatted/walk"
                    "per_visit" -> "$formatted/visit"
                    else -> formatted
                }
            }

            private fun haversineMiles(
                fromLat: Double,
                fromLon: Double,
                toLat: Double,
                toLon: Double,
            ): Double {
                val dLat = Math.toRadians(toLat - fromLat)
                val dLon = Math.toRadians(toLon - fromLon)
                val a =
                    sin(dLat / 2).pow(2) +
                        cos(Math.toRadians(fromLat)) * cos(Math.toRadians(toLat)) * sin(dLon / 2).pow(2)
                return EARTH_RADIUS_MILES * 2 * atan2(sqrt(a), sqrt(1 - a))
            }

            private fun distanceMiles(label: String): Double = label.substringBefore(" ").toDoubleOrNull() ?: Double.MAX_VALUE

            private fun priceValue(price: String): Double = price.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
        }
    }

private fun UserCoordinate.toMapAnchor(): MapAnchor = MapAnchor(latitude = latitude, longitude = longitude)
