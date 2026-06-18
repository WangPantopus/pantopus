@file:Suppress("PackageNaming", "MagicNumber", "LongParameterList", "LongMethod", "TooManyFunctions")

package app.pantopus.android.ui.screens.scheduling.invitee.confirm

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

private const val MAX_GUESTS = 5

/**
 * D1 — Intake / booking details form (the scrollable body; the flow shell owns
 * the top bar + sticky CTA). Mirrors Form.html: a non-editable summary header,
 * a slot-hold countdown, "Your info" (first/last/email/phone), schema-driven
 * host questions (dormant until the public payload carries them), and add-guests.
 */
@Composable
fun IntakeFormBody(
    state: ConfirmFlowState,
    args: InviteeConfirmArgs,
    pillar: SchedulingPillar,
    questions: List<IntakeQuestion>,
    onPatch: (IntakeValues) -> Unit,
    onAnswer: (String, AnswerValue) -> Unit,
    onEditSlot: () -> Unit,
    onChangeTz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val values = state.values
    val errors = state.shownErrors
    val disabled = state.holdExpired || state.submitting

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.s4)) {
        if (state.holdExpired) {
            ConfirmBanner(
                tone = BannerTone.Warning,
                icon = PantopusIcon.Timer,
                title = "This held time just expired",
                body = "Someone else can book it now. Pick another time to keep going.",
            )
        }

        IntakeSummaryHeader(
            args = args,
            pillar = pillar,
            whenLabel = ConfirmUtils.formatSlotRange(state.slotStartUtc, state.slotEndUtc, state.tz),
            tzLabel = ConfirmUtils.tzChipLabel(state.tz, state.slotStartUtc),
            dimmed = state.holdExpired,
            onEdit = onEditSlot,
            onChangeTz = if (state.holdExpired) null else onChangeTz,
        )

        if (!state.holdExpired) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.Clock,
                    contentDescription = null,
                    size = 13.dp,
                    tint = PantopusColors.appTextSecondary,
                    modifier = Modifier.padding(end = Spacing.s1),
                )
                Text(
                    text = "We're holding this time for ${holdLabel(state.holdSecondsLeft)}",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }

        // Your info
        Column {
            ConfirmOverline("Your info")
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField(label = "First name", required = true) {
                            ConfirmTextInput(
                                value = values.firstName,
                                onValueChange = { onPatch(values.copy(firstName = it)) },
                                placeholder = "Maya",
                                enabled = !disabled,
                                error = errors["firstName"],
                                valid = errors["firstName"] == null && values.firstName.isNotBlank(),
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledField(label = "Last name", required = true) {
                            ConfirmTextInput(
                                value = values.lastName,
                                onValueChange = { onPatch(values.copy(lastName = it)) },
                                placeholder = "Chen",
                                enabled = !disabled,
                                error = errors["lastName"],
                                valid = errors["lastName"] == null && values.lastName.isNotBlank(),
                            )
                        }
                    }
                }
                LabeledField(label = "Email", required = true) {
                    ConfirmTextInput(
                        value = values.email,
                        onValueChange = { onPatch(values.copy(email = it)) },
                        placeholder = "you@email.com",
                        keyboardType = KeyboardType.Email,
                        enabled = !disabled,
                        error = errors["email"],
                        valid = errors["email"] == null && ConfirmUtils.isValidEmail(values.email),
                        helper = "We'll only email you about this booking.",
                    )
                }
                LabeledField(label = "Phone number") {
                    ConfirmTextInput(
                        value = values.phone,
                        onValueChange = { onPatch(values.copy(phone = it)) },
                        placeholder = "(555) 000-0000",
                        leading = "+1",
                        keyboardType = KeyboardType.Phone,
                        enabled = !disabled,
                        helper = "For a text reminder before your booking.",
                    )
                }
            }
        }

        // Host questions (dynamic; dormant today)
        if (questions.isNotEmpty()) {
            Column {
                ConfirmOverline("A few questions")
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.s3)) {
                    questions.forEachIndexed { index, question ->
                        val key = ConfirmUtils.questionKey(question, index)
                        QuestionField(
                            question = question,
                            value = values.answers[key],
                            error = errors[key],
                            enabled = !disabled,
                            onAnswer = { onAnswer(key, it) },
                        )
                    }
                }
            }
        }

        AddGuests(values = values, enabled = !disabled, pillar = pillar, onPatch = onPatch, errors = errors)
    }
}

@Composable
private fun IntakeSummaryHeader(
    args: InviteeConfirmArgs,
    pillar: SchedulingPillar,
    whenLabel: String,
    tzLabel: String,
    dimmed: Boolean,
    onEdit: () -> Unit,
    onChangeTz: (() -> Unit)?,
) {
    val durationLine =
        listOfNotNull(
            ConfirmUtils.durationLabel(args.eventType.defaultDuration).ifEmpty { null },
            "with ${args.hostName}",
        ).joinToString(" · ")
    ConfirmCard(modifier = if (dimmed) Modifier.alpha(0.55f) else Modifier) {
        Row(verticalAlignment = Alignment.Top) {
            HostAvatar(pillar = pillar, initials = ConfirmUtils.initials(args.hostName))
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.s2)) {
                Text(
                    text = args.eventType.name ?: "Booking",
                    style = PantopusTextStyle.small,
                    fontWeight = FontWeight.Bold,
                    color = PantopusColors.appText,
                )
                Text(text = durationLine, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
            Text(
                text = "Edit",
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = pillar.accent,
                modifier = Modifier.clickable(onClick = onEdit),
            )
        }
        HorizontalDivider(color = PantopusColors.appBorder, modifier = Modifier.padding(vertical = Spacing.s3))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PantopusIconImage(
                icon = PantopusIcon.Calendar,
                contentDescription = null,
                size = 15.dp,
                tint = PantopusColors.appTextSecondary,
                modifier = Modifier.padding(end = Spacing.s2),
            )
            Text(text = whenLabel, style = PantopusTextStyle.caption, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
        }
        TimezoneChip(label = tzLabel, accent = pillar.accent, onChange = onChangeTz, modifier = Modifier.padding(top = Spacing.s2))
    }
}

@Composable
private fun AddGuests(
    values: IntakeValues,
    enabled: Boolean,
    pillar: SchedulingPillar,
    onPatch: (IntakeValues) -> Unit,
    errors: Map<String, String>,
) {
    if (values.guests.isEmpty()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                    .clickable(enabled = enabled) { onPatch(values.copy(guests = listOf(""))) }
                    .padding(Spacing.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(28.dp).clip(RoundedCornerShape(Radii.md)).background(pillar.accentBg),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(icon = PantopusIcon.UserPlus, contentDescription = null, size = 15.dp, tint = pillar.accent)
            }
            Column(modifier = Modifier.weight(1f).padding(start = Spacing.s2)) {
                Text(
                    text = "Add guests",
                    style = PantopusTextStyle.caption,
                    fontWeight = FontWeight.SemiBold,
                    color = PantopusColors.appText,
                )
                Text(text = "Add up to $MAX_GUESTS guests.", style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
            PantopusIconImage(icon = PantopusIcon.Plus, contentDescription = null, size = 17.dp, tint = pillar.accent)
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.s2)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s1)) {
                PantopusIconImage(
                    icon = PantopusIcon.Users,
                    contentDescription = null,
                    size = 14.dp,
                    tint = PantopusColors.appTextSecondary,
                )
                Text(text = "Guests", style = PantopusTextStyle.caption, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
            }
            values.guests.forEachIndexed { index, guest ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ConfirmTextInput(
                            value = guest,
                            onValueChange = { newValue ->
                                onPatch(values.copy(guests = values.guests.toMutableList().also { it[index] = newValue }))
                            },
                            placeholder = "guest@email.com",
                            keyboardType = KeyboardType.Email,
                            enabled = enabled,
                            error = errors["guest$index"],
                        )
                    }
                    Box(
                        modifier =
                            Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(Radii.md))
                                .background(PantopusColors.appSurface)
                                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.md))
                                .clickable(enabled = enabled) {
                                    onPatch(
                                        values.copy(
                                            guests =
                                                values.guests.filterIndexed {
                                                        i,
                                                        _,
                                                    ->
                                                    i != index
                                                },
                                        ),
                                    )
                                },
                        contentAlignment = Alignment.Center,
                    ) {
                        PantopusIconImage(
                            icon = PantopusIcon.X,
                            contentDescription = "Remove guest",
                            size = 15.dp,
                            tint = PantopusColors.appTextSecondary,
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    modifier =
                        Modifier.clickable(
                            enabled = enabled && values.guests.size < MAX_GUESTS,
                        ) { onPatch(values.copy(guests = values.guests + "")) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
                ) {
                    PantopusIconImage(icon = PantopusIcon.Plus, contentDescription = null, size = 13.dp, tint = pillar.accent)
                    Text(text = "Add another", style = PantopusTextStyle.caption, fontWeight = FontWeight.Bold, color = pillar.accent)
                }
                Text(text = "${values.guests.size} of $MAX_GUESTS", style = PantopusTextStyle.caption, color = PantopusColors.appTextMuted)
            }
        }
    }
}

@Composable
private fun QuestionField(
    question: IntakeQuestion,
    value: AnswerValue?,
    error: String?,
    enabled: Boolean,
    onAnswer: (AnswerValue) -> Unit,
) {
    when (question.fieldType) {
        IntakeFieldType.Textarea ->
            LabeledField(label = question.label, required = question.required) {
                ConfirmTextInput(
                    value = (value as? AnswerValue.Text)?.value.orEmpty(),
                    onValueChange = { onAnswer(AnswerValue.Text(it)) },
                    placeholder = "Type your answer",
                    enabled = enabled,
                    error = error,
                    singleLine = false,
                    minHeight = 78.dp,
                )
            }
        IntakeFieldType.Phone ->
            LabeledField(label = question.label, required = question.required) {
                ConfirmTextInput(
                    value = (value as? AnswerValue.Text)?.value.orEmpty(),
                    onValueChange = { onAnswer(AnswerValue.Text(it)) },
                    placeholder = "(555) 000-0000",
                    leading = "+1",
                    keyboardType = KeyboardType.Phone,
                    enabled = enabled,
                    error = error,
                )
            }
        else ->
            // text / select / multiselect / checkbox all collapse to a text answer here;
            // typed widgets render when the public payload ever exposes question schemas.
            LabeledField(label = question.label, required = question.required) {
                ConfirmTextInput(
                    value = (value as? AnswerValue.Text)?.value.orEmpty(),
                    onValueChange = { onAnswer(AnswerValue.Text(it)) },
                    placeholder = "Type your answer",
                    enabled = enabled,
                    error = error,
                )
            }
    }
}

// ─── Field atoms (mirror Form.html) ──────────────────────────────────────────

@Composable
fun LabeledField(
    label: String,
    required: Boolean = false,
    content: @Composable () -> Unit,
) {
    Column {
        Row(modifier = Modifier.padding(bottom = Spacing.s1)) {
            Text(text = label, fontSize = 11.5f.sp, fontWeight = FontWeight.SemiBold, color = PantopusColors.appTextStrong)
            if (required) Text(text = " *", fontSize = 11.5f.sp, color = PantopusColors.error)
        }
        content()
    }
}

@Composable
fun ConfirmTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    leading: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    enabled: Boolean = true,
    error: String? = null,
    valid: Boolean = false,
    helper: String? = null,
    singleLine: Boolean = true,
    minHeight: androidx.compose.ui.unit.Dp = 44.dp,
) {
    val borderColor =
        when {
            error != null -> PantopusColors.error
            valid -> PantopusColors.success
            else -> PantopusColors.appBorder
        }
    Column(modifier = modifier) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = minHeight)
                    .clip(RoundedCornerShape(Radii.md))
                    .background(PantopusColors.appSurface)
                    .border(1.dp, borderColor, RoundedCornerShape(Radii.md))
                    .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
            verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
        ) {
            if (leading != null) {
                Text(
                    text = leading,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appTextMuted,
                    modifier = Modifier.padding(end = Spacing.s1),
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    singleLine = singleLine,
                    textStyle = LocalTextStyle.current.merge(PantopusTextStyle.small).copy(color = PantopusColors.appText),
                    cursorBrush = SolidColor(PantopusColors.primary600),
                    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isEmpty()) {
                    Text(text = placeholder, style = PantopusTextStyle.small, color = PantopusColors.appTextMuted)
                }
            }
            when {
                error != null ->
                    PantopusIconImage(
                        icon = PantopusIcon.AlertCircle,
                        contentDescription = null,
                        size = 17.dp,
                        tint = PantopusColors.error,
                    )
                valid ->
                    PantopusIconImage(
                        icon = PantopusIcon.CheckCircle,
                        contentDescription = null,
                        size = 17.dp,
                        tint = PantopusColors.success,
                    )
            }
        }
        if (error != null) {
            Row(
                modifier = Modifier.padding(top = Spacing.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
            ) {
                PantopusIconImage(icon = PantopusIcon.AlertCircle, contentDescription = null, size = 11.dp, tint = PantopusColors.error)
                Text(text = error, style = PantopusTextStyle.caption, color = PantopusColors.error)
            }
        } else if (helper != null) {
            Text(
                text = helper,
                style = PantopusTextStyle.caption,
                color = PantopusColors.appTextSecondary,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = Spacing.s1),
            )
        }
    }
}

private fun holdLabel(secondsLeft: Int): String {
    val m = secondsLeft / 60
    val s = (secondsLeft % 60).toString().padStart(2, '0')
    return "$m:$s"
}
