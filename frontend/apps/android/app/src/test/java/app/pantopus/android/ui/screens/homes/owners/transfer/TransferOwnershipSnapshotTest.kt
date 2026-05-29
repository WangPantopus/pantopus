@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.owners.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.homes.owners.transfer.components.BiometricConfirmSheet
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * A13.4 Paparazzi baselines for the Transfer Ownership form.
 *
 * Locks the two design frames:
 *  - ready: Maya selected, 25% slider, before/after diff, TRANSFER typed,
 *    CTA armed.
 *  - confirm_sheet: Face ID / biometric bottom sheet over the form's
 *    diff card (rendered standalone so the scrim doesn't dominate the
 *    baseline diff).
 */
class TransferOwnershipSnapshotTest {
    @get:Rule
    val paparazzi: Paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2400,
                    softButtons = false,
                ),
        )

    @Test
    fun transfer_ownership_ready() {
        paparazzi.snapshot {
            Frame {
                TransferOwnershipLoaded(
                    state = readyState(),
                    onBack = {},
                    onAmountChange = {},
                    onPresetSelected = {},
                    onConfirmationChange = {},
                    onArmCta = {},
                )
            }
        }
    }

    @Test
    fun transfer_ownership_confirm_sheet() {
        paparazzi.snapshot {
            Frame {
                val state = readyState()
                BiometricConfirmSheet(
                    parties = state.confirmSheetParties,
                    amount = state.amount,
                    recipientName = state.recipient.name,
                    homeAddress = state.homeContext.address,
                    coOwnerNames = state.homeContext.coOwnerNames,
                    timestamp = state.confirmationTimestamp,
                    biometryLabel = state.biometryLabel,
                    isAuthenticating = false,
                    onCancel = {},
                    onConfirm = {},
                )
            }
        }
    }

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) { content() }
        }
    }

    private fun readyState(): TransferOwnershipUiState =
        TransferOwnershipUiState(
            homeContext = TransferOwnershipSampleData.homeContext("preview"),
            recipient = TransferOwnershipSampleData.mayaFortune,
            currentUser = TransferOwnershipSampleData.currentUser,
            coOwners = TransferOwnershipSampleData.coOwners,
            amount = TransferOwnershipSampleData.DEFAULT_AMOUNT,
            confirmationField =
                FormFieldState(
                    id = "confirmation",
                    value = TransferOwnershipSampleData.CONFIRMATION_PHRASE,
                    touched = true,
                ),
            biometryLabel = "Face ID",
        )
}
