@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "LongParameterList", "CyclomaticComplexMethod", "UnusedParameter")

package app.pantopus.android.ui.screens.scheduling.eventtypes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.ErrorState
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingScreenState
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingStateScaffold
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.screens.shared.form.FormShellLeading
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

@Composable
fun IntakeQuestionsEditorScreen(
    eventTypeId: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: IntakeQuestionsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    var toastText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(saved) {
        if (saved) {
            viewModel.savedConsumed()
            onBack()
        }
    }
    LaunchedEffect(toast) {
        toast?.let {
            toastText = it
            viewModel.toastConsumed()
        }
    }
    LaunchedEffect(toastText) {
        if (toastText != null) {
            delay(2200)
            toastText = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (val s = state) {
            IntakeUiState.Loading ->
                SchedulingStateScaffold(state = SchedulingScreenState.Loading, onRetry = viewModel::load) {}
            IntakeUiState.NeedsSaveFirst -> NeedsSaveFirst(onBack = onBack)
            is IntakeUiState.Error -> ErrorState(message = s.message, onRetry = viewModel::load)
            is IntakeUiState.Content -> IntakeContent(state = s, viewModel = viewModel, onBack = onBack)
        }
        toastText?.let { msg ->
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = Spacing.s12)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(PantopusColors.appText)
                        .padding(horizontal = Spacing.s4, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                PantopusIconImage(icon = PantopusIcon.AlertCircle, contentDescription = null, size = 15.dp, tint = PantopusColors.warning)
                Text(text = msg, color = PantopusColors.appTextInverse, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun IntakeContent(
    state: IntakeUiState.Content,
    viewModel: IntakeQuestionsViewModel,
    onBack: () -> Unit,
) {
    FormShell(
        title = "Intake questions",
        subtitle = state.eventName,
        isValid = true,
        isDirty = state.isDirty,
        onClose = onBack,
        onCommit = viewModel::save,
        rightActionLabel = "Done",
        isSaving = state.isSaving,
        leading = FormShellLeading.Back,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(
                "Ask people a few things when they book. Name and email are always asked.",
                fontSize = 11.sp,
                color = PantopusColors.appTextSecondary,
            )
            EtCard {
                LockedRow(icon = PantopusIcon.User, label = "Name")
                LockedRow(icon = PantopusIcon.Mail, label = "Email", last = true)
            }
            Text(
                "YOUR QUESTIONS",
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextMuted,
                modifier = Modifier.padding(top = Spacing.s2),
            )
            if (state.questions.isEmpty() && state.editing == null) {
                Text("You haven't added any yet.", fontSize = 11.sp, color = PantopusColors.appTextSecondary)
            }
            state.questions.forEach { q ->
                if (state.editing?.draft?.localId == q.localId && !state.editing.isNew) {
                    EditGroup(editing = state.editing, viewModel = viewModel)
                } else {
                    QuestionRow(
                        draft = q,
                        onEdit = { viewModel.editQuestion(q.localId) },
                        onDelete = { viewModel.deleteQuestion(q.localId) },
                    )
                }
            }
            if (state.editing?.isNew == true) {
                EditGroup(editing = state.editing, viewModel = viewModel)
            }
            EtPrimaryButton(
                label = "Add a question",
                onClick = viewModel::startAdd,
                leadingIcon = PantopusIcon.Plus,
                modifier = Modifier.padding(top = Spacing.s2),
            )
            Spacer(Modifier.height(Spacing.s4))
        }
    }
}

@Composable
private fun LockedRow(
    icon: PantopusIcon,
    label: String,
    last: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(Radii.md)).background(PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(icon = icon, contentDescription = null, size = 15.dp, tint = PantopusColors.appTextSecondary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
                Text("Always asked", fontSize = 10.5.sp, color = PantopusColors.appTextMuted, modifier = Modifier.padding(top = 1.dp))
            }
            PantopusIconImage(
                icon = PantopusIcon.Lock,
                contentDescription = "Always asked",
                size = 14.dp,
                tint = PantopusColors.appTextMuted,
            )
        }
        if (!last) HorizontalDivider(thickness = 1.dp, color = PantopusColors.appBorder)
    }
}

@Composable
private fun QuestionRow(
    draft: QuestionDraft,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .clickable(onClick = onEdit)
                .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(draft.label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                modifier = Modifier.padding(top = 3.dp),
            ) {
                Text(draft.type.typeCaption, fontSize = 11.sp, color = PantopusColors.appTextSecondary)
                if (draft.required) RequiredPill()
            }
        }
        Box(
            modifier = Modifier.size(30.dp).clip(RoundedCornerShape(Radii.md)).clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(icon = PantopusIcon.Trash2, contentDescription = "Delete", size = 15.dp, tint = PantopusColors.appTextMuted)
        }
    }
}

@Composable
private fun RequiredPill() {
    Box(
        modifier =
            Modifier.clip(
                RoundedCornerShape(Radii.pill),
            ).background(PantopusColors.primary50).padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text("REQUIRED", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = PantopusColors.primary700)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditGroup(
    editing: EditingQuestion,
    viewModel: IntakeQuestionsViewModel,
) {
    val draft = editing.draft
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.primary50)
                .border(1.5.dp, PantopusColors.primary600, RoundedCornerShape(Radii.lg))
                .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        EtTextField(value = draft.label, onValueChange = viewModel::onEditLabel, label = "Question", placeholder = "What should we cover?")
        Column {
            EtFieldLabel(text = "Answer type")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                QuestionType.entries.forEach { type ->
                    TypeChip(label = type.label, selected = type == draft.type, onClick = { viewModel.onEditType(type) })
                }
            }
        }
        if (draft.type.hasOptions) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                EtFieldLabel(text = "Options")
                draft.options.forEachIndexed { index, option ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                        EtTextField(value = option, onValueChange = {
                            viewModel.onEditOption(index, it)
                        }, placeholder = "Option ${index + 1}", modifier = Modifier.weight(1f))
                        Box(
                            modifier =
                                Modifier.size(28.dp).clip(RoundedCornerShape(Radii.md)).clickable {
                                    viewModel.removeOption(index)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            PantopusIconImage(
                                icon = PantopusIcon.X,
                                contentDescription = "Remove option",
                                size = 14.dp,
                                tint = PantopusColors.appTextMuted,
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.clickable(onClick = viewModel::addOption).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    PantopusIconImage(icon = PantopusIcon.Plus, contentDescription = null, size = 13.dp, tint = PantopusColors.primary600)
                    Text("Add option", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.primary600)
                }
            }
        }
        Row(
            modifier =
                Modifier.fillMaxWidth().clip(
                    RoundedCornerShape(Radii.md),
                ).background(
                    PantopusColors.appSurface,
                ).border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md)).padding(horizontal = 11.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Make this required",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            EtToggle(checked = draft.required, onToggle = viewModel::onEditRequired)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            EtPrimaryButton(
                label = "Save question",
                onClick = viewModel::saveEditing,
                enabled = draft.canSave,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier =
                    Modifier.clip(
                        RoundedCornerShape(Radii.md),
                    ).clickable(onClick = viewModel::cancelEditing).padding(horizontal = Spacing.s2, vertical = 10.dp),
            ) {
                Text("Cancel", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextSecondary)
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(if (selected) PantopusColors.appSurface else PantopusColors.appSurfaceSunken)
                .border(1.dp, if (selected) PantopusColors.primary600 else PantopusColors.appBorder, RoundedCornerShape(7.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 11.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) PantopusColors.primary700 else PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun NeedsSaveFirst(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(PantopusColors.appBg)) {
        EtTopBar(title = "Intake questions", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = Spacing.s8),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(Radii.pill)).background(PantopusColors.appSurfaceSunken),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ListChecks,
                    contentDescription = null,
                    size = 26.dp,
                    tint = PantopusColors.appTextMuted,
                )
            }
            Spacer(Modifier.height(Spacing.s4))
            Text("Save your event type first", fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
            Spacer(Modifier.height(Spacing.s2))
            Text(
                "Create the event type, then come back to add the questions people answer when they book.",
                fontSize = 12.sp,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.padding(horizontal = Spacing.s2),
            )
        }
    }
}
