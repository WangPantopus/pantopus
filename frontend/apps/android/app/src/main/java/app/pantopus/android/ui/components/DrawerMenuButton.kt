package app.pantopus.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon
import app.pantopus.android.ui.theme.PantopusIconImage

/**
 * The shared top-left hamburger that opens the global navigation drawer.
 * Every primary tab (Your Place / Pulse / Tasks / Marketplace / Messages)
 * renders this at the leading edge of its top bar, so the side menu is
 * reachable from anywhere. Carries the cross-platform `navMenuButton` tag
 * (mirrored on iOS as `.accessibilityIdentifier("navMenuButton")`).
 */
@Composable
fun DrawerMenuButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .testTag("navMenuButton")
                .semantics { contentDescription = "Open menu" },
        contentAlignment = Alignment.Center,
    ) {
        PantopusIconImage(
            icon = PantopusIcon.Menu,
            contentDescription = null,
            size = 22.dp,
            tint = PantopusColors.appText,
        )
    }
}
