@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.gigs.tasks_map

import androidx.compose.runtime.Immutable
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapPin
import app.pantopus.android.ui.screens.shared.map_list_hybrid.MapPinState
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * A11.1 Tasks map — the Gigs-only mode of the map+list hybrid archetype
 * (`ui/screens/shared/map_list_hybrid`). Reached from the Gigs feed's
 * list/map toggle. Same canvas as the generic Nearby map, filtered to
 * tasks with a "Post task" button below the locate / layers controls.
 *
 * No backend — the view-model seeds from [TasksMapSampleData].
 */
@Immutable
data class TaskMapItem(
    val id: String,
    val category: GigsCategory,
    val state: MapPinState,
    val latitude: Double,
    val longitude: Double,
    val title: String,
    /** Free-form price string ("$60", "$22/walk", "$180"). */
    val price: String,
    val distanceLabel: String,
    val bidCount: Int,
) {
    /** Projection into the shell's pin model — colour from the gig category
     * swatch, white-ring / dashed-outline treatment from [state]. */
    fun toPin(): MapPin =
        MapPin(
            id = id,
            latitude = latitude,
            longitude = longitude,
            color = category.color,
            state = state,
        )
}

/** Render state for the Tasks map — mirrors the four-state contract. */
sealed interface TasksMapUiState {
    data object Loading : TasksMapUiState

    data class Populated(val items: List<TaskMapItem>) : TasksMapUiState

    data object Empty : TasksMapUiState

    data class Error(val message: String) : TasksMapUiState
}

/**
 * Rail-card tile glyph for a task category. Best-fit from the typed icon
 * set for the design's per-category Lucide glyphs — `spray-can`, `truck`,
 * and `book-open` aren't in [PantopusIcon], so `Sparkles`, `Package`, and
 * `GraduationCap` stand in. Mirrors iOS `taskCategoryGlyph`.
 */
fun taskCategoryGlyph(category: GigsCategory): PantopusIcon =
    when (category) {
        GigsCategory.All -> PantopusIcon.Circle
        GigsCategory.Handyman -> PantopusIcon.Hammer
        GigsCategory.Cleaning -> PantopusIcon.Sparkles
        GigsCategory.Moving -> PantopusIcon.Package
        GigsCategory.PetCare -> PantopusIcon.PawPrint
        GigsCategory.ChildCare -> PantopusIcon.Baby
        GigsCategory.Tutoring -> PantopusIcon.GraduationCap
        GigsCategory.Tech -> PantopusIcon.Laptop
        GigsCategory.Delivery -> PantopusIcon.Send
    }
