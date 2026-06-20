@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.setup

import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.scheduling.BookingPageDto
import app.pantopus.android.data.api.models.scheduling.BookingPageResponse
import app.pantopus.android.data.api.models.scheduling.CreateEventTypeRequest
import app.pantopus.android.data.api.models.scheduling.EventTypeDto
import app.pantopus.android.data.api.models.scheduling.EventTypeResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.auth.AuthRepository
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class OnboardingHomeBusinessViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo: SchedulingRepository = mockk(relaxed = true)
    private val homes: HomesRepository = mockk()
    private val auth: AuthRepository = mockk(relaxed = true)
    private val errors = SchedulingErrorDecoder(Moshi.Builder().build())

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = OnboardingHomeBusinessViewModel(repo, homes, auth, errors)

    private fun myHome(id: String) =
        MyHome(
            id = id, name = null, address = null, city = null, state = null, zipcode = null,
            homeType = null, visibility = null, description = null, createdAt = null, updatedAt = null,
            occupancy = null, ownershipStatus = null, verificationTier = null, isPrimaryOwner = null,
            pendingClaimId = null,
        )

    @Test
    fun `default flow is Home with three steps`() {
        val vm = vm()
        assertEquals(OnboardingFlow.Home, vm.state.value.flow)
        assertEquals(3, vm.state.value.railSteps)
    }

    @Test
    fun `selecting Business flow resets to four steps at step one`() {
        val vm = vm()
        vm.selectFlow(OnboardingFlow.Business)
        assertEquals(OnboardingFlow.Business, vm.state.value.flow)
        assertEquals(4, vm.state.value.railSteps)
        assertEquals(1, vm.state.value.stepIndex)
    }

    @Test
    fun `home flow advances Members to Combine`() {
        val vm = vm()
        vm.onPrimary()
        assertEquals(2, vm.state.value.stepIndex)
    }

    @Test
    fun `home finish resolves first home id and creates collective event type`() =
        runTest(dispatcher) {
            coEvery { homes.myHomes() } returns
                NetworkResult.Success(MyHomesResponse(homes = listOf(myHome("home-9")), message = null))
            coEvery { repo.createEventType(any(), any()) } returns
                NetworkResult.Success(EventTypeResponse(EventTypeDto(id = "e", name = "n", slug = "s", durations = listOf(30))))
            coEvery { repo.updateBookingPage(any(), any()) } returns NetworkResult.Success(BookingPageResponse(BookingPageDto(id = "p")))
            val body = slot<CreateEventTypeRequest>()
            coEvery { repo.createEventType(SchedulingOwner.Home("home-9"), capture(body)) } returns
                NetworkResult.Success(EventTypeResponse(EventTypeDto(id = "e", name = "n", slug = "s", durations = listOf(30))))

            val vm = vm()
            vm.onPrimary() // Members -> Combine
            vm.onPrimary() // Combine -> finishSetup (step 3 == totalSteps)
            advanceUntilIdle()
            coVerify { repo.createEventType(SchedulingOwner.Home("home-9"), any()) }
            assertEquals("collective", body.captured.assignmentMode)
            assertTrue(vm.state.value.isSuccess)
        }
}
