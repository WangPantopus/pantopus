package app.pantopus.android.ui.screens.root

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import app.pantopus.android.ui.screens.hub.HUB_SCREEN_TAG
import app.pantopus.android.ui.theme.PantopusIcon
import org.junit.Rule
import org.junit.Test

/**
 * Drives the root tab scaffold without Hilt by hosting the bottom bar
 * directly. Verifies that:
 * - Hub is the default selection.
 * - Switching tabs swaps in each tab's body.
 * - Re-selecting Hub returns to the Hub container.
 *
 * The Hub and Nearby destinations are stubbed with tagged boxes so the test
 * stays free of Hilt — their real screens (HubScreen / NearbyMapScreen) pull
 * HiltViewModels which would require a Hilt-aware test runner. Inbox and You
 * render the Hilt-free NotYetAvailable placeholder.
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
                Box(Modifier.padding(padding)) {
                    when (selected) {
                        PantopusRoute.Hub -> Box(Modifier.fillMaxSize().testTag(HUB_SCREEN_TAG))
                        PantopusRoute.Nearby -> Box(Modifier.fillMaxSize().testTag("nearbyTab.mapList"))
                        PantopusRoute.Inbox ->
                            NotYetAvailableView(
                                tabName = "Inbox",
                                icon = PantopusIcon.Inbox,
                            )
                        PantopusRoute.You ->
                            NotYetAvailableView(
                                tabName = "You",
                                icon = PantopusIcon.User,
                            )
                    }
                }
            }
        }

        // Default is Hub.
        composeRule.onNodeWithTag(HUB_SCREEN_TAG).assertIsDisplayed()

        // Nearby tab renders the Map+List hybrid container.
        composeRule.onNodeWithTag("tab.nearby").performClick()
        composeRule.onNodeWithTag("nearbyTab.mapList").assertIsDisplayed()

        // Inbox tab renders its empty-state heading.
        composeRule.onNodeWithTag("tab.inbox").performClick()
        composeRule.onNodeWithTag(NOT_YET_AVAILABLE_TAG).assertIsDisplayed()

        // Back to Hub.
        composeRule.onNodeWithTag("tab.hub").performClick()
        composeRule.onNodeWithTag(HUB_SCREEN_TAG).assertIsDisplayed()
    }
}
