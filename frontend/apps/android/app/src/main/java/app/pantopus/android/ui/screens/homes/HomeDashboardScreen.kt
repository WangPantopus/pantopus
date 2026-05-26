@file:Suppress("MagicNumber", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.homes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.screens.shared.content_detail.FabCreateCTA
import app.pantopus.android.ui.screens.shared.content_detail.FabSheetAction
import app.pantopus.android.ui.screens.shared.content_detail.GridTabsBody
import app.pantopus.android.ui.screens.shared.content_detail.HomeHeroHeader
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * Hub -> MyHomes -> Home Dashboard screen. The ViewModel reads the home id
 * from the nav-backstack [androidx.lifecycle.SavedStateHandle].
 *
 * `onOpenPlaceholder` is invoked for FAB/quick actions whose dedicated
 * screen isn't built yet (Log package, Add mail, etc.) and receives the
 * human-readable action label.
 */
@Composable
fun HomeDashboardScreen(
    onBack: () -> Unit,
    onInviteOwner: ((String) -> Unit)? = null,
    onClaimOwnership: ((String) -> Unit)? = null,
    onOpenClaimsList: (() -> Unit)? = null,
    onOpenBills: ((String) -> Unit)? = null,
    onOpenPolls: ((String) -> Unit)? = null,
    onOpenPlaceholder: ((String) -> Unit)? = null,
    onOpenPets: ((String) -> Unit)? = null,
    onOpenCalendar: ((String) -> Unit)? = null,
    onOpenDocs: ((String) -> Unit)? = null,
    onOpenEmergency: ((String) -> Unit)? = null,
    onOpenPackages: ((String) -> Unit)? = null,
    onOpenAccessCodes: ((homeId: String, homeName: String?) -> Unit)? = null,
    onOpenTasks: ((String) -> Unit)? = null,
    onOpenMaintenance: ((String) -> Unit)? = null,
    onOpenPropertyDetails: ((String) -> Unit)? = null,
    /** T6.3a / P9 - push to the per-home Members list. When wired, the
     *  "Members" / "Add member" quick-actions navigate to the list
     *  (which owns its own invite FAB) instead of opening the legacy
     *  InviteOwner form. */
    onOpenMembers: ((String) -> Unit)? = null,
    viewModel: HomeDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.load()
        app.pantopus.android.data.analytics.Analytics.track(
            app.pantopus.android.data.analytics.AnalyticsEvent.ScreenHomeDashboardViewed,
        )
    }

    fun actionLabel(actionId: String): String =
        when (actionId) {
            "log_package" -> "Log a package"
            "view_packages" -> "Packages"
            "add_mail" -> "Add mail"
            "add_member" -> "Add member"
            "verify" -> "Verify home"
            "view_bills" -> "Bills"
            "view_docs" -> "Documents"
            "view_emergency" -> "Emergency info"
            "view_polls" -> "Polls"
            "view_tasks" -> "Tasks"
            "view_claims" -> "Claims"
            "view_maintenance" -> "Maintenance"
            "pets" -> "Pets"
            "calendar" -> "Calendar"
            "access_codes" -> "Access codes"
            else -> actionId.replace('_', ' ').replaceFirstChar(Char::uppercase)
        }

    fun openPlaceholder(actionId: String) {
        onOpenPlaceholder?.invoke(actionLabel(actionId))
    }

    fun handleFab(actionId: String) {
        when (actionId) {
            "add_member" -> {
                viewModel.currentHomeId()?.let { homeId ->
                    // Prefer the dedicated Members screen when its host
                    // wired the callback (T6.3a / P9). Falls back to
                    // the legacy InviteOwner form for older hosts.
                    onOpenMembers?.invoke(homeId)
                        ?: onInviteOwner?.invoke(homeId)
                        ?: openPlaceholder(actionId)
                }
            }
            else -> openPlaceholder(actionId)
        }
    }

    fun handleQuickAction(actionId: String) {
        when (actionId) {
            "verify" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onClaimOwnership?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "add_member" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenMembers?.invoke(homeId)
                        ?: onInviteOwner?.invoke(homeId)
                        ?: openPlaceholder(actionId)
                }
            "view_bills" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenBills?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "view_claims" -> onOpenClaimsList?.invoke() ?: openPlaceholder(actionId)
            "view_polls" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenPolls?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "view_maintenance" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenMaintenance?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "pets" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenPets?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "calendar" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenCalendar?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "view_docs" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenDocs?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "view_emergency" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenEmergency?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "view_packages" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenPackages?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "access_codes" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenAccessCodes?.invoke(homeId, viewModel.currentHomeName())
                        ?: openPlaceholder(actionId)
                }
            "view_tasks" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenTasks?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            else -> openPlaceholder(actionId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            HomeDashboardUiState.Loading -> LoadingLayout(onBack = onBack)
            is HomeDashboardUiState.Loaded ->
                DashboardLayout(
                    content = current.content,
                    brandNew = null,
                    selectedTab = selectedTab,
                    onSelectTab = viewModel::selectTab,
                    onBack = onBack,
                    onQuickAction = ::handleQuickAction,
                    onFabAction = ::handleFab,
                    onClaim = {
                        viewModel.currentHomeId()?.let { homeId ->
                            onClaimOwnership?.invoke(homeId) ?: openPlaceholder("verify")
                        }
                    },
                    onViewClaims = { onOpenClaimsList?.invoke() ?: openPlaceholder("verify") },
                    onOpenPropertyDetails = {
                        viewModel.currentHomeId()?.let { homeId ->
                            onOpenPropertyDetails?.invoke(homeId) ?: openPlaceholder("property_details")
                        }
                    },
                )
            is HomeDashboardUiState.Empty ->
                DashboardLayout(
                    content = current.brandNew.content,
                    brandNew = current.brandNew,
                    selectedTab = selectedTab,
                    onSelectTab = viewModel::selectTab,
                    onBack = onBack,
                    onQuickAction = ::handleQuickAction,
                    onFabAction = ::handleFab,
                    onClaim = {
                        viewModel.currentHomeId()?.let { homeId ->
                            onClaimOwnership?.invoke(homeId) ?: openPlaceholder("verify")
                        }
                    },
                    onViewClaims = { onOpenClaimsList?.invoke() ?: openPlaceholder("verify") },
                    onOpenPropertyDetails = {
                        viewModel.currentHomeId()?.let { homeId ->
                            onOpenPropertyDetails?.invoke(homeId) ?: openPlaceholder("property_details")
                        }
                    },
                )
            is HomeDashboardUiState.NeedsAttention ->
                DashboardLayout(
                    content = current.content,
                    brandNew = null,
                    selectedTab = selectedTab,
                    onSelectTab = viewModel::selectTab,
                    onBack = onBack,
                    onQuickAction = ::handleQuickAction,
                    onFabAction = ::handleFab,
                    onClaim = {
                        viewModel.currentHomeId()?.let { homeId ->
                            onClaimOwnership?.invoke(homeId) ?: openPlaceholder("verify")
                        }
                    },
                    onViewClaims = { onOpenClaimsList?.invoke() ?: openPlaceholder("verify") },
                    onOpenPropertyDetails = {
                        viewModel.currentHomeId()?.let { homeId ->
                            onOpenPropertyDetails?.invoke(homeId) ?: openPlaceholder("property_details")
                        }
                    },
                )
            is HomeDashboardUiState.Error ->
                ErrorLayout(message = current.message, onBack = onBack, onRetry = viewModel::refresh)
        }
    }
}

@Composable
fun HomeDashboardScreenContent(
    state: HomeDashboardUiState,
    selectedTab: String = "overview",
    onSelectTab: (String) -> Unit = {},
    onBack: () -> Unit = {},
    onQuickAction: (String) -> Unit = {},
    onFabAction: (String) -> Unit = {},
    onClaim: () -> Unit = {},
    onViewClaims: () -> Unit = {},
    onOpenPropertyDetails: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    when (state) {
        HomeDashboardUiState.Loading -> LoadingLayout(onBack = onBack)
        is HomeDashboardUiState.Loaded ->
            DashboardLayout(
                content = state.content,
                brandNew = null,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onBack = onBack,
                onQuickAction = onQuickAction,
                onFabAction = onFabAction,
                onClaim = onClaim,
                onViewClaims = onViewClaims,
                onOpenPropertyDetails = onOpenPropertyDetails,
            )
        is HomeDashboardUiState.Empty ->
            DashboardLayout(
                content = state.brandNew.content,
                brandNew = state.brandNew,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onBack = onBack,
                onQuickAction = onQuickAction,
                onFabAction = onFabAction,
                onClaim = onClaim,
                onViewClaims = onViewClaims,
                onOpenPropertyDetails = onOpenPropertyDetails,
            )
        is HomeDashboardUiState.NeedsAttention ->
            DashboardLayout(
                content = state.content,
                brandNew = null,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onBack = onBack,
                onQuickAction = onQuickAction,
                onFabAction = onFabAction,
                onClaim = onClaim,
                onViewClaims = onViewClaims,
                onOpenPropertyDetails = onOpenPropertyDetails,
            )
        is HomeDashboardUiState.Error -> ErrorLayout(message = state.message, onBack = onBack, onRetry = onRetry)
    }
}

@Composable
private fun DashboardLayout(
    content: HomeDashboardContent,
    brandNew: HomeDashboardBrandNewContent?,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onBack: () -> Unit,
    onQuickAction: (String) -> Unit,
    onFabAction: (String) -> Unit,
    onClaim: () -> Unit,
    onViewClaims: () -> Unit,
    onOpenPropertyDetails: () -> Unit,
) {
    ContentDetailShell(
        title = "Home",
        onBack = onBack,
        cta = {
            FabCreateCTA(
                actions =
                    listOf(
                        FabSheetAction("log_package", "Log a package", PantopusIcon.ShoppingBag),
                        FabSheetAction("add_member", "Invite owner", PantopusIcon.UserPlus),
                        FabSheetAction("add_mail", "Add mail", PantopusIcon.Mailbox),
                    ),
                onSelect = onFabAction,
            )
        },
        header = {
            HomeHeroHeader(address = content.address, verified = content.verified, stats = content.stats)
        },
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                content.attentionSummary?.let { summary ->
                    NeedsAttentionBanner(summary = summary, onJump = onQuickAction)
                }
                if (!content.isVerifiedOwner) {
                    ClaimOwnershipBanner(onClaim = onClaim, onViewClaims = onViewClaims)
                }
                GridTabsBody(
                    quickActions = content.quickActions,
                    tabs = content.tabs,
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    onQuickAction = onQuickAction,
                ) {
                    if (brandNew != null) {
                        BrandNewHomeSection(brandNew = brandNew, onStep = onQuickAction)
                    } else {
                        OverviewSection(
                            content = content,
                            onOpenEmergency = { onQuickAction("view_emergency") },
                            onOpenPropertyDetails = onOpenPropertyDetails,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun NeedsAttentionBanner(
    summary: HomeDashboardAttentionSummary,
    onJump: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.warningBg)
                .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s4)
                .testTag("homeDashboard_attentionBanner"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.warningBg),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.AlertTriangle,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.warning,
                )
            }
            Text(
                text = summary.message,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
        }
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            summary.chips.forEach { chip ->
                Row(
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(PantopusColors.warningBg)
                            .border(1.dp, PantopusColors.warningLight, RoundedCornerShape(Radii.pill))
                            .clickable { onJump(chip.actionId) }
                            .padding(horizontal = Spacing.s3)
                            .testTag("homeDashboard_attentionChip_${chip.id}")
                            .semantics {
                                role = Role.Button
                                contentDescription = chip.label
                            },
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PantopusIconImage(
                        icon = chip.icon,
                        contentDescription = null,
                        size = 14.dp,
                        tint = PantopusColors.warning,
                    )
                    Text(
                        text = chip.label,
                        style = PantopusTextStyle.caption,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.warning,
                    )
                }
            }
        }
    }
}

@Composable
private fun ClaimOwnershipBanner(
    onClaim: () -> Unit,
    onViewClaims: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(
                    1.dp,
                    PantopusColors.primary600.copy(alpha = 0.4f),
                    RoundedCornerShape(Radii.lg),
                )
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            PantopusIconImage(
                icon = PantopusIcon.ShieldCheck,
                contentDescription = null,
                size = Radii.xl2,
                tint = PantopusColors.primary600,
            )
            Text(
                text = "Are you the owner?",
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
            )
        }
        Text(
            text = "Claim this home to unlock private features for owners.",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.primary600)
                        .clickable(onClick = onClaim)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                        .testTag("homeDashboard_claimCTA"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Claim ownership",
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                )
            }
            Box(
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Radii.pill))
                        .clickable(onClick = onViewClaims)
                        .padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                        .testTag("homeDashboard_viewClaimsCTA"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "View claims",
                    style = PantopusTextStyle.small,
                    color = PantopusColors.primary600,
                )
            }
        }
    }
}

@Composable
private fun BrandNewHomeSection(
    brandNew: HomeDashboardBrandNewContent,
    onStep: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        DashboardCard(title = "Welcome home", accent = PantopusColors.home) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s3), verticalAlignment = Alignment.Top) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(PantopusColors.homeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.PartyPopper,
                        contentDescription = null,
                        size = 22.dp,
                        tint = PantopusColors.home,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1), modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Welcome home",
                        style = PantopusTextStyle.h3,
                        color = PantopusColors.appText,
                    )
                    Text(
                        text = "Set up the essentials for this verified address.",
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
            brandNew.onboardingSteps.forEachIndexed { index, step ->
                OnboardingStepRow(step = step) { onStep(step.actionId) }
                if (index != brandNew.onboardingSteps.lastIndex) {
                    HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
                }
            }
        }
        EmergencyInfoRow(info = brandNew.content.overview.emergency) { onStep("view_emergency") }
    }
}

@Composable
private fun OnboardingStepRow(
    step: HomeDashboardOnboardingStep,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(step.tone.backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = step.icon,
                contentDescription = null,
                size = 18.dp,
                tint = step.tone.color,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(
                text = step.title,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Text(
                text = step.body,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        Box(
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.primary50)
                    .border(1.dp, PantopusColors.primary100, RoundedCornerShape(Radii.md))
                    .clickable(onClick = onClick)
                    .padding(horizontal = Spacing.s3)
                    .testTag("homeDashboard_onboarding_${step.id}")
                    .semantics {
                        role = Role.Button
                        contentDescription = step.title
                    },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step.cta,
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.primary600,
            )
        }
    }
}

@Composable
private fun OverviewSection(
    content: HomeDashboardContent,
    onOpenEmergency: () -> Unit,
    onOpenPropertyDetails: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        DashboardCard(title = "Upcoming", action = "See all", accent = PantopusColors.warning) {
            content.overview.upcoming.forEachIndexed { index, item ->
                TimelineRow(item)
                if (index != content.overview.upcoming.lastIndex) {
                    HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
                }
            }
        }
        DashboardCard(title = "Recent activity", action = "See all") {
            content.overview.activity.forEachIndexed { index, item ->
                ActivityRow(item)
                if (index != content.overview.activity.lastIndex) {
                    HorizontalDivider(color = PantopusColors.appBorderSubtle, thickness = 1.dp)
                }
            }
        }
        EmergencyInfoRow(info = content.overview.emergency, onOpen = onOpenEmergency)
        PropertyDetailsRow(onClick = onOpenPropertyDetails)
    }
}

@Composable
private fun DashboardCard(
    title: String,
    action: String? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
                accent?.let {
                    Box(modifier = Modifier.size(6.dp).clip(RoundedCornerShape(Radii.pill)).background(it))
                }
                Text(
                    text = title.uppercase(),
                    style = PantopusTextStyle.overline,
                    color = PantopusColors.appTextSecondary,
                )
            }
            action?.let {
                Text(
                    text = it,
                    style = PantopusTextStyle.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.primary600,
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s1), content = content)
        Spacer(Modifier.height(Spacing.s2))
    }
}

@Composable
private fun TimelineRow(item: HomeDashboardTimelineItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(item.tone.backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = item.icon, contentDescription = null, size = Radii.xl, tint = item.tone.color)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(
                text = item.title,
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                maxLines = 1,
            )
            Text(
                text = item.subtitle,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                maxLines = 1,
            )
        }
        item.trailing?.let {
            Text(
                text = it,
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun ActivityRow(item: HomeDashboardActivityItem) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s3),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(item.tone.backgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = item.initials,
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = item.tone.color,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(
                text = item.title,
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Text(
                text = "${item.detail} - ${item.time}",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextMuted,
            )
        }
    }
}

@Composable
private fun EmergencyInfoRow(
    info: HomeDashboardEmergencyInfo,
    onOpen: () -> Unit,
) {
    DashboardCard(
        title = info.title,
        accent = if (info.isConfigured) PantopusColors.error else PantopusColors.appTextMuted,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onOpen)
                    .testTag("homeDashboard_emergencyInfoRow")
                    .semantics {
                        role = Role.Button
                        contentDescription = info.body
                    },
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(if (info.isConfigured) PantopusColors.errorBg else PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Siren,
                    contentDescription = null,
                    size = Radii.xl,
                    tint = if (info.isConfigured) PantopusColors.error else PantopusColors.appTextMuted,
                )
            }
            Text(
                text = info.body,
                style = PantopusTextStyle.caption,
                color = if (info.isConfigured) PantopusColors.appTextStrong else PantopusColors.appTextSecondary,
                modifier = Modifier.weight(1f),
            )
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = Radii.xl,
                tint = PantopusColors.appTextMuted,
            )
        }
    }
}

@Composable
private fun PropertyDetailsRow(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, RoundedCornerShape(Radii.lg))
                .clickable(onClick = onClick)
                .padding(Spacing.s4)
                .semantics(
                    mergeDescendants = true,
                ) {
                    role = Role.Button
                    contentDescription = "Property details. County records, beds, baths and verification"
                }
                .testTag("homeDashboard_propertyDetailsRow"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(icon = PantopusIcon.Home, contentDescription = null, size = Radii.xl2, tint = PantopusColors.home)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(
                text = "Property details",
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            Text(
                text = "County records, beds, baths & verification",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = null,
            size = 18.dp,
            tint = PantopusColors.appTextMuted,
        )
    }
}

@Composable
private fun LoadingLayout(onBack: () -> Unit) {
    ContentDetailShell(
        title = "Home",
        onBack = onBack,
        header = {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4)) {
                Shimmer(width = 328.dp, height = 180.dp, cornerRadius = Radii.xl2)
            }
        },
        body = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Shimmer(width = 328.dp, height = 80.dp, cornerRadius = Radii.md)
                Shimmer(width = 200.dp, height = 40.dp, cornerRadius = Radii.sm)
                Shimmer(width = 328.dp, height = 120.dp, cornerRadius = Radii.lg)
            }
        },
    )
}

@Composable
private fun ErrorLayout(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit,
) {
    ContentDetailShell(
        title = "Home",
        onBack = onBack,
        header = { Spacer(Modifier.height(Spacing.s2)) },
        body = {
            Box(modifier = Modifier.fillMaxWidth().height(400.dp)) {
                EmptyState(
                    icon = PantopusIcon.AlertCircle,
                    headline = "Couldn't load this home",
                    subcopy = message,
                    ctaTitle = "Try again",
                    onCta = onRetry,
                )
            }
        },
    )
}
