@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.nearby.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.data.listings.ListingsRepository
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.location.UserCoordinate
import app.pantopus.android.ui.screens.gigs.GigsCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Backs the Nearby Map+List Hybrid (T2.4). Fetches `/api/gigs/in-bounds`
 * + `/api/listings/in-bounds` for the current viewport, fans the results
 * into a homogeneous [MapEntity] list, and keeps a single `selectedId`
 * so pin↔card highlights stay in sync.
 */
@HiltViewModel
class NearbyMapViewModel
    @Inject
    constructor(
        private val gigs: GigsRepository,
        private val listings: ListingsRepository,
        private val location: LocationProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow<NearbyMapUiState>(NearbyMapUiState.Loading)
        val state: StateFlow<NearbyMapUiState> = _state.asStateFlow()

        private val _activeCategory = MutableStateFlow(GigsCategory.All)
        val activeCategory: StateFlow<GigsCategory> = _activeCategory.asStateFlow()

        private val _activeSort = MutableStateFlow(NearbySort.Closest)
        val activeSort: StateFlow<NearbySort> = _activeSort.asStateFlow()

        private val _sheetStop = MutableStateFlow(SheetStop.Standard)
        val sheetStop: StateFlow<SheetStop> = _sheetStop.asStateFlow()

        private val _userCoordinate = MutableStateFlow(location.cachedCoordinate())
        val userCoordinate: StateFlow<UserCoordinate?> = _userCoordinate.asStateFlow()

        private var entities: List<MapEntity> = emptyList()
        private var loading = false

        fun load() {
            if (_state.value is NearbyMapUiState.Loaded) return
            fetch()
        }

        fun refresh() = fetch()

        fun selectCategory(category: GigsCategory) {
            if (_activeCategory.value == category) return
            _activeCategory.value = category
            fetch()
        }

        fun selectSort(sort: NearbySort) {
            if (_activeSort.value == sort) return
            _activeSort.value = sort
            rebuild(currentSelectedId())
        }

        fun selectEntity(id: String?) {
            val current = _state.value as? NearbyMapUiState.Loaded ?: return
            _state.value = current.copy(selectedId = id)
        }

        fun setSheetStop(stop: SheetStop) {
            _sheetStop.value = stop
        }

        private fun currentSelectedId(): String? = (_state.value as? NearbyMapUiState.Loaded)?.selectedId

        private fun fetch() {
            if (loading) return
            loading = true
            viewModelScope.launch {
                try {
                    val coord = _userCoordinate.value ?: location.requestCurrent()
                    if (coord != _userCoordinate.value) _userCoordinate.value = coord
                    val center = coord ?: UserCoordinate(40.7484, -73.9857, 100.0)
                    val halfDegLat = 0.012
                    val halfDegLon = 0.016
                    val minLat = center.latitude - halfDegLat
                    val maxLat = center.latitude + halfDegLat
                    val minLon = center.longitude - halfDegLon
                    val maxLon = center.longitude + halfDegLon
                    val categoryParam = _activeCategory.value.key.takeIf { _activeCategory.value != GigsCategory.All }

                    if (_state.value !is NearbyMapUiState.Loaded) {
                        _state.value = NearbyMapUiState.Loading
                    }

                    val gigsDeferred = async {
                        gigs.inBounds(minLat = minLat, minLon = minLon, maxLat = maxLat, maxLon = maxLon, category = categoryParam)
                    }
                    val listingsDeferred = async {
                        listings.inBounds(south = minLat, west = minLon, north = maxLat, east = maxLon, category = categoryParam)
                    }
                    val gigsResult = gigsDeferred.await()
                    val listingsResult = listingsDeferred.await()

                    val gigRows = (gigsResult as? NetworkResult.Success)?.data?.gigs.orEmpty()
                    val listingRows = (listingsResult as? NetworkResult.Success)?.data?.listings.orEmpty()
                    val bothFailed = gigsResult is NetworkResult.Failure && listingsResult is NetworkResult.Failure
                    if (bothFailed) {
                        val message = (gigsResult as NetworkResult.Failure).error.message
                        _state.value = NearbyMapUiState.Error(message)
                        return@launch
                    }
                    entities = project(gigs = gigRows, listings = listingRows, anchor = center)
                    rebuild(selectedId = null)
                } finally {
                    loading = false
                }
            }
        }

        private fun rebuild(selectedId: String?) {
            val sorted = sortFor(entities)
            _state.value = NearbyMapUiState.Loaded(
                entities = sorted,
                userCoordinate = _userCoordinate.value,
                selectedId = selectedId,
            )
        }

        private fun sortFor(source: List<MapEntity>): List<MapEntity> =
            when (_activeSort.value) {
                NearbySort.Closest ->
                    source.sortedBy {
                        it.distanceLabel?.replace("mi", "")?.trim()?.toDoubleOrNull() ?: Double.MAX_VALUE
                    }
                NearbySort.HighestPay ->
                    source.sortedByDescending { priceValue(it.price) ?: Double.NEGATIVE_INFINITY }
                NearbySort.FewestBids -> source.sortedBy { it.bidCount }
                NearbySort.Newest -> source
            }

        private fun priceValue(raw: String?): Double? {
            if (raw == null) return null
            val digits = raw.filter { it.isDigit() || it == '.' }
            return digits.toDoubleOrNull()
        }

        private fun project(
            gigs: List<GigDto>,
            listings: List<ListingDto>,
            anchor: UserCoordinate,
        ): List<MapEntity> {
            val out = mutableListOf<MapEntity>()
            gigs.forEach { gig ->
                val coord = gigCoord(gig) ?: return@forEach
                val distance = distanceMiles(anchor, coord.first, coord.second)
                out +=
                    MapEntity(
                        id = gig.id,
                        kind = MapEntityKind.Gig,
                        category = GigsCategory.fromBackendKey(gig.category),
                        state = if (gig.status == "pending" || gig.status == "draft") MapEntityState.Pending else MapEntityState.Confirmed,
                        latitude = coord.first,
                        longitude = coord.second,
                        title = gig.title,
                        summary = gig.description,
                        price = priceLabel(gig.price, gig.payType),
                        distanceLabel = distanceLabel(distance),
                        bidCount = gig.bidCount ?: 0,
                    )
            }
            listings.forEach { listing ->
                val coord = listingCoord(listing) ?: return@forEach
                val distance = distanceMiles(anchor, coord.first, coord.second)
                out +=
                    MapEntity(
                        id = listing.id,
                        kind = MapEntityKind.Listing,
                        category = GigsCategory.fromBackendKey(listing.category),
                        state = MapEntityState.Confirmed,
                        latitude = coord.first,
                        longitude = coord.second,
                        title = listing.title ?: "Listing",
                        summary = null,
                        price = priceLabel(listing.price, null),
                        distanceLabel = distanceLabel(distance),
                        bidCount = 0,
                    )
            }
            return out
        }

        private fun gigCoord(gig: GigDto): Pair<Double, Double>? {
            val lat = gig.latitude ?: gig.approxLocation?.latitude ?: return null
            val lon = gig.longitude ?: gig.approxLocation?.longitude ?: return null
            return lat to lon
        }

        private fun listingCoord(listing: ListingDto): Pair<Double, Double>? {
            val lat = listing.latitude ?: listing.approxLocation?.latitude ?: return null
            val lon = listing.longitude ?: listing.approxLocation?.longitude ?: return null
            return lat to lon
        }

        private fun distanceMiles(
            origin: UserCoordinate,
            lat: Double,
            lon: Double,
        ): Double {
            val earthMiles = 3958.8
            val dLat = (lat - origin.latitude) * Math.PI / 180
            val dLon = (lon - origin.longitude) * Math.PI / 180
            val lat1 = origin.latitude * Math.PI / 180
            val lat2 = lat * Math.PI / 180
            val a = sin(dLat / 2) * sin(dLat / 2) + sin(dLon / 2) * sin(dLon / 2) * cos(lat1) * cos(lat2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthMiles * c
        }

        private fun distanceLabel(miles: Double): String? {
            if (miles.isNaN() || miles.isInfinite()) return null
            return when {
                miles < 0.1 -> "< 0.1 mi"
                miles < 10 -> String.format("%.1f mi", miles)
                else -> "${miles.toInt()} mi"
            }
        }

        private fun priceLabel(
            price: Double?,
            payType: String?,
        ): String? {
            if (price == null) return null
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
    }
