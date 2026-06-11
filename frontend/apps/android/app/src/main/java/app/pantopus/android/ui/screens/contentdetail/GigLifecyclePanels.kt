@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.contentdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.api.models.gigs.CancelGigReason
import app.pantopus.android.data.api.models.gigs.CancellationPreviewResponse
import app.pantopus.android.data.api.models.gigs.GigBidDto
import app.pantopus.android.data.api.models.gigs.GigReportReason
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.util.Locale

/**
 * Phase 5 — gig detail lifecycle sections rendered in the shell's
 * scroll footer: the owner bids panel (work item 1), the active-task
 * panel (work item 4), and the review affordance (work item 5).
 * Counter / reject / review / no-show sheets are owned here; the
 * top-bar report + cancel sheets stay in [GigDetailScreen].
 */
@Composable
fun GigLifecycleSections(viewModel: GigDetailViewModel) {
    val bids by viewModel.bids.collectAsStateWithLifecycle()
    val bidActionInFlight by viewModel.bidActionInFlight.collectAsStateWithLifecycle()
    val activeTask by viewModel.activeTask.collectAsStateWithLifecycle()
    val reviewState by viewModel.reviewState.collectAsStateWithLifecycle()

    var counterTarget by remember { mutableStateOf<GigBidDto?>(null) }
    var rejectTarget by remember { mutableStateOf<GigBidDto?>(null) }
    var noShowSheetVisible by remember { mutableStateOf(false) }

    val gig = viewModel.gigSnapshot()
    val ownerSeesBidsPanel =
        viewModel.viewerIsOwner() && gig?.status?.lowercase() == "open"

    if (ownerSeesBidsPanel) {
        GigOwnerBidsPanel(
            bids = bids,
            actionInFlightBidId = bidActionInFlight,
            onAccept = { viewModel.acceptBidAsOwner(it.id) },
            onCounter = { counterTarget = it },
            onReject = { rejectTarget = it },
        )
    }

    activeTask?.let { panel ->
        GigActiveTaskPanel(
            panel = panel,
            onWorkerAck = { viewModel.workerAck() },
            onStartTask = { viewModel.startTask() },
            onConfirmCompletion = { viewModel.confirmCompletion() },
            onReportNoShow = { noShowSheetVisible = true },
        )
    }

    GigReviewSection(
        state = reviewState,
        onSubmit = { rating, comment -> viewModel.submitGigReview(rating, comment) },
    )

    counterTarget?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { counterTarget = null }, sheetState = sheetState) {
            GigCounterSheetContent(
                bid = target,
                onSubmit = { amount, message ->
                    viewModel.counterBidAsOwner(target.id, amount, message) { ok ->
                        if (ok) counterTarget = null
                    }
                },
                onCancel = { counterTarget = null },
            )
        }
    }

    rejectTarget?.let { target ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { rejectTarget = null }, sheetState = sheetState) {
            GigRejectConfirmContent(
                bid = target,
                onConfirm = {
                    viewModel.rejectBidAsOwner(target.id)
                    rejectTarget = null
                },
                onCancel = { rejectTarget = null },
            )
        }
    }

    if (noShowSheetVisible) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { noShowSheetVisible = false }, sheetState = sheetState) {
            GigNoShowSheetContent(
                onSubmit = { description ->
                    viewModel.reportNoShow(description) { ok ->
                        if (ok) noShowSheetVisible = false
                    }
                },
                onCancel = { noShowSheetVisible = false },
            )
        }
    }
}

// MARK: - Work item 1 · owner bids panel

@Composable
private fun GigOwnerBidsPanel(
    bids: List<GigBidDto>,
    actionInFlightBidId: String?,
    onAccept: (GigBidDto) -> Unit,
    onCounter: (GigBidDto) -> Unit,
    onReject: (GigBidDto) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s5, vertical = Spacing.s4)
                .testTag("gigDetail.bids"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text(
            text = "Bids on your task",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        if (bids.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(Spacing.s4),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "No bids yet",
                    fontSize = 13.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
        } else {
            bids.forEach { bid ->
                GigOwnerBidRow(
                    bid = bid,
                    busy = actionInFlightBidId != null,
                    onAccept = { onAccept(bid) },
                    onCounter = { onCounter(bid) },
                    onReject = { onReject(bid) },
                )
            }
        }
    }
}

@Composable
private fun GigOwnerBidRow(
    bid: GigBidDto,
    busy: Boolean,
    onAccept: () -> Unit,
    onCounter: () -> Unit,
    onReject: () -> Unit,
) {
    val identity = bid.bidderIdentity()
    val name = identity?.resolvedDisplayName() ?: "Bidder"
    val rejected = bid.status?.lowercase() in listOf("rejected", "declined", "withdrawn")
    val countered = bid.hasPendingCounter
    val amount = bid.bidAmount ?: bid.amount ?: 0.0
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .alpha(if (rejected) 0.55f else 1f)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .testTag("gigDetail.bid_${bid.id}"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            BidderAvatar(name = name, avatarUrl = identity?.resolvedAvatarUrl())
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    Text(
                        text = name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                    if (identity?.resolvedVerified() == true) {
                        PantopusIconImage(
                            icon = PantopusIcon.ShieldCheck,
                            contentDescription = "Verified neighbor",
                            size = 14.dp,
                            tint = PantopusColors.primary600,
                        )
                    }
                }
                relativeAgeLabel(bid.createdAt)?.let { age ->
                    Text(text = age, fontSize = 11.sp, color = PantopusColors.appTextMuted)
                }
            }
            Text(
                text = formatBidAmount(amount),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
        }
        bid.message?.takeIf { it.isNotBlank() }?.let { message ->
            Text(text = message, fontSize = 13.sp, color = PantopusColors.appTextSecondary)
        }
        when {
            rejected ->
                Text(
                    text = "Rejected",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextMuted,
                )
            countered ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    PantopusIconImage(
                        icon = PantopusIcon.ArrowsRepeat,
                        contentDescription = null,
                        size = 13.dp,
                        tint = PantopusColors.warning,
                    )
                    Text(
                        text = "Countered ${formatBidAmount(bid.counterAmount ?: 0.0)}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.warning,
                    )
                }
            else ->
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    BidActionButton(
                        label = "Accept",
                        prominent = true,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).testTag("gigDetail.bid_${bid.id}.accept"),
                        onClick = onAccept,
                    )
                    BidActionButton(
                        label = "Counter",
                        prominent = false,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).testTag("gigDetail.bid_${bid.id}.counter"),
                        onClick = onCounter,
                    )
                    BidActionButton(
                        label = "Reject",
                        prominent = false,
                        destructive = true,
                        enabled = !busy,
                        modifier = Modifier.weight(1f).testTag("gigDetail.bid_${bid.id}.reject"),
                        onClick = onReject,
                    )
                }
        }
    }
}

@Composable
private fun BidderAvatar(
    name: String,
    avatarUrl: String?,
) {
    Box(
        modifier =
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(PantopusColors.primary50),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(36.dp).clip(CircleShape),
            )
        } else {
            Text(
                text = initialsOf(name),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.primary600,
            )
        }
    }
}

@Composable
private fun BidActionButton(
    label: String,
    prominent: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val background =
        when {
            !enabled -> PantopusColors.appSurfaceSunken
            prominent -> PantopusColors.primary600
            else -> PantopusColors.appSurfaceSunken
        }
    val foreground =
        when {
            !enabled -> PantopusColors.appTextMuted
            prominent -> PantopusColors.appTextInverse
            destructive -> PantopusColors.error
            else -> PantopusColors.appText
        }
    Box(
        modifier =
            modifier
                .heightIn(min = 38.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(background)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = foreground)
    }
}

/** Counter-offer sheet: amount + optional message (`POST .../counter`). */
@Composable
fun GigCounterSheetContent(
    bid: GigBidDto,
    onSubmit: (amount: Double, message: String?) -> Unit,
    onCancel: () -> Unit,
) {
    var amountText by remember { mutableStateOf("") }
    var messageText by remember { mutableStateOf("") }
    val amount =
        amountText.trim().replace("$", "").replace(",", "").toDoubleOrNull()?.takeIf { it > 0 }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4)
                .testTag("gigDetail.counterSheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
            Text(
                text = "Send a counter-offer",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
            Text(
                text =
                    "Their bid is ${formatBidAmount(bid.bidAmount ?: bid.amount ?: 0.0)}. " +
                        "Propose your price — they can accept or decline.",
                fontSize = 13.sp,
                color = PantopusColors.appTextSecondary,
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(text = "$", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextSecondary)
            BasicTextField(
                value = amountText,
                onValueChange = { amountText = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle =
                    PantopusTextStyle.body.copy(
                        color = PantopusColors.appText,
                        fontWeight = FontWeight.SemiBold,
                    ),
                cursorBrush = SolidColor(PantopusColors.primary600),
                modifier = Modifier.weight(1f).testTag("gigDetail.counterSheet.amount"),
                decorationBox = { inner ->
                    if (amountText.isEmpty()) {
                        Text(text = "0.00", style = PantopusTextStyle.body, color = PantopusColors.appTextMuted)
                    }
                    inner()
                },
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(Spacing.s3),
        ) {
            BasicTextField(
                value = messageText,
                onValueChange = { messageText = it.take(500) },
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(PantopusColors.primary600),
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth().testTag("gigDetail.counterSheet.message"),
                decorationBox = { inner ->
                    if (messageText.isEmpty()) {
                        Text(
                            text = "Add a note (optional)",
                            style = PantopusTextStyle.body,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SheetGhostButton(
                label = "Cancel",
                modifier = Modifier.weight(1f),
                onClick = onCancel,
            )
            SheetPrimaryButton(
                label = "Send counter",
                enabled = amount != null,
                modifier = Modifier.weight(1f).testTag("gigDetail.counterSheet.submit"),
                onClick = { amount?.let { onSubmit(it, messageText.trim().ifEmpty { null }) } },
            )
        }
    }
}

/** Two-step reject confirmation before `POST .../reject`. */
@Composable
private fun GigRejectConfirmContent(
    bid: GigBidDto,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val name = bid.bidderIdentity()?.resolvedDisplayName() ?: "this bidder"
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(
            text = "Reject this bid?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Text(
            text = "$name will be notified that their bid wasn't selected. This can't be undone.",
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SheetGhostButton(label = "Keep bid", modifier = Modifier.weight(1f), onClick = onCancel)
            SheetDestructiveButton(
                label = "Reject bid",
                modifier = Modifier.weight(1f).testTag("gigDetail.rejectConfirm"),
                onClick = onConfirm,
            )
        }
    }
}

// MARK: - Work item 4 · active-task panel

@Composable
private fun GigActiveTaskPanel(
    panel: GigActiveTaskUi,
    onWorkerAck: () -> Unit,
    onStartTask: () -> Unit,
    onConfirmCompletion: () -> Unit,
    onReportNoShow: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s5, vertical = Spacing.s4)
                .testTag("gigDetail.activePanel"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text(
            text = "Task progress",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        GigPhaseStrip(activeIndex = panel.phaseIndex)
        if (panel.acked) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = null,
                    size = 14.dp,
                    tint = PantopusColors.success,
                )
                Text(
                    text = "You let the owner know you're on it",
                    fontSize = 12.sp,
                    color = PantopusColors.success,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            if (panel.showWorkerAck) {
                ActivePanelButton(
                    label = "I'm on it",
                    icon = PantopusIcon.Hand,
                    prominent = false,
                    modifier = Modifier.testTag("gigDetail.workerAck"),
                    onClick = onWorkerAck,
                )
            }
            if (panel.showStartTask) {
                ActivePanelButton(
                    label = "Start task",
                    icon = PantopusIcon.Play,
                    prominent = true,
                    modifier = Modifier.testTag("gigDetail.startTask"),
                    onClick = onStartTask,
                )
            }
            if (panel.showConfirmCompletion) {
                ActivePanelButton(
                    label = "Confirm completion",
                    icon = PantopusIcon.CheckCheck,
                    prominent = true,
                    modifier = Modifier.testTag("gigDetail.confirmCompletion"),
                    onClick = onConfirmCompletion,
                )
            }
            if (panel.showNoShow) {
                ActivePanelButton(
                    label = "Report a no-show",
                    icon = PantopusIcon.AlertTriangle,
                    prominent = false,
                    destructive = true,
                    modifier = Modifier.testTag("gigDetail.noShow"),
                    onClick = onReportNoShow,
                )
            }
        }
    }
}

private val PHASE_LABELS = listOf("Assigned", "In progress", "Marked done", "Confirmed")

@Composable
private fun GigPhaseStrip(activeIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PHASE_LABELS.forEachIndexed { index, label ->
            val reached = index <= activeIndex
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(if (reached) PantopusColors.primary600 else PantopusColors.appSurfaceSunken),
                    contentAlignment = Alignment.Center,
                ) {
                    if (reached) {
                        PantopusIconImage(
                            icon = PantopusIcon.Check,
                            contentDescription = null,
                            size = 12.dp,
                            tint = PantopusColors.appTextInverse,
                        )
                    }
                }
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = if (index == activeIndex) FontWeight.Bold else FontWeight.Medium,
                    color = if (reached) PantopusColors.appText else PantopusColors.appTextMuted,
                )
            }
            if (index < PHASE_LABELS.lastIndex) {
                Box(
                    modifier =
                        Modifier
                            .weight(0.4f)
                            .height(2.dp)
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(
                                if (index < activeIndex) PantopusColors.primary600 else PantopusColors.appBorder,
                            ),
                )
            }
        }
    }
}

@Composable
private fun ActivePanelButton(
    label: String,
    icon: PantopusIcon,
    prominent: Boolean,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val background = if (prominent) PantopusColors.primary600 else PantopusColors.appSurfaceSunken
    val foreground =
        when {
            prominent -> PantopusColors.appTextInverse
            destructive -> PantopusColors.error
            else -> PantopusColors.appText
        }
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(background)
                .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 16.dp, tint = foreground)
        Spacer(modifier = Modifier.width(Spacing.s2))
        Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = foreground)
    }
}

/** No-show report sheet — optional description → `POST /report-no-show`. */
@Composable
private fun GigNoShowSheetContent(
    onSubmit: (String?) -> Unit,
    onCancel: () -> Unit,
) {
    var description by remember { mutableStateOf("") }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4)
                .testTag("gigDetail.noShowSheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(
            text = "Report a no-show",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Text(
            text =
                "This cancels the task and files an incident against the " +
                    "other party. Only report after the agreed start time has passed.",
            fontSize = 13.sp,
            color = PantopusColors.appTextSecondary,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(Spacing.s3),
        ) {
            BasicTextField(
                value = description,
                onValueChange = { description = it.take(1000) },
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(PantopusColors.primary600),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("gigDetail.noShowSheet.details"),
                decorationBox = { inner ->
                    if (description.isEmpty()) {
                        Text(
                            text = "What happened? (optional)",
                            style = PantopusTextStyle.body,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SheetGhostButton(label = "Back", modifier = Modifier.weight(1f), onClick = onCancel)
            SheetDestructiveButton(
                label = "Report no-show",
                modifier = Modifier.weight(1f).testTag("gigDetail.noShowSheet.submit"),
                onClick = { onSubmit(description.trim().ifEmpty { null }) },
            )
        }
    }
}

// MARK: - Work item 5 · reviews

@Composable
private fun GigReviewSection(
    state: GigReviewState,
    onSubmit: suspend (rating: Int, comment: String?) -> Boolean,
) {
    if (state is GigReviewState.Hidden) return
    var sheetVisible by remember { mutableStateOf(false) }
    var presetRating by remember { mutableStateOf(0) }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.s5, vertical = Spacing.s4)
                .testTag("gigDetail.review"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Text(
            text = "How did it go?",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        when (state) {
            is GigReviewState.Submitted ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                    PantopusIconImage(
                        icon = PantopusIcon.Check,
                        contentDescription = null,
                        size = 15.dp,
                        tint = PantopusColors.success,
                    )
                    Text(
                        text = "Reviewed — thanks for helping the neighborhood",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.success,
                        modifier = Modifier.testTag("gigDetail.review.done"),
                    )
                }
            is GigReviewState.Available -> {
                Row(
                    modifier = Modifier.testTag("gigDetail.review.stars"),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    for (value in 1..5) {
                        Box(
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable {
                                        presetRating = value
                                        sheetVisible = true
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            PantopusIconImage(
                                icon = PantopusIcon.Star,
                                contentDescription = "$value star" + if (value == 1) "" else "s",
                                size = 26.dp,
                                tint = PantopusColors.appBorderStrong,
                            )
                        }
                    }
                }
                Text(
                    text = "Leave a review — it only takes a moment.",
                    fontSize = 12.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
            is GigReviewState.Hidden -> Unit
        }
    }

    if (sheetVisible && state is GigReviewState.Available) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { sheetVisible = false }, sheetState = sheetState) {
            GigReviewSheetContent(
                initialRating = presetRating,
                revieweeName = state.revieweeName,
                onSubmit = { rating, comment ->
                    val ok = onSubmit(rating, comment)
                    if (ok) sheetVisible = false
                    ok
                },
                onCancel = { sheetVisible = false },
            )
        }
    }
}

/** Star row + comment sheet → `POST /api/reviews` (route `backend/routes/reviews.js:35`). */
@Composable
private fun GigReviewSheetContent(
    initialRating: Int,
    revieweeName: String?,
    onSubmit: suspend (rating: Int, comment: String?) -> Boolean,
    onCancel: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var rating by remember { mutableStateOf(initialRating) }
    var comment by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4)
                .testTag("gigDetail.review.sheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(
            text = if (revieweeName.isNullOrEmpty()) "Leave a review" else "Review $revieweeName",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            for (value in 1..5) {
                Box(
                    modifier =
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .clickable { rating = value }
                            .testTag("gigDetail.review.star_$value"),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Star,
                        contentDescription = "$value star" + if (value == 1) "" else "s",
                        size = 30.dp,
                        tint = if (value <= rating) PantopusColors.warning else PantopusColors.appBorderStrong,
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(Spacing.s3),
        ) {
            BasicTextField(
                value = comment,
                onValueChange = { comment = it.take(2000) },
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(PantopusColors.primary600),
                minLines = 3,
                maxLines = 6,
                modifier = Modifier.fillMaxWidth().testTag("gigDetail.review.comment"),
                decorationBox = { inner ->
                    if (comment.isEmpty()) {
                        Text(
                            text = "Anything the neighborhood should know? (optional)",
                            style = PantopusTextStyle.body,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SheetGhostButton(label = "Not now", modifier = Modifier.weight(1f), onClick = onCancel)
            SheetPrimaryButton(
                label = if (submitting) "Submitting…" else "Submit review",
                enabled = rating in 1..5 && !submitting,
                modifier = Modifier.weight(1f).testTag("gigDetail.review.submit"),
                onClick = {
                    if (rating !in 1..5 || submitting) return@SheetPrimaryButton
                    submitting = true
                    scope.launch {
                        try {
                            onSubmit(rating, comment.trim().ifEmpty { null })
                        } finally {
                            submitting = false
                        }
                    }
                },
            )
        }
    }
}

// MARK: - Work item 6 · report sheet

/** Moderation report sheet — reason radios + details (`POST /report`). */
@Composable
fun GigReportSheetContent(
    onSubmit: (GigReportReason, String?) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<GigReportReason?>(null) }
    var details by remember { mutableStateOf("") }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4)
                .testTag("gigDetail.reportSheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(
            text = "Report this task",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            GigReportReason.entries.forEach { reason ->
                ReasonRadioRow(
                    label = reason.label,
                    selected = selected == reason,
                    testTag = "gigDetail.reportSheet.${reason.wireValue}",
                    onClick = { selected = reason },
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(Spacing.s3),
        ) {
            BasicTextField(
                value = details,
                onValueChange = { details = it.take(1000) },
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(PantopusColors.primary600),
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth().testTag("gigDetail.reportSheet.details"),
                decorationBox = { inner ->
                    if (details.isEmpty()) {
                        Text(
                            text = "Add details (optional)",
                            style = PantopusTextStyle.body,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SheetGhostButton(label = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
            SheetDestructiveButton(
                label = "Send report",
                enabled = selected != null,
                modifier = Modifier.weight(1f).testTag("gigDetail.reportSheet.submit"),
                onClick = { selected?.let { onSubmit(it, details.trim().ifEmpty { null }) } },
            )
        }
    }
}

// MARK: - Work item 7 · cancel with fee preview

/** Owner cancel sheet — zone label + fee copy + reason radios. */
@Composable
fun GigCancelSheetContent(
    preview: CancellationPreviewResponse?,
    previewLoading: Boolean,
    onConfirm: (CancelGigReason) -> Unit,
    onCancel: () -> Unit,
) {
    var selected by remember { mutableStateOf<CancelGigReason?>(null) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s4)
                .testTag("gigDetail.cancelSheet"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Text(
            text = "Cancel this task?",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
        )
        when {
            previewLoading ->
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Shimmer(width = 220.dp, height = 16.dp, cornerRadius = Radii.xs)
                    Shimmer(width = 160.dp, height = 12.dp, cornerRadius = Radii.xs)
                }
            preview != null ->
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.md))
                            .background(PantopusColors.appSurfaceSunken)
                            .padding(Spacing.s3)
                            .testTag("gigDetail.cancelSheet.preview"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    Text(
                        text = preview.zoneLabel ?: "Cancellation",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PantopusColors.appText,
                    )
                    Text(
                        text = cancellationFeeCopy(preview),
                        fontSize = 12.sp,
                        color =
                            if ((preview.fee ?: 0.0) > 0.0) {
                                PantopusColors.error
                            } else {
                                PantopusColors.appTextSecondary
                            },
                    )
                    preview.policyLabel?.let { policy ->
                        Text(
                            text = "Policy: $policy",
                            fontSize = 11.sp,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                }
            else -> Unit
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            CancelGigReason.entries.forEach { reason ->
                ReasonRadioRow(
                    label = reason.label,
                    selected = selected == reason,
                    testTag = "gigDetail.cancelSheet.${reason.wireValue}",
                    onClick = { selected = reason },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            SheetGhostButton(label = "Keep task", modifier = Modifier.weight(1f), onClick = onCancel)
            SheetDestructiveButton(
                label = "Cancel task",
                enabled = selected != null,
                modifier = Modifier.weight(1f).testTag("gigDetail.cancelSheet.confirm"),
                onClick = { selected?.let(onConfirm) },
            )
        }
    }
}

private fun cancellationFeeCopy(preview: CancellationPreviewResponse): String {
    val fee = preview.fee ?: 0.0
    return when {
        preview.inGrace == true || fee <= 0.0 -> "No cancellation fee right now."
        else -> "A ${formatBidAmount(fee)} cancellation fee applies (${preview.feePct?.toInt() ?: 0}% of the task price)."
    }
}

// MARK: - Shared bits

@Composable
private fun ReasonRadioRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(if (selected) PantopusColors.primary50 else PantopusColors.appSurfaceSunken)
                .border(
                    width = 1.dp,
                    color = if (selected) PantopusColors.primary600 else PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.md),
                )
                .clickable(onClick = onClick)
                .padding(Spacing.s3)
                .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            PantopusIconImage(
                icon = PantopusIcon.Check,
                contentDescription = "Selected",
                size = 18.dp,
                tint = PantopusColors.primary600,
            )
        }
    }
}

@Composable
private fun SheetPrimaryButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(if (enabled) PantopusColors.primary600 else PantopusColors.appSurfaceSunken)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
        )
    }
}

@Composable
private fun SheetDestructiveButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(if (enabled) PantopusColors.error else PantopusColors.appSurfaceSunken)
                .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) PantopusColors.appTextInverse else PantopusColors.appTextMuted,
        )
    }
}

@Composable
private fun SheetGhostButton(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
        )
    }
}

private fun initialsOf(name: String): String =
    name.split(" ").take(2).mapNotNull { it.firstOrNull()?.toString() }.joinToString("").uppercase().ifEmpty { "?" }

private fun formatBidAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) "$${amount.toInt()}" else String.format(Locale.US, "$%.2f", amount)

private fun relativeAgeLabel(iso: String?): String? {
    if (iso.isNullOrEmpty()) return null
    return runCatching {
        val seconds = Duration.between(Instant.parse(iso), Instant.now()).seconds
        when {
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60}m ago"
            seconds < 86_400 -> "${seconds / 3_600}h ago"
            else -> "${seconds / 86_400}d ago"
        }
    }.getOrNull()
}
