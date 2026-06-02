@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.compose.gig

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.gigs.CreateGigBody
import app.pantopus.android.data.api.models.gigs.CreateGigResponse
import app.pantopus.android.data.api.models.gigs.GigDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.gigs.GigsRepository
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
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class GigComposeViewModelTest {
    private val repo: GigsRepository = mockk(relaxed = true)
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
        GigComposeViewModel(repo, savedStateHandle, networkMonitor)

    private val createGigResponse =
        CreateGigResponse(
            gig =
                GigDto(
                    id = "gig_42",
                    title = "Hang 3 shelves",
                    description = "Need three IKEA Lack shelves mounted on drywall.",
                    price = 60.0,
                    category = "handyman",
                    status = "open",
                    createdAt = "2025-01-01T00:00:00Z",
                ),
            message = "ok",
        )

    private fun seedReviewReady(vm: GigComposeViewModel) {
        vm.selectCategory(GigComposeCategory.Handyman)
        vm.setTitle("Hang 3 shelves in the living room")
        vm.setDescription("Need three IKEA Lack shelves mounted on drywall. Studs marked.")
        vm.selectBudgetType(GigComposeBudgetType.Fixed)
        vm.setBudgetMin("60")
        vm.selectScheduleType(GigComposeScheduleType.OneTime)
        vm.setScheduledStart(Instant.now().plusSeconds(86_400).toString())
        vm.selectLocationMode(GigComposeLocationMode.YourAddress)
    }

    /** Seed a VM that's already parked on the Review step. */
    private fun makeVmAtReview(): GigComposeViewModel {
        val isoFuture = Instant.now().plusSeconds(86_400).toString()
        val handle =
            SavedStateHandle(
                mapOf(
                    "composeGig.step" to GigComposeStep.Review.ordinal0,
                    "composeGig.category" to "Handyman",
                    "composeGig.title" to "Hang 3 shelves in the living room",
                    "composeGig.description" to "Need three IKEA Lack shelves mounted on drywall. Studs marked.",
                    "composeGig.budgetType" to "Fixed",
                    "composeGig.budgetMin" to "60",
                    "composeGig.scheduleType" to "OneTime",
                    "composeGig.scheduledStart" to isoFuture,
                    "composeGig.locationMode" to "YourAddress",
                ),
            )
        return GigComposeViewModel(repo, handle, networkMonitor)
    }

    // MARK: - Initial chrome

    @Test
    fun initial_chrome_reflects_category_step() {
        val vm = makeVm()
        val chrome = vm.chrome
        assertEquals("Post a task", chrome.title)
        assertEquals("Continue", chrome.primaryCtaLabel)
        assertFalse(
            "Continue must be disabled until a category is selected.",
            chrome.primaryCtaEnabled,
        )
        assertEquals(WizardLeadingControl.Close, chrome.leading)
        assertEquals(
            WizardProgressLabel.StepOf(current = 1, total = 6),
            chrome.progressLabel,
        )
        assertTrue(chrome.showsProgressBar)
    }

    @Test
    fun selecting_category_enables_continue() {
        // B.3 — category selection enables Continue in the manual picker.
        val vm = makeVm()
        vm.setComposeMode(ComposeMode.Manual)
        vm.selectCategory(GigComposeCategory.Handyman)
        assertTrue(vm.chrome.primaryCtaEnabled)
    }

    @Test
    fun preselect_from_route_argument() {
        val vm = makeVm()
        vm.preselectCategoryIfNeeded(GigComposeCategory.Cleaning)
        assertEquals(GigComposeCategory.Cleaning, vm.state.value.form.category)
    }

    @Test
    fun category_from_raw_key_parses_values() {
        assertEquals(GigComposeCategory.Handyman, GigComposeCategory.fromRawKey("handyman"))
        assertEquals(GigComposeCategory.PetCare, GigComposeCategory.fromRawKey("petcare"))
        assertNull(GigComposeCategory.fromRawKey(null))
        assertNull(GigComposeCategory.fromRawKey(""))
        assertNull(GigComposeCategory.fromRawKey("all"))
        assertNull(GigComposeCategory.fromRawKey("nope"))
    }

    // MARK: - Basics validation

    @Test
    fun basics_step_requires_lengths() =
        runTest {
            val vm = makeVm()
            vm.selectCategory(GigComposeCategory.Handyman)
            vm.onPrimary()
            advanceTimeBy(10)
            assertEquals(GigComposeStep.Basics, vm.state.value.form.currentStep)
            assertFalse(vm.chrome.primaryCtaEnabled)
            vm.setTitle("Hang shelves")
            assertFalse(vm.chrome.primaryCtaEnabled)
            vm.setDescription("Long enough description to clear the 20 char floor.")
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    @Test
    fun title_cannot_exceed_max() {
        val vm = makeVm()
        val over = "a".repeat(GigComposeLimits.TITLE_MAX + 50)
        vm.setTitle(over)
        assertEquals(GigComposeLimits.TITLE_MAX, vm.state.value.form.title.length)
    }

    @Test
    fun photo_cap_enforced_on_add() {
        val vm = makeVm()
        repeat(GigComposeLimits.MAX_PHOTOS + 3) { vm.addPlaceholderPhoto() }
        assertEquals(GigComposeLimits.MAX_PHOTOS, vm.state.value.form.photoIds.size)
    }

    // MARK: - Budget validation

    @Test
    fun budget_offers_enables_continue_without_numbers() {
        val vm = makeVm()
        seedReviewReady(vm)
        vm.selectBudgetType(GigComposeBudgetType.Offers)
        assertNotNull(
            "Open-to-bids must not require a numeric min.",
            vm.buildCreateBody(),
        )
    }

    @Test
    fun budget_fixed_requires_positive_min() {
        val vm = makeVm()
        seedReviewReady(vm)
        vm.selectBudgetType(GigComposeBudgetType.Fixed)
        vm.setBudgetMin("0")
        assertNull(vm.buildCreateBody())
        vm.setBudgetMin("25")
        assertNotNull(vm.buildCreateBody())
    }

    @Test
    fun budget_sanitizer_strips_non_digits() {
        val vm = makeVm()
        vm.setBudgetMin("\$1,234.50abc")
        assertEquals("1234.50", vm.state.value.form.budgetMin)
        vm.setBudgetMin("12.3.4")
        assertEquals("12.34", vm.state.value.form.budgetMin)
    }

    // MARK: - Schedule validation

    @Test
    fun schedule_one_time_requires_future_date() {
        val vm = makeVm()
        seedReviewReady(vm)
        vm.selectScheduleType(GigComposeScheduleType.OneTime)
        vm.setScheduledStart(Instant.now().minusSeconds(60).toString())
        assertNull(vm.buildCreateBody())
        vm.setScheduledStart(Instant.now().plusSeconds(3600).toString())
        assertNotNull(vm.buildCreateBody())
    }

    @Test
    fun selecting_non_one_time_clears_date() {
        val vm = makeVm()
        seedReviewReady(vm)
        vm.selectScheduleType(GigComposeScheduleType.Flexible)
        assertNull(vm.state.value.form.scheduledStartISO)
    }

    // MARK: - Location validation

    @Test
    fun location_a_place_requires_complete_address() {
        val vm = makeVm()
        seedReviewReady(vm)
        vm.selectLocationMode(GigComposeLocationMode.APlace)
        assertNull(vm.buildCreateBody())
        vm.updatePlaceAddress(line1 = "123 Main St", city = "Portland", state = "OR", zip = "97214")
        assertNotNull(vm.buildCreateBody())
    }

    @Test
    fun virtual_maps_to_remote_task_format() {
        val vm = makeVm()
        seedReviewReady(vm)
        vm.selectLocationMode(GigComposeLocationMode.Virtual)
        val body = vm.buildCreateBody()
        assertEquals("remote", body?.taskFormat)
        assertEquals("custom", body?.location?.mode)
    }

    @Test
    fun recurring_maps_to_flexible_wire_value() {
        val vm = makeVm()
        seedReviewReady(vm)
        vm.selectScheduleType(GigComposeScheduleType.Recurring)
        val body = vm.buildCreateBody()
        assertEquals("flexible", body?.scheduleType)
    }

    // MARK: - Forward / back

    @Test
    fun forward_advances_through_steps() =
        runTest {
            val vm = makeVm()
            vm.selectCategory(GigComposeCategory.Handyman)
            vm.onPrimary()
            advanceTimeBy(10)
            assertEquals(GigComposeStep.Basics, vm.state.value.form.currentStep)
            vm.setTitle("Hang 3 shelves")
            vm.setDescription("Need three IKEA Lack shelves mounted on drywall.")
            vm.onPrimary()
            advanceTimeBy(10)
            assertEquals(GigComposeStep.Budget, vm.state.value.form.currentStep)
        }

    @Test
    fun back_preserves_data() =
        runTest {
            val vm = makeVm()
            vm.selectCategory(GigComposeCategory.Handyman)
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onLeading()
            assertEquals(GigComposeStep.Category, vm.state.value.form.currentStep)
            assertEquals(
                "Going back must not stomp the user's category pick.",
                GigComposeCategory.Handyman,
                vm.state.value.form.category,
            )
        }

    // MARK: - Submit

    @Test
    fun submit_advances_to_success_and_records_gig_id() =
        runTest {
            coEvery { repo.create(any<CreateGigBody>()) } returns NetworkResult.Success(createGigResponse)
            val vm = makeVmAtReview()
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(GigComposeStep.Success, vm.state.value.form.currentStep)
            assertEquals("gig_42", vm.state.value.createdGigId)
            assertEquals("View task", vm.chrome.primaryCtaLabel)
            assertEquals("composeGigDone", vm.chrome.secondaryCta?.testTag)
            assertFalse(vm.chrome.showsProgressBar)
        }

    @Test
    fun submit_error_keeps_user_on_review() =
        runTest {
            coEvery { repo.create(any<CreateGigBody>()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "down"))
            val vm = makeVmAtReview()
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(GigComposeStep.Review, vm.state.value.form.currentStep)
            assertNotNull(vm.state.value.errorMessage)
        }

    @Test
    fun success_primary_fires_open_gig_detail_event() =
        runTest {
            coEvery { repo.create(any<CreateGigBody>()) } returns NetworkResult.Success(createGigResponse)
            val vm = makeVmAtReview()
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onPrimary()
            advanceTimeBy(10)
            val event = vm.pendingEvent.value
            assertTrue(event is GigComposeOutboundEvent.OpenGigDetail)
            assertEquals("gig_42", (event as GigComposeOutboundEvent.OpenGigDetail).gigId)
        }

    @Test
    fun success_secondary_fires_dismiss_event() =
        runTest {
            coEvery { repo.create(any<CreateGigBody>()) } returns NetworkResult.Success(createGigResponse)
            val vm = makeVmAtReview()
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onSecondary()
            assertEquals(GigComposeOutboundEvent.Dismiss, vm.pendingEvent.value)
        }

    // MARK: - Close-confirm

    @Test
    fun close_on_empty_step1_is_clean() {
        val vm = makeVm()
        assertFalse(vm.chrome.dirty)
    }

    @Test
    fun close_after_selecting_category_is_dirty() {
        val vm = makeVm()
        vm.selectCategory(GigComposeCategory.Handyman)
        assertTrue(vm.chrome.dirty)
    }

    // MARK: - Saved-state restore

    @Test
    fun saved_state_handle_restores_form_on_construct() {
        val handle =
            SavedStateHandle(
                mapOf(
                    "composeGig.step" to GigComposeStep.Budget.ordinal0,
                    "composeGig.category" to "Cleaning",
                    "composeGig.title" to "Deep clean",
                    "composeGig.description" to "Deep clean a 2BR apartment before move-out day.",
                    "composeGig.budgetType" to "Fixed",
                    "composeGig.budgetMin" to "180",
                ),
            )
        val vm = GigComposeViewModel(repo, handle, networkMonitor)
        assertEquals(GigComposeStep.Budget, vm.state.value.form.currentStep)
        assertEquals(GigComposeCategory.Cleaning, vm.state.value.form.category)
        assertEquals("Deep clean", vm.state.value.form.title)
        assertEquals(GigComposeBudgetType.Fixed, vm.state.value.form.budgetType)
    }

    // MARK: - E.1 Composer picker sheets

    @Test
    fun build_body_carries_picker_sheet_fields() {
        val vm = makeVm()
        seedReviewReady(vm)
        val deadline = Instant.now().plusSeconds(172_800).toString()
        vm.setDeadline(deadline)
        vm.setCancellationPolicy(GigCancellationPolicy.Moderate)
        vm.setUrgent(true)
        vm.addTag("#Heavy Lifting")
        vm.addTag("weekend")
        val body = vm.buildCreateBody()
        assertNotNull(body)
        assertEquals(deadline, body?.deadline)
        assertEquals("standard", body?.cancellationPolicy)
        assertEquals(true, body?.isUrgent)
        assertEquals(listOf("heavy-lifting", "weekend"), body?.tags)
    }

    @Test
    fun build_body_omits_picker_fields_when_unset() {
        val vm = makeVm()
        seedReviewReady(vm)
        val body = vm.buildCreateBody()
        assertNotNull(body)
        assertNull(body?.deadline)
        assertNull(body?.cancellationPolicy)
        assertNull("is_urgent is omitted when the boost is off.", body?.isUrgent)
        assertNull(body?.tags)
    }

    @Test
    fun cancellation_policy_wire_values() {
        assertEquals("flexible", GigCancellationPolicy.Flexible.wireValue)
        assertEquals("standard", GigCancellationPolicy.Moderate.wireValue)
        assertEquals("strict", GigCancellationPolicy.Strict.wireValue)
    }

    @Test
    fun normalize_tag_strips_hash_lowercases_and_hyphenates() {
        assertEquals("heavy-lifting", GigComposeViewModel.normalizeTag("#Heavy Lifting"))
        assertEquals("truck-needed", GigComposeViewModel.normalizeTag("  Truck  Needed "))
        assertNull(GigComposeViewModel.normalizeTag("   "))
        assertNull(GigComposeViewModel.normalizeTag("#"))
    }

    @Test
    fun add_tag_caps_at_max_and_dedupes() {
        val vm = makeVm()
        repeat(GigComposeLimits.MAX_TAGS + 3) { vm.addTag("tag$it") }
        assertEquals(GigComposeLimits.MAX_TAGS, vm.state.value.form.tags.size)
        val before = vm.state.value.form.tags
        vm.addTag("#TAG0")
        assertEquals("Duplicate (normalised) tags are ignored.", before, vm.state.value.form.tags)
    }

    @Test
    fun toggle_tag_adds_then_removes() {
        val vm = makeVm()
        vm.toggleTag("#furniture")
        assertEquals(listOf("furniture"), vm.state.value.form.tags)
        vm.toggleTag("#furniture")
        assertTrue(vm.state.value.form.tags.isEmpty())
    }

    @Test
    fun present_and_dismiss_picker() {
        val vm = makeVm()
        assertNull(vm.state.value.activeSheet)
        vm.presentPicker(GigPickerSheet.Tags)
        assertEquals(GigPickerSheet.Tags, vm.state.value.activeSheet)
        vm.dismissPicker()
        assertNull(vm.state.value.activeSheet)
    }

    @Test
    fun urgency_counts_toward_dirty() {
        val vm = makeVm()
        assertFalse(vm.chrome.dirty)
        vm.setUrgent(true)
        assertTrue("Setting urgent must trigger the discard confirm.", vm.chrome.dirty)
    }
}
