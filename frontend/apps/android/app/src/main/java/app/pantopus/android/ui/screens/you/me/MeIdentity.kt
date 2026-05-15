@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.you.me

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon

/** Three identity bindings for the Me tab. */
enum class MeIdentity(val key: String, val label: String, val icon: PantopusIcon) {
    Personal("personal", "Personal", PantopusIcon.User),
    Home("home", "Home", PantopusIcon.Home),
    Business("business", "Business", PantopusIcon.ShoppingBag),
    ;

    /** Header gradient + action-grid accent color. */
    val accent: Color
        get() =
            when (this) {
                Personal -> PantopusColors.primary600
                Home -> PantopusColors.home
                Business -> PantopusColors.business
            }

    /** Soft tint for the header gradient top + pill background. */
    val accentBg: Color
        get() =
            when (this) {
                Personal -> PantopusColors.primary50
                Home -> PantopusColors.homeBg
                Business -> PantopusColors.businessBg
            }

    companion object {
        fun fromKey(key: String): MeIdentity = entries.firstOrNull { it.key == key } ?: Personal
    }
}

/** One cell in the 4-cell stats row. */
@Immutable
data class MeStat(
    val id: String,
    val value: String,
    val label: String,
)

/** One tile in the 2×3 action grid. */
@Immutable
data class MeActionTile(
    val id: String,
    val icon: PantopusIcon,
    val label: String,
    val badge: Int? = null,
    val routeKey: String,
)

/** One row in a section group. */
@Immutable
data class MeSectionRow(
    val id: String,
    val icon: PantopusIcon,
    val label: String,
    val value: String? = null,
    val routeKey: String,
)

/** A grouped section in the lower stack (Account · Activity · Support). */
@Immutable
data class MeSection(
    val id: String,
    val header: String,
    val rows: List<MeSectionRow>,
)

/** VM-prepared bundle for a single identity binding. */
@Immutable
data class MeIdentityContent(
    val identity: MeIdentity,
    val displayName: String,
    val initials: String,
    val handle: String,
    val locality: String?,
    val bio: String?,
    val verified: Boolean,
    val stats: List<MeStat>,
    val actionTiles: List<MeActionTile>,
    val sections: List<MeSection>,
    val isUnbound: Boolean = false,
)
