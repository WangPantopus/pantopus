@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList")

package app.pantopus.android.ui.screens.scheduling.invitee.edge

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pantopus.android.data.scheduling.SchedulingError
import app.pantopus.android.ui.screens.scheduling._shared.PausedExpiredUnavailableState
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingTerminalState
import app.pantopus.android.ui.screens.scheduling._shared.TerminalAction
import app.pantopus.android.ui.screens.scheduling._shared.toTerminalState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val UNAVAILABLE_EXPIRED_TAG = "schedulingUnavailableExpired"

/**
 * D7 — Unavailable / Expired / Paused / Secret / Not-found / Fully-booked /
 * Cancelled. ONE status-driven terminal screen (per the design), built on the
 * Foundation [PausedExpiredUnavailableState]. The icon/headline/body switch on
 * [state]; status-specific affordances (access code, the host's paused note,
 * "book again") render in the inline extra slot, and the contextual primary CTA
 * (notify me / request a new link / book again / see times) carries the pillar
 * accent. Never separate screens per state.
 */
@Composable
fun UnavailableExpiredScreen(
    state: SchedulingTerminalState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pillar: SchedulingPillar = SchedulingPillar.Personal,
    title: String? = null,
    body: String? = null,
    hostName: String? = null,
    pausedNote: String? = null,
    reopensLabel: String? = null,
    waitingCount: Int? = null,
    onNotifyMe: (() -> Unit)? = null,
    onRequestNewLink: (() -> Unit)? = null,
    onBookAgain: (() -> Unit)? = null,
    onSeeTimes: (() -> Unit)? = null,
    onSubmitAccessCode: ((String) -> Unit)? = null,
) {
    PausedExpiredUnavailableState(
        state = state,
        modifier = modifier.testTag(UNAVAILABLE_EXPIRED_TAG),
        pillar = pillar,
        title = title,
        body = body,
        extra = {
            TerminalExtra(
                state = state,
                pillar = pillar,
                hostName = hostName,
                pausedNote = pausedNote,
                reopensLabel = reopensLabel,
                onSubmitAccessCode = onSubmitAccessCode,
                onBookAgain = onBookAgain,
            )
        },
        primaryAction = primaryActionFor(state, pillar, onNotifyMe, onRequestNewLink, onBookAgain, onSeeTimes),
        secondaryAction = TerminalAction(label = "Back to Pantopus", onClick = onBack),
    )
}

/** Convenience overload: decode straight from a [SchedulingError]. */
@Composable
fun UnavailableExpiredScreen(
    error: SchedulingError,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    pillar: SchedulingPillar = SchedulingPillar.Personal,
    fallback: SchedulingTerminalState = SchedulingTerminalState.Unavailable,
    onNotifyMe: (() -> Unit)? = null,
    onRequestNewLink: (() -> Unit)? = null,
    onSubmitAccessCode: ((String) -> Unit)? = null,
) {
    UnavailableExpiredScreen(
        state = error.toTerminalState() ?: fallback,
        onBack = onBack,
        modifier = modifier,
        pillar = pillar,
        onNotifyMe = onNotifyMe,
        onRequestNewLink = onRequestNewLink,
        onSubmitAccessCode = onSubmitAccessCode,
    )
}

private fun primaryActionFor(
    state: SchedulingTerminalState,
    @Suppress("UNUSED_PARAMETER") pillar: SchedulingPillar,
    onNotifyMe: (() -> Unit)?,
    onRequestNewLink: (() -> Unit)?,
    onBookAgain: (() -> Unit)?,
    onSeeTimes: (() -> Unit)?,
): TerminalAction? =
    when (state) {
        SchedulingTerminalState.Paused ->
            onNotifyMe?.let { TerminalAction(label = "Notify me when it reopens", onClick = it, icon = PantopusIcon.Bell) }
        SchedulingTerminalState.FullyBooked ->
            onNotifyMe?.let { TerminalAction(label = "Notify me when times open", onClick = it, icon = PantopusIcon.Bell) }
                ?: onSeeTimes?.let { TerminalAction(label = "See available times", onClick = it, icon = PantopusIcon.Calendar) }
        SchedulingTerminalState.Expired ->
            onRequestNewLink?.let { TerminalAction(label = "Request a new link", onClick = it, icon = PantopusIcon.Mail) }
        SchedulingTerminalState.Cancelled ->
            onBookAgain?.let { TerminalAction(label = "Book again", onClick = it, icon = PantopusIcon.ArrowsRepeat) }
        else -> null
    }

@Composable
private fun TerminalExtra(
    state: SchedulingTerminalState,
    pillar: SchedulingPillar,
    hostName: String?,
    pausedNote: String?,
    reopensLabel: String?,
    onSubmitAccessCode: ((String) -> Unit)?,
    onBookAgain: (() -> Unit)?,
) {
    when (state) {
        SchedulingTerminalState.Secret -> if (onSubmitAccessCode != null) AccessCodeField(pillar = pillar, onSubmit = onSubmitAccessCode)
        SchedulingTerminalState.Paused ->
            if (pausedNote != null) {
                PausedNoteCard(hostName = hostName, note = pausedNote, reopensLabel = reopensLabel)
            }
        SchedulingTerminalState.Cancelled ->
            if (onBookAgain != null) {
                Row(
                    modifier = Modifier.clickable(onClick = onBookAgain).padding(Spacing.s1),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    PantopusIconImage(icon = PantopusIcon.ArrowsRepeat, contentDescription = null, size = 14.dp, tint = pillar.accent)
                    Text(text = "Book again", style = PantopusTextStyle.small, fontWeight = FontWeight.Bold, color = pillar.accent)
                }
            }
        else -> Unit
    }
}

private val CODE_FIELD_MAX_WIDTH = 240.dp

@Composable
private fun AccessCodeField(
    pillar: SchedulingPillar,
    onSubmit: (String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Column(modifier = Modifier.widthIn(max = CODE_FIELD_MAX_WIDTH)) {
        Text(
            text = "Have a code?",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.padding(bottom = Spacing.s1),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (code.isEmpty()) {
                    Text(text = "Enter access code", style = PantopusTextStyle.small, color = PantopusColors.appTextMuted)
                }
                BasicTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    textStyle = PantopusTextStyle.small.copy(color = PantopusColors.appText),
                    modifier = Modifier.fillMaxWidth().testTag("accessCodeField"),
                )
            }
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(pillar.accentBg)
                        .clickable(enabled = code.isNotBlank()) { onSubmit(code.trim()) },
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(icon = PantopusIcon.ArrowRight, contentDescription = "Submit code", size = 17.dp, tint = pillar.accent)
            }
        }
    }
}

@Composable
private fun PausedNoteCard(
    hostName: String?,
    note: String,
    reopensLabel: String?,
) {
    Column(
        modifier =
            Modifier
                .widthIn(max = CODE_FIELD_MAX_WIDTH)
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurfaceSunken)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Box(
                modifier = Modifier.size(24.dp).clip(CircleShape).background(SchedulingPillar.Personal.accentBg),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.User,
                    contentDescription = null,
                    size = 12.dp,
                    tint = SchedulingPillar.Personal.accent,
                )
            }
            Text(
                text = if (hostName != null) "A note from $hostName" else "A note from the host",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextSecondary,
            )
        }
        Text(
            text = "“$note”",
            style = PantopusTextStyle.small.copy(fontStyle = FontStyle.Italic),
            color = PantopusColors.appTextSecondary,
        )
        if (reopensLabel != null) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                        .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Calendar,
                    contentDescription = null,
                    size = 12.dp,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(
                    text = reopensLabel,
                    style = PantopusTextStyle.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}
