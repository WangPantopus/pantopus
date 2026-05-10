@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.add_home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.homes.CheckAddressRequest
import app.pantopus.android.data.api.models.homes.CheckAddressResponse
import app.pantopus.android.data.api.models.homes.CreateHomeRequest
import app.pantopus.android.data.api.models.homes.CreateHomeResponse
import app.pantopus.android.data.api.models.homes.HomeDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.ui.screens.shared.wizard.WizardShellTags
import app.pantopus.android.ui.screens.shared.wizard.blocks.WIZARD_SUCCESS_HERO_TAG
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test

class AddHomeWizardScreenTest {
    @get:Rule val compose = createComposeRule()

    private val repo: HomesRepository = mockk(relaxed = true)

    private val checkAddressOk =
        CheckAddressResponse(
            exists = false,
            homeCount = 0,
            hasVerifiedMembers = false,
            verdictStatus = null,
        )

    private val createHomeResponse =
        CreateHomeResponse(
            message = "ok",
            home =
                HomeDto(
                    id = "home_42",
                    name = "412 Elm St",
                    address = "412 Elm St",
                    city = "Portland",
                    state = "OR",
                    zipcode = "97214",
                    homeType = null,
                    visibility = "public",
                    description = null,
                    createdAt = "2025-01-01T00:00:00Z",
                    updatedAt = "2025-01-01T00:00:00Z",
                ),
            requiresVerification = false,
            verificationType = null,
            role = "owner",
        )

    private fun makeViewModel(): AddHomeWizardViewModel {
        coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns NetworkResult.Success(checkAddressOk)
        coEvery { repo.create(any<CreateHomeRequest>()) } returns NetworkResult.Success(createHomeResponse)
        val networkMonitor =
            mockk<NetworkMonitor>(relaxed = true).also {
                every { it.isOnline } returns MutableStateFlow(true)
            }
        return AddHomeWizardViewModel(repo, SavedStateHandle(), networkMonitor)
    }

    @Test
    fun continue_button_is_disabled_until_address_is_complete() {
        compose.setContent {
            AddHomeWizardScreen(onDismiss = {}, onOpenHomeDashboard = {}, viewModel = makeViewModel())
        }
        compose
            .onNodeWithTag(WizardShellTags.PRIMARY_CTA)
            .assertIsDisplayed()
            .assertIsNotEnabled()
    }

    @Test
    fun filling_all_fields_enables_continue() {
        compose.setContent {
            AddHomeWizardScreen(onDismiss = {}, onOpenHomeDashboard = {}, viewModel = makeViewModel())
        }

        compose.onNodeWithTag("addHome_street").performTextInput("412 Elm St")
        compose.onNodeWithTag("addHome_city").performTextInput("Portland")
        compose.onNodeWithTag("addHome_state").performTextInput("OR")
        compose.onNodeWithTag("addHome_zip").performTextInput("97214")

        // updateField → _state.update → recompose → primaryEnabled isn't
        // synchronous from the test's POV. Poll until the CTA flips enabled.
        compose.waitUntil(timeoutMillis = 2_000) {
            runCatching { compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).assertIsEnabled() }.isSuccess
        }
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).assertIsEnabled()
    }

    @Test
    fun close_on_dirty_form_shows_discard_confirm() {
        compose.setContent {
            AddHomeWizardScreen(onDismiss = {}, onOpenHomeDashboard = {}, viewModel = makeViewModel())
        }
        compose.onNodeWithTag("addHome_street").performTextInput("412 Elm St")
        compose.onNodeWithTag(WizardShellTags.LEADING).performClick()
        // Material 3 AlertDialog renders inside its own Popup window; the
        // `Modifier.testTag(...)` parameter only tags the dialog anchor,
        // not the visible surface. Finding by title text is more reliable
        // than the WIZARD_DISCARD_DIALOG_TAG constant.
        compose.waitUntil(timeoutMillis = 2_000) {
            compose.onAllNodesWithText("Discard your progress?").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Discard your progress?").assertIsDisplayed()
        compose.onNodeWithText("Keep going").performClick()
        compose.onNodeWithTag(WizardShellTags.SHELL).assertIsDisplayed()
    }

    @Test
    fun close_on_empty_form_dismisses_immediately() {
        var dismissed = false
        compose.setContent {
            AddHomeWizardScreen(
                onDismiss = { dismissed = true },
                onOpenHomeDashboard = {},
                viewModel = makeViewModel(),
            )
        }
        compose.onNodeWithTag(WizardShellTags.LEADING).performClick()
        compose.waitForIdle()
        assert(dismissed) { "Empty step 1 must dismiss without prompting." }
    }

    @Test
    fun happy_path_reaches_success_step() {
        compose.setContent {
            AddHomeWizardScreen(onDismiss = {}, onOpenHomeDashboard = {}, viewModel = makeViewModel())
        }

        // Step 1 — fill all fields, wait for Continue to enable, advance.
        compose.onNodeWithTag("addHome_street").performTextInput("412 Elm St")
        compose.onNodeWithTag("addHome_city").performTextInput("Portland")
        compose.onNodeWithTag("addHome_state").performTextInput("OR")
        compose.onNodeWithTag("addHome_zip").performTextInput("97214")
        waitUntilTagPresent("addHome_street") // ensure last update flushed
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()

        // Step 2 — runCheckAddress is a suspending coroutine; waitForIdle
        // returns before it finishes. Poll for the role-step tile that
        // only appears AFTER advance() lands on .role.
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()
        waitUntilTagPresent("addHome_role_owner", timeoutMillis = 5_000)

        // Step 3 — pick Owner, advance.
        compose.onNodeWithTag("addHome_role_owner").performClick()
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()

        // Step 4 — submit. Wait for success hero.
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()
        waitUntilTagPresent(WIZARD_SUCCESS_HERO_TAG, timeoutMillis = 5_000)
        compose.onNodeWithTag(WIZARD_SUCCESS_HERO_TAG).assertIsDisplayed()
    }

    private fun waitUntilTagPresent(
        tag: String,
        timeoutMillis: Long = 2_000,
    ) {
        compose.waitUntil(timeoutMillis = timeoutMillis) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }
}
