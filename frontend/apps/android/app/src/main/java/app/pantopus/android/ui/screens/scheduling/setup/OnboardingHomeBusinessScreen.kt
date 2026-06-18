@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "CyclomaticComplexMethod", "UnusedParameter", "LongParameterList")

package app.pantopus.android.ui.screens.scheduling.setup

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SuccessHeroBlock

@Composable
fun OnboardingHomeBusinessScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: OnboardingHomeBusinessViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingShareUrl by viewModel.pendingShareUrl.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val pillar = state.pillar

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
        if (state.isSuccess) {
            OnboardingSuccess(state)
            return@WizardShell
        }
        if (state.stepIndex == 1) {
            OnboardingFlowSwitch(flow = state.flow, onSelect = viewModel::selectFlow)
            SetupPillarChip(pillar = pillar, label = if (state.flow == OnboardingFlow.Home) "Home" else "Business")
        }
        val steps =
            if (state.flow == OnboardingFlow.Home) {
                listOf("Members", "Combine", "Share")
            } else {
                listOf("Link", "Service", "Team", "Confirm")
            }
        WizardStepRail(steps = steps, current = state.displayStep, pillar = pillar)
        if (state.flow == OnboardingFlow.Home) HomeStep(state, viewModel, pillar) else BusinessStep(state, viewModel, pillar)
    }
}

@Composable
private fun HomeStep(
    state: OnboardingUiState,
    vm: OnboardingHomeBusinessViewModel,
    pillar: app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar,
) {
    when (state.stepIndex) {
        1 -> {
            HeadlineBlock("Choose who's scheduled")
            SubcopyBlock(
                "Pick the household members people can book. Family scheduling uses everyone's own hours — no one sets times twice.",
            )
            OnboardingMemberList(selected = state.selectedMembers, pillar = pillar, onToggle = vm::toggleMember)
        }
        2 -> {
            HeadlineBlock("How should times combine?")
            SubcopyBlock(
                if (state.combineMode == "round_robin") {
                    "Whoever's free gets the booking. Pick a rule for who hosts when more than one person is open."
                } else {
                    "Three members are scheduled. Choose how their availability turns into one set of bookable times."
                },
            )
            OnboardingModePicker(mode = state.combineMode, pillar = pillar, onSelect = vm::setCombineMode)
            if (state.combineMode == "round_robin") {
                OnboardingRoundRobinRule(rule = state.roundRobinRule, pillar = pillar, onSelect = vm::setRoundRobinRule)
            }
            ComposedAvailabilityCard(message = composedMessage(state.flow), timezoneId = state.timezoneId, pillar = pillar)
        }
    }
}

@Composable
private fun BusinessStep(
    state: OnboardingUiState,
    vm: OnboardingHomeBusinessViewModel,
    pillar: app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar,
) {
    when (state.stepIndex) {
        1 -> {
            HeadlineBlock("Claim your business link")
            SubcopyBlock("This is where clients book you. Pick something short — your business name usually works best.")
            SlugClaimField(
                overline = "Your business link",
                slug = state.slug,
                state = state.slugState,
                availableHint = "Clients will book your business here.",
                onSlugChange = vm::onSlugChange,
                onPickSuggestion = vm::onPickSuggestion,
                pillar = pillar,
            )
        }
        2 -> {
            HeadlineBlock("Add your first service")
            SubcopyBlock("Clients pick a service when they book. Start with one — you can add more from settings.")
            OnboardingServicePicker(
                serviceType = state.serviceType,
                duration = state.duration,
                priceText = state.priceText,
                paidEnabled = vm.paidEnabled,
                pillar = pillar,
                onSelect = vm::setServiceType,
                onDuration = vm::setDuration,
                onPrice = vm::setPriceText,
            )
        }
        3 -> {
            HeadlineBlock("Seat your team")
            SubcopyBlock("Seated teammates can take bookings. Front-desk roles manage the calendar without being booked.")
            OnboardingTeamList(seated = state.seatedTeam, pillar = pillar, onToggle = vm::toggleSeat)
            ComposedAvailabilityCard(message = composedMessage(state.flow), timezoneId = state.timezoneId, pillar = pillar)
        }
        4 -> {
            HeadlineBlock("Auto-confirm or approve?")
            SubcopyBlock("Decide what happens when a client picks a time. You can change this any time.")
            OnboardingConfirmMode(mode = state.confirmMode, pillar = pillar, onSelect = vm::setConfirmMode)
            if (state.confirmMode == "approve") OnboardingApproveExplainer(pillar = pillar)
        }
    }
}

@Composable
private fun OnboardingSuccess(state: OnboardingUiState) {
    val isHome = state.flow == OnboardingFlow.Home
    SuccessHeroBlock(
        headline = if (isHome) "Your family link is live" else "Your business is taking bookings",
        subcopy =
            if (isHome) {
                "Share it and people can book any free member during their own hours. Bookings show up on the family schedule."
            } else {
                val confirm =
                    if (state.confirmMode == "approve") {
                        "You approve each booking before it's confirmed."
                    } else {
                        "Bookings confirm automatically."
                    }
                "Your link is live with your first service and seated team. $confirm"
            },
    )
    WizardSuccessLinkCard(link = state.shareLink, pillar = state.pillar, onCopy = {})
}
