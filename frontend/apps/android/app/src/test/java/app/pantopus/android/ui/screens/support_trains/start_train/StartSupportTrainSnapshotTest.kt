@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.support_trains.start_train

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import app.pantopus.android.data.api.models.mail_compose.MailRecipientDto
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTheme
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

/**
 * P2.6 — Paparazzi snapshots for the three steps + success screen of
 * the Start-a-Support-Train wizard. Annotated `@Ignore` until baselines
 * land so the first PR doesn't fail CI on a missing image — a follow-up
 * records baselines via `./gradlew paparazziRecord` and removes the
 * annotation.
 */
@Ignore("Baselines recorded in follow-up — see commit body")
class StartSupportTrainSnapshotTest {
    @get:Rule
    val paparazzi =
        Paparazzi(
            deviceConfig =
                DeviceConfig.PIXEL_5.copy(
                    screenHeight = 2400,
                    softButtons = false,
                ),
        )

    @Test
    fun start_train_who_and_why_step() {
        paparazzi.snapshot {
            Frame {
                WhoAndWhyStep(
                    form =
                        StartSupportTrainFormState(
                            beneficiaryQuery = "Chen family",
                            reason = "Welcoming baby Theo — meals would be a huge help.",
                        ),
                    results = listOf(sampleRecipient()),
                    selected = sampleRecipient(),
                    isSearching = false,
                    onQuery = {},
                    onSelectBeneficiary = {},
                    onClearBeneficiary = {},
                    onReason = {},
                    reasonRemaining = 440,
                )
            }
        }
    }

    @Test
    fun start_train_what_and_when_step() {
        paparazzi.snapshot {
            Frame {
                WhatAndWhenStep(
                    form =
                        StartSupportTrainFormState(
                            step = StartSupportTrainStep.WhatAndWhen,
                            kind = SupportTrainKind.Meals,
                            slotDuration = StartSupportTrainSlotDuration.Sixty,
                        ),
                    onSelectKind = {},
                    onStartDate = {},
                    onEndDate = {},
                    onSelectDuration = {},
                )
            }
        }
    }

    @Test
    fun start_train_review_step() {
        val form =
            StartSupportTrainFormState(
                step = StartSupportTrainStep.ReviewAndLaunch,
                beneficiaryQuery = "Chen family",
                reason = "Welcoming baby Theo — meals would be a huge help.",
                kind = SupportTrainKind.Meals,
                slotDuration = StartSupportTrainSlotDuration.Sixty,
            )
        val slots =
            StartSupportTrainSlotGenerator.generate(
                startMillis = form.startDateMillis,
                endMillis = form.endDateMillis,
                durationMinutes = form.slotDuration.minutes,
                startHour = form.kind.defaultStartHour,
            )
        paparazzi.snapshot {
            Frame {
                ReviewStep(
                    form = form,
                    selectedBeneficiary = sampleRecipient(),
                    slots = slots,
                    onToggleComments = {},
                    onSelectVisibility = {},
                    launchError = null,
                )
            }
        }
    }

    @Test
    fun start_train_success_step() {
        paparazzi.snapshot {
            Frame {
                SuccessStep(
                    slotCount = 7,
                    visibility = StartSupportTrainVisibility.Neighbors,
                )
            }
        }
    }

    private fun sampleRecipient(): MailRecipientDto =
        MailRecipientDto(
            userId = "u_chen",
            name = "Maya Chen",
            username = "mayac",
            homeId = "home_demo",
            homeAddress = "412 Elm St, Portland, OR",
            isVerified = true,
            homeMediaUrl = null,
            isOnPantopus = true,
        )

    @Composable
    private fun Frame(content: @Composable () -> Unit) {
        PantopusTheme {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(PantopusColors.appBg),
            ) {
                Column(modifier = Modifier.padding(16.dp)) { content() }
            }
        }
    }
}
