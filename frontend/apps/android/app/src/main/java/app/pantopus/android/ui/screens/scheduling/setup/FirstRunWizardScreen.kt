@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "UnusedParameter")

package app.pantopus.android.ui.screens.scheduling.setup

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SuccessHeroBlock

@Composable
fun FirstRunWizardScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: FirstRunWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingShareUrl by viewModel.pendingShareUrl.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pillar = SchedulingPillar.Personal

    LaunchedEffect(finished) { if (finished) onBack() }

    LaunchedEffect(pendingShareUrl) {
        pendingShareUrl?.let { url ->
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
            runCatching { context.startActivity(Intent.createChooser(send, null)) }
            viewModel.shareConsumed()
        }
    }

    WizardShell(model = viewModel, identity = pillar.wizardIdentity) {
        WizardStepRail(steps = listOf("Link", "Type", "Hours", "Share"), current = state.step, pillar = pillar)
        when (state.step) {
            1 -> {
                HeadlineBlock("Claim your booking link")
                SubcopyBlock("This is the link you'll share. People book you at it — pick something short and memorable.")
                SlugClaimField(
                    overline = "Your link",
                    slug = state.slug,
                    state = state.slugState,
                    availableHint = "People will book you at this link.",
                    onSlugChange = viewModel::onSlugChange,
                    onPickSuggestion = viewModel::onPickSuggestion,
                    pillar = pillar,
                )
            }
            2 -> {
                HeadlineBlock("Pick a meeting type")
                SubcopyBlock("Start with one — how you meet and how long it runs. You can add more from settings.")
                WizardTypePicker(
                    selectedMode = state.locationMode,
                    duration = state.duration,
                    pillar = pillar,
                    onSelectMode = viewModel::onSelectLocation,
                    onSelectDuration = viewModel::onSelectDuration,
                )
            }
            3 -> {
                HeadlineBlock("Set your weekly hours")
                SubcopyBlock("People can only book inside these windows. You can fine-tune any day, or just use the defaults.")
                WizardTimezoneChip(timezoneId = state.timezoneId, pillar = pillar)
                WizardHoursGrid(hours = state.hours, pillar = pillar, onToggleDay = viewModel::onToggleDay)
            }
            else -> {
                SuccessHeroBlock(
                    headline = "You're all set",
                    subcopy =
                        "Your booking link is live. Share it and people can book a " +
                            "${state.duration}-minute meeting during your weekly hours.",
                )
                WizardSuccessLinkCard(
                    link = state.shareLink,
                    pillar = pillar,
                    onCopy = viewModel::onPrimary,
                )
            }
        }
    }
}
