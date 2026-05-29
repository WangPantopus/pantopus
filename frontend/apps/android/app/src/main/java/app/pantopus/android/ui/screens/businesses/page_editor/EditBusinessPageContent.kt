@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses.page_editor

/**
 * P4.2 — A13.10 Edit Business Page. Render-only data model for the
 * business-profile editor. Mirrors the iOS [EditBusinessPageContent]
 * shape so the same projections work on both platforms.
 */

/** Top-level mode — drives the strip + sticky save bar variants. */
sealed interface EditBusinessPageMode {
    /** Established business mid-edit. */
    data class Published(
        val unsavedCount: Int,
        val lastPublishedLabel: String,
    ) : EditBusinessPageMode

    /** Newly claimed business in setup. */
    data class Setup(
        val done: Int,
        val total: Int,
        val remaining: Int,
        val items: List<EditBusinessPageSetupItem>,
    ) : EditBusinessPageMode
}

/** One chip in the completion strip. */
data class EditBusinessPageSetupItem(
    val id: String,
    val label: String,
    val done: Boolean,
)

/** Per-field edit state — `current` is what the user typed. */
data class EditBusinessPageField(
    val original: String,
    val current: String,
    val placeholder: String = "",
) {
    val isDirty: Boolean
        get() = original != current
}

/** Banner state. */
sealed interface EditBusinessPageBannerState {
    data object Empty : EditBusinessPageBannerState

    data class Filled(
        val dirty: Boolean,
        val palette: BannerPalette = BannerPalette.CafeGoldenHour,
    ) : EditBusinessPageBannerState

    enum class BannerPalette { CafeGoldenHour }
}

/** Logo state. */
sealed interface EditBusinessPageLogoState {
    data object Empty : EditBusinessPageLogoState

    data class Filled(
        val initial: String,
        val palette: LogoPalette = LogoPalette.Sunrise,
    ) : EditBusinessPageLogoState

    enum class LogoPalette { Sunrise }
}

/** One day in the hours card. */
data class EditBusinessPageHoursRow(
    val id: String,
    val dayLabel: String,
    val state: State,
    val isDirty: Boolean = false,
) {
    sealed interface State {
        data class Open(
            val openLabel: String,
            val closeLabel: String,
        ) : State

        data object Closed : State

        data object NotSet : State
    }
}

/** Hours section state. */
sealed interface EditBusinessPageHoursState {
    data class Rows(
        val rows: List<EditBusinessPageHoursRow>,
        val footerHint: String?,
    ) : EditBusinessPageHoursState

    data class QuickApply(
        val rows: List<EditBusinessPageHoursRow>,
    ) : EditBusinessPageHoursState
}

/** Service chip. */
data class EditBusinessPageServiceChip(
    val id: String,
    val label: String,
    val iconKey: String,
    val isFresh: Boolean = false,
)

sealed interface EditBusinessPageServicesState {
    data class Chips(val chips: List<EditBusinessPageServiceChip>) : EditBusinessPageServicesState

    data class Prompt(val prompt: EditBusinessPagePrompt) : EditBusinessPageServicesState
}

/** One gallery tile. */
data class EditBusinessPageGalleryTile(
    val id: String,
    val palette: Palette,
    val isCover: Boolean = false,
) {
    enum class Palette { Croissant, Coffee, Interior, Bread, Latte, Crowd }
}

data class EditBusinessPageGalleryState(
    val tiles: List<EditBusinessPageGalleryTile>,
    val totalSlots: Int = 20,
    val freshAddTile: Boolean = false,
    val hintLabel: String,
) {
    val isEmpty: Boolean get() = tiles.isEmpty()
}

/** Setup-mode prompt block (Description / Services). */
data class EditBusinessPagePrompt(
    val iconKey: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
)

sealed interface EditBusinessPageDescriptionState {
    data class Field(
        val field: EditBusinessPageField,
        val charLimit: Int,
    ) : EditBusinessPageDescriptionState

    data class Prompt(val prompt: EditBusinessPagePrompt) : EditBusinessPageDescriptionState
}

/** Address + map state. */
data class EditBusinessPageLocation(
    val address: EditBusinessPageField,
    val error: String? = null,
    val mapVerified: Boolean,
    val pinDirty: Boolean = false,
    val hideExactAddress: Boolean,
)

/** Render-ready payload — mirrors the iOS struct field-by-field. */
data class EditBusinessPageContent(
    val businessId: String,
    val mode: EditBusinessPageMode,
    val banner: EditBusinessPageBannerState,
    val logo: EditBusinessPageLogoState,
    val name: EditBusinessPageField,
    val tagline: EditBusinessPageField,
    val category: EditBusinessPageField,
    val categoryRequired: Boolean,
    val price: EditBusinessPageField,
    val description: EditBusinessPageDescriptionState,
    val hours: EditBusinessPageHoursState,
    val services: EditBusinessPageServicesState,
    val gallery: EditBusinessPageGalleryState,
    val phone: EditBusinessPageField,
    val email: EditBusinessPageField,
    val website: EditBusinessPageField,
    val bookingLink: EditBusinessPageField?,
    val location: EditBusinessPageLocation,
)

/** Observed UI state. */
sealed interface EditBusinessPageUiState {
    data object Loading : EditBusinessPageUiState

    data class Loaded(val content: EditBusinessPageContent) : EditBusinessPageUiState

    data class Error(val message: String) : EditBusinessPageUiState
}
