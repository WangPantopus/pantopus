@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.gigs

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Nine category enums for the Gigs feed. `All` is filter-only — gigs in
 * the payload always carry a concrete category. Color hex values come
 * from the design tokens (`gigs-frames.jsx` CATS) so they stay
 * Gigs-local rather than crowding the shared theme palette.
 */
enum class GigsCategory(
    val key: String,
    val label: String,
    val color: Color,
) {
    All("all", "All", Color(0xFF0284C7)),
    Handyman("handyman", "Handyman", Color(0xFFEA580C)),
    Cleaning("cleaning", "Cleaning", Color(0xFF0EA5E9)),
    Moving("moving", "Moving", Color(0xFF7C3AED)),
    PetCare("petcare", "Pet care", Color(0xFF16A34A)),
    ChildCare("childcare", "Child care", Color(0xFFDB2777)),
    Tutoring("tutoring", "Tutoring", Color(0xFFCA8A04)),
    Tech("tech", "Tech", Color(0xFF475569)),
    Delivery("delivery", "Delivery", Color(0xFF0891B2)),
    ;

    companion object {
        /** Backend-key → enum. Unknown keys fall back to Handyman. */
        fun fromBackendKey(raw: String?): GigsCategory {
            val key =
                (raw ?: "")
                    .lowercase()
                    .replace("_", "")
                    .replace("-", "")
            return when (key) {
                "all" -> All
                "handyman", "handy", "repair", "repairs" -> Handyman
                "cleaning", "clean" -> Cleaning
                "moving", "move", "movers" -> Moving
                "petcare", "pet", "pets", "dogwalking", "petsitting" -> PetCare
                "childcare", "child", "babysitting", "nanny" -> ChildCare
                "tutoring", "tutor", "lessons", "teaching" -> Tutoring
                "tech", "technology", "it", "computer" -> Tech
                "delivery", "deliveries", "courier" -> Delivery
                else -> Handyman
            }
        }
    }
}

/** Sort options for the Gigs feed (matches backend `sort` enum). */
enum class GigsSort(
    val key: String,
    val label: String,
) {
    Newest("newest", "Newest"),
    Closest("closest", "Closest"),
    HighestPay("highest_pay", "Highest pay"),
    FewestBids("fewest_bids", "Fewest bids"),
    ;

    companion object {
        fun fromKey(key: String): GigsSort = entries.firstOrNull { it.key == key } ?: Newest
    }
}

/** One row in the populated feed (projected from [GigDto]). */
@Immutable
data class GigCardContent(
    val id: String,
    val category: GigsCategory,
    /** "0.2mi · 2h ago" — composed meta line. */
    val metaLine: String,
    val title: String,
    val body: String,
    /** Free-form price string: "$60", "$22 / walk". */
    val price: String,
    val bidCount: Int,
    /** Right-aligned distance label ("0.2mi"). `null` hides it. */
    val distanceLabel: String?,
)

/** Render state for the Gigs feed screen. */
sealed interface GigsFeedUiState {
    data object Loading : GigsFeedUiState

    data class Empty(
        val radiusMiles: Double,
    ) : GigsFeedUiState

    data class Loaded(
        val rows: List<GigCardContent>,
    ) : GigsFeedUiState

    data class Error(
        val message: String,
    ) : GigsFeedUiState
}
