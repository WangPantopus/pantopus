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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.mailbox.v2.MailboxCategoryPayload
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.BookletBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.CertifiedBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.CouponBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.MailItemPlaceholderBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.PackageBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.CertifiedTermsSheet
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
    onOpenSenderProfile: ((String) -> Unit)? = null,
    viewModel: MailboxItemDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctaFlags by viewModel.ctaFlags.collectAsStateWithLifecycle()
    val ackChecked by viewModel.certifiedAckChecked.collectAsStateWithLifecycle()
    var termsSheetUrl by remember { mutableStateOf<String?>(null) }

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
                val showTerms = {
                    val payload = content.payload
                    if (payload is MailboxCategoryPayload.Certified && !payload.detail.termsUrl.isNullOrEmpty()) {
                        termsSheetUrl = payload.detail.termsUrl
                    } else {
                        viewModel.performGhostAction()
                    }
                }
                MailboxItemDetailShell(
                    category = content.category,
                    trust = content.trust,
                    sender = content.sender,
                    aiElf = content.aiElf,
                    keyFacts = content.keyFacts,
                    timeline = content.timeline,
                    cta = ctaContent(content, ctaFlags, ackChecked),
                    onBack = onBack,
                    onPrimary = { viewModel.performPrimaryAction() },
                    onGhost = {
                        if (content.category == MailItemCategory.Certified) {
                            showTerms()
                        } else {
                            viewModel.performGhostAction()
                        }
                    },
                    onSenderAvatarTap = onOpenSenderProfile,
                ) {
                    CategoryBody(
                        content = content,
                        ackChecked = ackChecked,
                        onAckChange = { viewModel.setCertifiedAckChecked(it) },
                        onViewTerms = showTerms,
                    )
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

    termsSheetUrl?.let { url ->
        CertifiedTermsSheet(
            termsUrl = url,
            onDismiss = { termsSheetUrl = null },
        )
    }
}

private fun ctaContent(
    content: MailboxItemDetailContent,
    flags: MailboxCTAFlags,
    ackChecked: Boolean,
): MailboxCTAShelfContent? =
    when (content.category) {
        MailItemCategory.Package ->
            MailboxCTAShelfContent(
                primaryTitle = if (flags.primaryCompleted) "Delivered" else "Log as received",
                ghostTitle = "Not mine",
                primaryLoading = flags.primaryLoading,
                ghostLoading = flags.ghostLoading,
                primaryEnabled = content.ctaEnabled && !flags.primaryCompleted,
            )
        MailItemCategory.Coupon ->
            MailboxCTAShelfContent(
                primaryTitle =
                    if (flags.primaryCompleted) "Added to wallet ✓" else "Add to wallet",
                ghostTitle = "Save for later",
                primaryLoading = flags.primaryLoading,
                ghostLoading = flags.ghostLoading,
                primaryEnabled = content.ctaEnabled && !flags.primaryCompleted,
            )
        MailItemCategory.Booklet ->
            MailboxCTAShelfContent(
                primaryTitle = "Save to library",
                ghostTitle = null,
                primaryLoading = flags.primaryLoading,
                ghostLoading = false,
                primaryEnabled = content.ctaEnabled,
            )
        MailItemCategory.Certified ->
            MailboxCTAShelfContent(
                primaryTitle =
                    if (flags.primaryCompleted) "Acknowledged ✓" else "Acknowledge receipt",
                ghostTitle = "View terms",
                primaryLoading = flags.primaryLoading,
                ghostLoading = flags.ghostLoading,
                primaryEnabled =
                    content.ctaEnabled && ackChecked && !flags.primaryCompleted,
            )
        else -> null
    }

@Composable
private fun CategoryBody(
    content: MailboxItemDetailContent,
    ackChecked: Boolean,
    onAckChange: (Boolean) -> Unit,
    onViewTerms: () -> Unit,
) {
    when {
        content.category == MailItemCategory.Package && content.packageInfo != null ->
            PackageBody(
                carrier = content.packageInfo.carrier,
                etaLine = content.packageInfo.etaLine,
            )
        content.payload is MailboxCategoryPayload.Coupon ->
            CouponBody(coupon = content.payload.detail)
        content.payload is MailboxCategoryPayload.Booklet ->
            BookletBody(booklet = content.payload.detail)
        content.payload is MailboxCategoryPayload.Certified ->
            CertifiedBody(
                certified = content.payload.detail,
                isAcknowledged = ackChecked,
                onAcknowledgedChange = onAckChange,
                onViewTerms = onViewTerms,
            )
        content.category != MailItemCategory.Package ->
            MailItemPlaceholderBody(category = content.category)
        else -> Unit
    }
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
