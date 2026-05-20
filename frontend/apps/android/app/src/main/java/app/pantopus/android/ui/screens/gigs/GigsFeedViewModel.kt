@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.gigs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Backs the Gigs feed (Hub → Gigs pillar). Fetches `GET /api/gigs` with
 * the active category + sort, projects each gig to [GigCardContent], and
 * re-fetches when the chips or sort change.
 */
@HiltViewModel
class GigsFeedViewModel
    @Inject
    constructor(
        private val repo: GigsRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<GigsFeedUiState>(GigsFeedUiState.Loading)
        val state: StateFlow<GigsFeedUiState> = _state.asStateFlow()

        private val _activeCategory = MutableStateFlow(GigsCategory.All)
        val activeCategory: StateFlow<GigsCategory> = _activeCategory.asStateFlow()

        private val _activeSort = MutableStateFlow(GigsSort.Newest)
        val activeSort: StateFlow<GigsSort> = _activeSort.asStateFlow()

        private val _activeFilterCount = MutableStateFlow(0)
        val activeFilterCount: StateFlow<Int> = _activeFilterCount.asStateFlow()

        private val _filters = MutableStateFlow(GigFilterCriteria())
        val filters: StateFlow<GigFilterCriteria> = _filters.asStateFlow()

        private var latitude: Double? = null
        private var longitude: Double? = null
        private var radiusMiles: Double = 1.0
        private var loading = false

        /** Last fetched gigs, kept so a filter change re-derives the
         * visible rows client-side without a refetch. */
        private var loadedGigs: List<GigDto> = emptyList()

        /** Wire location coordinates + radius before the first load. */
        fun configureLocation(
            latitude: Double?,
            longitude: Double?,
            radiusMiles: Double = 1.0,
        ) {
            this.latitude = latitude
            this.longitude = longitude
            this.radiusMiles = radiusMiles
        }

        fun load() {
            if (_state.value is GigsFeedUiState.Loaded) return
            fetch()
        }

        fun refresh() = fetch()

        fun selectCategory(category: GigsCategory) {
            if (_activeCategory.value == category) return
            _activeCategory.value = category
            fetch()
        }

        fun selectSort(sort: GigsSort) {
            if (_activeSort.value == sort) return
            _activeSort.value = sort
            fetch()
        }

        /** Apply structured filters from the sheet. Re-derives the
         * visible rows from the already-loaded gigs without a refetch. */
        fun applyFilters(criteria: GigFilterCriteria) {
            _filters.value = criteria
            _activeFilterCount.value = criteria.activeCount
            rebuild()
        }

        private fun rebuild() {
            val now = Instant.now().epochSecond
            val visible = loadedGigs.filter { _filters.value.matches(it, now) }
            _state.value =
                if (visible.isEmpty()) {
                    GigsFeedUiState.Empty(radiusMiles = radiusMiles)
                } else {
                    GigsFeedUiState.Loaded(rows = visible.map(::projectCard))
                }
        }

        private fun fetch() {
            if (loading) return
            loading = true
            if (_state.value !is GigsFeedUiState.Loaded) {
                _state.value = GigsFeedUiState.Loading
            }
            viewModelScope.launch {
                try {
                    val category = _activeCategory.value
                    val sort = _activeSort.value
                    when (
                        val result =
                            repo.list(
                                category = category.key.takeIf { category != GigsCategory.All },
                                sort = sort.key,
                                latitude = latitude,
                                longitude = longitude,
                                radiusMiles = radiusMiles,
                            )
                    ) {
                        is NetworkResult.Success -> {
                            loadedGigs = result.data.gigs
                            rebuild()
                        }
                        is NetworkResult.Failure -> {
                            _state.value = GigsFeedUiState.Error(result.error.message)
                        }
                    }
                } finally {
                    loading = false
                }
            }
        }

        private fun projectCard(gig: GigDto): GigCardContent {
            val category = GigsCategory.fromBackendKey(gig.category)
            val distance = distanceLabel(gig.distanceMiles)
            val age = ageLabel(gig.createdAt)?.let { "$it ago" }
            val meta = listOfNotNull(distance, age).joinToString(" · ")
            return GigCardContent(
                id = gig.id,
                category = category,
                metaLine = meta,
                title = gig.title,
                body = gig.description.orEmpty(),
                price = priceLabel(gig.price, gig.payType),
                bidCount = gig.bidCount ?: 0,
                distanceLabel = distance,
            )
        }

        private fun priceLabel(
            price: Double?,
            payType: String?,
        ): String {
            if (price == null) return "—"
            val base =
                if (price % 1.0 == 0.0) {
                    "$${price.toInt()}"
                } else {
                    String.format("$%.2f", price)
                }
            return when (payType) {
                "hourly" -> "$base / hr"
                "per_session" -> "$base / session"
                "per_walk" -> "$base / walk"
                "per_visit" -> "$base / visit"
                else -> base
            }
        }

        private fun distanceLabel(miles: Double?): String? {
            if (miles == null) return null
            return when {
                miles < 0.1 -> "< 0.1mi"
                miles < 10 -> String.format("%.1fmi", miles)
                else -> "${miles.toInt()}mi"
            }
        }

        private fun ageLabel(iso: String?): String? {
            if (iso.isNullOrEmpty()) return null
            return runCatching {
                val instant = Instant.parse(iso)
                val seconds = Duration.between(instant, Instant.now()).seconds
                when {
                    seconds < 60 -> "now"
                    seconds < 3_600 -> "${seconds / 60}m"
                    seconds < 86_400 -> "${seconds / 3_600}h"
                    seconds < 604_800 -> "${seconds / 86_400}d"
                    else -> "${seconds / 604_800}w"
                }
            }.getOrNull()
        }
    }
