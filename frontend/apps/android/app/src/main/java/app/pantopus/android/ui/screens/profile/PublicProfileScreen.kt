@file:Suppress("LongMethod", "MagicNumber", "PackageNaming", "LongParameterList")

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.BeaconIdentity
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailShell
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBar
import app.pantopus.android.ui.screens.shared.content_detail.ContentDetailTopBarAction
import app.pantopus.android.ui.screens.shared.content_detail.bodies.ProfileTab
import app.pantopus.android.ui.screens.shared.content_detail.bodies.StatsTabsBody
import app.pantopus.android.ui.screens.transaction_reviews.ReceivedTransactionReviewsSection
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
 * P6.5 — The view-model picks the profile kind (Persona vs Local) from
 * the loaded DTO. The screen swaps banner color, header chips, sticky
 * footer CTAs (Follow vs Message + Connect), and post styling
 * accordingly.
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
    val selectedNeighborTab by viewModel.selectedNeighborTab.collectAsStateWithLifecycle()
    val toast by viewModel.toastMessage.collectAsStateWithLifecycle()
    val showOverflow by viewModel.showOverflow.collectAsStateWithLifecycle()
    val connectState by viewModel.connectState.collectAsStateWithLifecycle()
    val followState by viewModel.followState.collectAsStateWithLifecycle()
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

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("publicProfile"),
    ) {
        when (val s = state) {
            PublicProfileUiState.Loading -> LoadingLayout(onBack = onBack)
            is PublicProfileUiState.Error -> ErrorLayout(message = s.message, onRetry = { viewModel.refresh() }, onBack = onBack)
            is PublicProfileUiState.Loaded -> {
                val content = s.content
                val neighbor = content.neighbor
                if (content.kind == PublicProfileKind.Local && neighbor != null) {
                    NeighborProfileLayout(
                        content = neighbor,
                        selectedTab = selectedNeighborTab,
                        connectState = connectState,
                        onBack = onBack,
                        onSelectTab = { viewModel.selectNeighborTab(it) },
                        onMessage = { onOpenMessages(content.profile) },
                        onConnect = { viewModel.connect() },
                        onReport = { showReportSheet = true },
                        onBlock = { viewModel.block() },
                        onOverflow = { viewModel.setShowOverflow(true) },
                    )
                } else {
                    PublicProfileLoadedFrame(
                        content = content,
                        selectedTab = selectedTab,
                        followState = followState,
                        connectState = connectState,
                        onBack = onBack,
                        onSelectTab = { viewModel.selectTab(it) },
                        onFollow = { viewModel.follow() },
                        onMessage = { onOpenMessages(content.profile) },
                        onConnect = { viewModel.connect() },
                        onOverflow = { viewModel.setShowOverflow(true) },
                        onUnlock = { viewModel.showSubscribeToast() },
                        receivedReviews = {
                            ReceivedTransactionReviewsSection(userId = content.profile.id)
                        },
                    )
                }
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

/**
 * P6.5 — Kind-aware loaded frame, exposed as `internal` so Paparazzi
 * snapshot tests can render the populated view without spinning up a
 * Hilt VM.
 */
@Composable
internal fun PublicProfileLoadedFrame(
    content: PublicProfileContent,
    selectedTab: ProfileTab,
    followState: PublicProfileActionState,
    connectState: PublicProfileActionState,
    onBack: () -> Unit,
    onSelectTab: (ProfileTab) -> Unit,
    onFollow: () -> Unit,
    onMessage: () -> Unit,
    onConnect: () -> Unit,
    onOverflow: () -> Unit,
    onUnlock: () -> Unit,
    receivedReviews: @Composable () -> Unit = {},
) {
    val persona = content.kind == PublicProfileKind.Persona
    ContentDetailShell(
        title = null,
        onBack = onBack,
        topBarAction =
            ContentDetailTopBarAction(
                icon = PantopusIcon.MoreHorizontal,
                contentDescription = "More",
                onClick = onOverflow,
            ),
        header = {
            // Box stacks the 120dp banner with the identity block pulled
            // down to overlap the banner's lower 40dp (banner − overlap).
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .testTag(if (persona) "publicProfilePersonaHeader" else "publicProfileLocalHeader"),
            ) {
                PublicProfileBanner(kind = content.kind)
                Box(modifier = Modifier.padding(top = 80.dp)) {
                    BeaconIdentityBlock(
                        identity = if (persona) BeaconIdentity.Personal else BeaconIdentity.Home,
                        name = content.header.displayName,
                        handle = content.header.handle,
                        tierLabel = content.header.tierLabel,
                        isVerifiedNeighbor = content.header.isVerifiedNeighbor,
                        locality = content.header.locality,
                        bio = content.stats.bio,
                        isVerified = content.header.isVerified,
                        avatarUrl = content.header.avatarUrl,
                        stats = content.stats.stats,
                    ) {
                        if (persona) {
                            val following = followState is PublicProfileActionState.Succeeded
                            BeaconHeaderGhostButton(
                                icon = PantopusIcon.Share,
                                actionLabel = "Share profile",
                                onClick = onOverflow,
                            )
                            BeaconHeaderPrimaryButton(
                                title = if (following) "Following" else "Follow",
                                icon = PantopusIcon.Plus,
                                onClick = onFollow,
                                isProminent = !following,
                            )
                        } else {
                            val requested = connectState is PublicProfileActionState.Succeeded
                            BeaconHeaderGhostButton(
                                icon = PantopusIcon.UserPlus,
                                actionLabel = if (requested) "Requested" else "Connect",
                                onClick = onConnect,
                                title = if (requested) "Requested" else "Connect",
                            )
                            BeaconHeaderPrimaryButton(
                                title = "Message",
                                icon = PantopusIcon.MessageSquare,
                                onClick = onMessage,
                            )
                        }
                    }
                }
            }
        },
        body = {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
                StatsTabsBody(
                    content = content.stats,
                    selectedTab = selectedTab,
                    onSelectTab = onSelectTab,
                    showStats = false,
                    showActionRow = false,
                    onMessage = onMessage,
                    onConnect = onConnect,
                    onOverflow = onOverflow,
                )
                receivedReviews()
                PublicProfilePostsFeed(
                    kind = content.kind,
                    posts = content.posts,
                    onUnlock = { onUnlock() },
                    onEmptyCta = if (persona) onFollow else onMessage,
                )
            }
        },
    )
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
internal fun LoadingLayout(onBack: () -> Unit) {
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
internal fun ErrorLayout(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    // Mirror iOS: the error layout keeps a back chevron so a load failure
    // is escapable from in-screen chrome (LoadingLayout already does this).
    Column(modifier = Modifier.fillMaxSize()) {
        ContentDetailTopBar(title = null, onBack = onBack, action = null)
        EmptyState(
            icon = PantopusIcon.AlertCircle,
            headline = "Couldn't load this profile",
            subcopy = message,
            ctaTitle = "Try again",
            onCta = onRetry,
        )
    }
}
