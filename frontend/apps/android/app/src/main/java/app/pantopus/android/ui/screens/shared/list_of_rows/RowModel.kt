@file:Suppress("PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.shared.list_of_rows

import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.theme.PantopusIcon

// MARK: - Templates

/** Visual template for a list row. */
enum class RowTemplate { StatusChip, FileChevron, AvatarKebab }

// MARK: - Supporting types (T5 additions)

/** Two-stop gradient for category / avatar / thumbnail backgrounds. */
data class GradientPair(
    val start: Color,
    val end: Color,
)

/** Size variants for [RowLeading.AvatarWithBadge]. */
enum class AvatarBadgeSize(val sizeDp: Int) {
    /** 36dp — inside grouped sections (Discover hub). */
    Small(36),

    /** 40dp — Review claims rows. */
    Medium(40),

    /** 44dp — Connections row. */
    Large(44),
}

/** Background fill for [RowLeading.AvatarWithBadge]. */
sealed interface AvatarBackground {
    data class Solid(val color: Color) : AvatarBackground

    data class Gradient(val gradient: GradientPair) : AvatarBackground
}

/** Thumbnail payload for [RowLeading.Thumbnail]. */
sealed interface ThumbnailImage {
    data class IconOnGradient(
        val icon: PantopusIcon,
        val gradient: GradientPair,
    ) : ThumbnailImage

    data class Remote(
        val url: String,
        val fallback: PantopusIcon,
        val gradient: GradientPair,
    ) : ThumbnailImage
}

/** Size variants for [RowLeading.Thumbnail]. */
enum class ThumbnailSize(val sizeDp: Int) {
    /** 56dp — Offers row. */
    Medium(56),

    /** 64dp — Pets row. */
    Large(64),
}

/** Tone palette for [Bidder] avatars inside [RowLeading.BidderStack]. */
enum class BidderTone { Sky, Teal, Amber, Rose, Violet, Slate }

/** Mini-avatar inside [BidderStack][RowLeading.BidderStack]. */
data class Bidder(
    val id: String,
    val initials: String,
    val tone: BidderTone,
)

/**
 * Bidder-stack payload rendered inline on the chip row — used by My
 * tasks V2 (T5.3.2). Separate from [RowLeading.BidderStack] because
 * the design positions the stack next to the status chip rather than
 * in the leading slot (which holds the 40dp category icon).
 */
data class BidderStackData(
    val bidders: List<Bidder>,
    val overflow: Int = 0,
)

// MARK: - Leading

/** Optional leading visual. */
sealed interface RowLeading {
    data object None : RowLeading

    /** Existing — icon-only. */
    data class Icon(
        val icon: PantopusIcon,
        val tint: Color,
    ) : RowLeading

    /** Existing — avatar with identity-pillar ring. */
    data class Avatar(
        val name: String,
        val imageUrl: String?,
        val identity: IdentityPillar,
        val ringProgress: Float,
    ) : RowLeading

    // ─── T5 additions ──────────────────────────────────────────

    /**
     * 40dp rounded-square tile with a tinted background + foreground icon.
     * Used by Notifications (per type) and Bills (receipt icon).
     */
    data class TypeIcon(
        val icon: PantopusIcon,
        val background: Color,
        val foreground: Color,
    ) : RowLeading

    /**
     * 40dp rounded-square tile with a two-stop gradient background + white
     * foreground icon. Used by My bids / My tasks category icons.
     */
    data class CategoryGradientIcon(
        val icon: PantopusIcon,
        val gradient: GradientPair,
    ) : RowLeading

    /**
     * Plain circular avatar with optional 16dp verified-check overlay at
     * the bottom-right. Used by Connections (large) and Review claims
     * (medium) and Discover hub people rows (small).
     */
    data class AvatarWithBadge(
        val name: String,
        val imageUrl: String?,
        val background: AvatarBackground,
        val size: AvatarBadgeSize,
        val verified: Boolean = false,
    ) : RowLeading

    /** Rounded thumbnail (56dp or 64dp) used by Offers and Pets. */
    data class Thumbnail(
        val image: ThumbnailImage,
        val size: ThumbnailSize,
    ) : RowLeading

    /**
     * Overlapping mini-avatars + `+N` overflow tile, used by My tasks
     * bidder summary.
     */
    data class BidderStack(
        val bidders: List<Bidder>,
        val overflow: Int = 0,
    ) : RowLeading
}

// MARK: - Compact button shared variant

/** Variant for compact in-row buttons. Used by [RowTrailing.VerticalActions]
 *  and [RowFooterAction]. */
enum class CompactButtonVariant { Primary, Ghost, Destructive }

/** Single action description for [RowTrailing.VerticalActions]. */
data class VerticalAction(
    val label: String,
    val variant: CompactButtonVariant,
    val onClick: () -> Unit,
)

// MARK: - Trailing

/** Trailing payload — rendered according to the chosen [RowTemplate]. */
sealed interface RowTrailing {
    data object None : RowTrailing

    data object Chevron : RowTrailing

    data object Kebab : RowTrailing

    data class Status(
        val text: String,
        val variant: StatusChipVariant,
    ) : RowTrailing

    // ─── T5 additions ──────────────────────────────────────────

    /** Amount on top + status chip stacked below — Bills. */
    data class AmountWithChip(
        val amount: String,
        val chipText: String,
        val chipVariant: StatusChipVariant,
        val chipIcon: PantopusIcon? = null,
    ) : RowTrailing

    /** Single circular icon-button — Connections "message" CTA. */
    data class CircularAction(
        val icon: PantopusIcon,
        val accessibilityLabel: String,
        val background: Color,
        val foreground: Color,
        val onClick: () -> Unit,
    ) : RowTrailing

    /** Stacked Accept / Ignore pair — Connections pending requests. */
    data class VerticalActions(
        val primary: VerticalAction,
        val secondary: VerticalAction,
    ) : RowTrailing

    /** Price stack — amount on top, optional sublabel below. */
    data class PriceStack(
        val amount: String,
        val sublabel: String? = null,
    ) : RowTrailing
}

// MARK: - Chip

/** A chip rendered inline with the title (Pets species pill) or in the
 *  chip row beneath the body (My posts intent chip, status chip, counter). */
data class RowChip(
    val text: String,
    val icon: PantopusIcon? = null,
    val tint: Tint,
) {
    sealed interface Tint {
        data class Status(val variant: StatusChipVariant) : Tint

        data class Custom(val background: Color, val foreground: Color) : Tint
    }
}

// MARK: - Footer

/** One action inside [RowFooter]. */
data class RowFooterAction(
    val title: String,
    val icon: PantopusIcon? = null,
    val variant: CompactButtonVariant = CompactButtonVariant.Primary,
    /** Flex weight inside the footer row. Default 1. */
    val flex: Int = 1,
    val onClick: () -> Unit,
)

/** Optional in-card footer for a row — 1–3 inline compact buttons separated
 *  from the card body by a hairline. */
data class RowFooter(
    val actions: List<RowFooterAction>,
)

// MARK: - Highlight

/** Optional visual highlight wrapping the whole card. */
sealed interface RowHighlight {
    /** Notification unread row — primary25 background + personalBg border + dot. */
    data object Unread : RowHighlight

    /** Listing-offer "LEADING" row — amber border + badge. */
    data object Leading : RowHighlight

    /** My-posts archived row — 0.78 opacity. */
    data object Archived : RowHighlight

    /**
     * Terminal / non-actionable row — 0.78 opacity. Used by My bids for
     * rejected / withdrawn / expired / task-cancelled rows so the user
     * can scan them at a glance without confusing them for live bids.
     * Same visual effect as [Archived] but semantically distinct so
     * other screens (Review claims, completed offers) can opt in
     * without overloading the "archived" intent.
     */
    data object Muted : RowHighlight
}

// MARK: - RowModel

/**
 * A single row. ViewModels map their DTOs into a list of these.
 *
 * **Backwards compat:** all T5 fields default to `null` / empty so every
 * existing call site compiles unchanged.
 */
data class RowModel(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val template: RowTemplate,
    val leading: RowLeading = RowLeading.None,
    val trailing: RowTrailing = RowTrailing.None,
    val onTap: () -> Unit = {},
    val onSecondary: (() -> Unit)? = null,
    // ─── T5 additions ──────────────────────────────────────────
    /** Multiline body rendered below the subtitle (notifications, my posts). */
    val body: String? = null,
    /**
     * Optional small icon prefix for the subtitle line. Used by Connections
     * (map-pin in front of locality).
     */
    val subtitleIcon: PantopusIcon? = null,
    /**
     * Optional small icon prefix for the body line. Used by Connections
     * (per-row interaction-type icon: message-circle / wrench / megaphone /
     * user-plus / sparkles).
     */
    val bodyIcon: PantopusIcon? = null,
    /** Chip rendered inline with the title (Pets species pill). */
    val inlineChip: RowChip? = null,
    /** Chip row beneath the body (intent / status / counter chips). */
    val chips: List<RowChip>? = null,
    /** Small far-right time text on the chip row. */
    val timeMeta: String? = null,
    /** Text appended after the chip row, separated with "·". */
    val metaTail: String? = null,
    /** Italic block quote rendered below the row body (offer notes). */
    val note: String? = null,
    /** Optional visual highlight wrapping the row. */
    val highlight: RowHighlight? = null,
    /** Optional in-card footer with 1–3 compact buttons. */
    val footer: RowFooter? = null,
    /**
     * Optional bidder stack rendered inline on the chip line before
     * the [chips]. Used by My tasks V2 — 22dp overlapping avatars
     * with a `+N` overflow tile.
     */
    val bidderStack: BidderStackData? = null,
)

// MARK: - Section

/** Rendering style for a [RowSection]. */
enum class SectionStyle {
    /** Default — matches T1–T4.1 behaviour (rows separated by spacing). */
    Flat,

    /**
     * Rows grouped inside a single rounded card with hairline dividers
     * between them. Used by Discover hub's typed sections.
     */
    Card,
}

/** Optional grouping for the list body. */
data class RowSection(
    val id: String,
    val header: String? = null,
    val rows: List<RowModel>,
    // ─── T5 additions ──────────────────────────────────────────
    val count: Int? = null,
    val onSeeAll: (() -> Unit)? = null,
    val style: SectionStyle = SectionStyle.Flat,
)
