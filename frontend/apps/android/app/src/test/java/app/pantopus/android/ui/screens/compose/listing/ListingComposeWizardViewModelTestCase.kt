@file:Suppress("PackageNaming", "MagicNumber")

package app.pantopus.android.ui.screens.compose.listing

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.listings.CreateListingResponse
import app.pantopus.android.data.api.models.listings.ListingDto
import app.pantopus.android.data.api.models.listings.UpdateListingResponse
import app.pantopus.android.data.listings.ListingsRepository
import app.pantopus.android.data.network.NetworkMonitor
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before

@OptIn(ExperimentalCoroutinesApi::class)
abstract class ListingComposeWizardViewModelTestCase {
    protected val repo: ListingsRepository = mockk(relaxed = true)
    protected val isOnlineFlow = MutableStateFlow(true)
    protected val networkMonitor: NetworkMonitor =
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

    protected fun makeVm(savedStateHandle: SavedStateHandle = SavedStateHandle()) =
        ListingComposeWizardViewModel(repo, savedStateHandle, networkMonitor)

    protected fun makeEditVm(
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

    protected fun editListingDto(): ListingDto =
        ListingDto(
            id = "listing_42",
            userId = "user_seller",
            title = "Mid-century walnut credenza",
            description = "Solid walnut, four sliding doors, dovetail joinery.",
            price = 420.0,
            isFree = false,
            category = "furniture",
            condition = "like_new",
            mediaUrls = editListingMediaUrls,
            firstImage = editListingMediaUrls.firstOrNull(),
            layer = "goods",
            listingType = "sell_item",
            locationName = "Lincoln Park bandshell",
            status = "active",
        )

    protected fun freeEditListingDto(): ListingDto =
        editListingDto().copy(
            id = "listing_free",
            listingType = "free_item",
            layer = "goods",
            isFree = true,
            price = null,
            category = "free_stuff",
            condition = null,
        )

    protected fun wantedEditListingDto(): ListingDto =
        editListingDto().copy(
            id = "listing_w",
            listingType = "wanted_request",
        )

    protected val updateResponse =
        UpdateListingResponse(
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

    protected val createResponse =
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
    protected fun seedReadyToSubmit(vm: ListingComposeWizardViewModel) {
        vm.skipToManualPhotoEditor()
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

    private companion object {
        val editListingMediaUrls = listOf("https://example.com/a.jpg", "https://example.com/b.jpg")
    }
}
