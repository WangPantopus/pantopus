@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.verify_landlord.postcard

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.ui.screens.homes.verify_landlord.VerifyLandlordSubmitState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg key for the home whose postcard is being verified. */
const val POSTCARD_VERIFICATION_HOME_ID_KEY: String = "homeId"

/** Lifecycle state of the physical postcard. */
enum class PostcardDeliveryStage { Mailed, InTransit, Delivered }

/** Full payload describing the postcard verification surface. */
data class PostcardVerificationContent(
    val recipientName: String,
    val street: String,
    val cityZip: String,
    val trackingNumber: String,
    val mailedOn: String,
    val inTransitOn: String?,
    val deliveredOn: String?,
    val resendAvailableOn: String,
)

object PostcardVerificationSampleData {
    val deliveredContent: PostcardVerificationContent =
        PostcardVerificationContent(
            recipientName = "Mira Patel",
            street = "412 Elm St, Apt 3B",
            cityZip = "San Francisco, CA 94114",
            trackingNumber = "#9405 5036 …8421",
            mailedOn = "Oct 9",
            inTransitOn = "Oct 11",
            deliveredOn = "Oct 12",
            resendAvailableOn = "Oct 15",
        )

    val inTransitContent: PostcardVerificationContent =
        PostcardVerificationContent(
            recipientName = "Mira Patel",
            street = "412 Elm St, Apt 3B",
            cityZip = "San Francisco, CA 94114",
            trackingNumber = "#9405 5036 …8421",
            mailedOn = "Oct 9",
            inTransitOn = "Oct 11",
            deliveredOn = null,
            resendAvailableOn = "Oct 15",
        )

    fun content(stage: PostcardDeliveryStage): PostcardVerificationContent =
        if (stage == PostcardDeliveryStage.Delivered) deliveredContent else inTransitContent

    fun stage(homeId: String): PostcardDeliveryStage =
        if (homeId.contains("delivered", ignoreCase = true)) {
            PostcardDeliveryStage.Delivered
        } else {
            PostcardDeliveryStage.InTransit
        }
}

/** Outbound events the host nav stack acts on. */
sealed interface PostcardVerificationOutboundEvent {
    data object Dismiss : PostcardVerificationOutboundEvent

    /**
     * Verify pressed and the code matched — caller should pop the
     * screen and route to the verified-home success surface.
     */
    data class Verified(val homeId: String) : PostcardVerificationOutboundEvent
}

/** Aggregate UI state for A12.7. */
data class PostcardVerificationUiState(
    val stage: PostcardDeliveryStage,
    val content: PostcardVerificationContent,
    val codeInput: String = "",
    val submitState: VerifyLandlordSubmitState = VerifyLandlordSubmitState.Idle,
) {
    val isCodeInputUnlocked: Boolean get() = stage == PostcardDeliveryStage.Delivered

    val isSubmitting: Boolean get() = submitState is VerifyLandlordSubmitState.Submitting

    val primaryCtaEnabled: Boolean
        get() = isCodeInputUnlocked && codeInput.length == CODE_LENGTH && !isSubmitting

    val primaryCtaLabel: String
        get() = if (isSubmitting) "Verifying…" else "Verify code"

    companion object {
        /** Length of the postcard code printed by the issuer. */
        const val CODE_LENGTH: Int = 6
    }
}

/**
 * View model for A12.7. Holds the current delivery stage, the user's
 * 6-char code, and a `submitState` machine identical in shape to the
 * wizard's [VerifyLandlordSubmitState].
 */
@HiltViewModel
open class PostcardVerificationViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val homeId: String =
            requireNotNull(savedStateHandle[POSTCARD_VERIFICATION_HOME_ID_KEY]) {
                "PostcardVerificationViewModel requires a '$POSTCARD_VERIFICATION_HOME_ID_KEY' nav arg."
            }

        protected open val submitDelayMillis: Long = SUBMIT_DELAY_DEFAULT_MILLIS

        /** Code printed on the postcard. Replace with the per-claim
         *  secret when the postcard issuance endpoint ships. */
        protected open val expectedCode: String = DEFAULT_EXPECTED_CODE

        private val _state =
            MutableStateFlow(
                PostcardVerificationUiState(
                    stage = PostcardVerificationSampleData.stage(homeId),
                    content =
                        PostcardVerificationSampleData.content(
                            PostcardVerificationSampleData.stage(homeId),
                        ),
                ),
            )
        val state: StateFlow<PostcardVerificationUiState> = _state.asStateFlow()
        val pendingEvent = MutableStateFlow<PostcardVerificationOutboundEvent?>(null)

        // MARK: - Mutations

        fun updateCode(raw: String) {
            val sanitized = raw.uppercase().take(PostcardVerificationUiState.CODE_LENGTH)
            _state.update { it.copy(codeInput = sanitized) }
        }

        fun resendPostcard() {
            _state.update { it.copy(codeInput = "") }
        }

        /**
         * Used by debug / preview tooling and snapshot tests to flip
         * between the in-transit and delivered frames without waiting
         * on the simulated USPS clock.
         */
        fun setStage(next: PostcardDeliveryStage) {
            _state.update { current ->
                current.copy(
                    stage = next,
                    content = PostcardVerificationSampleData.content(next),
                    codeInput = if (next != PostcardDeliveryStage.Delivered) "" else current.codeInput,
                )
            }
        }

        fun verifyTapped() {
            val snapshot = _state.value
            if (!snapshot.primaryCtaEnabled) return
            viewModelScope.launch { verify() }
        }

        fun dismissTapped() {
            pendingEvent.value = PostcardVerificationOutboundEvent.Dismiss
        }

        fun acknowledgeEvent() {
            pendingEvent.value = null
        }

        // MARK: - Submit

        private suspend fun verify() {
            _state.update { it.copy(submitState = VerifyLandlordSubmitState.Submitting) }
            if (submitDelayMillis > 0) delay(submitDelayMillis)
            val snapshot = _state.value
            if (snapshot.codeInput == expectedCode) {
                _state.update { it.copy(submitState = VerifyLandlordSubmitState.Submitted) }
                pendingEvent.value = PostcardVerificationOutboundEvent.Verified(homeId)
            } else {
                _state.update {
                    it.copy(
                        submitState =
                            VerifyLandlordSubmitState.Error(
                                "That code didn't match. Double-check the postcard.",
                            ),
                        codeInput = "",
                    )
                }
            }
        }

        companion object {
            const val SUBMIT_DELAY_DEFAULT_MILLIS: Long = 800L
            const val DEFAULT_EXPECTED_CODE: String = "4Q2K7B"
        }
    }
