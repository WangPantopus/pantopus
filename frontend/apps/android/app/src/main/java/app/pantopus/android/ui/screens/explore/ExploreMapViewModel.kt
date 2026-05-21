@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.explore

import androidx.lifecycle.ViewModel
import app.pantopus.android.data.location.UserCoordinate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Backs the A11.2 Explore map. Holds the loaded sample entities, the top
 * type-toggle selection, the sort, the applied filter criteria, and a
 * single `selectedId` so pin taps and rail-card highlights stay in sync.
 * Filtering + clustering run locally over the sample set (no network —
 * backend removed from the repo). Mirrors `ExploreMapViewModel.swift`.
 */
@HiltViewModel
class ExploreMapViewModel
    @Inject
    constructor() : ViewModel() {
        private val _state = MutableStateFlow<ExploreMapUiState>(ExploreMapUiState.Loading)
        val state: StateFlow<ExploreMapUiState> = _state.asStateFlow()

        private val _activeKind = MutableStateFlow<ExploreKind?>(null)
        val activeKind: StateFlow<ExploreKind?> = _activeKind.asStateFlow()

        private val _activeSort = MutableStateFlow(ExploreSort.Closest)
        val activeSort: StateFlow<ExploreSort> = _activeSort.asStateFlow()

        private val _sheetStop = MutableStateFlow(ExploreSheetStop.Standard)
        val sheetStop: StateFlow<ExploreSheetStop> = _sheetStop.asStateFlow()

        private val _userCoordinate = MutableStateFlow<UserCoordinate?>(ExploreMapSampleData.center)
        val userCoordinate: StateFlow<UserCoordinate?> = _userCoordinate.asStateFlow()

        private val _filters = MutableStateFlow(ExploreFilterCriteria())
        val filters: StateFlow<ExploreFilterCriteria> = _filters.asStateFlow()

        private var scenario: ExploreScenario = ExploreScenario.Populated
        private var allEntities: List<ExploreEntity> = emptyList()
        private var clusterRadiusDegrees: Double = 0.005

        fun load(scenario: ExploreScenario = ExploreScenario.Populated) {
            this.scenario = scenario
            allEntities = ExploreMapSampleData.entities(scenario)
            _filters.value = ExploreMapSampleData.filters(scenario)
            when (scenario) {
                ExploreScenario.Loading -> _state.value = ExploreMapUiState.Loading
                ExploreScenario.Error -> _state.value = ExploreMapUiState.Error("Couldn't load the map.")
                else -> rebuild(selectedId = null)
            }
        }

        fun refresh() = load(scenario)

        // MARK: Type toggle

        fun selectKind(kind: ExploreKind?) {
            if (_activeKind.value == kind) return
            _activeKind.value = kind
            rebuild(selectedId = keptSelection())
        }

        // MARK: Sort

        fun selectSort(sort: ExploreSort) {
            if (_activeSort.value == sort) return
            _activeSort.value = sort
            rebuild(selectedId = currentSelectedId())
        }

        // MARK: Filters

        fun applyFilters(criteria: ExploreFilterCriteria) {
            _filters.value = criteria
            rebuild(selectedId = keptSelection())
        }

        /** Empty-state "Clear filters" — reset every dimension. */
        fun clearFilters() {
            _filters.value = ExploreFilterCriteria()
            _activeKind.value = null
            rebuild(selectedId = null)
        }

        /** Empty-state "Widen area" — open the radius to the widest stop. */
        fun widenArea() {
            _filters.value = _filters.value.copy(distanceUpper = ExploreFilterCriteria.DISTANCE_STOPS.last())
            rebuild(selectedId = currentSelectedId())
        }

        // MARK: Selection / sheet

        fun selectEntity(id: String?) {
            val current = _state.value as? ExploreMapUiState.Loaded ?: return
            _state.value = current.copy(selectedId = id)
        }

        fun setSheetStop(stop: ExploreSheetStop) {
            _sheetStop.value = stop
        }

        fun setClusterRadius(radiusDegrees: Double) {
            val clamped = radiusDegrees.coerceIn(0.0005, 0.05)
            if (kotlin.math.abs(clamped - clusterRadiusDegrees) < 1e-6) return
            clusterRadiusDegrees = clamped
            val current = _state.value as? ExploreMapUiState.Loaded ?: return
            rebuild(selectedId = current.selectedId)
        }

        // MARK: Projection

        private fun currentSelectedId(): String? = (_state.value as? ExploreMapUiState.Loaded)?.selectedId

        private fun keptSelection(): String? {
            val prior = currentSelectedId()
            return if (filtered().any { it.id == prior }) prior else null
        }

        private fun filtered(): List<ExploreEntity> =
            allEntities.filter { entity ->
                val kind = _activeKind.value
                (kind == null || entity.kind == kind) && _filters.value.matches(entity)
            }

        private fun sortFor(source: List<ExploreEntity>): List<ExploreEntity> =
            when (_activeSort.value) {
                ExploreSort.Closest -> source.sortedBy { it.distanceMiles }
                ExploreSort.Newest -> source
            }

        private fun rebuild(selectedId: String?) {
            val sorted = sortFor(filtered())
            _state.value =
                ExploreMapUiState.Loaded(
                    entities = sorted,
                    markers = cluster(sorted, clusterRadiusDegrees),
                    userCoordinate = _userCoordinate.value,
                    selectedId = selectedId,
                )
        }

        companion object {
            /** Grid-bucket clusterer — buckets of ≥2 collapse into a cluster. */
            fun cluster(
                entities: List<ExploreEntity>,
                radiusDegrees: Double,
            ): List<ExploreMarker> {
                if (radiusDegrees <= 0) return entities.map { ExploreMarker.Entity(it) }
                val buckets = linkedMapOf<String, MutableList<ExploreEntity>>()
                entities.forEach { entity ->
                    val key = bucketKey(entity.latitude, entity.longitude, radiusDegrees)
                    buckets.getOrPut(key) { mutableListOf() }.add(entity)
                }
                return buckets.entries.sortedBy { it.key }.map { (key, group) ->
                    if (group.size == 1) {
                        ExploreMarker.Entity(group[0])
                    } else {
                        val lats = group.map { it.latitude }
                        val lons = group.map { it.longitude }
                        val representative =
                            group.groupingBy { it.kind }.eachCount().maxByOrNull { it.value }?.key ?: group[0].kind
                        ExploreMarker.Cluster(
                            ExploreCluster(
                                id = key,
                                latitude = lats.average(),
                                longitude = lons.average(),
                                kind = representative,
                                count = group.size,
                                entityIds = group.map { it.id },
                                minLatitude = lats.min(),
                                maxLatitude = lats.max(),
                                minLongitude = lons.min(),
                                maxLongitude = lons.max(),
                            ),
                        )
                    }
                }
            }

            private fun bucketKey(
                latitude: Double,
                longitude: Double,
                radius: Double,
            ): String {
                val latBucket = kotlin.math.floor(latitude / radius).toInt()
                val lonBucket = kotlin.math.floor(longitude / radius).toInt()
                return "${latBucket}_$lonBucket"
            }
        }
    }
