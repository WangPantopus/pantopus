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
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.CommunityBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.CouponBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.GigBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.MailItemPlaceholderBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.MemoryBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.PackageBody
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.CertifiedConfirmGate
import app.pantopus.android.ui.screens.mailbox.item_detail.bodies.components.CertifiedTermsSheet
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Hub → MailboxList → MailboxItemDetail screen. The ViewModel reads the
 * mail id via the nav-backstack [androidx.lifecycle.SavedStateHandle].
 */
@Suppress("CyclomaticComplexMethod")
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
    var showsConfirmGate by remember { mutableStateOf(false) }
    var didAutoPresentConfirmGate by remember { mutableStateOf(false) }

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
                val certifiedPayload = content.payload as? MailboxCategoryPayload.Certified
                val shouldAutoShowConfirmGate =
                    certifiedPayload != null &&
                        content.isUnread &&
                        !content.isArchived &&
                        !certifiedPayload.detail.isAcknowledged &&
                        content.ctaEnabled &&
                        !ctaFlags.primaryCompleted
                LaunchedEffect(shouldAutoShowConfirmGate) {
                    if (shouldAutoShowConfirmGate && !didAutoPresentConfirmGate) {
                        didAutoPresentConfirmGate = true
                        showsConfirmGate = true
                    }
                }
                val showTerms = {
                    val payload = content.payload
                    if (payload is MailboxCategoryPayload.Certified && !payload.detail.termsUrl.isNullOrEmpty()) {
                        termsSheetUrl = payload.detail.termsUrl
                    } else {
                        viewModel.performGhostAction()
                    }
                }
                val primaryAction = {
                    if (certifiedPayload != null && !ackChecked && !certifiedPayload.detail.isAcknowledged) {
                        showsConfirmGate = true
                    } else {
                        viewModel.performPrimaryAction()
                    }
                }
                MailboxItemDetailShell(
                    category = content.category,
                    trust = content.trust,
                    sender = content.sender,
                    aiElf = content.aiElf,
                    keyFacts = content.keyFacts,
                    timeline = if (content.category == MailItemCategory.Package) emptyList() else content.timeline,
                    cta = ctaContent(content, ctaFlags),
                    onBack = onBack,
                    onAIChip = { kind ->
                        // AI suggestion chips are shortcuts for the bottom CTAs.
                        when (kind) {
                            MailboxItemDetailAIChipKind.Primary ->
                                primaryAction()
                            MailboxItemDetailAIChipKind.Secondary ->
                                if (content.category == MailItemCategory.Certified) {
                                    showTerms()
                                } else {
                                    viewModel.performGhostAction()
                                }
                        }
                    },
                    onPrimary = primaryAction,
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
                        ctaFlags = ctaFlags,
                        onViewTerms = showTerms,
                        onAcceptGig = { viewModel.acceptGigBid() },
                        onReceiveAtDoor = { viewModel.performPrimaryAction() },
                    )
                }
                if (showsConfirmGate && certifiedPayload != null) {
                    CertifiedConfirmGate(
                        senderName = content.sender.displayName,
                        referenceNumber = certifiedPayload.detail.referenceNumber,
                        deadlineLabel = formatCertifiedDeadline(certifiedPayload.detail.acknowledgeBy),
                        isSigning = ctaFlags.primaryLoading,
                        onReviewFirst = { showsConfirmGate = false },
                        onSign = {
                            viewModel.setCertifiedAckChecked(true)
                            showsConfirmGate = false
                            viewModel.performPrimaryAction()
                        },
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
): MailboxCTAShelfContent? =
    when (content.category) {
        MailItemCategory.Package ->
            null
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
                    if (flags.primaryCompleted) "Signed ✓" else "Sign for delivery",
                ghostTitle = "View terms",
                primaryLoading = flags.primaryLoading,
                ghostLoading = flags.ghostLoading,
                primaryEnabled =
                    content.ctaEnabled && !flags.primaryCompleted,
            )
        MailItemCategory.Memory -> {
            val saved = (content.payload as? MailboxCategoryPayload.Memory)?.detail?.isSaved ?: false
            MailboxCTAShelfContent(
                primaryTitle = if (saved) "Saved to Vault" else "Save to Vault",
                ghostTitle = "Share",
                primaryLoading = flags.primaryLoading,
                ghostLoading = flags.ghostLoading,
                primaryEnabled = !saved,
            )
        }
        else -> null
    }

@Composable
private fun CategoryBody(
    content: MailboxItemDetailContent,
    ctaFlags: MailboxCTAFlags,
    onViewTerms: () -> Unit,
    onAcceptGig: () -> Unit,
    onReceiveAtDoor: () -> Unit,
) {
    when {
        content.category == MailItemCategory.Package && content.packageInfo != null ->
            PackageBody(
                content = content.packageInfo,
                isReceiveEnabled = content.ctaEnabled && !ctaFlags.primaryCompleted,
                isReceiveLoading = ctaFlags.primaryLoading,
                isReceived = ctaFlags.primaryCompleted,
                onReceiveAtDoor = onReceiveAtDoor,
            )
        content.payload is MailboxCategoryPayload.Coupon ->
            CouponBody(coupon = content.payload.detail)
        content.payload is MailboxCategoryPayload.Booklet ->
            BookletBody(booklet = content.payload.detail)
        content.payload is MailboxCategoryPayload.Certified ->
            CertifiedBody(
                certified = content.payload.detail,
                onViewTerms = onViewTerms,
            )
        content.payload is MailboxCategoryPayload.Community ->
            CommunityBody(
                community = content.payload.detail,
                authorName = content.sender.displayName,
                authorInitials = content.sender.initials,
            )
        content.payload is MailboxCategoryPayload.Gig ->
            GigBody(
                gig = content.payload.detail,
                onAccept = onAcceptGig,
            )
        content.payload is MailboxCategoryPayload.Memory ->
            MemoryBody(
                memory = content.payload.detail,
                isSaved = content.payload.detail.isSaved,
            )
        content.category != MailItemCategory.Package ->
            MailItemPlaceholderBody(category = content.category)
        else -> Unit
    }
}

private fun formatCertifiedDeadline(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    val instant =
        runCatching { Instant.parse(iso) }
            .getOrNull()
            ?: runCatching {
                LocalDate.parse(iso).atStartOfDay(ZoneId.systemDefault()).toInstant()
            }.getOrNull()
            ?: return iso
    return DateTimeFormatter
        .ofPattern("EEE MMM d, yyyy", Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(instant)
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
