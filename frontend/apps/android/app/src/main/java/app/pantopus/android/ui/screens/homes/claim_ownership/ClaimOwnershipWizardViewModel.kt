@file:Suppress("PackageNaming", "TooManyFunctions", "LongMethod")

package app.pantopus.android.ui.screens.homes.claim_ownership

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.data.analytics.AnalyticsResult
import app.pantopus.android.data.api.models.homes.SubmitClaimRequest
import app.pantopus.android.data.api.models.homes.UploadEvidenceRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.ui.screens.shared.wizard.WizardChrome
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardModel
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import app.pantopus.android.ui.screens.shared.wizard.WizardSecondaryCta
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg key for the home being claimed. */
const val CLAIM_OWNERSHIP_HOME_ID_KEY: String = "homeId"

/**
 * Aggregate UI state for the Claim Ownership wizard. Combined so the
 * screen derives the [WizardChrome] from a single state read.
 */
data class ClaimOwnershipUiState(
    val currentStep: ClaimOwnershipStep = ClaimOwnershipStep.Start,
    val slots: Map<ClaimEvidenceSlot, ClaimSlotState> =
        ClaimEvidenceSlot.entries.associateWith { ClaimSlotState.Empty },
    val note: String = "",
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
) {
    val bothSlotsHaveFiles: Boolean
        get() = slots.values.all { it.hasFile }

    val anySlotHasFile: Boolean
        get() = slots.values.any { it.hasFile }
}

/**
 * Drives the 3-step claim-ownership wizard. Calls:
 *  1. `POST /api/homes/:id/ownership-claims` to create the claim
 *  2. For each evidence file:
 *      a. `POST /api/files/upload` (multipart) → URL
 *      b. `POST /api/homes/:id/ownership-claims/:claimId/evidence`
 *         with `storage_ref = <url>`
 *
 * Backend deviations flagged in the PR description:
 *  - `submitClaimSchema` does NOT accept a `note` field; the wizard's
 *    optional textarea is piped into evidence metadata on the first
 *    file.
 *  - The evidence endpoint takes JSON `storage_ref`, not multipart —
 *    we route bytes through `/api/files/upload` first.
 */
@HiltViewModel
open class ClaimOwnershipWizardViewModel
    @Inject
    constructor(
        private val repository: HomesRepository,
        private val networkMonitor: NetworkMonitor,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel(),
        WizardModel {
        private val homeId: String =
            requireNotNull(savedStateHandle[CLAIM_OWNERSHIP_HOME_ID_KEY]) {
                "ClaimOwnershipWizardViewModel requires a '$CLAIM_OWNERSHIP_HOME_ID_KEY' nav arg."
            }

        private val _state = MutableStateFlow(ClaimOwnershipUiState())
        val state: StateFlow<ClaimOwnershipUiState> = _state.asStateFlow()

        /** One-shot navigation events the screen reacts to. */
        val pendingEvent = MutableStateFlow<ClaimOwnershipOutboundEvent?>(null)

        /**
         * Server-side claim id once `POST /ownership-claims` succeeds.
         * Held across retry attempts so a partial-success → retry doesn't
         * create a duplicate claim row server-side.
         */
        private var pendingClaimId: String? = null

        /**
         * File URLs successfully pushed through `/api/files/upload` whose
         * evidence registration later failed. Held so retry can POST the
         * evidence call directly with the existing `storage_ref` instead
         * of re-uploading the bytes (which would orphan the prior file).
         */
        private val pendingUploadUrls: MutableMap<ClaimEvidenceSlot, String> = mutableMapOf()

        // MARK: - WizardModel

        override val chrome: WizardChrome
            get() = computeChrome(_state.value)

        override fun onLeading() {
            when (_state.value.currentStep) {
                ClaimOwnershipStep.Start -> pendingEvent.value = ClaimOwnershipOutboundEvent.Dismiss
                ClaimOwnershipStep.Upload -> transitionTo(ClaimOwnershipStep.Start)
                ClaimOwnershipStep.Success -> pendingEvent.value = ClaimOwnershipOutboundEvent.Dismiss
            }
        }

        override fun onDiscard() {
            pendingEvent.value = ClaimOwnershipOutboundEvent.Dismiss
        }

        override fun onPrimary() {
            when (_state.value.currentStep) {
                ClaimOwnershipStep.Start -> transitionTo(ClaimOwnershipStep.Upload)
                ClaimOwnershipStep.Upload -> viewModelScope.launch { submit() }
                ClaimOwnershipStep.Success ->
                    pendingEvent.value = ClaimOwnershipOutboundEvent.OpenClaimsList
            }
        }

        override fun onSecondary() {
            if (_state.value.currentStep == ClaimOwnershipStep.Success) {
                pendingEvent.value = ClaimOwnershipOutboundEvent.Dismiss
            }
        }

        // MARK: - Slot management

        fun picked(slot: ClaimEvidenceSlot, file: ClaimPickedFile) {
            // Picking a new file invalidates any prior URL we'd cached
            // for this slot — the next submit must re-upload these bytes.
            pendingUploadUrls.remove(slot)
            _state.update { current ->
                current.copy(
                    slots = current.slots.toMutableMap().apply { put(slot, ClaimSlotState.Picked(file)) },
                    submitError = null,
                )
            }
        }

        fun remove(slot: ClaimEvidenceSlot) {
            pendingUploadUrls.remove(slot)
            _state.update { current ->
                current.copy(slots = current.slots.toMutableMap().apply { put(slot, ClaimSlotState.Empty) })
            }
        }

        fun setNote(value: String) {
            _state.update { it.copy(note = value) }
        }

        fun acknowledgeEvent() {
            pendingEvent.value = null
        }

        // MARK: - Submit

        @Suppress("ReturnCount")
        private suspend fun submit() {
            val current = _state.value
            if (!current.bothSlotsHaveFiles || current.isSubmitting) return
            if (!networkMonitor.isOnline.value) {
                _state.update {
                    it.copy(
                        submitError = "You're offline. Try again when you're back online.",
                    )
                }
                return
            }
            _state.update { it.copy(isSubmitting = true, submitError = null) }

            // Step 1: create the claim — but only once across retry
            // attempts. Holding the id in `pendingClaimId` keeps a
            // partial-success retry from creating a duplicate row.
            val claimId = pendingClaimId ?: run {
                val claimResult =
                    repository.submitClaim(homeId, SubmitClaimRequest(method = "doc_upload"))
                val envelope =
                    when (claimResult) {
                        is NetworkResult.Success -> claimResult.data.claim
                        is NetworkResult.Failure -> {
                            Analytics.track(AnalyticsEvent.CtaClaimOwnershipSubmit(AnalyticsResult.ERROR))
                            _state.update {
                                it.copy(isSubmitting = false, submitError = "Couldn't submit. Retry.")
                            }
                            return
                        }
                    }
                val resolvedId =
                    envelope.id ?: run {
                        Analytics.track(AnalyticsEvent.CtaClaimOwnershipSubmit(AnalyticsResult.ERROR))
                        _state.update {
                            it.copy(
                                isSubmitting = false,
                                submitError = "We're already working on a claim for this home.",
                            )
                        }
                        return
                    }
                pendingClaimId = resolvedId
                resolvedId
            }

            // Step 2: upload each slot's bytes, then register the URL as
            // evidence. Skip slots already fully uploaded and reuse any
            // cached `storage_ref` from a prior partial-success run so a
            // retry doesn't re-upload bytes (which would orphan the
            // earlier file server-side).
            for ((index, slot) in ClaimEvidenceSlot.entries.withIndex()) {
                if (current.slots[slot] is ClaimSlotState.Uploaded) continue
                val file = current.slots[slot]?.pickedFile ?: continue
                val cachedUrl = pendingUploadUrls[slot]
                val fileUrl =
                    if (cachedUrl != null) {
                        cachedUrl
                    } else {
                        markSlot(slot, ClaimSlotState.Uploading(file, 0.4f))
                        val uploadResult =
                            repository.uploadFile(file.filename, file.mimeType, file.bytes)
                        when (uploadResult) {
                            is NetworkResult.Success -> {
                                val url = uploadResult.data.file.url
                                pendingUploadUrls[slot] = url
                                url
                            }
                            is NetworkResult.Failure -> {
                                markSlot(slot, ClaimSlotState.Failed(file, "Upload failed"))
                                failSubmit()
                                return
                            }
                        }
                    }
                markSlot(slot, ClaimSlotState.Uploading(file, 0.8f))
                val metadata: Map<String, String>? =
                    if (index == 0 && current.note.trim().isNotEmpty()) {
                        mapOf("note" to current.note.trim())
                    } else {
                        null
                    }
                val evidenceResult =
                    repository.uploadEvidence(
                        homeId = homeId,
                        claimId = claimId,
                        request =
                            UploadEvidenceRequest(
                                evidenceType = slot.backendType,
                                storageRef = fileUrl,
                                metadata = metadata,
                            ),
                    )
                when (evidenceResult) {
                    is NetworkResult.Success -> {
                        markSlot(slot, ClaimSlotState.Uploaded(file, fileUrl))
                        pendingUploadUrls.remove(slot)
                    }
                    is NetworkResult.Failure -> {
                        markSlot(slot, ClaimSlotState.Failed(file, "Couldn't register evidence"))
                        failSubmit()
                        return
                    }
                }
            }

            Analytics.track(AnalyticsEvent.CtaClaimOwnershipSubmit(AnalyticsResult.SUCCESS))
            _state.update { it.copy(isSubmitting = false) }
            transitionTo(ClaimOwnershipStep.Success)
        }

        private fun failSubmit() {
            _state.update {
                it.copy(
                    isSubmitting = false,
                    submitError = "Couldn't submit. Retry.",
                )
            }
        }

        private fun markSlot(slot: ClaimEvidenceSlot, value: ClaimSlotState) {
            _state.update { current ->
                current.copy(slots = current.slots.toMutableMap().apply { put(slot, value) })
            }
        }

        // MARK: - Step transitions

        private fun transitionTo(step: ClaimOwnershipStep) {
            _state.update { it.copy(currentStep = step) }
            Analytics.track(AnalyticsEvent.ScreenClaimOwnershipStepViewed(step.name))
        }

        // MARK: - Chrome derivation

        private fun computeChrome(state: ClaimOwnershipUiState): WizardChrome =
            when (state.currentStep) {
                ClaimOwnershipStep.Start ->
                    WizardChrome(
                        title = "Claim ownership",
                        progressLabel = WizardProgressLabel.StepOf(1, 3),
                        progressFraction = 1f / 3f,
                        leading = WizardLeadingControl.Close,
                        primaryCtaLabel = "Start claim",
                        primaryCtaEnabled = true,
                        secondaryCta = null,
                        isSubmitting = false,
                        // Once the user has touched Upload (picked a file or
                        // typed a note), Back→Start must still surface the
                        // discard-confirm so an X tap doesn't dump the
                        // in-memory bytes silently.
                        dirty = state.anySlotHasFile || state.note.isNotEmpty(),
                        showsProgressBar = true,
                    )
                ClaimOwnershipStep.Upload ->
                    WizardChrome(
                        title = "Claim ownership",
                        progressLabel = WizardProgressLabel.StepOf(2, 3),
                        progressFraction = 2f / 3f,
                        leading = WizardLeadingControl.Back,
                        primaryCtaLabel = "Submit claim",
                        primaryCtaEnabled = state.bothSlotsHaveFiles && !state.isSubmitting,
                        secondaryCta = null,
                        isSubmitting = state.isSubmitting,
                        dirty = state.anySlotHasFile || state.note.isNotEmpty(),
                        showsProgressBar = true,
                    )
                ClaimOwnershipStep.Success ->
                    WizardChrome(
                        title = "Claim ownership",
                        progressLabel = WizardProgressLabel.Hidden,
                        progressFraction = null,
                        leading = WizardLeadingControl.Close,
                        primaryCtaLabel = "View status",
                        primaryCtaEnabled = true,
                        secondaryCta = WizardSecondaryCta("Back to home", testTag = "claimOwnershipBackToHome"),
                        isSubmitting = false,
                        dirty = false,
                        showsProgressBar = false,
                    )
            }
    }
