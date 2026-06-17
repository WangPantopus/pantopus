@file:Suppress("MagicNumber", "PackageNaming", "LongMethod")

package app.pantopus.android.ui.screens.scheduling.home

import app.pantopus.android.data.api.models.homes.MyHome
import app.pantopus.android.data.api.models.homes.MyHomesResponse
import app.pantopus.android.data.api.models.scheduling.AvailabilityScheduleDto
import app.pantopus.android.data.api.models.scheduling.GetAvailabilityResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.homes.HomesRepository
import app.pantopus.android.data.network.NetworkMonitor
import app.pantopus.android.data.scheduling.SchedulingRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

/**
 * Covers the F8 Household Availability VM (A10): personal-set-up gating from
 * `getAvailability()`, home-name resolution, and the device-local exposure
 * toggles (which no-op until Personal availability is set up).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HouseholdAvailabilityViewModelTest {
    private val scheduling: SchedulingRepository = mockk()
    private val homes: HomesRepository = mockk()
    private val networkMonitor: NetworkMonitor = mockk()
    private val prefs: HomeSchedulingPrefs = mockk(relaxed = true)

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { networkMonitor.isOnline } returns MutableStateFlow(true)
        every { prefs.getBool(any(), any()) } answers { secondArg<Boolean>() }
        val home = mockk<MyHome>()
        every { home.id } returns "home-1"
        every { home.name } returns "Maple Street"
        every { home.occupancy } returns null
        coEvery { homes.myHomes() } returns NetworkResult.Success(MyHomesResponse(homes = listOf(home), message = null))
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm() = HouseholdAvailabilityViewModel(scheduling, homes, networkMonitor, prefs)

    private fun stubAvailability(scheduleCount: Int) {
        val schedules = (0 until scheduleCount).map { AvailabilityScheduleDto(id = "s$it") }
        coEvery { scheduling.getAvailability() } returns NetworkResult.Success(GetAvailabilityResponse(schedules = schedules))
    }

    @Test fun not_set_up_when_no_schedules() =
        runTest {
            stubAvailability(0)
            val vm = makeVm()
            vm.load()
            val ready = vm.state.value as HouseholdAvailabilityUiState.Ready
            assertFalse(ready.data.personalIsSetUp)
            assertEquals("Maple Street", ready.data.homeName)
        }

    @Test fun set_up_when_schedules_present() =
        runTest {
            stubAvailability(1)
            val vm = makeVm()
            vm.load()
            val ready = vm.state.value as HouseholdAvailabilityUiState.Ready
            assertTrue(ready.data.personalIsSetUp)
            // Defaults flow through prefs.
            assertTrue(ready.data.shareFreeBusy)
            assertTrue(ready.data.roundRobin)
            assertFalse(ready.data.autoDecline)
        }

    @Test fun availability_failure_renders_error() =
        runTest {
            coEvery { scheduling.getAvailability() } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = makeVm()
            vm.load()
            assertTrue(vm.state.value is HouseholdAvailabilityUiState.Error)
        }

    @Test fun toggle_ignored_when_not_set_up() =
        runTest {
            stubAvailability(0)
            val vm = makeVm()
            vm.load()
            vm.setExposure(Exposure.RoundRobin, to = false)
            val ready = vm.state.value as HouseholdAvailabilityUiState.Ready
            assertTrue("toggle is inert until Personal availability is set up", ready.data.roundRobin)
        }

    @Test fun toggle_flips_and_persists_when_set_up() =
        runTest {
            stubAvailability(1)
            val vm = makeVm()
            vm.load()
            vm.setExposure(Exposure.AutoDecline, to = true)
            val ready = vm.state.value as HouseholdAvailabilityUiState.Ready
            assertTrue(ready.data.autoDecline)
        }
}
