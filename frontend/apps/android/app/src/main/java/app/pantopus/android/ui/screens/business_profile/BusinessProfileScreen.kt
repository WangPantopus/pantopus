@file:Suppress(
    "MagicNumber",
    "LongMethod",
    "PackageNaming",
    "TooManyFunctions",
    "LongParameterList",
    "ComplexMethod",
)

package app.pantopus.android.ui.screens.business_profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.AvatarWithIdentityRing
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.IdentityPillar
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.StatusChip
import app.pantopus.android.ui.components.StatusChipVariant
import app.pantopus.android.ui.components.VerifiedBadge
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBar
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBarAction
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusElevations
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.pantopusShadow
import kotlinx.coroutines.delay

/**
 * P1.6 — Typed Business Profile screen. Mirrors the iOS layout: violet
 * hero band, business-pillar identity card, sticky Message/Save/Visit
 * footer, three tabs (Overview / Services / Reviews).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BusinessProfileScreen(
    onBack: () -> Unit,
    onOpenMessages: () -> Unit = {},
    onShare: () -> Unit = {},
    onOpenReport: () -> Unit = {},
    onOpenWebsite: (String) -> Unit = {},
    viewModel: BusinessProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val saveState by viewModel.saveState.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()
    val showOverflow by viewModel.showOverflow.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_000)
            viewModel.dismissToast()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("businessProfile"),
    ) {
        when (val current = state) {
            BusinessProfileUiState.Loading -> LoadingLayout(onBack = onBack)
            BusinessProfileUiState.NotFound -> NotFoundLayout(onBack = onBack, onRetry = viewModel::refresh)
            is BusinessProfileUiState.Error ->
                ErrorLayout(
                    onBack = onBack,
                    message = current.message,
                    onRetry = viewModel::refresh,
                )
            is BusinessProfileUiState.Loaded ->
                BusinessProfileLoadedFrame(
                    content = current.content,
                    selectedTab = selectedTab,
                    saveState = saveState,
                    onBack = onBack,
                    onShare = onShare,
                    onOverflow = { viewModel.setShowOverflow(true) },
                    onSelectTab = viewModel::selectTab,
                    onMessage = onOpenMessages,
                    onSave = viewModel::save,
                    onOpenWebsite = onOpenWebsite,
                )
        }

        toast?.let { message ->
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 100.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appText.copy(alpha = 0.9f))
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            ) {
                Text(
                    text = message,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                )
            }
        }

        if (showOverflow) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowOverflow(false) },
                sheetState = sheetState,
            ) {
                OverflowSheetContent(
                    onShare = {
                        viewModel.setShowOverflow(false)
                        onShare()
                    },
                    onReport = {
                        viewModel.setShowOverflow(false)
                        onOpenReport()
                    },
                    onCancel = { viewModel.setShowOverflow(false) },
                )
            }
        }
    }
}

// MARK: - Loaded layout

/**
 * The loaded-state body extracted as an `internal` composable so the
 * Paparazzi snapshot test can render it without standing up a Hilt VM.
 * Production code reaches it only via [BusinessProfileScreen].
 */
@Composable
internal fun BusinessProfileLoadedFrame(
    content: BusinessProfileContent,
    selectedTab: BusinessProfileTab,
    saveState: BusinessProfileSaveState,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOverflow: () -> Unit,
    onSelectTab: (BusinessProfileTab) -> Unit,
    onMessage: () -> Unit,
    onSave: () -> Unit,
    onOpenWebsite: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            ContentDetailTopBar(
                title = null,
                onBack = onBack,
                action =
                    ContentDetailTopBarAction(
                        icon = PantopusIcon.MoreHorizontal,
                        contentDescription = "More actions",
                        onClick = onOverflow,
                    ),
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
            ) {
                Hero(header = content.header, onShare = onShare)
                Spacer(modifier = Modifier.height(Spacing.s2))
                StatsStrip(stats = content.stats)
                Spacer(modifier = Modifier.height(Spacing.s4))
                TabStrip(selected = selectedTab, onSelect = onSelectTab)
                Spacer(modifier = Modifier.height(Spacing.s3))
                when (selectedTab) {
                    BusinessProfileTab.Overview ->
                        OverviewTab(content = content, onOpenWebsite = onOpenWebsite)
                    BusinessProfileTab.Services -> ServicesTab(services = content.services)
                    BusinessProfileTab.Reviews -> ReviewsTab(reviews = content.reviews)
                }
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        ActionFooter(
            saveState = saveState,
            websiteUrl = content.websiteUrl,
            onMessage = onMessage,
            onSave = onSave,
            onOpenWebsite = onOpenWebsite,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        )
    }
}

// MARK: - Hero

@Composable
private fun Hero(
    header: BusinessProfileHeader,
    onShare: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(PantopusColors.businessBg),
        ) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurface.copy(alpha = 0.92f))
                        .clickable(onClick = onShare)
                        .semantics { contentDescription = "Share" }
                        .testTag("businessProfile.share"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Share,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.appText,
                )
            }
        }
        IdentityCard(
            header = header,
            modifier =
                Modifier
                    .padding(horizontal = Spacing.s4)
                    .padding(top = Spacing.s3),
        )
    }
}

@Composable
private fun IdentityCard(
    header: BusinessProfileHeader,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .pantopusShadow(PantopusElevations.sm, RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Box(
                modifier = Modifier.size(80.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                AvatarWithIdentityRing(
                    name = header.displayName,
                    identity = IdentityPillar.Business,
                    ringProgress = 1f,
                    imageUrl = header.logoUrl,
                    size = 72.dp,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                if (header.isVerified) {
                    VerifiedBadge(size = Radii.xl3)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = header.displayName,
                    style = PantopusTextStyle.h2,
                    color = PantopusColors.appText,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    modifier = Modifier.semantics { heading() },
                )
                if (header.handle != null) {
                    Text(
                        text = "@${header.handle}",
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextSecondary,
                        maxLines = 1,
                    )
                }
                if (!header.locality.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                    ) {
                        PantopusIconImage(
                            icon = PantopusIcon.MapPin,
                            contentDescription = null,
                            size = Radii.lg,
                            tint = PantopusColors.appTextSecondary,
                        )
                        Text(
                            text = header.locality,
                            style = PantopusTextStyle.caption,
                            color = PantopusColors.appTextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        if (header.categoryChips.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                header.categoryChips.forEach { chip ->
                    StatusChip(text = chip, variant = StatusChipVariant.Business)
                }
            }
        }
    }
}

// MARK: - Stats strip

@Composable
private fun StatsStrip(stats: List<BusinessStatCell>) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .clip(RoundedCornerShape(Radii.lg))
                .pantopusShadow(PantopusElevations.sm, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(vertical = Spacing.s3, horizontal = Spacing.s3),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEach { stat ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stat.value,
                    style = PantopusTextStyle.h3,
                    color = PantopusColors.appText,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stat.label.uppercase(),
                    style = PantopusTextStyle.overline,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

// MARK: - Tab strip

@Composable
private fun TabStrip(
    selected: BusinessProfileTab,
    onSelect: (BusinessProfileTab) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4),
    ) {
        BusinessProfileTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 44.dp)
                        .clickable { onSelect(tab) }
                        .testTag("businessProfile.tab.${tab.key}")
                        .semantics { contentDescription = tab.label },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Spacer(modifier = Modifier.height(Spacing.s3))
                Text(
                    text = tab.label,
                    style = PantopusTextStyle.small,
                    color = if (isSelected) PantopusColors.business else PantopusColors.appTextSecondary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                )
                Spacer(modifier = Modifier.height(Spacing.s1))
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (isSelected) PantopusColors.business else PantopusColors.appBg,
                            ),
                )
            }
        }
    }
}

// MARK: - Overview tab

@Composable
private fun OverviewTab(
    content: BusinessProfileContent,
    onOpenWebsite: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .testTag("businessProfile.overview"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        if (!content.about.isNullOrEmpty()) {
            SectionLabel(title = "About")
            Text(
                text = content.about,
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
            )
        }

        if (content.hours.isNotEmpty()) {
            SectionLabel(title = "Hours")
            HoursTable(rows = content.hours)
        }

        content.address?.let { address ->
            SectionLabel(title = "Address")
            AddressBlock(address = address)
        }

        if (content.contact.isNotEmpty()) {
            SectionLabel(title = "Contact")
            ContactList(rows = content.contact, onOpenWebsite = onOpenWebsite)
        }

        val hasNoOverviewContent =
            content.about.isNullOrEmpty() &&
                content.hours.isEmpty() &&
                content.address == null &&
                content.contact.isEmpty()
        if (hasNoOverviewContent) {
            EmptyStateInline(
                icon = PantopusIcon.Building2,
                headline = "Nothing here yet",
                subcopy = "This business hasn't filled in their public profile.",
            )
        }
    }
}

@Composable
private fun SectionLabel(title: String) {
    Text(
        text = title.uppercase(),
        style = PantopusTextStyle.overline,
        color = PantopusColors.appTextSecondary,
        modifier = Modifier.semantics { heading() },
    )
}

@Composable
private fun HoursTable(rows: List<BusinessHoursRow>) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface),
    ) {
        rows.forEachIndexed { idx, row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.dayLabel,
                    style = PantopusTextStyle.body,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.appText,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = row.timeLabel,
                    style = PantopusTextStyle.body,
                    color = if (row.isClosed) PantopusColors.appTextSecondary else PantopusColors.appText,
                )
            }
            if (idx != rows.lastIndex) {
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
        }
    }
}

@Composable
private fun AddressBlock(address: BusinessAddress) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface),
    ) {
        if (address.hasCoordinates) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(96.dp)
                        .background(PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.MapPin,
                    contentDescription = null,
                    size = 28.dp,
                    tint = PantopusColors.business,
                )
            }
        }
        Column(modifier = Modifier.padding(Spacing.s3)) {
            address.lines.forEach { line ->
                Text(
                    text = line,
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appText,
                )
            }
        }
    }
}

@Composable
private fun ContactList(
    rows: List<BusinessContactRow>,
    onOpenWebsite: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface),
    ) {
        rows.forEachIndexed { idx, row ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .clickable(enabled = row.actionUri != null) {
                            row.actionUri?.let(onOpenWebsite)
                        }
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s3)
                        .semantics {
                            contentDescription = "${row.kind.name}: ${row.value}"
                        },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                PantopusIconImage(
                    icon = iconFor(row.kind),
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.business,
                )
                Text(
                    text = row.value,
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appText,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (row.actionUri != null) {
                    PantopusIconImage(
                        icon = PantopusIcon.ChevronRight,
                        contentDescription = null,
                        size = 14.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                }
            }
            if (idx != rows.lastIndex) {
                HorizontalDivider(color = PantopusColors.appBorderSubtle)
            }
        }
    }
}

private fun iconFor(kind: BusinessContactRow.Kind): PantopusIcon =
    when (kind) {
        BusinessContactRow.Kind.Phone -> PantopusIcon.Phone
        BusinessContactRow.Kind.Email -> PantopusIcon.Mail
        BusinessContactRow.Kind.Website -> PantopusIcon.Link
    }

// MARK: - Services tab

@Composable
private fun ServicesTab(services: List<BusinessServiceRow>) {
    if (services.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
                    .testTag("businessProfile.services.empty"),
        ) {
            EmptyStateInline(
                icon = PantopusIcon.Tag,
                headline = "No services listed yet",
                subcopy = "When this business adds services or products, you'll see them here.",
            )
        }
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s4)
                    .testTag("businessProfile.services"),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            services.forEach { ServiceCard(service = it) }
        }
    }
}

@Composable
private fun ServiceCard(service: BusinessServiceRow) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s3)
                .semantics { contentDescription = "${service.name}, ${service.priceLabel}" },
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = service.name,
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = service.priceLabel,
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.business,
            )
        }
        if (!service.detail.isNullOrEmpty()) {
            Text(
                text = service.detail,
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

// MARK: - Reviews tab

@Composable
private fun ReviewsTab(reviews: List<BusinessReviewCard>) {
    if (reviews.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 260.dp)
                    .testTag("businessProfile.reviews.empty"),
        ) {
            EmptyStateInline(
                icon = PantopusIcon.Star,
                headline = "No reviews yet",
                subcopy = "Reviews show up here after a completed gig or purchase.",
            )
        }
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s4)
                    .testTag("businessProfile.reviews"),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            reviews.forEach { ReviewCard(card = it) }
        }
    }
}

@Composable
private fun ReviewCard(card: BusinessReviewCard) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .pantopusShadow(PantopusElevations.sm, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s3)
                .semantics {
                    contentDescription =
                        "${card.reviewerName}, ${card.rating} star review, ${card.timestamp}"
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            AvatarWithIdentityRing(
                name = card.reviewerName,
                identity = IdentityPillar.Personal,
                ringProgress = 1f,
                imageUrl = card.reviewerAvatarUrl,
                size = 40.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.reviewerName,
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { idx ->
                        PantopusIconImage(
                            icon = PantopusIcon.Star,
                            contentDescription = null,
                            size = Radii.lg,
                            tint =
                                if (idx < card.rating) {
                                    PantopusColors.warning
                                } else {
                                    PantopusColors.appTextMuted
                                },
                        )
                    }
                }
            }
            Text(
                text = card.timestamp,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        if (card.body.isNotEmpty()) {
            Text(
                text = card.body,
                style = PantopusTextStyle.small,
                color = PantopusColors.appTextStrong,
            )
        }
    }
}

// MARK: - Sticky CTA

@Composable
private fun ActionFooter(
    saveState: BusinessProfileSaveState,
    websiteUrl: String?,
    onMessage: () -> Unit,
    onSave: () -> Unit,
    onOpenWebsite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl2))
                .pantopusShadow(PantopusElevations.md, RoundedCornerShape(Radii.xl2))
                .background(PantopusColors.appSurface.copy(alpha = 0.97f))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl2))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.business)
                    .clickable(onClick = onMessage)
                    .testTag("businessProfile.message")
                    .semantics { contentDescription = "Message" }
                    .padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.MessageCircle,
                contentDescription = null,
                size = Radii.xl,
                tint = PantopusColors.appTextInverse,
            )
            Spacer(modifier = Modifier.width(Spacing.s1))
            Text(
                text = "Message",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextInverse,
            )
        }

        val saved = saveState is BusinessProfileSaveState.Saved
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .clickable(onClick = onSave)
                    .testTag("businessProfile.save")
                    .semantics { contentDescription = if (saved) "Saved" else "Save" }
                    .padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PantopusIconImage(
                icon = if (saved) PantopusIcon.CheckCircle else PantopusIcon.Bookmark,
                contentDescription = null,
                size = Radii.xl,
                tint = if (saved) PantopusColors.business else PantopusColors.appText,
            )
            Spacer(modifier = Modifier.width(Spacing.s1))
            Text(
                text = if (saved) "Saved" else "Save",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
        }

        if (!websiteUrl.isNullOrEmpty()) {
            Row(
                modifier =
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.businessBg)
                        .clickable { onOpenWebsite(websiteUrl) }
                        .testTag("businessProfile.visit")
                        .semantics { contentDescription = "Visit website" }
                        .padding(horizontal = Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Link,
                    contentDescription = null,
                    size = Radii.xl,
                    tint = PantopusColors.business,
                )
                Spacer(modifier = Modifier.width(Spacing.s1))
                Text(
                    text = "Visit",
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.business,
                )
            }
        }
    }
}

// MARK: - Loading / NotFound / Error / inline empty

@Composable
internal fun LoadingLayout(onBack: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("businessProfile.loading"),
    ) {
        ContentDetailTopBar(title = null, onBack = onBack)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(PantopusColors.businessBg),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Shimmer(width = 72.dp, height = 72.dp, cornerRadius = 36.dp)
            Shimmer(width = 200.dp, height = 24.dp, cornerRadius = Radii.sm)
            Shimmer(width = 140.dp, height = 14.dp, cornerRadius = Radii.sm)
            Shimmer(width = 320.dp, height = 56.dp, cornerRadius = Radii.lg)
            Shimmer(width = 320.dp, height = 120.dp, cornerRadius = Radii.lg)
        }
    }
}

@Composable
internal fun NotFoundLayout(
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("businessProfile.notFound"),
    ) {
        ContentDetailTopBar(title = null, onBack = onBack)
        EmptyState(
            icon = PantopusIcon.Building2,
            headline = "Business not found",
            subcopy = "This business may have moved or unpublished their profile.",
            ctaTitle = "Try again",
            onCta = onRetry,
        )
    }
}

@Composable
private fun ErrorLayout(
    onBack: () -> Unit,
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("businessProfile.error"),
    ) {
        ContentDetailTopBar(title = null, onBack = onBack)
        EmptyState(
            icon = PantopusIcon.AlertCircle,
            headline = "Couldn't load this business",
            subcopy = message,
            ctaTitle = "Try again",
            onCta = onRetry,
        )
    }
}

@Composable
private fun EmptyStateInline(
    icon: PantopusIcon,
    headline: String,
    subcopy: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.businessBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 32.dp,
                tint = PantopusColors.business,
            )
        }
        Text(
            text = headline,
            style = PantopusTextStyle.h3,
            color = PantopusColors.appText,
            textAlign = TextAlign.Center,
        )
        Text(
            text = subcopy,
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun OverflowSheetContent(
    onShare: () -> Unit,
    onReport: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        OverflowRow(label = "Share business", onClick = onShare)
        OverflowRow(label = "Report", destructive = true, onClick = onReport)
        OverflowRow(label = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun OverflowRow(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.body,
            color = if (destructive) PantopusColors.error else PantopusColors.appText,
        )
    }
}
