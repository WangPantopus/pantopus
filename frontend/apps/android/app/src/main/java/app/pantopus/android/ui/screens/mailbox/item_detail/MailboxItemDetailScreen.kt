@file:Suppress("LongMethod", "MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.item_detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.MailItemPlaceholderBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.PackageBody
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Hub → MailboxList → MailboxItemDetail screen. The ViewModel reads the
 * mail id via the nav-backstack [androidx.lifecycle.SavedStateHandle].
 */
@Composable
fun MailboxItemDetailScreen(
    onBack: () -> Unit,
    viewModel: MailboxItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctaFlags by viewModel.ctaFlags.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(ctaFlags.errorToast) {
        if (ctaFlags.errorToast != null) {
            delay(2_000)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        when (val s = state) {
            MailboxItemDetailUiState.Loading -> LoadingLayout(onBack = onBack)
            is MailboxItemDetailUiState.Error ->
                ErrorLayout(message = s.message, onRetry = { viewModel.refresh() })
            is MailboxItemDetailUiState.Loaded -> {
                val content = s.content
                MailboxItemDetailShell(
                    category = content.category,
                    trust = content.trust,
                    sender = content.sender,
                    aiElf = content.aiElf,
                    keyFacts = content.keyFacts,
                    timeline = content.timeline,
                    cta = ctaContent(content, ctaFlags),
                    onBack = onBack,
                    onPrimary = { viewModel.logAsReceived() },
                    onGhost = { viewModel.markNotMine() },
                ) {
                    val pkg = content.packageInfo
                    if (content.category == MailItemCategory.Package && pkg != null) {
                        PackageBody(carrier = pkg.carrier, etaLine = pkg.etaLine)
                    } else if (content.category != MailItemCategory.Package) {
                        MailItemPlaceholderBody(category = content.category)
                    }
                }
            }
        }
        ctaFlags.errorToast?.let { toast ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.error)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            ) {
                Text(toast, style = PantopusTextStyle.small, color = PantopusColors.appTextInverse)
            }
        }
    }
}

private fun ctaContent(
    content: MailboxItemDetailContent,
    flags: MailboxCTAFlags,
): MailboxCTAShelfContent? {
    if (content.category != MailItemCategory.Package) return null
    return MailboxCTAShelfContent(
        primaryTitle = if (flags.primaryCompleted) "Delivered" else "Log as received",
        ghostTitle = "Not mine",
        primaryLoading = flags.primaryLoading,
        ghostLoading = flags.ghostLoading,
        primaryEnabled = content.ctaEnabled && !flags.primaryCompleted,
    )
}

@Composable
private fun LoadingLayout(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appBorder))
        Column(
            modifier = Modifier.padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Shimmer(width = 120.dp, height = 22.dp, cornerRadius = Radii.pill)
            Shimmer(width = 320.dp, height = 56.dp, cornerRadius = Radii.md)
            Shimmer(width = 320.dp, height = 120.dp, cornerRadius = Radii.lg)
            Shimmer(width = 320.dp, height = 180.dp, cornerRadius = Radii.lg)
        }
        PrimaryButton(title = "Back", onClick = onBack, modifier = Modifier.padding(Spacing.s4))
    }
}

@Composable
private fun ErrorLayout(
    message: String,
    onRetry: () -> Unit,
) {
    EmptyState(
        icon = PantopusIcon.AlertCircle,
        headline = "Couldn't load this item",
        subcopy = message,
        ctaTitle = "Try again",
        onCta = onRetry,
    )
}
