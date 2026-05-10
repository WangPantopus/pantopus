@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.add_home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
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
import app.pantopus.android.ui.screens.shared.wizard.WIZARD_DISCARD_DIALOG_TAG
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

        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).assertIsEnabled()
    }

    @Test
    fun close_on_dirty_form_shows_discard_confirm() {
        compose.setContent {
            AddHomeWizardScreen(onDismiss = {}, onOpenHomeDashboard = {}, viewModel = makeViewModel())
        }
        compose.onNodeWithTag("addHome_street").performTextInput("412 Elm St")
        compose.onNodeWithTag(WizardShellTags.LEADING).performClick()
        compose.onNodeWithTag(WIZARD_DISCARD_DIALOG_TAG).assertIsDisplayed()
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

        // Step 1 — fill all fields.
        compose.onNodeWithTag("addHome_street").performTextInput("412 Elm St")
        compose.onNodeWithTag("addHome_city").performTextInput("Portland")
        compose.onNodeWithTag("addHome_state").performTextInput("OR")
        compose.onNodeWithTag("addHome_zip").performTextInput("97214")
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()

        // Step 2 — confirm. Continue.
        compose.waitForIdle()
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()

        // Step 3 — pick Owner.
        compose.waitForIdle()
        compose.onNodeWithTag("addHome_role_owner").performClick()
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()

        // Step 4 — submit.
        compose.waitForIdle()
        compose.onNodeWithTag(WizardShellTags.PRIMARY_CTA).performClick()

        compose.waitForIdle()
        compose.onNodeWithTag(WIZARD_SUCCESS_HERO_TAG).assertIsDisplayed()
    }
}
