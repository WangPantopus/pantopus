@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.add_home

/**
 * The four pre-success steps of the Add-Home wizard, in order. The
 * success state is a sentinel terminal step used to render the success
 * hero block.
 */
enum class AddHomeStep(val ordinal0: Int) {
    Address(0),
    Confirm(1),
    Role(2),
    Review(3),
    Success(4),
    ;

    /** One-indexed position used in the "N of M" top-bar readout, or null
     *  for the success terminal. */
    val stepNumber: Int?
        get() = when (this) {
            Address -> 1
            Confirm -> 2
            Role -> 3
            Review -> 4
            Success -> null
        }

    companion object {
        /** Total number of "step N of M" steps shown in the readout. */
        const val PROGRESS_TOTAL: Int = 4

        fun fromOrdinal(value: Int): AddHomeStep =
            entries.firstOrNull { it.ordinal0 == value } ?: Address
    }
}

/**
 * User-supplied address fields in step 1. We deviate from the design's
 * "single typeahead" because the backend's `propertySuggestions` Joi
 * schema requires `{address, city, state, zipCode}` rather than a
 * free-form `{query}` (`backend/routes/home.js:540`).
 *
 * TODO(backend): expose a query-only autocomplete to enable the design's
 * single-input typeahead UX in step 1.
 */
data class AddHomeAddressFields(
    val street: String = "",
    val unit: String = "",
    val city: String = "",
    val state: String = "",
    val zipCode: String = "",
) {
    /** True when every required component has at least one non-blank char. */
    val isComplete: Boolean
        get() =
            street.trim().isNotEmpty() &&
                city.trim().isNotEmpty() &&
                state.trim().isNotEmpty() &&
                zipCode.trim().isNotEmpty()
}

/** User's role on the home being added — picked in step 3. */
enum class AddHomeRole(val label: String) {
    Owner("Owner"),
    Tenant("Tenant"),
    HouseholdMember("Household member"),
}

/**
 * Snapshot of all wizard form state. The view model mirrors each field
 * into [androidx.lifecycle.SavedStateHandle] so the wizard survives
 * config changes and process death (acceptance criterion #5).
 */
data class AddHomeFormState(
    val step: Int = AddHomeStep.Address.ordinal0,
    val address: AddHomeAddressFields = AddHomeAddressFields(),
    val isPrimary: Boolean = true,
    val role: AddHomeRole? = null,
) {
    val currentStep: AddHomeStep
        get() = AddHomeStep.fromOrdinal(step)

    companion object {
        val EMPTY = AddHomeFormState()
    }
}

/** Outbound navigation events the screen consumes. */
sealed interface AddHomeOutboundEvent {
    /** Pop the wizard with no further navigation. */
    data object Dismiss : AddHomeOutboundEvent

    /** Pop the wizard and navigate to the new home dashboard. */
    data class OpenHomeDashboard(val homeId: String) : AddHomeOutboundEvent
}
