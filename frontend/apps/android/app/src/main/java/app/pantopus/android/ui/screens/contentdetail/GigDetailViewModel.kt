@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.contentdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.models.gigs.PlaceBidBody
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.theme.PantopusIcon
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

@HiltViewModel
class GigDetailViewModel
    @Inject
    constructor(
        private val repo: GigsRepository,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        companion object {
            const val GIG_ID_KEY = "gigId"
        }

        private val gigId: String = savedStateHandle.get<String>(GIG_ID_KEY) ?: ""

        private val _state = MutableStateFlow<ContentDetailUiState>(ContentDetailUiState.Loading)
        val state: StateFlow<ContentDetailUiState> = _state.asStateFlow()

        private var rawGig: GigDto? = null

        /** Current gig snapshot — null until the first fetch resolves. */
        fun gigSnapshot(): GigDto? = rawGig

        fun load() {
            _state.value = ContentDetailUiState.Loading
            viewModelScope.launch {
                when (val result = repo.detail(gigId)) {
                    is NetworkResult.Success -> {
                        rawGig = result.data.gig
                        var bids: List<GigBidDto> = emptyList()
                        when (val bidsResult = repo.bids(gigId)) {
                            is NetworkResult.Success -> bids = bidsResult.data.bids
                            else -> Unit
                        }
                        _state.value = ContentDetailUiState.Loaded(Projection.project(result.data.gig, bids))
                    }
                    is NetworkResult.Failure -> {
                        _state.value = ContentDetailUiState.Error(result.error.message)
                    }
                }
            }
        }

        fun placeBid(
            amount: Double,
            message: String?,
            proposedTime: String? = null,
            onResult: (Boolean) -> Unit = {},
        ) {
            viewModelScope.launch {
                val result =
                    repo.placeBid(
                        gigId = gigId,
                        body =
                            PlaceBidBody(
                                bidAmount = amount,
                                message = message,
                                proposedTime = proposedTime,
                            ),
                    )
                if (result is NetworkResult.Success) {
                    load()
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
        }

        /** Returns the gig id wired from `SavedStateHandle`. */
        fun currentGigId(): String = gigId

        object Projection {
            fun project(
                gig: GigDto,
                bids: List<GigBidDto>,
            ): ContentDetailContent {
                val category = GigsCategory.fromBackendKey(gig.category)
                val bidCount = gig.bidCount ?: bids.size
                val statusLabel =
                    when (gig.status) {
                        "open", null ->
                            if (bidCount > 0) "Open · $bidCount ${if (bidCount == 1) "bid" else "bids"}" else "Open"
                        "accepted" -> "Accepted"
                        "completed" -> "Completed"
                        "cancelled" -> "Cancelled"
                        else -> gig.status.replaceFirstChar { it.uppercase() }
                    }
                val tone =
                    when (gig.status) {
                        "open", null -> ContentDetailPill.Tone.Warning
                        "completed" -> ContentDetailPill.Tone.Success
                        "cancelled" -> ContentDetailPill.Tone.Neutral
                        else -> ContentDetailPill.Tone.Info
                    }
                val metaPieces =
                    listOfNotNull(
                        distanceLabel(gig.distanceMiles),
                        relativeAge(gig.createdAt)?.let { "$it ago" },
                    )
                val priceLine = gig.price?.let { priceLabel(it, gig.payType) }
                val hero =
                    ContentDetailHero(
                        title = gig.title,
                        categoryChip = ContentDetailCategoryChip(category.label, category),
                        meta = metaPieces.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                        priceLine = priceLine,
                        priceCaption = if (priceLine != null) "budget" else null,
                    )
                val statStrip =
                    listOfNotNull(
                        gig.scheduleType?.takeIf { it.isNotEmpty() }?.let {
                            ContentDetailStat(it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }, "schedule")
                        },
                        gig.taskArchetype?.takeIf { it.isNotEmpty() }?.let {
                            ContentDetailStat(it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }, "type")
                        },
                        gig.engagementMode?.takeIf { it.isNotEmpty() }?.let {
                            ContentDetailStat(it.replace("_", " ").replaceFirstChar { c -> c.uppercase() }, "mode")
                        },
                    ).take(3)
                val modules =
                    buildList {
                        gig.description?.takeIf { it.isNotEmpty() }?.let {
                            add(
                                ContentDetailModule.Description(
                                    id = "desc",
                                    title = "What needs doing",
                                    icon = PantopusIcon.File,
                                    body = it,
                                ),
                            )
                        }
                        gig.pickupAddress?.takeIf { it.isNotEmpty() }?.let { pickup ->
                            add(
                                ContentDetailModule.DetailRow(
                                    id = "where",
                                    title = "Where",
                                    sectionIcon = PantopusIcon.MapPin,
                                    rowIcon = PantopusIcon.MapPin,
                                    label = pickup,
                                    trailing = distanceLabel(gig.distanceMiles),
                                ),
                            )
                        }
                        gig.scheduledStart?.takeIf { it.isNotEmpty() }?.let { iso ->
                            add(
                                ContentDetailModule.CaptionedText(
                                    id = "when",
                                    title = "When",
                                    icon = PantopusIcon.Calendar,
                                    label = iso,
                                ),
                            )
                        }
                        if (bids.isNotEmpty()) {
                            add(
                                ContentDetailModule.Bids(
                                    id = "bids",
                                    title = "$bidCount bids",
                                    bids = bids.map(::projectBid),
                                ),
                            )
                        }
                    }
                val trust =
                    listOf(
                        ContentDetailPill(
                            id = "addr",
                            label = "Verified address",
                            icon = PantopusIcon.ShieldCheck,
                            tone = ContentDetailPill.Tone.Info,
                        ),
                        ContentDetailPill(
                            id = "local",
                            label = "Local Pantopus job",
                            icon = PantopusIcon.Check,
                            tone = ContentDetailPill.Tone.Success,
                        ),
                    )
                val dock =
                    ContentDetailDock(
                        secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                        primary = ContentDetailDockButton(label = "Place bid"),
                    )
                return ContentDetailContent(
                    kind = ContentDetailKind.Gig,
                    statusPill = ContentDetailPill(id = "status", label = statusLabel, icon = PantopusIcon.Circle, tone = tone),
                    hero = hero,
                    statStrip = statStrip,
                    modules = modules,
                    trustCapsules = trust,
                    dock = dock,
                )
            }

            private fun projectBid(bid: GigBidDto): ContentDetailBidRow {
                val name = bid.bidder?.name ?: bid.bidder?.username ?: "Bidder"
                val initials =
                    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
                val amount = bid.bidAmount ?: bid.amount ?: 0.0
                val amountLabel =
                    if (amount % 1.0 == 0.0) "$${amount.toInt()}" else String.format("$%.2f", amount)
                return ContentDetailBidRow(
                    id = bid.id,
                    initials = initials.ifEmpty { "?" },
                    displayName = name,
                    ratingLine = "verified neighbor",
                    amount = amountLabel,
                    verified = bid.bidder?.verified ?: false,
                )
            }

            private fun priceLabel(
                price: Double,
                payType: String?,
            ): String {
                val base = if (price % 1.0 == 0.0) "$${price.toInt()}" else String.format("$%.2f", price)
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
                    miles < 0.1 -> "< 0.1 mi"
                    miles < 10 -> String.format("%.1f mi", miles)
                    else -> "${miles.toInt()} mi"
                }
            }

            private fun relativeAge(iso: String?): String? {
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
