@file:Suppress("MagicNumber", "MatchingDeclarationName")

package app.pantopus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.Spacing
import app.pantopus.android.ui.screens.shared.list_of_rows.Bidder
import app.pantopus.android.ui.screens.shared.list_of_rows.BidderTone

/**
 * Overlapping 22dp mini-avatars + `+N` overflow tile.
 *
 * Used inside My tasks rows to communicate competition at a glance
 * ("3 faces + 9 more bid on this task"). Not built on top of
 * [AvatarWithIdentityRing]: the identity ring assumes ≥40dp geometry; the
 * bidder primitive is intentionally minimal — initials on a tone-coloured
 * disk with a 2dp surface border that simulates the overlap when the
 * parent gives each tile a -8dp leading offset.
 *
 * Mirrors `Core/Design/Components/BidderStack.swift` pixel-for-pixel —
 * tile size, overlap, font ratio, border width, tone palette.
 *
 * @param bidders Up to N visible avatars; the caller decides the cap.
 * @param overflow Extra bidder count beyond [bidders]; renders as a
 *     `+N` tile. Negative values are clamped to 0.
 * @param onTap Optional tap callback. When provided, the whole stack
 *     becomes a clickable target — call sites typically open a
 *     "Participants" sheet in response. When null, the stack reads as
 *     decorative geometry (only the accessibility count label is spoken).
 */
@Composable
fun BidderStack(
    bidders: List<Bidder>,
    modifier: Modifier = Modifier,
    overflow: Int = 0,
    onTap: (() -> Unit)? = null,
) {
    val safeOverflow = overflow.coerceAtLeast(0)
    val label = bidderStackA11yLabel(bidders.size + safeOverflow)

    val rowModifier =
        modifier
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            // mergeDescendants collapses every initials-Text node into one
            // virtual a11y node; the explicit [contentDescription] then
            // replaces the merged value so screen readers hear "3 bidders",
            // not "AR, MT, JP, +9". Mirrors iOS `accessibilityElement(
            // children: .ignore)` + `accessibilityLabel(...)`.
            .semantics(mergeDescendants = true) {
                contentDescription = label
                if (onTap != null) role = Role.Button
            }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        bidders.forEachIndexed { index, bidder ->
            BidderTile(
                initials = bidder.initials,
                background = toneBackground(bidder.tone),
                foreground = toneForeground(bidder.tone),
                isFirst = index == 0,
            )
        }
        if (safeOverflow > 0) {
            BidderTile(
                initials = "+$safeOverflow",
                background = PantopusColors.appSurfaceSunken,
                foreground = PantopusColors.appTextStrong,
                isFirst = bidders.isEmpty(),
                bold = true,
            )
        }
    }
}

@Composable
private fun BidderTile(
    initials: String,
    background: Color,
    foreground: Color,
    isFirst: Boolean,
    bold: Boolean = false,
) {
    Box(
        modifier =
            Modifier
                .offset(x = if (isFirst) 0.dp else (-8).dp)
                .size(TILE_SIZE_DP)
                .clip(CircleShape)
                .background(background)
                .border(2.dp, PantopusColors.appSurface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials.take(2).uppercase(),
            color = foreground,
            fontSize = (TILE_SIZE_DP.value * 0.36f).sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/**
 * Accessibility label spoken in place of the visible initials. Mirrors
 * the iOS string format exactly (single source of truth so screen-reader
 * tests pass on both platforms).
 */
internal fun bidderStackA11yLabel(totalCount: Int): String =
    when (totalCount) {
        0 -> "No bidders"
        1 -> "1 bidder"
        else -> "$totalCount bidders"
    }

private val TILE_SIZE_DP = 22.dp

private fun toneBackground(tone: BidderTone): Color =
    when (tone) {
        BidderTone.Sky -> PantopusColors.primary200
        BidderTone.Teal -> PantopusColors.successLight
        BidderTone.Amber -> PantopusColors.warningLight
        BidderTone.Rose -> PantopusColors.errorLight
        BidderTone.Violet -> PantopusColors.businessBg
        BidderTone.Slate -> PantopusColors.appSurfaceSunken
    }

private fun toneForeground(tone: BidderTone): Color =
    when (tone) {
        BidderTone.Sky -> PantopusColors.primary800
        BidderTone.Teal -> PantopusColors.success
        BidderTone.Amber -> PantopusColors.warning
        BidderTone.Rose -> PantopusColors.error
        BidderTone.Violet -> PantopusColors.business
        BidderTone.Slate -> PantopusColors.appTextStrong
    }

@Preview(showBackground = true, widthDp = 200, heightDp = 80)
@Composable
private fun BidderStackPreviewThreeWithOverflow() {
    BidderStack(
        bidders =
            listOf(
                Bidder(id = "1", initials = "AR", tone = BidderTone.Violet),
                Bidder(id = "2", initials = "MT", tone = BidderTone.Amber),
                Bidder(id = "3", initials = "JP", tone = BidderTone.Teal),
            ),
        overflow = 9,
        modifier = Modifier.padding(Spacing.s4),
    )
}

@Preview(showBackground = true, widthDp = 120, heightDp = 80)
@Composable
private fun BidderStackPreviewSingle() {
    BidderStack(
        bidders = listOf(Bidder(id = "1", initials = "AR", tone = BidderTone.Sky)),
        modifier = Modifier.padding(Spacing.s4),
    )
}

