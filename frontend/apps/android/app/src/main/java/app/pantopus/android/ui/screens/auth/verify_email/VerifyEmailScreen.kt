@file:Suppress("PackageNaming", "MatchingDeclarationName", "MagicNumber", "LongMethod", "ReturnCount", "TooManyFunctions")

package app.pantopus.android.ui.screens.auth.verify_email

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.auth.AuthError
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

object VerifyEmailScreenTags {
    const val ROOT = "verifyEmailScreen"
    const val ILLUSTRATION = "verifyEmailIllustration"
    const val OPEN_MAIL = "verifyEmailOpenMailButton"
    const val RESEND = "verifyEmailResendButton"
    const val DO_LATER = "verifyEmailDoLaterButton"
    const val CHANGE_EMAIL = "verifyEmailChangeEmailButton"
    const val BANNER = "verifyEmailBanner"
}

/**
 * Verify-email surface. Big mail icon + headline + body + action stack
 * (Open mail / Resend / I'll do this later [soft-gate only] / Wrong
 * email). When the view-model carries a token (deep-link path), kicks
 * the verification call on appear and renders a status banner.
 */
@Composable
fun VerifyEmailScreen(
    viewModel: VerifyEmailViewModel = hiltViewModel(),
    onDone: () -> Unit = {},
    onChangeEmail: (String) -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.token) { viewModel.verifyOnAppearIfNeeded() }
    LaunchedEffect(state.didComplete) {
        if (state.didComplete) onDone()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appSurface)
                .testTag(VerifyEmailScreenTags.ROOT),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s5),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s5),
        ) {
            Spacer(modifier = Modifier.height(Spacing.s10))
            Illustration()
            HeadlineBlock(email = state.email)
            BannerLine(state = state)
            Spacer(modifier = Modifier.height(Spacing.s10))
        }

        ActionStack(
            state = state,
            onOpenMail = { openMailApp(context) },
            onResend = viewModel::resend,
            onDoLater = onDone,
            onChangeEmail = { onChangeEmail(state.email.orEmpty()) },
        )
    }
}

@Composable
private fun Illustration() {
    Box(
        modifier =
            Modifier
                .size(140.dp)
                .clip(CircleShape)
                .background(PantopusColors.primary50)
                .testTag(VerifyEmailScreenTags.ILLUSTRATION),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Mailbox,
            contentDescription = null,
            size = 80.dp,
            tint = PantopusColors.primary500,
        )
    }
}

@Composable
private fun HeadlineBlock(email: String?) {
    val recipient = email ?: "your email"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(
            text = "Verify your email",
            style = PantopusTextStyle.h2,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "We sent a verification link to $recipient. Click it to unlock all features.",
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

private data class BannerCopy(
    val text: String,
    val color: Color,
    val background: Color,
)

@Composable
private fun BannerLine(state: VerifyEmailViewModel.UiState) {
    val copy = bannerCopyFor(state) ?: return
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(copy.background)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                .testTag(VerifyEmailScreenTags.BANNER),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = copy.text,
            style = PantopusTextStyle.caption,
            color = copy.color,
            textAlign = TextAlign.Center,
        )
    }
}

private fun bannerCopyFor(state: VerifyEmailViewModel.UiState): BannerCopy? {
    if (state.isVerifying) {
        return BannerCopy(
            text = "Verifying your email…",
            color = PantopusColors.primary700,
            background = PantopusColors.primary50,
        )
    }
    if (state.didVerify) {
        return BannerCopy(
            text = "Email verified. You can now sign in.",
            color = PantopusColors.success,
            background = PantopusColors.successBg,
        )
    }
    state.errorMessage?.let { error ->
        return BannerCopy(
            text = errorBlurb(error),
            color = PantopusColors.error,
            background = PantopusColors.errorBg,
        )
    }
    if (state.didResend) {
        return BannerCopy(
            text = "Verification email sent.",
            color = PantopusColors.primary700,
            background = PantopusColors.primary50,
        )
    }
    return null
}

private fun errorBlurb(error: AuthError): String = error.message

@Composable
private fun ActionStack(
    state: VerifyEmailViewModel.UiState,
    onOpenMail: () -> Unit,
    onResend: () -> Unit,
    onDoLater: () -> Unit,
    onChangeEmail: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PrimaryButton(
            label = "Open mail app",
            onClick = onOpenMail,
            tag = VerifyEmailScreenTags.OPEN_MAIL,
        )
        GhostButton(
            label = "Resend email",
            isLoading = state.isResending,
            isEnabled = state.canResend,
            onClick = onResend,
            tag = VerifyEmailScreenTags.RESEND,
        )
        if (state.softGate) {
            Text(
                text = "I'll do this later",
                style = PantopusTextStyle.small.copy(fontWeight = FontWeight.SemiBold),
                color = PantopusColors.primary600,
                modifier =
                    Modifier
                        .clickable(onClick = onDoLater)
                        .padding(vertical = Spacing.s1)
                        .testTag(VerifyEmailScreenTags.DO_LATER)
                        .semantics { contentDescription = "I'll do this later" },
            )
        }
        Text(
            text = "Wrong email? Change it",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            modifier =
                Modifier
                    .clickable(onClick = onChangeEmail)
                    .padding(vertical = Spacing.s1)
                    .testTag(VerifyEmailScreenTags.CHANGE_EMAIL)
                    .semantics { contentDescription = "Change email" },
        )
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.primary600)
                .clickable(onClick = onClick)
                .testTag(tag)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.body.copy(fontWeight = FontWeight.SemiBold),
            color = PantopusColors.appTextInverse,
        )
    }
}

@Composable
private fun GhostButton(
    label: String,
    isLoading: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
    tag: String,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderStrong, RoundedCornerShape(Radii.lg))
                .clickable(enabled = isEnabled, onClick = onClick)
                .testTag(tag)
                .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                color = PantopusColors.appText,
                strokeWidth = 2.dp,
                modifier = Modifier.size(20.dp),
            )
        } else {
            Text(
                text = label,
                style = PantopusTextStyle.body.copy(fontWeight = FontWeight.SemiBold),
                color = if (isEnabled) PantopusColors.appText else PantopusColors.appTextMuted,
            )
        }
    }
}

/**
 * Fires an [Intent.ACTION_VIEW] against `mailto:` so the system picker
 * surfaces whichever mail apps the user has installed. If no app handles
 * the intent (rare; the AOSP base image ships Gmail) the launcher
 * silently swallows it — we don't crash.
 */
private fun openMailApp(context: android.content.Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mailto:"))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}
