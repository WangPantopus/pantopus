@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@file:Suppress("PackageNaming", "MagicNumber", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.compose.gig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.data.api.models.gigs.CareDetailsDto
import app.pantopus.android.data.api.models.gigs.EventDetailsDto
import app.pantopus.android.data.api.models.gigs.LogisticsDetailsDto
import app.pantopus.android.data.api.models.gigs.RemoteDetailsDto
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

// ════════════════════════════════════════════════════════════════
//  A12.8 — Fill-gaps archetype module fields
//
//  Compact editors for the JSONB module the detected `task_archetype`
//  owns (backend/utils/moduleSchemas.js):
//    care_task                 → care_details
//    home_service / quick_help → logistics_details
//    remote_task               → remote_details
//    event_shift               → event_details
//    delivery_errand           → items[]
//  `is_urgent` adds urgent_details at body-build time (VM).
// ════════════════════════════════════════════════════════════════

/** The module section the Fill-gaps step renders for an archetype, or null. */
@Composable
internal fun GigComposeModuleFields(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    val form = state.form
    when (form.taskArchetype) {
        "care_task" -> CareModuleFields(form.careDetails, vm::updateCareDetails)
        "home_service", "quick_help" -> LogisticsModuleFields(form.logisticsDetails, vm::updateLogisticsDetails)
        "remote_task" -> RemoteModuleFields(form.remoteDetails, vm::updateRemoteDetails)
        "event_shift" -> EventModuleFields(form.eventDetails, vm::updateEventDetails)
        "delivery_errand" -> ItemsModuleFields(state, vm)
        else -> Unit
    }
}

@Composable
private fun ModuleSection(
    title: String,
    testTag: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3)
                .testTag(testTag),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Text(
            title,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            color = PantopusColors.appTextSecondary,
        )
        content()
    }
}

@Composable
private fun ModuleChipRow(
    options: List<Pair<String, String>>,
    selectedValue: String?,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        options.forEach { (value, label) ->
            val active = value == selectedValue
            Box(
                modifier =
                    Modifier
                        .clip(CircleShape)
                        .background(if (active) PantopusColors.primary600 else PantopusColors.appSurfaceSunken)
                        .border(
                            width = 1.dp,
                            color = if (active) Color.Transparent else PantopusColors.appBorder,
                            shape = CircleShape,
                        )
                        .clickable(role = Role.Button, onClick = { onSelect(value) })
                        .padding(horizontal = Spacing.s3, vertical = Spacing.s1 + 3.dp)
                        .semantics {
                            contentDescription = label
                            selected = active
                        },
            ) {
                Text(
                    label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (active) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                )
            }
        }
    }
}

@Composable
private fun ModuleSwitchRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appText,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = PantopusColors.primary600, checkedThumbColor = Color.White),
            modifier = Modifier.testTag(testTag).semantics { contentDescription = label },
        )
    }
}

// ─── care_task ────────────────────────────────────────────────────

@Composable
private fun CareModuleFields(
    details: CareDetailsDto?,
    onUpdate: (CareDetailsDto?) -> Unit,
) {
    val current = details ?: CareDetailsDto()
    ModuleSection(title = "CARE DETAILS", testTag = "gigCompose.module.care") {
        ModuleChipRow(
            options = listOf("child" to "Child", "pet" to "Pet", "elder" to "Elder", "other" to "Other"),
            selectedValue = current.careType,
            onSelect = { onUpdate(current.copy(careType = it)) },
        )
        PantopusTextField(
            label = "Ages or details",
            value = current.agesOrDetails.orEmpty(),
            onValueChange = { onUpdate(current.copy(agesOrDetails = it)) },
            placeholder = "e.g. 5 and 8 years old",
            fieldTestTag = "gigCompose.module.care.ages",
        )
        PantopusTextField(
            label = "How many",
            value = current.count?.toString().orEmpty(),
            onValueChange = { onUpdate(current.copy(count = it.toIntOrNull())) },
            placeholder = "1",
            keyboardType = KeyboardType.Number,
            fieldTestTag = "gigCompose.module.care.count",
        )
    }
}

// ─── home_service / quick_help → logistics ────────────────────────

@Composable
private fun LogisticsModuleFields(
    details: LogisticsDetailsDto?,
    onUpdate: (LogisticsDetailsDto?) -> Unit,
) {
    val current = details ?: LogisticsDetailsDto()
    ModuleSection(title = "LOGISTICS", testTag = "gigCompose.module.logistics") {
        PantopusTextField(
            label = "Helpers needed",
            value = current.workerCount?.toString().orEmpty(),
            onValueChange = { onUpdate(current.copy(workerCount = it.toIntOrNull())) },
            placeholder = "1",
            keyboardType = KeyboardType.Number,
            fieldTestTag = "gigCompose.module.logistics.workers",
        )
        ModuleSwitchRow(
            label = "Vehicle needed",
            checked = current.vehicleNeeded == true,
            testTag = "gigCompose.module.logistics.vehicle",
            onToggle = { onUpdate(current.copy(vehicleNeeded = it)) },
        )
        ModuleSwitchRow(
            label = "Heavy lifting",
            checked = current.heavyLifting == true,
            testTag = "gigCompose.module.logistics.heavy",
            onToggle = { onUpdate(current.copy(heavyLifting = it)) },
        )
    }
}

// ─── remote_task ──────────────────────────────────────────────────

@Composable
private fun RemoteModuleFields(
    details: RemoteDetailsDto?,
    onUpdate: (RemoteDetailsDto?) -> Unit,
) {
    val current = details ?: RemoteDetailsDto()
    ModuleSection(title = "DELIVERABLE", testTag = "gigCompose.module.remote") {
        ModuleChipRow(
            options =
                listOf(
                    "document" to "Document",
                    "design" to "Design",
                    "code" to "Code",
                    "video" to "Video",
                    "other" to "Other",
                ),
            selectedValue = current.deliverableType,
            onSelect = { onUpdate(current.copy(deliverableType = it)) },
        )
        ModuleSwitchRow(
            label = "Meeting required",
            checked = current.meetingRequired == true,
            testTag = "gigCompose.module.remote.meeting",
            onToggle = { onUpdate(current.copy(meetingRequired = it)) },
        )
    }
}

// ─── event_shift ──────────────────────────────────────────────────

@Composable
private fun EventModuleFields(
    details: EventDetailsDto?,
    onUpdate: (EventDetailsDto?) -> Unit,
) {
    val current = details ?: EventDetailsDto()
    ModuleSection(title = "EVENT SHIFT", testTag = "gigCompose.module.event") {
        ModuleChipRow(
            options =
                listOf(
                    "party" to "Party",
                    "wedding" to "Wedding",
                    "corporate" to "Corporate",
                    "community" to "Community",
                    "other" to "Other",
                ),
            selectedValue = current.eventType,
            onSelect = { onUpdate(current.copy(eventType = it)) },
        )
        ModuleChipRow(
            options =
                listOf(
                    "setup" to "Setup",
                    "serving" to "Serving",
                    "bartending" to "Bartending",
                    "cleanup" to "Cleanup",
                    "general" to "General",
                ),
            selectedValue = current.roleType,
            onSelect = { onUpdate(current.copy(roleType = it)) },
        )
        PantopusTextField(
            label = "Guest count",
            value = current.guestCount?.toString().orEmpty(),
            onValueChange = { onUpdate(current.copy(guestCount = it.toIntOrNull())) },
            placeholder = "25",
            keyboardType = KeyboardType.Number,
            fieldTestTag = "gigCompose.module.event.guests",
        )
    }
}

// ─── delivery_errand → items[] ────────────────────────────────────

@Composable
private fun ItemsModuleFields(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    val items = state.form.items
    ModuleSection(title = "SHOPPING LIST", testTag = "gigCompose.module.items") {
        items.forEachIndexed { index, item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    PantopusTextField(
                        label = "Item ${index + 1}",
                        value = item.name,
                        onValueChange = { vm.updateItemName(index, it) },
                        placeholder = "e.g. Milk, 2%",
                        fieldTestTag = "gigCompose.module.item_$index",
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(PantopusColors.appSurfaceSunken)
                            .clickable(role = Role.Button, onClick = { vm.removeItem(index) })
                            .testTag("gigCompose.module.itemRemove_$index")
                            .semantics { contentDescription = "Remove item ${index + 1}" },
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.X,
                        contentDescription = null,
                        size = 14.dp,
                        tint = PantopusColors.appTextSecondary,
                    )
                }
            }
        }
        if (items.size < GigComposeLimits.MAX_ITEMS) {
            Row(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(Radii.sm))
                        .clickable(role = Role.Button, onClick = vm::addItem)
                        .padding(vertical = Spacing.s1)
                        .testTag("gigCompose.module.itemAdd"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Plus,
                    contentDescription = null,
                    size = 14.dp,
                    tint = PantopusColors.primary600,
                )
                Text(
                    "Add an item",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.primary600,
                )
            }
        }
    }
}
