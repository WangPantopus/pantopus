@file:Suppress("PackageNaming")

package app.pantopus.android.ui.screens.identity_center.view_as

import app.pantopus.android.ui.components.ViewerAudience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * B5.2 (A18.5) — projection tests for the "View as" preview. Mirrors iOS
 * `ViewAsViewModelTests`: locks the privacy matrix's two design endpoints
 * (Public redacts the most, Connection the least) and the instant
 * re-resolve when the picker changes. Pure JVM — the VM has no coroutines.
 */
class ViewAsViewModelTest {
    private fun render(vm: ViewAsViewModel): ViewAsRender? =
        (vm.state.value as? ViewAsUiState.Loaded)?.render

    private fun disclosure(
        render: ViewAsRender,
        fieldId: String,
    ): ViewAsFieldDisclosure? = render.fields.firstOrNull { it.id == fieldId }?.disclosure

    @Test
    fun startsLoading_thenLoadResolvesSelectedRender() {
        val vm = ViewAsViewModel()
        assertTrue(vm.state.value is ViewAsUiState.Loading)
        vm.load()
        assertEquals(ViewerAudience.Connection, render(vm)?.viewer)
    }

    @Test
    fun publicRedactsMutualsAndContact() {
        val vm = ViewAsViewModel()
        vm.load()
        vm.select(ViewerAudience.Public)
        val render = render(vm)!!
        assertEquals(true, disclosure(render, "mutuals")?.isHidden)
        assertEquals(true, disclosure(render, "contact")?.isHidden)
        // Location is coarsened, not withheld — the value still reads.
        assertEquals("Maple Heights district", disclosure(render, "location")?.shownValue)
        assertEquals(2, render.badges.count { !it.isOn })
        assertEquals(ViewAsTone.Restricted, render.banner.tone)
    }

    @Test
    fun connectionRedactsNothing() {
        val vm = ViewAsViewModel()
        vm.load()
        val render = render(vm)!!
        assertFalse(render.fields.any { it.disclosure.isHidden })
        assertTrue(render.badges.all { it.isOn })
        assertEquals("Available on request", disclosure(render, "contact")?.shownValue)
        assertEquals(ViewAsTone.Info, render.banner.tone)
    }

    @Test
    fun selectReResolvesRenderInPlace() {
        val vm = ViewAsViewModel()
        vm.load()
        assertEquals(false, disclosure(render(vm)!!, "contact")?.isHidden)

        vm.select(ViewerAudience.Public)

        assertEquals(ViewerAudience.Public, vm.selected.value)
        assertEquals(ViewerAudience.Public, render(vm)?.viewer)
        assertEquals(true, disclosure(render(vm)!!, "contact")?.isHidden)
    }

    @Test
    fun selectWhileLoadingUpdatesSelectionButStaysLoading() {
        val vm = ViewAsViewModel()
        vm.select(ViewerAudience.Public)
        assertEquals(ViewerAudience.Public, vm.selected.value)
        assertTrue(vm.state.value is ViewAsUiState.Loading)
    }

    @Test
    fun everyAudienceResolvesWithFiveFields() {
        val vm = ViewAsViewModel()
        vm.load()
        ViewerAudience.entries.forEach { audience ->
            vm.select(audience)
            assertEquals(audience, render(vm)?.viewer)
            assertEquals(5, render(vm)?.fields?.size)
        }
    }
}
