@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.homes.invite_owner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.screens.shared.form.FormFieldGroup
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Invite Owner form. ViewModel reads `homeId` and (debug-only)
 * `currentUserEmail` via [androidx.lifecycle.SavedStateHandle].
 */
@Composable
fun InviteOwnerFormScreen(
    onClose: () -> Unit,
    viewModel: InviteOwnerFormViewModel = hiltViewModel(),
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
        FormShell(
            title = "Invite owner",
            rightActionLabel = "Send",
            isValid = state.isValid,
            isDirty = state.isDirty,
            isSaving = state.isSaving,
            onClose = onClose,
            onCommit = { viewModel.submit() },
        ) {
            FormFieldGroup("Owner details") {
                FieldFor(
                    label = "Email",
                    placeholder = "name@example.com",
                    keyboardType = KeyboardType.Email,
                    field = InviteOwnerField.Email,
                    state = state,
                    onChange = viewModel::update,
                    testTag = "inviteOwnerEmailField",
                )
                FieldFor(
                    label = "Phone (optional)",
                    placeholder = "+15555550123",
                    keyboardType = KeyboardType.Phone,
                    field = InviteOwnerField.Phone,
                    state = state,
                    onChange = viewModel::update,
                    testTag = "inviteOwnerPhoneField",
                )
            }
        }

        state.toast?.let { toast ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
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
private fun FieldFor(
    label: String,
    placeholder: String,
    keyboardType: KeyboardType,
    field: InviteOwnerField,
    state: InviteOwnerUiState,
    onChange: (InviteOwnerField, String) -> Unit,
    testTag: String,
) {
    val snapshot = state.fields[field]
    val fieldState =
        when {
            snapshot == null || !snapshot.touched -> PantopusFieldState.Default
            snapshot.error != null -> PantopusFieldState.Error(snapshot.error)
            snapshot.value.trim().isEmpty() -> PantopusFieldState.Default
            else -> PantopusFieldState.Valid
        }
    PantopusTextField(
        label = label,
        value = snapshot?.value.orEmpty(),
        onValueChange = { onChange(field, it) },
        placeholder = placeholder,
        state = fieldState,
        keyboardType = keyboardType,
        fieldTestTag = testTag,
    )
}
