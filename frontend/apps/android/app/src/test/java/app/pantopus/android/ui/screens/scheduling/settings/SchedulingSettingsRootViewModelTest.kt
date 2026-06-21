@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.settings

import app.pantopus.android.data.api.models.scheduling.BookingPageDto
import app.pantopus.android.data.api.models.scheduling.BookingPageResponse
import app.pantopus.android.data.api.models.scheduling.GetEventTypesResponse
import app.pantopus.android.data.api.models.scheduling.PaymentStatusResponse
import app.pantopus.android.data.api.net.NetworkError
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
import app.pantopus.android.data.scheduling.SchedulingFeatureFlags
import app.pantopus.android.data.scheduling.SchedulingOwner
import app.pantopus.android.data.scheduling.SchedulingRepository
import com.squareup.moshi.Moshi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
class SchedulingSettingsRootViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo: SchedulingRepository = mockk(relaxed = true)
    private val errors = SchedulingErrorDecoder(Moshi.Builder().build())
    private val flags = SchedulingFeatureFlags().apply { environment = "development" }

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = SchedulingSettingsRootViewModel(repo, errors, flags)

    @Test
    fun `load yields Loaded with derived footer and reminders`() =
        runTest(dispatcher) {
            coEvery { repo.getBookingPage(any()) } returns
                NetworkResult.Success(BookingPageResponse(BookingPageDto(id = "p", slug = "maria-k", reminderMinutes = listOf(1440, 60))))
            coEvery { repo.getPaymentsStatus(any()) } returns
                NetworkResult.Success(PaymentStatusResponse(applicable = true, connected = true))
            coEvery { repo.getEventTypes(any()) } returns NetworkResult.Success(GetEventTypesResponse(emptyList()))
            val vm = vm()
            vm.load()
            advanceUntilIdle()
            val loaded = vm.state.value as SchedulingSettingsUiState.Loaded
            assertEquals("1 day · 1 hr", loaded.data.remindersValue)
            assertTrue(loaded.data.monoFooter.contains("maria-k"))
            assertTrue(loaded.data.paymentsConnected)
        }

    @Test
    fun `booking page failure yields Error`() =
        runTest(dispatcher) {
            coEvery { repo.getBookingPage(any()) } returns NetworkResult.Failure(NetworkError.Server(500, null))
            val vm = vm()
            vm.load()
            advanceUntilIdle()
            assertTrue(vm.state.value is SchedulingSettingsUiState.Error)
        }

    @Test
    fun `reset slug calls repository`() =
        runTest(dispatcher) {
            coEvery { repo.getBookingPage(any()) } returns
                NetworkResult.Success(BookingPageResponse(BookingPageDto(id = "p", slug = "old")))
            coEvery { repo.resetSlug(any()) } returns
                NetworkResult.Success(BookingPageResponse(BookingPageDto(id = "p", slug = "new")))
            val vm = vm()
            vm.load()
            advanceUntilIdle()
            vm.resetSlug()
            advanceUntilIdle()
            coVerify { repo.resetSlug(SchedulingOwner.Personal) }
        }
}
