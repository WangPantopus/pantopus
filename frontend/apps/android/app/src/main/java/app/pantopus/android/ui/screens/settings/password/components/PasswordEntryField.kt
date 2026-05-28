@file:Suppress("MagicNumber", "PackageNaming", "LongParameterList", "LongMethod")

package app.pantopus.android.ui.screens.settings.password.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * A13.14 — masked password input. Extends the [PantopusTextField][app.pantopus.android.ui.components.PantopusTextField]
 * visual vocabulary with an optional leading status icon (lock), a reveal
 * toggle (eye / eye-off), a "revealed" monospace mode for sanity-checking a
 * strong password, a helper line, and the shared default/valid/error states.
 * Mirrors the iOS `PasswordEntryField` (the design's `PasswordField` atom —
 * named `PasswordEntryField` to match iOS, where the Auth feature owns the
 * plain `PasswordField` name).
 */
@Composable
fun PasswordEntryField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    state: PantopusFieldState = PantopusFieldState.Default,
    isRequired: Boolean = false,
    leftIcon: PantopusIcon? = null,
    helper: String? = null,
    revealedByDefault: Boolean = false,
    fieldTestTag: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    var revealOverride by remember { mutableStateOf<Boolean?>(null) }
    val isRevealed = revealOverride ?: revealedByDefault
    val border = borderColor(state, isFocused)
    val borderWidth =
        if (isFocused) {
            2.dp
        } else if (state is PantopusFieldState.Default) {
            1.dp
        } else {
            1.5.dp
        }

    Column(
        modifier =
            modifier.semantics {
                val base =
                    when (state) {
                        is PantopusFieldState.Error -> "$label, error: ${state.message}"
                        PantopusFieldState.Valid -> "$label, valid"
                        PantopusFieldState.Default -> label
                    }
                contentDescription = if (isRequired) "$base, required" else base
            },
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = label, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            if (isRequired) {
                Text(text = "*", style = PantopusTextStyle.caption, color = PantopusColors.error)
            }
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 46.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(width = borderWidth, color = border, shape = RoundedCornerShape(Radii.md))
                    .padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            if (leftIcon != null) {
                PantopusIconImage(
                    icon = leftIcon,
                    contentDescription = null,
                    size = Radii.xl,
                    tint = if (state is PantopusFieldState.Error) PantopusColors.error else PantopusColors.appTextSecondary,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle =
                    if (isRevealed) {
                        PantopusTextStyle.body.copy(
                            color = PantopusColors.appText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.5.sp,
                        )
                    } else {
                        PantopusTextStyle.body.copy(color = PantopusColors.appText)
                    },
                cursorBrush = SolidColor(PantopusColors.primary600),
                singleLine = true,
                interactionSource = interactionSource,
                visualTransformation = if (isRevealed) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier =
                    Modifier
                        .weight(1f)
                        .then(if (fieldTestTag != null) Modifier.testTag(fieldTestTag) else Modifier),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(text = placeholder, style = PantopusTextStyle.body, color = PantopusColors.appTextMuted)
                    }
                    inner()
                },
            )
            when (state) {
                PantopusFieldState.Valid ->
                    PantopusIconImage(
                        icon = PantopusIcon.CheckCircle,
                        contentDescription = null,
                        size = 18.dp,
                        tint = PantopusColors.success,
                    )
                is PantopusFieldState.Error ->
                    PantopusIconImage(
                        icon = PantopusIcon.AlertCircle,
                        contentDescription = null,
                        size = 18.dp,
                        tint = PantopusColors.error,
                    )
                PantopusFieldState.Default -> Unit
            }
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clickable { revealOverride = !isRevealed }
                        .semantics { contentDescription = if (isRevealed) "Hide password" else "Show password" }
                        .then(if (fieldTestTag != null) Modifier.testTag("${fieldTestTag}_reveal") else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = if (isRevealed) PantopusIcon.EyeOff else PantopusIcon.Eye,
                    contentDescription = null,
                    size = 17.dp,
                    tint = PantopusColors.appTextSecondary,
                )
            }
        }
        when {
            state is PantopusFieldState.Error ->
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.AlertCircle,
                        contentDescription = null,
                        size = Radii.lg,
                        tint = PantopusColors.error,
                    )
                    Text(
                        text = state.message,
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.error,
                    )
                }
            helper != null ->
                Text(
                    text = helper,
                    style = PantopusTextStyle.caption,
                    color = if (state is PantopusFieldState.Valid) PantopusColors.success else PantopusColors.appTextSecondary,
                )
        }
    }
}

private fun borderColor(
    state: PantopusFieldState,
    isFocused: Boolean,
): Color =
    when (state) {
        is PantopusFieldState.Error -> PantopusColors.error
        PantopusFieldState.Valid -> PantopusColors.success
        PantopusFieldState.Default -> if (isFocused) PantopusColors.primary600 else PantopusColors.appBorder
    }
