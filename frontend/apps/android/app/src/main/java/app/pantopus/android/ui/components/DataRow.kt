@file:Suppress("MagicNumber", "MatchingDeclarationName", "LongParameterList", "UnusedPrivateMember")

package app.pantopus.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Optional trailing badge rendered next to a [DataRow] value. */
data class DataRowFlag(
    val text: String,
    val variant: StatusChipVariant = StatusChipVariant.Neutral,
)

/**
 * Read-only label / value row for detail surfaces. Supports an optional
 * secondary line under the value and an optional trailing status flag.
 *
 * @param label Leading caption.
 * @param value Trailing value.
 * @param sub Optional secondary line under the value.
 * @param flag Optional trailing status badge.
 */
@Composable
fun DataRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    sub: String? = null,
    flag: DataRowFlag? = null,
    testTag: String? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
                .padding(vertical = Spacing.s2)
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        buildString {
                            append("$label, $value")
                            if (sub != null) append(", $sub")
                            if (flag != null) append(", ${flag.text}")
                        }
                },
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = PantopusTextStyle.small,
            color = PantopusColors.appTextSecondary,
            modifier = Modifier.widthIn(min = 96.dp),
        )
        Spacer(Modifier.weight(1f))
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                Text(
                    text = value,
                    style = PantopusTextStyle.small,
                    color = PantopusColors.appText,
                    textAlign = TextAlign.End,
                )
                if (flag != null) {
                    StatusChip(text = flag.text, variant = flag.variant)
                }
            }
            if (sub != null) {
                Text(
                    text = sub,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextMuted,
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DataRowPreview() {
    Column(
        modifier =
            Modifier
                .padding(Spacing.s4)
                .background(PantopusColors.appBg),
    ) {
        Column(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .padding(Spacing.s3),
        ) {
            DataRow(label = "Year built", value = "1998")
            DataRow(label = "Square footage", value = "2,140 sq ft", sub = "Heated area")
            DataRow(label = "HOA", value = "Maplewood Ridge", flag = DataRowFlag("Active", StatusChipVariant.Success))
            DataRow(label = "Parcel ID", value = "48-2291-007", flag = DataRowFlag("Verified", StatusChipVariant.Personal))
        }
    }
}
