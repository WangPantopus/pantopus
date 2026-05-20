@file:Suppress("PackageNaming", "LongMethod", "MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.audience_profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii

@Composable
fun AudienceProfileScreen(
    onBack: () -> Unit = {},
    onOpenFollower: (FollowerRowContent) -> Unit = {},
    onOpenThread: (ThreadRowContent) -> Unit = {},
    onOpenBroadcast: (UpdateCardContent, List<TierBreakdownContent.TierSegment>) -> Unit = { _, _ -> },
    onOpenSetup: () -> Unit = {},
    onOpenCreatorInbox: () -> Unit = {},
    viewModel: AudienceProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val composer by viewModel.composer.collectAsStateWithLifecycle()
    val selectedTier by viewModel.selectedTierRank.collectAsStateWithLifecycle()
    val followerSearch by viewModel.followerSearchText.collectAsStateWithLifecycle()
    val followerSort by viewModel.followerSort.collectAsStateWithLifecycle()
    val activeThreadFilter by viewModel.activeThreadFilter.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    val displayName =
        (state as? AudienceProfileUiState.Loaded)?.content?.header?.displayName ?: "Public Profile"

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("audienceProfile"),
    ) {
        TopBar(title = displayName, onBack = onBack)
        when (val current = state) {
            is AudienceProfileUiState.Loading -> LoadingFrame()
            is AudienceProfileUiState.Empty -> EmptyFrame(message = current.message, onSetup = onOpenSetup)
            is AudienceProfileUiState.Error ->
                ErrorFrame(message = current.message, onRetry = viewModel::load)
            is AudienceProfileUiState.Loaded ->
                LoadedFrame(
                    state =
                        AudienceProfileLoadedFrameState(
                            loaded = current.content,
                            activeTab = activeTab,
                            composer = composer,
                            selectedTier = selectedTier,
                            followerSearchText = followerSearch,
                            followerSort = followerSort,
                            visibleFollowers = viewModel.visibleFollowers(),
                            activeThreadFilter = activeThreadFilter,
                            visibleThreads = viewModel.visibleThreads(),
                        ),
                    actions =
                        AudienceProfileLoadedFrameActions(
                            onSelectTab = viewModel::selectTab,
                            onSelectTier = viewModel::selectTierFilter,
                            onFollowerSearch = viewModel::onFollowerSearchText,
                            onFollowerSort = viewModel::selectFollowerSort,
                            onSelectThreadFilter = viewModel::selectThreadFilter,
                            composer =
                                AudienceProfileComposerActions(
                                    onText = viewModel::onComposerText,
                                    onVisibility = viewModel::onComposerVisibility,
                                    onTier = viewModel::onComposerTier,
                                    onSubmit = viewModel::submitUpdate,
                                ),
                            navigation =
                                AudienceProfileNavigationActions(
                                    onOpenFollower = onOpenFollower,
                                    onOpenThread = onOpenThread,
                                    onOpenBroadcast = onOpenBroadcast,
                                    onOpenCreatorInbox = onOpenCreatorInbox,
                                ),
                        ),
                )
        }
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onBack)
                        .testTag("audienceProfileBackButton"),
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
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(36.dp))
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
                .testTag("audienceProfileLoading"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Shimmer(width = 360.dp, height = 90.dp, cornerRadius = 16.dp)
        Shimmer(width = 360.dp, height = 44.dp, cornerRadius = 22.dp)
        repeat(3) { Shimmer(width = 360.dp, height = 88.dp, cornerRadius = 14.dp) }
    }
}

@Composable
private fun EmptyFrame(
    message: String,
    onSetup: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("audienceProfileEmpty"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Star,
            contentDescription = null,
            size = 40.dp,
            strokeWidth = 2f,
            tint = PantopusColors.primary600,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Create your Public Profile",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(horizontal = 40.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        PrimaryButton(
            title = "Set up Public Profile",
            onClick = onSetup,
            modifier = Modifier.testTag("audienceProfileSetupButton"),
        )
    }
}

@Composable
private fun ErrorFrame(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(20.dp)
                .testTag("audienceProfileError"),
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
            text = "Couldn't load Public Profile",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
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
            modifier = Modifier.testTag("audienceProfileRetry"),
        )
    }
}

@Composable
internal fun LoadedFrame(
    state: AudienceProfileLoadedFrameState,
    actions: AudienceProfileLoadedFrameActions,
) {
    Column(
        modifier = Modifier.fillMaxSize().testTag("audienceProfileContent"),
    ) {
        HeaderCard(state.loaded.header)
        TabStrip(activeTab = state.activeTab, onSelect = actions.onSelectTab)
        when (state.activeTab) {
            AudienceProfileTab.Updates ->
                UpdatesTab(
                    updates = state.loaded.updates,
                    composer = state.composer,
                    channelId = state.loaded.channelId,
                    composerActions = actions.composer,
                    onOpenBroadcast = { card ->
                        actions.navigation.onOpenBroadcast(card, state.loaded.tierBreakdown.segments)
                    },
                )
            AudienceProfileTab.Followers ->
                FollowersTab(
                    state =
                        FollowersTabState(
                            cells = state.loaded.analyticsCells,
                            breakdown = state.loaded.tierBreakdown,
                            chips = state.loaded.tierChips,
                            selectedTier = state.selectedTier,
                            searchText = state.followerSearchText,
                            activeSort = state.followerSort,
                            followers = state.visibleFollowers,
                        ),
                    actions =
                        FollowersTabActions(
                            onSelectTier = actions.onSelectTier,
                            onFollowerSearch = actions.onFollowerSearch,
                            onFollowerSort = actions.onFollowerSort,
                            onOpenFollower = actions.navigation.onOpenFollower,
                        ),
                )
            AudienceProfileTab.Threads ->
                ThreadsTab(
                    threads = state.loaded.threads,
                    visibleThreads = state.visibleThreads,
                    chips = state.loaded.threadsFilterChips,
                    activeFilter = state.activeThreadFilter,
                    onSelectFilter = actions.onSelectThreadFilter,
                    onOpenThread = actions.navigation.onOpenThread,
                    onOpenCreatorInbox = actions.navigation.onOpenCreatorInbox,
                )
        }
    }
}

internal data class AudienceProfileLoadedFrameState(
    val loaded: AudienceProfileLoaded,
    val activeTab: AudienceProfileTab,
    val composer: UpdateComposerState,
    val selectedTier: Int?,
    val followerSearchText: String = "",
    val followerSort: FollowerSort = FollowerSort.NewestActive,
    val visibleFollowers: List<FollowerRowContent> = emptyList(),
    val activeThreadFilter: ThreadsFilter = ThreadsFilter.All,
    val visibleThreads: List<ThreadRowContent> = emptyList(),
)

internal data class AudienceProfileLoadedFrameActions(
    val onSelectTab: (AudienceProfileTab) -> Unit,
    val onSelectTier: (Int?) -> Unit,
    val onFollowerSearch: (String) -> Unit,
    val onFollowerSort: (FollowerSort) -> Unit,
    val onSelectThreadFilter: (ThreadsFilter) -> Unit = {},
    val composer: AudienceProfileComposerActions,
    val navigation: AudienceProfileNavigationActions,
)

internal data class AudienceProfileComposerActions(
    val onText: (String) -> Unit,
    val onVisibility: (UpdateVisibility) -> Unit,
    val onTier: (Int?) -> Unit,
    val onSubmit: () -> Unit,
)

internal data class AudienceProfileNavigationActions(
    val onOpenFollower: (FollowerRowContent) -> Unit,
    val onOpenThread: (ThreadRowContent) -> Unit,
    val onOpenBroadcast: (UpdateCardContent, List<TierBreakdownContent.TierSegment>) -> Unit = { _, _ -> },
    val onOpenCreatorInbox: () -> Unit = {},
)

@Composable
private fun HeaderCard(header: AudienceHeaderContent) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(16.dp)
                .testTag("audienceProfileHeader"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = header.displayName,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
            header.handle?.let {
                Text(text = it, fontSize = 13.sp, color = PantopusColors.appTextSecondary)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "${header.followerCount} followers",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
            if (header.newThisWeek > 0) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(PantopusColors.successBg)
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "+${header.newThisWeek} new",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.success,
                    )
                }
            }
            Text(
                text = "${header.postCount} updates",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun TabStrip(
    activeTab: AudienceProfileTab,
    onSelect: (AudienceProfileTab) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            AudienceProfileTab.values().forEach { tab ->
                val isActive = activeTab == tab
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .clickable { onSelect(tab) }
                            .testTag("audienceProfileTab_${tab.key}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = tab.title,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) PantopusColors.primary600 else PantopusColors.appTextSecondary,
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .background(if (isActive) PantopusColors.primary600 else Color.Transparent),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    }
}

// MARK: - Updates tab

@Composable
private fun UpdatesTab(
    updates: List<UpdateCardContent>,
    composer: UpdateComposerState,
    channelId: String?,
    composerActions: AudienceProfileComposerActions,
    onOpenBroadcast: (UpdateCardContent) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("audienceProfileUpdatesList"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ComposerCard(
            composer = composer,
            channelId = channelId,
            onText = composerActions.onText,
            onVisibility = composerActions.onVisibility,
            onTier = composerActions.onTier,
            onSubmit = composerActions.onSubmit,
        )
        if (updates.isEmpty()) {
            EmptyUpdatesCard()
        } else {
            updates.forEach { card ->
                UpdateCard(card = card, onOpen = { onOpenBroadcast(card) })
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ComposerCard(
    composer: UpdateComposerState,
    channelId: String?,
    onText: (String) -> Unit,
    onVisibility: (UpdateVisibility) -> Unit,
    onTier: (Int?) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(14.dp))
                .padding(14.dp)
                .testTag("audienceProfileComposer"),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "POSTING AS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextSecondary,
                letterSpacing = 0.6.sp,
            )
            Spacer(modifier = Modifier.weight(1f))
            VisibilityPicker(visibility = composer.visibility, onVisibility = onVisibility, onTier = onTier)
        }
        OutlinedTextField(
            value = composer.text,
            onValueChange = onText,
            placeholder = {
                Text(
                    text = "Share an update with your followers",
                    fontSize = 14.sp,
                    color = PantopusColors.appTextMuted,
                )
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .testTag("audienceProfileComposerInput"),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = PantopusColors.appSurface,
                    unfocusedContainerColor = PantopusColors.appSurface,
                    focusedBorderColor = PantopusColors.primary600,
                    unfocusedBorderColor = PantopusColors.appBorder,
                ),
        )
        composer.error?.let {
            Text(text = it, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = PantopusColors.error)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            val canSubmit = composer.canSubmit && channelId != null
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (canSubmit) PantopusColors.primary600 else PantopusColors.appBorderStrong)
                        .clickable(enabled = canSubmit) { onSubmit() }
                        .padding(horizontal = 16.dp)
                        .height(38.dp)
                        .testTag("audienceProfileComposerSubmit"),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (composer.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = PantopusColors.appTextInverse,
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = "Post update",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appTextInverse,
                    )
                }
            }
        }
    }
}

@Composable
private fun VisibilityPicker(
    visibility: UpdateVisibility,
    onVisibility: (UpdateVisibility) -> Unit,
    onTier: (Int?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(PantopusColors.primary50)
                    .clickable { expanded = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag("audienceProfileVisibilityPicker"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Visible to ${visibility.title}",
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.primary700,
            )
            PantopusIconImage(
                icon = PantopusIcon.ChevronDown,
                contentDescription = null,
                size = 12.dp,
                strokeWidth = 2f,
                tint = PantopusColors.primary700,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            UpdateVisibility.values().forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.title) },
                    onClick = {
                        onVisibility(option)
                        if (option != UpdateVisibility.TierOrAbove) onTier(null)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EmptyUpdatesCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Send,
            contentDescription = null,
            size = 32.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "No updates yet",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        Text(
            text = "Use the composer above to share your first update.",
            fontSize = 12.sp,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun UpdateCard(
    card: UpdateCardContent,
    onOpen: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(14.dp)
                .testTag("updateCard_${card.id}"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(PantopusColors.primary50)
                        .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = card.visibilityLabel.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.primary700,
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(text = card.timeAgo, fontSize = 11.sp, color = PantopusColors.appTextSecondary)
        }
        Text(text = card.body, fontSize = 14.sp, color = PantopusColors.appText)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Delivered ${card.deliveredCount}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
            Text(
                text = "Read ${card.readCount}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appTextSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = 13.dp,
                strokeWidth = 2f,
                tint = PantopusColors.appTextMuted,
            )
        }
    }
}

// MARK: - Followers tab

private data class FollowersTabState(
    val cells: List<AnalyticsCellContent>,
    val breakdown: TierBreakdownContent,
    val chips: List<TierChipContent>,
    val selectedTier: Int?,
    val searchText: String,
    val activeSort: FollowerSort,
    val followers: List<FollowerRowContent>,
)

private data class FollowersTabActions(
    val onSelectTier: (Int?) -> Unit,
    val onFollowerSearch: (String) -> Unit,
    val onFollowerSort: (FollowerSort) -> Unit,
    val onOpenFollower: (FollowerRowContent) -> Unit,
)

@Composable
private fun FollowersTab(
    state: FollowersTabState,
    actions: FollowersTabActions,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("audienceProfileFollowersList"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AnalyticsRow(state.cells)
        TierStackedBar(state.breakdown)
        TierChipRow(chips = state.chips, selectedTier = state.selectedTier, onSelect = actions.onSelectTier)
        FollowerSearchField(text = state.searchText, onChange = actions.onFollowerSearch)
        FollowerSortChipRow(active = state.activeSort, onSelect = actions.onFollowerSort)
        if (state.followers.isEmpty()) {
            if (state.searchText.trim().isNotEmpty()) {
                EmptyFollowerSearchCard()
            } else {
                EmptyFollowersCard()
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.followers.forEach { follower -> FollowerRow(follower, onOpen = { actions.onOpenFollower(follower) }) }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun FollowerSearchField(
    text: String,
    onChange: (String) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                .padding(horizontal = 12.dp)
                .height(40.dp)
                .testTag("followerSearchField"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Search,
            contentDescription = null,
            size = 15.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextMuted,
        )
        Box(modifier = Modifier.weight(1f)) {
            if (text.isEmpty()) {
                Text(
                    text = "Search followers by name or handle",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = PantopusColors.appTextMuted,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onChange,
                textStyle =
                    TextStyle(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PantopusColors.appText,
                    ),
                cursorBrush = SolidColor(PantopusColors.primary600),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(),
                singleLine = true,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Search followers by name or handle" }
                        .testTag("followerSearchInput"),
            )
        }
        if (text.isNotEmpty()) {
            Box(
                modifier =
                    Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable { onChange("") }
                        .testTag("followerSearchClear"),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.X,
                    contentDescription = "Clear search",
                    size = 14.dp,
                    strokeWidth = 2f,
                    tint = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun FollowerSortChipRow(
    active: FollowerSort,
    onSelect: (FollowerSort) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag("followerSortChipRow")
                .semantics { contentDescription = "Sort followers" },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FollowerSort.entries.forEach { sort ->
            val isActive = sort == active
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(
                            if (isActive) PantopusColors.primary600 else PantopusColors.appSurface,
                        )
                        .border(
                            width = 1.dp,
                            color = if (isActive) PantopusColors.primary600 else PantopusColors.appBorder,
                            shape = RoundedCornerShape(Radii.pill),
                        )
                        .clickable { onSelect(sort) }
                        .padding(horizontal = 12.dp)
                        .heightIn(min = 28.dp)
                        .wrapContentSize(Alignment.Center)
                        .testTag("followerSortChip_${sort.key}"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (isActive) {
                    PantopusIconImage(
                        icon = PantopusIcon.Check,
                        contentDescription = null,
                        size = 11.dp,
                        strokeWidth = 2.6f,
                        tint = PantopusColors.appTextInverse,
                    )
                }
                Text(
                    text = sort.title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color =
                        if (isActive) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                )
            }
        }
    }
}

@Composable
private fun EmptyFollowerSearchCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag("followerSearchEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Search,
            contentDescription = null,
            size = 32.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "No followers match that search",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        Text(
            text = "Try a different name or handle.",
            fontSize = 12.sp,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun AnalyticsRow(cells: List<AnalyticsCellContent>) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cells.forEach { cell ->
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(10.dp))
                        .padding(10.dp)
                        .testTag("analyticsCell_${cell.id}"),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = cell.label.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextSecondary,
                    letterSpacing = 0.6.sp,
                )
                Text(
                    text = cell.value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                cell.trend?.let {
                    Text(text = it, fontSize = 10.sp, color = PantopusColors.success)
                }
            }
        }
    }
}

@Composable
private fun TierStackedBar(breakdown: TierBreakdownContent) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("tierStackedBar"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "Audience by tier",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(14.dp)) {
            val totalWidth = maxWidth
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                breakdown.segments.forEach { seg ->
                    val w =
                        if (breakdown.total > 0) {
                            totalWidth * (seg.count.toFloat() / breakdown.total.toFloat())
                        } else {
                            0.dp
                        }
                    val displayed =
                        if (seg.count > 0 && w < 4.dp) 4.dp else w
                    Box(
                        modifier =
                            Modifier
                                .width(displayed)
                                .height(14.dp)
                                .background(tierColor(seg.rank)),
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            breakdown.segments.forEach { seg ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        modifier =
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tierColor(seg.rank)),
                    )
                    Text(
                        text = "${seg.name} · ${seg.count}",
                        fontSize = 10.5.sp,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TierChipRow(
    chips: List<TierChipContent>,
    selectedTier: Int?,
    onSelect: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val isActive = selectedTier == chip.rank
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (isActive) PantopusColors.primary600 else PantopusColors.appSurface)
                        .border(
                            1.dp,
                            if (isActive) PantopusColors.primary600 else PantopusColors.appBorder,
                            RoundedCornerShape(999.dp),
                        )
                        .clickable { onSelect(chip.rank) }
                        .padding(horizontal = 10.dp)
                        .height(28.dp)
                        .wrapContentSize(Alignment.Center)
                        .testTag("tierChip_${chip.id}"),
            ) {
                Text(
                    text = "${chip.label} · ${chip.count}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isActive) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                )
            }
        }
    }
}

@Composable
private fun FollowerRow(
    row: FollowerRowContent,
    onOpen: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(12.dp)
                .testTag("followerRow_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = row.displayName.take(1).uppercase(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.primary700,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.displayName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                if (row.verifiedLocal) {
                    PantopusIconImage(
                        icon = PantopusIcon.ShieldCheck,
                        contentDescription = "Verified neighbor",
                        size = 12.dp,
                        strokeWidth = 2f,
                        tint = PantopusColors.success,
                    )
                }
            }
            Text(text = row.handle, fontSize = 11.sp, color = PantopusColors.appTextSecondary)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = row.tierName,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = tierColor(row.tierRank),
            )
            row.tenureLabel?.let {
                Text(text = it, fontSize = 10.5.sp, color = PantopusColors.appTextSecondary)
            }
        }
    }
}

@Composable
private fun EmptyFollowersCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.User,
            contentDescription = null,
            size = 32.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "No followers in this tier yet",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        Text(
            text = "Share your Public Profile to start building your audience.",
            fontSize = 12.sp,
            color = PantopusColors.appTextSecondary,
        )
    }
}

// MARK: - Threads tab

@Composable
private fun ThreadsTab(
    threads: List<ThreadRowContent>,
    visibleThreads: List<ThreadRowContent>,
    chips: List<ThreadsFilterChipContent>,
    activeFilter: ThreadsFilter,
    onSelectFilter: (ThreadsFilter) -> Unit,
    onOpenThread: (ThreadRowContent) -> Unit,
    onOpenCreatorInbox: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("audienceProfileThreadsList"),
    ) {
        ThreadsFilterStrip(chips = chips, activeFilter = activeFilter, onSelect = onSelectFilter)
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (threads.isEmpty()) {
                EmptyThreadsCard()
            } else if (visibleThreads.isEmpty()) {
                FilteredEmptyThreadsCard()
            } else {
                ViewAllMessagesCTA(onClick = onOpenCreatorInbox)
                visibleThreads.forEach { ThreadRow(it, onOpen = { onOpenThread(it) }) }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ThreadsFilterStrip(
    chips: List<ThreadsFilterChipContent>,
    activeFilter: ThreadsFilter,
    onSelect: (ThreadsFilter) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .testTag("audienceProfileThreadsFilterStrip"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scroll)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            chips.forEach { chip ->
                ThreadsFilterChip(chip = chip, isActive = chip.filter == activeFilter, onSelect = onSelect)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(PantopusColors.appBorder))
    }
}

@Composable
private fun ThreadsFilterChip(
    chip: ThreadsFilterChipContent,
    isActive: Boolean,
    onSelect: (ThreadsFilter) -> Unit,
) {
    val bg = if (isActive) PantopusColors.primary600 else PantopusColors.appSurface
    val border = if (isActive) PantopusColors.primary600 else PantopusColors.appBorder
    val fg = if (isActive) PantopusColors.appTextInverse else PantopusColors.appTextStrong
    val description = chip.count?.let { "${chip.label}, $it" } ?: chip.label
    Row(
        modifier =
            Modifier
                .heightIn(min = 28.dp)
                .clip(RoundedCornerShape(9999.dp))
                .background(bg)
                .border(width = 1.dp, color = border, shape = RoundedCornerShape(9999.dp))
                .clickable { onSelect(chip.filter) }
                .padding(horizontal = 11.dp, vertical = 5.dp)
                .testTag("threadsFilterChip_${chip.id}")
                .semantics { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = chip.label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
        chip.count?.let { count ->
            Text(
                text = "$count",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = fg.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun FilteredEmptyThreadsCard() {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .testTag("audienceProfileThreadsFilteredEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Inbox,
            contentDescription = null,
            size = 32.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "No threads in this view",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Try another filter to see the rest of your inbox.",
            fontSize = 12.sp,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun ViewAllMessagesCTA(onClick: () -> Unit) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary100, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .heightIn(min = 44.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .testTag("audienceProfileViewAllMessages")
                .semantics { contentDescription = "View all messages in Creator Inbox" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Inbox,
            contentDescription = null,
            size = 14.dp,
            strokeWidth = 2f,
            tint = PantopusColors.primary600,
        )
        Text(
            text = "View all messages",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.primary700,
            modifier = Modifier.weight(1f),
        )
        PantopusIconImage(
            icon = PantopusIcon.ChevronRight,
            contentDescription = null,
            size = 12.dp,
            strokeWidth = 2f,
            tint = PantopusColors.primary600,
        )
    }
}

@Composable
private fun ThreadRow(
    row: ThreadRowContent,
    onOpen: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(12.dp)
                .testTag("threadRow_${row.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(PantopusColors.primary50),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = row.displayName.take(1).uppercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.primary700,
                )
            }
            if (row.unreadCount > 0) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .clip(RoundedCornerShape(999.dp))
                            .background(PantopusColors.error)
                            .padding(horizontal = 4.dp)
                            .widthIn(min = 16.dp)
                            .height(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "${row.unreadCount}",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appTextInverse,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = row.displayName,
                    fontSize = 14.sp,
                    fontWeight = if (row.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                row.tierName?.let {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(PantopusColors.primary50)
                                .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = it,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = PantopusColors.primary700,
                        )
                    }
                }
            }
            Text(
                text = row.preview,
                fontSize = 12.sp,
                color = PantopusColors.appTextSecondary,
                maxLines = 2,
            )
        }
        Text(text = row.timeAgo, fontSize = 10.5.sp, color = PantopusColors.appTextSecondary)
    }
}

@Composable
private fun EmptyThreadsCard() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Inbox,
            contentDescription = null,
            size = 32.dp,
            strokeWidth = 2f,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "No threads yet",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
        Text(
            text = "Tier 2+ followers can open a thread with you.",
            fontSize = 12.sp,
            color = PantopusColors.appTextSecondary,
        )
    }
}

internal fun tierColor(rank: Int): Color =
    when (rank) {
        1 -> PantopusColors.primary600
        2 -> PantopusColors.success
        3 -> PantopusColors.warning
        4 -> PantopusColors.business
        else -> PantopusColors.appTextSecondary
    }
