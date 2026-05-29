@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.shared.grouped_list

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.components.ChannelGlyph
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Render models for the shared GroupedList archetype — every
 * settings-style surface in the app uses this shape. Two levels:
 * `groups[]` with optional overline + helper caption + `rows[]`. Each
 * row carries a single [RowControl] that drives the right-side
 * affordance (chevron, toggle, radio, chip+chevron, slider).
 */

/** The right-side control on one row. */
sealed interface RowControl {
    /** Plain navigation row — just a chevron on the right. */
    data object Chevron : RowControl

    /** Binary preference toggle. */
    @Immutable
    data class Toggle(val isOn: Boolean) : RowControl

    /** Radio selection within the group. */
    @Immutable
    data class Radio(val isSelected: Boolean) : RowControl

    /**
     * Status / value chip. `includesChevron` adds a chevron after the
     * chip when the row is also navigable.
     */
    @Immutable
    data class ChipStatus(
        val label: String,
        val tone: ChipTone,
        val includesChevron: Boolean,
    ) : RowControl

    /** Stops-based slider. */
    @Immutable
    data class Slider(
        val stops: List<String>,
        val index: Int,
    ) : RowControl

    /**
     * A14.5 Notifications — three Push / Email / SMS channel chips
     * (`ChannelTriad`) tiled into the trailing slot. `locked` forces a
     * chip "on, untoggleable" — Emergency alerts keep push locked on.
     */
    @Immutable
    data class ChannelTriad(
        val p: Boolean,
        val e: Boolean,
        val s: Boolean,
        val locked: Set<ChannelGlyph>,
    ) : RowControl

    enum class ChipTone { Success, Info, Neutral, Warning }
}

/** One row in a group. */
@Immutable
data class GroupedListRow(
    val id: String,
    val label: String,
    /** Optional secondary line under the label. */
    val subtext: String? = null,
    val control: RowControl,
    /** Red destructive text + this row lands in its own card. */
    val destructive: Boolean = false,
)

/** One group — a card of rows with optional overline + helper. */
@Immutable
data class GroupedListGroup(
    val id: String,
    /** 11sp uppercase overline above the card. `null` hides it. */
    val overline: String? = null,
    /** 11.5sp caption below the card. */
    val helper: String? = null,
    val rows: List<GroupedListRow>,
    /**
     * A14.5 — render a P/E/S column-header band (`ChannelHeader`) as the
     * first element inside the card. `false` for every other surface.
     */
    val showsChannelHeader: Boolean = false,
)

/**
 * A14.5 — a banner pinned above the groups inside the scroll. The
 * paused-notifications state swaps its Master card for one of these.
 * Rendered by `PauseBanner`.
 */
@Immutable
data class GroupedListBanner(
    val icon: PantopusIcon,
    val title: String,
    val subtitle: String? = null,
    val actionLabel: String,
)

/** Render state for the shell. */
sealed interface GroupedListUiState {
    data object Loading : GroupedListUiState

    data class Loaded(val groups: List<GroupedListGroup>) : GroupedListUiState

    data class Error(val message: String) : GroupedListUiState
}
