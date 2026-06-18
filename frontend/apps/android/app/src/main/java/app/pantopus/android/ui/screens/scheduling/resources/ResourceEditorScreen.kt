@file:Suppress(
    "PackageNaming",
    "UNUSED_PARAMETER",
    "LongMethod",
    "CyclomaticComplexMethod",
    "MagicNumber",
    "LongParameterList",
)
@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package app.pantopus.android.ui.screens.scheduling.resources

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingLoadingSkeleton
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

const val RESOURCE_EDITOR_TAG = "scheduling.resourceEditor"
const val RESOURCE_EDITOR_NAME_TAG = "scheduling.resourceEditor.nameField"
const val RESOURCE_EDITOR_DELETE_TAG = "scheduling.resourceEditor.delete"

private val CLOCK_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/** F10 Resource Editor — create / edit / delete. */
@Composable
fun ResourceEditorScreen(
    resourceId: String,
    onBack: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
    viewModel: ResourceEditorViewModel = hiltViewModel(),
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val saveError by viewModel.saveError.collectAsStateWithLifecycle()
    val showDelete by viewModel.showDeleteConfirm.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var nameTouched by remember { mutableStateOf(false) }

    LaunchedStart { viewModel.start() }

    val canSave =
        loadState is ResourceEditorLoadState.Ready && viewModel.isValid && viewModel.isDirty

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag(RESOURCE_EDITOR_TAG),
        containerColor = PantopusColors.appBg,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        viewModel.screenTitle,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.appText,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        PantopusIconImage(
                            icon = PantopusIcon.X,
                            contentDescription = "Close",
                            tint = PantopusColors.appText,
                        )
                    }
                },
                actions = {
                    if (isSaving) {
                        CircularProgressIndicator(
                            color = PantopusColors.home,
                            strokeWidth = 2.dp,
                            modifier = Modifier.padding(end = Spacing.s4).size(20.dp),
                        )
                    } else {
                        TextButton(onClick = {
                            scope.launch { if (viewModel.save()) onBack() }
                        }, enabled = canSave) {
                            Text(
                                text = "Save",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (canSave) PantopusColors.home else PantopusColors.appTextMuted,
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = PantopusColors.appSurface,
                    ),
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = loadState) {
                ResourceEditorLoadState.Loading -> EditorSkeleton()
                is ResourceEditorLoadState.Error ->
                    EditorError(
                        state.message,
                        onRetry = viewModel::load,
                    )
                ResourceEditorLoadState.Ready ->
                    EditorBody(
                        form = form,
                        viewModel = viewModel,
                        showNameError = nameTouched && form.name.isBlank(),
                        onNameChange = {
                            nameTouched = true
                            viewModel.setName(it)
                        },
                    )
            }
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            confirmButton = {
                TextButton(onClick = { scope.launch { if (viewModel.confirmDelete()) onBack() } }) {
                    Text("Delete", color = PantopusColors.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text("Keep") } },
            title = { Text("Delete ${form.name.ifBlank { "resource" }}?") },
            text = {
                Text("Existing bookings stay on the calendar. New bookings will be turned off.")
            },
        )
    }

    saveError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearSaveError,
            confirmButton = { TextButton(onClick = viewModel::clearSaveError) { Text("OK") } },
            title = { Text("Couldn't save") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun LaunchedStart(block: () -> Unit) {
    androidx.compose.runtime.LaunchedEffect(Unit) { block() }
}

@Composable
private fun EditorBody(
    form: ResourceEditorForm,
    viewModel: ResourceEditorViewModel,
    showNameError: Boolean,
    onNameChange: (String) -> Unit,
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        // Details
        SectionCard(overline = "Details") {
            PantopusTextField(
                label = "Name",
                value = form.name,
                onValueChange = onNameChange,
                placeholder = "Name this resource",
                isRequired = true,
                state =
                    if (showNameError) {
                        PantopusFieldState.Error(
                            "Give this resource a name",
                        )
                    } else {
                        PantopusFieldState.Default
                    },
                fieldTestTag = RESOURCE_EDITOR_NAME_TAG,
            )
            Text(
                "Type",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = PantopusColors.appText,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                ResourceKind.entries.forEach { kind ->
                    SelectChip(label = kind.label, isOn = kind == form.kind, onClick = {
                        viewModel.selectKind(kind)
                    })
                }
            }
        }

        // Who can book
        SectionCard(overline = "Who can book") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                modifier = Modifier.fillMaxWidth(),
            ) {
                WhoCanBook.entries.forEach { option ->
                    SelectChip(
                        label = option.label,
                        isOn = option == form.whoCanBook,
                        onClick = { viewModel.setWhoCanBook(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (form.whoCanBook != WhoCanBook.Members) {
                Text(
                    "In this version, all active home members can book. Per-member access is coming soon.",
                    fontSize = 11.sp,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }

        // Booking rules
        SectionCard(overline = "Booking rules") {
            CounterRow(
                label = "Max duration",
                value = form.maxDurationHours,
                onValueChange = viewModel::setMaxDurationHours,
                unit = "hr",
                range = 1..24,
                error = form.maxDurationHours <= 0,
            )
            HorizontalDivider(color = PantopusColors.appBorderSubtle)
            CounterRow(
                label = "Buffer between bookings",
                value = form.bufferMin,
                onValueChange = viewModel::setBufferMin,
                unit = "min",
                range = 0..120,
                step = 5,
            )
            HorizontalDivider(color = PantopusColors.appBorderSubtle)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Requires approval",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = form.requiresApproval,
                    onCheckedChange = viewModel::setRequiresApproval,
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = PantopusColors.appSurface,
                            checkedTrackColor = PantopusColors.home,
                        ),
                )
            }
        }

        // Available hours
        SectionCard(overline = "Available hours") {
            WeekdayPicker(selected = form.hoursDays, onToggle = viewModel::toggleDay)
            HorizontalDivider(color = PantopusColors.appBorderSubtle)
            PickerValueRow(
                label = "Available from",
                value =
                    form.hoursStart.format(
                        CLOCK_FORMAT,
                    ),
                onClick = {
                    showStartPicker =
                        true
                },
            )
            PickerValueRow(
                label = "Available until",
                value =
                    form.hoursEnd.format(
                        CLOCK_FORMAT,
                    ),
                onClick = {
                    showEndPicker =
                        true
                },
            )
        }

        if (!viewModel.isCreate) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextButton(
                    onClick = viewModel::requestDelete,
                    modifier = Modifier.testTag(RESOURCE_EDITOR_DELETE_TAG),
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Trash2,
                        contentDescription = null,
                        size = 14.dp,
                        tint = PantopusColors.error,
                    )
                    Text(
                        "  Delete resource",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PantopusColors.error,
                    )
                }
            }
        }
    }

    if (showStartPicker) {
        ResourceTimePickerDialog(
            initial = form.hoursStart,
            onSelect = {
                viewModel.setHoursStart(it)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false },
        )
    }
    if (showEndPicker) {
        ResourceTimePickerDialog(
            initial = form.hoursEnd,
            onSelect = {
                viewModel.setHoursEnd(it)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false },
        )
    }
}

@Composable
private fun EditorSkeleton() {
    SchedulingLoadingSkeleton(rows = 4)
}

@Composable
private fun EditorError(
    message: String,
    onRetry: () -> Unit,
) {
    app.pantopus.android.ui.components
        .ErrorState(message = message, onRetry = onRetry)
}
