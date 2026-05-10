@file:Suppress("MagicNumber", "PackageNaming")

package app.pantopus.android.ui.screens.mailbox.item_detail.bodies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.components.SectionHeader
import app.pantopus.android.ui.screens.mailbox.item_detail.MailItemCategory
import app.pantopus.android.ui.screens.root.NotYetAvailableView
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/** Summary card for the Package category body slot. */
@Composable
fun PackageBody(
    carrier: String,
    etaLine: String? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.s2),
    ) {
        SectionHeader("Delivery", modifier = Modifier.padding(horizontal = Spacing.s4))
        Column(
            modifier =
                Modifier
                    .padding(horizontal = Spacing.s4)
                    .clip(RoundedCornerShape(Radii.lg))
                    .background(PantopusColors.appSurface)
                    .padding(Spacing.s3),
            verticalArrangement = Arrangement.spacedBy(Spacing.s1),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.s2),
            ) {
                PantopusIconImage(
                    icon = PantopusIcon.ShoppingBag,
                    contentDescription = null,
                    size = 18.dp,
                    tint = PantopusColors.primary600,
                )
                Text(
                    "Carrier: $carrier",
                    style = PantopusTextStyle.body,
                    color = PantopusColors.appText,
                )
            }
            if (etaLine != null) {
                Text(
                    etaLine,
                    style = PantopusTextStyle.caption,
                    color = PantopusColors.appTextSecondary,
                )
            }
        }
    }
}

/** Factory for the 13 placeholder bodies used by the non-Package categories. */
@Composable
fun MailItemPlaceholderBody(
    category: MailItemCategory,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .padding(horizontal = Spacing.s4)
                .heightIn(min = 280.dp),
    ) {
        NotYetAvailableView(
            tabName = category.raw.replaceFirstChar { it.uppercase() },
            icon = PantopusIcon.Info,
            accent = PantopusColors.appSurfaceSunken,
            foreground = category.accent,
        )
    }
}
