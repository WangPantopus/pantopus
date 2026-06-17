@file:Suppress("PackageNaming", "LongMethod", "LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod", "LargeClass", "MatchingDeclarationName")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import app.pantopus.android.ui.components.PrimaryButton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Spacing

const val NUDGE_TAG = "scheduling.sendNudge"

enum class NudgeAudience(val label: String) {
    All("All attendees"),
    Confirmed("Confirmed only"),
    NoShows("No-shows"),
}

data class NudgeAudienceCounts(
    val all: Int = 0,
    val confirmed: Int = 0,
    val noShows: Int = 0,
) {
    fun count(audience: NudgeAudience): Int =
        when (audience) {
            NudgeAudience.All -> all
            NudgeAudience.Confirmed -> confirmed
            NudgeAudience.NoShows -> noShows
        }
}

data class NudgeTemplate(
    val id: String,
    val title: String,
    val body: String,
)

data class NudgeSheetState(
    val subtitle: String,
    val message: String = "",
    val audience: NudgeAudience = NudgeAudience.All,
    val counts: NudgeAudienceCounts = NudgeAudienceCounts(),
    val pushOn: Boolean = true,
    val emailOn: Boolean = false,
    val sending: Boolean = false,
    val error: String? = null,
    val templates: List<NudgeTemplate> = emptyList(),
    val templatePickerOpen: Boolean = false,
) {
    val over: Boolean get() = message.length > MESSAGE_LIMIT
    val recipientCount: Int get() = counts.count(audience)
    val canSend: Boolean get() = message.isNotBlank() && !over && recipientCount > 0 && !sending

    companion object {
        const val MESSAGE_LIMIT = 280
    }
}

/**
 * E11 Send a Nudge / Manual Follow-up — the ManageTrain SendUpdateForm pointed
 * at a booking/group. A composer with a live counter, a single-select audience
 * (All / Confirmed / No-shows, each with its recipient count), Push/Email
 * channel toggles, and a count-echoing CTA ("Send to 12"). Audience + channel
 * are presentation-only; the backend nudge carries the message and fans out to
 * each selected booking's invitee.
 */
@Composable
internal fun NudgeSheet(
    state: NudgeSheetState,
    sheetState: SheetState,
    accent: Color,
    onMessageChange: (String) -> Unit,
    onAudience: (NudgeAudience) -> Unit,
    onPushChange: (Boolean) -> Unit,
    onEmailChange: (Boolean) -> Unit,
    onUseTemplate: () -> Unit,
    onTemplatePicked: (NudgeTemplate) -> Unit,
    onTemplateDismiss: () -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
        modifier = Modifier.testTag(NUDGE_TAG),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4, vertical = Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(Spacing.s4),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                Text(text = "Message attendees", style = PantopusTextStyle.h3, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
                Text(text = state.subtitle, style = PantopusTextStyle.small, color = PantopusColors.appTextSecondary)
            }

            Box {
                ExtrasChipButton(label = "Use a template", icon = PantopusIcon.FileText, onClick = onUseTemplate, accent = accent)
                DropdownMenu(expanded = state.templatePickerOpen, onDismissRequest = onTemplateDismiss) {
                    if (state.templates.isEmpty()) {
                        DropdownMenuItem(text = { Text("No templates yet") }, onClick = onTemplateDismiss)
                    } else {
                        state.templates.forEach { template ->
                            DropdownMenuItem(text = { Text(template.title) }, onClick = { onTemplatePicked(template) })
                        }
                    }
                }
            }

            ExtrasMessageBox(
                value = state.message,
                onValueChange = onMessageChange,
                placeholder = "Write a quick update for your attendees…",
                accent = accent,
                limit = NudgeSheetState.MESSAGE_LIMIT,
            )
            if (state.over) {
                ExtrasInlineError(message = "Shorten your message")
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                ExtrasOverline("Audience")
                ExtrasChipFlow {
                    NudgeAudience.entries.forEach { option ->
                        ExtrasPillChip(
                            label = "${option.label} · ${state.counts.count(option)}",
                            selected = state.audience == option,
                            onClick = { onAudience(option) },
                            accent = accent,
                        )
                    }
                }
                if (state.recipientCount == 0) {
                    Text(
                        text = "No one to message in this group",
                        style = PantopusTextStyle.caption,
                        color = PantopusColors.appTextSecondary,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                ExtrasChannelRow(icon = PantopusIcon.Bell, label = "Push", checked = state.pushOn, onCheckedChange = onPushChange, accent = accent)
                ExtrasChannelRow(icon = PantopusIcon.Mail, label = "Email", checked = state.emailOn, onCheckedChange = onEmailChange, accent = accent)
            }

            state.error?.let { ExtrasInlineError(message = it) }

            PrimaryButton(
                title = if (state.recipientCount > 0) "Send to ${state.recipientCount}" else "No recipients",
                onClick = onSend,
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s4),
                isLoading = state.sending,
                isEnabled = state.canSend,
            )
        }
    }
}
