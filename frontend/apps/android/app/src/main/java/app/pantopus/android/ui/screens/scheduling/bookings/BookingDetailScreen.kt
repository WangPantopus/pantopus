@file:Suppress(
    "PackageNaming",
    "MagicNumber",
    "LongMethod",
    "LongParameterList",
    "CyclomaticComplexMethod",
    "UNUSED_PARAMETER",
    "MatchingDeclarationName",
)
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.bookings

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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.components.StatusChip
import app.pantopus.android.ui.screens.scheduling._shared.ConflictAlternativesSheet
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

object BookingDetailTags {
    const val SCREEN = "bookingDetail"
    const val RESCHEDULE = "bookingDetailReschedule"
    const val APPROVE = "bookingDetailApprove"
    const val DECLINE = "bookingDetailDecline"
    const val CANCEL = "bookingDetailCancel"
    const val REASSIGN = "bookingDetailReassign"
}

@Composable
fun BookingDetailScreen(
    bookingId: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: BookingDetailViewModel = hiltViewModel(),
    rescheduleViewModel: RescheduleReassignViewModel = hiltViewModel(),
    cancelViewModel: CancelRefundViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val approveDecline by viewModel.approveDecline.collectAsStateWithLifecycle()
    val reschedule by rescheduleViewModel.state.collectAsStateWithLifecycle()
    val rescheduleCommitted by rescheduleViewModel.committed.collectAsStateWithLifecycle()
    val cancel by cancelViewModel.state.collectAsStateWithLifecycle()
    val cancelCommitted by cancelViewModel.committed.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) viewModel.start() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(rescheduleCommitted) {
        if (rescheduleCommitted) {
            viewModel.refresh()
            rescheduleViewModel.committedConsumed()
        }
    }
    LaunchedEffect(cancelCommitted) {
        if (cancelCommitted) {
            viewModel.refresh()
            cancelViewModel.committedConsumed()
        }
    }

    val data = (state as? BookingDetailUiState.Loaded)?.data

    Box(
        modifier =
            Modifier.fillMaxSize().background(
                PantopusColors.appBg,
            ).testTag(BookingDetailTags.SCREEN),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(
                data = data,
                onBack = onBack,
                onReschedule = { d ->
                    rescheduleViewModel.open(
                        d.owner,
                        d.pillar,
                        d.startUtc,
                        d.endUtc,
                        d.canReassign,
                        reassignOnly = false,
                    )
                },
                onReassign = { d ->
                    rescheduleViewModel.open(
                        d.owner,
                        d.pillar,
                        d.startUtc,
                        d.endUtc,
                        allowReassign = true,
                        reassignOnly = true,
                    )
                },
                onCancel = { d ->
                    cancelViewModel.open(
                        d.owner,
                        d.pillar,
                        "${d.eventName} · ${d.requesterName} · ${d.whenRange}",
                        d.hasPayment,
                    )
                },
                onDecline = viewModel::openDecline,
            )
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (val s = state) {
                    BookingDetailUiState.Loading -> DetailSkeleton()
                    is BookingDetailUiState.Error ->
                        ErrorState(
                            message = s.message,
                            onRetry = viewModel::load,
                        )
                    is BookingDetailUiState.Loaded -> DetailContent(s.data)
                }
            }
            if (data != null && data.isActive) {
                DetailDock(
                    data = data,
                    onApprove = viewModel::openApprove,
                    onDecline = viewModel::openDecline,
                    onReschedule = {
                        rescheduleViewModel.open(
                            data.owner,
                            data.pillar,
                            data.startUtc,
                            data.endUtc,
                            data.canReassign,
                            reassignOnly = false,
                        )
                    },
                )
            }
        }
    }

    // ─── Sheets ───────────────────────────────────────────────────────────────
    approveDecline?.let { sheet ->
        ApproveDeclineSheet(
            state = sheet,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = viewModel::dismissApproveDecline,
            onApprove = viewModel::approve,
            onExpandDecline = viewModel::expandDecline,
            onSelectReason = viewModel::selectReason,
            onSetNote = viewModel::setNote,
            onDecline = viewModel::declineConfirm,
        )
    }

    reschedule?.let { rs ->
        val conflict = rs.conflict
        if (conflict != null) {
            ConflictAlternativesSheet(
                conflict = conflict,
                onPick = rescheduleViewModel::pickAlternative,
                onPickAnotherTime = rescheduleViewModel::dismissConflict,
                onDismiss = rescheduleViewModel::dismissConflict,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                accent = rs.pillar.accent,
                body = "Here are the nearest open times for this booking.",
                showSavedNote = false,
            )
        } else {
            RescheduleReassignSheet(
                state = rs,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                onDismiss = rescheduleViewModel::dismiss,
                onSelectDay = rescheduleViewModel::selectDay,
                onSelectSlot = rescheduleViewModel::selectSlot,
                onSelectMember = rescheduleViewModel::selectMember,
                onSetAuthority = rescheduleViewModel::setAuthority,
                onToggleNotify = rescheduleViewModel::toggleNotify,
                onConfirm = rescheduleViewModel::confirm,
                onProposedDone = {
                    rescheduleViewModel.dismiss()
                    viewModel.refresh()
                },
            )
        }
    }

    cancel?.let { cs ->
        CancelRefundSheet(
            state = cs,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismiss = cancelViewModel::dismiss,
            onSelectReason = cancelViewModel::selectReason,
            onSetOther = cancelViewModel::setOther,
            onSetNote = cancelViewModel::setNote,
            onToggleNotify = cancelViewModel::toggleNotify,
            onConfirm = cancelViewModel::confirm,
        )
    }
}

// ─── Top bar ──────────────────────────────────────────────────────────────────

@Composable
private fun DetailTopBar(
    data: BookingDetailData?,
    onBack: () -> Unit,
    onReschedule: (BookingDetailData) -> Unit,
    onReassign: (BookingDetailData) -> Unit,
    onCancel: (BookingDetailData) -> Unit,
    onDecline: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .height(48.dp)
                .padding(horizontal = Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.size(
                    40.dp,
                ).clip(CircleShape).clickable(onClickLabel = "Back", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.ChevronLeft,
                contentDescription = "Back",
                size = 21.dp,
                tint = PantopusColors.appText,
            )
        }
        Spacer(Modifier.weight(1f))
        if (data != null) {
            val statusLabel =
                if (data.status == BookingStatus.Pending) "Pending approval" else data.statusLabel
            StatusChip(text = statusLabel, variant = data.statusVariant)
        }
        if (data != null && data.isActive) {
            Box {
                Box(
                    modifier =
                        Modifier.size(
                            40.dp,
                        ).clip(CircleShape).clickable(onClickLabel = "More actions") {
                            menuOpen = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.MoreVertical,
                        contentDescription = "More actions",
                        size = 19.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    if (data.status == BookingStatus.Pending) {
                        MenuItem(PantopusIcon.X, "Decline", danger = true) {
                            menuOpen = false
                            onDecline()
                        }
                    }
                    if (data.canReschedule) {
                        MenuItem(PantopusIcon.CalendarClock, "Reschedule") {
                            menuOpen = false
                            onReschedule(data)
                        }
                    }
                    if (data.canReassign) {
                        MenuItem(PantopusIcon.UserCheck, "Reassign") {
                            menuOpen = false
                            onReassign(data)
                        }
                    }
                    if (data.canCancel) {
                        MenuItem(PantopusIcon.XCircle, "Cancel booking", danger = true) {
                            menuOpen = false
                            onCancel(data)
                        }
                    }
                }
            }
        }
    }
    HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorder)
}

@Composable
private fun MenuItem(
    icon: PantopusIcon,
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        leadingIcon = {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 16.dp,
                tint = if (danger) PantopusColors.error else PantopusColors.appTextSecondary,
            )
        },
        text = {
            Text(
                text = label,
                fontSize = 13.sp,
                color = if (danger) PantopusColors.error else PantopusColors.appText,
            )
        },
        onClick = onClick,
    )
}

// ─── Content ──────────────────────────────────────────────────────────────────

@Composable
private fun DetailContent(data: BookingDetailData) {
    Column(
        modifier =
            Modifier.fillMaxSize().verticalScroll(
                rememberScrollState(),
            ).padding(bottom = Spacing.s12),
    ) {
        Header(data)
        when {
            data.cancelledNote != null ->
                Banner(
                    icon = PantopusIcon.CircleSlash,
                    tint = PantopusColors.appTextSecondary,
                    bg = PantopusColors.appSurfaceSunken,
                    text = "Cancelled · ${data.cancelledNote}",
                )
            data.status == BookingStatus.NoShow ->
                Banner(
                    icon = PantopusIcon.UserMinus,
                    tint = PantopusColors.error,
                    bg = PantopusColors.errorBg,
                    text = "Marked no-show · the invitee didn't attend",
                )
            data.rescheduled ->
                Banner(
                    icon = PantopusIcon.CalendarClock,
                    tint = PantopusColors.primary600,
                    bg = PantopusColors.primary50,
                    text = "This booking was rescheduled",
                )
        }
        RequesterCard(data)
        if (data.assignedHostInitials != null) {
            AssignedMemberCard(data)
        }
        data.location?.let { LocationCard(it, data.pillar.accent) }
        if (data.intakeAnswers.isNotEmpty()) {
            IntakeCard(data.intakeAnswers)
        }
        Spacer(Modifier.height(Spacing.s4))
    }
}

@Composable
private fun Header(data: BookingDetailData) {
    Column(modifier = Modifier.padding(horizontal = Spacing.s4, vertical = Spacing.s4)) {
        Text(
            text = data.eventName,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Text(
            text = data.whenRange,
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(top = Spacing.s1),
        )
        Row(
            modifier =
                Modifier
                    .padding(top = Spacing.s3)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(data.pillar.accentBg)
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(data.pillar.accent))
            Text(
                text = data.ownerLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = data.pillar.accent,
            )
        }
    }
}

@Composable
private fun Banner(
    icon: PantopusIcon,
    tint: Color,
    bg: Color,
    text: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .padding(bottom = Spacing.s3)
                .clip(RoundedCornerShape(Radii.lg))
                .background(bg)
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 16.dp, tint = tint)
        Text(text = text, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

@Composable
private fun DetailCard(content: @Composable () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s4)
                .padding(bottom = Spacing.s3)
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
    ) {
        content()
    }
}

@Composable
private fun CardOverline(
    icon: PantopusIcon,
    text: String,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        modifier = Modifier.padding(bottom = Spacing.s2),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 13.dp, tint = accent)
        Text(
            text = text.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun RequesterCard(data: BookingDetailData) {
    DetailCard {
        CardOverline(PantopusIcon.User, "Requester", data.pillar.accent)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            BookingAvatar(pillar = data.pillar, initials = data.requesterInitials, size = 40.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = data.requesterName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                Text(
                    text = data.requesterSub,
                    fontSize = 11.5.sp,
                    color = PantopusColors.appTextMuted,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun AssignedMemberCard(data: BookingDetailData) {
    DetailCard {
        CardOverline(PantopusIcon.UserRound, "Assigned member", data.pillar.accent)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Box(
                modifier =
                    Modifier.size(
                        30.dp,
                    ).clip(CircleShape).background(PantopusColors.businessBg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = data.assignedHostInitials.orEmpty(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.business,
                )
            }
            Text(
                text = "Team member",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LocationCard(
    location: LocationInfo,
    accent: Color,
) {
    DetailCard {
        CardOverline(location.icon, "Location", accent)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(
                modifier =
                    Modifier.size(
                        34.dp,
                    ).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = location.icon,
                    contentDescription = null,
                    size = 16.dp,
                    tint = accent,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                Text(
                    text = location.sub,
                    fontSize = 11.sp,
                    color = PantopusColors.appTextMuted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }
    }
}

@Composable
private fun IntakeCard(answers: List<IntakeAnswer>) {
    var expanded by remember { mutableStateOf(false) }
    DetailCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Box(
                modifier =
                    Modifier.size(
                        34.dp,
                    ).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ClipboardList,
                    contentDescription = null,
                    size = 16.dp,
                    tint = PantopusColors.appTextSecondary,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Intake answers",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                Text(
                    text = "${answers.size} answers",
                    fontSize = 11.sp,
                    color = PantopusColors.appTextMuted,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            PantopusIconImage(
                icon = if (expanded) PantopusIcon.ChevronUp else PantopusIcon.ChevronDown,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.appTextMuted,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(Spacing.s2))
            answers.forEach { answer ->
                Column(modifier = Modifier.padding(top = Spacing.s2)) {
                    Text(
                        text = answer.label,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appTextSecondary,
                    )
                    Text(
                        text = answer.value,
                        fontSize = 12.5.sp,
                        color = PantopusColors.appText,
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
        }
    }
}

// ─── Dock ─────────────────────────────────────────────────────────────────────

@Composable
private fun DetailDock(
    data: BookingDetailData,
    onApprove: () -> Unit,
    onDecline: () -> Unit,
    onReschedule: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
    ) {
        HorizontalDivider(
            thickness = 1.dp,
            color = PantopusColors.appBorder,
            modifier = Modifier.padding(bottom = Spacing.s3),
        )
        if (data.status == BookingStatus.Pending) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PillarOutlineButton(
                    label = "Decline",
                    leadingIcon = PantopusIcon.X,
                    danger = true,
                    onClick = onDecline,
                    modifier = Modifier.weight(1f).testTag(BookingDetailTags.DECLINE),
                )
                PillarFilledButton(
                    label = "Approve",
                    accent = data.pillar.accent,
                    leadingIcon = PantopusIcon.Check,
                    onClick = onApprove,
                    modifier = Modifier.weight(1f).testTag(BookingDetailTags.APPROVE),
                )
            }
        } else {
            PillarFilledButton(
                label = "Reschedule",
                accent = data.pillar.accent,
                leadingIcon = PantopusIcon.CalendarClock,
                onClick = onReschedule,
                modifier = Modifier.testTag(BookingDetailTags.RESCHEDULE),
            )
        }
    }
}

// ─── Skeleton ─────────────────────────────────────────────────────────────────

@Composable
private fun DetailSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Shimmer(width = 220.dp, height = 22.dp, cornerRadius = Radii.sm)
        Shimmer(width = 160.dp, height = 12.dp, cornerRadius = Radii.xs)
        Shimmer(width = 90.dp, height = 22.dp, cornerRadius = Radii.pill)
        Spacer(Modifier.height(Spacing.s2))
        repeat(3) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.xl))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                        .padding(Spacing.s3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
            ) {
                Shimmer(width = 40.dp, height = 40.dp, cornerRadius = Radii.pill)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    Shimmer(width = 140.dp, height = 11.dp, cornerRadius = Radii.xs)
                    Shimmer(width = 90.dp, height = 9.dp, cornerRadius = Radii.xs)
                }
            }
        }
    }
}
