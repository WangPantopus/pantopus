package app.pantopus.android.ui.screens.place

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.pantopus.android.ui.components.DrawerMenuButton
import app.pantopus.android.ui.components.EmptyState
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.Spacing

/**
 * The Home tab's add-a-place empty state, shown to residents who haven't
 * claimed an address yet (it replaces the retired Hub launcher as the
 * no-home landing). Carries the same top-left menu button as Your Place so
 * the global drawer stays reachable. Parity twin of iOS `HomeNoPlaceView`.
 */
@Composable
fun HomeNoPlaceScreen(
    onAddHome: () -> Unit,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PantopusColors.appBg)
                .testTag("homeNoPlace"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.s4, vertical = Spacing.s2),
        ) {
            DrawerMenuButton(onClick = onOpenMenu)
        }
        EmptyState(
            icon = PantopusIcon.Home,
            headline = "Add your place",
            subcopy =
                "Claim your address to unlock Your Place — your neighborhood pulse, " +
                    "verified neighbors, and home tools.",
            ctaTitle = "Add a place",
            onCta = onAddHome,
            modifier = Modifier.weight(1f),
        )
    }
}
