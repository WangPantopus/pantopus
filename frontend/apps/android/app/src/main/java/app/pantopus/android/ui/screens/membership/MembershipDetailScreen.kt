@file:Suppress("PackageNaming", "LongMethod", "MagicNumber", "TooManyFunctions", "LongParameterList")

package app.pantopus.android.ui.screens.membership

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PersonaCard
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.StatusChip
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage

@Composable
fun MembershipDetailScreen(
    onBack: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenPersona: () -> Unit = {},
    onChangeTier: () -> Unit = {},
    onUpdatePayment: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRequestRefund: () -> Unit = {},
    viewModel: MembershipDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("membershipDetail"),
    ) {
        TopBar(onBack = onBack, onShare = onShare)
        when (val current = state) {
            is MembershipDetailUiState.Loading -> LoadingFrame()
            is MembershipDetailUiState.Error ->
                ErrorFrame(message = current.message, onRetry = viewModel::load)
            is MembershipDetailUiState.Populated ->
                MembershipLoadedContent(
                    content = current.content,
                    slaMissed = false,
                    onOpenPersona = onOpenPersona,
                    onChangeTier = onChangeTier,
                    onUpdatePayment = onUpdatePayment,
                    onCancel = onCancel,
                    onRequestRefund = onRequestRefund,
                    onDismissSla = viewModel::dismissSlaAlert,
                )
            is MembershipDetailUiState.SlaMissed ->
                MembershipLoadedContent(
                    content = current.content,
                    slaMissed = true,
                    onOpenPersona = onOpenPersona,
                    onChangeTier = onChangeTier,
                    onUpdatePayment = onUpdatePayment,
                    onCancel = onCancel,
                    onRequestRefund = onRequestRefund,
                    onDismissSla = viewModel::dismissSlaAlert,
                )
        }
    }
}

// MARK: - Top bar

@Composable
private fun TopBar(
    onBack: () -> Unit,
    onShare: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("membershipDetailBackButton"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = "Back",
                    size = 22.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appText,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Membership",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onShare)
                        .testTag("membershipDetailShareButton"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Share,
                    contentDescription = "Share membership",
                    size = 20.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appText,
                )
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    }
}

// MARK: - States

@Composable
internal fun LoadingFrame() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("membershipDetailLoading"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Shimmer(width = 360.dp, height = 64.dp, cornerRadius = 12.dp)
        Shimmer(width = 360.dp, height = 184.dp, cornerRadius = 16.dp)
        Shimmer(width = 360.dp, height = 176.dp, cornerRadius = 12.dp)
        Shimmer(width = 360.dp, height = 50.dp, cornerRadius = 12.dp)
    }
}

@Composable
internal fun ErrorFrame(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("membershipDetailError"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            strokeWidth = 2f,
            tint = PantopusColors.error,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Couldn't load membership",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            title = "Try again",
            onClick = onRetry,
            modifier = Modifier.testTag("membershipDetailRetry"),
        )
    }
}

// MARK: - Loaded

@Composable
internal fun MembershipLoadedContent(
    content: MembershipDetailContent,
    slaMissed: Boolean,
    onOpenPersona: () -> Unit = {},
    onChangeTier: () -> Unit = {},
    onUpdatePayment: () -> Unit = {},
    onCancel: () -> Unit = {},
    onRequestRefund: () -> Unit = {},
    onDismissSla: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("membershipDetailContent"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content.slaAlert?.let { alert ->
            SlaBanner(alert = alert, onRequestRefund = onRequestRefund, onDismiss = onDismissSla)
        }
        LabeledSection(title = "You support") {
            PersonaCard(
                name = content.persona.name,
                initials = content.persona.initials,
                subtitle = content.persona.subtitle,
                pillar = content.persona.pillar,
                pillarLabel = content.persona.pillarLabel,
                verified = content.persona.verified,
                testTag = "membershipDetailPersona",
                onClick = onOpenPersona,
            )
        }
        LabeledSection(title = "Your membership") {
            TierCard(content = content, slaMissed = slaMissed, onUpdatePayment = onUpdatePayment)
        }
        LabeledSection(title = "What you get") {
            BenefitsCard(benefits = content.benefits)
        }
        ChangeTierButton(onClick = onChangeTier)
        CancelBlock(onCancel = onCancel)
        PolicyFootnote(text = content.policyFootnote)
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun LabeledSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextSecondary,
            letterSpacing = 0.7.sp,
            modifier = Modifier.semantics { heading() },
        )
        content()
    }
}

// MARK: - SLA banner

@Composable
private fun SlaBanner(
    alert: MembershipSLAAlert,
    onRequestRefund: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.warningBg)
                .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("membershipDetailSLABanner"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                modifier =
                    Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PantopusColors.warning),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.AlertTriangle,
                    contentDescription = null,
                    size = 17.dp,
                    strokeWidth = 2.3f,
                    tint = PantopusColors.appTextInverse,
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = alert.title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.warning,
                )
                Text(
                    text = alert.message,
                    fontSize = 12.sp,
                    color = PantopusColors.appTextStrong,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(PantopusColors.error)
                        .clickable(onClick = onRequestRefund)
                        .testTag("membershipDetailRefundButton")
                        .semantics { contentDescription = alert.refundCtaLabel },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.HandCoins,
                        contentDescription = null,
                        size = 13.dp,
                        strokeWidth = 2f,
                        tint = PantopusColors.appTextInverse,
                    )
                    Text(
                        text = alert.refundCtaLabel,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appTextInverse,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, PantopusColors.warning, RoundedCornerShape(8.dp))
                        .clickable(onClick = onDismiss)
                        .testTag("membershipDetailSnoozeButton")
                        .semantics { contentDescription = alert.dismissCtaLabel },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = alert.dismissCtaLabel,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.warning,
                )
            }
        }
    }
}

// MARK: - Tier card

@Composable
private fun TierCard(
    content: MembershipDetailContent,
    slaMissed: Boolean,
    onUpdatePayment: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(16.dp))
                .testTag("membershipDetailTierCard"),
    ) {
        TierStrip(content)
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
        TierInfoRow(
            icon = PantopusIcon.CalendarClock,
            iconBackground = PantopusColors.primary50,
            iconForeground = PantopusColors.primary600,
            label = "Next renewal",
            value = content.renewalLabel,
            valueColor = if (slaMissed) PantopusColors.warning else PantopusColors.appText,
            rowTestTag = "membershipDetailRenewalRow",
        )
        Box(
            modifier =
                Modifier
                    .padding(start = 16.dp)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorderSubtle),
        )
        TierInfoRow(
            icon = PantopusIcon.Wallet,
            iconBackground = PantopusColors.appSurfaceSunken,
            iconForeground = PantopusColors.appTextStrong,
            label = "Payment",
            value = content.paymentLabel,
            valueColor = PantopusColors.appText,
            trailingLabel = "Update",
            onClick = onUpdatePayment,
            rowTestTag = "membershipDetailPaymentRow",
        )
    }
}

@Composable
private fun TierStrip(content: MembershipDetailContent) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(content.tier.bgColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics {
                    contentDescription =
                        "Your tier ${content.tier.displayName}, " +
                        "${content.tier.ladderRank} of ${MembershipTier.ladderTotal}, " +
                        "${content.priceLabel} per ${content.periodLabel}"
                },
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "YOUR TIER",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextSecondary,
                letterSpacing = 0.6.sp,
            )
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = content.tier.displayName,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = content.tier.fgColor,
                )
                LadderPill(content.tier)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = content.priceLabel,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = PantopusColors.appText,
            )
            Text(
                text = "/ ${content.periodLabel}",
                fontSize = 11.sp,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun LadderPill(tier: MembershipTier) {
    Row(
        modifier =
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(999.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Crown,
            contentDescription = null,
            size = 10.dp,
            strokeWidth = 2.2f,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = "${tier.ladderRank} of ${MembershipTier.ladderTotal}",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextSecondary,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun TierInfoRow(
    icon: PantopusIcon,
    iconBackground: Color,
    iconForeground: Color,
    label: String,
    value: String,
    valueColor: Color,
    rowTestTag: String,
    trailingLabel: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag(rowTestTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBackground),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 15.dp,
                strokeWidth = 2f,
                tint = iconForeground,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(text = label, fontSize = 12.sp, color = PantopusColors.appTextSecondary)
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
        }
        if (trailingLabel != null) {
            Text(
                text = trailingLabel,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.primary600,
            )
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = 14.dp,
                strokeWidth = 2f,
                tint = PantopusColors.appTextMuted,
            )
        }
    }
}

// MARK: - Benefits

@Composable
private fun BenefitsCard(benefits: List<MembershipBenefit>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .testTag("membershipDetailBenefits"),
    ) {
        benefits.forEachIndexed { index, benefit ->
            BenefitRow(benefit)
            if (index < benefits.lastIndex) {
                Box(
                    modifier =
                        Modifier
                            .padding(start = 50.dp)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(PantopusColors.appBorderSubtle),
                )
            }
        }
    }
}

@Composable
private fun BenefitRow(benefit: MembershipBenefit) {
    val description =
        buildString {
            append(benefit.label)
            append(". ")
            append(benefit.meta)
            benefit.slaBadge?.let {
                append(". ")
                append(it)
            }
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .testTag("membershipDetailBenefit_${benefit.id}")
                .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PantopusColors.successBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = null,
                size = 14.dp,
                strokeWidth = 2.5f,
                tint = PantopusColors.success,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PantopusIconImage(
                    icon = benefit.icon,
                    contentDescription = null,
                    size = 13.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(
                    text = benefit.label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                benefit.slaBadge?.let { StatusChip(text = it, variant = StatusChipVariant.Success) }
            }
            Text(text = benefit.meta, fontSize = 10.5.sp, color = PantopusColors.appTextSecondary)
        }
    }
}

// MARK: - Change tier + cancel

@Composable
private fun ChangeTierButton(onClick: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.primary600)
                .clickable(onClick = onClick)
                .testTag("membershipDetailChangeTier")
                .semantics { contentDescription = "Change tier" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ArrowDownUp,
                contentDescription = null,
                size = 17.dp,
                strokeWidth = 2f,
                tint = PantopusColors.appTextInverse,
            )
            Text(
                text = "Change tier",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun CancelBlock(onCancel: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Single-tap cancel by Pantopus policy — no confirm dialog, no
        // retention questions, no last-second offers.
        Row(
            modifier =
                Modifier
                    .heightIn(min = 44.dp)
                    .clickable(onClick = onCancel)
                    .testTag("membershipDetailCancel")
                    .semantics { contentDescription = "Cancel membership" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = null,
                size = 13.dp,
                strokeWidth = 2.4f,
                tint = PantopusColors.error,
            )
            Text(
                text = "Cancel membership",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.error,
            )
        }
        Text(
            text = "Single-tap cancel. No retention questions, no last-second offers.",
            fontSize = 10.5.sp,
            color = PantopusColors.appTextMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 260.dp),
        )
        Text(
            text = "— Pantopus policy",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun PolicyFootnote(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        color = PantopusColors.appTextMuted,
        textAlign = TextAlign.Center,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("membershipDetailPolicyFootnote"),
    )
}
