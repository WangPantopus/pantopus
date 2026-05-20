@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.compose.listing

import java.util.UUID

/**
 * The six pre-success steps of the Snap & Sell wizard, in order. The
 * success state is a sentinel terminal step used to render the success
 * hero block.
 */
enum class ListingComposeStep(
    val ordinal0: Int,
) {
    Photos(0),
    TitleCategory(1),
    ConditionDescription(2),
    Price(3),
    Location(4),
    Review(5),
    Success(6),
    ;

    /** One-indexed position used in the "N of M" top-bar readout, or null
     *  for the success terminal. */
    val stepNumber: Int?
        get() =
            when (this) {
                Photos -> 1
                TitleCategory -> 2
                ConditionDescription -> 3
                Price -> 4
                Location -> 5
                Review -> 6
                Success -> null
            }

    companion object {
        /** Total number of "step N of M" steps shown in the readout. */
        const val PROGRESS_TOTAL: Int = 6

        fun fromOrdinal(value: Int): ListingComposeStep = entries.firstOrNull { it.ordinal0 == value } ?: Photos
    }
}

/**
 * Category selectable in step 2. Mirrors the five Marketplace chips and
 * resolves onto backend `layer` + the wanted/free flags.
 */
enum class ListingComposeCategory(
    val key: String,
    val label: String,
    val subtitle: String,
) {
    Goods("goods", "Goods", "Sell something you own."),
    Rentals("rentals", "Rentals", "Rent something out by the day or week."),
    Vehicles("vehicles", "Vehicles", "Cars, bikes, scooters, trailers."),
    Free("free", "Free", "Give something away to a neighbor."),
    Wanted("wanted", "Wanted", "Ask the neighborhood for something."),
    ;

    /** Backend `layer`. Free + Wanted both map to `goods`. */
    val layer: String
        get() =
            when (this) {
                Goods, Free, Wanted -> "goods"
                Rentals -> "rentals"
                Vehicles -> "vehicles"
            }

    /** Backend `listing_type`. */
    val listingType: String
        get() =
            when (this) {
                Goods -> "sell_item"
                Rentals -> "rent_item"
                Vehicles -> "sell_item"
                Free -> "free_item"
                Wanted -> "wanted_request"
            }

    val isFreeDefault: Boolean get() = this == Free
    val isWanted: Boolean get() = this == Wanted

    /** Wanted requests skip the condition step. */
    val requiresCondition: Boolean
        get() =
            when (this) {
                Goods, Vehicles, Free, Rentals -> true
                Wanted -> false
            }
}

/** Condition selectable in step 3. */
enum class ListingComposeCondition(
    val key: String,
    val label: String,
    val subtitle: String,
) {
    New("new", "New", "Unused, in original packaging."),
    LikeNew("like_new", "Like new", "Barely used, no visible wear."),
    Good("good", "Good", "Lightly used, minor wear."),
    Fair("fair", "Fair", "Used, with visible wear."),
    ForParts("for_parts", "For parts", "Not working — usable for parts."),
}

/** Pricing kind in step 4. */
enum class ListingComposePriceKind(
    val key: String,
    val label: String,
    val subtitle: String,
) {
    Free("free", "Free", "No price — first to claim."),
    Fixed("fixed", "Fixed price", "Buyers see one price."),
    Negotiable("negotiable", "Open to offers", "Asking price, buyers can offer."),
}

/** Pickup vs delivery preference in step 4. */
enum class ListingComposeFulfillment(
    val key: String,
    val label: String,
    val subtitle: String,
) {
    Pickup("pickup", "Pickup", "Buyer comes to you."),
    Delivery("delivery", "Delivery", "You drop off within the neighborhood."),
    ;

    /** Maps onto the backend `meetup_preference` enum. */
    val meetupPreference: String
        get() =
            when (this) {
                Pickup -> "public_meetup"
                Delivery -> "delivery"
            }
}

/** Location kind in step 5. */
enum class ListingComposeLocationKind(
    val key: String,
    val label: String,
    val subtitle: String,
) {
    SavedAddress(
        "saved_address",
        "Use my saved address",
        "We'll share it after a buyer commits.",
    ),
    MeetPoint(
        "meet_point",
        "Pick a meet point",
        "Park, plaza, or storefront within walking distance.",
    ),
}

/**
 * One photo in the wizard's photo grid. The id is stable so reorder
 * and remove operations can identify rows.
 */
data class ListingComposePhoto(
    val id: String = UUID.randomUUID().toString(),
    val token: String,
)

/** Persistable form state for the wizard. */
data class ListingComposeFormState(
    val step: Int = ListingComposeStep.Photos.ordinal0,
    val photos: List<ListingComposePhoto> = emptyList(),
    val title: String = "",
    val category: ListingComposeCategory? = null,
    val condition: ListingComposeCondition? = null,
    val bodyText: String = "",
    val priceKind: ListingComposePriceKind? = null,
    val priceAmount: String = "",
    val fulfillment: ListingComposeFulfillment = ListingComposeFulfillment.Pickup,
    val locationKind: ListingComposeLocationKind? = null,
    val locationLabel: String = "",
) {
    val currentStep: ListingComposeStep get() = ListingComposeStep.fromOrdinal(step)

    companion object {
        val EMPTY = ListingComposeFormState()

        /** Max photos in the grid. */
        const val MAX_PHOTOS = 8

        /** Min / max bounds enforced on step transitions. */
        const val TITLE_MIN_LENGTH = 5
        const val TITLE_MAX_LENGTH = 80
        const val DESCRIPTION_MIN_LENGTH = 20
        const val DESCRIPTION_MAX_LENGTH = 2000
    }
}

/** Outbound navigation events the screen consumes. */
sealed interface ListingComposeOutboundEvent {
    /** Pop the wizard with no further navigation. */
    data object Dismiss : ListingComposeOutboundEvent

    /** Pop the wizard and navigate to the new listing's detail. */
    data class OpenListingDetail(
        val listingId: String,
    ) : ListingComposeOutboundEvent

    /** Pop the wizard after an edit save — the host pops back to the
     *  detail underneath so it refreshes from its own .task block. */
    data class ListingUpdated(
        val listingId: String,
    ) : ListingComposeOutboundEvent
}

/**
 * Whether the wizard is creating a new listing or editing an existing
 * one. Edit mode carries the listing id (POST → PATCH switch) and an
 * optional `jumpToStep` so entry points like "Edit price" can land
 * directly on the price step instead of step one.
 */
sealed interface ListingComposeMode {
    data object Create : ListingComposeMode

    data class Edit(
        val listingId: String,
        val jumpToStep: ListingComposeStep? = null,
    ) : ListingComposeMode

    val isEdit: Boolean get() = this is Edit

    val editingListingId: String? get() = (this as? Edit)?.listingId
}
