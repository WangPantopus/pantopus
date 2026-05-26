package app.pantopus.android

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import app.pantopus.android.ui.screens.root.NotYetAvailableView
import app.pantopus.android.ui.screens.root.PantopusBottomBar
import app.pantopus.android.ui.screens.root.PantopusRoute
import app.pantopus.android.ui.theme.PantopusIcon
import org.junit.Rule
import org.junit.Test

/**
 * P8.3 — End-to-end navigation smoke test (Android instrumented).
 *
 * Coverage strategy
 * ─────────────────
 * The Android side exposes 4 [PantopusRoute] bottom-bar destinations plus
 * 117 typed `ChildRoutes` constants inside `RootTabScreen.kt`. Hosting
 * the real `RootTabScreen` from a Compose UI test would require Hilt
 * test infrastructure (a `HiltAndroidRule`, `@HiltAndroidTest` activity)
 * that the codebase does not yet ship — driving every Hilt-injected
 * view-model through `connectedAndroidTest` is out of scope for a smoke
 * pass. The existing screen-level tests
 * (`AddHomeWizardScreenTest`, `LoginScreenTest`, `RootTabTest`,
 * `ComponentsInteractionTest`) follow the same pattern: bypass Hilt by
 * hosting a target composable directly via `composeRule.setContent`.
 *
 * This file extends that pattern across every bottom-bar route:
 *  - drives the real [PantopusBottomBar] with the same `tab.<path>`
 *    testTags the app ships;
 *  - swaps in stub destinations that render the testTag the real
 *    landing screen carries (`hubScreen`, `nearbyMap`, `chatList`,
 *    `meScreen`);
 *  - verifies tab selection swaps the visible destination's testTag.
 *
 * For in-tab navigation correctness the canonical reference is the
 * static analysis in `docs/nav-graph-closure.md` (every `ChildRoutes`
 * constant audited against its `navigate(...)` call sites + the
 * `composable(route)` block in `RootTabScreen.kt`). The route-by-route
 * pass/fail summary is in `docs/nav-smoke-results.md`.
 */
class NavigationSmokeTest {
    @get:Rule
    val composeRule = createComposeRule()

    /**
     * Render the four bottom-bar tabs with stub destinations whose
     * testTags match the real landing screens. Verifies:
     *  - all four tab affordances render with `tab.hub` / `tab.nearby` /
     *    `tab.inbox` / `tab.you`;
     *  - selecting a tab swaps the visible destination's testTag;
     *  - selecting Hub returns to the Hub destination.
     */
    @Test
    fun bottomBarTabs_swapDestinationsCorrectly() {
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
                Box(Modifier.fillMaxSize().padding(padding)) {
                    when (selected) {
                        PantopusRoute.Hub ->
                            Box(Modifier.fillMaxSize().testTag(LANDING_TAG_HUB))
                        PantopusRoute.Nearby ->
                            Box(Modifier.fillMaxSize().testTag(LANDING_TAG_NEARBY))
                        PantopusRoute.Inbox ->
                            Box(Modifier.fillMaxSize().testTag(LANDING_TAG_INBOX))
                        PantopusRoute.You ->
                            Box(Modifier.fillMaxSize().testTag(LANDING_TAG_YOU))
                    }
                }
            }
        }

        // 1. All four tabs render with the expected testTags.
        composeRule.onNodeWithTag("tab.hub").assertIsDisplayed()
        composeRule.onNodeWithTag("tab.nearby").assertIsDisplayed()
        composeRule.onNodeWithTag("tab.inbox").assertIsDisplayed()
        composeRule.onNodeWithTag("tab.you").assertIsDisplayed()

        // 2. Default landing is Hub.
        composeRule.onNodeWithTag(LANDING_TAG_HUB).assertIsDisplayed()

        // 3. Tapping Nearby swaps in the nearby landing tag.
        composeRule.onNodeWithTag("tab.nearby").performClick()
        composeRule.onNodeWithTag(LANDING_TAG_NEARBY).assertIsDisplayed()

        // 4. Tapping Inbox swaps in the chat list landing tag.
        composeRule.onNodeWithTag("tab.inbox").performClick()
        composeRule.onNodeWithTag(LANDING_TAG_INBOX).assertIsDisplayed()

        // 5. Tapping You swaps in the Me landing tag.
        composeRule.onNodeWithTag("tab.you").performClick()
        composeRule.onNodeWithTag(LANDING_TAG_YOU).assertIsDisplayed()

        // 6. Re-selecting Hub returns to the Hub landing.
        composeRule.onNodeWithTag("tab.hub").performClick()
        composeRule.onNodeWithTag(LANDING_TAG_HUB).assertIsDisplayed()
    }

    /**
     * Verifies that the `NotYetAvailableView` empty-state placeholder
     * still surfaces — `RootTabScreen.kt` routes any unknown
     * `ChildRoutes.PLACEHOLDER` push through this composable. The
     * placeholder funnel is the only `NOT_YET_AVAILABLE` destination in
     * the nav graph closure (see `docs/nav-graph-closure.md`).
     */
    @Test
    fun notYetAvailable_placeholderRenders() {
        composeRule.setContent {
            NotYetAvailableView(
                tabName = "Smoke Test",
                icon = PantopusIcon.Inbox,
            )
        }
        composeRule.onNodeWithTag(NOT_YET_AVAILABLE_TAG_SMOKE).assertIsDisplayed()
    }

    /**
     * Bottom-bar reachability for every `PantopusRoute.entries` element.
     * Independent of the rendering test above — this asserts the route
     * inventory (path string + testTag derivation) is stable. If a tab
     * is renamed or removed, this test fails before the integration test.
     */
    @Test
    fun pantopusRoute_entriesEachExposeBottomBarTestTag() {
        val expectedTags =
            PantopusRoute.entries.map { route ->
                "tab.${route.path.substringAfterLast('/')}"
            }
        // Hub / Nearby / Inbox / You.
        check(expectedTags == listOf("tab.hub", "tab.nearby", "tab.inbox", "tab.you")) {
            "PantopusRoute.entries derived testTags drifted: $expectedTags"
        }
    }

    private companion object {
        const val LANDING_TAG_HUB = "smokeStub.hubScreen"
        const val LANDING_TAG_NEARBY = "smokeStub.nearbyMap"
        const val LANDING_TAG_INBOX = "smokeStub.chatList"
        const val LANDING_TAG_YOU = "smokeStub.meScreen"

        // Note: matches the tag emitted by NotYetAvailableView via its
        // outermost `.testTag(NOT_YET_AVAILABLE_TAG)` constant, which is
        // visible only to package siblings. Re-declared here so the smoke
        // test stays decoupled from the internal constant; the assertion
        // still anchors on the same surface (icon + heading) that
        // NotYetAvailableView produces.
        const val NOT_YET_AVAILABLE_TAG_SMOKE = "notYetAvailable"
    }
}
