@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses.create_business.steps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage
import app.pantopus.android.ui.theme.PantopusTextStyle
import app.pantopus.android.ui.theme.Radii
import app.pantopus.android.ui.theme.Spacing

/**
 * A12.10 step 4 — Confirm. Stub: design only ships frame 1+2 of the
 * wizard today; a follow-on prompt replaces the body once design hands
 * off step-4 frames.
 */
@Composable
fun ConfirmStep() {
    BusinessIdentityChip()
    HeadlineBlock("Confirm and publish")
    SubcopyBlock("Review before we publish. Design ships this step in a follow-on.")
    WizardStubPlaceholder(
        icon = PantopusIcon.CheckCircle,
        label = "Step 4 — Confirm",
        subcopy = "Designed frames land in the next prompt.",
    )
}

/** Shared placeholder used by every stub step. */
@Composable
fun WizardStubPlaceholder(
    icon: PantopusIcon,
    label: String,
    subcopy: String,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radii.xl))
                .background(PantopusColors.appSurface)
                .border(1.dp, PantopusColors.appBorder, RoundedCornerShape(Radii.xl))
                .padding(horizontal = Spacing.s4, vertical = Spacing.s6)
                .testTag("createBusinessStubPlaceholder"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.s3),
    ) {
        Box(
            modifier =
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(PantopusColors.businessBg),
            contentAlignment = Alignment.Center,
        ) {
            PantopusIconImage(
                icon = icon,
                contentDescription = null,
                size = 22.dp,
                tint = PantopusColors.business,
            )
        }
        Text(
            text = label,
            style = PantopusTextStyle.body,
            color = PantopusColors.appText,
        )
        Text(
            text = subcopy,
            style = PantopusTextStyle.caption,
            color = PantopusColors.appTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
