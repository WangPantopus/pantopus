@file:Suppress(
    "PackageNaming",
    "LongMethod",
    "LongParameterList",
    "TooManyFunctions",
    "CyclomaticComplexMethod",
    "LargeClass",
    "MatchingDeclarationName",
)
@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingStatusPill
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

// ─── Section overline ──────────────────────────────────────────────────────

@Composable
internal fun ExtrasOverline(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text.uppercase(),
        style = PantopusTextStyle.overline,
        color = PantopusColors.appTextSecondary,
        modifier = modifier,
    )
}

// ─── Icon disc (confirmation/dialog halo) ──────────────────────────────────

private val DISC_SIZE = 44.dp
private val DISC_ICON = 22.dp

@Composable
internal fun ExtrasIconDisc(
    icon: PantopusIcon,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(DISC_SIZE).clip(CircleShape).background(background),
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = DISC_ICON, tint = tint)
    }
}

// ─── Inline error line ─────────────────────────────────────────────────────

@Composable
internal fun ExtrasInlineError(
    message: String,
    modifier: Modifier = Modifier,
    centered: Boolean = false,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (centered) Arrangement.Center else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.AlertCircle,
            contentDescription = null,
            size = 14.dp,
            tint = PantopusColors.error,
            modifier = Modifier.padding(end = Spacing.s1),
        )
        Text(text = message, style = PantopusTextStyle.caption, color = PantopusColors.error)
    }
}

// ─── Pill chip (single/multi select) ───────────────────────────────────────

@Composable
internal fun ExtrasPillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = PantopusColors.primary600,
    showDot: Boolean = false,
    enabled: Boolean = true,
) {
    val bg = if (selected) accent.copy(alpha = SELECTED_CHIP_ALPHA) else PantopusColors.appSurface
    val border = if (selected) accent else PantopusColors.appBorder
    val textColor =
        when {
            !enabled -> PantopusColors.appTextMuted
            selected -> accent
            else -> PantopusColors.appTextStrong
        }
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(bg)
                .border(BorderStroke(if (selected) 1.5.dp else 1.dp, border), RoundedCornerShape(Radii.pill))
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        if (showDot && selected) {
            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
        }
        Text(text = label, style = PantopusTextStyle.small, fontWeight = FontWeight.SemiBold, color = textColor)
    }
}

// ─── Removable active-filter chip ──────────────────────────────────────────

@Composable
internal fun ExtrasRemovableChip(
    label: String,
    accent: Color,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(accent.copy(alpha = SELECTED_CHIP_ALPHA))
                .clickable(onClickLabel = "Remove $label", onClick = onRemove)
                .padding(start = Spacing.s3, end = Spacing.s2, top = Spacing.s1, bottom = Spacing.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(text = label, style = PantopusTextStyle.small, fontWeight = FontWeight.SemiBold, color = accent)
        PantopusIconImage(icon = PantopusIcon.X, contentDescription = null, size = 13.dp, tint = accent)
    }
}

// ─── Outlined chip button ("Use a template", "Send rebook link") ───────────

@Composable
internal fun ExtrasChipButton(
    label: String,
    icon: PantopusIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = PantopusColors.primary600,
    enabled: Boolean = true,
) {
    val tint = if (enabled) accent else PantopusColors.appTextMuted
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(PantopusColors.appSurface)
                .border(
                    BorderStroke(1.dp, if (enabled) accent.copy(alpha = CHIP_BORDER_ALPHA) else PantopusColors.appBorder),
                    RoundedCornerShape(Radii.pill),
                )
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 14.dp, tint = tint)
        Text(text = label, style = PantopusTextStyle.small, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

// ─── Channel toggle row (Push / Email) ─────────────────────────────────────

@Composable
internal fun ExtrasChannelRow(
    icon: PantopusIcon,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    accent: Color = PantopusColors.primary600,
    subtitle: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(BorderStroke(1.dp, PantopusColors.appBorder), RoundedCornerShape(Radii.lg))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        PantopusIconImage(icon = icon, contentDescription = null, size = 18.dp, tint = PantopusColors.appTextStrong)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = PantopusTextStyle.body, fontWeight = FontWeight.Medium, color = PantopusColors.appText)
            if (subtitle != null) {
                Text(text = subtitle, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = PantopusColors.appTextInverse,
                    checkedTrackColor = accent,
                    uncheckedTrackColor = PantopusColors.appSurfaceSunken,
                    uncheckedBorderColor = PantopusColors.appBorderStrong,
                ),
        )
    }
}

// ─── Message box with optional character counter ───────────────────────────

@Composable
internal fun ExtrasMessageBox(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    accent: Color = PantopusColors.primary600,
    minHeight: androidx.compose.ui.unit.Dp = 96.dp,
    limit: Int? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    val over = limit != null && value.length > limit
    val border = if (over) PantopusColors.error else PantopusColors.appBorder
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceSunken)
                .border(BorderStroke(1.dp, border), RoundedCornerShape(Radii.lg))
                .padding(Spacing.s3),
    ) {
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = minHeight)) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = PantopusTextStyle.body, color = PantopusColors.appTextMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        }
        if (limit != null) {
            Text(
                text = "${value.length}/$limit",
                style = PantopusTextStyle.caption,
                color = if (over) PantopusColors.error else PantopusColors.appTextMuted,
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s1),
                textAlign = TextAlign.End,
            )
        }
    }
}

// ─── Single-line labelled input field ──────────────────────────────────────

@Composable
internal fun ExtrasInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: PantopusIcon,
    modifier: Modifier = Modifier,
    accent: Color = PantopusColors.primary600,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceSunken)
                .border(BorderStroke(1.dp, PantopusColors.appBorder), RoundedCornerShape(Radii.lg))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        PantopusIconImage(icon = leadingIcon, contentDescription = null, size = 16.dp, tint = PantopusColors.appTextMuted)
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(text = placeholder, style = PantopusTextStyle.body, color = PantopusColors.appTextMuted)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = PantopusTextStyle.body.copy(color = PantopusColors.appText),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        }
    }
}

// ─── Initials avatar (optional verified badge) ─────────────────────────────

private val AVATAR_PALETTE: List<Color> =
    listOf(
        PantopusColors.primary600,
        PantopusColors.business,
        PantopusColors.home,
        PantopusColors.warning,
        PantopusColors.info,
    )

@Composable
internal fun InitialsAvatar(
    name: String?,
    modifier: Modifier = Modifier,
    diameter: androidx.compose.ui.unit.Dp = 40.dp,
    verified: Boolean = false,
) {
    val color = AVATAR_PALETTE[(name?.hashCode()?.let { kotlin.math.abs(it) } ?: 0) % AVATAR_PALETTE.size]
    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier.size(diameter).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = BookingsExtrasFormatting.initials(name),
                style = PantopusTextStyle.caption,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appTextInverse,
            )
        }
        if (verified) {
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(PantopusColors.appSurface),
                contentAlignment = Alignment.Center,
            ) {
                PantopusIconImage(icon = PantopusIcon.CheckCircle, contentDescription = null, size = 13.dp, tint = PantopusColors.success)
            }
        }
    }
}

// ─── Status chip (Confirmed / Pending / …) ─────────────────────────────────

/**
 * Booking status chip — delegates to the shared [SchedulingStatusPill] primitive.
 * Kept as a thin wrapper so call sites in this package keep the same signature.
 * Normalises the two legacy wire aliases ("going" → confirmed, "waiting" →
 * waitlisted) the primitive does not yet alias, then hands off to the
 * tolerant string overload. A null/blank status falls through to the
 * primitive's neutral "Status" fallback.
 */
@Composable
internal fun StatusChip(
    status: String?,
    modifier: Modifier = Modifier,
) {
    val wire =
        when (status) {
            "going" -> "confirmed"
            "waiting" -> "waitlisted"
            else -> status.orEmpty()
        }
    SchedulingStatusPill(status = wire, modifier = modifier)
}

// ─── A wrapping row of chips ────────────────────────────────────────────────

@Composable
internal fun ExtrasChipFlow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        content()
    }
}

private const val SELECTED_CHIP_ALPHA = 0.12f
private const val CHIP_BORDER_ALPHA = 0.5f
