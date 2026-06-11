@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.gigs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.GigsBrowseResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.realtime.SocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.Locale
import javax.inject.Inject

/**
 * Backs the Gigs feed (Hub → Gigs pillar).
 *
 * Two modes (P1.F): with no category scope and no structured filters the
 * feed renders the sectioned **browse** surface from `GET /api/gigs/browse`;
 * any category selection, filter, sort change, or "See all" drops to the
 * **flat** list from `GET /api/gigs`. Re-selecting the "All" chip returns
 * to browse.
 *
 * Also owns the radius suggestion ladder (P1.B), "Not interested" dismiss +
 * hide-category with undo (P1.D), and the realtime `gig:new` counter (P1.E).
 */
@HiltViewModel
class GigsFeedViewModel
    @Inject
    constructor(
        private val repo: GigsRepository,
        private val socket: SocketManager,
        private val authRepo: AuthRepository,
        private val location: LocationProvider,
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

        /** P1.B — non-null when the slim "Search Y mi" banner should show. */
        private val _radiusSuggestion = MutableStateFlow<GigsRadiusSuggestion?>(null)
        val radiusSuggestion: StateFlow<GigsRadiusSuggestion?> = _radiusSuggestion.asStateFlow()

        /** P1.E — count of `gig:new` events from other users since the last load. */
        private val _newTaskCount = MutableStateFlow(0)
        val newTaskCount: StateFlow<Int> = _newTaskCount.asStateFlow()

        /** P1.D — transient toast (auto-dismissed by the screen after ~5s). */
        private val _toast = MutableStateFlow<GigsFeedToast?>(null)
        val toast: StateFlow<GigsFeedToast?> = _toast.asStateFlow()

        private var latitude: Double? = null
        private var longitude: Double? = null
        private var radiusMiles: Double = RADIUS_LADDER_MILES.first()
        private var loading = false

        /** P1.B — banner dismissed for this session (VM lifetime). */
        private var radiusSuggestionDismissed = false

        /** P1.F — sticky flat-list override after "See all" / sort change. */
        private var browseExited = false

        private var socketJob: Job? = null

        /** Last fetched gigs, kept so a filter change re-derives the
         * visible rows client-side without a refetch. */
        private var loadedGigs: List<GigDto> = emptyList()

        /** P1.D — original index + row of in-flight dismissals, for undo. */
        private val pendingDismissals = mutableMapOf<String, Pair<Int, GigDto>>()

        /** P1.D — rows removed per hidden category, for undo. */
        private val pendingCategoryHides = mutableMapOf<GigsCategory, List<IndexedValue<GigDto>>>()

        /** Wire location coordinates + radius before the first load. */
        fun configureLocation(
            latitude: Double?,
            longitude: Double?,
            radiusMiles: Double = RADIUS_LADDER_MILES.first(),
        ) {
            this.latitude = latitude
            this.longitude = longitude
            this.radiusMiles = radiusMiles
        }

        fun load() {
            subscribeToSocket()
            if (_state.value is GigsFeedUiState.Loaded || _state.value is GigsFeedUiState.BrowseLoaded) return
            fetch()
        }

        fun refresh() = fetch()

        fun selectCategory(category: GigsCategory) {
            val reEntersBrowse = category == GigsCategory.All && browseExited
            if (_activeCategory.value == category && !reEntersBrowse) return
            _activeCategory.value = category
            // Tapping "All" again is the explicit path back into browse mode.
            if (category == GigsCategory.All) browseExited = false
            fetch()
        }

        fun selectSort(sort: GigsSort) {
            if (_activeSort.value == sort) return
            _activeSort.value = sort
            // Sort is a flat-list concept — choosing one exits browse mode
            // (same semantics as a section "See all").
            if (isBrowseMode()) browseExited = true
            fetch()
        }

        /**
         * P1.F — "See all" on a browse section / the "See all N tasks"
         * footer. Switches to the flat list with the section's sort.
         */
        fun exitBrowse(sort: GigsSort = _activeSort.value) {
            browseExited = true
            _activeSort.value = sort
            fetch()
        }

        /**
         * P0.4 — apply structured filters from the sheet. Server-expressible
         * dimensions (budget → `minPrice`/`maxPrice`, open-to-bids →
         * `pay_type=offers`, exactly-one schedule → `schedule_type`) ride
         * the refetch as query params; [rebuild] keeps only what the server
         * can't express client-side.
         */
        fun applyFilters(criteria: GigFilterCriteria) {
            _filters.value = criteria
            _activeFilterCount.value = criteria.activeCount
            fetch()
        }

        // MARK: - P1.B Radius suggestion

        /** Accept the suggestion: bump the radius to the next step + refetch. */
        fun acceptRadiusSuggestion() {
            val suggestion = _radiusSuggestion.value ?: return
            radiusMiles = suggestion.suggestedRadiusMiles
            _radiusSuggestion.value = null
            fetch()
        }

        /** Dismiss the banner for the rest of this session. */
        fun dismissRadiusSuggestion() {
            radiusSuggestionDismissed = true
            _radiusSuggestion.value = null
        }

        // MARK: - P1.D Dismiss / hide

        /**
         * "Not interested": optimistically drop the row, POST the
         * dismissal, and surface an undo toast. A failed POST restores the
         * row and flips the toast to an error.
         */
        fun dismissGig(gigId: String) {
            val index = loadedGigs.indexOfFirst { it.id == gigId }
            if (index < 0) return
            val gig = loadedGigs[index]
            pendingDismissals[gigId] = index to gig
            loadedGigs = loadedGigs.filterNot { it.id == gigId }
            rebuild()
            _toast.value =
                GigsFeedToast(
                    text = "Not interested — we'll show fewer like this.",
                    undo = GigsFeedUndo.Dismiss(gigId),
                )
            viewModelScope.launch {
                when (val result = repo.dismissGig(gigId)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure -> {
                        restoreDismissedGig(gigId)
                        _toast.value = GigsFeedToast(text = result.error.message, isError = true)
                    }
                }
            }
        }

        /** Undo a dismissal: DELETE the record + reinsert the row. */
        fun undoDismissGig(gigId: String) {
            if (pendingDismissals[gigId] == null) return
            restoreDismissedGig(gigId)
            _toast.value = null
            viewModelScope.launch { repo.undoDismissGig(gigId) }
        }

        /**
         * "Hide all <Category>": optimistically remove every row of the
         * category, POST hidden-categories, undo restores via DELETE.
         */
        fun hideCategory(category: GigsCategory) {
            if (category == GigsCategory.All) return
            val removed =
                loadedGigs
                    .withIndex()
                    .filter { GigsCategory.fromBackendKey(it.value.category) == category }
            if (removed.isEmpty()) return
            pendingCategoryHides[category] = removed
            val removedIds = removed.map { it.value.id }.toSet()
            loadedGigs = loadedGigs.filterNot { it.id in removedIds }
            rebuild()
            _toast.value =
                GigsFeedToast(
                    text = "All ${category.label} tasks hidden.",
                    undo = GigsFeedUndo.HideCategory(category),
                )
            viewModelScope.launch {
                when (val result = repo.hideCategory(category.key)) {
                    is NetworkResult.Success -> Unit
                    is NetworkResult.Failure -> {
                        restoreHiddenCategory(category)
                        _toast.value = GigsFeedToast(text = result.error.message, isError = true)
                    }
                }
            }
        }

        /** Undo a category hide: DELETE hidden-categories/{category} + reinsert. */
        fun undoHideCategory(category: GigsCategory) {
            if (pendingCategoryHides[category] == null) return
            restoreHiddenCategory(category)
            _toast.value = null
            viewModelScope.launch { repo.unhideCategory(category.key) }
        }

        /** Route a toast's undo action. */
        fun undo(undo: GigsFeedUndo) {
            when (undo) {
                is GigsFeedUndo.Dismiss -> undoDismissGig(undo.gigId)
                is GigsFeedUndo.HideCategory -> undoHideCategory(undo.category)
            }
        }

        fun dismissToast() {
            _toast.value = null
        }

        private fun restoreDismissedGig(gigId: String) {
            val (index, gig) = pendingDismissals.remove(gigId) ?: return
            loadedGigs =
                loadedGigs.toMutableList().also {
                    it.add(index.coerceAtMost(it.size), gig)
                }
            rebuild()
        }

        private fun restoreHiddenCategory(category: GigsCategory) {
            val removed = pendingCategoryHides.remove(category) ?: return
            loadedGigs =
                loadedGigs.toMutableList().also { list ->
                    removed.forEach { (index, gig) -> list.add(index.coerceAtMost(list.size), gig) }
                }
            rebuild()
        }

        // MARK: - P1.E Realtime new-task counter

        /** Banner tap: clear the counter + refetch. */
        fun refreshFromNewTasksBanner() {
            _newTaskCount.value = 0
            fetch()
        }

        private fun subscribeToSocket() {
            if (socketJob != null) return
            socketJob =
                viewModelScope.launch {
                    socket.eventsOf(GIG_NEW_EVENT).collect { json ->
                        onGigNewEvent(posterId = json.optString("userId").takeIf { it.isNotEmpty() })
                    }
                }
        }

        /** Count a broadcast unless the viewer posted the gig themselves. */
        internal fun onGigNewEvent(posterId: String?) {
            if (posterId != null && posterId == currentUserId()) return
            _newTaskCount.value += 1
        }

        private fun currentUserId(): String? = (authRepo.state.value as? AuthRepository.State.SignedIn)?.user?.id

        // MARK: - Fetch

        /** Browse mode: "All" scope, no structured filters, no See-all override. */
        private fun isBrowseMode(): Boolean =
            _activeCategory.value == GigsCategory.All &&
                _activeFilterCount.value == 0 &&
                !browseExited

        private fun fetch() {
            if (loading) return
            loading = true
            _newTaskCount.value = 0
            val browse = isBrowseMode()
            val keepsContent =
                (browse && _state.value is GigsFeedUiState.BrowseLoaded) ||
                    (!browse && _state.value is GigsFeedUiState.Loaded)
            if (!keepsContent) {
                _state.value = if (browse) GigsFeedUiState.BrowseLoading else GigsFeedUiState.Loading
            }
            viewModelScope.launch {
                try {
                    ensureLocation()
                    if (browse && latitude != null && longitude != null) {
                        fetchBrowse()
                    } else {
                        fetchFlat()
                    }
                } finally {
                    loading = false
                }
            }
        }

        /** Resolve a coordinate once (cached → fresh); [configureLocation] overrides. */
        private suspend fun ensureLocation() {
            if (latitude != null && longitude != null) return
            val coordinate = location.cachedCoordinate() ?: location.requestCurrent()
            if (coordinate != null) {
                latitude = coordinate.latitude
                longitude = coordinate.longitude
            }
        }

        /** P1.F — sectioned browse fetch. Radius omitted ⇒ server default (~100 mi). */
        private suspend fun fetchBrowse() {
            val lat = latitude ?: return fetchFlat()
            val lng = longitude ?: return fetchFlat()
            // The radius ladder is a flat-list concept — drop any stale banner.
            _radiusSuggestion.value = null
            when (val result = repo.browse(lat, lng)) {
                is NetworkResult.Success -> {
                    val content = projectBrowse(result.data)
                    _state.value =
                        if (content.isEmpty) {
                            GigsFeedUiState.Empty(radiusMiles = radiusMiles)
                        } else {
                            GigsFeedUiState.BrowseLoaded(content)
                        }
                }
                is NetworkResult.Failure -> {
                    _state.value = GigsFeedUiState.Error(result.error.message)
                }
            }
        }

        private suspend fun fetchFlat() {
            val category = _activeCategory.value
            val sort = _activeSort.value
            val criteria = _filters.value
            when (
                val result =
                    repo.list(
                        category = category.key.takeIf { category != GigsCategory.All },
                        sort = sort.key,
                        latitude = latitude,
                        longitude = longitude,
                        radiusMiles = radiusMiles,
                        minPrice =
                            criteria.budgetLower
                                .toDouble()
                                .takeIf { criteria.budgetLower > GigFilterCriteria.BUDGET_MIN },
                        maxPrice =
                            criteria.budgetUpper
                                .toDouble()
                                // BUDGET_MAX is the "$500+" no-ceiling handle.
                                .takeIf { criteria.budgetUpper < GigFilterCriteria.BUDGET_MAX },
                        scheduleType = criteria.schedules.singleOrNull()?.backendValue,
                        payType = if (criteria.openToBids) OFFERS_PAY_TYPE else null,
                    )
            ) {
                is NetworkResult.Success -> {
                    loadedGigs = result.data.gigs
                    pendingDismissals.clear()
                    pendingCategoryHides.clear()
                    rebuild()
                    updateRadiusSuggestion()
                }
                is NetworkResult.Failure -> {
                    _state.value = GigsFeedUiState.Error(result.error.message)
                }
            }
        }

        /**
         * Client-side residual filtering over the fetched page: multi-
         * category, multi-schedule intersection, and posted-within (the
         * backend has no posted-within param). The server-applied
         * dimensions re-check harmlessly.
         */
        private fun rebuild() {
            val now = Instant.now().epochSecond
            val visible = loadedGigs.filter { _filters.value.matches(it, now) }
            _state.value =
                if (visible.isEmpty()) {
                    GigsFeedUiState.Empty(radiusMiles = radiusMiles)
                } else {
                    GigsFeedUiState.Loaded(rows = visible.map { projectCard(it) })
                }
        }

        /**
         * P1.B — after a flat load: fewer than [RADIUS_SUGGESTION_THRESHOLD]
         * visible rows with no active filters suggests the next radius
         * ladder step (1 → 3 → 5 → 10 mi cap).
         */
        private fun updateRadiusSuggestion() {
            val visibleCount =
                when (val current = _state.value) {
                    is GigsFeedUiState.Loaded -> current.rows.size
                    is GigsFeedUiState.Empty -> 0
                    else -> return
                }
            val nextRadius = RADIUS_LADDER_MILES.firstOrNull { it > radiusMiles }
            val shouldSuggest =
                nextRadius != null &&
                    visibleCount < RADIUS_SUGGESTION_THRESHOLD &&
                    _activeFilterCount.value == 0 &&
                    !radiusSuggestionDismissed
            _radiusSuggestion.value =
                if (shouldSuggest && nextRadius != null) {
                    GigsRadiusSuggestion(
                        visibleCount = visibleCount,
                        currentRadiusMiles = radiusMiles,
                        suggestedRadiusMiles = nextRadius,
                    )
                } else {
                    null
                }
        }

        // MARK: - Projection

        /** P1.F — DTO sections → render-only [GigsBrowseContent]. */
        internal fun projectBrowse(response: GigsBrowseResponse): GigsBrowseContent =
            GigsBrowseContent(
                bestMatches = response.sections.bestMatches.take(VERTICAL_SECTION_LIMIT).map { projectCard(it) },
                urgent = response.sections.urgent.map { projectRailCard(it) },
                newToday = response.sections.newToday.take(VERTICAL_SECTION_LIMIT).map { projectCard(it) },
                highPaying = response.sections.highPaying.map { projectRailCard(it) },
                quickJobs = response.sections.quickJobs.take(VERTICAL_SECTION_LIMIT).map { projectCard(it) },
                clusters =
                    response.sections.clusters.mapNotNull { cluster ->
                        val count = cluster.count ?: 0
                        if (cluster.category.isNullOrEmpty() || count <= 0) {
                            null
                        } else {
                            GigsBrowseClusterChip(
                                category = GigsCategory.fromBackendKey(cluster.category),
                                count = count,
                            )
                        }
                    },
                totalActive = response.totalActive ?: 0,
            )

        companion object {
            /** P0.4 — `pay_type` wire value for the open-to-bids filter. */
            private const val OFFERS_PAY_TYPE = "offers"

            /** P1.E — global broadcast emitted on gig creation (`backend/routes/gigs.js:1080`). */
            internal const val GIG_NEW_EVENT = "gig:new"

            /** P1.B — radius ladder; the last entry is the cap. */
            internal val RADIUS_LADDER_MILES = listOf(1.0, 3.0, 5.0, 10.0)

            /** P1.B — suggest widening when fewer rows than this load. */
            internal const val RADIUS_SUGGESTION_THRESHOLD = 3

            /** P1.F — vertical sections cap at three rows. */
            internal const val VERTICAL_SECTION_LIMIT = 3

            private const val METERS_PER_MILE = 1_609.344

            /**
             * `GigDto` → render-only [GigCardContent]. Exposed on the
             * companion so the Gig Search surface projects identical rows
             * without duplicating the meta / price / distance formatting.
             */
            fun projectCard(gig: GigDto): GigCardContent {
                val category = GigsCategory.fromBackendKey(gig.category)
                val distance = distanceLabel(resolvedDistanceMiles(gig))
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
                    isUrgent = gig.isUrgent == true,
                )
            }

            /** P1.F — `GigDto` → horizontal rail card (urgent / high paying). */
            fun projectRailCard(gig: GigDto): GigRailCardContent =
                GigRailCardContent(
                    id = gig.id,
                    category = GigsCategory.fromBackendKey(gig.category),
                    title = gig.title,
                    price = priceLabel(gig.price, gig.payType),
                    distanceLabel = distanceLabel(resolvedDistanceMiles(gig)),
                    bidCount = gig.bidCount ?: 0,
                )

            /** Spatial-RPC rows carry `distance_meters`; list rows `distance_miles`. */
            private fun resolvedDistanceMiles(gig: GigDto): Double? =
                gig.distanceMiles ?: gig.distanceMeters?.let { it / METERS_PER_MILE }

            private fun priceLabel(
                price: Double?,
                payType: String?,
            ): String {
                if (price == null) return "—"
                val base =
                    if (price % 1.0 == 0.0) {
                        "$${price.toInt()}"
                    } else {
                        String.format(Locale.US, "$%.2f", price)
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
                    miles < 10 -> String.format(Locale.US, "%.1fmi", miles)
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
    }
