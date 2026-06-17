@file:Suppress("PackageNaming", "LongMethod", "LongParameterList", "TooManyFunctions", "CyclomaticComplexMethod", "LargeClass", "MatchingDeclarationName")

package app.pantopus.android.ui.screens.scheduling.bookings_extra

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** One attendee or waitlist entry rendered in a roster list. */
data class RosterPerson(
    val id: String,
    val name: String,
    val meta: String,
    val status: String? = null,
)

private val BAR_HEIGHT = 8.dp

/**
 * The "12 of 16 seats filled · 3 waiting" capacity card with an identity-tinted
 * fill bar (clamps to grayscale when full) and an optional Confirmed / Pending
 * / Waitlisted stat strip. Mirrors iOS `CapacityHeaderCard`.
 */
@Composable
internal fun CapacityHeaderCard(
    filled: Int,
    total: Int,
    waiting: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    showStats: Boolean = true,
    confirmed: Int = filled,
    pending: Int = 0,
) {
    val isFull = total > 0 && filled >= total
    val fraction = if (total > 0) (filled.toFloat() / total).coerceIn(0f, 1f) else 0f
    val barColor = if (isFull) PantopusColors.appBorderStrong else accent
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "$filled of $total seats filled · $waiting waiting",
                style = PantopusTextStyle.body,
                fontWeight = FontWeight.Bold,
                color = PantopusColors.appText,
                modifier = Modifier.weight(1f),
            )
            if (isFull) {
                Text(
                    text = "All seats filled",
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                    modifier = Modifier.padding(start = Spacing.s2),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(BAR_HEIGHT)
                    .clip(RoundedCornerShape(Radii.pill))
                    .background(PantopusColors.appSurfaceSunken),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(BAR_HEIGHT)
                        .clip(RoundedCornerShape(Radii.pill))
                        .background(barColor),
            )
        }
        if (showStats) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s2)) {
                StatCell(value = confirmed, label = "Confirmed", valueColor = PantopusColors.appText, modifier = Modifier.weight(1f))
                StatCell(value = pending, label = "Pending", valueColor = PantopusColors.warning, modifier = Modifier.weight(1f))
                StatCell(value = waiting, label = "Waitlisted", valueColor = PantopusColors.appTextSecondary, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatCell(
    value: Int,
    label: String,
    valueColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurfaceSunken)
                .padding(vertical = Spacing.s3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        Text(text = value.toString(), style = PantopusTextStyle.h3, fontWeight = FontWeight.Bold, color = valueColor)
        Text(text = label.uppercase(), style = PantopusTextStyle.overline, color = PantopusColors.appTextSecondary)
    }
}

/**
 * A roster list row: avatar + name + meta caption, with a caller-supplied
 * trailing slot (status chip + kebab, a Promote button, or a checkbox).
 */
/** The pill-shaped "Promote to seat" action, disabled (gray) when no seat is open. */
@Composable
internal fun PromoteSeatButton(
    enabled: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) accent else PantopusColors.appTextMuted
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(Radii.pill))
                .background(if (enabled) accent.copy(alpha = PROMOTE_BG_ALPHA) else PantopusColors.appSurfaceSunken)
                .clickable(enabled = enabled, onClickLabel = "Promote to seat", onClick = onClick)
                .padding(horizontal = Spacing.s3, vertical = Spacing.s2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        app.pantopus.android.ui.theme.PantopusIconImage(
            icon = app.pantopus.android.ui.theme.PantopusIcon.ArrowUp,
            contentDescription = null,
            size = 14.dp,
            tint = tint,
        )
        Text(text = "Promote", style = PantopusTextStyle.small, fontWeight = FontWeight.SemiBold, color = tint)
    }
}

private const val PROMOTE_BG_ALPHA = 0.12f

@Composable
internal fun RosterRow(
    person: RosterPerson,
    modifier: Modifier = Modifier,
    verified: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .padding(horizontal = Spacing.s3, vertical = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        InitialsAvatar(name = person.name, diameter = 38.dp, verified = verified)
        Column(modifier = Modifier.weight(1f)) {
            Text(text = person.name, style = PantopusTextStyle.body, fontWeight = FontWeight.SemiBold, color = PantopusColors.appText)
            if (person.meta.isNotEmpty()) {
                Text(text = person.meta, style = PantopusTextStyle.caption, color = PantopusColors.appTextSecondary)
            }
        }
        trailing()
    }
}
