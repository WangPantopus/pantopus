@file:Suppress("MagicNumber", "LongMethod", "PackageNaming")

package app.pantopus.android.ui.screens.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.screens.feed.pulse.PulseFeedUiState
import app.pantopus.android.ui.screens.feed.pulse.PulseFeedViewModel
import app.pantopus.android.ui.screens.feed.pulse.PulseIntent
import app.pantopus.android.ui.screens.feed.pulse.PulsePostCard
import app.pantopus.android.ui.screens.shared.feed.FeedChipItem
import app.pantopus.android.ui.screens.shared.feed.FeedChipRow
import app.pantopus.android.ui.screens.shared.feed.FeedComposeFAB
import app.pantopus.android.ui.screens.shared.feed.FeedSkeletonCard
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * Pulse tab — the public neighborhood feed reached from
 * Hub → pillar(.pulse). Replaces the legacy List-of-strings stub.
 */
@Composable
fun FeedScreen(
    surface: FeedSurface = FeedSurface.Pulse,
    onOpenPost: (String) -> Unit = {},
    onCompose: (PulseIntent) -> Unit = {},
    onEmptyCta: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    viewModel: PulseFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val activeIntent by viewModel.activeIntent.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.configureSurface(surface)
        viewModel.load()
        Analytics.track(AnalyticsEvent.ScreenPulseFeedViewed(intent = activeIntent.key))
    }

    Box(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg).testTag("pulseFeed")) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopBar(title = surface.title, onBack = onBack)
            FeedChipRow(
                chips = PulseIntent.entries.map { FeedChipItem(id = it.key, label = it.label) },
                activeId = activeIntent.key,
                onSelect = { id -> viewModel.selectIntent(PulseIntent.fromKey(id)) },
            )
            when (val s = state) {
                is PulseFeedUiState.Loading -> LoadingFrame()
                is PulseFeedUiState.Empty ->
                    FeedEmptyState(content = s.content) { onEmptyCta?.invoke() ?: onCompose(activeIntent) }
                is PulseFeedUiState.Loaded ->
                    PopulatedFrame(
                        state = s,
                        onTapPost = onOpenPost,
                        onTapReaction = viewModel::tapReaction,
                        isRefreshing = isRefreshing,
                        onRefresh = viewModel::refresh,
                    )
                is PulseFeedUiState.Error ->
                    ErrorFrame(message = s.message, onRetry = { viewModel.refresh() })
            }
        }
        FeedComposeFAB(
            onClick = { onCompose(activeIntent) },
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = Spacing.s4, bottom = Spacing.s10),
        )
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: (() -> Unit)?,
) {
    Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appBg)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clickable(onClick = onBack)
                            .testTag("pulseBackButton"),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.ChevronLeft,
                        contentDescription = "Back",
                        size = 22.dp,
                        tint = PantopusColors.appText,
                    )
                }
                Spacer(modifier = Modifier.size(8.dp))
            }
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                modifier = Modifier.semantics { heading() },
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorder),
        )
    }
}

@Composable
private fun LoadingFrame() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s3).testTag("pulseFeedLoading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        FeedSkeletonCard()
        FeedSkeletonCard(withTitle = true)
        FeedSkeletonCard()
        FeedSkeletonCard()
    }
}

/**
 * Centered empty-state for a feed surface (Pulse radio glyph / Beacons rss
 * glyph). `internal` so the Pulse / Beacons snapshot tests can render it
 * directly from a [FeedSurface] descriptor.
 */
@Composable
internal fun FeedEmptyState(
    content: FeedEmptyContent,
    onCta: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(Spacing.s5)
                .testTag("pulseFeedEmpty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = content.icon,
                contentDescription = null,
                size = 32.dp,
                tint = PantopusColors.primary600,
            )
        }
        Spacer(modifier = Modifier.size(Spacing.s3))
        Text(
            text = content.headline,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(Spacing.s2))
        Text(
            text = content.body,
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.size(Spacing.s4))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onCta)
                    .padding(horizontal = 22.dp)
                    .height(44.dp)
                    .testTag("pulseEmptyCreatePost"),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                PantopusIconImage(
                    icon = content.ctaIcon,
                    contentDescription = null,
                    size = 15.dp,
                    tint = PantopusColors.appTextInverse,
                )
                Text(
                    text = content.ctaLabel,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appTextInverse,
                )
            }
        }
        val emphasis = content.footerEmphasis
        if (!emphasis.isNullOrEmpty()) {
            Spacer(modifier = Modifier.size(Spacing.s4))
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                PantopusIconImage(
                    icon = content.footerIcon,
                    contentDescription = null,
                    size = 13.dp,
                    tint = PantopusColors.appTextMuted,
                )
                Text(
                    text =
                        buildAnnotatedString {
                            append(content.footerLead)
                            withStyle(
                                SpanStyle(
                                    fontWeight = FontWeight.Bold,
                                    color = PantopusColors.appTextStrong,
                                ),
                            ) { append(emphasis) }
                            append(content.footerTrail)
                        },
                    fontSize = 11.5.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun PopulatedFrame(
    state: PulseFeedUiState.Loaded,
    onTapPost: (String) -> Unit,
    onTapReaction: (String) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
) {
    val pullState = rememberPullRefreshState(refreshing = isRefreshing, onRefresh = onRefresh)
    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullState)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("pulseFeedList"),
            contentPadding = PaddingValues(Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            items(items = state.rows, key = { it.id }) { row ->
                PulsePostCard(
                    content = row,
                    onTap = { onTapPost(row.id) },
                    onPrimaryReaction = { onTapReaction(row.id) },
                    onRSVP = if (row.attendees == null) null else ({ onTapReaction(row.id) }),
                )
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
        PullRefreshIndicator(
            refreshing = isRefreshing,
            state = pullState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = PantopusColors.primary600,
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
                .padding(Spacing.s5)
                .testTag("pulseFeedError"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 40.dp,
            tint = PantopusColors.error,
        )
        Spacer(modifier = Modifier.size(Spacing.s3))
        Text(
            text = "Couldn't load Pulse",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Spacer(modifier = Modifier.size(Spacing.s2))
        Text(
            text = message,
            fontSize = 13.5.sp,
            color = PantopusColors.appTextSecondary,
        )
        Spacer(modifier = Modifier.size(Spacing.s4))
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 22.dp)
                    .height(44.dp)
                    .testTag("pulseFeedRetry"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}
