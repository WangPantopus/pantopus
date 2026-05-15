@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.data.api.models.listings.MessageListingBody
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.listings.ListingsRepository
import app.pantopus.android.ui.screens.marketplace.ListingGradient
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ListingDetailViewModel
    @Inject
    constructor(
        private val repo: ListingsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            const val LISTING_ID_KEY = "listingId"
        }

        private val listingId: String = savedStateHandle.get<String>(LISTING_ID_KEY) ?: ""

        private val _state = MutableStateFlow<ContentDetailUiState>(ContentDetailUiState.Loading)
        val state: StateFlow<ContentDetailUiState> = _state.asStateFlow()

        private var rawListing: ListingDto? = null

        fun load() {
            _state.value = ContentDetailUiState.Loading
            viewModelScope.launch {
                when (val result = repo.detail(listingId)) {
                    is NetworkResult.Success -> {
                        rawListing = result.data.listing
                        _state.value = ContentDetailUiState.Loaded(Projection.project(result.data.listing))
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ContentDetailUiState.Error(result.error.message)
                    }
                }
            }
        }

        fun sendMessage(
            text: String,
            offerAmount: Double? = null,
            onResult: (Boolean) -> Unit = {},
        ) {
            viewModelScope.launch {
                val result =
                    repo.messageListing(
                        id = listingId,
                        body = MessageListingBody(message = text, offerAmount = offerAmount),
                    )
                onResult(result is NetworkResult.Success)
            }
        }

        object Projection {
            fun project(listing: ListingDto): ContentDetailContent {
                val isFree = listing.isFree ?: false
                val priceLine =
                    when {
                        isFree -> "Free"
                        listing.price == null -> "—"
                        listing.price % 1.0 == 0.0 -> "$${listing.price.toInt()}"
                        else -> String.format("$%.2f", listing.price)
                    }
                val imageUrl = listing.firstImage ?: listing.mediaUrls?.firstOrNull()
                val cover =
                    ContentDetailCover(
                        imageUrl = imageUrl,
                        gradient = ListingGradient.from(listing.id),
                        placeholderIcon = placeholderIcon(listing.category, listing.layer),
                        pageCount = (listing.mediaUrls?.size ?: 1).coerceAtLeast(1),
                        activePage = 0,
                    )
                val condition = conditionLabel(listing.condition)
                val trust =
                    buildList {
                        condition?.let {
                            add(
                                ContentDetailPill(
                                    id = "cond",
                                    label = it,
                                    icon = PantopusIcon.Star,
                                    tone = ContentDetailPill.Tone.Success,
                                ),
                            )
                        }
                        when {
                            listing.layer == "rentals" ->
                                add(
                                    ContentDetailPill(
                                        id = "rental",
                                        label = "Rental",
                                        icon = PantopusIcon.Calendar,
                                        tone = ContentDetailPill.Tone.Business,
                                    ),
                                )
                            isFree ->
                                add(
                                    ContentDetailPill(
                                        id = "free",
                                        label = "Free",
                                        icon = PantopusIcon.Heart,
                                        tone = ContentDetailPill.Tone.Success,
                                    ),
                                )
                            else ->
                                add(
                                    ContentDetailPill(
                                        id = "pickup",
                                        label = "Pickup",
                                        icon = PantopusIcon.MapPin,
                                        tone = ContentDetailPill.Tone.Neutral,
                                    ),
                                )
                        }
                    }
                val counterparty =
                    ContentDetailCounterparty(
                        displayName = "Seller",
                        initials = "S",
                        identityKind = "personal",
                        verified = true,
                        rating = null,
                        trailing = listing.locationName,
                    )
                val modules =
                    buildList {
                        listing.description?.takeIf { it.isNotEmpty() }?.let {
                            add(
                                ContentDetailModule.Description(
                                    id = "desc",
                                    title = "Description",
                                    icon = PantopusIcon.File,
                                    body = it,
                                ),
                            )
                        }
                        listing.locationName?.takeIf { it.isNotEmpty() }?.let { where ->
                            add(
                                ContentDetailModule.DetailRow(
                                    id = "where",
                                    title = "Where",
                                    sectionIcon = PantopusIcon.MapPin,
                                    rowIcon = PantopusIcon.MapPin,
                                    label = where,
                                    trailing = distanceLabel(listing.distanceMeters),
                                ),
                            )
                        }
                    }
                val dock =
                    ContentDetailDock(
                        secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                        primary = ContentDetailDockButton(label = "Make offer"),
                    )
                return ContentDetailContent(
                    kind = ContentDetailKind.Listing,
                    cover = cover,
                    statusPill = null,
                    hero =
                        ContentDetailHero(
                            title = listing.title ?: "Listing",
                            priceLine = priceLine,
                            priceCaption = if (listing.layer == "rentals") "per week" else null,
                        ),
                    counterparty = counterparty,
                    modules = modules,
                    trustCapsules = trust,
                    dock = dock,
                )
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
                    "tools" -> PantopusIcon.Hammer
                    "books_media" -> PantopusIcon.File
                    "free_stuff" -> PantopusIcon.Heart
                    else -> PantopusIcon.ShoppingBag
                }
            }

            private fun conditionLabel(condition: String?): String? {
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

            private fun distanceLabel(meters: Double?): String? {
                if (meters == null) return null
                val miles = meters / 1609.344
                return when {
                    miles < 0.1 -> "< 0.1 mi"
                    miles < 10 -> String.format("%.1f mi", miles)
                    else -> "${miles.toInt()} mi"
                }
            }
        }
    }
