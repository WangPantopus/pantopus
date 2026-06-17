@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList")

package app.pantopus.android.ui.screens.scheduling.invitee.edge

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.GhostButton
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling.invitee.edge.OpenInAppViewModel.OpenInAppUiState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val OPEN_IN_APP_TAG = "schedulingOpenInApp"

/**
 * D9 — Open-in-App / deep-link hand-off (native interstitial). Resolves the
 * inbound booking link and hands the invitee into the in-app flow ("Continue in
 * app"), or falls back to the web when it can't ("Continue on the web"). The
 * pending link is read read-only from the Foundation `DeepLinkRouter`; this
 * screen adds no route plumbing.
 */
@Composable
fun OpenInAppInterstitialScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: OpenInAppViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }

    val openWeb: (String?) -> Unit = { url ->
        if (url != null) {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                .onFailure { onBack() }
        } else {
            onBack()
        }
    }

    OpenInAppContent(
        state = state,
        onContinueInApp = onNavigate,
        onStayOnWeb = openWeb,
        onBack = onBack,
    )
}

@Composable
fun OpenInAppContent(
    state: OpenInAppUiState,
    onContinueInApp: (String) -> Unit,
    onStayOnWeb: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(PantopusColors.appBg).testTag(OPEN_IN_APP_TAG)) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s6).padding(bottom = Spacing.s16),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                is OpenInAppUiState.Resolving -> ResolvingBody()
                is OpenInAppUiState.Resolved -> ResolvedBody(state)
                is OpenInAppUiState.Failed -> FailedBody()
            }
        }
        Dock(state = state, onContinueInApp = onContinueInApp, onStayOnWeb = onStayOnWeb, onBack = onBack)
    }
}

@Composable
private fun ResolvingBody() {
    BrandMark(size = 52.dp)
    Box(modifier = Modifier.padding(top = Spacing.s5).fillMaxWidth()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                    .padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Shimmer(width = 38.dp, height = 38.dp, cornerRadius = Radii.md)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Shimmer(width = 120.dp, height = 11.dp, cornerRadius = Radii.xs)
                Shimmer(width = 160.dp, height = 9.dp, cornerRadius = Radii.xs)
            }
        }
    }
    Row(modifier = Modifier.padding(top = Spacing.s5), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(SchedulingPillar.Personal.accent))
        Text(
            text = "Opening your booking",
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(start = Spacing.s2),
        )
    }
}

@Composable
private fun ResolvedBody(state: OpenInAppUiState.Resolved) {
    Box(
        modifier = Modifier.size(64.dp).clip(CircleShape).background(SchedulingPillar.Personal.accentBg),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = PantopusIcon.User, contentDescription = null, size = 28.dp, tint = SchedulingPillar.Personal.accent)
    }
    Text(
        text = "Pick up where you left off",
        style = PantopusTextStyle.h2,
        color = PantopusColors.appText,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Spacing.s5),
    )
    Text(
        text = "Your timezone and details come with you.",
        style = PantopusTextStyle.small,
        color = PantopusColors.appTextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Spacing.s2).widthIn(max = 240.dp),
    )
    Row(
        modifier =
            Modifier
                .padding(top = Spacing.s5)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(RoundedCornerShape(Radii.md)).background(SchedulingPillar.Personal.accentBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Calendar,
                contentDescription = null,
                size = 18.dp,
                tint = SchedulingPillar.Personal.accent,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = state.title, style = PantopusTextStyle.small, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            state.subtitle?.let {
                Text(text = it, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
        }
    }
}

@Composable
private fun FailedBody() {
    Box(
        modifier = Modifier.size(84.dp).clip(CircleShape).background(PantopusColors.warningBg),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = PantopusIcon.Smartphone, contentDescription = null, size = 34.dp, tint = PantopusColors.warning)
    }
    Text(
        text = "We couldn't open this in the app",
        style = PantopusTextStyle.h2,
        color = PantopusColors.appText,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Spacing.s5),
    )
    Text(
        text = "No problem — you can keep going on the web. Your booking is right where you left it.",
        style = PantopusTextStyle.small,
        color = PantopusColors.appTextSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = Spacing.s2).widthIn(max = 244.dp),
    )
}

@Composable
private fun BoxScope.Dock(
    state: OpenInAppUiState,
    onContinueInApp: (String) -> Unit,
    onStayOnWeb: (String?) -> Unit,
    onBack: () -> Unit,
) {
    when (state) {
        is OpenInAppUiState.Resolving -> Unit
        is OpenInAppUiState.Resolved ->
            DockColumn {
                PrimaryButton(title = "Continue in app", onClick = { onContinueInApp(state.targetRoute) })
                GhostButton(title = "Stay on web", onClick = { onStayOnWeb(state.webUrl) })
            }
        is OpenInAppUiState.Failed ->
            DockColumn {
                PrimaryButton(title = "Continue on the web", onClick = { onStayOnWeb(state.webUrl) })
                GhostButton(title = "Go back", onClick = onBack)
            }
    }
}

@Composable
private fun BoxScope.DockColumn(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        content()
    }
}

@Composable
private fun BrandMark(size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(Radii.lg)).background(SchedulingPillar.Personal.accent),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Calendarly,
            contentDescription = "Pantopus",
            size = size * 0.6f,
            tint = PantopusColors.appTextInverse,
        )
    }
}
