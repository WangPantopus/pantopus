@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.businesses.create_business.steps

import androidx.compose.runtime.Composable
import app.pantopus.android.ui.screens.shared.wizard.blocks.HeadlineBlock
import app.pantopus.android.ui.screens.shared.wizard.blocks.SubcopyBlock
import app.pantopus.android.ui.theme.PantopusIcon

/**
 * A12.10 step 2 — Legal info. Stub: design only ships frame 1+2 of the
 * wizard today, so this step renders a placeholder hero. A follow-on
 * prompt replaces the body once design hands off step-2 frames.
 */
@Composable
fun LegalInfoStep() {
    BusinessIdentityChip()
    HeadlineBlock("Legal info")
    SubcopyBlock(
        "Tax ID, business address, and the legal name we put on 1099s. " +
            "Design ships this step in a follow-on; the chrome below keeps the violet identity wired.",
    )
    WizardStubPlaceholder(
        icon = PantopusIcon.FileSignature,
        label = "Step 2 — Legal info",
        subcopy = "Designed frames land in the next prompt.",
    )
}
