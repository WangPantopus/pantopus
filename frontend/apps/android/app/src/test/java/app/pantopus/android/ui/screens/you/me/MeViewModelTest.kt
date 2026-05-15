@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.you.me

import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.users.ProfileResponse
import app.pantopus.android.data.api.models.users.UserProfile
import app.pantopus.android.data.api.models.users.UserStatsDto
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MeViewModelTest {
    private val profileRepo: ProfileRepository = mockk()
    private val homesRepo: HomesRepository = mockk()

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun profile(): UserProfile =
        UserProfile(
            id = "u1",
            email = "alice@example.com",
            username = "alice",
            firstName = "Alice",
            middleName = null,
            lastName = "Doe",
            name = "Alice Doe",
            phoneNumber = null,
            dateOfBirth = null,
            address = null,
            city = "Cambridge",
            state = "MA",
            zipcode = null,
            accountType = "personal",
            role = "member",
            verified = true,
            residency = null,
            avatarUrl = null,
            profilePictureUrl = null,
            profilePicture = null,
            bio = "Gardener.",
            tagline = null,
            socialLinks = null,
            skills = emptyList(),
            followersCount = 0,
            averageRating = 4.9,
            gigsPosted = 12,
            gigsCompleted = 8,
            profileVisibility = "registered",
            createdAt = "2025-01-01T00:00:00Z",
            updatedAt = "2026-01-01T00:00:00Z",
        )

    private fun home(): MyHome =
        MyHome(
            id = "h1",
            name = "412 Birch Ln",
            address = "412 Birch Ln",
            city = "Cambridge",
            state = "MA",
            zipcode = "02139",
            homeType = null,
            visibility = null,
            description = null,
            createdAt = null,
            updatedAt = null,
            occupancy = null,
            ownershipStatus = "verified",
            verificationTier = null,
            isPrimaryOwner = true,
            pendingClaimId = null,
        )

    private fun stats(): UserStatsDto =
        UserStatsDto(
            totalGigsPosted = 12,
            totalGigsCompleted = 8,
            totalEarnings = 240.0,
            averageRating = 4.9,
            totalRatings = 5,
        )

    @Test fun load_produces_all_three_identities_when_home_exists() =
        runTest {
            coEvery { profileRepo.ownProfile() } returns NetworkResult.Success(ProfileResponse(profile(), null))
            coEvery { homesRepo.myHomes() } returns NetworkResult.Success(MyHomesResponse(listOf(home()), null))
            coEvery { profileRepo.stats("u1") } returns NetworkResult.Success(stats())
            val vm = MeViewModel(profileRepo, homesRepo)
            vm.load()
            val loaded = vm.state.value as MeUiState.Loaded
            assertEquals("Alice Doe", loaded.personal.displayName)
            assertTrue(loaded.personal.verified)
            assertEquals(4, loaded.personal.stats.size)
            assertEquals("4.9", loaded.personal.stats.first { it.id == "rating" }.value)
            assertEquals(6, loaded.personal.actionTiles.size)
            assertEquals("me.mail", loaded.personal.actionTiles.first { it.id == "mail" }.routeKey)

            assertEquals("412 Birch Ln", loaded.home.displayName)
            assertFalse(loaded.home.isUnbound)
            assertEquals(6, loaded.home.actionTiles.size)
            assertEquals("me.home.access", loaded.home.actionTiles.first().routeKey)

            assertTrue(loaded.business.isUnbound)
        }

    @Test fun load_produces_unbound_home_when_no_home() =
        runTest {
            coEvery { profileRepo.ownProfile() } returns NetworkResult.Success(ProfileResponse(profile(), null))
            coEvery { homesRepo.myHomes() } returns NetworkResult.Success(MyHomesResponse(emptyList(), null))
            coEvery { profileRepo.stats("u1") } returns NetworkResult.Success(stats())
            val vm = MeViewModel(profileRepo, homesRepo)
            vm.load()
            val loaded = vm.state.value as MeUiState.Loaded
            assertTrue(loaded.home.isUnbound)
            assertEquals("Claim a home", loaded.home.displayName)
        }

    @Test fun select_identity_flips_active_without_refetch() =
        runTest {
            coEvery { profileRepo.ownProfile() } returns NetworkResult.Success(ProfileResponse(profile(), null))
            coEvery { homesRepo.myHomes() } returns NetworkResult.Success(MyHomesResponse(listOf(home()), null))
            coEvery { profileRepo.stats("u1") } returns NetworkResult.Success(stats())
            val vm = MeViewModel(profileRepo, homesRepo)
            vm.load()
            assertEquals(MeIdentity.Personal, vm.activeIdentity.value)
            vm.selectIdentity(MeIdentity.Home)
            assertEquals(MeIdentity.Home, vm.activeIdentity.value)
            vm.selectIdentity(MeIdentity.Business)
            assertEquals(MeIdentity.Business, vm.activeIdentity.value)
        }

    @Test fun profile_failure_transitions_error() =
        runTest {
            coEvery { profileRepo.ownProfile() } returns NetworkResult.Failure(NetworkError.Server(500, null))
            coEvery { homesRepo.myHomes() } returns NetworkResult.Success(MyHomesResponse(emptyList(), null))
            val vm = MeViewModel(profileRepo, homesRepo)
            vm.load()
            assertTrue(vm.state.value is MeUiState.Error)
        }
}
