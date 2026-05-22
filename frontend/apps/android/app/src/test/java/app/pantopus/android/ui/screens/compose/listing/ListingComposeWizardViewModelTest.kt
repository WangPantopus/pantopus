@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.compose.listing

import app.pantopus.android.data.api.models.listings.CreateListingRequest
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.ui.screens.shared.wizard.WizardLeadingControl
import app.pantopus.android.ui.screens.shared.wizard.WizardProgressLabel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.slot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListingComposeWizardViewModelTest : ListingComposeWizardViewModelTestCase() {
    // MARK: - Chrome shape

    @Test
    fun initial_chrome_is_photos_step() {
        val vm = makeVm()
        val chrome = vm.chrome
        assertEquals("List an item", chrome.title)
        assertEquals("Review suggestions", chrome.primaryCtaLabel)
        assertFalse(chrome.primaryCtaEnabled)
        assertEquals(WizardLeadingControl.Close, chrome.leading)
        assertEquals(WizardProgressLabel.StepOf(current = 1, total = 3), chrome.progressLabel)
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

    @Test
    fun skip_to_manual_preserves_original_photo_grid_path() {
        val vm = makeVm()
        assertTrue(vm.isCameraCaptureStep)
        vm.skipToManualPhotoEditor()
        assertEquals(ListingComposeEntryMode.Manual, vm.state.value.form.entryMode)
        assertFalse(vm.isCameraCaptureStep)
        assertEquals("Continue", vm.chrome.primaryCtaLabel)
    }

    @Test
    fun camera_capture_seeds_snap_suggestions() =
        runTest {
            val vm = makeVm()
            vm.captureSnapPhoto()
            vm.captureSnapPhoto()
            vm.captureSnapPhoto()
            assertEquals(3, vm.state.value.form.photos.size)
            assertEquals("Sage green velvet sofa, 3-seater", vm.state.value.form.title)
            assertEquals(ListingComposeCategory.Goods, vm.state.value.form.category)
            assertEquals(ListingComposeCondition.Good, vm.state.value.form.condition)
            assertEquals(ListingComposePriceKind.Fixed, vm.state.value.form.priceKind)
            assertEquals("280", vm.state.value.form.priceAmount)
            assertEquals(ListingComposeLocationKind.SavedAddress, vm.state.value.form.locationKind)
            assertTrue(vm.state.value.form.deliveryEnabled)

            vm.onPrimary()
            advanceTimeBy(10)
            assertEquals(ListingComposeStep.TitleCategory, vm.state.value.form.currentStep)
            assertTrue(vm.isSnapReviewStep)
            assertEquals("Post listing", vm.chrome.primaryCtaLabel)
            assertEquals("listingComposeSaveDraft", vm.chrome.secondaryCta?.testTag)
            assertTrue(vm.chrome.primaryCtaEnabled)
        }

    @Test
    fun snap_review_primary_submits_listing() =
        runTest {
            coEvery { repo.create(any<CreateListingRequest>()) } returns NetworkResult.Success(createResponse)
            val vm = makeVm()
            vm.captureSnapPhoto()
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onPrimary()
            advanceTimeBy(50)
            assertEquals(ListingComposeStep.Success, vm.state.value.form.currentStep)
            assertEquals("listing_42", vm.state.value.createdListingId)
        }

    @Test
    fun snap_review_save_draft_dismisses() =
        runTest {
            val vm = makeVm()
            vm.captureSnapPhoto()
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onSecondary()
            assertEquals(ListingComposeOutboundEvent.Dismiss, vm.pendingEvent.value)
        }

    // MARK: - Title + category

    @Test
    fun title_category_gate() =
        runTest {
            val vm = makeVm()
            vm.skipToManualPhotoEditor()
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
            vm.skipToManualPhotoEditor()
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
            vm.skipToManualPhotoEditor()
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
            vm.skipToManualPhotoEditor()
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
            vm.skipToManualPhotoEditor()
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
            vm.skipToManualPhotoEditor()
            vm.addPhoto("a")
            vm.onPrimary()
            advanceTimeBy(10)
            vm.onLeading()
            assertEquals(ListingComposeStep.Photos, vm.state.value.form.currentStep)
        }
}
