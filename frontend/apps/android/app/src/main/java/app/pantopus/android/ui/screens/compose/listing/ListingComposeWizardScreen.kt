@file:Suppress("PackageNaming", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.compose.listing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.wizard.WizardShell
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

/** Test tag applied to the Snap & Sell wizard container. */
const val LISTING_COMPOSE_SCREEN_TAG = "listingComposeWizard"

/** Test tag applied to the wizard container in edit mode (P3.3). */
const val LISTING_EDIT_SCREEN_TAG = "listingEditWizard"

/**
 * Snap & Sell wizard composable. The view model survives config
 * changes via Hilt's `SavedStateHandle`, so the wizard restores after
 * process death. The same composable backs both the create flow (from
 * the Marketplace FAB) and the edit flow (P3.3) — the VM reads the
 * mode from the nav-arg listing id, and the screen wires
 * `onListingUpdated` so the host can pop back to the listing detail
 * after a save.
 */
@Composable
fun ListingComposeWizardScreen(
    onDismiss: () -> Unit,
    onOpenListingDetail: (String) -> Unit,
    onListingUpdated: ((String) -> Unit)? = null,
    viewModel: ListingComposeWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()
    var photoPendingRemoval by remember { mutableStateOf<ListingComposePhoto?>(null) }

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            ListingComposeOutboundEvent.Dismiss -> {
                viewModel.acknowledgeEvent()
                onDismiss()
            }
            is ListingComposeOutboundEvent.OpenListingDetail -> {
                viewModel.acknowledgeEvent()
                onOpenListingDetail(event.listingId)
            }
            is ListingComposeOutboundEvent.ListingUpdated -> {
                viewModel.acknowledgeEvent()
                onListingUpdated?.invoke(event.listingId) ?: onDismiss()
            }
            null -> Unit
        }
    }

    LaunchedEffect(Unit) {
        // Edit mode: kick the prefill fetch. Idempotent — the VM
        // no-ops in create mode or once the form is non-empty.
        viewModel.loadExistingIfNeeded()
        val current = state.form.currentStep
        current.stepNumber?.let { number ->
            Analytics.track(
                AnalyticsEvent.ScreenListingComposeWizardStepViewed(
                    stepNumber = number,
                    stepName = current.name,
                ),
            )
        }
    }

    val screenTag = if (viewModel.isEditMode) LISTING_EDIT_SCREEN_TAG else LISTING_COMPOSE_SCREEN_TAG
    WizardShell(
        model = viewModel,
        modifier = Modifier.testTag(screenTag),
    ) {
        if (state.isLoadingExisting) {
            EditPrefillLoadingBlock()
        } else {
            when (state.form.currentStep) {
                ListingComposeStep.Photos ->
                    PhotosStep(
                        state = state,
                        onAdd = { viewModel.addPhoto() },
                        onRequestRemove = { photoPendingRemoval = it },
                        onMoveUp = { index ->
                            viewModel.movePhoto(from = index, to = index - 1)
                        },
                        onMoveDown = { index ->
                            viewModel.movePhoto(from = index, to = index + 1)
                        },
                        onMakeHero = viewModel::makeHero,
                    )
                ListingComposeStep.TitleCategory -> TitleCategoryStep(state, viewModel)
                ListingComposeStep.ConditionDescription -> ConditionDescriptionStep(state, viewModel)
                ListingComposeStep.Price -> PriceStep(state, viewModel)
                ListingComposeStep.Location -> LocationStep(state, viewModel)
                ListingComposeStep.Review -> ReviewStep(state)
                ListingComposeStep.Success -> SuccessStep()
            }
            state.errorMessage?.let { ErrorBanner(it) }
        }
    }

    photoPendingRemoval?.let { photo ->
        AlertDialog(
            onDismissRequest = { photoPendingRemoval = null },
            title = { Text("Remove this photo?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removePhoto(photo.id)
                        photoPendingRemoval = null
                    },
                    modifier = Modifier.testTag("listingCompose_removePhotoConfirm"),
                ) {
                    Text("Remove photo")
                }
            },
            dismissButton = {
                TextButton(onClick = { photoPendingRemoval = null }) { Text("Cancel") }
            },
        )
    }
}

// MARK: - Step 1: Photos

@Composable
private fun PhotosStep(
    state: ListingComposeUiState,
    onAdd: () -> Unit,
    onRequestRemove: (ListingComposePhoto) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onMakeHero: (String) -> Unit,
) {
    HeadlineBlock("Add photos")
    SubcopyBlock(
        "Show your item in good light. The first photo becomes the hero — long-press a tile to reorder, tap to remove.",
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
        modifier = Modifier.heightIn(max = 600.dp),
    ) {
        itemsIndexed(state.form.photos, key = { _, p -> p.id }) { index, photo ->
            PhotoTile(
                index = index,
                onTap = { onRequestRemove(photo) },
                onMoveUp = if (index > 0) ({ onMoveUp(index) }) else null,
                onMoveDown = if (index < state.form.photos.lastIndex) ({ onMoveDown(index) }) else null,
                onMakeHero = if (index > 0) ({ onMakeHero(photo.id) }) else null,
            )
        }
        if (state.form.photos.size < ListingComposeFormState.MAX_PHOTOS) {
            item {
                AddPhotoTile(onTap = onAdd)
            }
        }
    }
    Text(
        text = "${state.form.photos.size} of ${ListingComposeFormState.MAX_PHOTOS} photos",
        style = PantopusTextStyle.caption,
        color = PantopusColors.appTextSecondary,
        modifier = Modifier.testTag("listingCompose_photoCount"),
    )
}

@Composable
private fun PhotoTile(
    index: Int,
    onTap: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onMakeHero: (() -> Unit)?,
) {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceMuted)
                .clickable(role = Role.Button, onClick = onTap)
                .testTag("listingCompose_photo_$index")
                .semantics {
                    contentDescription =
                        if (index == 0) {
                            "Photo ${index + 1} of grid. Hero photo. Tap to remove."
                        } else {
                            "Photo ${index + 1} of grid. Tap to remove."
                        }
                },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Image,
            contentDescription = null,
            size = 32.dp,
            tint = PantopusColors.appTextSecondary,
        )
        if (index == 0) {
            HeroChip(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.s2),
            )
        }
        // Hidden reorder controls reachable for accessibility / tests.
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(Spacing.s2),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            onMakeHero?.let {
                ReorderChip(
                    label = "Make hero",
                    testTag = "listingCompose_makeHero_$index",
                    onClick = it,
                )
            }
            onMoveUp?.let {
                ReorderChip(
                    label = "Move up",
                    testTag = "listingCompose_moveUp_$index",
                    onClick = it,
                )
            }
            onMoveDown?.let {
                ReorderChip(
                    label = "Move down",
                    testTag = "listingCompose_moveDown_$index",
                    onClick = it,
                )
            }
        }
    }
}

@Composable
private fun HeroChip(modifier: Modifier = Modifier) {
    Text(
        text = "HERO",
        style = PantopusTextStyle.overline,
        color = PantopusColors.appTextInverse,
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.sm))
                .background(PantopusColors.primary600)
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1),
    )
}

@Composable
private fun ReorderChip(
    label: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = PantopusTextStyle.caption,
        color = PantopusColors.appTextInverse,
        modifier =
            Modifier
                .clip(RoundedCornerShape(Radii.sm))
                .background(PantopusColors.appText)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = Spacing.s2, vertical = Spacing.s1)
                .testTag(testTag),
    )
}

@Composable
private fun AddPhotoTile(onTap: () -> Unit) {
    Box(
        modifier =
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radii.lg))
                .border(width = 1.dp, color = PantopusColors.appBorder, shape = RoundedCornerShape(Radii.lg))
                .clickable(role = Role.Button, onClick = onTap)
                .testTag("listingCompose_addPhoto")
                .semantics { contentDescription = "Add photo" },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Camera,
                contentDescription = null,
                size = 28.dp,
                tint = PantopusColors.appTextSecondary,
            )
            Text(
                text = "Add photo",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
        }
    }
}

// MARK: - Step 2: Title + Category

@Composable
private fun TitleCategoryStep(
    state: ListingComposeUiState,
    vm: ListingComposeWizardViewModel,
) {
    HeadlineBlock("Name it & pick a category")
    SubcopyBlock("Keep the title short and specific — buyers scan in a glance.")
    val titleLength = state.form.title.trim().length
    val titleState =
        when {
            titleLength == 0 -> PantopusFieldState.Default
            titleLength < ListingComposeFormState.TITLE_MIN_LENGTH ->
                PantopusFieldState.Error("Title must be at least ${ListingComposeFormState.TITLE_MIN_LENGTH} characters.")
            titleLength > ListingComposeFormState.TITLE_MAX_LENGTH ->
                PantopusFieldState.Error("Title must be at most ${ListingComposeFormState.TITLE_MAX_LENGTH} characters.")
            else -> PantopusFieldState.Valid
        }
    PantopusTextField(
        label = "Title",
        value = state.form.title,
        onValueChange = vm::setTitle,
        placeholder = "Moving boxes — bundle of 18",
        state = titleState,
        fieldTestTag = "listingCompose_title",
    )
    Text(
        text = "$titleLength/${ListingComposeFormState.TITLE_MAX_LENGTH}",
        style = PantopusTextStyle.caption,
        color = PantopusColors.appTextSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionLabel("Category")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposeCategory.entries.forEach { category ->
            CategoryRow(
                category = category,
                isSelected = state.form.category == category,
                onTap = { vm.setCategory(category) },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    category: ListingComposeCategory,
    isSelected: Boolean,
    onTap: () -> Unit,
) {
    RadioOptionRow(
        title = category.label,
        subtitle = category.subtitle,
        isSelected = isSelected,
        testTag = "listingCompose_category_${category.key}",
        onTap = onTap,
    )
}

// MARK: - Step 3: Condition + Description

@Composable
private fun ConditionDescriptionStep(
    state: ListingComposeUiState,
    vm: ListingComposeWizardViewModel,
) {
    HeadlineBlock("Condition & details")
    SubcopyBlock("Buyers want to know what they're getting before they message you.")
    if (state.form.category?.requiresCondition == true) {
        SectionLabel("Condition")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            ListingComposeCondition.entries.forEach { condition ->
                RadioOptionRow(
                    title = condition.label,
                    subtitle = condition.subtitle,
                    isSelected = state.form.condition == condition,
                    testTag = "listingCompose_condition_${condition.key}",
                    onTap = { vm.setCondition(condition) },
                )
            }
        }
    }
    SectionLabel("Description")
    val descLength = state.form.bodyText.trim().length
    val borderColor =
        when {
            descLength == 0 -> PantopusColors.appBorder
            descLength < ListingComposeFormState.DESCRIPTION_MIN_LENGTH -> PantopusColors.error
            descLength > ListingComposeFormState.DESCRIPTION_MAX_LENGTH -> PantopusColors.error
            else -> PantopusColors.appBorder
        }
    BasicTextField(
        value = state.form.bodyText,
        onValueChange = vm::setBody,
        textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
        cursorBrush = SolidColor(PantopusColors.primary600),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(
                    width = 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(Radii.md),
                )
                .padding(Spacing.s3)
                .testTag("listingCompose_description"),
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        if (descLength in 1 until ListingComposeFormState.DESCRIPTION_MIN_LENGTH) {
            Text(
                text = "At least ${ListingComposeFormState.DESCRIPTION_MIN_LENGTH} characters",
                style = PantopusTextStyle.caption,
                color = PantopusColors.error,
            )
        }
        Box(modifier = Modifier.weight(1f))
        Text(
            text = "$descLength/${ListingComposeFormState.DESCRIPTION_MAX_LENGTH}",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
    }
}

// MARK: - Step 4: Price

@Composable
private fun PriceStep(
    state: ListingComposeUiState,
    vm: ListingComposeWizardViewModel,
) {
    HeadlineBlock("Pricing & fulfillment")
    SubcopyBlock("Choose how to price it and how the buyer will receive it.")
    SectionLabel("Pricing")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposePriceKind.entries.forEach { kind ->
            RadioOptionRow(
                title = kind.label,
                subtitle = kind.subtitle,
                isSelected = state.form.priceKind == kind,
                testTag = "listingCompose_priceKind_${kind.key}",
                onTap = { vm.setPriceKind(kind) },
            )
        }
    }
    if (state.form.priceKind == ListingComposePriceKind.Fixed ||
        state.form.priceKind == ListingComposePriceKind.Negotiable
    ) {
        val amount = state.form.priceAmount
        val priceState =
            when {
                amount.isEmpty() -> PantopusFieldState.Default
                (amount.toDoubleOrNull() ?: 0.0) <= 0.0 ->
                    PantopusFieldState.Error("Enter an amount greater than zero.")
                else -> PantopusFieldState.Valid
            }
        PantopusTextField(
            label = "Amount (USD)",
            value = amount,
            onValueChange = vm::setPriceAmount,
            placeholder = "0.00",
            state = priceState,
            keyboardType = KeyboardType.Decimal,
            fieldTestTag = "listingCompose_priceAmount",
        )
    }
    SectionLabel("Fulfillment")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposeFulfillment.entries.forEach { kind ->
            RadioOptionRow(
                title = kind.label,
                subtitle = kind.subtitle,
                isSelected = state.form.fulfillment == kind,
                testTag = "listingCompose_fulfillment_${kind.key}",
                onTap = { vm.setFulfillment(kind) },
            )
        }
    }
}

// MARK: - Step 5: Location

@Composable
private fun LocationStep(
    state: ListingComposeUiState,
    vm: ListingComposeWizardViewModel,
) {
    HeadlineBlock("Where will the handoff happen?")
    SubcopyBlock("Your exact address is only shared with the buyer after both sides commit.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposeLocationKind.entries.forEach { kind ->
            RadioOptionRow(
                title = kind.label,
                subtitle = kind.subtitle,
                isSelected = state.form.locationKind == kind,
                testTag = "listingCompose_locationKind_${kind.key}",
                onTap = { vm.setLocationKind(kind) },
            )
        }
    }
    if (state.form.locationKind == ListingComposeLocationKind.MeetPoint) {
        PantopusTextField(
            label = "Meet point name",
            value = state.form.locationLabel,
            onValueChange = vm::setLocationLabel,
            placeholder = "Lincoln Park bandshell",
            fieldTestTag = "listingCompose_locationLabel",
        )
    }
}

// MARK: - Step 6: Review

@Composable
private fun ReviewStep(state: ListingComposeUiState) {
    HeadlineBlock("Review & list")
    SubcopyBlock("Take one last look — you can edit after listing.")
    val rows = buildReviewRows(state)
    ReviewSummaryBlock(rows = rows)
}

private fun buildReviewRows(state: ListingComposeUiState): List<ReviewSummaryRow> {
    val rows = mutableListOf<ReviewSummaryRow>()
    rows += ReviewSummaryRow("Photos", photoSummary(state.form))
    rows += ReviewSummaryRow("Title", state.form.title.trim())
    rows += ReviewSummaryRow("Category", state.form.category?.label ?: "—")
    state.form.condition?.let {
        rows += ReviewSummaryRow("Condition", it.label)
    }
    rows += ReviewSummaryRow("Description", state.form.bodyText.trim())
    rows += ReviewSummaryRow("Price", priceSummary(state.form))
    rows += ReviewSummaryRow("Fulfillment", state.form.fulfillment.label)
    rows += ReviewSummaryRow("Location", locationSummary(state.form))
    return rows
}

private fun photoSummary(form: ListingComposeFormState): String {
    val count = form.photos.size
    if (count == 0) return "0 photos"
    return "$count photo${if (count == 1) "" else "s"} (hero first)"
}

private fun priceSummary(form: ListingComposeFormState): String {
    val kind = form.priceKind ?: return "—"
    return when (kind) {
        ListingComposePriceKind.Free -> "Free"
        ListingComposePriceKind.Fixed ->
            if (form.priceAmount.isEmpty()) "—" else "\$${form.priceAmount}"
        ListingComposePriceKind.Negotiable ->
            if (form.priceAmount.isEmpty()) {
                "Open to offers"
            } else {
                "\$${form.priceAmount} · open to offers"
            }
    }
}

private fun locationSummary(form: ListingComposeFormState): String {
    val kind = form.locationKind ?: return "—"
    return when (kind) {
        ListingComposeLocationKind.SavedAddress -> kind.label
        ListingComposeLocationKind.MeetPoint ->
            if (form.locationLabel.isEmpty()) kind.label else "${kind.label} · ${form.locationLabel}"
    }
}

// MARK: - Success

@Composable
private fun SuccessStep() {
    SuccessHeroBlock(
        headline = "Your listing is live",
        subcopy = "Neighbors can find it in Marketplace now. We'll notify you when an offer comes in.",
    )
}

// MARK: - Shared

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = PantopusTextStyle.caption,
        color = PantopusColors.appTextSecondary,
        modifier = Modifier.fillMaxWidth().semantics { heading() },
    )
}

@Composable
private fun RadioOptionRow(
    title: String,
    subtitle: String,
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
                .border(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = RoundedCornerShape(Radii.md),
                )
                .clickable(role = Role.RadioButton, onClick = onTap)
                .padding(Spacing.s3)
                .testTag(testTag)
                .semantics { contentDescription = title },
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        RadioCircle(isSelected = isSelected)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Text(
                text = title,
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
            )
            Text(
                text = subtitle,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
            )
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

/**
 * Internal preview body that renders a single wizard step inline (no
 * `WizardShell`, no view model). Used by Paparazzi snapshot tests to
 * lock the visual contract for each step without standing up Hilt.
 */
@Composable
internal fun ListingComposeStepPreview(
    state: ListingComposeUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(PantopusColors.appBg)
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        when (state.form.currentStep) {
            ListingComposeStep.Photos ->
                PhotosStep(
                    state = state,
                    onAdd = {},
                    onRequestRemove = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onMakeHero = {},
                )
            ListingComposeStep.TitleCategory -> TitleCategoryStepPreview(state)
            ListingComposeStep.ConditionDescription -> ConditionDescriptionStepPreview(state)
            ListingComposeStep.Price -> PriceStepPreview(state)
            ListingComposeStep.Location -> LocationStepPreview(state)
            ListingComposeStep.Review -> ReviewStep(state)
            ListingComposeStep.Success -> SuccessStep()
        }
        state.errorMessage?.let { ErrorBanner(it) }
    }
}

@Composable
private fun TitleCategoryStepPreview(state: ListingComposeUiState) {
    HeadlineBlock("Name it & pick a category")
    SubcopyBlock("Keep the title short and specific — buyers scan in a glance.")
    val titleLength = state.form.title.trim().length
    PantopusTextField(
        label = "Title",
        value = state.form.title,
        onValueChange = {},
        placeholder = "Moving boxes — bundle of 18",
        fieldTestTag = "listingCompose_title",
    )
    Text(
        text = "$titleLength/${ListingComposeFormState.TITLE_MAX_LENGTH}",
        style = PantopusTextStyle.caption,
        color = PantopusColors.appTextSecondary,
        modifier = Modifier.fillMaxWidth(),
    )
    SectionLabel("Category")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposeCategory.entries.forEach { category ->
            RadioOptionRow(
                title = category.label,
                subtitle = category.subtitle,
                isSelected = state.form.category == category,
                testTag = "listingCompose_category_${category.key}",
                onTap = {},
            )
        }
    }
}

@Composable
private fun ConditionDescriptionStepPreview(state: ListingComposeUiState) {
    HeadlineBlock("Condition & details")
    SubcopyBlock("Buyers want to know what they're getting before they message you.")
    if (state.form.category?.requiresCondition == true) {
        SectionLabel("Condition")
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            ListingComposeCondition.entries.forEach { condition ->
                RadioOptionRow(
                    title = condition.label,
                    subtitle = condition.subtitle,
                    isSelected = state.form.condition == condition,
                    testTag = "listingCompose_condition_${condition.key}",
                    onTap = {},
                )
            }
        }
    }
    SectionLabel("Description")
    BasicTextField(
        value = state.form.bodyText,
        onValueChange = {},
        textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
        cursorBrush = SolidColor(PantopusColors.primary600),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 128.dp)
                .clip(RoundedCornerShape(Radii.md))
                .background(PantopusColors.appSurface)
                .border(
                    width = 1.dp,
                    color = PantopusColors.appBorder,
                    shape = RoundedCornerShape(Radii.md),
                )
                .padding(Spacing.s3)
                .testTag("listingCompose_description"),
    )
}

@Composable
private fun PriceStepPreview(state: ListingComposeUiState) {
    HeadlineBlock("Pricing & fulfillment")
    SubcopyBlock("Choose how to price it and how the buyer will receive it.")
    SectionLabel("Pricing")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposePriceKind.entries.forEach { kind ->
            RadioOptionRow(
                title = kind.label,
                subtitle = kind.subtitle,
                isSelected = state.form.priceKind == kind,
                testTag = "listingCompose_priceKind_${kind.key}",
                onTap = {},
            )
        }
    }
    if (state.form.priceKind == ListingComposePriceKind.Fixed ||
        state.form.priceKind == ListingComposePriceKind.Negotiable
    ) {
        PantopusTextField(
            label = "Amount (USD)",
            value = state.form.priceAmount,
            onValueChange = {},
            placeholder = "0.00",
            keyboardType = KeyboardType.Decimal,
            fieldTestTag = "listingCompose_priceAmount",
        )
    }
    SectionLabel("Fulfillment")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposeFulfillment.entries.forEach { kind ->
            RadioOptionRow(
                title = kind.label,
                subtitle = kind.subtitle,
                isSelected = state.form.fulfillment == kind,
                testTag = "listingCompose_fulfillment_${kind.key}",
                onTap = {},
            )
        }
    }
}

@Composable
private fun LocationStepPreview(state: ListingComposeUiState) {
    HeadlineBlock("Where will the handoff happen?")
    SubcopyBlock("Your exact address is only shared with the buyer after both sides commit.")
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        ListingComposeLocationKind.entries.forEach { kind ->
            RadioOptionRow(
                title = kind.label,
                subtitle = kind.subtitle,
                isSelected = state.form.locationKind == kind,
                testTag = "listingCompose_locationKind_${kind.key}",
                onTap = {},
            )
        }
    }
    if (state.form.locationKind == ListingComposeLocationKind.MeetPoint) {
        PantopusTextField(
            label = "Meet point name",
            value = state.form.locationLabel,
            onValueChange = {},
            placeholder = "Lincoln Park bandshell",
            fieldTestTag = "listingCompose_locationLabel",
        )
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
                .testTag("listingComposeErrorBanner"),
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

/**
 * P3.3 — Shimmer placeholder shown while the edit-mode prefill fetch
 * is in flight. Mirrors the rough geometry of the loaded review step
 * (title block + a few rows + a body block) so the layout doesn't jump
 * when the real fields land.
 */
@Composable
private fun EditPrefillLoadingBlock() {
    Column(
        modifier = Modifier.fillMaxWidth().testTag("listingComposeEditLoading"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Shimmer(width = 180.dp, height = 24.dp)
        Shimmer(width = 320.dp, height = 16.dp, modifier = Modifier.fillMaxWidth())
        Shimmer(width = 240.dp, height = 16.dp)
        Shimmer(width = 320.dp, height = 128.dp, modifier = Modifier.fillMaxWidth())
    }
}
