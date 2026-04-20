@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.shared.list_of_rows

import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.theme.PantopusIcon

/** Visual template for a list row. */
enum class RowTemplate { StatusChip, FileChevron, AvatarKebab }

/** Optional leading visual. */
sealed interface RowLeading {
    data object None : RowLeading

    data class Icon(
        val icon: PantopusIcon,
        val tint: androidx.compose.ui.graphics.Color,
    ) : RowLeading

    data class Avatar(
        val name: String,
        val imageUrl: String?,
        val identity: IdentityPillar,
        val ringProgress: Float,
    ) : RowLeading
}

/** Trailing payload — rendered according to the chosen [RowTemplate]. */
sealed interface RowTrailing {
    data object None : RowTrailing

    data object Chevron : RowTrailing

    data object Kebab : RowTrailing

    data class Status(val text: String, val variant: StatusChipVariant) : RowTrailing
}

/**
 * A single row. ViewModels map their DTOs into a list of these.
 *
 * @property id Stable row id for lazy-list keying.
 * @property title Primary text.
 * @property subtitle Optional secondary text.
 * @property template Visual template to apply.
 * @property leading Optional leading visual.
 * @property trailing Optional trailing visual.
 * @property onTap Invoked when the whole row is tapped.
 * @property onSecondary Optional handler for the kebab menu.
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
)

/** Optional grouping for the list body. */
data class RowSection(
    val id: String,
    val header: String? = null,
    val rows: List<RowModel>,
)
