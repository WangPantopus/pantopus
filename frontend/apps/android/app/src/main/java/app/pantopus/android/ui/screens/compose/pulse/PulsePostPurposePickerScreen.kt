@file:Suppress("LongMethod")

package app.pantopus.android.ui.screens.compose.pulse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.screens.shared.form.FormShellLeading
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

@Composable
fun PulsePostPurposePickerScreen(
    target: PulsePostingTarget,
    onSelect: (PulseComposePurpose) -> Unit,
    onBack: () -> Unit,
) {
    val purposes = PulseComposePurpose.allowedFor(target)

    FormShell(
        title = "New Post",
        leading = FormShellLeading.Back,
        rightActionLabel = null,
        isValid = false,
        isDirty = false,
        isSaving = false,
        onClose = onBack,
        onCommit = {},
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .testTag("pulsePostPurposePicker"),
        ) {
            Text(
                text = "What is this post for?",
                style = PantopusTextStyle.body.copy(fontWeight = FontWeight.Bold),
                color = PantopusColors.appText,
                modifier = Modifier.padding(Spacing.s4),
            )
            Column(
                modifier = Modifier.padding(horizontal = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                purposes.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        row.forEach { purpose ->
                            PurposeChip(
                                purpose = purpose,
                                modifier = Modifier.weight(1f),
                                onClick = { onSelect(purpose) },
                            )
                        }
                        if (row.size == 1) {
                            androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PurposeChip(
    purpose: PulseComposePurpose,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val (accent, background) = purposeColors(purpose)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(background)
                .border(1.dp, accent.copy(alpha = 0.25f), RoundedCornerShape(Radii.lg))
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag("pulsePurpose-${purpose.key}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = purposeIcon(purpose), contentDescription = null, size = 18.dp, tint = accent)
        Text(
            text = purpose.label,
            style = PantopusTextStyle.small.copy(fontWeight = FontWeight.SemiBold),
            color = accent,
        )
    }
}

private fun purposeIcon(purpose: PulseComposePurpose): PantopusIcon =
    when (purpose) {
        PulseComposePurpose.Ask -> PantopusIcon.HelpCircle
        PulseComposePurpose.HeadsUp -> PantopusIcon.Megaphone
        PulseComposePurpose.Recommend -> PantopusIcon.Star
        PulseComposePurpose.LostFound -> PantopusIcon.Search
        PulseComposePurpose.LocalUpdate -> PantopusIcon.FileText
        PulseComposePurpose.NeighborhoodWin -> PantopusIcon.Crown
        PulseComposePurpose.VisitorGuide -> PantopusIcon.Compass
        PulseComposePurpose.Event -> PantopusIcon.Calendar
        PulseComposePurpose.Deal -> PantopusIcon.Tag
    }

private fun purposeColors(purpose: PulseComposePurpose): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> =
    when (purpose) {
        PulseComposePurpose.Ask -> PantopusColors.primary600 to PantopusColors.primary50
        PulseComposePurpose.HeadsUp -> PantopusColors.error to PantopusColors.errorBg
        PulseComposePurpose.Recommend -> PantopusColors.warmAmber to PantopusColors.warmAmberBg
        PulseComposePurpose.LostFound -> PantopusColors.warning to PantopusColors.warningBg
        PulseComposePurpose.LocalUpdate -> PantopusColors.primary700 to PantopusColors.primary50
        PulseComposePurpose.NeighborhoodWin -> PantopusColors.success to PantopusColors.successBg
        PulseComposePurpose.VisitorGuide -> PantopusColors.home to PantopusColors.homeBg
        PulseComposePurpose.Event -> PantopusColors.primary600 to PantopusColors.primary50
        PulseComposePurpose.Deal -> PantopusColors.success to PantopusColors.successBg
    }
