@file:Suppress("PackageNaming", "MatchingDeclarationName", "MagicNumber")

package app.pantopus.android.ui.screens.scheduling._shared

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusElevation
import app.pantopus.android.ui.theme.PantopusElevations
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.theme.pantopusShadow

/**
 * Shared Calendarly screen chrome that mirrors iOS `SetupKit` (top bar, card
 * surface, mini toggle). These are the single sources of truth scheduling
 * screens reuse instead of re-implementing a bespoke top bar / card / toggle
 * per screen, which is what made the Android chip & bar drift across screens.
 */

/** Leading control for [SchedulingTopBar]. */
enum class SchedulingTopBarLeading { None, Back, Close }

private val TOP_BAR_HEIGHT = 56.dp
private val TOP_BAR_HIT = 36.dp
private val TOP_BAR_ICON = 22.dp

/**
 * The canonical 56dp scheduling top bar: optional leading chevron/close in a
 * 36dp hit target, a centered 16sp / 600 title, an optional trailing icon, an
 * app-surface background and a 1dp bottom hairline. Mirrors iOS `SetupTopBar`.
 * testTags mirror iOS: schedulingTopBarBack / schedulingTopBarClose /
 * schedulingTopBarTrailing.
 */
@Composable
fun SchedulingTopBar(
    title: String,
    modifier: Modifier = Modifier,
    leading: SchedulingTopBarLeading = SchedulingTopBarLeading.Back,
    onLeading: (() -> Unit)? = null,
    trailingIcon: PantopusIcon? = null,
    onTrailing: (() -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TOP_BAR_HEIGHT)
                .background(PantopusColors.appSurface)
                .padding(horizontal = Spacing.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Leading
        Box(modifier = Modifier.size(TOP_BAR_HIT), contentAlignment = Alignment.Center) {
            if (leading != SchedulingTopBarLeading.None) {
                val isBack = leading == SchedulingTopBarLeading.Back
                Box(
                    modifier =
                        Modifier
                            .size(TOP_BAR_HIT)
                            .testTag(if (isBack) "schedulingTopBarBack" else "schedulingTopBarClose")
                            .clip(CircleShape)
                            .clickable(
                                enabled = onLeading != null,
                                onClickLabel = if (isBack) "Back" else "Close",
                                onClick = { onLeading?.invoke() },
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = if (isBack) PantopusIcon.ChevronLeft else PantopusIcon.X,
                        contentDescription = if (isBack) "Back" else "Close",
                        size = TOP_BAR_ICON,
                        tint = PantopusColors.appText,
                    )
                }
            }
        }
        // Title
        Text(
            text = title,
            color = PantopusColors.appText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.s1),
        )
        // Trailing
        Box(modifier = Modifier.size(TOP_BAR_HIT), contentAlignment = Alignment.Center) {
            if (trailingIcon != null) {
                Box(
                    modifier =
                        Modifier
                            .size(TOP_BAR_HIT)
                            .testTag("schedulingTopBarTrailing")
                            .clip(CircleShape)
                            .clickable(enabled = onTrailing != null, onClick = { onTrailing?.invoke() }),
                    contentAlignment = Alignment.Center,
                ) {
                    PantopusIconImage(
                        icon = trailingIcon,
                        contentDescription = null,
                        size = TOP_BAR_ICON,
                        tint = PantopusColors.appText,
                    )
                }
            }
        }
    }
    // 1dp bottom hairline
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(PantopusColors.appBorder),
    )
}

/**
 * White card surface: rounded corners + 1dp border + soft shadow. Mirrors iOS
 * `View.setupCard(radius:shadow:)`. Apply to any container that should read as
 * the design's section card.
 */
fun Modifier.schedulingCard(
    radius: Dp = Radii.xl,
    elevation: PantopusElevation = PantopusElevations.sm,
): Modifier {
    val shape = RoundedCornerShape(radius)
    return this
        .pantopusShadow(elevation, shape)
        .clip(shape)
        .background(PantopusColors.appSurface)
        .border(1.dp, PantopusColors.appBorder, shape)
}

private val MINI_TOGGLE_W = 32.dp
private val MINI_TOGGLE_H = 18.dp
private val MINI_TOGGLE_KNOB = 14.dp

/**
 * Compact 32×18 mini-toggle used by wizard / weekly-hours rows. Accent fill when
 * on, neutral strong border when off, white 14dp knob with a soft shadow.
 * Mirrors iOS `SetupMiniToggle`.
 */
@Composable
fun SchedulingMiniToggle(
    isOn: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(MINI_TOGGLE_W)
                .height(MINI_TOGGLE_H)
                .clip(CircleShape)
                .background(if (isOn) accent else PantopusColors.appBorderStrong),
        contentAlignment = if (isOn) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(MINI_TOGGLE_KNOB)
                    .pantopusShadow(PantopusElevations.sm, CircleShape)
                    .clip(CircleShape)
                    .background(PantopusColors.appSurface),
        )
    }
}
