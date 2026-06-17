@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions", "LongParameterList")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package app.pantopus.android.ui.screens.scheduling.business

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

private val RULE_ICON_BOX = 32.dp
private val RADIO = 20.dp

/**
 * G1 Round-Robin Assignment sheet. Local `ModalBottomSheet` configuring which
 * members rotate for a service and the fairness rule. Saves via the repository
 * (`PUT /event-types/:id/assignees`). [onSaved] fires after a successful save.
 */
@Composable
fun RoundRobinSheet(
    eventTypeId: String,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RoundRobinAssignmentViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var toast by remember { mutableStateOf<String?>(null) }

    androidx.compose.runtime.LaunchedEffect(eventTypeId) { viewModel.start(eventTypeId) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                RoundRobinAssignmentViewModel.Event.Saved -> onSaved()
                is RoundRobinAssignmentViewModel.Event.Toast -> toast = event.message
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PantopusColors.appSurface,
        modifier = Modifier.testTag("roundRobinSheet"),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s4)) {
            Text(text = "Assign bookings", style = PantopusTextStyle.h3, color = PantopusColors.appText)
            Text(
                text = "New bookings rotate across the members you pick.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.padding(top = Spacing.s1, bottom = Spacing.s3),
            )
            when (val s = state) {
                RoundRobinAssignmentViewModel.UiState.Loading -> RoundRobinLoading()
                is RoundRobinAssignmentViewModel.UiState.Error ->
                    BizNote(text = s.message, tone = BizNoteTone.Error, icon = PantopusIcon.AlertCircle)
                is RoundRobinAssignmentViewModel.UiState.Content ->
                    RoundRobinBody(content = s, viewModel = viewModel, toast = toast, onDone = viewModel::save)
            }
        }
    }
}

@Composable
private fun RoundRobinBody(
    content: RoundRobinAssignmentViewModel.UiState.Content,
    viewModel: RoundRobinAssignmentViewModel,
    toast: String?,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        RuleCard(
            name = "Balanced",
            desc = "Spread bookings by weight",
            icon = PantopusIcon.Gauge,
            selected = content.rule == RoundRobinAssignmentViewModel.Rule.Balanced,
            onClick = { viewModel.selectRule(RoundRobinAssignmentViewModel.Rule.Balanced) },
        )
        RuleCard(
            name = "Priority order",
            desc = "Fill the top of the list first",
            icon = PantopusIcon.List,
            selected = content.rule == RoundRobinAssignmentViewModel.Rule.Priority,
            onClick = { viewModel.selectRule(RoundRobinAssignmentViewModel.Rule.Priority) },
        )
        RuleCard(
            name = "Strict round-robin",
            desc = "One each, strictly in turn",
            icon = PantopusIcon.ArrowsRepeat,
            selected = content.rule == RoundRobinAssignmentViewModel.Rule.Strict,
            onClick = { viewModel.selectRule(RoundRobinAssignmentViewModel.Rule.Strict) },
        )
        BizOverline("Bookable members", modifier = Modifier.padding(top = Spacing.s2))
        if (content.checkedCount == 0) {
            BizNote(
                text = "Pick at least one member to take bookings.",
                tone = BizNoteTone.Warning,
                icon = PantopusIcon.AlertTriangle,
            )
        }
        BizCard {
            content.picks.forEachIndexed { index, pick ->
                RoundRobinMemberRow(
                    pick = pick,
                    rule = content.rule,
                    showDivider = index != content.picks.lastIndex,
                    onToggle = { viewModel.toggle(pick.id) },
                    onWeightMinus = { viewModel.decrementWeight(pick.id) },
                    onWeightPlus = { viewModel.incrementWeight(pick.id) },
                    onUp = { viewModel.moveUp(pick.id) },
                    onDown = { viewModel.moveDown(pick.id) },
                )
            }
        }
        if (content.isSingleMember) {
            BizNote(
                text = "Rotation needs two or more members. Bookings go to ${content.firstCheckedName ?: "this member"} for now.",
                tone = BizNoteTone.Info,
                icon = PantopusIcon.Info,
            )
        }
        toast?.let { BizNote(text = it, tone = BizNoteTone.Error, icon = PantopusIcon.AlertCircle) }
        BizPrimaryButton(
            text = "Done",
            onClick = onDone,
            enabled = !content.doneDisabled,
            saving = content.saving,
            modifier = Modifier.padding(top = Spacing.s2, bottom = Spacing.s5),
        )
    }
}

@Composable
private fun RuleCard(
    name: String,
    desc: String,
    icon: PantopusIcon,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(if (selected) bizAccentBg else PantopusColors.appSurface)
                .border(
                    width = if (selected) 1.5.dp else 1.dp,
                    color = if (selected) bizAccent else PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.lg),
                )
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(RULE_ICON_BOX)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(if (selected) PantopusColors.appSurface else PantopusColors.appSurfaceSunken),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 16.dp,
                tint = if (selected) bizAccent else PantopusColors.appTextSecondary,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = PantopusTextStyle.small, fontWeight = FontWeight.Bold, color = PantopusColors.appText)
            Text(text = desc, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }
        Box(
            modifier =
                Modifier
                    .size(RADIO)
                    .clip(CircleShape)
                    .background(if (selected) bizAccent else Color.Transparent)
                    .then(if (selected) Modifier else Modifier.border(1.5.dp, PantopusColors.appBorderStrong, CircleShape)),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = "Selected",
                    size = 12.dp,
                    tint = PantopusColors.appTextInverse,
                    strokeWidth = 3.2f,
                )
            }
        }
    }
}

@Composable
private fun RoundRobinMemberRow(
    pick: RoundRobinAssignmentViewModel.PickUi,
    rule: RoundRobinAssignmentViewModel.Rule,
    showDivider: Boolean,
    onToggle: () -> Unit,
    onWeightMinus: () -> Unit,
    onWeightPlus: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            BizCheckbox(on = pick.checked)
            MemberAvatar(name = pick.name, seed = pick.id, dim = !pick.checked)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pick.name,
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(text = "Uses personal availability", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
            if (pick.checked) {
                when (rule) {
                    RoundRobinAssignmentViewModel.Rule.Balanced ->
                        BizStepper(value = "×${pick.weight}", onMinus = onWeightMinus, onPlus = onWeightPlus)
                    RoundRobinAssignmentViewModel.Rule.Priority ->
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                            ReorderButton(icon = PantopusIcon.ChevronUp, onClick = onUp)
                            ReorderButton(icon = PantopusIcon.ChevronDown, onClick = onDown)
                        }
                    RoundRobinAssignmentViewModel.Rule.Strict -> Unit
                }
            }
        }
        if (showDivider) BizRowDivider()
    }
}

@Composable
private fun ReorderButton(
    icon: PantopusIcon,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(26.dp).clip(CircleShape).border(1.dp, PantopusColors.appBorder, CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 13.dp, tint = PantopusColors.appTextSecondary)
    }
}

@Composable
private fun RoundRobinLoading() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        repeat(3) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Radii.lg))
                        .background(PantopusColors.appSurfaceSunken)
                        .padding(Spacing.s5),
            )
        }
        BizOverline("Bookable members", modifier = Modifier.padding(top = Spacing.s2))
        BizCard {
            repeat(4) { i ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s3),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    Box(modifier = Modifier.size(34.dp).clip(CircleShape).background(PantopusColors.appSurfaceSunken))
                    Box(
                        modifier =
                            Modifier.weight(
                                1f,
                            ).size(12.dp).clip(RoundedCornerShape(Radii.xs)).background(PantopusColors.appSurfaceSunken),
                    )
                }
                if (i != 3) BizRowDivider()
            }
        }
    }
}
