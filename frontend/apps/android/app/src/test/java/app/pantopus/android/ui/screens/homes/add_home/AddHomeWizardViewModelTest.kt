@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.homes.add_home

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import app.pantopus.android.data.api.models.homes.CheckAddressRequest
import app.pantopus.android.data.api.models.homes.CheckAddressResponse
import app.pantopus.android.data.api.models.homes.CreateHomeRequest
import app.pantopus.android.data.api.models.homes.CreateHomeResponse
import app.pantopus.android.data.api.models.homes.HomeDto
import app.pantopus.android.data.api.models.homes.PropertySuggestionsRequest
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddHomeWizardViewModelTest {
    private val repo: HomesRepository = mockk(relaxed = true)
    private val networkMonitor: NetworkMonitor =
        mockk<NetworkMonitor>(relaxed = true).also {
            every { it.isOnline } returns MutableStateFlow(true)
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        AddHomeWizardViewModel(repo, savedStateHandle, networkMonitor)

    private fun fillAddress(vm: AddHomeWizardViewModel) {
        vm.updateField(AddressField.Street, "412 Elm St")
        vm.updateField(AddressField.City, "Portland")
        vm.updateField(AddressField.State, "OR")
        vm.updateField(AddressField.Zip, "97214")
    }

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

    private val checkAddressOk =
        CheckAddressResponse(
            exists = false,
            homeCount = 0,
            hasVerifiedMembers = false,
            verdictStatus = null,
        )

    // MARK: - Initial chrome

    @Test
    fun initial_chrome_reflects_address_step() {
        val vm = makeVm()
        val chrome = vm.chrome
        assertEquals("Add a home", chrome.title)
        assertEquals("Continue", chrome.primaryCtaLabel)
        assertFalse(
            "Continue must be disabled until the address fields are filled.",
            chrome.primaryCtaEnabled,
        )
        assertEquals(WizardLeadingControl.Close, chrome.leading)
        assertEquals(
            WizardProgressLabel.StepOf(current = 1, total = 4),
            chrome.progressLabel,
        )
    }

    @Test
    fun filled_form_enables_continue() {
        val vm = makeVm()
        fillAddress(vm)
        assertTrue(vm.chrome.primaryCtaEnabled)
    }

    // MARK: - Address → Confirm

    @Test
    fun primary_advances_and_fires_check_address() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Success(checkAddressOk)
            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            // Allow advance() coroutine + check-address to flush.
            advanceTimeBy(50)
            assertEquals(AddHomeStep.Confirm, vm.state.value.form.currentStep)
            assertNotNull(vm.state.value.addressCheck)
            assertEquals(WizardLeadingControl.Back, vm.chrome.leading)
        }

    @Test
    fun check_address_error_surfaces_message() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "down"))
            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(AddHomeStep.Confirm, vm.state.value.form.currentStep)
            assertNull(vm.state.value.addressCheck)
            assertNotNull(vm.state.value.errorMessage)
        }

    // MARK: - Back navigation

    @Test
    fun back_on_confirm_returns_to_address() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Success(checkAddressOk)
            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onLeading()
            assertEquals(AddHomeStep.Address, vm.state.value.form.currentStep)
        }

    // MARK: - Role gating

    @Test
    fun role_step_requires_selection() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Success(checkAddressOk)
            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            // Confirm → Role
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(AddHomeStep.Role, vm.state.value.form.currentStep)
            assertFalse(vm.chrome.primaryCtaEnabled)
            vm.selectRole(AddHomeRole.Owner)
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    // MARK: - Submit happy path

    @Test
    fun submit_advances_to_success_and_records_home_id() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Success(checkAddressOk)
            coEvery { repo.create(any<CreateHomeRequest>()) } returns
                NetworkResult.Success(createHomeResponse)

            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            advanceTimeBy(50) // Confirm
            vm.onPrimary()
            advanceTimeBy(50) // Role
            vm.selectRole(AddHomeRole.Owner)
            vm.onPrimary()
            advanceTimeBy(50) // Review
            vm.onPrimary()
            advanceTimeBy(50) // Submit → Success

            assertEquals(AddHomeStep.Success, vm.state.value.form.currentStep)
            assertEquals("home_42", vm.state.value.createdHomeId)
            assertEquals("View home", vm.chrome.primaryCtaLabel)
            assertEquals("addHomeBackToHub", vm.chrome.secondaryCta?.testTag)
            assertFalse(
                "Success step must hide the segmented progress bar.",
                vm.chrome.showsProgressBar,
            )
        }

    @Test
    fun submit_error_keeps_user_on_review() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Success(checkAddressOk)
            coEvery { repo.create(any<CreateHomeRequest>()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "boom"))

            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.selectRole(AddHomeRole.Owner)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onPrimary()
            advanceTimeBy(50)

            assertEquals(AddHomeStep.Review, vm.state.value.form.currentStep)
            assertNotNull(vm.state.value.errorMessage)
        }

    // MARK: - Success step CTAs

    @Test
    fun success_primary_fires_open_dashboard_event() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Success(checkAddressOk)
            coEvery { repo.create(any<CreateHomeRequest>()) } returns
                NetworkResult.Success(createHomeResponse)

            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.selectRole(AddHomeRole.Owner)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onPrimary()
            advanceTimeBy(50)
            // Now on success step.
            vm.pendingEvent.test {
                // Drain the initial null.
                assertNull(awaitItem())
                vm.onPrimary()
                val event = awaitItem()
                assertTrue(event is AddHomeOutboundEvent.OpenHomeDashboard)
                assertEquals("home_42", (event as AddHomeOutboundEvent.OpenHomeDashboard).homeId)
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun success_secondary_fires_dismiss_event() =
        runTest {
            coEvery { repo.checkAddress(any<CheckAddressRequest>()) } returns
                NetworkResult.Success(checkAddressOk)
            coEvery { repo.create(any<CreateHomeRequest>()) } returns
                NetworkResult.Success(createHomeResponse)

            val vm = makeVm()
            fillAddress(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.selectRole(AddHomeRole.Owner)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onSecondary()
            assertEquals(AddHomeOutboundEvent.Dismiss, vm.pendingEvent.value)
        }

    // MARK: - Close-confirm

    @Test
    fun close_on_empty_step1_is_clean() {
        val vm = makeVm()
        assertFalse(vm.chrome.dirty)
    }

    @Test
    fun close_on_filled_step1_is_dirty() {
        val vm = makeVm()
        fillAddress(vm)
        assertTrue(vm.chrome.dirty)
    }

    // MARK: - Suggestions

    @Test
    fun suggestions_debounce_and_populate() =
        runTest {
            coEvery { repo.propertySuggestions(any<PropertySuggestionsRequest>()) } returns
                NetworkResult.Success(
                    mapOf("results" to listOf(mapOf("address" to "412 Elm St"))),
                )
            val vm = makeVm()
            fillAddress(vm)
            // No fetch yet — debounce hasn't elapsed.
            assertEquals(0, vm.state.value.suggestions.size)
            advanceTimeBy(AddHomeWizardViewModel.SUGGESTION_DEBOUNCE_MS + 50)
            assertTrue(
                vm.state.value.suggestions
                    .isNotEmpty(),
            )
        }

    @Test
    fun select_suggestion_populates_street() {
        val vm = makeVm()
        vm.selectSuggestion("100 Test Ave")
        assertEquals("100 Test Ave", vm.state.value.form.address.street)
    }

    // MARK: - Saved-state restore

    @Test
    fun saved_state_handle_restores_form_on_construct() {
        val handle =
            SavedStateHandle(
                mapOf(
                    "addHome.step" to AddHomeStep.Role.ordinal0,
                    "addHome.street" to "412 Elm St",
                    "addHome.unit" to "",
                    "addHome.city" to "Portland",
                    "addHome.state" to "OR",
                    "addHome.zip" to "97214",
                    "addHome.primary" to true,
                    "addHome.role" to "Tenant",
                ),
            )
        val vm = AddHomeWizardViewModel(repo, handle)
        assertEquals(AddHomeStep.Role, vm.state.value.form.currentStep)
        assertEquals("412 Elm St", vm.state.value.form.address.street)
        assertEquals(AddHomeRole.Tenant, vm.state.value.form.role)
    }

    // MARK: - Suggestion parser

    @Test
    fun flatten_suggestions_handles_nested_payload() {
        val payload: Map<String, Any?> =
            mapOf(
                "results" to
                    listOf(
                        mapOf("address" to "100 Main St"),
                        mapOf("nested" to mapOf("address" to "200 Oak St")),
                    ),
            )
        val flat = AddHomeWizardViewModel.flattenSuggestions(payload)
        assertEquals(listOf("100 Main St", "200 Oak St"), flat)
    }
}
