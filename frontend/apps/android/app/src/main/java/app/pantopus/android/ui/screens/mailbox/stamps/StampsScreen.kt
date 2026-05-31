@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "LongMethod",
    "TooManyFunctions",
    "LongParameterList",
    "UnusedPrivateMember",
)

package app.pantopus.android.ui.screens.mailbox.stamps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.components.PerforatedStamp
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.mailbox.stamps.components.StampBookHero
import app.pantopus.android.ui.screens.mailbox.stamps.components.StampSheet
import app.pantopus.android.ui.screens.mailbox.stamps.components.UsageHistoryCard
import app.pantopus.android.ui.screens.mailbox.stamps.components.WalletRail
import app.pantopus.android.ui.screens.shared.mail_item_detail.AIElfBullet
import app.pantopus.android.ui.screens.shared.mail_item_detail.AIElfStripContent
import app.pantopus.android.ui.screens.shared.mail_item_detail.AIElfStripView
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTheme
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * A17.11 — Stamps (postage wallet). A standalone mailbox screen reusing
 * the A17 archetype chrome: a top nav with a teal category dot, a white
 * card stack (book hero · sheet · wallet rail · usage history · issuer),
 * the sky-gradient "Elf" AI strip, and a sticky "Buy more" dock. Mirrors
 * iOS `StampsView.swift` / `docs/designs/A17/stamps.jsx`.
 */
@Composable
fun StampsScreen(
    onBack: () -> Unit,
    viewModel: StampsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configureNavigation(onBack = onBack)
        viewModel.load()
    }
    // Track the resolved screen view once (not the transient loading frame).
    LaunchedEffect(state.analyticsTag) {
        if (state !is StampsUiState.Loading) {
            Analytics.track(AnalyticsEvent.ScreenStampsViewed(state.analyticsTag))
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("stamps"),
    ) {
        StampsNav(onBack = { viewModel.tapBack() })
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (val current = state) {
                is StampsUiState.Loading -> StampsLoadingBody()
                is StampsUiState.Loaded ->
                    StampsPopulatedBody(content = current.content, onBuyMore = { viewModel.buyMore() })
                is StampsUiState.Empty ->
                    StampsEmptyBody(content = current.content, onBuy = { viewModel.purchaseStarterBook() })
                is StampsUiState.Error ->
                    StampsErrorBody(message = current.message, onRetry = { viewModel.refresh() })
            }
        }
    }
}

// MARK: - Top nav

@Composable
private fun StampsNav(onBack: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(PantopusColors.appSurface),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.semantics(mergeDescendants = true) {}.testTag("stampsNavEyebrow"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(StampInk.Local.color))
            Text(
                text = "STAMPS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = PantopusColors.appTextStrong,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier =
                    Modifier
                        .clickable(onClick = onBack)
                        .padding(horizontal = Spacing.s1)
                        .testTag("stampsNavBack"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ChevronLeft,
                    contentDescription = "Back to Mailbox",
                    size = 22.dp,
                    tint = PantopusColors.primary600,
                )
                Text(text = "Mailbox", fontSize = 15.sp, color = PantopusColors.primary600)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                NavIcon(icon = PantopusIcon.Gift, label = "Gift a stamp", tag = "stampsNavGift")
                NavIcon(icon = PantopusIcon.MoreHorizontal, label = "More actions", tag = "stampsNavMore")
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorderSubtle),
        )
    }
}

@Composable
private fun NavIcon(
    icon: PantopusIcon,
    label: String,
    tag: String,
) {
    Box(
        modifier =
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(PantopusColors.appSurfaceSunken)
                .clickable {}
                .testTag(tag),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = icon, contentDescription = label, size = 18.dp, tint = PantopusColors.appTextStrong)
    }
}

// MARK: - Populated body

@Composable
private fun StampsPopulatedBody(
    content: StampsContent,
    onBuyMore: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = Spacing.s3, bottom = Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Padded { StampsItemHeader(category = content.categoryLabel, time = content.timeLabel) }
            Padded { StampBookHero(book = content.book) }
            Padded { AIElfStripView(content = elfContent(content)) }
            Padded { StampSheet(book = content.book) }
            WalletRail(stamps = content.wallet, summary = content.walletSummary)
            Padded { UsageHistoryCard(usage = content.usage, window = content.usageWindow) }
            Padded { StampsIssuerCard(issuer = content.issuer) }
        }
        StampsDock(onBuyMore = onBuyMore)
    }
}

/** Horizontal-gutter wrapper for a card-stack section (rails bleed full-width). */
@Composable
private fun Padded(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4)) { content() }
}

private fun elfContent(content: StampsContent): AIElfStripContent =
    AIElfStripContent(
        headline = content.elfHeadline,
        summary = content.elfSummary,
        bullets = content.insights.map { AIElfBullet(id = it.id, icon = it.icon, label = it.label, text = it.text) },
    )

// MARK: - Item header (trust · category · time)

@Composable
private fun StampsItemHeader(
    category: String,
    time: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("stampsItemHeader"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        TrustChip()
        CategoryChip(label = category)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = time,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun TrustChip() {
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(PantopusColors.successBg)
                .padding(start = 7.dp, end = Spacing.s2, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.ShieldCheck,
            contentDescription = null,
            size = 11.dp,
            tint = PantopusColors.success,
        )
        Text(text = "Verified", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PantopusColors.success)
    }
}

@Composable
private fun CategoryChip(label: String) {
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(PantopusColors.appSurfaceSunken)
                .padding(horizontal = Spacing.s2, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(StampInk.Local.color))
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextStrong)
    }
}

// MARK: - Issuer card

@Composable
private fun StampsIssuerCard(issuer: StampIssuer) {
    StampCard(
        modifier =
            Modifier.semantics {
                contentDescription = "From ${issuer.name}. ${issuer.dept}. ${issuer.kindLabel}."
            }.testTag("stampsIssuer"),
    ) {
        StampSectionLabel(title = "From")
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            IssuerAvatar(initials = issuer.initials)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = issuer.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
                Text(text = issuer.dept, fontSize = 12.sp, color = PantopusColors.appTextSecondary)
                Row(
                    modifier = Modifier.padding(top = Spacing.s1),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IssuerChip(icon = PantopusIcon.Stamp, text = issuer.kindLabel, tint = StampInk.Local.color)
                    ProofChip(text = issuer.proofLabel)
                }
            }
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.appTextMuted,
            )
        }
    }
}

@Composable
private fun IssuerAvatar(initials: String) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier =
                Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(
                        Brush.linearGradient(listOf(StampInk.Local.color, StampPalette.issuerDeep)),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = initials, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Box(
            modifier =
                Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.appSurface)
                    .padding(1.5.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.success),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.Check, contentDescription = null, size = 9.dp, tint = Color.White)
        }
    }
}

@Composable
private fun IssuerChip(
    icon: PantopusIcon,
    text: String,
    tint: Color,
) {
    Row(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.12f))
                .padding(horizontal = Spacing.s1, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 9.dp, tint = tint)
        Text(text = text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun ProofChip(text: String) {
    Text(
        text = text,
        modifier =
            Modifier
                .clip(CircleShape)
                .background(PantopusColors.successBg)
                .padding(horizontal = Spacing.s1, vertical = 2.dp),
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = PantopusColors.success,
    )
}

// MARK: - Sticky dock

@Composable
private fun StampsDock(onBuyMore: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
        Column(
            modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, RoundedCornerShape(Radii.lg))
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.primary600)
                        .clickable(onClick = onBuyMore)
                        .padding(vertical = 14.dp)
                        .testTag("stampsBuyMore"),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PantopusIconImage(icon = PantopusIcon.Plus, contentDescription = null, size = 16.dp, tint = Color.White)
                Spacer(modifier = Modifier.width(Spacing.s2))
                Text(text = "Buy more stamps", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                ActionChip(icon = PantopusIcon.ArrowsRepeat, label = "Auto-refill", modifier = Modifier.weight(1f))
                ActionChip(icon = PantopusIcon.Gift, label = "Gift", modifier = Modifier.weight(1f))
                ActionChip(icon = PantopusIcon.Send, label = "Send mail", modifier = Modifier.weight(1f))
                ActionChip(icon = PantopusIcon.Archive, label = "Archive", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ActionChip(
    icon: PantopusIcon,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable {}
                .padding(vertical = 10.dp)
                .testTag("stampsAction.$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 17.dp, tint = PantopusColors.appTextSecondary)
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// MARK: - Empty body

@Composable
private fun StampsEmptyBody(
    content: StampsEmptyContent,
    onBuy: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s4)
                .padding(top = Spacing.s4, bottom = Spacing.s8)
                .testTag("stampsEmpty"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyHero(content = content, onBuy = onBuy)
        StarterBookCard(book = content.starterBook, onGetBook = onBuy)
        HowItWorks(title = content.howItWorksTitle, body = content.howItWorksBody)
    }
}

@Composable
private fun EmptyHero(
    content: StampsEmptyContent,
    onBuy: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier.padding(top = Spacing.s6, bottom = Spacing.s5),
            contentAlignment = Alignment.BottomEnd,
        ) {
            PerforatedStamp(ink = StampInk.Local.color, width = 108.dp, height = 138.dp)
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .shadow(6.dp, CircleShape)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Plus,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.appTextMuted,
                )
            }
        }
        Text(
            text = content.headline,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.padding(bottom = Spacing.s1),
        )
        Text(
            text = content.body,
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = Spacing.s5),
        )
        Row(
            modifier =
                Modifier
                    .shadow(6.dp, RoundedCornerShape(Radii.lg))
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onBuy)
                    .padding(horizontal = Spacing.s5, vertical = Spacing.s3)
                    .testTag("stampsEmptyBuy"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(text = content.buyLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            PantopusIconImage(
                icon = PantopusIcon.ArrowRight,
                contentDescription = null,
                size = 15.dp,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun StarterBookCard(
    book: StampStarterBook,
    onGetBook: () -> Unit,
) {
    StampCard(noPad = true, modifier = Modifier.testTag("stampsStarterBook")) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            PerforatedStamp(
                ink = StampInk.Local.color,
                width = 58.dp,
                height = 74.dp,
                toothRadius = 3.dp,
                toothGap = 9.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = book.title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
                Text(text = book.detail, fontSize = 11.5.sp, color = PantopusColors.appTextSecondary)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(
                    text = book.priceLabel,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = PantopusColors.appText,
                )
                Text(
                    text = "Get book",
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(StampInk.Local.color)
                            .clickable(onClick = onGetBook)
                            .padding(horizontal = Spacing.s3, vertical = 5.dp)
                            .testTag("stampsStarterGetBook"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun HowItWorks(
    title: String,
    body: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Info,
                contentDescription = null,
                size = 15.dp,
                tint = PantopusColors.primary700,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            Text(text = body, fontSize = 11.5.sp, color = PantopusColors.appTextSecondary, lineHeight = 17.sp)
        }
    }
}

// MARK: - Loading body

@Composable
private fun StampsLoadingBody() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.s4)
                .padding(top = Spacing.s3)
                .testTag("stampsLoading"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Shimmer(width = 160.dp, height = 22.dp, cornerRadius = Radii.pill)
        Shimmer(width = 360.dp, height = 160.dp, cornerRadius = Radii.xl)
        Shimmer(width = 360.dp, height = 120.dp, cornerRadius = Radii.xl)
        Shimmer(width = 360.dp, height = 220.dp, cornerRadius = Radii.xl)
        Shimmer(width = 360.dp, height = 150.dp, cornerRadius = Radii.xl)
    }
}

// MARK: - Error body

@Composable
private fun StampsErrorBody(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.s6)
                .testTag("stampsError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3, Alignment.CenterVertically),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            tint = PantopusColors.error,
        )
        Text(
            text = "Couldn't load your stamps",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = message, fontSize = 13.sp, color = PantopusColors.appTextSecondary, textAlign = TextAlign.Center)
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = Spacing.s5, vertical = Spacing.s3)
                    .testTag("stampsRetry"),
        ) {
            Text(text = "Try again", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

// MARK: - Shared card chrome

/** White rounded card with a hairline border — the A17 card-stack unit. */
@Composable
internal fun StampCard(
    modifier: Modifier = Modifier,
    noPad: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .shadow(1.dp, RoundedCornerShape(Radii.xl))
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(if (noPad) 0.dp else 14.dp),
        content = content,
    )
}

/** Uppercase overline used at the head of a card, with a trailing slot. */
@Composable
internal fun StampSectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(bottom = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title.uppercase(),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.7.sp,
            color = PantopusColors.appTextSecondary,
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}

// MARK: - VM-free frames (paparazzi / previews)

/** Populated frame without a view-model — for snapshot tests + previews. */
@Composable
internal fun StampsPopulatedFrame(content: StampsContent) {
    Column(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        StampsNav(onBack = {})
        StampsPopulatedBody(content = content, onBuyMore = {})
    }
}

/** Empty frame without a view-model — for snapshot tests + previews. */
@Composable
internal fun StampsEmptyFrame(content: StampsEmptyContent) {
    Column(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        StampsNav(onBack = {})
        StampsEmptyBody(content = content, onBuy = {})
    }
}

private val StampsUiState.analyticsTag: String
    get() =
        when (this) {
            is StampsUiState.Loading -> "loading"
            is StampsUiState.Loaded -> "populated"
            is StampsUiState.Empty -> "empty"
            is StampsUiState.Error -> "error"
        }

// MARK: - Previews

@Preview(showBackground = true, widthDp = 390, heightDp = 1500, name = "A17.11 · populated")
@Composable
private fun StampsPopulatedPreview() {
    PantopusTheme { StampsPopulatedFrame(content = StampsSampleData.populated) }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 900, name = "A17.11 · empty")
@Composable
private fun StampsEmptyPreview() {
    PantopusTheme { StampsEmptyFrame(content = StampsSampleData.empty) }
}
