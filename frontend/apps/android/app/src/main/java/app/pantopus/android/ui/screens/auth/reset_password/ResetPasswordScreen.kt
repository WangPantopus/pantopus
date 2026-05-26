@file:Suppress("PackageNaming", "MatchingDeclarationName", "MagicNumber", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.auth.reset_password

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.auth.sign_up.ErrorBanner
import app.pantopus.android.ui.screens.status.StatusWaitingContent
import app.pantopus.android.ui.screens.status.StatusWaitingScreen
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

object ResetPasswordScreenTags {
    const val ROOT = "resetPasswordScreen"
    const val CLOSE = "resetPasswordCloseButton"
    const val PASSWORD = "resetPasswordPasswordField"
    const val CONFIRM = "resetPasswordConfirmField"
    const val SUBMIT = "resetPasswordSubmitButton"
    const val STRENGTH = "resetPasswordStrengthMeter"
    const val ERROR_BANNER = "resetPasswordErrorBanner"
    const val SUCCESS_STATUS = "resetPasswordSuccessStatus"
    const val SUCCESS_BACK = "resetPasswordSuccessBack"
}

/**
 * Reset-password surface. Renders the form (X close + "Set new password"
 * title + new + confirm password fields + strength meter + "Set password"
 * CTA) while [ResetPasswordViewModel]'s phase is
 * [ResetPasswordViewModel.Phase.Form]; flips to the shared Status / Wait
 * "Password reset" frame on success and auto-redirects to login after
 * [redirectDelayMs] milliseconds.
 */
@Composable
fun ResetPasswordScreen(
    token: String,
    viewModel: ResetPasswordViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
    onDone: () -> Unit = {},
    redirectDelayMs: Long = 3_000,
) {
    @Suppress("UNUSED_PARAMETER")
    val tokenForRouter = token
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurface)
                .testTag(ResetPasswordScreenTags.ROOT),
    ) {
        TopBar(onClose = onClose)
        when (state.phase) {
            is ResetPasswordViewModel.Phase.Form -> FormBody(state = state, viewModel = viewModel)
            is ResetPasswordViewModel.Phase.Reset ->
                SuccessBody(
                    onDone = onDone,
                    redirectDelayMs = redirectDelayMs,
                )
        }
    }
}

@Composable
private fun TopBar(onClose: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(PantopusColors.appSurface),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clickable(onClick = onClose)
                        .testTag(ResetPasswordScreenTags.CLOSE)
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
        }
        Text(
            text = "Set new password",
            style = PantopusTextStyle.body.copy(fontWeight = FontWeight.SemiBold),
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
    }
}

@Composable
private fun FormBody(
    state: ResetPasswordViewModel.UiState,
    viewModel: ResetPasswordViewModel,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s5)
                .padding(top = Spacing.s5, bottom = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Text(
                text = "Set a new password",
                style = PantopusTextStyle.h2,
                color = PantopusColors.appText,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = "Choose a password you haven't used here before.",
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextSecondary,
            )
        }

        state.errorMessage?.let { error ->
            ErrorBanner(
                error = error,
                onDismiss = viewModel::clearError,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(ResetPasswordScreenTags.ERROR_BANNER),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            PasswordField(
                label = "New password",
                value = state.password,
                onChange = viewModel::onPasswordChange,
                tag = ResetPasswordScreenTags.PASSWORD,
            )
            PasswordStrengthRow(
                score = state.passwordStrength,
                label = state.passwordStrengthLabel,
            )
        }

        PasswordField(
            label = "Confirm new password",
            value = state.confirmPassword,
            onChange = viewModel::onConfirmPasswordChange,
            tag = ResetPasswordScreenTags.CONFIRM,
        )

        SubmitButton(
            label = "Set password",
            isLoading = state.isLoading,
            isEnabled = state.canSubmit,
            onClick = viewModel::submit,
        )
    }
}

@Composable
private fun PasswordField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    tag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                    .padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Lock,
                contentDescription = null,
                size = Radii.xl,
                tint = PantopusColors.appTextSecondary,
            )
            BasicTextField(
                value = value,
                onValueChange = onChange,
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(PantopusColors.primary600),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag(tag)
                        .semantics { contentDescription = label },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "••••••••",
                            style = PantopusTextStyle.body,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun PasswordStrengthRow(
    score: Int,
    label: String,
) {
    val color: Color =
        when (score) {
            1 -> PantopusColors.error
            2 -> PantopusColors.warning
            3 -> PantopusColors.success
            else -> PantopusColors.appBorder
        }
    Row(
        modifier = Modifier.fillMaxWidth().testTag(ResetPasswordScreenTags.STRENGTH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(3) { index ->
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(if (index < score) color else PantopusColors.appSurfaceSunken),
            )
        }
        Text(
            text = label,
            style = PantopusTextStyle.caption.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.width(48.dp),
        )
    }
}

@Composable
private fun SubmitButton(
    label: String,
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val background =
        if (isEnabled) PantopusColors.primary600 else PantopusColors.appBorderStrong
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(background)
                .clickable(enabled = isEnabled, onClick = onClick)
                .testTag(ResetPasswordScreenTags.SUBMIT)
                .semantics { contentDescription = if (isLoading) "Resetting" else label },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = PantopusColors.appTextInverse,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = label,
                style = PantopusTextStyle.body.copy(fontWeight = FontWeight.SemiBold),
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun SuccessBody(
    onDone: () -> Unit,
    redirectDelayMs: Long,
) {
    val content = remember { StatusWaitingContent.passwordReset() }
    LaunchedEffect(Unit) {
        if (redirectDelayMs > 0) delay(redirectDelayMs)
        onDone()
    }
    StatusWaitingScreen(
        content = content,
        onAction = {},
        onPrimary = { onDone() },
        onSecondary = { onDone() },
        modifier = Modifier.fillMaxSize(),
        rootTestTag = ResetPasswordScreenTags.SUCCESS_STATUS,
        primaryTestTag = ResetPasswordScreenTags.SUCCESS_BACK,
    )
}
