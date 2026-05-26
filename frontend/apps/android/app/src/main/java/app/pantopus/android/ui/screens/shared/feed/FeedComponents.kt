@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.shared.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pantopus.android.ui.components.Shimmer
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** One entry in [FeedChipRow]. */
@Immutable
data class FeedChipItem(
    val id: String,
    val label: String,
)

/** Horizontal scrolling chip row used by Pulse + Gigs to filter in place. */
@Composable
fun FeedChipRow(
    chips: List<FeedChipItem>,
    activeId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(PantopusColors.appBg)
                .testTag("feedChipRow"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s3),
            horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
        ) {
            chips.forEach { chip ->
                val active = chip.id == activeId
                Box(
                    modifier =
                        Modifier
                            .height(28.dp)
                            .clip(RoundedCornerShape(Radii.pill))
                            .background(if (active) PantopusColors.primary600 else PantopusColors.appSurface)
                            .then(
                                if (active) {
                                    Modifier
                                } else {
                                    Modifier.border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.pill))
                                },
                            )
                            .clickable { onSelect(chip.id) }
                            .padding(horizontal = 14.dp)
                            .testTag("feedChip_${chip.id}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = chip.label,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) PantopusColors.appTextInverse else PantopusColors.appTextStrong,
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(PantopusColors.appBorder),
        )
    }
}

/** Pencil FAB used by feed surfaces to start a compose flow. */
@Composable
fun FeedComposeFAB(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = "Compose post",
) {
    Box(
        modifier =
            modifier
                .size(52.dp)
                .shadow(elevation = 12.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(PantopusColors.primary600)
                .clickable(onClick = onClick)
                .testTag("feedComposeFAB")
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Pencil,
            contentDescription = null,
            size = Radii.xl2,
            tint = PantopusColors.appTextInverse,
        )
    }
}

/**
 * Single shimmer card used by the loading frame. Optionally renders an
 * extra title-line shimmer for intents whose cards have a title.
 */
@Composable
fun FeedSkeletonCard(
    withTitle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.lg))
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.lg))
                .background(PantopusColors.appSurface)
                .padding(Spacing.s3),
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Shimmer(width = 32.dp, height = 32.dp, cornerRadius = Radii.xl)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Shimmer(width = 110.dp, height = 10.dp, cornerRadius = Radii.xs)
                Shimmer(width = 70.dp, height = 8.dp, cornerRadius = Radii.xs)
            }
            Shimmer(width = 42.dp, height = 16.dp, cornerRadius = Radii.pill)
        }
        if (withTitle) {
            Shimmer(width = 200.dp, height = 11.dp, cornerRadius = Radii.xs)
        }
        Shimmer(width = 320.dp, height = 9.dp, cornerRadius = Radii.xs)
        Shimmer(width = 280.dp, height = 9.dp, cornerRadius = Radii.xs)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Shimmer(width = 56.dp, height = 10.dp, cornerRadius = Radii.xs)
            Shimmer(width = 56.dp, height = 10.dp, cornerRadius = Radii.xs)
            Spacer(modifier = Modifier.weight(1f))
            Shimmer(width = 42.dp, height = 10.dp, cornerRadius = Radii.xs)
        }
    }
}

/** Helper that builds the standard padding around the feed content area. */
fun feedContentPadding() = PaddingValues(Spacing.s3)
