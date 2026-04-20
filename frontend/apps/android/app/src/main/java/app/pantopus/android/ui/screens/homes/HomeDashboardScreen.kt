@file:Suppress("MagicNumber", "LongMethod", "LongParameterList")

package app.pantopus.android.ui.screens.homes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Hub → MyHomes → Home Dashboard screen. The ViewModel reads the home id
 * from the nav-backstack [androidx.lifecycle.SavedStateHandle].
 *
 * @param onBack Back handler wired to the NavController.
 */
@Composable
fun HomeDashboardScreen(
    onBack: () -> Unit,
    viewModel: HomeDashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    var toast by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.load() }

    fun showToast(actionId: String) {
        toast =
            when (actionId) {
                "log_package" -> "Log a package isn't available yet"
                "add_member" -> "Add member isn't available yet"
                "add_mail" -> "Add mail isn't available yet"
                "verify" -> "Verify home isn't available yet"
                else -> "That isn't available yet"
            }
        scope.launch {
            delay(1_800)
            toast = null
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
                    onQuickAction = ::showToast,
                    onFabAction = ::showToast,
                )
            is HomeDashboardUiState.Error ->
                ErrorLayout(message = current.message, onBack = onBack, onRetry = viewModel::refresh)
        }
        if (toast != null) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.s10),
            ) {
                Text(
                    text = toast.orEmpty(),
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextInverse,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(PantopusColors.appText.copy(alpha = 0.9f))
                            .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
                )
            }
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
) {
    ContentDetailShell(
        title = "Home",
        onBack = onBack,
        cta = {
            FabCreateCTA(
                actions =
                    listOf(
                        FabSheetAction("log_package", "Log a package", PantopusIcon.ShoppingBag),
                        FabSheetAction("add_member", "Add member", PantopusIcon.UserPlus),
                        FabSheetAction("add_mail", "Add mail", PantopusIcon.Mailbox),
                    ),
                onSelect = onFabAction,
            )
        },
        header = {
            HomeHeroHeader(address = content.address, verified = content.verified, stats = content.stats)
        },
        body = {
            GridTabsBody(
                quickActions = content.quickActions,
                tabs = content.tabs,
                selectedTab = selectedTab,
                onSelectTab = onSelectTab,
                onQuickAction = onQuickAction,
            ) {
                OverviewSection(content = content)
            }
        },
    )
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
