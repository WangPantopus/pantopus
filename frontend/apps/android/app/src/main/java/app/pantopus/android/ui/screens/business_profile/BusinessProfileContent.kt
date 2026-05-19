@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.business_profile

/** Tabs surfaced on the Business Profile detail. */
enum class BusinessProfileTab(val label: String, val key: String) {
    Overview("Overview", "overview"),
    Services("Services", "services"),
    Reviews("Reviews", "reviews"),
}

/** Hero-band content. */
data class BusinessProfileHeader(
    val displayName: String,
    val handle: String?,
    val locality: String?,
    val logoUrl: String?,
    val isVerified: Boolean,
    val categoryChips: List<String>,
)

/** One cell in the raised stats strip. */
data class BusinessStatCell(
    val id: String,
    val value: String,
    val label: String,
)

/** One review row. */
data class BusinessReviewCard(
    val id: String,
    val reviewerName: String,
    val reviewerAvatarUrl: String?,
    val rating: Int,
    val body: String,
    val timestamp: String,
)

/** One Services-tab row. */
data class BusinessServiceRow(
    val id: String,
    val name: String,
    val detail: String?,
    val priceLabel: String,
)

/** One Overview-tab hours row. */
data class BusinessHoursRow(
    val id: String,
    val dayLabel: String,
    val timeLabel: String,
    val isClosed: Boolean,
)

/** Address + map-preview marker for the Overview tab. */
data class BusinessAddress(
    val lines: List<String>,
    val latitude: Double?,
    val longitude: Double?,
) {
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null
}

/** A single Overview contact row. */
data class BusinessContactRow(
    val id: String,
    val kind: Kind,
    val value: String,
    val actionUri: String?,
) {
    enum class Kind { Phone, Email, Website }
}

/** Render-ready payload emitted by [BusinessProfileViewModel]. */
data class BusinessProfileContent(
    val businessId: String,
    val header: BusinessProfileHeader,
    val stats: List<BusinessStatCell>,
    val about: String?,
    val hours: List<BusinessHoursRow>,
    val address: BusinessAddress?,
    val contact: List<BusinessContactRow>,
    val services: List<BusinessServiceRow>,
    val reviews: List<BusinessReviewCard>,
    val websiteUrl: String?,
    val viewerIsOwner: Boolean,
)

/** Observed UI state for the Business Profile screen. */
sealed interface BusinessProfileUiState {
    data object Loading : BusinessProfileUiState

    data class Loaded(val content: BusinessProfileContent) : BusinessProfileUiState

    data object NotFound : BusinessProfileUiState

    data class Error(val message: String) : BusinessProfileUiState
}

/** Save action state. */
sealed interface BusinessProfileSaveState {
    data object Idle : BusinessProfileSaveState

    data object InFlight : BusinessProfileSaveState

    data object Saved : BusinessProfileSaveState

    data class Failed(val message: String) : BusinessProfileSaveState
}
