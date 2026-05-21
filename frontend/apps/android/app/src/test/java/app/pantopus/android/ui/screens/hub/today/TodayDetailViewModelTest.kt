@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.hub.today

import app.pantopus.android.ui.theme.PantopusIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A10.3 Today detail state machine. Backend has been removed, so the
 * view-model projects deterministic fixtures instead of repositories.
 */
class TodayDetailViewModelTest {
    @Test
    fun initialStateIsLoading() {
        val viewModel = TodayDetailViewModel()

        assertTrue(viewModel.state.value is TodayDetailUiState.Loading)
    }

    @Test
    fun loadResolvesToPopulated() {
        val viewModel = TodayDetailViewModel()
        viewModel.setFixture(TodaySampleData.populated)

        viewModel.load()

        val state = viewModel.state.value
        assertTrue(state is TodayDetailUiState.Populated)
        val content = (state as TodayDetailUiState.Populated).content
        assertEquals("67°", content.temperature)
        assertEquals("Mostly sunny", content.condition)
        assertFalse(content.isAlert)
    }

    @Test
    fun loadResolvesToAlertWhenRibbonPresent() {
        val viewModel = TodayDetailViewModel()
        viewModel.setFixture(TodaySampleData.alert)

        viewModel.load()

        val state = viewModel.state.value
        assertTrue(state is TodayDetailUiState.Alert)
        val content = (state as TodayDetailUiState.Alert).content
        assertTrue(content.isAlert)
        assertEquals(PantopusIcon.Snowflake, content.glyph)
    }

    @Test
    fun refreshReloadsCurrentFixture() {
        val viewModel = TodayDetailViewModel()
        viewModel.setFixture(TodaySampleData.alert)

        viewModel.refresh()

        assertTrue(viewModel.state.value is TodayDetailUiState.Alert)
    }

    @Test
    fun populatedFixtureShape() {
        val content = TodaySampleData.populated

        assertNull(content.ribbon)
        assertEquals(listOf("AQI", "UV", "Wind"), content.chips.map { it.label })
        assertEquals(4, content.signals.size)
        assertEquals(3, content.around.size)
        assertEquals("Signals · 4 today", content.signalsTitle)
        assertEquals(TodayTone.Personal, content.signalsAccent)
        assertEquals(1, content.signals.count { it.severity != null })
    }

    @Test
    fun alertFixtureShape() {
        val content = TodaySampleData.alert

        assertEquals("NWS hard-freeze warning · until 8am Fri", content.ribbon?.title)
        assertEquals(5, content.signals.size)
        assertEquals("Signals · 5 today", content.signalsTitle)
        assertEquals(TodayTone.Error, content.signalsAccent)
        assertTrue(content.around.isEmpty())
        assertEquals(listOf("Critical", "Watch"), content.signals.mapNotNull { it.severity?.label })
    }

    @Test
    fun chipDotTonesMatchScale() {
        val chips = TodaySampleData.populated.chips

        assertEquals(TodayTone.Success, chips[0].dotTone)
        assertEquals(TodayTone.Warning, chips[1].dotTone)
        assertNull(chips[2].dotTone)
    }
}
