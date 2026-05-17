package app.pantopus.android.ui.screens.businesses

import app.cash.turbine.test
import app.pantopus.android.data.api.models.businesses.BusinessMembership
import app.pantopus.android.data.api.models.businesses.BusinessProfileDto
import app.pantopus.android.data.api.models.businesses.BusinessUserDto
import app.pantopus.android.data.api.models.businesses.MyBusinessesResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.businesses.BusinessesRepository
import app.pantopus.android.ui.screens.shared.list_of_rows.BannerCtaTint
import app.pantopus.android.ui.screens.shared.list_of_rows.ListOfRowsUiState
import app.pantopus.android.ui.screens.shared.list_of_rows.RowLeading
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
class MyBusinessesViewModelTest {
    private val repo: BusinessesRepository = mockk()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun membership(
        id: String,
        name: String,
        city: String? = null,
        state: String? = null,
        role: String = "owner",
        published: Boolean = true,
        category: String? = "handyman",
    ) = BusinessMembership(
        id = "seat-$id",
        roleBase = role,
        title = null,
        joinedAt = null,
        businessUserId = id,
        business =
            BusinessUserDto(
                id = id,
                username = name.lowercase().replace(' ', '_'),
                name = name,
                email = null,
                profilePictureUrl = null,
                accountType = "business",
                city = city,
                state = state,
            ),
        profile =
            BusinessProfileDto(
                businessUserId = id,
                businessType = "home_services",
                categories = listOfNotNull(category),
                isPublished = published,
                logoFileId = null,
                bannerFileId = null,
                description = null,
            ),
    )

    @Test
    fun load_emits_loaded_rows_with_role_locality_and_banner() =
        runTest {
            coEvery { repo.myBusinesses() } returns
                NetworkResult.Success(
                    MyBusinessesResponse(
                        businesses =
                            listOf(
                                membership("b1", "Big Tree Handyman", city = "Elm Park", state = "NY"),
                                membership("b2", "Bayside Tutoring", role = "manager", published = false, category = "tutoring"),
                            ),
                    ),
                )
            val vm = MyBusinessesViewModel(repo)
            vm.load()
            val loaded = vm.state.value as ListOfRowsUiState.Loaded
            val rows = loaded.sections.first().rows
            assertEquals(2, rows.size)
            // Owner row: title cased category + role + locality body +
            // verified avatar.
            assertEquals("Big Tree Handyman", rows[0].title)
            assertEquals("Handyman · Owner", rows[0].subtitle)
            assertEquals("Elm Park, NY", rows[0].body)
            val leading0 = rows[0].leading as RowLeading.AvatarWithBadge
            assertTrue(leading0.verified)
            // Unpublished + locationless row falls back to "Online only"
            // and suppresses the verified badge.
            assertEquals("Tutoring · Manager", rows[1].subtitle)
            assertEquals("Online only", rows[1].body)
            val leading1 = rows[1].leading as RowLeading.AvatarWithBadge
            assertFalse(leading1.verified)
            // Banner shows count + business tint when populated.
            val banner = vm.banner.value
            assertNotNull(banner)
            assertEquals("2 verified businesses", banner!!.title)
            assertEquals(BannerCtaTint.Business, banner.tint)
        }

    @Test
    fun empty_response_surfaces_empty_state_and_clears_banner() =
        runTest {
            coEvery { repo.myBusinesses() } returns NetworkResult.Success(MyBusinessesResponse(businesses = emptyList()))
            val vm = MyBusinessesViewModel(repo)
            vm.state.test {
                awaitItem()
                vm.load()
                val empty = awaitItem() as ListOfRowsUiState.Empty
                assertEquals("No businesses yet", empty.headline)
                assertEquals("Register a business", empty.ctaTitle)
                cancelAndConsumeRemainingEvents()
            }
            assertNull(vm.banner.value)
        }

    @Test
    fun empty_state_cta_fires_onRegister() =
        runTest {
            coEvery { repo.myBusinesses() } returns NetworkResult.Success(MyBusinessesResponse(businesses = emptyList()))
            var registered = false
            val vm =
                MyBusinessesViewModel(repo).apply {
                    configureNavigation(onOpenBusiness = {}, onRegister = { registered = true })
                }
            vm.load()
            val empty = vm.state.value as ListOfRowsUiState.Empty
            empty.onCta?.invoke()
            assertTrue(registered)
        }

    @Test
    fun failure_surfaces_error_state() =
        runTest {
            coEvery { repo.myBusinesses() } returns NetworkResult.Failure(NetworkError.NotFound)
            val vm = MyBusinessesViewModel(repo)
            vm.state.test {
                awaitItem()
                vm.load()
                assertTrue(awaitItem() is ListOfRowsUiState.Error)
                cancelAndConsumeRemainingEvents()
            }
        }
}
