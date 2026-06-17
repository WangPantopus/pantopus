@file:Suppress("PackageNaming", "LongMethod", "LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod", "LargeClass", "MatchingDeclarationName")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

const val WAITLIST_TAG = "scheduling.waitlist"

@Composable
fun WaitlistScreen(
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: WaitlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(toast) {
        toast?.let {
            snackbar.showSnackbar(it)
            viewModel.toastConsumed()
        }
    }

    Scaffold(
        modifier = Modifier.testTag(WAITLIST_TAG),
        containerColor = PantopusColors.appBg,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Waitlist", style = PantopusTextStyle.h3, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        PantopusIconImage(icon = PantopusIcon.ChevronLeft, contentDescription = "Back", size = 22.dp, tint = PantopusColors.appText)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = PantopusColors.appSurface),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is WaitlistUiState.Loading -> SchedulingLoadingSkeleton(rows = 4)
                is WaitlistUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
                is WaitlistUiState.Empty ->
                    EmptyState(
                        icon = PantopusIcon.Users,
                        headline = "No event types yet",
                        subcopy = "Create a bookable event type to start collecting a waitlist.",
                    )
                is WaitlistUiState.Loaded ->
                    WaitlistContent(data = s.data, onSelect = viewModel::select, onPromote = viewModel::promote)
            }
        }
    }
}

@Composable
internal fun WaitlistContent(
    data: WaitlistData,
    onSelect: (String) -> Unit,
    onPromote: (String) -> Unit,
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
        if (data.options.size > 1) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                ExtrasOverline("Event type")
                ExtrasChipFlow {
                    data.options.forEach { option ->
                        ExtrasPillChip(
                            label = option.name,
                            selected = option.id == data.selectedId,
                            onClick = { onSelect(option.id) },
                            accent = data.pillar.accent,
                        )
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.xl))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                    .padding(Spacing.s4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${data.entries.size} ${if (data.entries.size == 1) "person" else "people"} waiting",
                    style = PantopusTextStyle.body,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                Text(text = "${data.seatTotal} ${if (data.seatTotal == 1) "seat" else "seats"} per session", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
            PantopusIconImage(icon = PantopusIcon.Users, contentDescription = null, size = 20.dp, tint = data.pillar.accent)
        }

        if (data.entries.isEmpty()) {
            EmptyState(
                icon = PantopusIcon.Clock,
                headline = "No one's waiting",
                subcopy = "When this event fills up, people can join the waitlist and you'll promote them here.",
            )
        } else {
            ExtrasOverline("Waitlist · ${data.entries.size}")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                data.entries.forEach { person ->
                    RosterRow(person = person) {
                        PromoteSeatButton(enabled = true, accent = data.pillar.accent, onClick = { onPromote(person.id) })
                    }
                }
            }
        }
    }
}
