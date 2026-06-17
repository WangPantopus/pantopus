@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.hub

import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.scheduling.BookingPageDto
import app.pantopus.android.data.api.models.scheduling.BookingPageResponse
import app.pantopus.android.data.api.models.scheduling.BookingSummaryResponse
import app.pantopus.android.data.api.models.scheduling.EventTypeDto
import app.pantopus.android.data.api.models.scheduling.GetAvailabilityResponse
import app.pantopus.android.data.api.models.scheduling.GetBookingsResponse
import app.pantopus.android.data.api.models.scheduling.GetConnectedCalendarsResponse
import app.pantopus.android.data.api.models.scheduling.GetEventTypesResponse
import app.pantopus.android.data.api.models.users.UserDto
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.ui.screens.scheduling._shared.SchedulingPillar
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SchedulingHubViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo: app.pantopus.android.data.scheduling.SchedulingRepository = mockk(relaxed = true)
    private val homes: HomesRepository = mockk()
    private val auth: AuthRepository = mockk()
    private val errors = SchedulingErrorDecoder(Moshi.Builder().build())

    @Before
    fun setup() {
        Dispatchers.setMain(dispatcher)
        every { auth.state } returns MutableStateFlow(AuthRepository.State.SignedIn(user()))
        coEvery { homes.myHomes() } returns NetworkResult.Success(MyHomesResponse(homes = listOf(home("home-7")), message = null))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun user() = UserDto(id = "user-1", email = "a@b.com", displayName = "A", avatarUrl = null)

    private fun home(id: String) =
        MyHome(
            id = id, name = "Birch Ln", address = null, city = null, state = null, zipcode = null,
            homeType = null, visibility = null, description = null, createdAt = null, updatedAt = null,
            occupancy = null, ownershipStatus = null, verificationTier = null, isPrimaryOwner = null,
            pendingClaimId = null,
        )

    private fun page(
        slug: String? = "maria-k",
        paused: Boolean = false,
    ) = BookingPageResponse(BookingPageDto(id = "p1", slug = slug, isPaused = paused, timezone = "America/New_York"))

    private fun stubFetch(eventTypes: List<EventTypeDto>) {
        coEvery { repo.getBookingPage(any()) } returns NetworkResult.Success(page())
        coEvery { repo.getEventTypes(any()) } returns NetworkResult.Success(GetEventTypesResponse(eventTypes))
        coEvery { repo.getBookingsSummary(any()) } returns
            NetworkResult.Success(BookingSummaryResponse(bookingsThisMonth = 18, deltaPct = 24, upcomingCount = 5, noShowCount = 1))
        coEvery { repo.getBookings(any(), any(), any(), any(), any(), any()) } returns NetworkResult.Success(GetBookingsResponse())
        coEvery { repo.getAvailability() } returns NetworkResult.Success(GetAvailabilityResponse())
        coEvery { repo.getConnectedCalendars() } returns NetworkResult.Success(GetConnectedCalendarsResponse())
    }

    private fun newVm() = SchedulingHubViewModel(repo, homes, auth, errors)

    private fun et(id: String) =
        EventTypeDto(id = id, name = "Intro call", slug = "intro", durations = listOf(30), defaultDuration = 30, locationMode = "video")

    @Test
    fun `empty page with no event types yields Empty state`() =
        runTest(dispatcher) {
            stubFetch(emptyList())
            val vm = newVm()
            vm.start()
            advanceUntilIdle()
            assertTrue(vm.state.value is SchedulingHubUiState.Empty)
        }

    @Test
    fun `loaded with event types maps summary metrics`() =
        runTest(dispatcher) {
            stubFetch(listOf(et("e1")))
            val vm = newVm()
            vm.start()
            advanceUntilIdle()
            val loaded = vm.state.value as SchedulingHubUiState.Loaded
            assertEquals(18, loaded.summary?.bookings)
            assertEquals(5, loaded.summary?.upcoming)
            assertEquals("pantopus.com/book/maria-k", loaded.handle)
        }

    @Test
    fun `selecting Home pillar resolves first home id`() =
        runTest(dispatcher) {
            stubFetch(listOf(et("e1")))
            val vm = newVm()
            vm.start()
            advanceUntilIdle()
            vm.selectPillar(SchedulingPillar.Home)
            advanceUntilIdle()
            assertEquals(SchedulingPillar.Home, vm.pillar.value)
            coVerify { repo.getBookingPage(SchedulingOwner.Home("home-7")) }
        }

    @Test
    fun `selecting Business pillar resolves signed-in user id`() =
        runTest(dispatcher) {
            stubFetch(listOf(et("e1")))
            val vm = newVm()
            vm.start()
            advanceUntilIdle()
            vm.selectPillar(SchedulingPillar.Business)
            advanceUntilIdle()
            coVerify { repo.getBookingPage(SchedulingOwner.Business("user-1")) }
        }

    @Test
    fun `pause toggle optimistically flips and persists`() =
        runTest(dispatcher) {
            stubFetch(listOf(et("e1")))
            coEvery { repo.updateBookingPage(any(), any()) } returns NetworkResult.Success(page(paused = true))
            val vm = newVm()
            vm.start()
            advanceUntilIdle()
            vm.setPaused(true)
            advanceUntilIdle()
            assertTrue((vm.state.value as SchedulingHubUiState.Loaded).isPaused)
            coVerify { repo.updateBookingPage(SchedulingOwner.Personal, any()) }
        }
}
