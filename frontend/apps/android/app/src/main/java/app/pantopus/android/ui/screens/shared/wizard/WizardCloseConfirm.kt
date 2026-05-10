package app.pantopus.android.ui.screens.shared.wizard

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.pantopus.android.ui.theme.PantopusColors

/** Test tag applied to the discard-confirm dialog container. */
const val WIZARD_DISCARD_DIALOG_TAG = "wizardDiscardDialog"

/**
 * Shared "Discard your progress?" confirmation dialog used by [WizardShell]
 * when the user taps X on a dirty step. The shell renders this; feature
 * code never imports it directly.
 */
@Composable
internal fun WizardCloseConfirm(
    visible: Boolean,
    onDiscard: () -> Unit,
    onKeepGoing: () -> Unit,
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onKeepGoing,
        modifier = Modifier.testTag(WIZARD_DISCARD_DIALOG_TAG),
        title = { Text("Discard your progress?") },
        text = { Text("You'll lose what you've entered so far.") },
        confirmButton = {
            TextButton(onClick = onDiscard) {
                Text("Discard", color = PantopusColors.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepGoing) { Text("Keep going") }
        },
    )
}
