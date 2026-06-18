@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.invitee.confirm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * D3 — Booking confirmed / thank-you (the scrollable body; the flow shell owns
 * the close bar + dock). Success-green halo for confirmed; info-blue hourglass +
 * 3-step timeline for the requires-approval "Request sent" variant. Paid
 * bookings get a receipt capsule; the add-to-calendar cluster + manage note sit
 * beneath, with a signed-out account nudge at the bottom.
 */
@Composable
fun ConfirmedBody(
    confirmed: ConfirmedData,
    args: InviteeConfirmArgs,
    pillar: SchedulingPillar,
    whenLabel: String,
    tzLabel: String,
    onAddToCalendar: (CalendarTarget) -> Unit,
    onDownloadIcs: () -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pending = confirmed.requiresApproval
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        if (pending) {
            ConfirmHero(
                kind = HaloKind.Info,
                icon = PantopusIcon.Hourglass,
                title = "Request sent",
                body = "${args.hostName} reviews each request before it's confirmed. We'll email you the moment it's set.",
            )
            ApprovalTimeline()
            ApprovalEtaPill()
        } else {
            ConfirmHero(
                kind = HaloKind.Success,
                icon = PantopusIcon.CheckCircle,
                title = "You're booked",
                body = confirmed.confirmationMessage?.takeIf { it.isNotBlank() } ?: "We sent the details to ${confirmed.sentToEmail}.",
            )
        }

        ConfirmedSummary(args = args, pillar = pillar, whenLabel = whenLabel, tzLabel = tzLabel, pending = pending)

        confirmed.paid?.let { ReceiptCapsule(it) }

        CalendarCluster(accent = pillar.accent, onAddTo = onAddToCalendar, onDownloadIcs = onDownloadIcs)

        ManageNote(accent = pillar.accent, onManage = onManage, enabled = confirmed.manageToken != null)

        AccountNudge()
    }
}

@Composable
private fun ConfirmedSummary(
    args: InviteeConfirmArgs,
    pillar: SchedulingPillar,
    whenLabel: String,
    tzLabel: String,
    pending: Boolean,
) {
    val location = ConfirmUtils.locationLabel(args.eventType.locationMode, args.eventType.locationDetail)
    ConfirmCard {
        SummaryDetailRow(icon = PantopusIcon.User) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HostAvatar(pillar = pillar, initials = ConfirmUtils.initials(args.hostName), diameter = 30.dp)
                Column(modifier = Modifier.padding(start = Spacing.s2)) {
                    Text(
                        text = args.eventType.name ?: "Booking",
                        style = PantopusTextStyle.caption,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appText,
                    )
                    Text(text = "with ${args.hostName}", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
                }
            }
        }
        SummaryDetailRow(icon = PantopusIcon.Calendar) {
            Text(text = whenLabel, style = PantopusTextStyle.caption, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
            TimezoneChip(label = tzLabel, accent = pillar.accent, modifier = Modifier.padding(top = Spacing.s1))
        }
        SummaryDetailRow(icon = PantopusIcon.Video, divider = false) {
            Text(text = location.label, style = PantopusTextStyle.caption, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
            Text(
                text =
                    if (pending) {
                        "Join link is sent once the host confirms."
                    } else {
                        location.sub ?: "Join link is in your email and calendar invite."
                    },
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

@Composable
private fun ReceiptCapsule(paid: PaidConfirmInfo) {
    val deposit = paid.mode == PriceMode.Deposit
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.successBg)
                .border(1.dp, PantopusColors.successLight, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PantopusIconImage(
                icon = PantopusIcon.BadgeCheck,
                contentDescription = null,
                size = 16.dp,
                tint = PantopusColors.success,
                modifier = Modifier.padding(end = Spacing.s2),
            )
            Text(
                text = if (deposit) "Deposit received" else "Payment received",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.success,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = ConfirmUtils.formatCents(paid.amountPaidCents, paid.currency),
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.ExtraBold,
                color = PantopusColors.success,
            )
        }
        if (deposit && paid.balanceCents > 0) {
            Text(
                text = "${ConfirmUtils.formatCents(paid.balanceCents, paid.currency)} due at your visit",
                style = PantopusTextStyle.caption,
                color = PantopusColors.success,
            )
        }
        HorizontalDivider(color = PantopusColors.successLight, modifier = Modifier.padding(vertical = Spacing.s1))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PantopusIconImage(
                icon = PantopusIcon.MailCheck,
                contentDescription = null,
                size = 13.dp,
                tint = PantopusColors.success,
                modifier = Modifier.padding(end = Spacing.s2),
            )
            Text(text = "Receipt emailed.", style = PantopusTextStyle.caption, color = PantopusColors.appTextStrong)
        }
    }
}

@Composable
private fun ManageNote(
    accent: androidx.compose.ui.graphics.Color,
    onManage: () -> Unit,
    enabled: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        PantopusIconImage(
            icon = PantopusIcon.CalendarClock,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.appTextMuted,
            modifier = Modifier.padding(end = Spacing.s2),
        )
        Row {
            Text(text = "Need to change it? ", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            Text(
                text = "Reschedule or cancel",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = if (enabled) accent else PantopusColors.appTextMuted,
                modifier = if (enabled) Modifier.clickable(onClick = onManage) else Modifier,
            )
            Text(text = " anytime.", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }
    }
}

@Composable
private fun AccountNudge() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.primary50)
                .border(1.dp, PantopusColors.primary100, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.appSurface),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.UserPlus, contentDescription = null, size = 16.dp, tint = PantopusColors.primary600)
        }
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.s2)) {
            Text(
                text = "Create an account to manage your bookings",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
            )
            Text(
                text = "Reschedule, cancel, and rebook in one place.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        PantopusIconImage(icon = PantopusIcon.ChevronRight, contentDescription = null, size = 15.dp, tint = PantopusColors.primary600)
    }
}

// ─── Approval timeline (requires_approval) ───────────────────────────────────

@Composable
private fun ApprovalTimeline() {
    val steps = listOf("Submitted" to TimelineState.Done, "Awaiting host" to TimelineState.Current, "Confirmed" to TimelineState.Pending)
    // Spec fill: 66.66% when all done, 33.33% when current step index > 0, else 0%.
    val currentIdx = steps.indexOfFirst { it.second == TimelineState.Current }
    val fillFraction =
        when {
            steps.all { it.second == TimelineState.Done } -> 0.6666f
            currentIdx > 0 -> 0.3333f
            else -> 0f
        }
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(vertical = Spacing.s4, horizontal = Spacing.s2),
    ) {
        // Connector track (spans 16.66%..83.33% = 66.66% wide, centered on the dot row)
        // plus a left-anchored INFO progress fill, vertically centered on the 28dp dots
        // (top inset 14dp = half the dot). zIndex 0/1: drawn before the dots so they sit on top.
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.6666f)
                    .align(Alignment.TopCenter)
                    .padding(top = 13.dp)
                    .height(2.dp)
                    .background(PantopusColors.appBorder),
        ) {
            if (fillFraction > 0f) {
                // Fill is a sub-segment of the track: fillFraction of the full width / 0.6666 track width.
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(fillFraction / 0.6666f)
                            .fillMaxHeight()
                            .background(PantopusColors.info),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            steps.forEach { (label, state) ->
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    val (bg, border, icon, tint) =
                        when (state) {
                            TimelineState.Done ->
                                TimelineDot(
                                    PantopusColors.success,
                                    PantopusColors.success,
                                    PantopusIcon.Check,
                                    PantopusColors.appTextInverse,
                                )
                            TimelineState.Current ->
                                TimelineDot(
                                    PantopusColors.info,
                                    PantopusColors.info,
                                    null,
                                    PantopusColors.appTextInverse,
                                )
                            TimelineState.Pending ->
                                TimelineDot(
                                    PantopusColors.appSurface,
                                    PantopusColors.appBorderStrong,
                                    null,
                                    PantopusColors.appTextMuted,
                                )
                        }
                    Box(
                        modifier =
                            Modifier.size(
                                28.dp,
                            ).clip(RoundedCornerShape(Radii.pill)).background(bg).border(1.5.dp, border, RoundedCornerShape(Radii.pill)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            PantopusIconImage(icon = icon, contentDescription = null, size = 14.dp, tint = tint)
                        } else if (state == TimelineState.Current) {
                            Box(
                                modifier =
                                    Modifier.size(
                                        8.dp,
                                    ).clip(RoundedCornerShape(Radii.pill)).background(PantopusColors.appTextInverse),
                            )
                        }
                    }
                    Text(
                        text = label,
                        style = PantopusTextStyle.caption,
                        fontWeight = if (state == TimelineState.Pending) FontWeight.Normal else FontWeight.Bold,
                        color = if (state == TimelineState.Pending) PantopusColors.appTextSecondary else PantopusColors.appText,
                    )
                }
            }
        }
    }
}

@Composable
private fun ApprovalEtaPill() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.infoBg)
                .border(1.dp, PantopusColors.infoLight, RoundedCornerShape(Radii.pill))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Clock,
            contentDescription = null,
            size = 12.dp,
            tint = PantopusColors.info,
            modifier = Modifier.padding(end = Spacing.s1),
        )
        Text(
            text = "Hosts usually reply within a day",
            style = PantopusTextStyle.caption,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.info,
        )
    }
}

private enum class TimelineState { Done, Current, Pending }

private data class TimelineDot(
    val bg: androidx.compose.ui.graphics.Color,
    val border: androidx.compose.ui.graphics.Color,
    val icon: PantopusIcon?,
    val tint: androidx.compose.ui.graphics.Color,
)
