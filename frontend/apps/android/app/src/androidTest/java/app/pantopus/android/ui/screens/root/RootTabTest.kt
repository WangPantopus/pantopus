package app.pantopus.android.ui.screens.root

import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import app.pantopus.android.ui.screens.hub.HUB_HEADING_TAG
import app.pantopus.android.ui.screens.inbox.InboxScreen
import app.pantopus.android.ui.screens.nearby.NearbyScreen
import org.junit.Rule
import org.junit.Test

/**
 * Drives the root tab scaffold without Hilt by hosting the bottom bar
 * directly. Verifies that:
 * - Hub is the default selection.
 * - Each un-designed tab renders the NotYetAvailable empty state.
 * - Re-selecting Hub returns to the Hub heading.
 */
class RootTabTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun hub_is_default_and_tabs_switch() {
        composeRule.setContent {
            var selected by remember { mutableStateOf<PantopusRoute>(PantopusRoute.Hub) }
            Scaffold(
                modifier = Modifier.testTag("rootScaffold"),
                bottomBar = {
                    PantopusBottomBar(
                        selected = selected,
                        onSelect = { selected = it },
                    )
                },
            ) { padding ->
                androidx.compose.foundation.layout.Box(Modifier.padding(padding)) {
                    when (selected) {
                        PantopusRoute.Hub -> app.pantopus.android.ui.screens.hub.HubScreen()
                        PantopusRoute.Nearby -> NearbyScreen()
                        PantopusRoute.Inbox -> InboxScreen()
                        PantopusRoute.You ->
                            NotYetAvailableView(
                                tabName = "You",
                                icon = app.pantopus.android.ui.theme.PantopusIcon.User,
                            )
                    }
                }
            }
        }

        // Default is Hub.
        composeRule.onNodeWithTag(HUB_HEADING_TAG).assertIsDisplayed()

        // Nearby tab renders the empty-state heading.
        composeRule.onNodeWithTag("tab.nearby").performClick()
        composeRule.onNodeWithTag(NOT_YET_AVAILABLE_TAG).assertIsDisplayed()

        // Inbox tab renders its empty-state heading.
        composeRule.onNodeWithTag("tab.inbox").performClick()
        composeRule.onNodeWithTag(NOT_YET_AVAILABLE_TAG).assertIsDisplayed()

        // Back to Hub.
        composeRule.onNodeWithTag("tab.hub").performClick()
        composeRule.onNodeWithTag(HUB_HEADING_TAG).assertIsDisplayed()
    }
}
