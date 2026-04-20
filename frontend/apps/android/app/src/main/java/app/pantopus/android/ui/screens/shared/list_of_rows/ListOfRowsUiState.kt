@file:Suppress("PackageNaming")

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
    data class Error(val message: String) : ListOfRowsUiState
}

/** Tab strip entry. */
data class ListOfRowsTab(
    val id: String,
    val label: String,
    val count: Int? = null,
)

/** Top-bar trailing action payload. */
data class TopBarAction(
    val icon: PantopusIcon,
    val contentDescription: String,
    val onClick: () -> Unit,
)

/** FAB payload. */
data class FabAction(
    val icon: PantopusIcon = PantopusIcon.PlusCircle,
    val contentDescription: String,
    val onClick: () -> Unit,
)
