@file:Suppress("PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.compose.gig

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Test tag applied to the GigCompose screen container. */
const val GIG_COMPOSE_SCREEN_TAG = "composeGigWizard"

/**
 * Concrete Post-a-Task wizard composable. The view model survives
 * config changes via Hilt's `SavedStateHandle`, so the wizard restores
 * after process death.
 */
@Composable
fun GigComposeWizardScreen(
    onDismiss: () -> Unit,
    onOpenGigDetail: (String) -> Unit,
    preselectedCategoryKey: String? = null,
    viewModel: GigComposeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()

    LaunchedEffect(preselectedCategoryKey) {
        viewModel.preselectCategoryIfNeeded(GigComposeCategory.fromRawKey(preselectedCategoryKey))
    }

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            GigComposeOutboundEvent.Dismiss -> {
                viewModel.acknowledgeEvent()
                onDismiss()
            }
            is GigComposeOutboundEvent.OpenGigDetail -> {
                viewModel.acknowledgeEvent()
                onOpenGigDetail(event.gigId)
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        val current = state.form.currentStep
        current.stepNumber?.let { number ->
            Analytics.track(
                AnalyticsEvent.ScreenComposeGigWizardStepViewed(
                    stepNumber = number,
                    stepName = current.name,
                ),
            )
        }
    }

    WizardShell(
        model = viewModel,
        modifier = Modifier.testTag(GIG_COMPOSE_SCREEN_TAG),
    ) {
        when (state.form.currentStep) {
            GigComposeStep.Category -> CategoryStep(state, viewModel)
            GigComposeStep.Basics -> BasicsStep(state, viewModel)
            GigComposeStep.Budget -> BudgetStep(state, viewModel)
            GigComposeStep.Schedule -> ScheduleStep(state, viewModel)
            GigComposeStep.Location -> LocationStep(state, viewModel)
            GigComposeStep.Review -> ReviewStep(state)
            GigComposeStep.Success -> SuccessStep()
        }
        state.errorMessage?.let { ErrorBanner(it) }
    }
}

// MARK: - Step 1: Category

@Composable
private fun CategoryStep(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    HeadlineBlock("What kind of help do you need?")
    SubcopyBlock("Pick the closest match. You can refine it later.")
    val rows = GigComposeCategory.entries.toList().chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        for (row in rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                for (category in row) {
                    Box(modifier = Modifier.weight(1f)) {
                        CategoryTile(
                            category = category,
                            isSelected = state.form.category == category,
                            onTap = { vm.selectCategory(category) },
                        )
                    }
                }
                // Pad the last row out to 3 cells so the tiles don't
                // stretch when the count isn't a multiple of 3.
                repeat(3 - row.size) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: GigComposeCategory,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    val (icon, _) = categoryIconAndLabel(category)
    val borderColor = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder
    val bg = if (isSelected) PantopusColors.primary50 else PantopusColors.appSurface
    val iconTint = if (isSelected) PantopusColors.primary600 else PantopusColors.appTextSecondary

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 88.dp)
                .clip(RoundedCornerShape(Radii.lg))
                .background(bg)
                .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(Radii.lg))
                .clickable(role = Role.Button, onClick = onTap)
                .padding(Spacing.s3)
                .testTag("composeGig_category_${category.key}")
                .semantics {
                    contentDescription = if (isSelected) "${category.label}, selected" else category.label
                },
        verticalArrangement = Arrangement.spacedBy(Spacing.s2, alignment = Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 22.dp, tint = iconTint)
        Text(
            text = category.label,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appText,
        )
    }
}

private fun categoryIconAndLabel(category: GigComposeCategory): Pair<PantopusIcon, String> =
    when (category) {
        GigComposeCategory.Handyman -> PantopusIcon.Hammer to "Handyman"
        GigComposeCategory.Cleaning -> PantopusIcon.Sparkles to "Cleaning"
        GigComposeCategory.Moving -> PantopusIcon.Package to "Moving"
        GigComposeCategory.PetCare -> PantopusIcon.PawPrint to "Pet care"
        GigComposeCategory.ChildCare -> PantopusIcon.Heart to "Child care"
        GigComposeCategory.Tutoring -> PantopusIcon.Lightbulb to "Tutoring"
        GigComposeCategory.Delivery -> PantopusIcon.Send to "Delivery"
        GigComposeCategory.Tech -> PantopusIcon.Zap to "Tech"
        GigComposeCategory.Other -> PantopusIcon.MoreHorizontal to "Other"
    }

// MARK: - Step 2: Basics

@Composable
private fun BasicsStep(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    HeadlineBlock("Describe the task")
    SubcopyBlock("A clear title and a few details help neighbors decide if it's right for them.")
    FormFieldsBlock {
        PantopusTextField(
            label = "Title",
            value = state.form.title,
            onValueChange = vm::setTitle,
            placeholder = "Hang 3 shelves in the living room",
            fieldTestTag = "composeGig_title",
        )
        DescriptionField(value = state.form.description, onValueChange = vm::setDescription)
        CharacterCounter(
            current = state.form.description.length,
            min = GigComposeLimits.DESCRIPTION_MIN,
            max = GigComposeLimits.DESCRIPTION_MAX,
        )
    }
    PhotoSlotsRow(
        count = state.form.photoIds.size,
        max = GigComposeLimits.MAX_PHOTOS,
        onAdd = vm::addPlaceholderPhoto,
        onRemove = vm::removePhoto,
    )
}

@Composable
private fun DescriptionField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(text = "Description", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(width = 1.dp, color = PantopusColors.appBorder, shape = RoundedCornerShape(Radii.md))
                    .padding(Spacing.s2),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(PantopusColors.primary600),
                modifier = Modifier.fillMaxWidth().testTag("composeGig_description"),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Add as much detail as you can.",
                            style = PantopusTextStyle.body,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
    }
}

@Composable
private fun CharacterCounter(
    current: Int,
    min: Int,
    max: Int,
) {
    val needsMore = current < min
    val label = if (needsMore) "${min - current} more characters" else "$current / $max"
    val color = if (needsMore) PantopusColors.warning else PantopusColors.appTextSecondary
    Text(
        text = label,
        style = PantopusTextStyle.caption,
        color = color,
        modifier =
            Modifier
                .fillMaxWidth()
                .testTag("composeGig_descriptionCounter"),
    )
}

@Composable
private fun PhotoSlotsRow(
    count: Int,
    max: Int,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Text(
            text = "Photos (optional, up to $max)",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            repeat(count) { index ->
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .background(PantopusColors.primary50)
                            .clickable(role = Role.Button) { onRemove(index) }
                            .testTag("composeGig_photo_$index")
                            .semantics { contentDescription = "Remove photo ${index + 1}" },
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Camera,
                        contentDescription = null,
                        size = 22.dp,
                        tint = PantopusColors.primary600,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    PantopusIconImage(
                        icon = PantopusIcon.X,
                        contentDescription = null,
                        size = 14.dp,
                        tint = PantopusColors.appText,
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.s1),
                    )
                }
            }
            if (count < max) {
                Box(
                    modifier =
                        Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .border(width = 1.dp, color = PantopusColors.appBorder, shape = RoundedCornerShape(Radii.md))
                            .clickable(role = Role.Button, onClick = onAdd)
                            .testTag("composeGig_addPhoto")
                            .semantics { contentDescription = "Add photo" },
                ) {
                    PantopusIconImage(
                        icon = PantopusIcon.Plus,
                        contentDescription = null,
                        size = 22.dp,
                        tint = PantopusColors.appTextSecondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

// MARK: - Step 3: Budget

@Composable
private fun BudgetStep(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    HeadlineBlock("Set your budget")
    SubcopyBlock("Pick a price model. Helpers see this on the gig card.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        GigComposeBudgetType.entries.forEach { type ->
            RadioRow(
                label = type.label,
                subcopy = type.subcopy(),
                isSelected = state.form.budgetType == type,
                testTag = "composeGig_budget_${type.wireValue}",
                onTap = { vm.selectBudgetType(type) },
            )
        }
    }
    val selected = state.form.budgetType
    if (selected != null && selected != GigComposeBudgetType.Offers) {
        val suffix = if (selected == GigComposeBudgetType.Hourly) "/ hr" else "total"
        FormFieldsBlock {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PantopusTextField(
                    label = "Min $suffix",
                    value = state.form.budgetMin,
                    onValueChange = vm::setBudgetMin,
                    placeholder = "20",
                    keyboardType = KeyboardType.Decimal,
                    fieldTestTag = "composeGig_budgetMin",
                    modifier = Modifier.weight(1f),
                )
                PantopusTextField(
                    label = "Max $suffix",
                    value = state.form.budgetMax,
                    onValueChange = vm::setBudgetMax,
                    placeholder = "Optional",
                    keyboardType = KeyboardType.Decimal,
                    fieldTestTag = "composeGig_budgetMax",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// MARK: - Step 4: Schedule

@Composable
private fun ScheduleStep(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    HeadlineBlock("When does it need to happen?")
    SubcopyBlock("Pick one — you can change it later.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        GigComposeScheduleType.entries.forEach { type ->
            RadioRow(
                label = type.label,
                subcopy = type.subcopy(),
                isSelected = state.form.scheduleType == type,
                testTag = "composeGig_schedule_${type.name.lowercase()}",
                onTap = { vm.selectScheduleType(type) },
            )
        }
    }
    if (state.form.scheduleType == GigComposeScheduleType.OneTime) {
        OneTimeDateRow(state, vm)
    }
}

@Composable
private fun OneTimeDateRow(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    FormFieldsBlock {
        val current =
            state.form.scheduledStartISO?.let { iso ->
                runCatching { Instant.parse(iso) }.getOrNull()
            }
        val formatted =
            current?.let { instant ->
                LocalDateTime.ofInstant(instant, ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
            } ?: "Pick a date & time"
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .border(width = 1.dp, color = PantopusColors.appBorder, shape = RoundedCornerShape(Radii.md))
                    .clickable(role = Role.Button) {
                        // Native pickers aren't wired in this prompt — the
                        // tap shortcuts a 24h-out placeholder so the
                        // form's "must be future" check passes and the
                        // wizard can advance. Real date / time pickers
                        // land with the calendar P18 follow-up.
                        val nextDay = Instant.now().plusSeconds(60L * 60L * 24L)
                        vm.setScheduledStart(nextDay.toString())
                    }
                    .padding(Spacing.s3)
                    .testTag("composeGig_scheduledStart"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Calendar,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text(
                text = formatted,
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            PantopusIconImage(
                icon = PantopusIcon.ChevronRight,
                contentDescription = null,
                size = 18.dp,
                tint = PantopusColors.appTextSecondary,
            )
        }
    }
}

// MARK: - Step 5: Location

@Composable
private fun LocationStep(
    state: GigComposeUiState,
    vm: GigComposeViewModel,
) {
    HeadlineBlock("Where does the task happen?")
    SubcopyBlock("Your exact address is shared only after a helper is selected.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        GigComposeLocationMode.entries.forEach { mode ->
            RadioRow(
                label = mode.label,
                subcopy = mode.subcopy(),
                isSelected = state.form.locationMode == mode,
                testTag = "composeGig_location_${mode.name.lowercase()}",
                onTap = { vm.selectLocationMode(mode) },
            )
        }
    }
    if (state.form.locationMode == GigComposeLocationMode.APlace) {
        FormFieldsBlock {
            PantopusTextField(
                label = "Street",
                value = state.form.placeAddress.line1,
                onValueChange = { vm.updatePlaceAddress(line1 = it) },
                placeholder = "123 Main St",
                fieldTestTag = "composeGig_place_line1",
            )
            PantopusTextField(
                label = "City",
                value = state.form.placeAddress.city,
                onValueChange = { vm.updatePlaceAddress(city = it) },
                fieldTestTag = "composeGig_place_city",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                PantopusTextField(
                    label = "State",
                    value = state.form.placeAddress.state,
                    onValueChange = { vm.updatePlaceAddress(state = it) },
                    modifier = Modifier.weight(1f),
                    fieldTestTag = "composeGig_place_state",
                )
                PantopusTextField(
                    label = "ZIP",
                    value = state.form.placeAddress.zip,
                    onValueChange = { vm.updatePlaceAddress(zip = it) },
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.weight(1f),
                    fieldTestTag = "composeGig_place_zip",
                )
            }
        }
    }
}

// MARK: - Step 6: Review

@Composable
private fun ReviewStep(state: GigComposeUiState) {
    HeadlineBlock("Review and post")
    SubcopyBlock("Check the details. Helpers see what's below as your gig card.")
    val form = state.form
    val condensedDescription = condensedDescription(form.description)
    val photosSummary =
        when (form.photoIds.size) {
            0 -> "None"
            1 -> "1 photo"
            else -> "${form.photoIds.size} photos"
        }
    val budgetSummary =
        when (val type = form.budgetType) {
            null -> "—"
            GigComposeBudgetType.Offers -> "Open to bids"
            else -> {
                val suffix = if (type == GigComposeBudgetType.Hourly) "/hr" else ""
                if (form.budgetMax.isNotEmpty()) {
                    "\$${form.budgetMin}–\$${form.budgetMax}$suffix"
                } else {
                    "\$${form.budgetMin}$suffix"
                }
            }
        }
    val scheduleSummary =
        form.scheduleType?.let { type ->
            if (type == GigComposeScheduleType.OneTime && form.scheduledStartISO != null) {
                runCatching {
                    LocalDateTime
                        .ofInstant(Instant.parse(form.scheduledStartISO), ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT))
                }.getOrNull() ?: type.label
            } else {
                type.label
            }
        } ?: "—"
    val locationSummary =
        when (form.locationMode) {
            null -> "—"
            GigComposeLocationMode.YourAddress -> "Your saved address"
            GigComposeLocationMode.Virtual -> "Virtual"
            GigComposeLocationMode.APlace ->
                if (form.placeAddress.isComplete) {
                    "${form.placeAddress.line1}, ${form.placeAddress.city}, ${form.placeAddress.state} ${form.placeAddress.zip}"
                } else {
                    "A place"
                }
        }

    ReviewSummaryBlock(
        rows =
            listOf(
                ReviewSummaryRow("Category", form.category?.label ?: "—"),
                ReviewSummaryRow("Title", form.title.ifEmpty { "—" }),
                ReviewSummaryRow("Description", condensedDescription),
                ReviewSummaryRow("Photos", photosSummary),
                ReviewSummaryRow("Budget", budgetSummary),
                ReviewSummaryRow("Schedule", scheduleSummary),
                ReviewSummaryRow("Location", locationSummary),
            ),
    )
}

private fun condensedDescription(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return "—"
    return if (trimmed.length > 140) trimmed.take(140) + "…" else trimmed
}

// MARK: - Success

@Composable
private fun SuccessStep() {
    SuccessHeroBlock(
        headline = "Task posted",
        subcopy = "Helpers can now see it on the Gigs feed. We'll notify you when bids come in.",
    )
}

// MARK: - Helpers

@Composable
private fun RadioRow(
    label: String,
    subcopy: String,
    isSelected: Boolean,
    testTag: String,
    onTap: () -> Unit,
) {
    val borderColor = if (isSelected) PantopusColors.primary600 else PantopusColors.appBorder
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(width = if (isSelected) 2.dp else 1.dp, color = borderColor, shape = RoundedCornerShape(Radii.md))
                .clickable(role = Role.RadioButton, onClick = onTap)
                .padding(Spacing.s3)
                .testTag(testTag)
                .semantics { contentDescription = "$label. $subcopy" },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        RadioCircle(isSelected = isSelected)
        Column {
            Text(text = label, style = PantopusTextStyle.body, color = PantopusColors.appText)
            Text(text = subcopy, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
        }
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
                .testTag("composeGigErrorBanner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = PantopusIcon.AlertCircle, contentDescription = null, size = 18.dp, tint = PantopusColors.error)
        Text(text = message, style = PantopusTextStyle.caption, color = PantopusColors.error)
    }
}
