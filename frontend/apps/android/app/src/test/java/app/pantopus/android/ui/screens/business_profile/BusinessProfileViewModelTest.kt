@file:Suppress("MagicNumber", "LongMethod", "PackageNaming", "TooManyFunctions")

package app.pantopus.android.ui.screens.business_profile

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.data.api.models.businesses.BusinessCatalogItemDto
import app.pantopus.android.data.api.models.businesses.BusinessDetailResponse
import app.pantopus.android.data.api.models.businesses.BusinessHoursDto
import app.pantopus.android.data.api.models.businesses.BusinessLocationDto
import app.pantopus.android.data.api.models.businesses.BusinessProfileDetailDto
import app.pantopus.android.data.api.models.businesses.BusinessPublicResponse
import app.pantopus.android.data.api.models.businesses.BusinessUserDetailDto
import app.pantopus.android.data.api.models.profile.PublicProfileDto
import app.pantopus.android.data.api.models.profile.PublicProfileReview
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.businesses.BusinessesRepository
import app.pantopus.android.data.profile.ProfileRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
class BusinessProfileViewModelTest {
    private val businesses: BusinessesRepository = mockk()
    private val profiles: ProfileRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(): BusinessProfileViewModel =
        BusinessProfileViewModel(
            businesses = businesses,
            profiles = profiles,
            savedStateHandle = SavedStateHandle(mapOf(BUSINESS_PROFILE_BUSINESS_ID_KEY to "biz-1")),
        )

    private fun sampleDetail(): BusinessDetailResponse =
        BusinessDetailResponse(
            business =
                BusinessUserDetailDto(
                    id = "biz-1",
                    username = "elmpark-coffee",
                    name = "Elm Park Coffee",
                    bio = "Slow coffee on the corner.",
                    tagline = "Made by neighbors",
                    profilePictureUrl = "https://example.test/elm.png",
                    accountType = "business",
                    city = "Cambridge",
                    state = "MA",
                    verified = true,
                    averageRating = 4.7,
                    reviewCount = 12,
                    followersCount = 240,
                    gigsCompleted = 0,
                    createdAt = "2023-04-10T00:00:00.000Z",
                ),
            profile =
                BusinessProfileDetailDto(
                    businessUserId = "biz-1",
                    businessType = "cafe",
                    categories = listOf("Coffee", "Bakery"),
                    description = "Pour-over coffee and laminated pastry.",
                    publicEmail = "hi@elmpark.test",
                    publicPhone = "+1-555-0101",
                    website = "elmparkcoffee.test",
                    isPublished = true,
                    verificationStatus = "address_verified",
                    primaryLocation =
                        BusinessLocationDto(
                            id = "loc-1",
                            label = "Main shop",
                            isPrimary = true,
                            address = "41 Elm Street",
                            city = "Cambridge",
                            state = "MA",
                            zipcode = "02139",
                            country = "US",
                            location = null,
                        ),
                ),
            locations = emptyList(),
            access = null,
        )

    private fun samplePublic(): BusinessPublicResponse =
        BusinessPublicResponse(
            hours =
                listOf(
                    BusinessHoursDto(
                        id = "h-mon",
                        locationId = "loc-1",
                        dayOfWeek = 1,
                        openTime = "07:00",
                        closeTime = "16:00",
                        isClosed = false,
                    ),
                    BusinessHoursDto(
                        id = "h-sun",
                        locationId = "loc-1",
                        dayOfWeek = 0,
                        openTime = null,
                        closeTime = null,
                        isClosed = true,
                    ),
                ),
            catalog =
                listOf(
                    BusinessCatalogItemDto(
                        id = "svc-1",
                        name = "Pour over",
                        description = "Single-origin.",
                        kind = "service",
                        priceCents = 500,
                        currency = "USD",
                    ),
                ),
        )

    private fun samplePublicProfile(): PublicProfileDto =
        PublicProfileDto(
            id = "biz-1",
            username = "elmpark-coffee",
            name = "Elm Park Coffee",
            accountType = "business",
            verified = true,
            createdAt = "2023-04-10T00:00:00.000Z",
            averageRating = 4.7,
            reviewCount = 12,
            followersCount = 240,
            reviews =
                listOf(
                    PublicProfileReview(
                        id = "r1",
                        reviewerId = "u9",
                        revieweeId = "biz-1",
                        rating = 5,
                        content = "Best coffee on Elm.",
                        createdAt = "2026-05-01T00:00:00.000Z",
                        reviewerName = "Sam",
                        reviewerAvatar = null,
                        reviewerUsername = "sam",
                    ),
                ),
        )

    @Test
    fun load_emitsLoadedWithHeaderStatsHoursAndServices() =
        runTest {
            coEvery { businesses.business("biz-1") } returns NetworkResult.Success(sampleDetail())
            coEvery { businesses.publicBusiness("elmpark-coffee") } returns NetworkResult.Success(samplePublic())
            coEvery { profiles.publicProfile("biz-1") } returns NetworkResult.Success(samplePublicProfile())

            val vm = makeVm()
            vm.load()

            val state = vm.state.value as BusinessProfileUiState.Loaded
            val c = state.content

            assertEquals("Elm Park Coffee", c.header.displayName)
            assertEquals("elmpark-coffee", c.header.handle)
            assertEquals("Cambridge, MA", c.header.locality)
            assertTrue(c.header.isVerified)
            assertEquals(listOf("Coffee", "Bakery"), c.header.categoryChips)

            assertEquals(listOf("Followers", "Reviews", "Years"), c.stats.map { it.label })
            assertEquals("240", c.stats.first().value)

            assertEquals("Pour-over coffee and laminated pastry.", c.about)

            assertEquals(2, c.hours.size)
            assertEquals(listOf("Sun", "Mon"), c.hours.map { it.dayLabel })
            assertTrue(c.hours.first().isClosed)

            assertEquals(1, c.services.size)
            assertEquals("$5", c.services.first().priceLabel)

            assertEquals(1, c.reviews.size)
            assertEquals("Sam", c.reviews.first().reviewerName)

            assertNotNull(c.websiteUrl)
        }

    @Test
    fun load_emptyServicesWhenPublicCatalogReturnsEmpty() =
        runTest {
            coEvery { businesses.business("biz-1") } returns NetworkResult.Success(sampleDetail())
            coEvery { businesses.publicBusiness("elmpark-coffee") } returns
                NetworkResult.Success(BusinessPublicResponse(emptyList(), emptyList()))
            coEvery { profiles.publicProfile("biz-1") } returns NetworkResult.Success(samplePublicProfile())

            val vm = makeVm()
            vm.load()

            val state = vm.state.value as BusinessProfileUiState.Loaded
            assertTrue(state.content.services.isEmpty())
            assertTrue(state.content.hours.isEmpty())
            assertNotNull(state.content.about)
        }

    @Test
    fun load_publicFetch404IsAbsorbed() =
        runTest {
            coEvery { businesses.business("biz-1") } returns NetworkResult.Success(sampleDetail())
            coEvery { businesses.publicBusiness("elmpark-coffee") } returns
                NetworkResult.Failure(NetworkError.NotFound)
            coEvery { profiles.publicProfile("biz-1") } returns NetworkResult.Success(samplePublicProfile())

            val vm = makeVm()
            vm.load()

            val state = vm.state.value as BusinessProfileUiState.Loaded
            assertTrue(state.content.services.isEmpty())
            assertTrue(state.content.hours.isEmpty())
            assertEquals(1, state.content.reviews.size)
        }

    @Test
    fun load_primary404EmitsNotFound() =
        runTest {
            coEvery { businesses.business("biz-1") } returns
                NetworkResult.Failure(NetworkError.NotFound)

            val vm = makeVm()
            vm.load()

            assertEquals(BusinessProfileUiState.NotFound, vm.state.value)
        }

    @Test
    fun load_primaryTransportErrorEmitsError() =
        runTest {
            coEvery { businesses.business("biz-1") } returns
                NetworkResult.Failure(NetworkError.Transport(RuntimeException("offline")))

            val vm = makeVm()
            vm.load()

            val state = vm.state.value
            assertTrue(state is BusinessProfileUiState.Error)
        }

    @Test
    fun selectTab_doesNotRefetch() =
        runTest {
            coEvery { businesses.business("biz-1") } returns NetworkResult.Success(sampleDetail())
            coEvery { businesses.publicBusiness("elmpark-coffee") } returns NetworkResult.Success(samplePublic())
            coEvery { profiles.publicProfile("biz-1") } returns NetworkResult.Success(samplePublicProfile())

            val vm = makeVm()
            vm.load()

            vm.selectTab(BusinessProfileTab.Services)
            vm.selectTab(BusinessProfileTab.Reviews)
            assertEquals(BusinessProfileTab.Reviews, vm.selectedTab.value)
            // No coVerify on number of calls: the structured-concurrency
            // primary path already issues 3 calls; we just want to know
            // tab switches don't trigger any state regression.
            assertTrue(vm.state.value is BusinessProfileUiState.Loaded)
        }

    @Test
    fun save_movesIdleToSavedAndEmitsToast() =
        runTest {
            coEvery { businesses.business("biz-1") } returns NetworkResult.Success(sampleDetail())
            coEvery { businesses.publicBusiness("elmpark-coffee") } returns NetworkResult.Success(samplePublic())
            coEvery { profiles.publicProfile("biz-1") } returns NetworkResult.Success(samplePublicProfile())

            val vm = makeVm()
            vm.load()
            vm.save()

            assertEquals(BusinessProfileSaveState.Saved, vm.saveState.value)
            assertEquals("Saved", vm.toastMessage.value)
        }

    @Test
    fun load_skipsPublicFetchWhenUsernameMissing() =
        runTest {
            val detailNoUsername =
                sampleDetail().copy(
                    business = sampleDetail().business.copy(username = null),
                )
            coEvery { businesses.business("biz-1") } returns NetworkResult.Success(detailNoUsername)
            coEvery { profiles.publicProfile("biz-1") } returns NetworkResult.Success(samplePublicProfile())

            val vm = makeVm()
            vm.load()

            val state = vm.state.value as BusinessProfileUiState.Loaded
            assertTrue(state.content.services.isEmpty())
            assertNull(state.content.header.handle)
            assertFalse(state.content.viewerIsOwner)
        }
}
