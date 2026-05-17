@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.settings.password

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.screens.shared.form.FormFieldGroup
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * P8 / T6.2c — Settings → Password.
 *
 * Thin wrapper around [FormShell] backed by [PasswordChangeViewModel].
 */
@Composable
fun PasswordChangeScreen(
    onBack: () -> Unit = {},
    viewModel: PasswordChangeViewModel = hiltViewModel(),
) {
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val shouldDismiss by viewModel.shouldDismiss.collectAsStateWithLifecycle()
    val hasPassword by viewModel.hasPassword.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(shouldDismiss) {
        if (shouldDismiss) {
            viewModel.acknowledgeDismiss()
            delay(700)
            onBack()
        }
    }

    FormShell(
        title = "Change password",
        rightActionLabel = "Save",
        isValid = viewModel.isValid,
        isDirty = viewModel.isDirty,
        isSaving = isSaving,
        onClose = onBack,
        onCommit = viewModel::save,
    ) {
        FormFieldGroup(
            title = if (hasPassword) "Update password" else "Set a password",
        ) {
            if (hasPassword) {
                SecureField(
                    label = "Current password",
                    key = PasswordChangeViewModel.FieldKey.Current,
                    snapshot = fields[PasswordChangeViewModel.FieldKey.Current],
                    onChange = { value -> viewModel.update(PasswordChangeViewModel.FieldKey.Current, value) },
                )
            }
            SecureField(
                label = "New password",
                key = PasswordChangeViewModel.FieldKey.New,
                snapshot = fields[PasswordChangeViewModel.FieldKey.New],
                onChange = { value -> viewModel.update(PasswordChangeViewModel.FieldKey.New, value) },
            )
            SecureField(
                label = "Confirm new password",
                key = PasswordChangeViewModel.FieldKey.Confirm,
                snapshot = fields[PasswordChangeViewModel.FieldKey.Confirm],
                onChange = { value -> viewModel.update(PasswordChangeViewModel.FieldKey.Confirm, value) },
            )
        }
        Text(
            text = "Use at least ${PasswordChangeViewModel.MIN_LENGTH} characters. A mix of letters, numbers, and symbols is strongest.",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
        toast?.let { message ->
            Text(
                text = message,
                style = PantopusTextStyle.caption,
                color = PantopusColors.success,
                modifier =
                    Modifier
                        .padding(horizontal = Spacing.s4),
            )
        }
    }
}

@Composable
private fun SecureField(
    label: String,
    key: PasswordChangeViewModel.FieldKey,
    snapshot: app.pantopus.android.ui.screens.shared.form.FormFieldState?,
    onChange: (String) -> Unit,
) {
    val value = snapshot?.value.orEmpty()
    val error = snapshot?.error
    PantopusTextField(
        label = label,
        value = value,
        onValueChange = onChange,
        state = if (error != null) PantopusFieldState.Error(error) else PantopusFieldState.Default,
        isSecure = true,
        keyboardType = KeyboardType.Password,
        fieldTestTag = "field_${key.name.lowercase()}",
    )
}
