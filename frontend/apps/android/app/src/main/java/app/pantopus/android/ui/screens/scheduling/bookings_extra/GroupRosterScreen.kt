@file:Suppress("PackageNaming", "LongMethod", "LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod", "LargeClass", "MatchingDeclarationName")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val ROSTER_TAG = "scheduling.roster"
const val ROSTER_MESSAGE_ALL_TAG = "scheduling.roster.messageAll"

@Composable
fun GroupRosterScreen(
    bookingId: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: GroupRosterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val noShow by viewModel.noShow.collectAsStateWithLifecycle()
    val nudge by viewModel.nudge.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val navRequest by viewModel.navRequest.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(navRequest) {
        navRequest?.let {
            onNavigate(it)
            viewModel.navRequestConsumed()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.toastConsumed()
        }
    }

    var menuOpen by remember { mutableStateOf(false) }
    val loaded = state as? GroupRosterUiState.Loaded

    Scaffold(
        modifier = Modifier.testTag(ROSTER_TAG),
        containerColor = PantopusColors.appBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Roster", style = PantopusTextStyle.h3, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        PantopusIconImage(icon = PantopusIcon.ChevronLeft, contentDescription = "Back", size = 22.dp, tint = PantopusColors.appText)
                    }
                },
                actions = {
                    if (loaded != null) {
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                PantopusIconImage(icon = PantopusIcon.MoreVertical, contentDescription = "More", size = 20.dp, tint = PantopusColors.appText)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Add or invite attendee") },
                                    onClick = {
                                        menuOpen = false
                                        viewModel.openAddAttendee()
                                    },
                                )
                                if (loaded.data.canMarkNoShow) {
                                    DropdownMenuItem(
                                        text = { Text("Mark no-show") },
                                        onClick = {
                                            menuOpen = false
                                            viewModel.openNoShow()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PantopusColors.appSurface),
            )
        },
        floatingActionButton = {
            if (loaded != null) {
                ExtendedFloatingActionButton(
                    onClick = { viewModel.openNudge() },
                    containerColor = loaded.data.pillar.accent,
                    contentColor = PantopusColors.appTextInverse,
                    modifier = Modifier.testTag(ROSTER_MESSAGE_ALL_TAG),
                    icon = { PantopusIconImage(icon = PantopusIcon.MessageSquare, contentDescription = null, size = 18.dp, tint = PantopusColors.appTextInverse) },
                    text = { Text("Message all") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is GroupRosterUiState.Loading -> SchedulingLoadingSkeleton(rows = 4)
                is GroupRosterUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is GroupRosterUiState.Empty ->
                    EmptyState(
                        icon = PantopusIcon.Users,
                        headline = "No signups yet",
                        subcopy = "Share the booking link to fill seats.",
                        ctaTitle = "Share booking link",
                        onCta = s.onShareLink,
                    )
                is GroupRosterUiState.Loaded ->
                    RosterContent(
                        data = s.data,
                        onPromote = viewModel::promote,
                        onAdjustCapacity = viewModel::adjustCapacity,
                        onAddAttendee = viewModel::openAddAttendee,
                        onRowNoShow = viewModel::openNoShowFor,
                    )
            }
        }
    }

    noShow?.let { sheet ->
        NoShowSheet(
            state = sheet,
            onToggle = viewModel::toggleNoShow,
            onNoteChange = viewModel::setNoShowNote,
            onConfirm = viewModel::confirmNoShow,
            onDismiss = viewModel::dismissNoShow,
        )
    }
    nudge?.let { sheet ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        NudgeSheet(
            state = sheet,
            sheetState = sheetState,
            accent = loaded?.data?.pillar?.accent ?: PantopusColors.primary600,
            onMessageChange = viewModel::setNudgeMessage,
            onAudience = viewModel::setNudgeAudience,
            onPushChange = viewModel::setNudgePush,
            onEmailChange = viewModel::setNudgeEmail,
            onUseTemplate = viewModel::openTemplatePicker,
            onTemplatePicked = viewModel::applyTemplate,
            onTemplateDismiss = viewModel::dismissTemplatePicker,
            onSend = viewModel::confirmNudge,
            onDismiss = viewModel::dismissNudge,
        )
    }
}

@Composable
internal fun RosterContent(
    data: RosterData,
    onPromote: (String) -> Unit,
    onAdjustCapacity: (Int) -> Unit,
    onAddAttendee: () -> Unit,
    onRowNoShow: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        CapacityHeaderCard(
            filled = data.filled,
            total = data.seatTotal,
            waiting = data.waiting,
            accent = data.pillar.accent,
            confirmed = data.confirmed,
            pending = data.pending,
        )

        if (data.seated.isNotEmpty()) {
            ExtrasOverline("Seated · ${data.seated.size}")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                data.seated.forEach { person ->
                    RosterRow(person = person, verified = true) {
                        StatusChip(status = person.status)
                        SeatedKebab(person = person, onRowNoShow = onRowNoShow)
                    }
                }
            }
        }

        if (data.waitlist.isNotEmpty()) {
            val openLabel = if (data.seatsOpen == 1) "1 seat open" else "${data.seatsOpen} seats open"
            val sectionLabel = if (data.seatsOpen > 0) "Waitlist · ${data.waitlist.size} · $openLabel" else "Waitlist · ${data.waitlist.size}"
            ExtrasOverline(sectionLabel)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                data.waitlist.forEach { person ->
                    RosterRow(person = person) {
                        PromoteSeatButton(enabled = data.seatsOpen > 0, accent = data.pillar.accent, onClick = { onPromote(person.id) })
                    }
                }
            }
            if (data.seatsOpen == 0) {
                Text(text = "Open a seat to promote", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
        }

        CapacityControls(seatTotal = data.seatTotal, onAdjustCapacity = onAdjustCapacity, onAddAttendee = onAddAttendee)
    }
}

@Composable
private fun SeatedKebab(
    person: RosterPerson,
    onRowNoShow: (String, String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(28.dp)) {
            PantopusIconImage(icon = PantopusIcon.MoreVertical, contentDescription = "Row actions", size = 18.dp, tint = PantopusColors.appTextMuted)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (person.status == "confirmed") {
                DropdownMenuItem(
                    text = { Text("Mark no-show") },
                    onClick = {
                        open = false
                        onRowNoShow(person.id, person.name)
                    },
                )
            } else {
                DropdownMenuItem(text = { Text("Pending approval") }, onClick = { open = false })
            }
        }
    }
}

@Composable
private fun CapacityControls(
    seatTotal: Int,
    onAdjustCapacity: (Int) -> Unit,
    onAddAttendee: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "Capacity", style = PantopusTextStyle.body, fontWeight = FontWeight.Medium, color = PantopusColors.appText, modifier = Modifier.weight(1f))
            StepperButton(icon = PantopusIcon.Minus, contentDescription = "Decrease capacity", onClick = { onAdjustCapacity(-1) })
            Text(text = seatTotal.toString(), style = PantopusTextStyle.body, fontWeight = FontWeight.Bold, color = PantopusColors.appText, modifier = Modifier.padding(horizontal = Spacing.s3))
            StepperButton(icon = PantopusIcon.Plus, contentDescription = "Increase capacity", onClick = { onAdjustCapacity(1) })
        }
        PrimaryButton(title = "Add or invite attendee", onClick = onAddAttendee, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun StepperButton(
    icon: PantopusIcon,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceSunken)
                .clickable(onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = icon, contentDescription = contentDescription, size = 16.dp, tint = PantopusColors.appTextStrong)
    }
}
