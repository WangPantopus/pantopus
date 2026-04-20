@file:Suppress("UnusedPrivateMember")

package app.pantopus.android.ui.screens.inbox

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.pantopus.android.ui.screens.root.NotYetAvailableView
import app.pantopus.android.ui.theme.PantopusColors
import app.pantopus.android.ui.theme.PantopusIcon

/** Placeholder body for the Inbox tab. Real mailbox UI lands in Prompt P8. */
@Composable
fun InboxScreen() {
    NotYetAvailableView(
        tabName = "Inbox",
        icon = PantopusIcon.Inbox,
        accent = PantopusColors.businessBg,
        foreground = PantopusColors.business,
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun InboxScreenPreview() {
    InboxScreen()
}
