@file:Suppress("PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.shared.list_of_rows

import app.pantopus.android.ui.theme.PantopusIcon

/** Lifecycle state for the List-of-Rows shell. */
sealed interface ListOfRowsUiState {
    /** Initial / refreshing state. */
    data object Loading : ListOfRowsUiState

    /** Loaded content. */
    data class Loaded(
        val sections: List<RowSection>,
        val hasMore: Boolean,
    ) : ListOfRowsUiState

    /** No items — render the shared EmptyState. */
    data class Empty(
        val icon: PantopusIcon,
        val headline: String,
        val subcopy: String,
        val ctaTitle: String? = null,
        val onCta: (() -> Unit)? = null,
    ) : ListOfRowsUiState

    /** Transport / server error — render banner + retry. */
    data class Error(
        val message: String,
    ) : ListOfRowsUiState
}

/** Tab strip entry. */
data class ListOfRowsTab(
    val id: String,
    val label: String,
    val count: Int? = null,
)

/**
 * Top-bar trailing action payload.
 *
 * T5.0 additive: [label] and [isEnabled] are new optional fields with
 * backwards-compatible defaults. Existing call sites pass
 * `(icon=, contentDescription=, onClick=)` and render as an icon-only
 * button. Notifications V2 passes a text [label] instead — the shell
 * renders the text in primary tint and respects [isEnabled] for the
 * disabled state in the design's empty-unread frame.
 */
data class TopBarAction(
    val icon: PantopusIcon,
    val contentDescription: String,
    val onClick: () -> Unit,
    val label: String? = null,
    val isEnabled: Boolean = true,
)

/**
 * FAB payload.
 *
 * T5.0 adds a [variant]:
 *  - [FabVariant.CanonicalCreate] (56dp) — primary create action of the
 *    screen (My tasks V2 "Post a task"). **Default** for backwards
 *    compat — every existing `FabAction(icon=, contentDescription=, onClick=)`
 *    constructor call renders 56dp exactly as before.
 *  - [FabVariant.SecondaryCreate] (52dp) — non-canonical create action
 *    (My posts, Connections, Bills, Pets).
 *  - [FabVariant.ExtendedNav] (48dp pill with label) — navigation FAB
 *    that signals "go elsewhere", not "create" (My bids "Browse tasks").
 */
data class FabAction(
    val icon: PantopusIcon = PantopusIcon.PlusCircle,
    val contentDescription: String,
    val variant: FabVariant = FabVariant.CanonicalCreate,
    val onClick: () -> Unit,
)

sealed interface FabVariant {
    /** 56dp round. Default — historical T1–T4.1 geometry. */
    data object CanonicalCreate : FabVariant

    /** 52dp round. */
    data object SecondaryCreate : FabVariant

    /** 48dp pill with a label beside the icon. */
    data class ExtendedNav(val label: String) : FabVariant
}

// ─── T5 chrome slots (all default-null) ─────────────────────────

/**
 * Optional search bar rendered between the top bar and the tab strip.
 * Used by Connections and Discover businesses.
 */
data class SearchBarConfig(
    val placeholder: String,
    val text: String,
    val onChange: (String) -> Unit,
    val onSubmit: (() -> Unit)? = null,
)

/**
 * Horizontally scrollable chip-filter strip used as an alternative to
 * tabs for filtering. Used by Discover hub and Discover businesses.
 */
data class ChipStripConfig(
    val chips: List<Chip>,
    val selectedId: String,
    val onSelect: (String) -> Unit,
) {
    data class Chip(
        val id: String,
        val label: String,
        val icon: PantopusIcon? = null,
    )
}

/**
 * Primary-tinted summary banner rendered above the first row in the
 * scroll area. Used by My bids, My tasks, Offers, Review claims.
 */
data class BannerConfig(
    val icon: PantopusIcon,
    val title: String,
    val subtitle: String? = null,
    val onTap: (() -> Unit)? = null,
)
