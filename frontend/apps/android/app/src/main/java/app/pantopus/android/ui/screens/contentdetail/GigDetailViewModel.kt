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
            /**
             * Splits on the explicit `is_v2` discriminator: V2 ("Magic Task")
             * gets the rich surface, legacy V1 gets the sparse layout (which
             * also carries the awarded terminal state). The full design-spec
             * V2 frame is rendered from [GigDetailSampleData] until the Magic
             * Task JSONB is wired through the backend (out of scope per P8.2).
             */
            fun project(
                gig: GigDto,
                bids: List<GigBidDto>,
            ): ContentDetailContent = if (gig.isV2 == true) projectTaskV2(gig, bids) else projectGigV1(gig, bids)

            private fun projectTaskV2(
                gig: GigDto,
                bids: List<GigBidDto>,
            ): ContentDetailContent {
                val category = GigsCategory.fromBackendKey(gig.category)
                val bidCount = gig.bidCount ?: bids.size
                val metaPieces =
                    listOfNotNull(
                        distanceLabel(gig.distanceMiles),
                        relativeAge(gig.createdAt)?.let { "posted $it ago" },
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
                                    icon = PantopusIcon.ClipboardList,
                                    body = it,
                                ),
                            )
                        }
                        addAll(locationModules(gig))
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
                        add(
                            ContentDetailModule.CapsuleRow(
                                id = "trust",
                                capsules =
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
                                    ),
                            ),
                        )
                        if (bidCount > 0 && bids.isNotEmpty()) {
                            add(ContentDetailModule.Bids(id = "bids", title = "$bidCount bids", bids = bids.map { projectBid(it) }))
                        } else {
                            add(
                                ContentDetailModule.Callout(
                                    id = "be-first",
                                    style = ContentDetailModule.Callout.Style.Empty,
                                    tone = ContentDetailModule.Callout.Tone.Dashed,
                                    icon = PantopusIcon.HandCoins,
                                    iconTone = ContentDetailModule.Callout.IconTone.Primary,
                                    title = "Be the first to bid",
                                    subtitle =
                                        "Fresh posts usually get a hire in the first hour. " +
                                            "First three bids land at the top of the list.",
                                    footerPill = "neighbors viewing",
                                ),
                            )
                        }
                    }
                val statusLabel =
                    if (bidCount > 0) {
                        "Open · $bidCount ${if (bidCount == 1) "bid" else "bids"}"
                    } else {
                        "Open · No bids yet"
                    }
                return ContentDetailContent(
                    kind = ContentDetailKind.Gig,
                    statusPill =
                        ContentDetailPill(
                            id = "status",
                            label = statusLabel,
                            icon = PantopusIcon.Circle,
                            tone = ContentDetailPill.Tone.Warning,
                        ),
                    hero = hero,
                    statStrip = statStrip,
                    modules = modules,
                    trustCapsules = emptyList(),
                    dock =
                        ContentDetailDock(
                            secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                            primary = ContentDetailDockButton(label = "Place bid"),
                        ),
                )
            }

            private fun locationModules(gig: GigDto): List<ContentDetailModule> {
                val pickup = gig.pickupAddress?.takeIf { it.isNotEmpty() }
                val dropoff = gig.dropoffAddress?.takeIf { it.isNotEmpty() }
                if (pickup != null && dropoff != null) {
                    return listOf(
                        ContentDetailModule.TwoStop(
                            id = "stops",
                            title = "Pickup → drop-off",
                            icon = PantopusIcon.MapPin,
                            stops =
                                listOf(
                                    ContentDetailModule.TwoStop.Stop(
                                        letter = "A",
                                        tone = ContentDetailModule.TwoStop.StopTone.Primary,
                                        address = pickup,
                                        distance = distanceLabel(gig.distanceMiles),
                                    ),
                                    ContentDetailModule.TwoStop.Stop(
                                        letter = "B",
                                        tone = ContentDetailModule.TwoStop.StopTone.Success,
                                        address = dropoff,
                                        distance = null,
                                    ),
                                ),
                        ),
                    )
                }
                if (pickup != null) {
                    return listOf(
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
                return emptyList()
            }

            private fun projectGigV1(
                gig: GigDto,
                bids: List<GigBidDto>,
            ): ContentDetailContent {
                val awarded = isAwarded(gig)
                val bidCount = gig.bidCount ?: bids.size
                val metaPieces =
                    listOfNotNull(
                        distanceLabel(gig.distanceMiles),
                        gig.scheduledStart?.takeIf { it.isNotEmpty() },
                    )
                val priceLine = gig.price?.let { priceLabel(it, gig.payType) }
                val hero =
                    ContentDetailHero(
                        title = gig.title,
                        categoryChip = null,
                        meta = metaPieces.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                        priceLine = priceLine,
                        priceCaption = gigV1PriceCaption(priceLine, awarded),
                    )
                val modules =
                    buildList {
                        if (awarded) {
                            add(
                                ContentDetailModule.Callout(
                                    id = "awarded",
                                    style = ContentDetailModule.Callout.Style.Banner,
                                    tone = ContentDetailModule.Callout.Tone.Success,
                                    icon = PantopusIcon.Check,
                                    iconTone = ContentDetailModule.Callout.IconTone.Success,
                                    title = awardWinnerName(gig, bids)?.let { "Awarded to $it" } ?: "Awarded",
                                    subtitle =
                                        listOfNotNull(relativeAge(gig.acceptedAt)?.let { "$it ago" }, "bidding now closed")
                                            .joinToString(" · "),
                                ),
                            )
                        }
                        gig.description?.takeIf { it.isNotEmpty() }?.let {
                            add(ContentDetailModule.Description(id = "desc", title = "Description", icon = null, body = it))
                        }
                        (gig.creator?.name ?: gig.creator?.username)?.let { poster ->
                            val posted = relativeAge(gig.createdAt)?.let { " · $it ago" } ?: ""
                            add(
                                ContentDetailModule.CaptionedText(
                                    id = "postedby",
                                    title = "Posted by",
                                    icon = null,
                                    label = "$poster$posted",
                                ),
                            )
                        }
                        if (bids.isNotEmpty()) {
                            add(
                                ContentDetailModule.Bids(
                                    id = "bids",
                                    title = "$bidCount bids",
                                    sub = if (awarded) "closed" else null,
                                    bids = bids.map { projectBid(it, if (awarded) gig.acceptedBy else null) },
                                ),
                            )
                        }
                    }
                return ContentDetailContent(
                    kind = ContentDetailKind.Gig,
                    statusPill =
                        if (awarded) {
                            ContentDetailPill(
                                id = "status",
                                label = "Awarded",
                                icon = PantopusIcon.Check,
                                tone = ContentDetailPill.Tone.Success,
                            )
                        } else {
                            ContentDetailPill(
                                id = "status",
                                label = "Open",
                                icon = PantopusIcon.Circle,
                                tone = ContentDetailPill.Tone.Warning,
                            )
                        },
                    hero = hero,
                    statStrip = emptyList(),
                    modules = modules,
                    trustCapsules = emptyList(),
                    dock =
                        if (awarded) {
                            ContentDetailDock(
                                secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                                primary = ContentDetailDockButton(label = "Bidding closed", icon = PantopusIcon.Lock, enabled = false),
                            )
                        } else {
                            ContentDetailDock(
                                secondary = ContentDetailDockButton(label = "Message", icon = PantopusIcon.Send),
                                primary = ContentDetailDockButton(label = "Place bid"),
                            )
                        },
                )
            }

            private fun isAwarded(gig: GigDto): Boolean {
                if (gig.acceptedBy.isNullOrEmpty()) return false
                return gig.status in listOf("accepted", "awarded", "completed", "in_progress")
            }

            private fun awardWinnerName(
                gig: GigDto,
                bids: List<GigBidDto>,
            ): String? {
                val winner = bids.firstOrNull { it.userId == gig.acceptedBy }
                return winner?.bidder?.name ?: winner?.bidder?.username
            }

            private fun projectBid(
                bid: GigBidDto,
                acceptedBy: String? = null,
            ): ContentDetailBidRow {
                val name = bid.bidder?.name ?: bid.bidder?.username ?: "Bidder"
                val initials =
                    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase()
                val amount = bid.bidAmount ?: bid.amount ?: 0.0
                val amountLabel =
                    if (amount % 1.0 == 0.0) "$${amount.toInt()}" else String.format("$%.2f", amount)
                val won = acceptedBy != null && bid.userId == acceptedBy
                val dimmed = acceptedBy != null && !won
                return ContentDetailBidRow(
                    id = bid.id,
                    initials = initials.ifEmpty { "?" },
                    displayName = name,
                    ratingLine = "verified neighbor",
                    amount = amountLabel,
                    verified = bid.bidder?.verified ?: false,
                    won = won,
                    dimmed = dimmed,
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

private fun gigV1PriceCaption(
    priceLine: String?,
    awarded: Boolean,
): String? =
    when {
        priceLine == null -> null
        awarded -> "winning bid"
        else -> "budget"
    }
