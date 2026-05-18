@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pantopus.android.data.analytics.Analytics
import app.pantopus.android.data.analytics.AnalyticsEvent
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.components.PantopusFieldState
import app.pantopus.android.ui.components.PantopusTextField
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.screens.shared.form.FormFieldGroup
import app.pantopus.android.ui.screens.shared.form.FormFieldState
import app.pantopus.android.ui.screens.shared.form.FormShell
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * P1.4 — Settings → Edit profile. Mirrors the iOS `EditProfileView`
 * 1:1: same sections (About / Contact / Address / Social / Visibility),
 * same field set, same validators, same dirty-tracked PATCH body.
 *
 * Renders four states out of [EditProfileUiState]: shimmer skeleton,
 * loaded form, error empty-state (with retry), and an inline toast for
 * save success / save failure.
 */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val shouldDismiss by viewModel.shouldDismiss.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val emailVerified by viewModel.emailVerified.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        Analytics.track(AnalyticsEvent.ScreenEditProfileViewed)
        viewModel.load()
    }

    LaunchedEffect(toast) {
        if (toast != null) {
            delay(2_000)
            viewModel.dismissToast()
        }
    }

    LaunchedEffect(shouldDismiss) {
        if (shouldDismiss) {
            viewModel.acknowledgeDismiss()
            // Hold the success toast on screen briefly before popping
            // so the user actually sees the confirmation — mirrors iOS.
            delay(700)
            onBack()
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("editProfileShell"),
    ) {
        when (val current = state) {
            EditProfileUiState.Loading ->
                EditProfileSkeleton()
            EditProfileUiState.Loaded ->
                EditProfileLoaded(
                    fields = fields,
                    email = email,
                    emailVerified = emailVerified,
                    isValid = viewModel.isValid,
                    isDirty = viewModel.isDirty,
                    isSaving = isSaving,
                    onClose = onBack,
                    onCommit = viewModel::save,
                    onUpdate = viewModel::update,
                )
            is EditProfileUiState.Error ->
                EmptyState(
                    icon = PantopusIcon.AlertCircle,
                    headline = "Couldn't load profile",
                    subcopy = current.message,
                    ctaTitle = "Try again",
                    onCta = viewModel::refresh,
                )
        }

        toast?.let { payload ->
            EditProfileToastView(
                payload = payload,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = Spacing.s10),
            )
        }
    }
}

@Composable
internal fun EditProfileToastView(
    payload: EditProfileToast,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(
                    if (payload.isError) PantopusColors.error else PantopusColors.success,
                ).padding(horizontal = Spacing.s4, vertical = Spacing.s2)
                .testTag("editProfileToast"),
    ) {
        Text(
            text = payload.text,
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextInverse,
        )
    }
}

@Composable
internal fun EditProfileLoaded(
    fields: Map<EditProfileField, FormFieldState>,
    email: String,
    emailVerified: Boolean,
    isValid: Boolean,
    isDirty: Boolean,
    isSaving: Boolean,
    onClose: () -> Unit,
    onCommit: () -> Unit,
    onUpdate: (EditProfileField, String) -> Unit,
) {
    FormShell(
        title = "Edit profile",
        rightActionLabel = "Save",
        isValid = isValid,
        isDirty = isDirty,
        isSaving = isSaving,
        onClose = onClose,
        onCommit = onCommit,
    ) {
        FormFieldGroup("About") {
            // Note: the design also calls for an avatar upload (tap to
            // replace). `updateProfileSchema` exposes no avatar field,
            // so the affordance is intentionally omitted until the
            // backend accepts an avatar key on PATCH /api/users/profile.
            TextRow(
                field = EditProfileField.FirstName,
                label = "First name",
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.MiddleName,
                label = "Middle name (optional)",
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.LastName,
                label = "Last name",
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.Tagline,
                label = "Tagline (optional)",
                placeholder = "A short headline",
                fields = fields,
                onUpdate = onUpdate,
            )
            BioField(fields = fields, onUpdate = onUpdate)
        }
        FormFieldGroup("Contact") {
            // Note: the design allows editing email when `verified ==
            // false`. `updateProfileSchema` exposes no `email` key, so
            // the field is read-only until the backend adds it.
            ReadOnlyEmailRow(email = email, verified = emailVerified)
            TextRow(
                field = EditProfileField.PhoneNumber,
                label = "Phone (optional)",
                placeholder = "+15555550123",
                keyboardType = KeyboardType.Phone,
                fields = fields,
                onUpdate = onUpdate,
            )
            DateOfBirthField(fields = fields, onUpdate = onUpdate)
        }
        FormFieldGroup("Address") {
            TextRow(
                field = EditProfileField.Address,
                label = "Street",
                placeholder = "123 Main St",
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.City,
                label = "City",
                fields = fields,
                onUpdate = onUpdate,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    TextRow(
                        field = EditProfileField.State,
                        label = "State",
                        fields = fields,
                        onUpdate = onUpdate,
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    TextRow(
                        field = EditProfileField.Zipcode,
                        label = "Zip",
                        fields = fields,
                        onUpdate = onUpdate,
                    )
                }
            }
        }
        FormFieldGroup("Social") {
            TextRow(
                field = EditProfileField.Website,
                label = "Website",
                placeholder = "https://example.com",
                keyboardType = KeyboardType.Uri,
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.Linkedin,
                label = "LinkedIn",
                placeholder = "https://linkedin.com/in/…",
                keyboardType = KeyboardType.Uri,
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.Twitter,
                label = "Twitter / X",
                placeholder = "https://x.com/…",
                keyboardType = KeyboardType.Uri,
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.Instagram,
                label = "Instagram",
                placeholder = "https://instagram.com/…",
                keyboardType = KeyboardType.Uri,
                fields = fields,
                onUpdate = onUpdate,
            )
            TextRow(
                field = EditProfileField.Facebook,
                label = "Facebook",
                placeholder = "https://facebook.com/…",
                keyboardType = KeyboardType.Uri,
                fields = fields,
                onUpdate = onUpdate,
            )
        }
        FormFieldGroup("Visibility") {
            // Note: the design splits visibility into a
            // `profile_visibility_public` boolean and a
            // `show_in_neighbor_discovery` toggle. The schema only
            // exposes the 3-way `profileVisibility` enum today, so we
            // render the segmented picker and omit the toggle until
            // the backend adds it.
            VisibilityPicker(fields = fields, onUpdate = onUpdate)
        }
    }
}

@Composable
private fun TextRow(
    field: EditProfileField,
    label: String,
    fields: Map<EditProfileField, FormFieldState>,
    onUpdate: (EditProfileField, String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val snapshot = fields[field]
    PantopusTextField(
        label = label,
        value = snapshot?.value.orEmpty(),
        onValueChange = { onUpdate(field, it) },
        placeholder = placeholder,
        state = fieldStateFor(snapshot),
        keyboardType = keyboardType,
        fieldTestTag = "field_${field.key}",
    )
}

@Composable
private fun BioField(
    fields: Map<EditProfileField, FormFieldState>,
    onUpdate: (EditProfileField, String) -> Unit,
) {
    val snapshot = fields[EditProfileField.Bio]
    val error = snapshot?.error
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = "Bio",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        BasicTextField(
            value = snapshot?.value.orEmpty(),
            onValueChange = { onUpdate(EditProfileField.Bio, it) },
            textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
            cursorBrush = SolidColor(PantopusColors.primary600),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(
                        width = 1.dp,
                        color = if (error != null) PantopusColors.error else PantopusColors.appBorder,
                        shape = RoundedCornerShape(Radii.md),
                    ).padding(Spacing.s2)
                    .testTag("field_${EditProfileField.Bio.key}"),
        )
        if (error != null) {
            Text(
                text = error,
                style = PantopusTextStyle.caption,
                color = PantopusColors.error,
            )
        }
    }
}

@Composable
private fun DateOfBirthField(
    fields: Map<EditProfileField, FormFieldState>,
    onUpdate: (EditProfileField, String) -> Unit,
) {
    val snapshot = fields[EditProfileField.DateOfBirth]
    val value = snapshot?.value.orEmpty()
    val error = snapshot?.error
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Date of birth (optional)",
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (value.isNotEmpty()) {
                Text(
                    text = "Clear",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.primary600,
                    modifier =
                        Modifier
                            .clickable { onUpdate(EditProfileField.DateOfBirth, "") }
                            .testTag("field_${EditProfileField.DateOfBirth.key}_clear")
                            .semantics { contentDescription = "Clear date of birth" },
                )
            }
        }
        // Compose's `DatePicker` is `Modifier`-incompatible with our
        // 44dp inline field and unsupported by Paparazzi; surface a
        // text field accepting `yyyy-MM-dd` and validate via the
        // shared `isoDateOrEmpty()` rule. The system date picker can
        // be wired by the host activity (`UseDatePickerHost`) in a
        // follow-up once we agree on the picker pattern.
        PantopusTextField(
            label = "",
            value = value,
            onValueChange = { onUpdate(EditProfileField.DateOfBirth, it) },
            placeholder = "YYYY-MM-DD",
            state = fieldStateFor(snapshot),
            keyboardType = KeyboardType.Number,
            fieldTestTag = "field_${EditProfileField.DateOfBirth.key}",
        )
        if (error != null) {
            Text(
                text = error,
                style = PantopusTextStyle.caption,
                color = PantopusColors.error,
            )
        }
    }
}

@Composable
private fun ReadOnlyEmailRow(
    email: String,
    verified: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = "Email",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2)
                    .testTag("field_email")
                    .semantics { contentDescription = "Email $email, read only" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = email,
                style = PantopusTextStyle.body,
                color = PantopusColors.appTextSecondary,
                modifier = Modifier.weight(1f),
            )
            if (verified) {
                PantopusIconImage(
                    icon = PantopusIcon.Check,
                    contentDescription = null,
                    size = 16.dp,
                    tint = PantopusColors.success,
                )
            }
        }
    }
}

@Composable
private fun VisibilityPicker(
    fields: Map<EditProfileField, FormFieldState>,
    onUpdate: (EditProfileField, String) -> Unit,
) {
    val snapshot = fields[EditProfileField.ProfileVisibility]
    val current = snapshot?.value ?: "public"
    val options =
        remember {
            listOf("public" to "Public", "registered" to "Registered", "private" to "Private")
        }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.s1)) {
        Text(
            text = "Profile visibility",
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurfaceSunken)
                    .padding(Spacing.s1)
                    .testTag("field_${EditProfileField.ProfileVisibility.key}"),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            options.forEach { (key, label) ->
                val selected = key == current
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .sizeIn(minHeight = 36.dp)
                            .clip(RoundedCornerShape(Radii.sm))
                            .background(
                                if (selected) PantopusColors.appSurface else PantopusColors.appSurfaceSunken,
                            ).clickable { onUpdate(EditProfileField.ProfileVisibility, key) }
                            .testTag("field_${EditProfileField.ProfileVisibility.key}_$key")
                            .semantics { contentDescription = "$label visibility" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        style = PantopusTextStyle.body,
                        color = if (selected) PantopusColors.appText else PantopusColors.appTextSecondary,
                    )
                }
            }
        }
    }
}

private fun fieldStateFor(snapshot: FormFieldState?): PantopusFieldState =
    when {
        snapshot == null -> PantopusFieldState.Default
        snapshot.error != null -> PantopusFieldState.Error(snapshot.error)
        snapshot.touched && snapshot.isDirty -> PantopusFieldState.Valid
        else -> PantopusFieldState.Default
    }

@Composable
internal fun EditProfileSkeleton() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .testTag("editProfileSkeleton"),
        verticalArrangement = Arrangement.spacedBy(Spacing.s5),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp)
                    .background(PantopusColors.appSurface)
                    .padding(horizontal = Spacing.s4),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Edit profile",
                style = PantopusTextStyle.body,
                color = PantopusColors.appText,
                modifier = Modifier.semantics { heading() },
            )
        }
        repeat(3) { groupIndex ->
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Spacing.s4),
                verticalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Shimmer(width = 96.dp, height = 12.dp)
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radii.lg))
                            .background(PantopusColors.appSurface)
                            .padding(Spacing.s4),
                    verticalArrangement = Arrangement.spacedBy(Spacing.s3),
                ) {
                    repeat(if (groupIndex == 0) 4 else 2) {
                        Shimmer(width = 240.dp, height = 44.dp)
                    }
                }
            }
        }
    }
}
