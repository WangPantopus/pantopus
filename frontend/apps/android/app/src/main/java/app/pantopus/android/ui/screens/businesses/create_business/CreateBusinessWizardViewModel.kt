@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses.create_business

import androidx.lifecycle.ViewModel
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.shared.wizard.WizardChrome
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardModel
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Aggregate UI state for the A12.10 Create Business wizard. Combined so
 * the screen derives the [WizardChrome] from a single state read.
 */
data class CreateBusinessUiState(
    val currentStep: CreateBusinessStep = CreateBusinessStep.PickCategory,
    /**
     * Seeded to [BusinessCategory.Home] so the wizard opens onto the
     * populated frame with a sensible default selection.
     */
    val selectedCategory: BusinessCategory? = BusinessCategory.Home,
    val searchText: String = "",
    val isSubmittingCustom: Boolean = false,
    val submitError: String? = null,
) {
    val isSearchActive: Boolean
        get() = searchText.trim().isNotEmpty()

    val searchHits: List<CategorySearchHit>
        get() = CreateBusinessSampleData.searchHits(searchText)

    val whatYouGetItems: List<WhatYouGetItem>
        get() =
            if (selectedCategory == BusinessCategory.Home) {
                CreateBusinessSampleData.homeServicesWhatYouGet
            } else {
                emptyList()
            }
}

/**
 * Drives the A12.10 Create Business wizard. Step 1 (pick category) is
 * the only step the new design ships frames for; steps 2-4 are stubs
 * the VM still routes through so the progress rail reads as
 * `N of 4` all the way through. A follow-on prompt replaces the stub
 * step plumbing once design hands off the remaining frames.
 *
 * The custom-category submit (search frame's "Add as custom category"
 * fallback) routes through a stub helper marked TODO — per audit open
 * question #3 the backend doesn't yet accept the payload. The wizard
 * surfaces a success-looking advance to step 2 so the UI flow is
 * exercisable end-to-end against the design; the network call moves
 * behind a real endpoint once backend ships it.
 */
@HiltViewModel
open class CreateBusinessWizardViewModel
    @Inject
    constructor() :
    ViewModel(),
        WizardModel {
        private val _state = MutableStateFlow(CreateBusinessUiState())
        val state: StateFlow<CreateBusinessUiState> = _state.asStateFlow()

        /** One-shot navigation events the screen reacts to. */
        val pendingEvent = MutableStateFlow<CreateBusinessOutboundEvent?>(null)

        // MARK: - WizardModel

        override val chrome: WizardChrome
            get() = computeChrome(_state.value)

        override fun onLeading() {
            when (_state.value.currentStep) {
                CreateBusinessStep.PickCategory ->
                    pendingEvent.value = CreateBusinessOutboundEvent.Dismiss
                CreateBusinessStep.LegalInfo -> transitionTo(CreateBusinessStep.PickCategory)
                CreateBusinessStep.Profile -> transitionTo(CreateBusinessStep.LegalInfo)
                CreateBusinessStep.Confirm -> transitionTo(CreateBusinessStep.Profile)
            }
        }

        override fun onDiscard() {
            pendingEvent.value = CreateBusinessOutboundEvent.Dismiss
        }

        override fun onPrimary() {
            val current = _state.value
            when (current.currentStep) {
                CreateBusinessStep.PickCategory -> {
                    // CTA gated open by chrome.primaryCtaEnabled when a
                    // category is selected; the explicit nullcheck here
                    // is a belt-and-braces guard.
                    if (current.selectedCategory == null) return
                    transitionTo(CreateBusinessStep.LegalInfo)
                }
                CreateBusinessStep.LegalInfo -> transitionTo(CreateBusinessStep.Profile)
                CreateBusinessStep.Profile -> transitionTo(CreateBusinessStep.Confirm)
                CreateBusinessStep.Confirm -> {
                    _state.update {
                        it.copy(
                            submitError =
                                "Business name, username, and email are required before this can be submitted.",
                        )
                    }
                }
            }
        }

        // MARK: - Selection

        fun selectCategory(category: BusinessCategory) {
            _state.update { it.copy(selectedCategory = category, submitError = null) }
        }

        fun selectSearchHit(hit: CategorySearchHit) {
            _state.update {
                it.copy(selectedCategory = hit.category, searchText = "")
            }
        }

        fun setSearchText(value: String) {
            _state.update { it.copy(searchText = value) }
        }

        /**
         * Submit the typed search string as a custom category candidate.
         * Per audit open question #3 the backend doesn't yet accept the
         * payload. Keep the user on the search step with an explicit error
         * until a real `POST /api/businesses/custom-categories` route ships.
         */
        fun submitCustomCategory() {
            val current = _state.value
            val trimmed = current.searchText.trim()
            if (trimmed.isEmpty() || current.isSubmittingCustom) return
            _state.update {
                it.copy(
                    isSubmittingCustom = true,
                    submitError = null,
                )
            }
            _state.update {
                it.copy(
                    isSubmittingCustom = false,
                    submitError = "Custom categories are not accepted by the backend yet.",
                )
            }
            Analytics.track(AnalyticsEvent.CtaCreateBusinessCustomCategorySubmit(label = trimmed))
        }

        fun acknowledgeEvent() {
            pendingEvent.value = null
        }

        // MARK: - Step transitions

        private fun transitionTo(step: CreateBusinessStep) {
            _state.update { it.copy(currentStep = step) }
            Analytics.track(
                AnalyticsEvent.ScreenCreateBusinessStepViewed(
                    stepNumber = step.stepNumber,
                    stepName = step.name.lowercase(),
                ),
            )
        }

        // MARK: - Chrome derivation

        private fun computeChrome(state: CreateBusinessUiState): WizardChrome {
            val label =
                WizardProgressLabel.StepOf(
                    current = state.currentStep.stepNumber,
                    total = CreateBusinessStep.TOTAL_STEPS,
                )
            val fraction =
                state.currentStep.stepNumber.toFloat() / CreateBusinessStep.TOTAL_STEPS.toFloat()
            return WizardChrome(
                title = "Create business",
                progressLabel = label,
                progressFraction = fraction,
                leading =
                    if (state.currentStep == CreateBusinessStep.PickCategory) {
                        WizardLeadingControl.Close
                    } else {
                        WizardLeadingControl.Back
                    },
                primaryCtaLabel = primaryLabel(state.currentStep),
                primaryCtaEnabled = primaryEnabled(state),
                secondaryCta = null,
                isSubmitting = state.isSubmittingCustom,
                // Once the user has picked a non-default category, or
                // typed a search query, an X tap must surface the
                // discard-confirm so the partial selection isn't dropped
                // silently.
                dirty = isDirty(state),
                showsProgressBar = true,
            )
        }

        private fun primaryLabel(step: CreateBusinessStep): String =
            when (step) {
                CreateBusinessStep.PickCategory -> "Continue"
                CreateBusinessStep.LegalInfo -> "Next"
                CreateBusinessStep.Profile -> "Next"
                CreateBusinessStep.Confirm -> "Confirm"
            }

        private fun primaryEnabled(state: CreateBusinessUiState): Boolean =
            when (state.currentStep) {
                CreateBusinessStep.PickCategory ->
                    state.selectedCategory != null && !state.isSubmittingCustom
                CreateBusinessStep.LegalInfo,
                CreateBusinessStep.Profile,
                -> true
                CreateBusinessStep.Confirm -> false
            }

        private fun isDirty(state: CreateBusinessUiState): Boolean {
            // The PickCategory step seeds `selectedCategory = .Home` so
            // the user opens onto a sensible default. Treat "still on
            // that default with no typed query and step 1" as clean —
            // every other state earns the discard confirm.
            if (state.currentStep == CreateBusinessStep.PickCategory &&
                state.selectedCategory == BusinessCategory.Home &&
                !state.isSearchActive
            ) {
                return false
            }
            return true
        }
    }
