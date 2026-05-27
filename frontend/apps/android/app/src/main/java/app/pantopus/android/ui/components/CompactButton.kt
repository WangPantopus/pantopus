@file:Suppress("MagicNumber", "MatchingDeclarationName", "LongParameterList")

package app.pantopus.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.screens.shared.list_of_rows.CompactButtonVariant
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * Geometry variant for [CompactButton]. Mirrors iOS `CompactButtonSize`.
 *
 * - [Footer] — 34dp tall, in-card row footer. Stretches to fill its flex
 *   cell (use `Modifier.weight(1f)` in a Row, or `Modifier.fillMaxWidth()`).
 * - [InlineAction] — 30dp primary / 28dp ghost. Compact pill used next to
 *   a row's metadata (Accept / Ignore on Connections, etc.).
 */
enum class CompactButtonSize {
    Footer,
    InlineAction,
}

/**
 * Compact in-row action button. Mirrors `Core/Design/Components/CompactButton.swift`
 * pixel-for-pixel: 34dp footer / 28-30dp inline, 12dp horizontal padding,
 * 13dp leading icon, caption type ramp, Radii.md corners.
 *
 * The variant palette (Primary / Ghost / Destructive) mirrors `PantopusButton`'s
 * but the type exists because feature code wants to request a *size*, not a
 * substituted style.
 *
 * Token-only: all colors come from [PantopusColors], all spacing from
 * [Spacing], all radii from [Radii].
 *
 * @param title Button label. Also used as the spoken accessibility label
 *     so screen readers don't need a separate string.
 * @param variant Color palette. Primary fills the surface; Ghost and
 *     Destructive paint a border instead.
 * @param size Geometry. See [CompactButtonSize].
 * @param onClick Click handler.
 * @param modifier Caller modifier. Footer-sized callers typically pass
 *     `Modifier.weight(1f)` or `Modifier.fillMaxWidth()` here.
 * @param icon Optional leading icon (13dp glyph).
 */
@Composable
fun CompactButton(
    title: String,
    variant: CompactButtonVariant,
    size: CompactButtonSize,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: PantopusIcon? = null,
) {
    val height = resolvedHeightDp(size, variant).dp
    val background = backgroundFor(variant)
    val foreground = foregroundFor(variant)
    val border: BorderStroke? =
        if (variant == CompactButtonVariant.Primary) {
            null
        } else {
            BorderStroke(1.dp, PantopusColors.appBorder)
        }
    val shape = RoundedCornerShape(Radii.md)

    Row(
        modifier =
            modifier
                .height(height)
                .clip(shape)
                .background(background)
                .let { m -> border?.let { m.border(it, shape) } ?: m }
                .clickable(onClick = onClick)
                .padding(horizontal = Spacing.s3)
                .semantics {
                    contentDescription = title
                    role = Role.Button
                },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s1, Alignment.CenterHorizontally),
    ) {
        if (icon != null) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 13.dp,
                tint = foreground,
            )
        }
        Text(text = title, style = PantopusTextStyle.caption, color = foreground)
    }
}

internal fun resolvedHeightDp(size: CompactButtonSize, variant: CompactButtonVariant): Int =
    when {
        size == CompactButtonSize.Footer -> 34
        // Secondary inline pill (Ghost variant) is 28dp; primary inline is 30dp.
        variant == CompactButtonVariant.Ghost -> 28
        else -> 30
    }

private fun backgroundFor(variant: CompactButtonVariant): Color =
    when (variant) {
        CompactButtonVariant.Primary -> PantopusColors.primary600
        CompactButtonVariant.Ghost -> PantopusColors.appSurface
        CompactButtonVariant.Destructive -> PantopusColors.appSurface
    }

private fun foregroundFor(variant: CompactButtonVariant): Color =
    when (variant) {
        CompactButtonVariant.Primary -> PantopusColors.appTextInverse
        CompactButtonVariant.Ghost -> PantopusColors.appTextStrong
        CompactButtonVariant.Destructive -> PantopusColors.error
    }

@Preview(showBackground = true, widthDp = 360, heightDp = 200)
@Composable
private fun CompactButtonPreviewFooter() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(Spacing.s4),
        horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        CompactButton(
            title = "Withdraw",
            variant = CompactButtonVariant.Destructive,
            size = CompactButtonSize.Footer,
            onClick = {},
            icon = PantopusIcon.X,
            modifier = Modifier.weight(1f),
        )
        CompactButton(
            title = "Edit bid",
            variant = CompactButtonVariant.Primary,
            size = CompactButtonSize.Footer,
            onClick = {},
            icon = PantopusIcon.Check,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview(showBackground = true, widthDp = 160, heightDp = 120)
@Composable
private fun CompactButtonPreviewInline() {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier.padding(Spacing.s4),
        verticalArrangement = Arrangement.spacedBy(Spacing.s1),
    ) {
        CompactButton(
            title = "Accept",
            variant = CompactButtonVariant.Primary,
            size = CompactButtonSize.InlineAction,
            onClick = {},
        )
        CompactButton(
            title = "Ignore",
            variant = CompactButtonVariant.Ghost,
            size = CompactButtonSize.InlineAction,
            onClick = {},
        )
    }
}
