@file:Suppress("PackageNaming", "TooManyFunctions", "LongMethod", "MagicNumber", "LongParameterList", "UNUSED_PARAMETER")

package app.pantopus.android.ui.screens.scheduling.findatime

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val MEMBER_POLL_RESPONSE_TAG = "memberPollResponseScreen"

/**
 * F6 Find a Time — Member Poll Response. The household member marks which
 * proposed times work (Works / If needed / Can't) and submits one public vote.
 */
@Composable
fun MemberPollResponseScreen(
    pollId: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: MemberPollResponseViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.start() }
    val state by viewModel.state.collectAsStateWithLifecycle()

    MemberPollResponseContent(
        state = state,
        onBack = onBack,
        onRetry = viewModel::load,
        onVote = viewModel::setVote,
        onName = viewModel::setVoterName,
        onEmail = viewModel::setVoterEmail,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun MemberPollResponseContent(
    state: PollResponseUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onVote: (String, VoteValue) -> Unit,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(PantopusColors.appBg).testTag(MEMBER_POLL_RESPONSE_TAG)) {
        FtTopBar(title = "Respond", onBack = onBack, backIcon = PantopusIcon.X)
        when (state) {
            is PollResponseUiState.Loading -> PollSkeleton()
            is PollResponseUiState.Error -> ErrorState(message = state.message, onRetry = onRetry)
            is PollResponseUiState.Loaded -> LoadedPoll(state = state, onVote = onVote, onName = onName, onEmail = onEmail, onSubmit = onSubmit)
            is PollResponseUiState.Closed -> ClosedPoll(state = state)
        }
    }
}

@Composable
private fun OrganizerHeader(header: PollHeader) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(38.dp).clip(CircleShape).background(HomeAccentBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.Vote, contentDescription = null, size = 18.dp, tint = HomeAccentDark)
        }
        Column(modifier = Modifier.weight(1f).padding(start = Spacing.s3)) {
            Text(text = header.title, style = PantopusTextStyle.small, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            Text(text = header.subtitle, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary, modifier = Modifier.padding(top = 2.dp))
        }
        FtChip(label = "Poll", icon = PantopusIcon.Vote)
    }
}

@Composable
private fun LoadedPoll(
    state: PollResponseUiState.Loaded,
    onVote: (String, VoteValue) -> Unit,
    onName: (String) -> Unit,
    onEmail: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Spacing.s4),
            verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            OrganizerHeader(state.header)
            if (state.submitted) {
                FtBanner(tone = FtBannerTone.Home, icon = PantopusIcon.CheckCircle, title = "Response submitted", body = "Thanks — we'll book the most-picked time.")
            }
            FtOverline("Mark which times work", color = PantopusColors.appTextSecondary)
            state.options.forEach { option ->
                PollOptionCard(option = option, locked = false, onVote = { onVote(option.id, it) })
            }
            if (state.needsEmail) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.xl))
                            .background(PantopusColors.appSurface)
                            .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                            .padding(Spacing.s3),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    FtOverline("Your details", color = PantopusColors.appTextSecondary)
                    FtInputField(value = state.voterName, placeholder = "Your name (optional)", onValueChange = onName)
                    FtInputField(value = state.voterEmail, placeholder = "Email to record your vote", onValueChange = onEmail, keyboardType = KeyboardType.Email)
                }
            }
            if (state.error != null) {
                FtBanner(tone = FtBannerTone.Error, icon = PantopusIcon.AlertCircle, body = state.error)
            }
        }
        Box(modifier = Modifier.fillMaxWidth().background(PantopusColors.appSurface).padding(Spacing.s4)) {
            FtPrimaryButton(
                label = if (state.submitted) "Update response" else "Submit response",
                icon = PantopusIcon.Send,
                enabled = state.canSubmit,
                onClick = onSubmit,
                modifier = Modifier.testTag("submitResponseButton"),
            )
        }
    }
}

@Composable
private fun ClosedPoll(state: PollResponseUiState.Closed) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        OrganizerHeader(state.header)
        FtBanner(
            tone = FtBannerTone.Home,
            icon = PantopusIcon.CheckCircle,
            title = "This proposal closed",
            body = state.finalizedLabel?.let { "Booked $it. It's on the family calendar." } ?: "Voting has ended for this poll.",
        )
        FtOverline("Proposed times", color = PantopusColors.appTextMuted)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
            state.options.forEach { option ->
                PollOptionCard(option = option, locked = true, onVote = {})
            }
        }
    }
}

@Composable
private fun PollOptionCard(
    option: PollOptionUi,
    locked: Boolean,
    onVote: (VoteValue) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s3),
    ) {
        Text(
            text = "${option.dayLabel} · ${option.timeLabel}",
            style = PantopusTextStyle.small,
            fontWeight = FontWeight.Bold,
            color = PantopusColors.appText,
            modifier = Modifier.padding(bottom = Spacing.s2),
        )
        VoteControl(optionId = option.id, selected = option.vote, locked = locked, onVote = onVote)
    }
}

@Composable
private fun VoteControl(
    optionId: String,
    selected: VoteValue?,
    locked: Boolean,
    onVote: (VoteValue) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceSunken)
                .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        VoteValue.entries.forEach { value ->
            val on = value == selected
            val onColor =
                when (value) {
                    VoteValue.Works -> HomeAccent
                    VoteValue.Maybe -> PantopusColors.warning
                    VoteValue.Cant -> PantopusColors.error
                }
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(Radii.sm))
                        .background(if (on) onColor else Color.Transparent)
                        .clickable(enabled = !locked, onClickLabel = value.label) { onVote(value) }
                        .padding(vertical = Spacing.s2)
                        .testTag("vote_${optionId}_${value.wire}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = value.label,
                    style = PantopusTextStyle.caption,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (on) PantopusColors.appTextInverse else PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

@Composable
private fun PollSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(Spacing.s4), verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        repeat(4) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.xl))
                        .background(PantopusColors.appSurface)
                        .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                        .padding(Spacing.s3),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Shimmer(width = 140.dp, height = 12.dp, cornerRadius = Radii.xs)
                Shimmer(width = 220.dp, height = 28.dp, cornerRadius = Radii.sm)
            }
        }
    }
}
