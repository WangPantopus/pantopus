@file:Suppress("PackageNaming", "LongMethod", "MagicNumber", "TooManyFunctions", "FunctionNaming", "LongParameterList")

package app.pantopus.android.ui.screens.wallet

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import app.pantopus.android.ui.components.BalanceHero
import app.pantopus.android.ui.components.BalanceHeroSplitCell
import app.pantopus.android.ui.components.BalanceHeroTone
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.screens.wallet.components.ActivityRow
import app.pantopus.android.ui.screens.wallet.components.HoldBanner
import app.pantopus.android.ui.screens.wallet.components.PayoutMethodCard
import app.pantopus.android.ui.screens.wallet.components.TaxDocsRow
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusElevations
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.pantopusShadow

/**
 * A10.10 — earnings wallet. Top-level destination distinct from
 * Settings → Payments: this is the earnings-side surface (BalanceHero
 * + recent activity + payout method + tax docs + Withdraw CTA). The
 * Settings → Payments row routes here in P3.2 (replacing the prior
 * `NotYetAvailableView` placeholder); the `pantopus://wallet` deep
 * link lands here too.
 *
 * Two designed frames: populated (happy path) and payout-on-hold
 * (bank verification expired — amber banner, locked withdraw,
 * re-verify CTA in the payout method card).
 */
@Composable
fun WalletScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onWithdraw: () -> Unit = {},
    onManagePayout: () -> Unit = {},
    onReverifyPayout: () -> Unit = {},
    onOpenTaxDocs: () -> Unit = {},
    onSeeAllActivity: () -> Unit = {},
    viewModel: WalletViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    WalletScreenContent(
        state = state,
        onBack = onBack,
        onOpenHistory = onOpenHistory,
        onWithdraw = onWithdraw,
        onManagePayout = onManagePayout,
        onReverifyPayout = onReverifyPayout,
        onOpenTaxDocs = onOpenTaxDocs,
        onSeeAllActivity = onSeeAllActivity,
        onRetry = { viewModel.refresh() },
    )
}

@Composable
internal fun WalletScreenContent(
    state: WalletUiState,
    onBack: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onWithdraw: () -> Unit = {},
    onManagePayout: () -> Unit = {},
    onReverifyPayout: () -> Unit = {},
    onOpenTaxDocs: () -> Unit = {},
    onSeeAllActivity: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("wallet"),
    ) {
        WalletTopBar(onBack = onBack, onOpenHistory = onOpenHistory)
        when (val current = state) {
            WalletUiState.Loading -> LoadingBody()
            is WalletUiState.Populated ->
                WalletBody(
                    content = current.content,
                    onWithdraw = onWithdraw,
                    onManagePayout = onManagePayout,
                    onReverifyPayout = onReverifyPayout,
                    onOpenTaxDocs = onOpenTaxDocs,
                    onSeeAllActivity = onSeeAllActivity,
                )
            is WalletUiState.Hold ->
                WalletBody(
                    content = current.content,
                    onWithdraw = onWithdraw,
                    onManagePayout = onManagePayout,
                    onReverifyPayout = onReverifyPayout,
                    onOpenTaxDocs = onOpenTaxDocs,
                    onSeeAllActivity = onSeeAllActivity,
                )
            is WalletUiState.Error ->
                EmptyState(
                    icon = PantopusIcon.AlertCircle,
                    headline = "Couldn't load wallet",
                    subcopy = current.message,
                    modifier = Modifier.testTag("walletError"),
                    ctaTitle = "Try again",
                    onCta = onRetry,
                )
        }
    }
}

// MARK: - Top bar

@Composable
private fun WalletTopBar(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = "Back",
                tag = "walletBackButton",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "Wallet",
                color = PantopusColors.appText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.15).sp,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                icon = PantopusIcon.History,
                contentDescription = "History",
                tag = "walletHistoryButton",
                onClick = onOpenHistory,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorder),
        )
    }
}

@Composable
private fun IconButton(
    icon: PantopusIcon,
    contentDescription: String,
    tag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Radii.md))
                .clickable(onClick = onClick)
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = contentDescription,
            size = 22.dp,
            tint = PantopusColors.appText,
        )
    }
}

// MARK: - Loaded body

@Composable
private fun WalletBody(
    content: WalletContent,
    onWithdraw: () -> Unit,
    onManagePayout: () -> Unit,
    onReverifyPayout: () -> Unit,
    onOpenTaxDocs: () -> Unit,
    onSeeAllActivity: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4)
                    .padding(top = Spacing.s3, bottom = if (content.isOnHold) 140.dp else 116.dp),
        ) {
            if (content.holdState != null) {
                HoldBanner(
                    headline = content.holdState.bannerHeadline,
                    body = content.holdState.bannerBody,
                )
                Spacer(Modifier.height(Spacing.s3))
            }
            Hero(content)
            SectionOverline(
                title = "Recent activity",
                actionLabel = "See all",
                onAction = onSeeAllActivity,
                actionTag = "walletSeeAllActivity",
            )
            ActivityList(items = content.activity)
            SectionOverline(title = "Payout method")
            PayoutMethodCard(
                method = content.payoutMethod,
                onManage = onManagePayout,
                onReverify = onReverifyPayout,
            )
            SectionOverline(title = "Taxes")
            TaxDocsRow(docs = content.taxDocs, onClick = onOpenTaxDocs)
        }
        WalletBottomBar(
            content = content,
            modifier = Modifier.align(Alignment.BottomCenter),
            onWithdraw = onWithdraw,
        )
    }
}

@Composable
private fun Hero(content: WalletContent) {
    BalanceHero(
        overline = "Available to withdraw",
        amount = content.available,
        currencyCode = "USD",
        split =
            listOf(
                BalanceHeroSplitCell(
                    icon = PantopusIcon.Clock,
                    overline = "Pending",
                    value = content.pending,
                    note = content.pendingMeta,
                ),
                BalanceHeroSplitCell(
                    icon = PantopusIcon.TrendingUp,
                    overline = "This month",
                    value = content.monthValue,
                    note = content.monthMeta,
                ),
            ),
        tone = if (content.isOnHold) BalanceHeroTone.HoldTone else BalanceHeroTone.Default,
        holdHeadline = content.holdState?.heroBannerHeadline,
        holdBody = content.holdState?.heroBannerBody,
    )
}

@Composable
private fun SectionOverline(
    title: String,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
    actionTag: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.s4, bottom = Spacing.s1),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = title.uppercase(),
            color = PantopusColors.appTextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
        )
        Spacer(Modifier.weight(1f))
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                color = PantopusColors.primary600,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .clickable(onClick = onAction)
                        .testTag(actionTag ?: "walletSectionAction"),
            )
        }
    }
}

@Composable
private fun ActivityList(items: List<WalletActivityItem>) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .pantopusShadow(PantopusElevations.sm, shape)
                .clip(shape)
                .background(PantopusColors.appSurface)
                .border(BorderStroke(1.dp, PantopusColors.appBorder), shape),
    ) {
        items.forEachIndexed { index, item ->
            val isNewDay = index == 0 || items[index - 1].day != item.day
            if (isNewDay) {
                if (index != 0) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(PantopusColors.appBorderSubtle),
                    )
                }
                Text(
                    text = item.day.uppercase(),
                    color = PantopusColors.appTextMuted,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.7.sp,
                    modifier =
                        Modifier
                            .padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = if (index == 0) Spacing.s2 else Spacing.s3,
                                bottom = Spacing.s1,
                            ),
                )
            }
            ActivityRow(item = item, isLast = index == items.size - 1)
        }
    }
}

@Composable
private fun WalletBottomBar(
    content: WalletContent,
    modifier: Modifier = Modifier,
    onWithdraw: () -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0f to PantopusColors.appBg.copy(alpha = 0f),
                                0.3f to PantopusColors.appBg.copy(alpha = 0.92f),
                                0.6f to PantopusColors.appBg,
                                1f to PantopusColors.appBg,
                            ),
                    ),
                )
                .padding(horizontal = Spacing.s4)
                .padding(top = 28.dp, bottom = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (content.isOnHold) {
            WithdrawLockedCta(amount = content.available)
            content.holdState?.withdrawFootnote?.let { footnote ->
                Text(
                    text = footnote,
                    color = PantopusColors.appTextSecondary,
                    fontSize = 10.5.sp,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .testTag("walletWithdrawFootnote"),
                )
            }
        } else {
            WithdrawCta(amount = content.available, onClick = onWithdraw)
        }
    }
}

@Composable
private fun WithdrawCta(
    amount: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .pantopusShadow(PantopusElevations.primary, shape)
                .clip(shape)
                .background(PantopusColors.primary600)
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp)
                .semantics { contentDescription = "Withdraw $$amount" }
                .testTag("walletWithdrawButton"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ArrowDownToLine,
                contentDescription = null,
                size = 17.dp,
                strokeWidth = 2.2f,
                tint = Color.White,
            )
            Text(
                text = "Withdraw",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.15).sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "\$$amount",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.15).sp,
        )
    }
}

@Composable
private fun WithdrawLockedCta(amount: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(PantopusColors.appSurfaceSunken)
                .border(BorderStroke(1.dp, PantopusColors.appBorder), shape)
                .padding(horizontal = 18.dp)
                .semantics { contentDescription = "Withdraw locked. $$amount." }
                .testTag("walletWithdrawLockedButton"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Lock,
                contentDescription = null,
                size = 16.dp,
                strokeWidth = 2.2f,
                tint = PantopusColors.appTextMuted,
            )
            Text(
                text = "Withdraw",
                color = PantopusColors.appTextMuted,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.15).sp,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "\$$amount",
            color = PantopusColors.appTextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.15).sp,
        )
    }
}

@Composable
private fun LoadingBody() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3)
                .testTag("walletLoading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        SkeletonBlock(height = 188.dp)
        SkeletonBlock(height = 220.dp)
        SkeletonBlock(height = 66.dp)
        SkeletonBlock(height = 66.dp)
    }
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurfaceSunken),
    )
}
