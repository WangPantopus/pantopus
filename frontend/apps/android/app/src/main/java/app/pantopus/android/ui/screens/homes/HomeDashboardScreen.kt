@file:Suppress("MagicNumber", "LongMethod", "LongParameterList", "CyclomaticComplexMethod")

package app.pantopus.android.ui.screens.homes

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.KeyFactRow
import app.pantopus.android.ui.components.KeyFactsPanel
import app.pantopus.android.ui.components.SectionHeader
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
 * Hub → MyHomes → Home Dashboard screen. The ViewModel reads the home id
 * from the nav-backstack [androidx.lifecycle.SavedStateHandle].
 *
 * `onOpenPlaceholder` is invoked for FAB/quick actions whose dedicated
 * screen isn't built yet (Log package, Add mail, …) — receives the
 * human-readable action label.
 */
@Composable
fun HomeDashboardScreen(
    onBack: () -> Unit,
    onInviteOwner: ((String) -> Unit)? = null,
    onClaimOwnership: ((String) -> Unit)? = null,
    onOpenClaimsList: (() -> Unit)? = null,
    onOpenBills: ((String) -> Unit)? = null,
    onOpenPlaceholder: ((String) -> Unit)? = null,
    onOpenPets: ((String) -> Unit)? = null,
    onOpenAccessCodes: ((homeId: String, homeName: String?) -> Unit)? = null,
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
            "add_mail" -> "Add mail"
            "add_member" -> "Add member"
            "verify" -> "Verify home"
            "view_bills" -> "Bills"
            "pets" -> "Pets"
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
                    onInviteOwner?.invoke(homeId) ?: openPlaceholder(actionId)
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
                    onInviteOwner?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "view_bills" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenBills?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "pets" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenPets?.invoke(homeId) ?: openPlaceholder(actionId)
                }
            "access_codes" ->
                viewModel.currentHomeId()?.let { homeId ->
                    onOpenAccessCodes?.invoke(homeId, viewModel.currentHomeName())
                        ?: openPlaceholder(actionId)
                }
            else -> openPlaceholder(actionId)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val current = state) {
            HomeDashboardUiState.Loading -> LoadingLayout(onBack = onBack)
            is HomeDashboardUiState.Loaded ->
                LoadedLayout(
                    content = current.content,
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
                )
            is HomeDashboardUiState.Error ->
                ErrorLayout(message = current.message, onBack = onBack, onRetry = viewModel::refresh)
        }
    }
}

@Composable
private fun LoadedLayout(
    content: HomeDashboardContent,
    selectedTab: String,
    onSelectTab: (String) -> Unit,
    onBack: () -> Unit,
    onQuickAction: (String) -> Unit,
    onFabAction: (String) -> Unit,
    onClaim: () -> Unit,
    onViewClaims: () -> Unit,
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
                    OverviewSection(content = content)
                }
            }
        },
    )
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
                size = 20.dp,
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
private fun OverviewSection(content: HomeDashboardContent) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        SectionHeader("Summary")
        KeyFactsPanel(
            rows =
                listOf(
                    KeyFactRow("Address", content.address),
                    KeyFactRow("Status", if (content.verified) "Verified" else "Unverified"),
                    KeyFactRow(
                        label = "Members",
                        value = content.stats.firstOrNull { it.id == "members" }?.value ?: "—",
                    ),
                ),
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
