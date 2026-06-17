@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "LongParameterList")
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.pantopus.android.ui.screens.scheduling.bookings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val CANCEL_REFUND_SHEET_TAG = "cancelRefundSheet"

/**
 * E5 Cancel & Refund sheet. Reason chips (free-text "Other") + a note + a
 * notify switch; when the booking is paid (and the paid flag is on) a refund
 * card explains the policy-driven refund the server issues. 409 `PAST_DEADLINE`
 * renders inline; `REFUND_FAILED` flips the CTA to "Retry refund".
 */
@Composable
fun CancelRefundSheet(
    state: CancelSheetUiState,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onSelectReason: (String) -> Unit,
    onSetOther: (String) -> Unit,
    onSetNote: (String) -> Unit,
    onToggleNotify: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
        modifier = modifier.testTag(CANCEL_REFUND_SHEET_TAG),
    ) {
        Column(
            modifier =
                Modifier.fillMaxWidth().padding(
                    horizontal = Spacing.s4,
                    vertical = Spacing.s2,
                ),
        ) {
            Text(
                text = "Cancel this booking?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
            Text(
                text = state.summary,
                fontSize = 12.sp,
                color = PantopusColors.appTextMuted,
                modifier = Modifier.padding(top = Spacing.s1, bottom = Spacing.s4),
            )

            CancelReasonChips(state = state, onSelect = onSelectReason, onSetOther = onSetOther)
            Spacer(Modifier.height(Spacing.s3))
            NoteField(
                value = state.note,
                placeholder = "Note to the other party (optional)",
                onChange = onSetNote,
            )

            if (state.showRefund) {
                Spacer(Modifier.height(Spacing.s3))
                RefundCard(copy = state.refundCopy)
            }

            Spacer(Modifier.height(Spacing.s3))
            NotifySwitch(notify = state.notify, onToggle = onToggleNotify)

            state.errorMessage?.let {
                Spacer(Modifier.height(Spacing.s3))
                CancelInlineError(it)
            }

            Spacer(Modifier.height(Spacing.s4))
            val label =
                if (state.refundFailed) {
                    "Retry refund"
                } else if (state.showRefund) {
                    "Cancel & refund"
                } else {
                    "Cancel booking"
                }
            val icon = if (state.refundFailed) PantopusIcon.RefreshCw else PantopusIcon.XCircle
            DangerFilledButton(
                label = label,
                leadingIcon = icon,
                loading = state.submitting,
                onClick = onConfirm,
                modifier = Modifier.testTag("cancelConfirm"),
            )
            Spacer(Modifier.height(Spacing.s4))
        }
    }
}

@Composable
private fun CancelReasonChips(
    state: CancelSheetUiState,
    onSelect: (String) -> Unit,
    onSetOther: (String) -> Unit,
) {
    Column {
        Text(
            text = "REASON",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(bottom = Spacing.s2),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            state.reasons.forEach { reason ->
                val on = reason == state.selectedReason
                Box(
                    modifier =
                        Modifier
                            .heightIn(min = 34.dp)
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(
                                if (on) PantopusColors.errorBg else PantopusColors.appSurface,
                            )
                            .then(
                                if (on) {
                                    Modifier
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        PantopusColors.appBorder,
                                        RoundedCornerShape(Radii.pill),
                                    )
                                },
                            )
                            .clickable { onSelect(reason) }
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = reason,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (on) PantopusColors.error else PantopusColors.appTextStrong,
                    )
                }
            }
        }
        if (state.selectedReason == "Other") {
            Spacer(Modifier.height(Spacing.s2))
            NoteField(
                value = state.otherText,
                placeholder = "Tell us what happened",
                onChange = onSetOther,
            )
        }
    }
}

@Composable
private fun RefundCard(copy: String) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Receipt,
                contentDescription = null,
                size = 13.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text(
                text = "REFUND",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextSecondary,
            )
        }
        Text(
            text = copy,
            fontSize = 11.5.sp,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(top = Spacing.s2),
        )
    }
}

@Composable
private fun NotifySwitch(
    notify: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable(onClick = onToggle)
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Bell,
            contentDescription = null,
            size = 17.dp,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = "Notify invitee",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .size(width = 42.dp, height = 25.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(
                        if (notify) PantopusColors.primary600 else PantopusColors.appBorderStrong,
                    ),
            contentAlignment = if (notify) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier.padding(
                        horizontal = 2.5.dp,
                    ).size(
                        20.dp,
                    ).clip(
                        androidx.compose.foundation.shape.CircleShape,
                    ).background(PantopusColors.appSurface),
            )
        }
    }
}

@Composable
private fun CancelInlineError(message: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.errorBg)
                .border(1.dp, PantopusColors.errorLight, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 16.dp,
            tint = PantopusColors.error,
        )
        Text(
            text = message,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.error,
        )
    }
}
