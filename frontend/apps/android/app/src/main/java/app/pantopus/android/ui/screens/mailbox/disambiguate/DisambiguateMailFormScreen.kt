@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.mailbox.disambiguate

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.form.FormFieldGroup
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay

/**
 * Disambiguate-mail form. ViewModel reads the mail id (and optional
 * preview metadata) via [androidx.lifecycle.SavedStateHandle].
 */
@Composable
fun DisambiguateMailFormScreen(
    onClose: () -> Unit,
    viewModel: DisambiguateMailFormViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.toast) {
        if (state.toast != null) {
            delay(2_500)
            viewModel.dismissToast()
        }
    }
    LaunchedEffect(state.shouldDismiss) {
        if (state.shouldDismiss) {
            viewModel.acknowledgeDismiss()
            onClose()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                FormShell(
                    title = "Who is this for?",
                    rightActionLabel = "",
                    isValid = false, // top-bar action intentionally disabled — sticky CTA owns submit
                    isDirty = state.isDirty, // drives discard-confirm on close
                    isSaving = false,
                    onClose = onClose,
                    onCommit = {},
                ) {
                    EnvelopeCard(
                        ocrRecipient = state.ocrRecipient,
                        confidence = state.confidence,
                        envelopeUrl = state.envelopeUrl,
                        modifier = Modifier.padding(horizontal = Spacing.s4),
                    )
                    FormFieldGroup("Possible recipients") {
                        MailRecipientChoice.entries.forEach { choice ->
                            RecipientRow(
                                choice = choice,
                                isSelected = state.selected == choice,
                                onTap = { viewModel.select(choice) },
                            )
                        }
                    }
                    FormFieldGroup("Anything else?") {
                        AliasNotesField(
                            text = state.aliasNotes,
                            onChange = viewModel::setAliasNotes,
                            error = state.aliasError,
                        )
                    }
                    Spacer(Modifier.height(96.dp))
                }
            }
            StickyCTA(
                isLoading = state.isSubmitting,
                isEnabled = state.canSubmit,
                onClick = { viewModel.submit() },
            )
        }

        state.toast?.let { toast ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 110.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (toast.isError) PantopusColors.error else PantopusColors.success)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            ) {
                Text(
                    text = toast.text,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
    }
}

@Composable
private fun EnvelopeCard(
    ocrRecipient: String,
    confidence: Double,
    envelopeUrl: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(Radii.lg))
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .semantics { contentDescription = "Scanned envelope" },
            contentAlignment = Alignment.Center,
        ) {
            if (envelopeUrl != null) {
                SubcomposeAsyncImage(
                    model = envelopeUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth(),
                    loading = { Shimmer(width = 320.dp, height = 180.dp, cornerRadius = Radii.lg) },
                    error = { EnvelopePlaceholder() },
                )
            } else {
                EnvelopePlaceholder()
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = ocrRecipient.ifEmpty { "—" },
                fontSize = 13.sp,
                color = PantopusColors.appTextStrong,
                fontFamily = FontFamily.Monospace,
                maxLines = 3,
                modifier = Modifier.weight(1f),
            )
            ConfidencePill(confidence = confidence)
        }
    }
}

@Composable
private fun EnvelopePlaceholder() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        PantopusIconImage(
            icon = PantopusIcon.Mailbox,
            contentDescription = null,
            size = 28.dp,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "Envelope preview unavailable",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConfidencePill(confidence: Double) {
    val percent = (confidence * 100).toInt()
    val (background, foreground) =
        when {
            confidence < 0.5 -> PantopusColors.errorBg to PantopusColors.error
            confidence < 0.8 -> PantopusColors.warningBg to PantopusColors.warning
            else -> PantopusColors.successBg to PantopusColors.success
        }
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(background)
                .padding(horizontal = Spacing.s2, vertical = 4.dp)
                .semantics { contentDescription = "AI confidence $percent percent" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Info,
            contentDescription = null,
            size = 12.dp,
            tint = foreground,
        )
        Text(
            text = "AI confidence: $percent%",
            style = PantopusTextStyle.caption,
            color = foreground,
        )
    }
}

@Composable
private fun RecipientRow(
    choice: MailRecipientChoice,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(onClick = onTap)
                .padding(vertical = Spacing.s2)
                .testTag("disambiguateRow_${choice.drawer}")
                .semantics {
                    contentDescription = "${choice.title}, ${choice.subtitle}"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        AvatarWithIdentityRing(
            name = choice.title,
            identity = choice.identity,
            ringProgress = 1f,
            size = 36.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = choice.title, style = PantopusTextStyle.body, color = PantopusColors.appText)
            Text(
                text = choice.subtitle,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        RadioMark(isSelected = isSelected)
    }
}

@Composable
private fun RadioMark(isSelected: Boolean) {
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 6.dp else 2.dp,
                    color =
                        if (isSelected) PantopusColors.primary600 else PantopusColors.appBorderStrong,
                    shape = CircleShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurface),
            )
        }
    }
}

@Composable
private fun AliasNotesField(
    text: String,
    onChange: (String) -> Unit,
    error: String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = "Notes",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        OutlinedTextField(
            value = text,
            onValueChange = onChange,
            placeholder = { Text("Add a name to remember this routing") },
            shape = RoundedCornerShape(Radii.md),
            textStyle = TextStyle(fontSize = 14.sp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor =
                        if (error == null) PantopusColors.primary600 else PantopusColors.error,
                    unfocusedBorderColor =
                        if (error == null) PantopusColors.appBorder else PantopusColors.error,
                    focusedContainerColor = PantopusColors.appSurfaceSunken,
                    unfocusedContainerColor = PantopusColors.appSurfaceSunken,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp)
                    .testTag("disambiguateAliasField"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = error.orEmpty(),
                style = PantopusTextStyle.caption,
                color = PantopusColors.error,
            )
            Text(
                text = "${text.length} / 255",
                style = PantopusTextStyle.caption,
                color =
                    if (text.length > 255) PantopusColors.error else PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun StickyCTA(
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorderSubtle))
        Box(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
            contentAlignment = Alignment.Center,
        ) {
            PrimaryButton(
                title = "Confirm recipient",
                onClick = onClick,
                isEnabled = isEnabled,
                isLoading = isLoading,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun DisambiguatePreview() {
    // Hilt-only ViewModel can't render in @Preview; surface a marker.
    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        Text(
            text = "DisambiguateMailFormScreen — runtime Hilt graph required",
            modifier = Modifier.align(Alignment.Center),
            color = PantopusColors.appTextSecondary,
        )
    }
}
