@file:Suppress("PackageNaming", "LongMethod", "MagicNumber", "TooManyFunctions")

package app.pantopus.android.ui.screens.support_trains.detail

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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.SlotCalendar
import app.pantopus.android.ui.components.SlotCalendarDay
import app.pantopus.android.ui.screens.support_trains.detail.components.RecipientCard
import app.pantopus.android.ui.screens.support_trains.detail.components.SlotRow
import app.pantopus.android.ui.screens.support_trains.detail.components.TypeDatesCard
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusElevations
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.pantopusShadow

/**
 * A10.9 — Participant-facing Support Train detail screen.
 *
 * Hilt entry point. Reads the train id from [SavedStateHandle] via
 * the view-model, drives the four-state machine (loading / loaded /
 * error), and surfaces the same callbacks as the iOS shell — back,
 * share, sign-up, edit-slot, send-card, join-as-backup, message-host,
 * open-manage (organizer dock overflow).
 */
@Composable
fun SupportTrainDetailScreen(
    onBack: () -> Unit,
    onOpenManage: () -> Unit = {},
    onShare: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onEditSlot: (SlotRowContent) -> Unit = {},
    onSendCard: () -> Unit = {},
    onJoinAsBackup: () -> Unit = {},
    onMessageHost: () -> Unit = {},
    isOrganizer: Boolean = false,
    viewModel: SupportTrainDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }

    SupportTrainDetailContentLayout(
        state = state,
        isOrganizer = isOrganizer,
        onBack = onBack,
        onOpenManage = onOpenManage,
        onShare = onShare,
        onSignUp = onSignUp,
        onEditSlot = onEditSlot,
        onSendCard = onSendCard,
        onJoinAsBackup = onJoinAsBackup,
        onMessageHost = onMessageHost,
        onRetry = { viewModel.refresh() },
    )
}

/**
 * Stateless layout. Used by previews + Paparazzi snapshot baselines —
 * the VM is injected from the public composable above.
 */
@Composable
internal fun SupportTrainDetailContentLayout(
    state: SupportTrainDetailUiState,
    isOrganizer: Boolean = false,
    onBack: () -> Unit = {},
    onOpenManage: () -> Unit = {},
    onShare: () -> Unit = {},
    onSignUp: () -> Unit = {},
    onEditSlot: (SlotRowContent) -> Unit = {},
    onSendCard: () -> Unit = {},
    onJoinAsBackup: () -> Unit = {},
    onMessageHost: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("supportTrainDetail"),
    ) {
        TopBar(
            isOrganizer = isOrganizer,
            onBack = onBack,
            onShare = onShare,
            onOpenManage = onOpenManage,
            onMessageHost = onMessageHost,
        )
        when (state) {
            SupportTrainDetailUiState.Loading -> LoadingShell()
            is SupportTrainDetailUiState.Loaded ->
                LoadedBody(
                    content = state.content,
                    onSignUp = onSignUp,
                    onEditSlot = onEditSlot,
                    onSendCard = onSendCard,
                    onJoinAsBackup = onJoinAsBackup,
                    onMessageHost = onMessageHost,
                )
            is SupportTrainDetailUiState.Error ->
                EmptyState(
                    icon = PantopusIcon.AlertCircle,
                    headline = "Couldn't load support train",
                    subcopy = state.message,
                    modifier = Modifier.testTag("supportTrainDetailError"),
                    ctaTitle = "Try again",
                    onCta = onRetry,
                )
        }
    }
}

// MARK: - Top bar

@Composable
private fun TopBar(
    isOrganizer: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onOpenManage: () -> Unit,
    onMessageHost: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(PantopusColors.appSurface),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Support train",
            color = PantopusColors.appText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.semantics { heading() },
        )
        TopBarButton(
            icon = PantopusIcon.ChevronLeft,
            label = "Back",
            tag = "supportTrainDetailBackButton",
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = Spacing.s2),
        )
        Row(modifier = Modifier.align(Alignment.CenterEnd).padding(end = Spacing.s2)) {
            TopBarButton(
                icon = PantopusIcon.Share,
                label = "Share train",
                tag = "supportTrainDetailShareButton",
                onClick = onShare,
            )
            OverflowMenuButton(
                isOrganizer = isOrganizer,
                onOpenManage = onOpenManage,
                onMessageHost = onMessageHost,
            )
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
private fun TopBarButton(
    icon: PantopusIcon,
    label: String,
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag(tag)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 22.dp,
            tint = PantopusColors.appText,
        )
    }
}

/**
 * Overflow menu placeholder — surfaces `Manage signups` for
 * organizers + `Message the host` for everyone. Full Material
 * `DropdownMenu` wiring lands with the manage flow; today the icon
 * cycles through the actions on tap so the affordance stays clickable
 * for accessibility tests.
 */
@Composable
private fun OverflowMenuButton(
    isOrganizer: Boolean,
    onOpenManage: () -> Unit,
    onMessageHost: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable {
                    // Default action is `Message the host`; the
                    // `Manage signups` action is surfaced by the
                    // Hub-tab host as a separate route push for
                    // organizers (see RootTabScreen.kt).
                    if (isOrganizer) onOpenManage() else onMessageHost()
                }
                .testTag("supportTrainDetailMoreButton")
                .semantics {
                    contentDescription = "More options"
                    role = Role.Button
                },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.MoreHorizontal,
            contentDescription = null,
            size = 22.dp,
            tint = PantopusColors.appText,
        )
    }
}

// MARK: - Loaded body

@Composable
private fun LoadedBody(
    content: SupportTrainDetailContent,
    onSignUp: () -> Unit,
    onEditSlot: (SlotRowContent) -> Unit,
    onSendCard: () -> Unit,
    onJoinAsBackup: () -> Unit,
    onMessageHost: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4)
                    .padding(bottom = Spacing.s6),
            verticalArrangement = Arrangement.spacedBy(Spacing.s0),
        ) {
            content.celebrationBanner?.let {
                Spacer(modifier = Modifier.height(Spacing.s3))
                CelebrationBannerView(it)
                Spacer(modifier = Modifier.height(Spacing.s1))
            }

            SectionOverline("For")
            RecipientCard(content.recipient)

            SectionOverline("The train")
            TypeDatesCard(content.typeDates)

            SectionOverline("Slot calendar")
            CalendarCard(content.calendarDays, onSelectDate = { onSignUp() })

            content.sections.forEach { section ->
                SectionOverline(section.overline, actionLabel = section.actionLabel)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    section.rows.forEach { row ->
                        SlotRow(
                            content = row,
                            onSignUp = if (row.state == SlotRowState.Open) onSignUp else null,
                            onEdit = if (row.mine) { { onEditSlot(row) } } else null,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.s3))
            HostedByRow(content.hostedBy, onMessageHost = onMessageHost)
            Spacer(modifier = Modifier.height(Spacing.s3))
        }

        Dock(
            dock = content.dock,
            onSignUp = onSignUp,
            onSendCard = onSendCard,
            onJoinAsBackup = onJoinAsBackup,
        )
    }
}

@Composable
private fun SectionOverline(
    label: String,
    actionLabel: String? = null,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = Spacing.s4, bottom = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            color = PantopusColors.appTextSecondary,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.weight(1f))
        if (actionLabel != null) {
            Text(
                text = actionLabel,
                color = PantopusColors.primary600,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier =
                    Modifier
                        .clickable { /* See-all wiring is a follow-up */ }
                        .padding(Spacing.s1)
                        .testTag("supportTrainSeeAll-$label"),
            )
        }
    }
}

@Composable
private fun CalendarCard(
    days: List<SlotCalendarDay>,
    onSelectDate: (java.util.Date) -> Unit,
) {
    val shape = RoundedCornerShape(Radii.lg)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .pantopusShadow(PantopusElevations.sm, shape)
                .clip(shape)
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, shape)
                .padding(Spacing.s3),
        contentAlignment = Alignment.Center,
    ) {
        SlotCalendar(days = days, onSelectDate = onSelectDate)
    }
}

// MARK: - Hosted by + banner

@Composable
private fun CelebrationBannerView(content: CelebrationBanner) {
    val shape = RoundedCornerShape(Radii.lg)
    Row(
        modifier =
            Modifier
                .testTag("supportTrainCelebrationBanner")
                .fillMaxWidth()
                .clip(shape)
                .background(PantopusColors.successBg)
                .border(1.dp, PantopusColors.successLight, shape)
                .padding(Spacing.s3),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.success),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.PartyPopper,
                contentDescription = null,
                size = 18.dp,
                strokeWidth = 2.2f,
                tint = PantopusColors.appTextInverse,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.weight(1f)) {
            Text(
                text = content.title,
                color = PantopusColors.success,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp,
            )
            Text(
                text = content.body,
                color = PantopusColors.success,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun HostedByRow(
    content: HostedByFooter,
    onMessageHost: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.md)
    Row(
        modifier =
            Modifier
                .testTag("supportTrainHostedBy")
                .fillMaxWidth()
                .clip(shape)
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorderSubtle, shape)
                .clickable { onMessageHost() }
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .semantics {
                    role = Role.Button
                    contentDescription =
                        "Hosted by ${content.organizerDisplayName}${content.neighborHint?.let { ", $it" } ?: ""}"
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Box(
            modifier =
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(PantopusColors.errorLight, PantopusColors.error))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = content.organizerInitials,
                color = PantopusColors.appTextInverse,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
            )
        }
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Hosted by ",
                color = PantopusColors.appTextSecondary,
                fontSize = 12.sp,
            )
            Text(
                text = content.organizerDisplayName,
                color = PantopusColors.appTextStrong,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
            content.neighborHint?.let {
                Text(
                    text = " · $it",
                    color = PantopusColors.appTextSecondary,
                    fontSize = 12.sp,
                )
            }
        }
        PantopusIconImage(
            icon = PantopusIcon.MessageSquare,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.appTextMuted,
        )
    }
}

// MARK: - Dock

@Composable
private fun Dock(
    dock: SupportTrainDock,
    onSignUp: () -> Unit,
    onSendCard: () -> Unit,
    onJoinAsBackup: () -> Unit,
) {
    Column {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorderSubtle),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(PantopusColors.appBg)
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        ) {
            when (dock) {
                is SupportTrainDock.SignUp -> PrimarySignUpCTA(dock.label, onSignUp)
                SupportTrainDock.SendCardAndBackup -> SplitCoveredDock(onSendCard, onJoinAsBackup)
            }
        }
    }
}

@Composable
private fun PrimarySignUpCTA(
    label: String,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(Radii.lg)
    Row(
        modifier =
            Modifier
                .testTag("supportTrainSignUpCTA")
                .fillMaxWidth()
                .height(50.dp)
                .pantopusShadow(PantopusElevations.primary, shape)
                .clip(shape)
                .background(PantopusColors.primary600)
                .clickable { onTap() }
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PantopusIconImage(
            icon = PantopusIcon.Calendar,
            contentDescription = null,
            size = 17.dp,
            tint = PantopusColors.appTextInverse,
        )
        Text(
            text = label,
            color = PantopusColors.appTextInverse,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun SplitCoveredDock(
    onSendCard: () -> Unit,
    onJoinAsBackup: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        DockSecondary(
            label = "Send a card",
            icon = PantopusIcon.Mail,
            tag = "supportTrainSendCardCTA",
            onTap = onSendCard,
            modifier = Modifier.weight(1f),
        )
        DockPrimary(
            label = "Join as backup",
            icon = PantopusIcon.UserPlus,
            tag = "supportTrainJoinBackupCTA",
            onTap = onJoinAsBackup,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DockSecondary(
    label: String,
    icon: PantopusIcon,
    tag: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.md)
    Row(
        modifier =
            modifier
                .testTag(tag)
                .height(46.dp)
                .clip(shape)
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, shape)
                .clickable { onTap() }
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.appText,
        )
        Text(
            text = label,
            color = PantopusColors.appText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DockPrimary(
    label: String,
    icon: PantopusIcon,
    tag: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(Radii.md)
    Row(
        modifier =
            modifier
                .testTag(tag)
                .height(46.dp)
                .pantopusShadow(PantopusElevations.primary, shape)
                .clip(shape)
                .background(PantopusColors.primary600)
                .clickable { onTap() }
                .semantics {
                    role = Role.Button
                    contentDescription = label
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Spacer(modifier = Modifier.weight(1f))
        PantopusIconImage(
            icon = icon,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.appTextInverse,
        )
        Text(
            text = label,
            color = PantopusColors.appTextInverse,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
    }
}

// MARK: - Loading

@Composable
private fun LoadingShell() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.s4)
                .padding(top = Spacing.s3)
                .testTag("supportTrainDetailLoading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        SkeletonBlock(100.dp)
        SkeletonBlock(130.dp)
        SkeletonBlock(240.dp)
        SkeletonBlock(64.dp)
        SkeletonBlock(64.dp)
    }
}

@Composable
private fun SkeletonBlock(height: androidx.compose.ui.unit.Dp) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceSunken),
    )
}

// MARK: - Previews

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun PopulatedPreview() {
    SupportTrainDetailContentLayout(
        state = SupportTrainDetailUiState.Loaded(SupportTrainDetailSampleData.populated),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun FullyCoveredPreview() {
    SupportTrainDetailContentLayout(
        state = SupportTrainDetailUiState.Loaded(SupportTrainDetailSampleData.fullyCovered),
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun LoadingPreview() {
    SupportTrainDetailContentLayout(state = SupportTrainDetailUiState.Loading)
}

@Preview(showBackground = true, widthDp = 360, heightDp = 800)
@Composable
private fun ErrorPreview() {
    SupportTrainDetailContentLayout(
        state = SupportTrainDetailUiState.Error("Network unavailable."),
    )
}
