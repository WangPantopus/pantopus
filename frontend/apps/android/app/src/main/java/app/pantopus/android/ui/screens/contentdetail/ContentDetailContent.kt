@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.contentdetail

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.marketplace.ListingGradient
import app.pantopus.android.ui.theme.PantopusIcon

/** Which entity is being shown. */
enum class ContentDetailKind { Gig, Listing, Invoice }

/** Cover image for the listing variant. */
@Immutable
data class ContentDetailCover(
    val imageUrl: String?,
    val gradient: ListingGradient,
    val placeholderIcon: PantopusIcon,
    val pageCount: Int = 1,
    val activePage: Int = 0,
)

/** Status / trust pill. */
@Immutable
data class ContentDetailPill(
    val id: String,
    val label: String,
    val icon: PantopusIcon? = null,
    val tone: Tone = Tone.Info,
) {
    enum class Tone { Info, Success, Warning, Business, Neutral }
}

@Immutable
data class ContentDetailCategoryChip(
    val label: String,
    val category: GigsCategory,
)

@Immutable
data class ContentDetailHero(
    val title: String,
    val categoryChip: ContentDetailCategoryChip? = null,
    val meta: String? = null,
    val monoId: String? = null,
    val priceLine: String? = null,
    val priceCaption: String? = null,
)

@Immutable
data class ContentDetailStat(
    val top: String,
    val bottom: String,
)

@Immutable
data class ContentDetailCounterparty(
    val displayName: String,
    val initials: String,
    val identityKind: String?,
    val verified: Boolean,
    val rating: Double?,
    val trailing: String?,
    val showsMessageButton: Boolean = true,
)

@Immutable
data class ContentDetailDockButton(
    val label: String,
    val icon: PantopusIcon? = null,
)

@Immutable
data class ContentDetailDock(
    val secondary: ContentDetailDockButton? = null,
    val primary: ContentDetailDockButton,
)

// MARK: - Modules

sealed interface ContentDetailModule {
    val id: String

    @Immutable
    data class Description(
        override val id: String,
        val title: String,
        val icon: PantopusIcon? = null,
        val body: String,
    ) : ContentDetailModule

    @Immutable
    data class DetailRow(
        override val id: String,
        val title: String,
        val sectionIcon: PantopusIcon? = null,
        val rowIcon: PantopusIcon,
        val label: String,
        val trailing: String? = null,
    ) : ContentDetailModule

    @Immutable
    data class CaptionedText(
        override val id: String,
        val title: String,
        val icon: PantopusIcon? = null,
        val label: String,
    ) : ContentDetailModule

    @Immutable
    data class PhotoStrip(
        override val id: String,
        val title: String,
        val icon: PantopusIcon? = PantopusIcon.File,
        val countLabel: String? = null,
        val tiles: List<ContentDetailPhotoTile>,
    ) : ContentDetailModule

    @Immutable
    data class SimilarStrip(
        override val id: String,
        val title: String,
        val sub: String? = null,
        val items: List<ContentDetailSimilarItem>,
    ) : ContentDetailModule

    @Immutable
    data class Bids(
        override val id: String,
        val title: String,
        val bids: List<ContentDetailBidRow>,
    ) : ContentDetailModule

    @Immutable
    data class FromTo(
        override val id: String,
        val from: ContentDetailParty,
        val to: ContentDetailParty,
    ) : ContentDetailModule

    @Immutable
    data class LineItems(
        override val id: String,
        val title: String,
        val icon: PantopusIcon? = PantopusIcon.File,
        val rows: List<ContentDetailLineItem>,
    ) : ContentDetailModule

    @Immutable
    data class Summary(
        override val id: String,
        val rows: List<ContentDetailSummaryRow>,
        val totalLabel: String,
        val totalValue: String,
    ) : ContentDetailModule
}

@Immutable
data class ContentDetailPhotoTile(
    val id: String,
    val gradient: ListingGradient,
    val icon: PantopusIcon,
)

@Immutable
data class ContentDetailSimilarItem(
    val id: String,
    val title: String,
    val price: String,
    val gradient: ListingGradient,
)

@Immutable
data class ContentDetailBidRow(
    val id: String,
    val initials: String,
    val displayName: String,
    val ratingLine: String,
    val amount: String,
    val verified: Boolean,
)

@Immutable
data class ContentDetailParty(
    val label: String,
    val name: String,
    val sub: String,
    val accent: Accent,
) {
    enum class Accent { Business, Personal, Neutral }
}

@Immutable
data class ContentDetailLineItem(
    val id: String,
    val item: String,
    val qty: String,
    val unit: String,
    val total: String,
)

@Immutable
data class ContentDetailSummaryRow(
    val id: String,
    val label: String,
    val value: String,
)

@Immutable
data class ContentDetailContent(
    val kind: ContentDetailKind,
    val cover: ContentDetailCover? = null,
    val statusPill: ContentDetailPill? = null,
    val hero: ContentDetailHero,
    val statStrip: List<ContentDetailStat> = emptyList(),
    val counterparty: ContentDetailCounterparty? = null,
    val modules: List<ContentDetailModule> = emptyList(),
    val trustCapsules: List<ContentDetailPill> = emptyList(),
    val dock: ContentDetailDock,
)

sealed interface ContentDetailUiState {
    data object Loading : ContentDetailUiState

    data class Loaded(val content: ContentDetailContent) : ContentDetailUiState

    data class Error(val message: String) : ContentDetailUiState
}
