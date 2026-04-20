@file:Suppress("UnusedPrivateMember")

package app.pantopus.android.ui.screens.nearby

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pantopus.android.ui.screens.root.NotYetAvailableView
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon

/** Placeholder body for the Nearby tab. Real surface lands later. */
@Composable
fun NearbyScreen() {
    NotYetAvailableView(
        tabName = "Nearby",
        icon = PantopusIcon.Map,
        accent = PantopusColors.homeBg,
        foreground = PantopusColors.home,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun NearbyScreenPreview() {
    NearbyScreen()
}
