@file:Suppress("PackageNaming", "MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.homes.owners.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pantopus.android.data.api.models.homes.TransferOwnerRequest
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomeOwnersRepository
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.screens.homes.owners.transfer.components.ConfirmSheetParty
import app.pantopus.android.ui.screens.homes.owners.transfer.components.SplitSegment
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Nav-arg key for the home id consumed via [SavedStateHandle]. */
const val TRANSFER_HOME_ID_KEY = "homeId"

/** Tone + text payload the form turns into a transient toast. */
data class TransferToast(
    val text: String,
    val isError: Boolean,
)

/** Visibility states for the bottom confirmation sheet. */
enum class ConfirmSheetPhase {
    Hidden,
    Visible,
    Authenticating,
    Dismissing,
}

/**
 * Aggregate UI state for the Transfer Ownership form. Mirrors the iOS
 * `TransferOwnershipViewModel` projection so snapshot / VM tests align.
 */
data class TransferOwnershipUiState(
    val homeContext: TransferOwnershipSampleData.HomeContext,
    val recipient: TransferOwnershipSampleData.RecipientSeed,
    val currentUser: TransferOwnershipSampleData.OwnerSeed,
    val coOwners: List<TransferOwnershipSampleData.OwnerSeed>,
    val recipientIsBackendBacked: Boolean = false,
    val amount: Int = TransferOwnershipSampleData.DEFAULT_AMOUNT,
    val confirmationField: FormFieldState = FormFieldState(id = "confirmation"),
    val sheetPhase: ConfirmSheetPhase = ConfirmSheetPhase.Hidden,
    val biometricErrorMessage: String? = null,
    val toast: TransferToast? = null,
    val shouldDismiss: Boolean = false,
    val biometryLabel: String = "Fingerprint",
) {
    val maxAmount: Int get() = currentUser.percent
    val presets: List<Int> get() = TransferOwnershipSampleData.presets
    val sliderRange: IntRange get() = TransferOwnershipSampleData.sliderRange
    val confirmationPhrase: String get() = TransferOwnershipSampleData.CONFIRMATION_PHRASE

    val confirmationMatches: Boolean
        get() = confirmationField.value == confirmationPhrase

    val isReadyToCommit: Boolean
        get() = amount in 1..maxAmount && confirmationMatches && recipientIsBackendBacked

    val isDirty: Boolean
        get() = amount != TransferOwnershipSampleData.DEFAULT_AMOUNT || confirmationField.value.isNotEmpty()

    val confirmationFieldState: PantopusFieldState
        get() =
            when {
                confirmationField.value.isEmpty() -> PantopusFieldState.Default
                confirmationMatches -> PantopusFieldState.Valid
                else -> PantopusFieldState.Default
            }

    val ctaLabel: String
        get() {
            if (!recipientIsBackendBacked) return "Transfer ownership unavailable"
            val firstName = recipient.name.split(" ").firstOrNull() ?: recipient.name
            return "Transfer $amount% to $firstName"
        }

    val setupBlockMessage: String?
        get() =
            if (recipientIsBackendBacked) {
                null
            } else {
                "Recipient selection is not connected to live owner data yet."
            }

    val warningCopy: String
        get() {
            val firstName = recipient.name.split(" ").firstOrNull() ?: recipient.name
            val others = coOwners.joinToString(separator = " and ") { it.displayName }
            return "$others will be notified after this transfer. You cannot reclaim the $amount% " +
                "without $firstName's signed transfer back."
        }

    val confirmationTimestamp: String
        get() = "14:23 May 26"

    val beforeSegments: List<SplitSegment>
        get() {
            val you =
                SplitSegment(
                    id = currentUser.id,
                    owner = currentUser.displayName,
                    percent = currentUser.percent,
                    color = currentUser.palette.color,
                )
            val others =
                coOwners.map { owner ->
                    SplitSegment(
                        id = owner.id,
                        owner = owner.displayName,
                        percent = owner.percent,
                        color = owner.palette.color,
                    )
                }
            return listOf(you) + others
        }

    val afterSegments: List<SplitSegment>
        get() {
            val recipientFirstName = recipient.name.split(" ").firstOrNull() ?: recipient.name
            val you =
                SplitSegment(
                    id = currentUser.id,
                    owner = currentUser.displayName,
                    percent = (currentUser.percent - amount).coerceAtLeast(0),
                    color = currentUser.palette.color,
                    delta = -amount,
                )
            val newcomer =
                SplitSegment(
                    id = recipient.id,
                    owner = recipientFirstName,
                    percent = amount,
                    color = TransferOwnershipSampleData.recipientPaletteStart,
                    delta = amount,
                    isNew = true,
                )
            val others =
                coOwners.map { owner ->
                    SplitSegment(
                        id = owner.id,
                        owner = owner.displayName,
                        percent = owner.percent,
                        color = owner.palette.color,
                    )
                }
            return listOf(you, newcomer) + others
        }

    val confirmSheetParties: List<ConfirmSheetParty>
        get() =
            listOf(
                ConfirmSheetParty(
                    id = currentUser.id,
                    role = "From",
                    name = "You · ${TransferOwnershipSampleData.SENDER_FULL_NAME}",
                    initials = currentUser.initials,
                    avatarStart = currentUser.palette.gradientStart,
                    avatarEnd = currentUser.palette.gradientEnd,
                    fromPercent = currentUser.percent,
                    toPercent = (currentUser.percent - amount).coerceAtLeast(0),
                ),
                ConfirmSheetParty(
                    id = recipient.id,
                    role = "To",
                    name = recipient.name,
                    initials = recipient.initials,
                    avatarStart = TransferOwnershipSampleData.recipientPaletteStart,
                    avatarEnd = TransferOwnershipSampleData.recipientPaletteEnd,
                    fromPercent = 0,
                    toPercent = amount,
                    verified = recipient.verified,
                ),
            )
}

/**
 * A13.4 — Transfer Ownership form view-model. After biometric auth
 * succeeds, [handleBiometricResult] calls
 * `POST /api/homes/:id/owners/transfer` (route
 * `backend/routes/homeOwnership.js:1526`) via [HomeOwnersRepository],
 * raises a success toast (including the co-owner-approval quorum
 * branch), and signals [shouldDismiss].
 *
 * Biometric authentication itself is owned by the host screen — the VM
 * exposes [requestBiometric] / [handleBiometricResult] entry points so
 * the platform [`androidx.biometric.BiometricPrompt`] lives at the
 * Activity layer where it has the FragmentManager it needs.
 */
@HiltViewModel
class TransferOwnershipViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val ownersRepo: HomeOwnersRepository,
    ) : ViewModel() {
        private val homeId: String = savedStateHandle.get<String>(TRANSFER_HOME_ID_KEY) ?: ""

        private val _state =
            MutableStateFlow(
                TransferOwnershipUiState(
                    homeContext = TransferOwnershipSampleData.homeContext(homeId),
                    recipient = TransferOwnershipSampleData.mayaFortune,
                    currentUser = TransferOwnershipSampleData.currentUser,
                    coOwners = TransferOwnershipSampleData.coOwners,
                ),
            )
        val state: StateFlow<TransferOwnershipUiState> = _state.asStateFlow()

        internal constructor(
            savedStateHandle: SavedStateHandle,
            ownersRepo: HomeOwnersRepository,
            recipientIsBackendBacked: Boolean,
        ) : this(savedStateHandle, ownersRepo) {
            _state.update { it.copy(recipientIsBackendBacked = recipientIsBackendBacked) }
        }

        fun updateAmount(raw: Int) {
            _state.update { current ->
                val clamped = raw.coerceIn(current.sliderRange.first, current.maxAmount)
                current.copy(amount = clamped)
            }
        }

        fun selectPreset(preset: Int) {
            updateAmount(preset)
        }

        fun updateConfirmation(value: String) {
            _state.update { current ->
                current.copy(confirmationField = current.confirmationField.copy(value = value, touched = true))
            }
        }

        fun presentConfirmSheet() {
            val current = _state.value
            val setupBlockMessage = current.setupBlockMessage
            if (setupBlockMessage != null) {
                _state.update {
                    it.copy(
                        toast = TransferToast(text = setupBlockMessage, isError = true),
                    )
                }
                return
            }
            if (!current.isReadyToCommit) return
            _state.update { it.copy(sheetPhase = ConfirmSheetPhase.Visible, biometricErrorMessage = null) }
        }

        fun dismissConfirmSheet() {
            val current = _state.value
            if (current.sheetPhase == ConfirmSheetPhase.Authenticating) return
            _state.update { it.copy(sheetPhase = ConfirmSheetPhase.Hidden, biometricErrorMessage = null) }
        }

        /**
         * Marks authentication as in-flight. The host calls this just
         * before invoking the platform BiometricPrompt.
         */
        fun requestBiometric() {
            val current = _state.value
            if (current.sheetPhase != ConfirmSheetPhase.Visible || !current.isReadyToCommit) return
            _state.update { it.copy(sheetPhase = ConfirmSheetPhase.Authenticating, biometricErrorMessage = null) }
        }

        /**
         * Drive the post-auth state machine. On success runs the stub
         * transfer; on failure surfaces an inline error and returns the
         * sheet to the Visible phase so the user can retry.
         */
        fun handleBiometricResult(
            success: Boolean,
            errorMessage: String? = null,
        ) {
            if (!success) {
                _state.update {
                    it.copy(
                        sheetPhase = ConfirmSheetPhase.Visible,
                        biometricErrorMessage = errorMessage ?: "Authentication failed. Try again.",
                    )
                }
                return
            }
            viewModelScope.launch {
                val current = _state.value
                val setupBlockMessage = current.setupBlockMessage
                if (setupBlockMessage != null) {
                    _state.update {
                        it.copy(
                            sheetPhase = ConfirmSheetPhase.Visible,
                            biometricErrorMessage = setupBlockMessage,
                        )
                    }
                    return@launch
                }
                // Identify the buyer by their account id; effective_date
                // is omitted so the transfer takes effect immediately.
                val result =
                    ownersRepo.transfer(
                        homeId,
                        TransferOwnerRequest(buyerUserId = current.recipient.id),
                    )
                when (result) {
                    is NetworkResult.Success -> {
                        val successText =
                            if (result.data.requiresQuorum) {
                                "Transfer sent for co-owner approval."
                            } else {
                                "Transferred ${current.amount}% to ${current.recipient.name}"
                            }
                        _state.update {
                            it.copy(
                                sheetPhase = ConfirmSheetPhase.Dismissing,
                                toast = TransferToast(text = successText, isError = false),
                                shouldDismiss = true,
                            )
                        }
                    }
                    is NetworkResult.Failure -> {
                        _state.update {
                            it.copy(
                                sheetPhase = ConfirmSheetPhase.Visible,
                                biometricErrorMessage =
                                    result.error.message.ifEmpty {
                                        "Couldn't complete the transfer. Try again."
                                    },
                            )
                        }
                    }
                }
            }
        }

        fun setBiometryLabel(label: String) {
            _state.update { it.copy(biometryLabel = label) }
        }

        fun dismissToast() {
            _state.update { it.copy(toast = null) }
        }

        fun acknowledgeDismiss() {
            _state.update { it.copy(shouldDismiss = false) }
        }
    }
