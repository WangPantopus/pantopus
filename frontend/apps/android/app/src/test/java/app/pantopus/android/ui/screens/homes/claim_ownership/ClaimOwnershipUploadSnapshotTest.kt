@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.homes.claim_ownership

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.ui.screens.homes.claim_ownership.components.UploadSlotFile
import app.pantopus.android.ui.screens.homes.claim_ownership.components.UploadSlotState
import app.pantopus.android.ui.screens.shared.wizard.WizardChrome
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardModel
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Rule
import org.junit.Test

/**
 * A12.4 Paparazzi snapshots for Claim Ownership · Evidence (step 2):
 * the ready-to-submit frame (both docs done · address matches) and the
 * mid-upload frame (one warn · one uploading · "Waiting for upload to
 * finish" footer hint).
 */
class ClaimOwnershipUploadSnapshotTest {
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
    fun claim_ownership_upload_ready_to_submit() {
        paparazzi.snapshot {
            Frame(chrome = chrome(primaryEnabled = true, footerHint = null)) {
                UploadStepContent(
                    homeLabel = HOME_LABEL,
                    slots = readyToSubmitSlots(),
                    note = READY_STATEMENT,
                    onNoteChange = {},
                    submitError = null,
                    onPick = {},
                    onRemove = {},
                )
            }
        }
    }

    @Test
    fun claim_ownership_upload_mid_upload() {
        paparazzi.snapshot {
            Frame(chrome = chrome(primaryEnabled = false, footerHint = "Waiting for upload to finish")) {
                UploadStepContent(
                    homeLabel = HOME_LABEL,
                    slots = midUploadSlots(),
                    note = "I purchased 412 Elm St in",
                    onNoteChange = {},
                    submitError = null,
                    onPick = {},
                    onRemove = {},
                )
            }
        }
    }

    @Composable
    private fun Frame(
        chrome: WizardChrome,
        content: @Composable () -> Unit,
    ) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) {
                WizardShell(model = SnapshotWizardModel(chrome), content = content)
            }
        }
    }

    private companion object {
        const val HOME_LABEL = "412 Elm St"
        const val MATCH_DETAIL = "\"412 Elm St\" matches the address on your account."
        const val DIFFER_DETAIL =
            "We couldn't confirm 412 Elm St on this document. You can still submit — the reviewer will resolve it."
        const val READY_STATEMENT =
            "I purchased the property at 412 Elm St in March 2022 and have lived here as the sole owner " +
                "since closing. The deed is in my name; the tax statement reflects the same address."

        fun readyToSubmitSlots(): List<ClaimUploadSlotModel> =
            listOf(
                ClaimUploadSlotModel(
                    id = "identity",
                    label = "Government ID",
                    required = true,
                    hint = "JPG, PNG, or PDF up to 10 MB",
                    state =
                        UploadSlotState.Done(
                            UploadSlotFile("drivers_license.jpg", "820 KB", null, UploadSlotFile.Kind.Image),
                            MATCH_DETAIL,
                        ),
                ),
                ClaimUploadSlotModel(
                    id = "ownership",
                    label = "Proof of ownership",
                    required = true,
                    hint = "JPG, PNG, or PDF up to 10 MB",
                    state =
                        UploadSlotState.Done(
                            UploadSlotFile("deed_412_elm.pdf", "1.4 MB", 8, UploadSlotFile.Kind.Pdf),
                            MATCH_DETAIL,
                        ),
                ),
            )

        fun midUploadSlots(): List<ClaimUploadSlotModel> =
            listOf(
                ClaimUploadSlotModel(
                    id = "identity",
                    label = "Government ID",
                    required = true,
                    hint = "JPG, PNG, or PDF up to 10 MB",
                    state =
                        UploadSlotState.Uploading(
                            UploadSlotFile("drivers_license.jpg", "1.1 MB", null, UploadSlotFile.Kind.Image),
                            0.62f,
                        ),
                ),
                ClaimUploadSlotModel(
                    id = "ownership",
                    label = "Proof of ownership",
                    required = true,
                    hint = "JPG, PNG, or PDF up to 10 MB",
                    state =
                        UploadSlotState.Warn(
                            UploadSlotFile("mortgage_statement.pdf", "2.1 MB", 4, UploadSlotFile.Kind.Pdf),
                            DIFFER_DETAIL,
                        ),
                ),
            )

        fun chrome(
            primaryEnabled: Boolean,
            footerHint: String?,
        ): WizardChrome =
            WizardChrome(
                title = "Claim ownership",
                progressLabel = WizardProgressLabel.StepOf(2, 3),
                progressFraction = 2f / 3f,
                leading = WizardLeadingControl.Back,
                primaryCtaLabel = "Submit claim",
                primaryCtaEnabled = primaryEnabled,
                isSubmitting = false,
                footerHint = footerHint,
                dirty = true,
                showsProgressBar = true,
            )
    }
}

private class SnapshotWizardModel(
    override val chrome: WizardChrome,
) : WizardModel {
    override fun onLeading() = Unit

    override fun onDiscard() = Unit

    override fun onPrimary() = Unit
}
