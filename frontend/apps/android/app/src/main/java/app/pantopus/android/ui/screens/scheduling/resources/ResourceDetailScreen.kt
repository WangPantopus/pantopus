@file:Suppress(
    "PackageNaming",
    "UNUSED_PARAMETER",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MagicNumber",
)
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.pantopus.android.ui.screens.scheduling.resources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingRoutes
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val RESOURCE_DETAIL_TAG = "scheduling.resourceDetail"
const val RESOURCE_DETAIL_EDIT_TAG = "scheduling.resourceDetail.edit"

/** F11 Resource Detail / Booking Calendar. */
@Composable
fun ResourceDetailScreen(
    resourceId: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: ResourceDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }

    val loaded = state as? ResourceDetailUiState.Loaded
    val title = loaded?.resourceName?.takeIf { it.isNotBlank() } ?: "Resource"

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(RESOURCE_DETAIL_TAG),
        containerColor = PantopusColors.appBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        PantopusIconImage(
                            icon = PantopusIcon.ChevronLeft,
                            contentDescription = "Back",
                            tint = PantopusColors.appText,
                        )
                    }
                },
                actions = {
                    if (loaded != null) {
                        TextButton(
                            onClick = {
                                onNavigate(SchedulingRoutes.resourceEditor(viewModel.resourceId))
                            },
                            modifier = Modifier.testTag(RESOURCE_DETAIL_EDIT_TAG),
                        ) {
                            Text(
                                "Edit",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PantopusColors.home,
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = PantopusColors.appSurface,
                    ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                ResourceDetailUiState.Loading -> SchedulingLoadingSkeleton(rows = 4)
                is ResourceDetailUiState.Error ->
                    ErrorState(
                        headline = "Couldn't load this resource",
                        message = s.message,
                        onRetry = viewModel::load,
                    )
                is ResourceDetailUiState.Loaded ->
                    DetailLoaded(
                        loaded = s,
                        onApprove = viewModel::approve,
                        onDecline = viewModel::decline,
                        onBookThis = {
                            onNavigate(SchedulingRoutes.bookResource(viewModel.resourceId))
                        },
                    )
            }
        }
    }

    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearActionError,
            confirmButton = { TextButton(onClick = viewModel::clearActionError) { Text("OK") } },
            title = { Text("Couldn't update") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun DetailLoaded(
    loaded: ResourceDetailUiState.Loaded,
    onApprove: (String) -> Unit,
    onDecline: (String) -> Unit,
    onBookThis: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).verticalScroll(rememberScrollState())
                    .padding(Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            HeaderCard(loaded)
            if (loaded.approvals.isNotEmpty()) {
                ApprovalQueueCard(loaded.approvals, onApprove, onDecline)
            }
            ResourceOverlineLabel(text = "Upcoming bookings")
            if (loaded.sections.isEmpty()) {
                SectionCard {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                    ) {
                        PantopusIconImage(
                            icon = PantopusIcon.Calendar,
                            contentDescription = null,
                            size = 16.dp,
                            tint = PantopusColors.appTextMuted,
                        )
                        Text(
                            "No upcoming bookings yet.",
                            fontSize = 13.sp,
                            color = PantopusColors.appTextSecondary,
                        )
                    }
                }
            } else {
                loaded.sections.forEach { section ->
                    Text(
                        section.title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appTextSecondary,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    section.rows.forEach { BookingRow(it) }
                }
            }
        }
        StickyFooter {
            HomePrimaryButton(title = "Book this", icon = PantopusIcon.Plus, onClick = onBookThis)
        }
    }
}

@Composable
private fun HeaderCard(loaded: ResourceDetailUiState.Loaded) {
    SectionCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(
                            46.dp,
                        ).clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.homeBg),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = loaded.kind.icon,
                    contentDescription = null,
                    size = 23.dp,
                    tint = PantopusColors.home,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Text(
                    loaded.resourceName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                TypeBadge(text = loaded.kind.label)
            }
        }
        if (loaded.ruleChips.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                loaded.ruleChips.forEach {
                    RuleChipView(icon = it.icon, text = it.text, home = true)
                }
            }
        }
    }
}

@Composable
private fun ApprovalQueueCard(
    approvals: List<ResourceApproval>,
    onApprove: (String) -> Unit,
    onDecline: (String) -> Unit,
) {
    SectionCard(overline = "Approval queue · ${approvals.size}") {
        approvals.forEachIndexed { index, approval ->
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    MemberOrInitials(approval.member, approval.who, size = 30.dp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            approval.who,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = PantopusColors.appText,
                        )
                        Text(
                            approval.whenText,
                            fontSize = 11.sp,
                            color = PantopusColors.appTextSecondary,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    InlineHomeButton(title = "Approve", icon = PantopusIcon.Check, filled = true, onClick = {
                        onApprove(approval.id)
                    }, modifier = Modifier.weight(1f))
                    InlineHomeButton(title = "Decline", icon = PantopusIcon.X, filled = false, onClick = {
                        onDecline(approval.id)
                    }, modifier = Modifier.weight(1f))
                }
            }
            if (index < approvals.size - 1) {
                HorizontalDivider(
                    color = PantopusColors.appBorder,
                    modifier = Modifier.padding(vertical = Spacing.s1),
                )
            }
        }
    }
}

@Composable
private fun BookingRow(row: ResourceBookingRow) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(
                        if (row.isPending) PantopusColors.warning else PantopusColors.success,
                    ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                row.timeRange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
            Text("For: ${row.who}", fontSize = 11.sp, color = PantopusColors.appTextSecondary)
        }
        MemberOrInitials(row.member, row.who, size = 26.dp)
    }
}

@Composable
private fun MemberOrInitials(
    member: HomeMember?,
    fallback: String,
    size: androidx.compose.ui.unit.Dp,
) {
    HomeMemberAvatar(member = member ?: HomeMember(id = fallback, name = fallback), size = size)
}

@Composable
private fun StickyFooter(content: @Composable () -> Unit) {
    Column {
        HorizontalDivider(color = PantopusColors.appBorderSubtle)
        Box(
            modifier =
                Modifier
                    .background(
                        PantopusColors.appSurface,
                    ).fillMaxWidth()
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
        ) {
            content()
        }
    }
}
