@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.compose.listing

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.listings.CreateListingRequest
import app.pantopus.android.data.api.models.listings.CreateListingResponse
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.listings.ListingsRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class ListingComposeWizardViewModelTest {
    private val repo: ListingsRepository = mockk(relaxed = true)
    private val isOnlineFlow = MutableStateFlow(true)
    private val networkMonitor: NetworkMonitor =
        mockk<NetworkMonitor>(relaxed = true).also {
            every { it.isOnline } returns isOnlineFlow
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        isOnlineFlow.value = true
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ListingComposeWizardViewModel(repo, savedStateHandle, networkMonitor)

    private fun makeEditVm(
        listingId: String = "listing_42",
        jumpToStep: ListingComposeStep? = null,
    ): ListingComposeWizardViewModel {
        val map =
            mutableMapOf<String, Any?>(
                ListingComposeWizardViewModel.EDIT_LISTING_ID_KEY to listingId,
            )
        if (jumpToStep != null) {
            map[ListingComposeWizardViewModel.EDIT_JUMP_TO_STEP_KEY] = jumpToStep.name
        }
        return ListingComposeWizardViewModel(repo, SavedStateHandle(map), networkMonitor)
    }

    private fun editListingDto(
        listingType: String = "sell_item",
        layer: String = "goods",
        isFree: Boolean = false,
        price: Double? = 420.0,
        category: String = "furniture",
        condition: String? = "like_new",
        title: String = "Mid-century walnut credenza",
        description: String = "Solid walnut, four sliding doors, dovetail joinery.",
        locationName: String? = "Lincoln Park bandshell",
        mediaUrls: List<String>? = listOf("https://example.com/a.jpg", "https://example.com/b.jpg"),
    ) = ListingDto(
        id = "listing_42",
        userId = "user_seller",
        title = title,
        description = description,
        price = price,
        isFree = isFree,
        category = category,
        condition = condition,
        mediaUrls = mediaUrls,
        firstImage = mediaUrls?.firstOrNull(),
        layer = layer,
        listingType = listingType,
        locationName = locationName,
        status = "active",
    )

    private val updateResponse =
        app.pantopus.android.data.api.models.listings.UpdateListingResponse(
            message = "Listing updated successfully",
            listing =
                ListingDto(
                    id = "listing_42",
                    title = "Mid-century walnut credenza",
                    category = "furniture",
                    layer = "goods",
                    listingType = "sell_item",
                    isFree = false,
                    price = 399.0,
                    status = "active",
                ),
        )

    private val createResponse =
        CreateListingResponse(
            message = "Listing created successfully",
            listing =
                ListingDto(
                    id = "listing_42",
                    title = "Moving boxes — bundle of 18",
                    category = "goods",
                    layer = "goods",
                    listingType = "sell_item",
                    isFree = false,
                    price = 25.0,
                    status = "active",
                ),
        )

    /** Move the wizard to the final review state with a valid form. */
    private fun seedReadyToSubmit(vm: ListingComposeWizardViewModel) {
        vm.addPhoto("photo_1")
        vm.addPhoto("photo_2")
        vm.setTitle("Moving boxes — bundle of 18")
        vm.setCategory(ListingComposeCategory.Goods)
        vm.setCondition(ListingComposeCondition.LikeNew)
        vm.setBody("Lightly used, perfect for a one-bedroom move across town.")
        vm.setPriceKind(ListingComposePriceKind.Fixed)
        vm.setPriceAmount("25")
        vm.setFulfillment(ListingComposeFulfillment.Pickup)
        vm.setLocationKind(ListingComposeLocationKind.SavedAddress)
        // Walk through 5 steps to land on Review.
        repeat(5) { vm.onPrimary() }
    }

    // MARK: - Chrome shape

    @Test
    fun initial_chrome_is_photos_step() {
        val vm = makeVm()
        val chrome = vm.chrome
        assertEquals("List an item", chrome.title)
        assertEquals("Continue", chrome.primaryCtaLabel)
        assertFalse(chrome.primaryCtaEnabled)
        assertEquals(WizardLeadingControl.Close, chrome.leading)
        assertEquals(WizardProgressLabel.StepOf(current = 1, total = 6), chrome.progressLabel)
    }

    // MARK: - Photo step

    @Test
    fun photo_step_requires_at_least_one_photo() {
        val vm = makeVm()
        assertFalse(vm.chrome.primaryCtaEnabled)
        vm.addPhoto("a")
        assertTrue(vm.chrome.primaryCtaEnabled)
    }

    @Test
    fun add_photos_caps_at_max() {
        val vm = makeVm()
        repeat(10) { vm.addPhoto("p_$it") }
        assertEquals(ListingComposeFormState.MAX_PHOTOS, vm.state.value.form.photos.size)
    }

    @Test
    fun remove_photo_by_id() {
        val vm = makeVm()
        vm.addPhoto("a")
        vm.addPhoto("b")
        val firstId = vm.state.value.form.photos.first().id
        vm.removePhoto(firstId)
        assertEquals(1, vm.state.value.form.photos.size)
        assertEquals("b", vm.state.value.form.photos.first().token)
    }

    @Test
    fun move_photo_changes_hero() {
        val vm = makeVm()
        vm.addPhoto("a")
        vm.addPhoto("b")
        vm.addPhoto("c")
        assertEquals("a", vm.state.value.form.photos.first().token)
        vm.movePhoto(from = 2, to = 0)
        assertEquals("c", vm.state.value.form.photos.first().token)
    }

    @Test
    fun make_hero_promotes_to_zero() {
        val vm = makeVm()
        vm.addPhoto("a")
        vm.addPhoto("b")
        vm.addPhoto("c")
        val secondId = vm.state.value.form.photos[1].id
        vm.makeHero(secondId)
        assertEquals("b", vm.state.value.form.photos.first().token)
    }

    @Test
    fun make_hero_is_noop_for_first_slot() {
        val vm = makeVm()
        vm.addPhoto("a")
        vm.addPhoto("b")
        val firstId = vm.state.value.form.photos.first().id
        vm.makeHero(firstId)
        assertEquals("a", vm.state.value.form.photos.first().token)
    }

    // MARK: - Title + category

    @Test
    fun title_category_gate() =
        runTest {
            val vm = makeVm()
            vm.addPhoto("a")
            vm.onPrimary() // -> TitleCategory
            advanceTimeBy(10)
            assertEquals(ListingComposeStep.TitleCategory, vm.state.value.form.currentStep)
            assertFalse(vm.chrome.primaryCtaEnabled)
            vm.setTitle("Hi")
            assertFalse("Title under 5 chars must fail", vm.chrome.primaryCtaEnabled)
            vm.setTitle("Moving boxes — bundle of 18")
            assertFalse("Title alone is not enough — category required.", vm.chrome.primaryCtaEnabled)
            vm.setCategory(ListingComposeCategory.Goods)
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    @Test
    fun title_max_length_enforced() =
        runTest {
            val vm = makeVm()
            vm.addPhoto("a")
            vm.onPrimary()
            advanceTimeBy(10)
            vm.setCategory(ListingComposeCategory.Goods)
            vm.setTitle("a".repeat(81))
            assertFalse("Title over 80 chars must fail", vm.chrome.primaryCtaEnabled)
            vm.setTitle("a".repeat(80))
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    @Test
    fun free_category_picks_price_kind_free() {
        val vm = makeVm()
        vm.setCategory(ListingComposeCategory.Free)
        assertEquals(ListingComposePriceKind.Free, vm.state.value.form.priceKind)
    }

    @Test
    fun wanted_category_clears_condition_and_skips_gate() =
        runTest {
            val vm = makeVm()
            vm.addPhoto("a")
            vm.setTitle("Looking for a sewing machine")
            vm.setCategory(ListingComposeCategory.Wanted)
            vm.setBody("Anything in working order would be great — thank you, neighbors!")
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onPrimary()
            advanceTimeBy(10)
            assertEquals(ListingComposeStep.ConditionDescription, vm.state.value.form.currentStep)
            // Wanted skips condition; description alone gates this step.
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    // MARK: - Description gate

    @Test
    fun description_min_length_enforced() =
        runTest {
            val vm = makeVm()
            vm.addPhoto("a")
            vm.setTitle("Moving boxes — bundle of 18")
            vm.setCategory(ListingComposeCategory.Goods)
            vm.setCondition(ListingComposeCondition.LikeNew)
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onPrimary()
            advanceTimeBy(10)
            assertEquals(ListingComposeStep.ConditionDescription, vm.state.value.form.currentStep)
            assertFalse(vm.chrome.primaryCtaEnabled)
            vm.setBody("Short.")
            assertFalse(vm.chrome.primaryCtaEnabled)
            vm.setBody("Lightly used, perfect for a one-bedroom move across town.")
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    @Test
    fun condition_required_for_goods() =
        runTest {
            val vm = makeVm()
            vm.addPhoto("a")
            vm.setTitle("Moving boxes — bundle of 18")
            vm.setCategory(ListingComposeCategory.Goods)
            vm.setBody("Lightly used, perfect for a one-bedroom move across town.")
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onPrimary()
            advanceTimeBy(10)
            assertFalse("Condition required for goods.", vm.chrome.primaryCtaEnabled)
            vm.setCondition(ListingComposeCondition.LikeNew)
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    // MARK: - Price gate

    @Test
    fun fixed_price_requires_positive_amount() {
        val vm = makeVm()
        vm.setPriceKind(ListingComposePriceKind.Fixed)
        // Manually drive to the Price step.
        assertFalse(vm.isPriceValid(vm.state.value.form))
        vm.setPriceAmount("0")
        assertFalse(vm.isPriceValid(vm.state.value.form))
        vm.setPriceAmount("25")
        assertTrue(vm.isPriceValid(vm.state.value.form))
    }

    @Test
    fun free_price_kind_always_valid() {
        val vm = makeVm()
        vm.setPriceKind(ListingComposePriceKind.Free)
        assertTrue(vm.isPriceValid(vm.state.value.form))
    }

    @Test
    fun price_amount_filters_to_decimal() {
        val vm = makeVm()
        vm.setPriceAmount("abc12.5")
        assertEquals("12.5", vm.state.value.form.priceAmount)
        vm.setPriceAmount("12.3.4")
        // Two-decimal-separator input is rejected — last valid value sticks.
        assertEquals("12.5", vm.state.value.form.priceAmount)
    }

    // MARK: - Location gate

    @Test
    fun location_requires_selection() {
        val vm = makeVm()
        assertNull(vm.state.value.form.locationKind)
        vm.setLocationKind(ListingComposeLocationKind.SavedAddress)
        assertEquals(ListingComposeLocationKind.SavedAddress, vm.state.value.form.locationKind)
    }

    // MARK: - Submit happy path

    @Test
    fun submit_advances_to_success_and_records_listing_id() =
        runTest {
            val captured = slot<CreateListingRequest>()
            coEvery { repo.create(capture(captured)) } returns NetworkResult.Success(createResponse)
            val vm = makeVm()
            seedReadyToSubmit(vm)
            assertEquals(ListingComposeStep.Review, vm.state.value.form.currentStep)
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(ListingComposeStep.Success, vm.state.value.form.currentStep)
            assertEquals("listing_42", vm.state.value.createdListingId)
            assertEquals("View listing", vm.chrome.primaryCtaLabel)
            assertEquals("listingComposeBackToMarketplace", vm.chrome.secondaryCta?.testTag)
            assertFalse("Success step hides progress bar", vm.chrome.showsProgressBar)
            coVerify { repo.create(any()) }
            assertEquals("Moving boxes — bundle of 18", captured.captured.title)
            assertEquals("goods", captured.captured.category)
            assertEquals("goods", captured.captured.layer)
            assertEquals("sell_item", captured.captured.listingType)
            assertEquals(25.0, captured.captured.price)
            assertFalse(captured.captured.isFree)
        }

    @Test
    fun submit_error_keeps_user_on_review() =
        runTest {
            coEvery { repo.create(any<CreateListingRequest>()) } returns
                NetworkResult.Failure(NetworkError.Server(500, "server"))
            val vm = makeVm()
            seedReadyToSubmit(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(ListingComposeStep.Review, vm.state.value.form.currentStep)
            assertNotNull(vm.state.value.errorMessage)
        }

    @Test
    fun submit_offline_surfaces_inline_error() =
        runTest {
            isOnlineFlow.value = false
            val vm = makeVm()
            seedReadyToSubmit(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(ListingComposeStep.Review, vm.state.value.form.currentStep)
            assertEquals(
                "You're offline. Try again when you're back online.",
                vm.state.value.errorMessage,
            )
        }

    // MARK: - Success CTAs

    @Test
    fun success_primary_emits_open_listing_event() =
        runTest {
            coEvery { repo.create(any<CreateListingRequest>()) } returns
                NetworkResult.Success(createResponse)
            val vm = makeVm()
            seedReadyToSubmit(vm)
            vm.onPrimary() // Review → submit → Success
            advanceTimeBy(50)
            vm.onPrimary() // Success → open listing detail
            advanceTimeBy(10)
            assertEquals(
                ListingComposeOutboundEvent.OpenListingDetail("listing_42"),
                vm.pendingEvent.value,
            )
        }

    @Test
    fun success_secondary_emits_dismiss() =
        runTest {
            coEvery { repo.create(any<CreateListingRequest>()) } returns
                NetworkResult.Success(createResponse)
            val vm = makeVm()
            seedReadyToSubmit(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            vm.onSecondary()
            assertEquals(ListingComposeOutboundEvent.Dismiss, vm.pendingEvent.value)
        }

    // MARK: - Close-confirm dirtiness

    @Test
    fun close_on_empty_photos_is_clean() {
        val vm = makeVm()
        assertFalse(vm.chrome.dirty)
    }

    @Test
    fun close_on_photos_present_is_dirty() {
        val vm = makeVm()
        vm.addPhoto("a")
        assertTrue(vm.chrome.dirty)
    }

    @Test
    fun close_on_success_is_clean() =
        runTest {
            coEvery { repo.create(any<CreateListingRequest>()) } returns
                NetworkResult.Success(createResponse)
            val vm = makeVm()
            seedReadyToSubmit(vm)
            vm.onPrimary()
            advanceTimeBy(50)
            assertFalse(vm.chrome.dirty)
        }

    // MARK: - Back navigation

    @Test
    fun back_on_title_step_returns_to_photos() =
        runTest {
            val vm = makeVm()
            vm.addPhoto("a")
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onLeading()
            assertEquals(ListingComposeStep.Photos, vm.state.value.form.currentStep)
        }

    // MARK: - Edit mode

    @Test
    fun edit_mode_chrome_shows_edit_title() {
        val vm = makeEditVm()
        assertTrue(vm.isEditMode)
        assertEquals("listing_42", vm.editingListingId)
        assertEquals("Edit listing", vm.chrome.title)
    }

    @Test
    fun edit_mode_review_step_cta_reads_save_changes() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing = editListingDto(),
                    ),
                )
            val vm = makeEditVm()
            vm.loadExistingIfNeeded()
            assertEquals(ListingComposeStep.Review, vm.state.value.form.currentStep)
            assertEquals("Save changes", vm.chrome.primaryCtaLabel)
        }

    @Test
    fun edit_prefill_projects_listing_dto_into_form() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing = editListingDto(),
                    ),
                )
            val vm = makeEditVm()
            vm.loadExistingIfNeeded()
            val form = vm.state.value.form
            assertEquals("Mid-century walnut credenza", form.title)
            assertEquals(ListingComposeCategory.Goods, form.category)
            assertEquals(ListingComposeCondition.LikeNew, form.condition)
            assertEquals(ListingComposePriceKind.Fixed, form.priceKind)
            assertEquals("420", form.priceAmount)
            assertEquals(
                "Solid walnut, four sliding doors, dovetail joinery.",
                form.bodyText,
            )
            assertEquals(2, form.photos.size)
            assertEquals(ListingComposeLocationKind.MeetPoint, form.locationKind)
            assertEquals("Lincoln Park bandshell", form.locationLabel)
            // jumpToStep default → land on Review so the user can scan +
            // tap Save changes immediately.
            assertEquals(ListingComposeStep.Review, form.currentStep)
        }

    @Test
    fun edit_prefill_jumps_to_price_step_when_requested() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing = editListingDto(),
                    ),
                )
            val vm = makeEditVm(jumpToStep = ListingComposeStep.Price)
            vm.loadExistingIfNeeded()
            assertEquals(ListingComposeStep.Price, vm.state.value.form.currentStep)
            assertEquals("420", vm.state.value.form.priceAmount)
        }

    @Test
    fun edit_prefill_is_noop_when_form_already_dirty() =
        runTest {
            val vm = makeEditVm()
            vm.addPhoto("user_photo")
            vm.setTitle("User-edited title")
            // No coEvery — fetch must NOT be invoked.
            vm.loadExistingIfNeeded()
            assertEquals("User-edited title", vm.state.value.form.title)
        }

    @Test
    fun edit_prefill_free_listing_maps_to_free_category() =
        runTest {
            coEvery { repo.detail("listing_free") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing =
                            editListingDto(
                                listingType = "free_item",
                                layer = "goods",
                                isFree = true,
                                price = null,
                                category = "free_stuff",
                                condition = null,
                            ).copy(id = "listing_free"),
                    ),
                )
            val vm = makeEditVm(listingId = "listing_free")
            vm.loadExistingIfNeeded()
            assertEquals(ListingComposeCategory.Free, vm.state.value.form.category)
            assertEquals(ListingComposePriceKind.Free, vm.state.value.form.priceKind)
            assertEquals("", vm.state.value.form.priceAmount)
        }

    @Test
    fun edit_prefill_wanted_listing_maps_to_wanted_category() =
        runTest {
            coEvery { repo.detail("listing_w") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing =
                            editListingDto(listingType = "wanted_request").copy(id = "listing_w"),
                    ),
                )
            val vm = makeEditVm(listingId = "listing_w")
            vm.loadExistingIfNeeded()
            assertEquals(ListingComposeCategory.Wanted, vm.state.value.form.category)
        }

    @Test
    fun edit_prefill_surfaces_error_on_fetch_failure() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Failure(
                    app.pantopus.android.data.api.net.NetworkError.Server(500, "boom"),
                )
            val vm = makeEditVm()
            vm.loadExistingIfNeeded()
            assertNotNull(vm.state.value.errorMessage)
            // Form stays empty so the user can retry.
            assertEquals(ListingComposeFormState.EMPTY, vm.state.value.form)
        }

    @Test
    fun edit_submit_fires_PATCH_and_emits_listing_updated() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing = editListingDto(),
                    ),
                )
            coEvery {
                repo.update(
                    "listing_42",
                    any<app.pantopus.android.data.api.models.listings.UpdateListingRequest>(),
                )
            } returns NetworkResult.Success(updateResponse)
            val vm = makeEditVm()
            vm.loadExistingIfNeeded()
            assertEquals(ListingComposeStep.Review, vm.state.value.form.currentStep)
            vm.onPrimary() // submit
            advanceTimeBy(50)
            assertEquals(ListingComposeStep.Success, vm.state.value.form.currentStep)
            assertEquals("listing_42", vm.state.value.createdListingId)
            assertEquals("Back to listing", vm.chrome.primaryCtaLabel)
            assertEquals("listingComposeEditDone", vm.chrome.secondaryCta?.testTag)
            vm.onPrimary()
            advanceTimeBy(10)
            assertEquals(
                ListingComposeOutboundEvent.ListingUpdated("listing_42"),
                vm.pendingEvent.value,
            )
            coVerify {
                repo.update(
                    "listing_42",
                    any<app.pantopus.android.data.api.models.listings.UpdateListingRequest>(),
                )
            }
        }

    @Test
    fun edit_submit_error_keeps_user_on_review_with_banner() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing = editListingDto(),
                    ),
                )
            coEvery {
                repo.update(
                    "listing_42",
                    any<app.pantopus.android.data.api.models.listings.UpdateListingRequest>(),
                )
            } returns
                NetworkResult.Failure(
                    app.pantopus.android.data.api.net.NetworkError.Server(500, "boom"),
                )
            val vm = makeEditVm()
            vm.loadExistingIfNeeded()
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(ListingComposeStep.Review, vm.state.value.form.currentStep)
            assertNotNull(vm.state.value.errorMessage)
        }

    @Test
    fun edit_mode_is_always_dirty_pre_success() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing = editListingDto(),
                    ),
                )
            val vm = makeEditVm()
            vm.loadExistingIfNeeded()
            assertTrue("Edit mode warns on close so the user doesn't lose intent.", vm.chrome.dirty)
        }

    @Test
    fun edit_secondary_tap_emits_listing_updated() =
        runTest {
            coEvery { repo.detail("listing_42") } returns
                NetworkResult.Success(
                    app.pantopus.android.data.api.models.listings.ListingDetailResponse(
                        listing = editListingDto(),
                    ),
                )
            coEvery {
                repo.update(
                    "listing_42",
                    any<app.pantopus.android.data.api.models.listings.UpdateListingRequest>(),
                )
            } returns NetworkResult.Success(updateResponse)
            val vm = makeEditVm()
            vm.loadExistingIfNeeded()
            vm.onPrimary() // submit
            advanceTimeBy(50)
            assertEquals(ListingComposeStep.Success, vm.state.value.form.currentStep)
            vm.onSecondary()
            assertEquals(
                ListingComposeOutboundEvent.ListingUpdated("listing_42"),
                vm.pendingEvent.value,
            )
        }

    @Test
    fun project_pure_helper_handles_default_jump_target() {
        val form =
            ListingComposeWizardViewModel.project(editListingDto())
        assertEquals(ListingComposeStep.Review, form.currentStep)
        assertEquals(ListingComposeCategory.Goods, form.category)
    }
}
