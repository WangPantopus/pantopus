@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.scheduling.availability

import app.cash.turbine.test
import app.pantopus.android.data.api.models.scheduling.EventTypeDto
import app.pantopus.android.data.api.models.scheduling.EventTypeResponse
import app.pantopus.android.data.api.models.scheduling.GetEventTypesResponse
import app.pantopus.android.data.api.net.NetworkResult
import app.pantopus.android.data.scheduling.SchedulingErrorDecoder
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookingLimitsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repo = mockk<SchedulingRepository>(relaxed = true)
    private val errors = SchedulingErrorDecoder(Moshi.Builder().build())

    @Before fun setup() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = BookingLimitsViewModel(repo, errors)

    private fun eventType() =
        EventTypeDto(
            id = "e1",
            name = "Intro call",
            slug = "intro",
            durations = listOf(30),
            minNoticeMin = 240,
            maxHorizonDays = 60,
            slotIntervalMin = 60,
            dailyCap = 8,
            perBookerCap = 2,
        )

    @Test
    fun `load with no event types yields Empty`() =
        runTest(dispatcher) {
            coEvery { repo.getEventTypes(any()) } returns NetworkResult.Success(GetEventTypesResponse(emptyList()))
            val vm = vm()
            vm.load()
            advanceUntilIdle()
            assertEquals(BookingLimitsUiState.Empty, vm.state.value)
        }

    @Test
    fun `load maps event-type fields into the form`() =
        runTest(dispatcher) {
            coEvery { repo.getEventTypes(any()) } returns NetworkResult.Success(GetEventTypesResponse(listOf(eventType())))
            val vm = vm()
            vm.load()
            advanceUntilIdle()
            val form = (vm.state.value as BookingLimitsUiState.Content).form
            assertEquals(4, form.minNoticeHours)
            assertEquals(60, form.bookUpToDays)
            assertEquals(8, form.maxPerDay)
            assertEquals(2, form.perPerson)
            assertEquals(StartInterval.Hourly, form.startInterval)
            assertTrue(form.isValid)
        }

    @Test
    fun `book-up-to shorter than notice is a window error`() =
        runTest(dispatcher) {
            coEvery { repo.getEventTypes(any()) } returns NetworkResult.Success(GetEventTypesResponse(listOf(eventType())))
            val vm = vm()
            vm.load()
            advanceUntilIdle()
            vm.changeBookUpTo(-1000)
            val form = (vm.state.value as BookingLimitsUiState.Content).form
            assertEquals(0, form.bookUpToDays)
            assertTrue(form.windowError)
            assertFalse(form.isValid)
        }

    @Test
    fun `save writes converted event-type fields`() =
        runTest(dispatcher) {
            coEvery { repo.getEventTypes(any()) } returns NetworkResult.Success(GetEventTypesResponse(listOf(eventType())))
            coEvery { repo.updateEventType(any(), any(), any()) } returns NetworkResult.Success(EventTypeResponse(eventType()))
            val vm = vm()
            vm.load()
            advanceUntilIdle()
            vm.events.test {
                vm.save()
                advanceUntilIdle()
                assertEquals(BookingLimitsEvent.Saved, awaitItem())
            }
            coVerify {
                repo.updateEventType(
                    any(),
                    "e1",
                    match {
                        it.minNoticeMin == 240 &&
                            it.maxHorizonDays == 60 &&
                            it.slotIntervalMin == 60 &&
                            it.dailyCap == 8 &&
                            it.perBookerCap == 2
                    },
                )
            }
        }
}
