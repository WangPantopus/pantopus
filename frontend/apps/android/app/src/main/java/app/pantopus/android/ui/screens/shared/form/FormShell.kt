@file:Suppress("MagicNumber", "PackageNaming", "LongParameterList")

package app.pantopus.android.ui.screens.shared.form

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag on the shell root. */
const val FORM_SHELL_TAG = "formShell"

/** Test tag on the close (X) button. */
const val FORM_CLOSE_BUTTON_TAG = "formCloseButton"

/** Test tag on the right-action commit button. */
const val FORM_COMMIT_BUTTON_TAG = "formCommitButton"

/** Test tag on the sticky bottom-CTA button (`bottomActionLabel` mode). */
const val FORM_BOTTOM_COMMIT_BUTTON_TAG = "formBottomCommitButton"

/**
 * Scaffold for every Form screen — mirrors the iOS `FormShell`.
 *
 * 44dp top bar with a leading X, centered title, and a right-aligned
 * text action (`Save` / `Send` / `Done`). The action button renders in
 * `primary600` when enabled and `appTextMuted` when disabled. The
 * shell owns the dirty-close confirm dialog so feature screens don't
 * re-implement it.
 *
 * @param title Centered top-bar title.
 * @param rightActionLabel Text for the trailing action; defaults to `Save`.
 * @param isValid Whether the right action is enabled.
 * @param isDirty Whether the right action is enabled, and whether
 *     close prompts the discard confirm.
 * @param isSaving Render a spinner in place of the right-action label
 *     while a commit is in flight.
 * @param onClose Invoked when the user taps X on a clean form, or
 *     confirms discard on a dirty one.
 * @param onCommit Invoked when the user taps the right action.
 * @param body Content slot — typically a vertical stack of
 *     [FormFieldGroup]s.
 */
@Composable
fun FormShell(
    title: String,
    isValid: Boolean,
    isDirty: Boolean,
    onClose: () -> Unit,
    onCommit: () -> Unit,
    rightActionLabel: String? = "Save",
    bottomActionLabel: String? = null,
    isSaving: Boolean = false,
    body: @Composable () -> Unit,
) {
    var showDiscardConfirm by remember { mutableStateOf(false) }

    val handleClose = {
        if (isDirty) showDiscardConfirm = true else onClose()
    }

    val showsTopRightAction = bottomActionLabel == null && rightActionLabel != null

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag(FORM_SHELL_TAG),
    ) {
        FormTopBar(
            title = title,
            rightActionLabel = if (showsTopRightAction) rightActionLabel else null,
            rightActionEnabled = isValid && isDirty && !isSaving,
            isSaving = isSaving && bottomActionLabel == null,
            onClose = handleClose,
            onCommit = onCommit,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s5),
        ) {
            body()
        }
        if (bottomActionLabel != null) {
            FormBottomCTA(
                label = bottomActionLabel,
                isEnabled = isValid && !isSaving,
                isSaving = isSaving,
                onCommit = onCommit,
            )
        }
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text("Discard changes?") },
            text = { Text("You'll lose any unsaved edits.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardConfirm = false
                    onClose()
                }) { Text("Discard", color = PantopusColors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Keep editing")
                }
            },
        )
    }
}

@Composable
private fun FormTopBar(
    title: String,
    rightActionLabel: String?,
    rightActionEnabled: Boolean,
    isSaving: Boolean,
    onClose: () -> Unit,
    onCommit: () -> Unit,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(PantopusColors.appSurface),
        ) {
            Text(
                text = title,
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .semantics { heading() },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clickable(onClick = onClose)
                            .testTag(FORM_CLOSE_BUTTON_TAG)
                            .semantics { contentDescription = "Close" },
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.X,
                        contentDescription = null,
                        size = 22.dp,
                        tint = PantopusColors.appText,
                    )
                }
                Box(modifier = Modifier.fillMaxWidth().sizeIn(minHeight = 44.dp).weight(1f))
                if (rightActionLabel != null) {
                    Box(
                        modifier =
                            Modifier
                                .sizeIn(minWidth = 60.dp, minHeight = 44.dp)
                                .clickable(enabled = rightActionEnabled, onClick = onCommit)
                                .testTag(FORM_COMMIT_BUTTON_TAG)
                                .semantics { contentDescription = rightActionLabel },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                        } else {
                            Text(
                                text = rightActionLabel,
                                style = PantopusTextStyle.body,
                                color =
                                    if (rightActionEnabled) {
                                        PantopusColors.primary600
                                    } else {
                                        PantopusColors.appTextMuted
                                    },
                            )
                        }
                    }
                } else {
                    // Reserve 60dp so the centered title stays optically
                    // centered against the leading X button.
                    Box(modifier = Modifier.size(width = 60.dp, height = 44.dp))
                }
            }
        }
        HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
    }
}

/**
 * Sticky full-width primary CTA used when `bottomActionLabel` is set.
 * Mirrors the iOS `FormBottomCTA`.
 */
@Composable
private fun FormBottomCTA(
    label: String,
    isEnabled: Boolean,
    isSaving: Boolean,
    onCommit: () -> Unit,
) {
    Column {
        HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appSurface)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .background(
                            color =
                                if (isEnabled) PantopusColors.primary600 else PantopusColors.appBorderStrong,
                            shape = RoundedCornerShape(Radii.lg),
                        ).clickable(enabled = isEnabled, onClick = onCommit)
                        .testTag(FORM_BOTTOM_COMMIT_BUTTON_TAG)
                        .semantics { contentDescription = label },
                contentAlignment = Alignment.Center,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        color = PantopusColors.appTextInverse,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    Text(
                        text = label,
                        style = PantopusTextStyle.body,
                        color = PantopusColors.appTextInverse,
                    )
                }
            }
        }
    }
}
