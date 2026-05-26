@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions", "PackageNaming")

package app.pantopus.android.ui.screens.gigs.quickpost

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.screens.gigs.GigsCategory
import app.pantopus.android.ui.screens.shared.form.FormFieldGroup
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.screens.shared.form.FormShellLeading
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PostGigV1Screen(
    onDismiss: () -> Unit,
    onPosted: (String) -> Unit,
    preselectedCategoryKey: String? = null,
    viewModel: PostGigV1ViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pendingEvent by viewModel.pendingEvent.collectAsStateWithLifecycle()
    val content = state as? PostGigV1UiState.Content

    LaunchedEffect(preselectedCategoryKey) {
        viewModel.preselectCategoryIfNeeded(GigsCategory.fromBackendKey(preselectedCategoryKey))
    }

    LaunchedEffect(pendingEvent) {
        when (val event = pendingEvent) {
            is PostGigV1Event.Posted -> {
                viewModel.acknowledgeEvent()
                onPosted(event.gigId)
            }
            null -> Unit
        }
    }

    FormShell(
        title = "Post gig",
        leading = FormShellLeading.Back,
        rightActionLabel = "Post",
        isValid = content?.canAttemptSubmit == true,
        isDirty = content?.isPostEnabled == true,
        isSaving = content?.isSubmitting == true,
        onClose = onDismiss,
        onCommit = { viewModel.submit() },
    ) {
        when (val s = state) {
            PostGigV1UiState.Loading -> PostGigV1Loading()
            PostGigV1UiState.Empty -> PostGigV1Empty(onStart = viewModel::startFromEmpty)
            is PostGigV1UiState.FatalError -> PostGigV1FatalError(message = s.message, onRetry = viewModel::retry)
            is PostGigV1UiState.Content ->
                PostGigV1Content(
                    state = s,
                    actions =
                        PostGigV1Actions(
                            onCategory = viewModel::updateCategory,
                            onTitle = viewModel::updateTitle,
                            onDescription = viewModel::updateDescription,
                            onPrice = viewModel::updatePrice,
                            onPriceType = viewModel::updatePriceType,
                            onPickDate = viewModel::pickNextSaturday,
                            onLocation = viewModel::updateLocation,
                            onAddPhoto = viewModel::addPlaceholderPhoto,
                            onRemovePhoto = viewModel::removePhoto,
                        ),
                )
        }
    }
}

data class PostGigV1Actions(
    val onCategory: (GigsCategory) -> Unit = {},
    val onTitle: (String) -> Unit = {},
    val onDescription: (String) -> Unit = {},
    val onPrice: (String) -> Unit = {},
    val onPriceType: (PostGigV1PriceType) -> Unit = {},
    val onPickDate: () -> Unit = {},
    val onLocation: (String) -> Unit = {},
    val onAddPhoto: () -> Unit = {},
    val onRemovePhoto: (String) -> Unit = {},
)

@Composable
fun PostGigV1Content(
    state: PostGigV1UiState.Content,
    actions: PostGigV1Actions,
) {
    val errors = state.validationErrors
    val form = state.form

    if (errors.isNotEmpty()) {
        PostGigV1ErrorBanner(
            errors = errors,
            modifier = Modifier.padding(horizontal = Spacing.s4),
        )
    }

    FormFieldGroup(title = "Category") {
        CategoryField(
            selected = form.category,
            error = errors.messageFor(PostGigV1Field.Category),
            onSelect = actions.onCategory,
        )
    }

    FormFieldGroup(title = "Details") {
        PantopusTextField(
            label = "Title",
            value = form.title,
            onValueChange = actions.onTitle,
            placeholder = "Help moving a sofa up 3 flights",
            state = errors.fieldState(PostGigV1Field.Title),
            isRequired = true,
            fieldTestTag = "postGigV1_title",
        )
        DescriptionField(
            value = form.description,
            error = errors.messageFor(PostGigV1Field.Description),
            onValueChange = actions.onDescription,
        )
    }

    FormFieldGroup(title = "Pay") {
        PriceField(
            value = form.price,
            unit = form.priceType.unitLabel,
            error = errors.messageFor(PostGigV1Field.Price),
            onValueChange = actions.onPrice,
        )
        PriceTypeRow(selected = form.priceType, onSelect = actions.onPriceType)
    }

    FormFieldGroup(title = "When") {
        DateField(
            scheduledAt = form.scheduledAt,
            error = errors.messageFor(PostGigV1Field.DateTime),
            onClick = actions.onPickDate,
        )
    }

    FormFieldGroup(title = "Photos") {
        PhotosGrid(
            photos = form.photos,
            canAdd = form.photos.size < PostGigV1SampleData.MAX_PHOTOS,
            onAdd = actions.onAddPhoto,
            onRemove = actions.onRemovePhoto,
        )
    }

    FormFieldGroup(title = "Location") {
        PantopusTextField(
            label = "Location",
            value = form.location,
            onValueChange = actions.onLocation,
            placeholder = "Pearl District · NW 11th & Johnson",
            state = errors.fieldState(PostGigV1Field.Location),
            isRequired = true,
            fieldTestTag = "postGigV1_location",
        )
    }

    LegacyStamp()
}

@Composable
private fun CategoryField(
    selected: GigsCategory,
    error: String?,
    onSelect: (GigsCategory) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val border = if (error == null) PantopusColors.appBorder else PantopusColors.error

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        FieldLabel("Category", required = true)
        Box {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Radii.md))
                        .background(PantopusColors.appSurface)
                        .border(
                            width = if (error == null) 1.dp else 1.5.dp,
                            color = border,
                            shape = RoundedCornerShape(Radii.md),
                        ).clickable { expanded = true }
                        .padding(horizontal = Spacing.s3)
                        .testTag("postGigV1_category")
                        .semantics { contentDescription = "Category" },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text(
                    text = if (selected == GigsCategory.All) "Choose a category" else selected.v1Label(),
                    style = PantopusTextStyle.small,
                    fontWeight = if (selected == GigsCategory.All) FontWeight.Normal else FontWeight.Medium,
                    color = if (selected == GigsCategory.All) PantopusColors.appTextMuted else PantopusColors.appText,
                    modifier = Modifier.weight(1f),
                )
                PantopusIconImage(
                    icon = PantopusIcon.ChevronDown,
                    contentDescription = null,
                    size = Radii.xl,
                    tint = PantopusColors.appTextSecondary,
                )
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                GigsCategory.entries.filter { it != GigsCategory.All }.forEach { category ->
                    DropdownMenuItem(
                        text = { Text(category.v1Label()) },
                        onClick = {
                            expanded = false
                            onSelect(category)
                        },
                    )
                }
            }
        }
        if (error != null) InlineError(error)
    }
}

@Composable
private fun DescriptionField(
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    val border = if (error == null) PantopusColors.appBorder else PantopusColors.error
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        FieldLabel("Description", required = true)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(
                        width = if (error == null) 1.dp else 1.5.dp,
                        color = border,
                        shape = RoundedCornerShape(Radii.md),
                    ).padding(Spacing.s3),
        ) {
            BasicTextField(
                value = value,
                onValueChange = {
                    onValueChange(it.take(PostGigV1SampleData.DESCRIPTION_MAX_LENGTH))
                },
                textStyle = PantopusTextStyle.small.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(PantopusColors.primary600),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .testTag("postGigV1_description")
                        .semantics {
                            contentDescription =
                                if (error == null) "Description" else "Description, error: $error"
                        },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(
                            text = "Tell neighbors what to carry, where to meet, and any stairs or timing constraints.",
                            style = PantopusTextStyle.small,
                            color = PantopusColors.appTextMuted,
                        )
                    }
                    inner()
                },
            )
        }
        Row(verticalAlignment = Alignment.Top) {
            if (error != null) InlineError(error)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${value.length} / ${PostGigV1SampleData.DESCRIPTION_MAX_LENGTH}",
                style = PantopusTextStyle.caption.copy(fontFamily = FontFamily.Monospace),
                color = PantopusColors.appTextMuted,
                modifier = Modifier.testTag("postGigV1_descriptionCount"),
            )
        }
    }
}

@Composable
private fun PriceField(
    value: String,
    unit: String?,
    error: String?,
    onValueChange: (String) -> Unit,
) {
    val border = if (error == null) PantopusColors.appBorder else PantopusColors.error
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        FieldLabel("Price", required = true)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(
                        width = if (error == null) 1.dp else 1.5.dp,
                        color = border,
                        shape = RoundedCornerShape(Radii.md),
                    ).padding(horizontal = Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            Text(
                text = "$",
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.SemiBold,
                color = if (value.isEmpty()) PantopusColors.appTextMuted else PantopusColors.appTextStrong,
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(PantopusColors.primary600),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier =
                    Modifier
                        .weight(1f)
                        .testTag("postGigV1_price")
                        .semantics { contentDescription = if (error == null) "Price" else "Price, error: $error" },
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text("0", style = PantopusTextStyle.body, color = PantopusColors.appTextMuted)
                    }
                    inner()
                },
            )
            if (unit != null) {
                Box(
                    modifier =
                        Modifier
                            .height(20.dp)
                            .width(1.dp)
                            .background(PantopusColors.appBorderSubtle),
                )
                Text(text = unit, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
        }
        if (error != null) InlineError(error)
    }
}

@Composable
private fun PriceTypeRow(
    selected: PostGigV1PriceType,
    onSelect: (PostGigV1PriceType) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        FieldLabel("Price type", required = false)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s5), verticalAlignment = Alignment.CenterVertically) {
            PostGigV1PriceType.entries.forEach { type ->
                Row(
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .clickable { onSelect(type) }
                            .padding(end = Spacing.s1)
                            .testTag("postGigV1_priceType_${type.name.lowercase()}")
                            .semantics { contentDescription = "${type.label} price" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(18.dp)
                                .border(
                                    width = if (type == selected) 5.dp else 1.5.dp,
                                    color = if (type == selected) PantopusColors.primary600 else PantopusColors.appBorderStrong,
                                    shape = CircleShape,
                                ),
                    )
                    Text(
                        text = type.label,
                        style = PantopusTextStyle.caption,
                        fontWeight = if (type == selected) FontWeight.SemiBold else FontWeight.Medium,
                        color = PantopusColors.appText,
                    )
                }
            }
        }
    }
}

@Composable
private fun DateField(
    scheduledAt: LocalDateTime,
    error: String?,
    onClick: () -> Unit,
) {
    val border = if (error == null) PantopusColors.appBorder else PantopusColors.error
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        FieldLabel("Date & time", required = true)
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(
                        width = if (error == null) 1.dp else 1.5.dp,
                        color = border,
                        shape = RoundedCornerShape(Radii.md),
                    ).clickable(onClick = onClick)
                    .padding(horizontal = Spacing.s3)
                    .testTag("postGigV1_dateTime")
                    .semantics { contentDescription = if (error == null) "Date and time" else "Date and time, error: $error" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Calendar,
                contentDescription = null,
                size = Radii.xl,
                tint = PantopusColors.appTextSecondary,
            )
            Text(
                text = scheduledAt.format(PostGigV1DateFormatter),
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.Medium,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            PantopusIconImage(
                icon = PantopusIcon.ChevronDown,
                contentDescription = null,
                size = 14.dp,
                tint = PantopusColors.appTextMuted,
            )
        }
        if (error != null) InlineError(error)
    }
}

@Composable
private fun PhotosGrid(
    photos: List<PostGigV1Photo>,
    canAdd: Boolean,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
            FieldLabel("Photos", required = false)
            Text(
                text = "(up to ${PostGigV1SampleData.MAX_PHOTOS})",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextMuted,
            )
        }

        val tiles: List<PhotoGridItem> =
            photos.map { PhotoGridItem.Photo(it) } +
                if (canAdd) listOf(PhotoGridItem.Add) else emptyList()
        tiles.chunked(4).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2), modifier = Modifier.fillMaxWidth()) {
                row.forEachIndexed { columnIndex, item ->
                    val index = rowIndex * 4 + columnIndex
                    when (item) {
                        PhotoGridItem.Add ->
                            AddPhotoTile(
                                onClick = onAdd,
                                modifier = Modifier.weight(1f),
                            )
                        is PhotoGridItem.Photo ->
                            PhotoTile(
                                photo = item.photo,
                                isCover = index == 0,
                                onRemove = onRemove,
                                modifier = Modifier.weight(1f),
                            )
                    }
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
        Text(
            text =
                if (photos.isEmpty()) {
                    "Photos help your gig get picked up faster."
                } else {
                    "First photo is the cover. Tap remove to delete."
                },
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.testTag("postGigV1_photoHint"),
        )
    }
}

private sealed interface PhotoGridItem {
    data class Photo(
        val photo: PostGigV1Photo,
    ) : PhotoGridItem

    data object Add : PhotoGridItem
}

@Composable
private fun AddPhotoTile(
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceMuted)
                .border(
                    width = 1.5.dp,
                    color = PantopusColors.appBorderStrong,
                    shape = RoundedCornerShape(Radii.lg),
                ).clickable(onClick = onClick)
                .testTag("postGigV1_addPhoto")
                .semantics { contentDescription = "Add photo" },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Plus,
            contentDescription = null,
            size = 18.dp,
            tint = PantopusColors.appTextSecondary,
        )
        Text(
            text = "Add",
            style = PantopusTextStyle.caption,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextSecondary,
        )
    }
}

@Composable
private fun PhotoTile(
    photo: PostGigV1Photo,
    isCover: Boolean,
    onRemove: (String) -> Unit,
    modifier: Modifier,
) {
    val fill =
        when (photo.tone) {
            PostGigV1PhotoTone.Sofa -> PantopusColors.primary100
            PostGigV1PhotoTone.Stairs -> PantopusColors.homeBg
            PostGigV1PhotoTone.Street -> PantopusColors.businessBg
            PostGigV1PhotoTone.Neutral -> PantopusColors.appSurfaceSunken
        }
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(Radii.lg))
                .background(fill)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg)),
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Image,
            contentDescription = null,
            size = Radii.xl2,
            tint = PantopusColors.appTextSecondary,
            modifier = Modifier.align(Alignment.Center),
        )
        if (isCover) {
            Text(
                text = "Cover",
                style = PantopusTextStyle.overline,
                color = PantopusColors.appTextInverse,
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(Spacing.s1)
                        .clip(RoundedCornerShape(Radii.xs))
                        .background(PantopusColors.appText.copy(alpha = 0.78f))
                        .padding(horizontal = Spacing.s1),
            )
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.appText.copy(alpha = 0.72f))
                    .clickable { onRemove(photo.id) }
                    .testTag("postGigV1_removePhoto_${photo.id}")
                    .semantics { contentDescription = if (isCover) "Remove cover photo" else "Remove photo" },
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.X,
                contentDescription = null,
                size = Radii.lg,
                tint = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun PostGigV1ErrorBanner(
    errors: List<PostGigV1ValidationError>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.errorBg)
                .border(1.dp, PantopusColors.errorLight, RoundedCornerShape(Radii.lg))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                .testTag("postGigV1_errorBanner")
                .semantics {
                    contentDescription = "${errors.size} problems. ${errors.joinToString(" ") { it.message }}"
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier.size(24.dp).clip(CircleShape).background(PantopusColors.error),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.AlertTriangle,
                contentDescription = null,
                size = 14.dp,
                tint = PantopusColors.appTextInverse,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1), modifier = Modifier.weight(1f)) {
            Text(
                text = "${errors.size} problems - please fix",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.error,
            )
            errors.forEach { error ->
                Text(
                    text = "• ${error.message}",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.error,
                )
            }
        }
    }
}

@Composable
private fun InlineError(message: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s1), verticalAlignment = Alignment.CenterVertically) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 11.dp,
            tint = PantopusColors.error,
        )
        Text(text = message, style = PantopusTextStyle.caption, color = PantopusColors.error)
    }
}

@Composable
private fun FieldLabel(
    title: String,
    required: Boolean,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = PantopusTextStyle.caption,
            fontWeight = FontWeight.SemiBold,
            color = PantopusColors.appTextStrong,
        )
        if (required) {
            Text(text = "*", style = PantopusTextStyle.caption, color = PantopusColors.error)
        }
    }
}

@Composable
private fun LegacyStamp() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.s2).testTag("postGigV1_legacyStamp"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Info,
            contentDescription = null,
            size = 11.dp,
            tint = PantopusColors.appTextMuted,
        )
        Text(
            text = "gig composer · v1.4.2",
            style = PantopusTextStyle.caption.copy(fontFamily = FontFamily.Monospace),
            color = PantopusColors.appTextMuted,
            modifier = Modifier.padding(start = Spacing.s1),
        )
    }
}

@Composable
private fun PostGigV1Loading() {
    listOf("Category", "Details", "Pay", "When", "Photos").forEach { title ->
        FormFieldGroup(title = title) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(Radii.md))
                            .background(PantopusColors.appSurfaceSunken),
                )
                if (title == "Details") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .clip(RoundedCornerShape(Radii.md))
                                .background(PantopusColors.appSurfaceSunken),
                    )
                }
            }
        }
    }
}

@Composable
private fun PostGigV1Empty(onStart: () -> Unit) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s6)
                .testTag("postGigV1_empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(72.dp).clip(CircleShape).background(PantopusColors.primary50),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.Briefcase,
                contentDescription = null,
                size = 30.dp,
                tint = PantopusColors.primary600,
            )
        }
        Text(
            text = "No quick-post draft",
            style = PantopusTextStyle.h3,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "Start with the V1 form when you already know the title, price, and time.",
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
        )
        Box(
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.primary600)
                    .clickable(onClick = onStart)
                    .padding(horizontal = Spacing.s5)
                    .testTag("postGigV1_emptyStart")
                    .semantics { contentDescription = "Start quick post" },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Start quick post",
                style = PantopusTextStyle.small,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
    }
}

@Composable
private fun PostGigV1FatalError(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(Spacing.s6)
                .testTag("postGigV1_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier = Modifier.size(64.dp).clip(CircleShape).background(PantopusColors.errorBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = PantopusIcon.AlertTriangle,
                contentDescription = null,
                size = 28.dp,
                tint = PantopusColors.error,
            )
        }
        Text(
            text = "Quick post unavailable",
            style = PantopusTextStyle.h3,
            color = PantopusColors.appText,
            modifier = Modifier.semantics { heading() },
        )
        Text(text = message, style = PantopusTextStyle.small, color = PantopusColors.appTextSecondary)
        Text(
            text = "Retry",
            style = PantopusTextStyle.body,
            color = PantopusColors.primary600,
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = Spacing.s4)
                    .testTag("postGigV1_retry"),
        )
    }
}

private fun List<PostGigV1ValidationError>.messageFor(field: PostGigV1Field): String? = firstOrNull { it.field == field }?.message

private fun List<PostGigV1ValidationError>.fieldState(field: PostGigV1Field): PantopusFieldState =
    messageFor(field)?.let { PantopusFieldState.Error(it) } ?: PantopusFieldState.Default

private fun GigsCategory.v1Label(): String = if (this == GigsCategory.Moving) "Moving & hauling" else label

private val PostGigV1DateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE, MMM d · h:mm a", Locale.US)
