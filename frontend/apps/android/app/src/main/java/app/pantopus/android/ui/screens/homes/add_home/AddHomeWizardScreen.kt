@file:Suppress("PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.homes.add_home

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
import app.pantopus.android.ui.screens.shared.wizard.blocks.FormFieldsBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.ReviewSummaryBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.ReviewSummaryRow
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SuccessHeroBlock
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Test tag applied to the AddHome screen container. */
const val ADD_HOME_SCREEN_TAG = "addHomeWizard"

/**
 * Concrete Add-Home wizard composable. The view model survives config
 * changes via Hilt's `SavedStateHandle`, so the wizard restores after
 * process death (acceptance criterion #5).
 */
@Composable
fun AddHomeWizardScreen(
    onDismiss: () -> Unit,
    onOpenHomeDashboard: (String) -> Unit,
    viewModel: AddHomeWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            AddHomeOutboundEvent.Dismiss -> {
                viewModel.acknowledgeEvent()
                onDismiss()
            }
            is AddHomeOutboundEvent.OpenHomeDashboard -> {
                viewModel.acknowledgeEvent()
                onOpenHomeDashboard(event.homeId)
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        // Fire the initial step view; subsequent transitions emit
        // their own ScreenAddHomeWizardStepViewed events from the VM.
        val current = state.form.currentStep
        current.stepNumber?.let { number ->
            Analytics.track(
                AnalyticsEvent.ScreenAddHomeWizardStepViewed(
                    stepNumber = number,
                    stepName = current.name,
                ),
            )
        }
    }

    WizardShell(
        model = viewModel,
        modifier = Modifier.testTag(ADD_HOME_SCREEN_TAG),
    ) {
        when (state.form.currentStep) {
            AddHomeStep.Address -> AddressStep(state, viewModel)
            AddHomeStep.Confirm -> ConfirmStep(state, viewModel)
            AddHomeStep.Role -> RoleStep(state, viewModel)
            AddHomeStep.Review -> ReviewStep(state)
            AddHomeStep.Success -> SuccessStep()
        }
        state.errorMessage?.let { ErrorBanner(it) }
    }
}

// MARK: - Step 1

@Composable
private fun AddressStep(
    state: AddHomeUiState,
    vm: AddHomeWizardViewModel,
) {
    HeadlineBlock("What's the address?")
    SubcopyBlock("Enter the street, city, state, and ZIP for the home you'd like to add.")
    FormFieldsBlock {
        PantopusTextField(
            label = "Street",
            value = state.form.address.street,
            onValueChange = { vm.updateField(AddressField.Street, it) },
            placeholder = "123 Main St",
            fieldTestTag = "addHome_street",
        )
        PantopusTextField(
            label = "Unit (optional)",
            value = state.form.address.unit,
            onValueChange = { vm.updateField(AddressField.Unit, it) },
            placeholder = "Apt 4B",
            fieldTestTag = "addHome_unit",
        )
        PantopusTextField(
            label = "City",
            value = state.form.address.city,
            onValueChange = { vm.updateField(AddressField.City, it) },
            fieldTestTag = "addHome_city",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            PantopusTextField(
                label = "State",
                value = state.form.address.state,
                onValueChange = { vm.updateField(AddressField.State, it) },
                modifier = Modifier.weight(1f),
                fieldTestTag = "addHome_state",
            )
            PantopusTextField(
                label = "ZIP",
                value = state.form.address.zipCode,
                onValueChange = { vm.updateField(AddressField.Zip, it) },
                modifier = Modifier.weight(1f),
                fieldTestTag = "addHome_zip",
            )
        }
    }
    if (state.suggestions.isNotEmpty()) {
        SuggestionList(state.suggestions, onSelect = vm::selectSuggestion)
    }
}

// MARK: - Step 2

@Composable
private fun ConfirmStep(
    state: AddHomeUiState,
    vm: AddHomeWizardViewModel,
) {
    HeadlineBlock("Confirm the property")
    SubcopyBlock(
        "We checked this address against our property records. Review the details before continuing.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
        ReviewSummaryBlock(
            rows =
                listOf(
                    ReviewSummaryRow("Street", state.form.address.street),
                    ReviewSummaryRow("City", state.form.address.city),
                    ReviewSummaryRow("State", state.form.address.state),
                    ReviewSummaryRow("ZIP", state.form.address.zipCode),
                ),
        )
        state.addressCheck?.let { check ->
            AddressVerdictRow(check)
        }
        PrimaryHomeToggle(
            isPrimary = state.form.isPrimary,
            onChange = vm::setPrimaryHome,
        )
    }
}

// MARK: - Step 3

@Composable
private fun RoleStep(
    state: AddHomeUiState,
    vm: AddHomeWizardViewModel,
) {
    HeadlineBlock("What's your role?")
    SubcopyBlock("This determines what verification we'll ask for next.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        AddHomeRole.entries.forEach { role ->
            RoleRow(
                role = role,
                isSelected = state.form.role == role,
                onTap = { vm.selectRole(role) },
            )
        }
    }
}

// MARK: - Step 4

@Composable
private fun ReviewStep(state: AddHomeUiState) {
    HeadlineBlock("Review and submit")
    SubcopyBlock("Make sure everything below looks right before submitting.")
    val composedAddress =
        buildString {
            append(state.form.address.street)
            if (state.form.address.unit
                    .isNotEmpty()
            ) {
                append(", ${state.form.address.unit}")
            }
            append(", ${state.form.address.city}")
            append(", ${state.form.address.state} ${state.form.address.zipCode}")
        }
    ReviewSummaryBlock(
        rows =
            listOf(
                ReviewSummaryRow("Address", composedAddress),
                ReviewSummaryRow("Role", state.form.role?.label ?: "—"),
                ReviewSummaryRow("Primary", if (state.form.isPrimary) "Yes" else "No"),
            ),
    )
}

// MARK: - Step 5

@Composable
private fun SuccessStep() {
    SuccessHeroBlock(
        headline = "Home added",
        subcopy = "We'll email you when verification completes.",
    )
}

// MARK: - Helpers

@Composable
private fun SuggestionList(
    suggestions: List<String>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface),
    ) {
        suggestions.forEachIndexed { index, suggestion ->
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSelect(suggestion) }
                        .padding(Spacing.s3)
                        .testTag("addHome_suggestion_$index"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = suggestion,
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appText,
                    modifier = Modifier.weight(1f),
                )
                PantopusIconImage(
                    icon = PantopusIcon.ChevronRight,
                    contentDescription = null,
                    size = 16.dp,
                    tint = PantopusColors.appTextSecondary,
                )
            }
            if (index != suggestions.lastIndex) {
                androidx.compose.material3.HorizontalDivider(
                    thickness = 1.dp,
                    color = PantopusColors.appBorderSubtle,
                )
            }
        }
    }
}

@Composable
private fun AddressVerdictRow(check: app.pantopus.android.data.api.models.homes.CheckAddressResponse) {
    val verdict =
        if (check.exists) {
            Verdict(
                icon = PantopusIcon.AlertCircle,
                color = PantopusColors.warning,
                headline = "Already on Pantopus",
                subcopy = "Another household already has this address. We'll route you to a join flow next.",
            )
        } else {
            Verdict(
                icon = PantopusIcon.CheckCircle,
                color = PantopusColors.success,
                headline = "Looks good",
                subcopy = "We'll create a new household for this address.",
            )
        }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurfaceMuted)
                .padding(Spacing.s3)
                .semantics(mergeDescendants = true) {
                    contentDescription = "${verdict.headline}. ${verdict.subcopy}"
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(icon = verdict.icon, contentDescription = null, size = 20.dp, tint = verdict.color)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = verdict.headline, style = PantopusTextStyle.body, color = PantopusColors.appText)
            Text(
                text = verdict.subcopy,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

private data class Verdict(
    val icon: PantopusIcon,
    val color: androidx.compose.ui.graphics.Color,
    val headline: String,
    val subcopy: String,
)

@Composable
private fun PrimaryHomeToggle(
    isPrimary: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "This is my primary home",
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
            )
            Text(
                text = "Use this home for default mail and notifications.",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
        Switch(
            checked = isPrimary,
            onCheckedChange = onChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PantopusColors.appTextInverse,
                    checkedTrackColor = PantopusColors.primary600,
                ),
            modifier = Modifier.testTag("addHome_primaryToggle"),
        )
    }
}

@Composable
private fun RoleRow(
    role: AddHomeRole,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .clickable(onClick = onTap, role = Role.RadioButton)
                .padding(Spacing.s3)
                .testTag("addHome_role_${role.name.lowercase()}")
                .semantics { contentDescription = role.label },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        RadioCircle(isSelected = isSelected)
        Text(text = role.label, style = PantopusTextStyle.body, color = PantopusColors.appText)
    }
}

@Composable
private fun RadioCircle(isSelected: Boolean) {
    val borderColor = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder
    Box(
        modifier =
            Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(PantopusColors.appSurface)
                .border(width = 2.dp, color = borderColor, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier =
                    Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.primary600),
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.errorBg)
                .padding(Spacing.s3)
                .testTag("addHomeErrorBanner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 18.dp,
            tint = PantopusColors.error,
        )
        Text(
            text = message,
            style = PantopusTextStyle.caption,
            color = PantopusColors.error,
        )
    }
}
