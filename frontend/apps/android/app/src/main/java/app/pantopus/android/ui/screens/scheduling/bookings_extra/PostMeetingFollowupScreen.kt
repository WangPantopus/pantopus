@file:Suppress("PackageNaming", "LongMethod", "LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod", "LargeClass", "MatchingDeclarationName")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.components.GhostButton
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

const val FOLLOW_UP_TAG = "scheduling.followUp"
private const val SUCCESS_DISMISS_MS = 1300L

@Composable
fun PostMeetingFollowupScreen(
    bookingId: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: PostMeetingFollowupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.didSend) {
        if (state.didSend) {
            delay(SUCCESS_DISMISS_MS)
            onBack()
        }
    }

    Scaffold(
        modifier = Modifier.testTag(FOLLOW_UP_TAG),
        containerColor = PantopusColors.appBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Follow up", style = PantopusTextStyle.h3, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        PantopusIconImage(icon = PantopusIcon.ChevronLeft, contentDescription = "Back", size = 22.dp, tint = PantopusColors.appText)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PantopusColors.appSurface),
            )
        },
        bottomBar = {
            if (!state.loading && state.loadError == null && !state.didSend) {
                FollowUpFooter(state = state, onSave = viewModel::submit, onSend = viewModel::submit)
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> SchedulingLoadingSkeleton(rows = 4)
                state.loadError != null -> ErrorState(message = state.loadError!!, onRetry = viewModel::load)
                state.didSend -> FollowUpSuccess(inviteeName = state.inviteeName)
                else ->
                    FollowUpContent(
                        state = state,
                        onSelectOutcome = viewModel::selectOutcome,
                        onMessage = viewModel::setMessage,
                        onPrivateNote = viewModel::setPrivateNote,
                        onPush = viewModel::setPush,
                        onRebookLink = viewModel::appendRebookLink,
                    )
            }
        }
    }
}

@Composable
internal fun FollowUpContent(
    state: FollowUpUiState,
    onSelectOutcome: (FollowUpOutcome) -> Unit,
    onMessage: (String) -> Unit,
    onPrivateNote: (String) -> Unit,
    onPush: (Boolean) -> Unit,
    onRebookLink: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = state.pillar.accent
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s4),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            ExtrasOverline("Outcome")
            ExtrasChipFlow {
                FollowUpOutcome.entries.forEach { outcome ->
                    ExtrasPillChip(
                        label = outcome.label,
                        selected = state.outcome == outcome,
                        onClick = { onSelectOutcome(outcome) },
                        accent = accent,
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            ExtrasOverline("Message to ${state.inviteeName}")
            ExtrasMessageBox(
                value = state.message,
                onValueChange = onMessage,
                placeholder = "Write a message, or pick an outcome above to start from a template.",
                accent = accent,
            )
            if (state.canAppendRebookLink) {
                ExtrasChipButton(
                    label = "Send rebook link",
                    icon = PantopusIcon.Link,
                    onClick = onRebookLink,
                    accent = accent,
                    enabled = !state.appendingLink,
                )
            }
            state.sendError?.let { ExtrasInlineError(message = it) }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                PantopusIconImage(icon = PantopusIcon.Lock, contentDescription = null, size = 12.dp, tint = PantopusColors.appTextSecondary)
                ExtrasOverline("Private note")
            }
            ExtrasMessageBox(
                value = state.privateNote,
                onValueChange = onPrivateNote,
                placeholder = "Outcome notes, next steps…",
                accent = accent,
                minHeight = 72.dp,
            )
            Text(text = "Only you can see this", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }

        ExtrasChannelRow(
            icon = PantopusIcon.Bell,
            label = "Send via push + message",
            checked = state.pushOn,
            onCheckedChange = onPush,
            accent = accent,
        )
    }
}

@Composable
private fun FollowUpFooter(
    state: FollowUpUiState,
    onSave: () -> Unit,
    onSend: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(Spacing.s4)) {
        if (state.isSaveNoteOnly) {
            GhostButton(title = "Save note only", onClick = onSave, modifier = Modifier.fillMaxWidth(), isLoading = state.sending)
        } else {
            PrimaryButton(
                title = if (state.sendError != null) "Try again" else "Send follow-up",
                onClick = onSend,
                modifier = Modifier.fillMaxWidth(),
                isLoading = state.sending,
                isEnabled = state.canSubmit,
            )
        }
    }
}

@Composable
private fun FollowUpSuccess(inviteeName: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.s6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ExtrasIconDisc(icon = PantopusIcon.Check, tint = PantopusColors.success, background = PantopusColors.successBg)
        Text(
            text = "Follow-up sent to $inviteeName",
            style = PantopusTextStyle.body,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            modifier = Modifier.padding(top = Spacing.s3),
        )
    }
}
