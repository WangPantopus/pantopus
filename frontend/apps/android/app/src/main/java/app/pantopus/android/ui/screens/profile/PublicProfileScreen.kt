@file:Suppress("LongMethod", "MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBar
import app.pantopus.android.ui.screens.shared.content_detail.bodies.StatsTabsBody
import app.pantopus.android.ui.screens.shared.content_detail.ctas.ActionRowCta
import app.pantopus.android.ui.screens.shared.content_detail.headers.ProfileHeader
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Public profile detail screen. ViewModel reads the user id via the
 * nav-backstack [androidx.lifecycle.SavedStateHandle].
 *
 * `onOpenMessages` is invoked with the loaded `PublicProfileDto` so the
 * host nav stack can construct a chat-conversation destination with the
 * profile's user as counterparty. The Report flow is presented as a
 * [ReportUserSheet] hosted locally here, not via the nav graph (per P6.2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    onBack: () -> Unit,
    onOpenMessages: (app.pantopus.android.data.api.models.profile.PublicProfileDto) -> Unit = {},
    viewModel: PublicProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()
    val showOverflow by viewModel.showOverflow.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState()
    val reportSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReportSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_000)
            viewModel.dismissToast()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        when (val s = state) {
            PublicProfileUiState.Loading -> LoadingLayout(onBack = onBack)
            is PublicProfileUiState.Error -> ErrorLayout(message = s.message, onRetry = { viewModel.refresh() })
            is PublicProfileUiState.Loaded -> {
                val content = s.content
                ContentDetailShell(
                    title = null,
                    onBack = onBack,
                    cta = { ActionRowCta() },
                    header = {
                        ProfileHeader(
                            displayName = content.header.displayName,
                            handle = content.header.handle,
                            locality = content.header.locality,
                            avatarUrl = content.header.avatarUrl,
                            isVerified = content.header.isVerified,
                            identityBadges = content.header.identityBadges,
                        )
                    },
                    body = {
                        StatsTabsBody(
                            content = content.stats,
                            selectedTab = selectedTab,
                            onSelectTab = { viewModel.selectTab(it) },
                            onMessage = { onOpenMessages(content.profile) },
                            onConnect = { viewModel.connect() },
                            onOverflow = { viewModel.setShowOverflow(true) },
                        )
                    },
                )
            }
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
                Text(message, style = PantopusTextStyle.small, color = PantopusColors.appTextInverse)
            }
        }
        if (showOverflow) {
            ModalBottomSheet(
                onDismissRequest = { viewModel.setShowOverflow(false) },
                sheetState = sheetState,
            ) {
                OverflowSheetContent(
                    onBlock = {
                        viewModel.setShowOverflow(false)
                        viewModel.block()
                    },
                    onReport = {
                        viewModel.setShowOverflow(false)
                        showReportSheet = true
                    },
                    onCancel = { viewModel.setShowOverflow(false) },
                )
            }
        }
        if (showReportSheet) {
            val loaded = state as? PublicProfileUiState.Loaded
            if (loaded != null) {
                ReportUserSheet(
                    userId = loaded.content.profile.id,
                    handle = loaded.content.header.handle,
                    displayName = loaded.content.header.displayName,
                    sheetState = reportSheetState,
                    onDismiss = { showReportSheet = false },
                    onSubmitted = {
                        showReportSheet = false
                        viewModel.showToast("Report received")
                    },
                )
            }
        }
    }
}

@Composable
private fun OverflowSheetContent(
    onBlock: () -> Unit,
    onReport: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s5),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        OverflowSheetRow(label = "Block this user", destructive = true, onClick = onBlock)
        OverflowSheetRow(label = "Report", onClick = onReport)
        OverflowSheetRow(label = "Cancel", onClick = onCancel)
    }
}

@Composable
private fun OverflowSheetRow(
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

@Composable
private fun LoadingLayout(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ContentDetailTopBar(title = null, onBack = onBack, action = null)
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Shimmer(width = 72.dp, height = 72.dp, cornerRadius = 36.dp)
            Shimmer(width = 160.dp, height = 22.dp, cornerRadius = Radii.sm)
            Shimmer(width = 220.dp, height = 12.dp, cornerRadius = Radii.sm)
            Shimmer(width = 320.dp, height = 80.dp, cornerRadius = Radii.lg)
            Shimmer(width = 320.dp, height = 42.dp, cornerRadius = Radii.lg)
        }
    }
}

@Composable
private fun ErrorLayout(
    message: String,
    onRetry: () -> Unit,
) {
    EmptyState(
        icon = PantopusIcon.AlertCircle,
        headline = "Couldn't load this profile",
        subcopy = message,
        ctaTitle = "Try again",
        onCta = onRetry,
    )
}
