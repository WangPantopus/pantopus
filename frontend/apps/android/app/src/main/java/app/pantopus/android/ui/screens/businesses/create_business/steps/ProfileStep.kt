@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses.create_business.steps

import androidx.compose.runtime.Composable
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * A12.10 step 3 — Profile. Stub: design only ships frame 1+2 of the
 * wizard today; a follow-on prompt replaces the body once design hands
 * off step-3 frames.
 */
@Composable
fun ProfileStep() {
    BusinessIdentityChip()
    HeadlineBlock("Business profile")
    SubcopyBlock(
        "Name, banner, services, hours, and contact. Design ships this step in a follow-on.",
    )
    WizardStubPlaceholder(
        icon = PantopusIcon.Briefcase,
        label = "Step 3 — Profile",
        subcopy = "Designed frames land in the next prompt.",
    )
}
