@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.support_trains.detail

import androidx.lifecycle.SavedStateHandle
import app.pantopus.android.ui.components.SlotCalendarState
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

/**
 * A10.9 (P3.1) — Covers the Android Support Train detail VM. Parity
 * with `SupportTrainDetailViewModelTests.swift`:
 *   - initial state is Loading,
 *   - load() resolves to Loaded via the default resolver,
 *   - the default resolver picks fullyCovered when the trainId
 *     contains "covered" / "full",
 *   - a resolver returning null surfaces an Error state,
 *   - seed() seeds the state directly for chrome tests,
 *   - the populated + fullyCovered sample fixtures match the
 *     A10.9 frame contract.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SupportTrainDetailViewModelTest {
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeVm(trainId: String): SupportTrainDetailViewModel {
        val handle = SavedStateHandle(mapOf(SupportTrainDetailViewModel.SUPPORT_TRAIN_ID_KEY to trainId))
        return SupportTrainDetailViewModel(handle)
    }

    @Test
    fun initial_state_is_loading() {
        val vm = makeVm("any")
        assertTrue(vm.state.value is SupportTrainDetailUiState.Loading)
    }

    @Test
    fun load_resolves_populated_by_default() =
        runTest {
            val vm = makeVm("abc-123")
            vm.load()
            val state = vm.state.value
            assertTrue("Expected Loaded, got $state", state is SupportTrainDetailUiState.Loaded)
            val content = (state as SupportTrainDetailUiState.Loaded).content
            assertEquals(12, content.typeDates.slotsFilled)
            assertEquals(21, content.typeDates.slotsTotal)
            assertFalse(content.isFullyCovered)
            assertNull(content.celebrationBanner)
            assertTrue(content.dock is SupportTrainDock.SignUp)
            assertEquals("Sign up for a slot", (content.dock as SupportTrainDock.SignUp).label)
        }

    @Test
    fun load_resolves_fully_covered_when_id_contains_covered() =
        runTest {
            val vm = makeVm("fully-covered-xyz")
            vm.load()
            val state = vm.state.value
            assertTrue(state is SupportTrainDetailUiState.Loaded)
            val content = (state as SupportTrainDetailUiState.Loaded).content
            assertTrue(content.isFullyCovered)
            assertEquals(21, content.typeDates.slotsFilled)
            assertEquals(21, content.typeDates.slotsTotal)
            assertNotNull(content.celebrationBanner)
            assertTrue(content.dock is SupportTrainDock.SendCardAndBackup)
        }

    @Test
    fun load_with_resolver_returning_null_surfaces_error() =
        runTest {
            val vm = makeVm("missing")
            vm.resolve = { null }
            vm.load()
            val state = vm.state.value
            assertTrue(state is SupportTrainDetailUiState.Error)
            assertEquals("Couldn't load this support train.", (state as SupportTrainDetailUiState.Error).message)
        }

    @Test
    fun seed_replaces_state_directly() {
        val vm = makeVm("seeded")
        vm.seed(SupportTrainDetailUiState.Error("Boom"))
        val state = vm.state.value
        assertTrue(state is SupportTrainDetailUiState.Error)
        assertEquals("Boom", (state as SupportTrainDetailUiState.Error).message)
    }

    @Test
    fun refresh_reruns_resolver() =
        runTest {
            var hits = 0
            val vm = makeVm("any")
            vm.resolve = {
                hits += 1
                SupportTrainDetailSampleData.populated
            }
            vm.load()
            vm.refresh()
            assertEquals(2, hits)
            assertTrue(vm.state.value is SupportTrainDetailUiState.Loaded)
        }

    // MARK: - Sample fixtures

    @Test
    fun populated_fixture_matches_frame() {
        val content = SupportTrainDetailSampleData.populated
        assertEquals("The Reyes household", content.recipient.householdName)
        assertEquals(RecipientIdentityTag.Home, content.recipient.identityTag)
        assertTrue(content.recipient.verified)

        assertEquals(SupportTrainKind.Meals, content.typeDates.kind)
        assertEquals(12, content.typeDates.slotsFilled)
        assertEquals(21, content.typeDates.slotsTotal)
        assertEquals(20, content.typeDates.daysLeft)
        assertEquals(57, content.typeDates.percentCovered)

        assertEquals(28, content.calendarDays.size)
        // Tue Dec 2 (idx 8) is the `today` cell.
        assertEquals(SlotCalendarState.Today, content.calendarDays[8].state)

        // Mirrors the JSX state-vocab: 8 past (Nov 24-30 + Dec 1),
        // 1 today, 6 filled (Dec 3/5/7/9/11/14), 13 open
        // (Dec 4/6/8/10/12/13 + the all-open Dec 15-21 band).
        val pastCount = content.calendarDays.count { it.state == SlotCalendarState.Past }
        val todayCount = content.calendarDays.count { it.state == SlotCalendarState.Today }
        val openCount = content.calendarDays.count { it.state == SlotCalendarState.Open }
        val filledCount = content.calendarDays.count { it.state == SlotCalendarState.Filled }
        assertEquals(8, pastCount)
        assertEquals(1, todayCount)
        assertEquals(6, filledCount)
        assertEquals(13, openCount)

        assertEquals(2, content.sections.size)
        assertEquals("open", content.sections[0].id)
        assertEquals(3, content.sections[0].rows.size)
        assertEquals("See all 9", content.sections[0].actionLabel)
        assertEquals("covered", content.sections[1].id)
        assertEquals(3, content.sections[1].rows.size)

        assertNull(content.celebrationBanner)
    }

    @Test
    fun fully_covered_fixture_matches_frame() {
        val content = SupportTrainDetailSampleData.fullyCovered
        assertEquals(21, content.typeDates.slotsFilled)
        assertEquals(21, content.typeDates.slotsTotal)
        assertTrue(content.isFullyCovered)
        assertEquals(100, content.typeDates.percentCovered)

        assertEquals(28, content.calendarDays.size)
        // Every open in populated flips to filled; idx 10 is "mine".
        assertEquals(SlotCalendarState.Mine, content.calendarDays[10].state)
        val mineCount = content.calendarDays.count { it.state == SlotCalendarState.Mine }
        val openCount = content.calendarDays.count { it.state == SlotCalendarState.Open }
        val pastCount = content.calendarDays.count { it.state == SlotCalendarState.Past }
        assertEquals(1, mineCount)
        assertEquals(0, openCount)
        assertEquals(8, pastCount)

        assertNotNull(content.celebrationBanner)
        assertEquals("Every slot is covered", content.celebrationBanner?.title)

        assertEquals(2, content.sections.size)
        assertEquals("mine", content.sections[0].id)
        assertTrue(content.sections[0].rows.all { it.mine })
        assertEquals("nextup", content.sections[1].id)
        assertEquals("See all 21", content.sections[1].actionLabel)

        assertTrue(content.dock is SupportTrainDock.SendCardAndBackup)
    }

    // MARK: - Helpers

    @Test
    fun type_dates_percent_rounds_half_up() {
        val card =
            TypeDatesCardContent(
                kind = SupportTrainKind.Meals,
                title = "x",
                dateRange = "y",
                daysLeft = 0,
                slotsFilled = 1,
                slotsTotal = 3,
                contributors = emptyList(),
                extraCount = 0,
            )
        assertEquals(33, card.percentCovered)
        assertFalse(card.isFullyCovered)
    }

    @Test
    fun default_resolve_function_routes_correctly() {
        assertEquals(SupportTrainDetailSampleData.populated, defaultResolve("abc"))
        assertEquals(SupportTrainDetailSampleData.fullyCovered, defaultResolve("fully-covered-1"))
        assertEquals(SupportTrainDetailSampleData.fullyCovered, defaultResolve("full-1"))
    }
}
