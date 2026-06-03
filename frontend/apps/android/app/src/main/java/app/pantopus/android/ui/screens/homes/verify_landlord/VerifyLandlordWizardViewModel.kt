@file:Suppress("MagicNumber", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.homes.verify_landlord

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeVerificationRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.ui.screens.shared.wizard.WizardChrome
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardModel
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg key for the home being verified. */
const val VERIFY_LANDLORD_HOME_ID_KEY: String = "homeId"

/**
 * Aggregate UI state for the verify-landlord wizard. Combined into a
 * single record so the screen derives [WizardChrome] from one state
 * read.
 */
data class VerifyLandlordUiState(
    val currentStep: VerifyLandlordStep = VerifyLandlordStep.Start,
    val startContent: VerifyLandlordStartContent = VerifyLandlordSampleData.canonical,
    val form: VerifyLandlordForm = VerifyLandlordForm(),
    /**
     * Validation errors materialised lazily — `null` means "user
     * hasn't tried to submit yet, don't render error chips". Becomes
     * an empty [VerifyLandlordValidationErrors] or populated after the
     * first submit attempt.
     */
    val errors: VerifyLandlordValidationErrors? = null,
    val submitState: VerifyLandlordSubmitState = VerifyLandlordSubmitState.Idle,
) {
    val isSubmitting: Boolean get() = submitState is VerifyLandlordSubmitState.Submitting

    val isDirty: Boolean
        get() =
            form.ownerName.isNotEmpty() ||
                form.contactName.isNotEmpty() ||
                form.email.isNotEmpty() ||
                form.lease != null ||
                form.pmEnabled
}

/**
 * Drives the A12.5 / A12.6 wizard state machine:
 *
 *     Start -> Details -> submit -> OpenPostcardVerification(homeId)
 *
 * On submit it requests the verification postcard via
 * `POST /api/homes/:id/request-postcard` (route
 * `backend/routes/homeOwnership.js:2452`) then dispatches the outbound
 * `OpenPostcardVerification` event. The landlord/PM details collected in
 * the form have no backend representation (request-postcard takes no
 * body), so they stay client-side.
 */
@HiltViewModel
open class VerifyLandlordWizardViewModel
    @Inject
    constructor(
        private val networkMonitor: NetworkMonitor,
        savedStateHandle: SavedStateHandle,
        private val verificationRepository: HomeVerificationRepository,
    ) : ViewModel(),
        WizardModel {
        private val homeId: String =
            requireNotNull(savedStateHandle[VERIFY_LANDLORD_HOME_ID_KEY]) {
                "VerifyLandlordWizardViewModel requires a '$VERIFY_LANDLORD_HOME_ID_KEY' nav arg."
            }

        /** Configurable so JVM unit tests can drop the delay to zero. */
        protected open val submitDelayMillis: Long = SUBMIT_DELAY_DEFAULT_MILLIS

        private val _state =
            MutableStateFlow(
                VerifyLandlordUiState(
                    startContent = VerifyLandlordSampleData.startContent(homeId),
                    form = VerifyLandlordSampleData.formSeed(homeId),
                ),
            )
        val state: StateFlow<VerifyLandlordUiState> = _state.asStateFlow()

        /** One-shot navigation events the screen reacts to. */
        val pendingEvent = MutableStateFlow<VerifyLandlordOutboundEvent?>(null)

        // MARK: - WizardModel

        override val chrome: WizardChrome
            get() = computeChrome(_state.value)

        override fun onLeading() {
            when (_state.value.currentStep) {
                VerifyLandlordStep.Start -> pendingEvent.value = VerifyLandlordOutboundEvent.Dismiss
                VerifyLandlordStep.Details -> {
                    _state.update { it.copy(currentStep = VerifyLandlordStep.Start, errors = null) }
                }
            }
        }

        override fun onDiscard() {
            pendingEvent.value = VerifyLandlordOutboundEvent.Dismiss
        }

        override fun onPrimary() {
            when (_state.value.currentStep) {
                VerifyLandlordStep.Start -> {
                    _state.update { it.copy(currentStep = VerifyLandlordStep.Details) }
                }
                VerifyLandlordStep.Details -> viewModelScope.launch { submit() }
            }
        }

        override fun onSecondary() = Unit

        // MARK: - Field mutations

        fun setOwnerName(value: String) = updateForm { it.copy(ownerName = value) }

        fun setContactName(value: String) = updateForm { it.copy(contactName = value) }

        fun setEmail(value: String) = updateForm { it.copy(email = value) }

        fun setPhone(value: String) = updateForm(revalidate = false) { it.copy(phone = value) }

        fun setLease(lease: VerifyLandlordLeaseFile?) = updateForm { it.copy(lease = lease) }

        fun setPMEnabled(enabled: Boolean) =
            updateForm { current ->
                if (enabled) {
                    current.copy(pmEnabled = true)
                } else {
                    current.copy(pmEnabled = false, pmName = "", pmEmail = "", pmPhone = "")
                }
            }

        fun setPMName(value: String) = updateForm { it.copy(pmName = value) }

        fun setPMEmail(value: String) = updateForm { it.copy(pmEmail = value) }

        fun setPMPhone(value: String) = updateForm(revalidate = false) { it.copy(pmPhone = value) }

        /**
         * Used by previews / sample-data toggles + the dashboard
         * fast-track decision tree. Mirrors iOS' `setVariant(_:)`.
         */
        fun setVariant(variant: VerifyLandlordVariant) {
            val next =
                when (variant) {
                    VerifyLandlordVariant.Canonical -> VerifyLandlordSampleData.canonical
                    VerifyLandlordVariant.FastTrack -> VerifyLandlordSampleData.fastTrack
                }
            _state.update { it.copy(startContent = next) }
        }

        fun acknowledgeEvent() {
            pendingEvent.value = null
        }

        // MARK: - Submit

        @Suppress("ReturnCount")
        private suspend fun submit() {
            val snapshot = _state.value
            if (snapshot.isSubmitting) return
            val live = snapshot.form.validate()
            _state.update { it.copy(errors = live) }
            if (!live.isEmpty) {
                val noun = if (live.count == 1) "thing" else "things"
                _state.update {
                    it.copy(
                        submitState = VerifyLandlordSubmitState.Error("Fix ${live.count} $noun to submit"),
                    )
                }
                return
            }
            _state.update { it.copy(submitState = VerifyLandlordSubmitState.Submitting) }
            if (!networkMonitor.isOnline.value) {
                _state.update {
                    it.copy(
                        submitState =
                            VerifyLandlordSubmitState.Error(
                                "You're offline. Try again when you're back online.",
                            ),
                    )
                }
                return
            }
            // The landlord / property-manager details collected above have
            // no backend representation today — `request-postcard` takes no
            // body — so finishing the wizard simply mails the verification
            // postcard and routes to code entry. The details stay client-side.
            if (submitDelayMillis > 0) delay(submitDelayMillis)
            when (val result = verificationRepository.requestPostcard(homeId)) {
                is NetworkResult.Success -> {
                    _state.update { it.copy(submitState = VerifyLandlordSubmitState.Submitted) }
                    pendingEvent.value = VerifyLandlordOutboundEvent.OpenPostcardVerification(homeId)
                }
                is NetworkResult.Failure -> {
                    // A pending/duplicate code (400) or address cap (429)
                    // means a postcard is already on its way — proceed to
                    // enter it. Other failures surface inline so the user
                    // can retry.
                    if (result.error.code == 400 || result.error.code == 429) {
                        _state.update { it.copy(submitState = VerifyLandlordSubmitState.Submitted) }
                        pendingEvent.value = VerifyLandlordOutboundEvent.OpenPostcardVerification(homeId)
                    } else {
                        _state.update {
                            it.copy(
                                submitState =
                                    VerifyLandlordSubmitState.Error(
                                        result.error.message.ifEmpty {
                                            "Couldn't request the verification postcard. Try again."
                                        },
                                    ),
                            )
                        }
                    }
                }
            }
        }

        // MARK: - Chrome derivation

        private fun computeChrome(state: VerifyLandlordUiState): WizardChrome =
            when (state.currentStep) {
                VerifyLandlordStep.Start ->
                    WizardChrome(
                        title = "Verify landlord",
                        progressLabel = WizardProgressLabel.StepOf(1, TOTAL_STEPS),
                        progressFraction = 1f / TOTAL_STEPS,
                        leading = WizardLeadingControl.Close,
                        primaryCtaLabel = "Start verification",
                        primaryCtaEnabled = true,
                        secondaryCta = null,
                        isSubmitting = false,
                        dirty = state.isDirty,
                        showsProgressBar = true,
                    )
                VerifyLandlordStep.Details -> {
                    val live = state.form.validate()
                    val blocked = (state.errors != null && !live.isEmpty) || state.isSubmitting
                    WizardChrome(
                        title = "Verify landlord",
                        progressLabel = WizardProgressLabel.StepOf(2, TOTAL_STEPS),
                        progressFraction = 2f / TOTAL_STEPS,
                        leading = WizardLeadingControl.Back,
                        primaryCtaLabel = "Submit",
                        primaryCtaEnabled = !blocked,
                        secondaryCta = null,
                        isSubmitting = state.isSubmitting,
                        dirty = state.isDirty,
                        showsProgressBar = true,
                    )
                }
            }

        // MARK: - Helpers

        private inline fun updateForm(
            revalidate: Boolean = true,
            crossinline transform: (VerifyLandlordForm) -> VerifyLandlordForm,
        ) {
            _state.update { current ->
                val nextForm = transform(current.form)
                val nextErrors =
                    if (revalidate && current.errors != null) {
                        nextForm.validate()
                    } else {
                        current.errors
                    }
                current.copy(form = nextForm, errors = nextErrors)
            }
        }

        companion object {
            /** Total steps surfaced to the user — the third is A12.7
             *  (the sibling postcard verification screen). */
            const val TOTAL_STEPS: Int = 3
            const val SUBMIT_DELAY_DEFAULT_MILLIS: Long = 800L
        }
    }
