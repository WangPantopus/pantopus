@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses.create_business

import androidx.compose.ui.graphics.Color
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * Steps the A12.10 Create Business wizard advances through. Step 1 is the
 * only step the new design ships frames for; steps 2-4 are stubs the
 * wizard still routes through so the progress rail reads as
 * `N of 4` all the way through. A follow-on prompt replaces the stub
 * step bodies once design hands off the remaining frames.
 */
enum class CreateBusinessStep {
    PickCategory,
    LegalInfo,
    Profile,
    Confirm,
    ;

    /** 1-indexed position used by the wizard's "N of M" readout. */
    val stepNumber: Int
        get() =
            when (this) {
                PickCategory -> 1
                LegalInfo -> 2
                Profile -> 3
                Confirm -> 4
            }

    companion object {
        /** Total number of steps in the wizard. Matches the audit's `1 of 4`. */
        const val TOTAL_STEPS: Int = 4
    }
}

/**
 * Category tiles rendered in the 2×4 picker grid. Order is meaningful —
 * the grid renders row-major in this order, with [Other] always last so
 * the "Something else" tile sits in the bottom-right.
 */
enum class BusinessCategory(
    val label: String,
    val subcopy: String,
    val icon: PantopusIcon,
    val backendSlug: String,
) {
    Home(
        label = "Home services",
        subcopy = "Handyman · cleaning · moving",
        icon = PantopusIcon.Wrench,
        backendSlug = "home_services",
    ),
    Personal(
        label = "Personal services",
        subcopy = "Tutoring · childcare · pet care",
        icon = PantopusIcon.GraduationCap,
        backendSlug = "personal_services",
    ),
    Tech(
        label = "Tech & repair",
        subcopy = "Devices · networks · break-fix",
        icon = PantopusIcon.Cpu,
        backendSlug = "tech_repair",
    ),
    Delivery(
        label = "Delivery & errands",
        subcopy = "Last-mile · courier · grocery",
        icon = PantopusIcon.Truck,
        backendSlug = "delivery_errands",
    ),
    Goods(
        label = "Goods & retail",
        subcopy = "Selling new or pre-loved items",
        icon = PantopusIcon.ShoppingBag,
        backendSlug = "goods_retail",
    ),
    Rentals(
        label = "Rentals",
        subcopy = "Short or long-term · gear · vehicles",
        icon = PantopusIcon.KeyRound,
        backendSlug = "rentals",
    ),
    Vehicles(
        label = "Vehicles & rideshare",
        subcopy = "Driving · towing · fleet",
        icon = PantopusIcon.Car,
        backendSlug = "vehicles_rideshare",
    ),
    Other(
        label = "Something else",
        subcopy = "Tell us what you do",
        icon = PantopusIcon.Sparkles,
        backendSlug = "other",
    ),
    ;

    /**
     * Per-category accent color used for the icon tile bg, the selected
     * ring, the check disc, and the selected-tile shadow. Tokens-only —
     * every value here is a [PantopusColors] swatch.
     */
    val accent: Color
        get() =
            when (this) {
                Home -> PantopusColors.handyman
                Personal -> PantopusColors.tutoring
                Tech -> PantopusColors.tech
                Delivery -> PantopusColors.delivery
                Goods -> PantopusColors.goods
                Rentals -> PantopusColors.rentals
                Vehicles -> PantopusColors.vehicles
                Other -> PantopusColors.business
            }
}

/** One row inside the "What you'll get" preview strip. */
data class WhatYouGetItem(
    val id: String,
    val icon: PantopusIcon,
    val label: String,
    val subcopy: String,
)

/**
 * One typeahead match returned by the search frame's filter. Carries the
 * owning category plus a sub-area sentence — what the audit calls the
 * "tutoring · K-12, test prep, music" line under each result.
 */
data class CategorySearchHit(
    val id: String,
    val category: BusinessCategory,
    val label: String,
)

/** Outbound navigation events the screen reacts to. */
sealed interface CreateBusinessOutboundEvent {
    /** Pop the wizard with no further navigation. */
    data object Dismiss : CreateBusinessOutboundEvent

    /**
     * Pop the wizard and route to the newly-created business profile.
     * Used by the (still-stub) confirm step once it ships.
     */
    data class OpenBusinessDashboard(val businessId: String) : CreateBusinessOutboundEvent
}
