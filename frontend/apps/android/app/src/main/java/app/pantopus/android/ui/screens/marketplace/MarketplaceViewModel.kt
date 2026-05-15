@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.marketplace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.listings.ListingsRepository
import app.pantopus.android.data.location.LocationProvider
import app.pantopus.android.data.location.UserCoordinate
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Backs the Marketplace tab (T2.5). Fetches `/api/listings/nearby` with
 * the active layer + free filter and projects each row into a
 * `MarketplaceCardContent`.
 */
@HiltViewModel
class MarketplaceViewModel
    @Inject
    constructor(
        private val repo: ListingsRepository,
        private val location: LocationProvider,
    ) : ViewModel() {
        private val _state = MutableStateFlow<MarketplaceUiState>(MarketplaceUiState.Loading)
        val state: StateFlow<MarketplaceUiState> = _state.asStateFlow()

        private val _activeCategory = MutableStateFlow(MarketplaceCategory.All)
        val activeCategory: StateFlow<MarketplaceCategory> = _activeCategory.asStateFlow()

        private val _searchText = MutableStateFlow("")
        val searchText: StateFlow<String> = _searchText.asStateFlow()

        private var radiusMiles: Double = 2.0
        private var loading = false

        fun configureRadius(miles: Double) {
            radiusMiles = miles
        }

        fun load() {
            if (_state.value is MarketplaceUiState.Loaded) return
            fetch()
        }

        fun refresh() = fetch()

        fun selectCategory(category: MarketplaceCategory) {
            if (_activeCategory.value == category) return
            _activeCategory.value = category
            fetch()
        }

        fun setSearchText(value: String) {
            _searchText.value = value
        }

        fun submitSearch() = fetch()

        private fun fetch() {
            if (loading) return
            loading = true
            viewModelScope.launch {
                try {
                    val coord = location.cachedCoordinate() ?: location.requestCurrent()
                    val center = coord ?: UserCoordinate(40.7484, -73.9857, 100.0)
                    if (_state.value !is MarketplaceUiState.Loaded) {
                        _state.value = MarketplaceUiState.Loading
                    }
                    val category = _activeCategory.value
                    val result =
                        repo.nearby(
                            latitude = center.latitude,
                            longitude = center.longitude,
                            radiusMiles = radiusMiles,
                            layer = category.layerParam,
                            isFree = if (category == MarketplaceCategory.Free) true else null,
                            search = _searchText.value.takeIf { it.isNotEmpty() },
                        )
                    when (result) {
                        is NetworkResult.Success -> {
                            val listings = result.data.listings
                            _state.value =
                                if (listings.isEmpty()) {
                                    MarketplaceUiState.Empty(radiusMiles = radiusMiles)
                                } else {
                                    MarketplaceUiState.Loaded(rows = listings.map(::projectCard))
                                }
                        }
                        is NetworkResult.Failure -> {
                            _state.value = MarketplaceUiState.Error(result.error.message)
                        }
                    }
                } finally {
                    loading = false
                }
            }
        }

        private fun projectCard(row: ListingDto): MarketplaceCardContent {
            val imageUrl = row.firstImage ?: row.mediaUrls?.firstOrNull()
            val gradient = ListingGradient.from(row.id)
            val icon = placeholderIcon(category = row.category, layer = row.layer)
            val isFree = row.isFree ?: false
            val price = priceLabel(row.price, isFree, row.layer, row.listingType)
            val distance = distanceLabel(row.distanceMeters)
            val age = ageLabel(row.createdAt)
            val meta = listOfNotNull(distance, age).joinToString(" · ")
            val badge = conditionBadge(row.condition, row.layer, isFree)
            return MarketplaceCardContent(
                id = row.id,
                title = row.title ?: "Listing",
                imageUrl = imageUrl,
                placeholderGradient = gradient,
                placeholderIcon = icon,
                price = price,
                isFree = isFree,
                metaLine = meta,
                conditionBadge = badge,
            )
        }

        private fun priceLabel(
            price: Double?,
            isFree: Boolean,
            layer: String?,
            listingType: String?,
        ): String {
            if (isFree) return "Free"
            if (price == null) return "—"
            val base =
                if (price % 1.0 == 0.0) {
                    "$${price.toInt()}"
                } else {
                    String.format("$%.2f", price)
                }
            val isRental = layer == "rentals" || listingType == "rent_sublet" || listingType == "vehicle_rent"
            return if (isRental) "$base / wk" else base
        }

        private fun distanceLabel(meters: Double?): String? {
            if (meters == null) return null
            val miles = meters / 1609.344
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

        private fun conditionBadge(
            condition: String?,
            layer: String?,
            isFree: Boolean,
        ): String? {
            if (layer == "rentals" || isFree) return null
            if (condition.isNullOrEmpty()) return null
            return when (condition) {
                "new" -> "New"
                "like_new" -> "Like new"
                "good" -> "Good"
                "fair" -> "Fair"
                "for_parts" -> "For parts"
                else -> condition.replace("_", " ").replaceFirstChar { it.uppercase() }
            }
        }

        private fun placeholderIcon(
            category: String?,
            layer: String?,
        ): PantopusIcon {
            if (layer == "vehicles") return PantopusIcon.Send
            if (layer == "rentals") return PantopusIcon.Calendar
            return when (category) {
                "furniture" -> PantopusIcon.Home
                "electronics" -> PantopusIcon.Lightbulb
                "clothing" -> PantopusIcon.ShoppingBag
                "kids_baby" -> PantopusIcon.Heart
                "tools" -> PantopusIcon.Hammer
                "home_garden" -> PantopusIcon.Sun
                "sports_outdoors" -> PantopusIcon.Star
                "vehicles" -> PantopusIcon.Send
                "books_media" -> PantopusIcon.File
                "appliances" -> PantopusIcon.Lightbulb
                "food_baked_goods" -> PantopusIcon.Heart
                "plants_garden" -> PantopusIcon.Sun
                "pet_supplies" -> PantopusIcon.Heart
                "arts_crafts" -> PantopusIcon.Pencil
                "tickets_events" -> PantopusIcon.Calendar
                "free_stuff" -> PantopusIcon.ShoppingBag
                else -> PantopusIcon.ShoppingBag
            }
        }
    }
